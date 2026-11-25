# WorkerBee 硬件规格

文档版本：`v6.1.0`

修订日期：`2026-11-25`

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
| v6.1.0 | 2026-11-25 | 兼容新增（对齐 Queen 通用 DSA 契约 §5.3/§5.5/§13.5/§13.10.5，Phase 2）：顶层新增 `deviceIdle` 强 idle 输出（纯组合电平：命令空 ∧ 无执行 ∧ 无在途内存事务 ∧ 无未回传 completion，覆盖 executor busy/osActive、inFlight、A/B/C 与 scaleA/scaleB/scaleC DMA busy、A/B/C/scaleB 工作 FIFO occupancy、bias 取数 FSM 与共享 ScaleA 物理口在途计数 biasPortBusy）；新增 `flushReq/flushAck` 四相恢复握手（req↑→置 flushing 并 block 新 RUN→等当前工作自然结束至 deviceIdle→ack↑→req↓→ack↓）。`startReady` 由 `!inFlight` 改为 `!inFlight && !flushing`。裁定边界：flush 不复位 RegIf、不抑制 completion（等待自然结束期间 done/configError 照常回传）、不做安全 abort、不触碰内部数据流。新增第 6.10 节与 WB-F028；第 14.2 节移除原 Phase 2 待办项。全量回归 23 套件 261 例通过；数据通路计算子模块 RTL 逐字节零 diff，DmaRdOnly 仅引出既有 state 的 io_busy 观测口，顶层为 deviceIdle/flush 集成层 | WB-F028（新增）；WB-F021、WB-F022（startReady 门控）；6.8、6.10（新增）、14.2 |
| v6.0.0 | 2026-11-25 | 不兼容端口变更（对齐 Queen 通用 DSA 契约，queenHarewareInterface.md §13/§14）：`cmd` 改为单 ready 并行 native beat `Flipped(Decoupled(Vec(4, Valid(QueenNativeCmd))))`，写 beat 恒可收、RUN 取 startReady、READ 取 resp.ready，RUN/READ 必须独占 beat；命令 payload 改用 QueenNativeCmd（op=WRITE1/WRITE2/READ/RUN，swTag 32-bit，无 pc）；`resp` 改为 `Decoupled(QueenDsaRegResp{data,err})`，只承载 QRD 读数据且与被接受读同拍，无 laneId、写不产生响应；新增 per-lane 写错误脉冲 `wrErr` 与完成流 `completion: Valid(QueenDsaCompletion)`——done/configError 不再置 IRQ，由 completion 携带 error/code/swTag/hwSignal（error=1 ⟹ hwSignal 全 0）；删除手写 WorkerBeeRegIf 类，直接例化 QueenDsaRegIf(12,2,8) 加纯组合译码层；删除 `cfgCredit` 端口（内部 startReady=!inFlight）；地址重映射：status 0x40、errorCode 0x41（替代旧 0x45），中断改 IntrRFMS4（0x60/0x64/0x68/0x6C），hwSignal 0x80/0x81、traceTag 0xA0，旧 IRQ 0x41–0x45、IRQ_CLEAR 0x44 与 WRITE_CONFLICT bit10 全部废除；中断源改为 8 位 DMA/总线错误 `intrSrc=dmaErrors(8)`（对原计划 intrWidth=1 的有意偏离，依据 §13.4 保留 DMA 错误 IRQ 覆盖）；普通配置写跨 lane 同址 last-wins，W1C/W1S 跨 lane 合并。CONTROL 位布局、数值、DMA、perf 与 errCode 判据/优先级契约不变；deviceIdle 强 idle 与 flush 四相握手留待 Phase 2。全量回归 22 套件 255 例通过，数据通路 RTL 零 diff | WB-F021、WB-F022、WB-F023；WB-F025/WB-D002（tracing 元数据）、WB-P006（口径）；4.2、5.1.5、6、8、9.1–9.2、10.4–10.5、11、13、14 |
| v5.1.0 | 2026-11-20 | 细化 FP/MX 默认归约实现：默认归约器由窄化多操作数加法器改为三槽 lazy-40 `{C,S1,S2}`（`W=19` 窗口），MXFP4 四元素块和改由块内整数前端直接从 nibble 精确计算（单积 ≤144、块和 ≤576，11-bit 带符号，不经 fp32 乘积域），五槽 `{C,L0,L1,L2,L3}` 串行基线退为显式关闭窄化时的逐位参考、不属于默认交付；明确三段精度契约（J1 窗口无损逐位精确 / J2 相对误差 ≤`2^-18` / J3 宽幅相消绝对界兜底）及窗口参数契约（`W∈[8,28]` 默认 19、BF16 需 `W≥16`、`cEffW≥40`、J2 相对界 `2^-(W-1)`，编译期 require 强制）；新增 MXFP4 块级边界语义（underflow `e2+scale<2` 元素冲刷保号零、tiny-scale `scaleA+scaleB<121` 整块冲刷真零、块溢出 `scaleA+scaleB−120>254` 饱和到有限 ≈fp32max、非 Inf 非 sticky）；声明 MXFP8/BF16 三槽与五槽掩零逐位等价，MXFP4 单拍数值相等但 lazy 切分不同 ⟹ 多拍累加可能分叉（仍受精度契约约束）。既有非逐位归约契约、格式集、寄存器映射与端口位宽不变 | WB-F005、WB-F006、WB-F007、WB-F009、WB-F011、WB-F026；5.2.5、5.2.6 |
| v5.0.0 | 2026-11-17 | 不兼容配置通路变更（对齐 Queen 接口语义）：WorkerBeeOp 新增 `RUN`（编码 3，原保留），启动不再经寄存器写，原 `START`=`0x10` 删除；配置寄存器拆为 shadow/live 两组——QWR/QRD 恒读写 shadow 且执行期不受限，`RUN` 接受拍把 shadow 整组 dump 进 live 并启动，`io.regFile` 恒取 live，故同批配置写属于下一次提交；新增 1-bit 电平输出 `cfgCredit`（=`!inFlight`）只门控 `RUN`，本批仅授权最低 valid 的 `RUN` lane，tracing 元数据改取该命令；命令地址由 6-bit 扩为 10-bit，状态/IRQ 区迁至 `0x40–0x45`（STATUS `0x40`、IRQ_RAW `0x41`、MASK `0x42`、MASKED `0x43`、CLEAR `0x44`、ERROR_CODE `0x45`），旧 `0x1E`/`0x30–0x34` 成为保留空洞；执行期配置写不再以 `resp.err` 拒绝。CONTROL 位布局、数值、DMA、IRQ 位定义与首次 tracing 配对限制不变。`hwSignal` 记录为待办，本版不实现。四路接口专项 26 例通过 | WB-F021、WB-F022、WB-F025、WB-D002；6.1–6.3、6.8（新增）、8.3、8.6、8.8、9.2、13.1–13.4、14.1–14.2 |
| v4.3.0 | 2026-11-16 | `loadWMode=1` 的 WS 权重装载改用独立水平 W 链（不再借 A 链），每 PE 驻留双权重 bank：写侧选择广播锁存只写非在读 bank，读侧选择随激活波前逐 PE 对角传播，使下一窗口权重装载与当前窗口计算/排空重叠；未完成时回退完整装载窗口。回灌就绪改由供数门控查 C buffer 读出口 valid 保证，有效稳态周期下界订正为 `2T`。perf 新增 `cRowIn`、`rdSelFirst`、`rdSelLast` 三个组合电平抽头 | WB-F002、WB-F024 / WB-D001；5.1.2、11.3、12.2 |
| v4.2.0 | 2026-11-09 | 增加显式 OS-M1 模式：CONTROL[31]、M=1/loadWMode=1 守护与 errCode 14；方形阵列按 arrayN×arrayM 扁平组织，首行本地累加、A 右传、B/ScaleB 按列升序送入双槽窗口；定义 Run/Tail、自然列序 bias/requant、一次提交与完整 Nprog 写回、模式间数值关系及 OS 性能事件。CONTROL[27] 为 osFmtKeep，软件使用基线值 0；默认交付保持一套全格式 RTL | WB-F027、WB-P011（新增）；WB-F001、WB-F002、WB-F014、WB-F016–WB-F019、WB-F023、WB-D001 |
| v4.1.0 | 2026-10-29 | BF16 升格为默认 A/B 输入格式：新增专用窄乘法器分支、单元素 lane 几何（`K%T=0`）、BF16 混配 errCode 3 守护，以及 BF16 特殊值（输入与乘积下溢冲刷为零、上溢饱和、无 IEEE Inf/NaN 语义）定义；明确 BF16 两种 B 装载模式均合法，errCode 4 不含 BF16。FP16 保持非默认输入，与 BF16 独立。寄存器映射、端口位宽与既有格式的数值契约不变 | WB-F026（新增）；WB-F011、WB-P001、WB-P002（文案） |
| v4.0.0 | 2026-10-26 | 不兼容命名变更：GEMM 模块由 HiveCore 更名为 WorkerBee（“Hive”名称改指 NPU core）；顶层、配置、命令/响应/DMA/状态 Bundle 与 HiveCore* 子模块改用 `WorkerBee*`，其余 Hive* 子模块改用 `Wb*`，RTL 模块前缀由 `Hive_` 改为 `Wb_`，源码目录改为 `src/workerbee`；Feature ID 前缀由 `HIVE-` 统一改为 `WB-`，编号与语义不变，旧 ID 不再使用，本表历史记录中的模块名与 ID 已同步改写为新名称；硬件行为、端口位宽、寄存器映射和数值契约不变 | WB-F001–WB-F025、WB-P001–WB-P010、WB-D001–WB-D002（仅更名） |
| v3.0.1 | 2026-10-26 | 按通用规格模板将第 3 章拆分为功能、性能和 Debug 三类独立清单；性能清单按通路位宽、每拍接受/发射能力和并行度分类，原 WB-P003/P004/P005 的复用、credit/FIFO 与 fused epilogue 机制分别归回既有功能条目，旧 P ID 保留为分类记录且不复用；新增产品内建观测能力的 Debug 分类 ID，不改变硬件接口或行为；既有 WB-F024/F025 作为历史稳定 ID 保留并分别关联 Debug 分类 ID | WB-P001、WB-P002、WB-P003–WB-P005（分类记录）、WB-P006–WB-P010、WB-D001–WB-D002；WB-F002、WB-F011–WB-F020、WB-F024、WB-F025 |
| v3.0.0 | 2026-10-23 | 不兼容端口变更：固定四路 WorkerBeeCmd Stream 共享单一寄存器 bank，单 resp 新增 2-bit laneId；采用四 slots/pending 位图的单批响应缓存、拍前读快照与逐地址跨 lane 写仲裁，START 合并为下一拍一次启动并使用胜出 lane 元数据；新增 IRQ_RAW[10] WRITE_CONFLICT，IRQ 低 11 bit 有效、复位 mask 为 0x000007FF；寄存器地址与 CONTROL 布局、数值、DMA、首次 tracing 配对限制不变。四路接口专项 23 例、既有计算/DMA 回归 51 例及测试编译通过 | WB-F021、WB-F022；WB-F025（仅 START 元数据选择） |
| v2.0.0 | 2026-10-23 | 不兼容重设计寄存器命令接口：采用 6-bit 索引和 START 寄存器，重映射配置与 STATUS；由独立 WorkerBeeRegIf 管理在途保护、原子双写、分源粘滞中断及首个未清配置错误码；DMA 错误改为响应握手时立即捕获，tracing 元数据改取启动写命令；数值、布局、计算、DMA 搬运流程与性能事件口径不变 | WB-F021–WB-F023、WB-F025 |
| v1.1.0 | 2026-10-23 | 按通用硬件规格重组为 14 个编号章节，第 7 章为数据表示规范；补充分立的时序/复位说明与性能/资源口径；更新功能清单引用，功能 ID 与既有技术契约不变 | WB-F001–WB-F025（章节引用） |
| v1.0.1 | 2026-10-22 | 版本控制前置为文首章节；Feature List 调整为第 3 章；原第 3–14 章顺延为第 4–15 章，并同步更新章节引用；功能 ID 与技术契约不变 | WB-F001–WB-F025（仅章节引用） |
| v1.0.0 | 2026-10-22 | 建立首个编号规格基线，覆盖当前计算架构、格式、布局、接口、寄存器、DMA、epilogue、错误码和软件约束；新增带稳定 ID 的 Feature List 及版本控制章节 | WB-F001–WB-F025 |

## 1. 文档说明

### 1.1 目的

本文定义 WorkerBee 的软件可见行为、计算语义、存储布局、接口协议和配置约束。WorkerBee 支持固定四路并行配置 beat（单 ready）共享一个执行上下文，以及显式选择的 WS 和 OS-M1 数据流；默认 A/B 输入包含 BF16 和各受支持低位宽格式。OS-M1 使用 CONTROL[31]，不增加顶层命令或 DMA 端口。本文不承诺规格之外的功能。带 ID 的 Feature List 见第 3 章；文档版本规则与修订记录见文首“版本控制”章节。

### 1.2 范围

本文覆盖：

- WorkerBee GEMM 阵列及其执行顺序；
- INT、FP8、MXFP8、MXFP4、BF16 数据格式；
- A、B、C、scale、bias 和 requant 参数的存储布局；
- 命令、读响应、写错误、完成、DMA、状态、中断和性能事件接口；
- 寄存器映射、运行期错误检查及软件编程模型。

本文不定义 Queen 指令编码、外部 Scheduler、ForagerBee、LLC、CPU cache 管理实现或系统物理地址映射。

### 1.3 规范性用语

- “必须”表示违反该要求会导致命令被拒绝、结果未定义或与本规格不一致。
- “应”表示集成或软件应遵循的推荐约束。
- “默认配置”指未显式覆盖 `WorkerBeeConfig` 参数时的配置。
- `T` 表示阵列方形边长（行数 = 输出列数）`totalN = arrayN`，且 `arrayM == arrayN`；默认 `T=16`。
- `Nlogical` 表示软件需要的有效列数，`Nprog` 表示写入 N 寄存器并按 T 补齐后的列数；除明确写出 `Nlogical` 外，后续公式中的 `N` 均指编程值 `Nprog`。
- DMA 与存储基地址均为字节地址；命令中的寄存器地址为索引，不是字节偏移。
- 除非另有说明，多字节数据采用低地址存放低有效字节的布局。

## 2. 模块概述

WorkerBee 是面向矩阵乘法和量化推理的 GEMM 单元，支持 weight-stationary（WS）和单行输出驻留（OS-M1）两种数据流，计算：

```text
C = Epilogue(A × B + Bias)
```

A 的逻辑形状为 `M×K`，B 的有效形状为 `K×Nlogical`。由于硬件要求 N 维按阵列宽度补齐，实际编程和存储形状为 B=`K×Nprog`、C=`M×Nprog`。WS 以 K 和 N 作为空间轴、M 作为时间轴；每个 `(N tile, K pass)` 装载一次权重，并连续处理全部 M 行。OS-M1 仅用于 `M=1、loadWMode=1`，以首行 T 个 PE 分别驻留一个输出列的部分和，沿时间轴完成全 K 累加；不根据 M 自动切换模式。

当前默认输入能力为：

