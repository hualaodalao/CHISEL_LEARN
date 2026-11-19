# rank7 操作数隔离（operand isolation）改动方案评估

**状态：只出方案，未动任何 RTL。** 待裁定后才编码。
**日期：2026-11-16**
**范围：** `src/workerbee`。不涉及 foragerbee / llc / gingercat / utils。

---

## 0. 一句话结论

原 rank7 写的「aPop/bPop 零拍 clock-enable」是术语混淆：本 MAC 数据通路**纯组合、无寄存器可门**，RTL 层能写的只有 **enable / 操作数隔离**。真正的浪费不是零操作数，而是**两个格式族被无条件同时供数**——签核部署（MXFP8/MXFP4 W/A 全量化）下整个 INT SIMD MAC 在白翻转。推荐**方案 A：非对称隔离，只掩 INT 族的 `a` + `cReg`（56 bit/PE）**，代价 **0.85% PE 面积**，**吞吐增益 0**，且**可证逐位不变**。

---

## 1. 实测基线

### 1.1 面积（yosys + nangate45 typical，2026-11-16）

口径：`synth -top <top> -flatten` → `dfflibmap -liberty` → `abc -liberty` → `opt_clean -purge` → `stat -liberty` 的 **Chip area**（µm²）。
脚本与逐 top 日志：`sim/rank7-area-20261116/`（临时目录）。

> **这是面积代理，不是功耗实测。** 本仓无 SAIF/FSDB、无 STA、无功耗仿真能力。本文所有「收益」都是**可冻住逻辑的规模**，不是省电百分比。

前端绕行：firtool 把组合临时量写成 always 块内 `automatic logic X = expr;`，yosys 前端报 `Invalid nesting of always blocks and/or initializations`。用 `hoist.py` 把这类声明提到模块级 wire，**只写测量副本到 `rtl_hoisted/`，交付 RTL 零改动**。守卫四条：不得已在模块级声明 / 全文件只能被赋值一次 / RHS 只能引用模块级信号 / 不满足则跳过并打印 SKIP。被跳过的 `Wb_WorkerBeeCalcConfig`、`Wb_WorkerBeeRegIf` 都不在待测闭包内。两次不同 hoist 范围下 FP 面积均为 4026.708，一致。

| 层次 | µm² | 构成 |
|---|---|---|
| `Wb_WbFusionMacFp` | 4026.708 | WorkUnit 的 **68.04%** |
| `Wb_WbFusionMacInt` | 1710.646 | **28.90%** |
| 输出路由 + fmt 解码残差 | 180.880 | 3.06% |
| **`Wb_WbWorkUnit`** | **5918.234** | = 上三项之和 |
| `Wb_WbWorker`（WS PE） | 7033.572 | MAC 占 **84.14%**；PE 级状态 1115.338（15.86%） |
| `Wb_WbWorkerOsM1`（首行 PE） | 7251.160 | MAC 占 81.62% |

阵列 = 240 × WS PE + 16 × OS PE = **1.8041 mm²**（只含 PE，不含 feeder / DMA / epilogue / regfile）。
其中 MAC 合计 **1.5151 mm² = 83.98%**。

**空转族规模：**

| 运行格式 | 空转族 | 单 PE 占比 | 全阵列 |
|---|---|---|---|
| MX / BF16（**签核部署**） | INT 族 1710.646 µm² | **24.32%** | **0.4379 mm²** |
| INT8 / INT4 | FP 族 4026.708 µm² | 57.25% | 1.0308 mm² |

### 1.2 掩码单价（实测，五个变体完全线性）

| 变体 | 覆盖 | AND 位 | µm² | µm²/bit |
|---|---|---|---|---|
| `mask_ab` | a/b × 2 族 | 64 | 68.096 | 1.0640 |
| `mask_abc` | +cReg × 2 族 | 144 | 153.216 | 1.0640 |
| `mask_full` | +scaleA/scaleB | 160 | 170.240 | 1.0640 |
| `mask_int_ac` | INT 族 a+cReg | 56 | **59.584** | 1.0640 |
| `mask_fp_ac_sa` | FP 族 a+cReg+scaleA | 64 | 68.096 | 1.0640 |
| `mask_c_only` | 单族 cReg | 40 | **42.560** | 1.0640 |

