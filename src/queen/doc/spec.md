# Queen 硬件规格

文档版本：`v1.2.0`

修订日期：`2026-10-30`

## 版本控制

### 文档版本规则

采用 `v主版本.次版本.修订号`：

- 主版本：外部可见规格契约发生不兼容变更。
- 次版本：新增兼容功能描述或规格章节，不改变既有契约。
- 修订号：修正文案、排版或引用，不改变功能契约。

文档版本不等于 RTL 版本、芯片型号或寄存器值。每次发布同步更新首页版本、日期和修订记录；日期使用 `YYYY-MM-DD`。影响功能契约的修改须注明相关 Feature ID；章节移动不改变 Feature ID。

### 修订记录

| 版本号 | 日期 | 修改内容 | 相关 Feature ID |
|---|---|---|---|
| v1.2.0 | 2026-10-30 | GEMM/DMA 由各自 FIFO 改为共享命令 ring（各持游标，槽在两侧游标都越过后回收），SCHED 改为 1 级暂存；`inst.ready` 按端点资源预约；`FENCE`/`WAIT_EVENT`/`WAIT_TIMELINE` 的 `endpoint=SCHED` 定为非法编码 | QN-F001、QN-F002、QN-F003、QN-F004、QN-F005、QN-F015、QN-F016、QN-F017 |
| v1.1.0 | 2026-10-30 | 增加 GEMM/DMA 硬件完成信号源，event/timeline 改为软件 signal 与硬件完成双更新源；软件 signal 保持不变 | QN-F023、QN-F029 |
| v1.0.0 | 2026-10-30 | 首次发布。依据 `queenISA.md` 与 `queenHarewareInterface.md` 建立 Queen V2 目标契约，并按当前 RTL 标注实现状态 | 全部 |

## 1. 文档说明

### 1.1 目的

本文定义 Queen 的外部可见行为、设计边界和交付配置。Queen 目前只有命令派发数据通路落地，指令语义、Scheduler 同步资源、通知与 fault 通路均属于目标契约，尚未实现。本文把这两部分分开陈述：已实现部分按当前 RTL 的实际行为写，目标部分按 `queenISA.md` 与 `queenHarewareInterface.md` 的契约写，并在每项功能上标注实现状态。路线图、性能优化计划和未定事项不作为当前能力。

### 1.2 范围

本文包含：

- Scalar Core 与 Queen 之间的指令、源操作数、异步写回、退休、trap 和中断接口。
- Queen 内部按 endpoint 组织的命令队列，以及 GEMM/DMA adapter 到原生 DSA 接口的转换职责。
- `QCTRL` 八条有效命令（FENCE、WAIT_EVENT、WAIT_TIMELINE、SIGNAL_TIMELINE、SIGNAL_EVENT、TRACE_TAG、POLL_EQ、ATOMIC_RMW）的目标语义与执行位置；`cmd=4'b0111` 为保留非法编码。
- Scheduler 的 timeline/event 同步资源、完成通知计数与中断、异步 fault、polling/atomic 系统内存主接口。
- event/timeline 的两类更新源：软件 `SIGNAL_*` 指令，以及 GEMM/DMA 完成驱动的硬件信号源。
- 编译期参数、复位行为、异常上报与已知实现限制。

本文不包含：

- RISC-V 标量核本身的指令集与流水线行为，仅定义其对 Queen 的接口义务。
- GEMM、DMA、VPU 三个 DSA 的内部实现，仅定义 Queen 依赖的接口契约。
- 中断控制器、地址映射表、操作系统与驱动模型。
- 资源申请、分配与回收协议。软件通过 Queen 资源寄存器申请 event/timeline，具体寄存器定义不在本文范围内，`queenISA.md` 第 8 节同样将其留给寄存器表。

### 1.3 术语与规范性用语

“必须”表示强制契约；“应”表示推荐要求；“可”表示可选能力。“不适用”表示模块确实无该项；“待确认”表示信息或验证尚未完成。

| 术语/符号 | 定义 | 单位或适用范围 |
|---|---|---|
| Queen | NPU core 内负责 DSA 命令派发与共享同步资源的硬件单元 | 本文对象 |
| Scalar Core | 单线程标量控制 CPU，Queen 的上游 | 外部对端 |
| endpoint | 指令目标单元，2-bit 编码 `inst[31:30]` | 00 GEMM / 01 DMA / 10 VPU / 11 SCHED |
| lane | 同一周期内 CPU 可并行提交的指令通道 | 0..instructionLanes-1，编号小者为程序序在前 |
| entry | 一条已接受指令及其操作数的存储单元 | 每条指令一个 |
| slot / 包 | 一次原子入队的 lane 集合 | 一个 slot 最多含 instructionLanes 条指令 |
| 次拍操作数 | `inst.fire` 次一周期返回的 `rs1/rs2` | 相对 `inst.fire` 周期 +1 |
| native command | DSA 自身的命令格式 | GEMM `WorkerBeeCmd` / DMA `FbCmd` |
| 强 idle | 设备与 adapter 均无在途工作的状态 | 见 5.6 与 `queenHarewareInterface.md` 5.3 |
| notify pulse | 无握手的单周期完成通知 | 四来源，每周期各计一次 |
| QRD | 读取一次 DSA 寄存器的 Queen 指令 | 唯一需要 CPU 等待响应的指令 |

### 1.4 参考资料与适用基线

| 资料 | 版本或路径 | 用途 |
|---|---|---|
| Queen ISA | `src/queen/doc/queenISA.md` | 指令编码、语义与软件可见行为 |
| Queen 硬件接口 | `src/queen/doc/queenHarewareInterface.md` | 顶层接口形状与握手契约 |
| 规格模板 | `src/doc/spec_template.md` | 本文结构与 Feature ID 规则 |
| 派发数据通路 RTL | `src/queen/scala/main/QueenDsaDecoder.scala` | 已实现行为的唯一事实源 |
| 编译期参数 | `src/queen/scala/main/QueenConfig.scala` | 参数与校验 |
| 遗留 V1 顶层 | `src/queen/scala/main/Queen.scala`、`QueenElaborate.scala` | 仅用于说明发射入口现状，不是 V2 契约 |

## 2. 模块概述

### 2.1 定位与职责

Queen 处在 Scalar Core decoder 与 GEMM、DMA 两个 DSA 之间，承担三件事：

1. 接收 Scalar Core 按 endpoint 分流后送来的指令，把指令与其次拍源操作数合并成一条完整的命令条目。
2. 按 endpoint 把条目分入 GEMM/DMA 共享的命令 ring 与 SCHED 的单级暂存，保持同端点内的程序序；SCHED 侧不执行条件等待，派发路径不被等待阻塞。
3. 在目标契约中，把这些条目解释为 DSA 原生命令、同步操作、polling/atomic 内存事务和完成通知，并维护 fault 状态与中断。

VPU 不属于 Queen 的调度范围。目标为 VPU 的配置、RUN、WAIT 和 TRACE 指令由 Scalar Core decoder 直接送入 VPU 前端；Queen 不建立 VPU 命令队列，也没有到 VPU 的命令输出接口。Queen 与 VPU 之间只有同步资源查询、完成通知和错误上报三个旁带接口。

当前交付的 RTL 只覆盖第 2 条的一部分：指令合并、按 `inst[31:30]` 分流、GEMM/DMA 共享 ring、SCHED 单级暂存与出口握手。第 1 条中的合法性检查与第 3 条的全部内容尚未实现。

### 2.2 系统边界

| 外部对象 | 与本模块的交互 | 外部承担的责任 |
|---|---|---|
| Scalar Core decoder | 输入 `inst`（`pc`+`inst`）、次拍 `rs1/rs2`；输出 `asyncRd`、`qrdRetire`、`trap`、`irq`、`idle` | 按 endpoint 分流，VPU 指令不得送入 Queen；已 fire 指令的源操作数必须在约定周期返回且不得撤销 |
| GEMM DSA | Queen 输出 native 命令与读请求，接收 response、completion、fault、强 idle | 提供命令接收握手、完成与错误回传、真实 idle |
| DMA DSA | Queen 输出 `FbCmd`，接收 `FbDone`、occupancy、busy | 提供 tag 回传、错误回传与资源占用查询 |
| VPU | 输入 `vpuSyncQuery`、`vpuNotifyPulse`、`vpuFault`；输出 `vpuSyncValue` | 自行保证 VPU 侧指令顺序、WAIT 阻塞与标量写回/退休 |
| 系统内存 | Queen 输出 `memReq`，接收 `memResp` | 提供 polling/atomic 事务所需的原子语义与时序 |
| 时钟复位环境 | 单时钟域，同步复位 | 跨时钟集成时在边界增加 CDC 结构 |

### 2.3 总体框图

