# HiveCore 硬件规格

文档版本：`v3.0.0`

修订日期：`2026-10-23`

## 版本控制

### 文档版本规则

文档采用 `v主版本.次版本.修订号`：

- 主版本：外部可见规格契约发生不兼容变更；
- 次版本：增加兼容的功能描述或规格章节，不改变既有功能契约；
- 修订号：修正文案、排版或引用，不改变功能契约。

版本号仅标识本文档修订，不代表 RTL 发布版本、寄存器值或芯片型号。每次发布文档修订必须同步更新首页版本/日期及下表，日期使用 `YYYY-MM-DD`；若修改影响功能契约，还应记录受影响的 Feature ID。Feature ID 按第 3.1 节保持稳定。

### 修订记录

从 v1.0.0 开始显式记录文档版本；此前未编号文档不追溯编造版本号或发布日期。

| 版本号 | 日期 | 修改内容 | 相关 Feature ID |
|---|---|---|---|
| v3.0.0 | 2026-10-23 | 不兼容端口变更：固定四路 HiveCoreCmd Stream 共享单一寄存器 bank，单 resp 新增 2-bit laneId；采用四 slots/pending 位图的单批响应缓存、拍前读快照与逐地址跨 lane 写仲裁，START 合并为下一拍一次启动并使用胜出 lane 元数据；新增 IRQ_RAW[10] WRITE_CONFLICT，IRQ 低 11 bit 有效、复位 mask 为 0x000007FF；寄存器地址与 CONTROL 布局、数值、DMA、首次 tracing 配对限制不变。四路接口专项 23 例、既有计算/DMA 回归 51 例及测试编译通过 | HIVE-F021、HIVE-F022；HIVE-F025（仅 START 元数据选择） |
| v2.0.0 | 2026-10-23 | 不兼容重设计寄存器命令接口：采用 6-bit 索引和 START 寄存器，重映射配置与 STATUS；由独立 HiveCoreRegIf 管理在途保护、原子双写、分源粘滞中断及首个未清配置错误码；DMA 错误改为响应握手时立即捕获，tracing 元数据改取启动写命令；数值、布局、计算、DMA 搬运流程与性能事件口径不变 | HIVE-F021–HIVE-F023、HIVE-F025 |
| v1.1.0 | 2026-10-23 | 按通用硬件规格重组为 14 个编号章节，第 7 章为数据表示规范；补充分立的时序/复位说明与性能/资源口径；更新功能清单引用，功能 ID 与既有技术契约不变 | HIVE-F001–HIVE-F025（章节引用） |
| v1.0.1 | 2026-10-22 | 版本控制前置为文首章节；Feature List 调整为第 3 章；原第 3–14 章顺延为第 4–15 章，并同步更新章节引用；功能 ID 与技术契约不变 | HIVE-F001–HIVE-F025（仅章节引用） |
| v1.0.0 | 2026-10-22 | 建立首个编号规格基线，覆盖当前计算架构、格式、布局、接口、寄存器、DMA、epilogue、错误码和软件约束；新增带稳定 ID 的 Feature List 及版本控制章节 | HIVE-F001–HIVE-F025 |

## 1. 文档说明

### 1.1 目的

本文定义 HiveCore 的软件可见行为、计算语义、存储布局、接口协议和配置约束。本文档 v3.0.0 将 v2.0.0 的单路寄存器命令端口改为固定四路输入，并在单一响应端口新增 laneId，属于不兼容端口契约；寄存器地址映射与 CONTROL 字段不变。本文不记录 RTL 开发过程，也不承诺本规格之外的功能。带 ID 的 Feature List 见第 3 章；文档版本规则与修订记录见文首“版本控制”章节。

HIVE-F021、HIVE-F022 的四路接收、单批缓存、响应顺序、读快照、写仲裁、START 时序/元数据及 WRITE_CONFLICT/IRQ 扩展已实现并通过接口验证；既有计算与 DMA 回归通过。验证不扩大参数化配置的支持范围，也不改变 tracing 首次配对等既有限制。

### 1.2 范围

本文覆盖：

- HiveCore GEMM 阵列及其执行顺序；
- INT、FP8、MXFP8、MXFP4 数据格式；
- A、B、C、scale、bias 和 requant 参数的存储布局；
- 命令、响应、DMA、状态、中断和性能事件接口；
- 寄存器映射、运行期错误检查及软件编程模型。

本文不定义 Queen 指令编码、外部 Scheduler、ForagerBee、LLC、CPU cache 管理实现或系统物理地址映射。

### 1.3 规范性用语

- “必须”表示违反该要求会导致命令被拒绝、结果未定义或与本规格不一致。
- “应”表示集成或软件应遵循的推荐约束。
- “默认配置”指未显式覆盖 `HiveCoreConfig` 参数时的配置。
- `T` 表示阵列输出列数 `totalN = arrayN × clusterM`；默认 `T=16`。
- `Nlogical` 表示软件需要的有效列数，`Nprog` 表示写入 N 寄存器并按 T 补齐后的列数；除明确写出 `Nlogical` 外，后续公式中的 `N` 均指编程值 `Nprog`。
- DMA 与存储基地址均为字节地址；命令中的寄存器地址为索引，不是字节偏移。
- 除非另有说明，多字节数据采用低地址存放低有效字节的布局。

## 2. 模块概述

HiveCore 是面向矩阵乘法和量化推理的 weight-stationary GEMM 单元，计算：

```text
C = Epilogue(A × B + Bias)
```

A 的逻辑形状为 `M×K`，B 的有效形状为 `K×Nlogical`。由于硬件要求 N 维按阵列宽度补齐，实际编程和存储形状为 B=`K×Nprog`、C=`M×Nprog`。阵列以 K 和 N 作为空间轴，以 M 作为时间轴；每个 `(N tile, K pass)` 装载一次权重，并连续处理全部 M 行。

当前默认输入能力为：

- INT8、INT4；
- MXFP8 E4M3、MXFP8 E5M2；
- MXFP4 E2M1；
- 非 MX FP8 E4M3、FP8 E5M2。

当前实现还提供 INT32、INT16、FP32、FP16、BF16 和 MX 格式的输出转换路径。FP8 不支持作为 C 输出格式。

## 3. 功能清单（Feature List）

### 3.1 Feature ID 规则

Feature ID 使用 `HIVE-Fnnn`，是功能项的稳定标识，不是寄存器地址、指令操作码或错误码。新增功能使用未分配编号；既有 ID 不因章节移动或文案调整而改变，也不得复用。若功能被移除，后续版本保留该 ID 并标注“已移除”。

下表列出功能及验证状态。“已实现”不表示任意参数化配置均可用，也不消除对应章节的输入域、布局、时序或系统集成约束；详细章节为功能契约的依据。HIVE-F021、HIVE-F022 的四路接口与 IRQ 扩展、HIVE-F025 的胜出 START 元数据选择已通过验证；首次执行配对的既有限制不变。

### 3.2 功能清单

| Feature ID | 功能 | 状态 | 能力与主要约束 | 规格章节 |
|---|---|---|---|---|
| HIVE-F001 | Weight-stationary GEMM | 已实现 | T×T PE；K/N 为空间轴、M 为时间轴；循环为 N tile → K pass → M 行 | 4.1、5.1.1–5.1.2 |
| HIVE-F002 | 多 K pass 内部累加 | 已实现 | 首 pass 使用零或 bias 基底；后续回灌 C FIFO；仅末 pass 写回 | 5.1.3 |
| HIVE-F003 | INT8 打包乘加 | 已实现 | 默认每 lane 两元素、每拍覆盖 2T 个 K 元素；有符号累加，溢出绕回 | 5.1.1、5.2.3、7.2 |
| HIVE-F004 | INT4 打包乘加 | 已实现 | 默认每 lane 四元素、每拍覆盖 4T 个 K 元素；二补码 nibble 按递增 K 打包 | 5.1.1、5.2.3、7.2 |
| HIVE-F005 | MXFP8 计算 | 已实现 | E4M3/E5M2 可交叉组合；使用 E8M0 block scale；必须 loadWMode=1 | 5.2.1、5.2.4、5.2.6、7.4、7.6 |
| HIVE-F006 | MXFP4 计算 | 已实现 | E2M1 四元素打包；保留 ±0.5 次正规输入；必须 loadWMode=1 | 7.2、5.2.4–5.2.6、7.4、7.6 |
| HIVE-F007 | 非 MX FP8 计算 | 已实现 | E4M3/E5M2 可交叉组合；单位 scale 注入且不读取输入 scale；必须 loadWMode=1；不能输出 FP8 | 5.2.1–5.2.5 |
| HIVE-F008 | INT W4A8 | 已实现 | 仅 A=INT8、B=INT4；B 经 DMA stretch 符号扩展；默认 K%64=0、B_ADDR%32=0、loadWMode=1 | 5.2.1、5.3.2、10.2 |
| HIVE-F009 | MX W4A8 | 已实现 | 仅 A=MXFP8、B=MXFP4；B 经 DMA stretch 展开；ScaleB 按 A 的 K 窗口步进；默认 K%64=0、B_ADDR%32=0 | 5.2.1、7.6.2、5.3.2 |
| HIVE-F010 | A/B 独立 MX block scale | 已实现 | A/B 独立选择 block16/32；scale 按 K 分组并使用 E8M0 编码 | 5.2.6、7.4、7.6.1–7.6.2 |
| HIVE-F011 | 浮点/MX 输出转换 | 已实现 | FP32/FP16/BF16 及 MX 输出；支持 RNE/RTZ/RDN/RUP/RMM；内部归约不保证 IEEE 逐项 FP32 bit-exact | 7.1、7.3、5.2.4–5.2.5 |
| HIVE-F012 | MX 输出量化与 ScaleC 生成 | 已实现 | 每 block 生成 E8M0 scale；默认 C block 仅允许 16；ScaleC 在途/反压限制必须满足 | 7.4、7.6.3、5.2.7、9.6 |
| HIVE-F013 | 整数输出截断 | 已实现 | INT32/INT16/INT8/INT4；先取 acc32 再按输出位宽截断；非 requant 模式不饱和 | 5.2.3、5.4.1 |
| HIVE-F014 | Per-channel fused bias | 已实现 | INT32 或 FP32 bias；每输出列一项；仅首 K pass 注入；需要参数取数 DMA | 7.7.1、5.4.2 |
| HIVE-F015 | Fused ReLU | 已实现 | 只作用于有符号 acc32，在 requant/最终整数截断前钳零；非整数输出忽略使能 | 5.4.1、5.4.3 |
| HIVE-F016 | Fused requant | 已实现 | Per-channel 有符号乘数、全命令 shift/zero-point；输入为 acc32/relu32；half-up 舍入后饱和至 INT8/INT4；需要参数取数 DMA | 7.7.2、8.7、5.4.4 |
| HIVE-F017 | B 权重装载模式 | 已实现 | mode0 对应 K×Nprog row-major；mode1 对应预转置 Nprog×K row-major；格式约束决定可用模式 | 5.1.2、7.5.2 |
| HIVE-F018 | 自主 A/B/C DMA | 已实现 | 按 N/K 窗口遍历；A 每 N tile 重读；C 按完整 T 列写回，软件必须编程 Nprog | 7.5、5.3、9.3–9.4 |
| HIVE-F019 | 非对齐读 Gather | 已实现 | A/B/ScaleA/ScaleB 非对齐逻辑拍由对齐物理读拼接；相邻覆盖地址必须可访问；W4A8 stretch 使用独立半片选择路径 | 5.3.2–5.3.3 |
| HIVE-F020 | DMA 握手与读 FIFO credit | 已实现 | 请求/响应 ready-valid；读请求预留 credit；读响应必须同序且每请求恰有一个响应；ScaleC 另有在途限制 | 6.4、9.3–9.4、9.6 |
| HIVE-F021 | 寄存器命令接口 | 已实现 | 固定四路 REG_WRITE1/REG_WRITE2/REG_READ 共享一个 bank；6-bit 索引，稀疏混合读写同拍 fire；四 slots/pending 单批缓存，单 resp 按 lane 递增返回 data/err/laneId；拍前读快照、逐命令检查与逐地址 lane3>2>1>0 仲裁；非零 START 合并为延后一拍一次启动；ACK 不表示计算完成 | 4.2、6.1–6.3、8、9.2 |
| HIVE-F022 | 状态与完成/错误中断 | 已实现 | busy、inFlight、progress 不变；分源 IRQ_RAW/MASK/MASKED/CLEAR 低 11 bit；新增粘滞 WRITE_CONFLICT[10]，fresh event 优先于 W1C；DMA 响应握手错误逐事件捕获，不等待 done；完成不表示无错误 | 6.5–6.6、8.6、8.8、11.1–11.2、10.5、5.1.5 |
| HIVE-F023 | 运行期配置检查 | 已实现 | 判据与优先级不变；IRQ_RAW[1] 标记配置错误，ERROR_CODE 保存首个未清配置错误码；M/N 非零、Nprog、缓冲区可访问性等仍是软件义务 | 8.8、10 |
| HIVE-F024 | 性能事件输出 | 已实现 | 输出组合事件电平，不含计数器；DMA 事件为 req.valid 而非 req.fire | 11.3 |
| HIVE-F025 | 首次执行 Tracing | 已实现 | 复位后首次有效非零 START 的 PC/tag 与 start、done/configuration-error 事件；跨 lane 取最高非零 START lane 的命令元数据（此选择契约已验证）；启动脉冲延后一拍，不保证后续任务配对 | 6.3、9.2、11.4 |

