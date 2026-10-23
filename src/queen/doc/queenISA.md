# Queen V2 扩展指令集及 DSA 软硬接口约束

## 1. 范围与架构边界

Queen 使用 RISC-V `custom-0`，固定 `opcode=7'b0001011`。

Queen 面向四个对等端点：

| endpoint | 名称 | 职责 |
| :---: | :--- | :--- |
| `2'b00` | GEMM | 矩阵计算 DSA |
| `2'b01` | DMA | 数据搬运 DSA |
| `2'b10` | VPU | 向量计算 DSA |
| `2'b11` | SCHED | 共享同步资源、polling、atomic 和状态配置 |

VPU 不归 Scheduler 调度或管理。Scheduler 只调度 GEMM 和 DMA，并提供 timeline、event/hwLock 等共享同步资源，以及 polling、atomic 等服务。SCHED endpoint 表示 Queen Scheduler 本身。Scalar Core decoder 根据 endpoint 直接分流：VPU 目标指令直接送入 VPU 前端，不进入 Queen；GEMM、DMA 和 SCHED 目标指令送入 Queen。

- `FENCE` 在 GEMM/DMA 命令队列中等待目标设备 idle；目标为 SCHED 时，在 Queen 派发处等待所选 GEMM/DMA idle。Scheduler fence 不包含 VPU。
- `WAIT_EVENT/WAIT_TIMELINE` 携带 target endpoint：GEMM/DMA 在其命令队列处等待，VPU 在其后端派发队列处等待，SCHED 在 Queen 派发给 DSA 之前等待。
- `SIGNAL_EVENT/SIGNAL_TIMELINE` 固定进入 Scheduler，更新集中管理的同步资源；同步资源的存放位置与 wait 指令的执行位置是两个不同概念。
- event/timeline 资源由软件通过读取 Queen 资源寄存器申请，申请/分配/消费协议不在本 ISA 中定义。资源申请成功后的 signal/wait 由 ISA 触发。
- `TRACE_TAG` 通过 endpoint 选择记录位置，可直接派发给 GEMM、DMA、VPU 或 Scheduler。
- `FENCE/WAIT_EVENT/WAIT_TIMELINE/POLL_EQ` 将 `inst[25]` 复用为 `notify`，置位时在成功完成并退出对应执行位置的周期发出一次通知 pulse。V2 不提供独立通知指令。
- 所有完成通知由 Scheduler 按来源累计次数并产生持久中断，CPU 查询并确认；通知接口不存在 valid/ready 握手。
- 除 `QRD` 外，Queen 指令均为异步操作，前端接受入队后即可退休。标量核 RISC-V `FENCE` 只约束 CPU 侧退休与规定的内存访问顺序，不等待已退休异步命令对应的 DSA/VPU 执行完成。等待 GEMM/DMA idle 必须另向 Queen 发送 DSA fence；VPU 完成等待由 VPU 侧机制保证。

---

## 2. 通用规则

### 2.1 指令类别

`inst[14:12]` 为完整的 3-bit `funct3`：

| funct3 | 助记符 | 功能 |
| :---: | :--- | :--- |
| `3'b000` | `QWR2` | 将两个 CPU GPR 写入两个相邻 DSA 寄存器 |
| `3'b001` | `QWR1` | 将一个 CPU GPR 写入任意 DSA 寄存器 |
| `3'b010` | `QRD` | 读取一个 DSA 寄存器到 CPU GPR |
| `3'b011` | `QCTRL` | fence、同步、tracing、polling、atomic 和 CPU 通知 |
| `3'b100`–`3'b111` | Reserved | V2 必须解码为非法指令 |

### 2.2 DSA 配置与提交

每个 DSA 端点维护 command-building 配置影子寄存器。配置寄存器之间不存在写入先后依赖，软件可以按任意顺序写入。

- CPU 顺序发送 Queen ISA 指令；Scalar Core decoder 先按 endpoint 选择 Queen 或 VPU 前端。指令握手时，对应前端锁存指令和 PC、预留完整请求 entry，所需 GPR 操作数按 CPU 接口约定在下一周期返回并锁存到该 entry。除 `QRD` 外，前端入队成功即满足退休条件。
- 后端按目标端点的指令顺序处理配置写入，不得让 `macroEop` 越过此前的配置指令。
- 普通 `QWR1/QWR2` 只更新目标端点的配置影子寄存器。
- 同一寄存器被重复写入时，以最后一笔按序处理的值为准。
- `macroEop=1` 时，后端先应用当前指令携带的寄存器写入，再原子快照完整配置并触发目标 DSA 工作；该动作可以晚于 CPU 指令退休。
- DSA 只能看到已提交的配置快照，不得观察配置写到一半的状态。
- DSA 命令 FIFO 满时，后端保持待提交配置并暂停推进，不得丢弃命令。只有前端也无法继续缓存请求时，才向 CPU 施加 backpressure。
- 提交成功后，后续配置写入新的 command-building 状态，不得修改已入队命令。

`macroEop` 只表示“配置完成并提交 DSA 命令”，不用于拼接 `QCTRL` 多指令 payload。V2 的 `QCTRL` 使用 `rs1/rs2` 携带完整动态操作数。

### 2.3 CPU GPR 使用原则

