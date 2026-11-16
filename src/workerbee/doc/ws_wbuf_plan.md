# rank4：WS（M>1）权重双缓冲 — 三方案改动评估

**状态**：**方案 A 已选定（用户裁定 2026-11-13，并按用户原意订正 wr_sel/rd_sel 语义）**，仍未实施、未动功能 RTL。B/C 评估保留作决策记录。
**日期**：2026-11-13
**范围**：只 cover WorkerBee 本身。外部真实 DRAM 时序模型暂缓（沿用阶段0 裁定）。
**目标负载**：MXFP8 / MXFP4（W/A 全量化），故所有增益以 MX 实测为准，不搬 BF16 数字。
**装载模式范围（用户裁定）**：**双缓冲只在 loadWMode=1 生效**；loadWMode=0 走原路、逐拍不变，**未来预计移除**（见 §12）。故 A 不为垂直模式做任何双支持设计。

> **本轮关键更新（2026-11-13）**：kTile 间回灌依赖下界 **L = 31 拍已实测钉死**（§3.1/§3.5），
> 且它是**正确性下界、无硬件守护**（§2 事实 14）。据此 **方案 C 的 4–5× 作废**。
>
> **⚠️ 方案 A 已按用户原意订正（§4.1 修正 1 / §4.7）**：用户提案的 **`wr_sel` 广播全局锁存 + `rd_sel` 随 A 口激活经 skew 逐 PE 进入**
> 本来就是**对角线切换**。本文档曾错误地把两者收敛成「1 个全局 bankSel」，那会迫使 `P ≥ m+30`、增益只剩 1.08×（§4.7.2 保留为反例）。
> **按用户原意（rd_sel 随 skew）：`P ≥ max(m+1, 31, 16/duty)`，M=8 1.326× / M=16 1.584× / M=32 ≈1.97×。**
> 事实 11「边界阵列已静默」已否证，但**它不影响用户方案**——写侧只动 inactive bank、读侧由 rd_sel 逐 PE 对齐，两侧都不需要阵列静默。

---

## 1. 实测基线（已钉死，非推导）

