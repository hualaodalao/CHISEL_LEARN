# OS 模式扩展形态评估：OS=1 / OS=2 / split-K / OS-M_r

**状态：只出方案，未动任何 RTL / 测试。** 待裁定后才编码。
**日期：2026-11-17**
**范围：** `src/workerbee`。不涉及 foragerbee / llc / gingercat / utils。
**前置裁定（用户 2026-11-17）：B 口带宽拓展是未来可能的** ⟹ 本文不把「B 端口 32 B/拍」当永久硬墙，而当作**代际条件项**：所有需要 2× B 带宽的形态都标为「条件立项，前置 = 端口加宽」，并给出端口加宽后的完整收益与代价。

---

## 0. 结论摘要

| 形态 | 语义 | B 带宽需求 | C 侧 | 增益（B 端口不变） | 增益（B 端口 2×） | 逻辑面积增量 |
|---|---|---|---|---|---|---|
| **OS=1**（现状） | 16 列 × M=1，K 时间归约 | 1×（已 94–98.5%） | 16 列 | 基线 | 基线 | 0 |
| **OS=2** | 32 列 × M=1（同 A 不同 B） | **2×** | **32 列，要改** | 1.008–1.015× | 2.000× | 中（含 totalN 解耦） |
| **split-K2** | 16 列 × M=1，K 切两半 | **2×** | **16 列，不动** | 0.996–0.999× | **1.876–1.966×** | **+6.81% 阵列** |
| **OS-M_r** | 16 列 × M=r（同 B 不同 A） | **1×（不变）** | 16 列 × r 行 | 需 M≥r，B 流量 ÷r | 同左 | 随 r 线性 |

**三条核心判断：**

1. **M=1 时 OS 的吞吐下界 = `K×N×elemBytes / 32B` 拍，与行数 / PE 数 / K 切分方式无关。** OS=1 已达该界的 94.1–98.5% ⟹ 不动 B 带宽时，任何形态的收益上界只有 1.5–5.9%。
2. **split-K2 是四个形态里唯一「C 侧与 totalN 完全不动」的 2× 方案**，绕开了 rank3 代价清单里最重的两项。代价转移到供数通路复制 + 合并加法器。
3. **OS-M_r 是唯一不增加 B 带宽的扩展方向**（B 跨 M 行复用），但需要 M≥r ⟹ 对纯 M=1 decode 无效，钥匙在软件侧抬 M（报告 rank6）。

**推荐路线**：split-K2 作为 **B 端口加宽代际的配套阵列改动**立项（条件项），OS=2 不立项（被 split-K2 严格支配：同样 2× 带宽需求，增益略低，代价更高）。OS-M_r 与 rank6 绑定评估。

---

## 1. 实测基线与端口下界

### 1.1 OS=1 的周期律（来自 spec 硬断言，非估算）

[WorkerBeeOsM1PerfSpec.scala:573](../scala/test/WorkerBeeOsM1PerfSpec.scala)：
```
advances   = nTiles × (length + T − 1)
lowerBound = nTiles × (length + T)          // +1 = commit
length     = K / lanes                      // :173，lanes = elemsPerLane(aFmt)
T          = cfg.totalN = 16
```
大形状 N=2048 / K=1024 / BF16 的硬断言值：`advances=132992`、`commits=128`、`lowerBound=133120`（[:579](../scala/test/WorkerBeeOsM1PerfSpec.scala)）。

B 侧 [:541](../scala/test/WorkerBeeOsM1PerfSpec.scala)：纯格式 `B.logicalReq = nTiles × bKTiles × T = nTiles × length`。

⟹ **B 请求率 = (nTiles×length) / (nTiles×(length+T)) = length/(length+T)**，端口 32 B/拍。

### 1.2 端口占用闭式

> **OS=1 的 B 端口占用 = length/(length+T) = (K/lanes) / (K/lanes + 16)**

唯一余量是每 N tile 的 T=16 拍 tail+commit。N=2048、K=1024、M=1：

| 格式 | lanes | length | 逻辑下界 | B 字节 | B 带宽下界 | **B 端口占用** | 不动带宽的收益上界 |
|---|---|---|---|---|---|---|---|
| BF16 | 1 | 1024 | 133,120 | 4096 KB | 131,072 | **98.5%** | 1.5% |
| MX8 / INT8 | 2 | 512 | 67,584 | 2048 KB | 65,536 | **97.0%** | 3.0% |
| MX4 / INT4 | 4 | 256 | 34,816 | 1024 KB | 32,768 | **94.1%** | 5.9% |

A 侧：每 16 步捕一个 32 B 窗（[WbOsM1Sequencer.scala:98-100](../scala/main/WbOsM1Sequencer.scala)）⟹ **A 端口 6.15%（BF16）/ 6.06%（MX8）**。
scaleB 侧：端口 `scaleBW = 8×maxScalePerCyele` = 32 bit = **4 B/拍**（[WorkerBeeConfig.scala:142](../scala/main/WorkerBeeConfig.scala)）；feeder 每个 B 窗必须配一个 scaleWord（[WbOsM1Feeder.scala:61](../scala/main/WbOsM1Feeder.scala)）⟹ **push 侧 1 次/拍 = 100%**；read 侧靠 rank2b pairScale（F=2）降到 0.5/拍。

