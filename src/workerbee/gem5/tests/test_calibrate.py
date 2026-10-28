from contextlib import redirect_stderr
import importlib.util
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
import xml.etree.ElementTree as ET

MODULE = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(MODULE / "tools"))
SPEC = importlib.util.spec_from_file_location("workerbee_rtl_calibrate", MODULE / "tools/calibrate.py")
calibrator = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(calibrator)


class CalibrationTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="workerbee-rtl-calibration-")
        self.addCleanup(self.temp.cleanup)
        self.output = Path(self.temp.name)
        (self.output / "test-reports").mkdir()
        self.suites = {"WorkerBeeRtlStorageSpec": calibrator.SUITES["WorkerBeeRtlStorageSpec"]}
        self.xml = self.output / "test-reports/TEST-WorkerBeeRtlStorageSpec.xml"
        tree = ET.Element("testsuite")
        for name in sorted(self.suites["WorkerBeeRtlStorageSpec"]):
            ET.SubElement(tree, "testcase", name=f"WorkerBee RTL storage should {name}", time="0.01")
        ET.ElementTree(tree).write(self.xml)
        self.trace = self.output / "WorkerBeeRtlStorageSpec.jsonl"
        trace_ids = ("fifo_A", "fifo_B", "fifo_C", "fifo_scaleA", "fifo_scaleB", "fifo_flush",
                     "calc_config_edges", "cvt_release_edges")
        self.trace.write_text("".join(json.dumps({"case_id": case, "cycle": 0,
                             "phase": "pre_edge", "signals": {"pop_valid": 0}}) + "\n" for case in trace_ids))
        self.rtl = self.output / "fixture.sv"
        self.rtl.write_text("module fixture; endmodule\n")
        self.sources = {"hardware_source_hash": "hardware", "model_source_hash": "model"}

    def report(self, **changes):
        report = {**self.sources, "unit_edges_passed": True, "edge_calibrated": False,
                  "p0_accepted": False, "performance_valid": False, "data_valid": False,
                  "baseline_directory": str(self.output / "baseline"), "baseline_rtl_hash": "rtl",
                  "suites": list(self.suites), "complete_unit_suite": False,
                  "cases": calibrator.read_results(self.output, self.suites),
                  "artifacts": [{"path": p.relative_to(self.output).as_posix(),
                                 "sha256": calibrator.digest(p)} for p in (self.xml, self.trace, self.rtl)]}
        report.update(changes)
        (self.output / "calibration.json").write_text(json.dumps(report))
        return report

    def verify(self):
        with patch.object(calibrator, "snapshot", return_value=self.sources), \
                patch.object(calibrator, "baseline_for_run", return_value={"rtl_hash": "rtl"}):
            return calibrator.verify_report(self.output)

    def test_successful_complete_suite_is_parsed(self):
        cases = calibrator.read_results(self.output, self.suites)
        self.assertEqual(len(cases), 3)
        self.assertTrue(all(case["passed"] for case in cases))

    def test_missing_case_is_rejected(self):
        tree = ET.parse(self.xml)
        tree.getroot().remove(tree.getroot()[0])
        tree.write(self.xml)
        with self.assertRaisesRegex(ValueError, "test set"):
            calibrator.read_results(self.output, self.suites)

    def test_failure_skipped_and_error_are_not_passes(self):
        original = self.xml.read_bytes()
        for kind in ("failure", "skipped", "error"):
            with self.subTest(kind=kind):
                self.xml.write_bytes(original)
                tree = ET.parse(self.xml)
                ET.SubElement(tree.getroot()[0], kind)
                tree.write(self.xml)
                with self.assertRaisesRegex(ValueError, "did not pass"):
                    calibrator.read_results(self.output, self.suites)

    def test_empty_trace_and_wrong_edge_are_rejected(self):
        for content in ("", json.dumps({"phase": "post_edge", "signals": {"x": 0}})):
            with self.subTest(content=content):
                self.trace.write_text(content)
                with self.assertRaisesRegex(ValueError, "edge samples"):
                    calibrator.read_results(self.output, self.suites)

    def test_each_case_must_have_edge_samples(self):
        lines = self.trace.read_text().splitlines()
        self.trace.write_text("\n".join(lines[:-1]) + "\n")
        with self.assertRaisesRegex(ValueError, "sample case set"):
            calibrator.read_results(self.output, self.suites)

    def test_duplicate_sample_cycle_is_rejected(self):
        original = self.trace.read_text()
        self.trace.write_text(original + original.splitlines()[0] + "\n")
        with self.assertRaisesRegex(ValueError, "Nonmonotonic"):
            calibrator.read_results(self.output, self.suites)

    def test_report_provenance_is_verified(self):
        expected = self.report()
        self.assertEqual(self.verify(), expected)
        self.report(hardware_source_hash="old")
        with self.assertRaisesRegex(ValueError, "Stale"):
            self.verify()
        self.report(model_source_hash="old")
        with self.assertRaisesRegex(ValueError, "Stale"):
            self.verify()

    def test_changed_trace_or_rtl_is_rejected(self):
        for path in (self.trace, self.rtl):
            with self.subTest(path=path):
                original = path.read_bytes()
                self.report()
                path.write_text("changed")
                with self.assertRaisesRegex(ValueError, "artifact"):
                    self.verify()
                path.write_bytes(original)

    def test_baseline_hash_is_verified(self):
        self.report(baseline_rtl_hash="changed")
        with self.assertRaisesRegex(ValueError, "baseline"):
            self.verify()

    def test_artifact_inventory_cannot_omit_rtl(self):
        report = self.report()
        report["artifacts"] = [entry for entry in report["artifacts"] if entry["path"] != "fixture.sv"]
        self.report(artifacts=report["artifacts"])
        with self.assertRaisesRegex(ValueError, "artifact inventory"):
            self.verify()

    def test_case_inventory_must_match_xml(self):
        for changes in ({"cases": []}, {"complete_unit_suite": True}):
            self.report(**changes)
            with self.assertRaisesRegex(ValueError, "case inventory"):
                self.verify()

    def test_unit_results_cannot_claim_full_acceptance(self):
        for field in ("edge_calibrated", "p0_accepted", "performance_valid", "data_valid"):
            with self.subTest(field=field):
                self.report(**{field: True})
                with self.assertRaisesRegex(ValueError, "acceptance"):
                    self.verify()

    def test_parent_traversal_is_rejected(self):
        self.report(artifacts=[{"path": "../other", "sha256": "wrong"}])
        with self.assertRaisesRegex(ValueError, "artifact"):
            self.verify()

    def test_existing_output_is_never_overwritten(self):
        with self.assertRaisesRegex(ValueError, "new directory"):
            calibrator.calibrate(self.output, self.output, "sbt", list(self.suites))

    def test_cli_errors_are_structured(self):
        stderr = io.StringIO()
        with redirect_stderr(stderr):
            status = calibrator.main([])
        self.assertEqual(status, 1)
        self.assertEqual(set(json.loads(stderr.getvalue())["error"]),
                         {"kind", "code", "message", "cycle", "task_id", "port"})


if __name__ == "__main__":
    unittest.main()
