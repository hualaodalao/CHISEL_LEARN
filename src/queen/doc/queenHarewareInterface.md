# Queen Scheduler 硬件接口约束

## 1. 范围

本文定义 Queen Scheduler 的硬件接口及握手语义，指令编码和软件可见行为以 `queenISA.md` 为准。

接口分为三类：

1. Scalar Core 与 Queen 的指令、源寄存器、异步写回、退休完成和中断接口。
2. Queen 与 GEMM/DMA 的命令、状态、完成和错误接口，以及 Queen 与 VPU 的同步资源查询和错误旁带接口。
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
                                              └── sync query / fault ── Queen
```

Scalar Core decoder 根据 endpoint 在 Queen 与 VPU 之间完成指令路由。目标为 VPU 的配置、RUN、WAIT 和 TRACE 指令直接送入 VPU 前端，不进入 Queen，Queen 也不向 VPU 输出命令。Queen 只接收 GEMM、DMA 和 SCHED 指令，SCHED 不经执行队列；GEMM/DMA adapter 把配置写与 `RUN` 按序转发到设备，配置寄存器由设备自己维护。VPU 与 Queen 之间仅保留同步资源查询和错误上报旁带通路。完成通知不经 Queen：各引擎用自己的中断通知 CPU。

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

已实现为 `QueenQrdTracker`。每个已接收 QRD 在顺序表中保存：

```text
pc
rdIndex
source      // 0=GEMM 1=DMA 2=SCHED（endpoint 3 映射为 2）
```

**不保存 `regAddr`**：同端点读响应必须按请求序返回（16.8 的设备硬义务），队头只需认来源即可配对，无需地址匹配或 tag。

**分配点在前端 `inst.fire`，不是派发点。** 否则一条排在 `WAIT` 后面的 GEMM QRD 会被后发的 SCHED QRD 超车，退休序就错了。一拍最多 `instructionLanes` 条 QRD 同拍 fire，按 lane 序压缩后多推入口，写索引前进 popcount。

Queen 可以同时向 GEMM、DMA 和 SCHED 发起多个读请求，但必须按 Queen 所接收的 QRD 请求顺序向 CPU 返回结果。三个来源各有一个深度 `qrdDepth` 的响应 FIFO 缓存早到响应，直到其成为顺序表头。目标为 VPU 的 QRD 由 Scalar Core decoder 直接送入 VPU，不进入 Queen QRD 顺序表。

准入为每条在途 QRD 预留一个响应槽，故 **Queen 侧 `resp.ready` 恒 1**，设备读响应不会被 Queen 反压。外部接口不需要 transaction ID，Queen 内部也不使用私有 sequence/tag。

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

val qrdRetire = Decoupled(new QueenRetire(cfg))
```

语义：

- 正常 `rd!=x0`：`qrdRetire.valid` 只能在对应 `asyncRd.fire` 的周期拉高。
- 正常 `rd=x0`：读响应到达表头后可以直接拉高 qrdRetire。
- 读错误：`trap=1`、`trapCause=9`（`rdError`），不写 GPR。该 cause 只出现在 `qrdRetire`，不占用前端同步 `trap` 端口——读请求已被接受，错误只能在退休时上报。
- QRD completion 必须严格按请求顺序产生。
- 每周期最多产生一个 QRD completion。
- `qrdRetire` 带 ready：CPU 未准备好退休时表头保持，`asyncRd` 也不得 fire。

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

`endpoint=2'b10`（VPU）送到 Queen 一律报 cause 4（`endpoint`）：Queen 没有 VPU 队列，收下就永远没有消费者。Scalar Core decoder 仍必须把 VPU 指令直发 VPU，报错只是让误路由立刻可见，不是正常通路。

### 3.10 CPU 中断与状态

Queen 对 CPU 输出一个 level-sensitive 中断：

```scala
val irq = Output(Bool())
```

```text
irq = faultIrq
```

fault 信息通过 SCHED endpoint 寄存器读取（当前 RTL 已外露为端口）。Queen 不再有通知计数：设备工作完成由设备自己的中断块上报。

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

Queen 内部只接收 GEMM、DMA 和 SCHED 的指令，不包含 VPU command queue。已实现的三级结构：

1. **指令接收级**（`QueenDsaDecoder`）：接收 QWR/QCTRL，按 endpoint 分流并保持程序序；GEMM/DMA 共享整包 ring，SCHED 走 1 级暂存。
2. **端点控制器**（`QueenEndpointCtrl`，GEMM/DMA 各一个）：整包寄存 + lane 游标，按 lane 序消费 `FENCE/WAIT_*/TRACE_TAG`，把队头连续的 QWR/RUN 切片转发给 adapter。条件未满足时只停本端点队头，其后 lane 不越过。转发切片按原序压缩到 lane 0 起，不改变相对顺序。
3. **派发 adapter**（`QueenDsaAdapter`）：把切片展开为设备 native beat，只处理 QWR/RUN。

SCHED 侧由 `QueenSchedCtrl` 按 lane 序执行：队头连续的 `QWR1/QWR2` 切片一拍并行执行，`QRD/SIGNAL_EVENT/SIGNAL_TIMELINE/TRACE_TAG` 各独占一拍（`TRACE_TAG` 消费为 no-op），不建立执行队列。SCHED 寄存器映射已实现（第 18 章）；`POLL_EQ/ATOMIC_RMW` 需要尚未实现的内存主接口，当前停等并触发仿真断言。

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

SCHED 的 fault 寄存器与恢复寄存器属于实时管理路径，任何时刻都可访问：既不被 GEMM/DMA 的 wait/fence 阻塞，也不依赖 SCHED 暂存是否空闲。

---

## 5. DSA 侧通用约束

### 5.1 Adapter 边界

Queen 的 GEMM/DMA endpoint adapter 属于 Queen Scheduler 集成边界，负责：

- 将 QWR 转发为设备寄存器写，将 RUN 转发为设备启动命令。
- 保持原生命令直到 ready 握手。
- 合成端点强 idle：本级无在途命令，且设备 `deviceIdle=1`。
- 实现 GEMM/DMA endpoint queue 处的 FENCE/WAIT/TRACE。

