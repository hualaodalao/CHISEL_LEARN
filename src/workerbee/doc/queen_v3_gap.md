# WorkerBee 配置通路 vs Queen 接口 v3.0.0 差距分析

状态：**分析交付，未实施**。用户 2026-11-18 裁定 R1–R4 全部搁置，等 Queen 侧继续迭代完善后再定范围。

本文只记录差距、证据与代价，不含任何 RTL 改动。当前 WorkerBee 源码停在 2026-11-17 的 266 绿状态（见第 1 节），`spec.md` v5.0.0 描述的是**该状态的实际行为**，不是 Queen 合规行为。两者不一致处已在 spec 中标注指向本文。

## 1. 当前已交付状态（基线）

Queen 语义变动的第一轮 WB 侧改造已完成并验证：

| 项 | 内容 |
|---|---|
| 改动面 | `WbEnum.scala`（RUN op）、`WbInterface.scala`（地址 6→10 bit）、`WorkerBeeRegIf.scala`（shadow/live 两组 + RUN + credit + 状态区迁 0x40）、`WorkerBee.scala`（cfgCredit 端口）、`WbSimCommon.scala`、`WorkerBeeRegIfSpec.scala` |
| 验证 | 全量 20 suite **266 test 全绿**（RegIfSpec 25→26），`build/regress-queen-run.log` |
| 数据通路 | 阵列/MAC/DMA/Executor/scratchpad/cvt **零改动**，金锚不变 |
| 文档 | `spec.md` v5.0.0 |

已对齐的部分（后续不必重做）：

- `WorkerBeeOp` 四编码 `{REG_WRITE1, REG_WRITE2, REG_READ, RUN}`，与 `QueenNativeOp{WRITE1,WRITE2,READ,RUN}` 一一对应。
- 地址 10 bit，与 `QueenNativeCmd.reg0Addr/reg1Addr` 等宽（`QueenDsaAdapter.scala:10-11`）。状态/IRQ 区迁到 `0x40–0x45`，避开 Queen 公共契约区 `0x080–0x0FF`，也仍落在设备私有区 `0x000–0x07F` 内。
- `reg1Addr = reg0Addr + 1` 由上游给出、设备直接使用，不自行推导。
- WRITE2 两地址同时生效、任一非法整条回 err 且都不改（`WorkerBeeRegIf.scala:199-207`）——满足 §16.8 第三条。
- RUN 不携带配置，`reg0Addr/data0` 被忽略；Queen 侧 RUN 也正是填 0（`QueenDsaAdapter.scala:79-81`）。
- RUN 在 credit 不足时**反压**（ready 低）而非回 err——满足 §16.6 硬约束"err 会导致命令永久丢失"。
- credit 只门控 RUN，QWR/QRD 不检查 credit。
- 设备私有 STATUS/IRQ 寄存器保留：§16.5 的"状态一律走端口"只约束 Queen 公共契约区，设备私有区不受禁止（Queen 不消费）。

## 2. 四项差距

### R1 RUN 快照未合并同 beat 前序写

**Queen 要求**（`queenHarewareInterface.md:1006`，§16.8）：

> 同拍内按 lane 序先应用全部写、再执行 `RUN`：`RUN` 快照必须包含同一 beat 内它前面那些 lane 的写。用 `liveReg := shadowReg` 直接取旧 shadow 值不满足。

**现状**：`WorkerBeeRegIf.scala:248` 正是 `liveReg := shadowReg`，取寄存器旧值。同 beat 的配置写落在下一拍才生效的 shadow 上，不进本次快照。

**注意方向问题**：第一轮改造把这个行为写成了软件义务（`spec.md` §6.1.0 / §8.6 / §13.1 步骤 8："RUN 不得与它所依赖的配置写同批"），并有用例钉住（`WorkerBeeRegIfSpec.scala` "dump the pre batch shadow so same batch configuration writes belong to the next submission"）。**这与 Queen 契约相反**，实施 R1 时该义务与用例都要撤掉，不是保留。

**为什么 Queen 能保证顺序**：§16.8 第二条 + `QueenDsaAdapter.scala:64` 的 beat 切分——遇切片内第一条 RUN 即截断且 RUN 是末条有效 lane。所以设备只需"把本 beat 全部写合并进快照"，不必处理"RUN 之后还有写"。

**改法与代价**：需要把 shadow 的**下一拍值**组合出来再赋给 liveReg，即按现有逐地址仲裁规则（跨 lane `lane3>2>1>0`、同 lane WRITE2 slot1 胜）对 `WorkerBeeRegister` 每个字段做 4:1 选择网络。约 12 个字段、多为 32 bit，面积不大，但新增一条 `cmd.payload → liveReg.D` 的组合路径，而 liveReg 扇出到整个数据通路配置，**时序风险需实测**，不能只看面积。

### R2 回执模型与 Queen 两个独立 ready 不兼容（主体成本）

**Queen 要求**（`queenHarewareInterface.md:978/989/990`，§16.6）：