- 动态地址、timeline 值、event mask、比较值和 atomic operand 使用 `rs1/rs2`。
- endpoint、寄存器地址、command 和小型模式字段使用指令立即数字段。
- `rd` 只用于返回结果，不作为输入操作数。
- 每条 Queen 指令最多读取两个 GPR，软件封装通常只需使用 `t0/t1` 等 caller-saved 临时寄存器，不会形成明显寄存器压力。

使用 GPR 传递 32-bit 动态参数可以避免 2～4 条指令拼成一个有状态 macro，也避免 macro 中途发生异常、队列阻塞或上下文切换时的恢复问题。

---

## 3. `QWR2`：相邻双寄存器写

`QWR2` 将 `rs1` 写入 `regBase`，将 `rs2` 写入 `regBase+1`。

| Field | Bits | Name | Description |
| :--- | :--- | :--- | :--- |
| `inst[31:30]` | 2 | endpoint | GEMM/DMA/VPU/SCHED |
| `inst[29:25]` | 5 | regBase | 第一个 DSA 寄存器地址，只允许 `0..30` |
| `inst[24:20]` | 5 | rs2 | CPU 源寄存器 2 |
| `inst[19:15]` | 5 | rs1 | CPU 源寄存器 1 |
| `inst[14:12]` | 3 | funct3 | 固定 `3'b000` |
| `inst[11]` | 1 | macroEop | 当前双写完成后提交 DSA 命令 |
| `inst[10:7]` | 4 | rsv | V2 固定为 0 |
| `inst[6:0]` | 7 | opcode | 固定 `7'b0001011` |

约束：

- `regBase=31` 为非法指令，禁止地址回绕。
- 高频配置寄存器对应该在 DSA 寄存器表中按偶数/奇数或连续地址成对布局。
- 非相邻寄存器使用两条 `QWR1`，不得为少数非相邻场景恢复 `rs2Addr` 字段。
- 相邻双写释放了原 `rs2Addr` 占用的编码空间，使 `funct3` 恢复为标准 `[14:12]`。

伪汇编：

```text
qwr2 endpoint, regBase, rs1, rs2, macroEop
```

---

## 4. `QWR1`：任意单寄存器写

| Field | Bits | Name | Description |
| :--- | :--- | :--- | :--- |
| `inst[31:30]` | 2 | endpoint | GEMM/DMA/VPU/SCHED |
| `inst[29:20]` | 10 | regAddr | DSA 寄存器地址 `0..1023` |
| `inst[19:15]` | 5 | rs1 | CPU 源寄存器 |
| `inst[14:12]` | 3 | funct3 | 固定 `3'b001` |
| `inst[11]` | 1 | macroEop | 当前写入完成后提交 DSA 命令 |
| `inst[10:7]` | 4 | rsv | V2 固定为 0 |
| `inst[6:0]` | 7 | opcode | 固定 `7'b0001011` |

伪汇编：

```text
qwr1 endpoint, regAddr, rs1, macroEop
```

---

## 5. `QRD`：DSA 寄存器读

`QRD` 使用标准 RISC-V `rd[11:7]` 作为 CPU 目的寄存器。

| Field | Bits | Name | Description |
| :--- | :--- | :--- | :--- |
| `inst[31:30]` | 2 | endpoint | GEMM/DMA/VPU/SCHED |
| `inst[29:20]` | 10 | regAddr | DSA 寄存器地址 `0..1023` |
| `inst[19:15]` | 5 | rsv | V2 固定为 0 |
| `inst[14:12]` | 3 | funct3 | 固定 `3'b010` |
| `inst[11:7]` | 5 | rd | CPU 目的寄存器 |
| `inst[6:0]` | 7 | opcode | 固定 `7'b0001011` |

CPU 必须在 Queen 返回读响应或异常后才能退休该指令。CPU 顺序发送请求，Queen 按 `QRD` 请求顺序返回读响应，包括错误响应，因此请求/响应接口不需要 transaction ID。

即使允许多个 `QRD` 在途，CPU 也只需按顺序保留目的 `rd`，将第 N 个读响应对应到第 N 个读请求。不同端点的读延迟可以不同，但 Queen 不得将后发请求的响应提前返回；实现可以串行处理读请求，或在内部缓存早到响应以维持顺序。写与控制指令不产生读响应，不占用该响应顺序。

读取配置影子寄存器时，读请求不得越过同端点此前的配置写入。读取资源分配寄存器可能包含申请副作用，其协议由 Queen 寄存器表定义；对 `rd=x0` 的读请求也必须执行一次，只有 CPU 写回结果被丢弃。

伪汇编：

```text
qrd rd, endpoint, regAddr
```

---

## 6. `QCTRL`：控制与同步指令

### 6.1 通用布局

| Field | Bits | Name | Description |
| :--- | :--- | :--- | :--- |
| `inst[31:30]` | 2 | endpoint | `FENCE/WAIT_EVENT/WAIT_TIMELINE/TRACE_TAG` 的执行端点；signal/polling/atomic 固定为 SCHED |
| `inst[29:26]` | 4 | cmd | 控制命令类型 |
| `inst[25]` | 1 | notify / flag | fence/wait/polling 的完成通知使能；SIGNAL_EVENT 的 set/clr；其他命令固定为 0 |
| `inst[24:20]` | 5 | rs2 | CPU 源寄存器 2，未使用时必须为 `x0` |
| `inst[19:15]` | 5 | rs1 | CPU 源寄存器 1，未使用时必须为 `x0` |
| `inst[14:12]` | 3 | funct3 | 固定 `3'b011` |
| `inst[11:7]` | 5 | aux | timeline/event/op 等小型立即数 |
| `inst[6:0]` | 7 | opcode | 固定 `7'b0001011` |

