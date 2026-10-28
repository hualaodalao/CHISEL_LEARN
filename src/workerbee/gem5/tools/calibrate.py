import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import subprocess
import sys
import xml.etree.ElementTree as ET

MODULE_ROOT = Path(__file__).resolve().parents[1]
REPO_ROOT = MODULE_ROOT.parents[2]
sys.path.insert(0, str(MODULE_ROOT / "configs"))
from hardware import DEFAULT_PROFILE
from baseline import snapshot
from run import baseline_for_run

SUITES = {
    "WorkerBeeRtlStorageSpec": {"fifo_boundary", "calc_config_edges", "cvt_release_edges"},
    "WorkerBeeRtlArraySpec": {"array_wavefront", "executor_windows"},
    "WorkerBeeRtlDmaSpec": {"dma_credit", "dma_gather", "dma_stretch"},
}


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def read_results(directory, suites):
    cases = []
    for suite, expected in suites.items():
        path = directory / "test-reports" / f"TEST-{suite}.xml"
        tree = ET.parse(path).getroot()
        tests = tree.findall(".//testcase")
        names = {entry.attrib["name"].rsplit(" should ", 1)[-1] for entry in tests}
        if names != expected or len(tests) != len(expected):
            raise ValueError(f"Incomplete or unexpected test set: {suite}: {names}")
        for entry in tests:
            if any(entry.find(kind) is not None for kind in ("failure", "error", "skipped")):
                raise ValueError(f"RTL edge test did not pass: {entry.attrib['name']}")
            cases.append({"suite": suite, "name": entry.attrib["name"],
                          "seconds": float(entry.attrib.get("time", "0")), "passed": True})
        trace = directory / f"{suite}.jsonl"
        rows = [json.loads(line) for line in trace.read_text().splitlines()]
        if not rows or any(row.get("phase") != "pre_edge" or not row.get("signals") for row in rows):
            raise ValueError(f"Missing edge samples: {suite}")
        expected_traces = set(expected)
        if "fifo_boundary" in expected_traces:
            expected_traces.remove("fifo_boundary")
            expected_traces.update({"fifo_A", "fifo_B", "fifo_C", "fifo_scaleA", "fifo_scaleB", "fifo_flush"})
        if {row.get("case_id") for row in rows} != expected_traces:
            raise ValueError(f"Incomplete edge sample case set: {suite}")
        last_cycle = {}
        for row in rows:
            cycle = row.get("cycle")
            if not isinstance(cycle, int) or cycle < 0 or cycle <= last_cycle.get(row["case_id"], -1):
                raise ValueError(f"Nonmonotonic edge samples: {suite}")
            last_cycle[row["case_id"]] = cycle
    return cases


def verify_report(directory, repo_root=REPO_ROOT):
    directory = Path(directory).resolve()
    report = json.loads((directory / "calibration.json").read_text())
    current = snapshot(repo_root)
    for key in ("hardware_source_hash", "model_source_hash"):
        if report[key] != current[key]:
            raise ValueError(f"Stale RTL calibration sources: {key}")
    baseline = baseline_for_run(report["baseline_directory"])
    if report["baseline_rtl_hash"] != baseline["rtl_hash"]:
        raise ValueError("Changed RTL calibration baseline")
    artifact_paths = set()
    for entry in report["artifacts"]:
        relative = Path(entry["path"])
        path = (directory / relative).resolve()
        if (relative.is_absolute() or ".." in relative.parts or not path.is_relative_to(directory)
                or entry["path"] in artifact_paths or digest(path) != entry["sha256"]):
            raise ValueError("Changed or invalid RTL calibration artifact")
        artifact_paths.add(entry["path"])
    expected_paths = {path.relative_to(directory).as_posix() for path in directory.rglob("*")
                      if path.is_file() and (path.suffix in {".sv", ".v", ".jsonl", ".xml"}
                                             or path.name == "calibration.log")}
    if artifact_paths != expected_paths or not any(Path(path).suffix in {".sv", ".v"} for path in artifact_paths):
        raise ValueError("Incomplete RTL calibration artifact inventory")
    if not report["unit_edges_passed"] or any(report[field] for field in
            ("edge_calibrated", "p0_accepted", "performance_valid", "data_valid")):
        raise ValueError("Invalid unit calibration acceptance flags")
    if not report["suites"] or len(set(report["suites"])) != len(report["suites"]) or not set(report["suites"]) <= set(SUITES):
        raise ValueError("Invalid RTL calibration suite inventory")
    suites = {name: SUITES[name] for name in report["suites"]}
    if report["cases"] != read_results(directory, suites) or report["complete_unit_suite"] != (set(suites) == set(SUITES)):
        raise ValueError("Changed RTL calibration case inventory")
    return report


