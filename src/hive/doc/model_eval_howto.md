# model_eval 操作指南

本文面向已完成[入门教程](model_eval_tutorial.md)的使用者，提供具体任务的操作步骤。字段、公式和参数默认值以[建模规范与接口参考](model_eval_spec.md)为准；设计原因见[设计说明](model_eval_explanation.md)；模型拆解和代码调用关系见[Qwen3-0.6B 端到端代码导读](model_eval_end2end.md)。

所有命令从仓库根目录执行，输出文件放在 `sim/model_eval/`。比较结果时保持模型、格式、工作点、拓扑、执行计划和未覆盖项口径一致。

## 如何比较多个工作点和 attention 路径

用逗号分隔独立的 decode 工作点，并分别生成 VPU 与量化 GEMM attention 结果：

```bash
PYTHONPATH=src python3 -m hive.model_eval.l1.run \
  --workload decode \
  --seq 0,32,4096 \
  --batch 1 \
  --attention both \
  --json sim/model_eval/attention-comparison.json \
  --html sim/model_eval/attention-comparison.html
```

在 HTML 顶部下拉框切换 case。`both` 表示分别分析两种路径，不表示二者组成流水线或将时间相加。

比较时逐项记录：

- `phase`、`seq_len`、`batch` 和 `attention`；
- `known_service_budget_s`、`makespan_s` 和 `projection_makespan_s`；
- excluded costs、未知阶段和 blocked constraints；
- manifest、拓扑、执行计划和来源指纹。

每个 `--seq` 是独立工作点，不是连续生成到该长度。decode 的 S 是参考历史 KV 长度；prefill 的 S 是整段 prompt 长度且必须大于 0。

## 如何评估 prefill 与多实例并行

生成 prefill 工作点，并显式构造两个 GEMM 实例的 list 调度场景：

```bash
PYTHONPATH=src python3 -m hive.model_eval.l1.run \
  --workload prefill \
  --seq 17 \
  --batch 2 \
  --attention vpu \
  --schedule list \
  --gemm-instances 2 \
  --topology-evidence "explicit duplicate GEMM analytical scenario" \
  --json sim/model_eval/two-gemm-prefill.json \
  --html sim/model_eval/two-gemm-prefill.html
```

`--gemm-instances 2` 只增加候选实例和对应物理 slot，不会自动拆分单个 GEMM，也不会复制 DRAM 带宽或私有 SRAM。

在报告中检查：

1. 任务是否实际绑定到不同 `instance_id`；
2. 是否仍存在依赖、共享 DRAM、local 端口或容量等待；
3. actual 与 projection 是否分别完整；
4. 多实例带来的投影变化是否来自真实可并行任务，而不是服务预算变化。

多核场景还需设置 `--active-cores` 并提供非空 `--topology-evidence`。跨核私有 SRAM 数据交换必须有显式 DMA。

## 如何配置权重驻留和激活 staging

权重 resident：

```bash
PYTHONPATH=src python3 -m hive.model_eval.l1.run \
  --seq 0 \
  --weight-policy resident \
  --activation-policy stream \
  --attention vpu \
  --json sim/model_eval/weight-resident.json
```

激活 stage：

```bash
PYTHONPATH=src python3 -m hive.model_eval.l1.run \
  --seq 0 \
  --weight-policy stream \
  --activation-policy stage \
  --attention vpu \
  --json sim/model_eval/activation-stage.json
```

检查 buffer allocation、内存峰值、容量错误和 blocked constraints。容量未知或超限时，工具不会自动降级为 stream、缩小 tile、spill 或推断 cache 命中；应修改显式执行计划或提供有依据的硬件容量。

启用 overlap 时同时提供证据：

```bash
PYTHONPATH=src python3 -m hive.model_eval.l1.run \
  --seq 0 \
  --overlap \
  --overlap-evidence "explicit double-buffer analytical scenario" \
  --json sim/model_eval/overlap-scenario.json
```

evidence 只记录场景声明，不会被程序验证为测量或 RTL 证据。

## 如何分析 310 profile 的部分已知预算

```bash
PYTHONPATH=src python3 -m hive.model_eval.l1.run \
  --profile 310 \
  --workload prefill \
  --seq 16 \
  --attention both \
  --json sim/model_eval/310-prefill.json \
  --html sim/model_eval/310-prefill.html
```