completion 与 fault **不经过 adapter**：设备直接回 `QueenTop`（5.4），由 Queen 统一应用硬件信号并记录故障。Queen 不做任务回收，也不维护 tag→sequence 映射。

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
QRD beat         : ready = 设备自己的读处理能力（Queen 侧 resp.ready 恒 1，见 16.6）
```

Queen 侧不再有 `ready_start`/`ready_rd` 端口：前者的信息并入设备 `ready`，后者本来就是 Queen 自己读响应表的余量，由 Queen 在 QRD 准入侧管理，与设备端口无关。设备的 `ready` 可以依赖 beat 内容，但 Queen 的 `valid` 不依赖 `ready`，无组合环。

`READ` 已在 op 枚举中定义（设备须实现），但 QRD 顺序表落地前 adapter 不支持该编码，遇到即停等并触发仿真断言。`FENCE/WAIT/TRACE` 由 `QueenEndpointCtrl` 在本级之前消费（已实现，见 4.1）。

设备相关映射（`QueenNativeCmd` → `WorkerBeeCmd` 或 DMA 配置前端）属各端点 adapter 的剩余工作。

集成现状：`QueenTop`（`queen/scala/main/QueenTop.scala`）已例化 `QueenDsaDecoder`、两个 `QueenEndpointCtrl`、两个 `QueenDsaAdapter`、`QueenSchedCtrl`、`QueenSyncState` 与 `QueenQrdTracker`，外露 CPU 侧 `inst/rs1/rs2/trap/asyncRd/qrdRetire`、两端点 native beat 与读响应、两端点 completion/wrErr/deviceIdle 输入，以及 `idle/events/timelines/fault*/irq` 输出，是当前的 verilog 发射目标。SCHED 暂存出口已由 `QueenSchedCtrl` 内部消费，不再外露；顶层没有 `faultClear` 端口，清除走软件写 SCHED `0x00`。配置、同步与读回通路已用第 17 章的 `QueenDsaRegIf` 加一个假 DSA 整条闭环验证（指令 → beat → 寄存器 → start → 采样 → completion → event 置位 → `WAIT_EVENT` 解阻塞 → 后续配置下发；以及 QRD → 在途表 → 按序 `asyncRd`/`qrdRetire`）。`POLL_EQ/ATOMIC_RMW` 与 trace 管理尚未接入该顶层。

当 `valid=1 && ready=0` 时，整个 beat 的 payload 与各 lane `Valid` 必须保持不变。只有 `fire` 表示 DSA 已接受该 beat。

Queen 不得因为 DSA ready 暂时拉低而丢弃 RUN，也不得重复发送已经 fire 的命令。

### 5.3 强 idle

每个 GEMM/DMA 设备必须向 Queen 提供：

```scala
val deviceIdle = Input(Bool())   // QueenTop 视角：设备 → Queen
```

`deviceIdle=1` 必须同时表示：

- DSA 内部命令队列为空；
- 没有命令正在执行；
- 没有未完成的内存读写请求或响应；
- 没有尚未回传的 completion/fault。

仅 `cmd.ready=1`、设备输入 FIFO 为空或计算阵列停止均不能单独作为 idle。

Queen 侧的强 idle 分三层合成，`QueenTop.idle` 是三层之与：

1. 前端 `QueenDsaDecoder.idle`：ring 写指针与两个消费游标相等、SCHED 暂存空、无待配操作数的 pending lane。
2. 每端点 `QueenDsaAdapter.idle = !active && deviceIdle`：本级整包已发完且设备强 idle。
3. 顶层 `idle = decoder.idle && gemmAdapter.idle && dmaAdapter.idle`。

completion 只有 valid、Queen 无条件接收，故不存在“Queen 侧未接收的 completion”这一项；设备仍须把未回传的完成计入自己的 `deviceIdle`。

### 5.4 Completion 与 fault

完成信息是**只有 valid 的单向通路**，Queen 无条件接收，不反压：

```scala
class QueenDsaCompletion(cfg: QueenConfig) extends Bundle {
  val pc       = UInt(cfg.pcWidth.W)      // 设备在 start 当拍采样后原样带回
  val cmdTag   = UInt(8.W)                // 同上
  val swTag    = UInt(cfg.xlen.W)         // 同上
  val error    = Bool()
  val code     = UInt(cfg.faultCodeWidth.W)
  val hwSignal = new QueenHwSignal        // 见 9.1，设备在 start 当拍采样
}