def calibrate(output_dir, rtl_dir, sbt, suites):
    output = Path(output_dir).expanduser().resolve()
    if output.exists() or not output.parent.is_dir():
        raise ValueError("Calibration output must be a new directory with an existing parent")
    baseline = baseline_for_run(rtl_dir)
    before = snapshot(REPO_ROOT)
    output.mkdir()
    source_setting = (
        'set Compile / unmanagedSources := (Compile / unmanagedSources).value.filter('
        'f => f.getPath.contains("/src/workerbee/scala/main/") || '
        'f.getPath.contains("/src/utils/"))'
    )
    test_setting = (
        'set Test / unmanagedSources := ((baseDirectory.value / "src" / "workerbee" / '
        '"gem5" / "tests") * "*.scala").get'
    )
    command = [str(sbt), "-J-Xmx6G", f"-Dworkerbee.calibration.output={output}",
               source_setting, test_setting, 'set Test / parallelExecution := false',
               'set Test / testReportsDirectory := file(' + json.dumps(str(output / "test-reports")) + ')',
               "testOnly " + " ".join(suites)]
    environment = dict(os.environ)
    for name, path in (("CPATH", "/opt/homebrew/include"), ("LIBRARY_PATH", "/opt/homebrew/lib")):
        if Path(path).is_dir():
            environment[name] = os.pathsep.join(filter(None, (environment.get(name), path)))
    with (output / "calibration.log").open("w", buffering=1) as log:
        log.write(json.dumps(command) + "\n")
        completed = subprocess.run(command, cwd=REPO_ROOT, env=environment,
                                   stdout=log, stderr=subprocess.STDOUT, check=False)
    after = snapshot(REPO_ROOT)
    if completed.returncode:
        raise RuntimeError(f"RTL calibration failed ({completed.returncode}); see {output / 'calibration.log'}")
    for key in ("hardware_source_hash", "model_source_hash"):
        if before[key] != after[key]:
            raise RuntimeError(f"Sources changed during RTL calibration: {key}; rerun in a fresh directory")
    cases = read_results(output, {name: SUITES[name] for name in suites})
    files = sorted(path for path in output.rglob("*") if path.is_file() and
                   (path.suffix in {".sv", ".v", ".jsonl", ".xml"} or path.name == "calibration.log"))
    rtl_files = [path for path in files if path.suffix in {".sv", ".v"}]
    if not rtl_files:
        raise RuntimeError("Calibration did not retain instrumented RTL")
    report = {
        "schema_version": 1, "phase": "P0", "run_kind": "rtl_unit_edges",
        "hardware_profile": DEFAULT_PROFILE, "hardware_source_hash": after["hardware_source_hash"],
        "model_source_hash": after["model_source_hash"],
        "hardware_sources": after["hardware_sources"], "model_sources": after["model_sources"],
        "baseline_rtl_hash": baseline["rtl_hash"], "baseline_directory": str(Path(rtl_dir).resolve()),
        "instrumentation": "test-only read probes and isolated submodule fixtures; no design source changes",
        "sampling": "signals sampled after input settling, immediately before each rising edge; reset frames identified where logged",
        "verilator_compile_jobs": 2,
        "suites": list(suites), "cases": cases, "unit_edges_passed": True,
        "complete_unit_suite": set(suites) == set(SUITES), "edge_calibrated": False,
        "p0_accepted": False, "performance_valid": False, "data_valid": False,
        "command": command, "host": platform.platform(),
        "verilator_version": subprocess.run(["verilator", "--version"], text=True,
                                             capture_output=True, check=True).stdout.strip(),
        "artifacts": [{"path": path.relative_to(output).as_posix(), "sha256": digest(path)} for path in files],
        "remaining_gates": ["top-level GEMM edge integration and done/write-response drain",
                            "ScaleA parameter-owner integration", "C-write outstanding legal domain",
                            "ScaleC service-domain tests", "gem5 protocol adaptation", "Linux build validation",
                            "specification ambiguity decisions"],
    }
    (output / "calibration.json").write_text(json.dumps(report, indent=2, sort_keys=True) + "\n")
    verify_report(output)
    return report


class ArgumentParser(argparse.ArgumentParser):
    def error(self, message):
        raise ValueError(message)


def main(argv=None):
    parser = ArgumentParser(description="Run default-profile P0 RTL unit edge calibration without changing design sources")
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--rtl-dir", type=Path)
    parser.add_argument("--sbt", default="sbt")
    parser.add_argument("--suite", choices=SUITES, action="append")
    parser.add_argument("--verify-only", action="store_true")
    try:
        args = parser.parse_args(argv)
        if args.verify_only:
            report = verify_report(args.output_dir)
        else:
            if args.rtl_dir is None:
                raise ValueError("--rtl-dir is required for calibration")
            report = calibrate(args.output_dir, args.rtl_dir, args.sbt, args.suite or list(SUITES))
        print(json.dumps({"unit_edges_passed": report["unit_edges_passed"],
                          "tests": len(report["cases"]), "edge_calibrated": False, "p0_accepted": False}))
        return 0
    except (ValueError, OSError, RuntimeError, ET.ParseError, subprocess.SubprocessError) as exc:
        sys.stderr.write(json.dumps({"error": {"kind": "environment", "code": "rtl_calibration_failed",
            "message": str(exc), "cycle": None, "task_id": None, "port": None}}) + "\n")
        return 1


if __name__ == "__main__":
    sys.exit(main())
