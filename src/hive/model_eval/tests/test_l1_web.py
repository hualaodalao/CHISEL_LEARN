import hashlib
import io
import json
import math
import re
import tempfile
import unittest
from contextlib import redirect_stderr, redirect_stdout
from pathlib import Path
from unittest.mock import patch

from hive.model_eval.common.manifest import build as build_manifest
from hive.model_eval.common.workload import decode, prefill
from hive.model_eval.frontend.qwen3_06b import CONFIG, build
from hive.model_eval.frontend.spec import FORMATS, MXFP8
from hive.model_eval.hw.profiles import G0, G0_PRO, MODEL_ANALYSIS_310
from hive.model_eval.l1 import web
from hive.model_eval.l1.params import ModelParams
from hive.model_eval.l1.run import main
from hive.model_eval.l1.timeline import evaluate
from hive.model_eval.l1.web import render_html, report_payload


TEMPLATE = Path(web.__file__).with_name("resources.html")
LEGACY_FIELDS = {
    "t_modeled_s", "modeled_throughput_excluding_unknown_costs",
    "operators_time_s", "handoff_time_s",
}


class TestL1Web(unittest.TestCase):
    @staticmethod
    def small_model(layers=2):
        return build({**CONFIG, "num_hidden_layers": layers})

    def payload(self, profile=G0, points=None, params=None, model=None, fmt=MXFP8):
        model = model or self.small_model()
        results = [evaluate(model, profile, fmt, point, attention=variant, params=params)
                   for point in (points or [decode(0), decode(32)])
                   for variant in ("vpu", "quantized_gemm")]
        manifest = build_manifest(
            profile=profile, model_name=model.name, layer="L1",
            named_inputs=(params or ModelParams()).named_inputs(),
            excluded_costs=sorted({item for result in results for item in result.excluded_costs}),
        )
        return report_payload(profile, results, manifest)

    def extract_payload(self, html):
        match = re.search(r'<script id="l1-data" type="application/json">(.*?)</script>', html, re.S)
        self.assertIsNotNone(match)
        return json.loads(match.group(1))

    def assert_close(self, actual, expected):
        self.assertTrue(math.isclose(actual, expected, rel_tol=1e-12, abs_tol=1e-15),
                        f"{actual!r} != {expected!r}")

    def test_payload_uses_authoritative_tasks_schedule_and_debug_ledger(self):
        result = evaluate(self.small_model(), G0, MXFP8, decode(32))
        manifest = build_manifest(profile=G0, model_name=result.model, layer="L1",
                                  named_inputs={}, excluded_costs=list(result.excluded_costs))
        payload = report_payload(G0, [result], manifest)
        case = payload["cases"][0]
        for key, value in result.to_dict(debug=True).items():
            self.assertEqual(case[key], value)
        self.assertTrue({"manifest", "digest", "hardware", "cases"}.issubset(payload))
        self.assertFalse(LEGACY_FIELDS.intersection(case))
        self.assertEqual(len(case["layers"]), 5)
        self.assertEqual(case["layers"][-1]["layer"], "sampling")
        self.assertTrue(case["tasks"])
        self.assertTrue(all("ledger" in op for op in case["operators"]))
        for key in ("schedule", "topology", "buffer_plan", "graph", "traffic",
                    "resource_busy_s", "projection_resource_busy_s",
                    "memory_peak_bytes", "projection_memory_peak_bytes"):
            self.assertIn(key, case)
        self.assertIsInstance(case["active_cores"], int)
        for task in case["tasks"]:
            self.assertTrue({"parent_op_id", "engine_kind", "spec", "inputs", "outputs",
                             "dependencies", "instance_id", "phases", "waits",
                             "known_service_budget_s", "start_s", "end_s",
                             "projection_start_s", "projection_end_s"}.issubset(task))
        for op in case["operators"]:
            self.assertTrue({"charges", "coupling", "excluded"}.issubset(op["ledger"]))
            self.assertTrue(all(task_id in {task["id"] for task in case["tasks"]}
                                for task_id in op["tasks"]))

    def test_only_service_budgets_sum_across_tasks_operators_and_layers(self):
        for profile in (G0, G0_PRO, MODEL_ANALYSIS_310):
            for fmt in FORMATS.values():
                for point in (decode(0), decode(32, 2), prefill(16)):
                    with self.subTest(profile=profile.name, fmt=fmt.name, point=point):
                        payload = self.payload(profile, [point], ModelParams(sync_us=2), fmt=fmt)
                        for case in payload["cases"]:
                            tasks = {task["id"]: task for task in case["tasks"]}
                            budget = case["known_service_budget_s"]
                            self.assert_close(sum(task["known_service_budget_s"]
                                                  for task in tasks.values()), budget)
                            self.assert_close(sum(op["known_service_budget_s"]
                                                  for op in case["operators"]), budget)
                            self.assert_close(sum(layer["known_service_budget_s"]
                                                  for layer in case["layers"]), budget)
                            for op in case["operators"]:
                                self.assert_close(sum(tasks[key]["known_service_budget_s"]
                                                      for key in op["tasks"]),
                                                  op["known_service_budget_s"])
                            for layer in case["layers"]:
                                ops = [op for op in case["operators"] if op["layer"] == layer["layer"]]
                                self.assert_close(sum(op["known_service_budget_s"] for op in ops),
                                                  layer["known_service_budget_s"])
                                self.assertEqual(layer["operators"], len(ops))
                                self.assertEqual(layer["tasks"], sum(len(op["tasks"]) for op in ops))
                            # Spans overlap and handoffs are schedule edges, not extra task budgets.
                            # Do not add layer/op spans or resource busy times to make a makespan.
                            for flag in ("coverage_complete", "performance_valid", "data_valid",
                                         "cross_level_comparable"):
                                self.assertFalse(case[flag])
                            self.assertIsNone(case["makespan_s"])
                            self.assertIsNone(case["modeled_throughput"])
                            self.assertIsNone(case["attribution_s"])

    def test_layer_count_is_derived_from_model(self):
        for case in self.payload(points=[decode(0)], model=self.small_model(3))["cases"]:
            self.assertEqual(len(case["layers"]), 6)
            self.assertEqual(case["layers"][-3]["layer"], "final-norm")

    def test_unknown_services_propagate_without_zero_or_throughput(self):
        for profile in (G0, G0_PRO, MODEL_ANALYSIS_310):
            with self.subTest(profile=profile.name):
                payload = self.payload(profile, [decode(32)])
                if profile is MODEL_ANALYSIS_310:
                    for key in ("freq_hz", "sram_bytes_per_core", "local_iface_gbps"):
                        self.assertIsNone(payload["hardware"][key])
                for case in payload["cases"]:
                    self.assertIsNone(case["makespan_s"])
                    self.assertIsNone(case["modeled_throughput"])
                    self.assertIsNone(case["attribution_s"])
                    self.assertFalse(case["cross_level_comparable"])
                    self.assertTrue(case["comparison_reason"])
                    self.assertGreater(case["known_service_budget_s"], 0)
                    unknown_tasks = [task for task in case["tasks"]
                                     if any(phase["seconds"] is None for phase in task["phases"])]
                    self.assertTrue(unknown_tasks)
                    for task in unknown_tasks:
                        self.assertIsNone(task["end_s"])
                        for phase in task["phases"]:
                            self.assertGreaterEqual(phase["known_seconds"], 0)
                            if phase["seconds"] is None:
                                self.assertIsNone(phase["end_s"])
                    self.assertTrue(case["excluded_costs"])
                    self.assertTrue(any(value is None for value in case["resource_busy_s"].values()))
                    self.assertTrue(any(value is None for value in case["memory_peak_bytes"].values()))
                    # Projection, when feasible, preserves Python's attribution, not a JS estimate.
                    if case["projection_makespan_s"] is not None:
                        self.assert_close(sum(case["projection_attribution_s"].values()),
                                          case["projection_makespan_s"])

    def test_safe_embedding_roundtrip_and_determinism(self):
        payload = self.payload(points=[decode(0)])
        injected = '</script><img src=x onerror="alert(1)"> & \u2028\u2029'
        payload["manifest"]["model"] = injected
        case = payload["cases"][0]
        case["operators"][0]["evidence"] = injected
        case["tasks"][0]["spec"]["evidence"] = injected
        case["tasks"][0]["waits"] = [{"reason": injected}]
        case["schedule"]["blocked_constraints"] = [{"reason": injected}]
        html = render_html(payload)
        self.assertNotIn(injected, html)
        self.assertNotIn("__L1_PAYLOAD__", html)
        self.assertEqual(self.extract_payload(html), payload)
        self.assertEqual(html, render_html(payload))
        self.assertEqual(render_html(self.payload(points=[decode(0)])),
                         render_html(self.payload(points=[decode(0)])))

    def test_blocked_schedule_and_null_projection_remain_serializable(self):
        payload = self.payload(points=[decode(0)])
        for case in payload["cases"]:
            case["makespan_s"] = None
            case["projection_makespan_s"] = None
            case["attribution_s"] = None
            case["projection_attribution_s"] = None
            case["projection_resource_busy_s"] = {}
            case["projection_memory_peak_bytes"] = {}
            case["schedule"] = {"blocked_constraints": [{"resource": "core0.sram", "reason": "capacity"}]}
            case["blocked_constraints"] = [{"reason": "input constraint"}]
            for task in case["tasks"]:
                task["instance_id"] = None
                task["start_s"] = task["end_s"] = None
                task["projection_start_s"] = task["projection_end_s"] = None
                for phase in task["phases"]:
                    phase["projection_start_s"] = phase["projection_end_s"] = None
        self.assertEqual(self.extract_payload(render_html(payload)), payload)

    def test_template_has_safe_offline_accessible_new_schema_controls(self):
        template = TEMPLATE.read_text(encoding="utf-8")
        self.assertEqual(template.count("__L1_PAYLOAD__"), 1)
        self.assertNotIn("innerHTML", template)
        self.assertNotIn("outerHTML", template)
        self.assertNotIn("insertAdjacentHTML", template)
        self.assertIn("document.createElement", template)
        self.assertIn("textContent", template)
        self.assertNotRegex(template, r'<(?:script|link|iframe|img)\b[^>]*(?:src|href)=')
        self.assertNotRegex(template, r'\b(?:fetch|XMLHttpRequest|WebSocket)\s*\(')
        self.assertNotIn("@import", template)
        self.assertNotIn("@font-face", template)
        self.assertIn("prefers-reduced-motion", template)
        self.assertIn(":focus-visible", template)
        self.assertIn('aria-live="polite"', template)
        for identifier in ("case-select", "layer-map", "operator-list", "detail-title",
                           "task-list", "task-detail", "task-spec", "phase-table",
                           "task-waits", "instance-filter", "show-projection", "timeline",
                           "known-service-budget", "projection-time", "blocked-warning",
                           "blocked-constraints", "resource-busy-table", "memory-peak-table",
                           "buffer-table", "traffic-table", "manifest-data", "ledger-details"):
            self.assertIn(f'id="{identifier}"', template)
        for identifier in ("case-select", "instance-filter", "show-projection"):
            self.assertIn(f'for="{identifier}"', template)
        for field in LEGACY_FIELDS:
            self.assertNotIn(field, template)
        self.assertIn("覆盖不完整", template)
        self.assertIn("不输出", template)
        self.assertIn("不证明实际并行", template)
        self.assertIn("预算不是延迟", template)

    def test_nonfinite_payload_rejected(self):
        for value in (float("nan"), float("inf"), float("-inf")):
            with self.subTest(value=value), self.assertRaises(ValueError):
                render_html({"invalid": value})

    def test_cli_html_json_equality_debug_ledger_and_source_fingerprint(self):
        for debug in (False, True):
            with self.subTest(debug=debug), tempfile.TemporaryDirectory() as tmp, \
                    redirect_stdout(io.StringIO()), \
                    patch("hive.model_eval.l1.run.get_model", return_value=self.small_model()):
                html_path = Path(tmp) / "nested" / "l1.html"
                json_path = Path(tmp) / "l1.json"
                args = ["--seq", "0,32", "--html", str(html_path), "--json", str(json_path)]
                if debug:
                    args.append("--debug-ledger")
                self.assertEqual(main(args), 0)
                payload = self.extract_payload(html_path.read_text(encoding="utf-8"))
                raw = json.loads(json_path.read_text(encoding="utf-8"))
                self.assertEqual(raw["schema_version"], 2)
                self.assertEqual(len(payload["cases"]), 4)
                self.assertEqual(payload["manifest"], raw["manifest"])
                self.assertEqual(payload["digest"], raw["manifest_digest"])
                fingerprint = next(source for source in payload["manifest"]["sources"]
                                   if source["path"].endswith("l1/resources.html"))
                source_bytes = TEMPLATE.read_bytes()
                self.assertEqual(fingerprint["sha256"], hashlib.sha256(source_bytes).hexdigest())
                self.assertEqual(fingerprint["bytes"], len(source_bytes))
                for case, result in zip(payload["cases"], raw["results"]):
                    self.assertFalse(LEGACY_FIELDS.intersection(case))
                    for key, value in result.items():
                        if key != "operators":
                            self.assertEqual(case[key], value, key)
                    self.assertEqual(len(case["operators"]), len(result["operators"]))
                    for html_op, json_op in zip(case["operators"], result["operators"]):
                        self.assertIn("ledger", html_op)
                        self.assertEqual("ledger" in json_op, debug)
                        self.assertEqual({key: value for key, value in html_op.items()
                                          if debug or key != "ledger"}, json_op)

    def test_cli_310_prefill_keeps_unknown_time_and_throughput(self):
        with tempfile.TemporaryDirectory() as tmp, redirect_stdout(io.StringIO()), \
                patch("hive.model_eval.l1.run.get_model", return_value=self.small_model()):
            path = Path(tmp) / "partial.html"
            self.assertEqual(main(["--profile", "310", "--workload", "prefill", "--seq", "16",
                                   "--html", str(path)]), 0)
            for case in self.extract_payload(path.read_text(encoding="utf-8"))["cases"]:
                self.assertEqual(case["phase"], "prefill")
                self.assertIsNone(case["makespan_s"])
                self.assertIsNone(case["modeled_throughput"])
                self.assertFalse(case["cross_level_comparable"])

    def test_duplicate_output_paths_rejected_before_writing(self):
        with tempfile.TemporaryDirectory() as tmp, redirect_stderr(io.StringIO()):
            path = Path(tmp) / "report"
            path.write_text("existing report", encoding="utf-8")
            for html_path in (path, Path(tmp) / "." / "report"):
                with self.assertRaises(SystemExit) as error:
                    main(["--json", str(path), "--html", str(html_path)])
                self.assertEqual(error.exception.code, 2)
                self.assertEqual(path.read_text(encoding="utf-8"), "existing report")


if __name__ == "__main__":
    unittest.main()