val completion = Flipped(Valid(new QueenDsaCompletion(cfg)))
```

- 每端点每拍至多一条 completion；两端点可以同拍各回一条，Queen 必须同拍全部吸收（事件按位或、timeline 计数相加），不得丢任何一条。
- 设备决定自己能接受多少个 `RUN`，因此 `pc/cmdTag/swTag/hwSignal` **必须由设备在 `start` 当拍采样走**，执行完毕后随 completion 回传。`QueenDsaRegIf` 不锁存这些字段，只在 `start` 当拍组合给出。
- `pc/cmdTag/swTag` 是 trace 上下文，未来由 Queen 侧统一做 trace 管理；当前 Queen 原样接收但不消费，协议与格式未定。
- Queen 不做任务回收，不维护 sequence/tag 映射，也不按 completion 顺序重排：`hwSignal` 的效果（置位/自增）与到达序无关。
- `error=1` 时 Queen 只保留**最新一条**故障 `{valid, kind, endpoint, code}`，并拉高电平 `faultIrq`，直到软件写 SCHED `0x00`。同拍双端点报错取低序号端点（GEMM=0，DMA=1）；同拍新错误优先于清除。故障不带 pc 归因——归因信息在 trace 上下文里。
- `error=1` 的 completion 不得携带有效目标（`hwSignal.enable=0`）。

### 5.5 Flush/reset（已实现）

flush 由 `QueenFlushCtrl` 执行，**三个触发源**汇聚成同一条恢复流程：

- 软件写 SCHED `0x01 RECOVER`（任意值）；
- `QCTRL FLUSH`（cmd=10，endpoint=SCHED）；
- CPU 侧 `flushReq` 端口。

两侧都是**四相 req/ack 握手**，时序同构：`req` 拉起后必须等 `ack` 拉起才能落下，`ack` 再随 `req` 落下。

```scala
// CPU 侧（Queen 为被请求方）
val flushReq = Input(Bool())
val flushAck = Output(Bool())
// 设备侧（Queen 为请求方），GEMM/DMA 各一套
val gemmFlushReq = Output(Bool())
val gemmFlushAck = Input(Bool())
val dmaFlushReq  = Output(Bool())
val dmaFlushAck  = Input(Bool())
```

执行顺序：

1. **入口拍**：在途 QRD 表全部 entry 标记为 dummy；此后前端进入「接受并丢弃」。
2. **设备握手**：同时拉起两路 `flushReq`，等两路 `flushAck` 都为 1 后落下 `flushReq`，再等两路 `flushAck` 都为 0。
3. **排空**：等 Queen 内部与设备全部回到 idle（见下）。
4. **静默窗**：再等 `cfg.flushQuietCycles`（默认 8）拍**无扰动**。任何扰动都重新计数：CPU 又 fire 了任何指令（含 QRD）、迟到 completion 到达、总线响应到达。若排空条件重新失效则退回第 3 步。
5. **清状态**：event/timeline 清零、fault 清除、openGroup 清零。
6. **回 CPU ack**：拉起 `flushAck`，等 `flushReq` 落下后再落下 `flushAck`。内部触发（RECOVER/FLUSH）没有 sys req 可等，`flushAck` 只有一拍。

**`flushReq` 是 sys 级广播**：sys 把它发给很多模块，收齐所有 `flushAck` 后才落下。因此 `flushReq` 可能远高于 Queen 自己的排空时间，**整个 req 有效期间 Queen 都必须停在 flush 态**（第 1–6 步的行为持续生效，`flushing` 电平不落），不能排空后就恢复派发。顶层 `idle` 在此期间恒 0。

**为什么需要静默窗**：flush 对 CPU 是异步过程（可能由中断触发），CPU 在 flush 期间仍可能向 Queen 发 QRD。接受并丢弃保证这些读不会挂死（立刻回 dummy），但 Queen 不能把「刚好排空」当作「CPU 已经安静」——必须观察到一段连续无新指令的窗口才回 ack。

flush 期间（`flushing` 电平有效）的行为：

- **前端接受并丢弃**：合法指令照常 `fire`（CPU 不 stall），但不入 ring、不入 SCHED 暂存、不派发。非法指令仍报 trap。
- **QRD 回 dummy**：在途表里的读以及 flush 期间新接收的读，一律以 `rdValid=1, data=0xdeadbeef, trap=0` 退休，不等设备响应。旧响应被排空丢弃。
- **DSA 侧队列全丢**：ring 游标与写指针复位，端点控制器/adapter/SCHED 控制器的整包寄存清零，polling 状态机中止。
- **迟到 completion 丢弃**：不置同步状态、不记 fault。
- **内存事务不可撤销**：只停发新请求，等已发出的响应回来。
- `idle` 端口在 flush 期间为 0。

`flushAck` 拉起只能在以下条件全部满足后产生：

- 前端 ring 游标对齐、SCHED 暂存空、无 pending lane；
- 两个端点控制器与两个 adapter 无在途整包；
- SCHED 控制器空闲且 polling 状态机已中止；
- 在途 QRD 表空且三个响应 FIFO 空；
- polling/atomic 在途表空；
- 两设备 `deviceIdle` 为 1；
- 以上条件连续成立且**无任何扰动**满 `flushQuietCycles` 拍。

设备侧义务（实现不在本文范围内）：收到 `flushReq` 后必须丢弃已收未执行的命令、终止或等完运行中工作、不再产生属于旧 context 的 completion，然后才能回 `flushAck`；不支持安全 abort 的 DSA 必须等当前工作自然结束。`flushAck` 落下前不得开始新工作。flush 后 credit 必须与 Queen 重新对齐。

**软件义务**：flush 期间 CPU 可以继续发指令（接受并丢弃，QRD 立刻回 `0xdeadbeef`，不会挂死），但**持续不间断地发射会让静默窗永远重新计数、flush 永不完成**。软件最终必须安静下来；判别恢复是否结束看 QRD 是否还回 `0xdeadbeef`，或读 SCHED `IDLE` 是否为 1。sys 侧则看 `flushAck`。

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
5. WorkerBee response、status.err 和执行错误由设备汇总进 completion 的 `error/code`，直接回 Queen。
6. 设备在 `RUN` beat 上以 `cmd.ready` 告知能否接受，Queen 按 5.6 只对该 beat 停等。

GEMM 设备对 Queen 提供的 `deviceIdle` 不能只等于 `!status.busy`，还必须包含：

```text
WorkerBee 没有未消费 response
WorkerBee 没有尚未结束的 DMA 请求/响应
WorkerBee 没有尚未回传 completion 的已完成工作
```

adapter 侧「没有待发送的 REG_WRITE/启动命令/REG_READ」由 Queen 自己的 `QueenDsaAdapter.idle` 覆盖，不占设备义务。

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
3. 设备在 `start` 当拍采样 `pc/cmdTag/swTag/hwSignal`，`FbDone` 回来时原样放进 completion 回 Queen；Queen 不维护 tag→sequence 映射。
4. `FbDone.err` 由设备放进 completion 的 `error/code`。
5. 设备在 `RUN` beat 上以 `cmd.ready` 告知能否接受，Queen 按 5.6 只对该 beat 停等。

DMA 强 idle 至少满足：

```text
ForagerBee busy == 0
ForagerBee occupancy == 0
adapter 没有待转发的配置写或 RUN（Queen 侧 adapter.idle 覆盖）
设备没有尚未回传 completion 的 FbDone
相关内存总线没有 outstanding transaction
```

DMA 本地 FENCE 和 WAIT 在 Queen DMA endpoint queue 的最终派发处执行。ForagerBee 不需要理解 Queen FENCE/WAIT 编码。

`QRD endpoint=DMA` 读取设备配置寄存器与状态寄存器，读请求不得越过同端点此前的配置写；Queen 不缓存配置值。

---

## 8. VPU 旁带接口

目标为 VPU 的配置、RUN、WAIT 和 TRACE 指令由 Scalar Core decoder 直接派发到 VPU 前端。Queen 不接收这些指令，也不维护 VPU command queue 或 VPU adapter，并且不存在 Queen 到 VPU 的命令接口。

VPU 自行保证其指令顺序、WAIT 阻塞、TRACE 记录以及所需的 Scalar Core 写回/退休协议。Queen 与 VPU 只通过以下同步资源查询和错误旁带接口交互。

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

### 8.2 VPU 错误

同一时钟域下：

```scala
val vpuFault = Flipped(Decoupled(new QueenDsaCompletion(cfg)))
```

- 该接口是 VPU 到 Queen 的旁带输入，不是 Queen 到 VPU 的命令或 completion 响应。
- VPU fault 使用 ready/valid，不能使用 pulse；Queen 不需要接收普通 VPU completion。
- VPU 的完成通知走 VPU 自己的中断，不经 Queen（V2 已删除 notify 机制）。
- fault 汇总不表示 VPU 归 Queen Scheduler 调度管理。

---

## 9. Scheduler 同步资源接口

Scheduler 内部维护 event/timeline（RTL 为 `QueenSyncState`），有两个更新源：硬件完成源（completion 的 `hwSignal`）与软件源（`SIGNAL_EVENT/SIGNAL_TIMELINE`，由 `QueenSchedCtrl` 每拍发一条 `QueenSyncWrite`）。GEMM/DMA 的 `QueenEndpointCtrl` 直接读取内部资源做 `WAIT_*` 判定；VPU 通过第 8.1 节接口查询。

软件源语义：

- `SIGNAL_EVENT`：`inst[25]` 为 set/clr，`aux[4:0]` 为组号，`rs1` 为 mask。**同一 bit 上软件清除与置位同拍冲突时置位优先**（逐位）：`events(g) := (events(g) & ~swClr) | hwSet | swSet`。
- `SIGNAL_TIMELINE`：`rs1` 索引为 `x0` 时自增，否则赋值。软件自增与两端点硬件自增同拍按次数累加；显式赋值同拍优先于硬件自增（软件协议禁止对同一 timeline 混用赋值与硬件自增）。
- **显式赋值不得回退**：`(int32_t)(新值 - 当前值) < 0` 时该拍抑制赋值，并报 fault（kind=2、code=timeline ID）；同拍硬件自增仍生效。

建议内部只读视图（RTL 中由 `QueenSyncState` 模块的 `events/timelines` 输出承载）：

```scala
class QueenSyncView(cfg: QueenConfig) extends Bundle {
  val events    = Vec(cfg.eventGroupCount, UInt(32.W))
  val timelines = Vec(cfg.timelineCount, UInt(32.W))
}
```

如果资源数量导致广播成本过高，可以改为每 endpoint 独立 query port，但必须满足：

- 查询不会消费或修改资源；
- 多 endpoint 查询不能相互阻塞到形成队头死锁；
- signal 更新和 wait 查询具备确定的同周期顺序；
- fault/flush 后旧 context 的 wait 不得观察到重分配资源的新值。

软件读通路已落地：`QRD endpoint=SCHED` 读 `0x40..0x5F EVENT[0..31]` 与 `0x60..0x67 TIMELINE[0..7]`，由 `QueenSchedCtrl` 就地把只读视图 mux 成读响应，完整映射见第 18 章。该通路与 GEMM/DMA 队列独立，不被它们的 WAIT/FENCE 阻塞。

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

- 目标由设备在 `start`（`RUN` 被接受）当拍从公共契约区 `0x080/0x081` **采样**进该次工作，并随该次工作的 completion 回传；Queen 不保存配置副本，也不在完成时重新读取设备寄存器，避免与后续配置写竞争。
- 设备侧义务：`start` 当拍采样 `HW_SIGNAL_TARGET/HW_SIGNAL_MASK`（位排布见 16.2）与 `pc/cmdTag/swTag`，完成时原样回传。设备能接受多少个 `RUN` 由设备自己决定，故采样责任在设备侧；`QueenDsaRegIf` 只组合给出当前值，不锁存。DSA 不需要理解 event/timeline 语义。
- 采样发生在 `RUN` 被接受时；credit 归还点由设备自定，两者不必是同一事件。
- 硬件信号在 completion 到达 Queen 的当拍应用（Queen 无条件接收）。置位与自增与到达序无关，故 Queen **不做**乱序重排或前序缓存。
- 硬件源只能置位 event 和自增 timeline；清除与赋值仅由软件指令完成。
- 两端点同拍各回一条 completion 时，event 按位或合并、timeline 计数相加，两条都不得丢失。
- 更新路径独立于 GEMM/DMA 命令队列的阻塞状态，任何端点等待期间仍要能应用。
- flush/reset 后，尚未应用的硬件信号一律作废，不得更新新 context 的资源。
- 硬件信号供 `WAIT_*` 使用，不产生 CPU 可见事件；CPU 感知完成靠设备自己的中断。

---

## 10. Polling/atomic 系统内存接口（已实现）

Queen 需要独立系统内存主接口执行 `POLL_EQ` 和 `ATOMIC_RMW`。该接口不属于 GEMM/DMA 命令接口，也不属于 VPU 旁带接口。由 `QueenMemMaster` 实现，顶层端口为 `memReq`/`memResp`。

```scala
object QueenMemOp extends ChiselEnum {
  val READ, ATOMIC_ADD, ATOMIC_CLR, ATOMIC_SET = Value
}

class QueenMemReq(cfg: QueenConfig) extends Bundle {
  val op      = QueenMemOp()
  val address = UInt(cfg.xlen.W)
  val operand = UInt(32.W)
}

class QueenMemResp extends Bundle {
  val data  = UInt(32.W)
  val error = Bool()
}