`310` 是 `MODEL_ANALYSIS_310` 的别名。该 profile 缺少频率、local 服务和 SRAM 容量，因此：

- L0 仍可使用声明的 310 GB/s 和 12.5 TMAC/s 计算 roofline；
- L1 保留已知 DRAM 预算和候选成本；
- local、阵列周期和容量相关服务保持未知；
- 流式 scratch 也可能因容量无法证明而阻断条件投影；
- 完整模型吞吐保持 `null`。

不要为了得到时间而猜测频率、local 速率或 SRAM 容量。显式假设应作为独立场景记录，而不是修改 profile 的已知事实。

## 如何提供显式服务场景参数

以下参数仅用于演示场景输入，数值不是目标硬件实测结果：

```bash
PYTHONPATH=src python3 -m hive.model_eval.l1.run \
  --profile G0 \
  --seq 0 \
  --sync-us 2 \
  --exp-cycles-per-element 1 \
  --reciprocal-cycles-per-vector 1 \
  --rsqrt-cycles-per-vector 1 \
  --conversion-elements-per-cycle 8 \
  --accum-elements-per-cycle 8 \
  --cpu-cycles-per-comparison 1 \
  --cpu-freq-hz 500000000 \
  --json sim/model_eval/explicit-services.json
```

检查任务阶段和 manifest 中的场景参数。`--sync-us 0` 表示显式零成本 handoff 场景；省略该参数才表示未知。`--conversion-elements-per-cycle` 和 `--accum-elements-per-cycle` 分别给 conversion/pack/dequant 与 FP32 accumulation 一个场景吞吐；省略时仍为 unknown，不会静默按 1 或 0 计费。填入这些参数不会补齐控制排空、路由和其他未覆盖服务。

## 如何读取算子 cost、下界与覆盖率

默认 CLI 输出已经从“只有投影”改为“算子 cost + 保守下界 + 覆盖”：

```bash
PYTHONPATH=src python3 -m hive.model_eval.l1.run \
  --profile G0 --seq 4096 --attention vpu --schedule serial
```

重点看四块：

1. **下界表**：`max(最忙单引擎, DRAM 流量下界, DAG 关键路径)`。它是下界，不是可达 makespan；跨引擎忙碌不能相加。
2. **覆盖**：`已知任务 / 全部任务` 与未知比例。下界相同不代表两个拓扑可比，必须同时看覆盖。
3. **Top unknown**：按根因聚合，例如 `conversion`、`fp32_accumulation`、`handoff`、`rsqrt`、全局 model-limit。不是 517 条重复条目。
4. **算子 cost 表**：按已知预算排序。`时间` 为未知时显示“未知”，但 `已知预算` 仍列出该任务已计入的部分；`basis` 区分 structural/architecture_peak/fallback/scenario/unknown。

需要阶段、等待、buffer 和拓扑原文时加 `--detailed`；`--top-unknowns` 和 `--top-ops` 控制默认行数。JSON 仍保留完整字段，并新增 `operator_costs`、`bounds`、`coverage`、`top_unknowns`。

## 如何评估同构 Qwen 尺寸变体

适用于仍为 Qwen3 full-attention、Q/K norm 和 SwiGLU 的同构结构。以下配置只是分析示例，不是另一个已发布模型：

```bash
PYTHONPATH=src python3 - <<'PY'
from dataclasses import replace

from hive.model_eval.common.workload import decode
from hive.model_eval.frontend.qwen3_06b import CONFIG, build
from hive.model_eval.frontend.spec import FORMATS
from hive.model_eval.hw.profiles import get_profile
from hive.model_eval.l0.roofline import evaluate

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
model = replace(
    build(config),
    name="Custom-Qwen3-2Block",
    source="in-memory custom Qwen3 config",
)
result = evaluate(model, get_profile("G0"), FORMATS["MXFP8"], decode(128))
print(result.mac, result.traffic_bytes, result.t_s)
PY
```

`build(config)` 不会把非空配置与默认 CONFIG 自动合并，因此示例显式使用 `{**CONFIG, ...}`。修改结构语义时不能只改尺寸；需要自定义 lowering 或直接 TaskGraph，见下一节。

## 如何新增一个自定义模型（结构与 Qwen 不同）

