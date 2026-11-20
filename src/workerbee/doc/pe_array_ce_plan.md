# PE 阵列计算逻辑：C / E 方案评估与实施计划（6 格式目标集）

> 本文是 [ws_tree_reduction_plan.md](ws_tree_reduction_plan.md) 的执行续篇。
> 范围：**只动 PE 阵列的计算逻辑**（MAC 内部加法器 + 列归约网络），不碰 DMA / feeder /
> Executor / sequencer / 寄存器映射 / 写回通路。
> 目标格式集（用户裁定）：**{BF16, MXE4M3, MXE5M2, MXE2M1, INT8, INT4}**。
> 所有面积数字为 yosys + Nangate45 liberty 预综合口径，**不是物理实现面积，更不是功耗**；
> 本机无 STA，**本文不给任何 Fmax / ns 结论**。

---

## 0. 结论摘要

| 编号 | 结论 |
|---|---|
| **P1** | 目标集与当前 8 格式默认集在 **PE 计算逻辑上逐位相同**：INT16 无硬件（保持弃用），纯 FP8 在阵列上游已被 `fp8FmtRemap` 改写成 MX ⟹ **基线唯一，无需为目标集另立对照**。 |
| **P2** | **方案 C（MX4 块内整数前端 + 3 槽 lazy 加法器）实测省 0.1103 mm² = 阵列 6.19%**，零数据流改动。**推荐立项。** |
| **P3** | **方案 E（加法器移出 PE + 列树）在 6 格式并集下两种形态都更差**：flat +7.21%、pair +3.84%（vs 基线）；vs C 分别 +14.29% / +10.70%。**不立项。** |
| **P4** | E 失败的根因是**节点数下界**，不是实现质量：MX8 每 PE 每拍 2 个 fp32 积且不可整数化 ⟹ 并集必须按 32 lazy 叶/列 provisioning；C 的串行链用 **16 个 3 槽节点/列**吸收 {carry, 积0, 积1}，任何 32 叶树需 **32 个节点**。E 在 MX4-only 下赢的 −42% 来自「把求和留在整数域」，而并集里那 12 个整数节点替换的是**为 MX8 已经付过钱的 lazy 节点** ⟹ 收益归零。 |
| **P5** | §6.1 Q1（饱和域）**已实测定价**：选项1 复刻逐元素饱和使 C 收益 **6.19%→3.37%**（+0.0502 mm²）。**选项2 声明规格变更 + block 级饱和到 max（§6.1.1）仅 +16.2 µm²/PE 直接逻辑（≈0.004 mm²），收益 ≈−5.96% 基本不变**，比选项1 便宜约 12×；Inf 会破坏有限值累加器（cReg=Inf 塌零）不可取。签核量化流不可达饱和域（F3）。这是语义裁定，须用户拍。 |
| **P6** | 本轮在测量代理里查出并修正 **4 处语义缺陷**（subnormal 等效尾数、underflow 冲刷、raw 前端位宽截断、小 scale 指数无符号绕回），修正后全部重测。**修正前的 C/E 数字作废**，本文数字为修正后口径。 |
| **P7** | **独立缺陷（与 C/E 无关）**：交付 RTL `Wb_WbFusionMacInt` 的四积中间和被 firtool 收窄到 16 bit，在 **INT8 且 a=b=0x8080** 这一个输入组合上结果偏 −65536。已用 yosys SAT 反例证实。建议单独修，见 §7。 |
| **P8** | 混合形态「C 串行链 + 仅 MX4 组网络」解析定价约 **−0.54%**，且注入时序在不去 skew 时不闭合 ⟹ 不值得立项，见 §5.4。 |
| **P9** | **§8.4 前置取证 F1–F3 已结**：C 在 in-spec 域与现状**数值相等但非逐位**（lazy-40 冗余切分，2100 SAT 分解证明 + 逐位失配见证）⟹ **金锚须由张量语义重推并量化多拍累加分叉界**，「零数值风险」卖点作废。tiny/E1 低域现状出非规格码（非真零）、现有回归零覆盖（F2）；饱和域签核量化流不可达（F3，amax≥2.98e38 才触发），但属软件流保证非硬件契约。见 §8.4.1。 |

**建议**：立项 C；E 关闭（保留 MX4-only SKU 场景下的结论备查）；先裁定 Q1 饱和域；
INT MAC 宽度缺陷单独开一条修复；**C 实施前先把金锚重推与多拍分叉界量化列入计划（P9）**。

---

## 1. 目标集与基线唯一性

用户裁定的目标集 = {BF16, MXE4M3, MXE5M2, MXE2M1, INT8, INT4}。两项与当前默认集的差异
**都不落在 PE 计算逻辑上**：

1. **INT16**：用户裁定「保持弃用，从列表去掉」。现状本就没有 INT16 硬件 ——
   [WorkerBeeConfig.scala:69](../scala/main/WorkerBeeConfig.scala) 注释「整数族 = {INT8, INT4}，不含 INT16」，
   [WbFusionMacInt.scala:36](../scala/main/WbFusionMacInt.scala) `require(hasInt8 || hasInt4)`。
   ⟹ 去掉 INT16 是**文档动作，零 RTL 改动**。
2. **纯 FP8（FP8E4M3 / FP8E5M2）**：用户裁定「只 MXFP8」。现状纯 FP8 在进阵列**之前**就被
   [WorkerBee.scala:287](../scala/main/WorkerBee.scala) / [:296](../scala/main/WorkerBee.scala) 的
   `fp8FmtRemap`（定义在 [:106](../scala/main/WorkerBee.scala)）改写成对应 MX 格式
   ⟹ **PE 阵列从来没见过纯 FP8 的 fmt 码**。
   ⟹ 删掉纯 FP8 只影响 `supportedFmts` 与 remap 分支，**PE 计算逻辑逐位不变**。

**⟹ P1：本文的基线就是当前默认配置的 PE 阵列，不存在「6 格式基线 vs 8 格式基线」的歧义。**

阵列形态（不变）：arrayN=16 行（K 轴）× arrayM=16 列（输出 N 轴），256 PE =
240 × `WbWorker` + 16 × `WbWorkerOsM1`（首行）。M 为时间维，B tile 驻留。

---

## 2. 测量方法与可信边界

### 2.1 方法

- 工具：yosys 0.69+post，liberty `~/.cache/pdk/nangate45/NangateOpenCellLibrary_typical.lib`。
- 流程：`read_verilog` 依赖闭包 → `hierarchy -check -top` → `synth -top X -flatten -noshare`
  → `dfflibmap -liberty` → `abc -liberty` → `opt_clean -purge` → `stat -liberty`。
  `-noshare` 必需（share pass 对大规模重复结构做 O(n²) SAT 会挂死）；已在 5 个模块上验证
  share/noshare 面积逐位相同。
- **整 PE 口径优先**：把候选 MAC 副本改名回 `Wb_WbFusionMacFp`，让
  `WbWorkUnit / WbWorker / WbWorkerOsM1` 的闭包自动拾取 ⟹ 量到的是**整 PE**，不是单模块相加。
  单模块面积**不可相加**（abc 跨模块边界共享逻辑）。
- E 的 PE 侧用**端口手术**：MAC / WorkUnit / Worker / OsM1 各加输出端口，其余逻辑逐字不动；
  `psumReg` 复用为部分积输出寄存。全部副本落在 `sim/ws-tree-20261118/`，
  **交付 RTL 与 `src/main` 零改动**。
- 触发器单价 **DFF_X1 = 4.522 µm²/flop**（liberty 实测）。
- **lazy-40 的 mant31 低 12 位结构性恒 0**（W=19 ⟹ LowSh=12）⟹ yosys 常量传播消掉这些触发器，
  每级有效 **28 bit**。本文所有寄存代价按 28 bit 计，且已被实测 seq 面积逐位验证
  （`tree32_skew` seq 8103.424 / 4.522 = **1792 flop** = 64 级 × 28 ✓）。

