import math
import unittest

from hive.model_eval.common.workload import decode
from hive.model_eval.frontend.qwen3_06b import CONFIG, build
from hive.model_eval.frontend.spec import MXFP8
from hive.model_eval.hw.geometry import load_array_geometry
from hive.model_eval.hw.profiles import G0
from hive.model_eval.l1.timeline import evaluate


class TestOperatorCostView(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.geometry = load_array_geometry()
        cls.model = build({**CONFIG, "num_hidden_layers": 1})
        cls.result = evaluate(cls.model, G0, MXFP8, decode(0),
                              attention="vpu", geometry=cls.geometry)
        cls.payload = cls.result.to_dict()

    def test_payload_exposes_cost_view(self):
        for key in ("operator_costs", "bounds", "coverage", "top_unknowns"):
            self.assertIn(key, self.payload)
        self.assertEqual(len(self.payload["operator_costs"]), len(self.result.selected_costs))
        self.assertEqual(self.payload["bounds"]["achievability"], "lower_bound")
        self.assertFalse(self.payload["bounds"]["complete"])
        self.assertEqual(self.payload["coverage"]["tasks_total"],
                         len(self.payload["operator_costs"]))
        self.assertEqual(self.payload["coverage"]["tasks_known"]
                         + self.payload["coverage"]["tasks_unknown"],
                         self.payload["coverage"]["tasks_total"])

    def test_known_budget_matches_selected_costs(self):
        total = math.fsum(row["known_seconds"] for row in self.payload["operator_costs"])
        self.assertAlmostEqual(total, self.result.known_service_budget_s, places=12)

    def test_dram_bound_uses_traffic_once(self):
        bytes_total = sum(row["dram_read_bytes"] + row["dram_write_bytes"]
                          for row in self.payload["operator_costs"])
        expected = bytes_total / self.result.dram_effective_bytes_per_s
        self.assertAlmostEqual(self.payload["bounds"]["dram_lb_s"], expected, places=12)

    def test_lower_bound_is_max_of_components(self):
        bounds = self.payload["bounds"]
        values = list(bounds["engine_busy_lb_s"].values())
        values.extend((bounds["dram_lb_s"], bounds["critical_path_lb_s"]))
        self.assertAlmostEqual(bounds["lower_bound_s"],
                               max(value for value in values if value is not None), places=12)

    def test_engine_busy_bound_counts_partial_budgets(self):
        bounds = self.payload["bounds"]
        expected = {}
        for row in self.payload["operator_costs"]:
            key = row["instance_id"] or "unassigned"
            expected[key] = expected.get(key, 0.0) + row["known_seconds"]
        self.assertEqual(set(expected), set(bounds["engine_busy_lb_s"]))
        for key, value in expected.items():
            self.assertAlmostEqual(bounds["engine_busy_lb_s"][key], value, places=12)
        self.assertTrue(any(value > 0.0 and not bounds["engine_busy_complete"][key]
                            for key, value in expected.items()))

    def test_unknown_causes_collapse_handoff_phases(self):
        causes = {item["cause"] for item in self.payload["top_unknowns"]}
        self.assertIn("handoff", causes)
        self.assertFalse(any(cause.startswith("handoff-") for cause in causes))
        self.assertTrue(any(item["category"] == "global" for item in self.payload["top_unknowns"]))

    def test_compact_view_keeps_cost_view(self):
        compact = self.result.to_dict(detailed=False)
        self.assertIn("operator_costs", compact)
        self.assertIn("bounds", compact)
        self.assertNotIn("tasks", compact)
        self.assertNotIn("topology", compact)
        self.assertNotIn("buffer_plan", compact)


if __name__ == "__main__":
    unittest.main()
