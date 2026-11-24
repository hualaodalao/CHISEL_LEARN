# Queen 硬件规格

文档版本：`v6.3.0`

修订日期：`2026-11-24`

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
| v6.3.0 | 2026-11-24 | `endpoint=2'b10`（VPU）误路由从「指令永久滞留、只能 reset」改为**前端报 trap cause 4**；新增 SCHED `0x03 POLL_TIMEOUT`（RW，复位 **0=禁用**）：一条 `POLL_EQ` 的最大 cycle 数，到期报 **kind=3/code=2** 并**中止该命令、放行派发点**（否则软件连清 fault 的写都发不进去），在途 READ 未回时须等它回来才放行（无 transaction ID，防陈旧响应配错比较器），超时后不再发 READ，**是否 flush 由软件决定**；`POLL_INTERVAL`/`POLL_TIMEOUT` 均不随 flush 复位，`0x02/0x03` 相邻可由一条 `QWR2` 同拍写。trace 记录流与 VPU 同步旁带裁定为 **TBD：只保留契约与接口位置，不实现**；设备侧（WorkerBee/ForagerBee）按本规格自行对齐，不属 Queen 交付范围 | QN-F006、QN-F021、QN-F025 |
| v6.2.0 | 2026-11-24 | polling/atomic 与 flush 落地：新增 `QueenMemMaster`（`memDepth` 默认 **8** 笔在途、无 transaction ID、**响应按请求序返回是总线硬义务**、`memResp.ready` 恒 1）与顶层 `memReq/memResp`；`POLL_EQ` 阻塞 SCHED 派发点直到读值相等、`ATOMIC_RMW` 发出即放行，两者**均无 CPU 可见完成通知**（推翻 v6.0.0 的「通路待定」）；SCHED 新增 `0x01 RECOVER`(WO)、`0x02 POLL_INTERVAL`(RW，复位 8，写 0 非法)。新增 `QueenFlushCtrl`：三触发源（`RECOVER` 写 / `QCTRL FLUSH` cmd=10 / CPU `flushReq` 端口）、两侧**四相 req/ack**、前端接受并丢弃、在途 QRD 回 `rdValid=1/data=0xdeadbeef/trap=0`、DSA 队列全丢、迟到 completion 丢弃、event/timeline/fault/openGroup 清零（`POLL_INTERVAL` 保持）；顶层新增 `flushReq/flushAck` 与两路 `gemmFlushReq/ACK`、`dmaFlushReq/ACK`，**`flushReq` 按 sys 级广播语义实现：整个 req 有效期间 Queen 停在 flush 态，排空后还要静默 `flushQuietCycles`（默认 8）拍无扰动才拉 ack，任何新指令 fire/迟到 completion/总线响应都重新计数**；fault 新增 **kind=3**（code 0=总线错误、1=地址未对齐）成为第七个源；`idle` 纳入内存主接口与 flush 控制器；`QueenQrdEntry` 加 `dummy` 位；`QueenConfig` 加 `memDepth`/`flushQuietCycles` | QN-F021、QN-F022、QN-F025..QN-F028、QN-F032、QN-F033 |
| v6.1.0 | 2026-11-19 | QRD 读通路落地：新增 `QueenQrdTracker`（前端 accept 时按 lane 序多推入口、`qrdDepth` 默认 16 可配、三来源响应 FIFO、按程序序退休），顶层新增 `asyncRd`/`qrdRetire`/`gemmResp`/`dmaResp`，读错误走 `qrdRetire.trap` + cause 9（`rdError`）；`QueenSchedCtrl` 折入 Scheduler 寄存器前端（`0x00 FAULT_CLEAR` WO、`0x40..0x5F EVENT`、`0x60..0x67 TIMELINE`、`0x68..0x6B FAULT_*`、`0x6C IDLE`，连续 QWR 切片一拍并行执行、QRD/SIGNAL/TRACE 独占一拍）；**删除顶层 `faultClear` 端口**，清除只能由软件写 SCHED `0x00` 触发；SCHED `wrErr` 成为第六个 fault 源；`idle` 纳入在途读表；新增设备硬义务「同端点读响应必须按请求序返回」 | QN-F008、QN-F009、QN-F023、QN-F025、QN-F028、QN-F032 |
| v6.0.0 | 2026-11-18 | 不兼容的 ISA 变更：**删除 Queen 侧完成通知机制**（notify 位、notifyPulse、四来源 COUNT/ACK/PENDING/ENABLE 全部移除），`inst[25]` 除 `SIGNAL_EVENT` 的 set/clr 外收为保留位（trap cause 7 更名为 `bit25`）；CPU 感知完成改走设备自己的中断 + 同步状态读。新增 `QueenEndpointCtrl`（GEMM/DMA 队头消费 FENCE/WAIT_EVENT/WAIT_TIMELINE/TRACE_TAG）与 `QueenSchedCtrl`（每拍一条 SIGNAL_EVENT/SIGNAL_TIMELINE）；`QueenSyncState` 加软件写口（逐位置位优先、软件与硬件自增累加、显式回退抑制并报 fault）；fault 加 2-bit `kind` 并接入 `wrErr`；顶层删除 `scheduler` 外露端口、新增 `wrErr` 输入与 `irq` | QN-F011、QN-F015..QN-F020、QN-F023..QN-F025、QN-F028、QN-F029 |
| v5.0.0 | 2026-11-18 | 不兼容的完成接口变更：`QueenDsaCompletion` 由 `Decoupled{sequence,error,code,hwSignal}` 改为 `Valid{pc,cmdTag,swTag,error,code,hwSignal}`，Queen 无条件接收、每端点每拍至多一条、两端点同拍必须全吸收；取消 sequence/tag 归档与乱序重排，`pc/cmdTag/swTag/hwSignal` 改由**设备**在 `start` 当拍采样并回传（`QueenDsaRegIf` 删 hwLatch、改组合输出）；completion 绕过 adapter 直入 `QueenTop`；新增 `QueenSyncState`（32 event 组 + 8 timeline，硬件源同拍按位或/计数相加）、最新一条 fault + level `faultIrq` + `faultClear`、三层强 idle（decoder/adapter/`deviceIdle`）；`QueenConfig` 新增 `faultCodeWidth/eventGroupCount/timelineCount` | QN-F012、QN-F013、QN-F014、QN-F023、QN-F025、QN-F028、QN-F029、QN-F031 |
| v4.2.0 | 2026-11-18 | 新增顶层 `QueenTop`（decoder + 两个 adapter）并把 `QueenElaborate` 发射目标换成它；配置通路首次整条打通并用 `QueenDsaRegIf` 端到端验证到 `start` pulse；删除遗留 V1 簇（`Queen`/`QueenInterface`/`QueenISAEnum`/`WorkerBeeQueenAdapter`）与未接入的 `QueenCpuMultiLaneInterface` | QN-F001..QN-F006、QN-F030、QN-F031 |
| v4.1.0 | 2026-10-30 | 通用寄存器接口类名统一加 `Queen` 前缀（`QueenDsaRegIf/QueenDsaRegIfConfig/QueenDsaRegIfAddr/QueenDsaRegResp`）；删除 `ERR_STATUS/ERR_CODE/ERR_CLEAR` 与 `localErr` 端口，公共区块 3 整块保留——无命令归属的本地错误改由设备中断 `INT_RAW` + 私有 `statusRegs` 承载，与既有 completion/wrErr 三条通路不再重复 | QN-F031、QN-F029 |
| v4.0.0 | 2026-10-30 | beat 切分改为「只有 QWR 可合并、QCTRL/QRD 独占」，流控收为设备单一 `cmd.ready` 并删除 Queen 侧 `readyStart` 端口；写不占 resp 通道、改写 per-lane `wrErr` pulse；因 RUN 独占 beat 而撤销设备侧「同 beat 写进快照」义务；新增 QN-F031 通用 DSA 寄存器接口 `QueenDsaRegIf`（`queen/scala/common`，中断按 IntrRFMS4 语义、0x60 对齐，配置/状态数量可配） | QN-F030、QN-F031、QN-F029 |
| v3.0.0 | 2026-10-30 | 不兼容的 DSA 配置接口变更：`QueenNativeCmd` 由 `{isStart,regAddr,data}` 改为 `{op,reg0Addr,reg1Addr,data0,data1}`，`QWR2` 原生透传为一条双地址写；出口由单条串行改为 `Decoupled(Vec(nativeWidth, Valid(...)))` 并行 beat，新增 `nativeLanes` 参数（0=对齐 instructionLanes）；确立 beat 切分规则（遇首条 RUN 截断且以它收尾、全无效切片跳过、只有含 RUN 的 beat 被 ready_start 门控）与设备侧同拍写进 RUN 快照义务 | QN-F030、QN-F012、QN-F013 |
| v2.4.0 | 2026-10-30 | 细化 `HW_SIGNAL_TARGET/MASK` 位排布、编码示例与锁存/读回时序，明确公共契约区寄存器不参与设备双 bank 切换、一条 RUN 只指向一个 group/timeline；确立“Queen 要的状态一律走握手端口”原则，删除设备侧 `CFG_CREDIT/DEV_STATUS/DONE_*/ERR_PC/ERR_TAG` 与 trace/signal 的 last-value 观测寄存器，块 2 整块保留、块 3 收窄为无命令归属的本地错误 | QN-F029、QN-F020、QN-F030 |
| v2.3.0 | 2026-10-30 | 设备流控改为两个独立 ready：`ready_start`（设备产生，只门控 RUN）与 `ready_rd`（即 resp.ready，来自 Queen QRD 响应通路），WRITE 无 ready 门控；adapter 端口由 credit 计数改为 `readyStart: Bool`，删除 `cfgCreditWidth`；新增接口文档第 16 章规定 DSA 设备寄存器分区（公共区 0x080..0x0FF 按 32 槽分 4 块）、signal/trace/状态/错误寄存器用法与软件流程 | QN-F030、QN-F020、QN-F029 |
| v2.2.0 | 2026-10-30 | 派发级 adapter 落地：`QueenDsaAdapter` 把整包串行化为设备寄存器写与启动命令，credit 只门控 `RUN`；`QueenDsaCommand` 增加 `pc`；新增 `cfgCreditWidth` 参数 | QN-F030、QN-F012、QN-F013 |
| v2.1.0 | 2026-10-30 | 前端合法性检查与同步 trap 落地：`inst` 改为 `QueenIssue{pc,inst}`，新增 `trap{pc,inst,cause}` 与 cause 编码表，每端点 openGroup 位与空组 `RUN` 检查已实现；`RUN.rs1` 高 24 位改为忽略（操作数次拍才返回，不能作前端非法条件） | QN-F006、QN-F030 |
| v2.0.0 | 2026-10-30 | 不兼容编码变更：弃用 `macroEop`，`QWR2.regBase` 借 `inst[11]` 扩为 6 bit（0..62，63 非法），新增 `QCTRL RUN`（cmd=9）作为唯一 DSA 启动命令；配置寄存器归 DSA 所有，Queen 不再保存影子与快照，改为按序转发配置写并按设备 credit 准入 | QN-F006、QN-F010（已移除）、QN-F012、QN-F013、QN-F029、QN-F030 |
| v1.2.0 | 2026-10-30 | GEMM/DMA 由各自 FIFO 改为共享命令 ring（各持游标，槽在两侧游标都越过后回收），SCHED 改为 1 级暂存；`inst.ready` 按端点资源预约；`FENCE`/`WAIT_EVENT`/`WAIT_TIMELINE` 的 `endpoint=SCHED` 定为非法编码 | QN-F001、QN-F002、QN-F003、QN-F004、QN-F005、QN-F015、QN-F016、QN-F017 |
| v1.1.0 | 2026-10-30 | 增加 GEMM/DMA 硬件完成信号源，event/timeline 改为软件 signal 与硬件完成双更新源；软件 signal 保持不变 | QN-F023、QN-F029 |
| v1.0.0 | 2026-10-30 | 首次发布。依据 `queenISA.md` 与 `queenHarewareInterface.md` 建立 Queen V2 目标契约，并按当前 RTL 标注实现状态 | 全部 |

## 1. 文档说明

### 1.1 目的

本文定义 Queen 的外部可见行为、设计边界和交付配置。Queen 目前已落地命令派发数据通路、端点控制器（FENCE/WAIT/TRACE 消费）、SCHED 同步指令执行与寄存器前端、event/timeline 双更新源、最新一条 fault、多层强 idle、QRD 在途表/顺序退休、polling/atomic 内存主接口与 flush 恢复；trace 管理与真实设备侧命令映射仍属于目标契约，尚未实现。本文把这两部分分开陈述：已实现部分按当前 RTL 的实际行为写，目标部分按 `queenISA.md` 与 `queenHarewareInterface.md` 的契约写，并在每项功能上标注实现状态。路线图、性能优化计划和未定事项不作为当前能力。

### 1.2 范围

本文包含：

- Scalar Core 与 Queen 之间的指令、源操作数、异步写回、退休、trap 和中断接口。
- Queen 内部按 endpoint 组织的命令队列，以及 GEMM/DMA adapter 到原生 DSA 接口的转换职责。
- `QCTRL` 八条有效命令（FENCE、WAIT_EVENT、WAIT_TIMELINE、SIGNAL_TIMELINE、SIGNAL_EVENT、TRACE_TAG、POLL_EQ、ATOMIC_RMW）的目标语义与执行位置；`cmd=4'b0111` 为保留非法编码。
- SCHED 侧 QWR/QRD 寄存器访问、polling/atomic 系统内存主接口、flush 恢复与 trace 管理。
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
| QRD | 读取一次 DSA 寄存器的 Queen 指令 | 唯一需要 CPU 等待响应的指令 |

### 1.4 参考资料与适用基线