- INT8、INT4；
- MXFP8 E4M3、MXFP8 E5M2；
- MXFP4 E2M1；
- 非 MX FP8 E4M3、FP8 E5M2；
- BF16。

当前实现还提供 INT32、INT16、FP32、FP16、BF16 和 MX 格式的输出转换路径。FP8 不支持作为 C 输出格式；BF16 既是默认输入格式也是输出格式。FP16 仅为输出格式，不在默认输入能力内。

## 3. 功能清单（Feature List）

### 3.1 Feature ID 规则

Feature ID 按三类独立编号：

| 类别 | 编号格式 | 描述对象 |
|---|---|---|
| 功能 | `WB-Fxxx` | 计算、数据搬运、接口及外部行为，包括 credit、预取、复用、融合和操作快路径等机制 |
| 性能 | `WB-Pxxx` | 各处通路的位宽与带宽、每拍接受/发射/完成能力，以及通路和阵列的并行度 |
| Debug | `WB-Dxxx` | 产品内建的性能观测、状态追踪等调试能力 |

ID 不是寄存器地址、指令操作码或错误码，不因章节移动或文案调整而重排、复用。新增条目使用对应类别中未分配的编号；移除条目保留原 ID 并标注“已移除”。性能和 Debug 条目只索引已存在的能力，不把目标值、未来计划、普通状态/错误回执、测试平台统计或仿真断言列为 feature。性能条目不得仅复制功能机制名称，分类更正时记录旧 ID 与对应功能 ID 的去向且不复用旧 ID。

`WB-F024`、`WB-F025` 是 v1.0.0 已发布的历史稳定 ID，因此继续保留在功能清单；从 v3.0.1 起分别以 `WB-D001`、`WB-D002` 作为类别清晰的 Debug 索引。新增分类 ID 不重复定义接口契约，详细语义仍以所引用章节为准。

v4.0.0 起 Feature ID 前缀由 `HIVE-` 改为 `WB-`，`HIVE-Xnnn` 与 `WB-Xnnn` 一一对应、编号不变；`HIVE-` 前缀不再用于本模块。

### 3.2 功能清单

| Feature ID | 功能 | 状态 | 能力与主要约束 | 规格章节 |
|---|---|---|---|---|
| WB-F001 | Weight-stationary GEMM | 已实现 | T×T PE；K/N 为空间轴、M 为时间轴；循环为 N tile → K pass → M 行 | 4.1、5.1.1–5.1.2 |
| WB-F002 | 多 K pass 内部累加 | 已实现 | WS 首 pass 使用零或 bias 基底，后续回灌 C FIFO；OS-M1 以首行本地反馈跨 K 窗口延续；均在全 K 完成后写回 | 5.1.3、5.1.6 |
| WB-F003 | INT8 打包乘加 | 已实现 | 默认每 lane 两元素、每拍覆盖 2T 个 K 元素；有符号累加，溢出绕回 | 5.1.1、5.2.3、7.2 |
| WB-F004 | INT4 打包乘加 | 已实现 | 默认每 lane 四元素、每拍覆盖 4T 个 K 元素；二补码 nibble 按递增 K 打包 | 5.1.1、5.2.3、7.2 |
| WB-F005 | MXFP8 计算 | 已实现 | E4M3/E5M2 可交叉组合；使用 E8M0 block scale；必须 loadWMode=1 | 5.2.1、5.2.5、5.2.7、7.4、7.6 |
| WB-F006 | MXFP4 计算 | 已实现 | E2M1 四元素打包；保留 ±0.5 次正规输入；必须 loadWMode=1 | 7.2、5.2.5–5.2.7、7.4、7.6 |
| WB-F007 | 非 MX FP8 计算 | 已实现 | E4M3/E5M2 可交叉组合；单位 scale 注入且不读取输入 scale；必须 loadWMode=1；不能输出 FP8 | 5.2.1–5.2.3、5.2.5–5.2.6 |
| WB-F008 | INT W4A8 | 已实现 | 仅 A=INT8、B=INT4；B 经 DMA stretch 符号扩展；默认 K%64=0、B_ADDR%32=0、loadWMode=1 | 5.2.1、5.3.2、10.2 |
| WB-F009 | MX W4A8 | 已实现 | 仅 A=MXFP8、B=MXFP4；B 经 DMA stretch 展开；ScaleB 按 A 的 K 窗口步进；默认 K%64=0、B_ADDR%32=0 | 5.2.1、7.6.2、5.3.2 |
| WB-F010 | A/B 独立 MX block scale | 已实现 | A/B 独立选择 block16/32；scale 按 K 分组并使用 E8M0 编码 | 5.2.7、7.4、7.6.1–7.6.2 |
| WB-F011 | 浮点/MX 输出转换 | 已实现 | FP32/FP16/BF16 及 MX 输出（BF16 同时是默认输入格式，FP16 不是）；支持 RNE/RTZ/RDN/RUP/RMM；内部归约不保证 IEEE 逐项 FP32 bit-exact | 7.1、7.3、5.2.5–5.2.6 |
| WB-F012 | MX 输出量化与 ScaleC 生成 | 已实现 | 每 block 生成 E8M0 scale；默认 C block 仅允许 16；ScaleC 在途/反压限制必须满足 | 7.4、7.6.3、5.2.8、9.6 |
| WB-F013 | 整数输出截断 | 已实现 | INT32/INT16/INT8/INT4；先取 acc32 再按输出位宽截断；非 requant 模式不饱和 | 5.2.3、5.4.1 |
| WB-F014 | Per-channel fused bias | 已实现 | INT32 或 FP32 bias；每输出列一项；WS 首 K pass 注入，OS-M1 每 N tile 启动时播种一次；需要参数取数 DMA | 7.7.1、5.4.2、5.4.5 |
| WB-F015 | Fused ReLU | 已实现 | 只作用于有符号 acc32，在 requant/最终整数截断前钳零；非整数输出忽略使能 | 5.4.1、5.4.3 |
| WB-F016 | Fused requant | 已实现 | Per-channel 有符号乘数、全命令 shift/zero-point；输入为 acc32/relu32；half-up 舍入后饱和至 INT8/INT4；需要参数取数 DMA | 7.7.2、8.7、5.4.4 |
| WB-F017 | B 权重装载模式 | 已实现 | mode0 对应 K×Nprog row-major；mode1 对应预转置 Nprog×K row-major；格式约束决定可用模式 | 5.1.2、7.5.2 |
| WB-F018 | 自主 A/B/C DMA | 已实现 | 按 N/K 窗口遍历；A 每 N tile 重读；C 按完整 T 列写回，软件必须编程 Nprog | 7.5、5.3、9.3–9.4 |
| WB-F019 | 非对齐读 Gather | 已实现 | A/B/ScaleA/ScaleB 非对齐逻辑拍由对齐物理读拼接；相邻覆盖地址必须可访问；W4A8 stretch 使用独立半片选择路径 | 5.3.2–5.3.3 |
| WB-F020 | DMA 握手与读 FIFO credit | 已实现 | 请求/响应 ready-valid；读请求预留 credit；读响应必须同序且每请求恰有一个响应；ScaleC 另有在途限制 | 6.4、9.3–9.4、9.6 |
| WB-F021 | 寄存器命令接口 | 已实现 | 单 ready 四路并行 native beat（QueenNativeCmd：WRITE1/WRITE2/READ/RUN，swTag 32-bit、无 pc）共享 shadow/live 两组配置 bank；写 beat 恒可收，RUN 取 startReady、READ 取 resp.ready，RUN/READ 独占 beat；10-bit 索引，QWR2 相邻配对写；普通写跨 lane 同址 last-wins，W1C/W1S 跨 lane 合并；非法写走 per-lane wrErr 脉冲；QRD 读数据同拍经 resp 返回；RUN 接受拍把 shadow 整组提交进 live 并同拍启动一次；写/RUN 不产生响应，接受不表示计算完成 | 4.2、6.1–6.3、6.8–6.9、8、9.2 |
| WB-F022 | 状态与完成/错误中断 | 已实现 | busy、inFlight、progress 不变；done/configError 经 completion 流回传（error/code/swTag/hwSignal，error=1 ⟹ hwSignal 全 0），不再置 IRQ；IRQ 仅承载 8 位 DMA/总线错误源，IntrRFMS4（IntrRaw/IntrForce/IntrMask/IntrStatus @0x60/0x64/0x68/0x6C），粘滞捕获、fresh event 优先于 W1C；DMA 响应握手错误逐事件捕获，不等待 done；正常完成不表示无 DMA 错误 | 6.5–6.6、6.9、8.6、8.8、11.1–11.2、10.5、5.1.5 |
| WB-F023 | 运行期配置检查 | 已实现 | 判据与优先级不变；配置错误经 completion.error/code 上报并清除 inFlight，errorCode 寄存器（0x41）锁存最近配置错误码供 QRD 调试、下次 start 清零；M/N 非零、Nprog、缓冲区可访问性等仍是软件义务 | 8.8、10 |
| WB-F024 | 性能事件输出（历史 ID） | 已实现 | 历史功能索引；类别化 Debug 索引为 WB-D001 | 11.3 |
| WB-F025 | 首次执行 Tracing（历史 ID） | 已实现 | 历史功能索引；类别化 Debug 索引为 WB-D002 | 6.3、9.2、11.4 |
| WB-F026 | BF16 计算 | 已实现 | 专用窄乘法器，不复用 MX remap；每 lane 单元素；输入与乘积下溢冲刷为零、上溢饱和为最大有限值；WS 两种 B 装载模式均合法，OS-M1 要求 mode1；不得与非 BF16 格式混配；可作为 C 输出格式 | 5.2.4、7.1–7.2、10.2 |
| WB-F027 | OS-M1 单行输出驻留 | 已实现 | CONTROL[31] 显式使能，仅 M=1/loadWMode=1；首行 T 个 PE 本地累加，A/ScaleA 右传，B/ScaleB 升序送入每列双槽窗口；Run/Tail 按真实消费推进，全 K 后自然列序提交一次，沿用 epilogue 和完整 Nprog 写回 | 4.1–4.2、5.1.6、5.2.9、5.4.5、7.5–7.7、8.5、10.2 |
| WB-F028 | 强 idle 与 flush 恢复握手 | 已实现 | `deviceIdle` 纯组合强 idle 电平（命令空 ∧ 无执行 ∧ 无在途内存 ∧ 无未回传 completion，覆盖 A/B/C/scale DMA busy、工作 FIFO occupancy、bias 取数与共享 ScaleA 口在途计数）；`flushReq/flushAck` 四相恢复（req↑→block 新 RUN→等自然结束至 deviceIdle→ack↑→req↓→ack↓）；不复位 RegIf、不抑制 completion、不做安全 abort | 6.8、6.10、14.2 |

### 3.3 性能清单

记 `T=totalN`、`DA=T×aW`、`DB=T×bW`、`DC=cDmaExtW`；默认 `T=16`、`DA=DB=DC=256 bit`。下表给出接口峰值或源码结构推导的局部上限，**不是端到端实测吞吐或持续带宽保证**；有效计算拍不含权重装载、预蓄、排空和停顿拍，物理 DMA 峰值不扣除写 strobe 未选中的字节。

| Feature ID | 通路/性能项目 | 实现状态 | 位宽、带宽或接受/发射/完成率 | 并行能力、成立条件与瓶颈 | 关联功能/契约章节 |
|---|---|---|---|---|---|
| WB-P001 | WS 阵列空间并行与有效计算拍 | 已实现 | `T×T` PE；每个有效 A 拍同时推进 `T` 个输出列，默认 16 列、256 个 PE；非打包 16-bit 格式（BF16）每个有效 A 拍覆盖 `T` 个 K 元素、`T²` 次乘加贡献，默认 16/256 | 每个 `(N tile, K pass)` 先消费 `T` 个有效 B 拍、再消费 `M` 个有效 A 拍，两阶段不重叠；供数、回灌、参数取数、波前排空和反压降低实际利用率 | WB-F001、WB-F002、WB-F017、WB-F026；4.1、5.1、12.2 |
| WB-P002 | WS 低位宽打包计算通路 | 已实现 | 每个有效 A 拍覆盖：INT8/MXFP8/FP8 及 W4A8 混配为 `2T` 个 K 元素、`2T²` 次 lane-element 乘加贡献，默认 32/512；同格式 INT4/MXFP4 为 `4T` 个 K 元素、`4T²` 次贡献，默认 64/1024 | 同拍并行来自每个 16-bit lane 的 2/4 元素打包，不表示完成 512/1024 个独立输出；W4A8 的 4-bit B 经 stretch 后跟随 8-bit A 的 2T 消费口径。仅在相应格式、K 对齐、合法混配和 loadWMode 条件下成立。BF16 非打包、每 lane 单元素，不属于本项，见 WB-P001 | WB-F003–WB-F009；5.1.1、5.2、7.2、12.2 |
| WB-P006 | 寄存器命令接受与响应通路 | 已实现 | 写 beat 最多 `4 条/cycle` 且恒可收；RUN/READ 独占 beat、各最多 `1 条/cycle`；QRD 读响应与被接受读同拍、最多 `1 条/cycle`；completion 每拍至多 `1 条` | 四 lane 共享一个寄存器 bank 和一个执行上下文；无响应缓存与批间反压，写不占响应通道；RUN 在 inFlight=1 时整拍停等，READ 由 resp.ready 反压 | WB-F021、WB-F022；6.1–6.3、6.9、9.2、12.1 |
| WB-P007 | A/B 外部读数据通路 | 已实现 | A、B 各自请求至多 `1 条/cycle`、响应至多 `1 beat/cycle`；每口数据宽 `DA`/`DB`，默认各 `256 bit = 32 byte/cycle`，两口聚合接口峰值默认 `64 byte/cycle` | A/B 为独立端口，可同拍握手和预取；WS 消费阶段按 B 后 A 串行，OS-M1 可在当前窗口消费时预取下一窗口。非对齐 gather 可令一个逻辑拍需要两个物理读，FIFO credit、存储响应和反压会降低持续供数率 | WB-F017–WB-F020；5.3.1–5.3.3、6.4、9.3、12.2 |
| WB-P008 | C 外部写回通路 | 已实现 | 单口宽 `DC`，请求至多 `1 beat/cycle`；默认 `256 bit = 32 byte/cycle` 物理峰值。默认每个 T 列行：4-bit 为 8 有效字节/1 拍，8-bit 为 16/1 拍，16-bit 为 32/1 拍，32-bit 为 64/2 拍 | 不跨行合拍；4/8-bit 行虽只占部分 byte lane，仍占一个物理请求拍。32-bit 行局部上限为 `0.5 row/cycle`，其余格式至多 `1 row/cycle`；C FIFO 供数、外部 ready、地址阶段和末尾响应排空会降低持续速率 | WB-F011–WB-F013、WB-F018、WB-F020；5.3.4、6.4、9.4、12.2 |
| WB-P009 | Scale 与 epilogue 参数通路 | 已实现 | 默认 ScaleA、ScaleB 读口各 `32 bit`、至多 `1 beat/cycle`；ScaleC 写口 `8 bit`、至多 `1 beat/cycle`；bias/rqM 在 ScaleA 物理口上各按 `32 bit`、至多 `1 item/cycle` 取数 | ScaleA 与 ScaleB 为独立读口，ScaleC 为独立写口；ScaleA、bias、rqM 共用一个物理口，不能并行，bias 与 rqM 同时使能时先后串行取各 `T` 项。ScaleC 仅用于 MX 量化输出并受在途深度和反压限制 | WB-F010、WB-F012、WB-F014、WB-F016、WB-F020；5.2.7–5.2.8、5.4、6.4、9.5–9.6 |
| WB-P010 | 输出转换行通路 | 已实现 | 输入为 `T×cEffW bit` 累加行，输出容器为 `T×32 bit`；输入接受和数据输出接口各至多 `1 row/cycle`，释放拍可背靠背接收下一行；默认一次并行处理 `T=16` 个输出元素 | 单行锁存，不能同时保有两行；非量化行需数据输出 fire 才释放，MX 量化行还需同一行 ScaleC 成功提交。下游 C/ScaleC FIFO 反压、requant 参数未就绪及 WB-P008 的多拍写回会降低端到端行率 | WB-F011–WB-F016；5.2.3–5.2.8、5.4、9.5、12.2 |
| WB-P011 | OS-M1 首行并行消费 | 已实现 | 每个逻辑 advance 至多推进 T 个首行 PE；BF16/8-bit/4-bit 每个有效列分别产生 1/2/4 个 lane-element 乘加贡献，默认稳态聚合为 16/32/64；W4A8 跟随 8-bit A 的双元素口径 | 首填充和尾波仅部分列有效；Run 必须有 A token 且全部 B/scale 窗口有效，Tail 只要求未完成列有窗口。其余 T×(T−1) 个 PE 不承担 OS 计算，不表示 T² 个 PE 同时工作 | WB-F027；5.1.6、11.3、12.2 |

