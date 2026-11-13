# rank 2b（B 级）改动 plan：scaleB 同字复用

- **状态**：✅ 已实施并验证（2026-11-13，全量回归 247/247）；路线=泛化 pair 机制（用户 2026-11-12 选定）。实施记录见 §11
- **日期**：2026-11-12
- **前置**：A 级（noGather）+ rank 2（B 数据 pairStretch）均已落地并验证（244/244）
- **范围**：只作用于 **MX scaleB 读通路**（`bDmaScale`）。纯 INT/BF16 无 scale；scaleA 本次不做。
- **收益口径**：**吞吐 +0**（A 级后 predPort 已 = 阵列下界 16）；scaleB 物理读字节 64→32 B/kTile（blk16，**−32**）或 64→16（blk32，**−48**）。这是**能耗/带宽项**，不是吞吐项。

---

## 1. 机制与几何（已查证）

A 级后 scaleB 每逻辑拍读一个对齐 4B 字、原样推，消费端按 `idx=(lane*step)/blk + off` 选字节。每列每窗口需求 `demand = kPerBeat/blk ∈ {1,2,4}` 字节，窗口步进 `scaleBColTileAddressOffset = demand`（`CalcConfig:609-624`），列步进 `scaleBRowAddressOffset = K/blk`。

| 格式 / blk | demand | 字共享因子 F=4/demand | sB 读/kTile | sB 字节/kTile |
|---|---|---|---|---|
| MX8-blk16 / MX4-blk32 | 2 | **2** | 16→**8** | 64→**32** |
| MX8-blk32 | 1 | **4** | 16→**4** | 64→**16** |
| MX4-blk16 | 4 | **1**（无可省） | 16（不变） | 64（不变） |

**字共享闭式**：逻辑地址 `L = base + j*sbRowOff + kt*demand`；`sbRowOff = kTiles*demand` ⟹ `L = base + (j*kTiles + kt)*demand`。字内偏移 `off_raw = (q*demand)&3`，`q=(j*kTiles+kt)&3`（正是 A 级消费端 off 闭式）。**新字条件 = off_raw==0 = `(kt*demand)%4==0`（当 sbRowOff%4==0 时与 j 无关）**。

**走序**：`(nt, kt, j)` 列 j 最内层（`WorkerBeeDmaRdOnly` isA=false，lineTarget=totalN）⟹ **复用距离 16 拍**，须 16 字 per-column 缓存（与 rank 2 同构）。缓存 = 16×4B = **64B**（极小）。

**对齐前提**：`sbRowOff%4==0`（blk16 ⟺ K%64==0；blk32 ⟺ K%128==0）且 `base%4==0` 时，新字条件跨列一致 ⟹ 单一读窗口判据可用。否则逐列字边界不同 ⟹ 退回基线（每窗口读）。

## 2. 路线：DMA 侧（沿用 rank 2，推翻原 pop 侧推荐）

原 memory 推荐 pop 侧（feeder 复用 scaleWord）。**沿用 rank 2 的 DMA 侧结论**：pop 侧要拆 feeder `bIn/scaleBIn` 1:1 配对 + 槽生命周期 + WS lockstep，波及 Feeder/Sequencer/Executor 多文件；DMA 侧只需 **64B 缓存 + 控制逻辑**，消费端 **off 完全不动**（它从推入的字选字节，缓存字与直读字同值）。故选 DMA 侧。

**与 rank 2 的关系——泛化而非另起炉灶**：rank 2 的 pairStretch 是 **demand=2 的特例**。统一读判据 `(kCnt*pairDemand)%4==0`：
- bDma：`pairDemand=2`（编译期，stretch 半字）⟹ `(kCnt*2)%4==0 = kCnt(0)==0`，**与现有 rank 2 逐位等价**。
- bDmaScale：`pairDemand=reg_colTileOffset`（运行期 demand）⟹ F∈{2,4}。

⟹ 把 pair 机制的 `pairDemand` 与使能源泛化，缓存/credit/rsp 收齐门控/遍历推进**全部共享**；push payload 分两臂（stretch 臂走 stretchBeat 半字加宽；非 stretch 臂走原始字 `Mux(pairPushValid, cache[j], rsp.data)`）。

## 3. 设计