⟹ 掩码就是纯 AND 平面，**1.0640 µm²/bit**，无意外优化或膨胀。

### 1.3 周期基线（rank4 落地后，265/265 全绿）

`cyc/kTile = max(m+1, L+1=32, max_over_ports(beats_per_kTile(port)/duty))`。
**本方案不触碰任何供数节拍、端口、FIFO 深度或状态机 ⟹ 周期律逐项不变，吞吐增益恒 0。** 这一点必须先说清，不能用「优化」含糊过去。

---

## 2. 承重事实（逐条核过源码）

### F1 — MAC 数据通路纯组合，clock gating 无对象

- `WbWorkUnit.scala` 全文 **零 `Reg`**。
- `WbFusionMacInt.scala:51-54`：4 个 `RegNext(fmt === ...)` 格式影子。
- `WbFusionMacFp.scala:82-93`：12 个声明，交付 RTL 实际存活 **9 个**（`Wb_WbFusionMacFp.sv:101-110`：`aIsFp16`/`bIsFp16`/`bIsMx2M1` 被 DCE）。
- `FpAddNarrow` / `FpMul` 零 Reg。

⟹ 每 WorkUnit 共 **13 个 flop**，全部是单次执行内静态的格式影子。数据通路无寄存器 ⟹ **没有 ICG 可插**。动态功耗全来自操作数输入驱动的组合翻转。

### F2 — 隔离必须做在 WorkUnit 操作数入口，不能做在 `aReg` 使能上

`WbWorker.scala:179` `workUnit.io.a := io.aIn(aW-1,0)` —— 喂 MAC 的是**组合输入**，不是 `aReg`。
`WbWorker.scala:209-216` `aReg` 是**脉动右传寄存器**（`io.aOut := aReg`）。保持 `aReg` 会把上一拍旧值送给右邻 PE ⟹ 算错。

### F3 — 两族被无条件同时供数，输出只留一路

- `WbWorkUnit.scala:67-75`：FP 族 a/b/cReg/scaleA/scaleB/rnd/consumeValid 全部无条件直连。
- `WbWorkUnit.scala:85-89`：INT 族 a/b/cReg 全部无条件直连。
- `WbWorkUnit.scala:101-105`：三级输出 mux，优先级 MX > BF16 > INT > passthrough(cReg)。

交付 RTL 已核实：每个 `Wb_WbWorkUnit` 内两个 MAC 都在（`Wb_WbWorkUnit.sv:19,33`），每 PE 一个 WorkUnit、共 256 个。

### F4 — INT 族的输出门控**不足以**止住翻转（已有反例）

`WbFusionMacInt.scala:69,73`：r2/r3 **已经**有 `Mux(aIsInt4 & bIsInt4, prod, 0.S)`。但交付 RTL `Wb_WbFusionMacInt.sv:68-69` 显示乘法器操作数仍来自 `io_a`/`io_b` ⟹ **乘积被门控，乘法器本身照样翻转**。
同文件 `:65-67` lane0/lane1 乘法器**完全无门控**。

⟹ 「在输出加个 Mux 到 0」这条路已被现存代码证伪，必须掩**操作数**。

### F5 — EXECUTE 期真正翻转的操作数只有 `a` / `cReg` / `scaleA`（**本轮订正**）

| 操作数 | WS PE 来源 | EXECUTE 期是否翻转 |
|---|---|---|
| `a` | `WbWorker.scala:179` 组合 `io.aIn` | **是**，每 valid 拍 |
| `cReg` | `WbWorker.scala:184` `io.psumIn`（垂直部分和链） | **是** |
| `scaleA` | `WbWorker.scala:189` 组合 `io.scaleAIn` | **是**（仅 FP 族有此口） |
| `b` | `WbWorker.scala:183` `Mux(rdSelIn, wRegB, wReg)` | **否** |
| `scaleB` | `WbWorker.scala:190` `Mux(rdSelIn, scaleBRegB, scaleBReg)` | **否** |
| `aFmt`/`bFmt`/`rnd` | `fmtReg`/`bFmtReg`/`rndReg` | **否**，单次执行内静态 |