分类勘误：原 `WB-P003`（多 K pass 片内部分和复用）、`WB-P004`（DMA credit/FIFO 解耦）、`WB-P005`（fused epilogue）属于功能机制，分别归入既有 `WB-F002`、`WB-F020`、`WB-F011–WB-F016`；旧 P ID 保留为分类记录、不得复用，**不表示 RTL 功能被删除**。

详细结构拍数见 12.2；端到端延迟、吞吐、频率、面积和功耗实测仍待补充，边界见 12.3。

### 3.4 Debug 清单

| Feature ID | Debug feature | 状态 | 观测能力、适用条件与主要约束 | 契约章节 |
|---|---|---|---|---|
| WB-D001 | 性能事件观测 | 已实现 | `perf` 输出 DMA、阵列、stall、epilogue 和 busy 的组合事件电平，不含计数器且不控制功能路径；DMA 为 `req.valid`，OS 阵列为真实 consume、Tail 豁免已完成空列；外部 PMU 采样累加并处理事件重叠 | 6.7、11.3 |
| WB-D002 | 首次执行 Tracing | 已实现 | `respForTracing` 无反压输出首次有效启动及其 done/configuration-error 事件，tag 取 startSwTag[7:0]、PC 恒 0；复位后仅首次执行保证配对，不能替代 completion/errorCode | 6.3、9.2、11.4 |

STATUS、中断/errorCode、completion 和普通 QRD 读响应属于功能接口，不列为专用 Debug feature；测试平台日志、波形开关和仿真断言也不构成产品内建 Debug 能力。

### 3.5 支持边界

三类清单只索引本规格已实现的能力；不支持项与实现限制见第 14 章。字段可读写不等价于存在相应数据通路功能，接口峰值或局部结构上限也不等价于已测得的持续吞吐。

## 4. 架构说明

### 4.1 阵列组织

阵列以 `arrayN×arrayM` 扁平网格例化，当前要求 `arrayN=arrayM=T`，共 `T×T` 个 PE。默认 T=16，每个 PE 仅有一份乘加单元，首行 T 个 PE 支持 WS/OS-M1 双模式，其余 PE 使用 WS 数据通路。

WS 模式中：

- x 方向对应 K-lane；
- y 方向对应输出 N 列；
- A 数据及 ScaleA 沿 y 方向传播；
- partial sum 沿 x 方向归约；
- B 权重锁存在 PE 内。

OS-M1 模式中，首行列 j 对应逻辑输出列 `nt×T+j`，A/ScaleA/有效 token 复用原右传链，B/ScaleB 从各列窗口供给。部分和在该列 PE 本地反馈；其余行和 WS skew 状态不随 OS advance 推进。两种模式不同时执行。

### 4.2 子模块与数据通路

| 子模块 | 职责 |
|---|---|
| QueenDsaRegIf（Queen 通用寄存器前端，直接例化） | 消费单 ready 四路 native beat：shadow/live 两组配置寄存器（numCfgRegs=12）、状态区接入（numStatusRegs=2）、同拍 QRD 读响应、per-lane wrErr、RUN 提交与同拍 start/startSwTag/hwSignal 组合输出、IntrRFMS4 中断块（intrWidth=8）与公共契约区 hwSignal/traceTag 寄存器。原手写 WorkerBeeRegIf 类已删除，`WorkerBeeRegIf.scala` 仅保留设备侧地址对象 |
| 配置译码层（顶层纯组合） | 把 QueenDsaRegIf 的 cfgRegs（live 组）按第 8.4–8.7 节位布局反演为 WorkerBeeRegister，供 CalcConfig/DMA/Executor/输出转换消费；CalcConfig 与 Executor 零改动 |
| 完成/上下文采样（顶层） | start 当拍采样 startSwTag/hwSignal 进上下文寄存器，维护 inFlight 与 errorCodeReg；done/configError 时产生 completion（见第 6.9 节），并把 8 位 DMA/总线错误接入 intrSrc |
| CalcConfig | 检查运行配置并派生执行几何与地址步长 |
| Executor | 控制 WS 权重装载、A 行消费、K pass 回灌；OS 配置、播种、一次提交及最终写回等待 |
| Scratchpad | 提供 A、B、C/partial-sum FIFO |
| WbComb | 完成 WS 空间归约或 OS 首行本地累加，复用同一乘加单元与输出转换入口 |
| WbOsM1Sequencer | 捕获 A/ScaleA 窗口并逐 lane 串行化，协调 Run/Tail 的统一 advance |
| WbOsM1Feeder | 按升序列号将 B/ScaleB 原子写入每列双槽窗口，按列消费推进读 lane |
| A/B 读 DMA、C 写 DMA | 搬运矩阵数据 |
| Scale 读写 DMA | 搬运输入/输出 scale；ScaleA 物理口另供 bias/rqM 取数 |
| WbOutCvt | 完成输出格式转换、整数 ReLU 与 requant |

顶层向 QueenDsaRegIf 提供各 DMA 的 `rsp.fire && rsp.err` 分源事件（8 位 intrSrc）、Executor busy/progress 与 inFlight 拼成的 status 字（0x40）及 errorCodeReg（0x41）；Executor `done` 与 CalcConfig `errPluse`/errCode 不再进入寄存器前端，由顶层直接生成 completion 并维护 inFlight/errorCodeReg。FIFO occupancy 由顶层直接输出到 `io.status`。共享 ScaleA 物理口的响应错误按仲裁器当前响应 owner 归类为 ScaleA、Bias 或 RqM；WAIT/IDLE 状态仍归 ScaleA，具体映射见第 10.5 节。

QueenDsaRegIf 是配置/中断/公共契约寄存器的所有者；inFlight、完成上下文与 errorCodeReg 由顶层持有。顶层不得等到 Executor done 才采样 DMA 本地错误锁存状态。计算、DMA 请求/响应协议、数据布局和 perf 事件口径不因这一职责拆分改变。

功能执行语义见第 5 章；FIFO 深度和编译条件见第 8 章。

## 5. 功能行为规范

### 5.1 GEMM 执行

#### 5.1.1 循环顺序与执行几何

`osM1=0` 时 WS 执行顺序为：

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
| BF16 及其它单元素格式 | 1 | 16 |

M 不在 Executor 内再次切 tile。单次命令必须满足：

```text
M ≤ cBufferDepth - T
```

默认 `cBufferDepth=64、T=16` 时，单命令 `M≤48`。软件可通过多条命令对更大的 M 分块。

#### 5.1.2 权重驻留与装载

每个 `(nt,kt)` 消费 T 个 B 拍完成权重装载，并连续消费 M 个 A 拍。相同权重在该窗口内供全部 M 行复用。

- `loadWMode=0`：B 通过垂直 partial-sum 链装载；
- `loadWMode=1`：B 通过**独立水平 W 链**移位装载，并在最后一拍广播锁存。W 链不占用 A 链与 partial-sum 链。

MX、FP8 以及正向 INT W4A8 必须使用 `loadWMode=1`。

`loadWMode=1` 下每个 PE 驻留**两个权重 bank**（含各自的 block scale）。写侧选择广播全局锁存，只写入当前未被计算读取的 bank；读侧选择是随激活波前逐 PE 传播的电平，经与激活相同的行错位与列移位到达每个 PE，因此该 PE 恰在自己读取新权重首行的那一拍切换 bank。两侧时序语义不同，不可合并为单一全局 bank 选择。

由此权重装载窗口可与上一窗口的计算及排空重叠：下一 `(nt,kt)` 的 T 个 B 拍在当前窗口的激活供数期间预装载，稳态下不再单独占用装载状态。若预装载未能在排空结束前完成，控制状态机回退为完整的 T 拍装载窗口，数值结果不变、仅周期增加。`loadWMode=0` 与 OS-M1 不启用双 bank，两个选择信号恒为 0。

#### 5.1.3 多 K Pass 累加

- `kt=0`：partial-sum 基底为 0，或在开启 bias 时为对应输出列的 bias；
- `kt>0`：上一 K pass 的结果从 C FIFO 回灌；
- 只有最后一个 K pass 的结果进入输出转换和 C DMA；
- 中间 K pass 结果只在内部 C FIFO 中周转，不写回外部存储器。

#### 5.1.4 WS 执行状态机

`osM1=0` 时 Executor 使用五个状态：

```text
IDLE -> DRAIN -> LOAD_B -> LOAD_A -> NEXT_NK_TILE -> ... -> DRAIN -> IDLE
```

##### IDLE

- 清除运行游标；
- 计算 `nTiles` 和 `kTiles`；
- RUN 被接受当拍经 QueenDsaRegIf 产生一次 start、通过 CalcConfig 检查后，flush A/B/C 工作 FIFO；
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

WS 完成条件为：

```text
无下一窗口
AND 阵列无有效输出
AND C/ScaleC DMA 不忙
```

OS-M1 在最后 N tile 提交后，等待最终写回门释放且 C/ScaleC DMA 不忙；各列完成或累加行提交本身不产生任务 done，见 5.1.6。

满足时：

- Executor `done` 输出一拍脉冲；
- 顶层清除 `inFlight`，并在同拍产生一条 `completion`（`error=0、code=0`，携带 start 当拍采样的 swTag/hwSignal，见第 6.9 节）；done **不再置位任何 IRQ 位**；
- 若这是复位后的首次执行，tracing 输出 done 事件；
- Executor 返回 IDLE。

完成事件表示 Executor 到达上述排空条件，不代表任务无 DMA 错误，也不自动清除任何错误源。DMA 错误 IRQ 可先于 completion 出现，不终止正在执行的任务；软件必须区分错误事件与完成事件，见第 10.5、13.1 节。配置检查拒绝正常 GEMM 时，`inFlight` 由配置错误脉冲清除，同拍回传 `completion.error=1` 与错误码（见第 10.4 节），并把错误码锁存进 errorCodeReg（0x41）供 QRD 调试读取；该路径不产生 Executor done。

#### 5.1.6 OS-M1 生命周期与消费协议

OS-M1 必须显式设置 `osM1=1`，且满足 `M=1、loadWMode=1`。A/B 格式、K 粒度、block、地址和 epilogue 约束与 WS 共用；N 仍必须编程为 Nprog。`osM1=0` 使用 WS，不因 M=1 自动切换。

Executor 生命周期为：

```text
IDLE -> OS_SETUP -> OS_START -> OS_RUN
                   ^             |
                   +-- 下一 N tile提交
                                 |
                          最后 N tile提交 -> OS_WAITC -> IDLE
```

- **OS_SETUP**：基线 `osFmtKeep=0` 时广播 `fmt/bFmt/rnd` 共 T 拍，并清除首行部分和和有效 token；本任务的其余 N tile 不重复广播。
- **OS_START**：开启 bias 时等待本 tile 参数收齐，按自然列号播种，再启动 Sequencer；启动拍不消费 A。rqM 可与计算重叠取数，但必须在提交前就绪。
- **OS_RUN**：Sequencer 按下述 Run/Tail 协议完成全 K；Executor 等待所有列完成和输出提交条件，不为每个 K 窗口打开 C 写回门。
- **提交**：全部首行结果已注册、上一写回门已释放、C FIFO 可接受且本 tile rqM 已正式提交时，整行 `cPush.fire` 恰好一次。列 0 放在累加行最低位；随后清除首行部分和/完成状态，并为下一 N tile 重新读取 bias/rqM。
- **OS_WAITC**：最后 N tile 提交后，等待写回门释放、C 和 ScaleC DMA 空闲才产生任务 done。

每列拥有两个完整 B 窗口槽；一个槽包含 `T×bW` bit 数据、配对的 `scaleBW` bit scale 字和 K 窗口标识。默认槽数据为 256 bit、scale 字为 32 bit。MX 运行只在 B 与 ScaleB 均有效且目标列有空槽时原子入队；非 MX 不等待 scale。列窗口按 `j=0..T−1` 循环分发，不是独立于 B 的两套 scale ping-pong。

槽头按 lane 0 到 T−1 每次输出一个 bW-bit packed 字，只有该列真实 consume 时推进；第 T 次消费才释放整槽。一个槽供数时，另一槽可预取下一 K 窗口；槽已满则向上游反压。末 lane 消费可同拍接收新窗口，读端仍使用已注册的旧槽头，不旁路新入队数据。

A 保持行主序；Sequencer 捕获一拍 `T×aW` 数据及其 MX ScaleA，按 lane 递增串行送入首行列 0。A、ScaleA、有效 token 和最后标记沿原水平链右传，每次逻辑 advance 移动一列；暂停拍保持链状态及各列读 lane，不消费窗口。

- **Run**：仅当已捕获的 A token 有效且全部 T 列 B/scale 槽头均有效时统一 advance；部分列即使尚无 A token，也参与该阶段的槽头就绪门控。
- **Tail**：最后一个 A token 注入后不再索取新 A；只要求未完成列的槽头有效，已完成列的空槽头不阻塞右侧尾波。
- **Done**：列 j 消费到最后 token 时锁存完成状态；全部列完成后停止 advance，结果保持直至一次提交。

令 `g=elemsPerLane(aFmt)`、`L=K/g`，以首个逻辑 advance 为编号 0，列 j 在 `[j,L+j)` 内有效消费。每列 consume=L，Run advance=L，Tail advance=T−1，总 advance=L+T−1；前后填充只使部分列有效，不产生重复消费。供数、scale、gather 或输出停顿可增加真实时钟，不能把 advance 数解释为端到端完成时限。

### 5.2 数值处理

#### 5.2.1 合法 A/B 组合

A 和 B 必须属于同一计算族：

- 整数与非整数不得混合；
- MX 与非 MX 不得混合；
- FP8 与非 FP8 不得混合；
- BF16 与非 BF16 不得混合。

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
| BF16 | BF16 | 支持 |

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

