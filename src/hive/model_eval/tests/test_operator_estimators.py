import unittest

from hive.model_eval.common.opspec import PrimitiveTask, VpuSpec
from hive.model_eval.common.workload import decode
from hive.model_eval.frontend import builtins  # noqa: F401  (registers built-in models)
from hive.model_eval.frontend.models.qwen3_06b import CONFIG
from hive.model_eval.frontend.registry import get_model
from hive.model_eval.frontend.spec import MXFP8
from hive.model_eval.hw.geometry import load_array_geometry
from hive.model_eval.hw.profiles import G0
from hive.model_eval.hw.topology import default_topology
from hive.model_eval.l1 import engines
from hive.model_eval.l1.costing.registry import (list_operator_estimators,
                                                 lookup_operator_estimator,
                                                 register_operator_estimator,
                                                 simple_cost)
from hive.model_eval.l1.ledger import EngineCost
from hive.model_eval.l1.params import ModelParams
from hive.model_eval.l1.residency import ExecutionPlan
from hive.model_eval.l1.timeline import evaluate


class TestOperatorRegistry(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.geometry = load_array_geometry()
        cls.topology = default_topology(G0, cls.geometry, ModelParams())
        cls.plan = ExecutionPlan()

    def test_builtin_forms_are_registered(self):
        pairs = set(list_operator_estimators())
        self.assertIn(("norm", "VPU"), pairs)
        self.assertIn(("softmax", "VPU"), pairs)
        self.assertIn(("transfer", "DMA"), pairs)
        self.assertIn(("sampling", "CPU"), pairs)
        self.assertIn(("gemm", "GEMM"), pairs)

    def test_scalar_operator_can_be_registered_without_engine_changes(self):
        def estimate(task, config, plan):
            return simple_cost(task, config, 1e-6, dram_read_bytes=128,
                               local_bytes=256, evidence="acme scenario")

        register_operator_estimator("acme_pool", "VPU", estimate, replace=True)
        task = PrimitiveTask("acme/pool", "acme", "VPU",
                             VpuSpec("acme_pool", elements=1024))
        cost = engines.estimate(task, self.topology.instances["vpu0"], self.plan)
        self.assertIsInstance(cost, EngineCost)
        self.assertEqual(cost.instance_id, "vpu0")
        self.assertAlmostEqual(cost.known_service_s, 1e-6, places=15)
        self.assertTrue(cost.complete)
        self.assertEqual(cost.metrics["dram_read_bytes"], 128)
        self.assertEqual(cost.metrics["local_bytes"], 256)
        self.assertEqual(sum(flow.logical_bytes for flow in cost.traffic), 384)

    def test_duplicate_registration_requires_replace(self):
        def estimate(task, config, plan):
            return simple_cost(task, config, 1.0)

        register_operator_estimator("acme_dup", "VPU", estimate, replace=True)
        with self.assertRaises(ValueError):
            register_operator_estimator("acme_dup", "VPU", estimate)

    def test_unknown_scalar_cost_stays_unknown(self):
        task = PrimitiveTask("acme/unknown", "acme", "VPU", VpuSpec("acme_unknown"))
        register_operator_estimator("acme_unknown", "VPU",
                                    lambda task, config, plan: simple_cost(
                                        task, config, None, evidence="no service model"),
                                    replace=True)
        cost = engines.estimate(task, self.topology.instances["vpu0"], self.plan)
        self.assertFalse(cost.complete)
        self.assertEqual(cost.known_service_s, 0.0)
        self.assertTrue(cost.excluded_costs)

    def test_existing_kind_still_resolves(self):
        task = PrimitiveTask("vpu/norm", "op", "VPU", VpuSpec("norm", elements=1024))
        self.assertIsNotNone(lookup_operator_estimator(task))
        cost = engines.estimate(task, self.topology.instances["vpu0"], self.plan)
        self.assertGreater(cost.known_service_s, 0.0)

    def test_engine_modules_remain_compatibility_wrappers(self):
        from hive.model_eval.l1 import cpu_model, dma_model, gemm_model, vpu_model
        from hive.model_eval.l1.costing.forms import gemm, sampling, transfer, vpu
        from hive.model_eval.l1.costing import gemm_commands
        self.assertIs(vpu_model.estimate, vpu.estimate)
        self.assertIs(dma_model.estimate, transfer.estimate)
        self.assertIs(cpu_model.estimate, sampling.estimate)
        self.assertIs(gemm_model.estimate, gemm.estimate)
        self.assertIs(gemm_commands.legalize, gemm_model.legalize)


class TestVpuScenarioParameters(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.geometry = load_array_geometry()
        cls.model = get_model("Qwen3-0.6B", config={**CONFIG, "num_hidden_layers": 1})
        cls.default = evaluate(cls.model, G0, MXFP8, decode(0),
                               attention="vpu", geometry=cls.geometry)
        cls.scenario = evaluate(cls.model, G0, MXFP8, decode(0),
                                attention="vpu", geometry=cls.geometry,
                                params=ModelParams(vpu_conversion_elements_per_cycle=8,
                                                   vpu_fp32_accum_elements_per_cycle=8))

    def _conversion_phase(self, result):
        task = next(task for task in result.graph.tasks
                    if isinstance(task.spec, VpuSpec) and task.spec.kind == "convert")
        timing = next(row for row in result.schedule.tasks if row.task_id == task.id)
        instance = timing.budget_instance_id or next(iter(result.costs[task.id]))
        cost = result.costs[task.id][instance]
        return next(phase for phase in cost.phases if phase.id.endswith(":conversion"))

    def test_conversion_is_unknown_by_default(self):
        self.assertIsNone(self._conversion_phase(self.default).seconds)

    def test_conversion_uses_scenario_value_when_supplied(self):
        phase = self._conversion_phase(self.scenario)
        self.assertIsNotNone(phase.seconds)
        self.assertEqual(phase.source, "scenario")
        self.assertIn("vpu_conversion_elements_per_cycle=8", phase.evidence)

    def test_coverage_improves_with_scenario_values(self):
        before = self.default.to_dict()["coverage"]
        after = self.scenario.to_dict()["coverage"]
        self.assertGreater(after["tasks_known"], before["tasks_known"])


if __name__ == "__main__":
    unittest.main()