### 1.3 不可约性

M=1 时每个 B 元素恰被读一次 ⟹ `B 字节 = K×N×elemBytes`，**与阵列形态无关**。逐拍核对（N=2048, K=1024）：

| 格式 | OS=1 | split-K2 | OS=2 | 张量 |
|---|---|---|---|---|
| BF16 | 32 B/拍 × 131,072 步 | 64 B/拍 × 65,536 步 | 32 B/拍 × 131,072 步 | 4096 KB |
| MX8 | 32 B/拍 × 65,536 步 | 64 B/拍 × 32,768 步 | 32 B/拍 × 65,536 步 | 2048 KB |
| MX4 | 32 B/拍 × 32,768 步 | 64 B/拍 × 16,384 步 | 32 B/拍 × 32,768 步 | 1024 KB |

**字节完全相同。** 切 K / 扩 N 都只是把同样的字节压进更短的时间 ⟹ 速率同比翻倍。这就是为什么 OS=2 与 split-K2 的带宽需求一模一样。

### 1.4 面积基线（yosys + nangate45 typical，2026-11-17 实测）

口径：`synth -top -flatten` + `dfflibmap` + `abc -liberty` + `stat -liberty` 的 Chip area（µm²）。脚本与日志：`sim/os-splitk-20261117/`。firtool `automatic` 用测量副本 hoist 绕过（方法同 `sim/rank7-area-20261116/hoist.py`），**交付 RTL 零改动**。

| 模块 | µm² | 备注 |
|---|---|---|
| `Wb_WbWorker`（WS PE） | 7,033.572 | |
| `Wb_WbWorkerOsM1`（OS 双模 PE） | 7,251.160 | 差 **217.588**/PE |
| `Wb_WbOsM1Feeder` | 77,986.146 | 16 列 × 2 槽 |
| `Wb_WbOsM1Sequencer` | 81,948.482 | feeder 占 **95.2%** |
| `Wb_FpAddNarrow`（5 槽，W=19） | 2,180.934 | |
| `lazy_add2`（2 槽合并器代理，W=19） | **795.606** | 5 槽版的 **36.5%** |
| `combine16`（16 列合并器） | **13,020.966** | |
| `Wb_Queue64_UInt256`（B FIFO） | 132,359.738 | ⚠ **flop 映射上界**，见下 |
| `Wb_Queue64_UInt32`（scaleB FIFO） | 16,779.280 | ⚠ **flop 映射上界** |
| DMA（4 变体） | 综合失败 | 只给 flop 下界，见 §5.6 |

阵列基线 = 240×WS PE + 16×OS PE = **1.8041 mm²**（只含 PE）。

> ⚠ **FIFO 面积口径警告**：`Wb_Queue64_UInt256` 内部是 `Wb_ram_64x256`（SyncReadMem，[WorkerBeeScratchpad.scala:45](../scala/main/WorkerBeeScratchpad.scala) 的 `StreamFifo`）。nangate45 liberty **无 SRAM 单元** ⟹ yosys 把 64×256 bit = 2 KB 映射成触发器阵列，得到 0.1324 mm²。**这是上界，不是真实 SRAM 面积**；真实工艺下 2 KB SRAM 会显著更小，本仓无 SRAM liberty 故不可测。本文凡涉及 FIFO 的面积都按此口径标注，不与逻辑面积混算结论。

---

## 2. 四个形态的机制与收益

### 2.1 OS=2（扩 N 到 32 列）—— 不立项

row1 与 row2 喂**同一份 A**、**不同的 B** ⟹ 输出列从 16 变 32。即报告 rank3（C32）。

- B：32 窗/kTile ÷ 16 步 = **2 推/拍**
- C：每 commit 32 列 ⟹ C FIFO 宽度翻倍、`cDmaExtW` 契约族重算（[WorkerBeeConfig.scala:203-212](../scala/main/WorkerBeeConfig.scala)）
- `totalN` 必须与 OS 列数解耦：[WorkerBeeConfig.scala:218](../scala/main/WorkerBeeConfig.scala) 的 `if (hasMx) require(totalN == 16)` 直接炸；WbOutCvt / C DMA / nEff 全要改
- WS M 上限 48→32（rank3 已核）

增益（N=2048, K=1024）：

| 格式 | OS=1 | OS=2 + B 不变 | OS=2 + B 2× |
|---|---|---|---|
| BF16 | 133,120 | 132,096 ⟹ 1.008× | 66,560 ⟹ **2.000×** |
| MX8 | 67,584 | 66,560 ⟹ 1.015× | 33,792 ⟹ **2.000×** |

那 1% 全部来自 tail+commit 被 2× 列摊薄。

**与 split-K2 相比被严格支配**：同样需要 2× B 带宽，增益略低（2.000× vs 1.966× 看似更高，但 split-K2 不动 C/totalN，代价小得多），且多付 C FIFO + totalN 解耦。⟹ **不立项**。

> 注：用户提出的「复用已有 row1」形态比 rank3 原案（新增第 17 行、+16 PE）便宜：只需把 row1 从 `WbWorker` 换成已是双模的 `WbWorkerOsM1`，+217.588 µm²/PE × 16 = **0.0035 mm²**，而非 +0.116 mm²。这条订正对 split-K2 同样适用。