#### 5.2.4 BF16 计算

BF16 使用专用窄乘法器，**不**复用 MXFP8 数据通路，也不注入单位 scale。乘法器直接接收 BF16 的 `sign(1)/exp(8)/mant(7)` 三段，有效尾数积写入与 MX 共享的乘积槽，再进入同一条 40-bit lazy 累加归约。

BF16 为非打包格式：每个 16-bit lane 承载一个元素。WS 每个有效 A 拍覆盖 `T` 个 K 元素，OS-M1 每次每列有效消费覆盖一个 K 元素，A 窗口仍包含 T 个元素。因此 `K` 必须为 `T` 的整数倍（默认 16），且 `K=0` 非法。

BF16 既可作为 A/B 输入，也可作为 C 输出格式。BF16 不得与非 BF16 格式混配。

WS 下 BF16 对 B 的两种装载模式均合法，B 的内存布局按所选模式遵循 5.1.2 和 7.5.2 的通用规则。OS-M1 下 BF16 同样要求 `loadWMode=1`，该限制来自单行数据流，不来自 BF16 格式。

*Note：WS 中 MX 与 FP8 必须使用 `loadWMode=1`，BF16 不受格式级限制。MX 的两条理由（scale 须按 K 维切分、B 须预转置）对 BF16 都不成立——BF16 无输入 scale 且为非打包格式；FP8 的理由是复用 MXFP8 数据通路而继承其约束，而 BF16 走专属窄乘法器。权重在两条装载路径上对乘法器位透明。*

*Note：BF16 的有效尾数积低位结构上恒零，其有效位宽度完全落在默认窄化归约窗口内，归约阶段不因 BF16 引入额外的窗口截断。*

#### 5.2.5 FP8 与 MX 计算

非 MX FP8 在输入端复用 MXFP8 数据通路，硬件注入 E8M0 scale `127` 作为单位缩放。FP8 运行仍必须设置 `loadWMode=1`，但不从 ScaleA/ScaleB 存储器读取输入 scale。

MX 输入使用 E8M0 block scale。MXFP8 元素有效尾数宽度为 4 bit，经窄乘法器产生乘积，乘法截断位结构上为零，乘法阶段不产生额外舍入。MXFP4（E2M1，有效尾数 2 bit）的四元素块和由块内整数前端直接从原始 nibble 精确计算：归一化整数单积 ≤144、四积块和 ≤576，以 11-bit 带符号精确求和后再打包为 lazy-40，不经 fp32 乘积域。其次正规值保留、underflow 冲刷、tiny-scale 冲刷与块溢出饱和语义见 5.2.6。

内部浮点/MX 累加采用 40-bit lazy 表示：

```text
{sign[1], exponent[8], mantissa[31]}
```

默认归约器为三槽 lazy-40 加法器 `{C, S1, S2}`，窄化窗口 `W=19`（取 mantissa 高 19 位对齐，低 12 位结构恒零）。槽位按运行格式选择：MXFP4 `S1=`整数块和、`S2=0`；MXFP8 `S1=lazy(r0)`、`S2=lazy(r1)`；BF16 `S1=lazy(r0)`、`S2=0`（`r0/r1` 为乘积槽的 lazy-40 打包）。五槽 `{C, L0, L1, L2, L3}` 串行基线仅在显式关闭窄化归约时例化，作为逐位参考，不属于默认交付配置。

窄化归约不应视为 IEEE-754 逐操作 bit-exact 累加。规格按向量保证三段精度契约：J1 窗口内无损则逐位精确；J2 有损时相对误差 ≤ `2^-18`；J3 宽幅相消以绝对界兜底（误差 ≤ 有损操作数个数 × 窗口 LSB）。规格只保证硬件定义的归约结果和输出舍入，不保证与软件逐项 FP32 累加完全一致。

*Note：窄化窗口 `W` 是编译期参数（默认 `19`，合法区间 `[8,28]`，由 elaboration `require` 强制，非运行期软件配置）：下界 8 = MXFP8 乘积 8 位有效尾数，上界 28 = mant31 高 W 位对齐留 3 位余量；BF16 额外要求 `W≥16` 以保 16 位乘积有效尾数无损（默认 19 满足）。三段误差界以窗口 LSB 为基准，J2 相对界为 `2^-(W-1)`（默认 W=19 ⟹ `2^-18`）。累加器 `cEffW≥40`，低 40 bit 承载 lazy-40。*

*Note：MXFP8 与 BF16 的三槽归约与五槽基线掩零后逐位等价。MXFP4 单拍数值与五槽基线相等，但 lazy 切分不同 ⟹ 多拍累加的截断点可能分叉，分叉幅度仍受上述三段精度契约约束；MXFP4 的实现间逐位一致性不属于规格保证。*

#### 5.2.6 特殊值处理

当前转换采用以下硬件语义：

- MXFP8 和 FP8 次正规输入冲刷为零；
- MXFP4 E2M1 的 `±0.5` 次正规值保留，且该格式没有 Inf/NaN 编码；
- MXFP4 块内整数前端按元素处理 underflow：`e2+scale<2` 的元素乘积冲刷为保号零（与元素转换路一致）；
- MXFP4 块级 tiny-scale 守卫：`scaleA+scaleB<121` 时整块冲刷为真零（lazy `exp=0` 且 `mantissa=0`），不产生非规格 lazy 码；
- MXFP4 块级溢出饱和：`scaleA+scaleB−120>254` 时整块饱和到有限最大值，饱和码 `{sgn, 254, 31'h7FFFFF80}` 经 `W=19` 窗口归一化后 ≈fp32max（落在窗口精度内，非逐位 fp32max），非 Inf、非 sticky；此块级饱和仅作用于 MXFP4 整数前端，MXFP8/BF16 的指数上溢仍按逐元素饱和到最大有限指数；
- MXFP8/FP8 的 A、B 元素在有效计算拍出现相应 Inf/NaN 保留码会触发硬件断言；
- BF16 输入任一侧指数为零（含零与次正规）即冲刷为零；BF16 乘积下溢冲刷为零，上溢饱和到最大有限值；
- BF16 不提供 IEEE Inf/NaN 语义，其 Inf/NaN 保留码**不触发硬件断言**而按正常数参与运算，软件必须提供有限值；
- FP16（默认未启用）及非整数 FP32 bias 同样不提供 IEEE Inf/NaN 语义，且 bias 不触发上述断言；软件必须提供有限值；
- E8M0 输入 scale 的 `0xFF` 是 NaN 保留码，软件不得使用；RTL 不对此值产生配置错误或断言；
- 有限输入导致的指数上溢饱和到最大有限指数；
- MX 输出上溢饱和，下溢通常冲刷为零；E2M1 输出额外支持次正规生成。

MXFP4 E2M1 的 `±0.5` 是受支持输入。MXFP8/FP8 元素的 Inf/NaN 保留码、BF16 输入的 Inf/NaN、FP16 输入的 Inf/NaN（若编译启用）、非有限 FP32 bias 以及 E8M0 `0xFF` 不属于受支持输入域；关闭断言后对这些不受支持值的退化结果不构成规范行为。

#### 5.2.7 MX 输入 Scale 选择

每个 MX block 对应一个 8-bit E8M0 scale。ScaleA 和 ScaleB 各使用独立的 32-bit DMA 读口。

Executor 对一拍内各 lane 使用：

```text
scaleIndex = floor(lane × step / blockSize)
```

其中：

- MXFP8：`step=2`；
- MXFP4：`step=4`；
- 正向 MX W4A8 的 B：按 A 的窗口节奏使用 `step=2`。

#### 5.2.8 MX 输出 Scale 生成

每个输出 block 产生一个 E8M0 scale byte。零 block 输出 scale 0；非零 block 根据 block 最大幅值计算并限制在有效 E8M0 范围内。

#### 5.2.9 OS-M1 与 WS 结果一致性

在两种模式均合法、`M=1、loadWMode=1、osFmtKeep=0`，且逻辑 A、B、scale、bias/rqM、输出格式与舍入相同时，`osM1=1` 与 `osM1=0` 的 C 结果关系如下。物理 B/ScaleB 的遍历方向不同，不改变逻辑张量：

| 输入族 | OS-M1 与 WS 结果关系 |
|---|---|
| INT8、INT4、INT W4A8（含 requant、ReLU） | 逐位一致 |
| BF16 | 逐位一致 |
| MXFP8、MXFP4、MX W4A8、FP8 | 不保证逐位一致；两种模式各自满足 5.2.5 的窄化归约契约 |

OS-M1 下 MX 输入 scale 的选择规则与 5.2.7 相同：scaleA 取 A lane 对应字节，scaleB 按列独立取对应字节；FP8 两侧均注入 `127`。

*Note：整数累加的模运算不依赖求和顺序，最终 ReLU/requant 采用相同 acc32 和参数。浮点族两模式使用相同乘加单元及 lane 内归约序列；WS 沿 K 方向传播部分和，OS-M1 在首行按 K 递增本地反馈。40-bit 部分和保存/回灌不额外截位，但归约本身仍遵循窄化窗口规则，不等价于逐项 FP32 累加。MX/FP8 的模式间逐位关系不属于规格保证。*

### 5.3 数据搬运

#### 5.3.1 A 读取

A DMA 遍历顺序为 N tile、K pass、M 行。每个 N tile 都重新读取完整 A。逻辑地址为：

```text
A_BASE + kt × aKStride + m × aRowStride
```

具体 stride 根据格式的元素位宽和 `kPerBeat` 派生。

#### 5.3.2 B 读取与 W4A8 Stretch

B DMA 遍历 N tile 和 K 窗口，每个窗口读取 T 个逻辑拍。WS 保持原列降序装载；OS-M1 按逻辑列 `nt×T+j`、`j=0..T−1` 升序送入各列窗口，ScaleB 使用相同方向和窗口顺序。

OS-M1 的 B 为预转置的稠密 Nprog×K 布局，令 `bitsB` 为外部 B 元素位宽：

```text
bRowStride = K × bitsB / 8
bKStride   = kPerBeatA × bitsB / 8
bAddr(nt, kt, j) = B_BASE + (nt×T+j)×bRowStride + kt×bKStride
```

上述是逻辑字节地址；非对齐地址按 gather 转为对齐物理请求。W4A8 中 B 的外部元素仍为 4 bit，窗口覆盖范围跟随 8-bit A，不因 DMA 展开而变成 8-bit 存储布局。

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

Bias 每个输出列一个值。WS 只在 `kt=0` 注入，后续 K pass 回灌的 partial sum 已包含 bias；OS-M1 在每个 N tile 启动时播种一次，后续 K 窗口继续本地累加，不重复相加。

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

#### 5.4.5 OS-M1 自然列序 Epilogue

OS-M1 的部分和列 j、bias 项 j、rqM 项 j 和输出列 `nt×T+j` 同序，不增加列反转。bias 在每个 N tile 启动时只播种一次：INT32 符号扩展为内部累加宽度，FP32 转为 lazy 格式；跨 K 窗口本地反馈不重复播种。

每个 N tile 的 rqM 必须进入正式参数寄存器后才能提交累加行，暂存收齐本身不代表提交完成。下一 N tile 参数取数可与前一 tile 输出转换/写回重叠，前一行使用的参数不得被下一组暂存内容覆盖。

提交复用 WbOutCvt、ReLU、requant、C/ScaleC DMA 及同一写回门。C 或 ScaleC 反压延长等待，不增加该 N tile 的提交次数；非 requant INT8/INT4 仍截断绕回，requant 仍饱和。每个输出行按完整 T 列和 Nprog 布局写回，软件只消费前 Nlogical 列。

## 6. 接口规范

### 6.1 命令接口

`cmd` 为 `Flipped(Decoupled(Vec(4, Valid(QueenNativeCmd))))`：**整个 beat 只有一个 ready**，四路 lane 各自只有 valid、没有独立 ready。beat 在 `cmd.valid && cmd.ready` 时整体被接受（下称接受拍），lane j 参与本拍当且仅当 beat 被接受且 `cmd.bits(j).valid` 为 1。四路共享一组 shadow 配置寄存器、一组 live 执行寄存器和一个执行上下文，不是四套配置或四个任务上下文；lane 编号由端口位置确定。命令由直接例化的 QueenDsaRegIf 处理（见第 4.2 节）。每路 Payload（QueenNativeCmd）：

| 字段 | 宽度 | 含义 |
|---|---:|---|
| `op` | 2 bit | QueenNativeOp：WRITE1、WRITE2、READ、RUN；四个编码全部合法 |
| `reg0Addr` | 10 bit | 第一个寄存器索引，即 slot0 的地址 |
| `reg1Addr` | 10 bit | 第二个寄存器索引，即 slot1 的地址（仅 WRITE2 参与） |
| `data0` | 32 | 第一个写数据 |
| `data1` | 32 | 第二个写数据（仅 WRITE2 参与） |
| `swTag` | 32 | RUN 携带的软件 tag；0 表示沿用 TRACE_TAG 寄存器（见第 6.1.2 节） |

命令**没有 pc 字段**；tracing PC 恒 0（见第 6.3 节）。地址宽度为 10 bit，与上游 QWR1/QRD 的 10-bit 寄存器地址字段对齐，覆盖第 8.3 节 `0x40` 之后的状态/中断/公共契约区。上游 QWR2 以 6-bit 相邻基址形式提交双写：`reg1Addr = reg0Addr + 1`，regBase 窗口为 `0..62`，因此双写只能落在配置区 `0x00–0x0B`。

这里的 slot0/slot1 是一条写命令内的两项写字段，不是响应缓存——本接口没有响应缓存。

操作码编码：

| 编码 | QueenNativeOp | 操作 |
|---:|---|---|
| 0 | WRITE1 | 写 `reg0Addr`，数据为 `data0`；slot1 不参与 |
| 1 | WRITE2 | 整体检查 `reg0Addr/data0` 与 `reg1Addr/data1`，合法后两项各自提交 |
| 2 | READ | 读取 `reg0Addr`，读数据同拍经 `resp` 返回；`reg1Addr/data0/data1` 不参与 |
| 3 | RUN | 把 shadow 组整组提交进 live 组并同拍启动一次执行；`reg0Addr/reg1Addr/data0/data1` 全部不参与，`swTag` 参与 |

启动计算只通过 `RUN` 完成，没有启动寄存器地址（原 `START`=`0x10` 已删除）。`RUN` 不携带配置：它提交的是此前已写入 shadow 组的内容。寄存器地址是寄存器索引，不是 MMIO byte offset。

#### 6.1.0 两组寄存器与提交语义

配置通路只有两组寄存器，没有第三级镜像：

| 组 | 写入者 | 读取者 | 执行期可写 |
|---|---|---|---|
| shadow | WRITE1 / WRITE2 | READ | 是，不受 `inFlight` 门控 |
| live | 仅 `RUN` 被接受的那一拍 | 全部执行侧消费者（CalcConfig、DMA、Executor、输出转换、阵列） | 否 |

`RUN` 被接受时把 shadow 组整组拷贝进 live 组，配置译码层恒取 live 组。因此：

