# model_eval 入门教程：完成一次 L0/L1 评估

本教程使用固定输入完成一次可重复的评估：Qwen3-0.6B、G0、MXFP8、单 batch、单 active core、VPU attention、serial 调度。完成后将得到一个 L0 roofline 结果，以及包含任务图、资源预算和条件投影的 L1 JSON/HTML 报告。

本教程只提供一条操作路径。字段、公式和完整参数见[建模规范与接口参考](model_eval_spec.md)；其他场景见[操作指南](model_eval_howto.md)；模型拆解和代码调用关系见[Qwen3-0.6B 端到端代码导读](model_eval_end2end.md)。

## 1. 准备运行环境

安装 Python 3.10 或更新版本。核心评估和报告生成只使用 Python 标准库，不需要安装 PyTorch、gem5、Verilator 或 sbt 依赖。

在仓库根目录确认源码包可用：

```bash
python3 --version
ls src/hive/model_eval
```

后续命令均在仓库根目录执行。`PYTHONPATH=src` 让 Python 从当前源码树导入 `hive.model_eval`。

## 2. 运行 L0 roofline 评估

执行一个 decode 工作点，参考历史 KV 长度为 4096：

```bash
PYTHONPATH=src python3 -m hive.model_eval.l0.run \
  --model Qwen3-0.6B \
  --profile G0 \
  --workload decode \
  --seq 4096 \
  --fmt MXFP8
```

终端输出总 MAC、总流量、带宽时间、计算时间、理想时间下界和瓶颈。

其中 `t_s=max(t_bandwidth,t_compute)`，不是实测延迟；`throughput` 是相应上界。L0 只将全模型总量代入 roofline，不描述 tile、任务依赖、SRAM 生命周期或未知服务。公式见[规范第 4 章](model_eval_spec.md#4-l0-评估规则)。

## 3. 运行 L1 任务图评估

执行一个空历史 KV 的 decode 工作点，并保存 JSON 与离线 HTML：

```bash
PYTHONPATH=src python3 -m hive.model_eval.l1.run \
  --model Qwen3-0.6B \
  --profile G0 \
  --workload decode \
  --seq 0 \
  --batch 1 \
  --fmt MXFP8 \
  --attention vpu \
  --schedule serial \
  --json sim/model_eval/tutorial-l1.json \
  --html sim/model_eval/tutorial-l1.html
```

该命令展开 28 个 decoder block，并生成 GEMM、VPU、CPU、DMA 原子任务及依赖。它不会加载模型权重，也不会生成 token。

命令成功后应存在两个文件：

```bash
ls sim/model_eval/tutorial-l1.json sim/model_eval/tutorial-l1.html
```

报告中出现未知时间、`null` 和未覆盖成本属于正常结果。默认模型包含尚未定价的转换、特殊函数、同步或路由服务，这些项不会被当成零。

## 4. 读取 L1 HTML 报告

直接用浏览器打开 `sim/model_eval/tutorial-l1.html`。HTML 内嵌数据，不需要 HTTP 服务或网络。

按以下路径检查报告：

1. 在顶部确认模型、profile、格式、decode、`S=0`、batch、attention 路径和调度策略。
2. 先看 `00 / 算子 cost 与下界`：覆盖任务数、综合下界、Top unknown、算子 cost 表。下界不是可达 makespan，unknown 未折 0。
3. 区分“完整 makespan”“已知服务预算”“串行已知和/条件投影”三个指标。
4. 在层列表选择一个 block，再选择一个逻辑算子，查看其原子任务。
5. 在任务详情中检查实例、shape/dtype、tensor、依赖、阶段、等待和未覆盖服务。
6. 在资源与内存区域检查 DRAM/local/engine busy、buffer 生命周期、流量和内存峰值。
7. 展开 manifest、来源 SHA-256 和 excluded costs，确认结果仍标记为未校准。

斜纹时间条表示条件投影，不表示实际硬件并行或实测时间。三种时间的精确定义见[规范第 5.6 节](model_eval_spec.md#56-调度actual-与条件投影)。

## 5. 核对 JSON 结果

用 Python 读取报告的关键字段：

```bash
python3 - <<'PY'
import json
from pathlib import Path

report = json.loads(Path("sim/model_eval/tutorial-l1.json").read_text())
assert report["schema_version"] == 2
assert len(report["results"]) == 1
result = report["results"][0]
for key in (
    "known_service_budget_s",
    "makespan_s",
    "projection_makespan_s",
):
    print(f"{key}: {result[key]}")
print(f"coverage: {result['coverage']}")
print(f"bounds: {result['bounds']}")
print(f"operator_costs: {len(result['operator_costs'])} rows")
print(f"top_unknowns: {len(result['top_unknowns'])} causes")
print(f"excluded_costs: {len(result['excluded_costs'])} items")
PY
```

`known_service_budget_s` 是任务局部已知预算之和；`makespan_s` 是 actual 排程跨度；`projection_makespan_s` 是忽略未知服务后的条件投影。三者不可互相替代。`bounds.lower_bound_s` 是 `max(最忙单引擎, DRAM 流量, DAG 关键路径)` 的保守下界，标注 `achievability="lower_bound"`；`coverage` 给出已知/未知任务数；`operator_costs` 是每个算子实例的只读 cost 记录；`top_unknowns` 是按根因聚合的未覆盖项。`excluded_costs` 的完整清单保留在 JSON 和 HTML 中，此处只显示条目数。

## 6. 已完成的评估

现在已经完成：

- 一次全模型 L0 DRAM/计算 roofline 粗筛；
- 一次完整 Qwen3-0.6B L1 任务图与资源评估；
- 一份可查询原始字段的 JSON 报告；
- 一份可离线浏览层、算子、任务、资源和来源的 HTML 报告。

这些结果仍是未经 RTL 校准的工程估算，不构成完整模型实测吞吐。继续比较 prefill、多实例或驻留策略时，使用[操作指南](model_eval_howto.md)；理解 L0/L1 分工和未知时间的设计原因时，阅读[设计说明](model_eval_explanation.md)。
