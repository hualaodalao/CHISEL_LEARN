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

- `FENCE` 只在 GEMM/DMA 命令队列中等待目标设备 idle，`endpoint=SCHED/VPU` 为非法编码。Scheduler 派发路径不做任何条件等待；跨设备先后顺序由消费者队列一侧的 `FENCE`/`WAIT_*` 表达。
- `WAIT_EVENT/WAIT_TIMELINE` 携带 target endpoint：GEMM/DMA 在其命令队列处等待，VPU 在其后端派发队列处等待，`endpoint=SCHED` 为非法编码。
- `QWR1/QWR2` 只写 DSA 内部的配置寄存器，`QCTRL RUN` 关闭当前配置组并启动设备；Queen 不保存配置影子，设备用 credit 告知还能接受多少条 `RUN`，`QWR`/`QRD` 不受 credit 门控（见 6.4）。
- `SIGNAL_EVENT/SIGNAL_TIMELINE` 固定进入 Scheduler，更新集中管理的同步资源；同步资源的存放位置与 wait 指令的执行位置是两个不同概念。
- event/timeline 资源由软件通过读取 Queen 资源寄存器申请，申请/分配/消费协议不在本 ISA 中定义。资源申请成功后的 signal/wait 由 ISA 触发。
- 除 ISA signal 外，GEMM/DMA 完成也可以更新同一套 event/timeline。这类更新不占指令编码，目标由软件在提交前写入端点配置并由硬件在完成点应用，具体映射与可见性由 Queen 硬件规格定义；软件用同一条 wait 观察两类来源。
- `TRACE_TAG` 通过 endpoint 选择记录位置，可直接派发给 GEMM、DMA、VPU 或 Scheduler。
- **V2 不提供 Queen 侧完成通知机制**：没有 notify 位、没有 notify pulse、没有通知计数寄存器。`inst[25]` 除 `SIGNAL_EVENT` 的 set/clr 外一律为保留位，必须为 0。
- CPU 感知完成走两条通路：单引擎完成由**设备自己的中断**上报；跨引擎条件由 event/timeline 表达，`WAIT_*` 在硬件里阻塞派发，CPU 不参与（见第 15 章）。
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
| `3'b011` | `QCTRL` | fence、同步、tracing、polling 与 atomic |
| `3'b100`–`3'b111` | Reserved | V2 必须解码为非法指令 |

### 2.2 DSA 配置与提交

配置寄存器位于 DSA 内部，Queen 不保存配置影子，也不生成配置快照。`QWR1/QWR2` 是按序转发的寄存器写，`QCTRL RUN` 是启动命令。配置寄存器之间不存在写入先后依赖，软件可以按任意顺序写入。

- CPU 顺序发送 Queen ISA 指令；Scalar Core decoder 先按 endpoint 选择 Queen 或 VPU 前端。指令握手时，对应前端锁存指令和 PC、预留完整请求 entry，所需 GPR 操作数按 CPU 接口约定在下一周期返回并锁存到该 entry。除 `QRD` 外，前端入队成功即满足退休条件。
- 后端按目标端点的指令顺序转发配置写，`RUN` 不得越过同端点此前的 `QWR`。
- 一个配置组指两次 `RUN` 之间的全部 `QWR`。设备把配置组写入当前打开的配置缓冲，在收到 `RUN` 之前不得执行。
- 同一寄存器被重复写入时，以最后一笔按序处理的值为准。
- `RUN` 关闭当前配置组并启动目标 DSA；该动作可以晚于 CPU 指令退休。
- 设备必须保证已启动的配置不被后续配置写修改：配置缓冲在被执行上下文取走前不得复用，取走后即归还 credit（见 6.4）。
- `RUN` 独占一个派发 beat，设备在该 beat 上用命令通道的 ready 告知能否接受新启动；纯写 beat 恒可写，`QRD` 只被 Queen 自己的读响应缓存反压，与设备忙闲无关。ready 为 0 时 `RUN` 停等，不得丢弃命令。只有前端也无法继续缓存请求时，才向 CPU 施加 backpressure。
- `RUN` 之后新的 `QWR` 开启新的配置组，不得修改已启动的配置。

`QCTRL` 使用 `rs1/rs2` 携带完整动态操作数，不用于拼接多指令 payload。

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
| `inst[29:25]` | 5 | regBase[4:0] | 第一个 DSA 寄存器地址的低 5 位 |
| `inst[24:20]` | 5 | rs2 | CPU 源寄存器 2 |
| `inst[19:15]` | 5 | rs1 | CPU 源寄存器 1 |
| `inst[14:12]` | 3 | funct3 | 固定 `3'b000` |
| `inst[11]` | 1 | regBase[5] | 地址第 5 位；`regBase = {inst[11], inst[29:25]}`，只允许 `0..62` |
| `inst[10:7]` | 4 | rsv | V2 固定为 0 |
| `inst[6:0]` | 7 | opcode | 固定 `7'b0001011` |

