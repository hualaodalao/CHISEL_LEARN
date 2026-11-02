from __future__ import annotations

import argparse
import json
from pathlib import Path

from ..common import manifest as manifest_mod
from ..common.report import Table, format_bytes, format_time, header_block
from ..common.workload import decode, prefill
from ..frontend.qwen3_06b import get_model
from ..frontend.spec import FORMATS
from ..hw.geometry import load_array_geometry, repo_root
from ..hw.profiles import get_profile
from .ledger import CATEGORIES
from .mapping import ATTENTION_VARIANTS
from .params import ModelParams
from .timeline import evaluate
from .web import render_html, report_payload


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="hive.model_eval.l1.run")
    parser.add_argument("--model", default="Qwen3-0.6B")
    parser.add_argument("--profile", default="G0")
    parser.add_argument("--workload", choices=("decode", "prefill"), default="decode")
    parser.add_argument("--seq", default="4096", help="逗号分隔的独立上下文/prompt 工作点")
    parser.add_argument("--batch", type=int, default=1)
    parser.add_argument("--fmt", choices=sorted(FORMATS), default="MXFP8")
    parser.add_argument("--attention", choices=(*ATTENTION_VARIANTS, "both"), default="both")
    parser.add_argument("--weight-policy", choices=("stream", "resident"), default="stream",
                        help="resident 仅在显式活跃区容量校验通过时驻留，否则流式")
    parser.add_argument("--block-size", type=int, choices=(16, 32), default=32)
    parser.add_argument("--sync-us", type=float, default=0)
    parser.add_argument("--exp-cycles-per-element", type=float)
    parser.add_argument("--reciprocal-cycles-per-vector", type=float)
    parser.add_argument("--rsqrt-cycles-per-vector", type=float)
    parser.add_argument("--cpu-cycles-per-comparison", type=float)
    parser.add_argument("--cpu-freq-hz", type=float)
    parser.add_argument("--overlap", action="store_true")
    parser.add_argument("--overlap-evidence", default="")
    parser.add_argument("--json", type=Path)
    parser.add_argument("--html", type=Path, help="导出离线逐层/逐算子网页，包含原始资源账本")
    parser.add_argument("--debug-ledger", action="store_true", help="JSON 保留原始账目；不改变结果")
    parser.add_argument("--layer-detail", action="store_true", help="终端展开逐层五类归因")
    return parser


def main(argv: list[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    if args.json and args.html and args.json.resolve() == args.html.resolve():
        parser.error("--json 与 --html 必须使用不同的输出路径")
    try:
        seqs = [int(value.strip()) for value in args.seq.split(",")]
        params = ModelParams(weight_policy=args.weight_policy, block_size=args.block_size,
                             sync_us=args.sync_us, exp_cycles_per_element=args.exp_cycles_per_element,
                             reciprocal_cycles_per_vector=args.reciprocal_cycles_per_vector,
                             rsqrt_cycles_per_vector=args.rsqrt_cycles_per_vector,
                             cpu_cycles_per_comparison=args.cpu_cycles_per_comparison,
                             cpu_freq_hz=args.cpu_freq_hz, overlap=args.overlap,
                             overlap_evidence=args.overlap_evidence)
        point_factory = decode if args.workload == "decode" else prefill
        points = [point_factory(seq, args.batch) for seq in seqs]
        model, profile, fmt = get_model(args.model), get_profile(args.profile), FORMATS[args.fmt]
        geometry = load_array_geometry()
        variants = ATTENTION_VARIANTS if args.attention == "both" else (args.attention,)
        results = [evaluate(model, profile, fmt, point, attention=variant,
                            params=params, geometry=geometry)
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
                   "output_format": "INT32" if fmt.name == "INT8" else "FP32",
                   "storage_schedule": "explicit per-command A staging, B reload, C spill; no inter-op cache",
                   "overlap_scope": "VPU arithmetic/local traffic and GEMM consume/C write only; loadW stays serial"})
    manifest = manifest_mod.build(profile=profile, model_name=model.name, layer="L1",
                                  named_inputs=inputs, excluded_costs=excluded)
    root = repo_root()
    source_paths = ["src/workerbee/doc/spec.md", "src/hive/model_eval/common/opspec.py",
                    "src/hive/model_eval/frontend/qwen3_06b.py",
                    "src/hive/model_eval/hw/geometry.py", "src/hive/model_eval/hw/profiles.py"]
    source_paths.extend(path.relative_to(root).as_posix() for path in sorted(Path(__file__).parent.glob("*.py")))
    if args.html:
        source_paths.append("src/hive/model_eval/l1/resources.html")
    manifest.sources.extend(manifest_mod._fingerprint(root, path) for path in source_paths)
    print(header_block(manifest))
    print("\n单核确定性解析时间线；不是周期仿真或可达端到端性能。未知成本未记入合计。")
    print("310 缺少时钟/端口信息时仅输出部分已知资源时间，不输出吞吐。")
    print("prefill 量化 GEMM attention 发射完整带 mask 矩阵；两变体的有效 MAC 均包含因果对角线，L0 仍为二次近似。\n")
    rows = []
    for result in results:
        metrics = result.metrics
        rows.append((result.workload.label, result.attention, str(metrics.get("commands", 0)),
                     str(metrics.get("windows", 0)), format_time(result.t_s),
                     format_time(result.l0_t_s),
                     f"{result.modeled_throughput:.2f}" if result.modeled_throughput is not None else "未知",
                     format_bytes(metrics.get("weight_reload_bytes", 0))))
    print(Table(("工作点", "attention", "命令", "窗口", "已建模时间", "L0 下界",
                 "已建模 tok/s*", "权重重装额外量"), rows).render())
    print("\n* 吞吐仅按已计入成本计算，不是性能承诺；单 token LM head，prefill prompt tokens/s 非生成速度。")
    for result in results:
        print(f"\n{result.workload.label} / {result.attention} 五类归因")
        print(Table(("资源", "已计入时间"), [(category, format_time(result.attribution_s[category]))
                                             for category in CATEGORIES]).render())
        if args.layer_detail:
            payload = result.to_dict()
            print(Table(("模型层", "算子数", "已建模时间", *CATEGORIES),
                        [(layer["layer"], str(layer["operators"]), format_time(layer["t_s"]),
                          *(format_time(layer["attribution_s"][category]) for category in CATEGORIES))
                         for layer in payload["layers"]]).render())
    print(f"\n未建模项共 {len(excluded)} 项（含逐算子特殊函数）；完整清单保留在 JSON manifest。")
    for item in excluded:
        if not item.startswith(("block-", "final-norm/")):
            print(f"  - {item}")
    if args.json:
        payload = {"manifest": manifest.to_dict(), "manifest_digest": manifest.digest,
                   "results": [result.to_dict(args.debug_ledger) for result in results]}
        args.json.parent.mkdir(parents=True, exist_ok=True)
        args.json.write_text(json.dumps(payload, ensure_ascii=False, indent=2,
                                       sort_keys=True, allow_nan=False), encoding="utf-8")
        print(f"\n已写入 {args.json}")
    if args.html:
        args.html.parent.mkdir(parents=True, exist_ok=True)
        args.html.write_text(render_html(report_payload(profile, results, manifest)), encoding="utf-8")
        print(f"\n已写入离线 L1 网页 {args.html}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