### 2.2 噪声底与组合误差

- **abc 噪声底 ±1.77%**（对 cell 顺序敏感）。小于此的差值不可作为结论。
- **单列 × 16 的组合误差**：`tree32_skew_x16` 实测 540292.816 vs 16 × 34173.818 = 546781.088
  ⟹ **−1.19%**（噪声内）。本文 E 的列网络按单列 × 16 计，并给出乘 0.98813 的修正值；
  两者都不改变结论符号。
- **整 PE 增量 ≠ 单模块增量**：本轮再次观测到放大效应 —— 饱和复刻在 MAC 级 +76.342 µm²，
  在整 PE 级 +200.298 µm²（2.6×）。**一律以整 PE 实测为准。**

### 2.3 ⚠ 本轮修正的 4 处代理语义缺陷（P6）

调研阶段的 MX4 整数前端代理**不是**张量语义的忠实实现。本轮用穷举校验脚本
`verify_mx4_all.py`（256 nibble 对 × 121 scale 对 = 30976 例）查出并修正：

| # | 缺陷 | 影响域 | 修正 |
|---|---|---|---|
| 1 | `P=(2+ma)(2+mb)` 对 **subnormal（e2=0,m=1）** 用了 sig=3，而 `FpCvtMx4ToMid` 把其尾数强制 0（sig=2） | 256 对中 **52 对**错（凡含码 0x1/0x9） | `maE = ma & (e2a!=0)`，零判据仍用原始 m |
| 2 | 前端**没有 underflow 冲刷**（`e2+scale<2` 时 `FpCvtMx4ToMid` 输出保号零） | scale∈{0,1} 的全部非零元素 | 加 `ua/ub` 比较并掩零 |
| 3 | `mx4_frontend_raw` 输出 11-bit，但 P<<sh 域块和上界 **2304** 需 13-bit ⟹ 截断 | 大幅值块和 | 内部 13-bit 求和后 `>>>2` 回网格域（上界 576，11-bit） |
| 4 | `exp = sa+sb−120` 用 **10-bit 无符号**算，`sa+sb<120` 时绕回成大指数 ⟹ 块和被钳到 exp=254（巨大值） | sa+sb<121 | 加 `tiny` 守卫冲刷为真零 |

修正后校验结果（`verify_mx4_all.log`）：

```
P<<sh (subnormal+underflow 修, 非饱和域): checked=26896  mismatches=0
n 形式 (underflow 修, 非饱和域):          checked=26896  mismatches=0
P<<sh (含饱和域, 预期失配):               checked=30976  mismatches=3360   ← 即 Q1 权衡
n 形式 + 饱和复刻 (全域):                 checked=30976  mismatches=0
n_eff 上界=12 ⟹ 单积 ≤144，4 积块和 ≤576 ⟹ 11-bit 带符号
ALL ASSERTIONS PASSED
```

> **缺陷 4 的遗留取证项（F1 已结，见 §8.4.1）**：`tiny` 域（sa+sb<121）现状 RTL **并非真零**。
> SAT 见证（sa=61, sb=3, a=0x37fa, b=0x0514）：`io.sum = {1, exp=0, mant≠0}` —— 正是
> `FpAddNarrow` 头注 (E1) 预告的「expOut=0 && mant31≠0 非规格 lazy 码」。该域现状无精度承诺，
> C 前端冲刷为真零是**语义变更**（向张量语义改进，但与现状逐位不兼容）。现有回归零覆盖（F2）。

---

## 3. 横向对比总表（全部整 PE 实测，修正后口径）

阵列 = 240 × `WbWorker` + 16 × `WbWorkerOsM1`。单位 mm²。

| 形态 | PE 侧 | +PE 输出寄存 | +列归约网络 | **合计** | **vs 基线** | **vs C** |
|---|---|---|---|---|---|---|
| **基线**（5 槽 FpAddNarrow 串行链） | 1.7810 | — | — | **1.7810** | — | +6.60% |
| **C**（3 槽 lazy + MX4 整数前端） | 1.6707 | — | — | **1.6707** | **−6.19%** | — |
| **C + 饱和复刻**（Q1 选项1） | 1.7209 | — | — | **1.7209** | **−3.37%** | +3.01% |
| **E-flat**（32 叶列树 + MX4 组网络） | 1.2962 | 0.0324 | 0.5808 | **1.9095** | **+7.21%** | +14.29% |
| **E-pair**（PE 内 MX8 对加 + 16 叶列树） | 1.4904 | 0.0127 | 0.3462 | **1.8494** | **+3.84%** | +10.70% |

按 §2.2 组合系数 0.98813 修正列网络后：E-flat **1.9026**（+6.82%），E-pair **1.8453**（+3.61%）。
**结论符号不变。**

### 3.1 整 PE 单模块实测明细（µm²）

| 模块 | 基线 | C | C+sat | E-flat | E-pair |
|---|---|---|---|---|---|
| `Wb_WbFusionMacFp` | 4026.708 | 3617.334 | 3693.676 | 2089.962 | — |
| `Wb_WbFusionMacInt` | 1710.646 | 1710.646 | 1710.646 | 1710.646 | 1710.646 |
| `Wb_WbWorkUnit` | 5918.234 | 5352.452 | 5394.214 | 3848.754 | 4779.488 |
| `Wb_WbWorker` | 6939.408 | 6518.596 | 6718.894 | 5050.276 | 5804.652 |
| `Wb_WbWorkerOsM1` | 7221.634 | 6638.296 | 6772.626 | 5257.756 | 6083.154 |

> E 的 INT MAC **保持原样**（仍含 cReg 加、仍走串行链）。理由：INT 的列归约若也整数化，
> 省掉的是每 PE 一个 40-bit cReg 加（≈219.7 µm² × 256 = **0.0562 mm²**），但要新增
> 16 列 × 15 个 19→23-bit 整数加（≈1704/列 ⟹ **0.0273 mm²**）+ 40-bit 整数 acc
> （≈220/列 ⟹ **0.0035 mm²**）+ 约 1007 flop/列的 skew 寄存（**0.0729 mm²**，解析估算）
> ⟹ 净 **+0.0475 mm²，亏**。
> **E∪ 里 INT 不动是唯一合理选择**，本文按此计价。

### 3.2 列网络单模块实测（µm²，每列）

| 模块 | 面积 | 其中时序 | 说明 |
|---|---|---|---|
| `tree32_skew` | 34173.818 | 8103.424（1792 flop） | 32 叶 skew 匹配树 + acc，64 级 × 28 bit |
| `col16u_flat_skew` | **36302.882** | 9604.728（2124 flop） | 上者 + 4 × `grp4_skew` + 叶 mux |
| `col16u_pair_skew` | **21639.898** | 7578.872（1676 flop） | 16 叶 skew 树 + 4 × `grp4_skew` + 叶 mux |
| `grp4_skew` | 682.290 | 375.326（83 flop） | 4 行裸块和 → 3 整数加 → 打包 lazy |
| `col16_grp_skew` | 8075.760 | 3780.392 | MX4-only 列网络（E 的原始形态） |

---

## 4. 方案 C：定义、实测、为什么省

### 4.1 定义

把 `WbFusionMacFp` 的累加器从 **5 槽 {C, L0, L1, L2, L3}** 改为 **3 槽 {C, S1, S2}**，
并新增一个 **MX4 块内整数前端**：

```
MX4 运行期：S1 = blk（前端输出的 lazy-40 块和），S2 = 0
MX8 运行期：S1 = lazy40(r0)，S2 = lazy40(r1)
BF16 运行期：S1 = lazy40(r0)，S2 = 0
```

前端直接从**原始 E2M1 nibble** 算四积块和，不经 fp32 乘积域：

