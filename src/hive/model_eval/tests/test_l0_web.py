import io
import json
import re
import tempfile
import unittest
from contextlib import redirect_stdout
from math import isclose
from pathlib import Path

from hive.model_eval.common.manifest import build as build_manifest
from hive.model_eval.common.workload import decode, frame, prefill
from hive.model_eval.frontend import builtins  # noqa: F401  (registers built-in models)
from hive.model_eval.frontend.models.qwen3_06b import CONFIG
from hive.model_eval.frontend.registry import get_model
from hive.model_eval.frontend.spec import FORMATS, MXFP8
from hive.model_eval.hw.profiles import G0, G0_PRO, MODEL_ANALYSIS_310
from hive.model_eval.l0.excluded import for_workload
from hive.model_eval.l0.roofline import evaluate
from hive.model_eval.l0.run import main
from hive.model_eval.l0.web import layer_resources, render_html, report_payload


class TestLayerResources(unittest.TestCase):
    def test_layer_totals_match_every_profile_format_and_phase(self):
        model = get_model("Qwen3-0.6B")
        for profile in (G0, G0_PRO, MODEL_ANALYSIS_310):
            for fmt in FORMATS.values():
                for point in (decode(0), decode(4096, 4), prefill(1024, 2), frame()):
                    with self.subTest(profile=profile.name, fmt=fmt.name, point=point):
                        result = evaluate(model, profile, fmt, point)
                        rows = layer_resources(model, profile, fmt, result)
                        self.assertEqual(len(rows), 30)
                        self.assertEqual(len({r["id"] for r in rows}), 30)
                        self.assertTrue(isclose(sum(r["mac"] for r in rows), result.mac))
                        self.assertTrue(isclose(
                            sum(r["traffic_bytes"] for r in rows), result.traffic_bytes
                        ))
                        self.assertTrue(isclose(
                            sum(r["weights_bytes"] for r in rows), result.traffic.breakdown["weights"]
                        ))
                        self.assertTrue(isclose(
                            sum(r["kv_bytes"] for r in rows),
                            result.traffic.breakdown.get("kv_read", result.traffic.breakdown.get("kv_write", 0)),
                        ))

    def test_decode_single_block_and_tails(self):
        model = get_model("Qwen3-0.6B")
        result = evaluate(model, G0, MXFP8, decode(4096))
        rows = layer_resources(model, G0, MXFP8, result)
        self.assertEqual(rows[0]["mac"], 32_592_896)
        self.assertEqual(rows[0]["kv_bytes"], 16_777_216)
        self.assertEqual(rows[-2]["mac"], 2048)
        self.assertEqual(rows[-1]["mac"], 155_582_464)
        self.assertEqual(rows[-1]["tensors"][0]["shape"], [151936, 1024])
        # 7 个投影 + input_norm/post_norm/qk_norm 三组系数权重。
        self.assertEqual(len(rows[0]["tensors"]), 10)

    def test_count_is_not_hardcoded(self):
        model = get_model("Qwen3-0.6B", config={**CONFIG, "num_hidden_layers": 3})
        result = evaluate(model, G0, MXFP8, decode(0))
        self.assertEqual(len(layer_resources(model, G0, MXFP8, result)), 5)

    def test_prefill_head_once_norm_for_all_tokens(self):
        model = get_model("Qwen3-0.6B")
        result = evaluate(model, G0, MXFP8, prefill(1024))
        rows = layer_resources(model, G0, MXFP8, result)
        self.assertEqual(rows[-2]["mac"], 2048 * 1024)
        self.assertEqual(rows[-1]["mac"], 155_582_464)

    def test_resource_time_sums_but_layer_max_is_not_model_time(self):
        model = get_model("Qwen3-0.6B")
        result = evaluate(model, G0, MXFP8, prefill(1024))
        rows = layer_resources(model, G0, MXFP8, result)
        self.assertTrue(isclose(sum(r["t_compute_s"] for r in rows), result.t_compute_s))
        self.assertTrue(isclose(sum(r["t_bandwidth_s"] for r in rows), result.t_bandwidth_s))
        self.assertGreater(sum(r["t_lower_bound_s"] for r in rows), result.t_s)


class TestHtmlExport(unittest.TestCase):
    def payload(self, profile=G0):
        model = get_model("Qwen3-0.6B")
        points = [decode(0), decode(4096), decode(32768)]
        results = [evaluate(model, profile, MXFP8, p) for p in points]
        manifest = build_manifest(
            profile=profile, model_name=model.name, layer="L0", named_inputs={},
            excluded_costs=for_workload(points[0]),
        )
        return report_payload(model, profile, MXFP8, results, manifest)

    def extract_payload(self, html):
        match = re.search(r'<script id="l0-data" type="application/json">(.*?)</script>', html, re.S)
        self.assertIsNotNone(match)
        return json.loads(match.group(1))

    def test_embedded_payload_and_validity(self):
        payload = self.payload()
        html = render_html(payload)
        self.assertNotIn("__L0_PAYLOAD__", html)
        self.assertEqual(self.extract_payload(html), payload)
        self.assertFalse(payload["manifest"]["performance_valid"])
        self.assertFalse(payload["manifest"]["data_valid"])

    def test_unknown_hardware_capacity_remains_unknown(self):
        payload = self.payload(MODEL_ANALYSIS_310)
        self.assertIsNone(payload["hardware"]["sram_bytes_per_core"])
        self.assertIsNone(payload["hardware"]["local_iface_gbps"])

    def test_safe_json_embedding(self):
        payload = self.payload()
        injected = '</script><img src=x onerror="alert(1)"> & \u2028\u2029'
        payload["manifest"]["model"] = injected
        html = render_html(payload)
        self.assertNotIn(injected, html)
        self.assertEqual(self.extract_payload(html)["manifest"]["model"], injected)

    def test_cli_html_export(self):
        with tempfile.TemporaryDirectory() as tmp, redirect_stdout(io.StringIO()):
            path = Path(tmp) / "layers.html"
            self.assertEqual(main(["--seq", "0,4096", "--html", str(path)]), 0)
            payload = self.extract_payload(path.read_text(encoding="utf-8"))
            self.assertEqual(len(payload["cases"]), 2)
            self.assertEqual(payload["manifest"]["profile"], "G0")
            self.assertEqual(len(payload["cases"][0]["layers"]), 30)

    def test_deterministic_html(self):
        self.assertEqual(render_html(self.payload()), render_html(self.payload()))


if __name__ == "__main__":
    unittest.main()