先按下表判断改动面，避免把“改尺寸”和“加算子”混在一起：

| 需求 | 需要注册的内容 |
|---|---|
| 只改尺寸、层数、词表等同构参数 | 不新增注册；用 `{**CONFIG, ...}` 构造 |
| 只用内置层重新组合（`generic.linear`、`generic.rms_norm`、`generic.lm_head`、`generic.sampling` 等） | 只写 `ModelPlan` + `register_model` |
| 新层类型，但算子形态已有（例如又是一次 norm 或 residual） | `register_layer` + `register_emitter`，复用现有 kind 的 cost |
| 新层类型 + 新算子形态（例如 GELU） | 再加 `register_operator_estimator` 和一条 `simple_cost` 公式 |
| 新引擎类型 | 还需 `common.opspec.register_engine_kind`、`l1.engines.register_estimator` 和对应拓扑实例 |

自定义模型涉及两套映射，互不替代：

1. **层类型 → emitter**：`register_emitter("acme.gelu", 1, fn)` 决定这一层展开成哪些 `LogicalOp` 和 `PrimitiveTask`。emitter 里写的 `LogicalOp.kind`（本例为 `"gelu"`）就是 `VpuSpec.kind`。
2. **算子形态 → cost**：`register_operator_estimator("gelu", "VPU", fn)` 把 `(spec kind, 引擎类型)` 映射到成本公式。只写公式、用 `simple_cost()` 构造最小单阶段 `EngineCost`；DRAM/local 字节只做记账，不重复增加阶段。

引擎归属只是标签：VPU 用 `ctx.operation(..., "VPU", kind)` + `builder.vector()`；GEMM 用 `builder.gemm()`；CPU 用 `builder.task(..., "CPU", CpuSpec(...))`；DMA 用 `builder.dma()`。新增算子时不需要改调度器。

以下示例新增一个 `acme.gelu_mlp` 聚合层、一个 `acme.gelu` 算子及其 cost 公式，其余层复用内置定义。保存为 `sim/model_eval/custom_model_example/acme_tiny.py`：