- 元素归一化整数 `n = value / 2^(scale−128) ∈ {0,1,2,3,4,6,8,12}`
- 单积整数 `n_a·n_b ≤ 144`，四积块和 `≤ 576` ⟹ **11-bit 带符号精确**
- 打包：`mant31 = mag10 << 21`，`exp = sa + sb − 120`（校验：`mag10 × 2^(exp−136) = mag10 × 2^(sa+sb−256)` ✓）
- 三个边界域必须显式处理：**subnormal 等效尾数 0**、**underflow 冲刷**、**tiny-scale 守卫**（§2.3）

### 4.2 为什么省（两条机制，都已实测）

1. **加法器槽位 5 → 3**：`lazy_add3` 1399.692 vs `FpAddNarrow` 2180.934（单模块）。
2. **结构性红利**：前端直接吃 nibble ⟹ `r2/r3` 不再被消费 ⟹
   **2 个 `FpMulMx4`（2 × 92.036 = 184.072）+ 4 个 `FpCvtMx4ToMid` 成为死硬件**。

整 MAC 实测：4026.708 → **3617.334**（−10.17%）；整 PE 阵列 1.7810 → **1.6707**（−6.19%）。

> ⚠ **红利必须在真实实现里显式删例化才拿得到**。本文测量副本是把 `FpAddNarrow` 实例整体替换，
> `r2/r3` 表达式随之消失。真实 Chisel 里若 `fp4Mul0/fp4Mul1` 仍被例化而输出悬空，
> **firtool 会保留实例**，只有 yosys 才剪 ⟹ 拿不到这 184 µm²。
> 已核 `FpCvtMx4ToMid` 无 assert 副作用；E2M1 无 inf/nan 码位，不需要守护。
> 但 **`FpCvtMx4ToMx8Mid` 不可删** —— lane0/lane1 的 MX4→MX8 中间表示转换仍在
> [WbFusionMacFp.scala:159-172](../scala/main/WbFusionMacFp.scala) / [:279-292](../scala/main/WbFusionMacFp.scala) 使用。

### 4.3 前端写法选择（修正后重测）

| 写法 | 单模块 µm² | 语义 |
|---|---|---|
| **`mx4_frontend`（P<<sh + subnormal/underflow 修）** | **562.324** | 非饱和域精确 |
| `mx4_frontend_n`（n 解码 + 4×4 乘） | 641.060 | 非饱和域精确 |
| `mx4_frontend_n_sat`（n 形式 + 饱和复刻） | 728.574 | **全域精确** |

⟹ **非饱和方案选 P<<sh**（比 n 形式省 78.736 = 12.3%，超出噪声底）。
若 Q1 选饱和复刻，则必须走 n 形式（P<<sh 无法自然表达饱和后的 `n_eff = 3<<(254−scale)`）。

饱和复刻的化简（本轮新推导，已穷举验证）：
`1.5×2^127 = 3×2^126 = (3 << (254−scale)) × 2^(scale−128)`
⟹ 饱和元素的等效整数 `n_eff = 3 << (254−scale)`；overflow 蕴含 `scale∈{253,254}`
⟹ 移位量 ∈{0,1}，`n_eff ≤ 6 < 12` ⟹ **积上界 144、块和上界 576 均不变，11-bit 仍够**。
代价只是每 lane 每侧一个 9-bit 比较 + 一个 4-bit 二选一。

---

## 5. 方案 E：两形态实测、节点数下界、不立项结论

### 5.1 两种形态的定义

- **E-flat**：PE 内**完全无加法器**，输出 `p0`(lazy40) / `p1`(lazy40) / `I`(11-bit 裸块和)。
  列网络按 MX8 最坏情况 provisioning **32 个 lazy 叶**：叶 2i/2i+1 = 行 i 的两个积；
  BF16 奇数叶掩零；MX4 的 4 个组和占叶 0..3。INT 走原串行链。
- **E-pair**：PE 内先做 **MX8 对加**（`lazy_add2(p0,p1)`，BF16 时 p1 掩零，MX4 时输出 0），
  每列只剩 **16 个 lazy 叶** ⟹ 16 叶 skew 树 + 4 × `grp4_skew`。

两者都用 **skew 匹配树**（不去 A 行 skew，用延迟线买错峰），故 **D1 免**；
但 **D2 列驻留 acc / D4 读出 / D5 精度契约 / D7 跨 PE 共 scale 供数**一项都少不了。

### 5.2 实测（见 §3 总表）

E-flat **1.9095 mm²（+7.21%）**，E-pair **1.8494 mm²（+3.84%）**。
**两者都比基线差，更比 C 差 10–14%。**

### 5.3 根因：节点数下界（P4，可证不是实现问题）

每列每拍的操作数：

| 格式 | 每 PE 积数 | 每列积数 | 可否整数化 |
|---|---|---|---|
| BF16 | 1 | 16 | 否（fp32 域） |
| **MX8** | **2** | **32** | **否**（E4M3 积网格 2⁻¹⁸ ⟹ 38-bit；E5M2 2⁻³² ⟹ 66-bit，见调研 §6.2） |
| MX4 | 4（块内） | 64 → 4 组和 | **是**（11-bit） |
| INT8/INT4 | 2/4 lane | — | 是（但串行链已最省，见 §3.1 注） |

**并集必须按 MX8 的 32 积/列 provisioning。** 在此前提下：

- **C 的串行链**：每个 PE 一个 **3 槽** `lazy_add3`，一次吸收 {carry, 积0, 积1}
  ⟹ **16 个节点/列**。carry 跨拍复用，所以 32 个积只花 16 个加法器。
- **任何 32 叶树**：31 个合并节点 + 1 个 acc = **32 个节点/列**。
- **E-pair**：16 个 PE 内对加 + 16 个树节点（15+acc）= **32 个节点/列**。

实测单价对照（每列，组合口径）：

| 形态 | 节点构成 | µm²/列 |
|---|---|---|
| **C 串行** | 16 × `lazy_add3`(1399.692) | **22395.072** |
| E-flat | `tree32_acc`(32 节点) | 25672.192（**+14.6%**） |
| E-pair | 16 × `lazy_add2`(809.704) + `tree16_acc` | 25869.298（**+15.5%**） |

**E 还要额外付**：PE 输出寄存（flat +0.0324 / pair +0.0127 mm²）、树流水/延迟寄存
（已含在列网络 seq 里）、D2/D4/D5/D7 全部改动。

> **E 在 MX4-only 下赢 −42.2% 的机制**（调研 §7.4）是「把 12 个合并节点从 lazy 换成整数，
> 单价差 11–13 倍」。**并集里这 12 个 lazy 节点是为 MX8 已经付过钱的**，
> 换成整数并不能把它们删掉 ⟹ **收益归零，只剩树形的额外开销**。
> 这就是 §7.4 缺口 1 的实测答案：**粗估方向正确，且比粗估更差**。

### 5.4 混合形态「C 串行链 + 仅 MX4 组网络」（P8，解析定价，未实测）

思路：保留 C 的 16 个 3 槽链节点，只把 MX4 前端换成裸整数 + 加 4 × `grp4`，
组和注入链中某行的 S1 槽，其余行 S1=0。

- PE 侧：`mx4_frontend`(562.324) → `mx4_frontend_raw`(446.614) = **−115.710/PE** × 256 = −0.0296 mm²
- 列侧：+4 × `grp4`(311.486) = +1245.944/列 × 16 = **+0.0199 mm²**
- 净 ≈ **−0.0097 mm² = −0.54%**（噪声底边缘）

**且时序不闭合**：`grp4_skew` 的组和比行值晚 5 拍到达，而链节点按行序逐拍推进。
组 g 覆盖行 4g..4g+3，其组和就绪时链已流过注入点；最后一组（g=3）会溢出列尾。
要闭合必须 **D1 去 A 行 skew**（改 feeder 供数契约）或 **底部注入**（即方案 G 的 acc）。
⟹ **为 0.54% 付 D1 或 G 的全部改动，不立项。**