```text
Scalar Core decoder
   │ inst[lanes] / rs1,rs2[lanes]（次拍）
   ▼
┌──────────────────────── Queen ────────────────────────┐
│ 前端合并 ──> endpoint 分流 ──┬─ 命令 ring（GEMM/DMA 游标）┬─ GEMM adapter ──> GEMM
│   (已实现)     (已实现)       │                           └─ DMA  adapter ──> DMA
│                              └─ SCHED 暂存 ─ Scheduler ──┬─> 同步资源 / notify / fault
│                                                          └─> 系统内存（poll）
│ asyncRd / qrdRetire / trap / irq / idle  <── 前端与 Scheduler
└───────────────────────────────────────────────────────┘
   VPU 旁带：sync query / notify pulse / fault ──> Scheduler 汇总
```

## 3. 功能清单（Feature List）

### 3.1 Feature ID 规则

模块代号为 `QN`。功能、性能、debug 三类独立编号，`xxx` 为从 001 开始的三位十进制序号。ID 不是寄存器地址、指令操作码或错误码，不因章节移动而变化。移除条目保留原 ID 并标记“已移除”。只有“已实现”行构成当前能力索引；`待确认`、`未实现` 不构成支持承诺。

### 3.2 功能清单

| Feature ID | 功能名称 | 实现状态 | 适用条件与主要约束 | 契约章节 |
|---|---|---|---|---|
| QN-F001 | 多 lane 指令接收与次拍源操作数合并 | 已实现 | 每 lane 独立 `Decoupled` 接收；`rs1/rs2` 为无 ready 的 `Valid`，仅在 fire 次一周期采样 | 5.1 |
| QN-F002 | endpoint 分流与整包 lane 有效标记 | 已实现 | 按 `inst[31:30]` 分入 GEMM/DMA/SCHED；`2'b10` 无目标队列；每包保留原始 lane 位置 | 5.2 |
| QN-F003 | GEMM/DMA 共享命令 ring 与 SCHED 暂存 | 已实现 | GEMM/DMA 共用一个 `cmdRingDepth` 个整包槽的 ring，各消费者独立游标；SCHED 为 1 级暂存；包内其余 lane 被标记无效 | 5.2 |
| QN-F004 | 整包出队握手与背压保持 | 已实现 | 出口为整包 `Decoupled`；`valid && !ready` 期间 payload 不变 | 5.2 |
| QN-F005 | 入口背压与 lane 接受规则 | 已实现 | 不要求连续前缀；`inst.ready` 按目标资源容量预约，规则见 5.1 | 5.1 |
| QN-F006 | 指令合法性检查与同步 trap | 未实现 | funct3/cmd/保留位/`regBase=31`/endpoint 约束全部未检测 | 5.3 |
| QN-F007 | 前端请求 entry 与退休时序 | 未实现 | 除 QRD 外入队即可退休；前端需为请求与次拍操作数预留空间 | 5.3 |
| QN-F008 | QRD 顺序读响应表 | 未实现 | 跨端点乱序响应必须在 Queen 内缓存并按请求序写回 | 5.4 |
| QN-F009 | 异步标量写回 `asyncRd` 与 `qrdRetire` | 未实现 | 只有顺序表头可驱动 `asyncRd`；退休在 `asyncRd.fire` 同周期 | 5.4 |
| QN-F010 | 配置影子寄存器与 `macroEop` 原子快照 | 未实现 | 影子写不改动已入队快照；`macroEop` 先应用当前写再快照 | 5.5 |
| QN-F011 | 端点执行队列与顺序保持 | 未实现 | 同端点 RUN/FENCE/WAIT/TRACE 不得相互越过 | 5.6 |
| QN-F012 | GEMM adapter 与 native 命令 burst | 未实现 | 一个 RUN 映射为寄存器写序列加 `EXECUTE`，不可与其它 RUN 交错 | 5.6 |
| QN-F013 | DMA adapter 与 `FbCmd`/`FbDone` tag 映射 | 未实现 | 内部 tag 映射回 sequence/pc；`FbDone.err` 转异步 fault | 5.6 |
| QN-F014 | DSA 强 idle 判定 | 未实现 | 必须覆盖命令、执行、内存响应和 completion，不能只看 `busy` | 5.6 |
| QN-F015 | `FENCE`（仅 GEMM/DMA 本地） | 未实现 | GEMM/DMA 本地 fence 等在队头；`endpoint=SCHED`/`VPU` 为非法编码 | 5.7 |
| QN-F016 | `WAIT_EVENT` | 未实现 | 条件 `(event[group] & rs1) == rs1`；只查询不消费 | 5.7 |
| QN-F017 | `WAIT_TIMELINE` | 未实现 | 条件 `(int32_t)(completed[id] - target) >= 0`，8 个 32-bit timeline | 5.7 |
| QN-F018 | `SIGNAL_TIMELINE` | 未实现 | 按 `rs1` 索引是否为 `x0` 决定自增或赋值，不看读回值 | 5.7 |
| QN-F019 | `SIGNAL_EVENT` | 未实现 | `inst[25]` 为 set/clr；只更新已申请资源状态 | 5.7 |
| QN-F020 | `TRACE_TAG` | 未实现 | 在 endpoint 指定的实际派发点记录 tag | 5.7 |
| QN-F021 | `POLL_EQ` | 未实现 | 4-byte 对齐 32-bit 读，默认间隔 8 周期，比较成功即完成 | 5.7 |
| QN-F022 | `ATOMIC_RMW` | 未实现 | `aux[1:0]` 为 ADD/CLR/SET，必须映射为真实原子事务 | 5.7 |
| QN-F023 | timeline/event 资源、双更新源与 VPU 只读查询 | 未实现 | 8 timeline、至多 32 event group；软件 signal 与硬件完成源写同一套资源；查询不改变资源 | 5.8 |
| QN-F024 | 完成通知计数与 level 中断 | 未实现 | 逐周期采样四来源 pulse；COUNT/ACK/PENDING/ENABLE | 5.8 |
| QN-F025 | 异步 fault 汇总与恢复 | 未实现 | 首个 fault 保持到 ACK/reset，同时记录来源 pending mask | 5.8 |
| QN-F026 | polling/atomic 系统内存主接口 | 未实现 | 基础版本单笔在途；`memResp` 需支持反压保持 | 5.9 |
| QN-F027 | flush/reset 与不可撤销边界 | 未实现 | `inst.fire` 为不可撤销边界；flushDone 前不得残留旧 context 事件 | 5.9 |
| QN-F028 | `idle` 与 busy 状态输出 | 未实现 | 必须覆盖所有队列、在途读、设备强 idle 与 flush 流程 | 5.9 |
| QN-F029 | GEMM/DMA 硬件完成信号源 | 未实现 | 随 RUN 快照携带目标；完成时置位 event 或自增 timeline；不经过 SCHED 暂存 | 5.8 |

### 3.3 性能清单

| Feature ID | 通路/性能项目 | 实现状态 | 位宽、带宽或接受/发射/完成率 | 并行能力、成立条件与瓶颈 | 关联功能/契约章节 |
|---|---|---|---|---|---|
| QN-P001 | Scalar Core 指令接受通路 | 已实现 | 每 lane 32 bit；最多 `instructionLanes` 条/周期（默认 4） | 各 lane 独立握手；GEMM/DMA lane 共享 ring 容量，SCHED lane 独立按 1 级暂存准入 | QN-F001、QN-F005；12.2 |
| QN-P002 | 命令 ring 与 SCHED 暂存的入队/出队率 | 已实现 | GEMM/DMA ring 1 包/周期写入，每个消费者 1 包/周期出队；SCHED 1 包/周期 | 包内有效条数 1..`instructionLanes`；写入与出队可同周期重叠；SCHED 深度 1 使连续 SCHED 命令按 1 条/2 周期接受 | QN-F003、QN-F004；12.2 |
| QN-P003 | GEMM/DMA 游标与 SCHED 并行推进 | 已实现 | 3 条独立出口 | GEMM/DMA 游标互不阻塞：某个消费者停在自己的槽上时，另一个消费者仍可越过该槽处理其后的包；SCHED 与 ring 互不阻塞 | QN-F002、QN-F003；12.2 |
| QN-P004 | 次拍操作数合并延迟 | 已实现 | 固定 1 周期采样窗 | 只覆盖 `inst.fire` 后紧邻的一周期，不可暂停 | QN-F001；12.2 |

### 3.4 Debug 清单

| Feature ID | Debug feature | 实现状态 | 观测/控制能力、适用条件与主要约束 | 契约章节 |
|---|---|---|---|---|

当前交付没有内建 debug 能力：无 trace 记录、无内部状态访问端口、无故障注入。仿真断言和波形属于验证设施，不计入 debug 功能。`TRACE_TAG` 是产品功能（QN-F020），未实现时不为它分配 debug ID。

## 4. 架构说明

### 4.1 子模块划分