### 6.2 cmd 分配

| cmd | 名称 | endpoint 规则与执行位置 |
| :---: | :--- | :--- |
| `4'b0000` | `FENCE` | GEMM/DMA 队列等待 idle；SCHED 派发处等待所选 GEMM/DMA idle；VPU 非法 |
| `4'b0001` | `WAIT_EVENT` | GEMM/DMA 命令队列、VPU 后端派发队列或 SCHED 派发处 |
| `4'b0010` | `WAIT_TIMELINE` | GEMM/DMA 命令队列、VPU 后端派发队列或 SCHED 派发处 |
| `4'b0011` | `SIGNAL_TIMELINE` | endpoint 固定 SCHED，更新同步资源 |
| `4'b0100` | `SIGNAL_EVENT` | endpoint 固定 SCHED，更新同步资源 |
| `4'b0101` | `TRACE_TAG` | endpoint 指定 GEMM/DMA/VPU/SCHED 记录位置 |
| `4'b0110` | `POLL_EQ` | endpoint 固定 SCHED |
| `4'b0111` | Reserved | V2 必须解码为非法指令 |
| `4'b1000` | `ATOMIC_RMW` | endpoint 固定 SCHED |
| 其他 | Reserved | V2 必须解码为非法指令 |

### 6.3 `inst[25]` 按 cmd 复用

| cmd | bit25 名称 | 为 0 | 为 1 |
| :--- | :--- | :--- | :--- |
| `FENCE` | notify | idle 条件满足后不通知 | idle 条件满足并完成 fence 时通知 |
| `WAIT_EVENT` | notify | event 条件满足后不通知 | event 条件满足并完成 wait 时通知 |
| `WAIT_TIMELINE` | notify | sequence 条件满足后不通知 | sequence 条件满足并完成 wait 时通知 |
| `POLL_EQ` | notify | 比较成功后不通知 | 比较成功并完成 polling 时通知 |
| `SIGNAL_EVENT` | flag | 清除 event bit | 置位 event bit |
| `SIGNAL_TIMELINE/TRACE_TAG/ATOMIC_RMW` | rsv | 合法 | 非法指令 |

notify 不改变 endpoint 路由、等待条件、派发顺序或 CPU 退休规则。前端锁存 bit25，后端保留到该条指令完成。仅凭条件电平为真或 CPU 指令已退休，不能产生通知。

通知必须关联一次成功完成事件：条件未满足、指令仍停留在队头、发生 fault 或被 flush/reset 丢弃时不产生成功通知。条件到达时已满足的指令，执行完成后也只通知一次；连续完成的不同指令可以每周期各通知一次。

---

## 7. `FENCE`

### 7.1 GEMM/DMA 本地 fence

当 `endpoint` 为 GEMM 或 DMA 时，fence 进入对应 DSA 命令队列。在队头执行时检查对应设备的 idle 状态：

- GEMM fence：等待 GEMM 为 idle，然后才允许继续向 GEMM 派发后续命令。
- DMA fence：等待 DMA 为 idle，然后才允许继续向 DMA 派发后续命令。
- 非目标端点不因本地 fence 而停止派发；目标队列中 fence 之前的命令继续推进，后续命令不得越过 fence。

这里的 idle 指设备已完成此前工作，不只是输入端 `ready=1` 或接收 FIFO 为空；需要等待的读写响应也必须计入设备 idle 判定。fence 在前端入队后即已退休，等待过程发生在后端，不阻塞 CPU 退休。

```text
rs1   = x0
rs2   = x0
notify = inst[25]
aux   = 0
```

`notify=1` 时，目标设备 idle 条件满足且该 fence 完成的周期，由目标 GEMM/DMA 端点发出一次通知 pulse；`notify=0` 时只解除派发阻塞，不通知 CPU。

`endpoint=VPU` 对 Queen `FENCE` 为非法编码。

### 7.2 Scheduler fence

当 `endpoint=SCHED` 时，`rs1[1:0]` 为端点选择 mask：

```text
bit0 GEMM
bit1 DMA
rs1[31:2] 必须为 0
```

该 fence 在 Queen 派发给 DSA 之前执行：先让此前的命令按序派发，随后等待 mask 选中的 GEMM/DMA 为 idle，条件满足后才继续派发 fence 后的 DSA 命令。Scheduler fence 不观察、不阻塞 VPU，也不提供跨 GEMM/DMA/VPU 的全局完成保证。`rs2=x0`、`notify=inst[25]`、`aux=0`；mask 为 0 时无设备等待条件。

`notify=1` 时，该 fence 成功完成的周期由 SCHED 发出一次 pulse，计入 SCHED 来源，不分别计入 mask 选中的 GEMM/DMA 来源；mask 为 0 的 fence 正常完成时同样遵循此规则。