### 3.3 支持边界

功能清单只索引本规格覆盖的能力；不支持项与实现限制见第 14 章。字段可读写不等价于存在相应数据通路功能。

## 4. 架构说明

### 4.1 阵列组织

阵列包含 `T×T` 个 PE：

- x 方向对应 K-lane；
- y 方向对应输出 N 列；
- A 数据及 ScaleA 沿 y 方向传播；
- partial sum 沿 x 方向归约；
- B 权重锁存在 PE 内。

默认 `T=16`。每个阵列有效计算拍同时产生 T 个输出列的部分结果。

### 4.2 子模块与数据通路

| 子模块 | 职责 |
|---|---|
| HiveCoreRegIf | 独立管理固定四路命令共享的一个配置/状态 bank、四 slots/pending 单批响应缓存、逐命令译码检查与逐地址仲裁、单 resp 的 laneId/顺序、start 及胜出 PC/tag、inFlight、完成/错误/WRITE_CONFLICT 事件汇聚、IRQ_RAW/MASK/CLEAR、ERROR_CODE 与 irq（四路相关契约已验证） |
| CalcConfig | 检查运行配置并派生执行几何与地址步长 |
| Executor | 控制权重装载、A 行消费、K pass 回灌和最终排空 |
| Scratchpad | 提供 A、B、C/partial-sum FIFO |
| HiveComb | 完成权重驻留乘加与空间归约 |
| A/B 读 DMA、C 写 DMA | 搬运矩阵数据 |
| Scale 读写 DMA | 搬运输入/输出 scale；ScaleA 物理口另供 bias/rqM 取数 |
| HiveOutCvt | 完成输出格式转换、整数 ReLU 与 requant |

顶层向 HiveCoreRegIf 提供 Executor `done`、CalcConfig `errPluse` 及各 DMA 的 `rsp.fire && rsp.err` 分源事件；配置错误事件同时提供 CalcConfig 的错误码。Executor busy/progress 接入 HiveCoreRegIf 供 STATUS 读取，FIFO occupancy 由顶层直接输出到 `io.status`。共享 ScaleA 物理口的响应错误按仲裁器当前响应 owner 归类为 ScaleA、Bias 或 RqM；WAIT 状态仍归 ScaleA，具体映射见第 10.5 节。

HiveCoreRegIf 是寄存器与中断状态的所有者；顶层不得等到 Executor done 才采样 DMA 本地错误锁存状态。计算、DMA 请求/响应协议、数据布局和 perf 事件口径不因这一职责拆分改变。

功能执行语义见第 5 章；FIFO 深度和编译条件见第 8 章。

## 5. 功能行为规范

### 5.1 GEMM 执行

#### 5.1.1 循环顺序与执行几何

硬件执行顺序固定为：

```text
for nt in N tiles:
  for kt in K passes:
    load B tile
    for m in 0 until M:
      consume one packed A row beat
```

因此外层循环为 N，中层为 K，M 作为每个权重窗口内的连续时间流。

```text
nTiles = ceil(N / T)
kPerBeat = T × elemsPerLane(aFmt)
kTiles = K / kPerBeat       // 当前默认输入格式要求整除
```

其中：

| A 格式 | `elemsPerLane` | 默认 T=16 时 `kPerBeat` |
|---|---:|---:|
| INT4、MXFP4 | 4 | 64 |
| INT8、MXFP8、FP8 | 2 | 32 |
| 其它单元素格式 | 1 | 16 |

M 不在 Executor 内再次切 tile。单次命令必须满足：

```text
M ≤ cBufferDepth - T
```

默认 `cBufferDepth=64、T=16` 时，单命令 `M≤48`。软件可通过多条命令对更大的 M 分块。

#### 5.1.2 权重驻留与装载

每个 `(nt,kt)` 先消费 T 个 B 拍完成权重装载，然后连续消费 M 个 A 拍。相同权重在该窗口内供全部 M 行复用。

- `loadWMode=0`：B 通过垂直 partial-sum 链装载；
- `loadWMode=1`：B 通过水平 A 链移位装载，并在最后一拍锁存。

MX、FP8 以及正向 INT W4A8 必须使用 `loadWMode=1`。

#### 5.1.3 多 K Pass 累加

- `kt=0`：partial-sum 基底为 0，或在开启 bias 时为对应输出列的 bias；
- `kt>0`：上一 K pass 的结果从 C FIFO 回灌；
- 只有最后一个 K pass 的结果进入输出转换和 C DMA；
- 中间 K pass 结果只在内部 C FIFO 中周转，不写回外部存储器。

#### 5.1.4 执行状态机

Executor 包含五个状态：

```text
IDLE -> DRAIN -> LOAD_B -> LOAD_A -> NEXT_NK_TILE -> ... -> DRAIN -> IDLE
```

##### IDLE

- 清除运行游标；
- 计算 `nTiles` 和 `kTiles`；
- 本批合法非零 START 经 HiveCoreRegIf 仲裁产生一次 start（四路 START 契约已验证）、通过 CalcConfig 检查后，flush A/B/C 工作 FIFO；
- 设置首个窗口待执行并进入 DRAIN。

##### DRAIN

- 等待上一波前排空；
- 等待 B FIFO 至少有 T 拍；
- MX B 还要求 ScaleB FIFO 至少有 T 拍；
- 满足条件后进入 LOAD_B；
- 最终窗口完成后，等待阵列输出无效且 C DMA 空闲，再产生 done 脉冲。

##### LOAD_B

- 每次 `bPop.fire` 消费一个 B 拍；
- 连续 T 个有效拍完成权重装载；
- 最后一拍锁存权重并进入 LOAD_A。

##### LOAD_A

- `aPop.fire` 时阵列消费一个 M 行拍；
- `curM` 只在真实 fire 时递增；
- 消费第 M 个有效拍后进入 NEXT_NK_TILE。

##### NEXT_NK_TILE

- 有下一 K pass：保存 partial sum，K 游标加一；
- 当前 N tile 的最后 K pass：打开最终写回门；
- 有下一 N tile：N 游标加一；
- 无下一 tile：转入最终排空。

#### 5.1.5 完成行为

完成条件为：

```text
无下一窗口
AND 阵列无有效输出
AND C DMA 不忙
```

满足时：

- Executor `done` 输出一拍脉冲；
- HiveCoreRegIf 清除 `inFlight`，并置位粘滞 `IRQ_RAW[0]`；
- 该完成源是否使 `irq` 置高取决于 `IRQ_MASK[0]`，`irq` 的完整表达式见第 11.2 节；
- 若这是复位后的首次执行，tracing 输出 done 事件；
- Executor 返回 IDLE。

完成事件表示 Executor 到达上述排空条件，不代表任务无错误，也不自动清除任何错误源。DMA 错误 IRQ 可先于完成出现，不终止正在执行的任务；软件必须区分错误事件与完成事件，见第 10.5、13.1 节。配置检查拒绝正常 GEMM 时，`inFlight` 由配置错误脉冲清除，不以 `IRQ_RAW[0]` 为该路径的结束依据。

### 5.2 数值处理

#### 5.2.1 合法 A/B 组合

A 和 B 必须属于同一计算族：

- 整数与非整数不得混合；
- MX 与非 MX 不得混合；
- FP8 与非 FP8 不得混合。

当前合法组合包括：

| A | B | 状态 |
|---|---|---|
| INT8 | INT8 | 支持 |
| INT4 | INT4 | 支持 |
| INT8 | INT4 | 支持，正向 INT W4A8 |
| INT4 | INT8 | 不支持 |
| MXFP8 E4M3/E5M2 | MXFP8 E4M3/E5M2 | 支持，E4M3/E5M2 可交叉 |
| MXFP4 E2M1 | MXFP4 E2M1 | 支持 |
| MXFP8 E4M3/E5M2 | MXFP4 E2M1 | 支持，正向 MX W4A8 |
| MXFP4 E2M1 | MXFP8 E4M3/E5M2 | 不支持 |
| FP8 E4M3/E5M2 | FP8 E4M3/E5M2 | 支持，E4M3/E5M2 可交叉 |

正向 W4A8 的 A 为 8-bit 激活、B 为 4-bit 权重。

#### 5.2.2 输出族约束

- 整数计算必须输出整数族格式；
- 非整数计算必须输出非整数族格式；
- FP8 不能作为 C 输出格式；
- MX 输出会同时产生 C 数据和 C scale；
- 非 MX 输出不得依赖 C scale。

#### 5.2.3 整数计算

INT8 和 INT4 输入均先符号扩展，再做二补码乘加。整数累加按配置的累加位宽保留低位，溢出绕回。

未开启 requant 时：

- INT32、INT16、INT8、INT4 均先从累加槽取低 32 位形成 `acc32`；`reluOn=1` 时将负 `acc32` 钳为零，再按 C 格式截取低位；
- 不执行饱和；
- `rnd` 字段不影响该路径。

开启 requant 时只允许输出 INT8 或 INT4，结果执行舍入、加 zero-point 和有符号饱和。

#### 5.2.4 FP8 与 MX 计算

非 MX FP8 在输入端复用 MXFP8 数据通路，硬件注入 E8M0 scale `127` 作为单位缩放。FP8 运行仍必须设置 `loadWMode=1`，但不从 ScaleA/ScaleB 存储器读取输入 scale。

MX 输入使用 E8M0 block scale。MXFP8 乘法器有效尾数宽度为 4 bit，MXFP4 为 2 bit。MX 乘法截断位结构上为零，乘法阶段不产生额外舍入。

内部浮点/MX 累加采用 40-bit lazy 表示：

```text
{sign[1], exponent[8], mantissa[31]}
```

默认归约器为窄化多操作数加法器，不应视为 IEEE-754 逐操作 bit-exact 累加。规格只保证硬件定义的归约结果和输出舍入，不保证与软件逐项 FP32 累加完全一致。

#### 5.2.5 特殊值处理

当前转换采用以下硬件语义：

