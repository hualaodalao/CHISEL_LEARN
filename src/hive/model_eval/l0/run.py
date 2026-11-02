"""L0 CLI：`python3 -m hive.model_eval.l0.run --model ... --profile ... --workload ...`"""

from __future__ import annotations

import argparse
import json
import sys
from dataclasses import asdict
from pathlib import Path

from ..common import manifest as manifest_mod
from ..common.report import (
    Table,
    excluded_block,
    format_bytes,
    format_mac,
    format_time,
    header_block,
)
from ..common.workload import Phase, WorkloadPoint, decode, frame, prefill
from ..frontend.qwen3_06b import get_model
from ..frontend.spec import FORMATS
from ..hw.geometry import load_array_geometry
from ..hw.profiles import get_profile
from . import bottleneck, excluded
from .roofline import evaluate


def _points(workload: str, seqs: list[int]) -> list[WorkloadPoint]:
    if workload == "decode":
        return [decode(s) for s in seqs]
    if workload == "prefill":
        return [prefill(s) for s in seqs]
    return [frame()]


def _machine_balance(profile, mac_family: str) -> float:
    return profile.mac_per_second(mac_family) / profile.dram_effective_bytes_per_s


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="hive.model_eval.l0.run")
    parser.add_argument("--model", default="Qwen3-0.6B")
    parser.add_argument("--profile", default="G0")
    parser.add_argument("--workload", default="decode", choices=("decode", "prefill", "frame"))
    parser.add_argument("--seq", default="1", help="逗号分隔的上下文/prompt 长度")
    parser.add_argument("--fmt", default="MXFP8", choices=sorted(FORMATS))
    parser.add_argument(
        "--exact-bytes",
        action="store_true",
        help="用精确的 bytes_per_param（MXFP8 为 33/32）而非文档的 1.031；"
        "文档没给口径的格式本来就走精确值",
    )
    parser.add_argument("--json", type=Path, default=None, help="结果与 manifest 落盘路径")
    parser.add_argument("--html", type=Path, default=None, help="逐层资源可视化网页落盘路径")
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    seqs = [int(s) for s in args.seq.split(",") if s.strip() != ""]

    model = get_model(args.model)
    profile = get_profile(args.profile)
    fmt = FORMATS[args.fmt]
    geometry = load_array_geometry()
    points = _points(args.workload, seqs)

    if fmt.name not in _supported_fmt_names(geometry):
        print(
            f"warning: 格式 {fmt.name} 不在 gem5 冻结 profile 的 supportedFmts"
            f" {geometry.supported_fmts} 里，L0 仍可算但 L1 会拒绝",
            file=sys.stderr,
        )

    results = [
        evaluate(model, profile, fmt, point, use_doc_bytes=not args.exact_bytes)
        for point in points
    ]
    balance = _machine_balance(profile, fmt.mac_family)
    attributions = [bottleneck.attribute(r, balance) for r in results]

    man = manifest_mod.build(
        profile=profile,
        model_name=model.name,
        layer="L0",
        named_inputs={
            "dram_eta": profile.dram_eta,
            "fmt": fmt.name,
            "bytes_per_param": results[0].bytes_per_param,
            "bytes_per_param_source": fmt.bytes_per_param_source(
                use_doc_value=not args.exact_bytes
            ),
            "array_geometry": f"{geometry.profile_name}@{geometry.spec_version}",
            "tile_n": geometry.tile_n,
        },
        excluded_costs=excluded.for_workload(points[0]),
    )

    print(header_block(man))
    print()
    print(_render(results, attributions))
    print()
    print(_footnotes(model, results, fmt, args.exact_bytes))
    print()
    print(excluded_block(man))

    if args.json:
        args.json.parent.mkdir(parents=True, exist_ok=True)
        args.json.write_text(
            json.dumps(
                {
                    "manifest": man.to_dict(),
                    "results": [_result_dict(r, a) for r, a in zip(results, attributions)],
                },
                ensure_ascii=False,
                indent=2,
                sort_keys=True,
            ),
            encoding="utf-8",
        )
        print(f"\n已写入 {args.json}")
    if args.html:
        from .web import render_html, report_payload

        html = render_html(report_payload(model, profile, fmt, results, man))
        args.html.parent.mkdir(parents=True, exist_ok=True)
        args.html.write_text(html, encoding="utf-8")
        print(f"\n已写入 {args.html}")
    return 0