标量核 RISC-V `FENCE` 与 Queen DSA fence 不等价：

- RISC-V `FENCE` 对应 CPU 侧的指令退休与规定的内存访问排序，不自动等待异步 DSA/VPU 执行 idle。
- Queen 配置写、控制命令以及 push 给 DSA/VPU 的操作，在前端接受时即可退休。即使这些指令已经退休，GEMM/DMA/VPU 后端也可能仍在执行。
- 需要等待 GEMM/DMA idle 后继续派发时，软件必须额外向 Queen 发送本地 DSA fence 或 Scheduler fence，不能只使用 RISC-V `FENCE`。
- Queen fence 本身也为异步指令：它保证对应后端的派发顺序，不保证 CPU 在其退休时观察到设备 idle。CPU 可设置 `notify=1` 后执行其他工作或 `WFI`，由完成通知恢复后续任务，也可以通过 `QRD` 读取规定的完成状态；VPU 完成等待由 VPU 自身机制实现。

---

## 8. `WAIT_EVENT`

`WAIT_EVENT` 携带 target endpoint；资源统一保存在 Scheduler，等待操作在 endpoint 指定的位置执行：

| endpoint | wait 执行位置 | 阻塞范围 |
| :--- | :--- | :--- |
| GEMM | GEMM 命令队列的最终派发处 | 该 GEMM 队列中 wait 之后的命令 |
| DMA | DMA 命令队列的最终派发处 | 该 DMA 队列中 wait 之后的命令 |
| VPU | VPU 后端派发队列，由 VPU 实现 | 该 VPU 队列中 wait 之后的命令 |
| SCHED | Queen 派发给 DSA 之前 | Queen 后续向 GEMM/DMA 的派发，不阻塞 VPU |

GEMM/DMA/VPU 的 wait 执行单元通过同步资源查询接口读取 Scheduler event 状态；读取共享资源不改变 VPU 独立管理的边界。条件满足时移除队头 wait 并继续派发，否则保持等待。wait 不阻止此前已经派发的任务继续运行。

```text
endpoint   = GEMM / DMA / VPU / SCHED
rs1[31:0] = event mask
aux[4:0]  = event group ID
notify     = inst[25]
rs2        = x0
```

满足条件为所选 event bit 全部为 1：

```text
(event[group] & rs1) == rs1
```

wait 只查询，不申请、不消费、不修改资源。`notify=1` 时，event 条件满足并完成该 wait 的周期，由执行 wait 的 endpoint 发出一次 pulse；`notify=0` 时不通知。资源存放在 Scheduler 不改变通知来源，例如 VPU wait 完成计入 VPU 来源。

event/timeline 的原子申请与消费由软件按 Queen 资源寄存器协议管理：软件通过 `QRD` 获取空闲资源，申请成功后使用 signal/wait。本 ISA 不定义资源分配寄存器地址、申请失败返回值或消费/回收协议。

---

## 9. `WAIT_TIMELINE`

`WAIT_TIMELINE` 与 `WAIT_EVENT` 使用相同的 target endpoint 语义：

- GEMM/DMA：在对应命令队列的最终派发处等待。
- VPU：在 VPU 后端派发队列等待，由 VPU 侧实现。
- SCHED：在 Queen 派发给 GEMM/DMA 之前等待。

timeline 资源由 Scheduler 集中管理，各 wait 执行单元通过同步资源查询接口获取值；等待只阻塞第 8 节规定的后续派发范围，不申请或消费 timeline，也不阻止已派发任务继续执行。

```text
endpoint   = GEMM / DMA / VPU / SCHED
rs1[31:0] = target sequence
aux[2:0]  = timeline ID
aux[4:3]  = 0
notify     = inst[25]
rs2        = x0
```

`notify=1` 时，sequence 条件满足并完成该 wait 的周期，由执行 wait 的 endpoint 发出一次 pulse，不额外申请或消费 timeline。`notify=0` 时只解除后续派发阻塞。

Scheduler 保存 8 个 32-bit 单调 timeline。等待条件为：

```text
(int32_t)(completed[timelineId] - targetSequence) >= 0
```

资源由软件通过 Queen 资源寄存器申请，申请成功后的 signal/wait 使用上述 timeline ID。单个 context 内最大在途距离必须小于 `2^31`。普通命令禁止清零 timeline；context reset 必须确认引用该资源的 GEMM/DMA/VPU 等待、执行和信号更新均已退出，不能只检查 Scheduler 队列空闲。

软件不得在被 wait 阻塞的同一派发路径后方安排其唯一解除条件，否则会形成队头死锁。

---

## 10. `SIGNAL_TIMELINE`

`SIGNAL_TIMELINE` 不包含可选 target endpoint 语义；`endpoint` 固定为 SCHED，命令进入 Scheduler 队列并更新 Scheduler timeline 资源。

```text
endpoint  = SCHED
rs1       = sequence source；编码为 x0 时表示自增
aux[2:0]  = timeline ID
aux[4:3]  = 0
flag      = 0
rs2       = x0
```

更新规则取决于指令编码中的 `rs1` 索引，而不是读取值是否为 0：

```text
rs1 == x0: completed[timelineId] = completed[timelineId] + 1
rs1 != x0: completed[timelineId] = GPR[rs1]
```