约束：

- `regBase=63` 为非法指令，保证 `regBase+1` 不越出 6-bit 地址窗口。
- `QWR` 不携带提交语义，提交由 `QCTRL RUN` 承担。
- 在 Queen→DSA 原生接口上 `QWR2` 是**一条双地址写命令**（`WRITE2`，携带 `reg0Addr/reg1Addr` 与 `data0/data1`），不拆成两条单写；两个寄存器必须同时生效。见 Queen 硬件接口约束 5.2。
- 高频配置寄存器对应该在 DSA 寄存器表中按偶数/奇数或连续地址成对布局。
- 非相邻寄存器使用两条 `QWR1`，不得为少数非相邻场景恢复 `rs2Addr` 字段。
- 相邻双写释放了原 `rs2Addr` 占用的编码空间，使 `funct3` 恢复为标准 `[14:12]`。

伪汇编：

```text
qwr2 endpoint, regBase, rs1, rs2
```

---

## 4. `QWR1`：任意单寄存器写

| Field | Bits | Name | Description |
| :--- | :--- | :--- | :--- |
| `inst[31:30]` | 2 | endpoint | GEMM/DMA/VPU/SCHED |
| `inst[29:20]` | 10 | regAddr | DSA 寄存器地址 `0..1023` |
| `inst[19:15]` | 5 | rs1 | CPU 源寄存器 |
| `inst[14:12]` | 3 | funct3 | 固定 `3'b001` |
| `inst[11:7]` | 5 | rsv | V2 固定为 0 |
| `inst[6:0]` | 7 | opcode | 固定 `7'b0001011` |

伪汇编：

```text
qwr1 endpoint, regAddr, rs1
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

读取 DSA 配置寄存器时，读请求不得越过同端点此前的配置写入；配置值由设备按序返回，Queen 不缓存配置影子。读取资源分配寄存器可能包含申请副作用，其协议由 Queen 寄存器表定义；对 `rd=x0` 的读请求也必须执行一次，只有 CPU 写回结果被丢弃。

实现口径：Queen 在**前端接受**时就把 QRD 压入在途表（默认 `qrdDepth=16`，可配），按程序序退休；GEMM/DMA/SCHED 三个来源各有一个响应 FIFO 吸收跨端点乱序。**同端点读响应必须按请求序返回是设备硬义务**——Queen 的表项不存寄存器地址、不打 tag，队头只按来源配对，设备乱序返回会把数据写进错误的 `rd` 且无法检测。SCHED 端点的寄存器映射见 `queenHarewareInterface.md` 第 18 章。

伪汇编：

```text
qrd rd, endpoint, regAddr
```

---

## 6. `QCTRL`：控制与同步指令

### 6.1 通用布局

| Field | Bits | Name | Description |
| :--- | :--- | :--- | :--- |
| `inst[31:30]` | 2 | endpoint | `FENCE/WAIT_EVENT/WAIT_TIMELINE/TRACE_TAG/RUN` 的执行端点；signal/polling/atomic 固定为 SCHED |
| `inst[29:26]` | 4 | cmd | 控制命令类型 |
| `inst[25]` | 1 | flag / rsv | SIGNAL_EVENT 的 set/clr；其他所有命令为保留位，必须为 0 |
| `inst[24:20]` | 5 | rs2 | CPU 源寄存器 2，未使用时必须为 `x0` |
| `inst[19:15]` | 5 | rs1 | CPU 源寄存器 1，未使用时必须为 `x0` |
| `inst[14:12]` | 3 | funct3 | 固定 `3'b011` |
| `inst[11:7]` | 5 | aux | timeline/event/op 等小型立即数 |
| `inst[6:0]` | 7 | opcode | 固定 `7'b0001011` |

### 6.2 cmd 分配

| cmd | 名称 | endpoint 规则与执行位置 |
| :---: | :--- | :--- |
| `4'b0000` | `FENCE` | GEMM/DMA 队列等待 idle；SCHED/VPU 非法 |
| `4'b0001` | `WAIT_EVENT` | GEMM/DMA 命令队列或 VPU 后端派发队列；SCHED 非法 |
| `4'b0010` | `WAIT_TIMELINE` | GEMM/DMA 命令队列或 VPU 后端派发队列；SCHED 非法 |
| `4'b0011` | `SIGNAL_TIMELINE` | endpoint 固定 SCHED，更新同步资源 |
| `4'b0100` | `SIGNAL_EVENT` | endpoint 固定 SCHED，更新同步资源 |
| `4'b0101` | `TRACE_TAG` | endpoint 指定 GEMM/DMA/VPU/SCHED 记录位置 |
| `4'b0110` | `POLL_EQ` | endpoint 固定 SCHED |
| `4'b0111` | Reserved | V2 必须解码为非法指令 |
| `4'b1000` | `ATOMIC_RMW` | endpoint 固定 SCHED |
| `4'b1001` | `RUN` | endpoint=GEMM/DMA，关闭当前配置组并启动设备；SCHED 非法，VPU 由 VPU 前端定义 |
| `4'b1010` | `FLUSH` | endpoint 固定 SCHED，触发一次恢复流程（6.5） |
| 其他 | Reserved | V2 必须解码为非法指令 |

