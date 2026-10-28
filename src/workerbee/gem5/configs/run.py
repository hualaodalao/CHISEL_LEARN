import argparse
import hashlib
import json
from pathlib import Path
import platform
import subprocess
import sys
import time

MODULE_ROOT = Path(__file__).resolve().parents[1]
REPO_ROOT = MODULE_ROOT.parents[2]
sys.path.insert(0, str(MODULE_ROOT / "configs"))
sys.path.insert(0, str(MODULE_ROOT / "tools"))
from hardware import DEFAULT_PROFILE, DMA_PORTS, GEM5_COMMIT, GEM5_RELEASE, verify_elaboration
from baseline import snapshot
from timing_evidence import collect


class ArgumentParser(argparse.ArgumentParser):
    def error(self, message):
        raise ValueError(message)


def arguments(argv=None):
    parser = ArgumentParser(description="P0 idle SimObject smoke; no command or GEMM execution")
    parser.add_argument("--gem5-root", type=Path, required=True)
    parser.add_argument("--rtl-dir", type=Path, required=True)
    parser.add_argument("--clock", default="500MHz")
    parser.add_argument("--smoke-cycles", type=int, default=8)
    args = parser.parse_args(argv)
    if args.smoke_cycles <= 0:
        raise ValueError("smoke-cycles must be positive")
    return args


def baseline_for_run(rtl_dir):
    directory = Path(rtl_dir).resolve()
    current = snapshot(REPO_ROOT, directory)
    recorded = json.loads((directory / "baseline.json").read_text())
    verify_elaboration(json.loads((directory / "hardware.json").read_text()))
    for key in ("hardware_source_hash", "rtl_hash"):
        if recorded[key] != current[key]:
            raise ValueError(f"Stale or changed elaboration baseline: {key}")
    if not recorded["elaboration"]["profile_matches"]:
        raise ValueError("RTL profile was not verified")
    emitter_path = "src/workerbee/gem5/tools/EmitP0.scala"
    emitter = next(entry for entry in current["model_sources"] if entry["path"] == emitter_path)
    original_emitter = next(entry for entry in recorded["model_sources"] if entry["path"] == emitter_path)
    if emitter != original_emitter:
        raise ValueError("RTL emitter changed; regenerate the elaboration baseline")
    current["rtl_generation"] = {
        "emitter_source": original_emitter,
        "model_source_hash_at_emission": recorded["model_source_hash"],
        "elaboration": recorded["elaboration"],
    }
    return current


def verify_build(executable, baseline):
    record = json.loads((executable.parent / "p0-build.json").read_text())
    sources = [entry for entry in baseline["model_sources"]
               if entry["path"].startswith("src/workerbee/gem5/model/")]
    binary_hash = hashlib.sha256(executable.read_bytes()).hexdigest()
    if record["gem5_commit"] != GEM5_COMMIT or record["compiled_model_sources"] != sources:
        raise ValueError("Model binding/C++ sources changed; rebuild the P0 binary")
    if record["executable_sha256"] != binary_hash:
        raise ValueError("gem5 binary changed since the recorded P0 build")
    return record


def git(root, *args):
    return subprocess.run(["git", "-C", str(root), *args], text=True,
                          capture_output=True, check=True, timeout=10).stdout.strip()


