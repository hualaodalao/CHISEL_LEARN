import contextlib
import io
import json
import tempfile
import unittest
from pathlib import Path

from hive.model_eval.common.opspec import GemmGroup, GemmSpec, VpuSpec
from hive.model_eval.common.workload import decode, prefill
from hive.model_eval.frontend import builtins  # noqa: F401  (registers built-in models)
from hive.model_eval.frontend.models.qwen3_06b import CONFIG
from hive.model_eval.frontend.registry import get_model
from hive.model_eval.frontend.spec import FORMATS, INT8, MXFP8
from hive.model_eval.hw.geometry import load_array_geometry
from hive.model_eval.hw.profiles import G0, G0_PRO, MODEL_ANALYSIS_310
from hive.model_eval.hw.topology import default_topology, explicit_topology
from hive.model_eval.l0.accounts import work_account
from hive.model_eval.l1.compose import lower_plan
from hive.model_eval.l1.params import ModelParams
from hive.model_eval.l1.residency import ExecutionPlan
from hive.model_eval.l1.run import main
from hive.model_eval.l1.timeline import evaluate, estimate_graph, evaluate_graph


def _plan(layers=None):
    config = dict(CONFIG)
    if layers is not None:
        config["num_hidden_layers"] = layers
    return get_model("Qwen3-0.6B", config=config)


