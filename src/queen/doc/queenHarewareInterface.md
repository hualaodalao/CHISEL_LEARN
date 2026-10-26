# Queen Scheduler 硬件接口约束

## 1. 范围

本文定义 Queen Scheduler 的硬件接口及握手语义，指令编码和软件可见行为以 `queenISA.md` 为准。

接口分为三类：

1. Scalar Core 与 Queen 的指令、源寄存器、异步写回、退休完成和中断接口。
2. Queen 与 GEMM/DMA 的命令、状态、完成和错误接口，以及 Queen 与 VPU 的同步资源查询、通知和错误旁带接口。
3. Queen 执行 polling/atomic 所需的系统内存主接口。

Queen、Scalar Core 和各 DSA 默认工作在同一时钟域。跨时钟实现必须在集成边界增加异步 FIFO、事件计数器或等价 CDC 结构，不能直接跨域传递单周期 pulse。

本文中的接口方向均以 Queen 为观察视角。

---

## 2. 总体结构

```text
Scalar Core Decoder
    ├── Queen instruction lanes / rs
    │       ▼
    │   Queen CPU Frontend
    │       ├──────── QRD ordered response queue ── Scalar async writeback/retire
    │       ▼
    │   Command Builder
    │       ├── GEMM endpoint queue ── GEMM adapter ── GEMM
    │       ├── DMA  endpoint queue ── DMA adapter  ── DMA
    │       └── SCHED command queue ── sync/poll/atomic/register logic
    │                                           │
    │                                           └── system memory master
    │
    └── VPU instruction lanes / rs ── VPU frontend/backend ── VPU
                                              │
                                              └── sync query / notify / fault ── Queen
```

Scalar Core decoder 根据 endpoint 在 Queen 与 VPU 之间完成指令路由。目标为 VPU 的配置、RUN、WAIT 和 TRACE 指令直接送入 VPU 前端，不进入 Queen，Queen 也不向 VPU 输出命令。Queen 只为 GEMM、DMA 和 SCHED 建立指令队列；GEMM/DMA adapter 将已提交配置快照转换为目标 DSA 的原生命令接口。VPU 与 Queen 之间仅保留同步资源查询、完成通知和错误上报旁带通路。

---

## 3. Scalar Core 侧接口

### 3.1 设计原则

Queen CPU 接口沿用 Scalar Core 到 VPU 接口的基本形状，但两者是由 Scalar Core decoder 分别驱动的独立接口：

- Scalar Core decoder 必须将目标为 VPU 的指令直接送到 VPU，不得送入 Queen instruction lanes。
- Queen 不提供向 VPU 转发命令的输出接口。
- 每个 instruction lane 使用独立 `Decoupled`。
- 每个 lane 固定对应两个次拍返回的 scalar source operand。
- Queen 不使用固定执行级 `rd` 写回。
- `QRD` 使用可背压的异步 scalar GPR 写回。
- `QRD` 另行返回退休完成或异常；异步写回本身不等于退休完成。
- `QWR1/QWR2/QCTRL` 在 Queen 前端成功接收后即可退休，不产生 scalar GPR 写回。
- 已被 Queen 接收的指令不可由后续分支恢复或普通 CPU pipeline flush 撤销。

### 3.2 指令 lane

建议定义未压缩的 Queen issue payload：

```scala
class QueenIssue(cfg: QueenConfig) extends Bundle {
  val pc   = UInt(cfg.pcWidth.W)
  val inst = UInt(32.W)
}
```

顶层接口：

```scala
val inst = Vec(cfg.instructionLanes,
  Flipped(Decoupled(new QueenIssue(cfg))))
```

固定 `custom-0` opcode 仍保留在 `inst` 中，由 Queen 完成全部合法性检查。若后续为了布线压缩 payload，压缩格式必须能够无损恢复原始 32-bit 指令，不能改变 ISA 字段语义。

### 3.3 多 lane 接受顺序