- MXFP8 和 FP8 次正规输入冲刷为零；
- MXFP4 E2M1 的 `±0.5` 次正规值保留，且该格式没有 Inf/NaN 编码；
- MXFP8/FP8 的 A、B 元素在有效计算拍出现相应 Inf/NaN 保留码会触发硬件断言；
- FP16/BF16（若编译启用）及非整数 FP32 bias 不提供 IEEE Inf/NaN 语义，且 bias 不触发上述断言；软件必须提供有限值；
- E8M0 输入 scale 的 `0xFF` 是 NaN 保留码，软件不得使用；RTL 不对此值产生配置错误或断言；
- 有限输入导致的指数上溢饱和到最大有限指数；
- MX 输出上溢饱和，下溢通常冲刷为零；E2M1 输出额外支持次正规生成。

MXFP4 E2M1 的 `±0.5` 是受支持输入。MXFP8/FP8 元素的 Inf/NaN 保留码、FP16/BF16 输入的 Inf/NaN（若编译启用）、非有限 FP32 bias 以及 E8M0 `0xFF` 不属于受支持输入域；关闭断言后对这些不受支持值的退化结果不构成规范行为。

#### 5.2.6 MX 输入 Scale 选择

每个 MX block 对应一个 8-bit E8M0 scale。ScaleA 和 ScaleB 各使用独立的 32-bit DMA 读口。

Executor 对一拍内各 lane 使用：

```text
scaleIndex = floor(lane × step / blockSize)
```

其中：

- MXFP8：`step=2`；
- MXFP4：`step=4`；
- 正向 MX W4A8 的 B：按 A 的窗口节奏使用 `step=2`。

#### 5.2.7 MX 输出 Scale 生成

每个输出 block 产生一个 E8M0 scale byte。零 block 输出 scale 0；非零 block 根据 block 最大幅值计算并限制在有效 E8M0 范围内。

### 5.3 数据搬运

#### 5.3.1 A 读取

A DMA 遍历顺序为 N tile、K pass、M 行。每个 N tile 都重新读取完整 A。逻辑地址为：

```text
A_BASE + kt × aKStride + m × aRowStride
```

具体 stride 根据格式的元素位宽和 `kPerBeat` 派生。

#### 5.3.2 B 读取与 W4A8 Stretch

B DMA 遍历 N tile 和 K pass。每个窗口读取 T 个逻辑拍并按阵列装载顺序送入 Executor。

W4A8 使用 stretch 读取：一个 32-byte 对齐物理字包含两个 16-byte 4-bit 半片，硬件根据 K 窗口选择低半或高半。软件必须满足对应的 B 基址对齐要求。

#### 5.3.3 非对齐 Gather

A、B、ScaleA 和 ScaleB 读 DMA 支持非对齐逻辑地址：

1. 将逻辑地址向下对齐到端口字宽；
2. 必要时读取当前字和下一字；
3. 拼接并右移，形成从原逻辑地址开始的完整逻辑拍。

每个逻辑拍只占用一个目标 FIFO credit。外部存储必须允许硬件访问 gather 覆盖的两个对齐字。32-bit scale 读取还可能触及当前计算不使用的相邻字节，这些字节也必须可安全读取。

ScaleA/ScaleB DMA 支持任意字节地址的 32-bit 逻辑读取。非对齐请求由硬件拆成相邻对齐物理读，并在内部拼接。因此软件不需要把每次逻辑 scale 地址强制对齐到 4 byte，但必须保证两个对齐物理读覆盖的地址均可访问。

#### 5.3.4 C 写回

C DMA 按 N tile、M 行写回。默认 `cDmaExtW=256`：

| C 格式位宽 | 每个 T 列行的有效字节 | 写拍数 |
|---:|---:|---:|
| 4 | 8 | 1 |
| 8 | 16 | 1 |
| 16 | 32 | 1 |
| 32 | 64 | 2 |

`data` 和 `strb` 使用同一个 `addr mod dmaBytes` 左移量。C 基地址必须按 `cDmaExtW/8` 对齐；默认要求 32-byte 对齐。

### 5.4 Epilogue

#### 5.4.1 执行顺序

逻辑顺序为：

```text
accWide = A × B + bias                          // 内部累加宽度
acc32   = signed(accWide mod 2^32)              // INT epilogue 固定先取低32位
relu32  = max(acc32, 0)                         // reluOn，否则等于acc32
q       = round(relu32 × M / 2^shift) + zeroPoint
out     = saturate(q)                           // requantOn
out     = truncate(acc32/relu32)                // requantOn=0 的整数输出
```

非整数输出不经过 `acc32`、ReLU 或 requant。

#### 5.4.2 Bias

Bias 每个输出列一个值，只在 `kt=0` 注入。后续 K pass 回灌的 partial sum 已包含 bias，因此不会重复相加。

- 整数路径：bias 按 INT32 符号扩展；
- 非整数路径：bias 按 FP32 解码后转为内部 lazy 格式；FP32 指数为 0 的值按零处理。

`biasOn=1` 时：

- `BIAS_ADDR` 必须 4-byte 对齐；
- 编译配置必须包含用于参数取数的 MX/scale DMA 硬件。

#### 5.4.3 ReLU

ReLU 只作用于整数输出路径。硬件先把宽累加槽截为有符号 `acc32`，再在 requant 或最终整数截断前把负 `acc32` 钳为零。非整数输出忽略 `reluOn`。

#### 5.4.4 Requant

Requant 只允许 INT8 或 INT4 输出。其输入是已经截为低 32 位并按 `reluOn` 可选钳零的 `acc32/relu32`，不是完整宽累加值。每个输出列使用独立有符号 32-bit 乘数 `M[n]`，全命令共享 `shift` 和有符号 16-bit `zeroPoint`。

当 `shift>0` 时：

```text
product = relu32 × M[n]
rounded = ((product >> (shift-1)) + 1) >> 1
```

随后：

```text
shifted = rounded + zeroPoint
```

最后饱和到：

- INT8：`[-128, 127]`；
- INT4：`[-8, 7]`。

当 `shift=0` 时不执行右移舍入。

`requantOn=1` 时：

- C 格式必须为 INT8 或 INT4；
- `RQ_M_ADDR` 必须 4-byte 对齐；
- 编译配置必须包含参数取数 DMA。

## 6. 接口规范

### 6.1 命令接口

本节四路接收、读快照、写仲裁与 START 契约已验证。

`cmd[0]`、`cmd[1]`、`cmd[2]`、`cmd[3]` 为固定四路、各自具有 ready/valid 握手的 `Stream[HiveCoreCmd]` 输入，由 HiveCoreRegIf 处理。四路共享一个配置/状态/IRQ 寄存器 bank 和一个执行上下文，不是四套配置或四个任务上下文；命令 lane 编号由输入端口位置确定，不新增命令 payload 字段。每路 Payload：

| 字段 | 宽度 | 含义 |
|---|---:|---|
| `op` | 2 bit | HiveCoreOp：REG_WRITE1、REG_WRITE2、REG_READ；编码 3 保留 |
| `reg0Addr` | 6 bit | 第一个寄存器索引，即 slot0 的地址 |
| `reg1Addr` | 6 bit | 第二个寄存器索引，即 slot1 的地址 |
| `data0` | 32 | 第一个写数据 |
| `data1` | 32 | 第二个写数据 |
| `pc` | 32 | tracing PC |
| `tag` | 8 | tracing tag |

这里的 slot0/slot1 是一条命令内的两项写字段，与第 6.2 节按 lane 分配的四个响应缓存 slots 不同。

操作码编码：

| 编码 | HiveCoreOp | 操作 |
|---:|---|---|
| 0 | REG_WRITE1 | 写 `reg0Addr`，数据为 `data0`；slot1 不参与 |
| 1 | REG_WRITE2 | 整体检查 `reg0Addr/data0` 与 `reg1Addr/data1`，合法后逐目标地址仲裁提交 |
| 2 | REG_READ | 读取 `reg0Addr`；`reg1Addr/data0/data1` 不参与读取 |
| 3 | 保留 | 拒绝，`resp.err=1`，无副作用 |

本接口只有上述三个合法操作码。启动计算通过向 `START`（索引 `0x10`）写入非零值完成，不另设执行操作码。寄存器地址是寄存器索引，不是 MMIO byte offset。

#### 6.1.1 单批接收与拍前检查

当响应 pending 位图为空时，四路均可接收命令；本拍所有 `cmd[i].fire` 组成一批。允许任意混合读写以及任意稀疏 lane 子集同拍 fire，例如仅 lane1 与 lane3；低层不要求 lane0 起的连续前缀，也不要求各路 op 相同。未 fire 的 lane 不属于本批，不预留其响应，也不能在后续拍补入本批。每批最多四条命令，每个 lane 至多一条。

`cmd[i].ready` 只受第 6.2 节单批未消费响应反压，不直接受执行事务在途门控。所有命令的合法性与 `inFlight` 保护均使用 `cmd.fire` 拍之前的状态独立检查；不得让某 lane 的 START、同拍 done 或配置错误先改变其它 lane 的合法性。拍前 `inFlight=0` 时，同批配置写与非零 START 可同时合法；拍前 `inFlight=1` 时，即使本拍 done/配置错误清除在途，配置写与非零 START 仍被拒绝。

`inFlight` 在本批合法非零 START 仲裁产生启动时置位，覆盖启动脉冲前、CalcConfig 配置阶段、执行及最终排空，直到 Executor done 或 CalcConfig 配置错误脉冲清除；不能用 Executor busy 代替该保护状态。

| 命令项 | 拍前 `inFlight=0` | 拍前 `inFlight=1` |
|---|---|---|
| REG_READ | 允许 | 允许 |
| 配置寄存器写（`0x00–0x0B`） | 允许 | 拒绝，`resp.err=1` |
| 非零 START 写 | 允许，参与启动仲裁 | 拒绝，`resp.err=1` |
| 零 START 写 | no-op | no-op |
| IRQ_MASK / IRQ_CLEAR 写 | 允许 | 允许 |
| 已知只读寄存器写 | 拒绝，`resp.err=1` | 拒绝，`resp.err=1` |
| 保留索引写 | no-op | no-op |
| 非法操作码 | 拒绝，`resp.err=1` | 拒绝，`resp.err=1` |

保留索引读取返回 0；已知只读寄存器与保留索引的区别以第 8.3 节为准。配置写即使写入值与当前相同，仍按配置写执行在途检查。接口拒绝不产生配置错误 IRQ，也不参与写仲裁或 WRITE_CONFLICT。

REG_WRITE2 的两项必须先整体检查：任何一项被拒绝，整条命令 `resp.err=1`，两项均无效，不更新配置或 IRQ_MASK、不执行 W1C，也不触发 start；两项均不得参与仲裁或阻挡其它合法 lane。拒绝不阻止同拍外部完成/错误事件的正常捕获。保留索引或 START 零写本身是合法 no-op，不使另一合法项被拒绝。

#### 6.1.2 合法写入的逐地址仲裁

所有合法写入在 `cmd.fire` 当拍提交仲裁后副作用；不等待写 ACK、任何读响应或本批排空。同一 lane 的两项先按以下规则归并，再在各目标地址独立选择 lane：

- 普通可写寄存器同址时 slot1（`data1`）胜；包括配置寄存器与 IRQ_MASK，字段截取和保留位处理仍按该寄存器定义。
- 同一 lane 两项 IRQ_CLEAR 的低 11 bit 按位 OR，作为该 lane 的一个清除候选。
- 同一 lane 两项 START 仅非零值参与，非零项按位 OR，至多形成一个启动候选；零项不触发、不阻挡非零项，也不计冲突。START 数据不存储。

跨 lane 对每个实际可写目标地址按 `lane3 > lane2 > lane1 > lane0` 仲裁。实际目标为配置寄存器（`0x00–0x0B`）、IRQ_MASK、IRQ_CLEAR，以及非零 START；读、保留索引 no-op 与 START 零写不提供目标。普通同址写由最高 lane 覆盖该地址的整项写入，不做跨 lane 寄存器位域合并。