### 6.3 `inst[25]` 按 cmd 复用

| cmd | bit25 名称 | 为 0 | 为 1 |
| :--- | :--- | :--- | :--- |
| `SIGNAL_EVENT` | flag | 清除 event bit | 置位 event bit |
| 其他全部 cmd（含 `FENCE/WAIT_*/POLL_EQ/RUN`） | rsv | 合法 | 非法指令 |

V2 删除了完成通知机制，因此除 `SIGNAL_EVENT` 外 bit25 不再承载任何语义。前端对非 0 值报非法指令，该检查先于空组 `RUN` 判定。

### 6.4 `RUN`：配置组提交与 DSA 启动

`RUN` 是唯一的 DSA 启动命令。配置数据全部由 `QWR` 写入设备，`RUN` 本身不携带配置。

```text
endpoint  = GEMM / DMA
cmd       = 4'b1001
rs1[7:0]  = cmdTag（可选，0 表示不使用；高 24 位忽略）
rs2       = swTag（可选，原样透传，0 表示不使用）
aux       = 0
```

- `RUN` 关闭当前配置组并启动目标设备；设备在收到 `RUN` 之前不得执行该组配置。
- `RUN` 按序派发，不得越过同端点此前的 `QWR`；同端点的 `FENCE/WAIT_*` 仍按队头阻塞规则处理。
- `cmdTag` 取 `rs1` 值的低 8 位，高 24 位忽略；`swTag` 取整个 `rs2` 值。两者透传到设备命令标签与 trace/fault 关联字段，不影响执行语义。操作数在次拍才返回，因此它们的取值不构成前端非法指令条件。
- `endpoint=SCHED` 为非法编码。VPU 的启动由 VPU 前端自行定义，不进入 Queen。
- 硬件完成信号目标 `HW_SIGNAL_TARGET/HW_SIGNAL_MASK` 是设备公共契约区寄存器（固定地址 `0x080/0x081`，见 Queen 硬件接口约束第 16 章），由 `QWR` 在 `RUN` 之前写入；设备在 `RUN` 被接受（`start`）当拍采样，完成时随 completion 回传，Queen 不保存副本。

配置组与 credit：

- Queen 为每个 GEMM/DMA 端点维护一个 openGroup 标志：`QWR` 置位，`RUN` 清零。`RUN` 时 openGroup 为 0 即空配置组，在前端报非法指令。
- 流控只门控 `RUN`：`QWR` 恒可写，`QRD` 只看 Queen 读响应通路。`RUN` beat 要求设备 ready 为 1，被接受即占用一个设备内部 credit；Queen 侧不读 credit 计数、不另设计数器，也没有独立的 `ready_start` 端口。
- credit 只表示“设备还能接受多少条 `RUN`”，归还点由设备自定（取走配置即还或完成时还），Queen 只看电平。设备须在自己的规格中写明归还点与初值；无论哪种，credit 不足都必须反压而不是报错丢弃。
- ready 为 0 时该 `RUN` beat 停等。因为 `RUN` 独占 beat，同组的配置写已在更早的 beat 发出，不会被它拖住。该端点后续指令仍按程序序等待，另一端点不受影响。
- 派发是并行的：只有 `QWR1/QWR2` 可以合并进一个 beat（至多 `nativeWidth` 条），任何 `QCTRL`/`QRD` 独占一个 beat。因此同端点一包内的多条 `RUN` 会分多个 beat 下发，设备同拍只会看到一条 `RUN`，且它不与任何配置写同拍。
- 设备侧义务：`RUN` 被接受后，后续 `QWR` 不得改变该 `RUN` 已提交的配置；要重叠配置准备与执行，设备需自行保证这一点（通常需要双缓冲）。DSA 侧实现不在本文范围内。

### 6.5 `FLUSH`：恢复流程触发

```text
endpoint = SCHED
cmd      = 4'b1010
aux      = 0
flag     = 0
rs1/rs2  = 忽略
```

`FLUSH` 与写 SCHED `0x01 RECOVER`、CPU 侧 `flushReq` 端口是**同一条恢复流程的三个触发源**，语义完全相同。四相 req/ack 握手、丢弃规则与完成条件见 `queenHarewareInterface.md` 第 5.5 节。

软件可见效果：

- 在途与 flush 期间新接收的 `QRD` 一律回 `rdValid=1, data=0xdeadbeef, trap=0`；
- 已派发给设备的 queued 命令被丢弃，运行中工作由设备终止或等完；
- event/timeline 清零、fault 清除、openGroup 清零；
- `POLL_INTERVAL` 是软件配置旋钮，**不随 flush 复位**。