来源：`WorkerBeePhase0Spec` B2/B3/B4/**B5** 组，2026-11-13，**12/12 全绿**，`SBT_EXIT=0`（`build/phase0-b5-wavefront.log`）。
配置：blk16、loadW=1、N=512、K=512、M∈{1,2,4,8,16}、cFmt=FP32、理想存储（duty=1）。
数据落盘：`build/phase0-20261111/measurements.md`（32 行 + B5 回灌下界小节，A/C 组未被覆写）。

| 格式（WS） | 斜率 | 截距 | M=8 | M=16 | sB读/kTile | predPort | array/kTile |
|---|---|---|---|---|---|---|---|
| BF16（参照） | 1.0000 | 33.000 | 41.048 | 49.063 | 0 | 16.00 | = m |
| **MX8×MX8** | 1.0008 | **33.107** | 41.104 | 49.119 | 8 | 16.00 | = m |
| **MX4×MX4** | 1.0031 | **33.180** | 41.215 | 49.230 | 16 | 16.00 | = m |

**稳态边际**（M=8，K512→K1024 差分消固定常数）：**MX8×MX8 与 MX4×MX4 同为 40.9922 拍/kTile**。

三条结论：
1. **rank4 收益格式无关**：三格式同落 `33+m` 律，截距差仅 0.107 / 0.180 拍。
2. **scaleB 在 WS 下不是瓶颈**：`predPort` 三者同为 16.00（阵列下界绑定），8/16 读全部藏进既有窗口。
3. **`array/kTile` 精确等于 m**（三格式 × 五 M 点全部）⟹ 非阵列开销 = 33.1 拍，是双缓冲的可隐藏池。

> **被否证的旧假设**：曾推测「MX 强制 loadWMode=1、预蓄倒计数 31 拍 ⟹ 截距更大、增益更低」。实测否证，根因见 §2 事实 6。另 BF16 的 33+m 本来也是 loadW=1 路测的（`params` 硬编码 `loadWMode=true`），原「垂直加载路」表述有误。

---

## 2. 承重结构事实（逐条已核源码）

| # | 事实 | 出处 |
|---|---|---|
| 1 | **LOAD_B 不可能短于 16 拍**：B buffer 读口 = `totalN×bW` = 256 bit = 一行 16 权重/拍，16 行需 16 拍。除非加宽 B 读口，否则**唯一杠杆是重叠**，不是缩短。 | `WorkerBeeExecutor.scala:456`/`:480`（bPop.payload 切 totalN 片） |
| 2 | **bPop 在 sLOAD_A / sDRAIN 期间完全空闲**：LOAD_A 只用 aPop/cPop/scaleAPop，DRAIN 不用任何 pop ⟹ 预装载**无端口竞争**。 | `WorkerBeeExecutor.scala:493-552`、`:665-691` |
| 3 | **加载通道与计算通道物理复用**：垂直模式权重经 `wbPsumIn` → `psumReg` 透传；水平 loadW 模式权重经 `wbAIn` → `aReg` 移位。 | `WbWorker.scala:164-165`（psumReg loadV 透传）、`:179`（aReg loadWIn 移位） |
| 4 | **DRAIN 期间 a 链与 psum 链都被占用**：`validOut := RegNext(validIn)` 使尾部 valid 脉冲继续传播，psum 链正在排空波前 ⟹ **不能借用现有链装载，影子路径是必需的**。 | `WbWorker.scala:194`、`WbComb.scala:252`/`:269` |
| 5 | **DRAIN 的 15 拍 = 波前排空深度，不是可缩短的盲等**：`drainNextCnt := totalN-1` = 15 = 阵列 16 行的对角线深度；hasNext 分支只查计数与 occupancy，不查 `wbValidOut` ⟹ 它是固定 flush 计时器。 | `WorkerBeeExecutor.scala:558`/`:568`/`:668-681` |
| 6 | **loadWMode 的 31 拍重载是死代码**：`:677` 写 `2*(totalN-1)+1`=31，但下一轮 `sNEXT_NK_TILE`（`:558`/`:568`）无条件写回 15 ⟹ 31 永不被用作倒数。**这就是 MX 与 BF16 截距相同的根因。** | `WorkerBeeExecutor.scala:677` vs `:558`/`:568` |
| 7 | **阵列拓扑**：i = 行 = K-lane 轴（arrayN=16），j = 列 = 输出 N 轴（arrayM=16）。横链(j)传 valid/fmt/rnd/bFmt/a/scaleA/scaleB/loadW；纵链(i)传 psum/validV/loadV；clear/loadHIn/loadVInLock/loadWInLock **全阵列广播**。 | `WbComb.scala:226-272` |
| 8 | **边界 skew 寄存器已存在**：`aSkewedPerRow`(aEffW×i)、`bSkewedPerRow`(cEffW×i)、`scaleASkewedPerRow`(8×i)，i=1..15 共 120 级；loadW/loadV 期有旁路直连。影子路径可复用同一旁路结构。 | `WbComb.scala:154-222`（旁路 `:172`/`:196`/`:218`） |
| 9 | **OS 硬守护**：`assert(!io.osM1 \|\| !(io.loadVIn \|\| io.loadVInLock \|\| loadWInSafe \|\| loadWInLockSafe))` ⟹ OS 态所有权重装载信号必须恒低。**任何预装载信号必须同样门控**，否则断言即炸。 | `WbComb.scala:287` |
| 10 | **两种 PE 都要加 bank**：`WbWorker` 与 `WbWorkerOsM1` 各有独立 `wReg`(bW=16) 与 `scaleBReg`(8)，共 256 个 PE（首行 16 个 OS PE 已在 256 内）。 | `WbWorker.scala:114`/`:105`；`WbWorkerOsM1.scala:117`/`:110`；`WbComb.scala:121-123` |
| 11 | ~~**bankSel 全局广播切换是安全的**：sDRAIN→sLOAD_B 边界阵列已静默~~ **⚠️ 本条已否证（2026-11-13 二次核源码）**：该边界阵列**并未静默**，LOAD_B 起拍 `T_last+18` 时波前还要跑到 `T_last+30`（12 拍重叠）。现设计安全的真实原因是**逐 PE 追赶余量**：W 装载脉冲到 PE(i,j) 于 `T_last+18+j`，激活波前过同一 PE 于 `T_last+i+j`（i≤15）⟹ 余量 `18−i ≥ 3`；且 wReg 只在 lock 拍 `T_last+33 > T_last+30` 被覆盖。**全局 bankSel 不能据此提前翻转**，详见 §4.7。 | `WbComb.scala:136-150`（无条件 valid skew）、`:252`/`:268`；`WorkerBeeExecutor.scala:558`/`:668-681`/`:486` |
| 12 | **C 读口是单口 FIFO**：`StreamFifo(UInt(totalN*cEffW), cBufferDepth)`，LOAD_A 期用于 partial-sum 回灌 ⟹ 方案 C 的连续流水会与结果提取**竞争同一读口**。 | `WorkerBeeScratchpad.scala:46`；`WorkerBeeExecutor.scala:520`/`:529` |
| 13 | **scaleB 必须与 wReg 严格同拍锁存**：错拍会让阵列深层排空的上一块乘积尾持旧权重读到新块 scaleB → 高 (m+n) 对角错位。**双 bank 必须把 scaleBRegB 与 wRegB 绑成同一锁存事件。** | `WbWorker.scala:132-137` |
| 14 | **回灌下界是正确性下界，且无硬件守护**：`feed` 与 `io.cPop.ready := feed & needPartialSum` **都不查 `cPop.valid`**；`feedBase` 的 `cBufferReservedAtleastTotalN` 查的是 C buffer **余量** ≥ totalN（depth=2048 ⟹ 恒真），`cStoreGateReg` 只在 N-tile 末 K pass 拉高（kTile 间恒 false）。cPop 侧**没有任何 assert**（只有 cPush 侧 `:298` 的 pop-before-push）。⟹ 周期压到 L 以下**不会停等、不会报错，而是静默回灌未就绪的 partial sum**。 | `WorkerBeeExecutor.scala:498`/`:503-520`；`WorkerBeeScratchpad.scala:71`；全仓 grep `cPop` 无 assert |
| 15 | **行级波前延迟 L = 2·totalN − 1 = 31 拍（已实测）**：纵向 `validV` 链 arrayN−1=15 拍 + 末列 j=15 的横向 skew 15 拍 + 输出寄存 1 拍。deskew 能对齐是因为底行各列 raw valid 本身错开 16 拍 ⟹ `cOutShiftEn` 连续高 16 拍，刚好把列 0 推过 15 级。 | `WbComb.scala:264`/`:268`/`:296`/`:315`、`WbWorker.scala:195`；实测见 §3.5 |

### 2.1 现有 PE 状态 flop 预算（面积对照基准）

仅 `psumReg`(cEffW=40) + `wReg`(16) + `scaleBReg`(8) 三项：`(40+16+8)×256 = 16384 bit = 2048 B`。
（另有 aReg/scaleAReg/fmtReg/rndReg/bFmtReg 与 MAC 组合逻辑，未计入此基准。）

---

## 3. 周期预算与可隐藏窗口

现周期律（实测吻合）：

```
16 (sLOAD_B) + m (sLOAD_A) + 1 (sNEXT_NK_TILE) + 16 (sDRAIN: 15 倒数 + 1 转移) = 33 + m
```

### 3.1 ⚠️ 关键订正（2026-11-13）：16 拍 LOAD_B 不是纯浪费，它正在吸收波前排空

**原「可隐藏窗口 = m+17 ⟹ 全隐藏 16 拍 ⟹ 17+m」的推导忽略了 kTile 间的数据依赖。**

已核事实链：
1. **`io.cPush.valid := io.wbValidOut`（`Executor:296`）是连续赋值、不受状态门控** ⟹ 结果从底行一冒出就推进 C buffer，**在 DRAIN 期间、也在下一个 LOAD_B 期间**。
2. **`needPartialSum = curKTile > 0`（`:494`）⟹ 下一 kTile 的 LOAD_A 从 cPop 读回上一 kTile 结果作 psum 基底**（`:520`/`:529`）⟹ **C buffer 是 kTile 间的 partial-sum 回灌通道**，不是只在任务末尾写回。
3. 输出 deskew（`WbComb:299-314`，列 j 延迟 `arrayM-1-j` = 15−j）+ 波前到达时刻 `i+1+j`（`WbComb:133-134`）⟹ 激活行 r 到达底行 PE(15,j) 于 `r+16+j`，deskew 补 `15−j` ⟹ **对齐输出于 r+31**。
4. 下一 kTile 的 LOAD_A 在自己第 r 拍读 row r ⟹ 回灌拍 = `T + r`，而该行结果入 FIFO 于 `r + 31` ⟹ **T ≥ 31，r 与 m 全部消掉**。

⟹ **kTile 间存在精确 `2*totalN − 1` = 31 拍的数据依赖下界，与 M 无关（已实测，§3.5）。**
现周期 33+M 对所有实测 M 均 > 31 ⟹ **当前是 FSM 在绑定，依赖被藏住了**；那 16 拍 LOAD_B 正在承担排空。

**⚠️ 「去掉 sDRAIN」的失效模式不是停等，而是静默算错**（原写「会卡在 C buffer 占用门、表现为 stallA 上升」，**已核源码否证**）：
`feedBase` 的 `cBufferReservedAtleastTotalN` 查的是**余量** ≥ 16，而 `cBufferDepth=2048` ⟹ 恒真；`cStoreGateReg` 在 kTile 间恒 false；**`feed` 与 `cPop.ready` 都不查 `cPop.valid`，cPop 侧也没有 assert**（事实 14）。
⟹ 周期一旦压到 31 以下，硬件**照常 fire、照常算**，只是 psum 基底取到未就绪的行。**31 拍是正确性下界，不是性能下界**，且**没有任何硬件守护会替你拦住它**。A 必须自带 `cPop.valid` 门控或保留足够延迟（§4.5 陷阱 4）。

### 3.2 订正后的通用周期律（31 已实测）

```
新拍/kTile = max(切换下界, 2*totalN − 1, 16 / duty)
```

- **回灌下界 `2*totalN−1 = 31`**：与方案无关，**已实测**（§3.1/§3.5）。
- **切换下界取决于 `rd_sel` 的传播方式**（§4.7）：**随 A 口 skew（方案 A，用户原意）⟹ `m+1`**；全局广播（反例）⟹ `m+30`。
- **`16/duty`**：B 端口受压项（§3.3）。

**方案 A：`max(m+1, 31, 16/duty)`**

| M | 现基线（MX8 实测） | ~~原估 17+m~~ | **A 后** | **增益** |
|---|---|---|---|---|
| 1 | 34.107 | ~~18~~ | **31** | **1.100×** |
| 2 | 35.092 | ~~19~~ | **31** | **1.132×** |
| 4 | 37.096 | ~~21~~ | **31** | **1.197×** |
| 8 | 41.104 | ~~25~~ | **31** | **1.326×**（原称 1.640×，**高估 24%**） |
| 16 | 49.119 | ~~33~~ | **31** | **1.584×** |
| 30 | ≈63（外推） | ~~47~~ | **31** | **≈2.03×（峰值）** |
| 32 | ≈65（外推） | ~~49~~ | **33** | ≈1.97× |
| 48 | ≈81（外推） | ~~65~~ | **49** | ≈1.65× |

**增益随 M 单调上升到 M≈30 的 ≈2.03×，之后缓慢下降**（`m+1` 超过 31 后重新主导）。
⟹ **最值钱区间 M=16~32**，正好覆盖 YOLO11n/s 视觉（M≫1）与 LLM prefill / 多 token verify；**不是原称的 M=2~8**。
**M ≤ 30 时增益被 L 钉在 31 拍分母上**，随 M 减小而下降 ⟹ **M=1 只有 1.10×，rank4 对 decode 几乎无价值**（decode 仍由 OS-M1 的 ≈17.04 拍覆盖）。

### 3.3 带宽受压项（duty 定义见 §3.4）

| duty | 16/duty | M=1 | M=4 | M=8 | M=16 |
|---|---|---|---|---|---|
| 1.0（理想，bp=0） | 16 | 31 | 31 | 31 | 31 |
| 2/3（bp=3） | 24 | 31 | 31 | 31 | 31 |
| 0.6（bp=5） | 26.7 | 31 | 31 | 31 | 31 |
| 4/7（bp=7） | 28.0 | 31 | 31 | 31 | 31 |
| **0.5（bp=4）** | **32** | **32** | **32** | **32** | **32** |

⟹ **31 拍依赖下界在常见 duty 下反而是主绑定项**，带宽项只在 duty < 16/31 ≈ 0.52 时才超过它。
**duty(bp) 非单调**（整数除法，见 §8 取证 1）：bp=4 是 duty 的最小值点 0.5，也是**唯一让带宽项反超 31 的档位**；bp=3/5/7 都超不过。

### 3.4 duty 的定义（TB 端口级节流，非真实 DRAM 模型）

`QuickSpec:1070`：`bpLow(ch) = bpPeriod > 0 && floorMod(cycle + 5*ch, bpPeriod) < (bpPeriod+2)/3`
⟹ bpPeriod>0 时把 **A/B/C/scaleA/scaleB 五通道的 `req.ready` 周期性拉低**，每 bp 拍拉低 `(bp+2)/3` 拍，各通道相位错开 5 拍。
`duty = (bp − (bp+2)/3) / bp`：bp=0→1.0（理想存储、零延迟、每拍可收请求）；bp=3→2/3；bp=5→0.6；bp=7→4/7。
**这是端口级有效带宽占空比**，物理含义 = 内存/互连不可能每拍都收一个请求；**不是**真实 DRAM 时序模型（后者按用户裁定暂缓）。

### 3.5 证据状态：**已闭合（2026-11-13 实测）**

**观测实现**（纯附加、零新状态，符合既有 perf 架构裁定「只拉活动电平到端口、计数在外部」）：

| 层 | 改动 |
|---|---|
| RTL | `WbInterface.scala` 的 `WorkerBeePerf` 增 `cRowIn`；`WorkerBee.scala` 接 `io.perf.cRowIn := executor.io.cPush.valid`（**共 2 行，无寄存器**） |
| TB | `WorkerBeeQuickSpec` 的 `perfNames`/`perfPeek` 增 `cRowIn`；主循环额外记 `array`/`cRowIn` 两条**稀疏流的事件拍号**，经 `TaskOutcome.perfEv` 暴露 |
| 用例 | `WorkerBeePhase0Spec` **B5 组**：MX8 M=1 / MX8 M=8 / MX4 M=8，逐下标相减求 L |

**实测结果**（`build/phase0-b5-wavefront.log:627-629`，Phase0Spec **12/12 全绿**、`SBT_EXIT=0`）：

| 用例 | L min | L max | 样本数 | kTile 边界数 | 边界空档众数 |
|---|---|---|---|---|---|
| MX8×MX8 M=1 | **31** | **31** | 512 | 511 | 34 |
| MX8×MX8 M=8 | **31** | **31** | 4096 | 511 | 34 |
| MX4×MX4 M=8 | **31** | **31** | 2048 | 255 | 34 |

⟹ **L = 31 精确成立，6656 个样本零例外**，跨 m∈{1,8}、跨 MX8/MX4 恒定，与源码闭式 `2*totalN−1` 逐位吻合。

**口径提醒（易错）**：「边界空档众数 34」**不是周期 T**，而是「上一 kTile 末行 → 下一 kTile 首行」的空档 = `T − (m−1)`。
M=1 → T=34；M=8 → T=34+7=**41**，与 `cyc/kTile` 实测 41.104 一致。B5 已按此换算并断言 `T ≥ L`。

**抽头无扰动的证据**：加 `cRowIn` 后 B2/B3 的周期数字**逐位复现**（MX8 截距 33.107、MX4 截距 33.180、斜率 1.0008/1.0031、`array/kTile` 仍精确 = m）。

**已作废的旧口径**：原 1.640× 沿用「减去 16 拍 LOAD_B」模型，该模型忽略 LOAD_B 正在承担排空 ⟹ **作废**。
**仍待取证**：duty<1 的实测退化（§8 取证 1）；A 原型上的 `stallA`/`cPop` 守护行为（§4.5 陷阱 4）。

---

## 4. 方案 A（**已选定，按用户原意订正**）— 专用 W 装载口 + 双 bank + wr_sel 广播 / rd_sel 随 skew

> **用户提案（2026-11-13）**：① 新增一个与 A 口一致的 W 装载口，逐列装载，带 `weight_sel` 选写入哪个 weight 寄存器；② 每 PE 增一个 weight 寄存器与 weight scale 寄存器；③ **A 口也多一个 `weight_sel`**，选使用哪个 weight/scale 进行计算。
> **用户澄清（同日）**：①的 wr_sel 是**广播全局锁存**；③的 rd_sel 是**跟随 A 口数据一起经 skew 进来**。
> 本节是该提案的**精化落地形态**，含 4 处必要修正与 5 个正确性陷阱。**修正 1 曾把 wr_sel/rd_sel 误收敛为 1 个全局 bankSel，已按用户原意订正**（§4.7 给出该误写的代价反例）。

### 4.0 范围裁定

- **双缓冲只在 loadWMode=1 生效**。loadWMode=0（垂直 loadV）走原路：`wr_sel`/`rd_sel` 恒 0、W 口空闲、预装载控制器不启动。
- **loadWMode=0 未来预计移除**（§12）⟹ A **不为垂直模式做双支持**：W 口只有水平移位一套，**无横/纵输入 mux**。
- mode 0 用例（BF16/INT8/INT4 默认走 mode 0，`QuickSpec:757`）必须**逐拍不变**；RTL 审计应显示 mode 0 路径零结构 diff。

### 4.1 机制（含 4 处修正）

**修正 1 — wr_sel 与 rd_sel 必须解耦，且 rd_sel 必须随 A 口 skew 逐 PE 进入（用户原意，本文档曾写错）**

提案点 1 的 `weight_sel` 选**写入目标**、点 3 的 `weight_sel` 选**计算源**；**若共用一根线，写 B 时就无法用 A 算，双缓冲失效**。用户裁定的落地形态是**两根独立的线，时序语义不同**：

| 信号 | 传播方式 | 时序 | 作用 |
|---|---|---|---|
| **`wr_sel`** | **全局广播**，与 W 链 lock 脉冲同拍 | 所有 PE **同一拍**锁存 | 选写入 `wReg`/`scaleBReg` 还是 `wRegB`/`scaleBRegB` |
| **`rd_sel`** | **随 A 口激活经 skew 进入**：行 skew 延迟 i + 横链延迟 j | PE(i,j) 在 `F+i+j` 各自翻转 | 选 MAC 读哪一组 weight/scale |

**为什么写侧可以全局广播**：写只动 **inactive bank**，而 in-flight 波前读的是 active bank ⟹ **写与读天然不冲突，不需要阵列静默**。W 链移位寄存器（`wChainReg`/`scaleBChainReg`）也与两个 bank 都独立 ⟹ 预装载可在 LOAD_A/DRAIN 任意时刻进行。

**为什么读侧必须随 skew**：阵列在 tile 边界**并未静默**（事实 11 已否证，§4.7.1）。PE(i,j) 处理 tile t 末行于 `T_last+i+j`、处理 tile t+1 首行于 `T_first(t+1)+i+j`。`rd_sel` 经与激活**完全相同的 i+j 延迟**后，每个 PE 恰在自己末次旧权重读取之后、首次新权重读取之前翻转 ⟹ **i+j 在两侧约束中消掉**，得 `P ≥ m+1`（§4.7.3）。
**若误做成全局广播 rd_sel**，翻转必须晚于**最慢**的 PE(15,15) ⟹ `P ≥ m+30`，增益塌到 1.08×（§4.7.2 反例）。

**实现上 rd_sel 就是 A 口上的一个 1-bit 随行字段**，与既有 `fmt`/`rnd`/`bFmt`/`scaleA` 同一传播结构（`WbComb:249-256` 横链 + `:136-150` 行 skew）。
⚠️ **对齐要求**：MAC 的 bank mux 选择子必须与 **`validIn` 同拍**（即用 `rdSelIn`，不是再寄存一拍的 `rdSelReg`）。`fmtReg` 之所以能用寄存值是因为它单次执行内静态（见记忆 `workerbee-afmt-bfmt-fusionmac-fmt-regnext`）；**`rd_sel` 每 tile 都变，错一拍就是整块权重用错 bank**。

成本：**wr_sel 1 bit 全局 + rd_sel 1 bit/PE（256 bit = 32 B）+ 行 skew 15 bit**。

**修正 2 — W 口是移位链，不是数据广播**
现 loadW 的真实机制：每 beat 向**左列(j=0)**注入 16 个值（每行 i 一个）→ 每拍右移一列 → 末拍 lock 脉冲**广播**，16 beat 后对角线落位。
W 口必须复制**同一移位链结构**：每 PE 一个链数据寄存器 + `RegNext` 右传 + 广播 lock。
⚠️ 若误做成数据广播（同值送所有列），装载需 **256 拍而非 16 拍**，收益全失。「逐列广播」中的"广播"只指 **lock 脉冲**，不是数据。

**修正 3 — W 链必须带自己的 scale 移位链（提案点 2 漏算）**
scaleB 现有**独立 8-bit 移位寄存器**：`io.scaleBOut := RegNext(io.scaleBIn)`（`WbWorker.scala:192`），与 a 链 lockstep、lock 拍捕获。故 W 口每 PE 需要 **4 个寄存器**，不是 2 个：

| 寄存器 | 位宽 | 作用 |
|---|---|---|
| `wChainReg` | bW=16 | W 链数据移位元素 |
| `scaleBChainReg` | 8 | W 链 scale 移位元素（与数据 lockstep） |
| `wRegB` | bW=16 | bank B 权重驻留 |
| `scaleBRegB` | 8 | bank B scale 驻留 |

**`wRegB` 与 `scaleBRegB` 必须绑同一锁存事件**（W-lock 拍），沿用现有 `scaleBReg`/`wReg` 同拍锁存约束（`WbWorker.scala:132-137`）；否则深层 PE 排空的上一块乘积尾会持旧权重读到新块 scale → 高 (m+n) 对角错位。

**修正 4 — A 口从此纯激活（净简化）**
现状：`aReg` 使能 = `loadWIn | validIn`（`WbWorker.scala:179`），mode 1 下 `wbAIn` 在 sLOAD_B 期承载**权重**（`Executor:480`）。A 后：
- `aReg` 使能只剩 `validIn`；`wbAIn` 恒承载激活
- `aSkewedPerRow` / `scaleASkewedPerRow` 的 loadW 旁路（`WbComb.scala:172`/`:196`）不再需要
- `Executor:474-489` 的 loadWMode 覆盖块改为驱动 W 口
- 现有 `loadWIn/loadWOut/loadWInLock` 信号组由 W 口接管；`loadVInLock` 与 `loadWInLock` 的互斥断言（`WbWorker.scala:126`）须相应重述

### 4.2 面积（**四次订正：复用既有 scaleB 移位链 ⟹ 1312 B**）

```
每 PE 41 bit（新增）= wChainReg16 + wRegB16 + scaleBRegB8 + rdSelReg1
  × 256 = 10496 bit = 1312 B
+ 全局 wr_sel 1 bit + rd_sel 行 skew 15 bit
+ MAC.b 2:1 mux(16bit) × 256、MAC.scaleB 2:1 mux(8bit) × 256
```

相对 §2.1 基准 2048 B ⟹ **+64%**。

> **⚠️ 四次订正（2026-11-16 实施前核源码）：`scaleBChainReg` 不是新增寄存器，原 1568 B 高估 256 B。**
> `io.scaleBOut := RegNext(io.scaleBIn)`（`WbWorker.scala:192`、`WbWorkerOsM1.scala:191`）**本来就是**每 PE 一个 8-bit 移位寄存器；`WbComb.scala:246` 在 j==0 直连注入、`:255` 在 j>0 接左邻 `scaleBOut` ⟹ **已经是一条与 a 链同深、lockstep 的 per-column 移位链**，且只在 lock 拍被捕获进 `scaleBReg`（`:135-137`）、**计算期 don't-care**。
> ⟹ W 装载的 scale **直接复用这条既有链**（`scaleBIn/scaleBOut`），不需要新建 `scaleBChainReg`。预装载在计算期推进该链也不会扰动在途计算（计算读的是驻留 `scaleBReg`/`scaleBRegB`，不是链）。
> 同理 **W 链的移位脉冲控制复用既有 `loadWIn/loadWOut/loadWInLock`**（`WbWorker:197` 的 `loadWOut` flop 已存在）⟹ 原「+W 链 shift/lock 传播 flop 1 bit×256」一项也**不成立**，无需新增。
> 代价数字历经四次订正：① 原「每 PE ~8 Kbit」错约 340×（wReg 只有 16 bit）；② 一次订正 768 B / 1312 B 被指「漏算 W 链 scale 移位寄存器」；③ 三次订正补 rd_sel 1 bit/PE 得 1568 B；④ **本次核源码发现②的指责本身有误——该寄存器已存在**，扣除 8 bit/PE 后回到 **1312 B**（rd_sel 的 1 bit/PE 仍计入）。
> 对比方案 B 的 800 B：A 多 512 B，但 B 需新建 256-bit 广播总线 + 16 行选通（RTL churn 更大、**无 STA 时序未知**）。**B 若要有同等增益也必须加 rd_sel 随 skew（+32 B ⟹ 832 B）**，否则同样塌到 1.08×。

> **⑤ 五次订正（2026-11-16 重发射后按 reg 声明集合实测）：总计 1316.75 B。**
> 1312 B 只是 PE 阵列部分。实测另有两项（见 `build/rtl-audit-rank4.md` §2.1）：
> - WbComb rd_sel 行 skew **15 bit**（不是 16：`VecInit(fill(16))` 的元素 15 从未被读，firtool DCE 掉）；
> - Executor 预装载 FSM **23 bit** = `preKTile`16 + `preInterCnt`4 + `preActive`/`preloadDone`/`rdSelReg` 各 1。
>
> 合计 10496 + 15 + 23 = **10534 bit = 1316.75 B**，相对 2048 B 基线 **+64.3%**。顶层 perf 抽头实测零新增状态。

### 4.3 周期与增益（**按用户原意 rd_sel 随 skew；L=31 已实测**）

新周期律 **`max(m + 1, 31, 16/duty)`**：
- **`m+1`** = 对角线 rd_sel 的切换下界（§4.7.3，i+j 消掉）；
- **`31`** = kTile 间经 C buffer 回灌 psum 的波前依赖下界（§3.1，**已实测**），同时也是写侧 bank 复用下界（§4.7.3）；
- **`16/duty`** = B 端口受压项（§3.3）。

| M | MX8 现基线（实测） | ~~原估 17+m~~ | ~~全局 rd_sel 反例 m+30~~ | **A（rd_sel 随 skew）** | **增益** |
|---|---|---|---|---|---|
| 1 | 34.107 | ~~18~~ | ~~31~~ | **31** | **1.100×** |
| 4 | 37.096 | ~~21~~ | ~~34~~ | **31** | **1.197×** |
| 8 | 41.104 | ~~25~~ | ~~38~~ | **31** | **1.326×** |
| 16 | 49.119 | ~~33~~ | ~~46~~ | **31** | **1.584×** |
| 30 | ≈63（外推） | ~~47~~ | ~~60~~ | **31** | **≈2.03×（峰值）** |
| 32 | ≈65（外推） | ~~49~~ | ~~62~~ | **33** | ≈1.97× |
| 48 | ≈81（外推） | ~~65~~ | ~~78~~ | **49** | ≈1.65× |

**增益随 M 单调上升到 M≈30 的 ≈2.03×，之后缓慢下降**（`m+1` 超过 31 后重新主导）。
⟹ **最值钱区间是 M=16~32**，正好覆盖 **YOLO11n/s 视觉（M≫1）与 LLM prefill / 多 token verify**。**decode M=1 只有 1.100×**，仍由 OS-M1（≈17.04 拍）覆盖 ⟹ rank4 不服务 M=1。
W 口宽度与 A 口一致（totalN×bW = 256 bit/拍）⟹ 16 行仍需 16 拍（事实 1 不变），但**这 16 拍完全重叠进 LOAD_A/DRAIN，不再占用周期**。

**证据等级**：`31` 为**RTL 仿真实测**（§3.5，6656 样本零例外）；`m+1` 与写侧 bank 复用为**结构推导**（锚定实测 L=31 的波前时序），须在原型上按 §4.7.6 复测；duty 项**尚未实测**（§8 取证 1）。⟹ 本表可作立项依据，但**增益是下界模型的期望值、不是原型实测值**，写进收益承诺时须带此标注。

### 4.4 改动面

| 文件 | 改动 |
|---|---|
| `WbWorker.scala` | +5 寄存器（48 bit 双 bank/链 + 1 bit rdSelReg）+ W 口端口 + `rdSelIn/Out` + 2 个 MAC 侧 mux（**选择子用 `rdSelIn`，陷阱 5**）；`aReg` 使能去掉 `loadWIn`；锁存事件绑定；互斥断言重述 |
| `WbWorkerOsM1.scala` | 同上（OS 态 W 口空闲、`wr_sel`/`rdSelIn` 恒 0） |
| `WbComb.scala` | W 链横链接线（j 向）+ `wrSel`/`wLoadInLock` 广播 + **rd_sel 行 skew 链（复制 `:136-140`）+ 横链** + W 口顶层端口；**OS 断言 `:287` 扩展覆盖 W 口信号**；去掉 a/scaleA 的 loadW 旁路 |
| `WorkerBeeExecutor.scala` | 预装载 FSM + **独立 `preKTile`/`preInterCnt`** + bPop 仲裁 + 跳过 sLOAD_B + **`wrSel` 窗口翻面 / `rdSelIn` 电平在 `T_last+1` 后翻面** + 首 tile bootstrap + **`feed` 并入 `cPop.valid`（陷阱 4）** |
| `WorkerBeeCalcConfig.scala` | 预装载 tile 索引所需的 scaleB 偏移参数（若需新增运行期量） |
| `WbInterface.scala` | 若顶层端口面变动（`wrSel`/`rdSelIn` 为内部信号，不外露） |

> 逐信号级清单（含端口名、寄存器名、锁存式、接线规则）见 **§4.7.5**。

### 4.5 五个正确性陷阱（必须显式处理）

**陷阱 1（最高优先）— scaleBOff 依赖当前 tile 计数器，预装载必须有自己的计数器**
`scaleBQ = (tileInterCnt * (kTiles + 1.U) + curKTile)(1,0)`（`Executor:203`），`idx = (lane*step)/blk + scaleBOff`（`:209`）。
预装载 tile i+1 时 `curKTile`/`tileInterCnt` 指的是 **tile i** ⟹ **scale 字节偏移会错，静默算错**。
⟹ 预装载路径必须有独立的 `preKTile`/`preInterCnt`，`scaleBOff` 由它们重算。这是 rank2b 同字复用引入的新耦合。
**验收必须含 MX blk16 与 blk32 两档**（demand=2/1，off 序列不同）**且含非零 B 延迟用例**。

**陷阱 2 — bPop 仲裁 + 首 tile bootstrap**
- bPop 在 sLOAD_A/sDRAIN 空闲（事实 2）⟹ 预装载无端口竞争
- **首 tile 无前序可重叠** ⟹ 需一次完整初始装载窗口（bootstrap），之后才进入重叠稳态
- 预装载控制器在 LOAD_A/DRAIN 驱动 `bPop.ready`；后续 tile **跳过 sLOAD_B**
- B FIFO 预蓄门控（`bOccupancy >= totalN`，MX 另需 `scaleBOcc >= totalN`）须为预装载窗口重新评估
- **「bPop 严格逐拍」不变式在预装载窗口同样必须成立**（既有 loadW 预取节奏不变式）

**陷阱 3 — OS 隔离**
`WbComb.scala:287` 断言要求 OS 态所有权重装载信号恒低。新增 W 口信号（shift/lock/data/scale/wrSel）**必须加进该断言**，且 OS 态 W 口空闲、`wr_sel`/`rdSelIn` 恒 0（OS 权重经 feeder 流式供给、不驻留）。

**陷阱 4（本轮取证产出）— 回灌就绪没有任何硬件守护，A 必须自己加**
事实 14：`feed`/`cPop.ready` 不查 `cPop.valid`，cPop 侧无 assert。现设计之所以安全，**完全靠 FSM 的 33+m ≥ 34 > 31 这个巧合余量**（M=1 时余量只有 3 拍）。
A 把周期压到 `max(m+1, 31)` 后，**M ≤ 30 时余量归零**：任何额外的供数抖动（scaleA 未就绪、bPop 节奏变化、DRAIN 缩短）都会让回灌拍早于结果入 FIFO 拍 ⟹ **静默数值错，且现有断言一条都不会响**。
⟹ A 必须二选一（推荐前者）：
- **把 `cPop.valid` 并入 `feed`**（`needPartialSum` 时），让硬件在基底未就绪时真正停等。代价：`stallA` 会开始计入这段等待，**性能表现为停等而非算错**，可观测、可验收；且这会让 §3.2 的 `max(...)` 从「设计者必须自己保证的约束」变成「硬件自动维持的下界」。
- 或保留 ≥31 拍的显式延迟并加 assert 守护（`needPartialSum && aPop.fire |-> cPop.valid`）。
**验收必须含一条把周期人为压到 31 以下的负向用例**，证明守护真的会拦（否则等于没加）。

**陷阱 5（本轮订正产出）— rd_sel 必须与 validIn 同拍，且必须随 skew 而非全局广播**
两条独立的错法，都是**静默数值错**：
- **错法 A：把 rd_sel 做成全局广播** ⟹ 切换下界变成 `m+30`，增益从 1.326× 塌到 1.082×@M=8（§4.7.2）。这是**性能错**，不会算错但收益归零。
- **错法 B：MAC mux 用 `rdSelReg`（比 `validIn` 晚一拍）而非 `rdSelIn`** ⟹ 每个 PE 在消费激活的那一拍读到**上一个 tile 的 bank**，整块权重错配。`fmtReg` 之所以能用寄存值是因为它单次执行内静态（记忆 `workerbee-afmt-bfmt-fusionmac-fmt-regnext`）；**rd_sel 每 tile 都变，不可套用该结论**。
⟹ 取证：加 `perf.rdSel00`/`perf.rdSel1515` 两抽头，测跳变拍差 = 30，且各自落在本 PE 的两 tile 激活空隙内（§4.7.6 第 2 项）。**这是 A 相对全局广播方案的唯一新增取证项，必须做。**

### 4.6 风险

**中**。
有利点：**写侧只动 inactive bank ⟹ 不需要阵列静默**；**读侧 rd_sel 复用既有 A 口 skew/横链拓扑 ⟹ 不需要新建对角线计数器**；W 口与 A 口同构；**mode 0 完全不受影响 ⟹ 回归面收窄**。
主要风险：陷阱 1（scale 偏移）、陷阱 2（预蓄节奏）、陷阱 4（回灌守护）都是**静默数值错**而非死锁，必须靠金锚对拍与非零延迟用例捕获；**新增陷阱 5（rd_sel 与 validIn 的对齐）**同样是静默错（错一拍 = 整块权重用错 bank）。

### 4.7 rd_sel 为什么必须随 skew：波前时序与全局广播反例（2026-11-13 二次核源码）

> **本节是 §4.1 修正 1 的取证依据。结论：用户原意（rd_sel 随 A 口 skew）正确且必需；
> 本文档曾把它误写成「1 个全局 bankSel」，那会迫使 `P ≥ m+30`、增益塌到 1.08×（§4.7.2 反例）。**
> 事实 11「sDRAIN→sLOAD_B 边界阵列已静默」**已否证**，但用户方案两侧都不依赖静默，故不受影响。

#### 4.7.1 波前时序（已被实测 L=31 反向确认，非纯推导）

`WbComb.scala:136-150` 的 valid skew 是**无条件移位寄存器**（行 i 延迟 i 拍），横链每列再 +1（`:252`）⟹ **PE(i,j) 消费第 r 行激活于 `T_r + i + j`**，该拍组合读 `wReg`（`WbWorker.scala:150`）并在拍末更新 `psumReg`。

⟹ 一个 tile 的**最后一次权重读取**在 PE(15,15)、时刻 **`T_last + 30`**（`T_last` = 该 tile 末行激活拍）。
**实测锚定**：`cRowIn = RegNext(PE(15,15).validInV)`（`WbComb:268/315` + `WbWorker:195`）= `T_r + 31`。**B5 实测 L=31 精确成立 ⟹ 阵列忙到 `array 事件 + 30` 拍这件事已被测量确认**，不是假设。

现 FSM 逐拍（与实测空档 34 吻合）：

| 拍 | 状态 | 阵列 |
|---|---|---|
| `T_last` | LOAD_A 末行 fire → sNEXT | 波前刚进 PE(0,0) |
| `T_last+1` | sNEXT，drainNextCnt:=15 | 忙 |
| `T_last+2..+16` | sDRAIN 倒数 15 拍 | 忙 |
| `T_last+17` | drainNextCnt=0，查预蓄 → sLOAD_B | **忙** |
| `T_last+18` | LOAD_B 首 beat | **忙（还要 12 拍）** |
| `T_last+30` | LOAD_B 第 13 beat | **PE(15,15) 最后一次读 wReg** |
| `T_last+33` | LOAD_B 第 16 beat，`wbLoadWLock` | 已静默 ⟹ 覆盖 wReg 安全 |
| `T_last+34` | 下一 tile LOAD_A 首行 | — |

**⟹ 「sDRAIN→sLOAD_B 边界阵列已静默」是错的**（事实 11 已订正）。现设计安全靠两件事：① W 装载脉冲到 PE(i,j) 于 `T_last+18+j`，激活波前过同一 PE 于 `T_last+i+j`（i≤15）⟹ **逐 PE 追赶余量 `18−i ≥ 3`**，共享 aReg 链不被踩；② wReg 只在 `T_last+33 > T_last+30` 被覆盖。

#### 4.7.2 反例：若把 rd_sel 做成全局广播，硬下界是 **P ≥ m + 30**

双 bank 把「装载」与「计算」解耦了，但**全局 rd_sel 把「切换」重新串行化**：

- 翻转拍 F 必须晚于**所有** PE 读完 tile t 权重：`F ≥ T_last + 30`。
- 下一 tile 首行在 PE(0,0) 于 `T_first(t+1)` 读权重，必须已看到新 bank：`T_first(t+1) ≥ F + 1`。

⟹ `T_first(t+1) ≥ T_last + 31 = T_first(t) + m + 30` ⟹ **P ≥ m + 30**。

| M | 现基线 | 全局 rd_sel 下界 | 增益 |
|---|---|---|---|
| 1 | 34.107 | 31 | 1.097× |
| 4 | 37.096 | 34 | 1.091× |
| 8 | 41.104 | 38 | **1.082×** |
| 16 | 49.119 | 46 | 1.068× |

**⟹ 全局广播只省 3 拍（把 lock 从 `T_last+33` 提前到 `T_last+30`），增益 ≈1.07–1.10× 且与 M 几乎无关。这就是本文档曾误写「收敛为 1 个全局 bankSel」的代价，也是用户原意必须保留的原因。**

**注意这与 §3.1 的 L=31 是两个不同的下界**：L=31 是**回灌数据就绪**下界（与 m 无关）；`m+30` 是**全局切换**下界（随 m 增长）。m≥2 时后者更紧。

#### 4.7.3 用户方案（rd_sel 随 A 口 skew）的下界：**P ≥ max(m+1, 31)**

**读侧**：rd_sel 经与激活完全相同的 i+j 延迟，PE(i,j) 在 `F+i+j` 各自翻转：
- 晚于本 PE 末次旧权重读取：`F+i+j ≥ T_last+i+j+1` ⟹ `F ≥ T_last+1`（**i+j 消掉**）
- 早于本 PE 下一 tile 首行：`T_first(t+1)+i+j ≥ F+i+j` ⟹ `T_first(t+1) ≥ F`

⟹ `T_first(t+1) ≥ T_last+1 = T_first(t)+m` ⟹ **P ≥ m+1**。

**写侧（bank 复用）**：W lock 全局广播于 `L_w`，写入 inactive bank。该 bank 上一次被读是 tile t−1，其末次读取在 PE(15,15) 于 `T_last−P+30` ⟹ 需 `L_w > T_last−P+30`；又需 `L_w < F`（新 bank 在被任何 PE 读之前装好）。取 `F = T_last+1`、`L_w = T_last` ⟹ `T_last > T_last−P+30` ⟹ **P ≥ 31**。

**回灌侧**：§3.1 的 **P ≥ 31**（已实测）。

⟹ **三条合并：P ≥ max(m+1, 31, 16/duty)**。写侧 bank 复用与回灌下界**恰好都是 31**，不是巧合——两者都源于同一个 `2*totalN−1` 波前深度。

| M | 现基线 | **A 下界** | **增益** |
|---|---|---|---|
| 8 | 41.104 | 31 | **1.326×** |
| 16 | 49.119 | 31 | **1.584×** |
| 30 | ≈63 | 31 | **≈2.03×（峰值）** |
| 32 | ≈65 | 33 | ≈1.97× |

#### 4.7.4 与方案 C 的关系：A 取 C 的对角线切换，不取 C 的连续流水

C 的复杂度有三块：① 逐 PE 对角线切换、② 跨 kTile 连续激活流水、③ C buffer「提取+回灌」双读竞争。
**用户方案 A 只取 ①（且以 rd_sel 随 skew 的形式免费获得），丢掉 ② ③**：FSM 仍按 tile 分离（保留 DRAIN/NEXT 骨架），tile 间仍隔 ≥31 拍 ⟹ **提取与回灌不会同时抢 cPop**。

**关键可行性：现有拓扑已经免费提供了 i+j 延迟。** rd_sel 从 A 口同一注入点进入、沿同一行 skew（延迟 i）+ 横链（延迟 j）传播，天然获得与激活波前完全相同的时序 ⟹ **不需要 C 原描述的 5 bit×256=160 B 对角线计数器**，只需 1 bit/PE。

| | 全局 rd_sel（反例） | **A：rd_sel 随 skew（用户原意）** | C 连续流水 |
|---|---|---|---|
| 周期律 | max(m+30, 31) | **max(m+1, 31)** | max(m+1, 31) |
| 增益@M=8 / M=16 | 1.082× / 1.068× | **1.326× / 1.584×** | 1.326× / 1.584× |
| 读选择成本 | 1 bit 全局 | **1 bit/PE = 32 B** + 行 skew 15 bit | 5 bit/PE = 160 B |
| C buffer 双读竞争 | 无 | **无**（tile 仍分离 ≥31 拍） | **有（硬阻塞）** |
| 连续流水改造 | 无 | **无** | 有 |

⟹ **C 相对 A 多付 ② ③ 却拿不到任何额外增益 ⟹ C 出局**（§6.4）。
**A 的新增风险（陷阱 5）**：rd_sel 必须与 `validIn` **同拍对齐**（用 `rdSelIn` 而非再寄存一拍），且其跳变必须落在两 tile 的激活空隙内（P−m ≥ 23 拍，余量充足）。建议 rd_sel 走**独立 1-bit 链**（复制 valid skew 结构、不与 valid 共用寄存器），时序推理独立、可单独断言。

#### 4.7.5 逐文件逐信号改动清单

**① `WbWorker.scala` / `WbWorkerOsM1.scala`（两种 PE 同改）**

> **实施期简化（2026-11-16 核源码后确定，取代原「新建整套 wLoad/wScale 信号组」写法）**：
> **复用既有的 `loadWIn/loadWOut/loadWInLock` 作为 W 链的移位脉冲控制**（它本来就是「每脉冲右移一列 + 末拍广播 lock」的级联，语义完全一致），
> **复用既有的 `scaleBIn/scaleBOut` 作为 W 链的 scale 移位通道**（`RegNext(scaleBIn)` 已是 8-bit/PE 移位寄存器，j==0 直连注入、j>0 接左邻，与 a 链同深 lockstep，且只在 lock 拍捕获、计算期 don't-care）。
> ⟹ **只新增 3 组端口**：`wIn/wOut`（bW 数据链）、`wrSel`（广播）、`rdSelIn/rdSelOut`（随 A 口 skew）。
> ⟹ 新增寄存器降为 **41 bit/PE = 1312 B**（§4.2 四次订正），且**不留 dead 的平行装载路径**。
> ⟹ `WbCell.scala`（死代码但禁删）引用的 `loadWIn/loadWOut/loadWInLock/scaleBIn/scaleBOut` 全部保留 ⟹ **无需改 WbCell 即可继续编译**。

新增端口：
- W 口数据（写侧）：**`wIn: UInt(bW.W)`、`wOut: UInt(bW.W)`**、**`wrSel: Bool()`（全局广播输入，无输出）**
- W 口控制/scale：**复用 `loadWIn/loadWOut/loadWInLock` 与 `scaleBIn/scaleBOut`**，不新建
- A 口（读侧）：**`rdSelIn: Bool()`、`rdSelOut: Bool()`**（随激活经 skew/横链传播）

新增寄存器（**41 bit/PE**）：
```
wChainReg  = RegInit(0.U(bW.W))    // W 链数据移位元素，使能 loadWIn
wRegB      = RegInit(0.U(bW.W))    // bank B 权重
scaleBRegB = RegInit(0.U(8.W))     // bank B scale，必须与 wRegB 同一 when 块
rdSelReg   = RegInit(false.B)      // 读选择子，随 A 口传播（仅供 rdSelOut，不参与 MAC mux）
```
写侧锁存（**wrSel 全局广播，与 loadWInLock 同拍**）：
```
when(io.loadWIn) { wChainReg := io.wIn }
io.wOut := wChainReg
when(io.loadWInLock) {                       // 既有广播 lock 拍
  when(io.wrSel) { wRegB := io.wIn; scaleBRegB := io.scaleBIn }
  .otherwise     { wReg  := io.wIn; scaleBReg  := io.scaleBIn }
}
io.loadWOut := RegNext(io.loadWIn && !io.loadWInLock, false.B)   // 既有，不动
io.scaleBOut := RegNext(io.scaleBIn)                             // 既有，不动（即 W scale 链）
```
读侧传播与消费（**rd_sel 随 skew，MAC mux 必须用 `rdSelIn` 与 validIn 同拍**）：
```
rdSelReg := io.rdSelIn
io.rdSelOut := rdSelReg                                          // 与 validOut := RegNext(validIn) 同构
workUnit.io.b      := Mux(io.rdSelIn, wRegB, wReg)
workUnit.io.scaleB := Mux(io.rdSelIn, scaleBRegB, scaleBReg)
```
⚠️ **锁存源是 `io.wIn`（输入）不是 `wChainReg`**——与现设计 `wReg := io.aIn` 同理（`WbWorker:124`），对角线落位才成立。
⚠️ **陷阱 5：MAC mux 用 `io.rdSelIn` 而非 `rdSelReg`**。`rdSelReg` 比 `validIn` 晚一拍；`fmtReg` 能用寄存值是因为它单次执行内静态，**rd_sel 每 tile 都变，错一拍 = 整块权重用错 bank**。
⚠️ **`aReg` 使能去掉 `loadWIn`**（`:179`）、`scaleAReg` 同样（`:188`）⟹ A 口纯激活。
⚠️ **mode 0（垂直 loadV）路径不动**：`wReg := io.psumIn` / `scaleBReg := io.scaleBIn` 两支保留；`wrSel`/`rdSelIn` 恒 0 ⟹ bank B 与 mux 恒选 A 侧，逐拍不变。
⚠️ 现 `assert(!(loadVInLock && loadWInLock))`（`:126`）须扩为三路互斥（loadV / loadW / wLoad）。
⚠️ **写侧不需要阵列静默**：`wrSel` 选中的是 inactive bank，in-flight 波前读的是另一 bank ⟹ 广播锁存任意时刻安全。

**② `WbComb.scala`**

- 新增顶层输入：`wIn: Vec(totalN, UInt(bW.W))`、`wScaleIn: Vec(totalN, UInt(8.W))`、`wLoadIn`、`wLoadInLock`、**`wrSel`（广播）**、**`rdSelIn`（进 A 口 skew）**。
- **W 链横链**：`j==0` 接 `io.wIn(i)`（**直连、无 skew**，与现 loadW 旁路 `:172` 同理）；`j>0` 接 `pes(i)(j-1).io.wOut`。`wLoadIn` 同样 j==0 接顶层、j>0 接左邻 `wLoadOut`。`wLoadInLock` 与 `wrSel` **全阵列广播**。
- **rd_sel 链（关键）**：复制 `validSkewedShiftRegisters`（`:136-140`）结构做**行延迟 i**（独立 1-bit 链，不与 valid 共用寄存器）；`j==0` 接 `rdSelSkewedPerRow(i)`、`j>0` 接 `pes(i)(j-1).io.rdSelOut` ⟹ **PE(i,j) 的 rdSelIn 与 validIn 严格同拍（延迟 i+j）**。
- 去掉 `aSkewedPerRow`/`scaleASkewedPerRow` 的 loadW 旁路（`:172`/`:196`）。
- **OS 断言 `:287` 扩展**：`assert(!osM1 || !(loadVIn || loadVInLock || loadWIn || loadWInLock || wLoadIn || wLoadInLock))`；OS 态 `wrSel`/`rdSelIn` 恒 0。

**③ `WorkerBeeExecutor.scala`**

- 现 `:474-489` 的 loadWMode 覆盖块改为驱动 **W 口**（`io.wIn(i) := bPop.payload` 的 bW 切片、`io.wLoadIn := bPop.fire`、末拍 `io.wLoadInLock`），**不再占用 `wbAIn`**。
- **预装载 FSM**：在 sLOAD_A / sDRAIN 期驱动 `bPop.ready`（事实 2：这两态 bPop 空闲），把 tile t+1 的 16 beat 装进 inactive bank。**W 链独立于 a 链 ⟹ 预装载可与激活供数同拍进行**。
- **独立计数器 `preKTile` / `preInterCnt`**（陷阱 1）：`scaleBQ`/`scaleBOff`（`:203`/`:209`）必须由它们重算，**不得复用 `curKTile`/`tileInterCnt`**。
- **`wrSel` 驱动**：每次预装载窗口指向 inactive bank，窗口末 lock 拍广播；下一窗口翻面。
- **`rdSelIn` 驱动**：per-tile 电平，在 **`T_last+1` 之后**翻面（§4.7.3），随 A 口 skew 传播 ⟹ 每 PE 自动在正确拍切换。**不是全局广播到 PE**。
- **跳过 sLOAD_B**：预装载完成后下一 tile 直接进 sLOAD_A；首 tile 仍需一次完整 bootstrap 装载窗口（`rdSelIn` 初值 0、`wrSel` 初值 0）。
- **陷阱 4 守护**：`feed` 并入 `io.cPop.valid`（`needPartialSum` 时）。
- 预蓄门控（`bOccupancy >= totalN`，MX 另需 `scaleBOcc >= totalN`）须为预装载窗口重评；**「bPop 严格逐拍」不变式在预装载窗口同样成立**。

**④ `WorkerBee.scala`**：W 口顶层接线 + `wrSel` 寄存器 + `rdSelIn` 电平驱动 + `io.perf` 可选增 `preload`/`rdSelFlip` 电平（便于验收观测重叠与切换拍）。

**⑤ `WorkerBeeCalcConfig.scala`**：预装载 tile 索引所需的 scaleB 偏移参数（若需新增运行期量）。

**⑥ `WbInterface.scala`**：W 口若引到顶层端口面则改 Bundle；`wrSel`/`rdSelIn` 为内部信号不外露。

#### 4.7.6 取证办法（实施前，只加观测）

1. **验证「阵列忙到 array+30」**：已由 L=31 间接确认。若要直接确认，可加 `perf.arrayTail := pes(15)(15).validInV` 抽头，测 `arrayTail − array = 30`。
2. **验证 rd_sel 的对角线时序（陷阱 5）**：加 `perf.rdSel00` / `perf.rdSel1515` 两个抽头，测**两者跳变拍差 = 30**，且各自跳变严格落在本 PE 的两 tile 激活空隙内。**这是 A 相对全局广播的唯一新增取证项，必须做。**
3. **验证写侧不扰动 active bank**：预装载窗口与 LOAD_A 重叠期间金锚逐位不变（MX blk16/blk32 × 非零 B 延迟）。
4. **验证回灌守护**：人为把周期压到 31 以下的负向用例，须被 `cPop.valid` 门控停等（`stallA` 上升）而非静默算错。
5. **C FIFO 可见性 ±1**：§3.1 的 31 是否需 +1（StreamFifo pop 是否 fall-through）须实测；影响 M=1 的下界是 31 还是 32。

---

## 5. 方案 B — 面积最小：双 bank + 列广播行选通总线（无影子移位链）

### 5.1 机制

- 每 PE 只增 `wRegB`(16) + `scaleBRegB`(8) + **`rdSelReg`(1)** + 2 mux ⟹ **不加 W 链移位寄存器**。
- 装载改走**专用广播总线**：256-bit 列总线（16 列 × 16 bit）纵贯 16 行 + **16 条行选通线**。每拍总线送一行 16 个权重，被选通行锁进 inactive bank；16 拍装满 16 行。**直接按行寻址 ⟹ 无对角线落位问题**。
- scaleB 走 128-bit（8 bit × 16 lane）随同行选通。
- 省掉 W 链移位寄存器**及其 wLoadOut 传播 flop**。
- **`wr_sel` 全局广播**（与行选通同拍）；**`rd_sel` 仍必须随 A 口 skew 逐 PE 进入**——这一点与 A 完全相同，**不能因为装载改成广播就把 rd_sel 也改成全局广播**（否则塌到 `m+30`，§4.7.2）。

### 5.2 面积

```
每 PE 25 bit（wRegB16 + scaleBRegB8 + rdSelReg1）× 256 = 6400 bit = 800 B
+ rd_sel 行 skew 15 bit + wr_sel 1 bit
```
相对基准 2048 B ⟹ **+39%**。**比 A 省 768 B（−49%）**。
另加 ~272 条总线 wire（256 数据 + 16 行选通）+ 128 条 scale wire。

### 5.3 周期与增益

**与 A 完全相同**：`max(m+1, 31, 16/duty)`，M=8 **1.326×**、M=16 **1.584×**、M≈30 **≈2.03×**（L=31 已实测，§3.5）。
（总线仍是 256 bit/拍 ⟹ 16 行仍需 16 拍，事实 1 不变；31 拍回灌下界与装载机制无关，B 同样绕不过；**rd_sel 随 skew 是 A/B 共同的必需项**。）

### 5.4 改动面

同 ~5 文件，但 **`WbComb` 改动比 A 更大**：新总线 + 行选通取代影子链，PE 装载协议从「移位」改「广播选通」，属结构性替换而非增量叠加。

### 5.5 风险

**中高**。
- **flop 最小 ≠ 硅面积最小**：256-bit 总线纵贯 16 行、fanout 16×16，长布线可能反超 A 的局部移位 hop。**本仓无 STA、只有 yosys+abc**，无法先验判定 ⟹ 必须综合对照（§8 取证 3）。
- 总线扇出时序未知。
- RTL churn 大于 A，回归面更宽。

---

## 6. 方案 C — 性能最高：双 bank + 跨 kTile 连续激活流水 + 逐 PE 对角线切换

### 6.1 机制

- bank 同 B（800 B），另加**每 PE 对角线切换计数器** `log2(2*totalN)`=5 bit × 256 = **160 B** ⟹ 合计 **960 B**。
- 激活**跨 kTile 边界连续流入**，取消 kTile 间的 sDRAIN（事实 5 的 15 拍 flush 被流水化吸收，不是删除）。
- **PE(i,j) 在新波前到达拍（边界后 i+j 拍）各自切 bank** ⟹ 不能再用全局广播（阵列不再静默）。
- 上一 tile 的 psum 必须**在新 tile 计算的同时**被提取。

### 6.2 周期与增益（**已订正：原 4–5× 夸大**）

原推导「新周期律 → ~m+ε，M=8 ≈4.1×（m+2）/5.1×（m）」**忽略了 §3.1 的 kTile 间 psum 回灌依赖**。

C 让激活跨 kTile 连续流入，但**下一 kTile 的 psum 基底仍须来自上一 kTile 的结果**，而该结果经 **单口 C buffer 往返**、且要等波前走完 `2*totalN−1 = 31` 拍（**已实测**，§3.5）才对齐可用。连续流水**不能凭空消除这个依赖**：激活可以提前进阵列，但 psum 基底到不了 ⟹ 要么停等，要么算错（且如陷阱 4 所述，现硬件**不会替你停等**）。

⟹ **C 的真实上界 = max(m+ε, 31) ≈ A 的水平（M=8 约 1.326×），不是 4–5×。**

| 口径 | M=8 | 增益 |
|---|---|---|
| ~~原保守 m+2~~ | ~~≈10~~ | ~~≈4.1×（**作废**）~~ |
| ~~原理论上界 m~~ | ~~≈8~~ | ~~≈5.1×（**作废**）~~ |
| **订正：受 31 拍回灌依赖绑定（L 已实测）** | **≈31** | **≈1.326×（≈A）** |

**要真正突破 31 拍，必须让 psum 不走 C buffer 往返**——例如蛇形（serpentine）流向、或底行→顶行的专用反馈通路，使上一 kTile 的结果留在阵列内直接作下一 kTile 的基底。那是**比 C 原描述大得多的重设计**（改 psum 链拓扑 + 逐 PE 时序），且本仓无 STA 无法先验评估时序。**未立项前不得把任何 >1.5× 的 rank4 增益写进承诺。**

### 6.3 五个未验证阻塞（必须先取证，不可直接实施）

1. **C 读口竞争**（事实 12）：tile i 结果提取 + tile i partial-sum 回灌**都要 cPop**，单口 FIFO 不够 ⟹ 需双口或 2× 宽端口。**可行性未验证。**
2. **逐 PE 对角线切换 off-by-one 即数值错**：现设计靠静默边界规避（事实 11），C 放弃该庇护。
3. **partial-sum 回灌 skew 对齐**：`bSkewedPerRow` 行 i 延迟 i（事实 8）在连续流下对齐关系改变。
4. **OS 隔离**：`WbComb.scala:287` 断言。
5. **pop-before-push 不变式**：`WorkerBeeExecutor.scala:298`。

### 6.4 结论（**已按实测 L 改写**）

**重大重设计，但增益上界并不比 A 高**——原「4–5×」的立项理由已随 L=31 实测而消失。五个阻塞中第 1 条（C 单读口）是硬结构约束，还要额外付出逐 PE 对角线切换的时序风险。
⟹ **不建议为增益立项 C**。唯一值得单独立项的方向是**突破 31 拍本身**：让 psum 不走 C buffer 往返（蛇形流向、或底行→顶行专用反馈通路），使上一 kTile 结果留在阵列内直接作下一 kTile 基底。那是改 psum 链拓扑的**另一项重设计**，本仓无 STA 无法先验评估时序，**未立项取证前不得把任何 >1.5× 的 rank4 增益写进承诺**。

---

## 7. 方案对比与选定结论

| 维度 | **A：W 口移位链（选定）** | B：广播总线 | C：连续流水 | ~~全局 rd_sel（反例）~~ |
|---|---|---|---|---|
| **改动最小** | ★ **最小**（W 口与 A 口同构，增量叠加） | 中（结构替换） | 最大（重设计） | — |
| **面积最小** | 1316.75 B（+64.3%，**实测**） | ★ **800 B（+39%）** | 960 B + C 端口改造 | — |
| **性能最高** | **实测 1.279×@M=8 / 1.521×@M=16**（MX8；预测 1.326/1.584 已作废） | 同 A | 同 A（**原 4–5× 作废**） | ~~1.082×@M=8~~ |
| 周期律 | **max(m+1, L+1=32, 16/duty)** | 同 A | max(m+1, 32) | ~~max(m+30, 32)~~ |
| 写侧选择 | `wr_sel` 全局广播 + W lock 同拍 | `wr_sel` 全局广播 + 行选通 | 同 B | — |
| **读侧选择** | **`rd_sel` 随 A 口 skew，1 bit/PE** | **同 A（必需，不可省）** | 逐 PE 计数器 5 bit/PE | ~~全局广播 1 bit~~ |
| C buffer 双读竞争 | **无**（tile 仍分离 ≥31 拍） | **无** | **有（硬阻塞）** | 无 |
| 改动文件 | ~6 | ~5（WbComb 更重） | ≥6 + C 端口 | — |
| 风险 | 中 | 中高（无 STA，总线 fanout 未闭合） | 高 | — |
| 增益证据 | L=31 实测 + 对角线时序推导（待原型复测） | 同 A | 上界由 L 钉死 | 推导 |

> **⚠️ 三角度评估的正确结论（经 §4.7 订正）**：真正的区分维度有两条——
> ① **装载机制**（移位链 A vs 广播总线 B）决定 flop 面积与 RTL churn；
> ② **rd_sel 是随 skew 还是全局广播**决定周期下界（`m+1` vs `m+30`），**这一条是收益的生死线，A/B 都必须随 skew**。
> **C 相对 A 多付连续流水 + C buffer 双读改造，却拿不到任何额外增益 ⟹ C 出局。**

### 选定：**A（用户裁定 2026-11-13，wr_sel 广播 / rd_sel 随 skew）**；B 保留为面积候选，C 出局

理由：
1. **A 与 B 增益完全相同**（同受 `max(m+1, 31)` 绑定），差别只在 flop vs 布线。在**没有 STA / 综合面积数据**前，选改动小、与现有 A 口同构的 A 更稳。
2. B 省下的 768 B 相对整个阵列状态（仅三项已 2048 B，外加 MAC 组合逻辑）占比有限，**不值得为它冒 256-bit 总线纵贯 16 行的扇出时序风险**。若后续 yosys 对照证明 B 确实更省，再做增量替换（**rd_sel 随 skew 部分可直接复用，无需返工**）。
3. C 的第 1 条阻塞（C 单读口）是硬结构约束，且**增益上界与 A 相同** ⟹ 立项理由消失。
4. **增益随 M 单调上升到 M≈30 的 ≈2.03×**，之后缓慢下降 ⟹ **最值钱区间 M=16~32**，正好覆盖 YOLO11n/s 视觉（M≫1）与 LLM prefill / 多 token verify。**这比原估（峰值 1.49×@M=16）显著更高**，因为 rd_sel 随 skew 让切换下界降到 m+1 而非 m+17/m+30。
5. **decode M=1 仍只有 ≈1.10×**（L=31 绑定），远逊 OS-M1 的 ≈17.04 拍 ⟹ **rank4 不服务 M=1**，这条不变。
6. **专注 loadWMode=1 使 W 口只需水平一套**，省掉横/纵输入 mux；mode 0 走原路 ⟹ 回归面收窄。
7. **陷阱 4（回灌守护）与陷阱 5（rd_sel 对齐）对 A/B/C 一律适用**，不因形态而免除。
8. **本文档曾把 wr_sel/rd_sel 误收敛为 1 个全局 bankSel**，那会使增益塌到 1.08×（§4.7.2 反例保留作教训）。**用户原意正确，已订正。**

---

## 8. 前置取证

0. **✅ 已完成（2026-11-13）：kTile 间回灌依赖下界 L**
   加 `io.perf.cRowIn` 组合抽头 + TB 事件拍号 + Phase0Spec **B5 组**，实测 **L = 31 拍精确成立**（6656 样本、跨 m 与跨格式零例外），与源码闭式 `2*totalN−1` 吻合。详见 §3.5。
   **产出两条**：① 周期律订正为 `max(m+1, 31, 16/duty)`（`m+1` 项来自 rd_sel 随 skew，见 §4.7.3），A/B 的 M=8 增益由 1.640× 降到 **1.326×**，C 的 4–5× **作废**；② 发现回灌**无硬件守护**（事实 14）⟹ 新增陷阱 4。
   **口径**：本项只加观测抽头（2 行 RTL、零新状态），**未动任何功能逻辑**；B2/B3 周期数字逐位复现可证无扰动。

1. **带宽受压下的预装载可行性**（A/B 共同，**现为最高优先**）：按 `max(m+1, 31, 16/duty)` 扫 M∈{1,4,8,16}。
   **注意预期已变，且 duty(bp) 非单调**：`duty = (bp − (bp+2)/3)/bp` 用的是**整数除法** ⟹ bp=3→2/3、bp=4→**0.5**、bp=5→0.6、bp=6→2/3、bp=7→4/7≈0.571、bp=8→0.625、bp=9→2/3。**最小 duty 在 bp=4（与 bp=2）处取到 0.5**，对应 `16/duty = 32`，是**唯一刚好超过 31 拍下界的档位**。
   ⟹ **bp=3/5/7 下 M=8 应仍为 31 拍、增益仍 1.326×**（带宽项 24 / 26.7 / 28 全部 < 31），而不是旧模型预测的「退化到 1.54×」。**要验的是「退化不发生」，与旧预期相反**；想看带宽项反超须专门用 **bp=4**。须如实记录，不得只报 duty=1。
2. **~~C 读口竞争可行性~~（已降级）**：C 的增益上界已被 L=31 钉死在 A 水平（§6.2），**为增益而取证 C 已无意义**。仅当立项「突破 31 拍」的 psum 拓扑重设计时才需要重新勘查 cPop 单口。
3. **A vs B 综合面积对照**（仅 B 决策）：yosys + abc + nangate45，`memory -nomap` 隔离 RAM。**无 STA ⟹ 只比面积，不比 Fmax**，总线时序风险须显式标注为未闭合。
4. **A 原型上的 `m+1` 项与 rd_sel 对角线时序复测**（新增，**取代原「m+17 项复测」**）：
   - `m+1` 是**推导**（rd_sel 与激活同 skew ⟹ i+j 消掉，§4.7.3），未实测。原型落地后须用 B5 同一套事件拍号口径复测周期，确认 `max(m+1, 31, 16/duty)` 三项都成立。
   - **关键正向证据**：`rd_sel` 到达 PE(0,0) 与 PE(15,15) 的拍号差须**精确 = 30**（§4.7.6 的 `perf.rdSel00`/`rdSel1515` 抽头）。若差为 0，说明误做成全局广播 ⟹ 增益会塌到 1.08×，须立即拦下。
   - **M=16 是判别点**：`m+1=17 < 31` ⟹ 周期应仍为 31；若实测落到 46（= m+30），即全局广播的特征值。
   - 并验证陷阱 4 的守护在人为压周期时真的会拦、陷阱 5 的对齐在错一拍时金锚会红。

---

## 9. 共同不变式与禁止事项

**不变式（违反即不得合并）**
- **WS 数值金锚逐位不变**：MX8/MX4/BF16/INT8/INT4 全格式。金锚由**张量语义独立推导**，**禁止以 RTL 行为为拟合目标**。
- **loadWMode=0 路径逐拍不变**：`wr_sel`/`rd_sel` 恒 0、W 口空闲、预装载不启动；mode 0 用例（BF16/INT8/INT4 默认）周期与数值均不得变化，RTL 审计须显示 mode 0 路径零结构 diff。
- **OS-M1 路径零回归**：`WbComb.scala:287` 断言必须继续成立，**且须扩展覆盖新增 W 口信号**；OS 态 W 口恒低、`wr_sel`/`rd_sel` 恒 0；首行 `WbWorkerOsM1` 的 bank B 门控关闭。
- **bPop 严格逐拍**：预装载窗口内不得破坏供数节奏（既有 loadW 预取不变式）。
- **scaleBRegB 与 wRegB 同拍锁存**（事实 13）；**W 链的 scale 移位与数据移位 lockstep**（修正 3）。
- **预装载 scale 偏移必须用独立计数器**（陷阱 1）：不得复用 `curKTile`/`tileInterCnt`。
- **wr_sel 只指向 inactive bank**：写侧广播锁存拍，被写 bank 不得是任何 PE 当前正在读的 bank。因写侧只在预装载窗口活跃、而该窗口内所有 PE 都还在读旧 bank，本条由「预装载不越界到下一 tile 计算期」保证（陷阱 2 的节奏不变式）。**不需要阵列静默**——事实 11 的静默假设已否证，不得再作为依据。
- **rd_sel 必须与 validIn 同源同 skew**（陷阱 5）：MAC 的 bank mux 用 `rdSelIn`（组合、与 `aIn` 同拍），**不得用已寄存的 `rdSelReg`**；`rdSelReg` 只驱动 `rdSelOut` 右传。错一拍 = 整块权重用错 bank，且无断言会响。
- **pop-before-push**：`WorkerBeeExecutor.scala:298`。
- **回灌就绪守护（陷阱 4，本轮新增）**：`needPartialSum` 时 `feed` 必须含 `cPop.valid`（或等效 assert）。现设计靠 FSM 的 33+m>31 巧合余量维持正确性，**A 把余量压到零后该守护是唯一防线**；缺失即静默数值错且无任何断言会响。
- 全量回归 **20 suite** 全绿（新增用例会改 test 计数，须重新枚举；concrete 基类 `WorkerBeeSimCase` 自身算一个 suite）。
- 默认 RTL 重发射 + 结构审计（非目标实例须逐位不变）。

**禁止事项**
- 不得加宽 B 外部端口（已排除裁定）；事实 1 的 16 拍下界靠**重叠**打破，不靠加宽。
- 不得复活已排除项：osFmtKeep（暂缓）、SiLU/GELU 融合、核内描述符队列、DMA 侧稠密 2:4 解压、朴素 regFile 双缓冲。
- 不得把 rank3（C32 扩列）捆绑进来：rank3 已降级为条件项，**对 W/A 全量化纯格式零收益**。
- 不得顺手删除事实 6 的死代码（31 拍重载）：与本 feature 无关，另案处理。
- 不写 foragerbee / llc / gingercat / utils / .gitignore；不恢复已删文件；不删 `WbCell.scala`；不改 `StreamFifo`。
- 硬件 assert/require 消息用**英文 ASCII**；测试消息用中文。

---

## 10. 验收标准（A 落地时）

| # | 判据 | 口径 |
|---|---|---|
| 1 | MX8×MX8 WS M=8 拍/kTile **41.104 → ≈31.1**（不是 ≈25.1） | Phase0Spec B2 |
| 2 | MX4×MX4 WS M=8 拍/kTile **41.215 → ≈31.2**（不是 ≈25.2） | Phase0Spec B3 |
| 3 | M=16 **49.119 / 49.230 → ≈31.1 / ≈31.2**（`m+1=17 < 31` ⟹ 仍由 L 绑定；**若落到 ≈46 即误做成全局 rd_sel**） | B2/B3 |
| 4 | 稳态边际 **40.9922 → ≈31.00**（差分消固定常数 ⟹ 应精确落到 L） | B4 双 K 差分 |
| 5 | `array/kTile` 仍精确 = m | 预装载不得侵占计算拍 |
| 6 | WS 全格式金锚逐位不变 | 既有 MxSpec/IntSpec/QuickSpec/LoadWSpec |
| 7 | OS-M1 全部用例零回归 | `WbComb:287` 断言 + OS 用例 |
| 8 | duty 项符合 `max(m+1, 31, 16/duty)`：**bp=3/5/7 不退化（仍 31）、bp=4 才退化到 32** | §8 取证 1 |
| 9 | 全量回归 20 suite 全绿（现基线 **263 test**） | 重新枚举 test 数 |
| 10 | 默认 RTL 重发射审计 | 非目标实例逐位不变 |
| 11 | **loadWMode=0 用例逐拍不变** | BF16/INT8/INT4 mode 0 用例周期与数值均不变；审计显示 mode 0 路径零结构 diff |
| 12 | **预装载 scale 偏移正确**（陷阱 1） | MX **blk16 与 blk32 两档** × **非零 B 延迟**用例金锚全绿；`preKTile`/`preInterCnt` 与 `curKTile`/`tileInterCnt` 不得混用 |
| 13 | **回灌守护生效**（陷阱 4） | 人为把周期压到 31 以下的负向用例须被拦（停等或 assert），**不得静默算错**；M=1/4/8 三档都要覆盖（余量归零区） |
| 14 | **L 仍 = 31**（A 不得改变波前延迟） | Phase0Spec B5 事件拍号口径，min=max=31 |
| 15 | **rd_sel 确为对角线传播**（陷阱 5 的正向证据） | `perf.rdSel00` 与 `perf.rdSel1515` 事件拍号差**精确 = 30**；差为 0 即全局广播，判不合格 |
| 16 | **rd_sel 与激活同拍对齐** | 人为把 MAC mux 改用 `rdSelReg`（错一拍）的负向变体须金锚红；正确实现全绿 |

---

## 11. 裁定记录与仍未决项

### 11.1 已裁定（用户，2026-11-13）

| # | 项 | 裁定 |
|---|---|---|
| 1 | 选哪个方案 | **A（专用 W 装载口 + 双 bank + `wr_sel` 全局广播锁存 / `rd_sel` 随 A 口 skew）**，见 §4。B 保留为后续面积候选，C 出局 |
| 2 | 装载模式范围 | **双缓冲只在 loadWMode=1 生效**；mode 0 走原路、逐拍不变 |
| 3 | loadWMode 合并 | **暂不做**，记录为未来项（§12）；后续分析一律专注 mode 1 |
| 4 | M 上限 | **无需调整**：A 不改 `cBufferDepth`/`totalN` ⟹ errCode 7 的 `m ≤ cBufferDepth-totalN` = 48 不变（与 rank3 不同，无此副作用） |
| 5 | wr_sel / rd_sel 语义 | **用户澄清（2026-11-13）**：写侧 `wr_sel` 广播全局锁存、读侧 `rd_sel` 随激活经 skew 进来。本文档一度误收敛为 1 个全局 bankSel，**已按原意订正**（§4.1 修正 1、§4.7） |

### 11.2 仍未决（实施前须闭合）

1. **~~A′ 还是 A″？~~ ✅ 已闭合（用户澄清 2026-11-13）**：本文档一度把用户提案的 `wr_sel`（写侧广播锁存）与 `rd_sel`（读侧随 A 口 skew）**误收敛成 1 个全局 bankSel**，那会迫使 `P ≥ m+30`、增益塌到 1.082×。用户澄清后确认：**原提案本来就是两侧分离的，等价于本文档一度另立的「A″」** ⟹ 不存在待裁定的分叉，**A 就是用户原方案**。全局变体降级为 §4.7.2 反例，不再是候选。
   - 事实 11「sDRAIN→sLOAD_B 边界阵列已静默」**已否证**，但 A 两侧都不依赖静默（写侧只动 inactive bank、读侧逐 PE 对角翻转），故不受影响。
   - 本节增益数字为**推导（锚定实测 L=31）**，`rd_sel` 的对角线标记时序须按 §4.7.6 取证后才可进收益承诺。
2. **~~C 是否单独立项取证？~~ 已否定**：C 的增益上界 = A（同受 L=31 与对角线切换绑定），却多付连续流水 + C buffer 双读改造 ⟹ **不为增益立项**。只有「突破 31 拍」的 psum 拓扑重设计（蛇形 / 底行→顶行反馈）才值得另案评估。
3. **收益口径与目标负载匹配度**：A 下增益**随 M 增长**（M=8 1.326× → M=16 1.584× → M=30 ≈2.03×，直到 m+1>31），**对 YOLO11n/s 视觉（M≫1）与 LLM prefill 价值高于原估**；decode M=1 仍只有 ≈1.10×，由 OS-M1 覆盖。**duty<1 的旧担忧已消解**：bp=3/5/7 带宽项 24/26.7/28 全 < 31 ⟹ 不退化，只有 bp=4（duty 0.5→32 拍）才反超；原「M=8@duty0.6 约 1.54×」**已撤回**。仍须做 §8 取证 1 实测确认。
4. ~~**是否先补 `WorkerBeeSimCaseLoadWSpec` 的直接覆盖？**~~ **✅ 已完成（2026-11-13）**：该 suite 从空壳（零用例）补为 **12 条 mode 1 WS 专用门禁**，`WorkerBeeSimCaseLoadWSpec` 改 extend `WorkerBeeCaseSpec` 以复用已验证金锚机制。**12/12 全绿**（`build/loadw-coverage-6g.log`，`SBT_EXIT=0`，逐用例 elemMismatch=0 / addrBad=0 / laneViolations=0）。覆盖：INT8/INT4 显式 mode1（QuickSpec 默认非 MX 走 mode 0，原为真缺口）、MX8/MX4 各 blk16+blk32、MX/INT W4A8 stretch、M=8/16 + N64(4 nTile) + K256(8 kTile)、五通道反压 bp5/bp7。
   ⚠️ **运行口径**：本 suite **必须用 `-J-Xmx6G`**；10G 会挤掉 firtool 原生内存致 **SIGKILL(137) 全 12 红**（空 stdout/stderr、Scala 编译已成功）——那是宿主杀进程，**不是用例或 RTL 缺陷**，见记忆 `chisel-learn-j-xmx6g-7-suite-oom` 事实 5。
   全量回归 test 数由 247 → **260**（247 + LoadWSpec 12 + Phase0Spec B5 1）。

---

## 12. 未来项：移除 loadWMode=0（本次不做）

**用户裁定（2026-11-13）**：「算了先不做 loadmode 变动，记录到未来再看」「loadwmode=0 未来应该会移除」。

### 12.1 两模式的权威映射（易反直觉）

| | 存储布局 | 行步进 | 每 beat 承载 | 连续取数轴 | 注入路径 |
|---|---|---|---|---|---|
| **mode 0** 垂直 | **K×N 行主序** | `n × bEb` | 一个 K-row（固定 k、16 个 n） | **N 轴** | wbPsumIn → 顶行 → 沿 i 下沉 |
| **mode 1** 水平 | **N×K 转置** | `k × bEb` | 一个 N-column（固定 n、16 个 k） | **K 轴** | wbAIn → 左列 → 沿 j 右移 |

出处：`WorkerBeeCalcConfig.scala:499-523`、`WorkerBeeDmaRdOnly.scala:37-47`（两套块首址闭式，**勿混用**）、`WorkerBeeExecutor.scala:455-489`。

⚠️ **「沿 K 轴连续取数」= mode 1，不是 mode 0**。K×N 行主序下连续元素变的是 n；要沿 K 取数须跨 stride=N 做 16 次离散读，反而更差。

### 12.2 为什么只能删 mode 0、不能删 mode 1

- **errCode 4**：`isMx & loadWMode==0` 报错（`CalcConfig:192-203`，纯 FP8 同臂）
- **errCode 14**：`osM1 & (m=/=1 | loadWMode==0)` 报错（`CalcConfig:174`）⟹ **OS-M1 decode 也强制 mode 1**
- 根因（`Executor:184-189`）：scaleB 是 per-K-lane，在列 0 注入后**沿 a 链右移**、与权重 lockstep ⟹ 同一 loadWLock 拍捕获同 n_out/同 lane/同 K 块，零 skew。mode 0 权重沿 **i 轴**下沉而 scaleB 沿 **j 轴**右移，**两轴正交** ⟹ 无法共链对齐。

⟹ 删 mode 1 = 删掉 MXFP8/MXFP4（签核目标）+ OS-M1（已落地 2.00×）。**只能删 mode 0。**

### 12.3 移除 mode 0 的收益/代价（未来评估用）

- **面积收益很小**：loadV 传播 flop 1 bit×256 = 32 B + mux；`psumReg` 是复用的删不掉。
- **真正省的是验证面**：两套 DMA 地址闭式、两套 TB 期望地址序列、两条 PE 锁存路径、互斥断言（`WbWorker:126`）。
- **代价**：BF16/INT8/INT4 现有用例要改布局（`QuickSpec:757` 默认非 MX 走 mode 0）。金锚**值**不变（张量语义推导），变的是内存布局与期望地址序列。
- **与 A 的关系**：A 已按「mode 0 未来移除」设计（W 口只有水平一套）⟹ 移除 mode 0 时 **A 无需返工**，只需删垂直装载通路与 mode 0 分支。这是本次范围裁定的长期收益。

### 12.4 留着 mode 0 到底有什么用（应用侧，2026-11-13 补）

> 用户问法：「移除硬件上收益比较小，但重点是留着是否有什么用」。下面按**应用可达性**而非硬件成本回答。

#### 12.4.1 先划可达范围：mode 0 对签核部署**已经被禁掉了**

| 运行形态 | mode 0 是否合法 | 出处 |
|---|---|---|
| **MXFP8 / MXFP4（签核主线，W/A 全量化）** | **非法**，errCode 4 | `CalcConfig:192-195` |
| 纯 FP8（remap 走 MX8 通路） | **非法**，errCode 4 | `CalcConfig:198-203` |
| **OS-M1（decode M=1）** | **非法**，errCode 14 | `CalcConfig:174` |
| BF16 / INT8 / INT4 且 WS M>1 | 合法 | `CalcConfig:204` 注释 |

⟹ **mode 0 唯一可达的角落 = 「非 MX 格式 + WS + M>1」**。签核部署是 MX 全量化 ⟹ **对目标负载零用处**，它今天就已经是一条死路。
⟹ 「留着是否有用」这个问题，等价于「**非 MX 格式的 WS M>1 路径，是否有必须 K×N 布局的应用**」。

#### 12.4.2 按 B 操作数的来源分三类

**(a) 静态权重（FFN up/gate/down、QKV/O 投影、conv 展开成 GEMM）——mode 0 无任何优势**
权重布局是**离线选择**，预转置零运行时代价。mode 1 要求的 N×K 只是编译期把权重矩阵转置一次。⟹ 这类占推理绝大多数 GEMM，**mode 0 在这里没有存在理由**。

**(b) 运行期产生、但布局可由生产者自选的 B（prefill 的 S·V）——mode 0 无优势**
prefill 阶段 V 是**本步刚算出来的**，生产者（前一个 GEMM 的写回 / 向量引擎）可以直接按 N×K 落盘，不需要额外 transpose pass。⟹ 仍不需要 mode 0。

**(c) 运行期产生、且布局已被外部固定的 B —— 这是 mode 0 唯一真实的用处**
具体就是 **attention 的 S·V，且 V cache 已按 `[seq, head_dim]` 落盘**：
- S·V 中 `B = V`，形状 `[K=seq, N=head_dim]`；V cache 若按 `[seq, head_dim]` 行主序存储，**正是 mode 0 的 K×N 布局**。
- 改用 mode 1 需要 V 按 `[head_dim, seq]` 存储 ⟹ 每追加一个 token 的 V 向量从「连续 append 128 B」变成「跨 stride=seq 的 d 次离散写」，或者在读侧插一次全量 transpose（长上下文下是一次完整 V cache 读+写）。
- **但两个前提把这条用处削掉了**：① **decode 的 S·V 是 M=1 ⟹ 走 OS-M1 ⟹ errCode 14 强制 mode 1**，mode 0 根本不可用；② 若 attention 跑 MX（签核基线）⟹ errCode 4 同样强制 mode 1。
⟹ **只剩「非 MX 格式 + chunked-prefill/多 token verify（M>1）+ V cache 已由前序步骤按 `[seq,d]` 固定」这一个交集**。这是个真实但很窄的角落。

对照：**Q·K^T 反而天然就是 mode 1**（`B = K^T`，形状 `[d, seq]`；K cache 按 `[seq, d]` 存 = N×K）⟹ attention 的两个 GEMM 里，**只有 S·V 偏向 mode 0**。

#### 12.4.3 留着的代价（不只是硬件）

| 代价 | 说明 |
|---|---|
| **验证面翻倍** | 两套 DMA 地址闭式（`CalcConfig:499-523`、`DmaRdOnly:37-47`）、两套 TB 期望地址序列、两条 PE 锁存路径（`WbWorker:118` vs `:123`）、互斥断言（`:126`）。**每次改 B 通路都要改两处**（A 级/rank2/rank2b 都踩过：只改 WbDebugModel 会红 MxSpec） |
| **变成永久慢路径** | A 明确只在 mode 1 生效 ⟹ mode 0 永远拿不到双缓冲收益（其垂直装载复用 `psumIn/psumReg`，与 partial-sum 回灌同一条链，双缓冲它需要第二份 psum 链，代价远高于水平路）。⟹ **同一个 GEMM 换个布局就有 ~1.3× 的性能悬崖，软件必须知道并规避** |
| **与 rank4 的设计耦合** | 留着 mode 0 就必须在 A 里永久维护「`wr_sel`/`rd_sel` 恒 0 + W 口空闲 + 预装载不启动」的旁路，以及 mode 0 逐拍不变的回归门禁 |
| 硬件面积 | **很小**：loadV 传播 flop 1 bit×256 = 32 B + mux；`psumReg` 是复用的删不掉 |

#### 12.4.4 结论与建议

- **对签核部署（MXFP8/MXFP4 全量化）：mode 0 零用处**，errCode 4 已经禁掉它。留着它不服务任何目标负载。
- **唯一真实用处**是 §12.4.2(c) 那个窄交集（非 MX + M>1 + 外部固定 K×N 的 V cache）。**是否保留应由「部署里是否存在这样的层」决定，而不是由硬件成本决定**。
- **次要用处**：两条独立装载路径产出同一 C，是 bring-up/调试期的天然交叉校验（`WorkerBeeSimCase` 垂直 vs `WorkerBeeSimCaseLoadWSpec` 水平金锚相等）。这是验证便利，不是应用需求；移除后该交叉校验消失。
- **建议**：若确认部署里 attention 也走 MX（或 decode 走 OS-M1、prefill 的 V 可自选布局），则 **mode 0 可以移除**，且**移除时机放在 A 之后**——A 已按「mode 0 未来移除」设计，移除时只需删垂直装载通路与 mode 0 分支，**A 无需返工**。
- **移除的前置动作**：BF16/INT8/INT4 现有用例要改内存布局与期望地址序列（`QuickSpec:757` 默认非 MX 走 mode 0）。**金锚值不变**（张量语义推导），变的是布局与地址序。

---

## 13. 实施记录（2026-11-16 起，用户「开始实施」）

### 13.1 步骤划分与顺序调整

S1–S3 实为**一次原子重构**（W 口只有等 WbComb 接线、Executor 驱动后才成立，拆开无法各自编译通过而不留 dead 平行路径），故合并实施；**S5 守护提前到 S4 之前**——在 S3 等价替换阶段加 `cPop.valid` 门是零行为变化（33+m 下恒真），先装防线再压周期。

| 步 | 内容 | 状态 |
|---|---|---|
| S1 | PE 层 W 链 + 双 bank + rd_sel 传播（两种 PE） | ✅ 已落地 |
| S2 | WbComb W 横链 + rd_sel 独立 skew 链 + 去 loadW 旁路 + OS 断言扩展 | ✅ 已落地 |
| S3 | loadW 供数从 A 口迁到 W 口（**等价替换**） | ✅ 已落地，全量 263 test / 20 suite 全绿且周期逐位不变 |
| S5 | 陷阱4 守护 `cPop.valid` 并入 `feed` | ✅ 已落地，实测在 33+m 基线下**完全惰性** |
| S4 | 预装载 FSM + wrSel/rdSelIn 驱动 + 跳过 sLOAD_B | ✅ 已落地，三格式周期塌到 32 平台、金锚 bit-exact |
| S6 | TB 镜像 + rd_sel 抽头 + 用例 + 回归 + 重发射审计 | ✅ 已落地，264/264 全绿 + 结构审计完成 |

### 13.2 S1–S3 实际改动面

**主侧 5 文件**：
- `WbWorker.scala`：新增 `wIn/wOut` + `wChainReg`、`wRegB/scaleBRegB`、`wrSel` 广播锁存、`rdSelIn/rdSelOut` + `rdSelReg`；MAC mux 用组合 `io.rdSelIn`；`aReg/scaleAReg` 使能去掉 `loadWIn`；**scale 锁存并入两个 lock 分支**（与权重同一 when 块，保证严格同拍）。
- `WbWorkerOsM1.scala`：同构；OS 侧 `Mux(osM1, osB, Mux(rdSelIn, wRegB, wReg))`，scaleB 同形。
- `WbComb.scala`：`WsPeIo` 结构类型扩 5 字段；新增 `wIn: Vec` / `wrSel` / `rdSelIn`；**独立 1-bit rd_sel 行 skew 链**（镜像 `validSkewedShiftRegisters`、无条件移位、不共用寄存器）；W 链 j==0 直连顶层、j>0 接左邻 `wOut`；**删除 a/scaleA 的 loadW 旁路**；OS 断言扩到 `wrSel/rdSelIn`。
- `WorkerBeeExecutor.scala`：新增 `wbWIn/wbWrSel/wbRdSelIn` 输出与默认值；loadW 覆盖块改驱动 `wbWIn`，不再占用 `wbAIn`。
- `WorkerBee.scala`：顶层三组接线。

**测试 harness 3 文件**（直接例化 PE/阵列，新输入端口必须连上否则 elaboration 失败）：
- `WbOsM1PeSpec`：harness 补 `wIn/wrSel/rdSelIn` 输入与 `osWOut/wsWOut/osRdSelOut/wsRdSelOut` 输出；**等价断言纳入 wOut/rdSelOut**；随机激励改为同源驱动 `wIn`（权重源已迁移）并**随机化 wrSel/rdSelIn** ⟹ 双 bank mux 也纳入两 PE 逐拍等价覆盖。
- `WbOsM1ArraySpec` / `WbOsM1SequencerSpec`：新端口恒 0（OS 态本就不允许 W 装载）。

### 13.3 S3 等价性判据与实测

**判据**：Executor 侧 `wrSel`/`rdSelIn` 仍恒 0（S4 才驱动）⟹ S3 是**纯通道迁移**，周期与数值必须逐位不变。

**实测**（`build/s3-equiv-check.log`，`SBT_EXIT=0`）：`WbOsM1PeSpec` + `WbOsM1ArraySpec` + `WbOsM1SequencerSpec` + `WorkerBeeSimCaseLoadWSpec` = **34 test / 4 suite 全绿**，LoadWSpec **12/12 且 elemMismatch=0**。
全量 20 suite 回归 `build/regression-s1s3.log`：**263 test / 20 suite 全绿**，17 行 WS 周期与 S3 前逐位相同（BF16 M8=41.048 / M16=49.063；MX8 M8=41.104 / M16=49.119；MX4 M8=41.215 / M16=49.230）。

### 13.3b S5 惰性取证

`build/s5-guard-inert.log`（`SBT_EXIT=0`，Phase0Spec 12/12）：加入 `cPop.valid` 门后，**17 行 WS 周期与 `regression-s1s3.log` 逐位相同**（同上三组数字），OS 侧亦不变。证实该守护在 33+m 基线下恒真、零行为变化——它是 S4 压周期后的防线，不是当下的性能改动。

### 13.3c S4 实际改动面与推导出的两条约束

**新增状态**（`WorkerBeeExecutor.scala`）：`rdSelReg`（计算侧在读 bank）、`preActive`（预装载窗口进行中，可跨 sLOAD_A/sNEXT/sDRAIN）、`preloadDone`（下一 tile 已装入 inactive bank）、`preInterCnt`/`preKTile`（下一 tile 的列号/kTile 快照）。

**驱动**：`wbWrSel = loadWModeReg && !osActive && ~rdSelReg`（写侧恒指向 inactive bank）、`wbRdSelIn = loadWModeReg && !osActive && rdSelReg`（标量电平，交 WbComb 走行 skew + 横链）。

plan 未写、实施期从第一性原理推导出的两条约束：

1. **`preStartAt = max(0, m − totalN)`**（plan 原写"行 0 起装"）。lock 拍 `L_w = start + 15` 须同时满足
   - ① bank 复用安全：`L_w > (m−1) + 31 − P`（该 bank 上次被 tile t−1 的波前读到的最晚拍）；
   - ② 装好先于被读：`L_w < P`。
   m≥30 时若仍从行 0 起装，会覆写 tile t−1 波前仍在读的 bank。取 `max(0, m−16)` 对全部 m 同时满足两条。
2. **`drainTargetCnt = max(0, 29 − m)`**。跳过 16 拍 sLOAD_B 后 `P = m + 1 + D`（`D = 初值 + 1`）。回灌下界 L=31 要求 `D ≥ 30 − m` ⟹ 初值 `29 − m`（m<30）；m≥30 时 m+1 已主导，初值取 0 ⟹ `P = m + 2`，比下界多 1 拍（FSM 最小粒度，不可再压）。

**陷阱1 处理**：`scaleBQ` 加 `Mux(preActive, preInterCnt*(kTiles+1)+preKTile, tileInterCnt*(kTiles+1)+curKTile)`——预装载窗口内本拍 scaleB 字属于**下一 tile**，沿用当前 tile 的计数会静默错位。

**回退路径**：预装载未在 drain 结束前装完 ⟹ sDRAIN 原分支进 sLOAD_B 全量重推 16 拍，并在该分支显式 `preActive := false.B` 撤销窗口（否则文末覆盖块与 sLOAD_B 同时驱动 W 链，两者 `preInterCnt`/`tileInterCnt` 不同步会提前发 lock）；sLOAD_B 的 lock 拍再翻 `rdSelReg` 并清 `preActive/preloadDone`。

**DMA 地址序不变**：预装载只把 bPop 的**消费时刻**提前，读 DMA 的地址生成序完全未动 ⟹ TB 期望地址序列预期无需改（待回归确认）。

### 13.5 S4 首轮全量回归结果与「L+1」订正

`build/regression-s4s5.log`：**260/263 通过，20 suite 全部完成**。3 个失败全部是 Phase0Spec 的 **B/B2/B3 斜率断言**——它们编码的是改造前的 `33+m` 律（斜率 1），而 S4 的目的正是消掉这条斜率。**不是功能回归**：

- 全部用例 `elemMismatch=0`、`laneViolations=0`、六通道地址对账不一致拍数 **0**；
- mode 0（垂直）与 OS-M1 全部用例逐位不变；
- 三格式的 WS 周期一律塌到 **≈32 拍/kTile 的平台**，斜率≈0：

| M | BF16 改造前 → 实测 | MX8×MX8 改造前 → 实测 | MX4×MX4 改造前 → 实测 |
|---|---|---|---|
| 1 | 34.052 → **32.013** | 34.107 → **32.033** | 34.184 → **32.074** |
| 2 | 35.054 → **32.030** | 35.092 → **32.033** | 35.168 → **32.074** |
| 4 | 37.058 → **32.030** | 37.096 → **32.068** | 37.207 → **32.074** |
| 8 | 41.048 → **32.030** | 41.104 → **32.068** | 41.215 → **32.145** |
| 16 | 49.063 → **32.118** | 49.119 → **32.209** | 49.230 → **32.426** |

**关键订正：有效回灌下界是 L+1 = 32，不是 L = 31。**

B5 组在同一轮里仍测得 `L: min=max=31`（跨 m∈{1,8}、MX8/MX4 零例外），而事件流推出的周期 `T=32`。差的一拍已核到源码：L 的观测口径是 `perf.cRowIn = executor.io.cPush.valid`（[WorkerBee.scala:562](../scala/main/WorkerBee.scala)），而 C FIFO 是 `StreamFifo` → `Queue(depth, hasFlush=true)`，**`flow=false`**（[Stream.scala:371](../../utils/Stream/Stream.scala)）⟹ `cPop.valid` 比 `cPush.valid` 晚一拍。S5 的 feed 门查的正是 `cPop.valid` ⟹ 真实下界 32。

由此两条结论：

1. **S4 已贴死下界**，不是"还差一拍可优化"。`drainTargetCnt = 29−m` 给出 FSM 名义周期 31，S5 守护把它停等补足到 32 —— 这一拍是**物理依赖**，不是 FSM 粒度浪费。
2. **S5 守护在 S4 之后是承重的，不再惰性**。改造前 33+m ≫ 32 ⟹ 恒真（§13.3b 已证）；S4 把名义周期压到 31 < 32 后，守护每 tile 恰好停等 1 拍，把"静默回灌未就绪 partial sum"换成"可观测的停等"。这正是它提前到 S4 之前落地的价值。

**断言随之更新**（`WorkerBeePhase0Spec.scala`）：B 组斜率 1→0、M=1/M=16 期望 34/49→32/32；B2/B3 斜率 1→0；B5 下界与增益表 `L`→`L+1`；B4 的 `边际/下界` 语义由"增益"改标为"距下界倍数（1.000 = 贴死）"。

订正后 Phase0Spec **12/12 全绿**（`build/phase0-s4-law.log`），measurements.md 恢复 32 行。实测增益：

| M | BF16 | MX8×MX8 | MX4×MX4 |
|---|---|---|---|
| 1 | 1.062× | 1.061× | 1.060× |
| 2 | 1.093× | 1.093× | 1.091× |
| 4 | 1.155× | 1.154× | 1.154× |
| 8 | 1.280× | 1.279× | 1.275× |
| 16 | 1.526× | 1.521× | 1.511× |

B4 双 K 差分给出稳态边际 **距下界 1.000×**（MX8 与 MX4 皆是）——周期精确贴死物理极限。

### 13.6 S6 取证机制（三项，均不新增 RTL 状态）

**① rd_sel 对角抽头（陷阱5 正向证据）**
`WbComb` 引出 `rdSelFirst = pes(0)(0).io.rdSelIn`、`rdSelLast = pes(arrayN-1)(arrayM-1).io.rdSelIn`，经 `perf.rdSelFirst/rdSelLast` 上顶层。两者都是**既有寄存器链的电平抽头**，零新增状态，符合 perf 端口裁定。

检边放在 **TB 侧**（`perfEdgeNames` + `perfPrev` 数组），因为 rd_sel 是长期保持的电平、只有上升沿才是事件；若在 RTL 里做边检测就要加 2 个寄存器，违反裁定。

新增 **B6 组**断言两角上升沿恒差 `(arrayN-1)+(arrayM-1) = 30` 拍。全局广播变体会给出 0 ⟹ 本组与 B 组的 32 平台互为反证。

**② 陷阱4 守护承重证据（无需强制注入）**
原 plan 设想"强制周期 <31 看是否停等"，但周期由 `drainTargetCnt` 内部推导、外部无法强制，加后门寄存器得不偿失。改用**已有 perf 计数**作正向证据：

| | 改造前 | S4 后 |
|---|---|---|
| WS `stallA` | **恒 0** | `iters − nTiles`（实测 480/512、992/1024、224/256） |

即**每个带 partial-sum 回灌的 kTile 恰好停等 1 拍**（每个 N tile 的首个 K pass 无回灌 ⟹ 减去 nTiles）。这正是守护把"静默回灌未就绪行"换成"可观测停等"的量化体现。断言取 `stallA ≥ iters − nTiles`（m=16 实测另有 +62 拍余量，故用 ≥ 而非 =）。

**③ blk32 + 节流 B 供给**
B6 三个任务：MX8 blk16 M=8 / MX8 blk32 M=8 / MX4 blk32 M=1，整组 `bpPeriod=5`（duty 0.6）。节流下预装载可能来不及在 drain 内装完 ⟹ 正好压测 §13.3c 的 `sLOAD_B` 全量重推回退路径。

**TB 地址序列无需镜像改动**：首轮回归六通道对账 0 坏拍已证实预装载只挪动 bPop 消费时刻、不改 DMA 地址生成序。

### 13.7 最终验收（264/264）与结构审计

`build/regression-s6.log`：**264 test / 20 suite 全绿**（263 + B6 新增 1）。

B6 组 rd_sel 对角取证 —— 陷阱5 的决定性正证据：

| 用例 | 对角偏移 | 翻面次数 |
|---|---|---|
| MX8 blk16 M=8 | min=max=**30** | 256 |
| MX8 blk32 M=8 | min=max=**30** | 256 |
| MX4 blk32 M=1 | min=max=**30** | 128 |

640 次翻面零例外，恰等于 `(arrayN-1)+(arrayM-1)`。全局广播变体会给出 0 ⟹ 方案 A 的语义已被硬件行为钉死。blk32 与 bp=5 节流（含 `sLOAD_B` 回退路径）同时通过。

**重发射结构审计**（`build/rtl-audit-rank4.md`）：72 → 72 文件集不变；变更 14 个 = 5 个目标模块 + 9 个 verification 伴生；**58 个逐位不变**（全部 MAC / DMA / Scratchpad / RegIf / CalcConfig / OS Feeder-Sequencer / Cvt）⟹ 计算与搬运通路未被触碰。新增寄存器按集合差逐项核对，只有新增无删除，面积订正为 **1316.75 B**（§4.2 五次订正）。perf 抽头实测为端口直连、零新增状态。

### 13.8 B7：bp=4 零余量档位取证（含一次归因订正）

**档位来历**：`duty(bp) = (bp − (bp+2)/3)/bp` 用整数除法 ⟹ 非单调，全局最小 `duty=0.5` 只在 **bp=2/4** 出现，对应 B 端口需求 `16/duty = 32`，恰等于有效下界 `L+1=32` ⟹ **B 侧余量为零**。旧下界 31 时它是唯一「退化档位」；订正为 32 后 B 侧理论不再退化，但余量归零，故仍须实测。

**担心的风险**：预装载窗口 = `m+1+D = 32` 拍，16 个 B 字在 duty0.5 下也恰需 32 拍。若预蓄门延迟点火使窗口不够，FSM 回退走完整 `sLOAD_B` ⟹ 周期 `m+1+(30−m)+16 = 47` 拍（**同时付拉长的 drain 与完整装载**），m≤13 时比改造前 `33+m` 还慢 ⟹ 是悬崖不是平局。

**实测（`build/phase0-b7-portmodel.log`，Phase0Spec 14/14 全绿）**：

| M | 端口拍/kTile B / A / scaleA / scaleB | 约束侧 | 模型下界 | 实测 | stallB |
|---|---|---|---|---|---|
| 1 | 16 / 1 / 1.5 / 8 | B | 32.0 | **32.209** | 112 |
| 8 | 16 / 8 / 12 / 8 | B | 32.0 | **32.244** | 48 |
| 16 | 16 / 16 / **24** / 8 | **scaleA** | **48.0** | **48.170** | 35 |

**结论：悬崖没有发生。** m=1/8 贴着 32 跑、stallB 极小 ⟹ 预装载正常完成。

**⚠️ 一次归因订正（承重教训）**：首版断言只算 B 端口、期望恒 32，于是 M=16 的 48.170 被判为「掉进 47 拍回退悬崖」——**数值巧合（47+1≈48）几乎骗过判定**。查 perf 计数后否证：
- `stallB` 仅 **35**（若真回退，16 拍装载窗在 duty0.5 下会产生大量 loadW 停拍）；
- `stallA = 8653`、**scaleA 端口占用 97.6%** ⟹ 是 sLOAD_A 饿死等 scaleA 供数。

真因是 **scaleA 拍数随 m 线性增长**（本配置 `1.5×m` 拍/kTile，取自 `plan.expScaleAAddrSeq` 的张量语义序列，非拟合），m=16 时 `24 ÷ 0.5 = 48` 拍 ⟹ scaleA 端口先撞墙，预装载余量问题在该点已无意义。

⟹ **周期律的带宽项必须取全部读端口的最大值，不能只看 B**：
```
拍/kTile = max(m+1, L+1, max_over_ports(beats_per_kTile(port) / duty))
```
断言已改为该完整模型，并打印约束侧名字与各端口拍数，防止以后再把端口墙误判成预装载缺陷。

**口径缺口（用户 2026-11-16 裁定：两个一起补，已落地）**：`meas` 的 `predPort` 原只取 `max(ArrayFloor, B, A, scaleB)`、`predDram` 原只取 `(B物理+A物理+sB物理)`，**两者都漏 scaleA** ⟹ m 大时 `predPort` 偏低、DRAM 字节偏低。现已补为：

```
predPort = max(阵列16, B读/kTile, A读/kTile, sB读/kTile, sA读/kTile)
predDram = (B物理 + A物理 + sB物理 + sA物理) / 32 / iters
```

补前已核实**不会破坏任何断言**：`predPort` 经 `structCycles` 参与断言的只有 4 处（Phase0Spec :228–230 / :292 / :304 / :608），**全部是 OS 模式**测量；OS 下 M=1，scaleA 拍/kTile 实测最大 **1.5**（K1024 MX8：1536÷1024），远低于 `ArrayFloor=16` ⟹ 取 max 后逐点不变。

**补后实测验证**（`build/phase0-predport-sa.log`，Phase0Spec **14/14 全绿**）：

| 项 | 结果 |
|---|---|
| OS 侧 `predPort`（8 行） | **全部 16.00 逐点不变** ⟹ A5 的 3% 结构模型断言不受影响 |
| WS MX8 M=16 `predPort` | **16.00 → 24.00**（正是 B7 撞到的 scaleA 墙） |
| WS MX4 M=16 `predPort` | 仍 16.00（sA=16 恰与 ArrayFloor 并列，非漏算） |
| WS BF16 全 M `predPort` | 仍 16.00（无 scale 通路，sA=0） |
| `predDram` | 仅 MX 用例上升（OS 19.00→19.13、9.50→9.72、18.00→18.19、10.00→10.19；WS MX8 M16 33→36）；INT/BF16 逐位不变 |

改动范围仅 `WorkerBeePhase0Spec` 的私有 `meas`/报告说明/文件头口径注释，**不触 RTL、不触其他 suite** ⟹ Phase0Spec 单跑即为充分覆盖。

### 13.4 实施期偏离 plan 之处（均已核源码正当化）

1. **端口复用而非新建**（§4.7.5① 已订正）：W 链移位脉冲控制复用既有 `loadWIn/loadWOut/loadWInLock`，scale 移位复用既有 `scaleBIn/scaleBOut`（`RegNext(scaleBIn)` 本就是 8-bit/PE 移位寄存器）⟹ 只新增 3 组端口、4 个寄存器、**1312 B**（非 1568 B），且 `WbCell.scala`（死代码禁删）无需改动即可编译。
2. **S5 提前到 S4 之前**（见 §13.1）。
3. **`src/workerbee/gem5/tests/WorkerBeeRtlArraySpec.scala` 未同步**：该文件不在 `build.sbt` 源目录内（只收 `scala/main` + `scala/test`），引用 WbComb 旧端口面、现已过时。属交付面外的 gem5 侧产物，**未改动**，需要时另案同步。