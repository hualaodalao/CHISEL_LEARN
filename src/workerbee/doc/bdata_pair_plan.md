# 阶段 3（rank 2）改动 plan：W4A8 混配 B 数据同字复用

- **状态**：✅ 已实施并验证（2026-11-12，用户「确认，开始实施」批准；实测见 §11）
- **日期**：2026-11-12
- **前置**：A 级已落地并验证（244/244 全绿，`src/workerbee/doc/scaleb_a_plan.md` §9）
- **范围**：**只作用于 W4A8 混配**（MX8×MX4 与 INT8×INT4）。纯格式 `bAmp` 已 1.00，无可省。
- **收益口径**：**吞吐 ≈1.00×（不承诺吞吐）**，B 物理读字节 512→256 B/kTile（**−41.7%**，A 级后总账 614→358）。这是**能耗项**。

---

## 1. 机制与几何（已查证）

混配下 B 走 stretch：逻辑拍 = 16B 半字片（32 个 fp4/int4 = 一个阵列 K 窗口的一行），物理读 = 1 次 32B 对齐读，DMA 内半字选择 → 顶层 LUT/sext 加宽成 32B 阵列拍。

| 量 | 值 | 出处 |
|---|---|---|
| 列步进 `bRowOff` | `K·bEbits/8` = 256 B（K512、fp4） | `WbDebugModel:633` |
| 窗口步进 `bKTileOffMix` | `aKpb·bEbits/8` = **16 B** | `WbDebugModel:692` |
| 端口字 | 32 B | `pushW = totalN*bW = 256 bit` |
| 走序 | `(nt, w, j)`，**列 j 最内层** | `WbDebugModel:693-699` |

⟹ 窗口 w 与 w+1 共用同一个 32B 字，但**复用距离 = 16 拍**（列在最内层）⟹ 单 holding register 命中率恒 0，必须 **16 字（每列一个）**。实测 `bAmp = 2.00` 即此。

**有利前提（已由 errCode 8 保证）**：混配要求 `K%64==0` 且 `bAddr%32==0`（`WorkerBeeCalcConfig:300-306`）⟹ 窗口对**恒对齐到端口字**，无需处理跨字边界。

## 2. 路线订正：为什么改回 DMA 侧（推翻 2026-11-11 的 pop 侧推荐）

上次推荐 pop 侧的理由是「feeder 每槽已存 bWindow，存储已存在、无需新增」。**细算后这句是错的**：槽数不变，但每槽要装**两个**窗口 ⟹ `bWindow` 必须 32B→64B。

| 路线 | 新增存储 | 波及面 | 纯格式影响 |
|---|---|---|---|
| pop 侧（feeder 拆生命周期） | feeder 16×2×32B = **+1 KB**；B buffer 256→512 bit = **+2 KB**（或深度减半 ⟹ 预取深度减半） | Scratchpad / Feeder / Sequencer / Executor 全链路；`bIn`/`scaleBIn` 严格 1:1 配对要拆 | **有**：B buffer 与 feeder 为纯/混配共用 |
| **DMA 侧 16 字配对缓存（本 plan）** | 16×32B = **+512 B** | 只在 `bDma` 的 stretch 分支 | **无**：B buffer / feeder / 加宽 LUT 全不动 |

DMA 侧存储只要一半、波及面小一个量级，且**加宽逻辑不用改**（仍 16B→32B，`fp4SliceToMx8Beat`/`int4SliceToInt8Beat` 原样）。故选 DMA 侧。

⚠️ pop 侧路线并未作废：它仍是 **rank 2b（scaleB 同字复用）** 的候选路线，届时单独论证。

## 3. 设计

**核心：遍历序与 push 序完全不变（仍 1 逻辑拍 1 push、`(w,j)` 列最内层），只把「奇数窗口的读」换成「命中缓存」。**

新增编译期常量 `pairStretch: Boolean = false`，仅 `bDma` 传 true；内部再按运行期 `io.stretch` 门控 ⟹ 其余三个读实例（aDma / aDmaScale / bDmaScale，stretch 恒 tie false）经 DCE 后**发射 RTL 逐位不变**，bDma 在纯格式下也走原路径。

