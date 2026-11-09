import unittest
from dataclasses import replace
from itertools import product

from hive.model_eval.common.opspec import (CpuSpec, GemmSpec, LogicalOp, PrimitiveTask,
                                         TaskGraph, Tensor, TensorRef, TransferSpec, VpuSpec,
                                         _ElementRegions)
from hive.model_eval.l1.ledger import EngineCost, Phase


class TestTaskGraph(unittest.TestCase):
    def graph(self):
        op = LogicalOp("op", "layer", "composite", "custom")
        a = PrimitiveTask("a", "op", "VPU", VpuSpec("residual"),
                          outputs=(TensorRef("value"),))
        b = PrimitiveTask("b", "op", "VPU", VpuSpec("residual"), ("a",),
                          inputs=(TensorRef("value"),))
        return TaskGraph((op,), (a, b), (Tensor("value", (8,), "FP32"),))

    def test_stable_topological_order(self):
        graph = self.graph()
        self.assertEqual(graph.validate(), ("a", "b"))
        self.assertEqual(replace(graph, tasks=tuple(reversed(graph.tasks))).validate(), ("a", "b"))

    def test_logical_operator_can_own_multiple_engines(self):
        op = LogicalOp("op", "layer", "composite", "custom")
        kinds = [("GEMM", GemmSpec(1, 16, 32))] * 3 + [("VPU", VpuSpec("residual"))] * 2
        kinds += [("CPU", CpuSpec())] * 4
        tasks = tuple(PrimitiveTask(f"task-{i}", op.id, engine, spec) for i, (engine, spec) in enumerate(kinds))
        graph = TaskGraph((op,), tasks)
        self.assertEqual(len(graph.validate()), 9)

    def test_duplicate_ids_missing_dependencies_and_cycles(self):
        graph = self.graph()
        for tasks in ((graph.tasks[0], graph.tasks[0]),
                      (replace(graph.tasks[0], dependencies=("missing",)),),
                      (replace(graph.tasks[0], dependencies=("b",)), graph.tasks[1])):
            with self.assertRaises(ValueError):
                replace(graph, tasks=tasks).validate()

    def test_missing_tensor_and_dtype_mismatch(self):
        graph = self.graph()
        for ref in (TensorRef("missing"), TensorRef("value", dtype="INT32")):
            with self.assertRaises(ValueError):
                replace(graph, tasks=(graph.tasks[0], replace(graph.tasks[1], inputs=(ref,)))).validate()

    def test_producer_requires_dependency(self):
        graph = self.graph()
        with self.assertRaises(ValueError):
            replace(graph, tasks=(graph.tasks[0], replace(graph.tasks[1], dependencies=()))).validate()

    def test_external_input_has_no_required_producer(self):
        graph = self.graph()
        replace(graph, tasks=(replace(graph.tasks[1], dependencies=()),),
                tensors=(replace(graph.tensors[0], external=True),)).validate()

    def test_task_cannot_supply_its_own_uninitialized_input(self):
        graph = self.graph()
        task = replace(graph.tasks[0], inputs=graph.tasks[0].outputs)
        with self.assertRaisesRegex(ValueError, "no producer"):
            replace(graph, tasks=(task,)).validate()

    def test_two_disjoint_writers_and_join(self):
        graph = self.graph()
        a = replace(graph.tasks[0], outputs=(TensorRef("value", (0,), (4,)),))
        b = replace(a, id="b", outputs=(TensorRef("value", (4,), (8,)),))
        c = replace(graph.tasks[1], id="c", dependencies=("a", "b"))
        replace(graph, tasks=(a, b, c)).validate()
        with self.assertRaisesRegex(ValueError, "unwritten"):
            replace(graph, tasks=(a, replace(c, dependencies=("a",)))).validate()

    def test_unordered_overlapping_writes_rejected(self):
        graph = self.graph()
        with self.assertRaises(ValueError):
            replace(graph, tasks=(graph.tasks[0], replace(graph.tasks[0], id="b"))).validate()

    def test_slice_bounds_and_mx_rounding(self):
        tensor = Tensor("x", (2, 33), "MXFP4")
        self.assertEqual(tensor.size_bytes, 37)
        weight = Tensor("b", (500, 17), "MXFP8", scale_axis=0)
        self.assertEqual(weight.size_bytes, 500 * 17 + 17 * 16)
        with self.assertRaisesRegex(ValueError, "scale axis"):
            Tensor("invalid", (2, 33), "MXFP4", scale_axis=2)
        for ref in (TensorRef("x", (0,), (2,)), TensorRef("x", (0, 0), (2, 34))):
            with self.assertRaises(ValueError):
                ref.bounds(tensor)

    def test_views_share_base_and_describe_slice(self):
        graph = self.graph()
        view = Tensor("view", (4,), "FP32", base_id="value", base_starts=(4,), base_stops=(8,))
        b = replace(graph.tasks[1], inputs=(TensorRef("view"),))
        replace(graph, tensors=(*graph.tensors, view), tasks=(graph.tasks[0], b)).validate()
        with self.assertRaises(ValueError):
            replace(graph, tensors=(*graph.tensors, replace(view, dtype="INT32"))).validate()
        writer = replace(graph.tasks[0], outputs=(TensorRef("value", (0,), (4,)),))
        with self.assertRaisesRegex(ValueError, "unwritten"):
            replace(graph, tensors=(*graph.tensors, view), tasks=(writer, b)).validate()

    def test_cross_view_read_requires_actual_written_elements(self):
        graph = self.graph()
        left = Tensor("left", (4,), "FP32", base_id="value", base_stops=(4,))
        right = Tensor("right", (4,), "FP32", base_id="value", base_starts=(4,))
        a = replace(graph.tasks[0], outputs=(TensorRef("left"),))
        b = replace(graph.tasks[1], inputs=(TensorRef("right"),))
        with self.assertRaisesRegex(ValueError, "unwritten"):
            replace(graph, tensors=(*graph.tensors, left, right), tasks=(a, b)).validate()
        overlapping = replace(right, base_starts=(2,), base_stops=(6,))
        with self.assertRaisesRegex(ValueError, "unwritten"):
            replace(graph, tensors=(*graph.tensors, left, overlapping), tasks=(a, b)).validate()

    def test_disjoint_view_writers_join_on_base_or_reshape(self):
        graph = self.graph()
        left = Tensor("left", (4,), "FP32", base_id="value", base_stops=(4,))
        right = Tensor("right", (4,), "FP32", base_id="value", base_starts=(4,))
        whole = Tensor("whole", (2, 4), "FP32", base_id="value")
        a = replace(graph.tasks[0], outputs=(TensorRef("left"),))
        b = replace(a, id="b", outputs=(TensorRef("right"),))
        graph = replace(graph, tensors=(*graph.tensors, left, right, whole))
        for ref in (TensorRef("value"), TensorRef("whole")):
            with self.subTest(reader=ref.tensor_id):
                c = replace(graph.tasks[1], id="c", dependencies=("a", "b"), inputs=(ref,))
                replace(graph, tasks=(a, b, c)).validate()
                with self.assertRaisesRegex(ValueError, "ancestor"):
                    replace(graph, tasks=(a, b, replace(c, dependencies=("a",)))).validate()
                with self.assertRaisesRegex(ValueError, "unwritten"):
                    replace(graph, tasks=(a, replace(c, dependencies=("a",)))).validate()

    def test_overlapping_view_writers_require_order(self):
        graph = self.graph()
        left = Tensor("left", (6,), "FP32", base_id="value", base_stops=(6,))
        right = Tensor("right", (4,), "FP32", base_id="value", base_starts=(4,))
        a = replace(graph.tasks[0], outputs=(TensorRef("left"),))
        b = replace(a, id="b", outputs=(TensorRef("right"),))
        graph = replace(graph, tensors=(*graph.tensors, left, right))
        with self.assertRaisesRegex(ValueError, "overlapping writes"):
            replace(graph, tasks=(a, b)).validate()
        replace(graph, tasks=(a, replace(b, dependencies=("a",)))).validate()
        # Partial references to overlapping views can nevertheless be disjoint.
        a = replace(a, outputs=(TensorRef("left", (0,), (4,)),))
        replace(graph, tasks=(a, b)).validate()

    def test_independent_writer_not_touching_reader_needs_no_dependency(self):
        graph = self.graph()
        left = Tensor("left", (4,), "FP32", base_id="value", base_stops=(4,))
        right = Tensor("right", (4,), "FP32", base_id="value", base_starts=(4,))
        a = replace(graph.tasks[0], outputs=(TensorRef("left"),))
        b = replace(a, id="b", outputs=(TensorRef("right"),))
        graph = replace(graph, tensors=(*graph.tensors, left, right))
        for ref in (TensorRef("left"), TensorRef("value", (0,), (4,))):
            c = replace(graph.tasks[1], id="c", inputs=(ref,))
            replace(graph, tasks=(a, b, c)).validate()
        # Only the requested part of an alias participates in ancestry checks.
        a = replace(a, outputs=(TensorRef("left", (0,), (2,)),))
        b = replace(b, outputs=(TensorRef("value", (2,), (4,)),))
        c = replace(c, inputs=(TensorRef("left", (0,), (2,)),))
        replace(graph, tasks=(a, b, c)).validate()
        with self.assertRaisesRegex(ValueError, "ancestor"):
            replace(graph, tasks=(a, b, replace(c, inputs=(TensorRef("left"),)))).validate()

    def test_strided_view_writes_leave_gaps_and_disjoint_strides_join(self):
        graph = self.graph()
        even = Tensor("even", (4,), "FP32", base_id="value", base_steps=(2,))
        odd = Tensor("odd", (4,), "FP32", base_id="value", base_starts=(1,), base_steps=(2,))
        a = replace(graph.tasks[0], outputs=(TensorRef("even"),))
        graph = replace(graph, tensors=(*graph.tensors, even, odd))
        for ref in (TensorRef("value"), TensorRef("odd"), TensorRef("value", (1,), (2,))):
            with self.subTest(reader=ref.tensor_id, starts=ref.starts):
                c = replace(graph.tasks[1], inputs=(ref,))
                with self.assertRaisesRegex(ValueError, "unwritten"):
                    replace(graph, tasks=(a, c)).validate()
        b = replace(a, id="b", outputs=(TensorRef("odd"),))
        c = replace(graph.tasks[1], id="c", dependencies=("a", "b"))
        replace(graph, tasks=(a, b, c)).validate()
        overlap = replace(b, outputs=(TensorRef("value", (2,), (3,)),))
        with self.assertRaisesRegex(ValueError, "overlapping writes"):
            replace(graph, tasks=(a, overlap)).validate()

    def test_strided_view_read_ignores_unselected_base_gaps(self):
        graph = self.graph()
        even = Tensor("even", (4,), "FP32", base_id="value", base_steps=(2,))
        writers = tuple(replace(graph.tasks[0], id=f"w{i}",
                                outputs=(TensorRef("value", (i,), (i + 1,)),)) for i in range(0, 8, 2))
        reader = replace(graph.tasks[1], dependencies=tuple(w.id for w in writers),
                         inputs=(TensorRef("even"),))
        graph = replace(graph, tensors=(*graph.tensors, even))
        replace(graph, tasks=(*writers, reader)).validate()
        with self.assertRaisesRegex(ValueError, "unwritten"):
            replace(graph, tasks=(*writers[:-1], replace(reader, dependencies=reader.dependencies[:-1]))).validate()
        # Reading one selected element does not require the rest of the view.
        reader = replace(reader, dependencies=(writers[1].id,), inputs=(TensorRef("even", (1,), (2,)),))
        replace(graph, tasks=(writers[1], reader)).validate()

    def test_partial_reshape_read_maps_selected_base_elements_row_major(self):
        graph = self.graph()
        base = replace(graph.tensors[0], shape=(4, 6))
        view = Tensor("view", (3, 2), "FP32", base_id="value",
                      base_starts=(0, 1), base_stops=(4, 6), base_steps=(2, 2))
        # Selected flat addresses are [1, 3, 5, 13, 15, 17]. The middle
        # reshaped row therefore crosses two different selected base rows.
        a = replace(graph.tasks[0], outputs=(TensorRef("value", (0, 5), (1, 6)),))
        b = replace(a, id="b", outputs=(TensorRef("value", (2, 1), (3, 2)),))
        c = replace(graph.tasks[1], id="c", dependencies=("a", "b"),
                    inputs=(TensorRef("view", (1, 0), (2, 2)),))
        graph = replace(graph, tensors=(base, view))
        replace(graph, tasks=(a, b, c)).validate()
        with self.assertRaisesRegex(ValueError, "unwritten"):
            replace(graph, tasks=(a, replace(c, dependencies=("a",)))).validate()
        # A writer on an unrequested selected element is not a producer.
        extra = replace(a, id="extra", outputs=(TensorRef("view", (0, 0), (1, 2)),))
        replace(graph, tasks=(a, b, extra, c)).validate()
        with self.assertRaisesRegex(ValueError, "overlapping writes"):
            replace(graph, tasks=(a, replace(extra, outputs=c.inputs))).validate()

    def test_partial_reshape_columns_join_partial_different_view_writes(self):
        graph = self.graph()
        base = replace(graph.tensors[0], shape=(4, 6))
        view = Tensor("view", (3, 2), "FP32", base_id="value",
                      base_starts=(0, 1), base_stops=(4, 6), base_steps=(2, 2))
        flat = replace(view, id="flat", shape=(6,))
        writers = tuple(replace(graph.tasks[0], id=f"w{i}",
                                outputs=(TensorRef("flat", (i,), (i + 1,)),)) for i in (1, 3, 5))
        c = replace(graph.tasks[1], dependencies=tuple(w.id for w in writers),
                    inputs=(TensorRef("view", (0, 1), (3, 2)),))
        graph = replace(graph, tensors=(base, view, flat))
        replace(graph, tasks=(*writers, c)).validate()
        with self.assertRaisesRegex(ValueError, "unwritten"):
            replace(graph, tasks=(*writers[:-1], replace(c, dependencies=c.dependencies[:-1]))).validate()

    def test_implicit_view_is_a_validated_row_major_prefix_reshape(self):
        graph = self.graph()
        view = Tensor("view", (2, 2), "FP32", base_id="value")
        a = replace(graph.tasks[0], outputs=(TensorRef("view"),))
        b = replace(graph.tasks[1], inputs=(TensorRef("value", (0,), (4,)),))
        graph = replace(graph, tensors=(*graph.tensors, view))
        replace(graph, tasks=(a, b)).validate()
        with self.assertRaisesRegex(ValueError, "unwritten"):
            replace(graph, tasks=(a, replace(b, inputs=(TensorRef("value", (4,), (8,)),)))).validate()
        # Implicit reshape column 1 maps to base addresses 1 and 3.
        a = replace(a, outputs=(TensorRef("value", (1,), (2,)),))
        c = replace(a, id="c", outputs=(TensorRef("value", (3,), (4,)),))
        b = replace(b, dependencies=("a", "c"), inputs=(TensorRef("view", (0, 1), (2, 2)),))
        replace(graph, tasks=(a, c, b)).validate()
        with self.assertRaisesRegex(ValueError, "unwritten"):
            replace(graph, tasks=(a, replace(b, dependencies=("a",)))).validate()
        # Packed byte rounding must not allow more elements than the base.
        base = Tensor("packed", (3,), "INT4")
        oversized = Tensor("oversized", (4,), "INT4", base_id="packed")
        self.assertEqual(base.size_bytes, oversized.size_bytes)
        with self.assertRaisesRegex(ValueError, "fit"):
            replace(graph, tasks=(), tensors=(base, oversized)).validate()

    def test_explicit_view_selection_size_and_strides_are_validated(self):
        graph = self.graph()
        view = Tensor("view", (4,), "FP32", base_id="value", base_steps=(2,))
        for invalid in (replace(view, shape=(3,)), replace(view, base_steps=(0,)),
                        replace(view, base_steps=(1.5,)), replace(view, base_steps=(1, 1)),
                        replace(view, base_stops=(9,))):
            with self.subTest(view=invalid), self.assertRaises(ValueError):
                replace(graph, tasks=(), tensors=(*graph.tensors, invalid)).validate()

    def test_small_strided_reshape_slices_match_element_oracle(self):
        regions = _ElementRegions(60)
        selected = regions.box((3, 4, 5), (0, 1, 0), (3, 4, 5), (2, 2, 2))
        base_elements = tuple((i * 4 + j) * 5 + k
                              for i, j, k in product(range(0, 3, 2), range(1, 4, 2), range(0, 5, 2)))
        for shape in ((12,), (3, 4), (2, 2, 3), (4, 3)):
            axis_slices = [[(a, b) for a in range(n) for b in range(a + 1, n + 1)] for n in shape]
            for bounds in product(*axis_slices):
                starts, stops = tuple(a for a, _ in bounds), tuple(b for _, b in bounds)
                expected = 0
                for coordinates in product(*(range(a, b) for a, b in bounds)):
                    ordinal = 0
                    for coordinate, n in zip(coordinates, shape):
                        ordinal = ordinal * n + coordinate
                    element = base_elements[ordinal]
                    expected = regions.union(expected, regions.interval(element, element + 1))
                with self.subTest(shape=shape, bounds=bounds):
                    ordinals = regions.box(shape, starts, stops)
                    self.assertEqual(regions.select(selected, ordinals, regions.top), expected)

    def test_large_strided_reshapes_remain_compact(self):
        size = 1 << 26
        regions = _ElementRegions(size)
        self.assertEqual(regions.box((size,), (0,), (size,)), 1)
        even = regions.box((size,), (0,), (size,), (2,))
        odd = regions.box((size,), (1,), (size,), (2,))
        self.assertFalse(regions.intersection(even, odd))
        self.assertEqual(regions.union(even, odd), 1)
        ordinals = regions.box((1 << 12, 1 << 13), (0, 1), (1 << 12, (1 << 13) - 1))
        selected = regions.select(even, ordinals, regions.top)
        self.assertEqual(regions._count(selected, regions.top), (1 << 12) * ((1 << 13) - 2))
        self.assertLess(len(regions.nodes), 5000)
        graph = self.graph()
        base = replace(graph.tensors[0], shape=(size,))
        views = tuple(Tensor(name, (1 << 12, 1 << 13), "FP32", base_id="value",
                             base_starts=(start,), base_steps=(2,)) for name, start in (("even", 0), ("odd", 1)))
        a = replace(graph.tasks[0], outputs=(TensorRef("even"),))
        b = replace(a, id="b", outputs=(TensorRef("odd"),))
        c = replace(graph.tasks[1], id="c", dependencies=("a", "b"))
        replace(graph, tensors=(base, *views), tasks=(a, b, c)).validate()

    def test_transfer_has_one_owner(self):
        op = self.graph().operations[0]
        spec = TransferSpec("stream", "dram", "core0.sram", 4)
        tasks = tuple(PrimitiveTask(str(i), op.id, "DMA", spec) for i in range(2))
        with self.assertRaises(ValueError):
            TaskGraph((op,), tasks).validate()

    def test_integer_gemm_output_is_not_fp32(self):
        with self.assertRaises(ValueError):
            GemmSpec(1, 16, 32, "INT8", "INT8", "FP32")
        GemmSpec(1, 16, 32, "INT8", "INT8", "INT32")

    def test_cpu_algorithm_is_not_inferred_from_shape(self):
        with self.assertRaises(ValueError):
            CpuSpec("arbitrary")

    def test_unknown_phase_keeps_budget_not_completion(self):
        cost = EngineCost("task", "engine", (Phase("known", .01, "vpu"),
                          Phase("unknown", None, "vpu", ("known",), known_seconds=.003)))
        self.assertAlmostEqual(cost.known_service_s, .013)
        self.assertFalse(cost.complete)

    def test_phase_cycle_is_rejected(self):
        with self.assertRaises(ValueError):
            EngineCost("task", "engine", (Phase("a", 1, "vpu", ("b",)),
                                          Phase("b", 1, "vpu", ("a",))))