```python
"""最小自定义模型：一个聚合层 + 一个新算子及其 cost 公式。"""

from dataclasses import dataclass

from hive.model_eval.frontend.layers import (LayerDefinition, LayerGeometry,
                                             LayerInstance, LayerL0, WeightSpec,
                                             register_layer, sum_l0)
from hive.model_eval.frontend.layers.generic import (LinearGeometry,
                                                     RmsNormGeometry,
                                                     SamplingGeometry)
from hive.model_eval.frontend.plan import ModelPlan, WeightAllocation
from hive.model_eval.frontend.registry import register_model
from hive.model_eval.l1 import dram
from hive.model_eval.l1.costing.registry import (register_operator_estimator,
                                                 simple_cost)
from hive.model_eval.l1.emitters import emitter


@dataclass(frozen=True)
class GeluGeometry(LayerGeometry):
    width: int

    def validate(self) -> None:
        if self.width <= 0:
            raise ValueError("gelu width must be positive")


@dataclass(frozen=True)
class GeluMlpGeometry(LayerGeometry):
    hidden: int
    intermediate: int

    def validate(self) -> None:
        if self.hidden <= 0 or self.intermediate <= 0:
            raise ValueError("MLP dimensions must be positive")


def _gelu_l0(instance: LayerInstance) -> LayerL0:
    return LayerL0(mac_fixed=instance.geometry.width)


def _mlp_children(instance: LayerInstance) -> tuple[LayerInstance, ...]:
    geometry = instance.geometry
    prefix = instance.id
    return (
        LayerInstance(f"{prefix}/up_proj", "generic.linear",
                      LinearGeometry(geometry.hidden, geometry.intermediate),
                      weights=(WeightSpec("weight", (geometry.intermediate,
                                                     geometry.hidden)),),
                      op_name=f"{prefix}/up_proj"),
        LayerInstance(f"{prefix}/gelu", "acme.gelu",
                      GeluGeometry(geometry.intermediate),
                      op_name=f"{prefix}/gelu"),
        LayerInstance(f"{prefix}/down_proj", "generic.linear",
                      LinearGeometry(geometry.intermediate, geometry.hidden),
                      weights=(WeightSpec("weight", (geometry.hidden,
                                                     geometry.intermediate)),),
                      op_name=f"{prefix}/down_proj"),
    )


def _mlp_l0(instance: LayerInstance) -> LayerL0:
    return sum_l0(_mlp_children(instance))


register_layer(LayerDefinition(
    name="acme.gelu",
    version=1,
    geometry_type=GeluGeometry,
    input_roles=("hidden",),
    output_roles=("hidden",),
    weight_roles=(),
    l0=_gelu_l0,
    source=__file__,
))

register_layer(LayerDefinition(
    name="acme.gelu_mlp",
    version=1,
    geometry_type=GeluMlpGeometry,
    input_roles=("hidden",),
    output_roles=("hidden",),
    weight_roles=(),
    children=_mlp_children,
    l0=_mlp_l0,
    source=__file__,
))


@emitter("acme.gelu")
def emit_gelu(ctx, instance, inputs):
    rows = ctx.rows
    width = instance.geometry.width
    op = ctx.operation(instance, "VPU", "gelu",
                       mac=width * rows, elements=width * rows,
                       read_bytes=4 * width * rows, write_bytes=4 * width * rows,
                       evidence="one FP32 elementwise pass")
    return {"hidden": ctx.builder.vector(op, (inputs["hidden"],), (rows, width))}


@emitter("acme.gelu_mlp")
def emit_gelu_mlp(ctx, instance, inputs):
    hidden = inputs["hidden"]
    up = ctx.emit(ctx.child(instance, "up_proj"), {"x": hidden})["out"]
    gated = ctx.emit(ctx.child(instance, "gelu"), {"hidden": up})["hidden"]
    return {"hidden": ctx.emit(ctx.child(instance, "down_proj"), {"x": gated})["out"]}


def estimate_gelu(task, engine_config, execution_plan):
    spec, params = task.spec, engine_config.params
    cycles = (spec.elements + params.vpu_fp32_lanes - 1) // params.vpu_fp32_lanes
    seconds = dram.cycle_seconds(cycles, engine_config.profile)
    return simple_cost(
        task, engine_config, seconds,
        dram_read_bytes=spec.dram_read_bytes,
        dram_write_bytes=spec.dram_write_bytes,
        local_bytes=spec.read_bytes + spec.write_bytes,
        source="fallback",
        evidence=("gelu scenario: one FP32 elementwise pass at lane rate; "
                  "approx special-function service is not modelled"),
        metrics={"kind": spec.kind, "cycles": cycles},
    )


register_operator_estimator("gelu", "VPU", estimate_gelu)


def build(config: dict | None = None) -> ModelPlan:
    cfg = {"hidden_size": 64, "intermediate_size": 128, "vocab_size": 256}
    cfg.update(config or {})
    hidden = cfg["hidden_size"]
    intermediate = cfg["intermediate_size"]
    vocab = cfg["vocab_size"]
    return ModelPlan(
        name="Acme-Tiny",
        layers=(
            LayerInstance("block-0", "acme.gelu_mlp",
                          GeluMlpGeometry(hidden, intermediate)),
            LayerInstance("final-norm", "generic.rms_norm",
                          RmsNormGeometry(hidden, last_position_only=True),
                          weights=(WeightSpec("weight", (hidden,)),),
                          op_name="final-norm/norm"),
            LayerInstance("lm-head", "generic.lm_head",
                          LinearGeometry(hidden, vocab),
                          weights=(WeightSpec("weight", (vocab, hidden),
                                              allocation="embedding"),),
                          op_name="lm-head/projection"),
            LayerInstance("sampling", "generic.sampling",
                          SamplingGeometry(vocab), op_name="sampling/sampling"),
        ),
        allocations=(WeightAllocation("embedding", (vocab, hidden),
                                      checkpoint_copies=2),),
        source=__file__,
    )


register_model("Acme-Tiny", build, source=__file__)
```

`--extension` 接收模块名，因此要把文件所在目录加入 `PYTHONPATH`，并且 L0 与 L1 命令都要带同一参数：

```bash
PYTHONPATH=src:sim/model_eval/custom_model_example python3 -m hive.model_eval.l0.run \
  --extension acme_tiny --model Acme-Tiny --profile G0 --workload decode --seq 4

PYTHONPATH=src:sim/model_eval/custom_model_example python3 -m hive.model_eval.l1.run \
  --extension acme_tiny --model Acme-Tiny --profile G0 --workload decode --seq 4 \
  --attention vpu --sync-us 1 \
  --json sim/model_eval/custom_model_example/acme-l1.json
```