| 资料 | 版本或路径 | 用途 |
|---|---|---|
| Queen ISA | `src/queen/doc/queenISA.md` | 指令编码、语义与软件可见行为 |
| Queen 硬件接口 | `src/queen/doc/queenHarewareInterface.md` | 顶层接口形状与握手契约 |
| 规格模板 | `src/doc/spec_template.md` | 本文结构与 Feature ID 规则 |
| 派发数据通路 RTL | `src/queen/scala/main/QueenDsaDecoder.scala`、`QueenDsaAdapter.scala` | 已实现行为的唯一事实源 |
| 顶层与发射入口 | `src/queen/scala/main/QueenTop.scala`、`QueenElaborate.scala` | 配置通路集成边界与 verilog 发射 |
| 通用设备寄存器接口 | `src/queen/scala/common/QueenDsaRegIf.scala` | 设备侧契约参考实现 |
| 编译期参数 | `src/queen/scala/main/QueenConfig.scala` | 参数与校验 |

## 2. 模块概述

### 2.1 定位与职责

Queen 处在 Scalar Core decoder 与 GEMM、DMA 两个 DSA 之间，承担三件事：

1. 接收 Scalar Core 按 endpoint 分流后送来的指令，把指令与其次拍源操作数合并成一条完整的命令条目。
2. 按 endpoint 把条目分入 GEMM/DMA 共享的命令 ring 与 SCHED 的单级暂存，保持同端点内的程序序；SCHED 侧不执行条件等待，派发路径不被等待阻塞。
3. 在目标契约中，把这些条目解释为 DSA 原生命令、polling/atomic 内存事务，并维护 fault 状态与中断。

VPU 不属于 Queen 的调度范围。目标为 VPU 的配置、RUN、WAIT 和 TRACE 指令由 Scalar Core decoder 直接送入 VPU 前端；Queen 不建立 VPU 命令队列，也没有到 VPU 的命令输出接口。Queen 与 VPU 之间只有同步资源查询和错误上报两个旁带接口。

当前交付的 RTL 覆盖第 1 条的合法性检查与同步 trap，以及第 2 条：指令合并、按 `inst[31:30]` 分流、GEMM/DMA 共享 ring、SCHED 单级暂存与出口握手。第 3 条的全部内容尚未实现。

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
│                              └─ SCHED 暂存 ─ SchedCtrl ──┬─> 同步资源 / fault
│                                                          └─> 系统内存（poll）
│ asyncRd / qrdRetire / trap / irq / idle  <── 前端与 Scheduler
└───────────────────────────────────────────────────────┘
   VPU 旁带：sync query / fault ──> Scheduler 汇总
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
| QN-F006 | 指令合法性检查与同步 trap | 已实现 | 检查 opcode/funct3/cmd/保留位/`regBase=63`/endpoint 约束（含 `endpoint=2'b10` 误路由）/`inst[25]` 规则/空组 `RUN`；只报程序序最早一条，其后 lane 不 fire；设备寄存器地址与执行期错误不在前端检测 | 5.3 |
| QN-F007 | 前端请求 entry 与退休时序 | 未实现 | 除 QRD 外入队即可退休；前端需为请求与次拍操作数预留空间 | 5.3 |
| QN-F008 | QRD 顺序读响应表 | 已实现 | `QueenQrdTracker`：前端 accept 时按 lane 序分配 entry（一拍最多 `instructionLanes` 条），`qrdDepth` 个在途槽；GEMM/DMA/SCHED 各一个响应 FIFO 缓存早到响应，跨端点乱序到达仍按请求序退休；同端点响应保序是设备义务，故 entry 不存 regAddr | 5.4 |
| QN-F009 | 异步标量写回 `asyncRd` 与 `qrdRetire` | 已实现 | 只有表头可驱动 `asyncRd`；`rd=x0` 不写回但仍执行读取并退休；读错误不产生 `asyncRd`，改由 `qrdRetire.trap=1` + cause 9 上报；`asyncRd` 反压期间表头保持 | 5.4 |
| QN-F010 | 配置影子寄存器与 `macroEop` 原子快照 | 已移除 | 配置寄存器归 DSA 所有，Queen 不保存影子、不生成快照；由 QN-F030 取代 | 5.5 |
| QN-F011 | 端点执行队列与顺序保持 | 已实现 | `QueenEndpointCtrl` 整包寄存 + lane 游标，按 lane 序消费控制命令并转发 QWR/QRD/RUN 切片；条件未满足只停本端点队头，其后 lane 不越过 | 5.6 |
| QN-F012 | GEMM adapter 与设备完成回传 | 部分实现 | `QueenDsaAdapter` 已把整包展开为 native beat，`RUN` 独占 beat 提交；completion 由设备直接回 `QueenTop`，不经 adapter，Queen 不做 tag 归档。设备侧 `QueenNativeCmd`→WorkerBee 命令映射未实现 | 5.6 |
| QN-F013 | DMA adapter 与设备完成回传 | 部分实现 | 与 GEMM 同一 adapter 与 completion 通路；tag→sequence/pc 映射已取消，`pc/cmdTag/swTag` 由设备在 `start` 当拍采样后随 completion 回传，`FbDone.err` 由设备放进 `error/code`。设备侧 DMA 配置前端未实现 | 5.6 |
| QN-F014 | DSA 强 idle 判定 | 部分实现 | Queen 侧多层合成已实现：decoder（ring 游标对齐 + SCHED 空 + 无 pending）、SCHED 控制器、两端点控制器（`!active`）、adapter（`!active && deviceIdle`），顶层取全部之与；设备 `deviceIdle` 须覆盖命令、执行、内存响应与未回传 completion，不能只看 `busy` | 5.6 |
| QN-F015 | `FENCE`（仅 GEMM/DMA 本地） | 已实现 | 队头等 `adapter.idle`（含设备强 idle）；`endpoint=SCHED`/`VPU` 为非法编码；不产生 CPU 可见事件 | 5.7 |
| QN-F016 | `WAIT_EVENT` | 已实现 | 条件 `(event[group] & rs1) == rs1`；只查询不消费，不通知 CPU | 5.7 |
| QN-F017 | `WAIT_TIMELINE` | 已实现 | 条件 `(int32_t)(completed[id] - target) >= 0`，8 个 32-bit timeline；回绕后仍正确判定 | 5.7 |
| QN-F018 | `SIGNAL_TIMELINE` | 已实现 | 按 `rs1` 索引是否为 `x0` 决定自增或赋值，不看读回值；显式回退抑制更新并报 fault(kind=2) | 5.7 |
| QN-F019 | `SIGNAL_EVENT` | 已实现 | `inst[25]` 为 set/clr；与硬件置位同拍冲突时逐位置位优先 | 5.7 |
| QN-F020 | `TRACE_TAG` | 部分实现 | 在端点控制器/SCHED 控制器按 lane 序消费，不停等派发；记录流协议未定，当前不产生记录 | 5.7 |
| QN-F021 | `POLL_EQ` | 已实现 | 4-byte 对齐 32-bit 读，间隔由 SCHED `0x02 POLL_INTERVAL` 给出（复位 8，写 0 非法）；**阻塞 SCHED 派发点**直到读值相等；`0x03 POLL_TIMEOUT`（复位 0=禁用）到期报 kind=3/code=2 并中止本命令、放行派发点，是否 flush 由软件决定；不产生 CPU 可见完成通知 | 5.7 |
| QN-F022 | `ATOMIC_RMW` | 已实现 | `aux[1:0]` 为 ADD/CLR/SET，必须映射为真实原子事务；**发出即放行**，最多 `memDepth` 笔在途；不产生 CPU 可见完成通知 | 5.7 |
| QN-F023 | timeline/event 资源、双更新源与 VPU 只读查询 | 部分实现 | `QueenSyncState` 已实现 32 event group × 32 bit 与 8 timeline × 32 bit、只读视图、硬件源（同拍双端点按位或/计数相加）与软件源（逐位置位优先、自增累加、回退抑制）；软件读通路已由 SCHED `QRD` 落地（QN-F032）；VPU 查询未实现 | 5.8 |
| QN-F024 | 完成通知计数与 level 中断 | 已移除 | V2 删除 Queen 侧通知机制：设备工作完成由设备自己的中断块上报，跨引擎条件由 event/timeline 表达。`POLL_EQ/ATOMIC_RMW` 已裁定为无 CPU 可见完成通知 | 5.8 |
| QN-F025 | 异步 fault 汇总与恢复 | 已实现 | 只保留最新一条 `{valid, kind, endpoint, code}` + level `faultIrq`/`irq`；七源固定优先级（completion GEMM/DMA > wrErr GEMM/DMA/SCHED > 内存错误 > timeline 回退），新错误优先于清除；清除由软件写 SCHED `0x00` 或 flush 清状态触发，顶层无 `faultClear` 端口；kind 0/1/2/3 分别为设备执行错误/写非法地址/回退/内存错误，不瓜分设备码空间；不带 pc 归因 | 5.8 |
| QN-F026 | polling/atomic 系统内存主接口 | 已实现 | `QueenMemMaster`：`memDepth`（默认 8）笔在途、无 transaction ID，**响应按请求序返回是总线硬义务**；在途表只记「是否 READ」，准入预留响应槽故 `memResp.ready` 恒 1；未对齐不发请求直接报 fault；flush 只停发新请求、等已发出的响应回来 | 5.9 |
| QN-F027 | flush/reset 与不可撤销边界 | 已实现 | `QueenFlushCtrl`：三触发源（SCHED `0x01 RECOVER` 写 / `QCTRL FLUSH` cmd=10 / sys 广播 `flushReq`），两侧四相 req/ack；**req 有效期间全程停在 flush 态**；排空后再静默 `flushQuietCycles` 拍无扰动才回 ack，扰动重新计数；前端接受并丢弃、在途 QRD 回 `0xdeadbeef`、DSA 队列全丢、迟到 completion 丢弃、event/timeline/fault/openGroup 清零；`inst.fire` 为不可撤销边界 | 5.9 |
| QN-F028 | `idle` 与 busy 状态输出 | 已实现 | `QueenTop.idle` = 前端 + SCHED 控制器 + 两端点控制器 + 两 adapter（含设备强 idle）+ 在途读表（含响应 FIFO）+ 内存主接口 + flush 控制器全部空闲；SCHED `IDLE` 寄存器口径不含在途读表与 flush 控制器 | 5.9 |
| QN-F029 | GEMM/DMA 硬件完成信号源 | 已实现 | 目标为设备公共区 `0x080/0x081`，`RUN` 前由 `QWR` 写入；`QueenDsaRegIf` 组合给出目标，设备在 `start` 当拍采样并随 completion 回传，Queen 当拍置位 event 或自增 timeline；不经过 SCHED 暂存 | 5.8 |
| QN-F030 | 配置写转发、`RUN` 提交与配置流控 | 已实现 | `QueenDsaAdapter` 按 lane 序并行展开为 native 命令，`QWR2` 为一条双地址写；只有写可合并 beat，`QCTRL`/`QRD` 独占 beat；流控收为设备单一 `cmd.ready`，无 `ready_start` 端口；`cmdTag/swTag/pc` 透传。completion 接收与强 idle 属 QN-F012/F013/F014 | 5.5 |
| QN-F031 | 通用 DSA 寄存器接口参考实现 | 已实现 | `queen/scala/common/QueenDsaRegIf`：4 lane 并行 beat、单 ready、写不占 resp（per-lane `wrErr` pulse）、shadow/live 双组、`RUN` 当拍 dump 与 `start` pulse、signal 目标与 trace 上下文组合输出（设备 `start` 当拍采样，本模块不锁存）、`IntrRFMS4` 语义中断（0x60 对齐）、配置/状态数量可配；无 `ERR_*` 寄存器，本地错误走中断+状态 | 5.5、接口 17 |
| QN-F032 | SCHED 寄存器前端与同步状态软件读通路 | 已实现 | `QueenSchedCtrl` 就地实现 `0x00 FAULT_CLEAR`(WO)、`0x01 RECOVER`(WO)、`0x02 POLL_INTERVAL`(RW，复位 8)、`0x40..0x5F EVENT`、`0x60..0x67 TIMELINE`、`0x68..0x6B FAULT_*`、`0x6C IDLE`（均 RO）；连续 QWR 切片一拍并行执行、QRD/SIGNAL/TRACE/POLL/ATOMIC/FLUSH 独占一拍；非法写走 per-lane `wrErr`、非法读回 `err=1/data=0`；不复用 `QueenDsaRegIf`（SCHED 无 RUN、无双组、读的是 Queen 架构状态） | 8.3.1 |
| QN-F033 | `FLUSH` 指令 | 已实现 | `QCTRL cmd=4'b1010`，endpoint 固定 SCHED，aux/flag 为 0；与写 `0x01 RECOVER`、CPU `flushReq` 端口共用同一条恢复流程 | 5.9 |

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
| 前端合法性与 trap（`QueenDsaDecoder` 内） | 组合判定各 lane 编码合法性，按 lane 序折叠 openGroup，报程序序最早的非法指令 | 先于 fire；非法 lane 及其后 lane 不 fire |
| 前端合并（`QueenDsaDecoder` 内） | 锁存本拍接受的 `inst`，在次拍与 `rs1/rs2` 合并为 `QueenDsaCommand` | 输入来自 Scalar Core；输出为 4-lane 包 |
| endpoint 分流（`QueenDsaDecoder` 内） | 用 `inst[31:30]` 给每 lane 打标 | 依赖前端合并结果 |
| GEMM/DMA 命令 ring | `cmdRingDepth` 个整包槽，GEMM/DMA 各一个游标；槽在两个游标都越过后回收 | 组合读出当前游标槽，接顶层 `Decoupled` |
| SCHED 暂存 | 1 级整包暂存，无游标、无等待 | 同上 |
| 端点控制器（`QueenEndpointCtrl`） | 已实现，GEMM/DMA 各一个：整包寄存 + lane 游标，按 lane 序消费 `FENCE/WAIT_*/TRACE_TAG`，把队头连续 QWR/QRD/RUN 切片转发给 adapter | 接在端点出口与 adapter 之间；`FENCE` 取 `adapter.idle`，`WAIT_*` 取同步资源视图 |
| SCHED 控制器（`QueenSchedCtrl`） | 已实现：连续 QWR 切片一拍并行执行、其余命令各独占一拍；就地实现 SCHED 寄存器映射（8.3.1），输出 `QueenSyncWrite`、读响应、`wrErr`、`faultClear`、`flushTrig`，并执行 `POLL_EQ`（阻塞派发点）与 `ATOMIC_RMW`（发出即放行） | 接在 SCHED 暂存出口；读响应送 `QueenQrdTracker` 的 SCHED 来源 FIFO；内存请求送 `QueenMemMaster` |
| QRD 在途表（`QueenQrdTracker`） | 已实现：前端 accept 时按 lane 序多推入口，`qrdDepth` 个 entry `{pc, rdIndex, source, dummy}`；GEMM/DMA/SCHED 各一个响应 FIFO；按程序序退休并驱动 `asyncRd`/`qrdRetire`；flush 期间全部 entry 回 `0xdeadbeef` | 准入余量回送 decoder；`resp.ready` 恒 1；同端点响应保序是设备义务 |
| 派发 adapter（`QueenDsaAdapter`） | 每端点一个：整包寄存 + beat 起点游标，按 `nativeWidth` 并行展开；写合并、`QCTRL`/`QRD` 独占 | 接在端点控制器之后；流控由设备单一 `cmd.ready` 表达 |
| 通用 DSA 寄存器接口（`QueenDsaRegIf`） | 设备侧参考实现：shadow/live、RO 状态、中断四寄存器、公共契约区、`start` pulse 与 signal/trace 上下文组合输出 | 位于 `queen/scala/common`，由 DSA 例化；不锁存在途上下文，不含 completion 生成与执行逻辑 |
| 同步状态（`QueenSyncState`） | 已实现：32 event group × 32 bit、8 timeline × 32 bit，硬件完成源 + 软件 SIGNAL 两个更新源，外露只读视图与回退报告 | 硬件源同拍双端点按位或/计数相加；软件源逐位置位优先；VPU 查询未实现 |
| GEMM 设备前端 | 目标契约：`QueenNativeCmd`→WorkerBee 命令映射、上下文采样、completion 与 `deviceIdle` 生成 | 设备侧工作，未实现 |
| DMA 设备前端 | 目标契约：寄存器写 + `RUN` 组装 `FbCmd`、上下文采样、`FbDone`→completion | 设备侧工作，未实现 |
| 内存主接口（`QueenMemMaster`） | 已实现：`memDepth` 笔在途、无 transaction ID，在途表只记「是否 READ」；READ 结果送 `POLL_EQ` 比较器，ATOMIC 只看 error；准入预留响应槽故 `memResp.ready` 恒 1 | 响应按请求序返回是总线硬义务；flush 只停发新请求并等已发出的响应回来 |
| flush 控制器（`QueenFlushCtrl`） | 已实现：三触发源汇聚，两侧四相 req/ack；先设备握手、再等全排空、再静默 `flushQuietCycles` 拍无扰动、然后清架构状态、最后回 CPU ack；`flushReq` 有效期间全程停在 flush 态 | 向 decoder/两端点控制器/两 adapter/SCHED 控制器/QRD 表/内存主接口广播 `flushing` 电平与 `start`/`clearState` pulse |
| 顶层 `QueenTop` | 已实现：例化 `QueenDsaDecoder`、两个 `QueenEndpointCtrl`、两个 `QueenDsaAdapter`、`QueenSchedCtrl`、`QueenSyncState`、`QueenQrdTracker`、`QueenMemMaster` 与 `QueenFlushCtrl`，外露 CPU 侧 `inst/rs1/rs2/trap/asyncRd/qrdRetire/flushReq/flushAck`、两端点 native beat 与读响应、两端点 completion/wrErr/deviceIdle/flushReq/flushAck、`memReq/memResp` 与 `idle/events/timelines/fault*/irq` 输出 | 配置、完成、同步、读回与恢复通路的集成边界；SCHED 暂存出口已内部消费不再外露；trace 管理尚未接入 |