def main(argv=None):
    output = None
    try:
        args = arguments(argv)
        baseline = baseline_for_run(args.rtl_dir)
        gem5_root = args.gem5_root.expanduser().resolve()
        commit = git(gem5_root, "rev-parse", "HEAD")
        if commit != GEM5_COMMIT:
            raise ValueError("Unsupported gem5 commit; revalidate P0 APIs before changing baseline")
        evidence = collect(REPO_ROOT, gem5_root)
        import m5
        from _m5 import core
        from m5.defines import buildEnv
        from m5.objects import (AddrRange, Root, SimpleMemory, SrcClockDomain,
                                System, SystemXBar, VoltageDomain, WorkerBeeGemm)
        from m5.util.convert import anyToFrequency

        if core.gem5Version != GEM5_RELEASE:
            raise ValueError("Running gem5 binary does not match the pinned release")
        output = Path(m5.options.outdir).resolve()
        if any((output / name).exists() for name in ("manifest.json", "result.json")):
            raise ValueError("Use a fresh gem5 output directory")
        m5.ticks.setGlobalFrequency("1ps")
        m5.ticks.fixGlobalFrequency()
        period = m5.ticks.fromSeconds(1.0 / anyToFrequency(args.clock))
        if period <= 0:
            raise ValueError("Clock period must be at least one simulation tick")
        system = System()
        system.clk_domain = SrcClockDomain(clock=args.clock, voltage_domain=VoltageDomain())
        system.mem_mode = "timing"
        system.mem_ranges = [AddrRange("1MiB")]
        system.membus = SystemXBar()
        system.memory = SimpleMemory(range=system.mem_ranges[0], latency="1ns", bandwidth="32GiB/s")
        system.memory.port = system.membus.mem_side_ports
        system.system_port = system.membus.cpu_side_ports
        system.workerbee = WorkerBeeGemm(hardware_profile=DEFAULT_PROFILE["name"])
        for name in DMA_PORTS:
            setattr(system.workerbee, name, system.membus.cpu_side_ports)
        root = Root(full_system=False, system=system)
        executable = Path(sys.executable).resolve()
        build_dir = executable.parent
        build_record = verify_build(executable, baseline)
        manifest = {
            "schema_version": 1, "phase": "P0", "run_kind": "idle_smoke",
            "model_kind": "timing_only", "data_valid": False, "performance_valid": False,
            "hardware_profile": DEFAULT_PROFILE, "baseline": baseline,
            "timing_evidence": evidence,
            "gem5": {"release": core.gem5Version, "commit": commit,
                     "checkout_status": git(gem5_root, "status", "--short"),
                     "executable": str(executable),
                     "executable_sha256": hashlib.sha256(executable.read_bytes()).hexdigest(),
                     "compile_date": core.compileDate, "build_env": dict(buildEnv),
                     "build_record": build_record},
            "host": {"os": platform.platform(), "architecture": platform.machine(),
                     "python": platform.python_version(), "linux_validated": False},
            "clock_period_ticks": period, "tick_frequency_hz": 10**12,
            "memory": {"kind": "idle_smoke_fixture", "connection": "six ports share one SystemXBar and SimpleMemory",
                       "capacity_bytes": 2**20, "latency": "1ns", "bandwidth": "32GiB/s",
                       "dma_protocol_tested": False},
            "invocation": list(sys.argv), "seed": None, "input_hash": None,
            "excluded_costs": ["commands", "GEMM", "DMA", "scheduler", "ISA", "numerical_execution"],
            "p0_accepted": False,
        }
        for name in ("config", "variables", "extras"):
            path = build_dir / "gem5.build" / name
            if path.is_file():
                manifest["gem5"][f"build_{name}_sha256"] = hashlib.sha256(path.read_bytes()).hexdigest()
        (output / "manifest.json").write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n")
        m5.instantiate()
        started = m5.curTick()
        wall = time.perf_counter()
        event = m5.simulate(args.smoke_cycles * period)
        elapsed = m5.curTick() - started
        if elapsed != args.smoke_cycles * period or event.getCause() != "simulate() limit reached":
            raise RuntimeError("Idle smoke did not reach the requested tick boundary")
        m5.stats.dump()
        result = {"schema_version": 1, "phase": "P0", "run_kind": "idle_smoke",
                  "model_kind": "timing_only", "data_valid": False, "performance_valid": False,
                  "smoke_passed": True, "gemm_executed": False, "p0_accepted": False,
                  "simulated_ticks": elapsed, "smoke_cycles": args.smoke_cycles,
                  "exit_cause": event.getCause(), "host_seconds": time.perf_counter() - wall,
                  "errors": [], "remaining_gates": evidence["open_issues"] + ["Linux build validation", "RTL edge calibration"]}
        (output / "result.json").write_text(json.dumps(result, indent=2, sort_keys=True) + "\n")
        print(json.dumps({"smoke_passed": True, "p0_accepted": False, "performance_valid": False}))
        return 0
    except Exception as exc:
        error = {"kind": "input" if isinstance(exc, ValueError) else "environment",
                 "code": "p0_smoke_failed", "message": str(exc),
                 "cycle": None, "task_id": None, "port": None}
        sys.stderr.write(json.dumps({"error": error}) + "\n")
        return 1


if __name__ == "__m5_main__" or __name__ == "__main__":
    sys.exit(main())