### 2.2 OS-M_r（扩 M，r 行共享 B）—— 与 rank6 绑定

row1..row_r 喂**同一份 B**、**不同的 A 行** ⟹ 输出 r 行 × 16 列。

**B 流量 = K × N × ceil(M/r)** ⟹ r 行共享使 B 字节除以 r。这是唯一能减少 B 字节的维度。

| r | 用 PE | 占阵列 | M=16 时 B 流量倍数 | A 端口占用 |
|---|---|---|---|---|
| 1 | 16 | 6.2% | 16× | 6% |
| 2 | 32 | 12.5% | 8× | 12% |
| 4 | 64 | 25% | 4× | 25% |
| 8 | 128 | 50% | 2× | 50% |
| 16 | 256 | **100%** | **1×** | **~100%** |

r=16 时恰好用满阵列、A/B 端口同时到 100% —— 自然平衡点。

**限制**：需要 M≥r。decode 是 M=1 ⟹ 对纯 decode 无效。⟹ 与报告 rank6（投机解码 / batch / GQA 打包）绑定，**硬件侧先不立项**，等软件侧 M 分布有实测再评。

### 2.3 split-K2 —— 条件立项（详见 §3–§9）

row1 取 K 前半、row2 取 K 后半，**输出列仍是 16**，末尾合并。

| 格式 | OS=1 | split-K2 + B 不变 | split-K2 + B 2× |
|---|---|---|---|
| BF16 | 133,120 | 133,248 ⟹ **0.999×** | 67,712 ⟹ **1.966×** |
| MX8 | 67,584 | 67,712 ⟹ **0.998×** | 34,944 ⟹ **1.934×** |
| MX4 | 34,816 | 34,944 ⟹ **0.996×** | 18,560 ⟹ **1.876×** |

B 端口不变时**略负**（多出的 1 拍合并开销无处摊薄）⟹ **必须与端口加宽同代际落地**。

---

## 3. split-K2 数据流与坐标映射

### 3.1 语义

对输出列 j ∈ [0,16)、单个 M=1 激活行：

```
C[j] = Σ_{k=0}^{K-1} A[k] × B[k][j]
     = ( Σ_{k=0}^{K/2-1} A[k]×B[k][j] )  +  ( Σ_{k=K/2}^{K-1} A[k]×B[k][j] )
       └──────── row1 的 psum[j] ────────┘    └──────── row2 的 psum[j] ────────┘
```

两行**并行**跑各自 K 半区，各 `length/2 = K/(2×lanes)` 步；结束后逐列合并成一个 40-bit 值，再走**既有**的 16 列 cPush → scratchpad → WbOutCvt → C DMA。

### 3.2 与 OS=1 的逐项对照

| 项 | OS=1 | split-K2 | 是否改动 |
|---|---|---|---|
| 活跃 PE | 16（row0） | 32（row0 + row1） | row1 换双模 PE |
| 输出列数 | 16 | **16** | **不动** |
| 每行步数 | length | length/2 | Sequencer kTiles 减半 |
| A 窗 | 1 个/16 步，广播 | **2 个/16 步**，各半区 | +1 A 通路 |
| B 窗 | 16 个/16 步 | **32 个/16 步** | +1 B 通路 |
| scaleA | 1 字/A 窗 | 2 字/16 步 | +1 通路 |
| scaleB | 16 字/16 步 | 32 字/16 步 | +1 通路 |
| bias 注入 | osSeed 一次 | **仍一次**（只注 row1） | 注入点选择 |
| requant/rqM | WbOutCvt 内 | **不动** | **不动** |
| cPush | 16×40 bit/tile | **16×40 bit/tile** | **不动** |
| C FIFO / WbOutCvt / C DMA / nEff | — | — | **全部不动** |
| `totalN` | 16 | **16** | **不动** |
| WS 模式 | — | — | **不动**（row1 双模 PE 的 WS 臂逐位不变） |

**这张表就是 split-K2 相对 OS=2 的全部优势来源**：C 侧与配置侧零改动。

### 3.3 列 skew 与 tail

两行各自保留 OS 的列延迟（列 j 比列 0 晚 j 拍，[WbComb.scala:314](../scala/main/WbComb.scala) 的 `osLastIn` 横链）。⟹ tail 仍是 T−1 = 15 拍，**不因切 K 而缩短**。合并须等两行的列 j 都 done。

---

## 4. split-K2 供数通路与地址生成

### 4.1 关键发现：第二路只是基址偏移，不需要新地址生成器

现有 OS B 闭式（[WorkerBeeDmaRdOnly.scala:55](../scala/main/WorkerBeeDmaRdOnly.scala)）：
```
addr(nt, kt, j) = base + (nt×T + j)×R + kt×S      j∈[0,T), kt∈[0,kTiles)
R = bRowAddressOffset（N×K 转置 B 的行跨距 = K×elemBytes）
S = bColTileAddressOffset（K 窗步进 = 32 B，所有格式同值）
```
切 K 后：
```
row1: addr = base                    + (nt×T+j)×R + kt×S,  kt∈[0, kTiles/2)
row2: addr = base + (kTiles/2)×S     + (nt×T+j)×R + kt×S,  kt∈[0, kTiles/2)
```
⟹ **row2 = 同一个 `WorkerBeeDmaRdOnly` 模块，只把 `reg_baseAddr` 加上 `(kTiles/2)×S`、`reg_kTile` 传 `kTiles/2`。** R / S / 升序遍历 / osAnchor 递推（[:516-517](../scala/main/WorkerBeeDmaRdOnly.scala)、[:538-539](../scala/main/WorkerBeeDmaRdOnly.scala)）全部原样复用，**零新地址逻辑**。