| 项 | 现状 | 改后（stretch=1 时） |
|---|---|---|
| 读发起 | 每逻辑拍 1 读 | **仅偶数窗口**（`kCnt(0)===0`）发读；奇数窗口不发 |
| 半字选择 | `selBits` FIFO 随在途读配对 | **确定性**：偶→低半字、奇→高半字 ⟹ `selBit = kCnt(0)`，pair 模式下 FIFO 旁路 |
| push 源 | 恒 `rsp` 驱动 | 偶窗口 = `rsp`；奇窗口 = `cache(j)` |
| push 数 | 1/逻辑拍 | **不变**（1/逻辑拍） |
| credit | 读锚定，1 读扣 1 | 1 读**扣 2**、门控 `credit >= 2`；`bufPopFire` 仍 +1 ⟹ 一次读为两个 push 预留空间 |
| 遍历推进 | `finalReqFire`（= req.fire） | 偶窗口 = `req.fire`；奇窗口 = 缓存 push 拍（curAddr 在奇数窗口不用于发读，按 push 推进安全） |
| 缓存 | — | 16 entry × 32B，**按列号 j 直接索引**（走序确定，无需 tag/比较） |

**为什么不需要改 `reg_kTile`/`colTileOffset`**：遍历仍按全量窗口数与 16B 步进走，只是奇数窗口不发读 ⟹ **不触碰「折半 kTile」**（见 §4）。

### 3.1 必须处理的冒险

**H1 — 缓存写先于读（关键，编码期细化）**：偶数窗口的 16 个读是**流水发起**的（credit 允许多拍在途），遍历在 `req.fire` 就推进 ⟹ 进入奇数窗口（`kCnt` 已切奇）时，偶数窗口的 rsp 可能尚未返回。
⚠️ **细化：冒险不只是「缓存未写满」，更是「push 源误选」**。若按当前 `kCnt` 奇偶决定 push 源，则滞后到奇窗的偶应答会被误当奇推（取缓存高半字而非 rsp 低半字）⟹ 数据错 + 双推。
**处置（取代原 per-entry valid 方案）**：
- **缓存写与偶推一律由 rsp 侧驱动，不看当前 `kCnt`**：pair 模式下每个 rsp 都是偶窗读应答（奇窗不发读）⟹ 缓存写使能 = `pairActive && rsp.fire`，写索引 = `pairRspCnt`（收齐序 0→16）；偶推（`alignedRspPush`）恒取 rsp 低半字。
- **奇推门控 = 本偶窗 16 个 rsp 全收齐**：`pairEvenDone = pairRspCnt >= totalN`。`pairPushValid = pairActive && pairOdd && sTRANSFER && pairEvenDone`。收齐前奇窗遍历**保持**（`advFire=pairPushFire=false`），滞后偶应答继续经 `alignedRspPush` 推低半字、写缓存。
- **push 源选择依据 = `pairPushValid` 而非 `pairOdd`**：`pairPushValid` 真 → 缓存高半字；否则 rsp 低半字。两者时间互斥（`pairEvenDone` 后无偶 rsp）。
- `pairRspCnt` 在 `io.start` 与奇窗末拍（`advFire && pairOdd && lineCounter===lineTarget-1`）清零；`reg_kTile` 偶（errCode 8 保证 K%64==0）⟹ 末窗恒奇、n-tile 切换前已清零。缓存内容无需清（每偶窗全重写后才被奇窗读）。

**H2 — credit 双扣的同拍竞态**：现有 `finalReqFire & bufPopFire` 同拍分支是「不增不减」；改成扣 2 后同拍应为 **−1**。漏改会溢出或饿死。

**H3 — 奇数窗口的 push 节奏**：缓存命中可连续每拍 1 push，但受 buffer 空间约束。因 H2 已在读时预留 2 槽 ⟹ 奇数窗口 push **无需再查空间**，否则会与预留重复计数。

**H4 — `start` 重载/flush**：缓存 valid 必须与 `gPhase`/`inflCnt`/`selCnt` 同拍清零，否则跨 run 泄漏陈旧字。

## 4. 与既有禁令的关系（必须显式对齐）

`WorkerBee.scala:108-111` 明令：**「不可按源字数折半（K/64）并在 push 侧连推 2 拍：一个 fp4 源字跨 2 个 loadW 窗口，连推会产生行交错序（权重错序、列对共享 scale）」**。