**软件义务**：flush 期间 CPU 可以继续发指令——它们被接受并丢弃，QRD 立刻回 `0xdeadbeef`，不会挂死。但**持续不间断地发射会让静默窗永远重新计数、flush 永不完成**（`flushAck` 要求排空后连续 `flushQuietCycles` 拍无新指令）。软件最终必须安静下来；判别恢复是否结束看 QRD 是否还回 `0xdeadbeef`，或读 SCHED `IDLE` 是否为 1。

`FLUSH` 指令与 `RECOVER` 写只触发 Queen 自己与两个设备的恢复；sys 级广播 `flushReq` 是第三条触发源，它有效期间 Queen 一直停在 flush 态，直到 sys 收齐所有模块的 ack 后落下 req。

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
aux   = 0
```

fence 只解除派发阻塞，不产生任何 CPU 可见事件。CPU 若需要知道设备已排空，读设备自己的中断/状态，而不是等 Queen 通知。

`endpoint=VPU` 对 Queen `FENCE` 为非法编码。

### 7.2 `endpoint=SCHED` 非法

Scheduler 的派发路径不执行任何条件等待。单线程 CPU 上，等待 Scheduler 派发点的指令会让后续所有指令都无法发射，而解除条件往往正需要后续指令，因此 `FENCE` 的 `endpoint=SCHED` 定义为非法编码，前端必须报非法指令异常。

跨设备先后顺序不经过 Scheduler，而是写进消费者队列：需要 GEMM 在 DMA 完成后才开始，就把 `WAIT_EVENT`/`WAIT_TIMELINE` 或 `FENCE` 放进 GEMM 队列，由 GEMM 在自己的派发点等待。Scheduler 通路只承载 signal、polling、atomic 与 trace，不承载完成顺序。

标量核 RISC-V `FENCE` 与 Queen DSA fence 不等价：

- RISC-V `FENCE` 对应 CPU 侧的指令退休与规定的内存访问排序，不自动等待异步 DSA/VPU 执行 idle。
- Queen 配置写、控制命令以及 push 给 DSA/VPU 的操作，在前端接受时即可退休。即使这些指令已经退休，GEMM/DMA/VPU 后端也可能仍在执行。
- 需要等待 GEMM/DMA idle 后继续派发时，软件必须额外发送本地 DSA fence，不能只使用 RISC-V `FENCE`。
- Queen fence 本身也为异步指令：它保证对应后端的派发顺序，不保证 CPU 在其退休时观察到设备 idle。CPU 若需要在设备排空后恢复任务，依赖设备自己的完成中断，或通过 `QRD` 读取规定的完成状态；VPU 完成等待由 VPU 自身机制实现。

---

## 8. `WAIT_EVENT`

`WAIT_EVENT` 携带 target endpoint；资源统一保存在 Scheduler，等待操作在 endpoint 指定的位置执行：

| endpoint | wait 执行位置 | 阻塞范围 |
| :--- | :--- | :--- |
| GEMM | GEMM 命令队列的最终派发处 | 该 GEMM 队列中 wait 之后的命令 |
| DMA | DMA 命令队列的最终派发处 | 该 DMA 队列中 wait 之后的命令 |
| VPU | VPU 后端派发队列，由 VPU 实现 | 该 VPU 队列中 wait 之后的命令 |

`endpoint=SCHED` 为非法编码：Scheduler 派发路径不做条件等待，需要顺序约束的一方应把 wait 放进自己的消费者队列。

GEMM/DMA/VPU 的 wait 执行单元通过同步资源查询接口读取 Scheduler event 状态；读取共享资源不改变 VPU 独立管理的边界。条件满足时移除队头 wait 并继续派发，否则保持等待。wait 不阻止此前已经派发的任务继续运行。

```text
endpoint   = GEMM / DMA / VPU
rs1[31:0] = event mask
aux[4:0]  = event group ID
rs2        = x0
```

满足条件为所选 event bit 全部为 1：

```text
(event[group] & rs1) == rs1
```

wait 只查询，不申请、不消费、不修改资源，也不产生任何 CPU 可见事件。资源存放在 Scheduler 不改变 wait 的执行位置：VPU 的 wait 由 VPU 后端自己执行。

event/timeline 的原子申请与消费由软件按 Queen 资源寄存器协议管理：软件通过 `QRD` 获取空闲资源，申请成功后使用 signal/wait。本 ISA 不定义资源分配寄存器地址、申请失败返回值或消费/回收协议。

---

## 9. `WAIT_TIMELINE`

`WAIT_TIMELINE` 与 `WAIT_EVENT` 使用相同的 target endpoint 语义：

- GEMM/DMA：在对应命令队列的最终派发处等待。
- VPU：在 VPU 后端派发队列等待，由 VPU 侧实现。
- `endpoint=SCHED`：非法编码，处理方式同第 8 节。

timeline 资源由 Scheduler 集中管理，各 wait 执行单元通过同步资源查询接口获取值；等待只阻塞第 8 节规定的后续派发范围，不申请或消费 timeline，也不阻止已派发任务继续执行。

```text
endpoint   = GEMM / DMA / VPU
rs1[31:0] = target sequence
aux[2:0]  = timeline ID
aux[4:3]  = 0
rs2        = x0
```

wait 满足后只解除后续派发阻塞，不额外申请或消费 timeline，也不通知 CPU。

Scheduler 保存 8 个 32-bit 单调 timeline。等待条件为：

```text
(int32_t)(completed[timelineId] - targetSequence) >= 0
```

资源由软件通过 Queen 资源寄存器申请，申请成功后的 signal/wait 使用上述 timeline ID。单个 context 内最大在途距离必须小于 `2^31`。普通命令禁止清零 timeline；context reset 必须确认引用该资源的 GEMM/DMA/VPU 等待、执行和信号更新均已退出，不能只检查 Scheduler 暂存空闲。

软件不得在被 wait 阻塞的同一派发路径后方安排其唯一解除条件，否则会形成队头死锁。

---

## 10. `SIGNAL_TIMELINE`

`SIGNAL_TIMELINE` 不包含可选 target endpoint 语义；`endpoint` 固定为 SCHED，命令进入 Scheduler 暂存并更新 Scheduler timeline 资源。

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

硬件完成源只能自增 timeline，不能赋值或清除。软件显式赋值与硬件自增不得指向同一 timeline；软件自增可以与硬件自增并存，同一时钟沿的多次自增按次数累加。timeline 的分配协议必须把这条约束包含进去。

signal 入队或 CPU 指令退休不等于所代表的 DSA 工作完成。若软件用该 signal 表示 GEMM/DMA 完成，必须确保相应的 idle fence 在 signal 更新之前已经满足；不能仅因配置指令在 CPU 侧已退休而提前发布完成值。改用硬件完成源时，发布时机由该完成点的定义决定，可见性口径见 Queen 硬件规格；若该完成点不保证写可见，软件仍需在提交前使用 fence。

---

## 11. `SIGNAL_EVENT`

`SIGNAL_EVENT` 不包含可选 target endpoint 语义；`endpoint` 固定为 SCHED，命令进入 Scheduler 暂存并更新 Scheduler event 资源。

```text
endpoint   = SCHED
rs1[31:0] = event mask
aux[4:0]  = event group ID
rs2        = x0
```

- `flag=1`：置位所选 event bit。
- `flag=0`：清除所选 event bit。

置位/清除只更新已由软件申请的资源状态，不构成资源申请、原子消费或回收指令。若 signal 表示工作完成，软件必须保证其发布时机满足对应完成依赖，与第 10 节相同。

硬件完成源对 event 只能置位，不能清除。同一 event bit 的软件置位、软件清除与硬件置位在硬件规格规定的提交顺序下生效；同一时钟沿的多个置位取或。

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
- `endpoint=SCHED`：进入 Scheduler 暂存，在 Scheduler 实际执行点记录。

---

## 13. `POLL_EQ`

`POLL_EQ` 的 `endpoint` 固定为 SCHED：

```text
rs1[31:0] = 4-byte aligned polling address
rs2[31:0] = expected value
aux        = 0
```

**完成语义（已裁定）**：`POLL_EQ` **不产生 CPU 可见完成通知**。它阻塞 SCHED 派发点直到读值相等，效果只体现为后续指令的顺序——与 `WAIT_EVENT` 同构。CPU 需要感知时读内存本身，这正是 polling 的语义。

Scheduler polling 间隔 N 通过 SCHED `0x02 POLL_INTERVAL` 设置，复位默认值为 8 cycle，写 0 非法（报 `wrErr` 并保持原值）。N 为正整数，不在指令编码中携带。

每 N cycle 最多发起一次 32-bit polling 读取；实际发起还受总线 ready 和内存主接口在途容量（`memDepth`，默认 8）限制。读值等于 `rs2` 时命令完成并放行后续，否则等满 N 拍再读。地址未 4-byte 对齐时不发请求，直接产生 kind=3/code=1 的 fault 并消费该命令。总线错误产生 kind=3/code=0 的 fault。

**软件义务**：`POLL_EQ` 挡住 SCHED 派发点，所以清 fault 的 `QWR 0x00` 不得排在未完成的 `POLL_EQ` 之后。硬件不加旁路。

V2 基础指令不携带 compare mask；需要 masked compare 时通过 Scheduler 配置寄存器扩展。超时机制尚未定义，待后续裁定。

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
| `2'b10` | `SET`: `new = old \| operand` |
| `2'b11` | Reserved |

