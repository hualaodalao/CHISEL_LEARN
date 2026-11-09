# model_eval 建模规格、使用手册与开发手册

实现基线：`src/hive/model_eval` 当前工作树；L0 与模型计量沿用原规格，L1 接口按任务图重构后的实现更新。本文说明当前代码的接口和计算规则，不把计划中的能力写成已经实现的功能。代码与本文发生差异时，应先核对输入口径及测试，再更新规格。当前 L1 架构与批准的迁移方案见 [model_eval_l1_opt](model_eval_l1_opt.md)。

## 阅读入口

- 想运行分析：看第 5 章。
- 想分析自定义模型：看第 6 章。
- 想修改或扩展建模代码：看第 7 章。
- 想理解函数、参数和调用关系：看第 2～4 章及附录。
- Qwen3 的 hidden / head / MLP 尺寸关系见 [Qwen3-0.6B 结构说明](qwen3_06b_structure.md)；项目分工与实施计划见 [model_eval_plan](model_eval_plan.md)。

# 1. 范围与结果含义

## 1.1 两级模型

| 项目 | L0 | L1 |
|---|---|---|
| 建模方式 | 全模型 MAC / 字节的闭式 roofline | LogicalOp→PrimitiveTask 图、引擎阶段预算、物理资源/内存预约 |
| 输入 | ModelSpec、HwProfile、NumFormat、WorkloadPoint | 同左，另加 ModelParams、ArrayGeometry、Topology、ExecutionPlan、BufferPlan；也可直接输入 TaskGraph |
| 主要输出 | 理想带宽时间、计算时间、吞吐上界、瓶颈及分项 | 已知服务预算、实际/条件投影跨度、任务/阶段/等待、流量与内存峰值、未知约束 |
| 默认流程是否运行权重 | 否 | 否 |
| 默认流程是否运行真实前向推理 | 否；独立 torch_check 可执行调用者 module | 否 |
| 是否按事件逐周期仿真 | 否 | 否 |
| 并行口径 | 计算峰值可以包含 profile 的全部核心 | 默认单 active core、serial；list 可绑定显式多实例，但受依赖、共享资源和私有容量约束 |

L0 的时间 `t_s` 是纳入资源项的理想时间下界，吞吐是相应上界。代码中的 `is_upper_bound=True` 指吞吐上界，不能将它解读为时间上界；终端沿用的 `t(上界)` 标签也不改变公式含义。

L1 分别报告 `known_service_budget_s`（候选/绑定实例的已知服务预算之和）、`makespan_s`（所有任务的 actual 完成跨度）和 `projection_makespan_s`（条件预算投影，非 actual）。未知阶段的占用/释放保持未知，传播到依赖、资源和内存；不能把投影填回实际时间。当前全模型完整吞吐不发布，三个有效性/覆盖标志仍为 False；即使 profile 速率已知，排空、同步、转换等也不会自动补齐。

## 1.2 固定边界

本工程只管理 L0 / L1，不建立 L2 / L3 实现，不导入 gem5 的校准结果。读取硬件几何配置不等于启动 gem5，也不等于完成 RTL 校准。

当前报告采用 `performance_valid=False`、`data_valid=False`；L1 另有 `coverage_complete=False`。固定声明为“未经 RTL 校准的工程估算”。这些标志不能因为单元测试通过、网页可打开或某个外部仿真通过而提升。

当前支持的完整模型前端只有 Qwen3-0.6B：28 个包含 attention 和 SwiGLU MLP 的 decoder block。L0 的数据结构可描述其他模型的总量；L1 的完整展开器只接受 `qwen3_full_attention` 层型。

默认分析流程没有 checkpoint 加载、PyTorch 图捕获、tokenizer、真实采样器、编译器或自动算子融合。独立的 `l0.torch_check.cross_check()` 是例外：调用者提供 PyTorch module 与示例输入时，它实际执行一次前向并计数，但不测目标硬件时间，也不检查输出数据正确性，详见附录 D。

## 1.3 单位与统计对象

| 名称 | 单位 / 约定 |
|---|---|
| MAC | 乘加计数；模型来源还把部分归约/逐元素项按既有 MAC 等价口径记入，不能据此推定硬件能按 FMA 峰值执行 |
| FLOP / TOPS | 引用峰值时必须声明换算；这里的 25 TOPS profile 对应 12.5 TMAC/s |
| bytes | 字节，不是 bit；GB 为十进制 10⁹ B，KiB 为 1024 B |
| seconds / cycles | 分别为秒和周期；周期转秒需要已知频率 |
| 权重参数量 | 可学习张量元素数，尚未乘数值格式字节数 |
| semantic MAC | 算子语义所需工作量 |
| useful / issued / padded / masked MAC | 分别描述原始 GEMM 形状、实际发射、对齐补齐及语义 mask 多算的工作量 |
| logical bytes | 逻辑有效数据量，可能包含重复访问 |
| physical bytes | 按物理拍宽计的端口数据量，包括行内未占满部分 |
| unknown / excluded | 没有数值覆盖，不是实测为零 |

# 2. 模块与调用关系

## 2.1 文件职责

所有下列路径相对于 `src/hive/model_eval/`。

| 区域 | 文件 | 职责 |
|---|---|---|
| 模型前端 | [frontend/spec.py](../model_eval/frontend/spec.py) | 模型、层型、张量与数值格式契约 |
| 模型前端 | [frontend/qwen3_06b.py](../model_eval/frontend/qwen3_06b.py) | Qwen3 配置、公式、模型注册表 |
| 硬件 | [hw/profiles.py](../model_eval/hw/profiles.py) | 命名硬件速率、容量及出处 |
| 硬件 | [hw/geometry.py](../model_eval/hw/geometry.py) | 从硬件配置加载并校验阵列几何 |
| 公共 | [common/workload.py](../model_eval/common/workload.py) | phase、seq_len、batch |
| 公共 | [common/opspec.py](../model_eval/common/opspec.py) | LogicalOp、PrimitiveTask、TaskGraph、tensor/base/view 和各引擎 typed spec |
| 公共 | [common/manifest.py](../model_eval/common/manifest.py) | 来源指纹、具名输入、有效性与未覆盖项 |
| 公共 | [common/report.py](../model_eval/common/report.py) | 终端表格和单位显示 |
| L0 | [l0/accounts.py](../model_eval/l0/accounts.py)、[roofline.py](../model_eval/l0/roofline.py)、[bottleneck.py](../model_eval/l0/bottleneck.py) | 工作量/流量、roofline、瓶颈 |
| L0 | [l0/excluded.py](../model_eval/l0/excluded.py)、[torch_check.py](../model_eval/l0/torch_check.py) | 未覆盖说明与外部计数交叉检查 |
| L0 | [l0/run.py](../model_eval/l0/run.py)、[web.py](../model_eval/l0/web.py)、[resources.html](../model_eval/l0/resources.html) | CLI、JSON、离线网页 |
| 硬件 | [hw/topology.py](../model_eval/hw/topology.py) | 物理引擎实例、共享资源和私有内存域 |
| L1 | [l1/mapping.py](../model_eval/l1/mapping.py)、[timeline.py](../model_eval/l1/timeline.py) | 模型→逻辑算子→任务图、引擎估算/调度编排与报告 |
| L1 | [l1/gemm_model.py](../model_eval/l1/gemm_model.py) | 唯一 GEMM 实现文件：格式、合法化、命令/窗口计数、流量、阶段成本 |
| L1 | [l1/vpu_model.py](../model_eval/l1/vpu_model.py)、[cpu_model.py](../model_eval/l1/cpu_model.py)、[dma_model.py](../model_eval/l1/dma_model.py) | 独立 VPU / CPU / DMA 引擎成本 |
| L1 | [l1/kernels.py](../model_eval/l1/kernels.py) | 薄 estimate 分发，不持有成本算法 |
| L1 | [l1/dram.py](../model_eval/l1/dram.py)、[residency.py](../model_eval/l1/residency.py) | 服务换算、ExecutionPlan 与 BufferPlan 生命周期 |
| L1 | [l1/scheduler.py](../model_eval/l1/scheduler.py) | serial/list 固定速率非抢占预约；actual 与条件 projection 分离 |
| L1 | [l1/ledger.py](../model_eval/l1/ledger.py)、[params.py](../model_eval/l1/params.py) | EngineCost、Phase、资源/流量/缓冲需求、原始账目与参数 |
| L1 | [l1/run.py](../model_eval/l1/run.py)、[web.py](../model_eval/l1/web.py)、[resources.html](../model_eval/l1/resources.html) | CLI、JSON、离线网页 |
| 测试 | `tests/test_*.py` | 数值锚点、几何、资源、映射及报告回归 |

## 2.2 L0 调用链

```text
l0.run.main(argv)
  ├─ build_parser().parse_args(argv)
  ├─ frontend.qwen3_06b.get_model(name) → ModelSpec
  ├─ hw.profiles.get_profile(name) → HwProfile
  ├─ hw.geometry.load_array_geometry() → 格式提示 / manifest 具名输入
  ├─ common.workload.decode / prefill / frame → WorkloadPoint
  ├─ 对每个独立工作点调用 l0.roofline.evaluate(...)
  │    ├─ accounts.work_account(model, point)
  │    ├─ accounts.traffic_account(model, fmt, point, use_doc_bytes=...)
  │    └─ profile.mac_per_second(fmt.mac_family)，总量比速率取 max
  ├─ bottleneck.attribute(result, machine_balance)
  ├─ manifest.build(...) → 来源指纹与未校准声明
  └─ 终端 / JSON / web.report_payload(...) → web.render_html(...)
```

直接调用 `l0.evaluate()` 不需要阵列几何，也不会自动构建 manifest；CLI 会加载几何和 manifest 来源，因此不能只复制几个 L0 文件就期待 CLI 独立运行。

## 2.3 L1 调用链

```text
l1.run.main(argv)
  ├─ model / profile / format / workload / ModelParams / geometry
  ├─ explicit_topology(...) / ExecutionPlan(...)
  ├─ 对工作点及 attention 场景调用 timeline.evaluate(...)
  │    ├─ mapping.lower_model(..., lowering=...) → TaskGraph
  │    │    ├─ 默认 map_model(...) → tuple[LogicalOp,...]
  │    │    └─ 生产/消费依赖、tensor views、转换/补齐/K merge 原子任务
  │    ├─ BufferPlan.from_graph(graph, execution_plan)
  │    ├─ estimate_graph(...) → task_id → instance_id → EngineCost
  │    │    └─ kernels.estimate(task, engine_config, execution_plan)
  │    │         └─ gemm_model / vpu_model / cpu_model / dma_model.estimate(...)
  │    ├─ 非 DMA 的跨引擎依赖边添加显式 handoff Phase
  │    ├─ scheduler.schedule(...) → ScheduleResult
  │    │    ├─ 条件预算 projection：固定计划、实例候选、资源和内存预约
  │    │    └─ actual replay：未知占用/释放不视为零
  │    └─ L1Result；evaluate_l0(..., use_doc_bytes=False) 仅作原口径参考
  └─ manifest / schema_version=2 JSON / HTML
```

全模型入口为 `timeline.evaluate()`；自定义分解通过 `lowering=` 注入，或直接构造 `TaskGraph` 调用 `timeline.evaluate_graph()`（返回 ScheduleResult，默认 list）。单任务成本用 `kernels.estimate()` 或对应引擎的 `estimate()`；纯命令/窗口仍可调用 `gemm_model.legalize()` / `count_windows()`，但不代表执行计划或完整性能。旧 OpSpec、cost/compose 入口及旧拆分模块均不保留兼容别名。

# 3. 输入数据规格

## 3.1 WorkloadPoint