同一周期中，lane 编号从小到大表示程序顺序。Queen 只能接受连续前缀：

```text
inst(i).fire => 对所有 j < i，inst(j).fire 必须为 1
```

不得在更早 lane 阻塞时单独接受更晚 lane。推荐 ready 关系：

```text
ready(0) = frontendCanAccept(inst(0))
ready(i) = ready(i-1) && valid(i-1) && frontendCanAccept(inst(i))
```

`frontendCanAccept` 必须同时考虑：

- CPU frontend reservation entry 是否充足；
- 指令所需 endpoint 的入口容量；
- QRD 顺序表和读响应缓存是否有空位；
- 同周期更早 lane 已经占用的容量。

若实现第一版只支持每周期接受一条 Queen 指令，可以只允许最早 valid lane ready；外部接口形状仍保持多 lane。

可选提供：

```scala
val queueCapacity = Output(UInt(log2Ceil(cfg.frontendDepth + 1).W))
```

`queueCapacity` 表示当前周期最多还能接收的 Queen 指令数量，仅用于 Scalar Core 提前限制发射；各 lane 的 `ready` 才是最终握手条件。

### 3.4 Scalar source operand

接口形状与 Scalar Core/VPU 对齐：

```scala
val rs = Vec(cfg.instructionLanes * 2,
  Flipped(new RegfileReadDataIO(cfg)))
```

其中 `RegfileReadDataIO` 的逻辑字段为：

```scala
class RegfileReadDataIO(cfg: QueenConfig) extends Bundle {
  val valid = Output(Bool())
  val data  = Output(UInt(cfg.xlen.W))
}
```

固定映射为：

```text
lane i 的 rs1 -> rs(2*i)
lane i 的 rs2 -> rs(2*i+1)
```

时序约束：

```text
cycle N:   inst(i).fire，Queen 锁存 inst/pc 并预留 request entry
cycle N+1: 所需 rs 端口 valid=1，Queen 将 data 写入已预留 entry
```

Queen 不得把 cycle N+1 的 rs 数据与新的 lane 指令错误配对。CPU 必须保证已 fire 指令的源数据在约定周期返回；Queen 应对所需 `rs.valid` 添加断言。

每类指令需要的源操作数如下：

| 指令 | rs1 | rs2 |
| :--- | :---: | :---: |
| `QWR2` | 必需 | 必需 |
| `QWR1` | 必需 | 不使用 |
| `QRD` | 不使用 | 不使用 |
| 本地 `FENCE` | 不使用 | 不使用 |
| SCHED `FENCE` | 必需，设备 mask | 不使用 |
| `WAIT_EVENT/WAIT_TIMELINE` | 必需 | 不使用 |
| `SIGNAL_TIMELINE` | 编码非 x0 时必需 | 不使用 |
| `SIGNAL_EVENT/TRACE_TAG` | 必需 | 不使用 |
| `POLL_EQ/ATOMIC_RMW` | 必需 | 必需 |

`SIGNAL_TIMELINE` 必须根据指令中的 rs1 index 是否为 `x0` 决定自增或显式赋值，不能根据返回 data 是否为 0 判断。

### 3.5 前端接收与退休

Queen frontend entry 至少保存：

```text
inst
pc
laneOrder
rs1Data
rs2Data
requiredRsMask
```

`inst.fire` 表示 Queen 已为该指令及其操作数预留不可撤销的存储空间。所需 rs 在下一周期返回后，该 entry 才能进入 decode/route 阶段。

- `QWR1/QWR2/QCTRL`：`inst.fire` 后满足 Scalar Core 侧退休条件；后端执行可以更晚发生。
- `QRD`：`inst.fire` 只表示请求已进入 Queen，不表示可退休；必须等待顺序读响应或读异常。
- 同步非法编码不得进入 endpoint queue；Queen 通过 trap/retire completion 向 Scalar Core 报告。
- 后端异步错误不得回溯撤销已经退休的 QWR/QCTRL，通过 Queen fault 状态和中断上报。