`b`/`scaleB` 静态的依据：`WbWorker.scala:142-157`，`wReg`/`wRegB`/`scaleBReg`/`scaleBRegB` **只在 lock 拍被写**（`loadVInLock` 或 `loadWInLockSafe`）。rank4 预装载在计算期写的是 **inactive bank**，而 mux 选的是 active bank ⟹ MAC 看到的 `b` 不变。

> **订正记录：** 我上一轮报的 `mask_int_only` = 72 bit / 76.608 µm² 把 `b` 的 16 bit 算进去了。按 F5，那 16 bit 在 240 个 WS PE 上是**重复计费**（掩一个本来就静态的信号）。有效宽度是 **56 bit / 59.584 µm²**。这与 rank4 面积账把已存在的 `scaleBChainReg` 误计为新增是同一类错误。

### F6 — OS 首行 PE 的 `b` / `cReg` 语义不同（**例外，必须单列**）

`WbWorkerOsM1.scala:173-174`：
```
workUnit.io.b    := Mux(io.osM1, io.osB, Mux(io.rdSelIn, wRegB, wReg))
workUnit.io.cReg := Mux(io.osM1, psumReg, io.psumIn)
```
OS 模式下 **B 逐拍流入**（`io.osB` 来自 feeder）、psum 驻留 ⟹ `b` **会翻转**，`cReg` 仍翻转（自身累加）。
⟹ F5 的「b 静态」只对 240 个 WS PE 成立；16 个 OS PE 在 OS 模式下例外。见 §6 的量化否决。

### F7 — 使能信号**已经存在**，不需新增 fmt 解码

`WbWorkUnit.scala:102,105` 的输出 mux 已经在算 `DataFormat.isInt(io.aFmt)` 与 `DataFormat.isMx(io.aFmt)`，`:104` 在算 `io.aFmt === BF16`。
⟹ 隔离使能直接复用这三个表达式，**零新增解码逻辑、零新增寄存器、零新增端口**。

### F8 — 使能是运行期静态信号

`io.aFmt := fmtReg`（`WbWorker.scala:185`），`fmtReg` 只在 `loadHIn` 更新（`:159-163`），单次 GEMM 内恒定。
⟹ 掩码使能无逐拍检测逻辑；但 AND 的**门延迟仍落在数据路径上**（见 §7 风险 R2）。

### F9 — FP 族 `r0` 是唯一无门控的乘积槽

`WbFusionMacFp.scala:331` `r0 = mulL0.io.prod`（无 Mux），而 `:334-336` 的 r1/r2/r3 都有 `Mux(..., 0.U(32.W))`。
⟹ FP 族在 INT 运行时 `r0` 输出的是「INT 字节按 MX 域解码后的乘积」，**非 0、非 cReg**。这条决定了 §6 的输出路由化简候选不能直接成立。

---

## 3. 方案 A —— 改动最小（推荐）

### 机制

在 `WbWorkUnit` 内，对 **INT 族入口**做非对称掩码，掩 `a`(16) + `cReg`(40) = **56 bit**：

```scala
// 使能复用输出 mux 已有的判据（F7），不新增解码
private val enInt: Bool = DataFormat.isInt(io.aFmt)
fusionInt.io.a    := io.a    & Fill(aW,    enInt)
fusionInt.io.cReg := io.cReg & Fill(cEffW, enInt)
fusionInt.io.b    := io.b            // 不掩：WS 期静态（F5）
```

FP 族**完全不动**（签核纯 MX ⟹ FP 恒活跃，掩它纯亏）。

### 为什么掩 `a` + `cReg` 就够

- 掩 `a` → 0：AND 型部分积阵列 `pp = a_bit & b` ⟹ **整个 pp 阵列安静**，与 `b` 无关。
- 掩 `cReg` → 0：`WbFusionMacInt.scala:76` 的 5 操作数加法树（`r0+r1+r2+r3+cReg`）输入全 0 ⟹ **树安静**。
- 两者合起来覆盖 INT 族除 4 个 fmt 影子 flop 与 `b` 侧 Booth 重编码器之外的全部组合逻辑。

