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
    │       └── SCHED staging ── sync/poll/atomic/register logic
    │                                           │
    │                                           └── system memory master
    │
    └── VPU instruction lanes / rs ── VPU frontend/backend ── VPU
                                              │
                                              └── sync query / notify / fault ── Queen
```

Scalar Core decoder 根据 endpoint 在 Queen 与 VPU 之间完成指令路由。目标为 VPU 的配置、RUN、WAIT 和 TRACE 指令直接送入 VPU 前端，不进入 Queen，Queen 也不向 VPU 输出命令。Queen 只接收 GEMM、DMA 和 SCHED 指令，SCHED 不经执行队列；GEMM/DMA adapter 把配置写与 `RUN` 按序转发到设备，配置寄存器由设备自己维护。VPU 与 Queen 之间仅保留同步资源查询、完成通知和错误上报旁带通路。

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

### 3.3 多 lane 接受与 ready 生成

同一周期中，lane 编号从小到大表示程序顺序。Queen 不要求连续前缀：

```text
inst(i).fire 不依赖 inst(j).fire，j != i
```

任一 lane 可以在其它 lane 阻塞时单独 fire；已 fire 的 lane 之间按 lane 编号保持程序序，未 fire 的 lane 由 CPU 稍后重新提交。

接收单位是整包。同一周期所有 fire 的 lane 合并为一个包，在次拍写入资源；包内未 fire 的 lane 在该包中标记无效。一个包最多含 `instructionLanes` 条指令，与包内有效条数无关。

GEMM 与 DMA 共用一个命令 ring，一包占一个 ring 槽；SCHED 单独走 1 级暂存，一包占该暂存。`inst.ready(i)` 按 lane 目标 endpoint 独立生成：

```text
ringUsed   = writePtr - min(gemmCursor, dmaCursor)     // 回绕计数
ringReady  = ringUsed + enq.valid < cmdRingDepth

schedUsed  = schedCount + schedEnq.valid - schedDeq.fire
schedReady = schedUsed < 1

ready(i) =
  endpoint ∈ {GEMM, DMA} ? ringReady :
  endpoint = SCHED        ? schedReady : 0
```

容量在 fire 时预约，因为下一拍的操作数不可暂停：被接受的 lane 必须保证次拍一定能入队。GEMM 与 DMA 共享容量池，两者各自持有游标，一侧停住只钉住它尚未释放的槽，不会阻塞另一侧已可出队的包。SCHED 深度固定为 1，接受一条后需要空一拍才能再接受，即连续 SCHED 指令最快 1 条/2 周期。

`frontendCanAccept` 必须同时考虑：

- CPU frontend reservation entry 是否充足；
- 指令所需 endpoint 的入口容量：GEMM/DMA 看 ring 余量，SCHED 看暂存是否空闲；
- QRD 顺序表和读响应缓存是否有空位；
- 容量按包计，不按指令条数计：同包命中同一资源的 lane 合占一个槽。

若实现第一版只支持每周期接受一条 Queen 指令，可以只允许一条 lane ready；外部接口形状仍保持多 lane。

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

Queen 内部只接收 GEMM、DMA 和 SCHED 的指令，不包含 VPU command queue；SCHED 指令只经 1 级暂存就地执行，不建立执行队列。对于 GEMM/DMA，建议区分：

1. **指令接收级**：接收 QWR/QCTRL，按 endpoint 分流并保持程序序。
2. **端点派发队列**：保存 QWR、RUN、FENCE、WAIT 和 TRACE entry，按序派发到设备。

配置寄存器位于 DSA 内部，Queen 不维护配置影子，也不生成配置快照。GEMM/DMA 的处理规则为：

```text
QWR1/QWR2:
    按 lane 序并行转发为目标设备的寄存器写，不受 credit 门控
    QWR2 是一条双地址写（reg0Addr/reg1Addr、data0/data1），不拆成两拍
    该端点 openGroup 置位

QCTRL RUN:
    独占一个 beat；设备在该 beat 上以 cmd.ready 告知能否接受，否则停等
    关闭当前配置组并启动设备
    该端点 openGroup 清零
    cmdTag/swTag 随启动命令透传
```

已启动配置不被后续配置写修改，这一保证由设备的配置缓冲承担（见 5.6）。目标为 VPU 的 QWR 与启动命令由 Scalar Core decoder 直接送入 VPU，并不经过上述结构。

### 4.2 端点 entry

概念接口：

```scala
object QueenEndpointOp extends ChiselEnum {
  val QWR, RUN, FENCE, WAIT_EVENT, WAIT_TIMELINE, TRACE_TAG = Value
}

