"""Run one loose-timing GEMM job per shape and aggregate the results.

Each shape is a separate gem5 process with its own output directory, because the
model exits the simulation when its trace drains. Every row is marked
data_valid=false and performance_valid=false: the numbers describe structure
(window barriers, DMA gaps, drain), not sign-off performance.
"""

import argparse
import json
from pathlib import Path
import shutil
import subprocess
import sys
import time

MODULE_ROOT = Path(__file__).resolve().parents[1]
REPO_ROOT = MODULE_ROOT.parents[2]
GEMM_SCRIPT = MODULE_ROOT / "configs/gemm.py"
sys.path.insert(0, str(MODULE_ROOT / "tools"))
import command_trace as wbtrace

PRESETS = {
    "int8": {
        "m": (16, 32, 48),
        "n": (16, 64, 128),
        "k": (64, 256),
    },
    "mx8": {
        "m": (16, 32),
        "n": (16, 64),
        "k": (64, 128),
    },
}


class ArgumentParser(argparse.ArgumentParser):
    def error(self, message):
        raise ValueError(message)


def parse_shapes(values):
    shapes = []
    for value in values:
        parts = value.lower().split("x")
        if len(parts) != 3:
            raise ValueError(f"shape must be MxNxK: {value}")
        shapes.append(tuple(int(part) for part in parts))
    return shapes


def preset_shapes(name):
    grid = PRESETS[name]
    return [(m, n, k) for m in grid["m"] for n in grid["n"] for k in grid["k"]]


def arguments(argv=None):
    parser = ArgumentParser(description=__doc__)
    parser.add_argument("--gem5", type=Path, required=True, help="gem5.opt binary")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--preset", default="int8", choices=sorted(PRESETS))
    parser.add_argument("--shape", action="append", default=[],
                        help="explicit MxNxK; repeatable, overrides the preset")
    parser.add_argument("--a-fmt", default="INT8")
    parser.add_argument("--b-fmt", default="INT8")
    parser.add_argument("--c-fmt", default="INT32")
    parser.add_argument("--load-w", type=int, default=0)
    parser.add_argument("--blk-a", type=int, default=16)
    parser.add_argument("--blk-b", type=int, default=16)
    parser.add_argument("--blk-c", type=int, default=16)
    parser.add_argument("--clock", default="500MHz")
    parser.add_argument("--memory-latency", default="1ns")
    parser.add_argument("--array-latency", type=int, default=31)
    parser.add_argument("--dma-outstanding", type=int, default=4)
    parser.add_argument("--shared-bus", action="store_true")
    parser.add_argument("--keep-runs", action="store_true")
    args = parser.parse_args(argv)
    args.shapes = parse_shapes(args.shape) if args.shape else preset_shapes(args.preset)
    if not args.shapes:
        raise ValueError("no shapes selected")
    return args


def run_shape(args, shape):
    m, n, k = shape
    tag = f"{args.a_fmt}_{m}x{n}x{k}"
    directory = args.output / "runs" / tag
    if directory.exists():
        shutil.rmtree(directory)
    directory.parent.mkdir(parents=True, exist_ok=True)
    command = [
        str(args.gem5.resolve()), "-d", str(directory), str(GEMM_SCRIPT),
        "--shape", f"{m}x{n}x{k}",
        "--a-fmt", args.a_fmt, "--b-fmt", args.b_fmt, "--c-fmt", args.c_fmt,
        "--load-w", str(args.load_w),
        "--blk-a", str(args.blk_a), "--blk-b", str(args.blk_b),
        "--blk-c", str(args.blk_c),
        "--clock", args.clock, "--memory-latency", args.memory_latency,
        "--array-latency", str(args.array_latency),
        "--dma-outstanding", str(args.dma_outstanding),
        "--task-id", tag,
    ]
    if args.shared_bus:
        command.append("--shared-bus")
    started = time.perf_counter()
    completed = subprocess.run(command, capture_output=True, text=True, check=False)
    wall = time.perf_counter() - started
    row = {"task_id": tag, "m": m, "n": n, "k": k, "wall_seconds": round(wall, 3),
           "returncode": completed.returncode, "ok": False}
    result_path = directory / "result.json"
    if result_path.is_file():
        result = json.loads(result_path.read_text())
        model = result.get("model") or {}
        gemm = model.get("gemm") or {}
        geo = result.get("expected_geometry") or {}
        row.update({
            "ok": not result.get("timed_out") and not result.get("errors"),
            "gemm_cycles": result.get("gemm_cycles"),
            "macs_per_cycle": result.get("macs_per_cycle"),
            "utilization": result.get("utilization"),
            "physical_dma_bytes": result.get("physical_dma_bytes"),
            "dma_bytes_per_cycle": result.get("dma_bytes_per_cycle"),
            "array_busy_cycles": gemm.get("array_busy_cycles"),
            "stall_a_cycles": gemm.get("stall_a_cycles"),
            "stall_b_cycles": gemm.get("stall_b_cycles"),
            "timed_out": result.get("timed_out"),
            "structural": structural_cycles(geo, args.array_latency, m),
            "structural_overhead": None,
            "errors": result.get("errors", []),
        })
        if row["gemm_cycles"] and row["structural"]:
            row["structural_overhead"] = row["gemm_cycles"] / row["structural"]
    else:
        row["errors"] = [{"kind": "harness", "code": "missing_result",
                          "message": completed.stderr.strip()[-800:] or "no stderr"}]
    if not args.keep_runs and row["ok"]:
        shutil.rmtree(directory)
    return row