val memReq  = Decoupled(new QueenMemReq(cfg))
val memResp = Flipped(Decoupled(new QueenMemResp))
```

允许 `cfg.memDepth`（默认 **8**）笔 transaction 在途，**不带 transaction ID**：总线必须按请求序返回响应，这是平台硬义务。在途表只记录「这笔是不是 READ」，按序消费即保证排在 `POLL_EQ` 之前的 `ATOMIC_RMW` 必然先落地，顺序无需额外握手。准入已为每笔在途事务预留响应槽，故 **`memResp.ready` 恒 1**。总线 adapter 可以转换为 TileLink、AXI 或平台专用接口。

两条指令的执行语义不同：

- `POLL_EQ` **阻塞 SCHED 派发点**：发出 READ 后等响应，相等则消费该命令并放行后续，失配则等 `POLL_INTERVAL` 拍再重读。期间 SCHED 不执行任何其它命令（与 `WAIT_EVENT` 同构）。
- `ATOMIC_RMW` **发出即放行**：只占用一个在途槽，不等待响应，因此 8 个槽才真正被用到。响应只用于检错。

约束：

- 地址必须 4-byte 对齐；未对齐不发请求，直接产生 kind=3/code=1 的 fault 并消费该命令。
- `POLL_EQ` 比较失败后必须等满 `POLL_INTERVAL`（SCHED `0x02`，复位 8）拍才能再次发 READ。
- `POLL_TIMEOUT`（SCHED `0x03`，复位 **0 = 禁用**）给出一条 `POLL_EQ` 的最大 cycle 数：启动时装载、持有派发点期间逐拍递减。到 0 报 kind=3/code=2 的 fault 并**中止该命令、放行派发点**（否则软件连清 fault 的写都发不进去）。若超时瞬间还有 READ 在途，必须等它回来才放行——无 transaction ID，陈旧响应否则会配到下一条 `POLL_EQ`。超时后不再发新 READ。**是否 flush 由软件决定**，硬件不自动恢复。
- ATOMIC 操作必须映射为平台真正支持的原子事务；普通 read-modify-write 序列不能冒充与 CPU/cache 一致的 atomic。
- `memResp.valid=1 && ready=0` 时 data/error 必须保持不变（本协议下 ready 恒 1，该情形不出现）。
- 总线 error 产生 kind=3/code=0 的异步 fault。
- **在途事务不可撤销**：flush 只能停止发新请求并等已发出的响应回来，不能取消总线事务。

**软件义务**：`POLL_EQ` 挡住 SCHED 派发点，所以清 fault 的 `QWR 0x00` 不得排在未完成的 `POLL_EQ` 之后，否则会被一起挡住。这是「`POLL_EQ`/`ATOMIC_RMW` 不产生 CPU 可见完成通知」的直接代价，硬件不加旁路；需要保证可清时配置 `POLL_TIMEOUT`（`0x03`），超时中止后派发点即放行。

---

## 11. 完成通知（V2 已删除）

V2 **不提供 Queen 侧完成通知机制**：没有 `notifyPulse`、没有四来源 COUNT/ACK/PENDING/ENABLE、ISA 也没有 notify 位。

CPU 感知完成的两条通路：

- **单引擎完成**：设备在产生 completion 的同一时刻置自己的 `INT_RAW`（16.1 的 `0x60` 块）并拉 `irq`，CPU 读 `INT_STATUS` 判来源、写 1 清除。需要计数时用设备私有的完成计数寄存器。
- **跨引擎条件**：硬件完成源置位 event / 自增 timeline，`WAIT_*` 在硬件里阻塞派发；需要 CPU 可见时读同步状态。

删除理由：设备本来就知道自己何时完成，Queen 再复制一套四来源计数是重复机制；`FENCE/WAIT` 是派发屏障，其完成对 CPU 本应透明。

`POLL_EQ`/`ATOMIC_RMW` **不产生 CPU 可见完成通知**：它们由 Queen 自己执行，效果只体现为后续指令的顺序（POLL_EQ 阻塞派发点）与内存副作用（ATOMIC 发出即放行）。CPU 需要感知时读内存本身，这正是 polling 的语义。代价与软件义务见第 10 节。

---

## 12. Fault 接口与恢复

所有异步错误汇总为持久 fault 状态，**只保留最新一条**：

```scala
class QueenFault(cfg: QueenConfig) extends Bundle {
  val kind     = UInt(2.W)   // 0=设备执行错误 1=配置写非法地址 2=timeline 显式回退 3=polling/atomic 内存错误
  val endpoint = UInt(2.W)   // GEMM=0, DMA=1, SCHED=3
  val code     = UInt(cfg.faultCodeWidth.W)
}
```

fault 汇总必须满足：

- fault 状态保持到软件写 SCHED `0x00`、flush 清状态或 reset；
- faultIrq 为 level-sensitive，等于 fault valid；
- 新到达的错误覆盖旧记录；同拍新错误优先于软件清除；
- 同拍多源按固定优先级取一条：`completion(GEMM) > completion(DMA) > wrErr(GEMM) > wrErr(DMA) > wrErr(SCHED) > 内存错误 > timeline 回退`；
- `code` 的含义由 `kind` 决定：kind=0 时原样透传设备私有码，kind=1 时为 0，kind=2 时为 timeline ID，kind=3 时 0=总线错误、1=地址未对齐。Queen 不瓜分设备码空间；
- 不带 pc/sequence 归因：命令级归因信息在 completion 的 trace 上下文（`pc/cmdTag/swTag`）里，由未来的 Queen trace 管理消费；
- fault 管理寄存器访问不受 endpoint WAIT/FENCE 阻塞；
- `error=1` 的 completion 不得携带有效硬件信号目标，故失败工作不会发布 event/timeline 成功状态。

恢复控制应通过 SCHED 实时管理寄存器触发，再转换为内部 endpoint flush/reset。恢复寄存器路径不得进入可能被冻结的普通 endpoint command queue。

---

## 13. Reset 与不可撤销边界

复位后：

- 所有 frontend、QRD、endpoint 和 native adapter 队列为空；
- 设备配置寄存器使用寄存器表规定的复位值，Queen 侧 openGroup 清零、credit 与设备重新对齐；
- event/timeline 与 fault 状态按 ISA/寄存器规范复位；
- irq 拉低；
- 不存在旧 context completion。

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
  faultCodeWidth: Int = 8,
  trapCauseWidth: Int = 8
)
```

elaboration 阶段至少检查：

- `instructionLanes >= 1`；
- `nativeLanes >= 0` 且派生的 `nativeWidth`（0 表示等于 `instructionLanes`）落在 `1..instructionLanes`；
- 所有 depth >= 1，`cmdRingDepth >= 2`；
- `xlen == 32`；
- `timelineCount == 8`（`hwSignal.timeline` 为 3-bit）；
- `eventGroupCount == 32`（`hwSignal.group` 为 5-bit）；
- `addressWidth <= xlen`；
- fault/trap 字段足以覆盖寄存器规范。