| 项 | 现状（A 级） | 改后（pair 使能时） |
|---|---|---|
| 读发起 | 每窗口 16 读 | **仅新字窗口** `(kCnt*demand)%4==0` 发 16 读；其余窗口不发 |
| 缓存 | — | 16×4B，按列号（收齐序写 / lineCounter 读） |
| push 源 | 恒 rsp 原始字 | 新字窗口 = rsp；复用窗口 = cache[j]（**原始字，无半字选择**） |
| push 数 | 1/逻辑拍 | **不变** |
| credit | 1 读扣 1 | 1 读扣 **F**、门控 `>=F`；bufPopFire +1 |
| 遍历推进 | req.fire | 新字窗口 = req.fire；复用窗口 = 缓存 push 拍 |
| 消费端 off | A 级闭式 | **完全不动**（OS `headKt(j)` / WS `tileInterCnt` 同式） |

**使能门控（运行期）**：`pairScaleEn = (demand =/= 4) && (reg_rowOffset%4==0) && (reg_baseAddr%4==0)`。demand=4（F=1）或 sbRowOff/base 非对齐 ⟹ 退回 A 级基线（每窗口读、noGather 单对齐字）。`demand = reg_colTileOffset`（scaleB 窗口步进恰 = demand）。

### 3.1 冒险（沿用 rank 2 处置）

- **H1 缓存写先于读 / 偶应答滞后**：新字窗口 16 读流水发起，rsp 可能滞后到 kCnt 已切复用窗口 ⟹ 缓存写/新字推由 **rsp 侧**驱动（不看当前 kCnt），复用推门控 = 本新字窗口 16 rsp 全收齐（`pairEvenDone`），push 源选择依据 `pairPushValid` 而非当前窗口奇偶。**与 rank 2 同一套处置**。
- **H2 credit 扣 F 同拍竞态**：`finalReqFire & bufPopFire` 同拍净 `-(F-1)`；漏改溢出/饿死。
- **H3 复用窗口 push 节奏**：credit 读时预留 F 槽 ⟹ 复用推不再查空间。
- **H4 start/flush**：pairRspCnt 与 gPhase/inflCnt 同拍清零；缓存内容无需清（每新字窗口全重写后才被读）。
- **H5（scaleB 专属）逐列对齐**：sbRowOff%4!=0 时逐列字边界不同 ⟹ 使能门控强制退回基线，**不在快路径处理逐列读判据**（避免 16 比较器）。边界用例 K=32/64-blk32 走基线、功能不变。

## 4. 与既有禁令/裁定的关系

- 不折半 kTile、不连推：`reg_kTile` 与 demand 步进原样，复用推与新字推相隔 16 拍、分属不同窗口 ⟹ 不违反 `WorkerBee.scala:108-111`。
- 不删 gather：noGather 快路径 + 基址非对齐回退保留（A 级裁定 [[workerbee-dma-unaligned-gather]]）；pair 再叠一层 sbRowOff 对齐门控。
- 消费端 off 闭式不动 ⟹ A 级 `headKt(j)`/`tileInterCnt`/`kTiles+1` 三处修正全部继承（[[workerbee-scaleb-port-bottleneck]]）。

## 5. 改动清单

| # | 文件 | 改动 |
|---|---|---|
| **M1** | `WorkerBeeDmaRdOnly.scala` | 泛化 pair：`pairDemand`（bDma=2 编译期 / scaleB=reg_colTileOffset 运行期）+ 实例使能源；读判据 `(kCnt*pairDemand)%4==0`；credit 扣 F；非 stretch 臂加缓存 push mux；新增 `pairScale: Boolean=false`（仅 bDmaScale 传 true）。**bDma pairStretch 行为须逐位不变**（pairDemand=2 特例） |
| **M2** | `WorkerBee.scala:38` | `bDmaScale` 例化传 `pairScale = true`（一行） |
| **M3** | `WbDebugModel.scala:754` | `expScaleBAddrSeq` 三臂（mix/OS/WS）：仅 `(kt*demand)%4==0` 窗口入序列（demand 由 sbColOff 取） |
| **M4** | `WorkerBeeSimCaseMxSpec.scala` | **第二套独立 scaleB 期望序列** `expScaleBPhysSeq` 同步改（A 级教训：两套都要改，漏改红 6 用例） |
| **M5** | `WorkerBeePhase0Spec.scala` | 混配/纯 MX `sbAmp` 2→1（blk16）/4→1（blk32）、`sB读/kTile` 16→8/4；拍/kTile 与 predPort 不变；MX4-blk16 sbAmp 恒 1 不变 |
| **M6** | 用例 | MX8-blk16/blk32 × MX4-blk32 × {OS,WS} × K∈{64,128,512} × 反压；**H1 非零延迟**（OsM1PerfSpec 已有 MX8 延迟用例，sb 延迟臂覆盖）；**H5 边界** K=32/64-blk32 退回基线须绿 |