### 3.6 QRD 顺序表

每个已接收 QRD 在顺序表中保存：

```text
pc
rdIndex
endpoint
regAddr
```

Queen 可以同时向 GEMM、DMA 和 SCHED 发起多个读请求，但必须按 Queen 所接收的 QRD 请求顺序向 CPU 返回结果。内部 endpoint response 可以乱序到达，Queen 必须缓存早到响应，直到其成为顺序表头。目标为 VPU 的 QRD 由 Scalar Core decoder 直接送入 VPU，不进入 Queen QRD 顺序表。

外部接口不需要 transaction ID。若内部适配器允许乱序响应，可在 Queen 内部使用私有 sequence/tag；该 tag 不进入 CPU 接口，也不属于 ISA。

### 3.7 异步 scalar GPR 写回

Queen 不提供每 lane 固定延迟 `rd`。QRD 成功结果使用：

```scala
class QueenAsyncRd(cfg: QueenConfig) extends Bundle {
  val addr = UInt(5.W)
  val data = UInt(cfg.xlen.W)
}

val asyncRd = Decoupled(new QueenAsyncRd(cfg))
```

握手约束：

- `asyncRd.valid=1 && asyncRd.ready=0` 时，addr/data 必须保持不变。
- 只有顺序表头 QRD 可以驱动 asyncRd。
- `rd=x0` 的 QRD 仍须执行寄存器读取及其副作用，但不产生 GPR 写入。
- 正常 QRD 只有在 `asyncRd.fire` 后才能从顺序表移除并报告退休完成。
- QRD 读取错误不产生 asyncRd，而是报告带 trap 的退休完成。

### 3.8 QRD 退休完成

`asyncRd` 只负责寄存器写回，不能代替 Scalar Core ROB/retirement completion。建议定义：

```scala
class QueenRetire(cfg: QueenConfig) extends Bundle {
  val pc        = UInt(cfg.pcWidth.W)
  val rdValid   = Bool()
  val rdIndex   = UInt(5.W)
  val trap      = Bool()
  val trapCause = UInt(cfg.trapCauseWidth.W)
}

val qrdRetire = Output(Valid(new QueenRetire(cfg)))
```

语义：

- 正常 `rd!=x0`：`qrdRetire.valid` 只能在对应 `asyncRd.fire` 的周期拉高。
- 正常 `rd=x0`：读响应到达表头后可以直接拉高 qrdRetire。
- 读错误：`trap=1`，不写 GPR，并携带稳定的同步读异常 cause。
- QRD completion 必须严格按请求顺序产生。
- 每周期最多产生一个 QRD completion，除非 Scalar Core 明确定义多退休端口。

若 Scalar Core 后续要求使用 ROB token 而不是 PC 关联，可在 `QueenIssue` 和 `QueenRetire` 中对称增加内部 token；基础版本不要求 transaction ID、hart ID 或乱序退休 ID。

### 3.9 前端非法指令 trap

建议沿用单路 trap：

```scala
class QueenTrap(cfg: QueenConfig) extends Bundle {
  val pc    = UInt(cfg.pcWidth.W)
  val inst  = UInt(32.W)
  val cause = UInt(cfg.trapCauseWidth.W)
}

val trap = Output(Valid(new QueenTrap(cfg)))
```

同周期出现非法指令时，只接受并报告程序序最早的非法 lane；该 lane 之后的 lane 不得 fire。trap 为无 ready 的单周期接口，Scalar Core 必须能够无条件采样。

### 3.10 CPU 中断与状态

Queen 对 CPU 输出一个 level-sensitive 中断：

```scala
val irq = Output(Bool())
```

```text
irq = notifyIrq || faultIrq
```

中断来源、通知计数和 fault 信息通过 SCHED endpoint 寄存器读取。CPU 屏蔽中断不影响 Queen 内部 COUNT、pending 或 fault 状态累计。