def structural_cycles(geo, array_latency, m):
    """Cycles the coarse structure alone predicts: windows, latency, C tail.

    One cycle per A beat and per B beat inside a window, so a window costs
    T + M when DMA never stalls. Only the last window's C beats are exposed:
    earlier windows write back while the next window loads.
    """
    try:
        windows = geo["n_tiles"] * geo["k_tiles"]
        window_cycles = windows * (geo["t"] + m)
        tail = geo["c_beats"] // max(geo["n_tiles"], 1)
        return window_cycles + array_latency + tail
    except (KeyError, TypeError):
        return None


def markdown(report):
    lines = [
        "# WorkerBee loose timing GEMM scan",
        "",
        "`model_kind=timing_loose`, `data_valid=false`, `performance_valid=false`.",
        "`cycles` and `structural` are deterministic; `wall` is host time.",
        "",
        "| task | M | N | K | cycles | structural | cycles/structural | MAC/cycle | utilization | DMA B/cycle | stall B |",
        "|---|---|---|---|---|---|---|---|---|---|---|",
    ]
    for row in report["rows"]:
        if not row.get("ok"):
            lines.append(f"| {row['task_id']} | {row['m']} | {row['n']} | {row['k']} "
                         f"| failed | | | | | | |")
            continue
        overhead = (f"{row['structural_overhead']:.2f}"
                    if row.get("structural_overhead") else "-")
        lines.append(
            f"| {row['task_id']} | {row['m']} | {row['n']} | {row['k']} "
            f"| {row['gemm_cycles']} | {row['structural']} "
            f"| {overhead} "
            f"| {row['macs_per_cycle']:.1f} | {row['utilization']:.3f} "
            f"| {row['dma_bytes_per_cycle']:.2f} | {row['stall_b_cycles']} |")
    return "\n".join(lines) + "\n"


def main(argv=None):
    try:
        args = arguments(argv)
        if not args.gem5.is_file():
            raise ValueError(f"gem5 binary not found: {args.gem5}")
        args.output.mkdir(parents=True, exist_ok=True)
        rows = [run_shape(args, shape) for shape in args.shapes]
        ok = [row for row in rows if row.get("ok")]
        scored = [row for row in ok if row.get("utilization") is not None]
        best = max(scored, key=lambda row: row["utilization"]) if scored else None
        report = {
            "schema_version": 1, "phase": "L1", "run_kind": "gemm_shape_scan",
            "model_kind": "timing_loose", "data_valid": False,
            "performance_valid": False, "p0_accepted": False,
            "config": {key: getattr(args, key) for key in (
                "preset", "a_fmt", "b_fmt", "c_fmt", "load_w", "blk_a", "blk_b",
                "blk_c", "clock", "memory_latency", "array_latency",
                "dma_outstanding", "shared_bus")},
            "gem5": str(args.gem5.resolve()),
            "rows": rows,
            "summary": {
                "shapes": len(rows), "ok": len(ok), "failed": len(rows) - len(ok),
                "best_utilization": best["utilization"] if best else None,
                "best_task": best["task_id"] if best else None,
            },
        }
        (args.output / "scan.json").write_text(
            json.dumps(report, indent=2, sort_keys=True) + "\n")
        (args.output / "scan.md").write_text(markdown(report))
        print(json.dumps(report["summary"], sort_keys=True))
        return 0 if len(ok) == len(rows) else 1
    except Exception as exc:
        error = {"kind": "input" if isinstance(exc, ValueError) else "environment",
                 "code": "scan_failed", "message": str(exc),
                 "cycle": None, "task_id": None, "port": None}
        sys.stderr.write(json.dumps({"error": error}, sort_keys=True) + "\n")
        return 1


if __name__ == "__main__":
    sys.exit(main())