- 执行期后续 WRITE1/WRITE2 只改 shadow，不扰动已提交配置——这是上游配置准备与本次执行重叠的硬件基础；
- READ 恒读 shadow，即"软件写了什么"，不是正在执行的配置。执行侧配置没有软件可读回地址；
- **`RUN` 必须独占 beat**（见第 6.1.1 节），它所依赖的配置写全部位于之前的 beat，因此 RUN 提交包含此前全部已接受写，不存在"同批写是否属于本次提交"的歧义。

复位同时清除两组。

#### 6.1.1 单 ready 接收规则

整个 beat 的 ready 按本拍 valid lane 的 op 构成决定：

| beat 构成 | `cmd.ready` |
|---|---|
| 含 RUN | `startReady = !inFlight`（拍前值，见第 6.8 节） |
| 含 READ（无 RUN） | `resp.ready` |
| 仅写（WRITE1/WRITE2 的任意稀疏子集） | 恒 1 |

约束与语义：

- **RUN 与 READ 必须独占 beat**：本拍只能有一个 lane valid。RTL 以断言强制；上游 Queen adapter 保证 RUN/READ 单独成拍。写命令可任意稀疏组合，最多 4 条同拍，不要求 lane0 起的连续前缀。
- 写 beat 恒可收：写不受在途状态、读响应或任何缓存反压门控，接受拍即提交副作用。
- `RUN` 的门控使用拍前 `inFlight`：本拍 done/配置错误清除在途时，该边沿仍按拍前 `inFlight=1` 拒绝 RUN（ready 低停等），下一拍才准入，不存在同边沿直通。`inFlight` 在 RUN 被接受时置位，覆盖 CalcConfig 配置阶段、执行及最终排空，直到 Executor done 或 CalcConfig 配置错误脉冲清除；不能用 Executor busy 代替该保护状态。
- READ beat 由 `resp.ready` 反压：读响应与被接受读同拍（见第 6.2 节），上游不取响应时 READ 停在 ready 低。

| 命令项 | 拍前 `inFlight=0` | 拍前 `inFlight=1` |
|---|---|---|
| READ | 允许（受 resp.ready） | 允许（受 resp.ready） |
| 配置寄存器写（`0x00–0x0B`） | 允许，写 shadow | 允许，写 shadow；live 不变 |
| RUN | 允许，同拍启动 | ready=0，停等（不产生错误） |
| IntrRaw/IntrForce/IntrMask、hwSignal/traceTag 写 | 允许 | 允许 |
| 只读寄存器或保留索引写 | 拒绝，`wrErr` 脉冲 | 拒绝，`wrErr` 脉冲 |

非法写（目标为只读寄存器、保留索引或超出合法写地址集合）在接受拍产生对应 lane 的 `wrErr` 脉冲（见第 6.2 节），该 lane 整条命令无副作用；**保留索引写不再是静默 no-op**。非法读返回 `data=0、resp.err=1`。接口拒绝不产生配置错误 completion，也不影响其它 lane。

WRITE2 的两项先整体检查合法性：任何一项地址非法，该 lane 整条拒绝（`wrErr` 脉冲），两项均无效，不更新配置或中断寄存器、不执行 W1C/W1S；其它 lane 的合法写不受影响。

#### 6.1.2 写入提交与合并规则

所有合法写入在接受拍当拍提交副作用，不等待任何响应或排空：

- **普通配置写与 IntrMask/hwSignal/traceTag 写按地址 last-wins**：同 lane 两项同址时 slot1（`data1`）胜；跨 lane 同址时高 lane（lane3 > lane2 > lane1 > lane0）整项覆盖，不做跨 lane 位域合并。同址竞争是合法行为，**不产生 WRITE_CONFLICT 事件**（该 IRQ 位已废除）；软件必须自行避免非预期同址写。
- **W1C（IntrRaw `0x60`）与 W1S（IntrForce `0x64`）跨 lane、跨 slot 按位 OR 合并**，不是 last-wins：所有被接受写命令中指向该地址的数据低 8 bit 先各自收集再归约，一次作用于中断寄存器（见第 8.8、11.2 节）。
- 字段截取和保留位处理按各寄存器定义（第 8 章）。

`RUN` 在接受拍同拍完成：shadow 整组拷贝进 live、置位一次 `inFlight`、产生一个一拍 start 脉冲、采样一次 `startSwTag` 与 hwSignal 公共寄存器进完成上下文（见第 6.9 节）。`startSwTag = RUN.swTag ≠ 0 ? RUN.swTag : TRACE_TAG(0xA0)`。RUN 不是写命令，不提供写目标。

#### 6.1.3 读取取值

READ 独占 beat，读数据在接受拍组合返回（见第 6.2 节）。配置寄存器读 shadow 组的拍前寄存器值；IntrRaw/IntrForce/IntrMask/IntrStatus、hwSignal、traceTag 与 errorCode 同样取拍前寄存器值——本拍 W1C/W1S、新中断事件或 RUN 提交不改写这条读响应。STATUS（0x40）读顶层当拍拼合的运行状态字。保留索引与非法地址读返回 0 且 `err=1`。

### 6.2 读响应与写错误接口

#### 6.2.1 读响应 `resp`

`resp` 为 `Decoupled(QueenDsaRegResp)`，**只承载 QRD 读数据**，没有 laneId：

| 字段 | 宽度 | 含义 |
|---|---:|---|
| `data` | 32 | READ 目标寄存器的读值；非法地址为 0 |
| `err` | 1 | 0=合法读，1=非法读地址（只读以外的保留索引、超出映射范围） |

- `resp.valid` 是**组合输出**：`resp.valid = beat 被接受 && 本拍含 READ`，即读响应与被接受的 READ **同拍**给出，没有注册下一拍、没有响应缓存、没有批间排空。
- READ beat 的 `cmd.ready = resp.ready`，因此 `resp.valid=1` 时 `resp.ready` 必为 1，读响应恒同拍 fire；上游不取响应时表现为 READ beat 停在 ready 低，而不是 resp 挂起。
- **写命令与 RUN 不产生任何响应**：没有写 ACK。写被接受即生效（last-wins/W1C/W1S 规则见第 6.1.2 节），写错误走 `wrErr`。
- 运行期配置检查和 DMA 故障不经 `resp.err` 上报：配置错误经 completion（第 6.9 节），DMA/总线错误经 IntrRaw/irq（第 6.6 节）。软件不能仅凭读响应、irq 电平或 busy 变化判断任务成功完成。

#### 6.2.2 写错误 `wrErr`

`wrErr` 为 `Output(Vec(4, Bool()))`，per-lane 一拍脉冲：beat 被接受当拍，lane j 的写命令（WRITE1/WRITE2）目标地址非法时 `wrErr(j)=1`。它取代旧模型中经 `resp.err` 上报的写拒绝：

- WRITE2 任一项地址非法即整条拒绝，两项均无副作用（原子性仅指拒绝时）；
- 非法目标包括只读寄存器（STATUS、errorCode、IntrStatus）与保留索引——保留索引写不再是静默 no-op；
- `wrErr` 不影响同拍其它 lane 的合法写，不产生配置错误 completion，不置中断位，不影响 `inFlight`。

### 6.3 Tracing 接口

`respForTracing` 是无反压 Flow，包含：

| 字段 | 宽度 | 含义 |
|---|---:|---|
| `state` | 2 | 0=start，1=done，3=configuration error |
| `pc` | 32 | 恒 0（Queen 原生命令没有 pc 字段） |
| `tag` | 8 | `startSwTag[7:0]`，即 RUN.swTag≠0 时取 RUN.swTag、否则取 TRACE_TAG（0xA0）寄存器 |

start 事件与 RUN 接受当拍产生的单个 start 对应；RUN 独占 beat，不存在多候选选择。普通配置写、读取、中断寄存器写与被拒绝命令均不捕获启动元数据，也不把读响应当作 tracing 完成事件。

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

`io.status` 为无握手输出 Bundle。字段语义见第 11.1 节。完成与配置错误经 completion 接口（第 6.9 节）观测，DMA/总线错误经中断寄存器观测；Bundle 不提供 `done` 或 `err` 字段。

| 字段 | 宽度 |
|---|---|
| `busy`、`inFlight` | 各 1 bit |
| `progress` | 16 bit |
| `aOccupancy` | `log2Up(aBufferDepth+1)` bit |
| `bOccupancy` | `log2Up(bBufferDepth+1)` bit |
| `cOccupancy` | `log2Up(cBufferDepth+1)` bit |

### 6.6 中断接口

`irq` 为 1-bit 电平输出，无 ready/valid 握手，`irq = OR-reduce(IntrRaw & ~IntrMask)`。中断源固定 8 位，全部为 DMA/总线响应错误（`intrSrc = dmaErrors(8)`，位定义见第 8.8.1 节）；**done 与配置错误不是中断源**，由 completion 携带。中断寄存器为 QueenDsaRegIf 的 IntrRFMS4 块：IntrRaw=0x60（W1C）、IntrForce=0x64（W1S）、IntrMask=0x68（1=屏蔽，复位全 1）、IntrStatus=0x6C（=Raw & ~Mask）。置位、屏蔽和清除语义见第 8.8、11.2 节。

### 6.7 性能事件接口

`perf` 为无握手输出 Bundle，各事件均为 1-bit 电平。事件定义与计数口径见第 11.3 节。

### 6.8 执行准入（startReady 与 inFlight）

原 1-bit 电平输出端口 `cfgCredit` **已删除**。执行准入改为设备内部信号 `startReady = !inFlight && !flushing`，直接馈入 QueenDsaRegIf 的 `startReady` 输入，语义与原深度 1 的 credit 等价（一个执行上下文）；`flushing` 为 flush 恢复期间内部状态（第 6.10 节）：

| 条件 | `startReady` | RUN beat `cmd.ready` |
|---|---:|---:|
| `inFlight=0 且 !flushing` | 1 | 1 |
| `inFlight=1` | 0 | 0 |
| `flushing=1`（flush 恢复期间） | 0 | 0 |

契约：

- `startReady` 只门控 RUN。WRITE1/WRITE2 恒可收，READ 只受 `resp.ready` 反压；执行期随时可写读 shadow 组。
- 本设备只有一组 live 执行上下文。RUN 被接受即置位 `inFlight`，直到 Executor done 或 CalcConfig 配置错误脉冲才清除；DMA 错误不清除 `inFlight`。
- 归还发生在**结束在途那一拍之后的下一拍**：`startReady` 取拍前 `inFlight`，故 done/配置错误脉冲当拍 RUN 仍停等，下一拍才准入，不存在同边沿直通。
- RUN 独占 beat（第 6.1.1 节），准入只会被本设备自己接受的 RUN 占用，电平检查无竞争。
- `startReady=0` 时 RUN 在派发级停等（ready 低），不产生响应、不置错误位；上游后续指令按程序序等待。
- 软件可经 STATUS（0x40）bit2 观测 `inFlight`。要让上游在执行期就提交下一个 RUN，需要第三组配置缓冲；当前不做，见第 14.2 节。

### 6.9 完成接口（completion）

`completion` 为 `Valid(QueenDsaCompletion)` 输出：**Queen 无条件接收，无 ready**，每拍至多一条。它取代旧模型中"完成/配置错误置 IRQ 位"的上报方式——done 与 configError 不再置任何中断位。

| 字段 | 宽度 | 含义 |
|---|---:|---|
| `swTag` | 32 | start 当拍采样的 `startSwTag`（RUN.swTag≠0 取 RUN.swTag，否则取 TRACE_TAG），完成时原样带回 |
| `hwTag` | 32 | 恒 0（设备私有，预留给未来 trace 管理） |
| `error` | 1 | = configError（CalcConfig `errPluse`）；0=正常完成 |
| `code` | 8 | configError 时为 CalcConfig errCode（零扩展至 8 bit），否则 0 |
| `hwSignal` | QueenHwSignal | start 当拍采样的 hwSignal 公共寄存器（enable/isTimeline/group/mask/timeline，见第 8.9 节）；**error=1 时强制全 0** |

时序与语义：

- `completion.valid = done || configError`，与 Executor done 脉冲或 CalcConfig 配置错误脉冲同拍；两者同拍时至多一条，`error` 以 configError 为准。
- 设备在 RUN 接受（start）当拍把 `startSwTag` 与 hwSignal 寄存器值采样进上下文寄存器，执行期间软件改写 TRACE_TAG/hwSignal 不影响本次回传。
- `error=1 ⟹ hwSignal.enable=0` 是硬规则：错误完成不得携带有效硬件信号目标。
- 同拍清除 `inFlight`；configError 还把 errCode 锁存进 errorCodeReg（0x41，下次 start 清零）供 QRD 调试读取。
- 正常完成（error=0）不表示无 DMA 错误：DMA/总线错误独立经 IntrRaw/irq 上报，软件必须两者都检查（第 13.1 节）。

### 6.10 强 idle 与 flush 恢复握手（deviceIdle / flushReq / flushAck）

对齐 Queen 通用 DSA 契约（queenHarewareInterface.md §5.3/§5.5/§13.5/§13.10.5）的恢复接口，三个端口：

| 端口 | 方向 | 宽度 | 含义 |
|---|---|---:|---|
| `deviceIdle` | Output | 1 | 强 idle 电平：命令空 ∧ 无执行 ∧ 无在途内存事务 ∧ 无未回传 completion |
| `flushReq` | Input | 1 | Queen 发起的恢复请求电平 |
| `flushAck` | Output | 1 | 设备恢复完成应答电平 |

**强 idle 判据**（`deviceIdle=1` 当且仅当全部成立）：

- `!executor.busy && !inFlight && !executor.osActive`（无执行、无在途命令上下文）；
- A/B/C 各读/写 DMA `!busy`，且 A/B/C 工作 FIFO occupancy 全 0；
- MX 配置下 scaleA/scaleB/scaleC 各 DMA `!busy`、scaleB FIFO occupancy 为 0；
- `!perfBiasFetchActive && !biasPortBusy`：bias 取数 FSM 空闲，且共享 ScaleA 物理口在途计数（覆盖 scaleA/bias/rqM 全部 owner）为 0。

DMA `busy=state≠sIDLE`，其 FSM 回 IDLE 前已 drain 在途计数，故 `!busy` 覆盖该口无在途内存请求/响应。`deviceIdle` 是纯组合电平，强于 `busy=0` 或 FIFO 空：仅写 shadow 配置不执行时仍为 1，RUN 进入执行后落 0，completion 自然结束后回 1。

**flush 四相握手**（无安全 abort ⟹ 等当前工作自然结束）：

1. `flushReq↑`：设备置内部 `flushing`，整个 `flushReq` 有效期间不落；`flushing` 期间 `startReady=0`，新 RUN 停在 ready 低（第 6.8 节），不丢弃已接受配置、不复位 RegIf；
2. 设备等待当前工作自然结束至 `deviceIdle=1`，置 `flushAck↑`；工作未结束不得提前回 ack；
3. `flushReq↓`：设备清 `flushing` 与 `flushAck`；
4. `flushAck↓`：握手结束，`startReady` 恢复，可接受新 RUN。

契约边界（裁定 R-A/R-C/R-D）：

