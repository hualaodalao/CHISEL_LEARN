"""Phase 6 extension points: split, dispatch, engine estimator and memory domains."""

import unittest
from dataclasses import replace

from hive.model_eval.common.opspec import (ENGINE_SPECS, CpuSpec, GemmSpec,
                                           LogicalOp, PrimitiveTask, TaskGraph,
                                           VpuSpec, register_engine_kind)
from hive.model_eval.common.workload import decode
from hive.model_eval.frontend import builtins  # noqa: F401  (registers models)
from hive.model_eval.frontend.models.qwen3_06b import CONFIG
from hive.model_eval.frontend.registry import get_model
from hive.model_eval.frontend.spec import MXFP8
from hive.model_eval.hw.geometry import load_array_geometry
from hive.model_eval.hw.profiles import G0
from hive.model_eval.hw.topology import (EngineConfig, MemoryDomain, Resource,
                                          Topology, default_topology)
from hive.model_eval.l1 import kernels, scheduler
from hive.model_eval.l1.dispatchers import dispatch, register_dispatcher
from hive.model_eval.l1.gemm_model import legalize
from hive.model_eval.l1.graph_builder import GraphBuilder
from hive.model_eval.l1.ledger import EngineCost, Phase, ResourceClaim
from hive.model_eval.l1.params import ModelParams
from hive.model_eval.l1.residency import ExecutionPlan
from hive.model_eval.l1.split_policies import register_split_policy, split_commands
from hive.model_eval.l1.timeline import evaluate