当前 RTL 的 `QueenConfig` 已实现 `qrdDepth = 16`（`require(qrdDepth >= 1)`），它既是 QRD 在途表 entry 数，也是每个来源响应 FIFO 的深度；深度不必是 2 的幂（物理索引用 `% qrdDepth` 取槽，与计数寄存器分离）。已实现 `memDepth = 8`（`require(memDepth >= 1)`），为 polling/atomic 内存主接口的在途事务数。已实现 `flushQuietCycles = 8`（`require(flushQuietCycles >= 1)`），为 flush 排空后到拉 `flushAck` 之间要求连续无扰动的拍数。`frontendDepth`/`addressWidth` 仍是目标契约字段，当前 RTL 无对应参数（内存地址宽度直接用 `xlen`）。

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
13. 验证设备在 `start` 当拍采样 `pc/cmdTag/swTag/hwSignal` 并随 completion 原样回传，包括任务飞行中改写配置的情形。
14. 验证强 idle 三层合成：前端 ring/SCHED/pending 空、adapter 整包发完、设备 `deviceIdle`（含命令、执行、内存响应与未回传 completion 全部退出）。
15. 验证目标为 VPU 的指令由 Scalar Core decoder 直发 VPU，Queen 不存在 VPU command queue 或命令输出。
16. 验证 VPU WAIT 查询不修改同步资源。
17. 验证 Queen 不产生任何完成通知 pulse，设备中断是唯一的单引擎完成通路。
18. 验证 GEMM/DMA wait/fence 阻塞期间仍可处理 fault 和实时管理寄存器，且 SCHED 暂存不参与条件等待。
19. 验证 polling interval（SCHED `0x02`，复位 8、写 0 非法）、总线 backpressure 与 error；验证 `POLL_TIMEOUT`（`0x03`，复位 0=禁用）到期报 kind=3/code=2、中止命令并放行派发点，在途 READ 未回时不得提前放行、超时后不得再发 READ。
20. 验证 atomic 请求映射为平台原子事务，且 `memDepth` 笔在途时响应仍按请求序消费。
21. 验证 fault 后失败工作不发布 event/timeline 成功状态，`flushAck` 拉起前不残留旧 context 事件。
22. 验证普通 CPU pipeline flush 不撤销已经 fire 的 Queen 指令。
23. 验证硬件完成信号只置位 event 或自增 timeline、在 GEMM/DMA 等待阻塞期间仍能应用，且 flush 后不更新新 context 的资源。
24. 验证单 ready 分类正确：纯写 beat 恒可收（含读响应被反压时），RUN beat 按 credit 停等而非报错，READ beat 按设备读处理能力停等而非丢命令；Queen 侧 `resp.ready` 恒 1。
25. 验证空配置组 RUN 在前端报 trap，不进入派发队列、不启动设备。
26. 验证 RUN 的 cmdTag/swTag 透传到设备命令，并随 completion 原样回传。
27. 验证 `QWR2` 在原生接口上是一条双地址写，`reg1Addr=reg0Addr+1`，两个寄存器同时生效。
28. 验证 beat 切分：一个 beat 至多一条 `RUN` 且为末条有效 lane；`[QWR,RUN,QWR,RUN]` 切成两个 beat；全无效切片不占 fire。
29. 验证 `nativeWidth` 小于 `instructionLanes` 时按连续切片分片，lane 位置与程序序不变。
30. 验证 flush 三触发源（SCHED `0x01` 写 / `QCTRL FLUSH` / CPU `flushReq`）走同一条流程，两侧四相 req/ack 时序正确（req 必须等 ack 才落下）。
31. 验证 flush 期间前端接受并丢弃、在途与新收 QRD 回 `rdValid=1/data=0xdeadbeef/trap=0`、迟到 completion 不置同步状态也不记 fault。
32. 验证 `flushAck` 只在内部全排空（含内存主接口与响应 FIFO）、两设备 `deviceIdle` 且连续 `flushQuietCycles` 拍无扰动后拉起；静默窗内任何指令 fire/迟到 completion/总线响应都重新计数，排空条件失效则退回排空态；`flushReq` 有效期间 `flushing` 不落、顶层 `idle=0`；完成后 event/timeline/fault/openGroup 清零而 `POLL_INTERVAL` 保持。
33. 验证 `POLL_EQ` 阻塞 SCHED 派发点期间不执行其它命令、失配后等满 `POLL_INTERVAL` 才重读；`ATOMIC_RMW` 发出即放行且按序响应保证它先于其后的 `POLL_EQ` 落地。
30. 验证 `RUN` beat 的 ready 为 0 时只停该 beat，纯写 beat 照常 fire；被停 beat 的 payload 与各 lane Valid 保持不变。
31. 验证写不占 resp 通道：写错误走 per-lane wrErr pulse，读响应反压不影响配置写。
32. 验证 completion 只有 valid、Queen 无条件接收；两端点同拍各一条时 event 按位或、timeline 计数相加，两条都不丢。
33. 验证 fault 只保留最新一条、`faultIrq` 电平保持到软件写 SCHED `0x00`、同拍双错取低序号端点、同拍新错误优先于清除。
34. 验证 `enable=0` 与 `mask=0` 的 completion 不更新资源，`error=1` 的 completion 不携带有效目标。
35. 验证 QRD 在**前端 accept** 当拍按 lane 序分配在途表 entry：一拍多条 QRD 同拍 fire 时写索引前进 popcount、无效 lane 被压缩跳过；排在 WAIT 后面的读不被后发的读超车。
36. 验证在途表满时只挡住 QRD lane，同包的写/控制 lane 仍可 fire；`qrdDepth` 不是 2 的幂时物理索引回绕仍正确。
37. 验证同端点读响应按请求序返回是设备义务：Queen 表项不存 regAddr、不打 tag，队头只按来源配对。
38. 验证读错误走 `qrdRetire.trap=1` + cause 9，不产生 `asyncRd`，也不占用前端同步 `trap` 端口。
39. 验证 `qrdRetire` 反压时表头与 `asyncRd` 都保持，不丢退休。
40. 验证 SCHED 寄存器映射：连续 QWR 切片一拍并行执行、QRD/SIGNAL/TRACE 各独占一拍；`QWR → SIGNAL → QRD` 不越过；写 `0x00` 清 fault、写其它地址报 per-lane `wrErr`、读保留地址回 `err=1/data=0`。
41. 验证 `IDLE` 寄存器不含在途读表（否则读它的那条 QRD 自身把读回值钉死为 0），而顶层 `idle` 端口含在途读表。

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

**寄存器与端口的分工原则**：公共契约区只放**软件写、设备消费**的配置，以及无命令归属、无端口可承载的兜底状态。凡是 Queen 需要的状态与事件——能否接受 `RUN`、强 idle、完成的 `error/code/hwSignal` 与 trace 上下文——一律走 16.5 的握手端口，**不得**在公共区另立状态寄存器。理由：Queen 是架构状态的归属地，端口是无损、按序的，而寄存器读回是 last-value 采样，多条同拍完成会丢中间事件；重复实现还会给软件一条绕过 Queen 的错误路径。块 2 因此整块保留。

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
4. `QRD 0x080/0x081` 读回的是**当前可写副本**，不是任何在途工作的采样值。在途工作用的目标只能从它的 completion 看到；Queen 不在完成时回读设备寄存器。
5. 该次工作完成时，设备把采样值原样放进 `QueenDsaCompletion.hwSignal`（5.4），由 Queen 应用：`kind=event` 用 `group`+`mask` 置位，`kind=timeline` 自增对应 timeline。硬件源只能置位与自增，清除/赋值只由软件指令做。
6. `enable=0` 时 completion 仍正常回传，`hwSignal.enable=0`，不更新任何资源。`enable=1 && kind=event && mask=0` 是合法的空操作，不是错误。
7. `error=1` 的 completion 不得携带有效目标（5.4）。
8. 设备不做 `group`/`timeline` 的范围检查（它不知道 Queen 的 `eventGroupCount`）。越界目标由 Queen 在应用时丢弃并上报 fault。
9. 一条 `RUN` 只能指向**一个** group 或一个 timeline；同组内可用 mask 一次置多位。跨多 group 置位需要多条 `RUN` 或软件 `SIGNAL_EVENT`。
10. flush/reset 后两个寄存器取复位值 0，已采样但未应用的信号一律作废。

### 16.3 块 1：trace 契约（QN-F020）

| 地址 | 名称 | 访问 | 复位值 | 必须 | 字段与用法 |
|---|---|---|---|---|---|
| `0x0A0` | `TRACE_CTRL` | RW | 0 | 否 | bit0 enable；bit1 record_pc；bit[31:2] 保留必须为 0 |

用法：

- `TRACE_TAG` 指令在设备**实际派发点**记录 `rs1`，不是在 Queen 入队时记录。
- `TRACE_CTRL.enable=0` 时指令照常完成，但不记录。`record_pc=1` 时记录携带该条指令的 pc。
- `TRACE_CTRL` 与块 0 一样是单一持久副本，不参与 bank 切换，也不随 `RUN` 锁存——它控制的是派发点行为，不是某次工作的上下文。
- trace 记录值（32 位软件标记）与 `RUN` 的 8 位 `cmdTag`（命令标签）是**两个不同概念**。
- `RUN` 的 `pc/cmdTag/swTag` 由设备在 `start` 当拍采样，随该次工作的 completion 回传（5.4），未来在 Queen 侧统一做 trace 管理；当前 Queen 原样接收但不消费，记录流协议与格式未定。本块**不设** `TRACE_LAST_VALUE/LAST_PC/COUNT/DROP_COUNT` 之类观测寄存器：按 16.1 的分工原则，记录与丢弃计数属于 Queen 要的事件流，必须走端口，last-value 寄存器会丢中间事件。