先确认两套映射都齐：

```bash
PYTHONPATH=src:sim/model_eval/custom_model_example python3 - <<'PY'
import acme_tiny
from hive.model_eval.frontend.registry import get_model
from hive.model_eval.l1.emitters import coverage

plan = get_model("Acme-Tiny")
plan.validate()
print(coverage(plan))  # {'L0': (), 'L1': ()}
PY
```

L1 报告应出现形态为 `gelu` 的独立算子行。该场景实测输出包含：

```text
覆盖：5/10 个任务已知；未知比例 50.0%；下界口径 lower_bound
block-0/gelu/execute   gelu  VPU  1.032 µs  1.032 µs  1.024 kB  scenario
```

其中 1 µs 来自显式 `--sync-us 1` 的跨引擎 handoff，算子本身为 16 cycles（128 elements / 8 lanes）@500 MHz ≈ 32 ns。不传 `--sync-us` 时该行时间保持“未知”，已有预算仍计入下界，不会折 0。

改动时逐项检查：

- 模型第一个顶层层的 geometry 必须暴露 `hidden` 或 `width`，`lower_plan` 用它创建根输入 `input:hidden`。首层若是纯 `generic.linear`，先用聚合层适配输入键（`linear` 用 `x`，根输入用 `hidden`）。
- 新 kind 若忘记 `register_operator_estimator`，L1 会报 `no feasible engine configuration: vpu0: unsupported VPU kind: gelu`；只注册 emitter 不够。
- 未注册 emitter 会报 `MissingL1Coverage`；`coverage(plan)` 的 `L1` 列表应保持为空。
- 公式缺少频率或速率时传 `None`，让报告列为未知；不要为凑出时间填 0 或猜测常数。
- 权重必须在 `WeightSpec` 中声明形状；共享权重用 `WeightAllocation` 并在 `checkpoint_copies` 说明重复份数。
- 聚合层的 L0 必须等于子层 L0 之和，且聚合层本身不拥有权重。
- 新增层/模型后补测试：`coverage(plan)`、L0/L1 结构锚、`--extension` CLI 路径，见 `tests/test_extension.py` 与 `tests/extension_demo.py`。

需要整体替换 lowering 而不是逐层注册时，继续看下一节。


## 如何构造并评估单个 GEMM 任务图

以下示例使用对齐的 M/N/K、external A/B 和 GEMM 产生的 C，从仓库根目录直接执行：

```bash
PYTHONPATH=src python3 - <<'PY'
from hive.model_eval.common.opspec import (
    GemmSpec, LogicalOp, PrimitiveTask, TaskGraph, Tensor, TensorRef,
)
from hive.model_eval.hw.geometry import load_array_geometry
from hive.model_eval.hw.profiles import get_profile
from hive.model_eval.hw.topology import default_topology
from hive.model_eval.l1.params import ModelParams
from hive.model_eval.l1.residency import BufferPlan, ExecutionPlan
from hive.model_eval.l1.timeline import estimate_graph, evaluate_graph

op = LogicalOp(
    "custom/q_proj", "custom", "q_proj", "weight_gemm",
    mac=1 * 2048 * 1024,
)
spec = GemmSpec(1, 2048, 1024, weight=True)
task = PrimitiveTask(
    op.id + "/execute", op.id, "GEMM", spec,
    inputs=(TensorRef("a"), TensorRef("b")),
    outputs=(TensorRef("c"),),
)
graph = TaskGraph((op,), (task,), (
    Tensor("a", (1, 1024), "MXFP8", external=True, scale_axis=1),
    Tensor("b", (1024, 2048), "MXFP8", external=True, scale_axis=0),
    Tensor("c", (1, 2048), "FP32"),
))
topology = default_topology(
    get_profile("G0"), load_array_geometry(), ModelParams(),
)
plan = ExecutionPlan(weight_policy="stream", activation_policy="stream")
costs = estimate_graph(graph, topology, plan)
cost = costs[task.id]["gemm0"]
assert cost.metrics["commands"] == 1
assert cost.metrics["windows"] == 4096
result = evaluate_graph(
    graph,
    topology,
    execution_plan=plan,
    buffer_plan=BufferPlan.from_graph(graph, plan),
)
print(cost.known_service_s, result.makespan_s, result.projection_makespan_s)
PY
```