本 plan **不违反**：
- **不折半 kTile**：`reg_kTile` 与 16B 步进原样，遍历仍逐窗口走。
- **不连推 2 拍**：一次读对应的两个 push 相隔 **16 拍**（分属两个窗口轮次），push 序仍是 `(w,j)` 列最内层 ⟹ 无行交错。

禁令针对的是「读一次就地连推两拍」，本 plan 是「读一次、16 拍后从缓存推第二拍」，正是它要求的顺序。

## 5. 改动清单

| # | 文件 | 改动 |
|---|---|---|
| **N1** | `WorkerBeeDmaRdOnly.scala` | 新增 `pairStretch: Boolean = false`；16×32B 缓存 + 16 valid 位；读门控加偶数窗口条件；push 源加缓存臂；credit 扣 2/门控 ≥2；遍历推进加缓存 push 拍；`selBit` 在 pair 模式取 `kCnt(0)`；start/flush 清 valid |
| **N2** | `WorkerBee.scala:31` | `bDma` 例化传 `pairStretch = true`（一行） |
| **N3** | `WbDebugModel.scala:689-701` | 混配臂 `expBAddrSeq` 的物理序列：由「每逻辑拍 1 个对齐地址」改为「**仅偶数窗口**各列一个对齐地址」（奇数窗口无读） |
| **N4** | ~~`WorkerBeeSimCaseMxSpec.scala`~~ | **已查证：不是改动点**。该 suite 的 `expBAddrSeq`（`:291-306`）用 `bRowOff=K`、`kSliceStep=32`，是**纯 MX8 几何**；14 处 MXE2M1/INT4 全为**输出量化格式**或 config `supportedFmts`，无 B 输入混配用例 ⟹ 混配 B 期望序列**只有 WbDebugModel 一处**（与 A 级的 scaleB 双套情况不同） |
| **N5** | `WorkerBeePhase0Spec.scala` | 混配 `bAmp`/`bByteAmp` 2.00→**1.00**；`B读/kTile` 16→**8**；拍/kTile **不变**（≈17.13）；`predPort` 不变 |
| **N6** | 用例 | 混配 K=64/128/512 × {MX,INT} × {OS,WS} × 反压；**缓存写先于读（H1）须有非零延迟响应用例覆盖** |

**不动**：B buffer 宽度/深度、feeder、Sequencer、Executor、加宽 LUT、scaleB/scaleA 通路、errCode、寄存器映射、C 金锚。

## 6. 不变式

1. **push 序与 push 数逐拍不变** ⟹ `bPop/scaleBPop` lockstep 断言（`Executor:427`）与预蓄门控（`scaleBOcc >= totalN`）不受影响。
2. **选中半字与加宽结果逐值相同** ⟹ C 张量值不变，OS 与 WS 两路皆然。
3. **纯格式（stretch=0）逐拍不变**：pair 逻辑全部由 `io.stretch` 门控。
4. **WS 同步受益、功能不变**：WS 本就阵列界，吞吐不变，只省字节。
5. 只**减少读**、不改 push 节奏 ⟹ 不触发 loadW 预取节奏不变式的风险面（[[workerbee-loadw-preload-rhythm-invariant]]）。

## 7. 验收

1. **既有 244 test 不得转红**（新增用例则总数增加）。20 suite 全量，判完成看 `Total number of tests run` 与 `Suites: completed 20`。
2. 混配 `bAmp` **2.00→1.00**、`B读/kTile` **16→8**；纯格式 `bAmp` 恒 1.00 不变。
3. **拍/kTile 不变**（混配 ≈17.13、MX4-blk16 ≈18.29、WS 全组不变）——本阶段**不承诺吞吐收益**，若变差即为回归。
4. `addrBadCnt == 0`、C `elemMismatch == 0`。
5. **非 bDma 实例发射 RTL 结构不变**：用 A 级确立的**结构归一化比对**（裸 MD5 会因模块名/dedup 编号位移而必变，见 `scaleb_a_plan.md` §9.6）。
6. **H1 专项**：非零响应延迟 + 反压下混配用例必须绿（理想零延迟存储会掩盖写先于读冒险）。