A 侧同理：OS A 闭式 `addr(nt,kt) = base + kt×C`（C = aColTileOffset = 32 B，与 nt 无关）⟹ row2 的 A 基址 = `aAddr + (kTiles/2)×32`。
scaleA / scaleB 同理，各自按所属通路的 S 偏移半个 K。

### 4.2 四通道增量汇总

| 通道 | OS=1 率 | split-K2 率 | 端口现状 | 需要 |
|---|---|---|---|---|
| B read | 1 推/拍（100%） | 2 推/拍 | 32 B/拍 | **64 B/拍 或 2 端口** |
| B push（DMA→FIFO→feeder） | 1/拍 | 2/拍 | 1/拍 | **通路翻倍** |
| scaleB read | 0.5/拍（pairScale F=2） | 1/拍 | 4 B/拍 | **请求率翻倍** |
| scaleB push | 1/拍（100%） | 2/拍 | 1/拍 | **通路翻倍** |
| A read | 0.0625/拍（6.25%） | 0.125/拍（12.5%） | 32 B/拍 | **有余量，不需加宽** |
| scaleA | 随 A 窗 | 2× | 4 B/拍 | 有余量 |

⟹ **端口加宽的范围是 B + scaleB 两条，A/scaleA 不用动。** 这比"所有端口翻倍"小一半，是 split-K2 的一个实质便利。

### 4.3 kTiles 整除约束

`kTiles = K/(16×lanes)`。split-K2 要求 kTiles 偶：
- MX8 / INT8（lanes=2）：kTiles = K/32 ⟹ **K ≡ 0 (mod 64)**
- MX4 / INT4（lanes=4）：kTiles = K/64 ⟹ **K ≡ 0 (mod 128)**
- BF16（lanes=1）：kTiles = K/16 ⟹ **K ≡ 0 (mod 32)**

decode 的 K 通常是 2 的幂 ⟹ 天然满足。不整除时的处置见 §9 未决问题 Q1。

---

## 5. split-K2 合并机制（核心设计点）

### 5.1 为什么现有硬件装不下

用户设想「row1 的结果给到 row2 做累加」。垂直 psum 链确实存在（[WbComb.scala:301](../scala/main/WbComb.scala) `pe.io.psumIn := pes(i-1)(j).io.psumOut`），但：

1. OS 模式下 row2 的 cReg 选的是**本地 psumReg**（[WbWorkerOsM1.scala:174](../scala/main/WbWorkerOsM1.scala)），垂直链被旁路；
2. 即使接回来也不够：**MAC 每步算 `sum = cReg + products`，只有一个 cReg 槽**（[WbFusionMacInt.scala:76](../scala/main/WbFusionMacInt.scala)、[WbFusionMacFp.scala:341](../scala/main/WbFusionMacFp.scala) 的 `connectFiveSlotAdder`）。合并要的是 `row1_psum + row2_psum`——**两个 40-bit lazy 累加值，都不是乘积**。一步装不下两个。

⟹ 合并必须是**新增硬件**，不能靠改接线。

### 5.2 候选 C1：每列一个 2 输入 lazy-40 加法器（**推荐**）

位置：WbComb 的 OS 抽头之后、Executor cPush 之前。新增模块 `WbOsCombine`（16 列）。

按 [FpAddNarrow.scala:9-45](../scala/main/FpAddNarrow.scala) 的契约实现 2 槽版：
```
解包 x/y → (sign, expEff 9-bit, magW)      // 真零判据 exp==0 && mant31==0
E = max(非零槽 expEff)
a_i = magW_i >> clamp(E − expEff_i, W)     // floor，窗外丢弃
S = ±a_x ± a_y                             // |S| ≤ 2×(2^W−1) < 2^(W+1) ⟹ SInt(W+2)
k = magFull[W]                             // ★2 槽版 k∈{0,1}（5 槽版 k∈[0,3]）
mant31 = Cat(magFull >> k, 0^(31−W))
expOut = clamp(E + k, 254)
magFull == 0 ⟹ 40'h0
```
与 5 槽版的唯一结构差别是 **k 只探 1 位**（`magFull[W]`），因为两个 W-bit 项之和最多 W+1 位。

**实测面积**（`sim/os-splitk-20261117/combine_adder.v`，测量代理、非候选 RTL）：
- `lazy_add2`（W=19）= **795.606 µm²** = 5 槽 `FpAddNarrow`（2180.934）的 **36.5%**
- `combine16` = **13,020.966 µm² = 0.0130 mm² = 0.72% 阵列**

**INT 族必须分流**：INT 的 psum 是 40-bit 二补码、不是 lazy-40 ⟹ 合并是**模 2^40 加法**（截断绕回，与既有 INT 语义一致，见 [WbFusionMacInt.scala:75-77](../scala/main/WbFusionMacInt.scala)）。故 `WbOsCombine` 内需 `Mux(isInt, acc32_add, lazy_add2)`，INT 加法器 16×40-bit（面积远小于 lazy 版，待实测）。

