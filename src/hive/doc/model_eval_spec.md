# model_eval 建模规范与接口参考

本文定义 `src/hive/model_eval` 当前工作树的建模范围、输入契约、计算规则、输出字段、扩展接口和验证要求。文档类型为 **Reference**，内容以代码行为为准；代码与本文不一致时，应核对实现、输入口径和回归测试后同步修正。

| 文档 | 类型 | 内容 |
|---|---|---|
| [入门教程](model_eval_tutorial.md) | Tutorial | 完成一次固定的 L0/L1 评估并读取报告 |
| [操作指南](model_eval_howto.md) | How-to | 工作点比较、多实例、驻留、自定义图、排错与回归 |
| [设计说明](model_eval_explanation.md) | Explanation | L0/L1 分工、任务图、资源预约与未知时间的设计原因 |
| [Qwen3-0.6B 端到端代码导读](model_eval_end2end.md) | Explanation | 从 CLI 到模型展开、引擎成本、调度和报告的逐阶段调用链 |
| 本文 | Reference | 数据契约、公式、接口、输出与有效性边界 |

## 目录

1. [范围与规范性约定](#1-范围与规范性约定)
2. [模块结构与调用关系](#2-模块结构与调用关系)
3. [输入规范](#3-输入规范)
4. [L0 评估规则](#4-l0-评估规则)
5. [L1 评估规则](#5-l1-评估规则)
6. [输出规范](#6-输出规范)
7. [扩展接口](#7-扩展接口)
8. [错误语义与验证要求](#8-错误语义与验证要求)
9. [附录 A：CLI 参数](#附录-a-cli-参数)
10. [附录 B：Python API](#附录-b-python-api)
11. [附录 C：JSON 与 HTML 数据](#附录-cjson-与-html-数据)
12. [附录 D：未覆盖项](#附录-d未覆盖项)

## 1. 范围与规范性约定

### 1.1 支持范围

| 项目 | L0 | L1 |
|---|---|---|
| 建模方式 | 全模型 MAC/字节闭式 roofline | LogicalOp→PrimitiveTask 图、独立引擎预算、物理资源与内存预约 |
| 输入 | ModelPlan、HwProfile、NumFormat、WorkloadPoint | 同左，另加 ModelParams、ArrayGeometry、Topology、ExecutionPlan、BufferPlan；也可直接输入 TaskGraph |
| 主要输出 | 理想带宽时间、计算时间、吞吐上界、瓶颈及分项 | 已知服务预算、actual/条件投影跨度、任务与资源等待、流量、内存峰值、未知约束 |
| 默认是否加载权重 | 否 | 否 |
| 默认是否执行真实前向 | 否；独立 `torch_check` 例外见 4.4 | 否 |
| 是否逐周期事件仿真 | 否 | 否 |
| 默认并行口径 | 可使用 profile 声明的全部核心峰值 | 单 active core、每类引擎一个实例、serial；多实例必须显式声明 |

当前完整模型前端只有一条路径：

- **ModelPlan 路径**：`frontend/layers/` 注册基础层与聚合层，`frontend/models/` 负责模型组合，`frontend/registry.py` 是中立模型注册表。Qwen3-0.6B 是内置 demo；`--extension` 可显式加载外部层/模型。L1 通过 `l1/compose.py` 的 emitter 递归展开，层几何显式声明，不从参数余额反推 `head_dim`。旧的 `ModelSpec`/`map_model()`/`lower_model()` 兼容路径已移除。

`frame` 是 L0 工作点接口；L1 的 plan 路径不提供视觉模型展开器。

本模块不包含 RTL 仿真、gem5 校准、checkpoint 加载、tokenizer、采样器实现、PyTorch 图捕获、自动编译、自动融合、自动分片或最优 tiling。读取硬件几何配置不表示启动 gem5，也不表示完成 RTL 校准。

### 1.2 结果有效性

当前报告固定保留：

```text
performance_valid = false
data_valid = false
coverage_complete = false        # L1
```

所有结果均为“未经 RTL 校准的工程估算”。单元测试通过、HTML 可打开或外部仿真通过均不改变这些标志。

L0 的 `t_s` 是纳入资源项的理想时间下界，`throughput` 是相应上界；`is_upper_bound=true` 描述吞吐，不表示时间上界。L1 分别报告已知服务预算、actual makespan 和条件投影，不将条件投影填回 actual，也不在覆盖不完整时发布完整模型吞吐。

L0 是独立分析工具，不是 L1 的前置阶段。L1 报告中的 L0 数值仅为同输入参考，不参与 L1 成本或调度推导；`cross_level_comparable=false` 时不得通过 `max(L1,L0)` 或逐项比较构造混合结论。

### 1.3 单位与术语

| 名称 | 单位或约定 |
|---|---|
| MAC | 乘加计数；模型来源中的部分归约/逐元素项沿用既有 MAC 等价口径，不据此推定硬件可按 FMA 峰值执行 |
| FLOP / TOPS | 引用峰值时必须声明换算；25 TOPS profile 对应 12.5 TMAC/s |
| bytes | 字节；GB 为十进制 10⁹ B，KiB 为 1024 B |
| seconds / cycles | 秒与周期分别记录；周期转秒要求已知频率 |
| 参数量 | 可学习张量元素数，尚未乘数值格式字节数 |
| semantic MAC | 算子语义所需工作量 |
| useful / issued / padded / masked MAC | 原始 GEMM 形状、实际发射、对齐补齐和语义 mask 对应的工作量 |
| logical bytes | 逻辑有效数据访问量，可包含重复访问 |
| physical bytes / beats | 按物理拍宽计的端口数据量，包括行内未占满部分 |
| unknown / excluded | 无数值覆盖；不表示实测为零 |
| actual | 所有必需服务已知且固定计划可完成时的排程结果；仍非 RTL 实测 |
| conditional projection | 忽略未知服务后，对已知预算进行依赖、资源和容量预约的条件结果 |

## 2. 模块结构与调用关系

### 2.1 文件职责

以下路径相对于 `src/hive/model_eval/`。

| 区域 | 文件 | 职责 |
|---|---|---|
| 模型前端 | `frontend/spec.py` | 数值格式 NumFormat 与 FORMATS |
| 模型前端 | `frontend/layers/base.py` | LayerDefinition、LayerInstance、LayerL0、LayerRegistry、WeightAllocation |
| 模型前端 | `frontend/layers/generic.py`、`qwen3.py` | 基础层与 Qwen 家族聚合层定义及 L0 公式 |
| 模型前端 | `frontend/plan.py` | ModelPlan、权重分配、层树遍历与参数/L0 汇总 |
| 模型前端 | `frontend/registry.py`、`models/`、`builtins.py` | 中立模型注册表、内置 demo、显式内置加载入口 |
| 公共 | `common/workload.py` | phase、seq_len、batch |
| 公共 | `common/opspec.py` | LogicalOp、PrimitiveTask、TaskGraph、tensor/base/view 和各引擎 typed spec |
| 公共 | `common/manifest.py` | 来源指纹、具名输入、有效性与未覆盖项 |
| 公共 | `common/report.py` | 终端表格和单位显示 |
| 硬件 | `hw/profiles.py` | 命名硬件速率、容量及出处 |
| 硬件 | `hw/geometry.py` | 硬件阵列几何加载与校验 |
| 硬件 | `hw/topology.py` | 物理引擎实例、共享资源和私有内存域 |
| L0 | `l0/accounts.py`、`roofline.py`、`bottleneck.py` | 工作量/流量、roofline、瓶颈归因 |
| L0 | `l0/excluded.py`、`torch_check.py` | 未覆盖说明与外部计数交叉检查 |
| L0 | `l0/run.py`、`web.py`、`resources.html` | CLI、JSON 和离线 HTML |
| L1 | `l1/timeline.py` | 引擎估算、调度编排和报告 |
| L1 | `l1/graph_builder.py`、`compose.py`、`emitters/` | 公共图构造器、emitter 注册表和 ModelPlan 递归展开 |
| L1 | `l1/split_policies.py`、`dispatchers.py`、`engines.py` | GEMM 命令拆分、调度策略和引擎 estimator 注册表 |
| L1 | `l1/costing/registry.py` | 算子形态 `(spec_kind, engine_kind) → cost_fn` 注册表与 `simple_cost()` |
| L1 | `l1/costing/services.py` | 公共 DRAM/local/boundary phase、traffic、buffer 构造 |
| L1 | `l1/costing/forms/` | 每个算子形态一个 cost function（vpu/sampling/transfer/gemm） |
| L1 | `l1/costing/gemm_commands.py` | GEMM 命令规划/窗口计数的共享入口，图构建与成本共用 |
| L1 | `l1/gemm_model.py` | GEMM 实现：格式、合法化、窗口、流量和阶段成本 |
| L1 | `l1/vpu_model.py`、`cpu_model.py`、`dma_model.py` | 兼容包装，公式已迁到 `l1/costing/forms/` |
| L1 | `l1/cost_view.py` | `OperatorCost` 只读投影、三个下界、coverage 与 unknown 聚合 |
| L1 | `l1/kernels.py` | 对 `engines.py` 的薄兼容入口，不持有成本算法 |
| L1 | `l1/residency.py` | ExecutionPlan 与 BufferPlan 生命周期 |
| L1 | `l1/scheduler.py` | serial/list 固定速率非抢占预约；actual 与条件投影分离 |
| L1 | `l1/ledger.py`、`params.py` | EngineCost、Phase、资源/流量/缓冲需求、原始账目和场景参数 |
| L1 | `l1/run.py`、`web.py`、`resources.html` | CLI、JSON 和离线 HTML |
| 测试 | `tests/test_*.py` | 数值锚点、几何、资源、映射和报告回归 |

### 2.2 L0 调用链

```text
l0.run.main(argv)
  ├─ 解析 model / profile / format / workload / geometry
  ├─ 构造独立 WorkloadPoint
  ├─ roofline.evaluate(...)
  │    ├─ accounts.work_account(model, point)
  │    ├─ accounts.traffic_account(model, fmt, point, use_doc_bytes=...)
  │    └─ profile.mac_per_second(fmt.mac_family)
  ├─ bottleneck.attribute(result, machine_balance)
  └─ manifest / 终端 / JSON / HTML
```

直接调用 `l0.roofline.evaluate()` 不需要阵列几何，也不会自动构建 manifest。L0 CLI 会加载几何和来源信息，因此不是可脱离仓库布局的单文件脚本。

### 2.3 L1 调用链

```text
l1.run.main(argv)
  ├─ [--extension MODULE ...] 显式 import 扩展注册
  ├─ frontend.registry.get_model(name, config) → ModelPlan
  ├─ profile / format / workload / ModelParams / geometry
  ├─ explicit_topology(...) / ExecutionPlan(...)
  └─ timeline.evaluate(...)
       ├─ compose.lower_plan(plan, ...) → TaskGraph
       │    └─ emitters.* 递归：聚合层连线，叶子层生成 op/task
       ├─ BufferPlan.from_graph(graph, execution_plan)
       ├─ estimate_graph(...) → task_id → instance_id → EngineCost
       │    └─ engines.estimate(task, engine_config, execution_plan)
       │         └─ costing.registry: (spec_kind, engine_kind) → cost_fn
       ├─ 为非 DMA 的跨引擎依赖添加 handoff Phase（注入 costs 时同样执行）
       ├─ dispatchers.dispatch(name, ...) → ScheduleResult
       │    ├─ 条件预算 projection
       │    └─ actual replay，未知占用与释放不视为零
       ├─ cost_view.build_cost_view(...) → OperatorCost / bounds / coverage
       └─ L1Result / schema_version=2 JSON / HTML（含 layer_tree）
```

全模型入口为 `timeline.evaluate()`，接受 `ModelPlan`，通过 `l1.compose.lower_plan()` 展开；传入 `lowering=` 回调时改用该回调生成的 TaskGraph。直接构造的 TaskGraph 使用 `timeline.evaluate_graph()`。

单任务成本由 `engines.estimate()`（`kernels.estimate()` 仍为兼容入口）返回。它先查 `costing.registry` 的 `(spec_kind, engine_kind)`，未命中再回退引擎级 estimator。`gemm_model.legalize()` 是默认拆分策略；`split_policies.split_commands()` 让 builder 与 estimator 共享同一策略选择。`gemm_model.count_windows()` 仅提供结构账，不构成完整执行计划或性能结果。

`cost_view.build_cost_view()` 只做只读投影：`seconds` 仍取 `EngineCost.complete ? known_service_s : None`，DRAM/local 字节仍来自 traffic，内存 footprint 来自 buffer 生命周期。它不参与调度，也不改变任何数值。

## 3. 输入规范

### 3.1 WorkloadPoint

定义：`common/workload.py`。

```text
WorkloadPoint(phase: Phase, seq_len: int = 0, batch: int = 1)
decode(seq_len: int, batch: int = 1) -> WorkloadPoint
prefill(seq_len: int, batch: int = 1) -> WorkloadPoint
frame(batch: int = 1) -> WorkloadPoint
```

| 字段或属性 | 含义 |
|---|---|
| `phase` | `Phase.DECODE`、`PREFILL` 或 `FRAME`；Python 调用传枚举 |
| `seq_len` | decode 的参考历史 KV 长度，或 prefill 的整段 prompt 长度 |
| `batch` | 独立序列或样本数 |
| `tokens_produced` | decode 为 batch；prefill 为 seq_len×batch；frame 为 0 |
| `throughput_unit` | 文本为 `tok/s`，frame 为 `fps` |
| `label` | 文本为 `phase@S=...`，frame 为 `frame×batch`；文本 label 不含 batch |

构造器检查 `seq_len>=0`、`batch>=1` 和 prefill 的 `seq_len>0`，不提供通用字符串枚举转换或完整整数类型检查。

每个 CLI `--seq` 值表示独立工作点，不表示连续生成过程。L1 decode 的 `seq_len` 同时用于历史 KV 读取和参考 QK/AV 长度，当前 token 的 KV 写入另计；真实推理中追加后的可见长度通常为 `S_past+1`，两种口径不得混用。

### 3.2 NumFormat

定义：`frontend/spec.py`。模型结构由 `frontend/plan.py` 的 `ModelPlan` 与 `frontend/layers` 的层注册表表达；本模块只保留 L0/L1 共用的数值格式。

| NumFormat 字段 | 类型 | 含义 |
|---|---|---|
| `name` | str | 格式名 |
| `elem_bytes` | Fraction | 单元素有效字节，包括半字节 |
| `scale_bytes_per_elem` | Fraction | 平均每元素 scale 字节 |
| `mac_family` | str | HwProfile 峰值查表键 |
| `doc_bytes_per_param` | float / None | 复现既有模型文档时使用的近似字节值 |

`bytes_per_param_exact = elem_bytes + scale_bytes_per_elem`。`bytes_per_param(use_doc_value=...)` 在选择文档口径且文档值存在时返回文档值，否则返回精确值；`bytes_per_param_source(...)` 记录实际来源。

| 格式 | 元素 B | scale B/元素 | 精确 B/参数 | `FORMATS` 与 CLI |
|---|---:|---:|---:|---|
| MXFP8 | 1 | 1/32 | 33/32 = 1.03125 | 已注册；L0 默认可用文档值 1.031 |
| MXFP4 | 1/2 | 1/32 | 17/32 = 0.53125 | 已注册；使用精确值 |
| INT8 | 1 | 0 | 1 | 已注册 |
| BF16 | 2 | 0 | 2 | 常量存在，但未加入 `FORMATS`，CLI 不接受 |

`BF16` 常量使用 `mac_family=int8_fp8_mxfp8_w4a8`，该映射不构成独立 BF16 硬件性能证据，也不表示 L1 GEMM/VPU/转换路径已完成注册与验证。

### 3.3 ModelPlan

定义：`frontend/plan.py`。ModelPlan 是有序层链，默认路径即 `layers` 顺序；第一版不实现通用分支图。层语义与 L0 闭式由 `frontend/layers` 的层注册表提供，模型文件只组合层实例与权重分配，不再自带聚合公式。

| 字段 | 含义 |
|---|---|
| `name` / `source` | 报告名与模型组合来源 |
| `layers` | 顶层 `LayerInstance`；每个实例含 id、type、version、geometry、weights、可选 op_name |
| `allocations` | 模型级 `WeightAllocation(id, shape, checkpoint_copies)`，用于 tied embedding 等共享 |
| `input_roles` / `output_roles` | 模型输入输出角色名 |

派生接口由叶子层闭式聚合而来，L0 `accounts` 可直接消费：

```text
params_unique / params_checkpoint
params_streamed_per_token / kv_bytes_per_token / state_bytes
mac_fixed() / mac_s_coef() / mac_s2_coef() / mac_output()
body_mac_fixed() / body_mac_s_coef() / body_mac_s2_coef()
final_norm_mac       # 顶层 last_position_only norm 的 mac_fixed 之和
lm_head_mac          # 所有叶子 mac_output 之和
```

`iter_layers()` 递归展开聚合层；`iter_leaves()` 只返回不拥有 children 的叶子，所有总量与 L0 汇总都走叶子，避免聚合层与子层重复计费。聚合层不拥有权重，权重在叶子层或 `allocations`。`final_norm_mac` 由 `RmsNormGeometry(last_position_only=True)` 标识，不是按名字猜测。

`ModelPlan.validate()` 检查几何类型、权重角色齐全、allocation 声明与形状、重复层 ID 和未引用 allocation；不证明模型数学等价。

叶子层的 `WeightSpec(name, shape)` 保存权重名称与存储形状。线性权重使用 `[N,K]`（`LinearGeometry(in_features, out_features)` 对应 `WeightSpec("weight", (out_features, in_features))`），逻辑 GEMM 为 `X[M,K] @ Wᵀ[K,N]`。

模型注册表位于 `frontend/registry.py`：`register_model(name, build, *, version=1, source)` 登记“模型名→ModelPlan builder”，`MODELS` 以 `(name, version)` 为键；`get_model(name, *, version=1, config=None)` 调用 builder 返回 ModelPlan，对未登记名字抛出 KeyError，`list_models()` 列出已登记键。Qwen builder `frontend/models/qwen3_06b.build(config=None)` 使用默认 CONFIG 或传入的完整 dict；非空 dict 不会与默认配置自动合并。

### 3.4 HwProfile

定义：`hw/profiles.py`。

| 字段 | 单位或类型 | 含义 |
|---|---|---|
| `name` | str | profile 名称 |
| `dram_peak_bytes_per_s` | float，B/s | DRAM 理论速率 |
| `dram_eta` | float | 有效速率系数 |
| `n_core` | int | L0 计算峰值使用的核心数 |
| `freq_hz` | int / None | 核心频率，未知为 None |
| `mac_per_cycle_per_core` | Mapping[str,int] | 按 MAC family 的单核每周期峰值 |
| `mac_per_s_declared` | float / None | 已声明整机峰值，存在时覆盖 family 查表 |
| `sram_bytes_per_core` | int / None | 单核私有 SRAM 容量 |
| `sram_peak_bytes_per_s_per_core` | float / None | SRAM 峰值元数据，不代替 local 接口速率 |
| `local_iface_bytes_per_cycle` | int / None | 单核 local 读写合计接口速率 |
| `citations` | tuple[Citation,...] | `Citation(doc,line,quantity)` 来源 |

```text
dram_effective_bytes_per_s = dram_peak_bytes_per_s × dram_eta
mac_per_second(family) = declared，或 family_peak × freq_hz × n_core
local_iface_bytes_per_s_per_core = local_iface_bytes_per_cycle × freq_hz
```

local 参数或频率未知时 local 速率为 None。未知 family 且无 declared 峰值时抛出 KeyError。构造器不全面检查所有速率是否为正，零、负数或缺失频率不构成有效硬件证据。

| CLI 名称 | DRAM B/s | 核心 / Hz | 单核 SRAM | local B/cycle |
|---|---:|---|---:|---:|
| G0 | 17.064×10⁹ | 2 / 500×10⁶ | 512 KiB | 32 |
| G0_PRO | 34.128×10⁹ | 4 / 500×10⁶ | 1 MiB | 32 |
| MODEL_ANALYSIS_310，别名 310 | 310×10⁹ | 1 / 未知 | 未知 | 未知 |

G0/G0_PRO 的 eta=1，单核 8-bit family 为 512 MAC/cycle、4-bit family 为 1024。310 声明整机 12.5×10¹² MAC/s，不区分格式。L1 默认拓扑不会按 `n_core` 自动启用多个核心。

### 3.5 ArrayGeometry

定义：`hw/geometry.py`。

| 字段组 | 字段 | 含义 |
|---|---|---|
| 来源 | `profile_name`、`spec_version`、`source_path` | 硬件配置身份，不是校准状态 |
| 阵列 | `array_n`、`array_m`、`total_n` | 方形阵列几何 |
| 数据位宽 | `a_ext_w`、`b_ext_w`、`c_eff_w`、`c_ext_w`、`c_dma_ext_w` | bit；内部累加宽度与外部 DMA 拍宽分开 |
| scale 位宽 | `scale_a_w`、`scale_b_w` | bit |
| 命令限制 | `mnk_width`、`supported_fmts` | M/N/K 位宽和允许格式 |
| buffer 深度 | `a_buffer_depth`、`b_buffer_depth`、`c_buffer_depth` | 深度计数 |

派生值：`tile_n=total_n`，`max_m=c_buffer_depth-tile_n`；A/B/C 外部 B/beat 为相应 DMA 位宽除以 8。

`load_array_geometry(root=None)` 从 `src/workerbee/gem5/configs/hardware.py` 的 DEFAULT_PROFILE 读取 parameters/derived，并校验：

- `total_n=array_n=array_m`；
- A/B buffer 深度大于 0，C buffer 至少为 2T；
- M/N/K 字段位宽大于 0；
- DMA 位宽为整字节且 C DMA 位宽大于 0；
- A/B 外部接口均为 32 B/beat。

`repo_root(start=None)` 向上查找包含 `build.sbt` 的目录，失败时抛出 RuntimeError；缺少硬件配置时抛出 FileNotFoundError。`gem5_release(root=None)` 仅返回记录的 release/commit，不运行 gem5。

### 3.6 ModelParams

定义：`l1/params.py`。

| 字段 | 默认值 | 含义 |
|---|---|---|
| `weight_policy` | stream | 权重策略元数据；实际执行计划由 ExecutionPlan 决定 |
| `overlap` / `overlap_evidence` | False / 空 | 重叠场景及非空证据 |
| `sync_us` | None | 非 DMA 跨引擎 handoff 服务；显式 0 表示零成本场景 |
| `exp_cycles_per_element` | None | exp 服务场景 |
| `reciprocal_cycles_per_vector` | None | reciprocal 服务场景；softmax 按向量、SiLU 按元素使用 |
| `rsqrt_cycles_per_vector` | None | rsqrt 服务场景 |
| `cpu_cycles_per_comparison` / `cpu_freq_hz` | None | CPU 比较成本与频率场景 |
| `vpu_fp32_lanes` / `vpu_fp16_lanes` | 8 / 16 | VPU 架构 lane 参数 |
| `vpu_conversion_elements_per_cycle` | None | conversion/pack/dequant 场景吞吐；None 保持 unknown |
| `vpu_fp32_accum_elements_per_cycle` | None | FP32 accumulation/reduction 场景吞吐；None 保持 unknown |
| `block_size` | 32 | MX block，支持 16 或 32 |

速率参数必须为有限非负数，CPU 频率必须为正，lane 数必须为正；conversion/accumulation 参数必须为正整数或省略。启用 overlap 必须提供非空 evidence。evidence 仅登记调用者声明，不被程序验证为 RTL 或测量证据。

## 4. L0 评估规则

### 4.1 工作量与流量账户

定义：

```text
F = model.body_mac_fixed()
A = model.body_mac_s_coef()
Q = model.body_mac_s2_coef()
N = model.final_norm_mac
O = model.lm_head_mac
P = model.params_streamed_per_token
U = model.params_unique
K = model.kv_bytes_per_token
R = model.state_bytes
b = fmt.bytes_per_param(所选口径)
S = point.seq_len
B = point.batch
```

| 工作点 | 总 MAC | 总流量 B |
|---|---|---|
| decode | B×(F+A×S+N+O) | P×b + K×S×B + R×B |
| prefill | B×((F+N)×S+Q×S²+O) | U×b + K×S×B + R×B |
| frame 占位口径 | B×(F+N+O) | P×b + R×B |

权重在一个 decode 步骤或整段 prefill 中只读一次，不乘 batch；KV 和循环状态乘 batch。decode 当前不计 KV 写入；prefill 计 KV 写入，但不按 attention 内每次 KV 访问计流量。循环状态只按容量一次计入，不描述详细读改写次数。

`Account(total, breakdown={})` 要求非空分项之和与 total 相符；`share(key)` 返回分项占比，total=0 时为 0。零分项从结果中移除。

`kv_weight_crossover(model, bytes_per_param)` 使用 `params_unique×b / kv_bytes_per_token`，KV=0 时返回 infinity。该接口没有 batch 参数，也未使用流过参数属性；唯一参数与流过参数不同的模型不能直接将其解释为 decode 流量翻转点。

Qwen prefill 的 L0 QK/AV 因果工作量采用 S²/2 近似，不包含 attention softmax 的二次服务项；L1 对语义有效元素使用 S(S+1)/2，包含对角线。两级工作量不因名称相同而逐项等价。

### 4.2 Roofline 公式

```text
t_bandwidth = traffic_bytes / effective_DRAM_Bps
t_compute   = MAC / profile_MACps
t_s         = max(t_bandwidth, t_compute)
AI          = MAC / traffic_bytes
throughput  = B/t_s                 # decode、frame
throughput  = B×S/t_s               # prefill
machine_balance = profile_MACps / effective_DRAM_Bps
```

两种资源总量必须先分别汇总，再取 max；不得以“各层 max 相加”替代全模型 roofline。

两资源时间相等时瓶颈归为 dram。`bottleneck_ratio` 为较大时间除以较小时间。`Attribution` 另含 machine_balance、dominant_traffic、dominant_work、对应 share 和 headroom_note。

### 4.3 L0Result

定义：`l0/roofline.py`。

| 字段组 | 字段 |
|---|---|
| 身份与输入 | model、profile、workload、fmt、mac_family、bytes_per_param |
| 资源分项 | work、traffic；mac 和 traffic_bytes 为派生属性 |
| 时间与强度 | arithmetic_intensity、t_bandwidth_s、t_compute_s、t_s |
| 瓶颈 | bottleneck、bottleneck_ratio |
| 吞吐 | throughput、throughput_unit、is_upper_bound=true |

L0 CLI JSON 为 `{"manifest": ..., "results": [...]}`。每条 result 包含 phase、seq_len、batch、MAC/流量分项、时间、吞吐、字节口径和 attribution，不直接序列化整个 dataclass。

### 4.4 PyTorch 计数交叉检查

`l0.torch_check.cross_check()` 在调用者提供 PyTorch module 和 example inputs 时执行一次真实前向并比较外部 FLOP 计数与模型 MAC 口径。该接口不测量目标硬件时间，不检查输出数值正确性，也不改变未校准标志。

## 5. L1 评估规则

### 5.1 LogicalOp、PrimitiveTask 与 TaskGraph

- `LogicalOp` 保存 layer/name/kind 和语义工作量，是报告与任务分组单位。
- `PrimitiveTask` 绑定 GEMM/VPU/CPU/DMA typed spec、依赖、输入输出 tensor 引用和可选实例白名单。
- 一个 LogicalOp 可包含多个不同引擎任务；算子分解属于 lowering，不属于 scheduler。
- `TaskGraph.validate()` 检查 ID、依赖无环、tensor shape/dtype、base/view/stride 覆盖、生产者祖先、无序重叠写和 transfer stream owner；不证明模型数学等价。

默认 plan 路径的 Qwen lowering 由 `qwen3.decoder_block` 聚合 emitter 递归驱动：block → norm/attention/residual/post-norm/MLP/residual；attention → q/k/v、QK norm、RoPE、KV append、QK、softmax、AV、o_proj；MLP → gate/up、SiLU gate、down。`head_dim` 由 `Qwen3BlockGeometry` 显式给出，不从参数余额反推。Q/K/V 共享量化 A，gate/up 共享另一份量化 A；GQA 使用共享 KV base 的逐 head views。decode `S=0` 不生成 QK/softmax/AV；S>0 每 block 18 个 LogicalOp，S=0 为 15 个。INT32 与 FP32 始终区分，类型转换和跨 K partial-sum 合并是独立任务。

报告分组为 28 个 block 加 final norm、lm_head、sampling，共 31 组；`layer_tree` 另给聚合层与叶子层两级视图。`layers` 数量不是 decoder 层数或原子任务数。

### 5.2 Tensor、view 与生产者约束

`Tensor` 描述 shape/dtype/domain/base_id/external/block_size/base_starts/stops/steps/scale_axis。view 只能指向 base，不能形成 view 链。`TensorRef` 表示 view 坐标内的半开切片。

共享 base 的不同 view 按实际元素区域判断覆盖，不仅按名称判断。每个被消费元素必须由祖先任务写入，独立 disjoint writer 可共存，无序 overlapping writer 被拒绝。stride 和 reshape 使用紧凑结构校验，不逐元素枚举。

`BufferPlan` 对被引用 base 建立唯一全局分配，view 不独立重复分配。跨域访问必须由显式 DMA 建立数据副本和依赖，私有 SRAM 不允许被其他 core 直接访问。

### 5.3 GEMM 结构与服务

`l1/gemm_model.py` 是 GEMM 格式、合法化、命令/窗口计数、流量和阶段成本的唯一实现，不保留旧拆分模块或兼容重导出。

命令按 M 段→N 段→K 段合法化：N 按 `tile_n` 补齐，K 按有效 K window、MX block 和合法混配粒度补齐，M 不补齐。窗口为 N tile×K pass：

```text
array_cycles = loadW_beats + consume_beats
```

C 每行独立按 C DMA 宽度计拍；独立 K 命令均输出 C。`array_utilization = consume/(loadW+consume)` 是结构比值，不是实测阵列效率。

MX A 的 scale 轴为 K 轴 1，B 为 K 轴 0。容量按对应轴每行 `ceil(K/block_size)` 计算，平均 scale 字节不能替代物理请求账。A/B storage shape 必须覆盖 padded N/K；默认 lowering 生成显式 VPU repack/zero-pad 任务，服务保持未知。手工图必须写满 backing。

K 分命令保留 backing stride 和全局 K offset；仅显式 A staging 使用 command-local 布局。跨 K partial-sum 通过独立 `k_accum` 任务合并，不在 GEMM 内隐式免费合并。

### 5.4 VPU、CPU 与 DMA 服务

| 类型 | 当前规则 |
|---|---|
| norm / softmax | 三遍 FP32 lane-rate 回退；rsqrt 或 exp/reciprocal 特殊服务另列，缺参为未知 |
| rope / residual / silu_gate | FP32 lane-rate 回退；SiLU 另有 exp/reciprocal |
| attention_vpu | FP16 FMA architecture peak；类型转换和 FP32 累加另列；有 conversion/accum 参数时按场景计费，缺省 unknown |
| k_accum | FP32 按 lane 回退；INT32 不静默转 FP32，服务未知 |
| convert | 量化、packing、dequantization、repad/layout 等显式服务；有 conversion 参数时按 elements/cycle 计费，否则 unknown |
| CPU sampling | 每 batch 向量独立按 `vectors × ceil(length × log2(max(2,length)))` 计比较；需要比较周期和 CPU 频率 |
| DMA | 按实际源/目的端口计费；私有↔私有不冒记 DRAM，DRAM↔DRAM 计一次读加一次写 |

VPU/CPU/DMA 公式位于 `l1/costing/forms/`；`vpu_model.py`、`cpu_model.py`、`dma_model.py` 只保留兼容包装。新增算子通过 `costing.registry.register_operator_estimator(spec_kind, engine_kind, fn)` 接入，公式内部可复用 `costing.services` 的 phase/traffic/buffer 构造；不需要修改四个引擎模型文件。

通用 handoff 仅添加到“依赖两端 engine kind 不同，且两端都不是 DMA”的目标任务。`sync_us=None` 表示未知，显式 0 表示零成本场景。任意一端为 DMA 时不添加该通用 handoff，以避免与显式搬运/可见性服务重复计费；这不表示同步免费。跨 core DMA 分别预约两端真实 local 端口，`cross_domain_route` 保持未知。

### 5.5 Topology、ExecutionPlan 与 BufferPlan

`Topology` 注册 EngineConfig、Resource 和 MemoryDomain：

- engine lease 和 local 接口为 slot 资源，DRAM 为共享 bandwidth 资源；
- 每核 SRAM 为私有 MemoryDomain，容量不跨核池化；
- DRAM 带宽不按 active core 数自动倍增；
- 多实例仅改变显式候选和物理资源，不自动拆分单个任务；
- 多实例或多核场景要求非空 source/evidence。

`ExecutionPlan(weight_policy, activation_policy, alignment, evidence)` 中 B 仅支持 `stream/resident`，A 仅支持 `stream/stage`。B resident 表示先装入一次再从 local 重用；A stage 表示每命令显式 staging。所需容量未知或超限时产生错误或阻断，不自动降级为 stream、缩小 tile 或 spill，也不自动推断跨任务 cache 命中。

`BufferEndpoint(task_id, phase_id=None)` 表示任务或阶段边界。`BufferAllocation` 描述 domain、size、acquire、releases、alignment、tensor/base、可选 offset/region 和来源。`BufferPlan.from_graph()` 为每个被引用 base 建立从首次写入到所有最后消费者的分配；graph-entry 输入采用显式入口生命周期，不虚构生产者依赖。

同域 engine buffer 与全局 base 去重，跨域 staging 为额外分配。固定 offset/region、对齐、生命周期峰值和私有域分别核验；未知释放保持开放。

### 5.6 调度、actual 与条件投影

L1 成本分为三层：

1. 算子语义：计算内容、形状和依赖；
2. 结构需求：命令、窗口、拍数、端口字节和阵列周期；
3. 数值服务：结构需求除以已知速率；速率未知时为 None，并声明未覆盖。

scheduler 对固定图和候选实例执行固定速率、非抢占预约。serial 按稳定拓扑序执行；list 按稳定拓扑序、可行结束/开始、任务 ID 和实例 ID 确定性选择。依赖、engine lease、共享 DRAM、local 端口、私有容量和 buffer 生命周期均参与约束。

| 字段 | 定义 |
|---|---|
| `known_service_budget_s` | 各任务 `EngineCost.known_service_s` 的合计；是局部已知预算之和，不是端到端时间 |
| `makespan_s` | 全部任务完成、所有必需服务已知且固定计划可完成时的 actual 排程跨度；未知或阻断时为 null |
| `projection_makespan_s` | 未知阶段仅保留已知部分时，仍执行依赖、实例、端口、带宽和容量预约的条件跨度 |
| `attribution_s` | actual 的确定性关键路径五类归因 |
| `projection_attribution_s` | 条件投影的关键路径归因 |
| `resource_busy_s` | 各物理资源预约区间并集；未知占用时为 null |
| `memory_peak_bytes` | 内存域生命周期峰值；未知释放、容量或分配时为 null |

完整排程的五类归因满足 `dram+gemm+vpu+cpu+handoff=makespan`，条件投影使用独立归因，两套结果不得混合。层和算子的 `span_s` 为最晚结束减最早开始，不能累加为全模型 makespan。

任务状态区分 `scheduled`、`projected`、`blocked`、`unassigned`。`instance_id=null` 表示没有实际绑定；`budget_instance_id` 仅标识保留候选预算的实例，`cost_basis=unbound_candidate_budget` 不表示已排程。`blocked` 描述当前固定图、拓扑和执行计划的阻断，不证明 workload 在任意计划下均不可部署。

### 5.7 算子级 cost 视图与下界

`cost_view.build_cost_view()` 从 `EngineCost` 和 `ScheduleResult` 只读派生 `OperatorCost`，不新增数值、不参与调度：

| 字段 | 来源 |
|---|---|
| `seconds` | `EngineCost.complete ? known_service_s : None` |
| `known_seconds` | `EngineCost.known_service_s` |
| `dram_read_bytes` / `dram_write_bytes` | traffic 中 `memory_domain=="dram"` 的逻辑字节 |
| `local_bytes` | traffic 中非 DRAM 域的逻辑字节 |
| `footprint_bytes` | buffers 在任务阶段区间内的峰值；时间未知时为 None |
| `lease` / `candidate_instances` | lease_resources / 候选实例集合 |
| `basis` | unknown / scenario / architecture_peak / fallback / structural |
| `unknown` | 与任务相关的未覆盖项 |

三个保守下界：

```text
engine_busy_lb[e] = Σ 绑定到 e 的 known_seconds（unknown 阶段记 0，仍为下界）
dram_lb           = Σ traffic(dram).logical_bytes / dram_effective_bytes_per_s
cp_lb             = max over DAG paths Σ known_seconds（unknown 阶段记 0）
lower_bound_s     = max(max_e engine_busy_lb[e], dram_lb, cp_lb)
```

- **跨引擎 busy 不能相加**；只对同一引擎求和，再在引擎之间取 max。
- 未实际绑定的任务按 `budget_instance_id` 归属；没有预算绑定时取候选成本中的首个实例。该归属只用于计数，不表示任务已排程。
- `lower_bound_s` 是下界，不是可达 makespan；JSON 标注 `achievability="lower_bound"`。
- unknown 不折 0；`coverage` 给出已知/未知任务数与未知比例，`top_unknowns` 按根因聚合。
- `projection_makespan_s` 仍是“未知服务按已知部分计算”的串行/条件投影，不因本视图改口径；人类可读报告称其为“串行已知和”。

## 6. 输出规范

### 6.1 EngineCost 与账目

`EngineCost` 包含 phases、lease_resources、traffic、buffers、metrics、ledger 和 excluded。

| 类型 | 主要字段与语义 |
|---|---|
| `Phase` | id、seconds、category、dependencies、claims、known_seconds、source、evidence；`budget_s` 为已知 seconds 或未知阶段的已知部分 |
| `ResourceClaim` | resource、amount；资源需求必须为正有限值 |
| `BufferRequest` | domain、size、alignment、acquire/release phase、tensor |
| `Traffic` | stream owner、tensor、interface/domain、方向、逻辑/物理量、segment、evidence |

原始 `fold(ledger)` 保留同源账目和 unknown，不执行调度，也不提供旧 `compose` 接口。窗口计数和原始资源需求不得直接相加为时间。

### 6.2 ScheduleResult 与 L1Result

`ScheduleResult` 输出 tasks、makespan/projection、两套 attribution、两套 busy、两套 memory peaks、blocked constraints、policy 和 `projection_kind=conditional_budget_nonactual`。`TaskTiming` 另含 status 和 budget_instance_id；`PhaseTiming` 与 `Wait` 分别记录两套时刻及阻塞任务/allocation。

`L1Result.selected_costs` 按预算候选保存成本；未绑定任务必须显式标注。L1 默认完整模型覆盖不完整时，`modeled_throughput` 为 null。

`L1Result.to_dict()` 默认（`detailed=True`）保留 tasks/operators/traffic/topology/graph/schedule 等完整字段，并新增 `operator_costs`、`bounds`、`coverage`、`top_unknowns`。`to_dict(detailed=False)` 只保留算子 cost 视图、下界、coverage、层级摘要和未覆盖项，供 CLI 默认输出；完整阶段明细走 `--detailed`。

L0 参考字段为 `l0_time_lower_bound_s`，使用精确平均参数字节。该字段不参与 L1 推导。L1 仅在完整 actual 可用时校验自身实际 DRAM 流量的共享带宽下界，不执行旧跨层断言。

### 6.3 流量、资源与内存

`Traffic.logical_bytes` 表示有效数据访问，`physical_bytes/beats` 表示物理端口宽度占用。DRAM 总量仅汇总 `memory_domain="dram"` 的流量，不将沿途 local、scale 和 DRAM 接口合并为同一 DRAM 数字。

同一 tensor 经过多个接口可保留同一 stream 身份；先 staging 再复用属于分开的服务。A/B 的 N/K 补齐具有显式 backing 和转换任务，不作为免费扩展。

资源 busy 是各资源自己的时间区间并集，不能跨资源相加得到 makespan。内存峰值基于分配生命周期，未知容量、分配或释放不补零。

### 6.4 Manifest 与来源

manifest 固定记录 profile、model、layer、trust level、未校准原因、命名输入、excluded、来源 SHA-256 和硬件引用。

L1 来源指纹包含 WorkerBee 规格、typed contract、builder、geometry/profiles/topology 和全部 L1 Python；请求 HTML 时加入模板。该指纹不是完整依赖闭包，也不是完整结果内容哈希。自定义模型需要另行保存 config、输入和补充来源。

JSON 序列化使用 `allow_nan=False`，未知值使用 null。两级 `render_html` 转义 `<`、`>`、`&`、U+2028 和 U+2029。HTML 内嵌同一次 Python 结果，可离线打开，不重新推导成本或发起网络计算。

## 7. 扩展接口

### 7.1 新增模型（复用已有层）

```python
from hive.model_eval.frontend.layers import LayerInstance, WeightAllocation, WeightSpec
from hive.model_eval.frontend.plan import ModelPlan
from hive.model_eval.frontend.registry import register_model

def build(config) -> ModelPlan:
    ...
    return ModelPlan(name="My-Model", layers=..., allocations=..., source=__file__)

register_model("My-Model", build, source=__file__)
```

模型模块只做层组合，不重写闭式公式。CLI 用 `--extension my_models.llama_like --model My-Model` 显式加载；系统不自动扫描目录。`frontend.builtins` 是内置层与 demo 的唯一加载入口，删除它后核心注册表仍可导入。

### 7.2 新增层

```python
from hive.model_eval.frontend.layers import (
    LayerDefinition, LayerGeometry, LayerL0, register_layer,
)

@dataclass(frozen=True)
class GeluGeometry(LayerGeometry):
    hidden: int
    def validate(self) -> None: ...

register_layer(LayerDefinition(
    name="acme.gelu_mlp", version=1, geometry_type=GeluGeometry,
    input_roles=("hidden",), output_roles=("hidden",), weight_roles=(),
    children=children, l0=l0, source=__file__,
))
```

约束：名称必须带命名空间；聚合层不拥有权重；聚合层 L0 必须等于子层 L0 之和；层定义不得出现模型名、层序号、batch 或 seq；版本用于不兼容变更。L0-only 层可以 `l0=None` 并写 `l0_excluded`，但 L1 会明确报 `MissingL1Coverage`，不会静默回退。

### 7.3 新增 emitter、拆分策略、派发器和引擎

| 扩展目标 | 接口 | 调用者责任 |
|---|---|---|
| 新叶子/聚合 lowering | `l1.emitters.register_emitter(name, version, fn)` | 生成 op/task、张量依赖、显式转换和未知项 |
| 新 GEMM 拆分 | `l1.split_policies.register_split_policy(name, fn)` | 返回 `Command` 序列；builder 与 estimator 共用 |
| 新调度策略 | `l1.dispatchers.register_dispatcher(name, fn)` | 只改变固定预算的放置，不改变成本 |
| 新算子 cost 公式 | `l1.costing.register_operator_estimator(spec_kind, engine_kind, fn)` + `simple_cost()` | 返回 EngineCost；显式给出 duration、DRAM/local 字节和 unknown |
| 新引擎类型 | `common.opspec.register_engine_kind(kind, spec_type)` + `l1.engines.register_estimator(kind, fn)` | 同时提供 Topology 实例、typed spec 和 estimator |
| 修改实例/存储域 | `explicit_topology()` 或构造 Topology | 物理证据、私有域、共享带宽和资源 ID 一致性 |
| 修改 resident/stage/对齐 | ExecutionPlan / BufferPlan | 容量、offset、region、生命周期和来源 |

算子形态是新增 cost 的第一入口：`engines.estimate()` 先查 `(spec_kind, engine_kind)`，未命中才回退引擎级 estimator。`costing/gemm_commands.py` 是 GEMM 命令规划的共享入口；`costing/services.py` 是 VPU/CPU/DMA 公共 phase/traffic/buffer 构造。GEMM/DMA 的规划逻辑保留为共享 helper，不复制进每个算子公式。

一个完整的最小自定义模型（新层、emitter、算子公式和 `--extension` 运行方式）见[操作指南](model_eval_howto.md)的“如何新增一个自定义模型”。

`GraphBuilder.gemm(..., split_policy=...)` 与 `gemm_model.estimate()` 都通过 `split_commands()` 取命令，避免两处 `legalize()` 漂移。`evaluate(..., dispatcher=... , costs=...)` 和 `evaluate_graph(..., dispatcher=..., costs=...)` 支持注入派发器与已估算成本；注入成本时 handoff 仍会应用一次。

### 7.4 自定义 lowering 与直接任务图

自定义 lowering 的调用契约为：

```text
lowering(model, fmt, point, attention=..., geometry=..., block_size=...) -> TaskGraph
```

该回调传给 `timeline.evaluate(..., lowering=...)`，`model` 为 ModelPlan；缺省时改用 `l1.compose.lower_plan()`。返回图执行结构校验，不自动证明数学等价。

直接任务图使用 `timeline.evaluate_graph()`，调用者负责全部 LogicalOp、PrimitiveTask、tensor、显式转换、padding backing、依赖、成本覆盖和未知项。GEMM 非对齐形状必须提供相应 `a_storage_shape` / `b_storage_shape` 及完整 backing 写入，不能只修改逻辑 tensor shape。

系统不会自动从 checkpoint/config/PyTorch 图捕获任务图，也不会自动搜索最优拓扑、融合、分片或 SRAM 计划。

## 8. 错误语义与验证要求

### 8.1 输入、格式与资源错误

| 类别 | 典型原因 | 语义 |
|---|---|---|
| 输入错误 | prefill S=0、batch<1、未知模型/profile、空 seq | 拒绝构造工作点或查询注册表 |
| 格式错误 | 未注册 NumFormat、geometry 不支持、非法 A/B family 或 C dtype | 不用 shape 推导服务，不静默转换 |
| 图结构错误 | 依赖环、缺失生产者、view 越界、无序重叠写、stream owner 不一致 | TaskGraph 校验失败 |
| 私有域错误 | 非 DMA 任务跨 core 访问私有 SRAM、DMA 端点声明不一致 | 要求显式 DMA 和依赖 |
| 容量错误 | resident/stage/overlap 超限、容量未知、offset/region 冲突 | 报错或阻断，不自动降级 |
| 调度阻断 | 无候选实例、固定布局冲突、未知分配或释放 | 状态与 blocked constraints 保留，未知不补零 |
| 输出路径错误 | L1 JSON 与 HTML 同路径 | 拒绝输出；L0 不提供同等同路径保护 |

L1 CLI 参数错误通常由 argparse 以退出码 2 报告；缺文件、断言和自定义回调异常不保证统一包装。

### 8.2 回归范围

标准库回归命令：

```bash
PYTHONPATH=src python3 -m unittest discover -s src/hive/model_eval/tests -t src
```

当前结构锚包括完整 decode S=0 的 `array_cycles=19788544`、权重 B logical bytes `595984384`、C rows `31000`、norm vectors `729` 和 M=1 结构利用率 `1/17`。这些数值仅适用于指定几何与输入，不得外推为通用常数。

调度验收使用完全已知的合成图：G0/G1/V0/G2/V1 为 10/8/4/6/2 ms，四个 CPU 各 1 ms，两 CPU 实例，无共享端口竞争，同步显式为零。单 GEMM 为 32 ms，双 GEMM 为 24 ms，任务已知预算之和为 34 ms；这些数值用于验证预约逻辑，不是硬件时延测量。

### 8.3 报告验证范围

网页修改的验证范围包括真实报告生成、工作点→层→算子→任务浏览、串行/并行、未知/阻断、实例筛选、投影开关、窄屏、键盘、控制台、外部请求和 file 离线打开。单元测试不替代浏览器交互验证。

新增模型或引擎服务时，验证交付物包括 typed contract、来源、未知传播、独立结构锚、端到端回归和同步更新的规范。任何场景参数均不改变未校准声明。

## 附录 A. CLI 参数

### A.1 环境

运行要求为 Python 3.10 或更新版本。核心、CLI、测试和离线网页生成仅依赖 Python 标准库。模块从仓库根目录以 `PYTHONPATH=src python3 -m ...` 方式启动，直接运行内部 `.py` 文件不满足相对导入要求。

### A.2 L0

```text
python3 -m hive.model_eval.l0.run
  --model Qwen3-0.6B
  [--extension MODULE ...]
  --profile G0 | G0_PRO | 310 | MODEL_ANALYSIS_310
  --workload decode | prefill | frame
  --seq 逗号分隔长度，默认 1
  --fmt MXFP8 | MXFP4 | INT8
  [--exact-bytes] [--json PATH] [--html PATH]
```

L0 CLI 无 batch/config/checkpoint/torch-check 参数。`--extension` 显式 import 扩展模块，可重复；不自动扫描目录。frame 忽略长度内容但仍解析 seq；空 seq 失败。`--exact-bytes` 禁用可用文档近似值，使用精确平均参数字节。

### A.3 L1

| 参数 | 默认值与选项 |
|---|---|
| `--model` | Qwen3-0.6B；来自中立 registry，可由扩展注册 |
| `--extension` | 空；可重复的扩展模块 import，不自动扫描 |
| `--profile` | G0；另有 G0_PRO、310、MODEL_ANALYSIS_310 |
| `--workload` | decode；可选 prefill |
| `--seq` | 4096；逗号分隔独立工作点，prefill 要求大于 0 |
| `--batch` | 1；正整数 |
| `--fmt` | MXFP8；可选 MXFP4、INT8，不含 BF16 |
| `--attention` | both；可选 vpu、quantized_gemm、both |
| `--weight-policy` | stream；可选 resident |
| `--activation-policy` | stream；可选 stage |
| `--block-size` | 32；可选 16、32 |
| `--sync-us` | None；显式 0 为零成本场景，仅非 DMA 跨引擎边 |
| `--exp-cycles-per-element` | None；softmax/SiLU exp 场景 |
| `--reciprocal-cycles-per-vector` | None；softmax 按向量、SiLU 按元素 |
| `--rsqrt-cycles-per-vector` | None；norm rsqrt 场景 |
| `--cpu-cycles-per-comparison` | None；CPU 比较场景 |
| `--cpu-freq-hz` | None；正 CPU 频率 |
| `--overlap` / `--overlap-evidence` | False；启用必须提供非空依据 |
| `--schedule` | serial；可选 list |
| `--gemm/vpu/cpu/dma-instances` | 各 1；非负整数，至少一种引擎存在 |
| `--active-cores` | 1；多核/多实例需要证据 |
| `--topology-evidence` | 单 core 各一实例自动说明；否则必填 |
| `--json` / `--html` | None；必须是不同路径 |
| `--debug-ledger` | False；JSON 加 raw ledger，HTML 始终含 ledger |
| `--layer-detail` | False；终端增加逐层任务预算表 |

VPU lane、自定义 lowering、BufferPlan 和异构资源仅通过 Python API 指定，CLI 不执行自动搜索。

## 附录 B. Python API

| API | 返回或职责 |
|---|---|
| `l0.roofline.evaluate(model,profile,fmt,point,use_doc_bytes=True)` | L0Result；model 为 ModelPlan |
| `frontend.builtins` | 显式注册内置层与 demo 模型 |
| `frontend.registry.get_model(name,version=1,config=None)` | ModelPlan |
| `frontend.registry.register_model(name,build,version=1,source=...)` | 注册新模型 |
| `frontend.layers.register_layer(definition)` | 注册新层定义 |
| `frontend.plan.ModelPlan.iter_layers()/iter_leaves()/validate()` | 层树遍历、校验与 L0 汇总 |
| `l1.compose.lower_plan(plan,fmt,point,attention="vpu",geometry=None,block_size=32)` | ModelPlan → TaskGraph |
| `l1.graph_builder.GraphBuilder(block_size,split_policy="default")` | 公共 op/task/tensor/view/DMA 构造器 |
| `l1.emitters.register_emitter(name,version,fn)` | 注册层 emitter |
| `l1.split_policies.register_split_policy(name,fn)` / `split_commands(...)` | GEMM 命令序列注册与查询 |
| `l1.gemm_model.legalize(shape,geometry)` | 默认命令合法化结果 |
| `l1.gemm_model.count_windows(shape,commands)` | 聚合窗口账，不物化全部窗口 |
| `l1.engines.register_estimator(kind,fn)` / `estimate(...)` | 引擎 estimator 注册与分发 |
| `l1.kernels.estimate(task,engine_config,execution_plan)` | 兼容入口，委托 `engines.estimate` |
| `l1.dispatchers.register_dispatcher(name,fn)` / `dispatch(...)` | 调度策略注册与分发 |
| `l1.timeline.estimate_graph(graph,topology,execution_plan)` | 每任务各候选成本 |
| `l1.timeline.evaluate_graph(...,scheduling_policy="list",costs=None,dispatcher=None)` | 直接图 ScheduleResult；始终应用一次 handoff |
| `l1.scheduler.schedule(graph,costs,topology,buffer_plan=None,policy="list")` | 固定图/计划的 ScheduleResult |
| `l1.timeline.evaluate(...,scheduling_policy="serial",lowering=None,costs=None,dispatcher=None)` | 完整模型 L1Result；model 为 ModelPlan |
| `l0.web` / `l1.web.report_payload` / `render_html` | 同一次结果的 JSON/HTML 导出 |

主要 typed spec：

| 类型 | 契约摘要 |
|---|---|
| `GemmSpec` | m/n/k、A/B 格式、C dtype、block_size、semantic_macs、weight、evidence、可选 A/B storage shape、`split_policy`、A/B/C 内存域；C 仅允许与输入族匹配的 INT32/FP32。非 `dram` 域跳过外部流量，但本地端口服务仍建模 |
| `VpuSpec` | kind、MAC、elements、vectors、length、读写流量、输入输出 dtype、evidence |
| `CpuSpec` | 当前 `kind="sampling"`、vectors、length 等；不推断任意 CPU 算法 |
| `TransferSpec` | stream_id、source_domain、destination_domain、byte_count、tensor、direction、evidence；direction 不覆盖实际端点收费 |
| `PrimitiveTask` | id、parent_op_id、engine_kind、spec、dependencies、inputs、outputs、allowed_instances；engine_kind 必须已在 `common.opspec.ENGINE_SPECS` 注册 |
| `LayerDefinition` | name/version/geometry_type/input/output/weight roles/children/l0/source |
| `ModelPlan` | layers/allocations/input/output roles/source；聚合层不拥有权重 |
| `Topology` | instances、resources、memory_domains、source、evidence |
| `BufferPlan` | allocations、execution、source、evidence |

## 附录 C. JSON 与 HTML 数据

### C.1 顶层结构

| 输出 | 顶层字段 |
|---|---|
| L0 JSON | `manifest`、`results` |
| L1 JSON | `schema_version=2`、`manifest`、`manifest_digest`、`results` |
| L1 HTML payload | `manifest`、`digest`、`hardware`、`cases` |

JSON `results[]` 与 HTML `cases[]` 对应同一组 Python 结果。

### C.2 L1 case 字段组

| 字段组 | 内容 |
|---|---|
| 输入身份 | model、profile、fmt、phase、seq_len、batch、attention、active_cores、scheduling_policy |
| 时间与有效性 | 三种时间、budget_basis、两套归因、throughput、validity flags |
| 参考与边界 | L0 参考、cross-level 原因、excluded_costs |
| 模型结构 | layers、layer_tree、operators、tasks、graph |
| 物理执行 | resources、memory、topology、buffer_plan、schedule |
| 流量 | traffic，区分 domain/interface、logical/physical bytes 和 beats |

### C.3 报告解释约束

- `layers` 仍按 LogicalOp 的 `op.layer` 分组（Qwen 为 28 个 block 加 final-norm/lm-head/sampling）；`layer_tree` 是 plan 路径新增的两级视图，列出聚合层（如 `block-00/attention`）和叶子层（如 `block-00/attention/q_proj`）的 operator/task 数与已知服务预算。两者不能相加成 makespan。
- `known_service_budget_s` 可在 tasks→operators→layers 对账，但不等于 makespan；`layer_tree` 的聚合行与叶子行存在包含关系，不应再对全树求和。
- actual 与 projection 使用各自时刻、归因、busy 和 memory peak，不交叉组合。
- `null` 保持 JSON null，不序列化为 0、NaN 或 Infinity。
- manifest digest 覆盖 manifest，不是完整结果内容哈希。
- HTML 的实例筛选和投影开关只改变展示，不改变 Python 计算结果。

## 附录 D. 未覆盖项

当前未覆盖或默认未知的范围包括：

- 控制设置、prologue/epilogue、drain、retry、FIFO、真实仲裁、DMA 首字节/响应、bank 冲突和物理 burst；
- 量化/反量化、布局转换、补齐服务、混合精度累加、整数 merge、特殊函数和真实 CPU sampler；
- 跨 core route/visibility、通用 handoff 默认服务、命令提交和软件 scheduler 完整成本；
- 任意模型/checkpoint 捕获、自动编译、自动分片/融合/最优 tiling 和 SRAM 重规划；
- 完整依赖闭包、结果内容哈希、RTL 校准和数值正确性。

显式场景参数只补充其声明的服务，不将其他未覆盖项自动转化为已知事实。
