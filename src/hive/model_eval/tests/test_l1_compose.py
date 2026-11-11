"""Golden equality between the ModelPlan emitter path and the legacy lowering.

The new path intentionally makes the KV append DMA own its cache outputs.  That
adds one redundant direct AV -> append dependency (AV is already transitively
ordered after append through QK and softmax) and no timing change.  Everything
else must match the legacy graph byte for byte.
"""

import unittest
from dataclasses import replace

from hive.model_eval.common.workload import decode, prefill
from hive.model_eval.frontend import builtins  # noqa: F401  (registers layers/models)
from hive.model_eval.frontend import qwen3_06b as legacy
from hive.model_eval.frontend.models.qwen3_06b import CONFIG
from hive.model_eval.frontend.registry import get_model
from hive.model_eval.frontend.spec import INT8, MXFP8
from hive.model_eval.hw.geometry import load_array_geometry
from hive.model_eval.l1.compose import lower_plan
from hive.model_eval.l1.emitters import coverage
from hive.model_eval.l1.mapping import lower_model


def _small_plan():
    config = dict(CONFIG)
    config["num_hidden_layers"] = 1
    return get_model("Qwen3-0.6B", config=config)


def _small_model():
    model = legacy.build()
    return replace(model, layer_types=(replace(model.layer_types[0], count=1),))


def _op_tuple(graph):
    return tuple(
        (op.id, op.layer, op.name, op.kind, op.mac, op.elements, op.vectors,
         op.length, op.read_bytes, op.write_bytes, op.dram_read_bytes,
         op.dram_write_bytes, op.weight_bytes, op.gemms, op.evidence)
        for op in graph.operations)


def _tensor_tuple(graph):
    return tuple(
        (tensor.id, tensor.shape, tensor.dtype, tensor.external, tensor.base_id,
         tensor.base_starts, tensor.base_stops, tensor.base_steps, tensor.scale_axis)
        for tensor in graph.tensors)


def _task_tuple(graph, *, normalize_append=False):
    tasks = []
    for task in graph.tasks:
        outputs = tuple(ref.tensor_id for ref in task.outputs)
        dependencies = task.dependencies
        if normalize_append and task.id.endswith("/kv_transfer/append"):
            outputs = ()
        if normalize_append and task.id.endswith("/av/execute"):
            dependencies = tuple(dep for dep in dependencies
                                 if not dep.endswith("/kv_transfer/append"))
        tasks.append((task.id, task.engine_kind, dependencies,
                      tuple(ref.tensor_id for ref in task.inputs), outputs,
                      task.spec, task.allowed_instances))
    return tuple(tasks)


class TestComposeGolden(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.geometry = load_array_geometry()
        cls.plan = _small_plan()
        cls.model = _small_model()

    def test_ops_tensors_and_normalized_tasks_match_legacy(self):
        points = (decode(0), decode(5), decode(1, 2), prefill(4), prefill(7, 3))
        for fmt in (MXFP8, INT8):
            for variant in ("vpu", "quantized_gemm"):
                for point in points:
                    with self.subTest(fmt=fmt.name, variant=variant,
                                      phase=point.phase.value, seq=point.seq_len,
                                      batch=point.batch):
                        old = lower_model(self.model, fmt, point, variant,
                                          geometry=self.geometry)
                        new = lower_plan(self.plan, fmt, point, attention=variant,
                                         geometry=self.geometry)
                        self.assertEqual(_op_tuple(new), _op_tuple(old))
                        self.assertEqual(_tensor_tuple(new), _tensor_tuple(old))
                        self.assertEqual(_task_tuple(new, normalize_append=True),
                                         _task_tuple(old, normalize_append=True))

    def test_kv_append_owns_its_cache_outputs(self):
        point = decode(5)
        new = lower_plan(self.plan, MXFP8, point, attention="vpu",
                         geometry=self.geometry)
        append = next(task for task in new.tasks
                      if task.id.endswith("/kv_transfer/append"))
        self.assertEqual(tuple(ref.tensor_id for ref in append.outputs),
                         ("block-00:K-cache", "block-00:V-cache"))
        av = next(task for task in new.tasks if task.id.endswith("/av/execute"))
        self.assertIn("block-00/kv_transfer/append", av.dependencies)

    def test_full_plan_task_counts_match_legacy(self):
        plan = get_model("Qwen3-0.6B")
        model = legacy.build()
        point = decode(0)
        old = lower_model(model, MXFP8, point, geometry=self.geometry)
        new = lower_plan(plan, MXFP8, point, geometry=self.geometry)
        self.assertEqual(len(new.operations), len(old.operations))
        self.assertEqual(len(new.tasks), len(old.tasks))
        self.assertEqual(len(new.tensors), len(old.tensors))

    def test_all_plan_layers_have_l0_and_l1_coverage(self):
        report = coverage(get_model("Qwen3-0.6B"))
        self.assertEqual(report["L0"], ())
        self.assertEqual(report["L1"], ())


if __name__ == "__main__":
    unittest.main()