**不动**：消费端 off（Sequencer/Executor）、scaleA 通路、B 数据 pairStretch 行为、feeder、Buffer、errCode、寄存器映射、C/scaleC 金锚。

## 6. 不变式

1. push 序与 push 数逐拍不变 ⟹ bPop/scaleBPop lockstep 与预蓄门控不受影响。
2. 消费端 off 不动 + 缓存字 == 直读字 ⟹ 选中字节逐值相同 ⟹ C 张量值不变（OS/WS）。
3. demand=4 / 非对齐 ⟹ 逐拍退回 A 级基线。
4. bDma（pairStretch）行为逐位不变（pairDemand=2 特例，rank 2 不回归）。

## 7. 验收

1. 既有 244 test 不得转红；20 suite 全量，判完成看汇总两行。
2. blk16 `sbAmp` 2→1、`sB读/kTile` 16→8；blk32 `sbAmp` 4→1、`sB读/kTile` 16→4；MX4-blk16 恒 1。
3. **拍/kTile 不变**（混配 ≈17.13、MX4-blk16 ≈18.29、WS 全组）——吞吐 +0，变差即回归。
4. `addrBadCnt==0`、C `elemMismatch==0`。
5. **bDma 发射 RTL 结构不变**（pairDemand=2 特例逐位等价，结构归一化比对）；非 MX 配置 pair 逻辑 DCE。
6. **H1 非零延迟 + H5 对齐边界**用例必须绿。

## 8. 非目标

- scaleA 同字复用（sA 对 predPort 无影响、字节占比极小，留后续）。
- pop 侧 feeder 复用路线（已否决，波及面大）。
- 吞吐类（rank 3 扩列、rank 4 WS 双缓冲）。

## 9. 风险与回退

| 风险 | 处置 |
|---|---|
| **泛化碰已验证 rank 2** | pairDemand=2 特例须逐位等价 + 全量回归 + bDma 结构归一化审计（验收 5）；若泛化风险高，退化为独立 `pairScale` 机制（不碰 pairStretch，代价是 ~40 行重复） |
| H1 缓存写先于读 | 沿用 rank 2 rsp 侧驱动 + pairEvenDone；非零延迟用例强制 |
| H5 逐列对齐 | 使能门控退回基线，不做逐列比较器 |
| 收益小（−32~48 B/kTile）改动中等 | 立项判断见 §10 |

**回退**：M2 一行改回即恢复（pairScale 默认 false，M1 泛化对 bDma 逐位等价）。

## 10. 立项判断（供决策）

**收益**：scaleB 读字节 blk16 −32 / blk32 −48 B/kTile（端口级事实）；**吞吐 +0**。post-rank2 总账 358→326（blk16）/310（blk32）。
**代价**：泛化已验证的 pair 机制（pairDemand 运行期 + 非 stretch push 臂 + 对齐门控）、TB 两套期望序列、4+1 冒险点。
**对比**：rank 2 用 DMA 侧 pair 拿 −256 B（−41.7%）；rank 2b 同一套机制泛化拿 −32~48 B（约 1/8~1/5）。**是 rank 2 机制的就绪后边际扩展，但并非零成本**（原 memory「顺带接上」低估了变 F + 非 stretch 臂 + 对齐门控的工作量）。

⟹ 若目标是**能耗/带宽收尾**，本项是 scale 通道最后一块冗余，值得做；若目标是**吞吐**，应转 rank 3（扩列，须捆绑）。**请确认路线（泛化 vs 独立机制）与目标后再实施。**

---

## 11. 实施记录（2026-11-13 完成并验证）

**状态**：✅ 已实施并验证。全量回归 **247/247 全绿**（20 suite、0 aborted；= rank2 后 244 + 新增 3 条 M6 用例）。日志 `build/regression-rank2b-rerun.log`。默认 RTL 已重发射（72 文件）。

### 11.1 落地形态（M1–M6）