`QueenTop` 是当前的发射目标（`QueenElaborate` → `src/queen/verilog`）。遗留 V1 簇（`Queen`、`QueenInterface`、`QueenISAEnum`、被整体注释且引用不存在 `WbConfig` 的 `WorkerBeeQueenAdapter`）已删除，`QueenCpuInterface.scala` 保留 V2 的 `QueenIssue/QueenTrap/QueenTrapCause/QueenAsyncRd/QueenRetire`。

配置、完成、同步、读回与恢复通路已整条闭环验证：`QueenTop` 直接对接两个 `QueenDsaRegIf` 与一个假 DSA（`start` 当拍采样上下文、延迟若干拍回传 completion、四相 flush ack），指令流经 native beat 落到设备寄存器、`RUN` 产生 `start` pulse，completion 回到 Queen 后置位 event，进而解除同端点队头 `WAIT_EVENT` 的阻塞并放行其后的配置写与 `RUN`；`QRD` 经在途表按程序序写回 GPR，软件可读同步状态与 fault 记录并写 SCHED `0x00` 清除 fault；三个 flush 触发源都能把卡在 `WAIT` 后面的在途 QRD 以 `0xdeadbeef` 退休并清零架构状态。真实设备执行与 trace 管理仍在这条通路之外。

### 4.2 数据通路与控制通路

已实现的通路是纯组合合并加两级存储：

1. `pending(i)` 每周期取 `io.inst(i).fire`，`pendingInst(i)` 在 fire 时锁存指令字。因此一包的有效 lane 恰好是本拍 fire 的 lane，位置不变。
2. `batch(i)` 为组合 `Wire`：`valid` 取 `pending(i)`，`inst` 取锁存值，`rs1/rs2` 取本拍 `io.rs1/rs2(i)` 且仅在 `pending(i)` 为真时有效，否则填 0。
3. 同一 4-lane 包写入 GEMM/DMA ring 的一个槽；SCHED lane 另写一份到 1 级暂存。未命中的 lane 在写入与出口都掩码为 `valid=0`、payload 填 0。
4. 出口 `io.gemm/dma` 组合读出各自游标指向的槽并按端点掩码；`io.scheduler` 直连暂存出口交给 `QueenSchedCtrl`。写入在同一时钟沿提交，没有额外寄存级或仲裁。

目标拓扑在队列之后增加端点执行层：GEMM/DMA 各有一个 adapter 把配置写与 `RUN` 转发到设备，SCHED 侧是同步资源与内存主接口。这些部件的输入是队列条目，输出不回写前端；唯一的反向通路是 completion、fault 与读写响应。

### 4.3 内部资源组织

| 资源 | 组织方式 | 所有权与访问 |
|---|---|---|
| 每 lane `pending` 位 | 1 bit × instructionLanes | 前端独占写 |
| 每 lane `pendingInst` | 32 bit × instructionLanes | 前端独占写 |
| GEMM/DMA 命令 ring | `cmdRingDepth` 个整包槽（默认 64）；`writePtr` 与 GEMM/DMA 两个游标 | 前端只写，每个消费者只推进自己的游标；槽在 `min(游标)` 处回收 |
| SCHED 暂存 | 1 个整包槽 | 前端只写，Scheduler 只读 |
| QRD 在途表 | 已实现（`QueenQrdTracker`），`qrdDepth` 个 entry（默认 16）`{pc, rdIndex(5), source(2)}` + 计数寄存器 + 读写物理索引；三个来源各一个深度 `qrdDepth` 的响应 FIFO | 前端 accept 时按 lane 序多推入口，退休时按程序序出表；物理索引 `% qrdDepth`，与计数分离故深度不必是 2 的幂 |
| 每端点 openGroup 标志 | 已实现，1 bit × 2（GEMM/DMA） | `QWR` 置位、`RUN` 清零，按 lane 序折叠，用于空组 `RUN` 检查 |
| 设备 `cmd.ready` | 单一流控入口，按 beat 内容分类 | 纯写恒 1；`RUN` beat 由设备 credit 与命令队列决定，归还点由设备自定；`QRD` beat 由设备读处理能力决定（Queen 侧 `resp.ready` 恒 1） |
| adapter beat 状态 | 已实现，每端点 1 个整包寄存 + `base` 起点游标（`log2Ceil(lanes+1)` bit） | beat 长度与掩码为组合逻辑；整包发完后才接受下一包 |
| event/timeline | 已实现（`QueenSyncState`），32 组 × 32 bit event、8 × 32 bit timeline | 硬件完成源置位/自增；软件 signal 落地后由同一模块修改，各端点只读查询 |
| 设备 completion 上下文 | 不在 Queen 侧存储：`pc/cmdTag/swTag/hwSignal` 由设备采样并随 completion 回传 | Queen 当拍消费 `hwSignal`/`error`，trace 字段暂不消费 |
| 端点控制器包状态 | 已实现，每端点 1 个整包寄存 + `base` lane 游标 | 控制命令条件为组合判定；转发切片按原序压缩到 lane 0 起 |
| SCHED 控制器包状态 | 已实现，1 个整包寄存 + `base` lane 游标 | 连续 QWR 切片一拍并行执行；QRD/SIGNAL/TRACE 各独占一拍 |
| fault 记录 | 已实现，`{valid, kind(2 bit), endpoint(2 bit), code}` 各一份，只保留最新一条 | 六源固定优先级；软件写 SCHED `0x00` 清 valid，新错误优先于清除 |

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
- QRD lane（`funct3=3'b010`，任意 endpoint）：在上述资源判定之外再叠加在途读表准入 `qrdUsed + 本拍已接受 QRD 数（含本 lane） <= qrdDepth`。计数按 lane 序折叠，故同拍多条 QRD 只有前 `qrdDepth - qrdUsed` 条能 fire。**分配点在 accept 而非派发**，否则排在 WAIT 后面的读会被后发的读超车。读表满只挡住 QRD lane，同包的写/控制 lane 仍可 fire。

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
| 前置条件 | 已实现的前端接收路径 | 目标契约的前端 entry |
| 输入与触发 | 本拍各 lane 的 `inst`（组合判定，先于 fire） | `inst.fire` |
| 处理语义 | 前端在退休前检查 opcode、funct3、cmd、保留位、`QWR2.regBase=63`、`SIGNAL_*`/`POLL_EQ`/`ATOMIC_RMW` 的 endpoint 约束、`FENCE`/`WAIT_*` 不得指向 SCHED、`RUN` 必须指向 GEMM/DMA 且配置组非空、`RUN` 的 `aux` 必须为 0、除 `SIGNAL_EVENT` 外全部 `QCTRL` 的 `inst[25]` 必须为 0；同周期多条非法时只报程序序最早的一条，其后 lane 不得 fire | fire 时预留请求 entry 与次拍操作数空间；`QWR1/QWR2/QCTRL` 在 entry 完成后即可退休，`QRD` 必须等读响应或异常 |
| 输出与完成 | 单周期 `trap`（`pc`/`inst`/`cause`），无 ready | `qrdRetire`（每周期至多一条）与 `asyncRd` |
| 边界情形 | 后端才能判定的错误不得走 trap，必须走异步 fault 或 QRD 错误响应 | 已接受指令不得由普通流水线 flush 撤销 |
| 异常关联 | 10.1 | 10.1、10.2 |

`trap.cause` 编码（`trapCauseWidth` 默认 8 bit，0 表示无异常）：

| cause | 含义 |
|---|---|
| 1 | opcode 不是 `custom-0`（`0x0b`） |
| 2 | funct3 保留（`3'b100`..`3'b111`） |
| 3 | `QCTRL.cmd` 保留（7 或 10..15） |
| 4 | endpoint 约束违规：`FENCE`/`RUN` 不是 GEMM/DMA，`WAIT_*` 指向 SCHED，`SIGNAL_*`/`POLL_EQ`/`ATOMIC_RMW` 不是 SCHED |
| 5 | 保留字段非 0：`QWR2.inst[10:7]`、`QWR1.inst[11:7]`、`QRD.inst[19:15]`，或该 cmd 要求为 0 的 `aux` 位 |
| 6 | `QWR2.regBase=63` |
| 7 | `inst[25]=1` 但该 cmd 不是 `SIGNAL_EVENT`（V2 已删除 notify，该位为保留） |
| 8 | 空配置组 `RUN`（openGroup 为 0） |
| 9 | `rdError`：QRD 读响应带 `err`。**只出现在 `qrdRetire.trapCause`，不占用同步 `trap` 端口**——读请求已被接受，错误只能在退休时上报 |

同一指令命中多项时按上表顺序取最早一项。`RUN` 的 `rs1/rs2` 是次拍返回的操作数值，不参与前端非法判定。cause 1..8 由前端组合判定并经 `trap` 上报；cause 9 由在途读表经 `qrdRetire` 上报。

### 5.4 QRD 读回

| 契约项 | QN-F008 | QN-F009 |
|---|---|---|
| 前置条件 | 在途表有空位（`qrdUsed + 本拍已接受 QRD 数 < qrdDepth`） | 存在表头 QRD 且其来源的响应 FIFO 非空 |
| 输入与触发 | `inst.fire` 当拍按 lane 序分配 entry；端点返回读响应或错误 | 表头响应到达 |
| 处理语义 | 分配点在**前端 accept**，不是派发点：否则排在 WAIT 后面的读会被后发的读超车。entry 只存 `{pc, rdIndex, source}`，不存 regAddr——同端点响应保序是设备义务，队头只认来源。GEMM/DMA/SCHED 各一个深度 `qrdDepth` 的响应 FIFO 缓存早到响应 | 正常结果驱动 `asyncRd{addr,data}`；`rd=x0` 执行读取但不写 GPR，响应到达表头即可退休；读错误不产生 `asyncRd` |
| 输出与完成 | 每周期至多一条退休；准入为每条在途 QRD 预留一个响应槽，故 Queen 侧 `resp.ready` 恒 1 | `qrdRetire` 为 `Decoupled`；`rd≠x0` 且无错时只在 `asyncRd.fire` 同拍退休，`rd=x0` 或读错误时直接退休 |
| 边界情形 | 写与控制指令不占读响应顺序；一拍最多 `instructionLanes` 条 QRD 同拍分配，写索引前进 popcount，无效 lane 被压缩跳过 | `asyncRd` 或 `qrdRetire` 反压期间表头与 `addr/data` 保持；读错误走 `qrdRetire.trap=1` + cause 9，不占用同步 `trap` 端口 |
| 异常关联 | 10.1 | 10.1 |