Scheduler 必须通过具备原子语义的总线事务实现；若底层互连不支持与外部 CPU/cache 一致的 atomic，则该能力只能用于明确标记为 non-cacheable 且由 Scheduler 独占原子访问的地址区间。

**完成语义（已裁定）**：`ATOMIC_RMW` **发出即放行**，不等待响应，也不产生 CPU 可见完成通知；效果只是内存副作用。最多 `memDepth`（默认 8）笔在途。总线按请求序返回响应，因此排在后续 `POLL_EQ` 之前的 `ATOMIC_RMW` 必然先落地，顺序无需额外握手。

地址未对齐时不发请求，直接产生 kind=3/code=1 的 fault 并消费该命令；总线错误产生 kind=3/code=0 的异步 fault。V2 基础指令不返回 old value。

---

## 15. CPU 完成感知

V2 **不提供 Queen 侧完成通知机制**：没有 notify 位、没有 notify pulse、没有 `NOTIFY_COUNT/ACK/PENDING/ENABLE`。`inst[25]` 除 `SIGNAL_EVENT` 的 set/clr 外一律为保留位。

CPU 感知后端完成有两条通路：

| 通路 | 用途 | 机制 |
| :--- | :--- | :--- |
| 设备自己的中断 | 单个引擎的工作完成 | 设备在产生 completion 的同一时刻置自己的 `INT_RAW`（设备地址 `0x60` 块）并拉 `irq`；CPU 读 `INT_STATUS` 判来源、写 1 清除 |
| 同步资源 | 跨引擎条件 | 硬件完成源置位 event / 自增 timeline；`WAIT_EVENT/WAIT_TIMELINE` 在硬件里阻塞派发，CPU 不参与；需要 CPU 可见时用 `QRD endpoint=SCHED` 读同步状态 |