- flush **不复位 RegIf**：shadow/live 配置寄存器与中断块保持，软件须自行按第 13.1/13.2 节重配与清错误；
- flush **不抑制 completion**：等待自然结束期间 done/configError 照常经 completion 回传，Queen 侧丢弃迟到/旧上下文项；
- flush **不做 abort**：不终止正在执行的任务、不撤销在途内存事务，只 block 新 RUN 并等自然排空；
- `deviceIdle` 与 flush 不扩展计算/DMA 能力，不引入任务排队或第三组配置缓冲。

## 7. 数据表示规范

### 7.1 格式编码

| 编码 | 格式 | 元素宽度 | 默认 A/B 支持 | C 输出路径 |
|---:|---|---:|:---:|:---:|
| 0 | FP32 | 32 | 否 | 是 |
| 1 | FP16 | 16 | 否 | 是 |
| 2 | BF16 | 16 | 是 | 是 |
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

- 16-bit 格式（BF16）：整个 lane 承载一个元素，不打包；
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

每个 N tile 都会重新读取对应 A 数据；WorkerBee 内部不跨 N tile 缓存 A。

#### 7.5.2 B 矩阵

##### `loadWMode=0`

B 按补齐后的 `K×Nprog` row-major 存储。硬件按 K/N tile 地址跳转读取。

##### `loadWMode=1`

B 必须预转置为 `Nprog×K` row-major：

```text
BT[n][k] = B[k][n]
```

MX、FP8 和正向 INT W4A8 必须采用此布局。OS-M1 的所有输入格式也必须采用此布局；B/ScaleB 升序读取只改变遍历方向，不定义第二种转置格式。

#### 7.5.3 C 矩阵

C 按 `M×Nprog` row-major 写回，其中 `Nprog` 必须是 T 的整数倍。硬件不会缩窄末 tile；软件只消费每行前 `Nlogical` 列。

#### 7.5.4 N 维补齐

硬件以完整 T 列为单位读取 B 和写回 C，不对最后一个 N tile 自动缩窄 byte strobe。C 行步长又直接由编程值 `N` 计算，因此仅在缓冲区末尾追加空间不能阻止最后一个 tile 跨入下一行。

软件必须把写入寄存器的 `N` 向上补齐为 T 的整数倍：

```text
Nprog = ceil(Nlogical / T) × T
```

A×B、C、bias、requant 参数和相关 scale 布局均按 `Nprog` 构造，逻辑列数 `Nlogical` 由软件在 WorkerBee 之外保存。补齐列必须具有可访问的 B/参数数据，C 缓冲区也必须按每行 `Nprog` 个元素分配。WS 和 OS-M1 均不读取 Nlogical，也不自动屏蔽补齐列。

若要求补齐列的乘加结果为零，软件应提供 B=0、bias=0，并使用合法有限 scale；启用 requant 时还应提供 rqM=0。此时 requant 输出为按 INT8/INT4 范围饱和后的 zeroPoint，不一定为零。无论补齐列的数值如何，完整 Nprog 写回范围都必须可访问。

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
| `arrayN` | 16 | 阵列行数（K-lane 轴） |
| `arrayM` | 16 | 阵列列数（输出 N 轴，须 == arrayN） |
| `totalN` | 16 | `arrayN`（方形边长） |
| `aW` | 16 bit | 每个 K-lane 的 A 输入打包宽度 |
| `bW` | 16 bit | 每个 PE 的 B 权重打包宽度 |
| `cW` | 0 | 自动派生累加宽度 |
| `aBufferDepth` | 64 | A FIFO 深度 |
| `bBufferDepth` | 64 | B FIFO 深度 |
| `cBufferDepth` | 64 | C/partial-sum FIFO 深度 |
| `addrWidth` | 32 | DMA 地址宽度 |
| `scaleBufferDepth` | 64 | scale FIFO 深度 |
| `cDmaExtW` | 256 bit | C 写 DMA 数据宽度 |

默认 `supportedFmts` 为 `INT8、INT4、MXE4M3、MXE5M2、MXE2M1、FP8E4M3、FP8E5M2、BF16`。FP16 不在默认集内，与 BF16 是两个独立的编译期开关。

四路端口契约：命令 lane 固定为 4（QueenConfig `nativeWidth=4`、`xlen=32`、`faultCodeWidth=8`），不新增 lane count 配置 knob；寄存器前端按 `QueenDsaRegIfConfig(numCfgRegs=12, numStatusRegs=2, intrWidth=8)` 固定例化，寄存器数量与地址映射由第 8.3 节定义，不属于运行期可配置 count，不列入编译配置参数。命令 lane 与阵列 K-lane、A/B 打包 lane、MAC 数据并行度无关，不改变 totalN、aW、bW 或任何数值几何。

### 8.2 编译期约束

配置必须满足：

- `arrayN ≥ 4`，`arrayM ≥ 1` 且 `arrayM == arrayN`（引擎按方形切块）；
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

寄存器索引为 10 bit，与上游 QWR1/QRD 的地址字段等宽。地址映射遵循 QueenDsaRegIfAddr 公共契约：配置区 `0x00–0x0B` 落在 QWR2 的 6-bit 相邻基址窗口内，可相邻配对双写；状态区从 `0x40` 起，中断/hwSignal/traceTag 走公共契约区，超出该窗口，只能由 QWR1/QRD 单条访问。配置存储由 QueenDsaRegIf 持有，分为 shadow 与 live 两组（见第 6.1.0 节）；四路共享这两组固定 bank，不按 lane 复制寄存器。配置字段的既有位布局与计算含义不变；中断区改为 IntrRFMS4 四寄存器、有效位 8 bit。

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
| `0x0C–0x3F` | RESERVED | — | 读 0 且 `resp.err=1`；写 `wrErr` 脉冲 |
| `0x40` | STATUS | RO | executorBusy、inFlight、progress |
| `0x41` | ERROR_CODE | RO | 低 4 bit 为最近一次配置错误码，下次 start 清零 |
| `0x42–0x5F` | RESERVED | — | 读 0 且 `resp.err=1`；写 `wrErr` 脉冲 |
| `0x60` | INTR_RAW | RO 读 / W1C 写 | 低 8 bit 分源粘滞 DMA/总线错误事件；写 1 清对应位 |
| `0x64` | INTR_FORCE | W1S / 读=RAW | 低 8 bit 写 1 置位对应 RAW 位；读返回 INTR_RAW |
| `0x68` | INTR_MASK | RW | 低 8 bit 中 bit=1 屏蔽对应中断源；复位 `0xFF`（全屏蔽） |
| `0x6C` | INTR_STATUS | RO | 低 8 bit 为 `INTR_RAW & ~INTR_MASK`，高位为 0 |
| `0x6D–0x7F` | RESERVED | — | 读 0 且 `resp.err=1`；写 `wrErr` 脉冲 |
| `0x80` | HW_SIGNAL_TARGET | RW | 硬件完成信号目标（位域见第 8.9 节） |
| `0x81` | HW_SIGNAL_MASK | RW | 硬件完成信号 32-bit mask |
| `0x82–0x9F` | RESERVED | — | 读 0 且 `resp.err=1`；写 `wrErr` 脉冲 |
| `0xA0` | TRACE_TAG | RW | 32-bit 软件 tag 缺省值，RUN.swTag=0 时沿用 |
| `0xA1–0x3FF` | RESERVED | — | 读 0 且 `resp.err=1`；写 `wrErr` 脉冲 |

`0x00–0x0B` 的 RW 指 shadow 组：写入立即生效于 shadow，`RUN` 后才进入 live。STATUS/ERROR_CODE 与中断/hwSignal/traceTag 区不属于任何一组配置缓冲，是独立的运行状态/事件/公共契约寄存器，读写随时可用。

启动没有寄存器地址：原 `START`=`0x10` 已删除，`0x10` 现为保留空洞（读 0/err=1、写 wrErr、不启动）。旧自定义 IRQ 区 `0x41–0x45`（IRQ_RAW/MASK/MASKED/CLEAR/ERROR_CODE）已废除：`0x41` 重定义为 ERROR_CODE（最近配置错误码），`0x42–0x45` 成为保留空洞，IRQ_CLEAR 与 WRITE_CONFLICT 不复存在。

M/N_K/CONTROL/RQ_PARAM 的字段定义分别见第 8.4、8.5、8.7 节。A/B/C、scale、bias 与 rqM 地址寄存器保留既有地址宽度及字段处理规则，DMA `addrWidth` 与对齐/可访问范围约束不变。

只读寄存器 STATUS、ERROR_CODE 和 INTR_STATUS 的写入必须拒绝，产生对应 lane 的 `wrErr` 脉冲（不再是 `resp.err`，写不产生响应）；保留索引写同样拒绝。不存在独立中断 ENABLE 寄存器。

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
| `[27]` | `osFmtKeep` | OS 格式广播优化控制；软件应写 0，基线执行完整广播 |
| `[28]` | `loadWMode` | B 装载模式 |
| `[29]` | `requantOn` | Requant 使能 |
| `[30]` | `biasOn` | Bias 使能 |
| `[31]` | `osM1` | 0=WS，1=OS-M1；必须 M=1 且 loadWMode=1 |

`osM1` 和 `osFmtKeep` 均可写入并读回，复位为 0；`osFmtKeep` 不改变格式字段或数值契约。本文使用 `osFmtKeep=0` 的完整广播基线，不以该位承诺跳过广播的节拍收益。

`AddrMode` 编码定义为 MAT/TILE，但当前主数据通路没有消费这三个字段；软件应写 0，不能依赖 TILE 模式改变 DMA 地址行为。

### 8.6 STATUS 与 RUN

`STATUS` 位于索引 `0x40`：

| Bits | 字段 | 含义 |
|---|---|---|
| `[0]` | `executorBusy` | Executor 非 IDLE，与 `io.status.busy` 相同 |
| `[1]` | Reserved | 0 |
| `[2]` | `inFlight` | `RUN` 已接受，尚未收到 done 或配置错误脉冲 |
| `[15:3]` | Reserved | 0 |
| `[31:16]` | `progress` | 已完成 `(N,K)` pass 数 |

STATUS 不含完成、错误标志或错误码；写入拒绝，产生 `wrErr` 脉冲。`executorBusy` 可能在 `inFlight=1` 的启动/配置阶段仍为 0。任务结果必须消费 completion（第 6.9 节）并检查 IntrRaw 与 ERROR_CODE，不能以 STATUS 的保留位或 busy 单独判断。

启动由 `QueenNativeOp.RUN` 命令完成，没有寄存器地址（原 `START`=`0x10` 已删除，该索引现为保留空洞）：

- `RUN` 不携带配置，`reg0Addr/reg1Addr/data0/data1` 全部被忽略；即使地址指向只读或中断寄存器也不产生写、拒绝或 W1C/W1S 副作用。`swTag` 参与：非 0 时作为本次 startSwTag，0 时沿用 TRACE_TAG（0xA0）。
- RUN 独占 beat；接受拍同拍把 shadow 组整组拷贝进 live 组、置位一次 `inFlight`、产生一个一拍 start，并采样 startSwTag/hwSignal 进完成上下文。它提交的是此前全部已接受写（第 6.1.0 节）。
- 拍前 `inFlight=1`（`startReady=0`）时 RUN 停在 ready 低，不产生响应或错误；done/配置错误脉冲当拍仍停等，下一拍才准入。
- `RUN` 不清除 IntrRaw 或 IntrMask；errorCodeReg 在 start 当拍清零（属完成上下文复位，不是中断清除）。

STATUS 与其它寄存器读均按第 6.1.3 节取值，不旁路本拍写入或新事件。

### 8.7 RQ_PARAM

| Bits | 字段 | 含义 |
|---|---|---|
| `[5:0]` | `shift` | 右移位数 |
| `[15:6]` | Reserved | 0 |
| `[31:16]` | `zeroPoint` | 有符号 16-bit zero-point |

### 8.8 中断与配置错误码寄存器

中断块为 QueenDsaRegIf 的 IntrRFMS4（Raw/Force/Mask/Status 四寄存器，`0x60` 对齐），有效位 8 bit，全部事件源为 DMA/总线响应错误。**done 与配置错误不再是中断源**（由 completion 携带，第 6.9 节）；旧 IRQ_RAW[0]/[1]、IRQ_CLEAR（0x44）与 WRITE_CONFLICT bit10 已废除。

#### 8.8.1 INTR_RAW（`0x60`，读 RO / 写 W1C）

INTR_RAW 捕获以下分源事件并保持粘滞，直至对应 W1C 或复位；读取没有副作用，捕获不受 INTR_MASK 影响。设备侧 `intrSrc = dmaErrors(8)`：

| Bit | 事件源 | 置位条件 |
|---:|---|---|
| 0 | A DMA 错误 | A DMA `rsp.fire && rsp.err` |
| 1 | B DMA 错误 | B DMA `rsp.fire && rsp.err` |
| 2 | C DMA 错误 | C DMA `rsp.fire && rsp.err` |
| 3 | ScaleA DMA 错误 | 共享 ScaleA 物理口响应错误，当前响应 owner 为 ScaleA（含 WAIT/IDLE） |
| 4 | ScaleB DMA 错误 | ScaleB DMA `rsp.fire && rsp.err` |
| 5 | ScaleC DMA 错误 | ScaleC DMA `rsp.fire && rsp.err` |
| 6 | Bias DMA 错误 | 共享 ScaleA 物理口响应错误，当前响应 owner 为 Bias |
| 7 | RqM DMA 错误 | 共享 ScaleA 物理口响应错误，当前响应 owner 为 RqM |
| `[31:8]` | Reserved | 高 24 bit 读 0，无事件源 |

这些事件属 Queen 契约 §13.4 的"与命令无关的总线错误"：粘滞、不中止执行、不进 completion、不分配配置错误码。

*Note：原实施计划为 Phase 1 取 intrWidth=1、intrSrc=0、dmaErrors 不外露；实际实现有意偏离为 intrWidth=8、intrSrc=dmaErrors(8)，以保留 DMA 错误的 IRQ 覆盖并符合 §13.4"总线错误 → intrSrc"的归类。本文以实现行为为准。*

对 `0x60` 的写是 W1C：数据低 8 bit 中写 1 清对应 RAW 位、写 0 保持；高位忽略。W1C 数据**跨 lane、跨 slot 按位 OR 合并**后一次作用（第 6.1.2 节），不存在 last-wins 或"最高 lane 阻挡"。同拍新事件（intrSrc）优先于 W1C，RAW 保持置位。

#### 8.8.2 INTR_FORCE（`0x64`，W1S / 读=RAW）

对 `0x64` 的写是 W1S：数据低 8 bit 中写 1 直接置位对应 INTR_RAW 位，用于软件自检中断通路；同样跨 lane、跨 slot OR 合并。没有独立的 force 寄存器，读 `0x64` 返回 INTR_RAW 当前值。

#### 8.8.3 INTR_MASK（`0x68`，RW）与 INTR_STATUS（`0x6C`，RO）

INTR_MASK 仅低 8 bit 有效：1=屏蔽对应源，0=允许该源驱动 irq。复位值为 `0xFF`，全部源默认屏蔽；高 24 bit 写入忽略，读回为 0。它是普通可写寄存器，按第 6.1.2 节同 lane slot1 优先、跨 lane 同址 last-wins。修改屏蔽不清除或阻止 RAW 捕获，已经 pending 的源解除屏蔽后即可驱动 irq。不存在额外 ENABLE 寄存器。