`source` 编码为 0=GEMM、1=DMA、2=SCHED（endpoint 3 映射为 2）。`qrdDepth` 默认 16、可配，`>= 1`；物理索引用 `% qrdDepth` 取槽，与计数寄存器分离，故深度不必是 2 的幂。

设备义务（见 `queenHarewareInterface.md` 第 16 章）：**同端点的读响应必须按请求序返回**。Queen 不为读响应打 tag、不做同端点重排；跨端点乱序由三个 FIFO 吸收。`READ` beat 的设备 `cmd.ready` 表达设备自己的读处理能力，与 Queen 的在途表容量无关。

### 5.5 配置写转发与 RUN 提交（目标契约）

| 契约项 | QN-F030 |
|---|---|
| 前置条件 | 目标为 GEMM/DMA；`RUN` beat 要求设备 `cmd.ready=1`，纯写 beat 恒可接受、`QRD` 只看读响应通路 |
| 输入与触发 | `QWR1`/`QWR2` 配置写；`QCTRL RUN` 提交 |
| 处理语义 | 配置写按序转发到设备配置寄存器，Queen 不保存影子；`RUN` 关闭当前配置组并启动设备，不携带配置数据；`cmdTag/swTag` 原样透传 |
| 输出与完成 | `Decoupled(Vec(nativeWidth, Valid(QueenNativeCmd{op, reg0Addr, reg1Addr, data0, data1, pc, cmdTag, swTag})))`，每 beat 至多 `nativeWidth` 条且至多一条 `RUN`；`RUN` 在前端入队后即可退休 |
| 边界情形 | 同一寄存器重复写以最后一条按序处理者为准（中断 W1C/W1S 例外，跨 lane 按位合并）；配置写之间无先后依赖，但 `RUN` 不得越过同端点此前的 `QWR`；openGroup 为 0 时的 `RUN` 为非法指令；`RUN` beat 的 ready 为 0 时只停该 beat，该端点后续指令按序等待 |
| 异常关联 | 未实现寄存器地址、空组 `RUN`、`RUN` endpoint 非法在前端报非法；设备访问错误走异步 fault |

因为 `RUN` 独占 beat，设备用**单一 `cmd.ready`** 即可表达全部流控：纯写 beat 恒 1，`RUN` beat 取设备能否接受新启动，`QRD` beat 取设备自己的读处理能力（Queen 侧 `resp.ready` 恒 1，见接口文档 16.6）。Queen 侧没有 `ready_start`/`ready_rd` 端口，`out.valid` 不依赖设备信号。credit 是设备内部量，只定义为“还能接受多少条 `RUN`”，**归还点由设备自定**（取走配置即还或完成时还），Queen 不看计数、不另设计数器，也不假设归还点。`RUN` 被设备接受即占用一个 credit。credit 为 0 时该 `RUN` beat 在派发级停等，同组配置写已在更早 beat 发出、不受拖累；该端点后续指令按程序序等待，另一端点不受影响。第一版不把 credit 纳入 5.1 的 ring 准入判定。

`QueenDsaAdapter` 的并行展开规则（接口文档 5.2）：一条指令对应一条 native 命令，`QWR2` 是**一条双地址写**（`op=WRITE2`，`reg1Addr=reg0Addr+1`、`data0=rs1`、`data1=rs2`），`QWR1` 一条单写，`RUN` 一条启动命令（`reg0Addr/reg1Addr/data0/data1` 为 0，`cmdTag` 取 `rs1` 低 8 位、`swTag` 取 `rs2`）。

beat 切分：一个 beat 是包内 lane 的连续切片，长度不超过 `nativeWidth`；**只有 `QWR1/QWR2` 可以合并**——首条有效 lane 是写时向后延伸穿过写与无效 lane，停在第一条 `QCTRL`/`QRD` 之前；首条有效 lane 是 `QCTRL`/`QRD` 时该 beat 只含这一条；全无效切片不占 fire 直接跳过；整包全部 beat 发完后才接受下一包；`in.ready` 取当拍 `base`，故末个 beat fire 的**次拍**才拉高，若其后仍有无效 lane 还要再花一拍跳过该切片。

`RUN` 独占 beat 的直接后果：该次启动的全部配置写都在更早的 beat 落进设备 shadow，设备在 `RUN` 当拍 `live := shadow` 即正确，**不存在“同 beat 写进快照”的问题**。

`nativeWidth` 由 `QueenConfig.nativeLanes` 决定，`0` 表示与 `instructionLanes` 对齐（默认 4），范围 `1..instructionLanes`。默认配置下一个 4 条 `QWR` 的包一拍发完。

当前 adapter 处理 `QWR1/QWR2/QRD/RUN`：`QRD` 展开为一条 `op=READ` 命令（`reg0Addr=inst[29:20]`，数据与 tag 字段为 0），独占一个 beat。`FENCE/WAIT/TRACE` 由端点控制器在本级之前消费；其它编码到达 adapter 会停等并触发仿真断言，属已知集成缺口（见 14.1）。

设备侧义务（实现不在本文范围内）：`RUN` 被接受后，后续 `QWR` 不得改变该 `RUN` 已提交的配置；`WRITE2` 的两个寄存器同时生效；设备在 `start` 当拍采样 `pc/cmdTag/swTag` 与 `0x080/0x081` 信号目标，执行完毕后随 completion 原样回传。要重叠配置准备与执行，设备需自行保证配置快照与在途上下文分离（通常需要双缓冲）。

### 5.6 端点执行与 DSA 适配

| 契约项 | QN-F011 | QN-F012 | QN-F013 | QN-F014 |
|---|---|---|---|---|
| 前置条件 | 端点队列非空 | 队列头为 RUN | 队列头为 RUN | 队列空或全速运行 |
| 输入与触发 | 队头条目条件满足 | 队头为配置写或 `RUN` | 队头为配置写或 `RUN` | 每周期评估 |
| 处理语义 | 同端点的 QWR/RUN/FENCE/WAIT/TRACE 严格按入队顺序执行，队头未完成不得推进后续 | 配置写逐条转发为设备寄存器写，`RUN` 转为启动命令；同一配置组的写与启动不得与其它组交错 | 配置写与 `RUN` 转发到设备配置前端，由设备组装 `FbCmd`；设备采样 `pc/cmdTag/swTag` 并随 completion 回传，Queen 不做 tag 映射 | 顶层 `idle` = decoder idle（ring 游标对齐 + SCHED 空 + 无 pending）&& SCHED 控制器 idle && 两端点控制器 idle && 两 adapter idle（`!active && deviceIdle`）；设备 `deviceIdle` 须覆盖队列空、无执行、无未完成内存请求/响应、无未回传 completion |
| 输出与完成 | 队头完成才出队 | 设备寄存器写与启动命令 | 设备配置写与启动命令 | 供 fence、`QUEEN_IDLE` 与状态输出使用 |
| 边界情形 | FENCE/WAIT 阻塞时不阻止其它端点 | `valid && !ready` 期间命令保持；credit 不足时停等 | `FbDone` 可乱序返回；Queen 不重排，`hwSignal` 的置位/自增与到达序无关 | `!busy`、`ready=1` 或输入 FIFO 空都不能单独作为 idle |
| 异常关联 | 10.1 | 10.1 | 10.1 | 无 |

`QueenEndpointCtrl` 的已实现行为：整包寄存 + lane 游标，窗口内首条有效 lane 是 `FENCE/WAIT_*/TRACE_TAG` 时独占一拍消费（条件满足才前进），否则把停在它之前的连续 QWR/RUN 切片转发给 adapter；全无效切片不占 fire 直接跳过。转发切片按原序压缩到 lane 0 起，相对顺序不变。`FENCE` 的条件取 `adapter.idle`（已含设备强 idle），`WAIT_*` 取 `QueenSyncState` 的只读视图。控制命令的 funct3 必须为 `3'b011`——`QRD` 的地址位会落在 cmd 字段上，只看 cmd 会把读命令误判为 `WAIT`。其它编码到达本级停等并触发仿真断言。

`QueenSchedCtrl` 的已实现行为：队头连续的 `QWR1/QWR2` 切片一拍并行执行（同拍多 lane 写 `POLL_INTERVAL` 以 lane 序最后一条为准），其余命令独占一拍；`SIGNAL_EVENT/SIGNAL_TIMELINE` 转成一次 `QueenSyncWrite`，`TRACE_TAG` 消费为 no-op，`QRD` 就地回读架构状态，`POLL_EQ` 阻塞派发点直到读值相等，`ATOMIC_RMW` 发出即放行，`FLUSH` 触发恢复流程（5.9）。

### 5.7 QCTRL 命令

`QCTRL` 的 `cmd` 分配与 `inst[25]` 的复用规则以 `queenISA.md` 第 6 节为准。下表给出各命令在 Queen 内的执行位置与完成条件；`RUN` 的提交与 credit 规则见 5.5。

| 契约项 | QN-F015 `FENCE` | QN-F016 `WAIT_EVENT` | QN-F017 `WAIT_TIMELINE` | QN-F018 `SIGNAL_TIMELINE` |
|---|---|---|---|---|
| 前置条件 | 已入对应队列 | 同左 | 同左 | 已入 SCHED 暂存 |
| 输入与触发 | 队头执行 | 队头执行 | 队头执行 | SCHED 执行 |
| 处理语义 | GEMM/DMA：等本设备强 idle；`endpoint=SCHED`/`VPU` 为非法编码 | 条件 `(event[aux] & rs1) == rs1`；只查询不消费 | 条件 `(int32_t)(completed[aux[2:0]] - rs1) >= 0` | `rs1` 编码为 `x0` 时自增，否则写入 `GPR[rs1]`；显式值不得回退 |
| 输出与完成 | 条件满足即消费该 lane，放行其后命令；不产生 CPU 可见事件 | 同左 | 同左 | 更新 timeline；不产生 CPU 可见事件 |
| 边界情形 | `endpoint=SCHED`/`VPU` 为非法；跨设备顺序改在消费者队列一侧用 wait 表达 | `endpoint=SCHED` 为非法；条件未满足时其后 lane 一律不越过 | `endpoint=SCHED` 为非法；单个 context 内在途距离必须小于 `2^31`；回绕后仍按 signed 距离判定 | 普通命令不得清零 timeline |
| 异常关联 | 10.1 | 10.1 | 10.1 | 显式回退抑制更新并报异步 fault（kind=2，code=timeline ID）——执行期才发现，指令已退休 |

| 契约项 | QN-F019 `SIGNAL_EVENT` | QN-F020 `TRACE_TAG` | QN-F021 `POLL_EQ` | QN-F022 `ATOMIC_RMW` |
|---|---|---|---|---|
| 前置条件 | 已入 SCHED 暂存 | 已入 endpoint 指定队列 | 已入 SCHED 暂存；地址 4-byte 对齐 | 同左 |
| 输入与触发 | SCHED 执行 | 在 endpoint 实际派发点执行 | SCHED 执行，间隔由配置寄存器给出（复位默认 8 周期） | SCHED 执行 |
| 处理语义 | `inst[25]=1` 置位，`=0` 清除，作用于 `aux[4:0]` 组与 `rs1` mask | 记录 `rs1` 作为 tracing tag | 发起 32-bit 读，与 `rs2` 比较，相等即完成，否则按间隔重试 | `aux[1:0]` 为 ADD/CLR/SET，`2'b11` 保留；通过平台原子事务实现 |
| 输出与完成 | 更新 event；不产生 CPU 可见事件 | 目标契约在记录点产生 trace 记录；当前消费为 no-op | 比较成功时消费该命令并放行后续；**不产生 CPU 可见完成通知** | 发出即消费该命令；V2 不返回旧值，不产生 CPU 可见完成通知 |
| 边界情形 | 与硬件置位同拍冲突时逐位置位优先 | `endpoint=SCHED` 由 SCHED 控制器消费 | 阻塞 SCHED 派发点，期间不执行其它命令；失配后等满 `POLL_INTERVAL` 才重读；`POLL_TIMEOUT`（`0x03`，复位 0=禁用）到期则中止本命令并放行派发点，但在途 READ 未回时不得提前放行 | 最多 `memDepth` 笔在途；不支持一致原子的区间只能用于 non-cacheable 独占地址 |
| 异常关联 | 10.1 | 10.1 | 未对齐不发请求，报 kind=3/code=1；总线错误 kind=3/code=0；超时 kind=3/code=2，是否 flush 由软件决定 | 同左（无超时） |

`QCTRL FLUSH`（cmd=10，endpoint 固定 SCHED，aux/flag 为 0）与写 SCHED `0x01 RECOVER`、CPU `flushReq` 端口共用同一条恢复流程，见 5.9。

`SIGNAL_EVENT` 和 `SIGNAL_TIMELINE` 是软件侧更新源。GEMM/DMA 的完成还可以作为硬件更新源（QN-F029）写同一套 event/timeline。软件源能置位、清除、赋值和自增；硬件源只能置位 event 和自增 timeline。两条路径的提交与可见性规则相同。

### 5.8 同步资源与 fault

| 契约项 | QN-F023 | QN-F025 | QN-F029 |
|---|---|---|---|
| 前置条件 | 复位完成 | 复位完成 | 设备在 `start` 当拍采样 `HW_SIGNAL_TARGET/MASK`，并随该次工作的 completion 回传 |
| 输入与触发 | 硬件源：两端点 completion 的 `hwSignal`；软件源：`QueenSchedCtrl` 每拍一条 `QueenSyncWrite`；GEMM/DMA 控制器内部查询，VPU 通过 `vpuSyncQuery` 查询（未实现） | 设备 completion 的 `error/code`、设备与 SCHED `wrErr`、polling/atomic 内存错误、timeline 显式回退；VPU 报错未实现 | Queen 直接接收该 RUN 的 completion（不经 adapter）及其回传目标 |
| 处理语义 | 32 组 32-bit event、8 个 32-bit timeline；查询只读；更新在时钟沿提交，下一周期各端可见。event：`(cur & ~swClr) | hwSet | swSet`（逐位置位优先）；timeline：软件赋值优先，否则 `cur + hwInc + swInc` | 只保留最新一条 fault 的 `{kind, endpoint, code}`，拉高 level `faultIrq`/`irq` 直到软件写 SCHED `0x00` | 按 completion 回传的采样 target 置位 event 的 group/mask，或自增 timeline；不经过 SCHED 暂存 |
| 输出与完成 | `events/timelines` 只读视图，软件经 SCHED `QRD` 读取；`vpuSyncValue` 与查询同周期对齐（未实现） | level 中断保持到软件清除或 reset | 时钟沿提交，下一周期各端点可见 |
| 边界情形 | signal 与 query 同周期时以提交后状态为准；软件赋值与硬件自增同拍时赋值优先（软件协议禁止混用）；显式回退抑制更新 | 同拍多源按 `completion(GEMM) > completion(DMA) > wrErr(GEMM) > wrErr(DMA) > wrErr(SCHED) > 内存错误 > 回退` 取一条；同拍新错误优先于清除；不带 pc 归因 | 两端点同拍各一条 completion 时 event 按位或、timeline 按源数累加，两条都不丢；`enable=0` 或 `mask=0` 为空操作；旧 context 的 completion 不得更新资源 |
| 异常关联 | 10.1 | 10.1、10.2 | 10.1、10.2 |