删除理由：设备本来就知道自己何时完成，Queen 再复制一套四来源计数（4×32 COUNT + 4×32 ACK）是重复机制；而 `FENCE/WAIT` 是派发屏障，其完成对 CPU 本应透明。跨引擎条件由软件组合各设备中断，或用 event/timeline 表达后交给硬件等待。

约束：

- `FENCE/WAIT_*` 只阻塞本端点后续派发，不产生任何 CPU 可见事件。软件不得把"指令已退休"当作设备工作已完成。
- 设备中断是电平 + sticky 位，同一位在软件读走前多次完成会合并；需要计数时用设备私有的完成计数寄存器，Queen 不提供。
- **`POLL_EQ`/`ATOMIC_RMW` 不产生 CPU 可见完成通知**（已裁定，见第 13/14 章）：`POLL_EQ` 阻塞 SCHED 派发点，`ATOMIC_RMW` 发出即放行。CPU 需要感知时读内存本身。
- 同步状态的软件读通路已落地：`QRD endpoint=SCHED` 读 `0x02 POLL_INTERVAL`、`0x40..0x5F EVENT[0..31]`、`0x60..0x67 TIMELINE[0..7]`、`0x68..0x6B FAULT_VALID/KIND/ENDPOINT/CODE`、`0x6C IDLE`；`QWR1 endpoint=SCHED, 0x00` 清 fault、`0x01` 触发恢复。完整映射见 `queenHarewareInterface.md` 第 18 章。

---

## 16. 软硬件握手与异常

### 16.1 CPU 请求与退休

- CPU 顺序发出 Queen ISA 请求，Scalar Core decoder 按 endpoint 路由到 Queen 或 VPU 前端。请求握手时，对应前端锁存指令和 PC，并为完整请求及所需源操作数预留 entry；CPU 在约定的下一周期返回源 GPR 值，接收前端将其锁存到对应 entry，后续执行不再读取 CPU GPR。
- 除 `QRD` 外，`QWR1/QWR2/QCTRL` 都是异步操作，给到对应前端接收队列并成功入队后即可退休。VPU 目标指令由 Scalar Core decoder 直接送入 VPU 独立前端，不进入 Queen，也不表示由 Scheduler 管理 VPU。
- 配置写与 `RUN` 都在前端入队后退休，不等待设备应用配置、启动执行或设备 idle。
- fence/wait/polling 指令同样在前端入队后退休，其等待/比较条件在对应后端执行。CPU 可继续发送后续指令，直至前端队列背压。
- fence/wait 完成不产生 CPU 可见事件；设备工作完成由设备自己的中断上报。
- `QRD` 必须等待顺序返回的数据或异常后才能退休。读响应只对应读请求，接口不要求 transaction ID。

### 16.2 Backpressure

- 前端没有空间保存完整请求及操作数时，必须拉低请求 ready，未接受的指令不得退休。
- DSA 命令队列满、wait/fence 未满足或设备配置 credit 不足，只阻塞对应后端推进，不撤销此前已接受指令的退休。
- 后端拥塞导致前端缓存也满时，才将背压传递到 CPU。
- `QRD` 请求和响应必须按序；没有读处理/响应缓存容量时暂停接受新的读请求。

当 response valid 为 1 而 ready 为 0 时，必须保持响应 valid、数据及错误状态不变，直到响应握手成功，不得丢失响应。