建议另输出：

```scala
val idle = Output(Bool())
```

Queen idle 必须同时满足：

- CPU frontend 和 rs 等待 entry 为空；
- 所有 endpoint execution queue 为空；
- 没有 QRD、polling、atomic 或 DSA native response 在途；
- GEMM/DMA adapter 和设备均满足强 idle；
- 没有 flush/reset 流程在执行。

---

## 4. Queen 内部端点队列

### 4.1 两级结构

Queen 内部只为 GEMM、DMA 和 SCHED 建立队列，不包含 VPU command queue。对于 GEMM/DMA，建议区分：

1. **指令接收/配置构建级**：处理 QWR、维护 GEMM/DMA 的 command-building 影子寄存器。
2. **端点执行队列**：只保存已经形成执行语义的 RUN、FENCE、WAIT 和 TRACE entry。

普通 `QWR1/QWR2` 不直接进入 DSA native command queue。GEMM/DMA 的处理规则为：

```text
QWR without macroEop:
    更新对应 GEMM/DMA endpoint 的 command-building shadow

QWR with macroEop:
    先应用当前 QWR
    原子快照完整配置
    向对应 GEMM/DMA execution queue 写入 RUN(snapshot)
```

这样可以保证已经入队的 RUN 不会被后续配置写修改。目标为 VPU 的 QWR 和 macroEop 由 Scalar Core decoder 直接送入 VPU，并不经过上述结构。

### 4.2 端点 entry

概念接口：

```scala
object QueenEndpointOp extends ChiselEnum {
  val RUN, FENCE, WAIT_EVENT, WAIT_TIMELINE, TRACE_TAG = Value
}

class QueenEndpointEntry(cfg: QueenConfig) extends Bundle {
  val op       = QueenEndpointOp()
  val pc       = UInt(cfg.pcWidth.W)
  val notify   = Bool()
  val aux      = UInt(5.W)
  val operand0 = UInt(cfg.xlen.W)
  val operand1 = UInt(cfg.xlen.W)
  val config   = new QueenConfigSnapshot(cfg)
}
```

该 entry 只用于 Queen 内部的 GEMM/DMA execution queue。实际实现可以用 tagged union 或按 endpoint 定制 Bundle，避免所有 entry 都携带最大配置快照。无论物理编码如何，必须保持以下顺序：

- 同一 GEMM/DMA endpoint 的 RUN/FENCE/WAIT/TRACE 严格按入队顺序执行。
- FENCE/WAIT 未完成时，后续 entry 不得越过。
- GEMM、DMA 和 SCHED 可以独立推进，SCHED 全局控制另有明确阻塞范围。

### 4.3 SCHED command queue

SCHED queue 处理：

```text
SCHED FENCE
SCHED WAIT_EVENT
SCHED WAIT_TIMELINE
SIGNAL_EVENT
SIGNAL_TIMELINE
POLL_EQ
ATOMIC_RMW
TRACE_TAG(SCHED)
```

SCHED 的通知寄存器、fault 寄存器、ACK/ENABLE 和恢复寄存器属于实时管理路径，不得被 SCHED command queue 中的 WAIT/FENCE 阻塞。

---

## 5. DSA 侧通用约束

### 5.1 Adapter 边界

Queen 的 GEMM/DMA endpoint adapter 属于 Queen Scheduler 集成边界，负责：

- 将 RUN 配置快照转换为目标 DSA 的原生命令。
- 保持原生命令直到 ready 握手。
- 收集 native completion、read response 和 fault。
- 维护设备强 idle。
- 将 native fault 转换为 Queen faultEndpoint/faultCode/faultSequence/faultPc。
- 实现 GEMM/DMA endpoint queue 处的 FENCE/WAIT/TRACE。

GEMM 和 DMA 不需要理解 Queen 的 32-bit ISA 编码，只接收自身原生命令。VPU 不使用 Queen endpoint adapter。

