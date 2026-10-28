import copy
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

MODULE = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location("workerbee_p0_run", MODULE / "configs/run.py")
runner = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(runner)
from hardware import DEFAULT_PROFILE, verify_elaboration
from timing_evidence import collect


class P0Tests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="workerbee-p0-")
        self.addCleanup(self.temp.cleanup)
        self.rtl = Path(self.temp.name)
        (self.rtl / "WorkerBee.sv").write_text("module WorkerBee; endmodule\n")
        self.profile = {key: copy.deepcopy(DEFAULT_PROFILE[key]) for key in ("parameters", "derived")}
        (self.rtl / "hardware.json").write_text(json.dumps(self.profile))
        self.recorded = runner.snapshot(runner.REPO_ROOT, self.rtl)
        self.recorded["elaboration"] = {"profile_matches": True}
        self.save()

    def save(self):
        (self.rtl / "baseline.json").write_text(json.dumps(self.recorded))

    def test_profile_matches_actual_elaboration_fields(self):
        verify_elaboration(self.profile)

    def test_profile_rejects_arbitrary_hardware_knobs(self):
        for field in ("cBufferDepth", "arrayN", "cW"):
            with self.subTest(field=field):
                changed = copy.deepcopy(self.profile)
                changed["parameters"][field] += 1
                with self.assertRaises(ValueError):
                    verify_elaboration(changed)

    def test_profile_rejects_wrong_derived_width(self):
        self.profile["derived"]["cEffW"] = 32
        with self.assertRaises(ValueError):
            verify_elaboration(self.profile)

    def test_baseline_presence_never_claims_calibration(self):
        value = runner.baseline_for_run(self.rtl)
        self.assertTrue(value["rtl_present"])
        self.assertFalse(value["rtl_verified"])
        self.assertFalse(value["data_valid"])

    def test_stale_hardware_hash_is_rejected(self):
        self.recorded["hardware_source_hash"] = "stale"
        self.save()
        with self.assertRaisesRegex(ValueError, "hardware_source_hash"):
            runner.baseline_for_run(self.rtl)

    def test_changed_rtl_is_rejected(self):
        (self.rtl / "WorkerBee.sv").write_text("module Changed; endmodule\n")
        with self.assertRaisesRegex(ValueError, "rtl_hash"):
            runner.baseline_for_run(self.rtl)

    def test_changed_emitter_is_rejected(self):
        emitter = next(entry for entry in self.recorded["model_sources"]
                       if entry["path"].endswith("/EmitP0.scala"))
        emitter["sha256"] = "stale"
        self.save()
        with self.assertRaisesRegex(ValueError, "emitter changed"):
            runner.baseline_for_run(self.rtl)

    def test_original_emission_provenance_is_preserved(self):
        value = runner.baseline_for_run(self.rtl)
        self.assertEqual(value["rtl_generation"]["model_source_hash_at_emission"],
                         self.recorded["model_source_hash"])
        self.assertEqual(value["rtl_generation"]["elaboration"], self.recorded["elaboration"])

    def test_unverified_profile_is_rejected(self):
        self.recorded["elaboration"]["profile_matches"] = False
        self.save()
        with self.assertRaisesRegex(ValueError, "not verified"):
            runner.baseline_for_run(self.rtl)

    def test_missing_elaboration_manifest_is_rejected(self):
        (self.rtl / "baseline.json").unlink()
        with self.assertRaises(FileNotFoundError):
            runner.baseline_for_run(self.rtl)

    def test_smoke_cycle_limits(self):
        for cycles in ("0", "-1"):
            with self.subTest(cycles=cycles), self.assertRaises(ValueError):
                runner.arguments(["--gem5-root", "/tmp/gem5", "--rtl-dir", str(self.rtl),
                                  "--smoke-cycles", cycles])

    def test_trace_execution_is_not_exposed_in_p0(self):
        with self.assertRaises(ValueError):
            runner.arguments(["--gem5-root", "/tmp/gem5", "--rtl-dir", str(self.rtl), "--trace", "x"])

    def test_compiled_sources_and_binary_fingerprint(self):
        executable = self.rtl / "gem5.opt"
        executable.write_bytes(b"binary fixture")
        record = {
            "gem5_commit": runner.GEM5_COMMIT,
            "compiled_model_sources": [entry for entry in self.recorded["model_sources"]
                                       if entry["path"].startswith("src/workerbee/gem5/model/")],
            "executable_sha256": runner.hashlib.sha256(executable.read_bytes()).hexdigest(),
        }
        target = self.rtl / "p0-build.json"
        target.write_text(json.dumps(record))
        self.assertEqual(runner.verify_build(executable, self.recorded), record)
        executable.write_bytes(b"changed binary")
        with self.assertRaisesRegex(ValueError, "binary changed"):
            runner.verify_build(executable, self.recorded)
        record["compiled_model_sources"] = []
        target.write_text(json.dumps(record))
        with self.assertRaisesRegex(ValueError, "sources changed"):
            runner.verify_build(executable, self.recorded)

    def test_all_hardware_evidence_locations_match_current_sources(self):
        gem5 = runner.REPO_ROOT.parent / "GEM5_Learn/gem5"
        if not gem5.is_dir():
            self.skipTest("Pinned gem5 checkout is unavailable")
        evidence = collect(runner.REPO_ROOT, gem5)
        self.assertEqual(len(evidence["entries"]), 20)
        self.assertFalse(evidence["edge_calibrated"])
        self.assertEqual(len(evidence["open_issues"]), 6)
        self.assertTrue(all(entry["locations"] for entry in evidence["entries"]))


if __name__ == "__main__":
    unittest.main()