| 子模块或逻辑块 | 职责 | 相互依赖 |
|---|---|---|
| 前端合并（`QueenDsaDecoder` 内） | 锁存本拍接受的 `inst`，在次拍与 `rs1/rs2` 合并为 `QueenDsaCommand` | 输入来自 Scalar Core；输出为 4-lane 包 |
| endpoint 分流（`QueenDsaDecoder` 内） | 用 `inst[31:30]` 给每 lane 打标 | 依赖前端合并结果 |
| GEMM/DMA 命令 ring | `cmdRingDepth` 个整包槽，GEMM/DMA 各一个游标；槽在两个游标都越过后回收 | 组合读出当前游标槽，接顶层 `Decoupled` |
| SCHED 暂存 | 1 级整包暂存，无游标、无等待 | 同上 |
| GEMM adapter | 目标契约：快照转 native 命令、读请求、completion/fault、强 idle、硬件完成信号应用 | 未实现 |
| DMA adapter | 目标契约：快照转 `FbCmd`、tag 管理、`FbDone` 归档、硬件完成信号应用 | 未实现 |
| Scheduler | 目标契约：同步资源（双更新源）、QCTRL 执行、通知计数、fault、polling/atomic | 未实现 |
| 遗留 V1 顶层 `Queen` | 占位：`req.ready` 恒 1，`rsp` 为一拍延迟且写回 `rs1`，例化两个 `QueenCpuDecode` | 与 V2 契约无关，仅作发射入口占位 |

当前 `QueenDsaDecoder` 未被任何顶层例化：`QueenElaborate` 仍发射遗留 `Queen`，`WorkerBeeQueenAdapter.scala` 全文被注释。`src/queen/verilog` 下的产物生成于 2026-09-18，对应遗留顶层而非本文的通路。

### 4.2 数据通路与控制通路

已实现的通路是纯组合合并加两级存储：

1. `pending(i)` 每周期取 `io.inst(i).fire`，`pendingInst(i)` 在 fire 时锁存指令字。因此一包的有效 lane 恰好是本拍 fire 的 lane，位置不变。
2. `batch(i)` 为组合 `Wire`：`valid` 取 `pending(i)`，`inst` 取锁存值，`rs1/rs2` 取本拍 `io.rs1/rs2(i)` 且仅在 `pending(i)` 为真时有效，否则填 0。
3. 同一 4-lane 包写入 GEMM/DMA ring 的一个槽；SCHED lane 另写一份到 1 级暂存。未命中的 lane 在写入与出口都掩码为 `valid=0`、payload 填 0。
4. 出口 `io.gemm/dma` 组合读出各自游标指向的槽并按端点掩码；`io.scheduler` 直连暂存出口。写入在同一时钟沿提交，没有额外寄存级或仲裁。

目标拓扑在队列之后增加端点执行层：GEMM/DMA 各有一个 adapter 把配置快照变成 native 命令，SCHED 侧是同步资源与内存主接口。这些部件的输入是队列条目，输出不回写前端；唯一的反向通路是 completion、fault 与读写响应。

### 4.3 内部资源组织

| 资源 | 组织方式 | 所有权与访问 |
|---|---|---|
| 每 lane `pending` 位 | 1 bit × instructionLanes | 前端独占写 |
| 每 lane `pendingInst` | 32 bit × instructionLanes | 前端独占写 |
| GEMM/DMA 命令 ring | `cmdRingDepth` 个整包槽（默认 64）；`writePtr` 与 GEMM/DMA 两个游标 | 前端只写，每个消费者只推进自己的游标；槽在 `min(游标)` 处回收 |
| SCHED 暂存 | 1 个整包槽 | 前端只写，Scheduler 只读 |
| QRD 顺序表 | 目标契约，深度待确认 | 前端入表，读响应通路出表 |
| 配置影子寄存器 | 目标契约，每端点一组 | 配置写更新，`macroEop` 读取并快照 |
| event/timeline | 目标契约，32 组 × 32 bit event、8 × 32 bit timeline | Scheduler 独占修改，各端点只读查询 |
| 通知计数 | 目标契约，4 来源 × (COUNT, ACK) | 硬件加计数，CPU 写 ACK |

## 5. 功能行为规范

### 5.1 指令接收与次拍操作数

| 契约项 | QN-F001 | QN-F005 |
|---|---|---|
| 前置条件 | 复位已释放；`instructionLanes >= 1` | 同左 |
| 输入与触发 | 每 lane 独立 `inst.valid`；`rs1/rs2` 在 fire 次一周期有效 | 各 lane 的 `ready` 由容量决定 |
| 处理语义 | fire 时锁存 32-bit 指令字；次拍把该 lane 的 `rs1/rs2` 合入条目，未 valid 的源填 0 | 不要求连续前缀，任一 lane 可单独 fire；`ready` 口径见下 |
| 输出与完成 | 形成 4-lane 包，交给 endpoint 分流 | `inst.fire` 即完成接收，不等待后端 |
| 边界情形 | 连续两拍都 fire 时，第一拍的 `pending` 在第二拍被覆盖，故两者不会混淆；未接受 lane 的次拍 `rs` 被丢弃 | 上游在 `valid && !ready` 期间必须保持 `inst` 稳定 |
| 异常关联 | 无断言检查源是否按需返回；需要 `rs` 而 `valid=0` 时静默填 0 | 无 |

`inst.ready` 按 lane 的目标资源预约生成：本拍被接受的 lane，其次拍条目必然能写入，且不破坏“每个资源每周期最多写入一个槽”。规则如下：

- GEMM/DMA lane（`2'b00`/`2'b01`）：两者共用 ring，因此共用同一个准入判定 `used + enq.valid < cmdRingDepth`，其中 `used = writePtr - min(gemmCursor, dmaCursor)`，`enq.valid` 表示本拍有待写入的包。本拍出队释放的槽在同拍即可被预约，因为判定使用出队后的游标。
- SCHED lane（`2'b11`）：1 级暂存在本拍入队/出队后占用为 0 时才接受。准入提前一拍，因此连续 SCHED 命令按 1 条/2 周期接受。
- VPU lane（`2'b10`）：`ready` 恒 0。Queen 不接收 VPU 指令，该编码由 Scalar Core decoder 在进入 Queen 前分流。

同一包内命中不同资源的 lane 相互独立；同一资源在同一包内只占一个槽，因此每个资源每周期只做一次准入判定。包仍是原子单位，不要求连续前缀，次拍 `rs` 仍只有一拍窗口。

### 5.2 endpoint 分流与命令队列

| 契约项 | QN-F002 | QN-F003 | QN-F004 |
|---|---|---|---|
| 前置条件 | 包已形成 | GEMM/DMA：ring 有空槽；SCHED：暂存空闲 | 出口非空 |
| 输入与触发 | 每 lane 的 `inst[31:30]` | 该包中至少一条 lane 命中该资源 | 下游 `ready` |
| 处理语义 | `2'b00`/`2'b01`→GEMM/DMA，`2'b11`→SCHED；`2'b10` 不命中任何资源 | 整包写入 ring 的一个槽；SCHED lane 另写一份到暂存；未命中的 lane 保持 `valid=0`，`inst/rs1/rs2` 填 0 | 每次 fire 传输完整 4-lane 向量，只有本端点 lane 有效 |
| 输出与完成 | ring 的 `enq.valid` 为包内 GEMM/DMA lane 的或；暂存的 `enq.valid` 为包内 SCHED lane 的或 | 包在 ring 中占一个槽，与包内有效条数无关；一个包最多占用 ring 1 个槽与暂存 1 个槽 | 出口组合读出当前游标槽；`valid && !ready` 期间 payload 不变 |
| 边界情形 | 同一包同时命中 GEMM 与 DMA 时两者共享同一个 ring 槽；同时命中 SCHED 时额外占暂存 1 个槽 | 同包内同一端点的多条 lane 合并为一个槽，条数不受限制直到 `instructionLanes`；槽在 GEMM/DMA 两个游标都越过后才回收 | 槽在两个游标都释放前不会被覆盖，因此另一消费者继续处理后续槽不影响该槽内容 |
| 异常关联 | `2'b10` 的 lane 永远无效，其 `ready` 恒 0 | 无 | 下游必须能整包接收，不能选择性消费部分 lane |

lane 位置是程序序信息：同一包内编号小的 lane 在程序序上更早，出口必须保留这一位置关系，消费方按 lane 编号还原顺序。

GEMM 与 DMA 共享 ring 容量但各自推进游标，因此一个消费者停在队头等待（例如 DMA 队头的 `WAIT_*`）不会阻止另一消费者处理其后的包。SCHED 是纯暂存，不执行条件等待；`FENCE`/`WAIT_EVENT`/`WAIT_TIMELINE` 的 `endpoint=SCHED` 为非法编码（`queenISA` 7.2、8、9），跨设备顺序在消费者队列一侧表达。

### 5.3 合法性与退休（目标契约）