class QueenEndpointEntry(cfg: QueenConfig) extends Bundle {
  val op       = QueenEndpointOp()
  val pc       = UInt(cfg.pcWidth.W)
  val notify   = Bool()
  val aux      = UInt(5.W)
  val operand0 = UInt(cfg.xlen.W)
  val operand1 = UInt(cfg.xlen.W)
}
```

该 entry 只用于 Queen 内部的 GEMM/DMA 派发队列，不携带配置快照：`QWR` 的 operand 是寄存器地址与数据，`RUN` 的 operand 是 `cmdTag/swTag`。无论物理编码如何，必须保持以下顺序：

- 同一 GEMM/DMA endpoint 的 QWR/RUN/FENCE/WAIT/TRACE 严格按入队顺序执行。
- FENCE/WAIT 未完成时，后续 entry 不得越过。
- GEMM、DMA 与 SCHED 相互独立推进；SCHED 不做条件等待，因此不存在全局阻塞范围。

### 4.3 SCHED 暂存

SCHED 侧只承载就地执行的操作，不做条件等待：

```text
SIGNAL_EVENT
SIGNAL_TIMELINE
POLL_EQ
ATOMIC_RMW
TRACE_TAG(SCHED)
```

`FENCE`/`WAIT_EVENT`/`WAIT_TIMELINE` 的 `endpoint=SCHED` 为非法编码（见 `queenISA.md` 7.2、8、9）。暂存只有 1 个槽，进入后尽快执行并在完成时释放；因此连续 SCHED 命令最快 1 条/2 周期。需要完成顺序的场景应把 wait 放进消费者（GEMM/DMA）队列。

SCHED 的通知寄存器、fault 寄存器、ACK/ENABLE 和恢复寄存器属于实时管理路径，任何时刻都可访问：既不被 GEMM/DMA 的 wait/fence 阻塞，也不依赖 SCHED 暂存是否空闲。

---

## 5. DSA 侧通用约束

### 5.1 Adapter 边界

Queen 的 GEMM/DMA endpoint adapter 属于 Queen Scheduler 集成边界，负责：

- 将 QWR 转发为设备寄存器写，将 RUN 转发为设备启动命令。
- 保持原生命令直到 ready 握手。
- 收集 native completion、read response 和 fault。
- 维护设备强 idle。
- 将 native fault 转换为 Queen faultEndpoint/faultCode/faultSequence/faultPc。
- 实现 GEMM/DMA endpoint queue 处的 FENCE/WAIT/TRACE。

GEMM 和 DMA 不需要理解 Queen 的 32-bit ISA 编码，只接收自身原生命令。VPU 不使用 Queen endpoint adapter。

### 5.2 命令接口

Queen 侧的派发 adapter（`QueenDsaAdapter`，每端点一个）把端点出口的整包**并行**展开为统一的 native 命令，一条指令对应一条 native 命令，`QWR2` 不再拆成两拍：

```scala
object QueenNativeOp extends ChiselEnum {
  val WRITE1, WRITE2, READ, RUN = Value
}

class QueenNativeCmd(cfg: QueenConfig) extends Bundle {
  val op       = QueenNativeOp()
  val reg0Addr = UInt(10.W)      // QWR1: inst[29:20]；QWR2: {inst[11],inst[29:25]}；RUN: 0
  val reg1Addr = UInt(10.W)      // WRITE2 时 = reg0Addr+1，由 Queen 保证；其它 op 为 0
  val data0    = UInt(cfg.xlen.W) // rs1；RUN 为 0
  val data1    = UInt(cfg.xlen.W) // rs2，仅 WRITE2 有效；其它 op 为 0
  val pc       = UInt(cfg.pcWidth.W)
  val cmdTag   = UInt(8.W)       // RUN 的 rs1 低 8 位；其它 op 为 0
  val swTag    = UInt(cfg.xlen.W) // RUN 的 rs2；其它 op 为 0
}

val out = Decoupled(Vec(cfg.nativeWidth, Valid(new QueenNativeCmd(cfg))))
```

并行度 `nativeWidth` 由 `QueenConfig.nativeLanes` 决定，`0` 表示与上游 `instructionLanes` 对齐（默认 4），取值范围 `1..instructionLanes`。每个出口 lane 带独立 `Valid`，保留包内原始 lane 位置。

**beat 切分规则**（一个 beat = 一次 `out.fire`）：

1. 一个 beat 是包内 lane 的**连续切片**，按 lane 序，长度不超过 `nativeWidth`。
2. **只有 `QWR1/QWR2` 可以合并进同一个 beat**。切片内第一条有效 lane 是写时，beat 向后延伸穿过写与无效 lane，**停在第一条 `QCTRL`/`QRD` 之前**（不含它）。
3. **任何 `QCTRL`（含 `RUN`）或 `QRD` 独占一个 beat**：切片内第一条有效 lane 是它们时，beat 只含这一条命令。
4. 全无效的切片不占 `fire`，直接跳过。
5. 整包全部 beat 发完后才接受下一包；入口 ready 取当拍游标，故末个 beat fire 的次拍才拉高。

因此同端点一包内的多条 `RUN`（如 `[QWR,RUN,QWR,RUN]`，前端 openGroup 按 lane 序折叠后合法）会被切成多个 beat，设备不必仲裁同拍多启动。`RUN` 永远独占 beat ⟹ 该次启动的全部配置写都在更早的 beat 落进 shadow，设备在 `RUN` 当拍直接 dump shadow 即可，**不需要处理“同 beat 写进快照”**。

**流控只有一个 ready**。`out.valid` 不依赖任何设备信号，设备按 beat 内容给出 `out.ready`：

```text
纯写 beat        : ready 恒 1（硬义务，不得被忙闲或未完成回执反压）
RUN beat         : ready = 设备能否接受新 RUN（credit 与命令队列共同决定）
QRD beat         : ready = 读响应通路可收（即 resp.ready）
```

Queen 侧不再有 `ready_start`/`ready_rd` 端口：前者的信息并入设备 `ready`，后者本来就是 Queen 自己读响应表的余量，由 Queen 在 QRD 准入侧管理，与设备端口无关。设备的 `ready` 可以依赖 beat 内容，但 Queen 的 `valid` 不依赖 `ready`，无组合环。

`READ` 已在 op 枚举中定义（设备须实现），但 QRD 顺序表落地前 adapter 不支持该编码，遇到即停等并触发仿真断言。`FENCE/WAIT/TRACE` 由端点控制器在本级之前消费。

设备相关映射（`QueenNativeCmd` → `WorkerBeeCmd` 或 DMA 配置前端）属各端点 adapter 的剩余工作。

当 `valid=1 && ready=0` 时，整个 beat 的 payload 与各 lane `Valid` 必须保持不变。只有 `fire` 表示 DSA 已接受该 beat。

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
- adapter 没有尚未发送完成的配置写或启动命令。

仅 `cmd.ready=1`、设备输入 FIFO 为空或计算阵列停止均不能单独作为 idle。

### 5.4 Completion 与 fault

普通命令完成信息使用可背压接口：

```scala
class QueenDsaCompletion(cfg: QueenConfig) extends Bundle {
  val sequence = UInt(cfg.sequenceWidth.W)
  val error    = Bool()
  val code     = UInt(cfg.faultCodeWidth.W)
  val hwSignal = new QueenHwSignal   // 见 9.1，设备在 RUN 被接受时锁存
}