### 16.3 异常与异步错误

以下情况产生同步非法指令异常：

- opcode/funct3/cmd 为保留编码；
- V2 要求为 0 的保留字段非 0；
- `QWR2.regBase=63`；
- `RUN` 的 endpoint 不是 GEMM/DMA；
- `RUN` 之前没有配置写（空配置组）；
- `SIGNAL_TIMELINE/SIGNAL_EVENT/POLL_EQ/ATOMIC_RMW/FLUSH` 的 endpoint 不是 SCHED；
- Queen `FENCE` 的 endpoint 为 VPU 或 SCHED；
- `WAIT_EVENT/WAIT_TIMELINE` 的 endpoint 为 SCHED；
- `SIGNAL_TIMELINE/TRACE_TAG/ATOMIC_RMW` 的 `inst[25]` 非 0；
- 前端可判定的保留编码与字段违规（寄存器地址合法性在执行期判定，走 `wrErr`/读错误响应，不在前端 trap）。

`QCTRL.cmd=4'b0111` 为保留编码，无论 endpoint、bit25 或操作数字段为何值，都必须在前端报非法指令。

只有 `SIGNAL_EVENT` 的 `inst[25]` 承载语义（set/clr，两种取值均合法）；其余全部 cmd 的该位为保留位，非 0 报非法指令。

同步编码错误必须在前端接受/退休之前检测。只有后端才能判定的寄存器访问或执行错误，不能回溯撤销已退休异步指令，必须走异步 fault；`QRD` 的访问错误通过按序读响应返回，并在退休时以 `qrdRetire.trap=1`、cause 9（`rdError`）上报——该 cause 不占用前端同步 trap 通路。

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
               uint32_t value0, uint32_t value1);
void queen_wr1(unsigned endpoint, unsigned reg_addr, uint32_t value);
uint32_t queen_rd(unsigned endpoint, unsigned reg_addr);
void queen_run(unsigned endpoint, uint32_t cmd_tag, uint32_t sw_tag);

void queen_fence(unsigned endpoint);
void queen_wait_event(unsigned endpoint, unsigned group, uint32_t mask);
void queen_wait_timeline(unsigned endpoint, unsigned timeline, uint32_t sequence);
void queen_signal_timeline_next(unsigned timeline);
void queen_signal_timeline_value(unsigned timeline, uint32_t sequence);
void queen_signal_event(unsigned group, uint32_t mask, bool set);
void queen_trace(unsigned endpoint, uint32_t tag);
void queen_poll_eq(uint32_t address, uint32_t expected);
void queen_atomic_rmw(uint32_t address, uint32_t operand, unsigned op);

