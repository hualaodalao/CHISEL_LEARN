from __future__ import annotations

import argparse
import importlib
import json
from pathlib import Path

from ..common import manifest as manifest_mod
from ..common.report import Table, format_bytes, format_time, header_block
from ..common.workload import decode, prefill
from ..frontend import builtins as _builtins  # noqa: F401  (registers built-in models)
from ..frontend.plan import ModelPlan
from ..frontend.registry import get_model
from ..frontend.spec import FORMATS
from ..hw.geometry import load_array_geometry, repo_root
from ..hw.profiles import get_profile
from ..hw.topology import explicit_topology
from .ledger import CATEGORIES
from .mapping import ATTENTION_VARIANTS
from .params import ModelParams
from .residency import ExecutionPlan
from .timeline import evaluate
from .web import render_html, report_payload


def _time(value) -> str:
    return "未知" if value is None else format_time(value)


def _max_engine(bounds):
    values = bounds.get("engine_busy_lb_s", {})
    if not values:
        return None, None
    name = max(values, key=values.get)
    return name, values[name]


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="hive.model_eval.l1.run")
    parser.add_argument("--model", default="Qwen3-0.6B")
    parser.add_argument("--extension", action="append", default=[],
                        help="显式导入的扩展模块，可重复；不会自动扫描目录")
    parser.add_argument("--profile", default="G0")
    parser.add_argument("--workload", choices=("decode", "prefill"), default="decode")
    parser.add_argument("--seq", default="4096", help="逗号分隔的独立工作点")
    parser.add_argument("--batch", type=int, default=1)
    parser.add_argument("--fmt", choices=sorted(FORMATS), default="MXFP8")
    parser.add_argument("--attention", choices=(*ATTENTION_VARIANTS, "both"), default="both")
    parser.add_argument("--weight-policy", choices=("stream", "resident"), default="stream",
                        help="显式驻留超容量时失败，不自动改为 stream")
    parser.add_argument("--activation-policy", choices=("stream", "stage"), default="stream")
    parser.add_argument("--block-size", type=int, choices=(16, 32), default=32)
    parser.add_argument("--sync-us", type=float, help="依赖边交接情景；省略为未知，显式 0 为零成本情景")
    parser.add_argument("--exp-cycles-per-element", type=float)
    parser.add_argument("--reciprocal-cycles-per-vector", type=float)
    parser.add_argument("--rsqrt-cycles-per-vector", type=float)
    parser.add_argument("--conversion-elements-per-cycle", type=int)
    parser.add_argument("--accum-elements-per-cycle", type=int)
    parser.add_argument("--cpu-cycles-per-comparison", type=float)
    parser.add_argument("--cpu-freq-hz", type=float)
    parser.add_argument("--overlap", action="store_true")
    parser.add_argument("--overlap-evidence", default="")
    parser.add_argument("--schedule", choices=("serial", "list"), default="serial")
    for kind in ("gemm", "vpu", "cpu", "dma"):
        parser.add_argument(f"--{kind}-instances", type=int, default=1)
    parser.add_argument("--active-cores", type=int, default=1)
    parser.add_argument("--topology-evidence", default="")
    parser.add_argument("--json", type=Path)
    parser.add_argument("--html", type=Path)
    parser.add_argument("--debug-ledger", action="store_true")
    parser.add_argument("--layer-detail", action="store_true")
    parser.add_argument("--detailed", action="store_true",
                        help="打印阶段/等待/分层明细；默认只显示算子 cost、下界和覆盖")
    parser.add_argument("--top-unknowns", type=int, default=5,
                        help="默认报告显示的未知成本根因数量")
    parser.add_argument("--top-ops", type=int, default=8,
                        help="默认报告显示的算子 cost 行数")
    return parser