### 5.2 命令接口

所有 native command 接口使用 ready/valid：

```scala
val cmd = Decoupled(new NativeDsaCmd(...))
```

当 `valid=1 && ready=0` 时，payload 必须保持不变。只有 `fire` 表示 DSA 已接受命令。

Queen 不得因为 DSA ready 暂时拉低而丢弃 RUN，也不得重复发送已经 fire 的命令。

### 5.3 强 idle

每个 GEMM/DMA adapter 必须向 Queen endpoint controller 提供：

```scala
val idle = Input(Bool())
```

idle=1 必须同时表示：

- DSA 内部命令队列为空；
- 没有命令正在执行；
- 没有未完成的内存读写请求或响应；
- 没有尚未被 adapter 接收的 completion/fault；
- adapter 没有尚未发送完成的 native command burst。

仅 `cmd.ready=1`、设备输入 FIFO 为空或计算阵列停止均不能单独作为 idle。

### 5.4 Completion 与 fault

普通命令完成信息使用可背压接口：

```scala
class QueenDsaCompletion(cfg: QueenConfig) extends Bundle {
  val sequence = UInt(cfg.sequenceWidth.W)
  val error    = Bool()
  val code     = UInt(cfg.faultCodeWidth.W)
}

val completion = Flipped(Decoupled(new QueenDsaCompletion(cfg)))
```

DSA native completion 若不带 Queen sequence，由 adapter 在提交时分配私有 sequence/tag 并维护映射。私有 tag 用于错误归因和多引擎乱序 completion，不对 CPU 暴露。

错误信息不得使用无保持能力的单拍 pulse。adapter 必须持续拉高 completion/fault valid，直到 Queen 接收。

### 5.5 Flush/reset

如果 DSA 支持独立 flush，建议接口为：

```scala
class QueenDsaFlushReq extends Bundle {
  val discardQueued = Bool()
  val abortRunning  = Bool()
}

val flushReq  = Decoupled(new QueenDsaFlushReq)
val flushDone = Flipped(Valid(Bool()))
```

`flushDone` 只能在以下条件满足后产生：

- 被丢弃的命令不会再开始执行；
- 所有无法取消的总线事务已经返回或被平台终止；
- 不再产生属于旧 context 的 completion/notify；
- adapter 和 DSA 已重新进入规定的 idle/reset 状态。

不支持安全 abort 的 DSA 必须将 `abortRunning` 视为非法，并等待当前工作自然结束后再完成 flush。

---

## 6. GEMM 接口

当前 WorkerBee 原生接口为命令流、响应流和状态：

```text
cmd  : Stream[WorkerBeeCmd]
resp : Stream[WorkerBeeResp]
status.busy/done/err
```

GEMM adapter 的建议行为：

1. 从 `RUN(configSnapshot)` 生成一组 `REG_WRITE1/REG_WRITE2`。
2. 写入完整配置后发送 `EXECUTE`。
3. 同一 RUN 的寄存器写和 EXECUTE 形成不可交错的 native command burst。
4. `QRD endpoint=GEMM` 转换为 `REG_READ`，响应送入 Queen QRD ordered response queue。
5. WorkerBee response、status.err 和执行错误转换为 Queen fault。

GEMM adapter 对 Queen 提供的强 idle 不能只等于 `!status.busy`，还必须包含：

```text
adapter 没有待发送的 REG_WRITE/EXECUTE/REG_READ
WorkerBee 没有未消费 response
WorkerBee 没有尚未结束的 DMA 请求/响应
```

GEMM 本地 FENCE 和 WAIT 在 Queen GEMM endpoint queue 的最终派发处执行。条件满足前不得向 WorkerBee 发送后续 RUN。

---

## 7. DMA 接口

当前 ForagerBee 原生接口为：

```text
cmd       : Stream[FbCmd]
done      : Stream[FbDone]
occupancy : UInt
busy      : Bool
```

DMA adapter 的建议行为：

