"""Single-job WorkerBee GEMM run for the gem5 L1 timing model.

The model is coarse-grained: one cycle per A beat and per B beat, real DMA
addresses and byte counts, but no per-cycle wavefront, skew or output-converter
detail. Every result is marked data_valid=false and performance_valid=false.
"""

import argparse
import hashlib
import json
from pathlib import Path
import platform
import sys

MODULE_ROOT = Path(__file__).resolve().parents[1]
REPO_ROOT = MODULE_ROOT.parents[2]
sys.path.insert(0, str(MODULE_ROOT / "configs"))
sys.path.insert(0, str(MODULE_ROOT / "tools"))

from hardware import DEFAULT_PROFILE, DMA_PORTS, GEM5_COMMIT, GEM5_RELEASE
from baseline import snapshot
import command_trace as wbtrace


class ArgumentParser(argparse.ArgumentParser):
    def error(self, message):
        raise ValueError(message)


def parse_shape(text):
    parts = text.lower().split("x")
    if len(parts) != 3:
        raise ValueError(f"shape must be MxNxK: {text}")
    try:
        return tuple(int(part) for part in parts)
    except ValueError:
        raise ValueError(f"shape must be MxNxK with integer fields: {text}")


def arguments(argv=None):
    parser = ArgumentParser(description=__doc__)
    parser.add_argument("--shape", required=True, help="MxNxK")
    parser.add_argument("--a-fmt", default="INT8")
    parser.add_argument("--b-fmt", default="INT8")
    parser.add_argument("--c-fmt", default="INT32")
    parser.add_argument("--load-w", type=int, default=0)
    parser.add_argument("--blk-a", type=int, default=16)
    parser.add_argument("--blk-b", type=int, default=16)
    parser.add_argument("--blk-c", type=int, default=16)
    parser.add_argument("--bias", action="store_true")
    parser.add_argument("--requant", action="store_true")
    parser.add_argument("--relu", action="store_true")
    parser.add_argument("--task-id", default="gemm0")
    parser.add_argument("--clock", default="500MHz")
    parser.add_argument("--memory-latency", default="1ns")
    parser.add_argument("--memory-bandwidth", default="64GiB/s")
    parser.add_argument("--array-latency", type=int, default=31)
    parser.add_argument("--dma-outstanding", type=int, default=4)
    parser.add_argument("--max-cycles", type=int, default=20_000_000)
    parser.add_argument("--trace-jsonl", type=Path)
    parser.add_argument("--shared-bus", action="store_true")
    args = parser.parse_args(argv)
    if args.array_latency < 0 or args.dma_outstanding <= 0:
        raise ValueError("array-latency must be >= 0 and dma-outstanding > 0")
    if args.max_cycles <= 0:
        raise ValueError("max-cycles must be positive")
    return args


def git(root, *args):
    import subprocess
    return subprocess.run(["git", "-C", str(root), *args], text=True,
                          capture_output=True, check=True, timeout=10).stdout.strip()