INTR_STATUS 读返回 `INTR_RAW & ~INTR_MASK`，高 24 bit 为 0；读取不清事件。写入拒绝（`wrErr` 脉冲）。

#### 8.8.4 ERROR_CODE（`0x41`，RO）

`[3:0]` 锁存**最近一次**配置错误码，`[31:4]` 读 0；复位为 0。错误码含义和 CalcConfig 同次检查的优先级见第 10.1–10.3 节。

- configError 脉冲当拍锁存 CalcConfig errCode，同拍经 completion.code 回传；
- 下一次 RUN 被接受（start）当拍清零；
- done、DMA 错误、W1C 与 RUN 之外的任何寄存器写均不修改它。

它只是 QRD 调试读回口，**不是"首个未清"语义**（旧规则已废除）：错误码 0 本身是有效配置错误码，是否存在配置错误必须以 `completion.error` 判断，不得通过 ERROR_CODE 是否非零判断。由于锁存寄存器延迟，configError 当拍读 0x41 仍为旧值，下一拍才可见新码。写入拒绝（`wrErr` 脉冲）。

### 8.9 hwSignal 与 TraceTag 寄存器

公共契约区寄存器由 QueenDsaRegIf 持有，普通 RW（last-wins），复位为 0：

`HW_SIGNAL_TARGET`（`0x80`）：

| Bits | 字段 | 含义 |
|---|---|---|
| `[0]` | `enable` | 硬件完成信号使能 |
| `[1]` | `isTimeline` | 0=事件组，1=时间线 |
| `[6:2]` | `group` | 5-bit 事件组号 |
| `[9:7]` | `timeline` | 3-bit 时间线号 |
| `[31:10]` | Reserved | 读回保留，无硬件语义 |

`HW_SIGNAL_MASK`（`0x81`）：32-bit mask，原样存储。

两者在 RUN 接受当拍被采样进完成上下文，随 completion.hwSignal 原样回传；`completion.error=1` 时强制回传全 0（第 6.9 节）。设备本身不消费 enable/group 等字段产生额外硬件动作，信号目标的最终解释由 Queen 侧完成。

`TRACE_TAG`（`0xA0`）：32-bit 软件 tag 缺省值。RUN.swTag≠0 时覆盖该值，为 0 时沿用；胜出值即 startSwTag，用于 completion.swTag 与 tracing tag（第 6.3 节）。

## 9. 时序与复位规范

### 9.1 时钟与复位边界

WorkerBee 使用 Chisel `Module` 的隐式 clock/reset 接口。本文不固定交付 RTL 的复位类型、有效电平、同步/异步形式或复位保持周期；这些项目须在具体集成时依据发射 RTL 确认，不得仅由命令/DMA payload 表推定。

复位清除 shadow/live 两组配置寄存器、`inFlight`、完成上下文（swTag/hwSignal 采样寄存器）、errorCodeReg、IntrRaw、hwSignal 目标/mask 与 traceTag；IntrMask 复位为 `0xFF`（低 8 bit 全 1），因此 IntrStatus 与 irq 为 0。接口没有响应缓存或待发启动状态：`resp.valid` 与 `completion.valid` 均为组合输出，复位后无残留响应或旧启动重发。未初始化的数据存储内容不构成有效输入；复位后必须重新配置再启动执行。

### 9.2 初始化与执行在途约束

QueenDsaRegIf 在接受拍对各 lane 写命令检查合法性：合法写当拍提交进 shadow 组（普通写 last-wins、W1C/W1S 合并，第 6.1.2 节），非法写产生 per-lane `wrErr` 脉冲且无副作用。RUN 独占 beat，在同一接受拍把 shadow 整组拷贝进 live 组、置位一次 `inFlight`、产生一个一拍 start，并采样一次 startSwTag/hwSignal 进完成上下文；它提交的是此前全部已接受写。写与 RUN 均不产生响应；READ 的响应同拍组合返回，读响应反压只体现为 READ beat 的 ready 低，不延迟或重复任何写提交、live 提交或启动副作用。

RUN 门控仅使用拍前 `inFlight`：本拍 done/配置错误清除在途时该边沿仍停等 RUN，下一拍才准入。配置写不受 inFlight 门控。

CalcConfig 检查通过后，有效执行启动时的工作 FIFO flush 与状态迁移见第 5.1.4 节。`inFlight` 持续覆盖配置与执行阶段，在 Executor done 或 CalcConfig 配置错误脉冲时清除，中断是否被屏蔽或软件是否清 IntrRaw 不影响该状态。DMA 错误事件不清 `inFlight`，也不产生 fail-fast 终止。

在途期间允许 READ、配置写（落 shadow，live 不变）、中断/hwSignal/traceTag 写，因此下一次任务的配置准备可与本次执行重叠。RUN 按第 6.1.1 节在 `startReady=0` 时停在 ready 低等待，不以错误拒绝。

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
| 14 | `osM1=1` 且 M≠1 或 loadWMode≠1 |
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
- BF16 与非 BF16 混合；
- C 使用 FP8。

*Note：C 使用 BF16 是合法的（BF16 有输出转换路径），该判据只约束 A/B 输入侧；这一点与 FP8 相反。*

#### errCode 8

- BF16：`K` 必须为 `T` 的整数倍；
- INT8、MXFP8、FP8：`K` 必须为 `2T` 的整数倍；
- INT4、MXFP4：`K` 必须为 `4T` 的整数倍；
- `K=0` 非法；
- 正向 W4A8 按 4-bit B 的几何要求执行，并附加 B 地址对齐检查；
- 正向 INT W4A8 必须设置 `loadWMode=1`。

*Note：BF16 非打包，其除数是 `T`（默认 16）而非 `2T`。默认配置下 `K=48` 对 BF16 合法、对 8-bit 格式非法（`48 mod 32 ≠ 0`），`K=32` 对两者都合法；BF16 的除数更小、约束更宽，但两族除数不同，软件不可互相套用。*

#### errCode 12

- `biasOn=1` 且 BIAS_ADDR 非 4-byte 对齐；
- `biasOn=1` 但编译配置不含参数取数硬件。

#### errCode 13

- `requantOn=1` 且 C 不是 INT8/INT4；
- RQ_M_ADDR 非 4-byte 对齐；
- 编译配置不含参数取数硬件。

#### errCode 14