### 5.5 E 的未闭合契约（即便面积划算也要先解决）

1. **MX4 组和注入叶位的到达拍与树固有错峰不对齐**（§5.4 同源）。本文列网络代理把
   MX4 组网络与树**并列计价**，未实现注入对齐 ⟹ 表内 E 的数字是**乐观下界**。
2. **blk32 组大小不同**（每列 2 组 × 8 行），代理只实现 blk16 的 4 组 × 4。
3. **D7 跨 PE 共 scale**：组内 4 行须同拍拿到同一 `(sa,sb)`，是新的供数契约。
4. **D5 精度契约**：截断点与 E 锚点都变，金锚须由张量语义重推。

⟹ **P3：E 关闭。** 保留调研文档里 MX4-only SKU 的 −42.2% 结论备查，
但**该结论不适用于本目标集**。

---

## 6. 语义边界与待裁定

### 6.1 Q1 饱和域（**须用户裁定，已实测定价**）

`FpCvtMx4ToMid`（[FpCvt.scala:168-179](../scala/main/FpCvt.scala)）对每个元素做
overflow 饱和（`exp>254 → exp=254, mant=1`，即 ±1.5×2^127）。饱和后的元素**不再落在
`2^(sa+sb−256)` 共网格上** ⟹ 非饱和前端在该域与现状不一致（穷举 3360/30976 例失配）。

| 选项 | 语义 | 实测阵列收益 | 代价 |
|---|---|---|---|
| **1 复刻逐元素饱和** | 饱和域**数值**一致（仍非逐位，lazy 切分差异同在） | **−3.37%** | +0.0502 mm²（200.3 µm²/PE） |
| **2 声明规格变更 + block 级饱和到 max** | 非溢出域精确；溢出域饱和到有限 fp32max（§6.1.1） | **≈−5.96%** | +~0.004 mm²（16.2 µm²/PE 直接逻辑）；须改规格 + 金锚 |
| 3 运行期 fallback 旧路 | 一致 | **0**（5 槽硬件不能删） | 不成立 |

> 触发域极窄：`e2+scale>255` ⟹ 仅 `(e2=3, scale≥253)` 或 `(e2=2, scale=254)`。
> 该域块和本身也撞 lazy-40 的 `expOut` 钳 254（`FpAddNarrow` 头注 E3），属已定义退化域。
> **F3 已查证**：签核量化流产生饱和需 amax≥2.98e38（fp32 顶端尾段），签核模型 O(1)–O(10²) 不可达
> ⟹ 选项 2 在签核流**无饱和域数值风险**且省 ≈2.6 个百分点。⚠ 但这是软件流保证非硬件契约
> （DMA 接受任意 scale）；且**无论选 1/2，C 都非逐位兼容（P9），金锚均须重推**。**本文不擅自选**。

### 6.1.1 选项 2 的溢出语义：block 级饱和到 max（已实测定价，2026-11-19）

**为什么必须定义**：C 整数前端不算逐元素 fp32，块和精确携带为「整数尾数 + 共享指数 eSum−120」。
但 lazy-40 指数 8-bit、钳 254 ⟹ 可表示值 < 2¹²⁸。当 `eRaw=eSum−120>254`（eSum>374）真值超范围。
**原代理只钳指数不动尾数 ⟹ 静默缩小 2^(eSum−374) 倍的错误有限值**（比 Inf 更糟，不可检测）——
这是隐性 bug，选项 2 必须把它换成有定义的行为。

**选定语义：饱和到有限 fp32max**（不建议 Inf，理由见下）：
- 溢出时打包 lazy 码 `{sgn, 8'd254, 31'h7FFFFF80}`。该码经 [Fp32.scala:211 fpNormalize40to32](../scala/main/Fp32.scala)
  （RNE）**恰好归一化为有限 fp32max `{s,254,0x7FFFFF}`**（Python 验证）。
- **RNE 对齐陷阱**：尾数若置全 1 `0x7FFFFFFF`，RNE 进位 → expOut=255 → **fp32 Inf**；
  必须用 `0x7FFFFF80`（bit6=0 ⟹ roundUp=0 ⟹ 无进位）。已验证两种码的归一化结果。
- 非溢出域逐位等同原前端（精确）；tiny/零域仍冲刷真零。`verify_satmax.py` 穷举：
  **精确域 89766 + 溢出域 39204（==±fp32max）+ tiny/零 68166，0 失配**。

**为什么不用 Inf**（追代码证实）：
- lazy-40 是有限值格式。注入 `{s,255,0}` 会被 [FpAddNarrow.scala:123 unpackC](../scala/main/FpAddNarrow.scala)
  当 eEff=255、magW=0 ⟹ 抬高公共指数 E=255 ⟹ 其余槽全 term-drop ⟹ **整个累加器塌成真零**（灾难）。
- `fpNormalize40to32` 做 LZC 规格化，`{255,0}` 被归成小有限值而非 fp32 Inf。
- 故 Inf 需保留编码 + 加法器特判 + 归一化旁路 + 跨 C FIFO/psum 链状态传播 ⟹ 跨多模块、验证面大。
  饱和到 max  staying finite，累加器语义不变，且与既有约定一致（元素饱和 1.5×2¹²⁷、量化溢出饱和最大码、
  FpAddNarrow 钳 254 避 Inf/NaN）。

**实测代价**（`summary19.txt`/`summary20.txt`，交付 RTL 零改动）：
- **直接逻辑 = 前端单模块 +16.226 µm²/PE**（mx4_frontend 562.324 → satmax 578.550），即一个溢出 mux。
- 整 PE 代理差分散布 −76…+149 µm²（WorkUnit −76.3 / MAC +94.4 / Worker +149.5 / OsM1 +17.6），
  是 **abc 跨边界共享重排**所致（连 union 单模块差都 −30.6；重采样 n1=n2=n3 确定 ⟹ 非运行噪声，
  是结构敏感）。OsM1 +17.6 与前端直接逻辑 +16.2 吻合 ⟹ **真实逻辑成本 ≈16 µm²/PE**。
- **阵列最佳估算 ≈16.2×256 ≈ 0.0042 mm² ≈ 阵列 0.23%**，C 收益 −6.19% → **≈−5.96%**（噪声内基本不变）。
  即便取最坏整 PE 代理上界 0.036 mm² 仍 < 选项 1 的 0.0502。**比选项 1 便宜约 12×，比 Inf 便宜得多。**
-  definitive 整 PE 数字须真实 Chisel 实现 + 重发射（F4/F5）；代理 abc 共享伪影不迁移到真实 RTL。

**两个裁定项**：
1. **非 sticky**：饱和码是有限 lazy 值，回灌累加器后异号相加可 desaturate。真 IEEE 饱和/Inf 是 sticky。
   本域 out-of-contract（F3 签核不可达），非 sticky 大概率可接受，但须明确裁定。
2. **保守阈值**：ovf 判据 `eRaw>254` 在 eSum=375 且 bsum 很小时会饱和掉技术上仍可表示（leading-zeros）的值。
   深 out-of-contract 域，可接受；如需精确可改为按真值幅值判溢出（代价略增）。
3. **累加溢出未改**：本语义只覆盖前端 block 溢出；跨 kTile 累加把 cReg 指数推向 254 仍走 FpAddNarrow E3
   （共享、现状不动）。若要全链路一致饱和，须另对齐 E3 钳位也置 max 尾数（影响 MX8/BF16 高域，单列裁定）。

### 6.2 tiny-scale 域（**F1 已结，见 §8.4.1**）

`sa+sb<121` 时前端 `exp=sa+sb−120<1`，本文代理冲刷为真零。
**F1-c 已证现状 RTL 在该域非真零**：出 `{sign, exp=0, mant≠0}` 的 E1 非规格 lazy 码（无精度承诺）。
⟹ C 冲刷真零是**语义变更**（向张量语义改进，但与现状逐位不兼容）；现有回归零覆盖（F2），立项须补定向用例。