**时序风险低**：合并器只在**每 N tile 一次的 commit 拍**上被采样，不在逐拍 MAC 环路里 ⟹ 即使组合延迟较长也不影响 advance 频率。这是 C1 相对"塞进 MAC"的关键优势。

### 5.3 候选 C2：塞进 row2 MAC 的空闲乘积槽 —— 不推荐

MX8 只激活 {C, L0, L1}，L2/L3 空闲（[FpAddNarrow.scala:53-55](../scala/main/FpAddNarrow.scala)）；BF16 只激活 {C, L0}。理论上可把 row1 的 psum 塞进空闲槽。但：
- **radix 不同**：乘积槽 `mag24 × 2^(exp−150)`，cReg 槽 `mant31 × 2^(exp−157)` ⟹ 需重新打包 row1 的 lazy-40；
- **MX4 五槽全满** ⟹ 对签核的 MXFP4 层**不可用**；
- 把合并放进逐拍 MAC 环路 ⟹ 时序风险高于 C1。

⟹ 格式相关的死路，**不推荐**。

### 5.4 候选 C3：多跑一个合并 pass —— 不可行

设想两行结束后把 row1 的 psum 作为 row2 的 `osSeed` 重注入、再跑 1 步 a=0。但 a=0 ⟹ 乘积 0 ⟹ `sum = cReg = row1_psum`，**row2 自己的 partial 丢失**。没有通路把两个累加值同时送进一个 cReg 槽。⟹ **不可行**。

### 5.5 候选 C4：在 WbOutCvt 里合并 —— 不推荐

把两行 psum 都送进 WbOutCvt，各自 40→32 规格化后在 fp32 域相加。问题：
- 每 tile 要推 **2 行**而不是 1 行 ⟹ **C FIFO 入口带宽翻倍**，直接摧毁 split-K2「C 侧不动」的核心优势；
- 两次规格化 + fp32 加 ⟹ 比 lazy 合并多一次舍入；
- INT 族无 fp32 通路，仍要分流。

⟹ **不推荐**。

### 5.6 供数通路复制的面积账

| 项 | µm² | mm² | % 阵列 | 口径 |
|---|---|---|---|---|
| 第 2 个 Sequencer（feeder + A 窗 + scale 选择） | 81,948.5 | 0.0819 | **4.54%** | 实测 |
| `combine16` | 13,021.0 | 0.0130 | 0.72% | 实测代理 |
| 16 个 PE WS→OS 双模转换 | 3,481.4 | 0.0035 | 0.19% | 实测差 |
| 第 2 个 bDma + bDmaScale | ≥24,464.0 | ≥0.0245 | ≥1.36% | **flop 下界** |
| **逻辑部分小计** | **≥122,914.9** | **≥0.1229** | **≥6.81%** | |
| 第 2 个 B FIFO（64×256 = 2 KB） | 132,359.7 | 0.1324 | 7.34% | ⚠ flop 映射上界 |
| 第 2 个 scaleB FIFO（64×32 = 256 B） | 16,779.3 | 0.0168 | 0.93% | ⚠ flop 映射上界 |
| **含 FIFO 合计** | **≥272,053.9** | **≥0.2721** | **≥15.08%** | 混合口径，不可直接当结论 |

**DMA 面积为何只有下界**：四个 `Wb_WorkerBeeDmaRdOnly*` 变体综合失败——`_0` 报 `Failed to detect width of signal access ..._GEN_16`（嵌套块变量），`_1/_3` 的 `automatic` 是**跨行声明且引用同块兄弟变量**（如 `startCurAddr` 引用 `_startLastB_T`），hoist 守卫正确拒绝。故只按 RTL `reg` 位宽 × nangate45 DFF_X1 单价（4.522 µm²/flop，由 `shadow_now` 13 flop = 58.786 µm² 反推）给下界：`_0` 439 bit / `_1` 4617 bit / `_2` 212 bit / `_3` 793 bit。**真实面积更高**（含地址算术与 gather 移位器），需另行定价。

**可省的项**：
- Sequencer 的 phase FSM 可两行共享（lockstep advance）⟹ 省 ~3,962 µm² 中的一部分（feeder 占 95.2%，省不了大头）。
- B FIFO 深度 64 是为隐藏 DMA 延迟；feeder 本身已有 16 列 × 2 槽 = 32 窗缓冲 ⟹ 第二路 FIFO 深度可评估减半（**需实测 stall 分项支撑，不能凭感觉减**）。
- 若端口加宽采用**单端口 64 B**而非双端口，则 B FIFO 可做成 64×512 bit 单实例（面积 ≈ 2×，与双实例同），但 DMA 只需一个（省 ≥0.0245 mm²）⟹ **单端口加宽优于双端口**，见 Q2。

---

## 6. split-K2 控制与生命周期

### 6.1 不需要改的部分（已核）