| 契约项 | QN-F006 | QN-F007 |
|---|---|---|
| 前置条件 | 目标契约的前端接收路径 | 同左 |
| 输入与触发 | 已合并的完整条目 | `inst.fire` |
| 处理语义 | 前端在退休前检查 opcode、funct3、cmd、保留位、`QWR2.regBase=31`、`SIGNAL_*`/`POLL_EQ`/`ATOMIC_RMW` 的 endpoint 约束、`FENCE` 不得指向 VPU、`SIGNAL_TIMELINE`/`TRACE_TAG`/`ATOMIC_RMW` 的 `inst[25]` 必须为 0；同周期多条非法时只报程序序最早的一条，其后 lane 不得 fire | fire 时预留请求 entry 与次拍操作数空间；`QWR1/QWR2/QCTRL` 在 entry 完成后即可退休，`QRD` 必须等读响应或异常 |
| 输出与完成 | 单周期 `trap`（`pc`/`inst`/`cause`），无 ready | `qrdRetire`（每周期至多一条）与 `asyncRd` |
| 边界情形 | 后端才能判定的错误不得走 trap，必须走异步 fault 或 QRD 错误响应 | 已接受指令不得由普通流水线 flush 撤销 |
| 异常关联 | 10.1 | 10.1、10.2 |

### 5.4 QRD 读回（目标契约）

| 契约项 | QN-F008 | QN-F009 |
|---|---|---|
| 前置条件 | 顺序表有空位 | 存在表头 QRD |
| 输入与触发 | 新 QRD 入表；端点返回读响应或错误 | 表头响应到达 |
| 处理语义 | 内部可乱序发起，对外必须按 Queen 接收的顺序返回；早到响应缓存至其成为表头 | 正常结果驱动 `asyncRd{addr,data}`；`rd=x0` 执行读取但不写 GPR，响应到达表头即可退休；读错误不产生 `asyncRd` |
| 输出与完成 | 每周期至多一条完成 | `qrdRetire.valid` 与 `asyncRd.fire` 同周期；错误时 `trap=1` 携带同步 cause |
| 边界情形 | 写与控制指令不占读响应顺序 | `asyncRd` 反压期间 `addr/data` 与表头必须保持 |
| 异常关联 | 10.1 | 10.1 |

### 5.5 配置构建与提交（目标契约）

| 契约项 | QN-F010 |
|---|---|
| 前置条件 | 该端点存在配置影子 |
| 输入与触发 | `QWR1`/`QWR2`，以及带 `macroEop=1` 的同类指令 |
| 处理语义 | 普通写只更新影子；`macroEop` 先应用本条的寄存器写，再原子快照整组配置并生成 RUN；快照入队后，后续配置写只影响新的影子状态 |
| 输出与完成 | 一条 RUN 条目，携带完整不可变快照 |
| 边界情形 | 同一寄存器重复写以最后一条按序处理者为准；配置写之间无先后依赖，但不得越过此前已入队的 `macroEop` |
| 异常关联 | 未实现寄存器地址在前端报非法；后端访问错误走异步 fault |

### 5.6 端点执行与 DSA 适配（目标契约）

| 契约项 | QN-F011 | QN-F012 | QN-F013 | QN-F014 |
|---|---|---|---|---|
| 前置条件 | 端点队列非空 | 队列头为 RUN | 队列头为 RUN | 队列空或全速运行 |
| 输入与触发 | 队头条目条件满足 | 快照就绪 | 快照就绪 | 每周期评估 |
| 处理语义 | 同端点的 RUN/FENCE/WAIT/TRACE 严格按入队顺序执行，队头未完成不得推进后续 | 寄存器写序列加 `EXECUTE` 构成不可交错 burst，保持到 `ready` | 每个 RUN 发一个完整 `FbCmd`，分配内部 tag 并维护到 sequence/pc 的映射 | `idle=1` 必须同时满足：设备内部队列空、无执行、无未完成内存请求/响应、无未接收 completion/fault、adapter 无待发命令 |
| 输出与完成 | 队头完成才出队 | native 命令 | `FbCmd` | 供 fence、`QUEEN_IDLE` 与状态输出使用 |
| 边界情形 | FENCE/WAIT 阻塞时不阻止其它端点 | `valid && !ready` 期间命令保持 | `FbDone` 可乱序返回，按 tag 归档 | `!busy`、`ready=1` 或输入 FIFO 空都不能单独作为 idle |
| 异常关联 | 10.1 | 10.1 | 10.1 | 无 |

### 5.7 QCTRL 命令（目标契约）

`QCTRL` 的 `cmd` 分配与 `inst[25]` 的复用规则以 `queenISA.md` 第 6 节为准。下表给出各命令在 Queen 内的执行位置与完成条件。

| 契约项 | QN-F015 `FENCE` | QN-F016 `WAIT_EVENT` | QN-F017 `WAIT_TIMELINE` | QN-F018 `SIGNAL_TIMELINE` |
|---|---|---|---|---|
| 前置条件 | 已入对应队列 | 同左 | 同左 | 已入 SCHED 暂存 |
| 输入与触发 | 队头执行 | 队头执行 | 队头执行 | SCHED 执行 |
| 处理语义 | GEMM/DMA：等本设备强 idle；`endpoint=SCHED`/`VPU` 为非法编码 | 条件 `(event[aux] & rs1) == rs1`；只查询不消费 | 条件 `(int32_t)(completed[aux[2:0]] - rs1) >= 0` | `rs1` 编码为 `x0` 时自增，否则写入 `GPR[rs1]`；显式值不得回退 |
| 输出与完成 | 条件满足即出队；`notify=1` 时在完成周期发对应来源 pulse | 同左，来源为执行该 wait 的 endpoint | 同左 | 更新 timeline；不产生 pulse |
| 边界情形 | `endpoint=SCHED`/`VPU` 为非法；跨设备顺序改在消费者队列一侧用 wait 表达 | `endpoint=SCHED` 为非法；条件已满足时完成一次也只通知一次 | `endpoint=SCHED` 为非法；单个 context 内在途距离必须小于 `2^31` | 普通命令不得清零 timeline |
| 异常关联 | 10.1 | 10.1 | 10.1 | 显式回退报同步错误 |

| 契约项 | QN-F019 `SIGNAL_EVENT` | QN-F020 `TRACE_TAG` | QN-F021 `POLL_EQ` | QN-F022 `ATOMIC_RMW` |
|---|---|---|---|---|
| 前置条件 | 已入 SCHED 暂存 | 已入 endpoint 指定队列 | 已入 SCHED 暂存；地址 4-byte 对齐 | 同左 |
| 输入与触发 | SCHED 执行 | 在 endpoint 实际派发点执行 | SCHED 执行，间隔由配置寄存器给出（复位默认 8 周期） | SCHED 执行 |
| 处理语义 | `inst[25]=1` 置位，`=0` 清除，作用于 `aux[4:0]` 组与 `rs1` mask | 记录 `rs1` 作为 tracing tag | 发起 32-bit 读，与 `rs2` 比较，相等即完成，否则按间隔重试 | `aux[1:0]` 为 ADD/CLR/SET，`2'b11` 保留；通过平台原子事务实现 |
| 输出与完成 | 更新 event；不产生 pulse | 在记录点产生 trace 记录 | 比较成功时完成；`notify=1` 发 SCHED 来源 pulse | 原子事务完成即出队；V2 不返回旧值 |
| 边界情形 | 只更新已申请资源的状态 | `endpoint=SCHED` 在 Scheduler 执行点记录 | 读错误、超时、比较失败均不产生完成通知 | 不支持一致原子的区间只能用于 non-cacheable 独占地址 |
| 异常关联 | 10.1 | 10.1 | 10.1 | 地址未对齐、总线拒绝、超时走异步 fault |

`SIGNAL_EVENT` 和 `SIGNAL_TIMELINE` 是软件侧更新源。GEMM/DMA 的完成还可以作为硬件更新源（QN-F029）写同一套 event/timeline。软件源能置位、清除、赋值和自增；硬件源只能置位 event 和自增 timeline。两条路径的提交与可见性规则相同。

### 5.8 同步资源、通知与 fault（目标契约）

| 契约项 | QN-F023 | QN-F024 | QN-F025 | QN-F029 |
|---|---|---|---|---|
| 前置条件 | 复位完成 | 复位完成 | 复位完成 | 该端点的 `HW_SIGNAL_TARGET` 有效，且已随 `macroEop` 快照进入该 RUN |
| 输入与触发 | GEMM/DMA/SCHED 内部查询；VPU 通过 `vpuSyncQuery` 查询 | 四来源 `notifyPulse`；同周期可同时为 1，也可连续为 1 | adapter、VPU 或内存通路报错 | adapter 接收该 RUN 的 native completion |
| 处理语义 | 32 组 32-bit event、8 个 32-bit timeline；查询只读；更新在时钟沿提交，下一周期各端可见 | 每个高电平周期各来源 COUNT 加 1；`PENDING = COUNT != ACK`；`irq = OR(PENDING & ENABLE)` | 保存首个 fault 的 endpoint/code/sequence/pc，并置位来源 pending mask；失败命令不产生成功 notify | 按快照 target 置位 event 的 group/mask，或自增 timeline；不经过 SCHED 暂存 |
| 输出与完成 | `vpuSyncValue` 与查询同周期对齐 | level 中断保持到 ACK 覆盖 | level 中断保持到 CPU ACK 或 reset | 时钟沿提交，下一周期各端点与 VPU 查询可见 |
| 边界情形 | signal 与 query 同周期时以提交后状态为准 | COUNT 不得由软件清零；ACK 写旧快照不清除新通知 | 多来源同时出错不得静默覆盖 | 同端点按命令完成序发布，早到的 DMA completion 须缓存至前序 target 已应用；同周期多个硬件源自增同一 timeline 时按源数累加；旧 context 的 completion 不得更新资源 |
| 异常关联 | 10.1 | 10.1 | 10.1、10.2 | 10.1、10.2 |