落败的冲突字段不生效，该命令其它不冲突或在其目标上胜出的字段仍生效，且整条合法命令 `resp.err=0`。REG_WRITE2 的原子性仅指合法性拒绝时整条无副作用；合法后的两个 slot 按地址分别仲裁，不保证跨 lane 时两个 slot 一起获胜，也不因其中一个落败而撤销另一个。例如 lane0 双写 A_ADDR/B_ADDR、lane3 单写 A_ADDR：A_ADDR 取 lane3，B_ADDR 仍取 lane0，两条 ACK 均 `err=0`。

IRQ_CLEAR 跨 lane 只使用最高 lane 的候选，禁止跨 lane OR；合法的 CLEAR 写即使数据为 0 也参与仲裁并阻挡低 lane 的 CLEAR。例如 lane3 CLEAR=0、lane0 CLEAR=1，不清 bit0，并形成跨 lane 同址写冲突。新事件仍按第 8.8、11.2 节优先于获胜 W1C。

跨 lane START 只在合法且非零的候选中选择最高 lane，其 PC/tag 取自该 lane 的整条命令。高 lane START=0 不阻挡低 lane 非零 START；多个合法非零 START 同址参与写冲突判定，但本批仅安排一次启动、一次元数据捕获和一个下一拍的一拍 start 脉冲。

本批所有 winning 配置在接受拍生效，下一拍 start 使用这些配置；不限于与 START 同一 lane 或同一条 REG_WRITE2 的配置项，配置与 START 在命令两项中的顺序均适用。不建立 shadow 配置组。响应反压不延迟或重复写提交、`inFlight` 置位、元数据捕获或启动。

#### 6.1.3 读取快照

每条 REG_READ 在其 `cmd.fire` 拍捕获拍前寄存器值到对应响应 slot，不旁路任何 lane 的同拍写。配置寄存器、STATUS、IRQ_RAW、IRQ_MASK、IRQ_MASKED 与 ERROR_CODE 均使用拍前快照；同拍 START 引起的 `inFlight` 置位、CLEAR/MASK 写、WRITE_CONFLICT、完成/配置错误/DMA 新事件不会改写这条读响应。START、IRQ_CLEAR 与保留索引的读取仍为 0。快照在响应背压期间保持，不在响应输出或消费时重新取值。

### 6.2 普通响应接口

本节单批 slots/pending、响应顺序与反压契约已验证。

`resp` 保持单一带 ready/valid 握手的输出流，不扩展为四路：

| 字段 | 宽度 | 含义 |
|---|---:|---|
| `data` | 32 | REG_READ 的拍前快照；其它命令通常为 0，沿用原规则 |
| `err` | 1 | 0=接口接受（包括合法但部分/全部写目标仲裁落败），1=接口拒绝 |
| `laneId` | 2 bit | 本响应对应的命令输入 lane 编号 0–3 |

每条 `cmd[i].fire` 均生成且只生成一个响应，包括读、写 ACK、合法 no-op 和拒绝命令。HiveCoreRegIf 仅提供四个按 lane 编号的响应缓存 slots 与 4-bit `pending[3:0]` 位图，缓存一批，不建立多批队列或四个执行上下文。接受拍保存每个 fire lane 的 data/err 并置其 pending 位；未 fire lane 的 pending 为 0。响应从接受后的下一拍起可返回。

只在本批 pending lane 中按编号递增串行输出响应，跳过未参与的 lane；读和所有写 ACK 遵循同一顺序，不按 op、成功/拒绝或写仲裁优先级重排。输出 laneId 是当前最小 pending lane；其 pending 位只在对应 `resp.fire` 清除。尤其读的 pending 不得在捕获快照、内部处理完成或 `resp.valid` 时提前清除，必须等该读响应 `resp.fire`。

只要本批任一 pending 位为 1，所有 `cmd[0..3].ready` 必须为 0，包括有 pending 读时的所有 lane。即使部分 lane 的响应已消费或 cache slot 空闲，也不接下一批；写已提交也不解除响应阻塞。`resp.valid && !resp.ready` 时必须持续保持 valid 与完整 payload（data/err/laneId）稳定。

最后一个响应 fire 的当拍，拍前 pending 仍非空，因此所有 lane ready 仍为 0，不同拍接入下一批；最后响应 fire 后的下一拍 pending 才为空，四路可接下一批。若无 pending，resp.valid=0。不存在本批读未消费时穿插下一批写入或用最后一次 resp.fire 组合直通接收的例外。

REG_WRITE1/REG_WRITE2 的 ACK 不等待计算结束；`resp.err=0` 只表示接口接受该寄存器操作，不表示每个写字段都仲裁获胜、配置合法、GEMM 已完成或结果无错误。`resp.err=1` 表示第 6.1 节定义的逐命令拒绝，该命令整体无副作用。WRITE_CONFLICT 不改变任一响应的 err。

运行期配置检查和 DMA 故障不改变已返回或待返回的 ACK，不通过 `resp.err` 上报；它们通过 IRQ_RAW、ERROR_CODE 与 irq 观测。软件必须先确认 START 写 ACK 成功，并结合本批 START 仲裁选择，再按第 13.1 节区分配置错误、执行完成、DMA 错误和 WRITE_CONFLICT，不能仅凭 ACK、irq 电平或 busy 变化判断成功完成。

### 6.3 Tracing 接口

`respForTracing` 是无反压 Flow，包含：

| 字段 | 宽度 | 含义 |
|---|---:|---|
| `state` | 2 | 0=start，1=done，3=configuration error |
| `pc` | 32 | 本批最高合法非零 START lane 的整条命令携带的 PC |
| `tag` | 8 | 本批最高合法非零 START lane 的整条命令携带的 tag |

四路 START 元数据选择契约已验证：REG_WRITE2 中的 START 使用整条命令的 PC/tag；同 lane 两项非零 START 合并一次，跨 lane 仅最高合法非零 START lane 胜出，本批只捕获一次该命令的 PC/tag。普通配置写、读取、IRQ 写、零 START 写、被拒绝命令以及落败 lane 的 START 均不捕获启动元数据。高 lane 零 START 不阻挡低 lane 非零 START。start 事件与 HiveCoreRegIf 在本批命令接受后一拍产生的单个 start 对应，不因响应反压延后或重复，不把普通 ACK 当作 tracing 完成事件。

可靠性边界见第 11.4 节，首次配对限制不变。

### 6.4 DMA 接口

读 DMA 接口：

```text
req  : Core -> Memory, ready/valid, {addr}
rsp  : Memory -> Core, ready/valid, {data, err}
```

写 DMA 接口：

```text
req  : Core -> Memory, ready/valid, {addr, data, strb}
rsp  : Memory -> Core, ready/valid, {err}
```

默认配置端口：

| 接口 | 方向 | 数据宽度 | 用途 |
|---|---|---:|---|
| `dmaA` | 读 | 256 bit | A 数据 |
| `dmaB` | 读 | 256 bit | B 数据 |
| `dmaC` | 写 | 256 bit | C 数据 |
| `dmaScaleA` | 读 | 32 bit | ScaleA、bias、rqM |
| `dmaScaleB` | 读 | 32 bit | ScaleB |
| `dmaCScale` | 写 | 8 bit | ScaleC |

Scale 端口只在编译期包含 MX 硬件时存在。

### 6.5 状态接口

`io.status` 为无握手输出 Bundle。字段语义见第 11.1 节。完成与错误只经中断寄存器观测，Bundle 不提供 `done` 或 `err` 字段。

| 字段 | 宽度 |
|---|---|
| `busy`、`inFlight` | 各 1 bit |
| `progress` | 16 bit |
| `aOccupancy` | `log2Up(aBufferDepth+1)` bit |
| `bOccupancy` | `log2Up(bBufferDepth+1)` bit |
| `cOccupancy` | `log2Up(cBufferDepth+1)` bit |

### 6.6 中断接口

`irq` 为 1-bit 电平输出，无 ready/valid 握手。中断寄存器见第 8.8 节；置位、屏蔽和 W1C 清除语义见第 11.2 节。

### 6.7 性能事件接口

`perf` 为无握手输出 Bundle，各事件均为 1-bit 电平。事件定义与计数口径见第 11.3 节。

## 7. 数据表示规范

### 7.1 格式编码

| 编码 | 格式 | 元素宽度 | 默认 A/B 支持 | C 输出路径 |
|---:|---|---:|:---:|:---:|
| 0 | FP32 | 32 | 否 | 是 |
| 1 | FP16 | 16 | 否 | 是 |
| 2 | BF16 | 16 | 否 | 是 |
| 3 | MXFP8 E4M3 | 8 | 是 | 是 |
| 4 | MXFP8 E5M2 | 8 | 是 | 是 |
| 5 | MXFP4 E2M1 | 4 | 是 | 是 |
| 6 | INT32 | 32 | 否 | 是 |
| 7 | INT16 | 16 | 否 | 是 |
| 8 | INT8 | 8 | 是 | 是 |
| 9 | INT4 | 4 | 是 | 是 |
| 10 | FP8 E4M3 | 8 | 是 | 否 |
| 11 | FP8 E5M2 | 8 | 是 | 否 |

“默认 A/B 支持”同时受编译期 `supportedFmts` 约束。软件不得使用未生成的数据通路。

### 7.2 Lane 打包

每个 A/B lane 宽度默认为 16 bit：

- 8-bit 格式：bit `[7:0]` 为较小 K，bit `[15:8]` 为较大 K；
- 4-bit 格式：四个 nibble 按 bit `[3:0]、[7:4]、[11:8]、[15:12]` 对应递增 K；
- INT4 使用 4-bit 二补码；
- MXFP4 使用 E2M1 编码。

在外部字节流中，4-bit 元素 `2p` 位于低 nibble，元素 `2p+1` 位于高 nibble。

### 7.3 舍入模式编码

| 编码 | 模式 | 含义 |
|---:|---|---|
| 0 | RNE | round to nearest, ties to even |
| 1 | RTZ | round toward zero |
| 2 | RDN | round toward negative infinity |
| 3 | RUP | round toward positive infinity |
| 4 | RMM | round to nearest, ties to max magnitude |
| 5–7 | 保留 | 软件不得使用 |

该字段用于浮点/MX 输出转换。整数非 requant 路径忽略该字段。

### 7.4 MX Block Size 编码

A、B、C 分别配置 block size：

| 编码 | Block Size |
|---:|---:|
| 0 | 16 elements |
| 1 | 32 elements |

该表表示字段可编码值。A/B 可使用 16 或 32；默认 `T=16` 时，C block 不能大于 T，因此 C 只允许 16，配置 C block=32 会触发 errCode 0。旧的 8/64 档位不可编码。

### 7.5 矩阵存储布局

#### 7.5.1 A 矩阵

A 按 `M×K` row-major 稠密存储：

```text
A[m][k]
```

每个 N tile 都会重新读取对应 A 数据；HiveCore 内部不跨 N tile 缓存 A。

#### 7.5.2 B 矩阵

##### `loadWMode=0`

B 按补齐后的 `K×Nprog` row-major 存储。硬件按 K/N tile 地址跳转读取。

##### `loadWMode=1`

B 必须预转置为 `Nprog×K` row-major：

```text
BT[n][k] = B[k][n]
```

MX、FP8 和正向 INT W4A8 必须采用此布局。

#### 7.5.3 C 矩阵

C 按 `M×Nprog` row-major 写回，其中 `Nprog` 必须是 T 的整数倍。硬件不会缩窄末 tile；软件只消费每行前 `Nlogical` 列。

#### 7.5.4 N 维补齐

硬件以完整 T 列为单位读取 B 和写回 C，不对最后一个 N tile 自动缩窄 byte strobe。C 行步长又直接由编程值 `N` 计算，因此仅在缓冲区末尾追加空间不能阻止最后一个 tile 跨入下一行。

软件必须把写入寄存器的 `N` 向上补齐为 T 的整数倍：

```text
Nprog = ceil(Nlogical / T) × T
```