### 6.3 underflow 域（已修正，须回归覆盖）

`e2+scale<2`（scale∈{0,1}）时元素冲刷为保号零。前端已复刻。
**现有测试是否覆盖 scale∈{0,1} 须核查**；若无，新增定向用例。

### 6.4 精度契约（C 相对现状）

> **⚠ 订正（F1 取证后）**：旧版「MX4 非饱和域逐位一致」**已被证伪**。lazy-40 是冗余表示
> （允许 leading zeros），C 前端把块和装在**固定位置**（exp=eSum−120, mant=mag<<19），现状
> `FpAddNarrow` 按 **E+kGrow** 规格化 ⟹ 同一实数的 (exp,mant) 切分不同。SAT 见证
> （sa=127, sb=22, a=0x44b6, b=0x6475）：两侧 canon 后**数值相等**但 lazy 码不同（exp 25 vs 29）。
> **单拍块和数值精确，但多拍累加的对齐/截断点不同 ⟹ 累加结果可分叉，金锚须由张量语义重推并量化分叉界。**

- **MX4 in-spec 域**（定义见 §8.4.1）：C 与现状**数值相等**（非逐位）。形式化证明 = L1（逐 lane
  积精确，768 SAT）+ L2（`FpAddNarrow` 在 MX4 约束积下 = 精确整数和，1332 SAT）复合；前端整数和
  ↔ 张量语义由 `verify_mx4_all.py` 穷举（26896 非饱和例，0 失配）。
- **MX8 / BF16**：3 槽与 5 槽在掩零后**逐位等价**（§8.2 (b)(c)，零槽不贡献，kGrow 探测位不变）——
  这两族仍走 lazy 打包，切分位置不变，**不受上述订正影响**。
- **截断点严格减少**：MX4 从「4 次对齐截断」变成「0 次」（单拍）；但跨 kTile 回灌的截断点位置改变。
- 窗口 W=19、相对误差界 2⁻¹⁸、`expOut` 钳 254、真零判据 —— **全部沿用，不改**。
- **E1 低域 / 高饱和域**：现状无承诺（E1 出非规格码、高端钳位失真）；C 在低域冲刷真零、高域属 Q1。
  两域现有回归零覆盖（F2），签核量化流不可达（F3）。

---

## 7. 独立缺陷：`Wb_WbFusionMacInt` 中间和宽度（P7）

**与 C/E 无关，建议单独修。**

### 7.1 现象

[WbFusionMacInt.scala:76-77](../scala/main/WbFusionMacInt.scala)：

```scala
val sumFull = r0 + r1 + r2 + r3 + io.cReg.asSInt   // Chisel 推断 SInt(41.W)
io.sum := sumFull.asUInt(cEffW - 1, 0)             // 取低 40 bit
```

四个 16-bit 有符号积之和按 Chisel 语义应为 **19-bit**。但交付 RTL
[Wb_WbFusionMacInt.sv:65](../verilog/Wb_WbFusionMacInt.sv) 把中间和发射成 **16-bit**：

```verilog
wire [15:0] _sumFull_T_6 = <四积之和>;              // ← 16 bit
assign io_sum = {{24{_sumFull_T_6[15]}}, _sumFull_T_6} + io_cReg;
```

即先 mod 2¹⁶ 再符号扩展，然后加 cReg。

### 7.2 触发域（解析 + SAT 双重确认）

- **INT4 模式**：四 lane 都是 4-bit 有符号（sext 到 8），积 ∈[−56,64]，和 ∈[−224,256]
  ⟹ 16-bit 足够，**无错**。
- **INT8 模式**：`r2=r3=0`，`r0,r1 ∈[−16256,16384]`，和 ∈[−32512,**32768**]。
  16-bit 有符号上界 32767 ⟹ **仅当和 = 32768 时溢出**，即
  **`aFmt=bFmt=INT8` 且 `a[7:0]=a[15:8]=b[7:0]=b[15:8]=0x80`（a=b=0x8080）**。
  这是 2³² 个输入组合里的**唯一一个**。

### 7.3 证据（yosys SAT，可复现）

`sim/ws-tree-20261118/intmac_point.ys`：把交付 RTL 与全宽金锚 `gold_intmac.sv` 做 miter，
钉死 a=b=0x8080、cReg=0、fmt=INT8，`sat -verify -prove trigger 0 -seq 2 -set-init-zero`：

```
ERROR: Called with -verify and proof did fail!
SAT proof finished - model found: FAIL!
  2 \gate.full   = 0x0000008000   (金锚 = +32768)
  2 \gold_io_sum = 0xFFFFFF8000   (交付 RTL = 2^40 − 32768)
  2 \trigger     = 1
```

**偏差 = −65536**（bit16 及以上全错）。

> 方法学坑（已记入 memory）：miter 必须 **`proc` 在 `miter` 之前**跑。
> 先 `miter -equiv -flatten` 再 `proc` 会让 fmt 寄存被优化成 `1'x`，
> 两侧塌成同一坨 ⟹ **SAT 给出假 SUCCESS**。本轮第一次就踩了这个坑，
> 靠 `dump` 检查 `connect \gold.aIsInt8 1'x` 才发现。
> 另：本机 Verilator 独立二进制被宿主 SIGKILL（exit 137），故改用 yosys SAT。

### 7.4 为什么 266 绿没抓到

随机/定向用例几乎不可能命中 a=b=0x8080 这一个组合；金锚由张量语义推导（正确），
但**没有覆盖该点**。

### 7.5 建议修法（**待授权，本文未改任何源码**）

显式钉住每个积的位宽，不给 firtool 收窄的机会：

```scala
val sumFull = r0.pad(19) + r1.pad(19) + r2.pad(19) + r3.pad(19) + io.cReg.asSInt
```

- 代价：加法链从 16/17/18/19-bit 变成 19/20/21/22-bit，**面积增量须实测**（预计 <100 µm²/PE）。
- **修完必须用同一个 SAT miter 复验**（`intmac_equiv.ys` 全域证明），不能只看仿真绿。
- 新增定向用例：INT8 a=b=0x8080，cReg ∈ {0, 1, 0xFFFFFFFFFF}。
- 是否顺带修 `WbDebugModel` 金锚：**不需要**，金锚本就正确。

---

## 8. 方案 C 实施计划（文件 × 改动点 × 零 diff 保证）

> **本节是待确认的 plan，不是已执行的改动。** 按「RTL 深改先出 plan 待确认」纪律，
> 确认前不动 `src/main`。

### 8.1 文件 × 改动点