| 项 | 依据 | 为什么不动 |
|---|---|---|
| `sOS_SETUP` 拍数 | [WbComb.scala:260](../scala/main/WbComb.scala) `pe.io.loadHIn := io.loadHIn` **全阵列广播** | row1 与 row0 同拍收到 fmt/rnd ⟹ 仍是 totalN 拍横链 |
| `clear` | 全阵列广播 | 两行同拍清 psum/done |
| `osSeed` 通路 | [WorkerBeeExecutor.scala:366](../scala/main/WorkerBeeExecutor.scala) | 只需把 row2 的 seed 钉 0 |
| bias 收集 / rqM staging | [WorkerBeeExecutor.scala:697-709](../scala/main/WorkerBeeExecutor.scala) | 每 tile 一次，与行数无关 |
| cPush / cStoreGate / sOS_WAITC | [WorkerBeeExecutor.scala:685-724](../scala/main/WorkerBeeExecutor.scala) | 仍 16 列一次 commit |
| WS 全通路 | row1 双模 PE 的 WS 臂 | `WbWorkerOsM1` 的 WS 语义与 `WbWorker` 逐位同构（S4 已验） |

### 6.2 需要改的部分

1. **bias 只注一次**：`osSeed` 对 row1 = `biasLazy(j)`、对 row2 = `0.U`。若两行都注 bias ⟹ **bias 被加两次**，静默算错。这是本方案最容易踩的坑，必须加断言。
2. **advance 同步**：kTiles 偶时两行步数相同 ⟹ 可共用一个 advance。但 `allHeads` 须是**两行 32 列**的 headValid 全与（[WbOsM1Sequencer.scala:86](../scala/main/WbOsM1Sequencer.scala) 现为 16 列）。
3. **osDone / resultsReady**：须两行的列 j 都 done 才允许合并与 commit。`osLastIn` 横链每行独立。
4. **kTiles 传值**：`io.osKTiles` 从 `kTiles+1` 改为 `kTiles/2+1`（[WorkerBeeExecutor.scala:365](../scala/main/WorkerBeeExecutor.scala)）。
5. **第 2 路 DMA 启动/完成**：`calcConfig.io.startOut` 需同时启动两路；`doneTiles` 推进仍按逻辑 kTiles 计（不是半区）。
6. **scaleB 字内偏移 `bOff(j)`**：[WbOsM1Sequencer.scala:151-157](../scala/main/WbOsM1Sequencer.scala) 的 `q = (j×kTiles + ktLocal)(1,0)` 依赖 **kTiles 与 headKt 的全局计数**。row2 的 ktLocal 起点偏移了 kTiles/2 ⟹ **off 必须按 row2 自己的全局 kt 算，不能复用 row1 的**。这与 rank1 的 `headKt(j)` 教训同源（OS 列 skew 使 per-column 索引必须用本列槽头）。**这是第二个容易静默算错的点。**

### 6.3 建议的新增断言（英文 ASCII）

```
assert(!(osM1 && splitK && bothRowsSeedEn), "WbOsCombine: bias must be seeded into exactly one row")
assert(!splitK || (kTiles % 2 == 0), "WorkerBeeExecutor: split-K2 requires even kTiles")
assert(!osM1 || !splitK || (row1Done.asUInt.andR && row2Done.asUInt.andR) || !commit,
       "WbOsCombine: commit requires both K halves done")
```

---

## 7. split-K2 精度契约

### 7.1 INT 族：逐位一致（可证）

整数累加是模 2^40 加法，**满足结合律** ⟹ 切 K 后合并与单链累加**逐位相同**。与 [spec.md:477](spec.md) 已有的「INT8/INT4/INT W4A8 逐位一致」一致，不新增违约。

### 7.2 FP/MX 族：不保证逐位一致，但可能更准

- 两条链各长 `length/2` ⟹ 公共指数 E 增长更慢 ⟹ 窗口 LSB `2^(E−126−W)` 更小 ⟹ 每步 floor 截断更少；
- 代价是合并多一次窗口归约（2 槽版 k∈{0,1}）；
- **净效果需按 [FpAddNarrow.scala:34-39](../scala/main/FpAddNarrow.scala) 的绝对界重推**，本文不断言"更准"。

绝对界形式（待重推）：
```
|diff| ≤ (n_lossy_chain1 + n_lossy_chain2 + n_lossy_combine + 2^k_total − 1) × 2^(E_final−126−W)
```
注意 `E_final` 是合并时的公共指数，可能大于任一链的末态 E。

[spec.md:479](spec.md) 已声明 MXFP8/MXFP4/MX W4A8/FP8 在 OS/WS 间不保证逐位一致 ⟹ split-K 不新增规格违约，但 §5.2.9 的对照表需扩一列。

### 7.3 BF16：未决

[spec.md:478](spec.md) 声明 BF16 在 OS/WS 间**逐位一致**，但**该行没有给机制**（INT 给了模运算论证，BF16 没给）。BF16 只激活 {C, L0} 两槽、W≥16 时乘积对齐前零丢失，但两槽和仍可能 k=1 丢 1 bit ⟹ 结合律是否成立**需要论证**。

⟹ **split-K2 落地前必须先闭合这条**（属报告 rank8 范围）。若论证不成立，BF16 的 split-K 结果会与 OS=1 不同，§5.2.9 那一行要降级。**这是前置取证 P1，不是可选项。**

---

## 8. split-K2 改动面清单（逐文件）