非对齐 N/K 需要提供 padded backing 和相应 storage shape，并写满 backing；不能只修改逻辑 tensor shape。即使该对齐图得到数值 actual，GEMM 控制/排空等 excluded 仍存在，不能据此发布完整模型吞吐。

## 如何注入自定义 lowering

实现符合以下契约的回调：

```python
def lowering(model, fmt, point, *, attention, geometry, block_size):
    ...
    return graph
```

将回调传给 `timeline.evaluate(..., lowering=lowering)`（`model` 为 ModelPlan）。返回对象必须是 TaskGraph，并通过 ID、依赖、tensor、view、生产者和 stream owner 校验。

逐层扩展优先注册 emitter（见上一节），只有需要整体替换图时才用 lowering 回调。

自定义 lowering 负责算子分解、显式转换、padding backing、依赖和未知服务声明；scheduler 只负责固定图的实例选择与资源预约，不会补全缺失任务或证明模型数学等价。

## 如何排查未知时间、容量和格式错误

| 现象 | 检查位置 | 处理方式 |
|---|---|---|
| `unknown` / `null` | 任务 phases、excluded costs、blocked constraints、buffer 生命周期 | 保留未知；仅为有依据的服务添加显式场景参数 |
| actual 为 null、projection 有值 | 未知阶段列表和投影标识 | 仅将结果解释为已知预算下的条件预约 |
| actual 与 projection 都为 null | 容量、分配、候选实例、固定布局冲突 | 修正图、拓扑或执行计划，不把阻断当成零时间 |
| `instance_id=null` | `budget_instance_id`、`cost_basis`、候选实例 | 区分未绑定任务与已排程实例 |
| capacity failure | resident/stage/overlap footprint、SRAM profile、对齐和 region | 提供真实容量或修改显式计划；不静默降级 |
| unsupported format | `FORMATS`、`geometry.supported_fmts`、A/B family、C dtype | 注册并验证完整路径；不用 shape 推导服务 |
| private domain requires explicit DMA | tensor domain、DMA 两端和依赖 | 添加显式跨域副本，不跨核免费读取私有 SRAM |
| unordered overlapping writes | base/view 实际元素覆盖和生产者祖先 | 修正共享存储写入顺序或拆分独立 base |
| JSON/HTML 同路径 | L1 输出参数 | 使用两个不同路径；L0 也需自行避免同路径 |
| BF16 不可选 | `FORMATS` 注册表 | 当前 CLI 不支持 BF16；不要只依赖 BF16 常量存在 |

L1 参数错误通常由 argparse 以退出码 2 报告；自定义回调异常、缺文件和断言不保证统一包装。

## 如何修改代码并运行回归

按修改区域定位实现：

| 修改区域 | 文件 |
|---|---|
| 层语义与 L0 闭式 | `frontend/layers/`（`LayerDefinition.l0`） |
| 模型组装与注册 | `frontend/models/`、`frontend/plan.py`、`frontend/registry.py` |
| DAG、格式与视图契约 | `common/opspec.py`、`l1/graph_builder.py` |
| 图展开（lowering） | `l1/emitters/`、`l1/graph_builder.py` |
| 算子 cost 公式 | `l1/costing/forms/`、`l1/costing/registry.py` |
| GEMM 命令与窗口 | `l1/gemm_model.py`、`l1/costing/gemm_commands.py` |
| 资源与容量 | `hw/topology.py`、`l1/scheduler.py`、`l1/residency.py` |
| 报告 | `l1/timeline.py`、`web.py`、`resources.html`、`run.py` |

运行标准库回归：

```bash
PYTHONPATH=src python3 -m unittest discover -s src/hive/model_eval/tests -t src
```

修改网页时，还需生成真实报告并在浏览器验证工作点→层→算子→任务、串行/并行、未知/阻断、实例筛选、投影开关、窄屏、键盘、控制台、外部请求和 file 离线打开。测试通过不表示完成 RTL 校准。

新增模型或引擎服务后，同步更新 typed contract、来源、未知传播、独立结构锚、端到端测试和[接口参考](model_eval_spec.md)。