val completion = Flipped(Decoupled(new QueenDsaCompletion(cfg)))
```

DSA native completion 若不带 Queen sequence，由 adapter 在提交时分配私有 sequence/tag 并维护映射。私有 tag 用于错误归因和多引擎乱序 completion，不对 CPU 暴露。

`hwSignal` 是该次工作提交时锁存的硬件信号目标，由设备随 completion 一并回传（见 9.1）。Queen 不在完成时读取设备配置寄存器，因此目标不受后续配置写影响。`error=1` 的 completion 不得携带有效目标。

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

### 5.6 配置流控与配置组

配置寄存器归设备所有。`RUN` 独占 beat（5.2），设备在该 beat 上用 `out.ready` 告知能否接受新启动；纯写 beat 恒可收，`QRD` 只被读响应通路反压（见 16.6）。Queen 侧**没有独立的 `ready_start` 端口**，`out.valid` 也不依赖设备信号。

- `RUN` beat 的 ready 由设备的 credit 与命令队列共同决定：credit 大于 0 且队列可收时为 1。Queen 只看这一位，不读 credit 计数、不另设计数器。
- credit 计数是设备内部量，不外露为寄存器；归还点由设备自定（见 16.7）。
- `RUN` 被设备接受即占用一个 credit；ready 为 0 时该 beat 在派发级停等，该端点后续指令按程序序等待，另一端点不受影响。
- 同一端点的 beat 是按序流且每个 `RUN` beat 只含一条 `RUN`，credit 只会被 Queen 自己发出的 `RUN` 占用，因此电平检查无竞争。
- openGroup 为 0 时的 `RUN` 是非法指令，在前端报 trap。
- 第一版不把设备 ready 纳入前端 ring 准入判定：派发级停等即可，共享 ring 的 per-consumer 游标已容得下单侧停顿。

设备侧义务（实现不在本文范围内）：`RUN` 被接受后，后续 `QWR` 不得改变该 `RUN` 已提交的配置；要重叠配置准备与执行，设备需自行保证这一点（通常需要双缓冲）；flush/reset 后 credit 必须与 Queen 重新对齐。

---

## 6. GEMM 接口

当前 WorkerBee 原生接口为命令流、响应流和状态：

```text
cmd  : Stream[WorkerBeeCmd]
resp : Stream[WorkerBeeResp]
status.busy/done/err
```

GEMM adapter 的建议行为：

1. `QWR1/QWR2` 逐条转发为 `REG_WRITE1/REG_WRITE2`，地址与数据原样透传。
2. `RUN` 转发为启动命令（WorkerBee 侧为 Start 寄存器写），`cmdTag/swTag` 随命令携带。
3. 同一配置组的寄存器写与启动不得与其它配置组交错。
4. `QRD endpoint=GEMM` 转换为 `REG_READ`，响应送入 Queen QRD ordered response queue；Queen 不缓存配置值。
5. WorkerBee response、status.err 和执行错误转换为 Queen fault。
6. 设备在 `RUN` beat 上以 `cmd.ready` 告知能否接受，Queen 按 5.6 只对该 beat 停等。

GEMM adapter 对 Queen 提供的强 idle 不能只等于 `!status.busy`，还必须包含：

```text
adapter 没有待发送的 REG_WRITE/启动命令/REG_READ
WorkerBee 没有未消费 response
WorkerBee 没有尚未结束的 DMA 请求/响应
```

GEMM 本地 FENCE 和 WAIT 在 Queen GEMM endpoint queue 的最终派发处执行。条件满足前不得向 WorkerBee 发送后续 QWR 或 RUN。

---

## 7. DMA 接口

当前 ForagerBee 原生接口为：

```text
cmd       : Stream[FbCmd]
done      : Stream[FbDone]
occupancy : UInt
busy      : Bool
```

ForagerBee 当前只有描述符接口，没有寄存器接口。按本契约，DMA 侧需要新增配置前端：接收寄存器写、在 `RUN` 时组装并提交一个完整 `FbCmd`。该前端属设备侧实现，不在本文范围内；其寄存器分区、公共契约区地址与按 op 分类的 ready 语义必须遵循第 16 章。

DMA adapter 的建议行为：

1. `QWR1/QWR2` 逐条转发到设备配置前端，Queen 不维护 `FbCmd` 影子。
2. `RUN` 转发为提交命令，由设备组装并提交一个完整 `FbCmd`。
3. adapter 维护 tag 到 Queen sequence/pc 的映射，将 `FbDone.tag` 归档。
4. `FbDone.err` 转换为 Queen asynchronous fault。
5. 设备在 `RUN` beat 上以 `cmd.ready` 告知能否接受，Queen 按 5.6 只对该 beat 停等。

DMA 强 idle 至少满足：

```text
ForagerBee busy == 0
ForagerBee occupancy == 0
adapter 没有待转发的配置写或 RUN
adapter 没有待消费或待归档 FbDone
相关内存总线没有 outstanding transaction
```

DMA 本地 FENCE 和 WAIT 在 Queen DMA endpoint queue 的最终派发处执行。ForagerBee 不需要理解 Queen FENCE/WAIT 编码。

`QRD endpoint=DMA` 读取设备配置寄存器与状态寄存器，读请求不得越过同端点此前的配置写；Queen 不缓存配置值。

---

## 8. VPU 旁带接口

目标为 VPU 的配置、RUN、WAIT 和 TRACE 指令由 Scalar Core decoder 直接派发到 VPU 前端。Queen 不接收这些指令，也不维护 VPU command queue 或 VPU adapter，并且不存在 Queen 到 VPU 的命令接口。

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

### 9.1 硬件完成信号源

除 `SIGNAL_EVENT/SIGNAL_TIMELINE` 外，GEMM 和 DMA 的完成也可以更新 event/timeline。建议接口：

```scala
class QueenHwSignal extends Bundle {
  val enable    = Bool()
  val isTimeline = Bool()
  val group     = UInt(5.W)   // event 组
  val mask      = UInt(32.W)  // event mask
  val timeline  = UInt(3.W)   // timeline ID
}