1. DMA command-building shadow 与 `FbCmd` 字段建立固定寄存器映射。
2. `macroEop` 产生一个完整 `FbCmd` 快照。
3. 每个 RUN 只向 ForagerBee cmd 发送一个完整描述符。
4. adapter 为命令分配内部 tag，将 `FbDone.tag` 映射回 Queen sequence/pc。
5. `FbDone.err` 转换为 Queen asynchronous fault。

DMA 强 idle 至少满足：

```text
ForagerBee busy == 0
ForagerBee occupancy == 0
adapter 没有待提交 FbCmd
adapter 没有待消费或待归档 FbDone
相关内存总线没有 outstanding transaction
```

DMA 本地 FENCE 和 WAIT 在 Queen DMA endpoint queue 的最终派发处执行。ForagerBee 不需要理解 Queen FENCE/WAIT 编码。

`QRD endpoint=DMA` 优先读取 Queen DMA shadow、adapter 状态和映射后的 DMA 状态寄存器。只有寄存器表明确要求时才向 ForagerBee 发起 native read。

---

## 8. VPU 旁带接口

目标为 VPU 的配置、RUN、WAIT 和 TRACE 指令由 Scalar Core decoder 直接派发到 VPU 前端。Queen 不接收这些指令，也不维护 VPU 配置影子、VPU command queue 或 VPU adapter，并且不存在 Queen 到 VPU 的命令接口。

VPU 自行保证其指令顺序、WAIT 阻塞、TRACE 记录以及所需的 Scalar Core 写回/退休协议。Queen 与 VPU 只通过以下同步资源查询、通知和错误旁带接口交互。

### 8.1 VPU 同步资源查询

VPU 后端队列中的 WAIT 只读 Scheduler event/timeline，不修改资源。建议使用可持续查询的只读接口：

```scala
class QueenSyncQuery extends Bundle {
  val isTimeline = Bool()
  val id         = UInt(5.W)
}

class QueenSyncValue extends Bundle {
  val eventBits     = UInt(32.W)
  val timelineValue = UInt(32.W)
}

val vpuSyncQuery = Flipped(Valid(new QueenSyncQuery))
val vpuSyncValue = Output(new QueenSyncValue)
```

接口方向以 Queen 为视角：query 由 VPU 输入 Queen，value 由 Queen 返回 VPU。基础版本可以定义为同周期组合查询。VPU 队头 WAIT 在未满足时每周期保持相同 query。若物理实现需要寄存器化查询，必须保证 response 与 query 对齐，并由 VPU 保持 WAIT，不得将查询延迟误认为条件满足。

同周期 signal 与 query 的可见性必须固定。建议定义为：本周期 signal 更新在时钟沿提交，所有 endpoint 从下一周期开始观察新值。

### 8.2 VPU 通知与错误

同一时钟域下：

```scala
val vpuNotifyPulse = Input(Bool())
val vpuFault       = Flipped(Decoupled(new QueenDsaCompletion(cfg)))
```

- 两个接口均为 VPU 到 Queen 的旁带输入，不是 Queen 到 VPU 的命令或 completion 响应。
- `vpuNotifyPulse` 每个高电平采样周期计数一次，允许连续周期为 1。
- 每个周期 VPU 最多完成一个带 notify 的命令。
- VPU fault 使用 ready/valid，不能使用 pulse；Queen 不需要接收普通 VPU completion。
- 通知或 fault 汇总不表示 VPU 归 Queen Scheduler 调度管理。
- 跨时钟时，notify 必须转换为事件计数或握手，不能直接同步单拍电平。

---

## 9. Scheduler 同步资源接口

Scheduler 内部维护 event/timeline。GEMM、DMA 和 SCHED endpoint controller 可以直接读取内部资源；VPU 通过第 8.1 节接口查询。

建议内部只读视图：