fault 寄存器访问必须独立于 SCHED 暂存的推进：SCHED 通路不参与任何条件等待，fault 逻辑也不得被任何端点的等待门控。

**V2 已删除完成通知机制**（QN-F024 标记已移除）：没有 notify 位、没有 notifyPulse、没有四来源 COUNT/ACK/PENDING/ENABLE。CPU 感知单引擎完成靠设备自己的中断块，跨引擎条件靠 event/timeline。`POLL_EQ/ATOMIC_RMW` 已裁定为**无 CPU 可见完成通知**：前者阻塞 SCHED 派发点、后者发出即放行，效果只体现为后续指令顺序与内存副作用。

### 5.9 内存主接口、flush 与状态

| 契约项 | QN-F026 | QN-F027 | QN-F028 |
|---|---|---|---|
| 前置条件 | 存在 polling/atomic 命令 | 三个触发源之一：SCHED `0x01 RECOVER` 写 / `QCTRL FLUSH` / CPU `flushReq` | 复位释放 |
| 输入与触发 | SCHED 执行到命令 | `QueenFlushCtrl` 状态机 | 连续评估 |
| 处理语义 | `QueenMemMaster` 允许 `memDepth`（默认 8）笔在途、无 transaction ID，**响应按请求序返回是总线硬义务**；在途表只记「是否 READ」，READ 结果送 `POLL_EQ` 比较器、ATOMIC 只看 error；地址必须 4-byte 对齐 | 两侧四相 req/ack（req↑ → ack↑ → req↓ → ack↓）：先与两设备握手，再等内部与设备全排空，再静默 `flushQuietCycles`（默认 8）拍无扰动，然后清 event/timeline/fault/openGroup，最后回 CPU ack。**`flushReq` 是 sys 级广播，整个 req 有效期间 Queen 都停在 flush 态**；静默窗内任何新指令 fire、迟到 completion 或总线响应都重新计数。flush 期间前端接受并丢弃、在途 QRD 回 `0xdeadbeef`、DSA 队列全丢、迟到 completion 丢弃、polling 中止；内存事务不可撤销，只停发新请求并等响应回来 | `QueenTop.idle` = decoder idle（ring 游标对齐 + SCHED 空 + 无 pending）&& SCHED 控制器 idle && 两端点控制器 idle && 两 adapter idle（`!active && deviceIdle`）&& 在途读表空（含三响应 FIFO）&& 内存主接口空 && flush 控制器空闲 |
| 输出与完成 | `memReq`/`memResp` 握手；准入预留响应槽故 `memResp.ready` 恒 1 | CPU 侧 `flushAck`；设备侧 `gemmFlushReq/ACK`、`dmaFlushReq/ACK` | `idle` 输出 |
| 边界情形 | 未对齐不发请求，直接报 fault 并消费该命令；`POLL_EQ` 失配后等满 `POLL_INTERVAL` 才重读；`POLL_TIMEOUT` 到期中止本命令并放行派发点，但在途 READ 未回时须等它回来；**`POLL_EQ` 停等，任一时刻至多一笔 READ 在途，且它在途时不允许再发任何事务** | 内部触发没有 sys req 可等，`flushAck` 只有一拍；`POLL_INTERVAL`/`POLL_TIMEOUT` 是软件配置旋钮，不随 flush 复位；排空条件在静默窗内失效则退回排空态；**软件义务**：flush 期间可继续发指令（接受并丢弃），但持续发射会让静默窗永不满足 | 任一条件不满足即 `idle=0`；SCHED `IDLE` 寄存器口径不含在途读表与 flush 控制器 |
| 异常关联 | 10.1（kind=3：code 0=总线错误、1=地址未对齐、2=POLL_EQ 超时） | 10.2 | 无 |

`inst.fire` 是不可撤销边界：已 fire 的指令只能由显式 flush/reset 终止，普通 CPU 流水线 flush 不得撤销。V2 不支持投机接收；若将来引入，必须在 issue payload 中增加 epoch/kill token，并重新定义所有副作用提交点。

## 6. 接口规范

方向以 Queen 为参照。6.1 为当前已实现的端口，6.2 为目标顶层端口清单（VPU 旁带仍未实现，其余已落地）。

### 6.1 已实现端口（`QueenDsaDecoder` / `QueenDsaAdapter` / `QueenTop`）

| 接口/信号 | 方向 | 宽度或类型 | 时钟域 | 含义与有效条件 |
|---|---|---|---|---|
| `inst(i)` | 输入 | `Decoupled(QueenIssue{pc, inst})`，i=0..L-1 | 单时钟 | 第 i 条 lane 的 PC 与指令字；`fire` 时 Queen 锁存 `inst`，次拍要求 `rs`；`pc` 供 trap 关联 |
| `rs1(i)` / `rs2(i)` | 输入 | `Valid(UInt(xlen.W))` | 单时钟 | 第 i 条 lane 的源操作数；仅在对应 lane 上拍 fire 时被采样，无 ready，不可暂停 |
| `gemm` | 输出 | `Decoupled(Vec(L, Valid(QueenDsaCommand)))` | 单时钟 | GEMM 端点的整包；每 lane 为 `{inst, rs1, rs2}` |
| `dma` | 输出 | 同上 | 单时钟 | DMA 端点 |
| `scheduler` | 输出 | 同上 | 单时钟 | SCHED 端点；1 级暂存出口，交给 `QueenSchedCtrl` |
| `idle`（decoder） | 输出 | `Bool` | 单时钟 | ring 写指针与两游标相等、SCHED 暂存空、无 pending lane |
| `trap` | 输出 | `Valid(QueenTrap{pc, inst, cause})` | 单时钟 | 同步非法指令，与非法 `inst` 同周期组合产生；无 ready，CPU 必须无条件采样；每周期至多一条 |
| `qrdUsed` | 输入 | `UInt(log2Ceil(qrdDepth+1).W)` | 单时钟 | 在途读表当前占用（不含本拍分配），来自 `QueenQrdTracker` |
| `qrdAlloc(i)` | 输出 | `Valid(QueenQrdEntry{pc, rdIndex, source})` | 单时钟 | 第 i 条 lane 本拍 fire 且为 QRD 时有效；按 lane 序压入在途表 |

`QueenTop` 已实现端口：把 decoder 的 CPU 侧端口原样外露，两端点出口经端点控制器与 adapter 换成 native beat，SCHED 暂存出口由 `QueenSchedCtrl` 内部消费、不再外露。

| 接口/信号 | 方向 | 宽度或类型 | 时钟域 | 含义与有效条件 |
|---|---|---|---|---|
| `inst` / `rs1` / `rs2` / `trap` | 输入/输出 | 同 decoder | 单时钟 | 原样透传 |
| `asyncRd` | 输出 | `Decoupled(QueenAsyncRd{addr, data})` | 单时钟 | 表头 QRD 的 GPR 写回；`rd=x0` 或读错误时不产生；`valid && !ready` 期间保持 |
| `qrdRetire` | 输出 | `Decoupled(QueenRetire{pc, rdValid, rdIndex, trap, trapCause})` | 单时钟 | 每周期至多一条，按 QRD 请求顺序；读错误时 `trap=1`、`trapCause=9`、`rdValid=0` |
| `gemm` / `dma` | 输出 | `Decoupled(Vec(nativeWidth, Valid(QueenNativeCmd)))` | 单时钟 | 两端点的并行 native beat |
| `gemmResp` / `dmaResp` | 输入 | `Flipped(Decoupled(QueenDsaRegResp{data, err}))` | 单时钟 | 设备读响应；Queen 侧 `ready` 恒 1（准入已预留槽），设备必须按请求序返回 |
| `gemmCompletion` / `dmaCompletion` | 输入 | `Flipped(Valid(QueenDsaCompletion{pc, cmdTag, swTag, error, code, hwSignal}))` | 单时钟 | 设备完成回传，Queen 无条件接收；每端点每拍至多一条，两端点可同拍各一条 |
| `gemmWrErr` / `dmaWrErr` | 输入 | `Vec(nativeWidth, Bool())` | 单时钟 | 设备写错误 per-lane pulse，按端点 OR 归约后锁存为 fault(kind=1) |
| `gemmDeviceIdle` / `dmaDeviceIdle` | 输入 | `Bool` | 单时钟 | 设备强 idle，定义见 5.6 |
| `memReq` / `memResp` | 输出/输入 | `Decoupled(QueenMemReq{op, address, operand})` / `Flipped(Decoupled(QueenMemResp{data, error}))` | 单时钟 | polling/atomic 主接口；`memDepth` 笔在途、无 ID，响应必须按请求序返回；`memResp.ready` 恒 1 |
| `flushReq` / `flushAck` | 输入/输出 | `Bool` / `Bool` | 单时钟 | CPU 侧四相恢复握手：req↑ → ack↑ → req↓ → ack↓ |
| `gemmFlushReq` / `dmaFlushReq` | 输出 | `Bool` | 单时钟 | Queen 向设备发起的四相 flush 请求 |
| `gemmFlushAck` / `dmaFlushAck` | 输入 | `Bool` | 单时钟 | 设备四相 flush 应答；两路都走完才进入排空 |
| `idle` | 输出 | `Bool` | 单时钟 | 多层强 idle 之与：decoder && SCHED 控制器 && 两端点控制器 && 两 adapter（含 deviceIdle）&& 在途读表（含响应 FIFO）&& 内存主接口 && flush 控制器 |
| `events` / `timelines` | 输出 | `Vec(32, UInt(32.W))` / `Vec(8, UInt(32.W))` | 单时钟 | 同步资源只读视图，时钟沿提交后次拍可见 |
| `faultValid` / `faultKind` / `faultEndpoint` / `faultCode` | 输出 | `Bool` / `UInt(2.W)` / `UInt(2.W)` / `UInt(faultCodeWidth.W)` | 单时钟 | 最新一条故障记录；kind 0=设备执行错误/1=写非法地址/2=timeline 回退/3=内存错误；endpoint GEMM=0、DMA=1、SCHED=3 |
| `faultIrq` / `irq` | 输出 | `Bool` | 单时钟 | level，均等于 `faultValid`（V2 无通知中断） |

顶层**没有 `faultClear` 端口**：清除故障的唯一通路是软件写 SCHED `0x00`（见 8.3），由 `QueenSchedCtrl.faultClear` 内部 pulse 驱动。

`QueenDsaAdapter`（每端点一个）已实现端口：

| 接口/信号 | 方向 | 宽度或类型 | 时钟域 | 含义与有效条件 |
|---|---|---|---|---|
| `in` | 输入 | `Flipped(Decoupled(Vec(L, Valid(QueenDsaCommand))))` | 单时钟 | 端点出口整包；整包全部 beat 发完后才 ready |
| `out` | 输出 | `Decoupled(Vec(nativeWidth, Valid(QueenNativeCmd)))` | 单时钟 | 一个 beat：纯写至多 `nativeWidth` 条，或单条 `QCTRL`/`QRD`；`valid` 不依赖 `ready`，`valid && !ready` 期间 payload 与各 lane `Valid` 保持 |
| `deviceIdle` | 输入 | `Bool` | 单时钟 | 设备强 idle，原样参与本级 idle 合成 |
| `idle` | 输出 | `Bool` | 单时钟 | `!active && deviceIdle`，即整包已发完且设备空闲 |

`QueenEndpointCtrl`（每端点一个）已实现端口：`in/out`（整包 `Decoupled`）、`events/timelines` 只读输入、`backendIdle`（接 `adapter.idle`）、`idle`。转发切片按原序压缩到 lane 0 起，切片含 `QWR/QRD/RUN`；beat 切分由 adapter 负责。

`QueenSchedCtrl` 已实现端口：`in`（SCHED 暂存整包）、`sw`（`Output(QueenSyncWrite)`，每拍至多一条）、`resp`（`Decoupled(QueenDsaRegResp)`，QRD 读响应）、`wrErr`（`Vec(L, Bool())`，非法写地址 per-lane pulse）、`faultClear`（`Bool`，写 `0x00` 当拍 pulse）、`flushTrig`（`Bool`，写 `0x01` 或执行 `FLUSH` 当拍 pulse）、`memReq`（`Decoupled(QueenMemReq)`）、`rdData`（`Flipped(Valid(UInt(32.W)))`）、`memUsed`、`addrErr`（`Bool`，未对齐 pulse）、`timeout`（`Bool`，`POLL_EQ` 超时中止当拍 pulse）、`events/timelines/faultValid/faultKind/faultEndpoint/faultCode` 只读输入、`idleIn`（不含本模块与在途读的派发通路 idle）、`flushing`、`idle`。

`QueenQrdTracker` 已实现端口：`alloc`（`Flipped(Vec(L, Valid(QueenQrdEntry{pc, rdIndex, source, dummy})))`）、`used`、`resp`（`Vec(3, Flipped(Decoupled(QueenDsaRegResp)))`，ready 恒 1）、`asyncRd`、`retire`、`markDummy`（flush 入口拍把全部 entry 标为 dummy）、`flushing`（丢弃旧响应并排空 FIFO）、`idle`。