```

`queen_run` 发出 `QCTRL RUN`，`cmd_tag` 取低 8 位、`sw_tag` 原样透传，两者为 0 表示不使用。

`queen_fence/queen_wait_event/queen_wait_timeline/queen_poll_eq` 各发出一条异步 ISA，只阻塞后端派发，不通知 CPU。`queen_signal_event` 的 `set` 参数编码到 `inst[25]`，是该位唯一的语义。

完成感知由设备中断处理封装提供，不属于 Queen ISA；同步状态的软件读写走 `QRD/QWR1 endpoint=SCHED`。

封装必须声明为 `volatile`，并根据命令是否访问内存插入必要的 compiler barrier。CPU 与 Queen 共享内存但不具备硬件 cache coherence 时，软件还必须在提交前执行平台规定的 cache clean/invalidate。

---

## 18. V2 最小验证要求

1. 为每条指令提供 encode/decode 十六进制测试向量。
2. 验证全部保留编码在前端退休前触发非法指令。
3. 验证 `QWR2` 相邻地址与 6-bit `regBase` 拼接（含 `inst[11]=1` 的高位地址）、`regBase=63` 异常。
4. 随机重排同一配置组内不同 DSA 配置寄存器的写入顺序，设备最终看到的配置必须一致；`RUN` 不得越过前序写入。
5. 验证 GEMM/DMA 本地 fence 在设备 idle 后才继续目标队列派发，非目标队列不受阻塞。
6. 验证 `FENCE/WAIT_EVENT/WAIT_TIMELINE` 的 `endpoint=SCHED` 在前端报非法指令，Scheduler 派发路径不出现条件等待。
7. 验证 Queen 配置/控制指令前端入队即退休；RISC-V `FENCE` 不因已退休异步命令的 DSA/VPU 尚未 idle 而额外等待。
8. 验证 wait endpoint 路由：GEMM/DMA 命令队列与 VPU 后端派发队列分别执行等待，SCHED 为非法编码。
9. 验证 `WAIT_EVENT` 只查询、不申请/消费/修改资源；资源申请由 Queen 寄存器协议处理，不在 wait ISA 中实现。
10. 验证 `SIGNAL_TIMELINE rs1=x0` 自增（按寄存器索引判定，不看读回值）、显式赋值、回绕比较、禁止回退和资源安全 reset。
11. 验证 TRACE_TAG 仅由 endpoint 选择 GEMM/DMA/VPU/SCHED 记录位置，flag 非 0 报非法指令。
12. 验证跨端点多个 `QRD` 延迟不同时仍按请求顺序返回，混入异步写/控制不影响响应配对，不需要 transaction ID。
13. 验证前端 queue full、后端阻塞、response backpressure 和异步 fault 不丢失。
14. 验证 polling 间隔复位为 8 cycle，写 SCHED `0x02` 后使用配置的 N cycle，写 0 报 `wrErr` 且保持原值。
15. 验证 polling/atomic 的地址对齐与总线错误路径（kind=3，code 1=未对齐、0=总线错误）；超时路径尚未定义。
16. 验证 `SIGNAL_EVENT` 的 bit25 两种取值均合法（set/clr）；其余全部 cmd 的 bit25 非 0 在前端报非法指令，且该检查先于空组 `RUN` 判定。
17. 验证 `QCTRL.cmd=4'b0111` 在全部 endpoint、bit25 两种取值及不同操作数字段下均在前端报非法指令，不入队。
18. 验证 `FENCE` 等目标设备强 idle、`WAIT_EVENT/WAIT_TIMELINE` 等同步条件，条件未满足时只停本端点队头，其后 lane 不越过，另一端点与 SCHED 不受影响。
19. 验证 `WAIT_TIMELINE` 用 signed 距离判定：计数回绕后仍能正确判满足，且不会把回绕误判为回退。
20. 验证 `SIGNAL_EVENT` 与硬件置位同拍冲突时逐位置位优先；`SIGNAL_TIMELINE` 软件自增与两端点硬件自增同拍按次数累加。
21. 验证 `SIGNAL_TIMELINE` 显式回退被抑制更新并报 fault（kind=2、code=timeline ID），同拍硬件自增仍生效。
22. 验证 `TRACE_TAG` 被消费但不产生记录（记录流未落地），不停等派发通路。
23. 验证 Queen 不产生任何完成通知 pulse：fence/wait 完成、RUN 完成均无 CPU 可见事件，设备中断是唯一的单引擎完成通路。
24. 验证 `POLL_EQ` 阻塞 SCHED 派发点直到读值相等、失配后等满 `POLL_INTERVAL` 才重读；`ATOMIC_RMW` 发出即放行、最多 `memDepth` 笔在途、按序响应保证它先于其后的 `POLL_EQ` 落地；两者均不产生 CPU 可见完成通知。
25. 验证设备写非法地址经 `wrErr` 进入 Queen fault（kind=1），同拍与 completion 错误竞争时按固定优先级取一条。
26. 验证 fault 只保留最新一条、`faultIrq` 电平保持到软件写 SCHED `0x00`、同拍新错误优先于清除。
27. 验证 `RUN` 关闭配置组并启动设备：`RUN` 之前设备不得执行，`RUN` 之后的 `QWR` 不得修改已启动配置。
28. 验证空配置组 `RUN`、`RUN endpoint=SCHED`、`RUN aux` 非 0 均在前端报非法指令；`rs1` 高 24 位非 0 不报非法，只取低 8 位作 `cmdTag`。
29. 验证流控只门控 `RUN`：`RUN` beat 的 ready 为 0 时纯写 beat 仍可接受与转发、`RUN` 停等而非报错；`QRD` 只被 Queen 读响应缓存反压；设备按其声明的归还点返还 credit 后 `RUN` 恢复派发。
30. 验证 `RUN` 的 `cmd_tag/sw_tag` 原样透传到设备命令，并由设备在 `start` 当拍采样后随 completion 回传。
31. 验证 `QWR1` 的 `inst[11]` 非 0 报非法指令；`QWR2` 的 `inst[11]` 作为地址高位不报非法。
32. 验证 `FLUSH`（cmd=10）、写 SCHED `0x01 RECOVER`、CPU `flushReq` 三个触发源走同一条恢复流程，效果一致。
33. 验证两侧四相握手：`req` 拉起后必须等 `ack` 拉起才落下，`ack` 再随 `req` 落下；设备侧两路 `flushReq/flushAck` 都走完才进入排空。
34. 验证 flush 期间前端接受并丢弃（合法指令照常 fire、不入队、不派发），在途与新收 `QRD` 一律回 `rdValid=1/data=0xdeadbeef/trap=0`，迟到 completion 不置同步状态也不记 fault。
35. 验证 `flushAck` 只在内部全排空、两设备 `deviceIdle` 且连续 `flushQuietCycles` 拍无扰动后拉起；静默窗内 CPU 再发指令须重新计数；`flushReq` 有效期间 Queen 全程停在 flush 态（`idle=0`、新 QRD 仍回 dummy）；完成后 event/timeline/fault/openGroup 清零而 `POLL_INTERVAL` 保持。