```scala
class QueenSyncState(cfg: QueenConfig) extends Bundle {
  val events    = Vec(cfg.eventGroupCount, UInt(32.W))
  val timelines = Vec(cfg.timelineCount, UInt(32.W))
}
```

如果资源数量导致广播成本过高，可以改为每 endpoint 独立 query port，但必须满足：

- 查询不会消费或修改资源；
- 多 endpoint 查询不能相互阻塞到形成队头死锁；
- signal 更新和 wait 查询具备确定的同周期顺序；
- fault/flush 后旧 context 的 wait 不得观察到重分配资源的新值。

---

## 10. Polling/atomic 系统内存接口

Queen 需要独立系统内存主接口执行 `POLL_EQ` 和 `ATOMIC_RMW`。该接口不属于 GEMM/DMA 命令接口，也不属于 VPU 旁带接口。

建议 Queen 内部抽象接口：

```scala
object QueenMemOp extends ChiselEnum {
  val READ, ATOMIC_ADD, ATOMIC_CLR, ATOMIC_SET = Value
}

class QueenMemReq(cfg: QueenConfig) extends Bundle {
  val op      = QueenMemOp()
  val address = UInt(cfg.addressWidth.W)
  val operand = UInt(32.W)
}

class QueenMemResp extends Bundle {
  val data  = UInt(32.W)
  val error = Bool()
}

val memReq  = Decoupled(new QueenMemReq(cfg))
val memResp = Flipped(Decoupled(new QueenMemResp))
```

基础版本只允许一笔 polling/atomic transaction 在途，因此不需要 transaction ID。总线 adapter 可以转换为 TileLink、AXI 或平台专用接口。

约束：

- 地址必须 4-byte 对齐。
- `memReq.fire` 后，在收到 memResp 或 timeout 前不得复用该 transaction 状态。
- POLL_EQ 比较失败后，必须等待配置的 polling interval 才能再次发 READ。
- ATOMIC 操作必须映射为平台真正支持的原子事务；普通 read-modify-write 序列不能冒充与 CPU/cache 一致的 atomic。
- `memResp.valid=1 && ready=0` 时 data/error 必须保持不变。
- 总线 error/timeout 产生异步 fault，不产生成功 notify。

---

## 11. 通知接口

GEMM、DMA 和 SCHED 的 FENCE/WAIT/POLL 完成 pulse 在 Queen 内部生成；VPU pulse 从 VPU 接口输入。

内部统一表示为：

```scala
val notifyPulse = Vec(4, Bool())
```

索引固定为：

```text
0 GEMM
1 DMA
2 VPU
3 SCHED
```

同周期四个来源可以同时为 1，Scheduler 必须独立增加四个 COUNT。通知逻辑必须独立于 SCHED command queue，即使 SCHED WAIT/FENCE 阻塞，也必须继续采样、计数和产生 level IRQ。

通知 pulse 不带 payload，软件通过每来源 COUNT 顺序关联任务。同一来源的完成通知必须保持该 endpoint 的命令完成顺序。

---

## 12. Fault 接口与恢复

所有异步错误汇总为持久 fault 状态：

```scala
class QueenFault(cfg: QueenConfig) extends Bundle {
  val endpoint = UInt(2.W)
  val code     = UInt(cfg.faultCodeWidth.W)
  val sequence = UInt(cfg.sequenceWidth.W)
  val pc       = UInt(cfg.pcWidth.W)
}
```

fault 汇总必须满足：

- fault 状态保持到 CPU 明确 ACK/reset；
- faultIrq 为 level-sensitive；
- 多来源同时错误不得静默覆盖，至少保存首个 fault 并记录来源 pending mask；
- fault 管理寄存器访问不受 endpoint WAIT/FENCE 阻塞；
- 失败命令不产生成功 notify；
- 与失败工作关联的 timeline/event 不得发布成功状态。

恢复控制应通过 SCHED 实时管理寄存器触发，再转换为内部 endpoint flush/reset。恢复寄存器路径不得进入可能被冻结的普通 endpoint command queue。