| # | 文件 | 改动 | 类型 |
|---|---|---|---|
| 1 | `src/workerbee/scala/main/Mx4BlockInt.scala` | **新增**。IO：`a0..a3/b0..b3`(UInt 4.W)、`scaleA/scaleB`(UInt 8.W)、`blk`(UInt 40.W)。P<<sh 前端 + subnormal 等效尾数 + underflow 冲刷 + tiny 守卫。头注写清网格推导与三个边界域。若 Q1 选 1 则改 n 形式 + `n_eff` 饱和 mux | 新增 |
| 2 | `src/workerbee/scala/main/FpAddLazy3.scala` | **新增**。IO：`cReg/p0/p1`(UInt 40.W)、`sum`(UInt 40.W)。三槽全用 lazy-40 解包（同 `unpackC`），W=19，`MagW=W+2`，`kGrow∈{0,1,2}`，平衡树 `tA=fC+f0; S=tA+f1`。精度契约沿用 `FpAddNarrow` 头注删去 L2/L3 段 | 新增 |
| 3 | `src/workerbee/scala/main/WbFusionMacFp.scala` | ① 把 [:195-223](../scala/main/WbFusionMacFp.scala) 的 `fp4Mul0/fp4Mul1` + 4 个 `FpCvtMx4ToMid` 与 [:335-336](../scala/main/WbFusionMacFp.scala) 的 `r2/r3` 用 `if (!useNarrowAdd)` 包住（默认路**不例化**）；② 例化 `Mx4BlockInt`，接 `io.a/io.b` 四 nibble + scale；③ `r0/r1` 打包 lazy-40；④ 槽选择 `s1=Mux(isMx4,blk,lazy(r0))`、`s2=Mux(isMx4,0,lazy(r1))`；⑤ `useNarrowAdd=true` 例化 `FpAddLazy3`，`false` 仍例化 `FpAdd`（5 槽基线参考路，原样）；⑥ `connectFiveSlotAdder` 拆成 3 槽 / 5 槽两个 helper | 改 |
| 4 | `src/workerbee/scala/main/FpAddNarrow.scala` | **不改、不删**。它仍是精度契约的权威参考且被 `FpAddNarrowSpec`/`FpAddNarrowOptSpec` 直接测试；默认 RTL 不再例化它。**处置（保留为参考 / 连同 spec 一起删）须用户裁定** | 不动 |
| 5 | `src/workerbee/scala/test/Mx4BlockIntSpec.scala` | **新增**。穷举 256 nibble 对 × scale 域（含 underflow / 饱和 / tiny），金锚由张量语义推导（复用 `verify_mx4_all.py` 的真值链，**禁止拟合 RTL**） | 新增 |
| 6 | `src/workerbee/scala/test/FpAddLazy3Spec.scala` | **新增**。3 槽 / p1=0 退化 2 槽 / 零槽 / 完全相消 / `expOut` 钳 254 / 与 `FpAddNarrow` 掩零后逐位对拍 | 新增 |
| 7 | `src/workerbee/scala/test/WbFusionMacFpNarrowSpec.scala`、`WbFusionMacFpBf16Spec.scala` | 对拍基线；新增 MX4 subnormal / underflow / tiny-scale 定向用例 | 改 |
| 8 | `src/workerbee/doc/spec.md` | 记录累加器 5→3 槽、MX4 整数前端、Q1 裁定结果、tiny 域契约 | 改 |

**不改（零 diff 保证面）**：`WbFusionMacInt` / `WbWorkUnit` / `WbComb` / `WbWorker` /
`WbWorkerOsM1` / `WorkerBeeExecutor` / `WbOsM1Feeder` / sequencer / 全部 DMA /
`WorkerBeeRegIf` / 寄存器映射 / `StreamFifo` / `WbCell.scala`。

### 8.2 逐位等价性证明（非目标配置零 diff 的依据）

**(a) `unpackProd(fp32)` ≡ `unpackC(prod_to_lazy40(fp32))`**

`prod_to_lazy40(p) = {p[31], p[30:23], ~z, p[22:0], 7'b0}`，`z = (exp==0 && mant==0)`。
- `m31 = {1, mant23, 7'b0}`（31 bit），`unpackC` 取 `m31 >> 12 = {1, mant23[22:5]}`（19 bit）；
  `unpackProd` 取 `mag24 >> 5 = {1, mant23[22:5]}` ⟹ **逐位相同**。
- `z` 时 `m31=0, exp=0` ⟹ `unpackC` 判真零、`eEff=0, mEff=0`；`unpackProd` 的 `valid=false`
  给同样结果 ⟹ **相同**。
- `exp==0 && mant!=0`（E1 subnormal 域）：两侧都给 `eEff=0, mEff=magW` ⟹ **相同**。

**(b) BF16：3 槽 {C, lazy(r0), 0} ≡ 现状 5 槽 {C, L0, 0, 0, 0}**
零槽 `eEff=0, mEff=0` 不参与 max、不贡献和 ⟹ 公共指数 E 与 S 逐位相同；
`MagW` 从 W+3 缩到 W+2 但 `|S| ≤ 2×(2^W−1) < 2^(W+1)` ⟹ `kGrow` 探测位与结果相同。∎

**(c) MX8：3 槽 {C, lazy(r0), lazy(r1)} ≡ 现状 {C, L0, L1, 0, 0}**
同 (b)，两个零槽不贡献。∎

**(d) MX4 in-spec 域：数值等价（非逐位），由 L1+L2 复合证明**

> 旧版「逐位一致」已证伪（§6.4 订正）：lazy-40 冗余 ⟹ 切分位置不同。下列证明只承诺**数值**。

- **L1（逐 lane 积精确）**：对每个 lane 结构（lane0 = `FpCvtMx4ToMx8Mid`+`FpMulBf16Mx` MX 臂；
  lane1 = `FpCvtMx4ToMx8Mid`+`FpMulMx8`；lane2/3 = `FpCvtMx4ToMid`+`FpMulMx4`），DUT 的 fp32 积
  == 前端整数 I=P<<sh 的 fp32 编码 `{sign, eSum+e2a+e2b+normBit−129, mant23(P)}`。
  钉 nibble 对（256 种）为常量、scale 自由 ⟹ 乘法器尾数输入恒定被 opt 折叠 ⟹
  **3 结构 × 256 = 768 个 SAT 证明全 SUCCESS**（`f1_l1_f1_lane{0,1,23}.log`）。
- **L2（加法器精确）**：`FpAddNarrow`（cReg=0, is4x=1）在 MX4 约束积（mant23∈{0,0x400000,0x100000}、
  非零槽 exp∈[1,251]、槽间跨度≤7）下 == 精确整数和。按零槽模式（15 种）× 跨度（8^k）枚举钉指数为
  常量（移位不变性 ⟹ 固定绝对指数无损一般性）⟹ **1332 个 SAT 证明全 SUCCESS**
  （`f1_l2_sweep.log` 512 + `f1_l2_zero.log` 820）。
- **复合**：DUT 块和 = L2( L1 各 lane 积 ) = 精确 Σ Iᵢ = 前端块和（前端定义 + `verify_mx4_all.py`
  张量语义穷举）。∎（数值）
- **逐位不等的见证**：`f1b.log`（sa=127, sb=22）canon 相等、lazy 码不同 ⟹ 多拍累加截断点分叉，
  金锚须重推。**端到端单 miter 的 UNSAT 在本机 minisat 不收敛**（无 SMT solver；故用上述分解）。

**(e) INT8/INT4**：`WbFusionMacInt` 与 `WbWorkUnit` 的 `selInt` 臂
（[WbWorkUnit.scala:101-105](../scala/main/WbWorkUnit.scala)）逐字不动 ⟹ 发射后
`Wb_WbFusionMacInt.sv` **MD5 必须不变**。∎

### 8.3 非目标配置零 diff 的机械验证

重发射默认 RTL 后：
1. `Wb_WbFusionMacInt.sv`、`Wb_WbWorkUnit.sv`、`Wb_WbWorker.sv`、`Wb_WbWorkerOsM1.sv`、
   `Wb_WbComb*.sv`、全部 DMA / feeder / sequencer / RegIf 模块 **MD5 逐字节不变**。
2. 只有 `Wb_WbFusionMacFp.sv` 变，且新增 `Wb_Mx4BlockInt.sv` / `Wb_FpAddLazy3.sv`、
   **不再出现** `Wb_FpAddNarrow.sv` / `Wb_FpMulMx4.sv` / `Wb_FpCvtMx4ToMid.sv` 的实例。
3. `loadWMode=0` 与 OS-M1 路径：PE 端口与 psum 链不变 ⟹ 由 (1) 覆盖。
4. 对照目录与子模块发射入口已删（交付面收为单一默认 verilog），**只发一组**。

### 8.4 前置取证（立项前必须做完）

