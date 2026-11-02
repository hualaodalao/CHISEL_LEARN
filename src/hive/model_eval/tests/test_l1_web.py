import io
import json
import math
import re
import tempfile
import unittest
from contextlib import redirect_stderr, redirect_stdout
from pathlib import Path

from hive.model_eval.common.manifest import build as build_manifest
from hive.model_eval.common.workload import decode, prefill
from hive.model_eval.frontend.qwen3_06b import CONFIG, build
from hive.model_eval.frontend.spec import FORMATS, MXFP8
from hive.model_eval.hw.profiles import G0, G0_PRO, MODEL_ANALYSIS_310
from hive.model_eval.l1.params import ModelParams
from hive.model_eval.l1.run import main
from hive.model_eval.l1.timeline import evaluate
from hive.model_eval.l1.web import render_html, report_payload


class TestL1Web(unittest.TestCase):
    def payload(self, profile=G0, points=None, params=None, model=None, fmt=MXFP8):
        model = model or build()
        results = [evaluate(model, profile, fmt, point, attention=variant, params=params)
                   for point in (points or [decode(0), decode(4096)])
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

    def test_payload_uses_authoritative_timeline_and_ledger(self):
        result = evaluate(build(), G0, MXFP8, decode(4096))
        manifest = build_manifest(profile=G0, model_name=result.model, layer="L1",
                                  named_inputs={}, excluded_costs=list(result.excluded_costs))
        case = report_payload(G0, [result], manifest)["cases"][0]
        for key, value in result.to_dict(debug=True).items():
            self.assertEqual(case[key], value)
        self.assertEqual(len(case["layers"]), 31)
        self.assertEqual(case["layers"][-1]["layer"], "sampling")
        self.assertTrue(all("ledger" in op for op in case["operators"]))
        self.assertEqual(case["metrics"]["array_cycles"], 19_788_544)
        self.assertEqual(case["metrics"]["b_beats"], 18_624_512)

    def test_totals_and_handoff_are_separate_for_profiles_formats_and_phases(self):
        for profile in (G0, G0_PRO, MODEL_ANALYSIS_310):
            for fmt in FORMATS.values():
                for point in (decode(0), decode(4096, 2), prefill(16)):
                    with self.subTest(profile=profile.name, fmt=fmt.name, point=point):
                        payload = self.payload(profile, [point], ModelParams(sync_us=2), fmt=fmt)
                        for case in payload["cases"]:
                            self.assertTrue(math.isclose(sum(case["attribution_s"].values()),
                                                         case["t_modeled_s"], rel_tol=1e-12))
                            layer_time = sum(layer["t_s"] for layer in case["layers"])
                            self.assertTrue(math.isclose(layer_time, case["operators_time_s"], rel_tol=1e-12))
                            self.assertAlmostEqual(case["handoff_time_s"], case["handoffs"] * 2e-6)
                            self.assertTrue(math.isclose(layer_time + case["handoff_time_s"],
                                                         case["t_modeled_s"], rel_tol=1e-12))
                            self.assertGreater(case["t_modeled_s"], layer_time)
                            self.assertFalse(case["coverage_complete"])
                            self.assertFalse(case["performance_valid"])
                            self.assertFalse(case["data_valid"])

    def test_layer_count_is_not_hardcoded(self):
        model = build({**CONFIG, "num_hidden_layers": 3})
        for case in self.payload(model=model)["cases"]:
            self.assertEqual(len(case["layers"]), 6)
            self.assertEqual(case["layers"][-3]["layer"], "final-norm")

    def test_unknown_hardware_and_charge_times_remain_null(self):
        payload = self.payload(MODEL_ANALYSIS_310)
        self.assertIsNone(payload["hardware"]["freq_hz"])
        self.assertIsNone(payload["hardware"]["sram_bytes_per_core"])
        self.assertIsNone(payload["hardware"]["local_iface_gbps"])
        for case in payload["cases"]:
            self.assertFalse(case["cross_level_comparable"])
            self.assertIsNone(case["modeled_throughput_excluding_unknown_costs"])
            charges = [charge for op in case["operators"] for charge in op["ledger"]["charges"]]
            self.assertTrue(any(charge["seconds"] is None for charge in charges))
            self.assertTrue(case["excluded_costs"])

    def test_safe_embedding_roundtrip_and_determinism(self):
        payload = self.payload()
        injected = '</script><img src=x onerror="alert(1)"> & \u2028\u2029'
        payload["manifest"]["model"] = injected
        payload["cases"][0]["operators"][0]["evidence"] = injected
        html = render_html(payload)
        self.assertNotIn(injected, html)
        self.assertNotIn("__L1_PAYLOAD__", html)
        self.assertEqual(self.extract_payload(html), payload)
        self.assertEqual(html, render_html(payload))
        self.assertEqual(render_html(self.payload()), render_html(self.payload()))

    def test_nonfinite_payload_rejected(self):
        with self.assertRaises(ValueError):
            render_html({"invalid": float("nan")})

    def test_cli_html_and_json_have_identical_results_and_manifest(self):
        with tempfile.TemporaryDirectory() as tmp, redirect_stdout(io.StringIO()):
            html_path = Path(tmp) / "nested" / "l1.html"
            json_path = Path(tmp) / "l1.json"
            self.assertEqual(main(["--seq", "0,4096", "--html", str(html_path),
                                   "--json", str(json_path)]), 0)
            payload = self.extract_payload(html_path.read_text(encoding="utf-8"))
            raw = json.loads(json_path.read_text(encoding="utf-8"))
            self.assertEqual(len(payload["cases"]), 4)
            self.assertEqual(payload["manifest"], raw["manifest"])
            self.assertEqual(payload["digest"], raw["manifest_digest"])
            self.assertTrue(any(s["path"].endswith("l1/resources.html")
                                for s in payload["manifest"]["sources"]))
            for case, result in zip(payload["cases"], raw["results"]):
                self.assertEqual(case["metrics"], result["metrics"])
                self.assertEqual(case["t_modeled_s"], result["t_modeled_s"])
                self.assertEqual(case["layers"], result["layers"])
                self.assertTrue(all("ledger" in op for op in case["operators"]))
                self.assertTrue(all("ledger" not in op for op in result["operators"]))

    def test_cli_310_prefill_keeps_unknown_throughput(self):
        with tempfile.TemporaryDirectory() as tmp, redirect_stdout(io.StringIO()):
            path = Path(tmp) / "partial.html"
            self.assertEqual(main(["--profile", "310", "--workload", "prefill", "--seq", "16",
                                   "--html", str(path)]), 0)
            for case in self.extract_payload(path.read_text(encoding="utf-8"))["cases"]:
                self.assertEqual(case["phase"], "prefill")
                self.assertIsNone(case["modeled_throughput_excluding_unknown_costs"])

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
