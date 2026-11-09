# L1 性能模型重构方案

2026-11-09 ｜ 已实施，性能未校准 ｜ 首个迁移对象：Qwen3-0.6B

## 1. 目标与范围

目标架构是：模型算子展开成带数据依赖的原子任务，各引擎模型计算局部成本，独立调度层决定任务在具体硬件资源上何时完成。

一个算子可以包含 3 个 GEMM、2 个 VPU、N 个 CPU 任务，不再要求一个算子只能对应一个引擎。GEMM、VPU、CPU、DMA 可以有多个实例，但只有数据依赖、物理资源、缓存容量和硬件重叠能力都满足时才允许并行。

沿用纯 Python，不引入 Timeloop 运行时。借鉴其映射描述、分层访问计数和 buffer 合法性检查，不引入通用 mapspace 搜索。

### 1.1 必须交付

1. 语义算子与原子执行任务分离，明确 tensor 的生产者、消费者和格式。
2. 独立的 GEMM、VPU、CPU、DMA 模型，统一返回分阶段成本、资源需求、流量和缓冲需求。
3. 单实例与多实例拓扑，区分私有 SRAM、共享带宽、端口和引擎资源。
4. 基于依赖与资源预约的确定性调度，输出任务及阶段时间线。
5. 分层流量、峰值活跃容量、资源 busy time、等待约束与关键路径归因。
6. 保留结构锚、来源和未知成本，不把部分预算包装成完整性能预测。

### 1.2 不在本方案内

- 不修改 L0 的模型工作量、算法或命名硬件口径。
- 不修改 `performance_analysis.md`、`model_analysis/硬件口径.md` 或模型拆解文档。
- 不修改 RTL、gem5、寄存器接口或硬件执行机制。
- 不建立逐拍 ready/valid、FIFO、retry 或仲裁状态机，不替代 L2/L3。
- 不实现 runtime、驱动、指令提交器或数值张量计算。
- 不同时扩展全部模型族，先迁移当前 Qwen frontend。
- 不通过增加引擎实例自动拆分单个 GEMM，不实现自动最优 tiling。
- 不维护永久的旧后端兼容层；按阶段直接迁移现有调用者和测试。

本方案描述目标接口和迁移工作，不表示多实例服务率、bank 并发能力或未知成本已经被验证。

## 2. 迁移前代码与职责变化

下表记录迁移前的职责，旧模块不作为保留接口；实施后的模块布局见第 6 节。