- **M1 泛化 pair**（`WorkerBeeDmaRdOnly.scala`）：新增编译期 `pairScale: Boolean=false`（与 `pairStretch` 互斥，`pairAny=pairStretch||pairScale`）。`pairDemand`（bDma=2 编译期 / scaleB=`reg_colTileOffset` 运行期）、`pairF=4/demand`、读判据 `(kCnt*pairDemand)%4==0`、`pairNextRead` 门控（F=4 仅末复用窗清 pairRspCnt，防中途死锁）。credit 扣 F/门控 ≥F；非 stretch 臂加 `Mux(pairPushValid, cache[lineCounter], rsp/merged)`。缓存 16×pushW（scaleB=16×4B=64B）。
- **M2**：`WorkerBee.scala:38` bDmaScale 传 `pairScale=true`。
- **M3/M4**：`WbDebugModel.expScaleBAddrSeq` 与 `MxSpec.expScaleBPhysSeq` 两套独立期望序列同步加 `sbReadWin` 过滤（仅新字窗入序列）。
- **M5**：Phase0Spec scaleB 断言 + C 带宽扫描预测订正（见 11.3）。
- **M6**：QuickSpec 补 3 条（MX8-blk16 WS F2+bp、MX4-blk32 OS F2+bp、MX4-blk32 WS F2）。H1 非零延迟由既有 OsM1PerfSpec mx=true(F4,sb=11)/mix(F2,sb=11) OS+WS 覆盖；H5 对齐边界由既有 QuickSpec A级 K32/96 用例覆盖（sbRowOff%4≠0 ⟹ 退回基线，与 A级 off 逐列条件同源）。

### 11.2 实测（Phase0Spec，N=512 M=1 OS-M1，理想存储 duty=1）

| 用例 | 拍/kTile | sB读/kTile | sbAmp | 对 rank2 |
|---|---|---|---|---|
| MX8×MX8 / MX8×MX4 blk16 | **17.127**（不变） | 16→**8** | 2→**1** | sB −32 B |
| MX8×MX4 blk32 | **17.127**（不变） | 16→**4** | 4→**1** | sB −48 B |
| MX4×MX4 blk32 | **18.293**（不变） | 16→**8** | 2→**1** | sB −32 B |
| MX4×MX4 blk16（demand=4） | 18.293（不变） | 16（不变） | 1（不变） | 无可省 |
| INT8×INT4 / BF16 / WS 全组 | 不变 | 0/不变 | — | 无 scale |

**吞吐 +0 确认**（cyc/kTile 逐值等于 rank2 基线 17.127/18.293）。**每 kTile 物理读字节账（MX8×MX4）**：post-rank2 358（B256/A32/sB64/sA6）→ rank2b **326（blk16，sB64→32）/ 310（blk32，sB64→16）**。addrBadCnt==0、C elemMismatch==0 全绿。

### 11.3 C 带宽扫描预测订正（编码期，会静默算错的坑）

原 M5 误设混配 `mxPred=max(16, 8/duty)`（以为读减半后轻反压仍阵列下界）⟹ C3/C5 红（实测 21.06/22.58 > 20）。**正确模型**：pair F=2 下偶 kTile 为读窗（16 读受 duty 节流 ⟹ 16/duty 拍）、奇 kTile 为复用窗（16 缓存推、无读、全速 ⟹ 16 拍）⟹ 稳态 `拍/kTile=(16/duty+16)/2=8/duty+8`。duty=1 时=16（阵列下界，吞吐+0）；duty<1 时优于 BF16 全节流 16/duty。**含义**：rank2b 在理想存储下吞吐+0，但在带宽受压下读字节减少转化为周期减少（stallB 由 ~11/kTile 减半到 ~5.4/kTile）。

### 11.4 RTL 审计（验收 5：bDma 逐位等价）

bDma（`Wb_WorkerBeeDmaRdOnly_1.sv`，io_stretch+256bit pairCache）结构归一化比对：**唯一差异 = firtool CSE**——`kCnt+1` 被提取为共享 wire `_kCnt_T_2`（因 pairNextRead 也用 kCnt+1），reset 条件多一项 `& ~(_kCnt_T_2[0])`。代数：`(kCnt+1)[0]=~kCnt[0]` ⟹ `~(_kCnt_T_2[0])=kCnt[0]`，与已存在的 `kCnt[0]` 冗余。**机器验证**：内联 CSE wire + 删冗余项后 `folded == baseline : True`（与 rank2 逐字节相同）。审计脚本/快照 `sim/rank2b-baseline/`。bDmaScale 为新增 pairScale 实例（32bit 缓存、demand 运行期），功能由回归验证。

### 11.5 与 rank2 的关系

pairDemand=2 是 rank2 pairStretch 的特例，泛化后 bDma 逻辑逐位不变（11.4 已证）。scaleB 复用同一套缓存/credit/rsp 收齐门控/遍历推进，仅 push payload 分臂（stretch 半字加宽 vs 非 stretch 原始字）。消费端 off 完全不动（缓存字==直读字）。