### 代价（实测）

| 项 | 值 |
|---|---|
| 掩码面积 | **59.584 µm²/PE** = **0.85% PE** = 1.01% MAC |
| 全阵列 | **0.0153 mm²**（阵列基线 1.8041 mm² 的 0.85%） |
| 新增寄存器 | **0** |
| 新增端口 | **0** |
| 新增 fmt 解码 | **0**（F7 复用） |
| 可冻住逻辑规模 | 1710.646 µm²/PE 的绝大部分 ⟹ 规模比 **28.71×** |
| **吞吐增益** | **0**（周期律逐项不变） |
| 字节增益 | **0**（不碰任何 DMA） |

### 改动面

**单文件**：`src/workerbee/scala/main/WbWorkUnit.scala`，约 4 行（1 个 enInt 定义 + 2 个掩码赋值 + 注释）。
`WbWorker.scala` / `WbWorkerOsM1.scala` / `WbComb.scala` / Executor / DMA / 寄存器 / TB **零改动**。

### 逐位不变性（可证，不是「跑绿了所以对的」）

`enInt` 与输出 mux 的选择子 `DataFormat.isInt(io.aFmt)`（`:102`）**是同一个表达式**：
- `enInt = 1` ⟹ 掩码是恒等变换，`sumInt` 逐位不变；
- `enInt = 0` ⟹ `:102` 的 mux 选 `io.cReg`，`sumInt` **不被任何下游消费**。

⟹ `io.sum` 在所有输入组合下逐位不变。265 个既有用例的金锚**必须全部保持逐位相同**，任何一个数值变化都是实现缺陷，不是「精度改善」。

### 风险

- R2（时序，见 §7）：`a` 侧 AND 落在乘法器输入头部。
- 覆盖不完整：4 个 fmt 影子 flop 与 `b` 侧重编码器仍翻转。前者见方案 C，后者见 §6。

---

## 4. 方案 B —— 面积最小

### 机制

只掩 INT 族的 `cReg`（40 bit），**不掩 `a`**：

```scala
fusionInt.io.cReg := io.cReg & Fill(cEffW, DataFormat.isInt(io.aFmt))
```

### 代价（实测）

| 项 | 值 |
|---|---|
| 掩码面积 | **42.560 µm²/PE** = **0.61% PE** |
| 全阵列 | **0.0109 mm²** |
| 相对 A 省 | 17.024 µm²/PE（**−28.6%** 掩码面积） |
| 吞吐增益 | **0** |

### 覆盖面（诚实说明：不可用面积代理排序）

冻住 5 操作数加法树的 `cReg` 输入，但 **4 个 8×8 乘法器仍随激活翻转**（F4 已证 lane0/lane1 无门控）。
`cReg` 是 40 bit 进加法树、`a` 是 16 bit 进 4 个乘法器——**哪个的动态功耗大，本仓无法测**（无 SAIF/FSDB）。所以 B 的收益上界低于 A，但**低多少不可量化**，不能编一个比例。

### 唯一实质优势：时序

`cReg` 在加法树**尾部**（乘法器之后），掩码插入的门延迟不叠加在乘法器关键路径头部。相比 A 的 `a` 侧掩码，B 对 Fmax 的影响更小。

### 改动面

单文件 `WbWorkUnit.scala`，**1 行**。

### 风险

收益覆盖面明确小于 A，且差额不可测 ⟹ 若最终目标是功耗，B 是在**用一个测不出来的收益损失**换 0.24% PE 面积和一点时序余量。

---

## 5. 方案 C —— 覆盖最大 / 功耗上限最高

> **「最高」指功耗收益上限，不是吞吐。三方案吞吐增益全部为 0，不存在性能差。** 不为凑差异编造吞吐数字。

### C1：双向对称隔离

两族都掩各自翻转的操作数：INT 族 `a`+`cReg` = 56 bit，FP 族 `a`+`cReg`+`scaleA` = 64 bit，共 **120 bit**。