`QueenMemMaster` 已实现端口：`req`（来自 SCHED）、`memReq`/`memResp`（系统总线）、`rdData`（只送 READ 结果）、`err`、`drop`（flush 期间停发新请求并抑制 stale 结果/错误）、`used`、`idle`。

`QueenFlushCtrl` 已实现端口：`trig`（内部 pulse）、`cpuReq`/`cpuAck`、`devReq`（`Vec(2, Bool())`）/`devAck`、`drained`、`disturb`（静默窗扰动：任何指令 fire、迟到 completion、总线响应）、`flushing`（电平，整个 req 有效期间为 1）、`start`（入口拍 pulse）、`clearState`（静默窗满足拍 pulse）、`idle`。构造参数 `quietCycles` 取自 `cfg.flushQuietCycles`。

`QueenSyncState` 已实现端口：`completion`（`Input(Vec(2, Valid(QueenDsaCompletion)))`）、`sw`（`Input(QueenSyncWrite)`）、`events/timelines` 只读输出、`rollback/rollbackId`、`clear`（flush 清状态拍清零）。只消费 `hwSignal` 与软件写，trace 字段与 `error/code` 不进本模块。

### 6.2 目标顶层端口清单

`QueenTop` 已实现下表除 VPU 旁带外的全部端口（`rs1/rs2` 为独立向量而非交织的 `rs`）。SCHED 暂存出口已内部消费，不再外露；`faultClear` 端口已删除。

| 接口/信号 | 方向 | 宽度或类型 | 时钟域 | 含义与有效条件 |
|---|---|---|---|---|
| `inst` | 输入 | `Vec(L, Flipped(Decoupled(QueenIssue)))`，`QueenIssue{pc, inst}` | 单时钟 | 已实现：指令字之外携带 `pc`，供 trap、`qrdRetire` 与 fault 关联 |
| `rs` | 输入 | `Vec(2L, Flipped(Valid(xlen)))` | 单时钟 | lane i 的 `rs1` 固定接 `rs(2i)`，`rs2` 接 `rs(2i+1)` |
| `asyncRd` | 输出 | `Decoupled(QueenAsyncRd{addr, data})` | 单时钟 | 已实现：表头 QRD 成功结果；`valid && !ready` 期间保持 |
| `qrdRetire` | 输出 | `Decoupled(QueenRetire{pc, rdValid, rdIndex, trap, trapCause})` | 单时钟 | 已实现：每周期至多一条，按 QRD 请求顺序；读错误 `trap=1`、cause 9 |
| `trap` | 输出 | `Valid(QueenTrap{pc, inst, cause})` | 单时钟 | 已实现：同步非法指令；无 ready，CPU 必须无条件采样 |
| `irq` | 输出 | `Bool` | 单时钟 | 已实现：等于 `faultIrq`，level-sensitive |
| `idle` | 输出 | `Bool` | 单时钟 | 已实现（多层合成，含在途读表、内存主接口与 flush 控制器），完整定义见 5.9 |
| `gemm` / `dma` | 输出 | `Decoupled(Vec(nativeWidth, Valid(QueenNativeCmd)))` | 单时钟 | 已实现：adapter 生成的并行 beat，`valid && !ready` 期间保持 |
| `gemm.ready` / `dma.ready` | 输入 | `Bool` | 单时钟 | 已实现：设备单一 ready，纯写恒 1，`RUN` beat 按 credit，`QRD` beat 按设备读处理能力 |
| `gemmResp` / `dmaResp` | 输入 | `Flipped(Decoupled(QueenDsaRegResp{data, err}))` | 单时钟 | 已实现：设备读响应，Queen `ready` 恒 1；同端点必须按请求序返回 |
| `gemmCompletion` / `dmaCompletion` | 输入 | `Flipped(Valid(QueenDsaCompletion{pc, cmdTag, swTag, error, code, hwSignal}))` | 单时钟 | 已实现：完成与错误回传，Queen 无条件接收；`hwSignal` 与 trace 上下文为设备在 `start` 当拍采样的值 |
| `gemmDeviceIdle` / `dmaDeviceIdle` | 输入 | `Bool` | 单时钟 | 已实现：设备强 idle |
| `events` / `timelines` | 输出 | `Vec(32, UInt(32.W))` / `Vec(8, UInt(32.W))` | 单时钟 | 已实现：同步资源只读视图 |
| `faultValid` / `faultKind` / `faultEndpoint` / `faultCode` / `faultIrq` / `irq` | 输出 | `Bool` / `UInt(2.W)` / `UInt(2.W)` / `UInt(faultCodeWidth.W)` / `Bool` / `Bool` | 单时钟 | 已实现：最新一条故障（含 kind）与 level 中断；清除走软件写 SCHED `0x00` |
| `gemmWrErr` / `dmaWrErr` | 输入 | `Vec(nativeWidth, Bool())` | 单时钟 | 已实现：设备写错误 pulse，锁存为 fault(kind=1) |
| `flushReq` / `flushAck` | 输入/输出 | `Bool` / `Bool` | 单时钟 | 已实现：CPU 侧四相恢复握手，见 5.9 |
| `gemmFlushReq` / `gemmFlushAck` / `dmaFlushReq` / `dmaFlushAck` | 输出/输入 | `Bool` | 单时钟 | 已实现：设备侧四相 flush 握手，见 5.9 |
| `vpuSyncQuery` / `vpuSyncValue` | 输入/输出 | `Flipped(Valid(QueenSyncQuery))` / `QueenSyncValue` | 单时钟 | 未实现：组合只读查询，值必须与 query 对齐 |
| `vpuFault` | 输入 | `Flipped(Decoupled(QueenDsaCompletion))` | 单时钟 | 未实现：VPU 错误旁带，不得用 pulse |
| `memReq` / `memResp` | 输出/输入 | `Decoupled(QueenMemReq)` / `Flipped(Decoupled(QueenMemResp))` | 单时钟 | 已实现：polling/atomic 专用主接口，`memDepth` 笔在途、响应按序 |

### 6.3 协议与握手

- `Decoupled` 接口统一遵循“`valid && !ready` 期间 payload 不变，`fire` 表示接收方接受”。
- `inst` 与 `rs` 不构成同一条握手：`inst` 有 ready，`rs` 没有。Queen 侧必须把两者在时间上绑定，CPU 侧必须保证已 fire 指令的源操作数在次一周期返回。
- 整包出口一次 fire 传输全部 lane；消费方不得只取其中一部分再重新握手。
- `trap`、`irq`、`idle` 为电平/单周期无握手输出，CPU 必须无条件可采样。
- `completion` 只有 `valid`：Queen 无条件接收，设备不得等待 ready，也不得重复回传同一条完成。每端点每拍至多一条，两端点同拍各一条时 Queen 必须全部吸收。

### 6.4 接口依赖与互斥

- 三个出口共享同一个 4-lane 包，各出口独立握手；GEMM/DMA 还共享 ring 容量，SCHED 容量独立。
- `asyncRd` 与 `qrdRetire` 由同一表头驱动：`rd≠x0` 且无错时二者同拍 fire；`rd=x0` 或读错误时只有 `qrdRetire` fire。`trap`（前端非法）与 `qrdRetire.trap`（读错误）是两条互斥通路，前者在 accept 前、后者在退休时。
- Queen 侧 `gemmResp.ready`/`dmaResp.ready` 恒 1：准入已为每条在途 QRD 预留响应槽，设备不得被读响应反压。
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
| timeline 计数 | 32 bit | 无符号回绕 | 模 `2^32` 递增，比较用 signed 距离 |
| fault kind | 2 bit | 0=设备执行错误 / 1=配置写非法地址 / 2=timeline 回退 | 枚举 |
| fault endpoint | 2 bit | 0=GEMM / 1=DMA / 3=SCHED | 枚举 |

### 7.2 编码与打包

指令字段布局以 `queenISA.md` 为准，本文不重复。Queen 侧只使用 `inst[31:30]` 做路由，其余字段原样透传给下游执行层与命令解码。已实现的出口包按 lane 位置打包，无效 lane 的 `inst`、`rs1`、`rs2` 全部填 0，因此空 lane 的 `inst[31:30]=2'b00`，消费方必须先用 `Valid` 过滤，不能只看 payload。

`2'b10` 是 VPU，由 Scalar Core decoder 直发 VPU 前端；送到 Queen 一律在前端报 trap cause 4，因为 Queen 没有 VPU 队列，见 14.1。

### 7.3 存储布局与地址约定

polling/atomic 使用 32-bit、4-byte 对齐的物理地址（`QueenMemReq.address` 宽 `xlen`）；`ATOMIC_RMW` 只能用于平台保证原子性且由 Scheduler 独占的地址区间。物理地址空间归属与总线错误编码由平台定义；polling 超时阈值是运行期寄存器 SCHED `0x03 POLL_TIMEOUT`（复位 0=禁用），不是平台常量。

## 8. 配置与寄存器规范

### 8.1 编译期参数

| 参数 | 默认值 | 合法范围/依赖 | 影响 |
|---|---|---|---|
| `instructionLanes` | 4 | `>= 1`（elaboration `require`） | 每包 lane 数、出口向量宽度、次拍 `rs` 端口数 |
| `nativeLanes` | 0 | `>= 0` 且派生 `nativeWidth` 落在 `1..instructionLanes`（`require`） | 原生配置命令出口并行度；0 表示与 `instructionLanes` 对齐 |
| `cmdRingDepth` | 64 | `>= 2`（`require`） | GEMM/DMA 共享命令 ring 的整包槽数 |
| `qrdDepth` | 16 | `>= 1`（`require`） | QRD 在途表 entry 数，也是每个来源响应 FIFO 的深度；不必是 2 的幂 |
| `memDepth` | 8 | `>= 1`（`require`） | polling/atomic 内存主接口在途事务数；无 transaction ID，响应必须按请求序返回 |
| `flushQuietCycles` | 8 | `>= 1`（`require`） | flush 排空后到拉 `flushAck` 之间要求连续无扰动的拍数；任何新指令 fire/迟到 completion/总线响应都重新计数 |
| `trapCauseWidth` | 8 | `>= 4`（`require`） | `trap.cause` 位宽 |
| `faultCodeWidth` | 8 | `>= 1`（`require`） | `fault.code` 位宽；kind=0 时透传设备码，kind=2 时放 timeline ID |
| `eventGroupCount` | 32 | `== 32`（`require`） | event 组数；`hwSignal.group` 为 5-bit，故固定 |
| `timelineCount` | 8 | `== 8`（`require`） | timeline 数；`hwSignal.timeline` 为 3-bit，故固定 |
| `xlen` | 32 | 无 `require` | `rs1/rs2` 与 `QueenDsaCommand` 字段宽度 |
| `pcWidth` | 32 | 无 `require` | `QueenIssue`/`QueenTrap`/`QueenDsaCommand`/`QueenNativeCmd` 的 `pc` 宽度 |
| `slotDepth` | 8 | 无 `require` | 无消费者，保留字段 |
| `instWidth` | 32 | 无 `require` | 无消费者，保留字段 |

注意容量口径：ring 深度按“包”计数，不按“指令条数”计数。GEMM 与 DMA 共用这 64 个槽，混合包只占 1 个槽；SCHED 暂存固定 1 个槽，不作为可配置项。

### 8.2 运行期配置

运行期配置集中在 SCHED 端点寄存器（8.3.1）：

- polling 间隔 `POLL_INTERVAL`（`0x02`）：正整数，复位默认 8 周期，写 0 非法（报 `wrErr` 且保持原值）；**不随 flush 复位**。
- polling 超时 `POLL_TIMEOUT`（`0x03`）：一条 `POLL_EQ` 的最大 cycle 数，复位 **0 = 禁用**；到期报 fault 并中止该命令，是否 flush 由软件决定；**不随 flush 复位**。
- 恢复触发 `RECOVER`（`0x01`）：任意值写入即触发一次 flush，与 `QCTRL FLUSH`、CPU `flushReq` 端口等价。该路径在 `QueenSchedCtrl` 就地执行，不经过可能被冻结的普通命令队列。
- 硬件信号目标 `HW_SIGNAL_TARGET[e]`：只对 GEMM/DMA 端点存在，属于该端点的设备配置寄存器。软件在 `RUN` 之前用 `QWR` 写入，设备在完成点应用；运行时改写只影响后续启动的 RUN。

这些配置的更新在时钟沿生效；同一周期内的 signal 与 query 关系见 5.8。

### 8.3 寄存器映射

#### 8.3.1 SCHED 端点寄存器（已实现，QN-F032）

由 `QueenSchedCtrl` 就地实现，软件用 `QWR1 endpoint=SCHED` 写、`QRD endpoint=SCHED` 读。地址沿用 DSA 设备的 10-bit 寄存器地址风格，但**不复用 `QueenDsaRegIf`**：SCHED 没有 `RUN`、没有配置 shadow/live 双组，读的是 Queen 自己的架构状态。

| 地址 | 名称 | 访问属性 | 复位值 | 功能 |
|---|---|---|---|---|
| `0x00` | `FAULT_CLEAR` | WO | 0 | 任意值写入即清 `faultValid`；同拍新错误优先。读回 0 且报 `err` |
| `0x01` | `RECOVER` | WO | 0 | 任意值写入即触发一次 flush（5.9）。读回 0 且报 `err` |
| `0x02` | `POLL_INTERVAL` | RW | 8 | `POLL_EQ` 失配后的重读间隔（cycle）；**写 0 非法**，报 `wrErr` 且保持原值 |
| `0x03` | `POLL_TIMEOUT` | RW | 0 | 一条 `POLL_EQ` 的最大 cycle 数；**0 = 禁用**。到期报 kind=3/code=2 并中止该命令、放行派发点 |
| `0x04..0x3F` | 保留 | — | 0 | 写报 `wrErr`，读报 `err=1` 且 `data=0` |
| `0x40..0x5F` | `EVENT[0..31]` | RO | 0 | `events[addr-0x40]` 的 32-bit 当前值 |
| `0x60..0x67` | `TIMELINE[0..7]` | RO | 0 | `timelines[addr-0x60]` 的 32-bit 当前值 |
| `0x68` | `FAULT_VALID` | RO | 0 | 最新一条 fault 的有效标志 |
| `0x69` | `FAULT_KIND` | RO | 0 | 0=设备执行错误 / 1=写非法地址 / 2=timeline 回退 / 3=内存错误（code 0=总线错误、1=未对齐、2=POLL_EQ 超时） |
| `0x6A` | `FAULT_ENDPOINT` | RO | 0 | GEMM=0、DMA=1、SCHED=3 |
| `0x6B` | `FAULT_CODE` | RO | 0 | `faultCodeWidth` 位错误码 |
| `0x6C` | `IDLE` | RO | 1 | bit0 = 派发通路强 idle（见下） |
| `0x6D..0x3FF` | 保留 | — | 0 | 写报 `wrErr`，读报 `err=1` 且 `data=0` |