| 文件 | 改动 | 规模 |
|---|---|---|
| `WbOsCombine.scala` | **新增**：16 列 2 槽 lazy 加法器 + INT 模加分流 + fmt mux | ~80 行 |
| `WbComb.scala` | row1 改 `WbWorkerOsM1`；新增 row1 的 osB/osScaleB/osSeed/osAdvance/osLast 端口面；`osPsum` 改取合并器输出 | ~40 行 |
| `WbOsM1Sequencer.scala` | 支持双行：`allHeads` 扩到 32 列、`bOff(j)` 按行独立全局 kt、kTiles 半区 | ~30 行 |
| `WbOsM1Feeder.scala` | **不改**（例化两份即可） | 0 |
| `WorkerBee.scala` | 第 2 路 bDma/bDmaScale/aDma/aDmaScale 例化与基址偏移；第 2 组 FIFO；合并器接线 | ~60 行 |
| `WorkerBeeScratchpad.scala` | 第 2 组 B/scaleB FIFO（或单路加宽） | ~25 行 |
| `WorkerBeeExecutor.scala` | `osKTiles` 半区、row2 seed 钉 0、两行 done 与门、splitK 使能与 kTiles 偶守护 | ~35 行 |
| `WorkerBeeConfig.scala` | 新增 `osSplitK` 编译期使能（**不是运行期配置项**，见 Q3） | ~5 行 |
| `WorkerBeeCalcConfig.scala` | 半区 kTiles 派生、第 2 路基址偏移 | ~15 行 |
| `spec.md` | §5.2.9 扩 split-K 列、§5.2.5 补合并归约契约、新增 split-K 布局与 K 对齐约束 | 文档 |
| TB（`src/workerbee/scala/test`） | 金锚按张量语义推导切半求和；地址序列两套；新增 split-K 用例 | ~200 行 |

**不动**：`WbWorker.scala`、`WbWorkUnit.scala`、`WbFusionMacFp/Int.scala`、`FpAddNarrow.scala`、`WbOutCvt.scala`、`WorkerBeeDmaWrOnly.scala`、`WbRequant.scala`、`StreamFifo`（utils，禁改）。

---

## 9. 不变式、禁止事项、前置取证、验收、未决

### 9.1 共同不变式

- I1：**WS 模式逐位不变**。row1 换双模 PE 后，WS 臂必须与 `WbWorker` 逐位同构（S4 已建立该等价，须重验）。
- I2：**C 侧逐位不变**（16 列、同序、同 WbOutCvt、同 C DMA 地址模型）。
- I3：**bias 恰好注入一次**。
- I4：**INT 族 split-K 与 OS=1 逐位一致**（模加结合律）。
- I5：`totalN == 16` 与 `arrayN == arrayM` 两条 require **不动**。
- I6：B/scaleB 的 OS 升序闭式**不动**，第 2 路只改基址与 kTile 数。
- I7：`headKt(j)` 语义不动——每列仍用**本列槽头**的全局 kt，row2 不得复用 row1 的计数。

### 9.2 禁止事项

- P1：**不得**把合并塞进逐拍 MAC 环路（C2/C3 已否决）。
- P2：**不得**让 C FIFO 入口带宽翻倍（C4 已否决，那会摧毁本方案的核心优势）。
- P3：**不得**两行都注 bias。
- P4：**不得**在 kTiles 奇数时静默切半（须守护或回退，见 Q1）。
- P5：**不得**把 FIFO 的 flop 映射面积当真实 SRAM 面积报（§1.4 警告）。
- P6：**不得**把面积比当省电比（本仓无 SAIF/FSDB、无 STA）。
- P7：**不得**改 `StreamFifo`（utils 禁改）、不恢复已删锚文件/取证文件。
- P8：硬件 assert/require 消息英文 ASCII；测试消息中文。
- P9：金锚由张量语义推导，**禁止以 RTL 行为为拟合目标**。

### 9.3 前置取证

| # | 项 | 状态 | 阻塞什么 |
|---|---|---|---|
| P0 | OS=1 端口占用闭式与不可约性 | ✅ §1.1–1.3 | — |
| P1 | **BF16 两槽加法的结合律**（spec §5.2.9 该行无机制） | ❌ **未闭合** | **阻塞 BF16 的 split-K 正确性声明** |
| P2 | OS=1 实测总拍 vs 逻辑下界的 gap（stallA/stallB 分项） | ❌ 日志随 `build/` 清空丢失 | 决定"不动端口还能回收多少" |
| P3 | 合并器 INT 分流面积 | ❌ 未测 | 面积账完整性 |
| P4 | DMA 真实面积（yosys 前端在嵌套块变量上失败） | ❌ 只有 flop 下界 | 面积账完整性 |
| P5 | 第 2 路 B FIFO 深度可否减半 | ❌ 需 P2 的 stall 分项支撑 | 面积优化 |
| P6 | 端口加宽的 SoC 侧可行性与代际排期 | ❌ **用户已表态"未来可能"，但无具体代际** | **整个方案的前置** |

### 9.4 验收标准