| 项 | 值 |
|---|---|
| 掩码面积 | **127.680 µm²/PE** = **1.82% PE** |
| 全阵列 | **0.0327 mm²** |
| 吞吐增益 | **0** |

**对签核负载的净效果：** FP 族恒活跃 ⟹ 那 64 bit（68.096 µm²/PE，全阵列 0.0174 mm²）**收益恒 0、代价全额**。
⟹ C1 相对 A 在纯 MX 部署下是**严格劣化**：收益不增、代价 +114%。
只有当部署**逐层混用 INT 与 MX** 时 FP 侧掩码才开始付钱。硬件支持逐层混用（errCode 3 只拦单次 GEMM 内跨族），但 G0 签核模型是 MXFP8/MXFP4 W/A 全量化 ⟹ 当前不成立。

### C2：13 个 fmt 影子寄存器改带使能（**唯一真正的 clock-enable 项**）

这是本 MAC 内唯一有时序对象的地方（F1）。现状 `RegNext` 无条件每拍时钟翻转，尽管 `fmt` 静态、`D == Q`。

候选：共享一个 fmt 变化检测（`{aFmt,bFmt} =/= RegNext({aFmt,bFmt})`），13 个影子改 `RegEnable`。

**实测代价（`shadow_now` / `shadow_en`，含 dfflibmap）：**

| 变体 | µm² | flop 数 | 说明 |
|---|---|---|---|
| `shadow_now` | 84.322 | 13 × DFF_X1 | 现状 |
| `shadow_en` | 158.536 | 21 × DFF_X1 | +8 检测 flop，13 个影子各带反馈 mux |
| **增量** | **+74.214 µm²/PE** | | **+1.06% PE**，面积 **+88%** |

**关键限制：yosys 的 `dfflibmap` 把 `RegEnable` 映射成反馈 mux，不是 ICG。** 日志明确 `mapped 21 $_DFF_P_ cells to DFF_X1 cells`，无 CLKGATE 单元。
⟹ 在本仓综合流程下，C2 是**用 +74.214 µm²/PE 的面积去换 13 个 flop 的时钟翻转**，而时钟树功耗节省**无法测量**。要真正拿到 ICG 收益，需要综合工具做 clock-gating 插入（本仓无该能力，也无 STA/功耗仿真验证）。

⟹ **C2 在当前工具链下不可验证收益、代价明确 ⟹ 建议暂缓**，等具备 ASIC 综合 + 功耗仿真能力后再评。

### C 合计

127.680 + 74.214 = **201.894 µm²/PE = 2.87% PE**，全阵列 0.0517 mm²，吞吐增益 0。

---

## 6. 已核价并否决的候选（不留给用户裁决）

### N1 — 掩 `b` / `scaleB`：**否决**

- 240 个 WS PE：`b`/`scaleB` EXECUTE 期静态（F5）⟹ 掩码收益 **0**，代价 16 bit × 1.064 × 240 = 4085.8 µm² = **0.0041 mm²**。
- 16 个 OS PE：OS 模式 `b` 确实翻转（F6），但**掩 `a` 已经让 pp 阵列安静**，掩 `b` 的边际收益只剩 Booth 重编码器；而 `WbWorkUnit` 是 256 个 PE 共享的同一模块，为 16 个 PE 的需求给 256 个都加 16 bit ⟹ 代价 0.0041 mm² 换 16 个 PE 的重编码器安静。
- 全阵列 OS PE 的 `b` 侧可冻规模上界 = 16 bit × 1.064 × 16 = 272.4 µm² = **0.00027 mm²**。

⟹ **代价 0.0041 mm² 对收益上界 0.00027 mm²，差 15×，方向反了。否决。**
（若将来确要做，正确形态是给 `WbWorkUnit` 加一个编译期 `maskB` 参数、只 OS PE 置真，代价是 firtool 去重出两个 WorkUnit 变体。当前不立项。）

### N2 — 输出路由化简抵面积：**否决（净收益 0.13% PE，不值风险）**

思路：若两族在 inactive 时输出恒 0，则三级优先级 mux 可塌成 `Mux(fmtOk, sumFpMx | sumInt, cReg)`。

**实测定价：**