四个 fault 字段是**四个独立寄存器**，不打包成一个字：软件按需单读，避免读改写竞争。

`IDLE` 的 bit0 = `decoder.idle && 两端点控制器.idle && 两 adapter.idle（含 deviceIdle）&& schedCtrl.idle`，**不含在途读表与 flush 控制器**——否则读 `IDLE` 的那条 QRD 自身就把读回值钉死为 0。顶层 `idle` 端口额外与在途读表空、内存主接口空、flush 控制器空闲取与，口径比 `IDLE` 寄存器更严。

执行纪律：队头连续的 `QWR1/QWR2` 切片**一拍并行执行**（`wrErr` 为 per-lane pulse，`FAULT_CLEAR`/`RECOVER` 按 lane OR 归约，同拍多 lane 写 `POLL_INTERVAL`/`POLL_TIMEOUT` 以 lane 序最后一条为准）；`QRD`/`SIGNAL_EVENT`/`SIGNAL_TIMELINE`/`TRACE_TAG`/`POLL_EQ`/`ATOMIC_RMW`/`FLUSH` 各独占一拍。因此 `QWR → SIGNAL → QRD` 天然保序，不需要额外握手。`QWR2` 的第二个地址是 `regBase+1`：`0x02/0x03` 相邻且都合法，一条 `QWR2` 可一拍同时写间隔与超时；指向 `0x00` 的 `QWR2` 仍会同时触发清除与 `wrErr`，软件应只用 `QWR1` 写 `FAULT_CLEAR`/`RECOVER`。

`POLL_EQ`/`ATOMIC_RMW` 由 `QueenMemMaster` 执行（5.9）：前者阻塞派发点直到读值相等，后者发出即放行，两者都不产生 CPU 可见完成通知。

#### 8.3.2 设备侧硬件信号目标

硬件信号目标不放在 Scheduler 地址空间，而是 GEMM/DMA 设备公共契约区的固定地址寄存器（`0x080/0x081`，完整分区与字段见 `queenHarewareInterface.md` 第 16 章），由 `QWR1` 写入；设备在 `start`（`RUN` 被接受）当拍采样，完成时随 completion 回传：

| 字段 | 位置 | 访问 | 复位值 | 功能 |
|---|---|---|---|---|
| `HW_SIGNAL_TARGET[e]` | 端点 e 设备地址 `0x080` | RW，回读走 `QRD` | 0（禁用） | bit0 enable（即原 `HW_SIGNAL_VALID`）、bit1 kind（0=event/1=timeline）、bit[6:2] group、bit[9:7] timeline、bit[31:10] 保留必须为 0 |
| `HW_SIGNAL_MASK[e]` | 端点 e 设备地址 `0x081` | RW | 0 | event mask[31:0]，bit i 对应 `events[group]` 的 bit i；kind=timeline 时忽略 |

Queen 不保存这两个字段的副本；写入后到下一次 `RUN` 之间只影响新启动的工作。两者是单一持久副本，不参与设备双 bank 切换，因此目标不变时可跨多条 `RUN` 只写一次。`QRD` 读回当前可写副本，不是任何在途工作的采样值。设备只做采样与回传，不检查 group/timeline 范围；越界目标由 Queen 在应用时丢弃并上报 fault。

一条 `RUN` 的硬件信号只能指向**一个** group（5 bit）或一个 timeline（3 bit）；同组内可用 mask 一次置多位。跨多个 group 的置位需要多条 `RUN`，或由软件 `SIGNAL_EVENT` 完成。

## 9. 时序与复位规范

### 9.1 时钟与跨域

Queen、Scalar Core 与各 DSA 默认同一时钟域。本文所有时序均以该时钟为准，没有内部 CDC。跨时钟集成必须在边界增加异步 FIFO、事件计数器或等价结构：单周期 pulse 不得直接跨域。这一义务属于集成方，不属于 Queen。

### 9.2 复位与初始化

复位为 Chisel 隐式同步复位，在时钟沿生效。复位后每 lane 的 `pending` 为 0，`writePtr` 与两个游标为 0，两个 openGroup 位为 0，adapter 的 `active/base` 为 0，ring 内容与暂存不参与判定，出口 `valid` 与 `trap.valid` 为 0。复位期间 `inst.ready` 可能为高，上游应同时保持复位或在释放后重新发起指令，不得依赖复位期间被接受的指令。

目标契约要求复位后前端、QRD 顺序表、端点队列与 native adapter 全部为空，设备配置寄存器取寄存器表规定的复位值，Queen 侧 openGroup 清零、credit 与设备重新对齐，event/timeline 与 fault 状态按规范复位，`irq` 拉低，且不存在旧 context 的 completion。

### 9.3 事务时序与反压

已实现通路的时间关系（结构推导，见 12.2）：

```text
cycle N    : 组合判定合法性；非法则同周期 trap 且该 lane 不 fire
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
| 已实现：保留 opcode/funct3/cmd、保留位非 0、endpoint 约束违规、`FENCE`/`WAIT_*` 指向 SCHED、空组 `RUN`、`regBase=63`、`inst[25]` 规则 | 前端组合判定 | `trap{pc,inst,cause}`，cause 编码见 5.3 | 不进入端点队列；只报程序序最早一条，其后 lane 不 fire | 随 CPU trap 处理 |
| 已实现：未实现寄存器地址 | 不检测 | 无 | 原样透传给设备 | 不适用 |
| 目标契约：`SIGNAL_TIMELINE` 显式值回退 | 执行时 | 同步错误 | 不更新 timeline | 随 CPU trap 处理 |
| 已实现：DSA 执行错误、`FbDone.err` | 设备 completion 的 `error/code` | 最新一条 `fault{valid,kind=0,endpoint,code}` + level `faultIrq` | 失败工作不发布 event/timeline；队列冻结尚未实现 | 软件写 SCHED `0x00` 或 reset |
| 已实现：配置写非法地址（GEMM/DMA 设备） | 设备 `wrErr` per-lane pulse | `fault{kind=1,endpoint,code=0}` | 该写被设备丢弃，后续 `RUN` 会用旧配置 | 软件写 SCHED `0x00` 或 reset |
| 已实现：SCHED 寄存器写非法地址 | `QueenSchedCtrl` 地址判定 | per-lane `wrErr` pulse → `fault{kind=1,endpoint=SCHED,code=0}` | 该写被丢弃 | 软件写 SCHED `0x00` 或 reset |
| 已实现：QRD 读非法地址 | 设备或 SCHED 前端返回 `resp.err=1` | `qrdRetire{trap=1, trapCause=9}`，不产生 `asyncRd` | 该读不写 GPR；不影响其它在途读的顺序 | 随 CPU trap 处理 |
| 已实现：`SIGNAL_TIMELINE` 显式回退 | SCHED 执行期 signed 距离为负 | `fault{kind=2,endpoint=SCHED,code=timeline ID}` | 抑制该次赋值，同拍硬件自增仍生效 | 软件写 SCHED `0x00` 或 reset |
| 已实现：polling/atomic 地址未对齐 | SCHED 执行期 `rs1[1:0] != 0` | `fault{kind=3,endpoint=SCHED,code=1}` | 不发内存请求，直接消费该命令 | 软件写 SCHED `0x00`、flush 或 reset |
| 已实现：polling/atomic 总线错误 | `memResp.error=1` | `fault{kind=3,endpoint=SCHED,code=0}` | 不发布成功状态；在途事务按序消费 | 软件写 SCHED `0x00`、flush 或 reset |
| 已实现：`POLL_EQ` 超时 | `POLL_TIMEOUT`（SCHED `0x03`，0=禁用）计数到 0 | `fault{kind=3,endpoint=SCHED,code=2}` + level `faultIrq` | 中止该命令并放行派发点；在途 READ 未回时等它回来才放行；超时后不再发 READ | 软件写 SCHED `0x00`、自行决定是否 flush、或 reset |
| 目标契约：VPU fault | 在途工作 | 异步 fault + `irq` | 冻结相关队列 | CPU ACK 或 reset |
| 已实现：`inst` 需要 `rs` 但 `rs.valid=0` | 无检测 | 无 | 该源填 0，指令继续派发 | 不适用 |
| 已实现：`endpoint=2'b10`（VPU）误路由 | 前端 `trapCause` | 同步 trap cause 4（`endpoint`），该 lane 及其后 lane 不 fire | 指令不入队；Queen 无 VPU 队列，收下就永远没有消费者 | 随 CPU trap 处理 |

当前交付有同步 `trap`（cause 1..8）、退休时的读错误（`qrdRetire.trap`，cause 9）与四类异步 fault 源（设备 completion、`wrErr`（含 SCHED）、内存错误、timeline 回退）及 `faultIrq`/`irq`；flush 恢复已实现，VPU 通路 fault 与 polling 超时仍未定义。仿真断言（第 9.3 节的入队断言）只在验证环境生效，不属于硬件错误接口。

### 10.2 优先级与恢复

已实现语义：只保留**最新一条** fault 的 `{valid, kind, endpoint, code}`，后到错误覆盖先到错误；同拍多源按固定优先级取一条；`faultIrq` 为 level 且等于 valid，保持到软件写 SCHED `0x00`、flush 清状态或 reset，同拍新错误优先于清除。fault 不带 pc/sequence 归因——归因信息在 completion 的 trace 上下文里。fault 管理寄存器访问不受 endpoint WAIT/FENCE 阻塞（SCHED 与 GEMM/DMA 队列独立），但**会被未完成的 `POLL_EQ` 挡住**（它阻塞 SCHED 派发点）；这是「无 CPU 感知」的直接代价，硬件不加旁路，需要保证可清时配置 `POLL_TIMEOUT`。`error=1` 的 completion 不得携带有效目标，故失败工作不会发布 event/timeline 成功状态。

已接受但未完成的指令不可由 CPU 普通流水线 flush 回滚，只有显式 Queen flush/reset 能终止。flush 已实现（5.9）：被丢弃的命令不会再开始执行、无法取消的总线事务等其自然返回、旧 context 的 completion 一律丢弃。

硬件信号源同样受这条约束：flush/reset 后，旧 context 的 completion 一律丢弃，不得更新新 context 的 event 或 timeline；设备已采样但尚未回传的目标在 flush 时一并作废。

## 11. 状态与观测规范

### 11.1 状态输出

已实现部分没有状态输出端口。队列占用、空满与在途包数均不对外暴露。

目标契约包含：

| 信号 | 含义 | 属性 |
|---|---|---|
| `idle` | Queen 与全部 DSA 均无在途工作 | level，条件见 5.9 |
| `irq` | fault 待处理（等于 `faultIrq`） | level-sensitive，直到软件写 SCHED `0x00` |
| `trap` | 同步非法指令 | 单周期，无 ready |

### 11.2 中断、性能事件与调试

| 观测项 | 触发/统计定义 | 保持与清除 | 可靠性边界 |
|---|---|---|---|
| `FAULT_*` | 最新一条异步错误 | 保持到软件写 SCHED `0x00`/reset | 后续错误覆盖旧记录；同拍双错取低序号端点 |

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
| adapter 整包寄存 | 每端点 1 个 `instructionLanes × (96 + pcWidth + 1)` bit | 编译期 |
| adapter beat 游标 | `log2Ceil(instructionLanes + 1)` bit（无相位位） | 编译期 |
| adapter 出口向量 | `nativeWidth × QueenNativeCmd`，默认 4 路 | 编译期 |
| QRD 在途表容量 | `qrdDepth` 个 entry，默认 16 | 编译期 |
| QRD entry 位宽 | `pcWidth + 5 + 2 + 1`（含 dummy 位），默认 40 bit | 编译期 |
| QRD 响应 FIFO | 3 个来源 × `qrdDepth` 深 × `(32 + 1)` bit | 编译期 |
| QRD 准入余量位宽 | `log2Ceil(qrdDepth + 1)` bit，默认 5 | 编译期 |
| 内存在途表容量 | `memDepth` 笔，默认 8；每笔只存 1 bit「是否 READ」 | 编译期 |

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
| adapter 发射率 | ≤ `nativeWidth` 条 native 命令/周期（默认 4） | 结构上限 | 一条指令一条命令，`QWR2` 不再占 2 拍；`QCTRL`/`QRD` 独占 beat，故含 k 条 `RUN` 的包至少 k+1 个 beat（写与启动分离）；全无效切片每拍跳过一个切片 |
| adapter 包接受间隔 | 整包全部 beat 发完后 | 结构上限 | 末个 beat fire 的次拍才可接受下一包；`RUN` 收尾后若仍有无效 lane，再需一拍跳过该切片 |
| `RUN` 发射条件 | 该 beat 的 `cmd.ready = 1` | 设计保证 | 为 0 时只停该 beat；同组配置写已在更早 beat 发出，不受门控 |
| QRD 分配率 | ≤ `instructionLanes` 条/周期 | 结构上限 | 在前端 accept 当拍按 lane 序多推入口，写索引前进 popcount；受 `qrdDepth` 余量门控 |
| QRD 退休率 | 1 条/周期 | 结构上限 | 只有表头可退休；`rd≠x0` 且无错时还需 `asyncRd.fire` |
| QRD 在途上限 | `qrdDepth`，默认 16 | 编译期 | 三个来源共享该上限；每个来源的响应 FIFO 深度也等于它，故准入预留后 `resp.ready` 恒 1 |
| SCHED 寄存器写率 | ≤ `instructionLanes` 条/周期 | 结构上限 | 队头连续 QWR 切片一拍并行执行；QRD/SIGNAL/TRACE 各独占一拍 |