| # | 取证项 | 判据 |
|---|---|---|
| F1 | **tiny-scale 域对账**（§6.2）：sa+sb<121 时现状 RTL 的 `io.sum` 是否真零 | 仿真扫 sa,sb ∈{0,1,60,120} × 全 nibble；若现状非零，须改前端守卫策略并重新定价 |
| F2 | **underflow 域覆盖核查**：现有用例是否含 scale∈{0,1} |  grep + 仿真；缺则补 |
| F3 | **签核模型 MXFP4 block scale 直方图**：是否触及 253/254 | 决定 Q1 选 1 还是 2（§6.1） |
| F4 | **死硬件红利核实**：真实 Chisel 删例化后重发射，确认 `FpMulMx4`/`FpCvtMx4ToMid` 实例消失 | §8.3 (2) |
| F5 | **184 µm² 红利在整 PE 的兑现率**：重发射后重跑 yosys 整 PE 对照 | 与本文 1.6707 mm² 对账（±1.77% 噪声内） |

#### 8.4.1 F1–F5 取证结果（F1–F3 2026-11-19 已结；F4/F5 2026-11-20 C 实施后已结）

**in-spec 域定义**（F1 证明的适用域，`f1_probe.sv:inSpec`）：
`noSat`（sa,sb≤252，排除元素饱和）∧ 每非零积 fp32 exp 字段≥1（`eAdj=eSum+e2a+e2b+normBit−2≥128`，
排除 E1 subnormal 积）∧ `eSum≤370`（排除块和指数饱和邻域，属 Q1）。

**F1 — tiny/非饱和域对账（SAT，交付 RTL 的 hoisted 语义等价副本）**
- **F1-a 数值等价：成立（分解形式化证明）**。端到端单 miter 的 UNSAT 在本机 minisat 不收敛
  （canon-LZC 版 15min CPU 未结；网格比较器版 24897 变量仍不结；无 SMT solver，pip 装 z3 被权限拦）。
  改用分解：**L1 逐 lane 积精确 768 SAT + L2 加法器精确 1332 SAT，共 2100 证明全 SUCCESS**（§8.2(d)）。
  复合 ⟹ in-spec 域 C 与现状**数值相等**。
- **F1-b 逐位等价：证伪**。见证 sa=127, sb=22, a=0x44b6, b=0x6475：canon 后数值相等，
  lazy 码不同（exp 25 vs 29，mant 15 同值异位）⟹ **C 非逐位兼容**，多拍累加截断点分叉，金锚须重推。
- **F1-c tiny 域真零：证伪**。见证 sa=61, sb=3, a=0x37fa, b=0x0514：现状 `io.sum={1,exp=0,mant≠0}`
  = E1 非规格码（非真零）⟹ C 冲刷真零是语义变更。现状在该域无承诺，现有回归零覆盖。
- 证据：`f1_probe.sv`/`f1_adder.sv`/`f1_lane.sv`，`f1b.log`/`f1c.log`/`f1_l1_*.log`/`f1_l2_sweep.log`/`f1_l2_zero.log`。

**F2 — underflow/极端 scale 覆盖核查（grep + 量化器分析）**
- 端到端 MX 用例（`WorkerBeeSimCaseMxSpec`/`WbDebugModel`）：scale 指数恒 ∈{−1,0,1}（字节 126–128），
  eSum∈[252,256]。**无 scale∈{0,1}，无 E1 低域，无高饱和**。
- MAC 级 `WbFusionMacFpNarrowSpec` 定向：scale 对最小和 338（238+100）、最大 506（253+253 饱和例）；
  18 例 127/127。**无 scale 0/1**。
- `swBlockScale` 钳 s∈[1,254]，全零块→0 哨兵；s=1 仅 fp32 次正规 amax（<1.18e−38）可达。
- **结论：E1 低域与元素 underflow 现有 266 绿零覆盖** ⟹ C 在该域的行为变更对现有回归不可观测；
  若立项须补定向用例（scale∈{0,1}、eSum<130、全零块哨兵）。

**F3 — 签核模型 MXFP4 block scale 直方图**
- 仓库**无真实权重张量/scale 直方图**（`model_analysis` 仅 shape/FLOPs；`model_eval` 为 cost 模型）。
- 改用**量化器不变量穷举**（`f3_quant_invariant.py`，镜像 `swBlockScale`+`swQuantElem` E2M1 臂）：
  元素饱和（e2+s−1>254）**仅当 amax ≥ 3.5×2¹²⁶ ≈ 2.98e38**（fp32 顶端尾段 e=254, m≥0x600000）；
  s≥253 需 amax≥2¹²⁶≈8.5e37；块和指数饱和（eSum≥375）需 amax_A×amax_B≥2¹²¹≈2.6e36。
- 签核模型（Qwen3.5-0.8B/2B、DeepSeek1.5B、YOLO11n/s）权重/激活 O(1)–O(10²)
  ⟹ **饱和域在签核量化流不可达**。
- **⚠ 边界**：硬件经 DMA 接受任意 scale 字节，量化器不变量是**软件流保证**而非硬件强制契约。
  Q1（声明饱和 out-of-contract 保 −6.19% vs 复刻饱和 −3.37%）仍由用户裁定；数据强烈支持「签核流不可达」。

**F4 — 死硬件红利核实（2026-11-20，C 实施后重发射，已结）**
- 真实 Chisel 编译期 `if(!useNarrowAdd)` 不例化 fp4Mul0/1 + 4×FpCvtMx4ToMid；默认 RTL 重发射后
  `Wb_FpAddNarrow.sv`/`Wb_FpMulMx4.sv`/`Wb_FpCvtMx4ToMid.sv` 三文件删除，新增 `Wb_Mx4BlockInt.sv`/`Wb_FpAddLazy3.sv`。
- C 的 WbWorker 依赖闭包 = 10 模块，无上述三者；交付 `Wb_WbFusionMacFp.sv` dead-ref grep = **0**；
  `FpCvtMx4ToMx8Mid`（lane0/lane1 MX4→MX8 中间表示）×4 保留 ⟹ **184 µm² 死硬件红利兑现**（编译期不例化，非悬空）。
- Q1 裁定 = **选项 2 + block 饱和到 max**；`FpAddNarrow` 保留为精度契约参考（仍被 FpAddNarrowSpec/OptSpec 测试），默认 RTL 不再例化。

**F5 — 整 PE 红利兑现率（2026-11-20，真实重发射 RTL yosys+nangate45，已结）**
- 基线（HEAD 5 槽 MAC + 当前 WP-A IntMac + 未改 PE 模块）阵列 **1.8014 mm²** → C **1.6805 mm²** = **−6.71%**
  （IntMac 两侧同 1714.902 µm² ⟹ 纯 WP-B）。单模块 MAC 3995.054→3604.832（−9.77%）、WorkUnit 5734.694→5252.702、
  Worker 7018.942→6557.698、OsM1 7304.094→6665.694。
- 对账（±1.77% 噪声内）：真实 C vs 本文代理 1.6707 **+0.59%**、vs satmax 1.6975 **−1.00%**、真实基线 vs 代理 1.7810 **+1.15%**。
  收益符号与量级确认，真实 **−6.71%** 略优于代理 −6.19%（Q1 选 2+satmax 增量已含于 Mx4BlockInt）。
- ⚠ 代理与真实 ~1% 系统偏移来自 yosys 0.69→0.71 + 真实独立模块文件 vs 代理共享 e_nodes.v 的 abc 输入序差异；
  §3 代理数字保留作背景，**definitive 以本 F5 真实实测为准**。
- 测量法：`hoist.py`（提 automatic，四守卫，不回写交付 RTL）+ `closure.py`（只读依赖闭包，hoisted-if-exists）
  + `synth -flatten -noshare`；报告 `build/wpB_f5_area.md`，脚本/日志 `sim/wpB-f5-20261120/`。