通知计数与 fault 寄存器访问必须独立于 SCHED 暂存的推进：SCHED 通路不参与任何条件等待，计数逻辑也不得被任何端点的等待门控。

硬件信号源与 notify 是两条独立通路。前者更新同步资源、供 wait 使用；后者只累加 CPU 通知计数。两者可以由同一个完成点触发，但不得互相替代，也不能用 notify 推断资源已经更新。硬件更新路径同样不进 SCHED 暂存。

### 5.9 内存主接口、flush 与状态（目标契约）

| 契约项 | QN-F026 | QN-F027 | QN-F028 |
|---|---|---|---|
| 前置条件 | 存在 polling/atomic 命令 | 软件触发 flush/reset | 复位释放 |
| 输入与触发 | SCHED 执行到命令 | SCHED 实时管理寄存器 | 连续评估 |
| 处理语义 | 基础版本只允许一笔在途，无需 transaction ID；地址必须 4-byte 对齐 | 丢弃队列、终止或等待运行中工作、清空旧 context 状态 | `idle=1` 必须同时满足前端与 rs 等待 entry 空、端点队列空、无在途读/polling/atomic、DSA 强 idle、无 flush 在途 |
| 输出与完成 | `memReq`/`memResp` 握手 | `flushReq`/`flushDone` | `idle` 输出 |
| 边界情形 | `memResp.valid && !ready` 时 data/error 保持 | 不支持安全 abort 的 DSA 必须等自然结束 | 任一条件不满足即 `idle=0` |
| 异常关联 | 10.1 | 10.2 | 无 |

`inst.fire` 是不可撤销边界：已 fire 的指令只能由显式 flush/reset 终止，普通 CPU 流水线 flush 不得撤销。V2 不支持投机接收；若将来引入，必须在 issue payload 中增加 epoch/kill token，并重新定义所有副作用提交点。

## 6. 接口规范

方向以 Queen 为参照。6.1 为当前已实现的端口，6.2 为目标顶层端口，尚未实现。

### 6.1 已实现端口（`QueenDsaDecoder`）

| 接口/信号 | 方向 | 宽度或类型 | 时钟域 | 含义与有效条件 |
|---|---|---|---|---|
| `inst(i)` | 输入 | `Decoupled(UInt(32.W))`，i=0..L-1 | 单时钟 | 第 i 条 lane 的指令字；`fire` 时 Queen 锁存，次拍要求 `rs` |
| `rs1(i)` / `rs2(i)` | 输入 | `Valid(UInt(xlen.W))` | 单时钟 | 第 i 条 lane 的源操作数；仅在对应 lane 上拍 fire 时被采样，无 ready，不可暂停 |
| `gemm` | 输出 | `Decoupled(Vec(L, Valid(QueenDsaCommand)))` | 单时钟 | GEMM 端点的整包；每 lane 为 `{inst, rs1, rs2}` |
| `dma` | 输出 | 同上 | 单时钟 | DMA 端点 |
| `scheduler` | 输出 | 同上 | 单时钟 | SCHED 端点；1 级暂存出口 |

### 6.2 目标顶层端口（未实现）

| 接口/信号 | 方向 | 宽度或类型 | 时钟域 | 含义与有效条件 |
|---|---|---|---|---|
| `inst` | 输入 | `Vec(L, Flipped(Decoupled(QueenIssue)))`，`QueenIssue{pc, inst}` | 单时钟 | 目标形态在指令字之外携带 `pc`，供 trap、`qrdRetire` 与 fault 关联 |
| `rs` | 输入 | `Vec(2L, Flipped(Valid(xlen)))` | 单时钟 | lane i 的 `rs1` 固定接 `rs(2i)`，`rs2` 接 `rs(2i+1)` |
| `asyncRd` | 输出 | `Decoupled(QueenAsyncRd{addr, data})` | 单时钟 | 顺序表头的 QRD 成功结果；`valid && !ready` 期间保持 |
| `qrdRetire` | 输出 | `Valid(QueenRetire{pc, rdValid, rdIndex, trap, trapCause})` | 单时钟 | 每周期至多一条，按 QRD 请求顺序 |
| `trap` | 输出 | `Valid(QueenTrap{pc, inst, cause})` | 单时钟 | 同步非法指令；无 ready，CPU 必须无条件采样 |
| `irq` | 输出 | `Bool` | 单时钟 | `notifyIrq` 或 `faultIrq` 的或，level-sensitive |
| `idle` | 输出 | `Bool` | 单时钟 | 定义见 5.9 |
| `gemm.cmd` / `dma.cmd` | 输出 | `Decoupled(NativeDsaCmd)` | 单时钟 | adapter 生成的原生命令，`valid && !ready` 期间保持 |
| `completion` | 输入 | `Flipped(Decoupled(QueenDsaCompletion{sequence, error, code}))` | 单时钟 | 完成与错误回传，必须持续有效到被接收 |
| `flushReq` / `flushDone` | 输出/输入 | `Decoupled(QueenDsaFlushReq)` / `Flipped(Valid(Bool))` | 单时钟 | 见 5.9 |
| `vpuSyncQuery` / `vpuSyncValue` | 输入/输出 | `Flipped(Valid(QueenSyncQuery))` / `QueenSyncValue` | 单时钟 | 组合只读查询，值必须与 query 对齐 |
| `vpuNotifyPulse` | 输入 | `Bool` | 单时钟 | 每高电平周期计一次 |
| `vpuFault` | 输入 | `Flipped(Decoupled(QueenDsaCompletion))` | 单时钟 | VPU 错误旁带，不得用 pulse |
| `memReq` / `memResp` | 输出/输入 | `Decoupled(QueenMemReq)` / `Flipped(Decoupled(QueenMemResp))` | 单时钟 | polling/atomic 专用主接口 |
| `notifyPulse` | 内部 | `Vec(4, Bool)` | 单时钟 | 索引 0/1/2/3 为 GEMM/DMA/VPU/SCHED |

### 6.3 协议与握手

- `Decoupled` 接口统一遵循“`valid && !ready` 期间 payload 不变，`fire` 表示接收方接受”。
- `inst` 与 `rs` 不构成同一条握手：`inst` 有 ready，`rs` 没有。Queen 侧必须把两者在时间上绑定，CPU 侧必须保证已 fire 指令的源操作数在次一周期返回。
- 整包出口一次 fire 传输全部 lane；消费方不得只取其中一部分再重新握手。
- 通知为无握手 pulse，接收方必须逐周期采样，不能只做上升沿检测。
- `trap`、`irq`、`idle` 为电平/单周期无握手输出，CPU 必须无条件可采样。

### 6.4 接口依赖与互斥

- 三个出口共享同一个 4-lane 包，各出口独立握手；GEMM/DMA 还共享 ring 容量，SCHED 容量独立。
- `asyncRd` 与 `qrdRetire` 由同一表头驱动，二者在同一周期产生；`trap` 与 `qrdRetire` 的错误分支互斥。
- VPU 旁带查询必须组合返回，不能引入会与 `WAIT` 形成循环等待的握手回路。
- polling/atomic 内存接口不与 GEMM/DMA 命令接口共享事务通道。

## 7. 数据表示规范

### 7.1 类型、位宽与单位

| 数据项 | 位宽/类型 | 符号与编码 | 单位或数值域 |
|---|---|---|---|
| `inst` | 32 bit 无符号 | `custom-0`，opcode `7'b0001011` | 指令字 |
| `endpoint` | 2 bit | 00 GEMM / 01 DMA / 10 VPU / 11 SCHED | 枚举 |
| `xlen` | 32 bit | 无符号标量 | 源操作数与寄存器数据 |
| `pc` | `pcWidth`（默认 32） | 无符号 | 字节地址 |
| lane index | `log2Ceil(L)`（默认 2） | 无符号 | 0..3；小者为程序序在前 |
| event group | 5 bit | 无符号 | 0..31 |
| timeline id | 3 bit | 无符号 | 0..7 |
| notify 来源 | 2 bit | 0/1/2/3 = GEMM/DMA/VPU/SCHED | 枚举 |
| sequence | `sequenceWidth`（目标 32） | 无符号 | 模 `2^32` 递增 |

### 7.2 编码与打包