| 模块 | µm² |
|---|---|
| `route_now`（现三级 mux，abc 已合并同选 sFp 的两级） | 149.758 |
| `route_or`（40-bit OR + 1 级 mux） | 106.400 |
| 毛节省 | **43.358** |
| `gate_r0`（32-bit 乘积门控，F9 必需） | **+34.048** |
| **净节省** | **9.310 µm²/PE = 0.132% PE** |

而且还欠一条**未证的不变式**：`FpAddNarrow(cReg=0, 四路乘积全 0)` 的输出必须逐位等于 0（延迟规格化累加器在全零输入下可能产出偏置伪码）。

⟹ 净收益 0.13% PE，代价是改 FP 乘积槽 + 证一条累加器全零不变式。**不立项。**

### N3 — 零操作数检测（原 rank7 本体）：**降为次级，不立项**

- 打包 lane（INT8 双 lane、INT4/MXFP4 四 lane）要**全部打包元素为零**才算 lane 零 ⟹ 概率 **p²/p⁴**，不是元素稀疏度 p。
- 真零本身已让 AND 型 pp 阵列安静 ⟹ 可省的主要是 Booth 重编码 / scale 乘 / 格式解码，不是整个乘法器。
- 需要逐拍零检测逻辑（16-bit NOR + 打包分组），而格式族隔离的使能是**静态**的、零检测逻辑。

⟹ 报告引用的 **−4.5% 外部锚对本仓打包格式偏乐观**；另一处 −12% 是 vectorless FPGA 估计。**在格式族隔离落地前不立项。**

### N4 — 编译期删掉 INT 族（SKU 化）：**不是技术优化，是产品决策**

若部署确定只跑 MX，把 INT8/INT4 从 `supportedFmts` 移除 ⟹ 面积 **−1710.646 µm²/PE（−24.32% PE，全阵列 −0.4379 mm²）**，代价 0，INT 族功耗省 100%。
但这**改变功能**（不再支持 INT8/INT4），与已批准的「默认全格式 + 运行期 `isMx` 门控」裁定冲突，属报告 rank11「格式集 SKU 化」。
⟹ **本方案不推荐、不擅自执行**，仅作为「隔离的收益上界」参照列出：掩码花 0.85% PE 去逼近删硬件的 24.32% PE，这是隔离能达到的天花板。

---

## 7. 三方案对比

| | **A 改动最小** | **B 面积最小** | **C 覆盖最大** |
|---|---|---|---|
| 掩码位宽/PE | 56（INT a+cReg） | 40（INT cReg） | 120（双族）+ 13 flop 使能 |
| 面积 µm²/PE | **59.584** | **42.560** | **201.894** |
| % of PE | **0.85%** | **0.61%** | **2.87%** |
| 全阵列 mm² | 0.0153 | 0.0109 | 0.0517 |
| 新增 flop | 0 | 0 | +8/PE（检测） |
| 改动文件数 | **1** | **1** | 3+（WorkUnit / FusionMacFp / FusionMacInt） |
| 改动行数 | ~4 | **1** | ~30 |
| 冻住 INT 乘法器 | **是** | 否 | 是 |
| 冻住 INT 加法树 | **是** | **是** | 是 |
| 冻住 FP 族（INT 运行时） | 否 | 否 | 是（**签核下零收益**） |
| fmt 影子时钟 | 仍翻转 | 仍翻转 | 停（**收益不可测、面积 +88%**） |
| **吞吐增益** | **0** | **0** | **0** |
| 逐位不变可证 | **是** | **是** | C1 是；C2 需证 fmt 变化检测无漏拍 |
| 时序风险 | `a` 侧 AND 在乘法器头部 | **最低**（加法树尾） | 最高（两族 + 检测路径） |
| 签核负载净评价 | **推荐** | 收益缺口不可测 | **严格劣化**（+114% 代价、收益不增） |

---

## 8. 推荐与理由

**推荐方案 A。**