A×B、C、bias、requant 参数和相关 scale 布局均按 `Nprog` 构造，逻辑列数 `Nlogical` 由软件在 HiveCore 之外保存。补齐列必须具有可访问的 B/参数数据，C 缓冲区也必须按每行 `Nprog` 个元素分配。

### 7.6 MX Scale 存储布局

#### 7.6.1 ScaleA 布局

ScaleA 逻辑形状为：

```text
M × (K / blockA)
```

地址为：

```text
scaleAAddr(m, kt) = aScaleBase
                    + m × (K / blockA)
                    + kt × (kPerBeatA / blockA)
```

每个元素占 1 byte。

#### 7.6.2 ScaleB 布局

ScaleB 逻辑形状为：

```text
N × (K / blockB)
```

B 以 N-major 行组织。定义：

```text
kWindowBEffective = kPerBeatB                  // 同宽 A/B
kWindowBEffective = kPerBeatA                  // 正向 MX W4A8
```

地址为：

```text
scaleBAddr(n, kt) = bScaleBase
                    + n × (K / blockB)
                    + kt × (kWindowBEffective / blockB)
```

默认 `T=16` 的正向 MX W4A8 中 `kWindowBEffective=32`，因此 block16 的步长为 2 byte，block32 的步长为 1 byte。该运行还要求 `K%64=0` 且 `B_ADDR%32=0`。

#### 7.6.3 ScaleC 布局

MX 输出的 ScaleC 逻辑形状为：

```text
M × ceil(Nprog / blockC)
```

地址为：

```text
scaleCAddr(m, nt) = cScaleBase
                    + m × ceil(Nprog / blockC)
                    + floor(nt × T / blockC)
```

### 7.7 参数存储布局

#### 7.7.1 Bias

Bias 为每个补齐后的输出列提供一个 32-bit 值，共 Nprog 项：

```text
bias[n]
```

每个 N tile 读取 T 个连续 32-bit 字：

```text
biasAddr(nt, i) = biasBase + nt × T × 4 + i × 4
```

整数计算把 bias 解释为有符号 INT32；非整数计算把相同 32-bit 字解释为 FP32。

#### 7.7.2 Requant 乘数

Requant 乘数 `M[n]` 为每个输出列一个有符号 32-bit 定点乘数：

```text
rqMAddr(nt, i) = rqMBase + nt × T × 4 + i × 4
```

bias 和 rqM 共用 ScaleA DMA 物理读口；两者同时启用时，硬件先读取 bias，再读取 rqM。

## 8. 配置与寄存器规范

### 8.1 默认编译期配置

| 参数 | 默认值 | 含义 |
|---|---:|---|
| `arrayN` | 8 | 每个 cluster 的 N 方向宽度 |
| `clusterM` | 2 | cluster 数 |
| `totalN` | 16 | `arrayN × clusterM` |
| `aW` | 16 bit | 每个 K-lane 的 A 输入打包宽度 |
| `bW` | 16 bit | 每个 PE 的 B 权重打包宽度 |
| `cW` | 0 | 自动派生累加宽度 |
| `aBufferDepth` | 64 | A FIFO 深度 |
| `bBufferDepth` | 64 | B FIFO 深度 |
| `cBufferDepth` | 64 | C/partial-sum FIFO 深度 |
| `addrWidth` | 32 | DMA 地址宽度 |
| `scaleBufferDepth` | 64 | scale FIFO 深度 |
| `cDmaExtW` | 256 bit | C 写 DMA 数据宽度 |

默认 `supportedFmts` 为 `INT8、INT4、MXE4M3、MXE5M2、MXE2M1、FP8E4M3、FP8E5M2`。

四路端口契约已验证：命令 lane 固定为 4，不新增 lane count 配置 knob；寄存器数量由第 8.3 节固定地址映射定义，`registerNumRW` / `registerNum` 不属于可配置 count，不列入编译配置参数。命令 lane 与阵列 K-lane、A/B 打包 lane、MAC 数据并行度无关，不改变 totalN、aW、bW 或任何数值几何。

### 8.2 编译期约束

配置必须满足：

- `arrayN ≥ 4`，`clusterM ≥ 1`；
- `aBufferDepth > 0`，`bBufferDepth > 0`；
- `cBufferDepth ≥ 2T`；
- `0 < addrWidth ≤ 32`；
- `scaleBufferDepth > 0`；
- `cDmaExtW` 为 2 的幂，位于 `[8T, 32T]`，并整除 `32T`；
- 含 MX 格式时必须满足 `T=16`、`aW≥16`、`bW≥16`；
- 含 FP8 时必须同时生成 MX 数据通路；
- MX 数据通路必须同时包含至少一种 MXFP8（E4M3 或 E5M2）和 MXFP4 E2M1；fp8-only 与 fp4-only 的 MX 编译配置均不合法；
- 浮点/MX 累加有效宽度不得小于 40 bit；
- 整数累加有效宽度不得小于 `aW+bW`。

当前发布配置均包含 MX 硬件。纯无 MX 的参数化实例不属于本文保证的交付配置。

### 8.3 寄存器索引总表

寄存器索引为 6 bit，范围 `0x00–0x3F`。配置存储由 HiveCoreRegIf 持有；四路共享这一固定 bank（共享与仲裁契约已验证），不按 lane 复制寄存器。地址映射、配置字段的既有位布局与计算含义不变，仅中断有效位扩展到低 11 bit。

| 索引 | 名称 | 访问 | 含义 |
|---:|---|---|---|
| `0x00` | M | RW | M 维度 |
| `0x01` | N_K | RW | N、K 维度 |
| `0x02` | A_ADDR | RW | A 基地址 |
| `0x03` | B_ADDR | RW | B 基地址 |
| `0x04` | C_ADDR | RW | C 基地址 |
| `0x05` | CONTROL | RW | 格式和控制位 |
| `0x06` | SCALE_A_ADDR | RW | ScaleA 基地址 |
| `0x07` | SCALE_B_ADDR | RW | ScaleB 基地址 |
| `0x08` | SCALE_C_ADDR | RW | ScaleC 基地址 |
| `0x09` | BIAS_ADDR | RW | Bias 基地址 |
| `0x0A` | RQ_M_ADDR | RW | Requant 乘数基地址 |
| `0x0B` | RQ_PARAM | RW | Zero-point 和 shift |
| `0x0C–0x0F` | RESERVED | — | 读 0，写 no-op |
| `0x10` | START | 写触发 / 读 0 | 非零写触发一次启动，零写 no-op |
| `0x11–0x1D` | RESERVED | — | 读 0，写 no-op |
| `0x1E` | STATUS | RO | executorBusy、inFlight、progress |
| `0x1F–0x2F` | RESERVED | — | 读 0，写 no-op |
| `0x30` | IRQ_RAW | RO | 低 11 bit 分源粘滞完成/错误/WRITE_CONFLICT 事件 |
| `0x31` | IRQ_MASK | RW | 低 11 bit 中 bit=1 屏蔽对应中断源；复位 `0x000007FF` |
| `0x32` | IRQ_MASKED | RO | 低 11 bit 为 `IRQ_RAW & ~IRQ_MASK`，高 21 bit 为 0 |
| `0x33` | IRQ_CLEAR | W1C / 读 0 | 低 11 bit 写 1 清对应 RAW；bit 1 同步清 ERROR_CODE |
| `0x34` | ERROR_CODE | RO | 低 4 bit 为首个未清配置错误码 |
| `0x35–0x3F` | RESERVED | — | 读 0，写 no-op |

M/N_K/CONTROL/RQ_PARAM 的字段定义分别见第 8.4、8.5、8.7 节。A/B/C、scale、bias 与 rqM 地址寄存器保留既有地址宽度及字段处理规则，DMA `addrWidth` 与对齐/可访问范围约束不变。

已知 RO 寄存器 STATUS、IRQ_RAW、IRQ_MASKED 和 ERROR_CODE 的写入必须拒绝，`resp.err=1`；不得当作保留索引 no-op。保留索引没有启动、配置或 IRQ 副作用，也不存在独立中断 ENABLE 寄存器。

### 8.4 M 与 N/K

`M`：

| Bits | 字段 |
|---|---|
| `[15:0]` | M |
| `[31:16]` | 读 0 |

`N_K`：

| Bits | 字段 |
|---|---|
| `[15:0]` | N |
| `[31:16]` | K |

M、N、K 均为 16-bit 无符号值。

### 8.5 CONTROL

| Bits | 字段 | 含义 |
|---|---|---|
| `[3:0]` | `aFmt` | A 格式 |
| `[7:4]` | `bFmt` | B 格式 |
| `[11:8]` | `cFmt` | C 格式 |
| `[12]` | `aAddrMode` | 0=MAT，1=TILE |
| `[13]` | `bAddrMode` | 0=MAT，1=TILE |
| `[14]` | `cAddrMode` | 0=MAT，1=TILE |
| `[15]` | `reluOn` | ReLU 使能 |
| `[18:16]` | `rnd` | 舍入模式 |
| `[19]` | Reserved | 写入被忽略 |
| `[20]` | `warmdown` | 当前数据通路未消费，仅保留读写 |
| `[21]` | `warmup` | 当前数据通路未消费，仅保留读写 |
| `[22]` | `aMxBlockSize` | 0=16，1=32 |
| `[23]` | Reserved | 写入被忽略 |
| `[24]` | `bMxBlockSize` | 0=16，1=32 |
| `[25]` | Reserved | 写入被忽略 |
| `[26]` | `cMxBlockSize` | 0=16，1=32 |
| `[27]` | Reserved | 写入被忽略 |
| `[28]` | `loadWMode` | B 装载模式 |
| `[29]` | `requantOn` | Requant 使能 |
| `[30]` | `biasOn` | Bias 使能 |
| `[31]` | Reserved | 写入被忽略 |

`AddrMode` 编码定义为 MAT/TILE，但当前主数据通路没有消费这三个字段；软件应写 0，不能依赖 TILE 模式改变 DMA 地址行为。

### 8.6 STATUS 与 START

`STATUS` 位于索引 `0x1E`：

| Bits | 字段 | 含义 |
|---|---|---|
| `[0]` | `executorBusy` | Executor 非 IDLE，与 `io.status.busy` 相同 |
| `[1]` | Reserved | 0 |
| `[2]` | `inFlight` | 有效启动写已接受，尚未收到 done 或配置错误脉冲 |
| `[15:3]` | Reserved | 0 |
| `[31:16]` | `progress` | 已完成 `(N,K)` pass 数 |

STATUS 不含完成、错误标志或错误码；写入拒绝，`resp.err=1`。`executorBusy` 可能在 `inFlight=1` 的启动/配置阶段仍为 0。任务结果必须读取 IRQ_RAW 与 ERROR_CODE，不能以 STATUS 的保留位或 busy 单独判断。

`START` 位于索引 `0x10`，读返回 0，不存储写数据。四路 START 契约已验证：命令整体合法后，非零 32-bit 写值参与启动仲裁，零写不触发、不阻挡也不计冲突。同 lane 两项非零 START 按位 OR 合并一次，跨 lane 选择最高合法非零 START lane；本批只在接受拍置位一次 `inFlight`、捕获一次胜出命令的 PC/tag，并在下一拍产生一个一拍 start。计算使用本批所有 winning 配置。START 不清除 IRQ_RAW、IRQ_MASK 或 ERROR_CODE；在途拍前检查和 REG_WRITE2 的整体拒绝/逐地址仲裁规则见第 6.1 节。STATUS 与其它寄存器读均按第 6.1.3 节取拍前快照，不旁路本拍 START、写入或新事件。

### 8.7 RQ_PARAM

| Bits | 字段 | 含义 |
|---|---|---|
| `[5:0]` | `shift` | 右移位数 |
| `[15:6]` | Reserved | 0 |
| `[31:16]` | `zeroPoint` | 有符号 16-bit zero-point |

### 8.8 中断与配置错误码寄存器