定义见 [common/workload.py:16](../model_eval/common/workload.py#L16)。

```text
WorkloadPoint(phase: Phase, seq_len: int = 0, batch: int = 1)
decode(seq_len: int, batch: int = 1) -> WorkloadPoint
prefill(seq_len: int, batch: int = 1) -> WorkloadPoint
frame(batch: int = 1) -> WorkloadPoint
```

| 字段 / 属性 | 含义 |
|---|---|
| `phase` | `Phase.DECODE` / `PREFILL` / `FRAME`；Python 调用应传枚举，不传字符串 |
| `seq_len` | decode 的参考上下文长度，或 prefill 的整段 prompt 长度 |
| `batch` | 独立序列/样本数 |
| `tokens_produced` | decode 为 batch；prefill 为 seq_len×batch，表示被处理的 prompt token；frame 为 0 |
| `throughput_unit` | 文本为 `tok/s`，frame 为 `fps` |
| `label` | 文本为 `phase@S=...`，frame 为 `frame×batch`；文本 label 不包含 batch |

构造时检查 seq_len≥0、batch≥1、prefill 的 seq_len>0；没有全面检查整数类型或字符串枚举转换。调用者应提供符合注解的值。

每个 `--seq` 值是独立分析工作点，不是自动连续生成到那个长度。L1 decode 的 `seq_len` 同时用于历史 KV 读取和参考 QK/AV 长度，当前 token 的 KV 写入另计；真实推理中追加后的可见长度通常是 `S_past+1`，二者不可直接混同。

## 3.2 NumFormat 与 TensorSpec

定义见 [frontend/spec.py:15](../model_eval/frontend/spec.py#L15) 和 [frontend/spec.py:79](../model_eval/frontend/spec.py#L79)。

| NumFormat 字段 | 类型 | 含义 |
|---|---|---|
| `name` | str | 格式名 |
| `elem_bytes` | Fraction | 单元素有效字节，包括半字节 |
| `scale_bytes_per_elem` | Fraction | 平均每元素 scale 字节 |
| `mac_family` | str | HwProfile 计算峰值查表键 |
| `doc_bytes_per_param` | float / None，默认 None | 复现原模型文档时用的近似字节值 |

`bytes_per_param_exact` 是 elem+scale。`bytes_per_param(use_doc_value=...)` 在选择文档值且该值存在时使用文档值，否则使用精确值；`bytes_per_param_source(...)` 给出实际来源字符串。

| 当前 CLI 格式 | 元素 B | scale B/元素 | 精确 B/参数 | L0 默认口径 |
|---|---:|---:|---:|---|
| MXFP8 | 1 | 1/32 | 33/32 = 1.03125 | 文档 1.031 |
| MXFP4 | 1/2 | 1/32 | 17/32 = 0.53125 | 精确值 |
| INT8 | 1 | 0 | 1 | 文档值与精确值相同 |

`BF16` 对象在 spec 模块中有定义，但不在 `FORMATS` 中，CLI 不接受 `--fmt BF16`。其峰值族映射也不能替代独立的 BF16 硬件性能证据。

`TensorSpec(name, shape)` 保存权重名字及存储形状，`elements` 为维度连乘。线性权重使用 `[N,K]`，逻辑 GEMM 为 `X[M,K] @ Wᵀ[K,N]`。构造器没有维度合法性校验。

## 3.3 LayerTypeSpec

定义见 [frontend/spec.py:92](../model_eval/frontend/spec.py#L92)。除 tensors 默认空元组外，字段均需提供。

| 字段 | 类型 / 单位 | 含义，均按一个实例 |
|---|---|---|
| `name` | str | 层型标识；L1 当前只接受 qwen3_full_attention |
| `count` | int | 此类型的实例数；同类型形状相同，不表示权重值共享 |
| `params_unique` | int，元素 | 每实例唯一参数量 |
| `params_streamed_per_token` | int，元素 | decode 每步流过的参数量；可与唯一参数量不同 |
| `mac_fixed` | int，MAC/token | 与上下文无关的工作量 |
| `mac_s_coef` | int，MAC/参考位置/token | decode 的 S 线性系数 |
| `mac_s2_coef` | int，MAC | prefill 整段的 S² 系数，含来源中已定义的因果折半 |
| `kv_bytes_per_token` | int，B/token | 该实例每缓存一个 token 的 K+V 大小 |
| `state_bytes` | int，B | 固定循环状态；与随上下文增长的 KV 分开 |
| `tensors` | tuple[TensorSpec,...]，默认 () | L1 需要的投影权重形状 |

字段构造时不自动验证总参数、head 几何或 count。L0 使用总量字段，不从 tensors 重新推导总量。

`layer_types` 按类型和 count 聚合，不能表达任意逐层交错顺序。L0 的总量分析通常不需要顺序；新架构的 L1 展开必须额外解决顺序描述，不能只将混合类型各自的 count 相加。

## 3.4 ModelSpec

定义见 [frontend/spec.py:114](../model_eval/frontend/spec.py#L114)。字段均需提供。

| 字段 | 类型 | 含义 |
|---|---|---|
| `name` | str | 报告显示的模型名 |
| `layer_types` | tuple[LayerTypeSpec,...] | 层型及数量 |
| `final_norm_mac` | int | 末层 norm 每 token 工作量 |
| `lm_head_params` / `lm_head_mac` | int / int | 输出头参数量及每输出位置工作量 |
| `embedding_params` | int | embedding 参数量元数据，当前不会生成独立 embedding 成本 |
| `params_unique` / `params_checkpoint` | int / int | 逻辑唯一参数 / checkpoint 登记元素总数 |
| `vocab_size` / `hidden_size` | int / int | 词表大小 / 残差流宽度 |
| `source` | str | 模型公式来源标识；不是自动加载路径 |

派生接口：

```text
layer_count = Σ(count)
params_streamed_per_token = Σ(count × 层型流过参数) + lm_head_params + hidden_size
kv_bytes_per_token = Σ(count × 层型 KV bytes/token)
state_bytes = Σ(count × 层型 state_bytes)
body_mac_fixed() / body_mac_s_coef() / body_mac_s2_coef() = 对应系数按 count 求和
```

注意流过参数的属性固定加 `hidden_size` 作为 final norm 权重数，即使 `final_norm_mac=0` 也不会自动去掉；其他模型不应忽略这一约定。容量与流量也不会根据 embedding 是否共享自动去重，builder 必须给出正确数值。

Qwen builder `build(config=None)` 使用默认 CONFIG 或传入的完整 dict；传非空 dict 时不会与默认配置自动合并。注册表 `MODELS` 是“模型名→零参数 builder”，`get_model(name)` 调用它；未知名字抛 KeyError。

## 3.5 HwProfile

定义见 [hw/profiles.py:32](../model_eval/hw/profiles.py#L32)，字段均需提供。

| 字段 | 类型 / 单位 | 含义 |
|---|---|---|
| `name` | str | profile 名称 |
| `dram_peak_bytes_per_s` | float，B/s | DRAM 理论速率 |
| `dram_eta` | float | 有效速率系数 |
| `n_core` | int | L0 计算峰值使用的核心数 |
| `freq_hz` | int / None，Hz | 核心频率，未知保持 None |
| `mac_per_cycle_per_core` | Mapping[str,int] | 按 MAC family 的单核每周期峰值 |
| `mac_per_s_declared` | float / None，MAC/s | 已声明的整机峰值，存在时覆盖 family 查表 |
| `sram_bytes_per_core` | int / None，B | 驻留容量上限 |
| `sram_peak_bytes_per_s_per_core` | float / None，B/s | SRAM 峰值元数据；当前 L1 不以它代替 local 接口速率 |
| `local_iface_bytes_per_cycle` | int / None，B/cycle | 单核 local 读写合计接口速率 |
| `citations` | tuple[Citation,...] | `Citation(doc, line, quantity)` 来源 |

```text
dram_effective_bytes_per_s = dram_peak_bytes_per_s × dram_eta
mac_per_second(family) = declared，或 family_peak × freq_hz × n_core
local_iface_bytes_per_s_per_core = local_iface_bytes_per_cycle × freq_hz
```

local 参数或频率未知时，local 速率为 None；未知 family 且没有 declared 时抛 KeyError。HwProfile 构造器不全面检查速率是否正数，不能用零、负数或缺失频率强行代表一个有效 profile。

| CLI 名称 | DRAM B/s | 核心 / Hz | 单核 SRAM | local B/cycle |
|---|---:|---|---:|---:|
| G0 | 17.064×10⁹ | 2 / 500×10⁶ | 512 KiB | 32 |
| G0_PRO | 34.128×10⁹ | 4 / 500×10⁶ | 1 MiB | 32 |
| MODEL_ANALYSIS_310，别名 310 | 310×10⁹ | 1 / 未知 | 未知 | 未知 |

前两项 eta=1，单核 8-bit family 为 512 MAC/cycle、4-bit family 为 1024。310 声明整机 12.5×10¹² MAC/s，不区分格式。310 可完整计算 L0，但 L1 的阵列与 local 时间缺失，不能输出完整吞吐。

## 3.6 ArrayGeometry

定义见 [hw/geometry.py:36](../model_eval/hw/geometry.py#L36)。

| 字段组 | 字段 | 单位 / 含义 |
|---|---|---|
| 来源 | `profile_name`, `spec_version`, `source_path` | 硬件配置身份；不是校准状态 |
| 阵列 | `array_n`, `array_m`, `total_n` | 方形几何，本次加载三者均为 16 |
| 数据位宽 | `a_ext_w`, `b_ext_w`, `c_eff_w`, `c_ext_w`, `c_dma_ext_w` | bit；内部累加宽度与外部 DMA 拍宽必须区分 |
| scale 位宽 | `scale_a_w`, `scale_b_w` | bit |
| 命令限制 | `mnk_width`, `supported_fmts` | M/N/K 位宽及允许格式 |
| buffer 深度 | `a_buffer_depth`, `b_buffer_depth`, `c_buffer_depth` | 深度计数 |

派生值：`tile_n=total_n`，`max_m=c_buffer_depth-tile_n`；A、B、C 外部 B/beat 分别为相应 DMA 位宽÷8。

当前加载结果为 `workerbee_default_v4@4.1.0`，T=16、A/B/C DMA=32 B/beat、C buffer=64、max_m=48、mnk_width=16。每次运行仍应读取配置，不把本文的快照硬编码进评估模块。

`load_array_geometry(root=None)` 从 [硬件配置](../../workerbee/gem5/configs/hardware.py) 的 DEFAULT_PROFILE 读 parameters / derived，然后检查：

- total_n=array_n=array_m；不是旧的两个子阵列维度相乘关系。
- A/B buffer 深度>0，C buffer≥2T。
- M/N/K 字段位宽>0；DMA 位宽为整字节，C DMA 位宽>0。
- A/B 外部每拍必须是 32 B；当前同源耦合的前提不支持任意替换成其他拍宽。

`repo_root(start=None)` 向上查找含 build.sbt 的目录，找不到抛 RuntimeError。缺硬件配置抛 FileNotFoundError。`gem5_release(root=None)` 返回记录的 release / commit，不运行 gem5。

# 4. 计算与输出规格

## 4.1 L0 公式

令：

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
S = point.seq_len，B = point.batch
```

| 工作点 | 总 MAC | 总流量 B |
|---|---|---|
| decode | B×(F+A×S+N+O) | P×b + K×S×B + R×B |
| prefill | B×((F+N)×S+Q×S²+O) | U×b + K×S×B + R×B |
| frame 占位口径 | B×(F+N+O) | P×b + R×B |

权重默认在一步/整段中只读一次，不乘 batch；KV 和循环状态乘 batch。decode 当前不计 KV 写入；prefill 计 KV 写入但不按 attention 中每次 KV 访问计流量。循环状态只按容量一次计入，未描述详细读改写次数。

```text
t_bandwidth = traffic_bytes / effective_DRAM_Bps
t_compute   = MAC / profile_MACps
t_s         = max(t_bandwidth, t_compute)
AI          = MAC / traffic_bytes
throughput  = B/t_s（decode、frame），或 B×S/t_s（prefill）
machine_balance = profile_MACps / effective_DRAM_Bps
```

必须先求全模型两种资源总量，再取 max。不能用“各层 max 相加”替代 L0 整体 roofline。

`Account(total, breakdown={})` 检查非空分项之和与 total 相符；`share(key)` 返回该项占比，total=0 时为 0。零分项从工作/流量结果中移除。

`kv_weight_crossover(model, bytes_per_param)` 当前使用 `params_unique×b / kv_bytes_per_token`，KV=0 返回 infinity。它没有 batch 参数，也没有使用流过参数属性；MoE 等唯一参数与流过参数不同的模型不能直接把它当成 decode 流量翻转点。

Qwen prefill 的 L0 QK/AV 因果工作量沿用 S²/2 近似，不包含 attention softmax 的二次服务项；L1 对语义有效元素用 S(S+1)/2，包含对角线。两级不能只看名字就假设工作量逐项完全相同。

## 4.2 L0Result

返回类见 [l0/roofline.py:18](../model_eval/l0/roofline.py#L18)。

| 字段组 | 字段 |
|---|---|
| 身份与输入 | model、profile、workload、fmt、mac_family、bytes_per_param |
| 资源分项 | work: Account、traffic: Account；mac / traffic_bytes 为属性 |
| 时间与强度 | arithmetic_intensity、t_bandwidth_s、t_compute_s、t_s |
| 瓶颈 | bottleneck（dram / compute）、bottleneck_ratio |
| 吞吐 | throughput、throughput_unit、is_upper_bound（固定 True） |

两资源时间相等时归为 dram。瓶颈比为较大时间÷较小时间。`Attribution` 另含 machine_balance、dominant_traffic / dominant_work 及 share、headroom_note。

L0 CLI JSON 为 `{"manifest": ..., "results": [...]}`。每条 result 包含 phase、seq_len、batch、mac_breakdown、traffic_breakdown、时间、吞吐、字节口径及 attribution；不直接序列化整个 dataclass。

## 4.3 L1 的三步区分

L1 成本建模必须区分：

1. 算子语义：算什么、形状是什么、依赖谁。
2. 结构需求：命令/窗口/拍数、端口字节数、阵列周期。
3. 数值服务：需求除以速率；速率未知则为 None，并声明未覆盖。

因此窗口计数不是完整的周期仿真，原始资源需求也不能直接相加作为时间。详细 L1 参数、资源账本和合成规则见附录 B。

# 5. 使用手册

## 5.1 环境与启动

需要 Python 3.10 或更新版本；本次验证使用 Python 3.14.6。核心、CLI、测试和离线网页生成只依赖 Python 标准库，不要求 pip、PyTorch、sbt、Verilator 或启动 gem5。Python 版本最低要求来自当前类型注解语法，不是已测试的完整版本矩阵。

以下命令在仓库根执行。输出目录由 CLI 自动创建；生成文件存在时会覆盖，重要报告应使用不同文件名。

```bash
export PYTHONPATH="$PWD/src${PYTHONPATH:+:$PYTHONPATH}"
python3 -m hive.model_eval.l0.run --help
python3 -m hive.model_eval.l1.run --help
```

不要直接运行内部 py 文件，其相对导入需要包上下文。

### L0 decode、prefill 与离线网页

```bash
python3 -m hive.model_eval.l0.run --profile G0 --seq 1,4096,32768 \
  --exact-bytes \
  --json sim/model_eval/spec-20261105-l0-decode.json \
  --html sim/model_eval/spec-20261105-l0-decode.html

python3 -m hive.model_eval.l0.run --profile G0 --workload prefill --seq 1024,4096 \
  --json sim/model_eval/spec-20261105-l0-prefill.json \
  --html sim/model_eval/spec-20261105-l0-prefill.html
```

### L1 decode、prefill 与原始账本

```bash
python3 -m hive.model_eval.l1.run --profile G0 --seq 0,4096 \
  --attention both --debug-ledger --layer-detail \
  --json sim/model_eval/spec-20261105-l1-decode.json \
  --html sim/model_eval/spec-20261105-l1-decode.html

python3 -m hive.model_eval.l1.run --profile G0 --workload prefill --seq 16,1024 \
  --json sim/model_eval/spec-20261105-l1-prefill.json \
  --html sim/model_eval/spec-20261105-l1-prefill.html

python3 -m hive.model_eval.l1.run --profile 310 --seq 4096 \
  --json sim/model_eval/spec-20261105-l1-310.json \
  --html sim/model_eval/spec-20261105-l1-310.html
```

`--attention both` 是分别分析 VPU 与量化 GEMM 两种场景，不是将它们并行执行或叠加时间。310 的报告仍保留未知周期/local 服务，吞吐为 null。

### 显式场景输入

以下常数全部是假设，用于检查参数接入，不是目标硬件的实测速率：

```bash
python3 -m hive.model_eval.l1.run --profile G0 --seq 0 \
  --sync-us 2 --exp-cycles-per-element 1 \
  --reciprocal-cycles-per-vector 1 --rsqrt-cycles-per-vector 1 \
  --cpu-cycles-per-comparison 1 --cpu-freq-hz 500000000 \
  --overlap --overlap-evidence 'spec guide hypothetical overlap, not measured' \
  --json sim/model_eval/spec-20261105-l1-scenario.json \
  --html sim/model_eval/spec-20261105-l1-scenario.html
```

非空 evidence 只是登记调用者的说明，程序不会验证这段话是否来自 RTL 或测量。不能靠填一句话把场景变成已校准事实。

## 5.2 看报告的顺序

1. 先核对模型、profile、格式、phase、seq_len、batch 与 manifest。
2. 看未校准声明和 excluded_costs，再看时间与吞吐。
3. L0 看总量、AI、瓶颈及分项；不要把网页层局部下界相加当整体时间。
4. L1 先区分 actual makespan、已知预算与条件投影，再看任务实例/等待/阻塞、资源 busy time 和内存峰值；展开算子检查命令、窗口、流量、阶段和 raw ledger。
5. 比较结果前确认字节口径、attention 路径、block_size、ExecutionPlan、Topology、调度策略和 overlap 假设相同。

HTML 内嵌数据，不依赖外部服务器或 CDN，可直接用浏览器打开。L1 网页始终带原始 ledger，`--debug-ledger` 只决定 JSON 是否保留算子聚合 ledger；任务 phases/traffic 不因此消失，也不改变建模数值。

L1 的 31 个报告分组是 28 个 block 加 final norm、lm_head、sampling，不是 31 个 decoder block。一个逻辑算子可包含多个引擎任务；handoff 是依赖边对应的任务前缀阶段，计入任务预算及 span，不再是层外的相邻引擎切换游标。层 span、关键路径归因和资源服务需求不能相互求和替代。

## 5.3 常见问题

| 现象 | 原因 / 处理 |
|---|---|
| ModuleNotFoundError: hive | 确认 PYTHONPATH 包含仓库 src，使用 python -m |
| 找不到 build.sbt / 硬件配置 | 必须在完整仓库布局中使用默认 geometry / manifest；不要移动孤立文件 |
| prefill seq=0 报错 | prefill 必须有正长度，改为非零值 |
| 模型名/profile 名 KeyError | 当前注册表中没有该名称；按第 6 章接入 |
| unknown format / unsupported pair | CLI 格式集合与底层 legalize 能力不是一回事，检查 FORMATS、supported_fmts 和 A/B 配对 |
| --overlap 报错 | 需要显式非空 --overlap-evidence 和足够缓冲容量；仍不表示校准或端口独立 |
| resident / stage 容量失败 | 显式策略不自动降为 stream；检查 EngineCost.buffers、placement 和私有域容量 |
| 310 投影也为 null | freq/local 服务未知，且必需 SRAM 容量未知可阻断固定计划的预算投影 |
| sampling 有已知 CPU 预算但算法未覆盖 | 可能只是 local/DRAM 读取；比较阶段需 CPU 频率与每比较周期 |
| sync_us=0 | 显式零成本场景；省略（None）才是未知 handoff |
| L1 与 L0 时间不同 | cross_level_comparable 保守为 False，L0 只作原口径参考；没有严格 L1≥L0 断言或 max(L1,L0) 修补 |

# 6. 自定义模型分析

## 6.1 同架构、改尺寸：不修改仓库即可运行

适用于仍是 Qwen3 full-attention + Q/K norm + SwiGLU 的同构结构。下面不是另一个真实发布模型，仅用于展示接入方法。

```python
from dataclasses import replace
from hive.model_eval.common.workload import decode, prefill
from hive.model_eval.frontend.qwen3_06b import CONFIG, build
from hive.model_eval.frontend.spec import FORMATS
from hive.model_eval.hw.profiles import get_profile
from hive.model_eval.l0.roofline import evaluate as evaluate_l0
from hive.model_eval.l1.timeline import evaluate as evaluate_l1

config = {
    **CONFIG,
    "hidden_size": 256,
    "intermediate_size": 768,
    "vocab_size": 4096,
    "num_hidden_layers": 2,
    "num_attention_heads": 4,
    "num_key_value_heads": 2,
    "head_dim": 64,
}
model = replace(build(config), name="Custom-Qwen3-2Block",
                source="in-memory custom Qwen3 config")
profile = get_profile("G0")
fmt = FORMATS["MXFP8"]
for point in (decode(128), prefill(16)):
    l0 = evaluate_l0(model, profile, fmt, point, use_doc_bytes=False)
    l1 = evaluate_l1(model, profile, fmt, point)
    assert not l1.cross_level_comparable
    assert len(l1.graph.operations) == 39
    assert l1.makespan_s is None and l1.modeled_throughput is None
    print(point.label, model.params_unique, l0.t_s,
          l1.known_service_budget_s, l1.projection_makespan_s)
```

启动方式：将代码放入自己的脚本，在已经设置 PYTHONPATH 的环境中运行 `python3 你的脚本.py`，或者交互式 Python 中执行。得到的是解析分析结果，不是真实生成文本。L1 的转换/特殊函数等阶段未知；条件投影仅反映当前固定计划的已知预算，不是延迟预测。

必须合并完整 CONFIG，因为 `build(config)` 不做默认值合并。应同步替换 name/source，否则报告可能把自定义尺寸标成默认 Qwen3-0.6B；但更换 source 字符串本身不会让 manifest 自动指纹新模型文件。

本示例唯一参数量为 2,622,976。decode/prefill 各有 39 个算子，即 2×18+3。数值随硬件配置及代码改变，应以运行结果为准。

在同一脚本中接上以下代码，可导出最后一个工作点（prefill 16）的两级网页。示例将自定义 config 直接记录到 named_inputs；公共来源指纹仍不是该自定义模型的独立文件指纹。

```python
from pathlib import Path
from hive.model_eval.common.manifest import build as build_manifest
from hive.model_eval.l0.excluded import for_workload
from hive.model_eval.l0.web import report_payload as payload_l0, render_html as html_l0
from hive.model_eval.l1.web import report_payload as payload_l1, render_html as html_l1
from hive.model_eval.l1.params import ModelParams

out = Path("sim/model_eval")
out.mkdir(parents=True, exist_ok=True)
inputs = {"config": config, "phase": point.phase.value,
          "seq_len": point.seq_len, "batch": point.batch, "fmt": fmt.name,
          "bytes_per_param_source": "exact", "dram_eta": profile.dram_eta}
man0 = build_manifest(profile=profile, model_name=model.name, layer="L0",
                      named_inputs=inputs, excluded_costs=for_workload(point))
man1 = build_manifest(profile=profile, model_name=model.name, layer="L1",
                      named_inputs={**inputs, **ModelParams().named_inputs(),
                                    "attention": l1.attention},
                      excluded_costs=list(l1.excluded_costs))
(out / "spec-20261105-custom-l0.html").write_text(
    html_l0(payload_l0(model, profile, fmt, [l0], man0)), encoding="utf-8")
(out / "spec-20261105-custom-l1.html").write_text(
    html_l1(payload_l1(profile, [l1], man1)), encoding="utf-8")
```

## 6.2 接入新的 L0 模型

需要编写一个返回 `ModelSpec` 的 builder，而不是只传模型大小或 Hugging Face 名称。

接入步骤：

1. 根据 config、权重形状和参考实现核对 block 类型、实例数、参数共享、输出头及状态。
2. 将参数容量、每步流过参数、MAC 固定/上下文线性/prefill 二次项分别推导，不能用“参数量≈MAC”替代所有算子。
3. 创建 LayerTypeSpec / ModelSpec，必要时附上 TensorSpec；L0 本身不依赖 tensors 推导总量。
4. 先通过 Python 调用 `evaluate_l0(model, profile, fmt, point)`。确认所有 Account 分项和总量一致。
5. 添加独立数值锚点和来源复算脚本。与已有 Qwen 数值分开，不修改既有模型公式来迁就新模型。
6. 需要 CLI 名称时，在当前 `frontend.qwen3_06b.MODELS` 注册零参数 builder，并让 get_model 能调用它。当前两个 CLI 都直接使用这里的 get_model；没有自动扫描模型文件的插件机制。

`ModelSpec` 的总量契约可以区分 MoE 的唯一/流过参数、KV/循环状态，但当前 L0 的一次权重读取、state 容量计流量及参数化输出头仍是简化模型。对新架构必须逐项声明适用性，不能把构造成功当成准确覆盖。

当前没有 `--config`、`--checkpoint` 或 `--model-path` 入口。自定义配置文件需要自己的加载脚本，并在系统边界校验类型、正尺寸、head 比例和缺字段。

## 6.3 接入新的 L1 架构

仅注册一个 ModelSpec 不够。当前 mapping 还依赖以下 Qwen 结构约定：

- 层型名字必须为 qwen3_full_attention。
- tensors 必须有 q/k/v/o/gate/up/down 七个名字，形状是 `[N,K]`。
- 从 `params_unique - 七投影元素总数 - 2×hidden_size` 的余额除以 2 反推 head_dim；这个余额代表 Q/K norm 两个增益向量。
- 然后由 q_proj / k_proj 输出宽度除以 head_dim 求 Q/KV 头数。
- 每层都生成两处 outer norm、Q/K norm、RoPE、KV、attention、SwiGLU 和两条残差。

任意省掉 Q/K norm、加入 bias / gate / MoE / DeltaNet 的模型不能伪装成该层型。反推尺寸没有完整语义校验，错误的参数余额可能造成错误 head 几何。

以上约定只约束默认 Qwen mapper，不约束通用任务图接口。可向 `mapping.lower_model()` / `timeline.evaluate()` 传入 `lowering=函数`；调用签名为 `lowering(model, fmt, point, attention=..., geometry=..., block_size=...) -> TaskGraph`。返回图会进行结构校验，但不会证明它与模型数学等价。也可直接构造 TaskGraph，通过 `timeline.evaluate_graph()` 调度，不必伪装成 Qwen 层型。新 kind 的服务仍需在对应引擎模型中实现；未有服务证据的阶段保持 None。验证要求见第 7 章。

## 6.4 只分析单个 GEMM / 直接任务图

不必创建整模型，也不必注册 builder。以下尺寸已经对齐；直接图需要显式给出 operand 类型、MX scale 轴及生产/消费关系：

```python
from hive.model_eval.common.opspec import (
    GemmSpec, LogicalOp, PrimitiveTask, TaskGraph, Tensor, TensorRef,
)
from hive.model_eval.hw.geometry import load_array_geometry
from hive.model_eval.hw.profiles import get_profile
from hive.model_eval.hw.topology import default_topology
from hive.model_eval.l1.params import ModelParams
from hive.model_eval.l1.residency import BufferPlan, ExecutionPlan
from hive.model_eval.l1.timeline import estimate_graph, evaluate_graph

op = LogicalOp("custom/q_proj", "custom", "q_proj", "weight_gemm",
               mac=1 * 2048 * 1024)
spec = GemmSpec(1, 2048, 1024, weight=True)
task = PrimitiveTask(op.id + "/execute", op.id, "GEMM", spec,
                     inputs=(TensorRef("a"), TensorRef("b")),
                     outputs=(TensorRef("c"),))
graph = TaskGraph((op,), (task,), (
    Tensor("a", (1, 1024), "MXFP8", external=True, scale_axis=1),
    Tensor("b", (1024, 2048), "MXFP8", external=True, scale_axis=0),
    Tensor("c", (1, 2048), "FP32"),
))
topology = default_topology(get_profile("G0"), load_array_geometry(), ModelParams())
plan = ExecutionPlan(weight_policy="stream", activation_policy="stream")
costs = estimate_graph(graph, topology, plan)
cost = costs[task.id]["gemm0"]
assert cost.metrics["commands"] == 1 and cost.metrics["windows"] == 4096
result = evaluate_graph(graph, topology, execution_plan=plan,
                        buffer_plan=BufferPlan.from_graph(graph, plan))
print(cost.known_service_s, result.makespan_s, result.projection_makespan_s)
```

`GemmSpec` / `GemmGroup` 的尺寸顺序为 m、n、k。GemmSpec 限制 semantic_macs 在原形状工作量内；LogicalOp.mac 的模型语义一致性仍由调用者负责。纯 GEMM 图可以有数值 actual 排程，但 GEMM excluded_costs 仍包含控制/排空等缺口，不能据此发布完整吞吐或已校准性能。

非对齐形状必须有覆盖合法化后 N/K 的零填充 backing，以及对应 `a_storage_shape` / `b_storage_shape`；不能只改尺寸而省略补齐生产者。默认 lowering 插入未知服务的 VPU convert（repack/pad）任务。独立 K 子命令产生独立 INT32/FP32 部分和，必须显式下游 k_accum 合并，不能把它藏进 GEMM 标量成本。

## 6.5 自定义硬件与格式

Python API 可直接接收手工构造的 HwProfile、NumFormat、ArrayGeometry；也可用 `dataclasses.replace()` 在已有对象上修改。速率和容量应来自具名假设或证据，不从目标吞吐反向拟合。

CLI 新 profile 需要加入 `hw.profiles.PROFILES`；新格式需要加入 `frontend.spec.FORMATS`，并核对 profile 的 mac_family。L1 还要检查 legalize 的格式别名、配对、硬件支持集及精度语义。注册 NumFormat 并不会自动扩展这些规则。

默认 geometry 从外部硬件配置读。本建模任务不修改 `src/workerbee/gem5/`；独立几何实验优先用 Python 参数注入，不把另一配置的几何冒充默认硬件事实。

# 7. 开发手册

## 7.1 开发步骤与修改位置

| 修改意图 | 入口 | 必须同步检查 |
|---|---|---|
| 新模型总量 | frontend builder / ModelSpec | 来源公式、共享/流过参数、L0 Account、manifest |
| 新 L1 层型 | mapping.lower_model / lowering= | LogicalOp 语义、typed tasks、生产/消费依赖、真实层顺序与模型对照 |
| 新算子 | 对应 *_model.estimate / kernels.estimate 分发 | engine/spec 一致、阶段/资源/流量/缓冲、未知服务与归因 |
| 新硬件速率 | HwProfile / PROFILES | 单核与整机口径、family、缺失速率行为、来源 |
| 新 geometry | ArrayGeometry / gemm_model.legalize / count_windows | C 拍、M 缓冲、N/K 对齐、显式 padded backing 与 K merge |
| 新数值格式 | NumFormat / FORMATS / gemm_model / typed spec | 元素/scale_axis、peak family、支持集、混配方向、INT32/FP32 与转换 |
| 新参数 | ModelParams / ExecutionPlan / Topology / run parser | 默认值、校验、具名输入、物理实例/私有容量与结果作用 |
| 报告变化 | to_dict / web.report_payload / template | schema2、actual/projection/budget 分离、null、HTML 转义和浏览器交互 |

不要为暂时没有证据的排空、转换或同步服务填一个“合理常数”。保留 excluded 或 seconds=None，直到有明确参数或证据来源。

## 7.2 必须保持的数值约束

- Account.total 等于有效分项之和。
- ModelSpec 参数公式与独立权重形状/来源复算一致。
- 不能将独立数据流在同一物理资源上的竞争用 max 隐去。
- 独立 K 子命令各自输出 C；跨命令部分和的外部累加成本不能消失。
- C 每行按外部拍宽计费，不允许小格式跨行合拍。
- 逻辑与物理字节分别记录；MX scale 不能只加平均字节而忽略物理请求数。
- semantic/useful/issued/padded/masked 的含义保持一致。
- TaskGraph 的 op/task/tensor ID 各自唯一、引用完整、依赖无环；消费切片必须被祖先写入或来自 external base，别名重叠写入必须有序。
- 同一物理端口上的独立流服务求和；阶段预算的同源取 max 不得吞掉任何 unknown。
- actual 与 projection 各有独立时间、归因、busy time 和容量字段；未知释放不能释放引擎租约或缓冲。
- 已完成排程的五类关键路径归因之和等于对应 makespan，不等于资源服务需求之和；未知跨度的归因保持 None。
- L0 是原口径参考，cross_level_comparable 保守为 False；不设严格 L1≥L0 断言，也不取 max(L1,L0)。actual 排程若完整，只检查其自身实际 DRAM 流量的共享带宽下界。
- JSON、HTML、终端显示来自同一次 Python 结果，展示层不复制成本公式。

## 7.3 测试启动

在仓库根并设置 PYTHONPATH 后运行：

```bash
python3 -m unittest discover -s src/hive/model_eval/tests -p 'test_*.py'
python3 -m unittest hive.model_eval.tests.test_l0_anchors
python3 -m unittest hive.model_eval.tests.test_l1_geometry
python3 -m unittest hive.model_eval.tests.test_l1_integration
python3 -m unittest hive.model_eval.tests.test_l1_vpu
python3 -m unittest hive.model_eval.tests.test_l0_web hive.model_eval.tests.test_l1_web
```

此处仅列启动入口，不登记本次全量回归或浏览器验收状态。

| suite | 验证面 |
|---|---|
| test_l0_anchors | Qwen 来源复算、参数/MAC/流量、格式和 profile 口径 |
| test_l0_web | 层/类型资源、网页数据和安全嵌入 |
| test_l1_geometry / test_l1_gemm_model | 统一 GEMM 的合法化、窗口、C 多拍、混配、存储与阶段 |
| test_l1_graph / test_l1_topology | typed graph、base/views、生产者、物理资源、私有域与实例候选 |
| test_l1_scheduler / test_l1_integration | 固定计划预约、生命周期、实际/投影分离、阻塞、关键路径与 L0 参考 |
| test_l1_vpu / test_l1_dma | VPU/CPU 参数、转换/K merge 未知服务、DMA 实际端点 |
| test_l1_web | schema2、原始 ledger、HTML/JSON 一致、实例/未知值及安全转义 |

新增算子至少覆盖最小形状、尾部补齐/边界、未知速率、所选 overlap/serial、原始需求和最终归因。金锚从张量语义或独立推导得到，不从正在测试的成本函数抄一个输出。

修改网页时还要生成 HTML，在浏览器使用工作点→层→算子的完整路径，检查窄屏、键盘、unknown/null、console 和外部请求。单元测试和语法检查不能替代交互验证。

## 7.4 来源与交付

建模代码、模型事实和性能分析分开维护。Qwen 基线数值以已有模型拆解复算为来源，不修改用户维护的 performance_analysis 文档。临时报告放在 sim/model_eval，不把生成文件和波形当成源码规格。

新增模型/算子后应提交可追溯的参数说明、独立公式、测试和变化后的报告口径；“结果符合预期”不能替代原始资源账目。

# 附录 A. 主要函数接口索引

以下是计算及集成接口。以下路径仍相对于 `src/hive/model_eval/`；带下划线的内部函数不作为稳定外部接口。

| 函数 | 参数 / 返回 | 作用 |
|---|---|---|
| frontend.qwen3_06b.build | config: dict / None → ModelSpec | 默认或完整自定义 Qwen 配置转规格 |
| frontend.qwen3_06b.get_model | name: str → ModelSpec | 注册表查找并调用 builder |
| hw.profiles.get_profile | name: str → HwProfile | 命名硬件配置查找 |
| hw.geometry.repo_root | start: Path / None → Path | 定位完整仓库 |
| hw.geometry.load_array_geometry | root: Path / None → ArrayGeometry | 加载 DEFAULT_PROFILE，结构校验 |
| hw.geometry.gem5_release | root: Path / None → tuple[str,str] | 读取 release、commit 元数据 |
| common.workload.decode / prefill | seq_len: int, batch: int=1 → WorkloadPoint | 文本工作点构造 |
| common.workload.frame | batch: int=1 → WorkloadPoint | frame 占位工作点 |
| l0.accounts.work_account | model, point → Account | 闭式总工作量及分项 |
| l0.accounts.traffic_account | model, fmt, point, *, use_doc_bytes: bool → Account | 总流量及分项 |
| l0.roofline.evaluate | model, profile, fmt, point, *, use_doc_bytes: bool=True → L0Result | 两资源 roofline |
| l0.bottleneck.attribute | result: L0Result, machine_balance: float → Attribution | 首位瓶颈和主导分项 |
| l0.bottleneck.kv_weight_crossover | model, bytes_per_param: float → float | 单序列 KV/唯一权重翻转点 |
| l0.excluded.for_workload | point: WorkloadPoint → list[str] | 基础及 phase 未覆盖项 |
| l0.run.build_parser / main | 无参数 → ArgumentParser；argv: list[str] / None → int | CLI，正常 main 返回 0 |
| l1.mapping.map_model | model, fmt, point, attention: str="vpu" → tuple[LogicalOp,...] | 默认 Qwen 逻辑算子展开，不是执行图 |
| l1.mapping.lower_model | model, fmt, point, attention="vpu", *, geometry=None, block_size=32, lowering=None → TaskGraph | 默认或调用者提供的 typed lowering |
| l1.gemm_model.legalize | shape: GemmShape, geometry: ArrayGeometry → tuple[Command,...] | 对齐并拆分命令 |
| l1.gemm_model.format_elem_bytes / format_is_mx | name: str → Fraction / bool | 格式元素字节 / 是否 MX 族 |
| l1.gemm_model.iter_windows | commands: Iterable[Command] → Iterator[Window] | 逐命令 N tile、K pass 枚举 |
| l1.gemm_model.count_windows | shape: GemmShape, commands: Iterable[Command] → WindowCounts | 解析计数，不物化所有窗口 |
| l1.kernels.estimate / 各 *_model.estimate | task: PrimitiveTask, engine_config: EngineConfig, execution_plan: ExecutionPlan → EngineCost | 单个候选引擎的阶段/资源/流量/缓冲预算 |
| l1.timeline.estimate_graph | graph: TaskGraph, topology: Topology, execution_plan: ExecutionPlan → dict[str,dict[str,EngineCost]] | 各任务可访问的候选实例估算 |
| l1.timeline.evaluate_graph | graph, topology, *, execution_plan=None, buffer_plan=None, scheduling_policy="list", costs=None → ScheduleResult | 直接图调度；未传 costs 时估算并添加 handoff |
| l1.scheduler.schedule | graph, costs, topology, buffer_plan=None, policy="list" → ScheduleResult | 固定图/缓冲计划的资源预约 |
| l1.residency.BufferPlan.from_graph | graph: TaskGraph, execution: ExecutionPlan / None=None → BufferPlan | 每个 tensor base 的完整生命周期 |
| hw.topology.default_topology | profile, geometry, params → Topology | 单 active core0，不从 profile.n_core 自动分片 |
| hw.topology.explicit_topology | profile, geometry, params, *, gemm_instances=1, vpu_instances=1, cpu_instances=1, dma_instances=1, active_cores=1, source="scenario", evidence="" → Topology | 显式实例数/核心与共享关系 |
| l1.timeline.evaluate | model, profile, fmt, point, *, attention="vpu", params=None, geometry=None, topology=None, execution_plan=None, buffer_plan=None, scheduling_policy="serial", lowering=None → L1Result | 全模型编排、schema2 报告与 L0 参考 |
| l1.run.build_parser / main | 无参数 → ArgumentParser；argv: list[str] / None → int | CLI，正常 main 返回 0 |
| common.manifest.build | 关键字 profile, model_name, layer, named_inputs, excluded_costs, root=None → Manifest | 可信度和来源出口 |

公共显示函数 `format_bytes`、`format_mac`、`format_time`、`Table.render`、`header_block`、`excluded_block` 只负责显示，不参与成本计算。结构化消费者应读取数值字段，不能从格式化字符串反解秒或字节。

# 附录 B. L1 详细契约与计算规则

## B.1 ModelParams

定义见 [l1/params.py:8](../model_eval/l1/params.py#L8)，为 frozen dataclass。

| 参数 | 默认值 | 单位 / 作用 |
|---|---|---|
| weight_policy | `"stream"` | 未传 ExecutionPlan 时的 B 策略；resident 失败不自动回退 |
| overlap | False | 引擎内显式重叠场景；跨任务并发由 scheduler/Topology 决定 |
| overlap_evidence | `""` | 调用者提供的重叠依据文字 |
| sync_us | None | 非 DMA 的跨引擎依赖边 handoff 延迟，µs；None 为未知，0 为零成本场景 |
| exp_cycles_per_element | None | 每次 exp 服务周期；softmax / SiLU 按元素计 |
| reciprocal_cycles_per_vector | None | 每次 reciprocal 服务周期；softmax 按向量，SiLU 按元素 |
| rsqrt_cycles_per_vector | None | norm 每个向量的 rsqrt 服务周期 |
| cpu_cycles_per_comparison | None | sampling 全排序比较预算的每比较周期 |
| cpu_freq_hz | None | CPU 场景频率，Hz；不自动沿用 GEMM 频率 |
| vpu_fp32_lanes | 8 | FP32 算术/归约回退预算的 lane 数 |
| vpu_fp16_lanes | 16 | attention FP16 FMA 峰值预算的 lane 数 |
| block_size | 32 | MX scale 分组元素数，仅 16 / 32 |

weight_policy 只允许 stream/resident。overlap=True 必须给非空白 evidence。sync、特殊函数服务、CPU 比较服务及 CPU 频率必须为有限非负数或 None；CPU 频率不能为 0。lane 数检查≥1，不做完整整数类型校验；调用者仍须遵守 int 注解。

None 表示没有数值服务；特殊函数或 sync_us 明确传 0 是“零周期场景”，不是未知。给出这些参数并不会定价转换、补齐、FP32 accumulation 或命令提交开销。

`named_inputs()` 返回所有字段，另加 numeric_source 和 service_assumptions；不再写死 active_cores。后者记录 lane 峰值、非 FMA 回退、特殊函数/CPU/handoff 的来源类别，不是独立证据认证器。实际拓扑、执行计划和调度策略由 CLI 另行登记。

MX 字节需特别核对：NumFormat 平均 scale 固定为 1/32，mapping 的 norm 权重与 L0 精确参考使用它；GEMM 与 Tensor 则使用 block_size 和 scale_axis。block_size=16 时，不能声称全模型各处都统一成 1/16 存储口径。

## B.2 LogicalOp、PrimitiveTask、TaskGraph 与 tensor

定义见 [common/opspec.py](../model_eval/common/opspec.py)。以下是当前 typed contract，不提供旧 OpSpec 兼容别名。

| 类型 | 构造字段（按声明顺序；未标默认为必填） |
|---|---|
| LogicalOp | id、layer、name、kind；mac/elements/vectors/length/read_bytes/write_bytes/dram_read_bytes/dram_write_bytes/weight_bytes=0，gemms=()，evidence="" |
| GemmGroup | m、n、k，repeats=1；仅用于逻辑展开的相同形状组，不是可绑定任务 |
| PrimitiveTask | id、parent_op_id、engine_kind、spec；dependencies/inputs/outputs/allowed_instances=() |
| TaskGraph | operations: tuple[LogicalOp,...]、tasks: tuple[PrimitiveTask,...]；tensors=()，evidence="user-defined lowering" |
| Tensor | id、shape、dtype；memory_domain="dram"，base_id=None，external=False，block_size=32，base_starts/base_stops/base_steps=()，scale_axis=-1 |
| TensorRef | tensor_id；starts/stops=()，dtype=None；bounds(tensor) 返回起止坐标 |
| GemmSpec | m、n、k；a_fmt/b_fmt="MXFP8"，c_dtype="FP32"，block_size=32，semantic_macs=None，weight=False，evidence=""，a_storage_shape/b_storage_shape=() |
| VpuSpec | kind；mac/elements/vectors/length/read_bytes/write_bytes/dram_read_bytes/dram_write_bytes=0，input_dtype/output_dtype="FP32"，evidence="" |
| CpuSpec | kind="sampling"，vectors/length=1，read_bytes/write_bytes/dram_read_bytes/dram_write_bytes=0，input_dtype="FP32"，evidence="" |
| TransferSpec | stream_id、source_domain、destination_domain、byte_count；tensor=None，direction="copy"，evidence="" |

LogicalOp 没有 engine 或 dependencies；一个 op 可以拥有任意多个原子任务。PrimitiveTask 的 engine_kind 为 GEMM/VPU/CPU/DMA，spec 必须分别为 GemmSpec/VpuSpec/CpuSpec/TransferSpec；allowed_instances 是实例 ID 白名单，不是任意 core/placement 字典。

Tensor 尺寸必须为正整数；支持 INT4/INT8/INT32/UINT8、FP8/MXFP4/MXFP8、BF16/FP16/FP32。`size_bytes` 为元素位宽向上取整；MX 另加 `其他轴元素数 × ceil(scale轴长度/block_size)` 的一字节 scale。GEMM A 为 `[M,K]`、scale_axis=1，B 为 `[K,N]`、scale_axis=0；block_size 必须匹配 GemmSpec。不能用默认末轴替代 B 的 K 轴。

view 的 base_id 只能引用 base allocation（不能 view 链），类型/域不变且容量可容纳。显式 base_starts/stops/steps 选择 base 元素，允许 reshape，但元素数必须一致；未给 selection 的 view 只重塑 base 的前 prod(shape) 个元素。TensorRef 是 view 自身坐标内的半开区间切片；dtype 不能偷偷改，转换必须有任务。所有 views 共享唯一 base allocation。

`TaskGraph.validate() -> tuple[str,...]` 返回稳定拓扑序，检查各类 ID 唯一、父 op/依赖/tensor 完整、无环、切片范围、GEMM operand 形状/类型/MX 轴及 backing。非 external base 的输入区域必须由依赖祖先完整写入；有交叠的其他写入也必须是祖先，无序重叠输出被拒绝。transfer stream 只能有一个任务所有者。校验不等价于算子数学正确性证明，也不自动生成搬运或 dtype 转换。

GemmSpec 只允许 FP32/INT32 累加，整数 A 对应 INT32，浮点对应 FP32；semantic_macs 必须在 0～M×N×K 内。VpuSpec 的工作量/流量为非负整数；CpuSpec 只支持 sampling 且 vectors/length 为正整数；TransferSpec 需非负整数 byte_count、stream ID 和 read/write/copy 方向。

## B.3 Qwen 展开与形状

展开器见 [l1/mapping.py:12](../model_eval/l1/mapping.py#L12)。设 H=hidden、I=intermediate、D=head_dim、h=Q 头数、hkv=KV 头数，R 为本轮投影行数：decode R=B，prefill R=B×S。

| 算子 | 引擎 / 形状或有效元素 |
|---|---|
| input_norm | VPU；R 个 H 长向量 |
| q_proj / k_proj / v_proj | GEMM；(R,hD,H) / (R,hkvD,H) / (R,hkvD,H) |
| qk_norm | VPU；(h+hkv)R 个 D 长向量，两个 D 长增益向量 |
| rope | VPU；(h+hkv)DR 个元素 |
| kv_transfer | 逻辑字段为写 layer_KV_bytes×R、decode 读 layer_KV_bytes×S×B；实际 DMA 只负责 append，历史 KV 读取归 QK/AV 或显式量化任务 |
| qk | VPU 或量化 GEMM，见下表 |
| softmax | VPU；decode 有效元素 hSB，prefill 为 hS(S+1)B/2 |
| av | VPU 或量化 GEMM，见下表 |
| o_proj | GEMM；(R,H,hD) |
| attention_residual | VPU；HR 个元素 |
| post_norm | VPU；R 个 H 长向量 |
| gate_proj / up_proj | GEMM；各 (R,I,H) |
| silu_gate | VPU；IR 个元素 |
| down_proj | GEMM；(R,H,I) |
| mlp_residual | VPU；HR 个元素 |

| 工作点 | QK GemmGroup | AV GemmGroup | 每个 QK / AV 的语义 MAC |
|---|---|---|---|
| decode | (B,S,D)，repeats=h | (B,D,S)，repeats=h | hDSB |
| prefill | (S,S,D)，repeats=hB | (S,D,S)，repeats=hB | hDS(S+1)B/2 |

量化 GEMM attention 发射完整密集矩阵，语义量按因果 mask 减少，结构量不减；没有“因果 mask 自动跳过 MAC”功能。VPU 路径以有效语义量计算，但使用 FP16 FMA 峰值，尚未覆盖 FP32 累加、转换和归约约束。两种路径不是精度已验证的可互换实现。

S=0 时不生成 qk / softmax / av，每个 block 剩 15 个算子。S>0 时每 block 18 个。尾部为 final_norm、单输出位置 lm_head、CPU sampling；prefill 也只执行 B 行 lm_head，不为所有 prompt 位置生成 logits。默认 28 个 block 共 507 个算子，decode S=0 为 423 个。

上述数量是 LogicalOp 数量，不是原子任务数。默认 `lower_model()` 根据生产/消费关系生成依赖：Q/K/V 共享一份量化输入，gate/up 共享另一份输入并形成独立分支；GQA 的逐 head views 共享 KV base，量化 K/V 按 batch/KV head 复用。调度策略 serial 会限制跨任务并发，list 才按图与物理资源选择可行绑定。

激活量化、INT32→FP32、FP16 KV pack、head assembly、operand repack/zero-pad 均为显式 VPU convert 任务，服务未知。非对齐 GEMM 使用 padded backing 及逻辑 view；独立 K 命令的部分和产生显式 k_accum 任务并保持 INT32/FP32 类型。norm 系数为独立 DMA 任务；KV append 从外部 FP16 staging 拷到外部 KV 分配，不凭空设为 SRAM 命中。VPU/CPU 边界 DRAM 读写和 local 多遍访问分别计账；embedding 仍没有独立算子。

## B.4 GEMM 合法化

定义见统一的 [l1/gemm_model.py](../model_eval/l1/gemm_model.py)；合法化、窗口计数和引擎预算均在此文件，不存在旧拆分模块或重导出兼容层。

```text
GemmShape(m, n, k, a_fmt="MXFP8", b_fmt="MXFP8", c_bits=32, block_size=32)
legalize(shape, geometry) -> tuple[Command,...]
```

m/n/k 必须是正整数，明确拒绝 bool。c_bits 只能 4/8/16/32；block_size 必须是正整数，MX 另限制 16/32 且不超过 K 窗口。

格式名按大小写和别名规范化，支持 INT8/INT4/BF16/FP8/MXFP8/MXFP4 及当前实现的 E4M3/E5M2/E2M1 拼写。A/B 必须同 family，或正向 INT8×INT4、MXFP8×MXFP4。反向混配不支持，还要分别核对 geometry.supported_fmts；显式 E4M3 请求不会因为只有 E5M2 就自动通过。

设 T=geometry.tile_n、eA=A 元素字节：

```text
K_per_beat = T × int(2/eA)
K_granule = K_per_beat
MX：K_granule = max(K_granule, block_size)
正向 W4A8：K_granule = max(K_granule, 4T)
N_pad = ceil(N/T) × T
K_pad = ceil(K/K_granule) × K_granule
limit = 2^mnk_width - 1
command_max_M = min(geometry.max_m, limit)
command_max_N = floor(limit/T) × T
command_max_K = floor(limit/K_granule) × K_granule
```

M 不补齐，只按 max_M 分段；N/K 补齐后按可表示的最大值拆分。命令次序为 M 段→N 段→K 段。字段位宽不能容纳一个对齐 N/K 命令时拒绝输入。

| Command 字段 | 含义 |
|---|---|
| m_offset、n_offset、k_offset | 原问题中的命令起点，N/K 可覆盖补齐区 |
| m、n、k | 本命令发射尺寸 |
| n_tiles、k_tiles | n/T、k/K_per_beat |
| k_per_beat、tile_n | 有效 K 打包量、N tile 宽度 |
| a_fmt、b_fmt、c_bits、block_size | 命令数值布局 |
| final_k | 是否最后一个独立 K 子命令；不是 C 写回门控 |
| c_bytes_per_beat | 外部 C DMA 拍宽，不是内部累加行宽 |

纯结构 legalize 支持 C 位宽 4/8/16/32；typed GemmSpec/estimate 的 C 固定为 32-bit INT32 或 FP32。单任务 spec 可指定合法的 A/B 正向混配，默认全模型 lowering 使用同一 fmt.name；CLI 没有 `--a-fmt/--b-fmt/--c-bits` 参数。GemmShape 仅检查结构，不代表 padded 数据已经存在。

## B.5 窗口与结构工作量

定义见 [l1/gemm_model.py](../model_eval/l1/gemm_model.py) 的 Window、WindowCounts、iter_windows 和 count_windows。

`Window(command_index, n_tile, k_pass, load_beats, consume_beats, c_rows)` 对应命令内一个 N tile、一个 K pass；load_beats=T，consume_beats=M，本命令最后内部 K pass 的 c_rows=M，其余为 0。

独立 K 子命令各自输出 C，即使 final_k=False。跨命令部分和不能靠内部 buffer 自动保留。

`WindowCounts` 包含 commands、windows、a_beats、b_beats、c_rows、c_beats、array_cycles、useful_macs、padded_macs、compute_cycles、load_cycles。对每条命令：

```text
windows += n_tiles × k_tiles
A_beats += windows_in_command × M
B_beats += windows_in_command × T
C_rows += n_tiles × M
C_beats_per_row = max(1, ceil(T × c_bits / (8 × C_bytes_per_beat)))
C_beats += C_rows_in_command × C_beats_per_row
issued_MAC += M × N × K
```

总 useful_MAC=原始 M×N×K，padded_MAC=issued−useful。compute_cycles=A_beats，load_cycles=B_beats，array_cycles=A+B；utilization=compute/array。这个比值是当前窗口结构中 consume 的占比，不是经过 RTL stall / drain 对拍的真实阵列利用率。

小格式 C 行也至少一拍，不跨行合拍。`iter_windows()` 可检查逐窗口序列；`count_windows()` 对命令直接计数，避免将大量窗口物化到内存。

## B.6 ExecutionPlan、缓冲生命周期、流量与 scale

执行/缓冲契约见 [l1/residency.py](../model_eval/l1/residency.py)，GEMM 物理需求见 [l1/gemm_model.py](../model_eval/l1/gemm_model.py)。

- `ExecutionPlan(weight_policy="stream", activation_policy="stream", alignment=32, evidence="explicit execution policy")`：B 允许 stream/resident，A 允许 stream/stage；alignment 为正整数，evidence 非空。resident 为一次 B DRAM stage 加后续 local 重装，stage 为每命令 A stage；不推断跨任务 cache 命中，也不自动降为 stream。
- GEMM 申请对齐的 A stream-window 或 staged command、B window、32-bit C tile，overlap 另申请 C buffer，resident 另申请整份 B。显式 resident/stage/overlap 要求已知 SRAM 且 footprint 可容纳；流式也有 scratch，容量未知不能证明调度可行。
- `BufferEndpoint(task_id, phase_id=None)`：task start/end 或指定 phase 边界。`BufferAllocation(id, memory_domain, size_bytes, acquire, releases, alignment=32, tensor_id=None, base_id=None, offset=None, region=None, source="explicit", evidence="explicit allocation lifecycle")`：size 可为 None，allocated_bytes 向 alignment 对齐；offset 必须对齐。
- `BufferPlan(allocations=(), execution=ExecutionPlan(), source="explicit", evidence="explicit graph allocation lifecycles")`：validate(graph) 校验生命周期与 base/域/大小；from_graph(graph, execution=None) 为每个被引用 base 建立唯一分配，从首写之前至全部末消费者完成。独立 external readers/disjoint writers 可用 source="graph-entry" 表示图入口持有，不捏造先后依赖。
- EngineCost.buffers 是局部 BufferRequest；若与全局 base 同域，扩充同一分配而不重复计容量；跨域 stage copy 是额外物理分配。固定 offset/region、对齐、活跃容量和私有域分别检查；不做自动 SRAM 分配/重规划。释放未知时生命周期保持开放。

GEMM logical bytes 包含合法化补齐后的数据，不只含原语义元素：

```text
A_logical_data = Σ(A_beats × K_per_beat × A_elem_bytes)
B_logical_data = Σ(B_beats × K_per_beat × B_elem_bytes)
C_logical = Σ(M_command × N_command × 4)  # 每个独立 K 命令均输出
A/B/C_physical = 对应拍数 × 外部拍宽
scale_logical = 对应数据拍数 × (K_per_beat/block_size)
```

scale 请求按实际 storage stride 和命令偏移计数。每次逻辑步进读一拍，地址非 4-byte 对齐另加一拍：`Σ_r,p [1 + (address mod 4 != 0)]`；地址包含 m/n/k_offset、内部/输出 pass 偏移。A stage 使用命令 K stride，A stream/B 使用全 padded K stride；B scale 沿 K（scale_axis=0）。每个请求为 4 B / 1 cycle，不复用相邻请求；余数类计数不逐元素模拟，响应延迟/stall 仍未知。

A stream 从 DRAM 重复读数据+逻辑 scale，stage 则每命令读一次；B stream 随窗口重读，resident 则首次装入 padded B。C 每条命令写 DRAM，physical C 行仍独立计拍；物理 DRAM burst 放大未知。Traffic 同时保留 logical/physical bytes 与真实 interface/domain，不能把沿途每个接口的 bytes 再加成 DRAM 总量。

`k_split_cells` 与 `k_split_accum_bytes=12×cells` 是 GEMM 所需下游合并的结构指标，不在 GEMM 中隐藏计费。默认 lowering 的 VPU k_accum 显式读两次/写一次（local 与 DRAM 分列）；FP32 merge 用 lane-rate add 回退，INT32 merge 服务未知且保持 INT32，不能静默转成 FP32。部分和 base、合并输出及其消费者生命周期均在图中。

## B.7 EngineCost、物理拓扑与调度

类型见 [l1/ledger.py](../model_eval/l1/ledger.py)，拓扑见 [hw/topology.py](../model_eval/hw/topology.py)，预约见 [l1/scheduler.py](../model_eval/l1/scheduler.py)。

| 类型 | 当前字段 / 默认值 |
|---|---|
| EngineCost | task_id、instance_id、phases；lease_resources/traffic/buffers/excluded_costs=()，metrics={}，ledger=Ledger() |
| Phase | id、seconds、category；dependencies/claims=()，known_seconds=0.0，source="structural"，evidence="" |
| ResourceClaim | resource，amount=1.0；必须为有限正数 |
| Traffic | stream_id、owner_task_id、tensor_id、interface、memory_domain、direction、logical_bytes、physical_bytes；physical_beats=0，segment/evidence="" |
| BufferRequest | id、memory_domain、size_bytes；alignment=1，acquire_phase/release_phase/tensor_id=None |
| Origin / Charge / Ledger | 原始来源与账目：Origin(stream, producer)，Charge(resource, instance, origin, category, seconds, beats=0, bytes=0, cycles=0, ops=0, overlap_class="serial", evidence="", source="structural")；Ledger(charges=[], coupling={}, excluded=[]) |

category 为 dram/gemm/vpu/cpu/handoff；seconds 为有限非负数或 None。`Phase.budget_s` 为 seconds（已知）或 known_seconds（未知的已知部分）。`EngineCost.known_service_s` 在局部阶段 DAG 上按 budget 求最长路径，不是所有 charge 秒数之和；complete 只表示所有 Phase.seconds 已知，不表示 excluded_costs 已清空。phase IDs/依赖、buffer phase endpoints 和 traffic owner 均校验。

原始 `fold(ledger)` 仍可按完整 Origin 折叠：同组重叠类别一致、不同资源必须有 same_source 依据、同名资源重复被拒绝。任一 seconds 未知则 FoldedCharge.seconds=None，known_seconds 只留已知 max；未知不被吞掉。它不是调度器，也不存在 compose 入口。GEMM emit 按物理资源汇总服务（同端口独立流求和），再对不同物理约束取 max 形成 Phase；coupling 在引擎模型产生，不由 residency 生成。

`Topology(instances, resources, memory_domains, source="structural", evidence="")` 的注册项为：

- `Resource(id, kind, capacity, source="structural", evidence="")`：kind=slot/bandwidth；容量为正有限值。引擎 lease 为 slot，共享 DRAM 为 bandwidth。
- `MemoryDomain(id, core_id, capacity_bytes, source="profile", evidence="")`：core_id=None 表示共享，私有域只允许本 core 访问；SRAM 容量不可跨 core 池化。
- `EngineConfig(id, kind, core_id, profile, geometry, params, resources, memory_domain, source)`：resources 将接口别名映射到注册物理 ID，resolve 不凭空发明端口。allowed_instances 过滤候选，validate_access 检查域及显式 DMA 端点。

default_topology 只启用 core0 的 GEMM/VPU/CPU/DMA 各一实例，不使用 profile.n_core 倍乘任务吞吐。explicit_topology 可声明多实例/多 core；需要 source/evidence，实例逐类 round-robin 放到 core，仍共享一个 `dram` 带宽资源，每 core 的 local 读写共用一个保守 slot，GEMM scale 端口单独登记。增加实例不自动拆分 GEMM/重写图，也不证明真实 bank 并发能力。

serial 使用稳定任务拓扑序且限制前一个任务完成后再运行；list 在 ready 任务的候选中按可行 finish、start、task ID、instance ID 选最小值。均非抢占，固定速率预约；整任务（含内部等待）持有 engine lease，phase 另外预约端口/带宽，容量冲突只移动到已知释放端点。不能用 max 隐去共享端口竞争或把 SRAM 容量未知当无限。

调度先生成条件预算 projection，再按其选择顺序/绑定 replay actual。actual 将未知阶段占用视为无已知释放，阻断依赖及相冲突租约/资源/内存；输出用 None，不写 infinity 或零。projection 使用 Phase.budget_s 但仍受真实拓扑与固定 BufferPlan 约束：必需 SRAM 容量或 allocation 大小未知，以及已指定 offset/region 的容量/重叠冲突，也可阻断投影。offset/region 未指定不等于自动分配已完成。阻塞是当前固定计划不可完成，不是证明 workload 不可执行。

GEMM loadW 不与 consume 自动重叠；显式 consume/C-write 场景在共享 local 上仍要累加端口需求，并申请额外 C buffer。VPU 的 arithmetic/local 重叠要求证据和不同物理资源；特殊函数、边界 DRAM 和 CPU 比较仍按依赖执行。handoff 按非 DMA 的跨引擎依赖边加前缀 Phase，None 为未知、显式 0 为零成本场景。

服务换算由 [l1/dram.py](../model_eval/l1/dram.py) 提供：seconds(byte_count,profile)、local_seconds(byte_count,profile)、cycle_seconds(cycles,profile)；缺 local 速率或频率返回 None，不自行猜测。

## B.8 VPU、CPU 与 DMA

独立预算见 [l1/vpu_model.py](../model_eval/l1/vpu_model.py)、[cpu_model.py](../model_eval/l1/cpu_model.py)、[dma_model.py](../model_eval/l1/dma_model.py)，均为 `estimate(task, engine_config, execution_plan) -> EngineCost`。

| VPU kind / CPU 服务 | 算术预算 | 附加阶段 / 未知服务 |
|---|---|---|
| norm | 3×ceil(elements/FP32_lanes) | vectors 次 rsqrt；norm 系数由 lowering 的 DMA task 负责 |
| softmax | 3×ceil(elements/FP32_lanes) | elements 次 exp、vectors 次 reciprocal |
| rope / residual / silu_gate | ceil(mac/FP32_lanes) | SiLU 另有 elements 次 exp 和 reciprocal |
| attention_vpu | ceil(mac/FP16_lanes) | 非 FP16→FP16 增加未知 conversion，FP32 输出另有未知 accumulation；整数/量化类型无该峰值服务 |
| k_accum | FP32→FP32 为 ceil((elements or mac)/FP32_lanes) | INT32 merge 保持类型但 arithmetic 服务未知；不隐式 FP32 typecast |
| convert | seconds=None | 量化/packing/dequantization、同 dtype repack/pad/布局组装均未定价 |
| CPU sampling | vectors×ceil(length×log2(max(2,length))) 次比较 | 需要 cpu_cycles_per_comparison 与 cpu_freq_hz；分 batch 向量，不展平成一次 sort |

norm/softmax 与 FP32 add 是 non-FMA lane-rate fallback，FP16 attention 是 architecture_peak，不是实测。特殊函数缺参仍创建 seconds=None 的 Phase；给参数后按 calls×cycles_per_call÷profile.freq_hz，缺频率仍未知。其他类型的浮点运算可增加 conversion 阶段；不支持的整数/量化算术保持未知，不套 FP32 lane 峰值。CPU 比较有独立频率/模型，不路由到 VPU，也不执行 sampler。

VPU/CPU 的 DRAM ingress→local/compute→DRAM egress 均为显式阶段；local 按 read_bytes+write_bytes 合计，scratch/staged chunk 有 acquire/release，overlap 的 read scratch 持有到并行用户 join。已知读取预算不代表比较或转换服务已覆盖。

DMA 只计实际 source/destination 端点：DRAM→DRAM 是一次读加一次写；私有→私有不计 DRAM，必须使用两端对应的物理 local 端口。跨 core 路由/可见性是未知 cross_domain_route Phase，不能当免费 copy，也不能把远端访问误记为 DMA 自己的 local 端口。TransferSpec 的 direction 是声明，不能改变实际端点收费；Traffic 记录每端 interface、domain、read/write、stream 和字节，默认 chunk 为有限缓冲预算，不表示完整张量驻留。布局/strides、bank conflicts、route latency 和 DRAM first-byte 服务仍未校准/未覆盖。

## B.9 ScheduleResult、指标与 schema2

`kernels.estimate()` 返回 EngineCost，而非 `(Ledger, metrics)`。GEMM metrics 包含 WindowCounts、useful/issued/padded/semantic/masked_macs、a/b/c logical/physical bytes、scale_a/b logical/physical bytes、dram_bytes、weight_unique/weight_dram/weight_reload_bytes（仅 spec.weight=True）、k_split_cells/k_split_accum_bytes、placement（策略/容量/footprint/alignment）及 array_utilization。semantic_macs 来自 GemmSpec.semantic_macs 或原 M×N×K，masked=useful−semantic。

VPU metrics 为 kind/cycles/ops/elements/vectors/overlap/input_dtype/output_dtype/evidence；CPU 为 kind/comparisons/cycles/vectors/length/missing_inputs/evidence；DMA 为 stream_id/direction/source_domain/destination_domain/physical_bytes/local_bytes/dram_read_bytes/dram_write_bytes/evidence。不适用项不是已覆盖的零服务。全模型 numeric metrics 从选定成本汇总，DRAM 总量仅从 memory_domain="dram" 的 Traffic 求和；array_utilization 用总 compute/array，semantic_macs 用 LogicalOp.mac，norm_calls/softmax_reductions 从 typed tasks 汇总。

`ScheduleResult` 的完整字段为 tasks、makespan_s、projection_makespan_s、attribution_s、projection_attribution_s、resource_busy_s、projection_resource_busy_s、memory_peak_bytes、projection_memory_peak_bytes、blocked_constraints、policy，以及 projection_kind="conditional_budget_nonactual"。`to_dict()` 保持 dataclass 字段，不把投影值塞入 actual 字段。

`TaskTiming` 的字段为 task_id、instance_id、start_s/end_s、projection_start_s/projection_end_s，phases=()、waits=()、blocked_constraints=()、status="scheduled"、budget_instance_id=None。status 可为 scheduled/projected/blocked/unassigned。未绑定任务 instance_id=None；budget_instance_id 仅标明保留预算使用的候选，不宣称已绑定或可调度。绑定来自可行 projection 的选择，actual end 仍可能未知。

`PhaseTiming` 分别给 phase_id 与 actual/projection 起止；未知 phase 的 actual 起止均为 None。`Wait` 包含 reason、resource、两套起止和 blocking_task_ids/blocking_allocation_ids；dependency/serial/resource/memory 等等待保持各自语义。busy time 是预约区间并集，不是带宽加权服务；未知占用/未完成的潜在使用传播为 None。内存峰值也受未知大小/释放/私有容量影响，不借投影补全 actual。

`L1Result` 字段为 model、profile、workload、fmt、attention、graph、topology、execution_plan、buffer_plan、schedule、costs、l0_t_s、metrics、excluded_costs，cross_level_comparable=False、comparison_reason，以及不可由构造器改成 True 的 performance_valid/data_valid。selected_costs 按 TaskTiming.budget_instance_id 取预算；unbound task 的候选估算必须带标签。

序列化见 [l1/timeline.py](../model_eval/l1/timeline.py) 的 `L1Result.to_dict(debug=False)`：

| schema2 JSON 字段 | 含义 |
|---|---|
| schema_version / model_kind | 2 / deterministic_resource_schedule |
| model/profile/fmt/phase/seq_len/batch/attention | 实际输入身份 |
| active_cores / scheduling_policy | Topology 的实际核心数 / serial 或 list |
| known_service_budget_s / budget_basis | 选定任务 EngineCost.known_service_s 之和，含明确标记的 unbound candidate；不是排程跨度 |
| makespan_s / attribution_s | 完整 actual 排程及其五类关键路径归因；未完整则 null |
| projection_makespan_s / projection_attribution_s | 条件预算投影跨度及对应归因；固定计划阻塞也为 null |
| l0_time_lower_bound_s / cross_level_comparable / comparison_reason | 原精确平均字节 L0 参考；保守 False，不施加严格跨级断言或 max(L1,L0) |
| modeled_throughput / throughput_unit | 完整 actual、无 excluded 且时间非零才可计算；当前全模型始终 null |
| coverage_complete/performance_valid/data_valid | 全部 False，不由测试或场景参数提升 |
| layers | layer、operators/tasks 数、known_service_budget_s、actual span_s、projection_span_s |
| operators | LogicalOp 字段、所属 tasks、预算、两种 span、metrics/excluded；debug=True 加聚合 ledger |
| tasks | PrimitiveTask 字段、instance_id/budget_instance_id/cost_basis/status、两套时间、phases、waits、预算/metrics/excluded |
| traffic | 沿途 interface/domain 流量，附 budget_instance_id/cost_basis 标签 |
| resource_busy_s / projection_resource_busy_s | 实际/投影资源忙区间并集 |
| memory_peak_bytes / projection_memory_peak_bytes | 实际/投影各内存域峰值，unknown 保持 null |
| topology / buffer_plan / graph / schedule / excluded_costs | 完整可追溯契约和当前固定计划的阻塞/缺口 |

cost_basis 为 bound_instance 或 unbound_candidate_budget。op/layer 的 span 是其任务最晚 end 减最早 start，任一端点未知则 None；不是原先的算子 t_s 累加。关键路径、预算、层 span 和 busy time 不混合求和。

310 缺频率/local 速率，且必需 SRAM 的容量未知可连 projection 也阻断；仍可保留 labeled candidate 的已知 DRAM 预算，不发布完整吞吐。完整 actual 若可计算，仅校验该图自身实际 DRAM 流量的共享带宽下界；L0 工作量/模型计量保持不变。

# 附录 C. CLI 参数完整表

## C.1 L0

定义见 [l0/run.py:41](../model_eval/l0/run.py#L41)。

| 参数 | 默认值 | 含义 |
|---|---|---|
| --model | Qwen3-0.6B | 注册表模型名 |
| --profile | G0 | 注册表 profile 名 |
| --workload | decode | decode / prefill / frame |
| --seq | 1 | 逗号分隔的独立长度；frame 不使用这些长度 |
| --fmt | MXFP8 | MXFP8 / MXFP4 / INT8 |
| --exact-bytes | False | 使用 NumFormat 精确平均字节值 |
| --json / --html | None | 输出路径；不指定时仅输出终端 |

L0 CLI 没有 batch 参数，固定构造 batch=1；Python API 支持 batch。也没有 config、checkpoint、dram_eta、torch-check 参数。

--seq 必须含至少一个有效整数。空列表当前会在读取第一个 result 时失败；非法整数也会直接失败。frame 仍先解析 --seq，但只生成一个 frame 工作点，不能把它当成 YOLO 已实现前端。

L0 没有检查 JSON 与 HTML 同路径，后写的 HTML 会覆盖 JSON。用户必须使用不同路径。L1 则在任何输出写入前拒绝同路径。

## C.2 L1

定义见 [l1/run.py:21](../model_eval/l1/run.py#L21)。

| 参数 | 默认值 | 含义 |
|---|---|---|
| --model | Qwen3-0.6B | 注册表模型名 |
| --profile | G0 | 注册表 profile 名 |
| --workload | decode | decode / prefill，无 frame |
| --seq | 4096 | 逗号分隔的独立长度 |
| --batch | 1 | 独立序列数 |
| --fmt | MXFP8 | MXFP8 / MXFP4 / INT8 |
| --attention | both | vpu / quantized_gemm / both；API 默认是 vpu |
| --weight-policy | stream | stream / resident；显式驻留失败不自动回退 |
| --activation-policy | stream | stream / stage；显式每命令 A staging |
| --block-size | 32 | 16 / 32 |
| --sync-us | None | 跨引擎依赖边 handoff；省略为未知，显式 0 为零成本场景 |
| --exp-cycles-per-element | None | exp 服务周期 |
| --reciprocal-cycles-per-vector | None | reciprocal 服务周期，SiLU 按元素 |
| --rsqrt-cycles-per-vector | None | rsqrt 服务周期 |
| --cpu-cycles-per-comparison | None | sampling 比较服务周期 |
| --cpu-freq-hz | None | CPU 场景 Hz |
| --overlap | False | 显式引擎内重叠场景，仍受物理端口与缓冲约束 |
| --overlap-evidence | 空字符串 | 非空依据文字 |
| --schedule | serial | serial / list；直接 evaluate_graph 的默认是 list |
| --gemm-instances / --vpu-instances / --cpu-instances / --dma-instances | 各 1 | 各引擎显式物理实例数，不自动拆任务 |
| --active-cores | 1 | 显式核心数，不采用 profile.n_core 自动分片 |
| --topology-evidence | 空字符串 | 多实例/多 core 必须提供非空说明；单 core 各一实例自动登记说明 |
| --json / --html | None | 独立输出路径 |
| --debug-ledger | False | JSON 包含算子 raw ledger；phases/traffic 始终保留，HTML 始终包含 ledger |
| --layer-detail | False | 终端打印逐层 op/task 数和已知服务预算，不改变结果 |

L1 没有修改 lane 数的 CLI 参数，Python 使用 ModelParams 的两个 lane 字段。多个工作点的结果按 seq 次序，再按 attention 变体次序输出；both 为每点两份结果。自定义 lowering、BufferPlan、异构引擎 profile 与资源注册使用 Python API，不是 CLI 自动搜索功能。

两级 main 正常返回 0。argparse 输入错误退出码为 2；L1 把分析中的 ValueError / KeyError 转成 parser.error，缺文件、断言等异常不保证统一包装。不能依赖所有失败都有同一种错误结构。

# 附录 D. 可选 PyTorch 工作量核对

接口见 [l0/torch_check.py:41](../model_eval/l0/torch_check.py#L41)：

```text
cross_check(model, point, *, module=None, example_inputs=None,
            tolerance=0.02) -> CrossCheck
```

调用者需自行准备与工作点匹配的 PyTorch module 和可展开的位置参数序列 example_inputs。函数在 `FlopCounterMode(display=False)` 和 `torch.no_grad()` 内实际调用 `module(*example_inputs)`，把 counter FLOP/2 与 `work_account(model,point).total` 比较。它不自动加载 Qwen、下载 checkpoint、构造 KV、设置 eval 模式、捕获 torch.export 图或检验输出值。

`CrossCheck(status, reason, spec_mac=None, torch_mac=None, rel_error=None)` 的 status 为 ok/mismatch/skipped。rel_error=abs(torch_mac−spec_mac)/spec_mac，小于等于 tolerance 为 ok。

缺 torch、缺 module/inputs、frame 未实现或计数为零时 skipped；`CrossCheck.ok` 对 ok 和 skipped 都返回 True，所以必须检查 status 才能声称执行了核对。真实 forward 抛出的异常不会全部转换成 skipped。

全量 FLOP/2 只是矩阵乘的 MAC 换算约定，非矩阵算子计数、单位置 LM head、因果对角线、softmax 和 KV 输入等可能存在口径差，不能将 mismatch 直接解释成模型公式错误。它不提供 Hive wall time，也不会修改有效性标志。本次环境未安装 torch，只验证缺依赖的 skipped 路径，未验证真实 Qwen forward。

# 附录 E. Manifest、JSON 与离线网页

## E.1 来源与可信度

定义见 [common/manifest.py:49](../model_eval/common/manifest.py#L49)。

| Manifest 字段 | 含义 |
|---|---|
| profile、model、layer | 报告对象身份 |
| trust_level | 当前 build 固定 engineering_estimate；枚举另含 analytic_only |
| performance_valid、data_valid | build 固定 False |
| notice、not_calibrated_reason | 未校准声明及原因 |
| named_inputs、excluded_costs | 显式分析参数及排除项 |
| sources | SourceFingerprint(path, sha256, bytes) 列表 |
| gem5_release、gem5_commit | 硬件来源记录的版本，不是运行状态 |
| citations | profile 的 doc/line/quantity 列表，默认空列表 |

`build(*,profile,model_name,layer,named_inputs,excluded_costs,root=None)` 指纹以下固定真源，缺失即 FileNotFoundError，不静默跳过：

- [arch.md](arch.md)
- [arch_roadmap.md](arch_roadmap.md)
- [Qwen3-0.6B 逐层计算拆解](model_analysis/Qwen3-0.6B-逐层计算拆解.md)
- [硬件配置](../../workerbee/gem5/configs/hardware.py)

`to_dict()` 转为普通嵌套对象；`to_json(indent=2)` 输出中文、排序键的 JSON；`digest` 是完整 manifest 的排序紧凑 JSON SHA256，不包含 results。本工程没有完整的结果内容哈希；L1 JSON/result 使用 schema_version=2，L0/Manifest 不因此自动迁移为统一版本。

L0 CLI 的 named_inputs 记录 dram_eta、fmt、实际平均字节及来源、geometry 身份和 tile_n；没有记录 seq 扫描、batch 或自定义配置。因此不同工作点扫描可能共享 manifest digest，不能只按 digest 判断结果文件相同。

L1 CLI 在公共指纹基础上增加 WorkerBee 规格、common/opspec.py typed contract、Qwen builder、geometry/profiles/topology 和 l1 下所有 py 文件；仅请求 HTML 时额外指纹 L1 HTML 模板。它记录 seq、batch、attention 变体、ModelParams、输出 INT32/FP32、Topology、ExecutionPlan、调度策略、lowering/projection 说明，但仍没有指纹整个 frontend/spec.py、common/workload.py 等依赖闭包；L0 也不自动指纹全部代码及 HTML 模板。

ModelSpec.source 是文字，不会自动加载或指纹其指向文件。自定义模型/API 报告的调用方应在 named_inputs 记录完整 config / workload / scenario，并明确补充模型文件等指纹或保存独立输入副本。不能将当前 manifest 包装成无缺口的复现证明。

## E.2 导出接口

| 函数 | 签名 / 返回 |
|---|---|
| l0.web.layer_resources | (model,profile,fmt,result) → list[dict] |
| l0.web.report_payload | (model,profile,fmt,results:list[L0Result],manifest) → dict |
| l1.web.report_payload | (profile,results:list[L1Result],manifest) → dict |
| l0.web.render_html / l1.web.render_html | (payload:dict) → str |
| Ledger.to_dict | () → charges / coupling / excluded dict |
| ScheduleResult.to_dict | () → dataclass 字段 dict（actual/projection 分离） |
| L1Result.to_dict | (debug:bool=False) → schema_version=2 dict |

JSON 导出由各 CLI 负责写文件，web 函数返回 payload 或 HTML 字符串，不自行落盘。直接 Python 调用可以复用相同导出函数，但必须自行创建 manifest 和管理输出路径。

| 导出 | 顶层结构 |
|---|---|
| L0 CLI JSON | manifest、results |
| L1 CLI JSON | schema_version=2、manifest、manifest_digest、results（每条同为 schema2） |
| 两级 HTML payload | manifest、digest、hardware、cases（L1 cases 同为 schema2） |

L0 HTML 的 cases 含工作点总量和 layers；每层有 id/name/kind、mac、weights/kv/state/traffic bytes、两个资源时间、局部 t_lower_bound_s 和 tensors。Qwen 为 28 blocks+norm+head=30 组。当前层型显示全部使用 Block 编号，不保留 layer_type.name 为行种类，也只显示所给 tensors，不是自动完整权重 inventory。

L0 导出校验层 MAC / traffic 合计等于整体 Account。prefill 导出还隐含参数分解要求：params_unique=Σ(count×layer_unique)+hidden_size+lm_head_params；有独立 embedding 或不同共享关系的新模型即使 L0 主 API 可以计算，现有网页拆分也可能不适用而拒绝总账。

L1 HTML cases 复用 `result.to_dict(debug=True)`，仅另加 label；hardware 区分 profile configured_cores 与 Topology active_cores。actual、条件 projection、预算、实例/等待/容量和 raw ledger 均来自同一次结果；HTML 不重新计算资源时间，也不借投影补全实际时间。

两级 render_html 将数据嵌入各自 resources.html 模板，转义 `<`、`>`、`&`、U+2028、U+2029，避免模型名或 evidence 闭合 script 标签。L1 JSON 和两级 HTML payload 序列化使用 allow_nan=False；结构化消费者应保留 None/null，不转成性能零值。

## E.3 目前不覆盖的功能清单

- 自动读取任意 checkpoint/config、PyTorch 图到 TaskGraph 的捕获/转换及自动编译器 lowering；手工 lowering 与直接任务图 API 已支持。
- Qwen3 以外架构的内置完整 L1 mapping，视觉 frame 前端、MoE 路由、MLA / DeltaNet 等专用成本。
- 自动多核分片、自动融合、最优图/tiling 搜索或自动 SRAM 分配；显式多实例图可解析预约，但跨 core DMA 的 route/visibility 服务仍未知，条件投影不证明实际并行。
- 完整的命令设置、prologue/epilogue、drain、控制气泡、DMA 首字节/响应等待、bank conflict 和物理 DRAM burst。
- 激活量化、scale 生成、布局转换和整数输出到 VPU FP32 的转换服务。
- attention FP16/FP32 混合精度服务与量化 attention 精度验证。
- 默认特殊函数、真实 CPU sampler、命令提交/软件 scheduler 的完整服务。
- 完整的来源依赖闭包、结果文件内容哈希及 RTL 校准导入。

这些缺口必须随报告保留。增加一个场景参数可以覆盖某项数值预算，但不会自动补齐相邻的精度、布局、调度或校准证据。