## 8. 非目标

- **rank 2b（scaleB 同字复用）**：吞吐 +0、字节 −5.2%，机制就绪后顺带接上，不在本次。
- 纯格式 B 通路、B 端口加宽、扩列（rank 3）、WS 权重双缓冲（rank 4）。
- 外部真实 DRAM 时序模型（用户裁定暂缓）。

## 9. 风险与回退

| 风险 | 处置 |
|---|---|
| **H1 缓存写先于读** | per-entry valid + 暂停；验收 6 强制非零延迟用例 |
| **H2 credit 双扣竞态** | 同拍分支显式改 −1；用反压用例压 |
| 碰已验证的 W4A8 stretch 通路 | pair 逻辑全部 `io.stretch` 门控 + 编译期 `pairStretch` 隔离；纯格式与其余三实例 RTL 结构不变（验收 5） |
| **收益是能耗、本仓无功耗仿真能力** | 只能交付**读字节 −41.7%** 这一端口级事实，**不得声称能耗百分比**（需 ASIC + SAIF/FSDB + 真实负载，见报告 G2） |
| 改动面膨胀 | 本 plan RTL 仅 2 文件；超出即停下重新对齐 |

**回退**：N2 一行改回即恢复原行为（`pairStretch` 默认 false，N1 全部逻辑被 DCE）。

## 10. 立项判断（供决策）

**收益**：混配 B 读字节 −41.7%（端口级事实）；**吞吐 +0**。
**代价**：+512 B 缓存、DMA stretch 分支中等改动、触碰已验证 W4A8 通路、4 个冒险点。
**对比**：A 级用 6 处小改拿到 1.46–1.70× 吞吐；本阶段用更大的改动拿 0 吞吐。

⟹ 若目标是**吞吐**，本阶段不是下一步的最优选择（rank 3 扩列才有 2.01×/2.95×，但必须捆绑）。若目标是**能耗/带宽**，本阶段是清单里性价比最高的一项（同一套机制还能顺带接上 rank 2b）。**请确认目标后再实施。**

---

## 11. 实施记录与实测（2026-11-12）

**改动落地（N1–N6）**：
- N1 `WorkerBeeDmaRdOnly.scala`：新增编译期 `pairStretch`（`require(!pairStretch || cfg.hasMx4)`）；16×32B `pairCache` + 5-bit `pairRspCnt`；`pairActive=io.stretch`（运行期门控）、`pairOdd=kCnt(0)`、`pairEvenDone=pairRspCnt>=totalN`、`pairPushValid`、`advFire`。读门控加 `!pairOdd && ostCredit>=2`；credit 扣 2（同拍 pop 净 −1）；缓存写/偶推由 rsp 侧驱动；奇推取缓存高半字。全部 `if(pairStretch)` 编译期隔离。
- N2 `WorkerBee.scala:31`：`bDma` 传 `pairStretch = true`（一行）。
- N3 `WbDebugModel.scala` 混配臂：`expBAddrSeq` 窗口循环改 `by 2`（仅偶窗发读）。数据按地址取整字（`beatAtB`），奇窗高半字由硬件提取，C 金锚不变。
- N4：查证非改动点（MxSpec 纯 MX8 几何）。
- N5 `WorkerBeePhase0Spec.scala`：混配 `bAmp/bByteAmp` 断言 2.0→1.0（A2/A3）+ 头注。
- N6 `WorkerBeeOsM1PerfSpec.scala`：`params` 加 `mix/intMix`；新增 2 条**非零 B 延迟**混配用例（MX/INT，N48 K128，b=5/7，OS+WS）；B 计数断言改混配感知（纯格式 `bKTiles==aKTiles` 逐值不变）。

**H1 处置较 plan 原稿细化**（见 §3.1）：原稿「per-entry valid」改为「**偶窗 16 个 rsp 全收齐门控（pairEvenDone）+ 缓存写/偶推由 rsp 侧驱动不看当前 kCnt**」。原因：偶窗读流水发起，偶应答会**滞后到 kCnt 已切奇**；若按当前 kCnt 选 push 源，滞后偶应答会被误当奇推（取缓存高半字）⟹ 数据错+双推。改后 push 源选择依据 = `pairPushValid`（非 `pairOdd`），滞后偶应答仍走 rsp 低半字。