本节新增 WRITE_CONFLICT、低 11 bit 扩展及四路 CLEAR/MASK 仲裁相关契约已验证；既有完成/DMA 事件源与配置错误首码规则保持不变。

#### 8.8.1 IRQ_RAW（`0x30`，RO）

IRQ_RAW 捕获以下分源事件并保持，直至对应 W1C 或复位；读取没有副作用，捕获不受 IRQ_MASK 影响。

| Bit | 事件源 | 置位条件 |
|---:|---|---|
| 0 | 完成 | Executor `done` 脉冲；不代表任务无错误 |
| 1 | 配置错误 | CalcConfig `errPluse` 脉冲 |
| 2 | A DMA 错误 | A DMA `rsp.fire && rsp.err` |
| 3 | B DMA 错误 | B DMA `rsp.fire && rsp.err` |
| 4 | C DMA 错误 | C DMA `rsp.fire && rsp.err` |
| 5 | ScaleA DMA 错误 | 共享 ScaleA 物理口响应错误，当前响应 owner 为 ScaleA 或 WAIT |
| 6 | ScaleB DMA 错误 | ScaleB DMA `rsp.fire && rsp.err` |
| 7 | ScaleC DMA 错误 | ScaleC DMA `rsp.fire && rsp.err` |
| 8 | Bias DMA 错误 | 共享 ScaleA 物理口响应错误，当前响应 owner 为 Bias |
| 9 | RqM DMA 错误 | 共享 ScaleA 物理口响应错误，当前响应 owner 为 RqM |
| 10 | WRITE_CONFLICT | 同一接受批中，不同 lane 的已接受且整条合法写命令具有相同实际可写目标地址 |
| `[31:11]` | Reserved | 高 21 bit 读 0，无事件源 |

WRITE_CONFLICT 的实际目标与第 6.1.2 节相同：配置寄存器、IRQ_MASK、IRQ_CLEAR、非零 START。按 lane 内归并后的目标集合判定，只要至少两个不同 lane 指向同一实际可写地址即形成事件，即便 data 相同或经字段截取后结果相同。IRQ_CLEAR=0 仍有实际目标，会阻挡低 lane，也可形成冲突；START=0 没有实际目标。

读写同址、同 lane 两项重复、整条被拒绝命令的任何项、保留索引 no-op 和 START 零写均不形成该事件。同拍多个地址或多个 lane 对的冲突只汇聚为一个 `newEvents[10]`，不记录次数。RAW bit10 粘滞直到 W1C/复位，fresh event 优先于同拍 W1C。该事件仅报告确定性仲裁中的跨 lane 写竞争，不改变命令 err、ERROR_CODE 或 inFlight，不撤销 winning 写入，也不终止任务。

#### 8.8.2 IRQ_MASK（`0x31`，RW）与 IRQ_MASKED（`0x32`，RO）

IRQ_MASK 仅低 11 bit 有效：1=屏蔽对应源，0=允许该源驱动 irq。复位值为 `0x000007FF`（`0x7FF`），全部源默认屏蔽；高 21 bit 写入忽略，读回为 0。合法写按第 6.1.2 节同 lane slot1 优先、跨 lane 最高 lane 优先。修改屏蔽不清除或阻止 RAW 捕获，已经 pending 的源解除屏蔽后即可驱动 irq。不存在额外 ENABLE 寄存器。

IRQ_MASKED 读返回 `IRQ_RAW & ~IRQ_MASK`，高 21 bit 为 0；读取不清事件。IRQ_RAW 与 IRQ_MASKED 的写入均拒绝。

#### 8.8.3 IRQ_CLEAR（`0x33`，W1C / 读 0）

仅低 11 bit 有效，写 1 清对应 IRQ_RAW 位，写 0 保持；高 21 bit 写入忽略，读取恒为 0。写 bit 1 同步清除 ERROR_CODE，写 bit10 清 WRITE_CONFLICT。先将同一 lane 的两个 IRQ_CLEAR 数据低 11 bit 按位 OR，再在跨 lane 候选中仅选最高 lane 执行 W1C；不跨 lane OR。CLEAR=0（包括仅写保留高位）仍参与地址仲裁，可阻挡低 lane 清除。整条命令被拒绝时，其两项既不清位也不参与仲裁。

同一拍 CLEAR 与新事件作用于同一 RAW 位时，新事件优先，RAW 保持置位；包括 CLEAR 冲突自身产生的新 bit10。IRQ_CLEAR 不终止任务，不清 `inFlight` 或 progress；START、普通读取、完成事件和屏蔽写均不自动清 RAW。

#### 8.8.4 ERROR_CODE（`0x34`，RO）

`[3:0]` 保存首个未清配置错误码，`[31:4]` 读 0；复位为 0。错误码含义和 CalcConfig 同次检查的优先级见第 10.1–10.3 节。

- 配置错误事件到达且原 `IRQ_RAW[1]=0` 时，捕获该事件的配置错误码；
- 原 `IRQ_RAW[1]=1` 且本拍不清 bit 1 时，后续配置错误只保持 pending，不覆盖 ERROR_CODE；
- CLEAR bit 1 与新配置错误同拍时，捕获新错误码且 RAW bit 1 保持置位；
- CLEAR bit 1 且无新配置错误时，ERROR_CODE 清零，与 RAW bit 1 同步；
- START 不清 ERROR_CODE，DMA 错误和完成事件不修改 ERROR_CODE。

错误码 0 本身是有效配置错误码；是否有 pending 配置错误必须看 IRQ_RAW[1]，不得通过 ERROR_CODE 是否非零判断。

上述 CLEAR bit 1 指本拍最终胜出 lane 的清除掩码，按第 8.8.3 节归并/仲裁；其首个未清配置错误码规则不变。WRITE_CONFLICT bit10 不产生 configError，不写 ERROR_CODE，也不改变任何响应 err 或 inFlight。

## 9. 时序与复位规范

### 9.1 时钟与复位边界

HiveCore 使用 Chisel `Module` 的隐式 clock/reset 接口。本文不固定交付 RTL 的复位类型、有效电平、同步/异步形式或复位保持周期；这些项目须在具体集成时依据发射 RTL 确认，不得仅由命令/DMA payload 表推定。

复位清除配置寄存器组、待发 start、`inFlight`、IRQ_RAW（含 WRITE_CONFLICT）和 ERROR_CODE；IRQ_MASK 复位为 `0x000007FF`，高 21 bit 为 0，因此 IRQ_MASKED 与 irq 为 0。四路单批缓存复位契约已验证：复位使四个响应 slots 无效、`pending[3:0]=0`、`resp.valid=0`，不保留未消费响应或重发旧启动。未初始化的数据存储内容不构成有效输入；复位后必须重新配置再启动执行。

### 9.2 初始化与执行在途约束

本节四路接收、拍前快照/保护、单批反压与 START 时序/元数据契约已验证。

HiveCoreRegIf 在批接收拍对各 lane 命令整体检查，再对合法写入逐地址仲裁。全部 winning 配置在该 `cmd.fire` 拍提交；本批存在合法非零 START 候选时仅最高 lane 胜出，在接受拍置位一次 `inFlight` 并捕获一次胜出命令的 PC/tag，下一拍输出一个一拍 start，计算使用本批所有 winning 配置。单写 START、同 lane 配置+START 双写和跨 lane 配置/START 同批均使用这一时序；同 lane 两项非零 START 按位 OR 合并一次，零 START 不阻挡非零候选。不建立 shadow 配置组。ACK 不等待启动检查或任务完成；响应反压也不延迟或重复写提交或启动副作用。

同批各读命令在接受拍捕获拍前快照，包含 STATUS 与 IRQ 寄存器，不旁路同拍写/启动/新事件。合法性与 inFlight 保护也仅使用拍前状态：本拍 done/配置错误不使拍前在途配置写或非零 START 变为合法，同批 START 不使原本拍前空闲的其它 lane 配置写变为非法。

本批四 slots/pending 中任何响应未消费时（尤其有 pending 读时），四路 ready 全低；pending 只在各自 `resp.fire` 清除，响应背压时 data/err/laneId 稳定。最后响应 fire 的当拍不接新批，下一拍才可接新批。读响应阻塞不推迟本批已合法写入的提交或下一拍 start，不能把响应排空当作写提交点。

CalcConfig 检查通过后，有效执行启动时的工作 FIFO flush 与状态迁移见第 5.1.4 节。`inFlight` 持续覆盖配置与执行阶段，在 Executor done 或 CalcConfig 配置错误脉冲时清除，错误是否被屏蔽或软件是否清 IRQ 不影响该状态。DMA 错误事件不清 `inFlight`，也不产生 fail-fast 终止。WRITE_CONFLICT 同样不清 `inFlight`、不修改 ERROR_CODE 或命令 err。

在途期间允许 READ、IRQ_MASK/IRQ_CLEAR 写、零 START 写和保留索引 no-op；配置写和非零 START 写按第 6.1 节逐命令拒绝，而不是排队等待执行结束。上述允许仍须等单批响应排空，不绕过四路统一 ready 门控。

本文没有定义执行中复位对外部已接受 DMA 请求的取消、响应丢弃或结果回滚协议。系统必须先协调在途事务，再按其复位方案处理，不得把复位解释为完整的事务撤销机制。

### 9.3 读响应顺序与 FIFO Credit

读请求发出时即预留 FIFO credit；响应到达并进入 FIFO 后，直到该项被消费才返还 credit。读接口没有 transaction ID，gather/stretch 状态机按请求顺序配对响应。外部必须严格按请求顺序返回读响应；每个已接受的读请求必须且只能返回一个响应，不得产生无请求响应。

### 9.4 C 写反压与响应完成

写请求可被 `req.ready` 反压。硬件在一整行最后一拍被接受后才弹出对应 C FIFO 项。执行完成还会等待全部 C 写响应返回。外部必须为每个已接受的写请求返回且只返回一个响应，不得产生无请求响应。

### 9.5 权重装载连续性

B 装载节拍不得在一个窗口中断裂；入口预蓄门控用于保证完整窗口供数。

### 9.6 ScaleC 在途与反压限制

ScaleC 通过独立 8-bit 写接口输出。当前实现的 ScaleC FIFO/在途控制深度为 2，但 FIFO pop 的 ready 未被“在途已满”条件门控。集成环境必须保证在 `bufPop.valid` 有效时未完成 ScaleC 写请求数不会达到 2；否则当前 RTL 可能在没有真正发出请求时弹出并丢失一个 scale。最安全的集成方式是对 ScaleC 写请求提供无停顿 ready，并在零或一拍内返回响应。

## 10. 异常处理规范

### 10.1 配置错误码表

| errCode | 判据摘要 |
|---:|---|
| 0 | MX block size 大于对应 K 窗口，或 C block size 大于 T |
| 1 | 冻结，无生产者 |
| 2 | 冻结，无生产者 |
| 3 | 不支持的格式组合，包括 MX 单边、反向 W4A8、不支持的 FP8 组合或 FP8 输出 |
| 4 | MX/FP8 未设置 `loadWMode=1` |
| 5 | A 或 B 使用 FP32 |
| 6 | 无 RTL 生产者 |
| 7 | `M > cBufferDepth-T` |
| 8 | K 粒度、K=0 或正向 W4A8 的 K/B 对齐违约 |
| 9 | 保留，无生产者 |
| 10 | C 基地址未按 C DMA 字宽对齐 |
| 11 | 计算域与 C 输出的整数/非整数族不一致 |
| 12 | Bias 配置非法 |
| 13 | Requant 配置非法 |
| 14 | 保留，无生产者 |
| 15 | 保留，无生产者 |

### 10.2 主要判据

#### errCode 0

- MX A block 大于 A 的 `kPerBeat`；
- MX B block 大于 B 的 `kPerBeat`；
- MX C block 大于 T。

#### errCode 3

包括但不限于：