指令字段布局以 `queenISA.md` 为准，本文不重复。Queen 侧只使用 `inst[31:30]` 做路由，其余字段原样透传给下游执行层与命令解码。已实现的出口包按 lane 位置打包，无效 lane 的 `inst`、`rs1`、`rs2` 全部填 0，因此空 lane 的 `inst[31:30]=2'b00`，消费方必须先用 `Valid` 过滤，不能只看 payload。

`2'b10` 在已实现的分流中不命中任何队列，属于尚未接入 VPU 直发路径的空档，见 14.1。

### 7.3 存储布局与地址约定

已实现部分无存储布局和地址约定。目标契约中 polling/atomic 使用 32-bit、4-byte 对齐的物理地址；`ATOMIC_RMW` 只能用于平台保证原子性且由 Scheduler 独占的地址区间。物理地址空间归属、总线错误响应和超时阈值由平台定义，待确认。

## 8. 配置与寄存器规范

### 8.1 编译期参数

| 参数 | 默认值 | 合法范围/依赖 | 影响 |
|---|---|---|---|
| `instructionLanes` | 4 | `>= 1`（elaboration `require`） | 每包 lane 数、出口向量宽度、次拍 `rs` 端口数 |
| `cmdRingDepth` | 64 | `>= 2`（`require`） | GEMM/DMA 共享命令 ring 的整包槽数 |
| `xlen` | 32 | 无 `require` | `rs1/rs2` 与 `QueenDsaCommand` 字段宽度 |
| `pcWidth` | 32 | 无 `require` | 仅遗留 `QueenInterface` 使用；当前 decoder 不含 `pc` |
| `slotDepth` | 8 | 无 `require` | 无消费者，保留字段 |
| `instWidth` | 32 | 无 `require` | 无消费者，保留字段 |

注意容量口径：ring 深度按“包”计数，不按“指令条数”计数。GEMM 与 DMA 共用这 64 个槽，混合包只占 1 个槽；SCHED 暂存固定 1 个槽，不作为可配置项。

### 8.2 运行期配置

已实现部分没有运行期配置寄存器，行为完全由编译期参数决定。

目标契约中，运行期配置集中在 Scheduler：

- polling 间隔：正整数，复位默认 8 周期，寄存器地址与字段宽度待确认。
- 通知使能 `NOTIFY_ENABLE`：4 bit。
- 通知确认 `NOTIFY_ACK[e]`：每来源 32 bit，CPU 写入已处理快照。
- 恢复/flush 触发：通过实时管理寄存器触发内部 endpoint flush/reset，不得经过可能被冻结的普通命令队列。
- 硬件信号目标 `HW_SIGNAL_TARGET[e]`：只对 GEMM/DMA 端点存在，属于该端点的 command-building 影子。软件在 `macroEop` 之前写入，随配置快照进入 RUN；运行时改写只影响后续提交的 RUN。

这些配置的更新在时钟沿生效；同一周期内的 signal 与 query 关系见 5.8。

### 8.3 寄存器映射（目标契约）

以下为逻辑寄存器，地址待确认，由 Scheduler 寄存器表分配。当前 RTL 未实现任何一项。

| 地址/索引 | 名称 | 访问属性 | 复位值 | 功能 |
|---|---|---|---|---|
| 待确认 | `NOTIFY_COUNT[e]` | RO | 0 | 来源 e 的通知累计次数，模 `2^32`，软件不可清零 |
| 待确认 | `NOTIFY_ACK[e]` | RW | 0 | CPU 已确认的累计次数 |
| 待确认 | `NOTIFY_PENDING` | RO | 0 | 4 bit，bit e = `COUNT[e] != ACK[e]` |
| 待确认 | `NOTIFY_ENABLE` | RW | 0 | 4 bit 中断使能，只屏蔽 IRQ，不影响计数 |
| 待确认 | `POLL_INTERVAL` | RW | 8 | polling 间隔，单位 cycle，必须为正整数 |
| 待确认 | `FAULT_VALID` | RO | 0 | 异步 fault 有效标志，保持到 ACK/reset |
| 待确认 | `FAULT_ENDPOINT` | RO | 0 | 首个 fault 的来源 |
| 待确认 | `FAULT_CODE` | RO | 0 | `faultCodeWidth` 位错误码 |
| 待确认 | `FAULT_SEQUENCE` | RO | 0 | 关联 sequence |
| 待确认 | `FAULT_PC` | RO | 0 | 关联指令 PC |
| 待确认 | `FAULT_PENDING_MASK` | RO | 0 | 各来源出错待处理位，避免覆盖 |
| 待确认 | `RECOVER` | WO | 0 | 触发 endpoint flush/reset |

`e` 取值 0/1/2/3 对应 GEMM/DMA/VPU/SCHED。COUNT/PENDING 的读不清零计数或 pending；ACK 写旧快照不得清除其后新增的通知。

硬件信号目标不放在 Scheduler 地址空间，而是作为 GEMM/DMA 端点的配置影子字段，随该端点的 `macroEop` 快照进入 RUN：

| 字段 | 位置 | 访问 | 复位值 | 功能 |
|---|---|---|---|---|
| `HW_SIGNAL_TARGET[e]` | 端点 e 的配置地址空间（`QWR1 endpoint=e` 写入） | 影子内只写，回读按影子规则 | 0（禁用） | `{enable, kind, group, mask, timeline}`；`kind=event` 用 group/mask 置位，`kind=timeline` 自增 timeline |
| `HW_SIGNAL_VALID` | 同上 | 同上 | 0 | 快照时有效位；无效的 RUN 完成时不更新任何资源 |

它不改变 DSA 原生寄存器映射，只由 adapter 在快照与完成处理时使用；写入后到下一次 `macroEop` 之间只影响新的快照。

## 9. 时序与复位规范

### 9.1 时钟与跨域

Queen、Scalar Core 与各 DSA 默认同一时钟域。本文所有时序均以该时钟为准，没有内部 CDC。跨时钟集成必须在边界增加异步 FIFO、事件计数器或等价结构：单周期 pulse（尤其 notify）不得直接跨域。这一义务属于集成方，不属于 Queen。

### 9.2 复位与初始化

复位为 Chisel 隐式同步复位，在时钟沿生效。复位后每 lane 的 `pending` 为 0，`writePtr` 与两个游标为 0，ring 内容与暂存不参与判定，出口 `valid` 为 0。复位期间 `inst.ready` 可能为高，上游应同时保持复位或在释放后重新发起指令，不得依赖复位期间被接受的指令。

目标契约要求复位后前端、QRD 顺序表、端点队列与 native adapter 全部为空，配置影子取寄存器表规定的复位值，event/timeline、COUNT、ACK、ENABLE 与 fault 状态按规范复位，`irq` 拉低，且不存在旧 context 的 completion 或 notify。

### 9.3 事务时序与反压

已实现通路的时间关系（结构推导，见 12.2）：

```text
cycle N    : inst(i).fire，pending(i) 置 1，pendingInst(i) 锁存
cycle N+1  : rs1(i)/rs2(i) 采样并入包；包在本拍写入 ring 槽或 SCHED 暂存
cycle N+2  : 该槽或暂存成为各自出口的队头，下游可 fire
```

反压只在两个位置出现：入口 `inst.ready` 与出口 `deq.ready`。出口不 ready 时槽位占满即反向压住入口；上游在 `valid && !ready` 期间必须保持 `inst` 稳定，Queen 不会缓存未被 fire 的指令。

当前实现在 ring 写入路径上有断言 `!ringEnqValid || ringUsed < cmdRingDepth`，即设计假设入口准入判断已经保证了写入必定有槽；SCHED 暂存的 `enq.ready` 同理由 5.1 的准入规则保证。

## 10. 异常处理规范

### 10.1 异常检测与上报

| 异常项 | 检测条件 | 上报途径/编码 | 执行影响 | 清除或恢复 |
|---|---|---|---|---|
| 保留 opcode/funct3/cmd | 已实现：不检测 | 无 | 指令进入队列并被透传 | 不适用 |
| `QWR2.regBase=31` | 已实现：不检测 | 无 | 原样透传 | 不适用 |
| 目标契约：保留编码、保留位非 0、endpoint 约束违规、`FENCE` 指向 VPU、`regBase=31` | 前端 | `trap{cause}` | 不进入端点队列；只报程序序最早一条 | 随 CPU trap 处理 |
| 目标契约：`SIGNAL_TIMELINE` 显式值回退 | 执行时 | 同步错误 | 不更新 timeline | 随 CPU trap 处理 |
| 目标契约：DSA 执行错误、`FbDone.err`、VPU fault | 在途工作 | 异步 fault + `irq` | 冻结相关队列，失败命令不产生成功 notify | CPU ACK 或 reset |
| 目标契约：polling/atomic 地址未对齐、总线错误、超时 | 执行时 | 异步 fault | 不产生成功通知 | CPU ACK 或 reset |
| 已实现：`inst` 需要 `rs` 但 `rs.valid=0` | 无检测 | 无 | 该源填 0，指令继续派发 | 不适用 |
| 已实现：`endpoint=2'b10` | 无检测 | 无 | 该 lane 无队列可入，`ready` 不被驱动，指令滞留 | 只能 reset |