def _supported_fmt_names(geometry) -> set[str]:
    alias = {
        "MXE4M3": "MXFP8",
        "MXE5M2": "MXFP8",
        "MXE2M1": "MXFP4",
        "INT8": "INT8",
        "INT4": "INT4",
        "BF16": "BF16",
        "FP8E4M3": "FP8",
        "FP8E5M2": "FP8",
    }
    return {alias.get(name, name) for name in geometry.supported_fmts}


def _render(results, attributions) -> str:
    rows = []
    for r, a in zip(results, attributions):
        rows.append(
            (
                r.workload.label,
                format_mac(r.mac),
                format_bytes(r.traffic_bytes),
                f"{r.arithmetic_intensity:.3f}",
                format_time(r.t_bandwidth_s),
                format_time(r.t_compute_s),
                format_time(r.t_s),
                f"{r.bottleneck}×{a.bottleneck_ratio:.2f}",
                f"{r.throughput:.1f} {r.throughput_unit}",
            )
        )
    return Table(
        headers=(
            "工作点",
            "MAC",
            "流量",
            "AI",
            "t_bw",
            "t_mac",
            "t(上界)",
            "瓶颈",
            "吞吐上界",
        ),
        rows=rows,
    ).render()


def _footnotes(model, results, fmt, exact_bytes: bool) -> str:
    first = results[0]
    lines = [
        f"机器平衡点与 AI 的比较见瓶颈列；L0 给的是上界，L1 只会更慢。",
        f"KV 与权重流量追平的上下文长度 S ≈"
        f" {bottleneck.kv_weight_crossover(model, first.bytes_per_param):,.0f}",
    ]
    if fmt.doc_bytes_per_param is not None:
        exact = float(fmt.bytes_per_param_exact)
        doc = fmt.doc_bytes_per_param
        if abs(exact - doc) > 0:
            used = "精确值" if exact_bytes else "文档值"
            lines.append(
                f"bytes_per_param 当前用{used}：文档 {doc} vs 精确 {exact:.5f}"
                f"（相差 {abs(exact - doc) / doc:.4%}）"
            )
    for r in results:
        dominant = max(r.traffic.breakdown, key=lambda k: r.traffic.breakdown[k])
        lines.append(
            f"{r.workload.label}：流量主项 {dominant}"
            f" {r.traffic.share(dominant):.1%}，工作量分项 "
            + "、".join(f"{k} {r.work.share(k):.1%}" for k in sorted(r.work.breakdown))
        )
    return "\n".join(lines)


def _result_dict(result, attribution) -> dict:
    payload = {
        "model": result.model,
        "profile": result.profile,
        "fmt": result.fmt,
        "phase": result.workload.phase.value,
        "seq_len": result.workload.seq_len,
        "batch": result.workload.batch,
        "mac": result.mac,
        "mac_breakdown": result.work.breakdown,
        "traffic_bytes": result.traffic_bytes,
        "traffic_breakdown": result.traffic.breakdown,
        "arithmetic_intensity": result.arithmetic_intensity,
        "t_bandwidth_s": result.t_bandwidth_s,
        "t_compute_s": result.t_compute_s,
        "t_s": result.t_s,
        "throughput": result.throughput,
        "throughput_unit": result.throughput_unit,
        "bytes_per_param": result.bytes_per_param,
        "is_upper_bound": result.is_upper_bound,
        "attribution": asdict(attribution),
    }
    return payload


if __name__ == "__main__":
    raise SystemExit(main())