| # | 项 | 判据 |
|---|---|---|
| V1 | 全量回归 | 20 suite / 265 test 全绿（`-J-Xmx10G`，绝对 tmpdir）；split-K 关闭时**既有用例金锚一个不改** |
| V2 | WS 逐位不变 | row1 换双模 PE 后，所有 WS 用例金锚逐位相同 |
| V3 | INT split-K 逐位一致 | 同形状同数据，splitK=1 与 splitK=0 的 INT 结果**逐位相同** |
| V4 | FP/MX 金锚 | 按张量语义独立推导切半求和，**不得拟合 RTL**；误差须落在 §7.2 重推的绝对界内 |
| V5 | 周期律 | splitK=1 且 B 端口 2× 时，实测 `cyc ≈ nTiles×(length/2 + T + 1)`；B 端口不变时须实测确认 ≈1×（不是假设） |
| V6 | 地址序列 | 两路 B/scaleB/A/scaleA 的 `req.addr` 硬断言逐拍对账（含 row2 基址偏移与 `bOff(j)`） |
| V7 | bias 单次注入 | 负向用例：两行都 seed 时断言触发 |
| V8 | kTiles 奇数守护 | 负向用例：奇数 kTiles + splitK 时 errCode/断言触发，不静默算错 |
| V9 | RTL 改动面审计 | 重发射默认 RTL，逐文件比对；`WbWorker/WbWorkUnit/WbFusionMac*/FpAddNarrow/WbOutCvt/DmaWrOnly` 须逐位相同（寄存器名+宽度集合比对，不用行 diff） |
| V10 | 面积增量核实 | 重跑综合，报**实测**增量而非套用 §5.6 预测；FIFO 项按 flop 映射口径单独列 |

### 9.5 未决问题（需用户裁定）

- **Q1：kTiles 奇数怎么办？** 三个选项：(a) 软件保证 K 对齐 + errCode 守护（最简，decode 的 K 通常是 2 的幂）；(b) row1 取 ceil、row2 取 floor，两行 advance 不再 lockstep，合并等慢行（复杂度上升、收益略降）；(c) 奇数时静默回退 OS=1（**违反 P4，不推荐**）。**倾向 (a)**。
- **Q2：端口加宽形态——单端口 64 B 还是双端口 32 B？** 单端口可省一个 bDma+bDmaScale（≥0.0245 mm²）与一套控制，但 FIFO 宽度翻倍（面积同）；双端口反之。**倾向单端口**，但这是 SoC 存储接口决策，需与 P6 一起定。
- **Q3：`osSplitK` 是编译期参数还是运行期寄存器位？** 本文按**编译期参数**写（与 `useNarrowAdd`/`pairStretch` 同范式），因为它决定例化几路 DMA/FIFO/Sequencer，是硬件结构而非运行期模式。若要做成运行期可切，则两路硬件必须常例化 ⟹ 面积全额付、OS=1 时白占。**倾向编译期**，但这意味着 split-K 是一个 SKU 选项。
- **Q4：split-K_r（r>2）要不要一并设计？** r=4 需 4× B 带宽、4 路供数、3 级合并树。逻辑面积近似线性增长，收益上界仍是"带宽倍数"。**倾向 v1 只做 r=2**，把合并器做成可级联的 2 输入单元，r=4 留作后续。
- **Q5：是否先跑 P2 实测？** 若 OS=1 实测总拍远高于逻辑下界（说明有大量 stall），则"不动端口先回收 stall"可能比 split-K 更划算，优先级要重排。**建议先跑 P2**。

---

## 10. 与既有路线图的关系

| 报告项 | 关系 |
|---|---|
| **rank3（C32）** | 被 split-K2 **严格支配**（同带宽需求、C/totalN 不动、代价更低）⟹ 建议把 rank3 的状态从"降级为条件项"改为"**被 split-K2 取代**"，条件项本体转移到 split-K2 |
| **rank6（抬 M）** | OS-M_r 的前置。两者互补：rank6 减少 B 字节，split-K 提高 B 速率 |
| **rank8（精度契约）** | **P1（BF16 结合律）是 split-K 的硬前置** ⟹ rank8 从"可独立推进"升级为"split-K 的前置" |
| **rank4（WS 双缓冲）** | 完全解耦。split-K 只动 OS 通路，WS 臂逐位不变（I1） |
| **rank7（操作数隔离）** | 正交。split-K 让 OS 模式下活跃 PE 从 16 变 32 ⟹ 空转 PE 从 240 变 224，rank7 的收益面略减 |
| **MX4 部署** | M=1 下唯一**已生效**的带宽优化：B 字节减半 ⟹ 逻辑下界 67,584→34,816 = **1.941×**。split-K2 与它**可叠加**（MX4 + split-K2 + 2× 端口 = 相对 MX8/OS=1 的 3.64×） |

---

## 11. 变更边界声明

- **未改任何 RTL**（`src/workerbee/scala/main` 零改动）。
- **未改任何测试**（`src/workerbee/scala/test` 零改动）。
- **未改 `spec.md`**（§9.4 V10 与 §10 的文档同步是落地后的动作，不是本轮）。
- 本轮新增文件：本文 + `sim/os-splitk-20261117/`（`combine_adder.v` 测量代理、`run.sh`、逐 top `.ys`/`.log`）。全部在 `sim/` 临时目录。
- 复用 `sim/rank7-area-20261116/` 的 `hoist.py` / `closure.py` 与 `rtl_hoisted/` 测量副本；**交付 RTL 零改动**。
- **未做任何 git 写操作。**