> 写 beat（无 RUN）：`cmd.ready` 必须恒 1
> `WRITE` 不受任何 ready 门控，也不得被未取走的回执反压。
> 回执与命令同拍：`resp.valid = accept`，并行 beat 下每条被接受的 lane 各产生一条回执，回执通路必须能按 lane 区分且不丢。

**现状**（`WorkerBeeRegIf.scala:163-188`）：4-slot `respPending/readPending/respData/respErr` 位图缓存一批；`acceptBatch`（:168）在任何回执未消费时把**四路 ready 全拉低**（:180）；`io.resp` 是**单条 Stream**，按 lane 升序串行输出（:183-188），且回执是接受拍的**下一拍**才 valid。

三处直接冲突：写被回执反压、回执不是同拍、单条 resp 无法按 lane 并行区分。

**改法与代价**：这不是补丁，是换契约。

- 删除 `respPending/readPending/respData/respErr/acceptBatch/respLane` 整套批缓存。
- `WorkerBee.io.resp` 从 `master(Stream(WorkerBeeResp))`（`WorkerBee.scala:8`）改为按 lane 的回执向量（`Vec(4, Valid(...))`，或 Queen 保证写回执恒可收的 Decoupled）。**顶层端口形状变更**。
- `spec.md` 整个 §6.2（单批 slots/pending、升序串行、最后 fire 当拍不接新批）作废重写；§6.1.1 的 ready 门控表、§9.2 的批间衔接同步作废。
- READ 也不得被设备忙闲拒绝，只由 Queen 的 `ready_rd` 反压 ⟹ 设备侧不再有自己的读准入状态。

**TB 影响面**（这是 R2 的真实工作量所在）：

| 文件 | 影响 |
|---|---|
| `WbSimCommon.scala` | `transactReg`/`readRegister`/`prepareStart`/`launchStart`/`pokeStatusRead`/`pokeIrqRead`/`pollRegisterStep` 全部基于"发命令→等下一拍 resp.valid→消费"模型，需重写 |
| `WorkerBeeQuickSpec.scala:674` | `WorkerBeeCaseSpec` 抽象基类在此，是 QuickSpec / LoadWSpec / Phase0Spec / ManualSpec / SimCase 的共享 runner，改一处波及五个 suite |
| `WorkerBeeRegIfSpec.scala` | 26 例中大部分钉的是批/pending/背压/升序契约，直接作废；只有地址译码、字段往返、IRQ 仲裁/W1C、错误码首码保持等与回执模型无关的意图可保留 |
| `WorkerBeeDmaErrorSpec` / `WorkerBeeOsM1PerfSpec` / `WorkerBeeSimCaseIntSpec` / `WorkerBeeSimCaseMxSpec` | 直接使用 `io.resp` 与上述 helper，需适配 |

数据通路与金锚不受影响：这些 spec 的**期望值**来自张量语义，改的只是寄存器 plumbing。

### R3 端口形态：cfgCredit → readyStart

**Queen 要求**（`queenHarewareInterface.md:957/972`，§16.5/16.6）：设备输出 `ready_start`（Bool），由 credit 与命令队列共同决定，**不得依赖 `cmd.valid`**（否则与 Queen `out.valid` 组合环）；credit 计数不外露，不设 `CFG_CREDIT`。Queen adapter 端口已是 `val readyStart = Input(Bool())`（`QueenDsaAdapter.scala:26`），`cfgCreditWidth` 参数已删。

**现状**：`WorkerBeeRegIf.scala:63/83` 与 `WorkerBee.scala:20/539` 外露 `cfgCredit: UInt(1.W)`，值为 `Mux(inFlight, 0, 1)`。

**评估**：深度为 1 时 1-bit UInt 与 Bool 信息等价，且该值**不依赖 cmd.valid**，语义已合规；差的是端口名与类型。属小改。

附带：`runPresent/runGrant`（:172-175）用 valid 做同拍多 RUN 仲裁。§16.8 第二条明确"设备不必再对同拍多启动做仲裁"，可删；删后 RUN 的 ready 只由 `readyStart` 决定，也顺带消除 cmd.ready 对 valid 的依赖。

### R4 缺 completion 与强 idle 端口

**Queen 要求**（`queenHarewareInterface.md:953-965`，§16.5）：四个必须外露的握手端口 `ready_start` / `completion` / `idle` / `resp`，是 Queen 获取设备状态的**唯一**通路。

- `completion`：`Decoupled{sequence, error, code, hwSignal}`，必须可背压保持，不得用单拍 pulse。
- `idle`：**强 idle** = 命令队列空 + 无执行 + 无未完成内存请求/响应 + 无未接收 completion。`busy=0` 或输入 FIFO 空都不够。

**现状**：`WorkerBee.io` 只有 `status`（busy/inFlight/progress/occupancy）、`irq`、`perf`。两个端口都没有。完成目前只经 `IRQ_RAW[0]` 粘滞位 + `respForTracing` 观测，而 §16.5 恰恰禁止用寄存器读回替代端口（last-value 采样会丢同拍多完成）。

**改法与代价**：新增 completion 队列（可背压保持）+ 强 idle 归约。强 idle 需要汇总 RegIf 命令队列、Executor、五个 DMA 的在途请求/响应、以及 completion 未被接收的状态——是跨模块的新汇总逻辑，不是 RegIf 内部改线。