### 16.4 块 2/块 3：整块保留

两块都不分配寄存器。错误按归属分三条既有通路，不再另立 `ERR_*`：

| 错误种类 | 通路 |
|---|---|
| 能归属到某条命令的执行错误 | completion 的 `error/code`；`pc/cmdTag/swTag` 由设备在 `start` 当拍采样后随同一条 completion 回传 |
| 寄存器写错误（非法地址等） | `wrErr` per-lane pulse，Queen 锁存为 fault（kind=1，code=0） |
| 无命令归属的设备本地错误（SRAM ECC、与命令无关的总线错误等） | 设备私有中断 `INT_RAW` 逐位置位 + 私有 `statusRegs` 放错误码 |

第三条是删除 `ERR_STATUS/ERR_CODE/ERR_CLEAR` 的理由：中断 `RAW` 本身就是 sticky + W1C 的逐位事件通知，`statusRegs` 已能承载错误码，两者合起来覆盖 `ERR_*` 的全部信息。给错误码一个“标准地址”也没有实际收益——码的编码本来就是设备私有的，通用软件无法跨设备解读。

因此**不设** `ERR_STATUS/ERR_CODE/ERR_CLEAR/ERR_PC/ERR_TAG`，也不设 `DONE_TAG/DONE_ERR/DONE_COUNT/DONE_SW_TAG`。`swTag` 是软件自带的标记，由设备随 completion 原样回传，不需要另设回读寄存器。Queen 的 fault 汇总仍以 completion 为准。

### 16.5 必须外露的握手端口

| 端口 | 方向（设备视角） | 含义 |
|---|---|---|
| `cmd` | Input `Decoupled(Vec(nativeWidth, Valid(QueenNativeCmd)))` | 并行配置 beat；其 `ready` 是唯一的流控出口（16.6） |
| `completion` | Output `Valid` | 完成流，含 `pc/cmdTag/swTag/error/code/hwSignal`；Queen 无条件接收，每端点每拍至多一条 |
| `deviceIdle` | Output | 设备强 idle，供 Queen 合成顶层 idle 与 fence |
| `resp` | Output `Decoupled` | **只承载 QRD 读数据与 err**；写不占此通道 |
| `wrErr` | Output `Vec(nativeWidth, Bool())` | 写错误 per-lane pulse，无 ready，Queen 无条件采样并锁存为 fault（kind=1）。`QueenTop` 已提供对应输入端口 |
| `flushReq` | Input `Bool` | Queen 发起的四相恢复请求（5.5）。设备必须丢弃已收未执行命令、终止或等完运行中工作 |
| `flushAck` | Output `Bool` | 四相应答：`flushReq` 拉起后才能拉起，`flushReq` 落下后才能落下。拉起前不得残留旧 context 的 completion |

这些端口是 Queen 获取设备状态的**唯一**通路：

- `deviceIdle` 必须是**强 idle**：命令队列空、无执行、无未完成内存请求/响应、无未回传 completion（见 5.3）。`busy=0` 或输入 FIFO 空都不足以作为 idle。
- credit 可收性只由 `RUN` beat 的 `cmd.ready` 表达，Queen 与软件都不需要 credit 计数——发不进去就停等，因此不设 `CFG_CREDIT`。
- 设备内部 occupancy 是设备自己的量，Queen 的准入只看自己的 ring 与 `cmd.ready`，因此不设 `DEV_STATUS`。

### 16.6 单一 ready 与写/读通道分离

5.2 的切分规则保证一个 beat 要么是纯写、要么是单条 `QCTRL`/`QRD`，因此**一个 `cmd.ready` 就足以表达全部流控**，不再需要 `ready_start`/`ready_rd` 两条独立电平：

```text
纯写 beat   : cmd.ready 恒 1
RUN beat    : cmd.ready = 设备能否接受新 RUN（credit > 0 且命令队列可收）
QRD beat    : cmd.ready = 设备自己的读处理能力（能收下这条读并保证按序回响应）
```

- Queen 的 `out.valid` **不依赖** `cmd.ready`，设备的 `ready` 可以依赖 beat 内容，无组合环。
- `QRD` 的准入还受 Queen 自己的在途读表余量（`qrdDepth`）限制，那是 Queen 内部判定，不是设备端口。
- **Queen 侧 `resp.ready` 恒 1**：准入已为每条在途 QRD 预留响应槽，设备不得被读响应反压。因此 `QRD` beat 的 `cmd.ready` 不再等于 `resp.ready`，而由设备自己的读处理容量决定。
- 设备若有除 credit/队满之外的额外接收限制，必须并入对应 beat 的 `ready`，不得接受后再回 err。

硬约束：

- **纯写 beat 的 `ready` 恒 1**，不得被设备忙闲、也不得被未取走的回执反压。这是单 ready 方案成立的前提。
- `RUN` 在 credit 不足或队满时必须让 `ready=0` **反压**，不得接受后回 err：`RUN` 在 Queen 前端入队即退休，err 会导致命令永久丢失。
- `READ` 不得用“忙”拒绝后丢命令：要么 `ready=0` 反压，要么接受并按序回响应。
- **写不占 `resp` 通道**：`resp` 只承载 QRD 的 data/err，与被接受的读命令同拍有效。写错误走 `wrErr` per-lane pulse（无 ready），Queen 无条件采样并锁存为 fault 状态。这样读响应反压不会连带停住配置写。

### 16.7 credit 语义

- credit 只定义为“设备还能接受多少条 `RUN`”，**归还点由设备自定**：可以在执行上下文取走配置时归还，也可以在完成时归还。Queen 只看 `RUN` beat 的 `cmd.ready`，不假设归还点。
- 归还点影响吞吐口径：取走即还允许配置准备与执行充分重叠；按完成归还则把在途 `RUN` 数绑到执行资源数。设备必须在自己的规格中写明归还点与初值。
- 无论哪种归还点，credit 不足都必须反压，不得丢弃 `RUN`。

### 16.8 设备侧其它义务

- **同端点读响应必须按请求序返回**（硬义务）。Queen 的在途表 entry 不存 `regAddr`、不打 tag，队头只按来源配对响应；设备若乱序返回，数据会被写进错误的 `rd` 且 Queen 无法检测。跨端点乱序是允许的，由 Queen 三个来源 FIFO 吸收。
- `RUN` 被接受后，后续 `QWR` 不得改变该次已提交配置。
- 若设备要求每条命令全量配置（如双 bank 不继承旧值），必须在自己的规格中写明；Queen ISA 默认允许增量配置。该要求只适用于设备私有区，公共契约区寄存器不参与 bank 切换（16.2）。
- flush/reset 后 credit 与 Queen 重新对齐，公共区寄存器取复位值。
- **flush 四相握手是硬义务**（时序见 5.5，设备侧动作见 16.10）：`flushReq` 拉起后必须丢弃已收未执行的命令、终止或等完运行中工作、不再产生属于旧 context 的 completion，然后才能拉 `flushAck`；`flushAck` 落下前不得开始新工作。不支持安全 abort 的设备必须等当前工作自然结束，不得提前回 ack。

并行 beat 带来的设备侧义务（5.2 的切分规则是 Queen 侧保证）：

- **`RUN` 独占 beat**，因此该次启动的全部配置写都在更早的 beat 落进 shadow。设备在 `RUN` 当拍直接 `live := shadow` 即正确，**不需要处理“同 beat 写进快照”**，也不必对同拍多启动做仲裁。
- **`WRITE2` 是一条命令写两个地址**：`reg1Addr = reg0Addr + 1` 由 Queen 给出，设备直接使用，不要自行推导；两个地址的写必须同时生效，不得只生效一半。地址合法性按整个地址对判定，任一非法则整条命令回 err 且两个寄存器都不改。
- **同 beat 内同一寄存器被多个 lane 写时以 lane 序最后一条为准**；但中断 `RAW` 的 W1C 与 `FORCE` 的 W1S 必须**跨 lane 合并**（清除位按位或、置位位按位或），不是 last-wins。

