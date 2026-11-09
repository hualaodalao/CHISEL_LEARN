import unittest
from dataclasses import replace
from unittest.mock import patch

from hive.model_eval.common.opspec import GemmSpec, LogicalOp, PrimitiveTask, TaskGraph, Tensor, TensorRef
from hive.model_eval.hw.geometry import load_array_geometry
from hive.model_eval.hw.profiles import G0, MODEL_ANALYSIS_310
from hive.model_eval.hw.topology import default_topology
from hive.model_eval.l1 import gemm_model
from hive.model_eval.l1.mapping import _GraphBuilder
from hive.model_eval.l1.params import ModelParams
from hive.model_eval.l1.residency import ExecutionPlan


class TestGemmModel(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.geometry = load_array_geometry()

    def estimate(self, spec=None, *, profile=G0, params=None, plan=None, geometry=None):
        spec = spec or GemmSpec(1, 16, 32)
        geometry = geometry or self.geometry
        commands = gemm_model.legalize(gemm_model.GemmShape(
            spec.m, spec.n, spec.k, spec.a_fmt, spec.b_fmt, block_size=spec.block_size), geometry)
        k = sum(c.k for c in commands if c.m_offset == 0 and c.n_offset == 0)
        n = sum(c.n for c in commands if c.m_offset == 0 and c.k_offset == 0)
        spec = replace(spec, a_storage_shape=(spec.m, k), b_storage_shape=(k, n))
        task = PrimitiveTask("gemm", "op", "GEMM", spec)
        config = default_topology(profile, geometry, params or ModelParams()).instances["gemm0"]
        return gemm_model.estimate(task, config, plan or ExecutionPlan())

    def test_staged_phases_and_whole_instance_lease(self):
        cost = self.estimate(GemmSpec(1, 16, 32, "INT8", "INT8", "INT32"))
        self.assertEqual([phase.id.rsplit(":", 1)[-1] for phase in cost.phases], ["loadW", "consume", "write"])
        self.assertEqual(cost.lease_resources, ("core0.gemm0",))
        self.assertTrue(cost.complete)
        self.assertEqual(cost.metrics["array_cycles"], 17)
        self.assertEqual(cost.metrics["c_beats"], 2)
        for previous, current in zip(cost.phases, cost.phases[1:]):
            self.assertEqual(current.dependencies, (previous.id,))
        self.assertAlmostEqual(cost.known_service_s, sum(phase.seconds for phase in cost.phases))

    def test_no_hidden_vpu_cost(self):
        cost = self.estimate(GemmSpec(2, 17, 500), geometry=replace(self.geometry, mnk_width=8))
        self.assertGreater(cost.metrics["k_split_cells"], 0)
        self.assertTrue(all(phase.category in ("gemm", "dram") for phase in cost.phases))
        self.assertTrue(any("explicit downstream merge" in item for item in cost.excluded_costs))

    def test_same_transfer_has_one_identity_across_interfaces(self):
        cost = self.estimate()
        for phase in cost.phases:
            flows = [flow for flow in cost.traffic if flow.segment == phase.id[len(cost.task_id) + 1:]]
            self.assertEqual(len({flow.stream_id for flow in flows}), 1)
            self.assertEqual({flow.owner_task_id for flow in flows}, {cost.task_id})
        self.assertEqual(sum(flow.logical_bytes for flow in cost.traffic if flow.memory_domain == "dram"), cost.metrics["dram_bytes"])

    def test_resident_weight_staging_and_reuse_are_separate(self):
        spec = GemmSpec(49, 16, 64, weight=True)
        streamed = self.estimate(spec)
        resident = self.estimate(spec, plan=ExecutionPlan("resident", "stream"))
        self.assertEqual(resident.phases[0].id, "gemm:weight-stage")
        self.assertGreater(streamed.metrics["weight_reload_bytes"], 0)
        self.assertEqual(resident.metrics["weight_reload_bytes"], 0)
        self.assertEqual(resident.metrics["weight_dram_bytes"], resident.metrics["weight_unique_bytes"])
        staged = [flow for flow in resident.traffic if flow.segment == "weight-stage"]
        self.assertEqual(len({flow.stream_id for flow in staged}), 1)
        resident_buffer = next(buffer for buffer in resident.buffers if buffer.id.endswith("weight-resident"))
        self.assertEqual(resident_buffer.acquire_phase, resident.phases[0].id)
        self.assertEqual(resident_buffer.release_phase, resident.phases[-1].id)

    def test_a_staging_is_not_folded_into_later_reuse(self):
        cost = self.estimate(GemmSpec(2, 32, 64), plan=ExecutionPlan("stream", "stage"))
        self.assertIn("a-stage", cost.phases[0].id)
        self.assertTrue(all("a-stage" not in phase.id for phase in cost.phases[1:]))
        ingress = sum(flow.logical_bytes for flow in cost.traffic if flow.memory_domain == "dram" and flow.segment.endswith("a-stage"))
        reuse = cost.metrics["a_logical_bytes"] + cost.metrics["scale_a_logical_bytes"]
        self.assertLess(ingress, reuse)

    def test_shared_local_port_read_and_write_sum_during_overlap(self):
        params = ModelParams(overlap=True, overlap_evidence="synthetic simultaneous read/write scenario")
        cost = self.estimate(GemmSpec(1, 16, 32, "INT8", "INT8", "INT32"), params=params)
        phase = next(phase for phase in cost.phases if phase.id.endswith("consume_write"))
        self.assertAlmostEqual(phase.seconds, (32 + 64) / G0.local_iface_bytes_per_s_per_core)
        self.assertEqual(len([claim for claim in phase.claims if claim.resource == "core0.local"]), 1)
        self.assertTrue(any(buffer.id.endswith("c-overlap") for buffer in cost.buffers))

    def test_missing_clock_or_local_service_is_not_zero(self):
        for profile in (MODEL_ANALYSIS_310, replace(G0, local_iface_bytes_per_cycle=None)):
            with self.subTest(profile=profile):
                cost = self.estimate(profile=profile)
                self.assertFalse(cost.complete)
                self.assertTrue(any(phase.seconds is None for phase in cost.phases))
                self.assertGreater(cost.known_service_s, 0)

    def test_requested_capacity_failure_does_not_switch_policy(self):
        for profile, plan in ((replace(G0, sram_bytes_per_core=32), ExecutionPlan()),
                              (MODEL_ANALYSIS_310, ExecutionPlan("resident", "stream")),
                              (MODEL_ANALYSIS_310, ExecutionPlan("stream", "stage"))):
            with self.subTest(profile=profile, plan=plan), self.assertRaisesRegex(ValueError, "capacity"):
                self.estimate(profile=profile, plan=plan)

    def test_aligned_buffer_peak_matches_placement(self):
        cost = self.estimate(GemmSpec(3, 17, 65))
        self.assertEqual(sum(buffer.allocated_bytes for buffer in cost.buffers), cost.metrics["placement"]["footprint_bytes"])
        self.assertEqual(cost.metrics["c_logical_bytes"], 3 * 32 * 4)
        self.assertEqual(cost.metrics["c_physical_bytes"], 3 * 32 * 4)
        self.assertGreater(cost.metrics["padded_macs"], 0)

    def test_scale_narrow_request_physical_bytes_are_preserved(self):
        cost = self.estimate(GemmSpec(3, 16, 65, block_size=16))
        self.assertGreater(cost.metrics["scale_a_physical_bytes"], cost.metrics["scale_a_logical_bytes"])
        self.assertGreater(cost.metrics["scale_b_physical_bytes"], cost.metrics["scale_b_logical_bytes"])

    def test_estimation_never_materializes_windows(self):
        with patch.object(gemm_model, "iter_windows", side_effect=AssertionError("materialized windows")):
            cost = self.estimate(GemmSpec(1, 151936, 1024))
        self.assertEqual(cost.metrics["windows"], 303872)
        self.assertLess(len(cost.phases), 30)

    def test_integer_and_floating_accumulators_stay_distinct(self):
        for fmt, dtype in (("INT8", "INT32"), ("INT4", "INT32"), ("MXFP4", "FP32"), ("BF16", "FP32")):
            with self.subTest(fmt=fmt):
                self.assertTrue(self.estimate(GemmSpec(1, 16, 64, fmt, fmt, dtype)).complete)

    def test_tail_c_and_k_merge_allocate_physical_padded_output(self):
        for geometry in (self.geometry, replace(self.geometry, mnk_width=8)):
            op = LogicalOp("op", "layer", "tail", "gemm")
            builder = _GraphBuilder((op,), 32)
            a = builder.tensor("a", (2, 500), "MXFP8", external=True)
            b = builder.tensor("b", (500, 17), "MXFP8", external=True, scale_axis=0)
            result = builder.gemm(op, a, b, GemmSpec(2, 17, 500), geometry)
            graph = TaskGraph((op,), tuple(builder.tasks), tuple(builder.tensors.values()))
            graph.validate()
            view = builder.tensors[result]
            self.assertEqual(view.shape, (2, 17))
            self.assertIsNotNone(view.base_id)
            self.assertEqual(builder.tensors[view.base_id].shape, (2, 32))
            if geometry.mnk_width == 8:
                partial = next(tensor for tensor in graph.tensors if len(tensor.shape) == 3)
                self.assertEqual(partial.shape, (3, 2, 32))
                merge = next(task for task in graph.tasks if task.engine_kind == "VPU" and task.spec.kind == "k_accum")
                self.assertEqual(merge.spec.elements, 2 * 2 * 32)

    def test_padding_has_backing_producer_bytes_and_unknown_service(self):
        op = LogicalOp("op", "layer", "tail", "gemm")
        builder = _GraphBuilder((op,), 32)
        a = builder.tensor("a", (1, 500), "MXFP8", external=True)
        b = builder.tensor("b", (500, 17), "MXFP8", external=True, scale_axis=0)
        builder.gemm(op, a, b, GemmSpec(1, 17, 500, weight=True), self.geometry)
        graph = TaskGraph((op,), tuple(builder.tasks), tuple(builder.tensors.values()))
        graph.validate()
        task = next(task for task in graph.tasks if task.engine_kind == "GEMM")
        config = default_topology(G0, self.geometry, ModelParams()).instances["gemm0"]
        cost = gemm_model.estimate(task, config, ExecutionPlan("resident", "stream"))
        bview = builder.tensors[task.inputs[1].tensor_id]
        backing = builder.tensors[bview.base_id]
        self.assertEqual(backing.shape, (512, 32))
        self.assertEqual(backing.size_bytes, cost.metrics["weight_unique_bytes"])
        pad = next(t for t in graph.tasks if t.id == builder.producers[bview.id])
        self.assertEqual(pad.spec.dram_read_bytes, builder.tensors[b].size_bytes)
        self.assertEqual(pad.spec.dram_write_bytes, backing.size_bytes)
        self.assertIn(pad.id, task.dependencies)
        partial_pad = replace(pad, outputs=(TensorRef(backing.id, (0, 0), (500, 17)),))
        with self.assertRaisesRegex(ValueError, "unwritten"):
            replace(graph, tasks=tuple(partial_pad if t.id == pad.id else t for t in graph.tasks)).validate()
        from hive.model_eval.l1 import vpu_model
        vconfig = default_topology(G0, self.geometry, ModelParams()).instances["vpu0"]
        self.assertFalse(vpu_model.estimate(pad, vconfig, ExecutionPlan()).complete)
        unbacked = PrimitiveTask("unbacked", "op", "GEMM", GemmSpec(1, 17, 500))
        with self.assertRaisesRegex(ValueError, "backing storage"):
            gemm_model.estimate(unbacked, config, ExecutionPlan())
        wrong = replace(task, spec=replace(task.spec, b_storage_shape=(512, 48)))
        with self.assertRaisesRegex(ValueError, "backing allocation"):
            replace(graph, tasks=tuple(wrong if t.id == task.id else t for t in graph.tasks)).validate()

    def test_shared_padded_kv_operand_has_one_producer(self):
        op = LogicalOp("op", "layer", "tail", "gemm")
        builder = _GraphBuilder((op,), 32)
        a = builder.tensor("a", (1, 32), "MXFP8", external=True)
        b = builder.tensor("b", (32, 17), "MXFP8", external=True, scale_axis=0)
        for suffix in ("head-0", "head-1"):
            builder.gemm(op, a, b, GemmSpec(1, 17, 32), self.geometry, suffix)
        gemms = [task for task in builder.tasks if task.engine_kind == "GEMM"]
        self.assertEqual(gemms[0].inputs[1], gemms[1].inputs[1])
        self.assertEqual(len([t for t in builder.tasks if "-pad-b" in t.id]), 1)
        TaskGraph((op,), tuple(builder.tasks), tuple(builder.tensors.values())).validate()

    def test_k_split_scale_addresses_keep_backing_offsets(self):
        geometry = replace(self.geometry, mnk_width=8)
        spec = GemmSpec(1, 16, 512)
        for plan in (ExecutionPlan(), ExecutionPlan("resident", "stream"),
                     ExecutionPlan("resident", "stage")):
            cost = self.estimate(spec, geometry=geometry, plan=plan)
            commands = gemm_model.legalize(gemm_model.GemmShape(1, 16, 512), geometry)
            a_requests = b_requests = 0
            for c in commands:
                stride = c.k // 32 if plan.activation_policy == "stage" else 512 // 32
                start = 0 if plan.activation_policy == "stage" else c.k_offset // 32
                for row in range(c.m):
                    for k in range(c.k_tiles):
                        a_requests += c.n_tiles * (1 if (start + row * stride + k) % 4 == 0 else 2)
                for row in range(c.n):
                    for k in range(c.k_tiles):
                        address = (c.n_offset + row) * 16 + c.k_offset // 32 + k
                        b_requests += 1 if address % 4 == 0 else 2
            self.assertEqual(cost.metrics["scale_a_physical_bytes"], 4 * a_requests)
            self.assertEqual(cost.metrics["scale_b_physical_bytes"], 4 * b_requests)
            if plan.activation_policy == "stream":
                self.assertEqual(cost.metrics["scale_a_physical_bytes"], 112)


if __name__ == "__main__":
    unittest.main()