class TestSplitPolicyRegistry(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.geometry = load_array_geometry()
        cls.calls = []

        def counted(shape, geometry):
            cls.calls.append(shape)
            return legalize(shape, geometry)

        register_split_policy("test-counted", counted)

    def test_builder_and_estimator_share_one_policy(self):
        builder = GraphBuilder(32, split_policy="test-counted")
        a = builder.tensor("a", (1, 32), "MXFP8", external=True)
        b = builder.tensor("b", (32, 16), "MXFP8", external=True)
        op = builder.add_op(LogicalOp("op/gemm", "op", "gemm", "weight_gemm",
                                      mac=1 * 16 * 32))
        builder.gemm(op, a, b, GemmSpec(m=1, n=16, k=32, a_fmt="MXFP8",
                                        b_fmt="MXFP8", c_dtype="FP32", block_size=32,
                                        semantic_macs=1 * 16 * 32, weight=True), self.geometry)
        task = builder.tasks[0]
        self.assertEqual(task.spec.split_policy, "test-counted")
        topology = default_topology(G0, self.geometry, ModelParams())
        calls_before = len(self.calls)
        cost = kernels.estimate(task, topology.instances["gemm0"],
                                ExecutionPlan("stream", "stream"))
        self.assertEqual(len(self.calls), calls_before + 1)
        self.assertGreater(cost.metrics["issued_macs"], 0)

    def test_unknown_policy_is_rejected(self):
        with self.assertRaises(KeyError):
            split_commands(None, self.geometry, "no-such-policy")


class TestDispatcherRegistry(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.calls = []

        def recorder(graph, costs, topology, buffer_plan):
            cls.calls.append(len(graph.tasks))
            return dispatch("serial", graph, costs, topology, buffer_plan)

        register_dispatcher("test-recorder", recorder)

    def test_evaluate_uses_injected_dispatcher(self):
        plan = get_model("Qwen3-0.6B", config={**CONFIG, "num_hidden_layers": 1})
        result = evaluate(plan, G0, MXFP8, decode(0), dispatcher="test-recorder",
                          geometry=load_array_geometry())
        self.assertEqual(self.calls, [len(result.graph.tasks)])
        self.assertEqual(result.schedule.policy, "serial")

    def test_unknown_dispatcher_is_rejected(self):
        plan = get_model("Qwen3-0.6B", config={**CONFIG, "num_hidden_layers": 1})
        with self.assertRaises(KeyError):
            evaluate(plan, G0, MXFP8, decode(0), dispatcher="no-such-dispatcher",
                     geometry=load_array_geometry())


class FakeSpec:
    pass


class TestEngineRegistry(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if "TESTENGINE" not in ENGINE_SPECS:
            register_engine_kind("TESTENGINE", FakeSpec)
            kernels.register_estimator("TESTENGINE", lambda task, config, plan: EngineCost(
                task.id, config.id, (), (config.resolve("engine"),), metrics={"fake": 1}))

    def test_new_kind_flows_through_task_topology_and_estimator(self):
        geometry = load_array_geometry()
        task = PrimitiveTask("t", "op", "TESTENGINE", FakeSpec())
        instance = EngineConfig("test0", "TESTENGINE", "core0", G0, geometry, ModelParams(),
                                {"engine": "core0.test0", "slot": "core0.test0"}, "dram", "test")
        topology = Topology(
            {"test0": instance},
            {"dram": Resource("dram", "bandwidth", 1e9),
             "core0.test0": Resource("core0.test0", "slot", 1)},
            {"dram": MemoryDomain("dram", None, None)}, "test")
        self.assertEqual(topology.candidates(task), (instance,))
        cost = kernels.estimate(task, instance, ExecutionPlan("stream", "stream"))
        self.assertEqual(cost.metrics, {"fake": 1})

    def test_unknown_kind_is_rejected(self):
        with self.assertRaises(ValueError):
            PrimitiveTask("t", "op", "NO-SUCH-ENGINE", FakeSpec())
        with self.assertRaises(KeyError):
            kernels.register_estimator("NO-SUCH-ENGINE", lambda *args: None)


class TestPhaseLease(unittest.TestCase):
    def test_non_engine_lease_does_not_cancel_phase_claim(self):
        geometry = load_array_geometry()
        topology = default_topology(G0, geometry, ModelParams())
        instance = topology.instances["cpu0"]
        phase = Phase("cpu0:p", 1.0, "cpu", claims=(ResourceClaim("port.local", 1.0),))
        cost = EngineCost("task", instance.id, (phase,), lease_resources=("core0.local",))
        leases, claims = scheduler._claims(cost, instance, topology)
        self.assertIn("core0.local", leases)
        # The phase claim must survive; only the engine lease is implicit.
        self.assertEqual(claims["cpu0:p"], {"core0.local": 1.0})


class TestGemmMemoryDomain(unittest.TestCase):
    def setUp(self):
        from hive.model_eval.frontend import qwen3_06b as legacy
        from hive.model_eval.l1.mapping import lower_model
        model = legacy.build()
        small = replace(model, layer_types=(replace(model.layer_types[0], count=1),))
        graph = lower_model(small, MXFP8, decode(1), geometry=load_array_geometry())
        self.task = next(task for task in graph.tasks if task.engine_kind == "GEMM")
        self.geometry = load_array_geometry()
        self.instance = default_topology(G0, self.geometry, ModelParams()).instances["gemm0"]
        self.plan = ExecutionPlan("stream", "stream")

    def test_all_dram_operands_charge_dram(self):
        cost = kernels.estimate(self.task, self.instance, self.plan)
        self.assertGreater(cost.metrics["dram_bytes"], 0)

    def test_local_operands_skip_dram_traffic(self):
        spec = replace(self.task.spec, a_domain="core0.sram",
                       b_domain="core0.sram", c_domain="core0.sram")
        cost = kernels.estimate(replace(self.task, spec=spec), self.instance, self.plan)
        self.assertEqual(cost.metrics["dram_bytes"], 0)
        self.assertEqual(cost.metrics["weight_dram_bytes"], 0)
        self.assertTrue(cost.traffic)
        self.assertTrue(all(flow.memory_domain == "core0.sram" for flow in cost.traffic))

    def test_builder_carries_tensor_domains_into_gemm_spec(self):
        builder = GraphBuilder(32)
        a = builder.tensor("a", (1, 32), "MXFP8", external=True,
                           memory_domain="core0.sram")
        b = builder.tensor("b", (32, 16), "MXFP8", external=True,
                           memory_domain="core0.sram")
        op = builder.add_op(LogicalOp("op/gemm", "op", "gemm", "weight_gemm",
                                      mac=1 * 16 * 32))
        builder.gemm(op, a, b, GemmSpec(m=1, n=16, k=32, a_fmt="MXFP8",
                                        b_fmt="MXFP8", c_dtype="FP32", block_size=32,
                                        semantic_macs=1 * 16 * 32, weight=True,
                                        c_domain="core0.sram"), self.geometry)
        task = builder.tasks[0]
        self.assertEqual(task.spec.a_domain, "core0.sram")
        self.assertEqual(task.spec.b_domain, "core0.sram")
        output = builder.tensors[task.outputs[0].tensor_id]
        self.assertEqual(output.memory_domain, "core0.sram")


if __name__ == "__main__":
    unittest.main()