def main(argv=None):
    try:
        args = arguments(argv)
        m, n, k = parse_shape(args.shape)
        job = wbtrace.Job(m=m, n=n, k=k, a_fmt=args.a_fmt, b_fmt=args.b_fmt,
                          c_fmt=args.c_fmt, load_w=args.load_w,
                          blk_a=args.blk_a, blk_b=args.blk_b, blk_c=args.blk_c,
                          bias=args.bias, requant=args.requant,
                          relu=args.relu)
        wbtrace.validate(job)
        if args.shared_bus:
            addresses, span = wbtrace.layout(job)
            bank = None
        else:
            addresses, span, bank = wbtrace.banked_layout(job)
        job = wbtrace.Job(**{**job.__dict__, **addresses})
        batches = wbtrace.program(job)
        words = wbtrace.pack(batches)
        expected = wbtrace.geometry(job)

        import m5
        from _m5 import core
        from m5.defines import buildEnv
        from m5.objects import (AddrRange, Root, SimpleMemory, SrcClockDomain,
                                System, SystemXBar, VoltageDomain,
                                WorkerBeeGemm)
        from m5.util.convert import anyToFrequency

        if core.gem5Version != GEM5_RELEASE:
            raise ValueError("Running gem5 binary does not match the pinned release")
        output = Path(m5.options.outdir).resolve()
        if (output / "result.json").exists():
            raise ValueError("Use a fresh gem5 output directory")
        m5.ticks.setGlobalFrequency("1ps")
        m5.ticks.fixGlobalFrequency()
        frequency = anyToFrequency(args.clock)
        period = m5.ticks.fromSeconds(1.0 / frequency)
        if period <= 0:
            raise ValueError("Clock period must be at least one simulation tick")

        memory_size = (span + 4095) // 4096 * 4096
        system = System()
        system.clk_domain = SrcClockDomain(clock=args.clock,
                                           voltage_domain=VoltageDomain())
        system.mem_mode = "timing"
        system.mem_ranges = [AddrRange(memory_size)]
        result_path = output / "model_result.json"
        system.workerbee = WorkerBeeGemm(
            hardware_profile=DEFAULT_PROFILE["name"],
            command_batches=words,
            dma_outstanding=args.dma_outstanding,
            array_pipeline_latency=args.array_latency,
            result_path=str(result_path),
        )
        if args.shared_bus:
            system.membus = SystemXBar()
            system.memory = SimpleMemory(range=AddrRange(memory_size),
                                         latency=args.memory_latency,
                                         bandwidth=args.memory_bandwidth)
            system.memory.port = system.membus.mem_side_ports
            system.system_port = system.membus.cpu_side_ports
            for name in DMA_PORTS:
                setattr(system.workerbee, name, system.membus.cpu_side_ports)
        else:
            for index, name in enumerate(DMA_PORTS):
                memory = SimpleMemory(range=AddrRange(index * bank, (index + 1) * bank),
                                      latency=args.memory_latency,
                                      bandwidth=args.memory_bandwidth)
                setattr(system, f"{name}_memory", memory)
                setattr(system.workerbee, name, memory.port)
        Root(full_system=False, system=system)

        baseline = snapshot(REPO_ROOT)
        trace_records = wbtrace.jsonl_records(job, batches, task_id=args.task_id,
                                              clock_period_ticks=period)
        if args.trace_jsonl:
            wbtrace.write_jsonl(args.trace_jsonl, trace_records)
        trace_hash = hashlib.sha256(
            json.dumps(trace_records, sort_keys=True).encode()).hexdigest()

        manifest = {
            "schema_version": 1, "phase": "L1", "run_kind": "gemm_single_job",
            "model_kind": "timing_loose", "data_valid": False,
            "performance_valid": False,
            "hardware_profile": DEFAULT_PROFILE,
            "baseline": {"git_head": baseline["git_head"],
                         "model_source_hash": baseline["model_source_hash"],
                         "model_sources": baseline["model_sources"]},
            "gem5": {"release": core.gem5Version, "commit": GEM5_COMMIT,
                     "executable": sys.executable,
                     "executable_sha256": hashlib.sha256(
                         Path(sys.executable).read_bytes()).hexdigest(),
                     "build_env": dict(buildEnv)},
            "host": {"os": platform.platform(),
                     "architecture": platform.machine(),
                     "python": platform.python_version()},
            "clock_period_ticks": period, "clock_frequency_hz": frequency,
            "memory": {"kind": "shared_bus" if args.shared_bus else "private_bank",
                       "latency": args.memory_latency,
                       "bandwidth": args.memory_bandwidth,
                       "capacity_bytes": memory_size,
                       "bank_bytes": bank,
                       "connection": ("six DMA ports share one SystemXBar"
                                      if args.shared_bus else
                                      "one SimpleMemory per DMA port, disjoint ranges")},
            "model": {"array_pipeline_latency": args.array_latency,
                      "dma_outstanding": args.dma_outstanding},
            "trace": {"task_id": args.task_id, "sha256": trace_hash,
                      "records": trace_records if len(trace_records) <= 32 else None},
            "expected_geometry": expected,
            "seed": None, "p0_accepted": False,
            "excluded_costs": ["cpu_instruction_execution", "isa_decode",
                               "mmio_mapping", "runtime_driver",
                               "numerical_execution", "external_coherence"],
        }
        manifest["gem5"]["script"] = str(Path(__file__).resolve())
        (output / "manifest.json").write_text(
            json.dumps(manifest, indent=2, sort_keys=True) + "\n")

        m5.instantiate()
        started = m5.curTick()
        limit = args.max_cycles * period
        event = m5.simulate(limit)
        elapsed_ticks = m5.curTick() - started
        cause = event.getCause()
        timed_out = cause == "simulate() limit reached"
        model = json.loads(result_path.read_text()) if result_path.exists() else None
        m5.stats.dump()

        gemm_cycles = (model or {}).get("gemm", {}).get("latency_cycles")
        simulated_seconds = elapsed_ticks * 1e-12
        job_info = (model or {}).get("job") or {}
        scheduled_ops = (model or {}).get("gemm", {}).get("scheduled_ops")
        errors = list((model or {}).get("errors", []))
        if timed_out:
            errors.append({"kind": "model", "code": "cycle_limit",
                           "message": f"model did not finish within {args.max_cycles} cycles",
                           "cycle": None, "task_id": args.task_id, "port": None})
        if model is None:
            errors.append({"kind": "model", "code": "missing_model_result",
                           "message": "the model wrote no result file",
                           "cycle": None, "task_id": args.task_id, "port": None})

        dma = (model or {}).get("dma", {})
        physical_bytes = sum(entry.get("bytes", 0) for entry in dma.values())
        result = {
            "schema_version": 1, "phase": "L1", "run_kind": "gemm_single_job",
            "model_kind": "timing_loose", "data_valid": False,
            "performance_valid": False, "p0_accepted": False,
            "task_id": args.task_id, "logical_shape": {"m": m, "n": n, "k": k},
            "programmed_shape": {key: job_info.get(key) for key in ("m", "n", "k")},
            "model": model, "expected_geometry": expected,
            "gemm_cycles": gemm_cycles,
            "simulated_ticks": elapsed_ticks,
            "simulated_seconds": simulated_seconds,
            "scheduled_ops": scheduled_ops,
            "physical_dma_bytes": physical_bytes,
            "exit_cause": cause, "timed_out": timed_out, "errors": errors,
        }
        peak_macs = expected["t"] * expected["k_per_beat_a"]
        result["peak_macs_per_cycle"] = peak_macs
        if gemm_cycles and not timed_out:
            macs = scheduled_ops // 2
            result["macs_per_cycle"] = macs / gemm_cycles
            result["utilization"] = macs / gemm_cycles / peak_macs
            result["ops_per_cycle"] = scheduled_ops / gemm_cycles
            result["ops_per_second"] = scheduled_ops / simulated_seconds
            result["dma_bytes_per_cycle"] = physical_bytes / gemm_cycles
            busy = (model or {}).get("gemm", {}).get("array_busy_cycles", 0)
            result["array_busy_fraction"] = busy / gemm_cycles
        (output / "result.json").write_text(
            json.dumps(result, indent=2, sort_keys=True) + "\n")
        print(json.dumps({"task_id": args.task_id, "gemm_cycles": gemm_cycles,
                          "ops_per_second": result.get("ops_per_second"),
                          "data_valid": False, "performance_valid": False}))
        return 0 if not (timed_out or model is None) else 1
    except Exception as exc:
        error = {"kind": "input" if isinstance(exc, ValueError) else "environment",
                 "code": "gemm_run_failed", "message": str(exc),
                 "cycle": None, "task_id": None, "port": None}
        sys.stderr.write(json.dumps({"error": error}, sort_keys=True) + "\n")
        return 1


if __name__ == "__m5_main__" or __name__ == "__main__":
    sys.exit(main())