**全量回归 244/244 全绿**（20 suite、0 aborted；新增 2 条混配用例在 OsM1PerfSpec 单一 test 方法内迭代，故 test 计数仍 244）。日志 `build/regression-stage3.log`。

**实测（Phase0Spec，N=512 M=1 OS，理想存储）**：

| 用例 | B读/kTile | bAmp/bByteAmp | cyc/kTile | predPort | predDram |
|---|---|---|---|---|---|
| MX8×MX4 blk16 K512 | 16→**8** | 2.00→**1.00** | **17.127**（不变，8769 拍） | 16（不变） | 13→**11** |
| MX8×MX4 blk32 K512 | 16→**8** | 2.00→**1.00** | **17.127**（不变） | 16 | 13→**11** |
| INT8×INT4 blk16 K512 | 16→**8** | 2.00→**1.00** | **17.127**（不变） | 16 | 11→**9** |
| MX8×MX4 K1024 | 16→**8** | **1.00** | 17.036（不变） | 16 | →11 |
| MX4×MX4 blk16（纯） | 16（不变） | 1.00（不变） | **18.293**（不变） | 16 | 19（不变） |
| BF16 WS M=1/16（纯） | 不变 | 1.00 | 34.052 / 49.063（不变） | 16 | 不变 |

⟹ **吞吐 +0（验收 3 达成）**，混配 B 物理字节 512→256 B/kTile；A 级后总账 614→**358（−41.7%）**。

**H1 专项（验收 6，非零延迟）**：MX/INT W4A8 混配 N48 K128，b=5/7 延迟 + randomReady。OS 侧 `B.logicalReq=96`（=3 nTile×2 偶窗×16）、`B.push=192`（=3×4 窗×16，推数不变）、`B.reqWait=45`（读被节流⟹应答确实滞后到奇窗）；**OS/WS 两路独立黄金对比全部通过**（元素=48）。证明滞后偶应答不误选缓存、奇推等齐 16 字。

**默认 RTL 重发射结构审计（验收 5，72 文件）**：RdOnly 由 3 变体分裂为 4——`_1`=bDma（新 pair 变体，pairCache 80 行/pairRspCnt 24 行）、base=aDma、`_2`=scaleA、`_3`=scaleB。
- **pair 逻辑物理隔离**：`pairCache/pairRspCnt` 仅出现在 `_1`(bDma)；base/_2/_3 全 0。**gather（gPhase/loReg）四变体均保留** ⟹ 基址非对齐回退路径不动。
- **scaleA/scaleB 逻辑逐位不变**：去注释 + 把中间线 `reqValidBase` 还原为 `io_dmaExtRdIF_req_valid_0` 后与 pre **完全一致**（唯一差异 = N1 把 req.valid 基础式抽成命名 val `reqValidBase` 导致的改名，纯 cosmetic）。
- **aDma 常量特化**：pre base 是 aDma+bDma 共享体（`io_isA` 运行期端口、含 A/B 双遍历）；bDma 分裂后 aDma 独占模块，`isA=true` 被常量传播 ⟹ B 路径（lastBTileStartAddr/osAnchor 23/10→0/0）DCE、`io_isA` 端口消失（156→61 内部名）。**与 A 级 scaleA/scaleB 特化同类、语义保持**（aDma 恒 isA=true），A 路径 + gather 完整。
- **Queue64_UInt256/StreamFifo/Scratchpad**：firtool 端口提升/内联（`io_enq_ready=~full` 内联进 `do_enq`），原始 diff 证实逻辑等价，非本次改动引入。
- **Wb_WorkerBee 顶层**：bDma 例化引用新 pair 模块（+2 内部名），预期。

⚠️ 验收 5 原判据「非 bDma 实例结构不变」需按 A 级先例放宽为「**逻辑不变**」：bDma 改动必然触发 dedup 分裂 ⟹ aDma 常量特化不可避免；scale 仅改名。功能正确性由 244/244 回归背书。

**收益口径纪律**：只交付**端口级读字节 −41.7%** 这一事实，**不声称能耗百分比**（本仓无功耗仿真，需 ASIC + SAIF/FSDB + 真实负载）。