def main(argv: list[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    for module in args.extension:
        try:
            importlib.import_module(module)
        except ImportError as error:
            parser.error(f"无法导入扩展 {module!r}: {error}")
    if args.json and args.html and args.json.resolve() == args.html.resolve():
        parser.error("--json 与 --html 必须使用不同的输出路径")
    if args.top_unknowns < 0 or args.top_ops < 0:
        parser.error("--top-unknowns/--top-ops 不能为负数")
    try:
        seqs = [int(value.strip()) for value in args.seq.split(",")]
        params = ModelParams(weight_policy=args.weight_policy, block_size=args.block_size,
                             sync_us=args.sync_us, exp_cycles_per_element=args.exp_cycles_per_element,
                             reciprocal_cycles_per_vector=args.reciprocal_cycles_per_vector,
                             rsqrt_cycles_per_vector=args.rsqrt_cycles_per_vector,
                             vpu_conversion_elements_per_cycle=args.conversion_elements_per_cycle,
                             vpu_fp32_accum_elements_per_cycle=args.accum_elements_per_cycle,
                             cpu_cycles_per_comparison=args.cpu_cycles_per_comparison,
                             cpu_freq_hz=args.cpu_freq_hz, overlap=args.overlap,
                             overlap_evidence=args.overlap_evidence)
        point_factory = decode if args.workload == "decode" else prefill
        points = [point_factory(seq, args.batch) for seq in seqs]
        model, profile, fmt = get_model(args.model), get_profile(args.profile), FORMATS[args.fmt]
        geometry = load_array_geometry()
        topology = explicit_topology(profile, geometry, params, active_cores=args.active_cores,
            gemm_instances=args.gemm_instances, vpu_instances=args.vpu_instances,
            cpu_instances=args.cpu_instances, dma_instances=args.dma_instances,
            evidence=args.topology_evidence or ("single active core" if args.active_cores == 1 and
                all(getattr(args, f"{kind}_instances") == 1 for kind in ("gemm", "vpu", "cpu", "dma")) else ""))
        plan = ExecutionPlan(args.weight_policy, args.activation_policy)
        variants = ATTENTION_VARIANTS if args.attention == "both" else (args.attention,)
        results = [evaluate(model, profile, fmt, point, attention=variant, params=params,
                            geometry=geometry, topology=topology, execution_plan=plan,
                            scheduling_policy=args.schedule)
                   for point in points for variant in variants]
    except (ValueError, KeyError) as error:
        parser.error(str(error))
    excluded = sorted({item for result in results for item in result.excluded_costs})
    inputs = params.named_inputs()
    inputs.update({"dram_eta": profile.dram_eta, "fmt": fmt.name,
                   "attention_variants": list(variants), "workload": args.workload,
                   "seq_lengths": seqs, "batch": args.batch,
                   "array_geometry": f"{geometry.profile_name}@{geometry.spec_version}",
                   "max_m_per_command": geometry.max_m,
                   "output_format": "INT32" if fmt.name.startswith("INT") else "FP32",
                   "topology": topology.to_dict(), "execution_plan": plan.to_dict(),
                   "scheduling_policy": args.schedule,
                   "lowering": ("ModelPlan emitter composition" if isinstance(model, ModelPlan)
                                else "Qwen tensor producer/consumer graph; configurable Python lowering API"),
                   "projection": "conditional known-cost projection; not evidence of actual overlap"})
    provenance = ["src/workerbee/doc/spec.md", "src/hive/model_eval/common/opspec.py",
                  "src/hive/model_eval/hw/geometry.py", "src/hive/model_eval/hw/profiles.py",
                  "src/hive/model_eval/hw/topology.py"]
    if isinstance(model, ModelPlan):
        provenance.extend(model.provenance_paths())
    else:
        provenance.extend((model.source, "src/hive/model_eval/frontend/qwen3_06b.py"))
    provenance.extend(path.relative_to(repo_root()).as_posix()
                      for path in sorted(Path(__file__).parent.glob("*.py")))
    provenance.extend(path.relative_to(repo_root()).as_posix()
                      for path in sorted(Path(__file__).parent.joinpath("emitters").glob("*.py")))
    if args.html:
        provenance.append("src/hive/model_eval/l1/resources.html")
    manifest = manifest_mod.build(profile=profile, model_name=model.name, layer="L1",
                                  named_inputs=inputs, excluded_costs=excluded,
                                  provenance=provenance)
    print(header_block(manifest))
    print("\n任务图 / 实例 / 资源预约的解析排程；不是周期仿真或可达性能。")
    print("未知阶段阻断确定 makespan 与完整吞吐；投影忽略未知服务，仅用于查看已知预算。")
    print("L0 保持原口径参考，不以 max(L1,L0) 修补时间。\n")
    rows = []
    summaries = []
    for result in results:
        metrics = result.metrics
        summary = result.to_dict(detailed=False)
        summaries.append(summary)
        rows.append((result.workload.label, result.attention, str(metrics.get("commands", 0)),
                     _time(result.makespan_s), _time(result.known_service_budget_s),
                     _time(result.projection_makespan_s), _time(result.l0_t_s),
                     format_bytes(metrics.get("weight_reload_bytes", 0))))
    print(Table(("工作点", "attention", "命令", "makespan", "已知服务预算", "串行已知和", "L0 参考", "权重重装"), rows).render())
    for result, summary in zip(results, summaries):
        bounds, coverage = summary["bounds"], summary["coverage"]
        print(f"\n{result.workload.label} / {result.attention} 下界（不是可达 makespan；unknown 未折 0）")
        engine, busy = _max_engine(bounds)
        print(Table(("下界项", "值"), [
            (f"最忙引擎 {engine}" if engine else "最忙引擎", _time(busy)),
            ("DRAM 流量下界", _time(bounds["dram_lb_s"])),
            ("DAG 关键路径", _time(bounds["critical_path_lb_s"])),
            ("综合下界", _time(bounds["lower_bound_s"])),
        ]).render())
        print(f"覆盖：{coverage['tasks_known']}/{coverage['tasks_total']} 个任务已知；"
              f"未知比例 {coverage['unknown_ratio']:.1%}；"
              f"下界口径 {bounds['achievability']}")
        unknowns = summary["top_unknowns"][:args.top_unknowns]
        if unknowns:
            print("Top unknown：")
            print(Table(("根因", "类别", "任务数"),
                        [(item["cause"][:100], item["category"], str(item["tasks"]))
                         for item in unknowns]).render())
        operators = sorted(summary["operator_costs"],
                           key=lambda row: (row["known_seconds"], row["task_id"]),
                           reverse=True)[:args.top_ops]
        if operators:
            print("算子 cost（按已知预算排序）：")
            print(Table(("算子", "形态", "引擎", "时间", "已知预算", "DRAM 字节", "basis"), [
                (row["task_id"], row["form"], row["engine_kind"], _time(row["seconds"]),
                 _time(row["known_seconds"]),
                 format_bytes(row["dram_read_bytes"] + row["dram_write_bytes"]), row["basis"])
                for row in operators]).render())
        print(f"\n{result.workload.label} / {result.attention} 串行已知和五类归因（不是资源需求之和）")
        attribution = result.projection_attribution_s or {}
        print(Table(("资源", "串行已知和"), [(category, _time(attribution.get(category)))
                                             for category in CATEGORIES]).render())
        if args.layer_detail or args.detailed:
            print(Table(("模型层", "算子", "原子任务", "已知服务预算"),
                        [(layer["layer"], str(layer["operators"]), str(layer["tasks"]),
                          format_time(layer["known_service_budget_s"]))
                         for layer in result.to_dict()["layers"]]).render())
    print(f"\n未覆盖成本 {len(excluded)} 项；完整清单、实例、等待、流量和峰值在报告中。")
    if args.json:
        payload = {"schema_version": 2, "manifest": manifest.to_dict(), "manifest_digest": manifest.digest,
                   "results": [result.to_dict(args.debug_ledger) for result in results]}
        args.json.parent.mkdir(parents=True, exist_ok=True)
        args.json.write_text(json.dumps(payload, ensure_ascii=False, indent=2,
                                       sort_keys=True, allow_nan=False), encoding="utf-8")
        print(f"已写入 {args.json}")
    if args.html:
        args.html.parent.mkdir(parents=True, exist_ok=True)
        args.html.write_text(render_html(report_payload(profile, results, manifest)), encoding="utf-8")
        print(f"已写入 {args.html}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