- A/B 只有一侧为 MX；
- A=MXFP4、B=MXFP8；
- A=INT4、B=INT8；
- FP8 与非 FP8 混合；
- C 使用 FP8。

#### errCode 8

- INT8、MXFP8、FP8：`K` 必须为 `2T` 的整数倍；
- INT4、MXFP4：`K` 必须为 `4T` 的整数倍；
- `K=0` 非法；
- 正向 W4A8 按 4-bit B 的几何要求执行，并附加 B 地址对齐检查；
- 正向 INT W4A8 必须设置 `loadWMode=1`。

#### errCode 12

- `biasOn=1` 且 BIAS_ADDR 非 4-byte 对齐；
- `biasOn=1` 但编译配置不含参数取数硬件。

#### errCode 13

- `requantOn=1` 且 C 不是 INT8/INT4；
- RQ_M_ADDR 非 4-byte 对齐；
- 编译配置不含参数取数硬件。

### 10.3 错误优先级

多个错误同时成立时，RTL 的最终优先级从高到低为：

```text
3(late format guards)
> 11
> 8
> 13
> 12
> 10
> 7
> 5
> 4
> 3(MX one-sided)
> 0
```

该优先级选择同一次 CalcConfig 检查的错误码，不等于不同任务间覆盖 ERROR_CODE 的优先级。软件不得假设 errCode 会报告同次检查中最先出现或数值最小的错误；首个未清配置错误的跨事件保存规则见第 8.8.4 节。

### 10.4 配置错误行为

本批合法非零 START 仲裁产生的单个延后一拍 start 触发运行期配置检查（四路 START 选择/时序已验证），检查本批全部 winning 配置；检查判据、优先级与首码保持规则不变。若配置非法：

- 不启动正常 GEMM；
- CalcConfig 输出 `errPluse` 和按第 10.3 节选出的配置错误码；
- HiveCoreRegIf 清除 `inFlight`、置位 IRQ_RAW[1]，按首次 pending / 清除同拍新事件规则捕获 ERROR_CODE；
- `irq` 是否置高取决于 IRQ_MASK[1] 及其它未屏蔽 pending 源；
- 若这是复位后的首次执行，tracing 输出 configuration error。

配置错误不追溯修改 START 写的 ACK，也不通过 STATUS 或 `io.status.err` 上报。该检查失败路径以配置错误脉冲结束在途状态，不要求 Executor 产生完成事件。DMA 错误或 irq 电平本身不终止运行中的任务。

### 10.5 DMA 错误与上报边界

顶层必须逐拍将各 DMA 的 `rsp.fire && rsp.err` 作为分源事件提供给 HiveCoreRegIf，直接置位第 8.8.1 节对应 IRQ_RAW 位，不等待 Executor done。响应仅 `valid` 而未握手时不重复形成事件；同一源后续错误保持粘滞位，W1C 同拍新事件优先。DMA 本地错误锁存在返回 IDLE 时清除，不影响已捕获的 RAW。因此早期读 DMA 响应错误不会因本地状态先行清除而漏报。

A、B、C、ScaleB、ScaleC 使用各自响应源。共享 ScaleA 物理口上的错误必须按该响应握手时仲裁器的当前响应 owner 分类，而不是按请求端的新 owner、最终 done 拍 owner 或某项使能位推断：

| 当前响应 owner | IRQ_RAW 位 |
|---|---:|
| ScaleA | 5 |
| WAIT | 5 |
| Bias | 8 |
| RqM | 9 |

DMA 错误不是 fail-fast：已发出的事务不会撤销，通道继续排空，任务继续按既有执行流程运行，C 也可能已部分写入。错误 IRQ 可能先于完成事件；不能因 irq 已置高就回收缓冲区或消费结果。完成位也不排除此前或同拍 DMA 错误。

上报保证仅覆盖实际握手且携带 `rsp.err=1` 的响应。未通过 `rsp.err` 标记的内存错误不可检测；请求缺失响应、乱序响应、非法存储访问未被外部标错及其它协议违约不因本次汇聚获得额外检测能力。DMA 错误不分配配置错误码，也不更新 ERROR_CODE。数值路径的 assert 仍是断言，不是 IRQ 事件。

### 10.6 非错误码约束

以下条件不会自动产生专用 errCode，软件仍必须满足：

- `M≥1`、`N≥1`；M/N 为 0 会使内部减一计数回绕，可能越界运行或挂死；
- 编程值 `N=Nprog` 且为 T 的整数倍；
- A/B/C 和参数缓冲区实际可访问范围；
- 配置写与非零 START 写必须在拍前 `inFlight=0` 时提交；在途违约由接口 `resp.err=1` 逐命令拒绝，不是运行期配置错误码；四路拍前检查/仲裁契约已验证，合法的跨 lane 写冲突仅置 WRITE_CONFLICT，不分配 errCode；
- 保留字段写 0；
- 使用编译配置真正生成的 A/B 格式；
- 外部 DMA 严格同序并对每个请求返回一个且仅一个响应。

## 11. 状态与观测规范

### 11.1 运行状态

`io.status` 输出：

| 字段 | 含义 |
|---|---|
| `busy` | Executor 不在 IDLE；对应 STATUS.executorBusy |
| `inFlight` | 有效非零 START 写已接受，尚未收到 Executor done 或 CalcConfig 配置错误脉冲 |
| `progress` | 已完成的 `(N tile, K pass)` 数 |
| `aOccupancy` | A FIFO 占用 |
| `bOccupancy` | B FIFO 占用 |
| `cOccupancy` | C FIFO 占用 |

`io.status` 不含 `done` 或 `err`。`progress` 不表示 M 行数，也不表示写回 byte 数；其计算口径保持不变。`busy=0` 不保证没有已接受启动，`inFlight=0` 也不表示成功：配置检查失败同样结束在途状态。完成与错误必须从 IRQ_RAW 和 ERROR_CODE 判断。

### 11.2 完成与错误中断

本节 WRITE_CONFLICT、低 11 bit IRQ 公式与四路 CLEAR/MASK 仲裁相关契约已验证。

`irq` 为电平信号，仅取决于未屏蔽的 pending 事件：

```text
IRQ_MASKED[10:0]  = IRQ_RAW[10:0] & ~IRQ_MASK[10:0]
IRQ_MASKED[31:11] = 0
irq               = OR-reduce(IRQ_MASKED[10:0])
```

RAW 更新与屏蔽分离。设 `newEvents[10:0]` 为本拍分源完成/错误/WRITE_CONFLICT 事件，bit10 是本批所有跨 lane 同址实际写冲突的 OR 汇聚；`clearBits[10:0]` 为本拍最终胜出的 IRQ_CLEAR 掩码：先对每条整体合法命令在同 lane 内将两项 CLEAR 低 11 bit OR，再取最高 CLEAR lane，无候选时为 0。跨 lane 禁止 OR，CLEAR=0 也参与并阻挡低 lane，被拒绝命令不参与。则非复位拍：

```text
IRQ_RAW_next[10:0] = (IRQ_RAW[10:0] & ~clearBits[10:0]) | newEvents[10:0]
IRQ_RAW[31:11]     = 0
```

- RAW 粘滞且不受 mask 捕获门控；MASK 只影响 IRQ_MASKED 与 irq，低 11 bit 有效，高 21 bit 保留为 0；
- 复位清 RAW 与 ERROR_CODE，MASK 复位为低 11 bit 全 1（`0x000007FF`），默认全屏蔽；
- START 不清 RAW 或 ERROR_CODE；读取、完成和写 MASK 也不自动清除；
- CLEAR/new 同拍时 fresh event 优先，包括 bit10；清 bit 1 同步处理 ERROR_CODE，首个未清配置错误码规则仍见第 8.8.4 节；
- IRQ_RAW[0] 是完成 pending，不是无错误标志；IRQ_RAW[1] 是配置检查失败，IRQ_RAW[9:2] 是分源 DMA 响应错误，IRQ_RAW[10] 是跨 lane 实际写地址冲突；
- WRITE_CONFLICT 即便 data 相同也捕获，但读写同址、同 lane 重复、拒绝命令、保留 no-op、START0 不算；同拍多个冲突只置一个 bit10，不影响响应 err、ERROR_CODE 或 inFlight；
- 错误 IRQ 可以早于完成，IRQ 本身不是任务终止信号，也不是 fail-fast；
- 软件轮询应读取 IRQ_RAW，不依赖 MASK 是否允许 irq；irq 或 IRQ_MASKED 为 0 不代表没有错误或完成事件。

IRQ_MASK 写也在接受拍按同 lane 普通同址 slot1 胜、跨 lane 最高 lane 胜提交，不因本批读 pending 或响应背压延后。所有 IRQ 寄存器读取是 `cmd.fire` 拍的拍前快照，不含本拍 CLEAR/MASK 更新或 fresh event；读取自身不清事件。

RAW 不携带任务 tag，也不累计事件次数。这里的 IRQ pending 是事件粘滞位，与响应缓存 pending 位图不同。启动前必须按第 13.1–13.2 节清除旧事件并确认完成清除写的 ACK，避免把旧完成位误判为新任务完成；START 不提供隐式事件清除。

### 11.3 性能事件

`perf` 只输出组合事件电平，不包含硬件计数器。外部 PMU 应在每个时钟采样并累加。

| 事件 | 定义 |
|---|---|
| `dmaA` | A DMA `req.valid` |
| `dmaB` | B DMA `req.valid` |
| `dmaC` | C DMA `req.valid` |
| `scaleA` | ScaleA 物理口 `req.valid`，包含 bias/rqM 取数 |
| `scaleB` | ScaleB DMA `req.valid` |
| `scaleC` | ScaleC DMA `req.valid` |
| `array` | 阵列输入有效，即 LOAD_A 的 `aPop.fire` |
| `stallA` | LOAD_A 中没有发生 `aPop.fire` |
| `stallB` | LOAD_B 中没有 `bPop.fire`，或预蓄 B/ScaleB 不足 |
| `epilogue` | bias/rqM 取数活动，或输出转换输入/输出/scale 有效 |
| `busy` | Executor 非 IDLE |

DMA 事件使用 `req.valid` 而不是 `req.fire`。当外部 `ready=0` 时，同一请求可令事件连续多个周期为 1；外部 PMU 若要统计事务数，应额外结合对应 ready 信号。

### 11.4 Tracing 可靠性边界

tracing 保持既有首次可靠范围：只有复位后的第一次实际启动，保证产生配对的 start 与 done/configuration-error 事件；元数据为该批最高合法非零 START lane 的整条命令 PC/tag（四路选择契约已验证）。零写、被拒绝命令和落败 START lane 不另算执行启动；同 lane 两项或跨 lane 多个非零 START 候选均只产生一次启动，不产生第二个任务。

tracing 状态在首次启动后不会返回初始态，后续执行的 tracing 行为不属于可靠接口契约；此次重设计不扩展为多任务可靠配对。软件不得用 tracing 代替 IRQ_RAW/ERROR_CODE 的结果判断或 `inFlight` 的在途判断。

## 12. 性能与资源规格

### 12.1 资源与容量口径

默认阵列几何为 `T=16`、PE 数为 `T×T`。FIFO 深度、端口宽度及其编译约束以第 8.1–8.2 节为准；单命令 M 上限以第 5.1.1 节的 `cBufferDepth-T` 为准。FIFO 容量按对应接口项计数，不能直接解释为可完整驻留的任意尺寸矩阵。

四路接口缓存契约已验证：仅有四个按命令 lane 分配的响应 slots 与 4-bit pending 位图，可保存一批至多四条命令的响应，不是四个 GEMM 上下文或多批 FIFO。固定命令 lane 数不改变 MAC 数据 lane、PE 数或计算吞吐口径。

### 12.2 结构性处理拍数

每个 `(N tile, K pass)` 权重窗口消费 T 个有效 B 拍，随后消费 M 个有效 A 拍，见第 5.1 节。每个 T 列输出行的 C 写拍数由输出位宽决定，见第 5.3.4 节。