// 作为 QueenDsaCompletion.hwSignal 字段随 completion 回传（见 5.4），
// 不单独开一条通道，避免目标与完成事件之间出现顺序歧义。
```

约束：

- 目标由设备在 `RUN` 被接受时从公共契约区 `0x080/0x081` 锁存进该次工作，并随该次工作的 completion 回传；Queen 不保存配置副本，也不在完成时重新读取设备寄存器，避免与后续配置写竞争。
- 设备侧义务：`RUN` 被接受时锁存 `HW_SIGNAL_TARGET/HW_SIGNAL_MASK`（位排布见 16.2），完成时原样回传；DSA 不需要理解 event/timeline 语义，只负责锁存与回传。
- 锁存发生在 `RUN` 被接受时；credit 归还点由设备自定，两者不必是同一事件。
- 硬件信号只在对应命令的 native completion 被 adapter 接收后应用；同端点必须按命令完成序发布，早到的乱序 completion 缓存到前序 target 已应用。
- 硬件源只能置位 event 和自增 timeline；清除与赋值仅由软件指令完成。
- 更新路径独立于 GEMM/DMA 命令队列的阻塞状态，任何端点等待期间仍要能应用。
- flush/reset 后，未归档 tag 与尚未应用的硬件信号一律作废，不得更新新 context 的资源。
- 硬件信号与 `notifyPulse` 是两条通路：前者供 wait 使用，后者只累加 CPU 通知计数；同一完成点可以同时触发两者，但不能相互替代。

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

同周期四个来源可以同时为 1，Scheduler 必须独立增加四个 COUNT。通知逻辑不得被任何端点的等待门控：GEMM/DMA wait/fence 阻塞期间仍须继续采样、计数和产生 level IRQ。

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
- 设备配置寄存器使用寄存器表规定的复位值，Queen 侧 openGroup 清零、credit 与设备重新对齐；
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
  nativeLanes: Int = 0,
  frontendDepth: Int,
  qrdDepth: Int,
  cmdRingDepth: Int = 64,
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
- `nativeLanes >= 0` 且派生的 `nativeWidth`（0 表示等于 `instructionLanes`）落在 `1..instructionLanes`；
- 所有 depth >= 1，`cmdRingDepth >= 2`；
- `xlen == 32`；
- `timelineCount == 8` 或与 ISA aux 编码范围一致；
- `eventGroupCount <= 32`；
- `addressWidth <= xlen`；
- sequence/fault/trap 字段足以覆盖寄存器规范。

---

## 15. 最小接口验证要求

1. 验证多 lane 不要求连续前缀：某 lane 阻塞时其它 lane 仍可 fire；同周期 fire 的多条 lane 按 lane 编号合并为一个包并保持程序序。
2. 验证每个 lane 的 rs1/rs2 次拍返回与已 fire 指令准确配对。
3. 验证 frontend 为指令和次拍 rs 预留空间后才拉高 ready。
4. 验证 QWR/QCTRL 接收后可退休，后端阻塞不撤销已接收请求。
5. 验证 QRD 成功时 asyncRd fire 与 qrdRetire 的原子关系。
6. 验证 asyncRd backpressure 下 addr/data 和 QRD 表头保持稳定。
7. 验证 rd=x0 的 QRD 执行副作用但不写 GPR。
8. 验证跨 endpoint QRD 响应乱序到达时仍按请求顺序写回和退休。
9. 验证同步非法指令不进入 endpoint queue，并只报告最早非法 lane。
10. 验证 `RUN` 之前的配置写全部到达设备，`RUN` 之后的配置写不修改已启动配置。
11. 验证同一 GEMM/DMA endpoint 的 QWR/RUN/FENCE/WAIT/TRACE 不越过。
12. 验证同一配置组的寄存器写与启动不与其他配置组交错。
13. 验证 DMA FbCmd/FbDone tag 映射和乱序 completion 错误归因。
14. 验证强 idle 包含命令、执行、内存响应和 completion 全部退出。
15. 验证目标为 VPU 的指令由 Scalar Core decoder 直发 VPU，Queen 不存在 VPU command queue 或命令输出。
16. 验证 VPU WAIT 查询不修改同步资源，完成通知计入 VPU 来源。
17. 验证四来源同周期 notify 和单来源连续 pulse 均逐周期计数。
18. 验证 GEMM/DMA wait/fence 阻塞期间仍可处理 notify、fault 和实时管理寄存器，且 SCHED 暂存不参与条件等待。
19. 验证 polling interval、总线 backpressure、error 和 timeout。
20. 验证 atomic 请求映射为平台原子事务。
21. 验证 fault 后失败命令不产生成功 notify，flushDone 前不残留旧 context 事件。
22. 验证普通 CPU pipeline flush 不撤销已经 fire 的 Queen 指令。
23. 验证硬件完成信号按命令序发布、只置位 event 或自增 timeline、在 GEMM/DMA 等待阻塞期间仍能应用，且 flush 后不更新新 context 的资源。
24. 验证单 ready 分类正确：纯写 beat 恒可收（含读响应被反压时），RUN beat 按 credit 停等而非报错，READ beat 只被 resp.ready 反压。
25. 验证空配置组 RUN 在前端报 trap，不进入派发队列、不启动设备。
26. 验证 RUN 的 cmdTag/swTag 透传到设备命令与 trace/fault 关联。
27. 验证 `QWR2` 在原生接口上是一条双地址写，`reg1Addr=reg0Addr+1`，两个寄存器同时生效。
28. 验证 beat 切分：一个 beat 至多一条 `RUN` 且为末条有效 lane；`[QWR,RUN,QWR,RUN]` 切成两个 beat；全无效切片不占 fire。
29. 验证 `nativeWidth` 小于 `instructionLanes` 时按连续切片分片，lane 位置与程序序不变。
30. 验证 `RUN` beat 的 ready 为 0 时只停该 beat，纯写 beat 照常 fire；被停 beat 的 payload 与各 lane Valid 保持不变。
31. 验证写不占 resp 通道：写错误走 per-lane wrErr pulse，读响应反压不影响配置写。

---


## 16. DSA 设备寄存器空间与握手契约

本章由 Queen 侧规定，GEMM/DMA 设备参考实施。设备内部私有寄存器排布不在本章范围内，但公共契约区的地址、字段与握手语义必须一致，否则软件无法跨设备复用配置代码。

### 16.1 地址空间分区

`QWR1/QRD` 地址 10 bit（0..1023），`QWR2.regBase` 6 bit（0..62）。

| 区间 | 归属 | 约束 |
|---|---|---|
| `0x000..0x03F` | 设备私有 RW 配置 | 64 槽窗口，恰好等于 `QWR2` 的 6-bit regBase 范围；实际数量可配（第 17 章） |
| `0x040..0x05F` | 设备私有 RO 状态 | 32 槽窗口，由 DSA 输入；实际数量可配 |
| `0x060..0x07F` | 设备私有中断 | `0x60/0x64/0x68/0x6C` 四槽，其余保留 |
| `0x080..0x0FF` | Queen 公共契约区 | 按功能分块，地址与字段固定；设备私有寄存器不得占用 |
| `0x100..0x3FF` | 保留 | 写回 err，读回 0 |

私有区的内部排布 Queen 不强制，但第 17 章的通用 `QueenDsaRegIf` 按上表实现，直接例化即得到一致布局。窗口内未实现的地址按保留处理（写回 err、读回 0）。

公共区按 32 槽对齐分 4 块，块号 = `addr[6:5]`、块内偏移 = `addr[4:0]`，便于地址解码与后续扩展：

| 块 | 区间 | 用途 | 当前已用 |
|---|---|---|---|
| 0 | `0x080..0x09F` | 信号契约（配置） | 2 槽 |
| 1 | `0x0A0..0x0BF` | trace 契约（配置） | 1 槽 |
| 2 | `0x0C0..0x0DF` | 保留 | 0 槽 |
| 3 | `0x0E0..0x0FF` | 保留 | 0 槽 |

扩展规则：新功能只在所属块内追加地址，**不得改动已定义地址与字段**；块内未列出地址为保留（写回 err，读回 0）。跨块功能必须新开块并更新本章。公共区超出 `QWR2` 窗口，用 `QWR1` 访问；信号目标通常跨多条 `RUN` 不变，两条 `QWR1` 的开销可忽略。

**寄存器与端口的分工原则**：公共契约区只放**软件写、设备消费**的配置，以及无命令归属、无端口可承载的兜底状态。凡是 Queen 需要的状态与事件——能否接受 `RUN`、强 idle、完成的 `sequence/error/code/hwSignal`、命令级错误归因——一律走 16.5 的握手端口，**不得**在公共区另立状态寄存器。理由：Queen 是架构状态的归属地，端口是无损、按序、可背压的，而寄存器读回是 last-value 采样，多条同拍完成会丢中间事件；重复实现还会给软件一条绕过 Queen 归档的错误路径。块 2 因此整块保留。

强制范围：块 0 的 `HW_SIGNAL_TARGET/HW_SIGNAL_MASK` 必须实现（否则硬件完成信号源不可用）。块 1 为**标准化地址的可选项**——设备可以不实现，但一旦实现必须用规定地址与字段；未实现时读回 0、写回 err。块 2/块 3 整块保留。握手端口（16.5）全部必须实现。

### 16.2 块 0：信号契约（QN-F029）

两个寄存器合起来就是 9.1 的 `QueenHwSignal`，字段一一对应，设备只做搬运不做解释。

| 地址 | 名称 | 访问 | 复位值 | 必须 |
|---|---|---|---|---|
| `0x080` | `HW_SIGNAL_TARGET` | RW | 0 | 是 |
| `0x081` | `HW_SIGNAL_MASK` | RW | 0 | 是 |

`0x080 HW_SIGNAL_TARGET` 位排布：

| 位 | 字段 | 对应 `QueenHwSignal` | 含义 |
|---|---|---|---|
| 0 | `enable` | `enable` | 1=该次 `RUN` 完成时更新同步资源；0=不更新（即原 `HW_SIGNAL_VALID`，不单独占地址） |
| 1 | `kind` | `isTimeline` | 0=event；1=timeline |
| 6:2 | `group` | `group`（5 bit） | event 组号 0..31；`kind=1` 时忽略 |
| 9:7 | `timeline` | `timeline`（3 bit） | timeline ID 0..7；`kind=0` 时忽略 |
| 31:10 | 保留 | — | 必须写 0；写非 0 回 err，读回 0 |

`0x081 HW_SIGNAL_MASK` 位排布：`[31:0]` 全部为 event mask，bit i 对应 `events[group]` 的 bit i；`kind=1`（timeline）时整个寄存器被忽略。

编码示例：

```text
置位 event 组 3 的 bit3      : 0x080 = (3<<2)|0|1 = 0x0000000D, 0x081 = 0x00000008
同时置位组 3 的 bit0/3/31    : 0x080 = 0x0000000D,             0x081 = 0x80000009
自增 timeline 5             : 0x080 = (5<<7)|2|1 = 0x00000283, 0x081 = 任意（忽略）
不发信号                    : 0x080 = 0x00000000（复位值）
```

用法与时序：

1. 软件用 `QWR1` 写 `0x080/0x081`，必须落在同一配置组内、`RUN` 之前，可与设备私有配置任意交错。地址超出 `QWR2` 的 0..62 窗口，只能用 `QWR1`。
2. 这两个寄存器是**单一持久副本，不参与设备双 bank 切换**。因此即使设备私有配置是“每组必须全量重写”的非继承双 bank，信号目标仍可跨多条 `RUN` 只写一次。
3. 设备在 `RUN` 被接受**当拍**（该 beat `fire` 且其末条 lane 为 `RUN`）把两个寄存器采样进该次工作的上下文，与私有配置快照同一时刻。此后软件改写只影响后续 `RUN`。
4. `QRD 0x080/0x081` 读回的是**当前可写副本**，不是任何在途工作的锁存值。在途工作用的目标只能从它的 completion 看到；Queen 不在完成时回读设备寄存器。
5. 该次工作完成时，设备把锁存值原样放进 `QueenDsaCompletion.hwSignal`（5.4），由 Scheduler 应用：`kind=event` 用 `group`+`mask` 置位，`kind=timeline` 自增对应 timeline。硬件源只能置位与自增，清除/赋值只由软件指令做。
6. `enable=0` 时 completion 仍正常回传，`hwSignal.enable=0`，不更新任何资源。`enable=1 && kind=event && mask=0` 是合法的空操作，不是错误。
7. `error=1` 的 completion 不得携带有效目标（5.4）。
8. 设备不做 `group`/`timeline` 的范围检查（它不知道 Queen 的 `eventGroupCount`）。越界目标由 Queen 在应用时丢弃并上报 fault。
9. 一条 `RUN` 只能指向**一个** group 或一个 timeline；同组内可用 mask 一次置多位。跨多 group 置位需要多条 `RUN` 或软件 `SIGNAL_EVENT`。
10. flush/reset 后两个寄存器取复位值 0，已锁存但未应用的信号一律作废。

### 16.3 块 1：trace 契约（QN-F020）

| 地址 | 名称 | 访问 | 复位值 | 必须 | 字段与用法 |
|---|---|---|---|---|---|
| `0x0A0` | `TRACE_CTRL` | RW | 0 | 否 | bit0 enable；bit1 record_pc；bit[31:2] 保留必须为 0 |

用法：

- `TRACE_TAG` 指令在设备**实际派发点**记录 `rs1`，不是在 Queen 入队时记录。
- `TRACE_CTRL.enable=0` 时指令照常完成，但不记录。`record_pc=1` 时记录携带该条指令的 pc。
- `TRACE_CTRL` 与块 0 一样是单一持久副本，不参与 bank 切换，也不随 `RUN` 锁存——它控制的是派发点行为，不是某次工作的上下文。
- trace 记录值（32 位软件标记）与 `RUN` 的 8 位 `cmdTag`（命令归档标签）是**两个不同概念**。
- 记录流走旁带通路（尚未实现），由 Queen 侧归档。本块**不设** `TRACE_LAST_VALUE/LAST_PC/COUNT/DROP_COUNT` 之类观测寄存器：按 16.1 的分工原则，记录与丢弃计数属于 Queen 要的事件流，必须走端口，last-value 寄存器会丢中间事件。

### 16.4 块 2/块 3：整块保留

两块都不分配寄存器。错误按归属分三条既有通路，不再另立 `ERR_*`：

| 错误种类 | 通路 |
|---|---|
| 能归属到某条命令的执行错误 | completion 的 `error/code`；pc 与 `cmdTag` 由 Queen 自己的命令归档提供 |
| 寄存器写错误（非法地址等） | `wrErr` per-lane pulse，Queen 锁存为 fault |
| 无命令归属的设备本地错误（SRAM ECC、与命令无关的总线错误等） | 设备私有中断 `INT_RAW` 逐位置位 + 私有 `statusRegs` 放错误码 |

第三条是删除 `ERR_STATUS/ERR_CODE/ERR_CLEAR` 的理由：中断 `RAW` 本身就是 sticky + W1C 的逐位事件通知，`statusRegs` 已能承载错误码，两者合起来覆盖 `ERR_*` 的全部信息。给错误码一个“标准地址”也没有实际收益——码的编码本来就是设备私有的，通用软件无法跨设备解读。

因此**不设** `ERR_STATUS/ERR_CODE/ERR_CLEAR/ERR_PC/ERR_TAG`，也不设 `DONE_TAG/DONE_ERR/DONE_COUNT/DONE_SW_TAG`。`swTag` 是软件自带的标记，软件按自己的提交记录对应，不需要设备回读。Queen 的 fault 汇总仍以 completion 为准。

### 16.5 必须外露的握手端口

| 端口 | 方向（设备视角） | 含义 |
|---|---|---|
| `cmd` | Input `Decoupled(Vec(nativeWidth, Valid(QueenNativeCmd)))` | 并行配置 beat；其 `ready` 是唯一的流控出口（16.6） |
| `completion` | Output `Decoupled` | 完成流，含 `sequence/error/code/hwSignal`，必须可背压保持 |
| `idle` | Output | 强 idle，供 fence 与状态输出 |
| `resp` | Output `Decoupled` | **只承载 QRD 读数据与 err**；写不占此通道 |
| `wrErr` | Output `Vec(nativeWidth, Bool())` | 写错误 per-lane pulse，无 ready，Queen 无条件采样并锁存为 fault |

这些端口是 Queen 获取设备状态的**唯一**通路：

- `idle` 必须是**强 idle**：命令队列空、无执行、无未完成内存请求/响应、无未接收 completion（见 5.3）。`busy=0` 或输入 FIFO 空都不足以作为 idle。
- credit 可收性只由 `RUN` beat 的 `cmd.ready` 表达，Queen 与软件都不需要 credit 计数——发不进去就停等，因此不设 `CFG_CREDIT`。
- 设备内部 occupancy 是设备自己的量，Queen 的准入只看自己的 ring 与 `cmd.ready`，因此不设 `DEV_STATUS`。

### 16.6 单一 ready 与写/读通道分离

5.2 的切分规则保证一个 beat 要么是纯写、要么是单条 `QCTRL`/`QRD`，因此**一个 `cmd.ready` 就足以表达全部流控**，不再需要 `ready_start`/`ready_rd` 两条独立电平：

```text
纯写 beat   : cmd.ready 恒 1
RUN beat    : cmd.ready = 设备能否接受新 RUN（credit > 0 且命令队列可收）
QRD beat    : cmd.ready = resp.ready（读响应通路可收）
```

- Queen 的 `out.valid` **不依赖** `cmd.ready`，设备的 `ready` 可以依赖 beat 内容，无组合环。
- `QRD` 的准入还受 Queen 自己的读响应顺序表余量限制，那是 Queen 内部判定，不是设备端口。
- 设备若有除 credit/队满之外的额外接收限制，必须并入对应 beat 的 `ready`，不得接受后再回 err。

硬约束：

- **纯写 beat 的 `ready` 恒 1**，不得被设备忙闲、也不得被未取走的回执反压。这是单 ready 方案成立的前提。
- `RUN` 在 credit 不足或队满时必须让 `ready=0` **反压**，不得接受后回 err：`RUN` 在 Queen 前端入队即退休，err 会导致命令永久丢失。
- `READ` 只被 `resp.ready` 反压；设备不得用“忙”拒绝 `READ`。
- **写不占 `resp` 通道**：`resp` 只承载 QRD 的 data/err，与被接受的读命令同拍有效。写错误走 `wrErr` per-lane pulse（无 ready），Queen 无条件采样并锁存为 fault 状态。这样读响应反压不会连带停住配置写。

### 16.7 credit 语义

- credit 只定义为“设备还能接受多少条 `RUN`”，**归还点由设备自定**：可以在执行上下文取走配置时归还，也可以在完成时归还。Queen 只看 `RUN` beat 的 `cmd.ready`，不假设归还点。
- 归还点影响吞吐口径：取走即还允许配置准备与执行充分重叠；按完成归还则把在途 `RUN` 数绑到执行资源数。设备必须在自己的规格中写明归还点与初值。
- 无论哪种归还点，credit 不足都必须反压，不得丢弃 `RUN`。

### 16.8 设备侧其它义务

- `RUN` 被接受后，后续 `QWR` 不得改变该次已提交配置。
- 若设备要求每条命令全量配置（如双 bank 不继承旧值），必须在自己的规格中写明；Queen ISA 默认允许增量配置。该要求只适用于设备私有区，公共契约区寄存器不参与 bank 切换（16.2）。
- flush/reset 后 credit 与 Queen 重新对齐，公共区寄存器取复位值。

并行 beat 带来的设备侧义务（5.2 的切分规则是 Queen 侧保证）：

- **`RUN` 独占 beat**，因此该次启动的全部配置写都在更早的 beat 落进 shadow。设备在 `RUN` 当拍直接 `live := shadow` 即正确，**不需要处理“同 beat 写进快照”**，也不必对同拍多启动做仲裁。
- **`WRITE2` 是一条命令写两个地址**：`reg1Addr = reg0Addr + 1` 由 Queen 给出，设备直接使用，不要自行推导；两个地址的写必须同时生效，不得只生效一半。地址合法性按整个地址对判定，任一非法则整条命令回 err 且两个寄存器都不改。
- **同 beat 内同一寄存器被多个 lane 写时以 lane 序最后一条为准**；但中断 `RAW` 的 W1C 与 `FORCE` 的 W1S 必须**跨 lane 合并**（清除位按位或、置位位按位或），不是 last-wins。

### 16.9 软件配置流程示例

```text
QWR1 endpoint=DMA, 0x080, 0x0000000D   // enable=1, kind=event, group=3；跨 RUN 不变时只写一次
QWR1 endpoint=DMA, 0x081, 0x00000008   // 置位 events[3] 的 bit3
QWR1/QWR2 endpoint=DMA, 0x00..0x22, ... // 设备私有配置（全量或增量按设备规格）
QCTRL RUN endpoint=DMA, cmdTag=0x11, swTag=0x2000  // 关闭配置组并启动，设备当拍锁存 0x080/0x081
...                                    // 完成后回传 completion{sequence, error=0, hwSignal}
QWR1 endpoint=DMA, 0x00..0x22, ...      // 下一组配置，可与上一次执行重叠；信号目标无需重写
QCTRL RUN endpoint=DMA, cmdTag=0x12
```

---

## 17. 通用 DSA 寄存器接口 `QueenDsaRegIf`

`src/queen/scala/common/QueenDsaRegIf.scala` 是本章契约的**设备侧参考实现**，DSA 可直接例化，不必各自手写一套语义不一致的寄存器前端。它消费 5.2 的并行 native beat，不做 ISA 解码。

### 17.1 参数

```scala
case class QueenDsaRegIfConfig(
  numCfgRegs: Int = 64,      // 1..64，RW 配置寄存器数量
  numStatusRegs: Int = 32,   // 0..32，RO 状态输入数量
  intrWidth: Int = 32,       // 1..32，中断源位数
  qcfg: QueenConfig = QueenConfig()
)
```

地址窗口固定（16.1），只有**数量**可配：小设备不必为用不到的槽位付出 shadow+live 两组触发器。cmd lane 数取 `qcfg.nativeWidth`，与 Queen 出口宽度天然对齐。寄存器数据宽度固定 32，`qcfg.xlen` 必须为 32。

### 17.2 地址映射

| 地址 | 名称 | 访问 | 复位值 | 说明 |
|---|---|---|---|---|
| `0x00..numCfgRegs-1` | 配置 | RW | 0 | shadow 可写可读；live 在 `RUN` 当拍整组 dump |
| `0x40..0x40+numStatusRegs-1` | 状态 | RO | — | 直接来自 `statusRegs` 输入 |
| `0x60` | `INT_RAW` | RO + W1C | 0 | 硬件事件置位，写 1 清对应位 |
| `0x64` | `INT_FORCE` | RO + W1S | 0 | 写 1 置 raw，供软件调试；读回 raw |
| `0x68` | `INT_MASK` | RW | 全 1 | 1=屏蔽，默认全屏蔽 |
| `0x6C` | `INT_STATUS` | RO | 0 | `raw & ~mask` |
| `0x80/0x81` | `HW_SIGNAL_TARGET/MASK` | RW | 0 | 16.2；`RUN` 当拍锁存到 `hwSignal` |
| `0xA0` | `TRACE_CTRL` | RW | 0 | 16.3；记录流本身不在本模块 |

中断四寄存器的顺序与字段语义取自 `utils/bus/regif/Interrupts.scala` 的 `IntrRFMS4`，基址按 `0x60` 对齐。该库的 `BusIf` 是单写口模型，无法直接例化到 4 lane 并行写，故本模块**沿用其更新方程**而非复用其实例：

```text
raw := (raw & ~clrMask) | setMask | intrSrc      // 置位优先
clrMask = 各 lane 对 INT_RAW 的 W1C 数据按位或
setMask = 各 lane 对 INT_FORCE 的 W1S 数据按位或
```

### 17.3 端口

| 端口 | 方向 | 类型 | 含义 |
|---|---|---|---|
| `cmd` | 输入 | `Decoupled(Vec(lanes, Valid(QueenNativeCmd)))` | 并行 beat；单 ready，分类见 16.6 |
| `resp` | 输出 | `Decoupled({data, err})` | 只承载 QRD 读结果，与被接受的读命令同拍 |
| `wrErr` | 输出 | `Vec(lanes, Bool())` | 写错误 per-lane pulse，无 ready |
| `cfgRegs` | 输出 | `Vec(numCfgRegs, UInt(32.W))` | **live** 组，即已提交配置 |
| `statusRegs` | 输入 | `Vec(numStatusRegs, UInt(32.W))` | RO 状态源 |
| `intrSrc` | 输入 | `UInt(intrWidth.W)` | 硬件事件，逐拍置位 raw |
| `irq` | 输出 | `Bool` | `(raw & ~mask)` 非零 |
| `start` | 输出 | `Bool` | `RUN` 被接受当拍的一拍 pulse |
| `startReady` | 输入 | `Bool` | DSA 告知能否接受新 `RUN`；决定 `RUN` beat 的 ready |
| `startPc/startTag/startSwTag` | 输出 | `UInt` | 随 `start` pulse 有效的命令标签 |
| `hwSignal` | 输出 | `QueenHwSignal` | `RUN` 当拍锁存的信号目标，供 DSA 附到 completion |

### 17.4 语义要点

- **写恒可收**：纯写 beat 的 ready 恒 1，与 `resp.ready`、设备忙闲无关。写错误只体现在 `wrErr`，不影响接受。
- **`QRD` 读 shadow**：软件读到的是"我写了什么"，不是正在执行的配置；正在执行的配置只能从 `cfgRegs`（live）观察。
- **`RUN` 当拍**：`live := shadow`、`start` 拉高一拍、锁存 `0x80/0x81`。因为 `RUN` 独占 beat，shadow 已含该组全部写。
- **credit 不在本模块**：`startReady` 由 DSA 自己产生（在途标志、队列余量等），本模块不做计数器，也不假设归还点。
- **中断逐拍更新**，不依赖命令是否被接受。无命令归属的本地错误由 DSA 接进 `intrSrc` 并把错误码放进 `statusRegs`，本模块不另设 `ERR_*` 寄存器（16.4）。
- **beat 形状断言**：`RUN`/`READ` 所在 beat 若含多于一条有效 lane，仿真断言失败——把 5.2 的 Queen 侧保证变成设备侧可检查的约束。

### 17.5 边界

本模块不含 completion 归档、tag→sequence 映射、强 idle 判定、trace 记录流与设备执行逻辑，这些仍属各 DSA 与 Queen adapter 的剩余工作（14.1）。两个现有 RegIf（`WorkerBeeRegIf`/`FbRegIf`）尚未迁移到本模块。