显式写入的新值不得相对当前值回退；非法回退产生同步错误。timeline 只能通过 context reset 清零。

signal 入队或 CPU 指令退休不等于所代表的 DSA 工作完成。若软件用该 signal 表示 GEMM/DMA 完成，必须确保相应的 idle fence 在 signal 更新之前已经满足；不能仅因配置指令在 CPU 侧已退休而提前发布完成值。

---

## 11. `SIGNAL_EVENT`

`SIGNAL_EVENT` 不包含可选 target endpoint 语义；`endpoint` 固定为 SCHED，命令进入 Scheduler 队列并更新 Scheduler event 资源。

```text
endpoint   = SCHED
rs1[31:0] = event mask
aux[4:0]  = event group ID
rs2        = x0
```

- `flag=1`：置位所选 event bit。
- `flag=0`：清除所选 event bit。

置位/清除只更新已由软件申请的资源状态，不构成资源申请、原子消费或回收指令。若 signal 表示工作完成，软件必须保证其发布时机满足对应完成依赖，与第 10 节相同。

---

## 12. `TRACE_TAG`

```text
endpoint    = tracing 记录位置
rs1[31:0] = tracing tag
rs2        = x0
flag       = 0
aux        = 0
```

`TRACE_TAG` 不使用 flag，直接通过 `endpoint` 选择记录位置：

- `endpoint=GEMM`：进入 GEMM 队列，在 GEMM 实际派发点记录。
- `endpoint=DMA`：进入 DMA 队列，在 DMA 实际派发点记录。
- `endpoint=VPU`：由 Scalar Core decoder 直接送入 VPU 队列，在 VPU 实际派发点记录，不进入 Queen。
- `endpoint=SCHED`：进入 Scheduler 队列，在 Scheduler 实际执行点记录。

---

## 13. `POLL_EQ`

`POLL_EQ` 的 `endpoint` 固定为 SCHED：

```text
rs1[31:0] = 4-byte aligned polling address
rs2[31:0] = expected value
notify     = inst[25]
aux        = 0
```

`notify=1` 时，一笔有效读响应的数值等于 expected value、该 polling 成功完成的周期，SCHED 发出一次 pulse。读请求发出、比较失败、重复重试、总线错误或超时均不产生成功通知。

Scheduler polling 间隔 N 通过 Scheduler 配置寄存器设置，复位默认值为 8 cycle。N 为正整数，具体寄存器地址和字段宽度由 Scheduler 寄存器表定义，不在指令编码中携带。

每 N cycle 最多发起一次 32-bit polling 读取；实际发起还受总线 ready 和前一笔读取状态限制。读值等于 `rs2` 时命令完成，否则继续 polling。实现必须定义该地址所在的物理地址空间、总线错误响应和超时寄存器。地址未 4-byte 对齐时产生异常。

V2 基础指令不携带 compare mask；需要 masked compare 时通过 Scheduler 配置寄存器扩展。

---

## 14. `ATOMIC_RMW`

`ATOMIC_RMW` 的 `endpoint` 固定为 SCHED：

```text
rs1[31:0] = 4-byte aligned address
rs2[31:0] = operand
aux[1:0]  = operation
aux[4:2]  = 0
flag       = 0
```

| aux[1:0] | 操作 |
| :---: | :--- |
| `2'b00` | `ADD`: `new = old + operand` |
| `2'b01` | `CLR`: `new = old & ~operand` |
| `2'b10` | `SET`: `new = old | operand` |
| `2'b11` | Reserved |

Scheduler 必须通过具备原子语义的总线事务实现；若底层互连不支持与外部 CPU/cache 一致的 atomic，则该能力只能用于明确标记为 non-cacheable 且由 Scheduler 独占原子访问的地址区间。

地址未对齐、总线拒绝或超时均产生异步错误。V2 基础指令不返回 old value。

---

## 15. CPU 完成通知

### 15.1 通知使能与执行位置

完成通知通过 `FENCE/WAIT_EVENT/WAIT_TIMELINE/POLL_EQ` 的 `notify=1` 使能，不使用独立指令。通知与等待条件的成功完成绑定，不能只因队列推进到某个标记而推断设备工作完成。

| endpoint | 通知发出位置与条件 |
| :--- | :--- |
| GEMM | GEMM 最终派发处，fence 的 idle 条件或 wait 条件满足并完成 |
| DMA | DMA 最终派发处，fence 的 idle 条件或 wait 条件满足并完成 |
| VPU | VPU 后端派发队列，wait 条件满足并完成，由 VPU 侧实现 |
| SCHED | Scheduler 派发/执行处，fence/wait/polling 条件满足并完成 |

CPU 将指令交给前端并成功入队后即可退休。指令在指定端点成功完成时才发出一次 pulse，不能在 CPU 退休或 Queen 接收时提前产生通知。VPU 仅向 Scheduler 汇总通知，不因此归 Scheduler 管理。

### 15.2 端点 pulse 接口与完成顺序

每个 endpoint 向 Scheduler 提供一根 `notify_pulse[e]`，来源由通道身份确定，无 payload，也没有 valid/ready 握手。V2 通知接口要求生产端与 Scheduler 使用同一时钟、接收端持续采样；若集成跨时钟，必须在边界增加可靠事件跨域机制，不能直接跨域传递一拍 pulse。