| 迁移前文件 | 迁移前职责 | 重构后的职责 |
|---|---|---|
| [common/opspec.py](../model_eval/common/opspec.py#L15) | `OpSpec` 绑定一个 engine，包含 `GemmGroup` 和依赖 | 区分语义算子与原子任务，增加 tensor identity、切片和专用任务规格 |
| [l1/mapping.py](../model_eval/l1/mapping.py#L21) | 每个算子依赖前一个算子，形成单链 | 根据真实生产/消费关系展开任务图 |
| 原 `l1/gemm_geom.py` | 格式守护、补齐和命令合法化 | 合入 `gemm_model.py` 内部命令映射函数 |
| 原 `l1/windows.py` | 命令内部 N tile、K pass 的窗口及聚合计数 | 合入 `gemm_model.py` 内部计数函数，不保留平行模块或重导出 |
| [l1/kernels.py](../model_eval/l1/kernels.py#L27) | GEMM 成本、跨 K 的 VPU 累加、引擎分发和 DMA 成本 | 只保留薄分发，不持有成本算法 |
| 原 `l1/vpu.py` | VPU 与 CPU sampling 共用成本入口 | 拆成 `vpu_model.py`、`cpu_model.py` |
| [l1/ledger.py](../model_eval/l1/ledger.py#L19) | 原始资源账本、同源折叠 | 统一阶段、资源请求、流量、来源与耦合证据 |
| [l1/residency.py](../model_eval/l1/residency.py#L21) | 单个 active footprint 与整体翻倍判断 | 明确执行计划、缓冲布局、生命周期和容量合法性 |
| [l1/timeline.py](../model_eval/l1/timeline.py#L132) | 顺序递增 cursor，按相邻 engine 计交接 | 编排任务图、调用调度器、生成结果 |
| [hw/profiles.py](../model_eval/hw/profiles.py#L32) | 命名常数，混合 per-core 属性和 cluster 核数 | 保留常数；另建拓扑描述物理实例与共享关系 |
| [l1/web.py](../model_eval/l1/web.py#L11) | 聚合串行算子时间、输出单 active core | 展示任务图、实例、并发、等待与内存峰值 |
| [l1/run.py](../model_eval/l1/run.py#L73) | 固定单核及当前重叠范围的 CLI/manifest | 记录实际拓扑、执行计划、调度策略和覆盖范围 |

### 2.1 迁移前限制

- 迁移前 `mapping.py` 的依赖是拓扑单链，不能表达 Q/K/V、gate/up 等独立分支。
- `kernels.py` 内的跨 K 累加消耗 VPU，但在任务图中不可见。
- `GemmGroup.repeats` 通过乘数聚合，无法直接绑定不同实例或表达各份输入的共享关系。
- 资源名称含 op/group 前缀，不足以证明不同任务使用不同物理端口。
- `2 × footprint` 无法准确表达当前操作数、预取、待写回和下游消费的不同生命周期。
- 未知 charge 被折叠时跳过；部分特殊函数缺参时仅写入 excluded 清单，没有保留未知执行阶段。
- `sum(op.t_s)` 适用于串行预算，不能作为并行任务图的完成时间。
- L1 固定一个 active core，而 profile 的计算峰值可能按 cluster 核数汇总，跨层比较需要明确资源口径。

## 3. 目标架构

```text
ModelSpec
    │ 模型形状、工作点、量化格式
    ▼
LogicalOp
    │ 模型算子与报表归属
    ▼
TaskGraph
    │ GEMM / VPU / CPU / DMA 原子任务、tensor、依赖
    ▼
ExecutionPlan + Topology
    │ 命令分块、存储位置、实例候选、缓冲与复用策略
    ▼
EngineCost
    │ 分阶段服务成本、物理资源需求、流量、缓冲需求
    ▼
ResourceScheduler
    │ 依赖、实例、端口、带宽、容量与可见性约束
    ▼
ScheduleResult
      时间线、完成时间、资源占用、流量、容量峰值与归因
```

| 层 | 负责 | 不负责 |
|---|---|---|
| 模型/算子展开 | 需要执行哪些任务、输入输出与数据依赖 | 硬件耗时、全局排程 |
| 命令与存储计划 | tile、驻留、流式补给、搬运与缓冲需求 | 自动最优搜索、真实总线仲裁 |
| 引擎模型 | 指定任务在指定引擎与执行计划下的局部成本 | 全局开始时间、多任务资源分配 |
| 调度层 | 实例选择、资源预约、缓冲生命周期、开始/结束时间 | 重写引擎算术公式、逐拍仿真 |
| 报告层 | 任务与模型归属、资源占用、等待与可信度 | 通过报表修正计算结果 |

引擎模型可以直接返回单任务已知服务预算，但调度必须使用其阶段、资源和缓冲明细，而不是只接收一个时间标量。

算子分解由 `mapping.lower_model(..., lowering=自定义函数)` 完成；用户也可直接构造 `TaskGraph` 后调用 `timeline.evaluate_graph`。调度器只做实例绑定与资源预约，不拆分或重写用户任务图；结构校验不等价于数学算法等价性证明。

## 4. 数据契约与接口

### 4.1 语义算子与原子任务

`LogicalOp` 保留模型侧的 layer、name、语义工作量和报表归属。它不占用引擎资源，也不限制内部任务数量。

`PrimitiveTask` 是可调度节点：

| 字段 | 含义 |
|---|---|
| `id` | 唯一任务 ID |
| `parent_op_id` | 所属语义算子 |
| `engine_kind` | GEMM、VPU、CPU 或 DMA |
| `dependencies` | 必须完成或达到明确可见性条件的前置任务 |
| `inputs`、`outputs` | tensor 引用及实际读取/写入切片 |
| `spec` | 对应引擎的任务规格 |
| `allowed_instances` | 允许绑定的物理实例 ID；core/内存域访问另由 Topology 校验 |

`TaskGraph` 保存任务、tensor 和依赖，并检查唯一 ID、缺失引用、环和不一致的格式/切片。

tensor 引用至少说明：base identity、形状、格式、布局/别名、实际切片、生产者和消费者。共享 view 不代表新分配，`repeat_kv` 等别名不得自动生成重复容量或物理 copy。

### 4.2 引擎输入规格

| 规格 | 必须表达的输入 |
|---|---|
| `GemmSpec` | M/N/K、A/B 格式、C 类型、MX block、有效/掩码工作量、A/B 的实际 storage shape |
| `VpuSpec` | kernel 类型、元素/向量数、实际长度、归约轴、输入输出格式、有效元素范围 |
| `CpuSpec` | 支持的计算类型、输入长度/数量、算法情景及显式服务参数 |
| `TransferSpec` | 源/目的内存域、tensor 切片、字节数、布局与方向、唯一 stream ID |

CPU 只对已支持的服务估算成本。sampling 的比较预算不能代替任意 CPU 计算；形状本身不足以决定 CPU 算法耗时。

格式必须完整描述类型。INT32 与 FP32 虽都占 4 B，但不能视为同一种数据，转换成本也不能默认是零。

Tensor 的 MX scale 容量按 `scale_axis` 逐行取整：A 的 K 轴为 1，B 的 K 轴为 0。GEMM 补齐后的 N/K 需要真实 backing allocation；默认展开器显式生成 VPU repack/zero-padding 任务，其服务保持未知，后续 GEMM 才读取补齐存储。手工任务图同样核验整个 backing 已写满，不能只声明更大的 storage shape。K 分命令的 scale 请求保留 backing stride 与全局 K offset，只有显式 repack 的 A staging 才使用 command-local 布局。

### 4.3 调用接口

```text
mapping.lower_model(model, fmt, point, lowering=custom_lowering) -> TaskGraph

gemm_model.estimate(task_with_gemm_spec, engine_config, execution_plan) -> EngineCost
vpu_model.estimate(task_with_vpu_spec, engine_config, execution_plan) -> EngineCost
cpu_model.estimate(task_with_cpu_spec, engine_config, execution_plan) -> EngineCost
dma_model.estimate(task_with_transfer_spec, engine_config, execution_plan) -> EngineCost

timeline.evaluate_graph(graph, topology, execution_plan=plan, scheduling_policy="list") -> ScheduleResult
scheduler.schedule(graph, costs, topology, buffer_plan, policy="list") -> ScheduleResult
```

`engine_config` 使用指定实例的几何、时钟、格式与服务能力。若候选实例不同，按候选实例分别估算；不能先用一个统一吞吐值估算，再任意绑定到另一类实例。

`execution_plan` 明确影响局部成本的存储策略，包括 B 流式/驻留、A staging、C 输出、scale 布局和可执行分块。引擎模型不自行推测整个模型已经常驻 SRAM。

### 4.4 EngineCost

| 内容 | 字段/含义 |
|---|---|
| `phases` | 阶段 ID、阶段依赖、耗时或未知标记 |
| `claims` | 引擎占用范围、物理端口请求、带宽需求 |
| `traffic` | stream ID、tensor、接口、方向、有效字节、物理拍 |
| `buffers` | 分配大小、对齐、内存域、生命周期端点 |
| `metrics` | 有效/补齐工作量、命令、窗口、重装量 |
| `source`、`evidence` | structural、architecture_peak、fallback、scenario 等来源 |
| `excluded_costs` | 未覆盖的服务及其影响 |

引擎资源占用与阶段耗时分开表示。例如 GEMM 可能需要从 loadW 到 consume 完成持续保有实例状态，不能因为阶段边界释放实例，让另一个任务覆盖已装载权重。

同一流式传输经过多个接口时，可以形成有证据的 coupled segment；先 staging 再计算则是不同阶段。不能跨串行阶段把同源需求整体取 max。

### 4.5 ScheduleResult 与未知时间

结果分别保留：

- task/phase 的实例绑定、开始与结束时间；
- 已知服务预算；
- 可确定时才给出的 `makespan`；
- 各物理资源 busy time、等待约束及释放事件；
- 各内存域峰值活跃容量；
- 关键路径、归因与未覆盖成本。

未知阶段保留为节点，不转成零耗时。其影响传播到下游依赖，也传播到无法证明不冲突的共享资源预约。

对不受未知阶段影响的独立部分可以输出已知信息，但不能为受影响的任务伪造确定开始/结束时间。若另输出忽略未知项的投影时间线，必须标记为条件化投影，不能使用它证明实际并行或生成完整吞吐。

## 5. 任务展开与命令映射

### 5.1 一个组合算子的例子

```text
输入就绪
   ├─► G0: GEMM(A0[M,K0], B0[K0,N0]) ─┐
   └─► G1: GEMM(A1[M,K1], B1[K1,N1]) ─┤
                                      ▼
                             V0: VPU(G0.out, G1.out)
                                      │
                                      ▼
                             G2: GEMM(V0.out, B2)
                                      │
                                      ▼
                             V1: VPU(G2.out)
                                      │
                        ┌─────────────┼─────────────┐
                        ▼             ▼             ▼
                       C0            C1         C(N-1)
```

G0/G1 的输入与输出明确独立时，有两个 GEMM 实例可能重叠它们的时间。V0 必须等两者，G2 必须等 V0，增加实例不会消除这些依赖。

CPU 分支是否独立由输入输出及算法定义决定，不能只按 N 个调用判断独立。跨 core 或内存域执行时，图中还需 copy 和完成可见性节点。

### 5.2 Qwen 首个迁移对象

- Q/K/V 投影从共享的归一化输入分叉，不因代码列表顺序互相依赖。
- Q/K norm、RoPE 根据实际输入建立边，AV 等待所需 V 和 attention 权重。
- gate/up 投影独立，后续 SiLU/门控与 down projection 根据消费关系合流。
- 两条残差输入保留到实际消费者完成，不能因当前算子结束提前释放。
- KV append/read 与当步 attention 的边，按现有上下文长度和缓存语义核对，不能因重排改变 S 的含义。
- prefill 的有效因果工作量与实际发射的 dense masked 工作量继续分开。
- 逻辑组合算子可拆成多个任务，但模型层级报表仍按 `parent_op_id` 聚合。

### 5.3 分块、重复与部分和

- `GemmGroup.repeats` 展开为可绑定实例的任务，保留各头/各 batch 的输入切片与共享关系。
- 不能将 repeated head GEMV 偷换成共享同一 B 的普通 GEMM。
- 一个 GEMM 不因引擎数增加自动分片；分片必须明确输出范围和合并依赖。
- 跨独立 K 子命令的部分和由显式 VPU/合并任务处理，不隐藏在 GEMM 成本中。
- 每个 K 子命令的输出、累加器与搬运缓冲都进入生命周期账。
- INT32 部分和不能默认为 FP32；未覆盖的转换或累加服务保持未知。
- 硬件窗口是引擎内部计数单位，SRAM 分配块是存储计划单位，两者不要求相等。
- 不为每个 PE 窗口建立调度节点。命令/阶段采用聚合计数，保留惰性调试展开，避免海量窗口物化。

## 6. 引擎模型与文件命名

| 模块 | 目标职责 |
|---|---|
| `l1/gemm_model.py` | 单一 GEMM 模型模块，内部包含命令合法化、窗口聚合计数、阶段、端口和格式成本 |
| `l1/vpu_model.py` | 由 `vpu.py` 迁移，向量、归约和特殊函数服务 |
| `l1/cpu_model.py` | 从 `vpu.py` 拆出 CPU sampling，并接收明确支持的 CPU 任务 |
| `l1/dma_model.py` | 显式传输任务及接口服务 |
| `l1/kernels.py` | 仅按 engine kind 分发，无成本算法 |
| `l1/dram.py` | 现有基础带宽/周期换算，不冒充完整 DMA 模型 |
| `hw/topology.py` | 物理引擎、core、端口、带宽和内存域 |
| `l1/residency.py` | 存储执行计划、分配与容量合法性 |
| `l1/scheduler.py` | 依赖与资源日历上的确定性排程 |
| `l1/timeline.py` | 总体编排、结果与模型归属聚合 |

实施采用这份模块布局。公共规格与账本直接扩展，不另建平行契约，不保留旧 GEMM 几何/窗口模块或旧 VPU 后端。

硬件变化分两类：

1. 尺寸、时钟、格式支持等参数变化，由冻结几何及实例配置体现。
2. 装载/消费顺序、状态保持、排空等执行机制变化，修改对应 engine model 的结构算法。

内部固定执行事实不作为任意用户配置开关。拓扑情景可以指定实例数和服务假设，但不能把当前后端不支持的 loadW/consume 重叠伪装成既有能力。

## 7. SRAM 容量与分层流量

### 7.1 单任务计划

容量按实际分配后的大小核算，包含：

- A/B 数据、scale 与补齐；
- C 输出、部分和及累加器；
- 当前任务操作数、下一任务预取和上一任务待写回；
- VPU scratch、转换 staging 和显式保留区。

```text
C_live(memory_domain, time)
    = 在该时刻仍活跃的唯一物理分配大小之和

C_peak(memory_domain)
    = max_time C_live(memory_domain, time)
```

只对同时活跃的分配相加；共享 tensor/view 按真实 base allocation 计一次。数据产生期间即可占用缓冲，不能等产生完成才开始记容量。

局部工作缓冲与跨任务输出生命周期分开：前者随明确阶段结束释放，后者从首次写入保留到最后消费者读完或所需搬运完成。

### 7.2 合法性检查

- 容量、对齐、实际布局与地址区域都必须满足，不只检查 tensor 有效元素字节数。
- 一个任务的计划超限，报告超限区域和需求；不得静默缩 tile 或插入免费 spill。
- 如果执行计划明确允许 stream、分块或 spill，按该计划重新计流量、成本与缓冲。
- 多任务单独 fit 不等于同时 fit；全局峰值由调度器核验。
- 每 core 私有 SRAM 不池化；共享 SRAM 单独核算，不能按容量相加扩大 local SRAM。
- 容量或布局未知，不宣称驻留或重叠成立。
- 当前放置策略无可行下一步与单任务硬超限分别报告；前者不证明整个 workload 不可部署。

本阶段不做自动最优内存规划。固定计划可失败并指出原因，而不是偷偷改变用户指定的执行方式。

### 7.3 唯一传输与多接口计账

每次真实传输有唯一 `TransferSpec/stream ID`，明确由独立 DMA 任务或引擎流式阶段拥有。

同一个 tensor 穿过 DRAM、DMA、local port、SRAM，在各接口均可记录其访问量；同一 DRAM 搬运不能因出现多个参与任务被重复计入。

先 staging 再本地复用：首次外部装载与后续本地重读分阶段计账。流式经过多个接口：只在具体 coupled segment 内、且有数据路径与服务依据时折叠。

分层报表统一包含：

| 字段 | 用途 |
|---|---|
| parent op、task、phase | 定位算子与执行阶段 |
| tensor、stream ID | 追踪数据身份与搬运唯一性 |
| memory domain、interface、direction | 区分 DRAM/local/共享层和读写 |
| access count、logical bytes | 有效数据访问及复用 |
| physical beats、physical bytes | padding、端口宽度与窄请求占用 |
| reload bytes、reuse factor | 解释容量不足或执行顺序导致的放大 |

## 8. 多实例资源调度

### 8.1 Topology

资源至少分四类：

| 类型 | 例子 | 约束 |
|---|---|---|
| 独占引擎 | GEMM instance、VPU instance、CPU instance、DMA instance | 同一实例的状态与执行占用 |
| 端口/请求槽 | SRAM bank、共享读写口、DMA local 口 | 并发服务槽与读写冲突 |
| 带宽 | 共享 DRAM、system interconnect、local 接口 | 同时预约的速率不能超过已知能力 |
| 容量 | core 私有 SRAM、SoC SRAM | 活跃分配及布局不能超限 |

FP16/FP32 VPU 服务不能仅因账本名称不同就成为两台独立 VPU。op/group 前缀也不构成独立物理端口。

默认保持单 active core 的串行分析配置。多实例必须显式描述来源、实例属性、共享关系和可访问内存域，不按 `profile.n_core` 自动复制 GEMM/VPU/CPU/DMA 的全部能力。

### 8.2 排程规则

采用非抢占、固定带宽预约的解析 list scheduling：

1. 校验任务图、执行计划及拓扑。
2. 计算依赖就绪条件，按稳定的拓扑序及任务 ID 选择候选。
3. 对允许的实例计算最早可行开始时间。
4. 在整个所需区间核验 engine lease、端口请求槽、带宽预约和缓冲分配。
5. 候选冲突时推进到相关释放/日历端点，不逐周期扫描。
6. 选择最早结束的可行实例；平局按固定实例 ID 处理。
7. 记录绑定、预约、数据可见性、缓冲释放及等待原因。
8. 无前进条件时报告当前计划的阻塞约束，不无限等待。

单任务内部阶段依赖由 engine model 指定；全局调度不得重新排序有状态阶段。

带宽不足时延后任务，不中途猜测动态均分或重算任务速度。固定预约属于解析服务假设，报告中必须说明，不能替代真实 burst/仲裁验证。

未知 bank 映射或重叠能力时，采用明确的保守并发限制，并保留未覆盖声明。仅总带宽足够不构成各 bank 无冲突的证明。

### 8.3 重叠的条件

允许重叠必须同时满足：

- 输入已产生且在目标实例可见；
- 无未满足的数据依赖或读写别名冲突；
- 引擎及端口服务槽可用；
- 共享带宽满足整个预约区间；
- 生命周期内的缓冲与地址区域合法；
- 后端执行机制支持该重叠方式；
- 所依赖的阶段与资源占用足够已知。

“无前后依赖”是必要条件，不是充分条件。增加两个 GEMM 实例仍可能因共享 DRAM、本地 bank 或中间输出容量而排队。

### 8.4 跨 core 与同步

跨 core 依赖需要显式源/目的放置、copy、完成和可见性条件。目标 GEMM/VPU 不得免费访问另一个 core 的私有 SRAM。

handoff 根据实际依赖边与放置产生，不继续使用列表中相邻 engine 名称变化作为全部同步开销。复制与同步不得对同一可见性服务重复收费；未知同步服务保留为未知阶段。

## 9. 时间、归因与下界

### 9.1 三种时间分别报告

| 指标 | 含义 | 可否相加成端到端时间 |
|---|---|---|
| 任务服务预算 | 单任务各已知服务需求 | 并行时不可直接相加 |
| 资源 busy time | 具体物理资源的占用区间并集 | 跨资源之和可能大于 makespan |
| makespan | 整张图从起点到最后任务完成的跨度 | 所需成本已知时才有确定值 |

parent op/layer 可以报告其任务跨度、区间并集和工作量，不把层跨度累加成整体 makespan。

JSON schema 2 分别输出 `known_service_budget_s`、`makespan_s` 与 `projection_makespan_s`。未能绑定的任务保留 `instance_id=null`；候选成本单独标注 `budget_instance_id` 和 `cost_basis=unbound_candidate_budget`，不冒充已经排程的实例。未知服务导致实际排程不完整时，条件投影仍可存在；未知容量或固定计划无解时，投影也保持未知。跨核 DMA 预约两端真实 local 端口，路由/可见性成本保持显式未知。

### 9.2 关键路径归因

保留 `dram/gemm/vpu/cpu/handoff` 五类，但不再对并行任务的原始服务需求直接求和。

从执行图中提取确定性关键路径，图中包含数据依赖、引擎释放、带宽/端口预约以及缓冲释放造成的约束。用不重复区间归因，稳定处理并列限制。

```text
sum(exclusive critical-path attribution) = known makespan
```

资源等待另外记录阻塞条件和释放事件。同一等待可能同时涉及带宽和容量，保留这些依据，不仅输出一个胜者标签。

关键路径归因是当前映射与调度策略的结果，不等于通过一次分析证明硬件存在唯一固有瓶颈。

### 9.3 与 L0 的关系

L0 算法和输出保持不变，作为原口径的参考。

L1 的资源下界检查必须使用：实际 active resources、任务引擎路由及选定存储计划下的流量。异构工作量按相关引擎资源分别建立下界，不能把全部 MAC 除以 cluster GEMM 峰值。

只有工作量、精度、存储流量和资源口径一致时，才声明跨层可比较；否则明确 `cross_level_comparable=false` 并给原因。禁止使用 `max(L1, L0)` 修补时间。

P310 缺少时钟、SRAM 或端口服务时保持未知，不因多实例调度推造新的完整服务率。

## 10. 迁移故事

S1–S11 已按以下范围实施，最终回归和浏览器验证记录见第 11.4 节。下列任务保留为交付边界，不再表示待实施。

### S1：建立任务图与引擎成本契约

依赖：无。

任务：

1. 在现有公共规格中区分 LogicalOp/PrimitiveTask，增加 tensor identity、切片与格式。
2. 定义 TaskGraph 及 GEMM/VPU/CPU/DMA 专用任务规格。
3. 在账本中定义阶段、物理资源需求、传输流和缓冲生命周期。
4. 将未知服务保留为阶段，不仅写字符串清单。
5. 增加重复 ID、缺失依赖、环、格式不一致及未知阶段的契约测试，记录迁移结构锚。

验收：可构造并校验 3 GEMM/2 VPU/N CPU 示例图，CLI 路径暂不改变。

### S2：隔离完整 GEMM 模型

依赖：S1。

任务：

1. 将 GEMM 成本移入 `gemm_model.py`，保留现有合法化和窗口算法。
2. 将 `gemm_geom.py` 与 `windows.py` 的合法化和计数实现合入单个 `gemm_model.py`，迁移所有引用，移除旧模块，不保留重导出。
3. 输出 loadW/consume/write 阶段及实例状态保持范围。
4. 保留 scale、补齐工作量、C 输出拍数等独立计数。
5. 将跨 K 累加需求交给展开层，移除 GEMM 对 VPU 的隐藏消耗。
6. 迁移几何测试并逐项守住结构锚。

验收：单独调用 GEMM 模型得到阶段成本；几何、格式、聚合计数测试通过。

### S3：分离 VPU 与 CPU 模型

依赖：S1。

任务：

1. 将 `vpu.py` 迁移为 `vpu_model.py`，CPU sampling 拆入 `cpu_model.py`。
2. 返回向量、归约、特殊函数和 local 服务阶段，保留因果元素数。
3. CPU 按明确支持的任务及算法情景估算，拒绝未知任务类型。
4. 缺失特殊函数或 CPU 参数时保留未知阶段与来源。
5. 为跨 K 合并定义显式任务，区分 FP32/INT32 和未覆盖转换。
6. 验证调用量、batch 采样预算和未知服务行为。

验收：三个计算引擎模型分别可调用，CPU 不再借道 VPU 分发。

### S4：统一 DMA 模型和分层流量身份

依赖：S1。

任务：

1. 将 DMA 成本分支移入 `dma_model.py`，明确源/目的、切片与方向。
2. 每次真实搬运建立唯一 TransferSpec/stream owner。
3. 各接口独立计流量，避免重复的 DRAM 服务量。
4. 将耦合证据限定到具体段，区分 staging 与流式。
5. 实现统一分层流量汇总。
6. 验证同流多接口、独立重读、scale 窄请求和显式 spill/reload。

验收：同一次搬运在各接口均有记录，但外部传输不会重复收费。

### S5：描述物理拓扑与多实例资源

依赖：S1。

任务：

1. 建立 Topology，描述实例、core、格式、时钟、端口共享和内存域。
2. 分别描述独占引擎、请求槽、带宽与容量。
3. 默认复现单 active core，多实例显式给出来源。
4. 去除任务/group 前缀对物理资源独立性的错误暗示。
5. 在 L1 内建立同路由、同资源、同存储口径的下界检查，L0 保持参考。
6. 验证私有容量不池化、DRAM 不随核数倍增和未知拓扑不推断并行。

验收：能构造单实例/双 GEMM 拓扑并查询真实共享资源。

### S6：明确执行与缓冲计划

依赖：S2、S4、S5。

任务：

1. 区分命令窗口与 SRAM 分配块，记录补齐、格式、scale 和缓冲份数。
2. 用实际生命周期替代整体 footprint 翻倍。
3. 根据明确 stream/resident 策略产生 BufferPlan，未知容量不推断驻留。
4. 核验容量、对齐和区域，超限报告具体项，不免费缩 tile/spill。
5. 为输出、共享输入、部分和与 scratch 定义生命周期。
6. 增加边界容量、双缓冲、FP32 输出与 K 部分和用例。

验收：每个执行计划都能列出分配明细、峰值依据和超限原因。

### S7：接通新接口的串行端到端路径

依赖：S2、S3、S4、S6。

任务：

1. 接通任务图、执行计划、引擎模型与串行时间线。
2. 展开 repeats 与 K 累加，保留 parent op 和共享 tensor 身份。
3. 暂时保持原执行顺序，核验工作量、拍数和流量无意外变化。
4. 未知阶段阻断确定完成时间，单独输出已知服务预算。
5. kernels 只保留薄分发，直接迁移调用者，不保留永久双后端。
6. 更新引用、源码指纹与集成测试；正确性修复造成的差异逐项说明。

验收：CLI 经新接口完成单核串行分析，结构锚和未知声明保留。

### S8：将 Qwen 单链改为真实数据依赖图

依赖：S7。

任务：

1. 去除无条件 previous-op 依赖，依据 tensor 生产/消费建边。
2. 开放 Q/K/V 和 gate/up 分支，保留归约、残差及合流依赖。
3. 拆分逻辑组合算子，报表仍按原 parent op/layer 聚合。
4. 核对 KV append/read 与当步 S 语义，重排不得改变 attention 范围。
5. 跨域输入显式 copy/可见性，转换或同步未知时保留阶段。
6. 验证示例图、fork/join、共享输入、残差及 decode/prefill 有效 MAC。

验收：图能展示独立分支和合流，串行执行仍保留相同语义工作量。

### S9：实现确定性的依赖与实例调度

依赖：S5、S6、S8。

任务：

1. 实现非抢占 list scheduling，固定就绪顺序与实例平局规则。
2. 按阶段依赖排程，GEMM lease 覆盖状态保持范围。
3. 先对合成图验证多实例；CLI 暂不开放未经共享资源核验的并行。
4. 记录实例占用日历、就绪时间与释放约束。
5. 未知占用传播到依赖和潜在共享资源冲突。
6. 验证单/双 GEMM、CPU 并发、fork/join、确定性及不自动分片。

验收：已知成本的合成图得到可手算核对的多实例时间线。

### S10：加入端口、带宽与容量预约

依赖：S4、S9。

任务：

1. 日历增加端口互斥、服务槽和固定带宽预约，核验整个执行区间。
2. 带宽不足延后执行，不猜测动态均分。
3. 在具体内存域预约输入/输出/scratch，核验生命周期峰值与区域复用。
4. 纳入跨 core copy、完成和可见性，不免费跨私有内存访问。
5. 未知 bank/能力时限制并发；无前进条件时输出当前计划阻塞原因。
6. 验证共享 DRAM、bank 冲突、读写口、待消费输出容量及跨 core 搬运。

验收：独立任务也能因带宽/容量排队，等待原因可追溯。

### S11：交付报告、CLI 与最终回归

依赖：S10。

任务：

1. 分开服务预算、资源 busy time、makespan；parent op/layer 不再累加为端到端时间。
2. 提取包含数据和资源等待约束的确定性关键路径，保留不重复五类归因。
3. 展示实例、等待、分层流量和 SRAM 峰值。
4. 更新 CLI 拓扑入口、JSON schema、manifest、离线网页和输入指纹。
5. 未知 makespan 不输出完整吞吐，保持未校准与未覆盖声明。
6. 跑 L0/L1 回归和 CLI 矩阵，并在浏览器验证串行、并行、未知和超容量案例。

验收：用户能看到一个算子内部的多个任务如何重叠、为何等待，以及完成时间是否可确定。

### 10.1 依赖关系

```text
S1
 ├─ S2 ─┐
 ├─ S3  │
 ├─ S4 ─┼─ S6 ─ S7 ─ S8 ─ S9 ─ S10 ─ S11
 └─ S5 ─┘
```

图只展示主干；各故事列出的完整依赖是执行依据。S2/S3/S4/S5 可在 S1 契约稳定后独立推进，但共享契约及分发文件必须由明确责任方修改，不能并发覆写。

## 11. 验证与交付条件

### 11.1 结构锚与语义锚

以现有测试为迁移基准：

- [test_l1_geometry.py](../model_eval/tests/test_l1_geometry.py#L27)：命令顺序、格式、补齐、窗口、输出拍数与惰性计数。
- [test_l1_integration.py](../model_eval/tests/test_l1_integration.py#L115)：整模型结构账与串行基线。

当前明确结构锚包括：

| 锚 | 值/要求 |
|---|---|
| 28 层权重投影加 LM head array cycles | 19,788,544 |
| 权重 B logical bytes | 595,984,384 |
| C rows | 31,000 |
| norm vectors，decode S=4096 | 729 |
| softmax vectors，decode S=4096 | 448 |
| M=1 的结构利用率 | 1/17 |
| M=48 的结构利用率 | 48/64 |
| 每层权重 GEMM | 7 个，另有 1 个 LM head |

这些锚限定现有几何与输入情景，不能推广为所有硬件版本的固定常数。语义工作量继续验证 decode 与原模型账一致、prefill 因果对角线及 softmax 差异显式列出。

串行结构和流量应保持；未知阶段传播、正确资源口径和显式转换造成的报告差异应解释，不能为守住旧时间数值恢复错误假设。

### 11.2 可手算调度样例

对第 5.1 节的合成图，用 stub 引擎成本设 G0/G1/V0/G2/V1 的已知耗时分别为 10/8/4/6/2 ms，设 4 个独立 CPU 任务各 1 ms、2 个 CPU 实例。

这是完全已知的测试情景：输入预先就绪，缓冲、端口和带宽无竞争，同步成本明确为测试值零，不代表真实硬件。

| 情景 | 手算完成时间 |
|---|---:|
| 1 个 GEMM 实例 | 10+8+4+6+2+2 = 32 ms |
| 2 个 GEMM 实例 | max(10,8)+4+6+2+2 = 24 ms |

再增加共享端口、带宽或容量冲突，验证重叠被限制或取消；增加 VPU 实例不应消除 V0/G2/V1 的真实依赖。

### 11.3 必须新增的边界测试

| 类别 | 验证内容 |
|---|---|
| 图 | fork/join、缺失依赖、环、共享 view、部分写入、最后消费者 |
| 引擎 | 不支持格式、输出类型、K 部分和、未知特殊函数、实例状态保持 |
| 流量 | 同流多接口不重复外部搬运、显式 staging、本地重读、scale 物理请求 |
| 拓扑 | 同一物理端口竞争、私有 SRAM 不池化、共享带宽不倍增 |
| 容量 | 恰好 fit、对齐后超限、多个任务单独 fit 但同时不 fit、待消费输出 |
| 调度 | 非抢占、稳定平局、共享资源排队、无前进条件、跨 core copy |
| 未知 | 未知阶段下游传播、潜在资源冲突、部分预算不生成完整吞吐 |
| 归因 | busy time 可大于 makespan、关键路径含资源等待、独占归因守恒 |
| 报告 | schema、来源指纹、debug 不改数值、未知显示、图与表一致 |

### 11.4 回归方式

实施后从仓库根执行：

```sh
PYTHONPATH=src python3 -m unittest discover -s src/hive/model_eval/tests -t src
```

CLI 矩阵至少覆盖：decode/prefill、已有格式、G0/G0_PRO/P310、attention 两变体、batch、单/多实例、已知/未知服务、stream/resident 与容量超限。

离线网页验证需启动本地服务并使用浏览器检查任务时间线、实例切换、未知时间、等待明细和流量表，同时检查控制台与加载错误。仅单测通过不能替代网页使用验证。

2026-11-09 验证记录：

- 完整 `model_eval` 回归 259 项通过；L0 锚、原 GEMM 结构账、视图/补齐/K 合并与资源预约均保留独立测试。
- 单/双 GEMM 合成图由真实调度器得到 32 ms / 24 ms，任务已知预算之和均为 34 ms；这不是硬件性能实测。
- CLI 覆盖完整 Qwen decode S=0/32、batch=2 的 prefill S=17 双 GEMM 场景、量化 attention 及 310 未知容量；格式/profile/attention/容量边界矩阵另由集成测试覆盖。
- Chrome 在 1280/768/390/320 px 视口及 file 离线方式验证串行、多实例、未知服务与容量阻断报告，检查工作点/层/算子/任务切换、实例筛选、投影开关、键盘、溢出、控制台和外部请求。
- JSON schema 2 已规范化嵌套数组；未绑定候选预算、实际未知与条件投影分别标记。固定计划阻断明细可展开，不遮蔽报告主体。

性能覆盖仍不完整，转换/补齐、特殊函数、混合精度累加、路由/同步和控制排空等未定价项不会因回归通过而变成已知；有效性标志保持 false。

## 12. 最终完成标准

1. 组合算子能展开为 3 GEMM、2 VPU、N CPU，接口不依赖模型名称。
2. 同几何与执行计划下，原窗口、拍数与流量锚保留。
3. 多实例、共享带宽、缓存不足、fork/join、跨 core copy 均有可手算测试。
4. 所有重叠都有依赖、资源、容量与能力依据，没有隐式免费搬运或转换。
5. 未知阶段不成为零，不生成伪确定的端到端吞吐。
6. 相同输入、拓扑与策略重复运行得到一致时间线、报告和指纹。
7. L0 不回退；跨层比较声明工作量、存储与资源口径是否一致。
8. 原始工作量、资源 busy time 和关键路径归因分别展示，不混算。
9. 始终保持 `performance_valid=false`、`data_valid=false` 和未校准声明；新增结构测试不替代校准。
10. 所有旧调用者与测试完成迁移，没有永久兼容后端、改名重导出或旧时间含义残留。