### 16.9 软件配置流程示例

```text
QWR1 endpoint=DMA, 0x080, 0x0000000D   // enable=1, kind=event, group=3；跨 RUN 不变时只写一次
QWR1 endpoint=DMA, 0x081, 0x00000008   // 置位 events[3] 的 bit3
QWR1/QWR2 endpoint=DMA, 0x00..0x22, ... // 设备私有配置（全量或增量按设备规格）
QCTRL RUN endpoint=DMA, cmdTag=0x11, swTag=0x2000  // 关闭配置组并启动，设备当拍采样 0x080/0x081 与 pc/cmdTag/swTag
...                                    // 完成后回传 completion{pc, cmdTag, swTag, error=0, hwSignal}
QWR1 endpoint=DMA, 0x00..0x22, ...      // 下一组配置，可与上一次执行重叠；信号目标无需重写
QCTRL RUN endpoint=DMA, cmdTag=0x12
```

### 16.10 设备侧集成清单

本节是给 DSA 集成者的照做清单。契约本体在 16.1–16.9 与第 17 章，这里只回答「复用什么、自己写什么、怎么连、怎么验」。设备侧实现由设备团队自行对齐本规格，不属于 Queen 的交付范围。

#### 16.10.1 复用与自研的分界

| 部分 | 来源 | 说明 |
|---|---|---|
| 寄存器前端：shadow/live、中断四寄存器、公共契约区、单 ready、`wrErr`、`start` pulse、上下文与 `hwSignal` 组合输出 | **直接例化** `src/queen/scala/common/QueenDsaRegIf.scala` | 第 17 章；不必各自手写一套语义不一致的前端 |
| `QueenNativeCmd` / `QueenDsaRegResp` / `QueenDsaCompletion` / `QueenHwSignal` | **直接复用** `queen/scala/common/` 与 `QueenDsaAdapter.scala` 里的 bundle | 不要自定义等价结构 |
| 执行逻辑、`startReady`（credit）、`deviceIdle`、completion 生成与上下文采样、flush 四相状态机、`intrSrc`/`statusRegs` 源 | **设备自研** | RegIf 明确不含这些（17.5） |

#### 16.10.2 逐步集成与验证路径

最小可运行参考就在仓库里：`src/queen/scala/test/QueenConfigPathSpec.scala` 的 `QueenFakeDsa` + `QueenConfigPathDut`。它把 `QueenTop`、两个 `QueenDsaRegIf`、一个假设备连成整条闭环，是本章契约的可执行版本。

1. 读 `QueenConfigPathDut` 的连线（`top.io.gemm <> gemmReg.io.cmd` 等），与 16.10.3 的表对照。
2. 读 `QueenFakeDsa`：它示范了 `start` 当拍采样上下文、延迟若干拍回传 completion、四相 flush ack、`idle = 流水线全空`。**它不建模真实 abort 的等待语义**，真实设备必须自己补。
3. 用 `QueenDsaRegIfConfig(numCfgRegs, numStatusRegs, intrWidth, qcfg = QueenConfig())` 例化自己的 RegIf，`cfgRegs` 接执行逻辑。
4. 自研四件：`startReady`（credit）、`deviceIdle`（强 idle）、completion 生成（含采样副本）、flush 状态机。
5. 跑闭环验证：

```bash
CPATH=/opt/homebrew/include LIBRARY_PATH=/opt/homebrew/lib \
sbt -J-Xmx8G -J-Djava.io.tmpdir=<仓库绝对路径>/build/tmp \
  "testOnly QueenConfigPathSpec QueenDsaRegIfSpec"
```

判读：必须看到 `Tests: succeeded N, failed 0` 与 `All tests passed.` 两行才算过；只看日志尾部会漏掉退出码。单 suite 至少 `-J-Xmx6G`，多 suite 用 8G。

6. 验收 `deviceIdle` 与 flush：`deviceIdle` 必须覆盖命令队列空、无执行、无未完成内存请求/响应、无未回传 completion；`busy=0` 或输入 FIFO 空都不够。Queen 的 flush 会一直等两个 `deviceIdle` 都为 1，设备 idle 判据偏松会让 flush 提前完成并残留旧 context。

#### 16.10.3 连线表（以 GEMM 为例，DMA 同构）

| `QueenTop` 端口 | 方向（Queen 视角） | 接到 |
|---|---|---|
| `io.gemm` | 输出 | `regif.io.cmd` |
| `io.gemmResp` | 输入 | `regif.io.resp` |
| `io.gemmWrErr` | 输入 | `regif.io.wrErr` |
| `io.gemmCompletion` | 输入 | 设备自己的 completion 生成逻辑（**不经 RegIf、不经 adapter**） |
| `io.gemmDeviceIdle` | 输入 | 设备强 idle 判定 |
| `io.gemmFlushReq` | 输出 | 设备 flush 状态机 |
| `io.gemmFlushAck` | 输入 | 设备 flush 状态机 |

RegIf 其余端口的去向：`cfgRegs` → 执行逻辑的配置输入（live 组）；`statusRegs` ← 设备状态源；`intrSrc` ← 设备硬件事件（含无命令归属的本地错误）；`irq` → 设备自己到 CPU 的中断（Queen 不消费）；`start`/`startPc`/`startTag`/`startSwTag`/`hwSignal` → 设备**当拍采样**；`startReady` ← 设备 credit 与队列余量。

#### 16.10.4 completion 字段责任表

| 字段 | 谁填 | 从哪取 | 何时有效 |
|---|---|---|---|
| `pc` | 设备 | `regif.io.startPc` | **`start` 当拍采样**，完成时原样带回 |
| `cmdTag` | 设备 | `regif.io.startTag` | 同上 |
| `swTag` | 设备 | `regif.io.startSwTag` | 同上 |
| `hwSignal` | 设备 | `regif.io.hwSignal` | 同上 |
| `error` | 设备 | 自己的执行结果 | 完成当拍 |
| `code` | 设备 | 设备私有错误码 | `error=1` 时；Queen 原样透传进 `fault.code`，不瓜分设备码空间 |

硬约束：

- 每端点每拍至多一条；两端点可同拍各一条，Queen 必须全吸收。
- `Valid` 无 ready，Queen 无条件接收：设备**不得**因为「Queen 可能不收」而缓存或重发同一条完成。
- `error=1` 时 `hwSignal.enable` 必须为 0——失败工作不得发布 event/timeline 成功状态。
- **采样时机是最容易接错的地方**：RegIf 的上下文与 `hwSignal` 都是组合输出、不锁存任何在途副本。晚一拍采样就会把下一次 `RUN` 的目标附到前一次 completion 上，而 Queen 不做 tag 校验、查不出来。设备能接受多少条在途 `RUN`，就必须自己保存多少份采样副本。

#### 16.10.5 flush 四相的设备侧动作

时序见 5.5：`req↑ → ack↑ → req↓ → ack↓`。设备在 `flushReq` 拉起后必须做完下列全部才能拉 `flushAck`：

1. 丢弃已收下但尚未开始执行的命令；
2. 终止运行中工作，或等它自然结束（不支持安全 abort 就必须等，不得提前回 ack）；
3. 保证不再产生属于旧 context 的 completion，已采样未回传的副本一并作废；
4. credit 与 Queen 重新对齐，公共契约区寄存器取复位值。

`flushAck` 落下前不得开始新工作。

**RegIf 不含 flush 处理**：它没有 flush 端口，shadow/live 与中断寄存器都不会因 flush 复位。第 4 条要设备自己加——给 RegIf 包一层复位门控，或自行维护公共区复位。

#### 16.10.6 三个不能照抄的地方