```text
notify_pulse[e] = cmd_success_complete[e]
                 && cmd_supports_notify[e]
                 && cmd_notify[e]

每个 notify_pulse[e] 为 1 的采样周期：
    NOTIFY_COUNT[e] = NOTIFY_COUNT[e] + 1
```

`cmd_success_complete` 指当前指令成功完成并退出执行位置的一次性事件，不是 CPU retire，也不是 wait 条件持续为真的电平。`cmd_supports_notify` 仅包括 `FENCE/WAIT_EVENT/WAIT_TIMELINE/POLL_EQ`。

- 每条带 notify 的指令只产生一次 pulse；`notify=0` 或不支持 notify 的指令不产生完成通知。
- 每个 endpoint 每周期最多完成一个通知事件。不同 endpoint 同周期的 pulse 必须分别计数，不允许单路仲裁丢弃其中任意 pulse。
- 连续周期完成不同指令时，pulse 可连续为 1，Scheduler 必须逐周期计数，不能只做上升沿检测。
- Scheduler 始终采样并记录各来源 pulse，通知计数逻辑独立于命令派发，不得被 Scheduler 自身的 wait/fence 阻塞或门控。
- pulse 发出后端点按正常完成流程推进，不等待 Scheduler 返回确认；COUNT/ACK 是 CPU 的通知处理确认，不是端点握手。

推荐软件表达：

```text
WAIT_EVENT/WAIT_TIMELINE(endpoint, notify=1)
POLL_EQ(SCHED, notify=1)
GEMM/DMA 工作 -> FENCE(endpoint, notify=1)
```

分别在 wait 条件满足、polling 比较成功、目标设备 idle 且对应指令完成时通知。通知来源是该指令执行的 endpoint；例如 SCHED fence 等待 GEMM/DMA 后只产生一次 SCHED 来源通知。

带 notify 的指令不得越过同一 endpoint 队列中的前序命令；不同 endpoint 之间不能仅凭 CPU 提交顺序推断通知时机。wait 通知只证明等待条件已满足，不自动证明其他设备 idle。

GEMM/DMA 工作完成通知使用 `FENCE(notify=1)`。VPU 的工作完成等待必须由 VPU 自身机制或对应 timeline/event 保证，不能用 Queen VPU fence 替代。

### 15.3 Scheduler 汇总寄存器与中断

Scheduler 按四个 endpoint 保存通知状态，同一来源的 fence/wait/polling 完成 pulse 共用该来源计数。CPU 通过 `QRD endpoint=SCHED` 查询，通过 `QWR1 endpoint=SCHED` 写入 enable/ack。以下为逻辑寄存器名，具体地址由 Scheduler 寄存器表分配：

| 寄存器 | 位宽 | 访问 | 语义 |
| :--- | :---: | :--- | :--- |
| `NOTIFY_COUNT[e]` | 每来源 32 bit | RO | 来源 e 已采样的通知 pulse 累计次数，模 `2^32` 递增 |
| `NOTIFY_ACK[e]` | 每来源 32 bit | RW | CPU 已处理并确认的累计次数 |
| `NOTIFY_PENDING` | 4 bit | RO | bit e 为 1 表示 `COUNT[e] != ACK[e]`，同时表示通知来源 |
| `NOTIFY_ENABLE` | 4 bit | RW | bit e 使能该来源的 CPU 通知中断 |

`e=0/1/2/3` 分别对应 GEMM/DMA/VPU/SCHED。复位时 COUNT、ACK 和 ENABLE 均为 0。

通知中断为 level-sensitive：

```text
pending[e] = (NOTIFY_COUNT[e] != NOTIFY_ACK[e])
notify_irq = OR(NOTIFY_PENDING & NOTIFY_ENABLE)
```

中断屏蔽仅屏蔽 IRQ，不影响 COUNT 累计或 PENDING 查询。多次通知可以合并成一次 IRQ，但次数分别保存在 COUNT 中，不会因为中断合并而丢失。

CPU 查询到 COUNT 快照后，处理该快照覆盖的通知，再将同一个快照写入 ACK。读取 COUNT/PENDING 不清零计数或 pending；不得通过盲目清 pending 丢弃并发到来的通知。

```text
读取 COUNT[e] = n
处理累计到 n 的通知
写 ACK[e] = n
```

若查询后又收到通知，COUNT 将继续增加，写入旧快照 n 不会清除新增通知，IRQ 保持有效。硬件必须保证通知计数更新与 ACK 写入同周期发生时也符合上述语义。

软件只确认已处理的 COUNT 快照，单来源未确认距离必须小于 `2^31`；新增次数按无符号差 `COUNT[e] - ACK[e]` 计算。COUNT 不允许软件清零；reset 前必须停止新的通知并确认所有相关队列和通知通道已退出。

COUNT、PENDING 的读取以及 ACK、ENABLE 的写入必须能够在 Scheduler 派发路径被 wait/fence 阻塞时独立服务，并保持同寄存器访问顺序，避免 CPU 中断处理或确认动作被等待队列阻塞。

### 15.4 单线程软件使用方式

软件按 endpoint 和通知累计序号维护待恢复任务，不依赖硬件保存逐通知 tag。提交 `notify=1` 的 fence/wait/polling 前，先登记任务后续状态并使能对应来源；通知可能在 CPU 准备进入 `WFI` 前已经到达，因此 pending 必须持续保持至 ACK。

