"""Phase 1: user-controlled engine dispatch via DispatchPolicy.

The emit layer chooses the engine for auxiliary convert/pad/k-merge/join-heads
tasks; the builder only constructs the typed spec it is told.  Defaults must
reproduce the historical all-VPU lowering exactly, and non-VPU dispatch is not
modeled yet (Phase 2).
"""

import math
import unittest
from dataclasses import replace

from hive.model_eval.common.opspec import GemmSpec, LogicalOp, PrimitiveTask, Tensor, TransferSpec
from hive.model_eval.common.workload import decode
from hive.model_eval.frontend import builtins  # noqa: F401  (registers layers/models)
from hive.model_eval.frontend.models.qwen3_06b import CONFIG
from hive.model_eval.frontend.registry import get_model
from hive.model_eval.frontend.spec import INT8, MXFP8
from hive.model_eval.hw.geometry import load_array_geometry
from hive.model_eval.hw.profiles import G0
from hive.model_eval.hw.topology import default_topology
from hive.model_eval.l1 import gemm_model
from hive.model_eval.l1.compose import lower_plan
from hive.model_eval.l1.emitters import DispatchPolicy
from hive.model_eval.l1.graph_builder import GraphBuilder
from hive.model_eval.l1.params import ModelParams
from hive.model_eval.l1.residency import ExecutionPlan
from hive.model_eval.l1.timeline import evaluate


def _small_plan():
    return get_model("Qwen3-0.6B", config={**CONFIG, "num_hidden_layers": 1})