没有实测的功耗、面积或频率数据。本文不把结构推导当作 PPA 结论。

## 13. 集成与使用规范

### 13.1 外部环境要求

- Scalar Core decoder 必须按 endpoint 分流：`2'b10` 指令不得送入 Queen。当前实现没有兜底，误送会导致该 lane 永久不 ready。
- 已 fire 指令的 `rs1/rs2` 必须在次一周期提供且 `valid=1`；Queen 不检查是否“按需返回”，缺省填 0。
- 上游在 `valid && !ready` 期间必须保持 `inst` 稳定。
- 下游必须能整包接收 `Vec(L, Valid(QueenDsaCommand))`，不能按 lane 拆分后重新握手。
- Queen、Scalar Core 与 DSA 必须同处一个时钟域，或由集成方在边界提供 CDC。
- 设备必须提供 `completion`（只有 valid，每端点每拍至多一条）与 `deviceIdle`；Queen 无条件接收 completion，设备不得等待 ready，也不得重复回传。
- 设备必须在 `start` 当拍采样 `pc/cmdTag/swTag` 与 `0x080/0x081` 信号目标，并随该次工作的 completion 原样回传；`error=1` 时不得携带有效目标。
- 目标契约中，VPU 需自行实现其指令顺序、WAIT 阻塞、标量写回与完成中断；Queen 只提供同步资源查询与 fault 汇总。

### 13.2 使用流程

已实现部分的使用流程：

1. 复位释放后，Scalar Core 在任意 lane 上给出 `inst.valid` 与指令字。
2. Queen 在容量允许时拉高该 lane 的 `ready`，`fire` 即接受。
3. 次一周期提供同一 lane 的 `rs1/rs2`。
4. 包在次拍写入：GEMM/DMA lane 进共享命令 ring 的一个槽，SCHED lane 进 1 级暂存。
5. GEMM 与 DMA 各自沿游标按序取走命中本端点的 lane，SCHED 从暂存出口整包取走。
6. 每端点 adapter 把整包展开为 native beat：纯写合并、`RUN` 独占；设备在 `RUN` beat 被接受时 dump 配置并采样上下文。
7. 设备执行完毕后回传 completion，Queen 当拍应用 `hwSignal`（置位 event 或自增 timeline）、记录最新 fault，并在前端、adapter 与设备都空闲时拉高 `idle`。

目标契约在此之后增加：SCHED 侧 QWR/QRD 寄存器访问与 polling/atomic，CPU 通过 `QRD` 读同步状态与 fault，以及 trace 管理。

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
| 合法性检查只覆盖编码 | 已检测 opcode/funct3/cmd/保留位/endpoint/`regBase=63`/空组 `RUN`/`inst[25]` 规则；寄存器地址合法性由设备或 SCHED 前端在执行期判定，走 `wrErr`/`resp.err`，不回溯撤销已退休指令 | 上游仍需保证寄存器地址合法；执行期错误经异步 fault 或读错误响应上报 | 5.3、10.1 |
| `endpoint=2'b10` 误路由报 trap | Queen 没有 VPU 队列，前端直接报 cause 4，不再让指令滞留 | Scalar Core decoder 仍必须把 VPU 指令直发 VPU；报错只是让误路由立刻可见，不是正常通路 | 5.2、10.1 |
| QRD 依赖设备保序 | Queen 不为读响应打 tag、不做同端点重排；设备若乱序返回同端点读响应，数据会被配到错误的 `rd` 且无法检测 | 设备必须保证同端点读响应按请求序返回（接口文档第 16 章硬义务）；跨端点乱序由 Queen 三个 FIFO 吸收 | 5.4、接口 16 |
| 容量按整包计 | 一个包无论含 1 条还是 4 条指令都占 ring 一个槽；同包的 GEMM/DMA lane 合占该槽，SCHED lane 另占暂存 | 上层评估吞吐时按包而非按指令计容量 | 8.1、12.1 |
| ring 槽按两游标释放 | 槽只有在 GEMM 与 DMA 都越过它之后才可复用；单侧长期不消费会耗尽 ring 并反压入口，但不会阻塞另一侧已可出队的包 | 上层需保证两侧消费者都持续前进 | 5.2、A.1.1 |
| SCHED 暂存深度固定 1 | 连续 SCHED 指令最快 1 条/2 周期；暂存不缓存、不重排，下游不 ready 时 SCHED lane 整体停住 | 需要更高 SCHED 吞吐时合并指令或改用硬件信号源，不要加深暂存 | 5.1、12.2 |
| 无效 lane payload 清零 | 空 lane 的 `inst=0`、`rs=0`；`inst[31:30]=2'b00` 看起来像 GEMM | 消费方必须先看 `Valid` 再解释 payload | 7.2 |
| 次拍 `rs` 不可暂停 | `rs` 只有一拍窗口，缺失即填 0；上游不能反压 | CPU 必须保证时序 | 5.1 |
| 入口不缓存未 fire 指令 | Queen 只锁存已 fire 的指令，`valid && !ready` 的指令由上游保持 | 上游需自带发射缓冲或 stall | 9.3 |
| adapter 只支持 QWR/QRD/RUN | `FENCE/WAIT/TRACE` 由端点控制器在 adapter 之前消费，`POLL_EQ/ATOMIC_RMW/FLUSH` 由 SCHED 控制器消费；其它编码到达 adapter 会停等并触发仿真断言 | 上游不得把 polling/atomic/flush 送到 GEMM/DMA 端点（前端已按 endpoint 规则报非法） | 5.5、5.6 |
| 每个 RUN 多占一个 beat | `QCTRL`/`QRD` 独占 beat，`[W,W,W,RUN]` 需 2 拍而非 1 拍 | 换来单一 ready 与设备侧免处理同 beat 快照；写与启动分离是有意取舍 | 5.5、接口 5.2 |
| 无 trace 管理 | completion 的 `pc/cmdTag/swTag` 已原样回到 Queen 但不消费：记录流协议、格式与存储都未定；Queen 不做任务回收，也不维护 tag→sequence 映射 | 需要命令级归因时只能看波形或设备侧 trace；trace 管理落地前不得假设 Queen 会归档 | 5.6、接口 5.4 |
| DMA 设备侧契约未对齐 | 现有 `FbRegIf` 的 START 在 credit 不足/队满时回 err 而非反压，写也占 resp 通道且 `cmd.ready` 取 `resp.ready`（写被读反压），无 completion 流外露、无信号目标寄存器、无 `swTag` 通路；其双 bank 不继承旧值，要求每条命令全量配置 | 设备侧按接口文档第 16/17 章补齐或改用 `QueenDsaRegIf` 前 DMA 端到端不可用；软件对 DMA 须全量配置 | 接口 16、17 |
| 出口无 buffer | GEMM/DMA 出口组合读 ring 槽，SCHED 出口直连 1 级暂存；下游必须能消费整包，否则对应游标或暂存停住并反压入口 | 下游需具备整包接收能力 | 5.2 |
| GEMM 真实设备尚未接入 | 配置与完成通路已用 `QueenDsaRegIf` + 假 DSA 整条闭环验证，但 `WorkerBeeRegIf` 未迁移：缺 hwSignal 寄存器与回传、缺 `swTag`，且 `QueenNativeCmd` 到 `WorkerBeeCmd` 的映射、上下文采样与 completion 生成尚无 RTL | 接真实 WorkerBee 前 GEMM 执行端到端不可用；`cfgCredit` 需作为 `RUN` beat 的 ready 来源，写必须脱离 resp 反压，并须在 `start` 当拍采样上下文 | 5.6、接口 16、17 |
| GEMM 设备侧 ready 违反 16.6 | `WorkerBeeRegIf` 的 `cmd.ready` 含 `acceptBatch = !readPending && !respPending`，任何未取走的回执（含写回执）会反压包括 `WRITE` 在内的所有命令；且写也占 resp 通道 | 契约要求纯写 beat 的 ready 恒 1、写不占 resp；设备侧需拆开或改用 `QueenDsaRegIf`，否则读响应阻塞会连带停住配置写 | 接口 16.6、17 |
| 顶层到恢复闭环为止 | `QueenTop` 已含端点控制器、SCHED 同步执行与寄存器前端、completion 接收、双源同步资源、四类 fault、多层 idle、QRD 在途表/顺序退休、polling/atomic 内存主接口与 flush 恢复；仍不含 trace 管理与真实设备侧命令映射 | 端到端可观测到 `RUN→completion→event→WAIT 解阻塞`、`QRD→asyncRd/qrdRetire` 与 `flush→dummy 退休→状态清零`；软件可读写同步状态与 fault 记录 | 4.1、6.2 |
| `POLL_EQ` 挡住 fault 清除 | `POLL_EQ` 阻塞 SCHED 派发点，排在其后的 `QWR 0x00` 一起被挡住 | 这是「无 CPU 可见完成通知」的直接代价，硬件不加旁路；需要保证可清时配置 `POLL_TIMEOUT`（`0x03`），超时中止后派发点放行 | 5.7、10.2 |
| 内存响应必须按序 | `QueenMemMaster` 无 transaction ID，在途表只记「是否 READ」并按序消费 | 总线/平台必须保证响应按请求序返回，否则 READ 结果会配错比较器 | 5.9、接口 10 |
| flush 期间 CPU 仍可发指令 | flush 对 CPU 是异步的（可能由中断触发），接受并丢弃保证 QRD 立刻回 dummy 不挂死；但静默窗要求连续 `flushQuietCycles` 拍无新指令 | 持续不间断发射会让 flush 永不完成；软件最终必须安静下来。`flushReq` 是 sys 级广播，Queen 在整个 req 有效期间都停在 flush 态 | 5.9、ISA 6.5 |
| `POLL_EQ` 停等而非流水 | 任一时刻至多一笔 READ 在途，且它在途时不允许再发任何事务（atomic 也发不出） | 因此不存在「多笔 polling 读在途、其中一笔命中、其余要 drop」的场景；8 个在途槽只由 `ATOMIC_RMW` 使用。要流水化 polling 需另加丢弃语义 | 5.7、5.9 |
| `IDLE` 寄存器不含在途读 | 读 `IDLE` 的那条 QRD 自身若计入就永远读回 0，故该寄存器只看派发通路；顶层 `idle` 端口才含在途读表、内存主接口与 flush 控制器 | 软件用 `IDLE` 判断异步工作是否排空，不要用顶层 `idle` 的口径去期待读回 1 | 8.3.1、5.9 |
| 未使用的参数 | `slotDepth`、`instWidth` 无消费者；`xlen` 无 `require` | 不建议在交付配置中改动这些字段 | 8.1 |
| 单时钟域假设 | 没有内部 CDC；completion 与 fault 的跨域责任在集成方 | 跨域集成必须提供 CDC | 9.1 |

### 14.2 不支持能力与待确认事项

不支持或未实现：

- GEMM/DMA 设备侧命令映射与 completion 生成（QN-F012/F013 的设备侧部分）。
- VPU 同步查询（QN-F023 的 VPU 部分）、trace 记录流（QN-F020 的记录部分）——两者均裁定为 **TBD，只保留契约与接口位置，不实现**。
- 投机接收。V2 不支持；引入需要 epoch/kill token。

待确认：

- 硬件信号在 completion 与强 idle 之间的可见性口径：当前按 completion 定义，若 DSA 的 completion 不保证写可见，需要改成 idle 触发。
- trace 记录流的协议、格式与 Queen 侧存储组织。
- polling/atomic 的物理地址空间归属（总线错误编码已定为 kind=3/code=0，超时为 code=2）。
- 各设备私有配置寄存器排布、credit 初值与归还点（公共契约区地址已由 Queen 规定，见接口文档第 16 章）。设备侧按本规格自行对齐，不属于 Queen 的交付范围。

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

#### A.1.3 fault 记录与中断

```text
irq = faultIrq = faultValid
源优先级 = completion(GEMM) > completion(DMA) > wrErr(GEMM) > wrErr(DMA) > wrErr(SCHED) > timeline 回退
同拍多源取优先级最高的一条；新错误优先于软件写 SCHED 0x00 的清除
```

V2 已删除四来源通知计数：设备工作完成由设备自己的中断块上报。

#### A.1.4 timeline 等待条件

```text
wait 满足  <=>  (int32_t)(completed[id] - targetSequence) >= 0
```

`completed[id]` 由两条路径推进：软件 `SIGNAL_TIMELINE`（自增或赋值）与硬件完成源（每个携带该 timeline 目标的 RUN 完成时 +1）。同一时钟沿上，软件自增与 k 个硬件源自增可以叠加，结果为 `+1+k`；软件显式赋值同拍优先于硬件自增，但软件协议禁止对同一 timeline 混用两者。显式赋值若相对当前值 signed 距离为负则被抑制，并报 fault(kind=2, code=timeline ID)，同拍硬件自增仍生效。

#### A.1.5 event 置位

```text
setMask  = softwareSetMask | hardwareMask
event[g] = (event[g] | setMask)            // 软件清除按同一提交点的规则另行清除
```

软件 `SIGNAL_EVENT` 按 `inst[25]` 置位或清除；硬件完成源只能置位。同一周期多个置位来源作用于同一 bit 时取或，不清除其它 bit。**同一 bit 上软件清除与任一置位同拍时置位优先**（逐位）：`events(g) := (events(g) & ~swClr) | hwSet | swSet`。

### A.2 发布前检查

- 一级章节、F/P/D 三类 Feature ID 与所有引用一致；首页版本日期与修订记录一致。
- 功能、性能与 debug 清单独立编号；debug 清单为空并已说明原因，不混入规划项。
- 性能清单描述通路带宽与并行发射，不重复列举功能机制；接受、派发、执行与完成并行度分别说明。
- 功能支持、合法域、接口、配置、时序、异常及限制相互一致，无重复冲突定义。
- 已实现事实有可核对依据（`QueenDsaDecoder.scala`、`QueenConfig.scala`、`QueenDsaDecoderSpec.scala`）；未知与不适用区分。
- 接口峰值、局部结构上限、设计目标与实测值区分；本文无实测 PPA 数据。
- 模板占位项与填写提示已处理，表格与代码块完整。