当前交付不具备任何产品可见的错误上报：没有 trap、没有 fault 状态、没有 irq。仿真断言（第 9.3 节的入队断言）只在验证环境生效，不属于硬件错误接口。

### 10.2 优先级与恢复

目标契约要求：多个错误同时发生时至少保存首个 fault 并置位来源 pending mask，不允许后到错误静默覆盖先到错误；fault 状态保持到 CPU 明确 ACK 或 reset；fault 管理寄存器访问不受 endpoint WAIT/FENCE 阻塞；与失败工作关联的 timeline/event 不得发布成功状态。

已接受但未完成的指令不可由 CPU 普通流水线 flush 回滚，只有显式 Queen flush/reset 能终止。flush 必须保证被丢弃的命令不会再开始执行、无法取消的总线事务已经返回或被平台终止、不再产生旧 context 的 completion/notify。

硬件信号源同样受这条约束：flush/reset 后，adapter 对未归档 tag 的 completion 一律丢弃，不得更新新 context 的 event 或 timeline。已完成归档但尚未应用的硬件信号在 flush 时一并作废。

## 11. 状态与观测规范

### 11.1 状态输出

已实现部分没有状态输出端口。队列占用、空满与在途包数均不对外暴露。

目标契约包含：

| 信号 | 含义 | 属性 |
|---|---|---|
| `idle` | Queen 与全部 DSA 均无在途工作 | level，条件见 5.9 |
| `irq` | 通知或 fault 待处理 | level-sensitive，直到 CPU 处理 |
| `trap` | 同步非法指令 | 单周期，无 ready |

### 11.2 中断、性能事件与调试

| 观测项 | 触发/统计定义 | 保持与清除 | 可靠性边界 |
|---|---|---|---|
| `NOTIFY_COUNT[e]` | 来源 e 的 pulse 在高电平周期各计 1 | 保持到 reset；软件不可清零 | 模 `2^32` 回绕；单来源未确认距离必须小于 `2^31` |
| `NOTIFY_PENDING` | `COUNT[e] != ACK[e]` | 随 ACK 推进清除 | 只反映计数差，不携带 payload |
| `FAULT_*` | 首个异步错误 | 保持到 ACK/reset | 后续错误只置 pending mask，不覆盖首个 fault |

没有内建性能计数器、没有 trace 缓冲、没有断点或故障注入能力。`TRACE_TAG` 属于功能（QN-F020），未实现。

## 12. 性能与资源规格

### 12.1 结构与容量

| 项目 | 数值/表达式 | 配置依赖 |
|---|---|---|
| 指令 lane 数 | `instructionLanes`，默认 4 | 编译期 |
| 每包最大指令条数 | `instructionLanes` | 编译期 |
| 每包每 lane 位宽 | `32 + xlen + xlen`，默认 96 bit | 编译期 |
| 每包有效位 | `instructionLanes` bit，默认 4 bit | 编译期 |
| 每包存储宽度 | `instructionLanes × (96 + 1)`，默认 388 bit | 编译期 |
| GEMM/DMA 命令 ring 容量 | `cmdRingDepth` 个包，默认 64 | 编译期 |
| SCHED 暂存容量 | 1 个包 | 固定 |
| ring 理论最大指令数 | `cmdRingDepth × instructionLanes`，默认 256 | 编译期，仅在全部 lane 命中 GEMM/DMA 时可达到 |
| 游标与写指针位宽 | `log2Ceil(cmdRingDepth) + 2` bit | 编译期 |

上述位宽为结构推导，未做综合，也不代表实际触发器/存储宏映射。

### 12.2 延迟与吞吐

| 指标 | 数值/表达式 | 属性 | 成立条件与统计口径 |
|---|---|---|---|
| 指令接受率 | ≤ `instructionLanes` 条/周期（默认 4） | 接口峰值 | 各 lane 独立 fire；GEMM/DMA lane 看 ring 余量，SCHED lane 看暂存是否空闲，两类资源独立判定 |
| ring 写入率 | 1 包/周期 | 结构上限 | 单写端口；同拍 GEMM 与 DMA lane 合并为同一包 |
| ring 出队率 | 每消费者 1 包/周期 | 接口峰值 | GEMM 与 DMA 各自独立游标，互不等待；每个游标每周期最多跳过一个槽 |
| SCHED 接受率 | 1 条 / 2 周期 | 结构上限 | 暂存深度固定 1，且下一拍 `rs` 不可暂停，故接受后需空一拍才能再接受 |
| 三出口并行 | 3 包/周期合计 | 结构上限 | 同一包分别命中不同端点时，各出口独立握手 |
| `inst.fire` → 出口可 fire | ≥ 2 周期 | 结构推导 | 1 拍 `pending` 锁存 + 出口路径上 1 拍寄存；ring 出口每跳过一个不含本端点 lane 的槽再 +1 周期；从空开始且下游持续 ready |
| 稳态吞吐 | 受下游 `ready` 决定 | 待测 | 未做带宽测量；出口长期不 ready 时延迟无界 |
| 次拍 `rs` 窗口 | 固定 1 周期 | 设计保证 | 只覆盖 fire 次一拍，不可延长或暂停 |

没有实测的功耗、面积或频率数据。本文不把结构推导当作 PPA 结论。

## 13. 集成与使用规范

### 13.1 外部环境要求

- Scalar Core decoder 必须按 endpoint 分流：`2'b10` 指令不得送入 Queen。当前实现没有兜底，误送会导致该 lane 永久不 ready。
- 已 fire 指令的 `rs1/rs2` 必须在次一周期提供且 `valid=1`；Queen 不检查是否“按需返回”，缺省填 0。
- 上游在 `valid && !ready` 期间必须保持 `inst` 稳定。
- 下游必须能整包接收 `Vec(L, Valid(QueenDsaCommand))`，不能按 lane 拆分后重新握手。
- Queen、Scalar Core 与 DSA 必须同处一个时钟域，或由集成方在边界提供 CDC。
- 目标契约中，VPU 需自行实现其指令顺序、WAIT 阻塞和标量写回；Queen 只提供同步资源查询与通知汇总。

### 13.2 使用流程

已实现部分的使用流程：

1. 复位释放后，Scalar Core 在任意 lane 上给出 `inst.valid` 与指令字。
2. Queen 在容量允许时拉高该 lane 的 `ready`，`fire` 即接受。
3. 次一周期提供同一 lane 的 `rs1/rs2`。
4. 包在次拍写入：GEMM/DMA lane 进共享命令 ring 的一个槽，SCHED lane 进 1 级暂存。
5. GEMM 与 DMA 各自沿游标按序取走命中本端点的 lane，SCHED 从暂存出口整包取走。

目标契约在此之后增加：队列条目被解释为 native 命令或同步操作，完成时更新通知计数或 fault 状态，CPU 通过 `QRD` 读状态、通过 `QWR1` 写 ACK/ENABLE。

### 13.3 使用示例

已实现通路的最小时序：

```text
cycle 0: inst(0).valid=1, inst(0).bits=QWR2(endpoint=GEMM), inst(0).ready=1
cycle 1: rs1(0).valid=1, rs2(0).valid=1 → 包写入 ring 的一个槽
cycle 2: gemm.valid=1，gemm.bits(0).valid=1，其余 lane valid=0
         gemm.ready=1 时该包出队，GEMM 游标前移
```

混合包示例：同一拍 lane 0 为 GEMM、lane 1 为 DMA，两条指令合占 ring 的一个槽；GEMM 出口只看到 `[valid, -, -, -]`，DMA 出口只看到 `[-, valid, -, -]`，各自出队时只前移自己的游标，不等待对方。若同拍还有 lane 2 为 SCHED，该指令进暂存，不占 ring 槽。若 `instructionLanes=4` 且四路都命中同一端点，该端点只占一个槽。

## 14. 约束与实现限制

### 14.1 支持边界与已知限制