等待指令直接携带通知，例如 `queen_wait_timeline(endpoint, timeline, sequence, true)`。`notify=false` 保留无中断的后端同步用法。

中断处理程序读取来源和 COUNT，记录已就绪任务，并按已处理快照确认；任务后续阶段由单线程主循环执行。CPU 不必忙等，也不需要新增硬件线程；IRQ 不会自动跳回某个 kernel 的中间 PC。

---

## 16. 软硬件握手与异常

### 16.1 CPU 请求与退休

- CPU 顺序发出 Queen ISA 请求，Scalar Core decoder 按 endpoint 路由到 Queen 或 VPU 前端。请求握手时，对应前端锁存指令和 PC，并为完整请求及所需源操作数预留 entry；CPU 在约定的下一周期返回源 GPR 值，接收前端将其锁存到对应 entry，后续执行不再读取 CPU GPR。
- 除 `QRD` 外，`QWR1/QWR2/QCTRL` 都是异步操作，给到对应前端接收队列并成功入队后即可退休。VPU 目标指令由 Scalar Core decoder 直接送入 VPU 独立前端，不进入 Queen，也不表示由 Scheduler 管理 VPU。
- 带 `macroEop` 的配置写也在前端入队后退休，不等待后端应用配置、生成快照、触发执行或设备 idle。
- fence/wait/polling 指令同样在前端入队后退休，其等待/比较条件在对应后端执行；`notify=1` 不改变退休规则。CPU 可继续发送后续指令，直至前端队列背压。
- 带 notify 的指令成功完成时，端点发出一次 pulse，Scheduler 在采样周期更新对应 COUNT/pending，不存在通知 valid/ready 握手，也不等待 CPU 中断处理。
- `QRD` 必须等待顺序返回的数据或异常后才能退休。读响应只对应读请求，接口不要求 transaction ID。

### 16.2 Backpressure

- 前端没有空间保存完整请求及操作数时，必须拉低请求 ready，未接受的指令不得退休。
- DSA 命令队列满、wait/fence 未满足或配置快照暂不可提交，只阻塞对应后端推进，不撤销此前已接受指令的退休。
- 后端拥塞导致前端缓存也满时，才将背压传递到 CPU。
- `QRD` 请求和响应必须按序；没有读处理/响应缓存容量时暂停接受新的读请求。

当 response valid 为 1 而 ready 为 0 时，必须保持响应 valid、数据及错误状态不变，直到响应握手成功，不得丢失响应。

### 16.3 异常与异步错误

以下情况产生同步非法指令异常：

- opcode/funct3/cmd 为保留编码；
- V2 要求为 0 的保留字段非 0；
- `QWR2.regBase=31`；
- `SIGNAL_TIMELINE/SIGNAL_EVENT/POLL_EQ/ATOMIC_RMW` 的 endpoint 不是 SCHED；
- Queen `FENCE` 的 endpoint 为 VPU；
- `SIGNAL_TIMELINE/TRACE_TAG/ATOMIC_RMW` 的 `inst[25]` 非 0；
- 前端可判定的未实现寄存器地址。

`QCTRL.cmd=4'b0111` 为保留编码，无论 endpoint、bit25 或操作数字段为何值，都必须在前端报非法指令。

`FENCE/WAIT_EVENT/WAIT_TIMELINE/POLL_EQ` 的 `inst[25]=1` 是合法的完成通知使能；`SIGNAL_EVENT` 的该位仍为 set/clr，两种取值均合法。

同步编码错误必须在前端接受/退休之前检测。只有后端才能判定的寄存器访问或执行错误，不能回溯撤销已退休异步指令，必须走异步 fault；`QRD` 的访问错误通过按序读响应返回。

DSA 执行错误、polling/atomic 总线错误属于异步错误，由 Scheduler 状态寄存器记录：

```text
faultValid
faultEndpoint
faultCode
faultSequence
faultPc
```

发生异步错误后，关联 timeline 不得发布成功值；相关队列冻结并通知 CPU，由软件执行 flush/reset。

---

## 17. 软件接口建议

建议提供以下编译器 intrinsic 或内联汇编封装：

```c
void queen_wr2(unsigned endpoint, unsigned reg_base,
               uint32_t value0, uint32_t value1, bool eop);
void queen_wr1(unsigned endpoint, unsigned reg_addr,
               uint32_t value, bool eop);
uint32_t queen_rd(unsigned endpoint, unsigned reg_addr);

void queen_fence(unsigned endpoint, uint32_t gemm_dma_mask, bool notify);
void queen_wait_event(unsigned endpoint, unsigned group,
                      uint32_t mask, bool notify);
void queen_wait_timeline(unsigned endpoint, unsigned timeline,
                         uint32_t sequence, bool notify);
void queen_signal_timeline_next(unsigned timeline);
void queen_signal_timeline_value(unsigned timeline, uint32_t sequence);
void queen_signal_event(unsigned group, uint32_t mask, bool set);
void queen_trace(unsigned endpoint, uint32_t tag);
void queen_poll_eq(uint32_t address, uint32_t expected, bool notify);
void queen_atomic_rmw(uint32_t address, uint32_t operand, unsigned op);

uint32_t queen_notify_pending(void);
uint32_t queen_notify_count(unsigned source_endpoint);
uint32_t queen_notify_ack_count(unsigned source_endpoint);
void queen_notify_ack(unsigned source_endpoint, uint32_t processed_count);
void queen_notify_enable(uint32_t source_mask);
```