1. **READ beat 的 ready**：RegIf 用 `cmd.ready = resp.ready`，这对它成立是因为它**当拍组合应答**（`resp.valid := accept && hasRd`），没有读积压；Queen 侧 `resp.ready` 恒 1，故 RegIf 的读恒可收。**若你的设备不能当拍应答，必须改用自己的读处理容量**，否则会收下答不出的读，破坏「同端点按序回响应」这条硬义务（16.8）。
2. **不要让 `resp.ready` 依赖 `resp.valid`**：RegIf 的 `resp.valid` 组合依赖 `cmd.fire`，而 `cmd.ready` 又取 `resp.ready`。Queen 侧恒 1 才没有环；设备侧若把 `resp.ready` 接到依赖 `resp.valid` 的信号上就成组合环。
3. **写不占 `resp`**：写错误只走 `wrErr` pulse。把写回执塞进 `resp` 会让读响应反压连带停住配置写，违反 16.6。

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
| `0x80/0x81` | `HW_SIGNAL_TARGET/MASK` | RW | 0 | 16.2；组合输出到 `hwSignal`，由 DSA 在 `start` 当拍采样 |
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
| `startPc/startTag/startSwTag` | 输出 | `UInt` | 随 `start` pulse 有效的 trace 上下文，由 DSA 当拍采样 |
| `hwSignal` | 输出 | `QueenHwSignal` | `0x80/0x81` 的组合译码，`start` 当拍有效，由 DSA 采样后附到 completion |

### 17.4 语义要点

- **写恒可收**：纯写 beat 的 ready 恒 1，与 `resp.ready`、设备忙闲无关。写错误只体现在 `wrErr`，不影响接受。
- **`QRD` 读 shadow**：软件读到的是"我写了什么"，不是正在执行的配置；正在执行的配置只能从 `cfgRegs`（live）观察。
- **`RUN` 当拍**：`live := shadow`、`start` 拉高一拍。因为 `RUN` 独占 beat，shadow 已含该组全部写。
- **不锁存上下文**：`startPc/startTag/startSwTag/hwSignal` 都是组合输出，本模块不保存任何在途工作的副本。设备能接受多少条 `RUN` 由设备决定，故采样与回传是设备侧义务（5.4）。
- **credit 不在本模块**：`startReady` 由 DSA 自己产生（在途标志、队列余量等），本模块不做计数器，也不假设归还点。
- **中断逐拍更新**，不依赖命令是否被接受。无命令归属的本地错误由 DSA 接进 `intrSrc` 并把错误码放进 `statusRegs`，本模块不另设 `ERR_*` 寄存器（16.4）。
- **beat 形状断言**：`RUN`/`READ` 所在 beat 若含多于一条有效 lane，仿真断言失败——把 5.2 的 Queen 侧保证变成设备侧可检查的约束。

### 17.5 边界

本模块不含 completion 生成、上下文采样、`deviceIdle` 判定、flush 四相处理、trace 记录流与设备执行逻辑，这些属设备侧剩余工作。completion 由设备直接回 `QueenTop`，不经本模块也不经 adapter。各设备现有的自有 RegIf 是否改用本模块，由设备团队按 16.10 的清单自行对齐，Queen 不做代改。

---

## 18. SCHED 端点寄存器映射（已实现）

SCHED 端点的寄存器由 `QueenSchedCtrl` 就地实现，**不复用 `QueenDsaRegIf`**：SCHED 没有 `RUN`、没有配置 shadow/live 双组，读的是 Queen 自己的架构状态（event/timeline/fault/idle），而不是设备配置。地址沿用 10-bit 寄存器地址风格，软件用 `QWR1 endpoint=SCHED` 写、`QRD endpoint=SCHED` 读。

| 地址 | 名称 | 访问 | 复位值 | 功能 |
|---|---|---|---|---|
| `0x00` | `FAULT_CLEAR` | WO | 0 | 任意值写入即清 `faultValid`；同拍新错误优先。读回 `err=1/data=0` |
| `0x01` | `RECOVER` | WO | 0 | 任意值写入即触发一次 flush（5.5）。读回 `err=1/data=0` |
| `0x02` | `POLL_INTERVAL` | RW | 8 | `POLL_EQ` 失配后的重读间隔（拍）。**写 0 非法**，报 `wrErr` 且保持原值 |
| `0x03` | `POLL_TIMEOUT` | RW | 0 | 一条 `POLL_EQ` 的最大 cycle 数；**0 = 禁用**。到期报 kind=3/code=2 并中止该命令。不随 flush 复位 |
| `0x04..0x3F` | 保留 | — | 0 | 写报 `wrErr`，读报 `err=1/data=0` |
| `0x40..0x5F` | `EVENT[0..31]` | RO | 0 | `events[addr-0x40]` |
| `0x60..0x67` | `TIMELINE[0..7]` | RO | 0 | `timelines[addr-0x60]` |
| `0x68` | `FAULT_VALID` | RO | 0 | 最新一条 fault 的有效标志 |
| `0x69` | `FAULT_KIND` | RO | 0 | 0=设备执行错误 / 1=写非法地址 / 2=timeline 回退 / 3=内存错误（code 0=总线错误、1=未对齐、2=POLL_EQ 超时） |
| `0x6A` | `FAULT_ENDPOINT` | RO | 0 | GEMM=0、DMA=1、SCHED=3 |
| `0x6B` | `FAULT_CODE` | RO | 0 | `faultCodeWidth` 位错误码 |
| `0x6C` | `IDLE` | RO | 1 | bit0 = 派发通路强 idle |
| `0x6D..0x3FF` | 保留 | — | 0 | 写报 `wrErr`，读报 `err=1/data=0` |

四个 fault 字段是四个独立寄存器，不打包成一个字，软件按需单读。

`IDLE` 的 bit0 = `decoder.idle && 两端点控制器.idle && 两 adapter.idle（含 deviceIdle）&& schedCtrl.idle`，**不含在途读表与 flush 控制器**——否则读 `IDLE` 的那条 QRD 自身就把读回值钉死为 0。顶层 `idle` 端口额外与在途读表空、内存主接口空、flush 控制器空闲取与，口径更严。

执行纪律：队头连续的 `QWR1/QWR2` 切片一拍并行执行（`wrErr` 为 per-lane pulse，`FAULT_CLEAR`/`RECOVER` 按 lane OR 归约，同拍多 lane 写 `POLL_INTERVAL`/`POLL_TIMEOUT` 以 lane 序最后一条为准）；`QRD`/`SIGNAL_EVENT`/`SIGNAL_TIMELINE`/`TRACE_TAG`/`POLL_EQ`/`ATOMIC_RMW`/`FLUSH` 各独占一拍。因此 `QWR → SIGNAL → QRD` 天然保序。`QWR2` 的第二个地址是 `regBase+1`：`0x02/0x03` 相邻且都合法，故一条 `QWR2` 可以一拍同时写间隔与超时；但指向 `0x00` 的 `QWR2` 会同时触发清除与 `wrErr`（`0x01` 合法而 `0x00` 的配对地址落在保留区），软件应只用 `QWR1` 写 `FAULT_CLEAR`/`RECOVER`。

顶层**没有 `faultClear` 端口**：清除故障的唯一通路是软件写 SCHED `0x00`。这保证清除动作与同步状态读走同一条按序通路，不会被 GEMM/DMA 的 WAIT/FENCE 阻塞。

`POLL_EQ`/`ATOMIC_RMW` 由 `QueenMemMaster` 执行（第 10 节）：`POLL_EQ` 阻塞派发点直到读值相等，`ATOMIC_RMW` 发出即放行。两者都不产生 CPU 可见完成通知。

软件流程示例：

```text
QWR1 endpoint=GEMM, 0x080, 0x0000000D    // 完成时置位 events[3] bit3
QWR1/QWR2 endpoint=GEMM, ...             // 设备私有配置
QCTRL RUN endpoint=GEMM                  // 启动，CPU 可继续发后续指令
QRD  rd, endpoint=SCHED, 0x43            // 读 events[3]，轮询 bit3
QWR1 endpoint=SCHED, 0x000, 0            // 处理完后清 fault（若有）
```