- 定向精度回归（非全量，按用户裁定纯数据通路不涉控制层）：Mx4BlockIntSpec/FpAddLazy3Spec/WbFusionMacFpNarrowSpec/
  WbFusionMacFpBf16Spec/WbFusionMacIntSpec **44/44 绿、5 suite、SBT_EXIT=0**；spec 同步 v5.1.0（§5.2.5/§5.2.6）。

### 8.5 验证与回归

- 单 suite：`-J-Xmx6G`；全量：**20 suite / 266 test，`-J-Xmx10G` + 绝对 tmpdir**。
- 一次只跑一个 sbt 进程；后台 run 期间不编辑源码。
- 新增用例消息用中文；硬件 assert/require 消息用英文 ASCII。
- 参数化用例名不得撞车（`DuplicateTestNameException` 会在 suite 构造期 abort）。
- 金锚一律由张量语义推导，**禁止以 RTL 行为为拟合目标**。

### 8.6 风险

| 风险 | 缓解 |
|---|---|
| firtool 保留悬空实例 ⟹ 拿不到 184 µm² | 用编译期 `if` 不例化，而非例化后悬空；F4 核实 |
| **MX4 多拍累加与现状分叉（P9）** | F1 已证非逐位；金锚由张量语义重推，量化分叉界（绝对界 ≤ n_lossy×窗口LSB 沿用），补多拍 MX4 定向用例 |
| tiny/E1 低域行为变更 | F1-c 已证现状非真零；C 冲刷真零属语义改进，补 scale∈{0,1}/全零块哨兵定向用例（F2 零覆盖） |
| Q1 选 2 后签核模型实际触及饱和域 | F3 已证签核量化流不可达（amax≥2.98e38）；硬件契约层面仍须用户裁定 |
| `FpAddNarrow` 处置悬而未决 | §8.1 #4 列为裁定项，不擅自删 |
| 面积收益被 abc 噪声吞掉 | 6.19% ≫ 1.77% 噪声底，且整 PE 口径已实测 |

---

## 9. 证据索引

全部测量脚本 / 日志 / 副本在 `sim/ws-tree-20261118/`（临时仿真目录，交付 RTL 零改动）。

### 9.1 本轮新增

| 文件 | 内容 |
|---|---|
| `verify_mx4_all.py` / `.log` | **MX4 前端全域语义校验**（30976 例，三种写法，含 underflow/饱和域） |
| `verify_mx4_frontend.py` / `.log` | subnormal 修正的前后对照（52/256 → 0/256） |
| `verify_mx4_sat.py` | 饱和复刻校验（早期版本，已被 `verify_mx4_all.py` 取代） |
| `gold_intmac.sv` / `intmac_point.ys` / `.log` | **INT MAC 宽度缺陷的 SAT 反例**（§7） |
| `intmac_equiv.ys` | 全域等价证明脚本（修完后复验用） |
| `e_nodes.v` | E 列网络代理 + 饱和复刻前端 |
| `mk_e_variants.py` / `mk_e_pair.py` | E 的整 PE 端口手术副本生成器 |
| `run11.sh`…`run18.sh` / `summary11.txt`…`summary18.txt` | 测量批与汇总 |
| `f1_probe.sv` / `f1_reads.ysinc` | F1 顶层探针（DUT MX4 vs 整数前端，网格比较器 + inSpec 约束 + canon） |
| `f1b.log` / `f1c.log` | **F1-b 逐位失配见证 / F1-c tiny 域非真零见证**（SAT FAIL 反例） |
| `f1_adder.sv` / `f1_l2_sweep.ys` / `.log` / `f1_l2_zero.ys` / `.log` | **L2 加法器精确引理**（512+820=1332 SAT 全 SUCCESS） |
| `f1_lane.sv` / `f1_l1_f1_lane{0,1,23}.ys` / `.log` | **L1 逐 lane 积精确引理**（3×256=768 SAT 全 SUCCESS） |
| `f3_quant_invariant.py` / `.log` | **F3 量化器饱和不变量穷举**（饱和需 amax≥2.98e38） |
| `f1a*.log` / `f1_l2.log` | 端到端单 miter 不收敛记录（minisat，无 SMT solver；故用 L1+L2 分解） |
| `e_nodes.v:mx4_frontend_satmax` / `mac_add_union_satmax` | **Q1 选项2 block 级饱和到 max 前端代理**（§6.1.1） |
| `verify_satmax.py` / `.log` | satmax 语义穷举（精确 89766 + 溢出→fp32max 39204 + tiny/零 68166，0 失配） |
| `run19.sh` / `summary19.txt` / `run20.sh` / `summary20.txt` | satmax vs C 整 PE 对照 + 3× 重采样（前端 +16.2 µm²/PE；整 PE 差分 abc 共享散布） |

### 9.2 关键数据来源

| 数字 | 来源 |
|---|---|
| 基线整 PE（6939.408 / 7221.634 / 5918.234 / 1710.646） | `summary10.txt` |
| C 整 PE（6518.596 / 6638.296 / 5352.452 / 3617.334） | `summary16.txt` |
| C+sat 整 PE（6718.894 / 6772.626 / 5394.214 / 3693.676） | `summary17.txt` |
| E-flat / E-pair 整 PE | `summary16.txt` |
| 列网络（36302.882 / 21639.898 / 34173.818） | `summary18.txt` / `summary12.txt` |
| 组合系数 0.98813 | `summary12.txt`（`tree32_skew_x16`） |
| 前端单价（562.324 / 641.060 / 728.574 / 446.614） | `summary16.txt` |
| lazy 节点单价（809.704 / 1399.692 / 12914.034 / 25672.192） | `summary.txt` |

### 9.3 源码锚点（已 grep 核实）

- [WbFusionMacFp.scala:195-223](../scala/main/WbFusionMacFp.scala) `fp4Mul0/fp4Mul1` + 4 转换器
- [WbFusionMacFp.scala:334-336](../scala/main/WbFusionMacFp.scala) `r1/r2/r3`
- [WbFusionMacFp.scala:341-365](../scala/main/WbFusionMacFp.scala) 累加器条件例化 + helper
- [WbFusionMacInt.scala:76-77](../scala/main/WbFusionMacInt.scala) 四积 + cReg
- [Wb_WbFusionMacInt.sv:65](../verilog/Wb_WbFusionMacInt.sv) / [:97](../verilog/Wb_WbFusionMacInt.sv) 16-bit 中间和
- [FpAddNarrow.scala:110-134](../scala/main/FpAddNarrow.scala) `unpackProd` / `unpackC`
- [FpAddNarrow.scala:177-197](../scala/main/FpAddNarrow.scala) 平衡树 + 输出装配
- [FpCvt.scala:162-179](../scala/main/FpCvt.scala) `FpCvtMx4ToMid` 三个边界域
- [WbWorkUnit.scala:101-105](../scala/main/WbWorkUnit.scala) 输出路由 mux
- [WorkerBee.scala:106](../scala/main/WorkerBee.scala) / [:287](../scala/main/WorkerBee.scala) / [:296](../scala/main/WorkerBee.scala) `fp8FmtRemap`
- [WorkerBeeConfig.scala:38](../scala/main/WorkerBeeConfig.scala) 默认格式集 / [:69](../scala/main/WorkerBeeConfig.scala) 整数族不含 INT16

### 9.4 本文未做 / 不做

- 未改任何 `src/main` / `src/workerbee/verilog` 文件；未跑回归；未重发射 RTL。
- 未给 Fmax / 时序 ns 结论（本机无 STA）。
- 面积比**不得**当作功耗节省。
- 不复活已排除项：SiLU/GELU 融合、核内描述符队列、单独加宽 B 口、DMA 侧 2:4 解压、
  普通 regFile 双缓冲、W4A8→INT8×INT4 独立吞吐项、rank3 捆绑。
- 方案 G（底部注入）已按用户指示记录于调研文档 §7.7，**M=1 吞吐优化本轮不做**。
- Queen R1–R4 对齐仍搁置。