**未定项**：`sequence` 与 WB 现有 `tag`(8 bit) / Queen `cmdTag`(8 bit) / `swTag`(xlen) 的映射尚未定义。Queen 侧备忘把"设备相关映射（WorkerBeeCmd/DMA 配置前端）、completion/tag 归档、fault、QRD、端点控制器"列为剩余工作，所以这一项**两侧都还没定**，不宜单方面先做。

`hwSignal`：用户 2026-11-18 裁定"先不做，等 queen 侧继续迭代完善"。方向已裁定为 (a)（设备 RUN 接受拍锁存、随 completion 回传、error=1 不得携带有效目标），但寄存器地址/位域/锁存逻辑本轮不开。

## 3. 代价分布与 sequencing 建议

| 项 | 规模 | 主要风险 | 依赖 |
|---|---|---|---|
| R1 | 小（1 文件，一个字段级写合并网络） | `cmd→liveReg.D` 新组合路径的时序，需综合实测 | 无 |
| R3 | 很小（2 文件改名 + 删仲裁） | 无 | 无 |
| R2 | **大**（顶层端口形状 + 7 个 test 文件 + 共享基类 + spec 两章作废） | 回归面广；但金锚不变，失败可归因到 plumbing | 无 |
| R4 | 中大（跨模块强 idle 汇总 + completion 队列） | sequence/tag 映射两侧未定 | **Queen 侧映射先定** |

建议顺序（待 Queen 侧完善后由用户裁定）：

1. **R3 + R1** 先做：小、无外部依赖、消除文档点名的不合规，且 R1 撤掉一条写反的软件义务。
2. **R2** 单独一轮：它是唯一动顶层端口形状与共享 TB 基类的项，独立成轮便于把回归失败归因到 plumbing 而非数值。
3. **R4** 最后：等 Queen 侧把设备相关映射与 completion/tag 归档定下来，避免先做再返工。

R1–R4 全部**不动数据通路**：阵列、MAC、DMA、Executor、scratchpad、输出转换与金锚期望值零 diff。这是四轮都可以承诺的边界。

## 4. 本轮遗留的小项

- ~~`WorkerBeeQuickSpec.scala:1014` 过期注释~~ 已订正为 `0x40`/`0x41..0x45` + RUN 触发（S2 迁移漏项，仅注释，无 RTL 影响）。
- 默认交付 RTL 尚未按本轮源码重发射，`build/` 下的 Verilog 仍是改造前版本。R1–R4 既然搁置，重发射时机由用户定：现在发射一次、或等 Queen 对齐落地后一次发射，避免重复。
- `spec.md` v5.0.0 的 §6.1.0 / §6.8 已加注指向本文，标明"同批写不进本次快照"与"回执批缓存"是**当前实现行为、已知与 Queen v3.0.0 不一致**，不是 settled 设计选择。

## 5. 证据索引

| 主张 | 出处 |
|---|---|
| RUN 快照须含同 beat 前序写，且点名 `liveReg := shadowReg` 不满足 | `src/queen/doc/queenHarewareInterface.md:1006` |
| 写 beat cmd.ready 恒 1 | 同上 `:978` |
| WRITE 不得被未取走回执反压 | 同上 `:989` |
| 回执与命令同拍、按 lane 区分且不丢 | 同上 `:990` |
| ready_start 不得依赖 cmd.valid | 同上 `:972` |
| 四个必须外露端口 / 强 idle 定义 / 不设 CFG_CREDIT | 同上 `:953-965` |
| 同 beat ≤1 RUN 且为末条，设备不必仲裁 | 同上 §16.8 第二条 |
| Queen adapter 端口已是 readyStart: Bool | `src/queen/scala/main/QueenDsaAdapter.scala:26` |
| Queen 原生地址 10 bit、RUN 填 0、beat 遇 RUN 截断 | 同上 `:10-11`、`:79-81`、`:64` |
| adapter 只在 out.fire 时推进 base（故设备反压不丢命令） | 同上 `:92-94` |
| nativeWidth 默认 = instructionLanes = 4 | `src/queen/scala/main/QueenConfig.scala:10-11,23` |
| WB 现状：liveReg 取旧 shadow | `src/workerbee/scala/main/WorkerBeeRegIf.scala:248` |
| WB 现状：批缓存与全 lane ready 门控 | 同上 `:163-188`、`:180` |
| WB 现状：cfgCredit UInt(1.W) | 同上 `:63`、`:83`；`WorkerBee.scala:20`、`:539` |
| WB 现状：runGrant 用 valid 仲裁 | 同上 `:172-175` |
| WB 现状：resp 为单条 Stream | `src/workerbee/scala/main/WorkerBee.scala:8`、`:27` |
| 共享 TB 基类位置 | `src/workerbee/scala/test/WorkerBeeQuickSpec.scala:674` |
| 266 绿基线 | `build/regress-queen-run.log`（Total 266 / Suites 20 / failed 0 / SBT_EXIT=0） |