1. **对签核负载收益最大 / 代价最小。** 签核是纯 MX ⟹ 只有 INT 族空转。A 恰好只掩 INT 族，规模比 **28.71×**（59.584 换 1710.646）；C 的对称掩码有一半纯亏，B 放弃乘法器侧。
2. **代价可证、收益机制可证。** 掩 `a` 杀 pp 阵列、掩 `cReg` 杀加法树，两条都是结构论证，不依赖功耗仿真。而「省了多少电」本仓测不了——A 的价值在于**它是唯一能靠结构证明浪费被消除的形态**。
3. **逐位不变可证**（§3），265 个金锚必须逐位不动 ⟹ 回归是**强验收**，不是「跑绿就行」。
4. **改动面 1 文件 4 行、零新增 flop / 端口 / 解码**（F7 复用），是本项目历史上最小的一次功能改动面。
5. **不碰任何时序逻辑、供数节拍、端口或状态机** ⟹ 与 rank4 双缓冲、rank2/2b 的 DMA 复用完全解耦，周期律逐项不变。

**不推荐 B**：省 0.24% PE，但放弃的收益份额**不可测**——用测不出来的损失换测得出的小面积，方向不对。若后续 STA 显示 A 的 `a` 侧 AND 确实吃掉不可接受的 Fmax 余量，B 是**现成的降级路径**（改 1 行）。

**不推荐 C**：C1 对签核严格劣化；C2 在本仓工具链下映射成反馈 mux 而非 ICG，代价 +74.214 µm²/PE 明确、收益不可测 ⟹ 暂缓至具备 ASIC 综合 + 功耗仿真能力。

---

## 9. 共同不变式与禁止事项

**不变式（三方案都必须成立）**

- I1：`io.sum` 在所有输入组合下**逐位不变**。掩码使能必须与输出 mux 的选择子**同源同表达式**，不得另写一个「差不多」的判据。
- I2：`aReg` / `scaleAReg` / `psumReg` / `rdSelReg` 等 PE 级脉动寄存器的使能**一律不动**（F2）。
- I3：`b` / `scaleB` 的 bank mux 与 lock 拍锁存时序**一律不动**（F5）。
- I4：周期律 `max(m+1, 32, max_port(beats/duty))` 逐项不变；任何 `cyc/kTile` 变化都是缺陷。
- I5：`consumeValid` 语义不变（它门控的是 inf/nan assert，不是数据通路）。
- I6：编译期 `if (hasInt)` / `if (hasMx)` 分支结构不变——路径不存在时仍直接短接，不得生成 dead mask。

**禁止事项**

- P1：**不得**声称任何省电百分比。本仓无 SAIF/FSDB，面积比不是功耗比。
- P2：**不得**提「PE clock gating」——数据通路无寄存器（F1）。
- P3：**不得**保持 `aReg` 来省翻转（破脉动传播，F2）。
- P4：**不得**用输出门控代替操作数掩码（F4 已有反例）。
- P5：**不得**掩 `b` / `scaleB`（N1 已量化否决）。
- P6：**不得**顺手做输出路由化简（N2 净收益 0.13% PE，且欠一条未证不变式）。
- P7：**不得**擅自删减 `supportedFmts`（N4 是产品决策）。
- P8：硬件 `assert`/`require` 消息纯英文 ASCII；测试消息中文。
- P9：`sim/` 只放临时文件，`build/` 只放报告与日志；不恢复已删的锚文件/取证文件。

---

## 10. 前置取证

**A 方案的前置取证已全部闭合**，无待跑项：

| # | 取证 | 状态 |
|---|---|---|
| 0 | MAC 是否纯组合（有无 ICG 对象） | ✅ F1，源码 + 交付 RTL reg 清单 |
| 1 | 两族是否无条件供数 | ✅ F3，源码 + `Wb_WbWorkUnit.sv:19,33` |
| 2 | 输出门控是否已足够 | ✅ F4，`Wb_WbFusionMacInt.sv:65-69` 证伪 |
| 3 | EXECUTE 期哪些操作数真翻转 | ✅ F5/F6，lock 拍写条件 + OS mux |
| 4 | 使能是否需新增解码 | ✅ F7，输出 mux 已有 |
| 5 | 两族面积占比 | ✅ §1.1，八个 top 全 rc=0 |
| 6 | 掩码单价与有效宽度 | ✅ §1.2，六个变体线性 1.0640 µm²/bit |
| 7 | `b` 掩码是否值得 | ✅ N1，代价/收益差 15×，否决 |
| 8 | 路由化简能否抵面积 | ✅ N2，净 0.13% PE，否决 |
| 9 | fmt 影子使能代价 | ✅ C2，+74.214 µm²/PE，映射为反馈 mux 非 ICG |