class TestDispatchPolicy(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.geometry = load_array_geometry()
        cls.plan = _small_plan()

    def test_explicit_default_policy_matches_implicit(self):
        point = decode(1)
        implicit = evaluate(self.plan, G0, MXFP8, point, geometry=self.geometry)
        explicit = evaluate(self.plan, G0, MXFP8, point, geometry=self.geometry,
                            dispatch=DispatchPolicy())
        self.assertEqual(implicit.to_dict(True), explicit.to_dict(True))

    def test_default_policy_lower_plan_matches_implicit(self):
        point = decode(1)
        implicit = lower_plan(self.plan, MXFP8, point, geometry=self.geometry)
        explicit = lower_plan(self.plan, MXFP8, point, geometry=self.geometry,
                              dispatch=DispatchPolicy())
        self.assertEqual([task.id for task in implicit.tasks],
                         [task.id for task in explicit.tasks])
        self.assertEqual(implicit.validate(), explicit.validate())

    def test_unsupported_convert_engine_is_not_modeled(self):
        with self.assertRaisesRegex(ValueError, "conversion cost is not modeled"):
            lower_plan(self.plan, MXFP8, decode(1), geometry=self.geometry,
                       dispatch=DispatchPolicy(convert="GEMM"))

    def test_non_vpu_pad_is_not_modeled(self):
        op = LogicalOp("op", "layer", "tail", "gemm")
        builder = GraphBuilder(32, operations=(op,))
        a = builder.tensor("a", (1, 500), "MXFP8", external=True)
        b = builder.tensor("b", (500, 17), "MXFP8", external=True, scale_axis=0)
        with self.assertRaisesRegex(ValueError, "pad/repack cost is not modeled"):
            builder.gemm(op, a, b, GemmSpec(1, 17, 500), self.geometry,
                         pad_engine="DMA")

    def test_non_vpu_k_merge_is_not_modeled(self):
        geometry = replace(self.geometry, mnk_width=8)
        op = LogicalOp("op", "layer", "tail", "gemm")
        builder = GraphBuilder(32, operations=(op,))
        a = builder.tensor("a", (2, 500), "MXFP8", external=True)
        b = builder.tensor("b", (500, 17), "MXFP8", external=True, scale_axis=0)
        with self.assertRaisesRegex(ValueError, "K-merge cost is not modeled"):
            builder.gemm(op, a, b, GemmSpec(2, 17, 500), geometry,
                         merge_engine="DMA")


class TestGemmEpilogue(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.geometry = load_array_geometry()
        cls.plan = _small_plan()

    def _estimate(self, spec):
        commands = gemm_model.legalize(gemm_model.GemmShape(
            spec.m, spec.n, spec.k, spec.a_fmt, spec.b_fmt,
            block_size=spec.block_size), self.geometry)
        k = sum(c.k for c in commands if c.m_offset == 0 and c.n_offset == 0)
        n = sum(c.n for c in commands if c.m_offset == 0 and c.k_offset == 0)
        spec = replace(spec, a_storage_shape=(spec.m, k), b_storage_shape=(k, n))
        task = PrimitiveTask("gemm", "op", "GEMM", spec)
        config = default_topology(G0, self.geometry, ModelParams()).instances["gemm0"]
        return gemm_model.estimate(task, config, ExecutionPlan())

    def test_fp16_epilogue_halves_output_bytes_not_array_cycles(self):
        fp32 = self._estimate(GemmSpec(1, 16, 32, c_dtype="FP32"))
        fp16 = self._estimate(GemmSpec(1, 16, 32, c_dtype="FP16"))
        self.assertEqual(fp16.metrics["c_logical_bytes"] * 2,
                         fp32.metrics["c_logical_bytes"])
        self.assertEqual(fp16.metrics["array_cycles"], fp32.metrics["array_cycles"])
        self.assertEqual(fp16.metrics["c_rows"], fp32.metrics["c_rows"])
        self.assertTrue(fp16.complete)

    def test_integer_gemm_fp32_epilogue_removes_dequantize_task(self):
        default = lower_plan(self.plan, INT8, decode(1), geometry=self.geometry)
        fused = lower_plan(self.plan, INT8, decode(1), geometry=self.geometry,
                           dispatch=DispatchPolicy(gemm_output="FP32"))
        self.assertTrue(any(task.id.endswith("/dequantize-output")
                            for task in default.tasks))
        self.assertFalse(any(task.id.endswith("/dequantize-output")
                             for task in fused.tasks))
        tensors = {tensor.id: tensor for tensor in fused.tensors}
        for task in fused.tasks:
            if task.engine_kind == "GEMM" and task.spec.weight:
                self.assertEqual(task.spec.c_dtype, "FP32")
                self.assertEqual(tensors[task.outputs[0].tensor_id].dtype, "FP32")
        fused.validate()

    def test_fp16_epilogue_keeps_explicit_dequantize_convert(self):
        graph = lower_plan(self.plan, MXFP8, decode(1), geometry=self.geometry,
                           dispatch=DispatchPolicy(gemm_output="FP16"))
        tensors = {tensor.id: tensor for tensor in graph.tensors}
        gemms = [task for task in graph.tasks
                 if task.engine_kind == "GEMM" and task.spec.weight]
        self.assertTrue(gemms)
        for task in gemms:
            self.assertEqual(task.spec.c_dtype, "FP16")
            self.assertEqual(tensors[task.outputs[0].tensor_id].dtype, "FP16")
        dequant = [task for task in graph.tasks
                   if task.id.endswith("/dequantize-output")]
        self.assertTrue(dequant)
        for task in dequant:
            self.assertEqual(task.spec.input_dtype, "FP16")
            self.assertEqual(task.spec.output_dtype, "FP32")
        graph.validate()

    def test_unmodeled_epilogue_output_is_rejected(self):
        for dtype in ("INT32", "FP8"):
            with self.subTest(dtype=dtype), self.assertRaisesRegex(ValueError, "not modeled"):
                lower_plan(self.plan, MXFP8, decode(1), geometry=self.geometry,
                           dispatch=DispatchPolicy(gemm_output=dtype))

    def test_mx_epilogue_reduces_output_bytes_not_array_cycles(self):
        fp32 = self._estimate(GemmSpec(1, 16, 32, c_dtype="FP32"))
        mx8 = self._estimate(GemmSpec(1, 16, 32, c_dtype="MXFP8"))
        mx4 = self._estimate(GemmSpec(1, 16, 32, c_dtype="MXFP4"))
        self.assertLess(mx4.metrics["c_logical_bytes"], mx8.metrics["c_logical_bytes"])
        self.assertLess(mx8.metrics["c_logical_bytes"], fp32.metrics["c_logical_bytes"])
        for cost in (mx8, mx4):
            self.assertEqual(cost.metrics["array_cycles"], fp32.metrics["array_cycles"])
            self.assertEqual(cost.metrics["c_rows"], fp32.metrics["c_rows"])
            self.assertTrue(cost.complete)

    def test_mx_scale_is_counted_in_output_bytes(self):
        cost = self._estimate(GemmSpec(2, 64, 32, c_dtype="MXFP8", block_size=32))
        expected = Tensor("c", (2, 64), "MXFP8", block_size=32, scale_axis=-1).size_bytes
        self.assertEqual(cost.metrics["c_logical_bytes"], expected)
        self.assertGreater(expected, 2 * 64)  # scale adds bytes beyond pure element data

    def test_mx_epilogue_output_tensor_is_mx_with_explicit_dequantize(self):
        graph = lower_plan(self.plan, MXFP8, decode(1), geometry=self.geometry,
                           dispatch=DispatchPolicy(gemm_output="MXFP8"))
        tensors = {tensor.id: tensor for tensor in graph.tensors}
        gemms = [task for task in graph.tasks
                 if task.engine_kind == "GEMM" and task.spec.weight]
        self.assertTrue(gemms)
        for task in gemms:
            self.assertEqual(task.spec.c_dtype, "MXFP8")
            out = tensors[task.outputs[0].tensor_id]
            self.assertEqual(out.dtype, "MXFP8")
            self.assertEqual(out.scale_axis % len(out.shape), len(out.shape) - 1)
        dequant = [task for task in graph.tasks if task.id.endswith("/dequantize-output")]
        self.assertTrue(dequant)
        for task in dequant:
            self.assertEqual(task.spec.input_dtype, "MXFP8")
            self.assertEqual(task.spec.output_dtype, "FP32")
        graph.validate()


class TestDmaConversionDispatch(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.geometry = load_array_geometry()
        cls.plan = _small_plan()
        cls.default = evaluate(cls.plan, G0, MXFP8, decode(0), attention="vpu",
                               geometry=cls.geometry, dispatch=DispatchPolicy(convert="DMA"))
        cls.scenario = evaluate(cls.plan, G0, MXFP8, decode(0), attention="vpu",
                                geometry=cls.geometry, dispatch=DispatchPolicy(convert="DMA"),
                                params=ModelParams(dma_conversion_elements_per_cycle=8))

    def _dma_converts(self, result):
        return [task for task in result.graph.tasks
                if task.engine_kind == "DMA" and isinstance(task.spec, TransferSpec)
                and task.spec.input_dtype]

    def test_dma_dispatch_routes_dtype_converts_and_keeps_graph_valid(self):
        graph = lower_plan(self.plan, MXFP8, decode(1), geometry=self.geometry,
                           dispatch=DispatchPolicy(convert="DMA"))
        tensors = {tensor.id: tensor for tensor in graph.tensors}
        converts = [task for task in graph.tasks
                    if task.engine_kind == "DMA" and isinstance(task.spec, TransferSpec)
                    and task.spec.input_dtype]
        self.assertTrue(converts)
        for task in converts:
            spec = task.spec
            self.assertEqual(tensors[task.inputs[0].tensor_id].dtype, spec.input_dtype)
            self.assertEqual(tensors[task.outputs[0].tensor_id].dtype, spec.output_dtype)
            self.assertEqual(spec.read_bytes, tensors[task.inputs[0].tensor_id].size_bytes)
            self.assertEqual(spec.write_bytes, tensors[task.outputs[0].tensor_id].size_bytes)
        graph.validate()

    def test_dma_convert_read_bytes_differ_from_write_bytes(self):
        graph = lower_plan(self.plan, MXFP8, decode(1), geometry=self.geometry,
                           dispatch=DispatchPolicy(convert="DMA"))
        quantize = next(task for task in graph.tasks
                        if task.id.endswith("/quantize-input") and task.engine_kind == "DMA")
        # FP32 activation -> MXFP8: write bytes (data + scale) < read bytes.
        self.assertGreater(quantize.spec.read_bytes, quantize.spec.write_bytes)
        self.assertNotEqual(quantize.spec.read_bytes, quantize.spec.write_bytes)

    def _conversion_phase(self, result):
        task = self._dma_converts(result)[0]
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
        self.assertIn("dma_conversion_elements_per_cycle=8", phase.evidence)

    def test_coverage_improves_with_scenario_values(self):
        before = self.default.to_dict()["coverage"]
        after = self.scenario.to_dict()["coverage"]
        self.assertGreater(after["tasks_known"], before["tasks_known"])


if __name__ == "__main__":
    unittest.main()