---

## 13. Reset 与不可撤销边界

复位后：

- 所有 frontend、QRD、endpoint 和 native adapter 队列为空；
- command-building shadow 使用寄存器表规定的复位值；
- event/timeline、COUNT、ACK、ENABLE 和 fault 状态按 ISA/寄存器规范复位；
- irq 拉低；
- 不存在旧 context completion 或 notify。

不可撤销边界为 `inst.fire`：Queen 已经为请求和所需操作数预留空间，Scalar Core 不得通过普通 pipeline flush 撤销该请求。只有显式 Queen flush/reset 协议可以终止未完成工作。

如果未来 Scalar Core 允许投机发射 Queen 指令，则必须在 issue payload 中增加 epoch/kill token，并重新定义所有副作用提交点；V2 基础接口不支持投机接收。

---

## 14. 建议 QueenConfig 参数

```scala
case class QueenConfig(
  instructionLanes: Int,
  frontendDepth: Int,
  qrdDepth: Int,
  gemmQueueDepth: Int,
  dmaQueueDepth: Int,
  schedQueueDepth: Int,
  xlen: Int = 32,
  pcWidth: Int = 32,
  addressWidth: Int = 32,
  timelineCount: Int = 8,
  eventGroupCount: Int = 32,
  sequenceWidth: Int = 32,
  faultCodeWidth: Int = 8,
  trapCauseWidth: Int = 8
)
```

elaboration 阶段至少检查：

- `instructionLanes >= 1`；
- 所有 queue depth >= 1；
- `xlen == 32`；
- `timelineCount == 8` 或与 ISA aux 编码范围一致；
- `eventGroupCount <= 32`；
- `addressWidth <= xlen`；
- sequence/fault/trap 字段足以覆盖寄存器规范。

---

## 15. 最小接口验证要求

1. 验证多 lane 只能接受连续前缀，早 lane 阻塞时晚 lane 不得 fire。
2. 验证每个 lane 的 rs1/rs2 次拍返回与已 fire 指令准确配对。
3. 验证 frontend 为指令和次拍 rs 预留空间后才拉高 ready。
4. 验证 QWR/QCTRL 接收后可退休，后端阻塞不撤销已接收请求。
5. 验证 QRD 成功时 asyncRd fire 与 qrdRetire 的原子关系。
6. 验证 asyncRd backpressure 下 addr/data 和 QRD 表头保持稳定。
7. 验证 rd=x0 的 QRD 执行副作用但不写 GPR。
8. 验证跨 endpoint QRD 响应乱序到达时仍按请求顺序写回和退休。
9. 验证同步非法指令不进入 endpoint queue，并只报告最早非法 lane。
10. 验证 macroEop 当前写入包含在配置快照中，后续写入不修改已入队 RUN。
11. 验证同一 GEMM/DMA endpoint 的 RUN/FENCE/WAIT/TRACE 不越过。
12. 验证 GEMM native command burst 不与其他 RUN 交错。
13. 验证 DMA FbCmd/FbDone tag 映射和乱序 completion 错误归因。
14. 验证强 idle 包含命令、执行、内存响应和 completion 全部退出。
15. 验证目标为 VPU 的指令由 Scalar Core decoder 直发 VPU，Queen 不存在 VPU command queue 或命令输出。
16. 验证 VPU WAIT 查询不修改同步资源，完成通知计入 VPU 来源。
17. 验证四来源同周期 notify 和单来源连续 pulse 均逐周期计数。
18. 验证 SCHED WAIT/FENCE 阻塞期间仍可处理 notify、fault 和实时管理寄存器。
19. 验证 polling interval、总线 backpressure、error 和 timeout。
20. 验证 atomic 请求映射为平台原子事务。
21. 验证 fault 后失败命令不产生成功 notify，flushDone 前不残留旧 context 事件。
22. 验证普通 CPU pipeline flush 不撤销已经 fire 的 Queen 指令。