这些是有效数据消费/写请求次数，不是固定端到端延迟。配置阶段、预蓄、波前排空、供数反压、参数取数、输出转换及写响应等待均会影响实际周期数。

### 12.3 实测指标边界

本文未给出已测量的频率、端到端吞吐、面积或功耗保证值。不能把阵列几何、有效拍数或 perf 电平直接当作实测性能。

后续报告实测值时必须说明编译配置、工作负载、时钟频率、存储响应/反压条件、工具及适用的工艺条件，并按第 11.3 节区分 valid 周期与真实事务数。

## 13. 集成与使用规范

### 13.1 基本使用流程

本节四路组批、响应 laneId/排空、拍前读、START 仲裁及 bit10 诊断相关用法已验证；数值与 DMA 软件义务不变。

1. 复位后确认 `inFlight=0`；校验 `M≥1、Nlogical≥1、K≥1`，计算 `Nprog=ceil(Nlogical/T)×T`；
2. 按 `Nprog` 准备 A、B、C 和可选 scale/bias/requant 缓冲区，并完成 B 转置、4-bit 打包和补齐列；
3. 写入 M/Nprog/K、A/B/C 地址和 CONTROL。可用任意稀疏 lane 子集同拍组批，允许混合读写；按本批 fire lane 递增接收所有读响应/写 ACK，以 laneId 匹配并逐条确认 `resp.err=0`。所有响应排空前不能提交下一批，最后响应 fire 后下一拍可接新批；
4. MX 运行写入 ScaleA/ScaleB/ScaleC 地址（索引 `0x06/0x07/0x08`）；
5. bias 运行写入 BIAS_ADDR（`0x09`）；
6. requant 运行写入 RQ_M_ADDR（`0x0A`）和 RQ_PARAM（`0x0B`）。配置批应避免跨 lane 同址写；合法冲突不令 ACK 报错，低 lane 的冲突字段可能不生效，其它字段仍可生效，不能认为 REG_WRITE2 两项必定一起获胜；
7. 在上一任务已结束后读取并处理旧 IRQ_RAW/ERROR_CODE，向 IRQ_CLEAR 写 `0x000007FF` 清旧 pending（含 bit10），确认清除写 ACK 并排空本批；需要电平中断时将 IRQ_MASK 的相应位写 0，复位默认全部屏蔽。CLEAR 推荐只由一个 lane 提交；同批多 lane CLEAR 不 OR，最高 lane 的 CLEAR=0 也会阻挡低 lane。需要确认清后值时在后续批读取，同批读是清除前快照；
8. 向 START（`0x10`）写非零值并确认 `resp.err=0`；建议每批仅一个非零 START 候选，确保预期启动命令与元数据。多个合法非零 START 时只有最高 lane 的 PC/tag 生效，本批仅启动一次；零 START 不触发也不阻挡。ACK 只确认接口接受，start 在接受后的下一拍产生，不等待响应消费。可按第 6.1 节用配置+START 双写或跨 lane 同批配置/START 提交最后的配置，本批所有 winning 配置都用于此次启动；
9. 轮询 IRQ_RAW，或在 irq 通知后读取 IRQ_RAW；不要只等 irq、busy 变化或任一非零 RAW 就认定完成。配置错误位 1 置位时读取 ERROR_CODE，并确认 `inFlight=0`；该路径不等待完成位。仅 DMA 错误位 `[9:2]` 置位时记录错误但继续等待 IRQ_RAW[0] 与在途结束，不提前回收缓冲区。bit10 是写仲裁冲突诊断，不是配置检查失败或完成，不改变 err、ERROR_CODE 或 inFlight；应核对实际 winning 配置与预期是否一致，不能因该 IRQ 提前回收缓冲区或在途重配；
10. 完成位 0 置位后仍检查全部错误源；错误任务不能据完成位判为结果有效。若曾出现 WRITE_CONFLICT，另须依据逐地址仲裁核实实际配置是否符合软件意图，不能仅以全部 ACK 成功替代这一核实。无已上报错误的任务在满足输出存储/cache 一致性及既有 DMA/ScaleC 集成约束后消费 C；完成不扩展未标记内存错误的检测能力；
11. 保存诊断信息后按需 W1C 清 pending；清 bit 1 同步清 ERROR_CODE，清 bit10 清冲突记录，fresh event 同拍优先于 W1C。若执行尚在途清错误位，软件须保留已观察错误记录，不能因此把该任务最终视为无错误。

### 13.2 重复执行

IRQ_RAW（含 WRITE_CONFLICT）与 ERROR_CODE 跨启动保持；没有自动清事件的启动操作，也没有粘滞 `status.done` 字段。四路批间衔接相关用法已验证。重复执行流程为：

1. 确认 `inFlight=0`，并已处理上一任务的完成/配置错误及 DMA 错误，保存所需 bit10 诊断；不得在仅看到早期错误 IRQ 后立即启动下一任务；
2. 保存旧 IRQ_RAW/ERROR_CODE 诊断，由一个 lane 写 IRQ_CLEAR 清除已处理事件，确认清除写 ACK 并排空该批；需要时在后续批读回 IRQ_RAW 确认不存在旧完成 pending，不用同批拍前读来确认本拍清除。同拍新事件会保持置位，不得当作清除失败而忽略；
3. 更新配置并按 laneId 确认各 ACK，避免非预期同址冲突；在非零 START 写之前完成旧事件清除。START 本身不清 RAW 或 ERROR_CODE，配置与 START 可按第 6.1 节同批但仅使用 winning 配置；
4. 在上一批最后响应 fire 后的下一拍或更晚提交下一批命令；提交下一次非零 START 写并确认 ACK，通过新的 IRQ_RAW 完成/配置错误事件与 `inFlight` 判断结束，再检查错误源与 WRITE_CONFLICT 诊断。

轮询流程不依赖 mask；中断流程必须为所需源解除屏蔽并在通知后读 RAW 分辨来源。若选择保留旧事件，RAW 位不区分新旧任务且 ERROR_CODE 保留首个未清配置错误，不能据这些旧值完成新任务判定。所有 REG_READ、写 ACK、合法 no-op 和拒绝响应均参与本批排空；pending 读未 fire 时不允许任何 lane 接收下一批。

### 13.3 地址与缓冲区要求

软件必须保证：

- C 基地址按 `cDmaExtW/8` 对齐；
- bias/rqM 基地址按 4 byte 对齐；
- 正向 W4A8 的 B 基地址满足错误检查要求；
- gather 可能访问的相邻对齐字均位于合法存储区；
- B、C、bias、rqM 和 scale 均按 `Nprog` 提供完整补齐列；
- 在拍前 `inFlight=1` 时不修改配置寄存器、不提交非零 START；硬件会逐命令以接口错误拒绝这类命令，不提供排队；本批其它合法 lane 不受拒绝命令阻挡（四路拍前检查/仲裁契约已验证）；
- 输出写回完成前，不回收或覆盖任何输入、参数和输出缓冲区；早期 DMA 错误 IRQ 不解除这一义务。

### 13.4 Cache 与一致性

HiveCore 接口本身不定义 cache coherence。若系统 DMA 与 CPU cache 不一致，软件必须在非零 START 写前 clean 输入和参数缓冲区，并在完成后 invalidate 输出缓冲区；具体操作由 SoC 平台定义。

## 14. 约束与实现限制

### 14.1 当前实现限制摘要

- 当前发布配置固定 MX 阵列几何 `T=16`；
- 单命令 M 受 C buffer 容量限制；
- `M/N` 必须大于 0，但 RTL 没有专用错误码；
- 编程值 N 必须补齐为 T 的整数倍，硬件不自动缩窄末 tile；
- FP8 不能直接作为 C 输出；
- 默认配置的 C block 只允许 16；
- W4A8 只支持 A8×B4，不支持 A4×B8；
- `AddrMode`、`warmup`、`warmdown` 当前仅可读写，不改变数据通路；
- 固定四路命令共享一个 bank/执行上下文，仅四 slots/pending 缓存一批响应；任意混合读写及稀疏 lane 同拍 fire，不要求连续前缀（四路相关契约已验证）；
- 接口不排队执行任务；拍前在途配置写与非零 START 逐命令拒绝，但 READ 与 IRQ 写仍可用，均须遵守单批响应未排空时所有 lane ready 低；读 pending 只在 resp.fire 清，最后响应 fire 后下一拍可接下批（已验证）；
- 写在 cmd.fire 当拍按目标地址仲裁提交，读取拍前快照不旁路同拍写或 STATUS/IRQ 新状态；响应背压时 data/err/laneId 稳定（已验证）；
- 合法写逐地址 lane3>2>1>0，落败冲突字段不生效、其它字段仍可生效且 err=0；REG_WRITE2 仅拒绝原子，不保证跨 lane 两项一起胜出；CLEAR 同 lane OR、跨 lane 最高 lane（含 CLEAR0），START 仅非零参与并合并为下一拍单个 start/最高非零 lane 元数据（已验证）；
- START 写 ACK 不表示计算完成或配置合法，运行期错误经 IRQ 而非 ACK 返回；
- IRQ_RAW/ERROR_CODE 不因 START 自动清除；完成位不表示无错误，错误 IRQ 不表示任务已终止；
- WRITE_CONFLICT bit10 仅记录跨 lane 已接受合法命令的同址实际写目标冲突，不改变 err、ERROR_CODE 或 inFlight；低 11 bit IRQ、高 21 bit 保留、reset mask=0x7FF、fresh event 优先于 W1C（已验证）；
- tracing 只保证复位后的首次执行；
- 仅握手响应上的 `rsp.err` 可作为 DMA 错误事件，未标记的内存错误不可检测，assert 不转为 IRQ；
- perf DMA 事件统计 valid 周期，不等价于事务数；
- ScaleC 写响应不得无限延迟，第 9.6 节的在途与反压限制不变。

### 14.2 不属于规格支持功能的项目

以下项目不得依据字段存在或相关功能 ID 推定为受支持能力：

- `AddrMode` 的 TILE 寻址、`warmup`、`warmdown` 的数据通路行为；当前仅保留字段读写；
- A4×B8 反向混配、FP8 C 输出、自动 N 尾 tile 缩窄写回；
- 执行任务排队、多批命令队列、四 lane 独立配置/执行上下文、shadow 配置组、错误 fail-fast/事务撤销、未标记内存错误检测、多任务可靠 tracing；
- 可配置命令 lane count 或寄存器 count、将命令 lane 解释为 MAC 数据 lane、同批响应未排空即接受下一批、读旁路同拍写、跨 lane REG_WRITE2 两项共同获胜保证、跨 lane IRQ_CLEAR OR；上述四路非目标边界已验证，不扩展既有计算/DMA 能力；
- 独立 IRQ ENABLE、自动启动清 IRQ、以完成位表示无错误或将断言转换为 IRQ；
- 内建 PMU 计数器、cache coherence、SiLU/GELU 或外部 Scheduler 功能。

## 附录：补充资料

### A.1 关键公式

```text
T            = arrayN × clusterM
nTiles       = ceil(N / T)
elemsPerLane = 4 for INT4/MXFP4
               2 for INT8/MXFP8/FP8
               1 otherwise
kPerBeat     = T × elemsPerLane
kTiles       = K / kPerBeat
Mmax         = cBufferDepth - T
```

MX 输入 scale：

```text
ScaleA[m, kt] = SA_BASE + m×(K/blockA) + kt×(kPerBeatA/blockA)
ScaleB[n, kt] = SB_BASE + n×(K/blockB) + kt×(kWindowBEffective/blockB)
scaleIndex    = floor(lane×step/block)

kWindowBEffective = kPerBeatA for forward MX W4A8
                    kPerBeatB otherwise
```

MX 输出 scale：

```text
ScaleC[m, nt] = SC_BASE + m×ceil(Nprog/blockC) + floor(nt×T/blockC)
```
