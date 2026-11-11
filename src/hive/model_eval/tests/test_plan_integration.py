"""Plan-driven L0/L1/report/manifest integration anchors.

The legacy ModelSpec path these results were once compared against has been
removed; the ModelPlan emitter path is now the single source, so the checks
below pin absolute values and internal consistency directly.
"""

import unittest
from pathlib import Path

from hive.model_eval.common.manifest import build as build_manifest
from hive.model_eval.common.workload import decode, prefill
from hive.model_eval.frontend import builtins  # noqa: F401  (registers layers/models)
from hive.model_eval.frontend.models.qwen3_06b import CONFIG
from hive.model_eval.frontend.registry import get_model
from hive.model_eval.frontend.spec import MXFP8
from hive.model_eval.hw.geometry import load_array_geometry
from hive.model_eval.hw.profiles import G0
from hive.model_eval.l0.roofline import evaluate as evaluate_l0
from hive.model_eval.l0.web import layer_resources
from hive.model_eval.l1.timeline import evaluate


def _plan(layers=None):
    if layers is None:
        return get_model("Qwen3-0.6B")
    return get_model("Qwen3-0.6B", config={**CONFIG, "num_hidden_layers": layers})


class TestPlanL1(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.geometry = load_array_geometry()
        cls.plan = _plan(1)

    def test_plan_evaluation_is_self_consistent(self):
        for point in (decode(0), decode(5), prefill(16)):
            for variant in ("vpu", "quantized_gemm"):
                with self.subTest(point=point, variant=variant):
                    result = evaluate(self.plan, G0, MXFP8, point, attention=variant,
                                      geometry=self.geometry)
                    self.assertIsNone(result.makespan_s)
                    self.assertIsNone(result.modeled_throughput)
                    metrics = result.metrics
                    self.assertEqual(metrics["semantic_macs"],
                                     metrics["l0_reference_macs"]
                                     + metrics["extra_semantic_macs_vs_l0"])
                    if result.projection_makespan_s is not None:
                        self.assertAlmostEqual(
                            sum(result.projection_attribution_s.values()),
                            result.projection_makespan_s)

    def test_single_block_decode_anchor(self):
        result = evaluate(self.plan, G0, MXFP8, decode(0), attention="vpu",
                          geometry=self.geometry)
        self.assertEqual(result.metrics["array_cycles"], 5688064)
        self.assertEqual(result.metrics["semantic_macs"], 171334656)

    def test_report_has_aggregate_and_leaf_layers(self):
        result = evaluate(self.plan, G0, MXFP8, decode(5), geometry=self.geometry)
        tree = result.to_dict()["layer_tree"]
        ids = {row["id"] for row in tree}
        self.assertIn("block-00", ids)
        self.assertIn("block-00/attention", ids)
        self.assertIn("block-00/attention/q_proj", ids)
        self.assertIn("block-00/mlp", ids)
        aggregate = next(row for row in tree if row["id"] == "block-00")
        self.assertTrue(aggregate["aggregate"])
        self.assertEqual(aggregate["operators"], 18)
        self.assertTrue(any(not row["aggregate"] for row in tree))


class TestPlanL0(unittest.TestCase):
    def test_layer_resources_match_roofline(self):
        plan = _plan()
        for point in (decode(0), decode(4096, 4), prefill(1024, 2)):
            with self.subTest(point=point):
                result = evaluate_l0(plan, G0, MXFP8, point)
                rows = layer_resources(plan, G0, MXFP8, result)
                self.assertEqual(len(rows), 30)
                self.assertAlmostEqual(sum(row["mac"] for row in rows), result.mac)
                self.assertAlmostEqual(sum(row["traffic_bytes"] for row in rows),
                                       result.traffic_bytes)

    def test_decode_zero_mac_anchor(self):
        result = evaluate_l0(_plan(), G0, MXFP8, decode(0))
        self.assertEqual(int(result.mac), 596588544)

    def test_layer_count_follows_plan(self):
        small = _plan(3)
        result = evaluate_l0(small, G0, MXFP8, decode(0))
        self.assertEqual(len(layer_resources(small, G0, MXFP8, result)), 5)


class TestPlanManifest(unittest.TestCase):
    def test_provenance_includes_model_layer_and_emitter(self):
        plan = _plan(1)
        root = Path(__file__).resolve().parents[4]
        emitters = (root / "src/hive/model_eval/l1/emitters").glob("*.py")
        provenance = [*plan.provenance_paths(), *(path.relative_to(root).as_posix()
                                                   for path in sorted(emitters))]
        manifest = build_manifest(profile=G0, model_name=plan.name, layer="L1",
                                  named_inputs={}, excluded_costs=[],
                                  provenance=provenance, root=root)
        paths = {source.path for source in manifest.sources}
        self.assertTrue(any(path.endswith("Qwen3-0.6B-逐层计算拆解.md") for path in paths))
        self.assertTrue(any(path.endswith("frontend/layers/qwen3.py") for path in paths))
        self.assertTrue(any(path.endswith("l1/emitters/qwen3.py") for path in paths))
        self.assertTrue(any(path.endswith("l1/emitters/generic.py") for path in paths))


if __name__ == "__main__":
    unittest.main()