`osM1=1` 且 `M≠1` 或 `loadWMode≠1`。OS-M1 不放宽任一格式、K、block、地址或 epilogue 约束；同时存在其它错误时，按 10.3 的优先级报告具体配置错误。

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
> 14(OS-M1 shape/load mode)
```

该优先级选择同一次 CalcConfig 检查的错误码。软件不得假设 errCode 会报告同次检查中最先出现或数值最小的错误；errorCode 寄存器（0x41）的锁存/清零规则见第 8.8.4 节。

### 10.4 配置错误行为

RUN 接受当拍的 start 触发运行期配置检查，检查该次提交进 live 组的全部配置；检查判据与优先级不变。若配置非法：

- 不启动正常 GEMM；
- CalcConfig 输出 `errPluse` 和按第 10.3 节选出的配置错误码；
- 顶层清除 `inFlight`，同拍产生一条 `completion`：`error=1`、`code=errCode`（零扩展至 8 bit）、`hwSignal` 强制全 0、swTag 为 start 当拍采样值；**不置任何 IRQ 位**；
- 错误码同拍锁存进 errorCodeReg（0x41），供 QRD 调试读取，下次 start 清零；
- 若这是复位后的首次执行，tracing 输出 configuration error。

配置错误不产生写 ACK 或读响应（写/RUN 本就没有响应），也不通过 STATUS 或 `io.status.err` 上报。该检查失败路径以配置错误脉冲结束在途状态并回传错误 completion，不要求 Executor 产生 done。DMA 错误或 irq 电平本身不终止运行中的任务。

### 10.5 DMA 错误与上报边界

顶层逐拍将各 DMA 的 `rsp.fire && rsp.err` 汇成 8 位 `dmaErrors`，作为 `intrSrc` 提供给 QueenDsaRegIf，直接置位第 8.8.1 节对应 IntrRaw 位，不等待 Executor done。这些事件属 Queen 契约 §13.4 的"与命令无关的总线错误"，走 intrSrc/IRQ 通路，**不进 completion、不分配配置错误码**。响应仅 `valid` 而未握手时不重复形成事件；同一源后续错误保持粘滞位，W1C 同拍新事件优先。DMA 本地错误锁存在返回 IDLE 时清除，不影响已捕获的 RAW。因此早期读 DMA 响应错误不会因本地状态先行清除而漏报。

A、B、C、ScaleB、ScaleC 使用各自响应源。共享 ScaleA 物理口上的错误必须按该响应握手时仲裁器的当前响应 owner 分类，而不是按请求端的新 owner、最终 done 拍 owner 或某项使能位推断：

| 当前响应 owner | IntrRaw 位 |
|---|---:|
| ScaleA | 3 |
| WAIT/IDLE | 3 |
| Bias | 6 |
| RqM | 7 |

DMA 错误不是 fail-fast：已发出的事务不会撤销，通道继续排空，任务继续按既有执行流程运行，C 也可能已部分写入。错误 IRQ 可能先于 completion；不能因 irq 已置高就回收缓冲区或消费结果。正常 completion（error=0）也不排除此前或同拍 DMA 错误。

上报保证仅覆盖实际握手且携带 `rsp.err=1` 的响应。未通过 `rsp.err` 标记的内存错误不可检测；请求缺失响应、乱序响应、非法存储访问未被外部标错及其它协议违约不因本次汇聚获得额外检测能力。DMA 错误不更新 errorCodeReg。数值路径的 assert 仍是断言，不是 IRQ 事件。

### 10.6 非错误码约束

以下条件不会自动产生专用 errCode，软件仍必须满足：

- `M≥1`、`N≥1`；M/N 为 0 会使内部减一计数回绕，可能越界运行或挂死；
- 编程值 `N=Nprog` 且为 T 的整数倍；
- A/B/C 和参数缓冲区实际可访问范围；
- 配置写在任何时刻都合法（落 shadow 组），不产生在途违约错误；RUN 必须在拍前 `inFlight=0` 时提交，`startReady=0` 时停在 ready 低等待，不置错误、不是运行期配置错误码；跨 lane 同址写按 last-wins（高 lane 胜）合法提交，不再产生 WRITE_CONFLICT 事件，软件必须自行避免非预期同址写并核实实际生效配置；
- 保留字段写 0；
- 使用编译配置真正生成的 A/B 格式；
- 外部 DMA 严格同序并对每个请求返回一个且仅一个响应。

## 11. 状态与观测规范

### 11.1 运行状态

`io.status` 输出：

| 字段 | 含义 |
|---|---|
| `busy` | Executor 不在 IDLE；对应 STATUS.executorBusy |
| `inFlight` | RUN 已接受，尚未收到 Executor done 或 CalcConfig 配置错误脉冲 |
| `progress` | 已完成的 `(N tile, K pass)` 数 |
| `aOccupancy` | A FIFO 占用 |
| `bOccupancy` | B FIFO 占用 |
| `cOccupancy` | C FIFO 占用 |

`io.status` 不含 `done` 或 `err`。`progress` 不表示 M 行数，也不表示写回 byte 数；WS 每完成一个 `(N,K)` pass 增加 1，OS-M1 每提交一个 N tile 增加该 tile 的 K 窗口数，计算期间不逐 K 窗口实时增加。`busy=0` 不保证没有已接受启动，`inFlight=0` 也不表示成功：配置检查失败同样结束在途状态。完成与配置错误必须从 completion（辅以 errorCode 0x41）判断，DMA/总线错误从 IntrRaw 判断。

### 11.2 DMA/总线错误中断

`irq` 为电平信号，仅取决于未屏蔽的 pending DMA/总线错误事件：

```text
INTR_STATUS[7:0]  = INTR_RAW[7:0] & ~INTR_MASK[7:0]
INTR_STATUS[31:8] = 0
irq               = OR-reduce(INTR_STATUS[7:0])
```

RAW 更新与屏蔽分离。设 `intrSrc[7:0]` 为本拍分源 DMA/总线错误事件；`clrBits[7:0]` 为本拍全部被接受 W1C 写（IntrRaw 0x60）数据低 8 bit 的跨 lane、跨 slot OR；`setBits[7:0]` 为全部被接受 W1S 写（IntrForce 0x64）的同样合并。则非复位拍：

```text
INTR_RAW_next[7:0] = (INTR_RAW[7:0] & ~clrBits[7:0]) | setBits[7:0] | intrSrc[7:0]
INTR_RAW[31:8]     = 0
```

- RAW 粘滞且不受 mask 捕获门控；MASK 只影响 INTR_STATUS 与 irq，低 8 bit 有效，高 24 bit 保留为 0；
- 复位清 RAW，MASK 复位为低 8 bit 全 1（`0xFF`），默认全屏蔽；
- RUN 不清 RAW；读取、completion 和写 MASK 也不自动清除；
- W1C/new 同拍时 fresh event（intrSrc）优先；W1C/W1S 跨 lane 合并，不存在 last-wins 或高 lane 阻挡；
- IntrRaw[7:0] 全部是分源 DMA 响应错误（位定义见第 8.8.1 节）；**没有完成位、配置错误位或 WRITE_CONFLICT 位**——done/configError 由 completion 携带（第 6.9 节）；
- 错误 IRQ 可以早于 completion，IRQ 本身不是任务终止信号，也不是 fail-fast；
- 软件轮询 DMA 错误应读取 IntrRaw，不依赖 MASK 是否允许 irq；irq 或 INTR_STATUS 为 0 不代表没有错误事件。任务完成判定不依赖 IRQ，必须消费 completion。

INTR_MASK 写在接受拍按普通寄存器规则提交（同 lane slot1 胜、跨 lane 同址 last-wins）。所有中断寄存器读取取拍前寄存器值，不含本拍 W1C/W1S 更新或 fresh event；读取自身不清事件。

RAW 不携带任务 tag，也不累计事件次数。启动前应按第 13.1–13.2 节处理旧 DMA 错误事件，避免把旧错误误判为新任务错误；RUN 不提供隐式事件清除。

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
| `array` | WS 的阵列输入 `wbValidIn`（LOAD_A 的 `aPop.fire`）；OS-M1 至少一列真实 consume，包含 Tail |
| `stallA` | WS 的 LOAD_A 中没有 `aPop.fire`；OS-M1 的 Run 缺已捕获的 A/ScaleA token |
| `stallB` | WS 的 LOAD_B 中没有 `bPop.fire`，或 DRAIN 倒计数归零且有下一窗口时 B/ScaleB 预蓄不足；OS-M1 的 Run 缺任一 B/scale 槽头，或 Tail 的未完成列缺槽头 |
| `epilogue` | bias/rqM 取数活动，或输出转换 `in.fire`、数据 `out.valid`、scale `out.valid` |
| `busy` | Executor 非 IDLE |
| `cRowIn` | 结果行写入 C buffer 的有效拍。WS 为 deskew 对齐后的每个结果行一拍；OS-M1 为每个 N tile 的提交拍 |
| `rdSelFirst` | 阵列首个 PE（行 0、列 0）实际读取的权重 bank 选择电平 |
| `rdSelLast` | 阵列末个 PE（行 T−1、列 T−1）实际读取的权重 bank 选择电平 |

DMA 事件使用 `req.valid` 而不是 `req.fire`。当外部 `ready=0` 时，同一请求可令事件连续多个周期为 1；外部 PMU 若要统计事务数，应额外结合对应 ready 信号。

WS 下 `array` 与 `cRowIn` 是同序等长的两条事件流：每个输入激活行恰产生一个结果行，且结果行按激活行序入 C buffer。外部 PMU 按事件序号配对两者的采样拍号，其差即阵列行级波前延迟；`array` 相邻事件拍号差大于 1 处即 `(N tile, K pass)` 边界。OS-M1 下两条流不等长，不具此配对关系。

`rdSelFirst`/`rdSelLast` 是电平而非脉冲，外部 PMU 需自行检上升沿。`loadWMode=1` 的 WS 下两者上升沿相差 `2(T−1)` 拍，即读侧 bank 选择随波前对角传播的直接观测；差值为 0 表示退化成了全局广播切换。`loadWMode=0` 与 OS-M1 下两者恒为 0。

OS-M1 的 Run 可同时缺 A 和 B，因此 stallA/stallB 可以同时为高；两者不能直接相加作为互斥停顿总周期。Tail 不索取新 A，已完成列的空 B 槽头不产生 stallB。配置广播、提交和最终写回等待不属于 OS 供数停顿。`array` 为活动周期，不表示活跃列数、乘加元素数或已完成输出行数；C/ScaleC 活动由对应 DMA 事件独立观测。

### 11.4 Tracing 可靠性边界

tracing 保持既有首次可靠范围：只有复位后的第一次实际启动，保证产生配对的 start 与 done/configuration-error 事件；元数据为 `startSwTag[7:0]`（RUN.swTag≠0 取 RUN.swTag，否则取 TRACE_TAG），PC 恒 0。RUN 独占 beat，一次接受只产生一次启动。被拒绝命令不另算执行启动。

tracing 状态在首次启动后不会返回初始态，后续执行的 tracing 行为不属于可靠接口契约；此次重设计不扩展为多任务可靠配对。软件不得用 tracing 代替 completion/errorCode 的结果判断或 `inFlight` 的在途判断。

## 12. 性能与资源规格

### 12.1 资源与容量口径

默认阵列几何为 `T=16`、PE 数为 `T×T`。FIFO 深度、端口宽度及其编译约束以第 8.1–8.2 节为准；单命令 M 上限以第 5.1.1 节的 `cBufferDepth-T` 为准。FIFO 容量按对应接口项计数，不能直接解释为可完整驻留的任意尺寸矩阵。

命令接口没有响应缓存：写 beat 恒可收、当拍提交，QRD 读响应同拍组合返回，RUN/READ 独占 beat；只有一个执行上下文，不是四个 GEMM 上下文或多批 FIFO。固定命令 lane 数不改变 MAC 数据 lane、PE 数或计算吞吐口径。

### 12.2 结构性处理拍数

WS 每个 `(N tile, K pass)` 权重窗口消费 T 个有效 B 拍与 M 个有效 A 拍。`loadWMode=0` 下两者串行；`loadWMode=1` 下 B 拍可与上一窗口的 A 拍及排空重叠（见 5.1.2），故有效拍数之和不等于窗口周期。OS-M1 每个 N tile 串行消费 `L=K/g` 个 A token，各列分别 consume=L；Run advance=L，Tail advance=T−1，全 K 后提交一行。默认 `osFmtKeep=0` 时每任务广播 T 拍；首窗口填充、K 窗口预取和上一 tile 写回可以部分重叠，不规定为零开销。每个 T 列输出行的 C 写拍数由输出位宽决定，见 5.3.4。

这些是有效数据消费/写请求次数，不是固定端到端延迟。配置阶段、预蓄、DMA 控制空泡、gather、供数反压、参数取数、输出转换及写响应等待均会影响实际周期数。OS 尾波的 T−1 是逻辑 advance 数，未完成列缺供数时会延长真实时钟。

WS 的相邻 K pass 之间经 C buffer 回灌部分和：下一 pass 的第 r 行激活以同一 r 行的上一 pass 结果作为累加基底，结果行按激活行序入 C buffer。结果行相对激活行的波前延迟为 `2T−1` 拍（纵向 valid 链 T−1 拍、末列横向 skew T−1 拍、输出寄存 1 拍），与 M 和数据格式无关。

该延迟的观测口径是结果行**写入** C buffer 的拍号；C buffer 为非直通 FIFO，读出口 valid 比写入晚一拍，而激活供数门控查的是读出口 valid。因此每个 `(N tile, K pass)` 的**有效稳态周期下界为 `2T` 拍**，该下界不随 M 减小而放松。供数门控包含 C buffer 读出口 valid：周期被压到下界以下时表现为激活供数停等（计入 `stallA`），不会静默回灌尚未就绪的部分和。

### 12.3 实测指标边界

本文未给出已测量的频率、端到端吞吐、面积或功耗保证值。不能把阵列几何、有效拍数或 perf 电平直接当作实测性能。

后续报告实测值时必须说明编译配置、工作负载、时钟频率、存储响应/反压条件、工具及适用的工艺条件，并按第 11.3 节区分 valid 周期与真实事务数。

## 13. 集成与使用规范

### 13.1 基本使用流程

本节单 ready beat、同拍读响应、wrErr、RUN 提交与 completion 消费相关用法与实现一致；数值与 DMA 软件义务不变。

1. 复位后确认 `inFlight=0`；校验 `M≥1、Nlogical≥1、K≥1`，计算 `Nprog=ceil(Nlogical/T)×T`；
2. 按 `Nprog` 准备 A、B、C 和可选 scale/bias/requant 缓冲区，并完成 B 转置、4-bit 打包和补齐列；
3. 写入 M/Nprog/K、A/B/C 地址和 CONTROL。选择 OS-M1 时必须写 `M=1、loadWMode=1、osM1=1、osFmtKeep=0`；选择 WS 时写 `osM1=0`，不能仅根据 M 推定数据流。写 beat 恒可收、没有写 ACK：软件以 `wrErr=0` 确认接口接受，必要时读回 shadow 核实生效值。避免跨 lane 同址写——同址按 last-wins（高 lane 胜）合法提交且不报任何冲突事件；
4. MX 运行写入 ScaleA/ScaleB/ScaleC 地址（索引 `0x06/0x07/0x08`）；
5. bias 运行写入 BIAS_ADDR（`0x09`）；
6. requant 运行写入 RQ_M_ADDR（`0x0A`）和 RQ_PARAM（`0x0B`）；
7. 在上一任务已结束后读取并处理旧 IntrRaw（0x60）/errorCode（0x41），向 IntrRaw 写 W1C 清除已处理的 DMA 错误位；需要电平中断时将 IntrMask（0x68）的相应位写 0，复位默认全部屏蔽。W1C 跨 lane 合并，可多 lane 同拍提交；需要确认清后值时在后续 beat 读取，同 beat 读是清除前值；
8. 按需写 TRACE_TAG（0xA0）与 hwSignal（0x80/0x81）；确认 `inFlight=0`（STATUS bit2）后单独发一条 RUN beat（建议携带非 0 swTag 以标识任务）。RUN 独占 beat，提交此前全部已接受配置写；`inFlight=1` 时 RUN 停在 ready 低等待，start 与接受同拍。RUN 被接受不表示计算完成或配置合法；
9. 等待 completion（Queen 无条件接收）：`error=0` 为正常完成；`error=1` 为配置错误，读 `code`（或下一拍读 0x41）并确认 `inFlight=0`，该路径不等待 Executor done。DMA 错误 IRQ 可能先于 completion 出现：仅 IntrRaw 置位时记录错误但继续等待 completion，不提前回收缓冲区，也不能只等 irq、busy 变化就认定完成；
10. 收到正常 completion 后仍检查 IntrRaw 全部 DMA 错误源；有已上报错误的任务不能据 `error=0` 判为结果有效。若配置写曾发生跨 lane 同址竞争，另须按 last-wins 规则核实实际配置是否符合软件意图。无已上报错误的任务在满足输出存储/cache 一致性及既有 DMA/ScaleC 集成约束后消费 C；completion 不扩展未标记内存错误的检测能力；
11. 保存诊断信息后按需 W1C 清 IntrRaw pending，fresh event 同拍优先于 W1C。若执行尚在途清错误位，软件须保留已观察错误记录，不能因此把该任务最终视为无错误。

### 13.2 重复执行

IntrRaw 跨启动保持粘滞；errorCode 在下一次 RUN 接受当拍清零；没有粘滞 `status.done` 字段，完成判定一律消费 completion。重复执行流程为：

1. 确认 `inFlight=0`（上一任务的 completion 已收到），并已处理上一任务的配置错误与 DMA 错误；不得在仅看到早期错误 IRQ 后立即启动下一任务；
2. 保存旧 IntrRaw/errorCode 诊断，W1C 清除已处理的 DMA 错误位；需要时在后续 beat 读回 IntrRaw 确认。同拍新事件会保持置位，不得当作清除失败而忽略；
3. 更新 shadow 配置，避免非预期同址竞争（last-wins 无冲突事件）。本步的配置写**可与上一任务执行重叠**（写不受 inFlight 门控），RUN 提交此前全部已接受写；
4. 确认 `inFlight=0` 后发下一条 RUN（独占 beat），通过新的 completion 与 `inFlight` 判断结束，再检查 IntrRaw 错误源。

轮询流程不依赖 mask；中断流程必须为所需 DMA 错误源解除屏蔽并在通知后读 IntrRaw 分辨来源。若选择保留旧事件，RAW 位不区分新旧任务，不能据旧值完成新任务判定。READ 响应同拍返回，不存在批间排空约束；RUN 在 `inFlight=1` 时整拍停等。

### 13.3 地址与缓冲区要求

软件必须保证：

- C 基地址按 `cDmaExtW/8` 对齐；
- bias/rqM 基地址按 4 byte 对齐；
- 正向 W4A8 的 B 基地址满足错误检查要求；
- gather 可能访问的相邻对齐字均位于合法存储区；
- B、C、bias、rqM 和 scale 均按 `Nprog` 提供完整补齐列；
- 在拍前 `inFlight=1` 时不提交 RUN；硬件让该 RUN beat 停在 ready 低等待，不以接口错误拒绝、也不排队执行。配置写在此期间合法且只落 shadow 组，正在执行的 live 组不受影响；READ 与中断/hwSignal/traceTag 写同样可用；
- 输出写回完成前，不回收或覆盖任何输入、参数和输出缓冲区；早期 DMA 错误 IRQ 不解除这一义务。

### 13.4 Cache 与一致性

WorkerBee 接口本身不定义 cache coherence。若系统 DMA 与 CPU cache 不一致，软件必须在 RUN 前 clean 输入和参数缓冲区，并在完成后 invalidate 输出缓冲区；具体操作由 SoC 平台定义。若利用执行期重叠准备下一任务配置，须在该次 RUN 之前完成对应缓冲区的 clean。

## 14. 约束与实现限制

### 14.1 当前实现限制摘要

- 唯一默认交付配置使用方形 `T=16` 阵列，所有默认受支持格式通路均存在，由运行期格式选择；
- OS-M1 仅支持 M=1/loadWMode=1，首行参与计算，不支持多行 OS；软件使用 osFmtKeep=0；
- 单命令 M 受 C buffer 容量限制；
- `M/N` 必须大于 0，但 RTL 没有专用错误码；
- 编程值 N 必须补齐为 T 的整数倍，硬件不自动缩窄末 tile；
- FP8 不能直接作为 C 输出；
- 默认配置的 C block 只允许 16；
- W4A8 只支持 A8×B4，不支持 A4×B8；
- `AddrMode`、`warmup`、`warmdown` 当前仅可读写，不改变数据通路；
- 固定四路单 ready native beat 共享 shadow/live 两组配置 bank 与一个执行上下文；写 beat 恒可收，RUN/READ 独占 beat，没有响应缓存或批间排空；
- 接口不排队执行任务；`inFlight=1` 时 RUN 停在 ready 低等待，READ、配置写与中断/hwSignal/traceTag 写仍可用；READ 由 resp.ready 反压，读响应与被接受读同拍；
- 写在被接受当拍提交：普通写跨 lane 同址 last-wins（高 lane 胜）、同 lane slot1 胜，W1C/W1S 跨 lane 跨 slot OR 合并；WRITE2 仅拒绝原子（任一项非法整条 wrErr），合法后两项按地址各自提交，不保证跨 lane 两项一起胜出；读不旁路同拍写或新事件；
- RUN 被接受不表示计算完成或配置合法；done/configError 经 completion 回传，DMA/总线错误经 IntrRaw/irq 回传，均不经读响应；
- IntrRaw 不因 RUN 自动清除；errorCode 在下次 start 清零；正常 completion 不表示无 DMA 错误，错误 IRQ 不表示任务已终止；
- 中断有效位 8 bit、全部为 DMA/总线错误源，高 24 bit 保留、reset mask=0xFF、fresh event 优先于 W1C；没有完成/配置错误/WRITE_CONFLICT 中断位；
- tracing 只保证复位后的首次执行；
- 仅握手响应上的 `rsp.err` 可作为 DMA 错误事件，未标记的内存错误不可检测，assert 不转为 IRQ；
- perf DMA 事件统计 valid 周期，不等价于事务数；
- ScaleC 写响应不得无限延迟，第 9.6 节的在途与反压限制不变。

### 14.2 不属于规格支持功能的项目

以下项目不得依据字段存在或相关功能 ID 推定为受支持能力：

- `AddrMode` 的 TILE 寻址、`warmup`、`warmdown` 的数据通路行为；当前仅保留字段读写；
- A4×B8 反向混配、FP8 C 输出、自动 N 尾 tile 缩窄写回；
- 执行任务排队、多批命令队列、四 lane 独立配置/执行上下文、第三组配置缓冲（执行期即提交下一个 RUN）、错误 fail-fast/事务撤销、未标记内存错误检测、多任务可靠 tracing；flush 恢复只 block 新 RUN 并等自然结束，不做安全 abort、不复位 RegIf、不抑制 completion（第 6.10 节）；
- 可配置命令 lane count 或寄存器 count、将命令 lane 解释为 MAC 数据 lane、同 beat 多条 RUN/READ、写 ACK 响应、读旁路同拍写、跨 lane 同址写的位域合并或冲突事件、跨 lane WRITE2 两项共同获胜保证；上述非目标边界不扩展既有计算/DMA 能力；
- 独立 IRQ ENABLE、自动启动清 IntrRaw、以正常 completion 表示无 DMA 错误或将断言转换为 IRQ；
- 内建 PMU 计数器、cache coherence、SiLU/GELU 或外部 Scheduler 功能。

## 附录：补充资料

### A.1 关键公式

```text
T            = totalN = arrayN
nTiles       = ceil(N / T)
elemsPerLane = 4 for INT4/MXFP4
               2 for INT8/MXFP8/FP8
               1 otherwise
kPerBeat     = T × elemsPerLane
kTiles       = K / kPerBeat
Mmax         = cBufferDepth - T

OS-M1: M=1, loadWMode=1
L            = K / elemsPerLane
consume[j]   = L
RunAdvance   = L
TailAdvance  = T-1
advanceTile  = L+T-1
commitTile   = 1
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