`queen_fence/queen_wait_event/queen_wait_timeline/queen_poll_eq` 各发出一条异步 ISA，`notify` 直接编码到 `inst[25]`；置为 true 时在成功完成后通知 CPU。`queen_signal_event` 的 `set` 参数仍编码到同一位，不产生完成通知。

notify 状态查询及 ACK/ENABLE 封装通过 Scheduler 寄存器读写实现，不新增 ISA。`source_endpoint` 表示查询的来源，寄存器访问的 endpoint 始终为 SCHED。

封装必须声明为 `volatile`，并根据命令是否访问内存插入必要的 compiler barrier。CPU 与 Queen 共享内存但不具备硬件 cache coherence 时，软件还必须在提交前执行平台规定的 cache clean/invalidate。

---

## 18. V2 最小验证要求

1. 为每条指令提供 encode/decode 十六进制测试向量。
2. 验证全部保留编码在前端退休前触发非法指令。
3. 验证 `QWR2` 相邻地址、`regBase=31` 异常及 `macroEop` 当前写入包含在提交快照中。
4. 随机重排不同 DSA 配置寄存器的写入顺序，最终快照必须一致；`macroEop` 不得越过前序写入。
5. 验证 GEMM/DMA 本地 fence 在设备 idle 后才继续目标队列派发，非目标队列不受阻塞。
6. 验证 Scheduler fence 在所选 GEMM/DMA idle 后才继续 Queen 的后续 DSA 派发，不包含 VPU。
7. 验证 Queen 配置/控制指令前端入队即退休；RISC-V `FENCE` 不因已退休异步命令的 DSA/VPU 尚未 idle 而额外等待。
8. 验证 wait endpoint 路由：GEMM/DMA 命令队列、VPU 后端派发队列、Queen 派发处分别执行等待。
9. 验证 `WAIT_EVENT` 只查询、不申请/消费/修改资源；资源申请由 Queen 寄存器协议处理，不在 wait ISA 中实现。
10. 验证 `SIGNAL_TIMELINE rs1=x0` 自增、显式赋值、回绕比较、禁止回退和资源安全 reset。
11. 验证 TRACE_TAG 仅由 endpoint 选择 GEMM/DMA/VPU/SCHED 记录位置，flag 非 0 报非法指令。
12. 验证跨端点多个 `QRD` 延迟不同时仍按请求顺序返回，混入异步写/控制不影响响应配对，不需要 transaction ID。
13. 验证前端 queue full、后端阻塞、response backpressure 和异步 fault 不丢失。
14. 验证 polling 间隔复位为 8 cycle，修改 Scheduler 配置寄存器后使用配置的 N cycle。
15. 验证 polling/atomic 的地址对齐、总线错误与超时路径。
16. 验证 `FENCE/WAIT_EVENT/WAIT_TIMELINE/POLL_EQ` 的 `inst[25]` 两种取值均合法，只有 `notify=1` 才在成功完成时通知；前端入队和 CPU 退休不提前产生 pulse。
17. 验证 `SIGNAL_EVENT` 的 bit25 保持 set/clr；`SIGNAL_TIMELINE/TRACE_TAG/ATOMIC_RMW` 的 bit25 非 0 在前端报非法指令。
18. 验证 `QCTRL.cmd=4'b0111` 在全部 endpoint、bit25 两种取值及不同操作数字段下均在前端报非法指令，不入队、不产生通知。
19. 验证 GEMM/DMA/VPU/SCHED 的 wait 完成通知计入执行端点来源；Scheduler fence 只产生一次 SCHED 来源通知，包括多设备 mask 和 mask 为 0 的情况。
20. 验证带 notify 的指令不越过同端点前序命令；不同端点不产生隐式依赖，wait 通知不被误判为其他设备 idle。
21. 验证 fence/wait 条件初始已满足时仍只通知一次；条件持续满足但命令未退出时不重复通知，`notify=0` 或不支持 notify 的指令不发完成 pulse。
22. 验证 `POLL_EQ(notify=1)` 仅在收到比较成功的读响应并完成时通知；读请求发出、比较失败重试、总线错误、超时及 flush 不产生成功通知，其他命令的 fault/flush 同样不产生成功通知。
23. 验证无 valid/ready 的通知 pulse 逐周期采样：四来源同周期通知各自加一，单来源连续周期为 1 时每周期加一，不做上升沿检测，也不重复计数。
24. 验证 Scheduler wait/fence 阻塞期间仍持续采样通知、读取 COUNT/PENDING 和处理 ACK/ENABLE；端点完成不等待 CPU ACK。
25. 验证 COUNT/ACK/pending/IRQ：读不清状态、屏蔽不丢计数、旧快照 ACK 不覆盖新增通知、同周期 ACK 与 pulse 到达无丢失。
26. 验证 CPU 执行 `WFI` 前已到达的通知仍保持 pending，单来源累计计数回绕时按模数差计算新增次数。