**唯一未闭合项（不阻塞 A 的实施，阻塞「收益报数」）：**

- **U1：`a` 侧 AND 的 Fmax 代价。** 本仓无 STA。BF16 集成已有 **−5.6% Fmax** 的前例 ⟹ 不能当零成本。实施后若要把这项写进规格，需要 STA 能力；在那之前只能记「未量化」。
- **U2：动态功耗节省份额。** 需 SAIF/FSDB + 功耗仿真。⟹ 落地后**只报「已隔离的组合逻辑规模 1710.646 µm²/PE（24.32% PE）」与「掩码代价 59.584 µm²/PE（0.85% PE）」**，不报省电百分比（与 rank2 只报端口读字节 −41.7%、不声称能耗百分比同一纪律）。

---

## 11. 验收标准

| # | 项 | 判据 |
|---|---|---|
| V1 | 全量回归 | **20 suite / 265 test 全绿**（`-J-Xmx10G`，绝对 tmpdir） |
| V2 | **金锚逐位不变** | 所有既有用例的期望值**一个都不许改**。任何数值变化 ⟹ 实现缺陷，回退 |
| V3 | 周期不变 | Phase0Spec 全部 `cyc/kTile` 断言原样通过；B/B2/B3/B5/B6/B7 组不改一个常数 |
| V4 | RTL 改动面审计 | 重发射默认 RTL，逐文件比对：**只允许 `Wb_WbWorkUnit.sv` 变化**，其余文件逐位相同（寄存器名+宽度集合比对，不用行 diff） |
| V5 | 面积增量核实 | 重跑 `Wb_WbWorkUnit` / `Wb_WbWorker` / `Wb_WbWorkerOsM1` 综合，增量应为 **+59.584 µm²/PE ±**（abc 重映射可能有小幅偏差，需报实测值而非套用预测） |
| V6 | 掩码有效性结构证明 | 在交付 RTL 中确认 INT 族乘法器操作数来自掩码后的 wire，且 `enInt` 与输出 mux 选择子是同一节点 |
| V7 | 编译期分支 | `hasInt=false` 配置下不生成任何掩码节点（I6）；`hasMx=false` 配置仍可构造 |
| V8 | 文档同步 | `spec.md` 记录隔离机制与「吞吐增益 0」；`DEEP_RESEARCH_workerbee_optimization.md` §11.3 rank7 行回写状态与实测数 |

---

## 12. 未决问题（需用户裁定）

1. **是否批准方案 A 进入实施？**（本文只出方案，未动 RTL）
2. **U1 的处置**：`a` 侧 AND 的 Fmax 代价本仓测不了。是接受「未量化」并实施，还是先要求具备 STA 能力再做？若倾向保守，B 是 1 行的降级路径。
3. **是否保留 C2 作为独立后续项**（fmt 影子 clock-enable），等 ASIC 综合 + 功耗仿真能力就绪再评？本文建议暂缓。
4. **部署是否会逐层混用 INT 与 MX？** 若会，C1 的 FP 侧 64 bit 掩码开始付钱，A 需要升级为对称形态。当前按 G0 签核（MXFP8/MXFP4 W/A 全量化）判为不会。

---

## 13. 变更边界声明

- **未改任何 RTL**（`src/workerbee/scala/main` 零改动）。
- **未改任何测试**（`src/workerbee/scala/test` 零改动）。
- 本轮新增文件：本文 + `sim/rank7-area-20261116/`（脚本 `hoist.py`/`closure.py`/`run_area.sh`/`run_mask2.sh`/`run_mask3.sh`/`run_shadow.sh`、`mask_variants.v`、测量副本 `rtl_hoisted/`、逐 top `.ys`/`.log`）。全部在 `sim/` 临时目录。
- **未做任何 git 写操作。**