class TestMapping(unittest.TestCase):
    def setUp(self):
        self.model = _plan()
        self.geometry = load_array_geometry()

    def ops(self, point, variant="vpu", fmt=MXFP8, model=None):
        return lower_plan(model or self.model, fmt, point, attention=variant,
                          geometry=self.geometry).operations

    def test_decode_semantic_work_matches_l0(self):
        for seq in (0, 1, 4096, 131072):
            for batch in (1, 3):
                point = decode(seq, batch)
                self.assertEqual(sum(op.mac for op in self.ops(point)),
                                 work_account(self.model, point).total)

    def test_prefill_causal_diagonal_and_softmax_are_explicit(self):
        point = prefill(49, 2)
        operations = self.ops(point)
        softmax = sum(op.mac for op in operations if op.kind == "softmax")
        self.assertEqual(sum(op.mac for op in operations) - softmax,
                         work_account(self.model, point).total + 28 * 16 * 128 * 49 * 2)
        self.assertEqual(softmax, 28 * 16 * 49 * 50 * 2 // 2)

    def test_structural_calls_and_head_position(self):
        operations = self.ops(decode(4096))
        self.assertEqual(sum(op.vectors for op in operations if op.kind == "norm"), 729)
        self.assertEqual(sum(op.vectors for op in operations if op.kind == "softmax"), 448)
        weight = [op for op in operations if op.kind == "weight_gemm"]
        self.assertEqual(len(weight), 28 * 7 + 1)
        self.assertEqual(weight[-1].gemms, (GemmGroup(1, 151936, 1024),))

    def test_qkv_and_gate_up_are_independent_forks(self):
        graph = lower_plan(self.model, MXFP8, decode(1), geometry=self.geometry)
        tasks = {task.id: task for task in graph.tasks}
        projections = [tasks[f"block-00/{name}/execute"]
                       for name in ("q_proj", "k_proj", "v_proj")]
        self.assertEqual(len({task.inputs[0].tensor_id for task in projections}), 1)
        for task in projections:
            self.assertFalse(any(other.id in task.dependencies for other in projections))
        gate, up = (tasks[f"block-00/{name}/execute"] for name in ("gate_proj", "up_proj"))
        self.assertEqual(gate.inputs[0], up.inputs[0])
        self.assertNotIn(gate.id, up.dependencies)
        residual = tasks["block-00/attention_residual/execute"]
        self.assertIn("input:hidden", {ref.tensor_id for ref in residual.inputs})
        graph.validate()

    def test_repeat_heads_use_shared_kv_views_not_shared_b_gemm(self):
        graph = lower_plan(_plan(1), MXFP8, decode(32, 2), attention="quantized_gemm",
                           geometry=self.geometry)
        qk = [task for task in graph.tasks if task.parent_op_id == "block-00/qk"
              and task.engine_kind == "GEMM"]
        self.assertEqual(len(qk), 32)
        self.assertTrue(all((task.spec.m, task.spec.n, task.spec.k) == (1, 32, 128) for task in qk))
        self.assertEqual(len({task.inputs[1].tensor_id for task in qk}), 16)
        tensors = {tensor.id: tensor for tensor in graph.tensors}
        views = [tensor for tensor in graph.tensors if tensor.id.startswith("block-00:K:b")]
        self.assertEqual(len({tensor.base_id for tensor in views}), 1)
        self.assertEqual({tensor.base_starts[1] for tensor in views}, set(range(8)))
        self.assertTrue(all(tensors[view.base_id].dtype == "FP16" for view in views))

    def test_explicit_conversion_tasks_and_types(self):
        graph = lower_plan(self.model, INT8, decode(0), geometry=self.geometry)
        gemms = [task for task in graph.tasks if isinstance(task.spec, GemmSpec)]
        self.assertTrue(all(task.spec.c_dtype == "INT32" for task in gemms))
        conversions = [task for task in graph.tasks if isinstance(task.spec, VpuSpec) and task.spec.kind == "convert"]
        self.assertTrue(any(task.spec.input_dtype == "INT32" and task.spec.output_dtype == "FP32" for task in conversions))

    def test_last_prompt_position_view_is_strided_by_batch(self):
        graph = lower_plan(self.model, MXFP8, prefill(7, 3), geometry=self.geometry)
        view = next(tensor for tensor in graph.tensors if tensor.id == "final-norm:last-position")
        self.assertEqual(view.base_starts, (6, 0))
        self.assertEqual(view.base_steps, (7, 1))
        self.assertEqual(view.shape, (3, 1024))

    def test_custom_lowering_is_called_not_overridden(self):
        graph = lower_plan(self.model, MXFP8, decode(0), geometry=self.geometry)
        seen = []
        def custom(model, fmt, point, **kwargs):
            seen.append((model, fmt, point))
            return graph
        result = evaluate(self.model, G0, MXFP8, decode(0), geometry=self.geometry, lowering=custom)
        self.assertIs(result.graph, graph)
        self.assertEqual(len(seen), 1)

    def test_invalid_attention_variant_rejected(self):
        with self.assertRaises(ValueError):
            self.ops(decode(1), variant="bf16_gemm")


class TestTimeline(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.model = _plan()
        cls.small = _plan(2)
        cls.geometry = load_array_geometry()

    def test_weight_only_anchors_survive_new_backend(self):
        result = evaluate(self.model, G0, MXFP8, decode(0), geometry=self.geometry)
        self.assertEqual(result.metrics["array_cycles"], 19788544)
        self.assertEqual(result.metrics["b_logical_bytes"], 595984384)
        self.assertEqual(result.metrics["c_rows"], 31000)
        self.assertEqual(result.metrics["norm_calls"], 729)
        self.assertEqual(result.metrics["array_utilization"], 1 / 17)

    def test_unknown_special_conversion_handoff_blocks_complete_throughput(self):
        result = evaluate(self.small, G0, MXFP8, decode(1), geometry=self.geometry)
        self.assertIsNone(result.makespan_s)
        self.assertIsNone(result.modeled_throughput)
        self.assertGreater(result.known_service_budget_s, 0)
        self.assertTrue(any(phase["seconds"] is None for task in result.to_dict()["tasks"] for phase in task["phases"]))
        self.assertFalse(result.performance_valid)
        self.assertFalse(result.data_valid)

    def test_service_budget_is_not_parallel_elapsed_time(self):
        params = ModelParams(sync_us=0)
        topology = explicit_topology(G0, self.geometry, params, gemm_instances=2, evidence="synthetic duplicate GEMM scenario")
        result = evaluate(self.small, G0, MXFP8, decode(1), params=params, topology=topology,
                          geometry=self.geometry, scheduling_policy="list")
        payload = result.to_dict()
        self.assertAlmostEqual(sum(layer["known_service_budget_s"] for layer in payload["layers"]), result.known_service_budget_s)
        if result.projection_makespan_s is not None:
            self.assertGreaterEqual(result.known_service_budget_s, result.projection_makespan_s)
            self.assertAlmostEqual(sum(result.projection_attribution_s.values()), result.projection_makespan_s)
        self.assertFalse(result.cross_level_comparable)

    def test_310_keeps_missing_services_and_capacity_unknown(self):
        result = evaluate(self.small, MODEL_ANALYSIS_310, MXFP8, prefill(16), geometry=self.geometry)
        self.assertIsNone(result.makespan_s)
        self.assertIsNone(result.modeled_throughput)
        self.assertFalse(result.cross_level_comparable)
        self.assertTrue(result.excluded_costs)

    def test_dense_masked_attention_work_stays_separate(self):
        result = evaluate(self.small, G0, MXFP8, prefill(17), attention="quantized_gemm", geometry=self.geometry)
        self.assertGreater(result.metrics["masked_macs"], 0)
        self.assertGreater(result.metrics["padded_macs"], 0)
        self.assertTrue(any("quantization" in item or "quantized" in item for item in result.excluded_costs))

    def test_requested_resident_does_not_silently_stream(self):
        with self.assertRaises(ValueError):
            evaluate(self.small, G0, MXFP8, decode(0), geometry=self.geometry,
                     execution_plan=ExecutionPlan("resident", "stream"))

    def test_debug_and_repeated_runs_do_not_change_schedule(self):
        result = evaluate(self.small, G0, MXFP8, decode(16), geometry=self.geometry)
        brief, debug = result.to_dict(), result.to_dict(True)
        self.assertEqual(brief["metrics"], debug["metrics"])
        self.assertEqual(brief["schedule"], debug["schedule"])
        self.assertNotIn("ledger", brief["operators"][0])
        self.assertIn("ledger", debug["operators"][0])
        second = evaluate(self.small, G0, MXFP8, decode(16), geometry=self.geometry)
        self.assertEqual(result.to_dict(True), second.to_dict(True))

    def test_handoff_counts_dependencies_not_list_neighbors(self):
        result = evaluate(self.small, G0, MXFP8, decode(1), geometry=self.geometry, params=ModelParams(sync_us=.5))
        tasks = result.to_dict()["tasks"]
        phases = [phase for task in tasks for phase in task["phases"] if ":handoff-" in phase["id"]]
        self.assertTrue(phases)
        self.assertTrue(all(phase["category"] == "handoff" and phase["seconds"] == .5e-6 for phase in phases))

    def test_injected_costs_still_receive_handoffs(self):
        graph = lower_plan(self.small, MXFP8, decode(1), geometry=self.geometry)
        topology = default_topology(G0, self.geometry, ModelParams())
        plan = ExecutionPlan("stream", "stream")
        costs = estimate_graph(graph, topology, plan)
        scheduled = evaluate_graph(graph, topology, execution_plan=plan,
                                   scheduling_policy="serial", costs=costs)
        phase_ids = {phase.phase_id for task in scheduled.tasks for phase in task.phases}
        self.assertTrue(any(":handoff-" in phase_id for phase_id in phase_ids))


class TestCli(unittest.TestCase):
    def invoke(self, *args):
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            self.assertEqual(main(list(args)), 0)
        return output.getvalue()

    def test_json_contains_graph_instances_phases_and_manifest(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "l1.json"
            self.invoke("--seq", "0", "--attention", "vpu", "--json", str(path), "--debug-ledger")
            payload = json.loads(path.read_text())
        self.assertEqual(payload["schema_version"], 2)
        case = payload["results"][0]
        self.assertEqual(len(case["layers"]), 31)
        self.assertTrue(case["tasks"])
        self.assertTrue(case["topology"]["resources"])
        self.assertFalse(payload["manifest"]["performance_valid"])
        self.assertTrue(any(source["path"].endswith("l1/gemm_model.py") for source in payload["manifest"]["sources"]))
        self.assertIsNone(case["makespan_s"])

    def test_invalid_inputs_and_unproven_multiinstance_are_rejected(self):
        for args in (("--seq", ""), ("--seq", "-1"), ("--batch", "0"),
                     ("--overlap",), ("--sync-us", "nan"), ("--gemm-instances", "2"),
                     ("--workload", "prefill", "--seq", "0")):
            with contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit) as caught:
                main(list(args))
            self.assertEqual(caught.exception.code, 2)

    def test_json_digest_and_output_are_deterministic(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "l1.json"
            args = ("--seq", "0", "--attention", "vpu", "--json", str(path))
            first = self.invoke(*args)
            payload = path.read_text()
            self.assertEqual(self.invoke(*args), first)
            self.assertEqual(path.read_text(), payload)


if __name__ == "__main__":
    unittest.main()