| 项目 | 限制及影响 | 集成/使用义务 | 契约引用 |
|---|---|---|---|
| 无合法性检查 | funct3、cmd、保留位、`QWR2.regBase=31`、endpoint 约束、`FENCE→VPU` 全部不检测；非法指令会被当作普通指令派发 | 上游必须保证编码合法；不能依赖 Queen 报 trap | 5.3、10.1 |
| `endpoint=2'b10` 滞留 | 该 lane 不命中任何队列，其 `ready` 恒为 0；指令永久滞留，无 trap、无超时 | Scalar Core decoder 必须把 VPU 指令直发 VPU，绝不送入 Queen | 5.2、10.1 |
| QRD 无读回通路 | `QRD` 被当作普通条目入队，没有顺序表、没有 `asyncRd`、没有 `qrdRetire`；CPU 若按 ISA 等待响会将永久挂起 | 在 QRD 实现前，软件不得使用 `QRD`；CPU 侧不能假设读响应会返回 | 5.4 |
| 容量按整包计 | 一个包无论含 1 条还是 4 条指令都占 ring 一个槽；同包的 GEMM/DMA lane 合占该槽，SCHED lane 另占暂存 | 上层评估吞吐时按包而非按指令计容量 | 8.1、12.1 |
| ring 槽按两游标释放 | 槽只有在 GEMM 与 DMA 都越过它之后才可复用；单侧长期不消费会耗尽 ring 并反压入口，但不会阻塞另一侧已可出队的包 | 上层需保证两侧消费者都持续前进 | 5.2、A.1.1 |
| SCHED 暂存深度固定 1 | 连续 SCHED 指令最快 1 条/2 周期；暂存不缓存、不重排，下游不 ready 时 SCHED lane 整体停住 | 需要更高 SCHED 吞吐时合并指令或改用硬件信号源，不要加深暂存 | 5.1、12.2 |
| 无效 lane payload 清零 | 空 lane 的 `inst=0`、`rs=0`；`inst[31:30]=2'b00` 看起来像 GEMM | 消费方必须先看 `Valid` 再解释 payload | 7.2 |
| 次拍 `rs` 不可暂停 | `rs` 只有一拍窗口，缺失即填 0；上游不能反压 | CPU 必须保证时序 | 5.1 |
| 入口不缓存未 fire 指令 | Queen 只锁存已 fire 的指令，`valid && !ready` 的指令由上游保持 | 上游需自带发射缓冲或 stall | 9.3 |
| 无 `pc` 通路 | `QueenDsaCommand` 只含 `inst/rs1/rs2`；当前 decoder 无 `pc` 输入，因此无法支持 trap/`qrdRetire`/fault 的 PC 关联 | 目标接口需在 `QueenIssue` 中引入 `pc` | 6.2 |
| 出口无 buffer | GEMM/DMA 出口组合读 ring 槽，SCHED 出口直连 1 级暂存；下游必须能消费整包，否则对应游标或暂存停住并反压入口 | 下游需具备整包接收能力 | 5.2 |
| `QueenCpuMultiLaneInterface` 已定义未接入 | 该 Bundle 的 `rd` 为 `Decoupled(UInt)`，与目标 `asyncRd{addr,data}` 不一致；`QueenDsaDecoder` 自建 IO，未使用它 | 接入前需统一形状 | 6.2 |
| 遗留 V1 顶层仍在发射 | `QueenElaborate` 发射 `Queen`（`req.ready` 恒 1、`rsp` 一拍延迟写 `rs1`），其 IO 与 V2 契约不同；`src/queen/verilog` 产物为 2026-09-18 旧版 | 不能把该产物当作本文能力的 RTL 交付；换发射目标前需明确版本口径 | 4.1 |
| 遗留 V1 编码冲突 | `QueenISAEnum` 中 `SCHE=0/GEMM=1/DMA=2`，与 V2 的 `GEMM=0/DMA=1/VPU=2/SCHED=3` 冲突 | 使用该枚举的代码必须先迁移，不能与 V2 字段混用 | 7.1 |
| `WorkerBeeQueenAdapter` 全文注释 | GEMM adapter 尚不存在可发射实现 | 目标契约中的 QN-F012 无 RTL | 5.6 |
| 未使用的参数 | `slotDepth`、`instWidth` 无消费者；`pcWidth` 仅被遗留 `QueenInterface` 使用；`xlen` 无 `require` | 不建议在交付配置中改动这些字段 | 8.1 |
| 单时钟域假设 | 没有内部 CDC；notify 与 fault 的跨域责任在集成方 | 跨域集成必须提供 CDC | 9.1 |

### 14.2 不支持能力与待确认事项

不支持或未实现：

- 全部 `QCTRL` 语义（QN-F015..QN-F022）。
- 合法性检查与同步 trap（QN-F006）。
- QRD 读回与异步写回/退休（QN-F008、QN-F009）。
- 配置影子与 `macroEop` 快照（QN-F010）。
- 端点执行队列、GEMM/DMA adapter、强 idle（QN-F011..QN-F014）。
- 同步资源、VPU 查询、双更新源、通知计数与中断、fault、内存主接口、flush、`idle`（QN-F023..QN-F029）。
- 投机接收。V2 不支持；引入需要 epoch/kill token。

待确认：

- Scheduler 寄存器地址映射与 `POLL_INTERVAL` 字段宽度。
- `HW_SIGNAL_TARGET` 的字段编码与地址，以及 event 目标是否允许一次置多个 group。
- 同一 event bit 上软件清除与硬件置位同周期冲突时的优先级。
- 硬件信号在 native completion 与强 idle 之间的可见性口径：当前按 completion 定义，若 DSA 的 completion 不保证写可见，需要改成 idle 触发。
- QRD 顺序表深度与读响应缓存深度。
- polling/atomic 的物理地址空间、总线错误编码与超时阈值。
- 目标顶层替换遗留 `Queen` 的时间点与 `src/queen/verilog` 的重新发射。

## 附录：补充资料

### A.1 公式与推导

#### A.1.1 ring 容量与回收

一个包只占一个 ring 槽，与该包命中 GEMM、DMA 还是两者都命中无关；SCHED lane 不进 ring。

设写指针 `writePtr` 与两个消费者游标 `gemmCursor`、`dmaCursor` 均为 `ptrW = log2Ceil(cmdRingDepth) + 2` bit 的回绕计数，且落在 `(writePtr - 2^(ptrW-1), writePtr]` 窗口内。任何时刻被占用的槽数为

```text
ringUsed = (writePtr - nextCommit) mod 2^ptrW
nextCommit = min(gemmCursor_next, dmaCursor_next)     // 按回绕窗口比较
```

其中游标的“下一值”已计入当拍的出队前移，因此同一拍被释放的槽可以在同一拍被新接受的指令预约。接受条件为

```text
ringUsed + enq.valid < cmdRingDepth
```

回绕比较用 `(a - b) < 2^(ptrW-1)` 判定，因为 `ptrW` 比索引位宽多 2 bit，半窗口严格大于 `cmdRingDepth`，不会把相差 `cmdRingDepth` 的两个指针误判为反向。若两侧游标差距达到 `cmdRingDepth`，ring 满，入口停止接受 GEMM/DMA 指令；另一侧仍可沿自己的游标继续出队。

#### A.1.2 出口延迟

设 `inst.fire` 在周期 N 发生，则包在 N+1 写入 ring 或 SCHED 暂存；出口路径含一级寄存，故最早在 N+2 可 fire。GEMM/DMA 出口按各自游标推进，游标每跳过一个本端点无 lane 的槽用掉一拍，因此额外延迟等于途中被跳过的槽数。SCHED 出口只有固定的 1 级暂存，不受其它端点包影响。若下游不 ready，延迟为不定值，最坏无界。

#### A.1.3 通知计数与确认

```text
pending(e)  = (COUNT(e) != ACK(e))
irq         = OR_e ( pending(e) & ENABLE(e) )
未确认次数   = COUNT(e) - ACK(e)          // 无符号差，要求 < 2^31
```

#### A.1.4 timeline 等待条件

```text
wait 满足  <=>  (int32_t)(completed[id] - targetSequence) >= 0
```

`completed[id]` 由两条路径推进：软件 `SIGNAL_TIMELINE`（自增或赋值）与硬件完成源（每个携带该 timeline 目标的 RUN 完成时 +1）。同一时钟沿上，软件自增与 k 个硬件源自增可以叠加，结果为 `+1+k`；软件显式赋值与硬件自增不得指向同一 timeline，由资源分配协议保证。

#### A.1.5 event 置位

```text
setMask  = softwareSetMask | hardwareMask
event[g] = (event[g] | setMask)            // 软件清除按同一提交点的规则另行清除
```

软件 `SIGNAL_EVENT` 按 `inst[25]` 置位或清除；硬件完成源只能置位。同一周期多个置位来源作用于同一 bit 时取或，不清除其它 bit。同一 bit 上软件清除与任一置位发生在同一时钟沿时的优先级待确认。

### A.2 发布前检查

- 一级章节、F/P/D 三类 Feature ID 与所有引用一致；首页版本日期与修订记录一致。
- 功能、性能与 debug 清单独立编号；debug 清单为空并已说明原因，不混入规划项。
- 性能清单描述通路带宽与并行发射，不重复列举功能机制；接受、派发、执行与完成并行度分别说明。
- 功能支持、合法域、接口、配置、时序、异常及限制相互一致，无重复冲突定义。
- 已实现事实有可核对依据（`QueenDsaDecoder.scala`、`QueenConfig.scala`、`QueenDsaDecoderSpec.scala`）；未知与不适用区分。
- 接口峰值、局部结构上限、设计目标与实测值区分；本文无实测 PPA 数据。
- 模板占位项与填写提示已处理，表格与代码块完整。
