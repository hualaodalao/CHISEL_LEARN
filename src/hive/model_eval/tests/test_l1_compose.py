"""ModelPlan emitter-composition structural anchors.

The legacy lowering these graphs were once compared against has been removed;
the emitter path is now the single source.  These tests pin the plan graph's
structure (KV-append ownership, full-model counts, L0/L1 coverage) directly.
"""

import unittest

from hive.model_eval.common.workload import decode
from hive.model_eval.frontend import builtins  # noqa: F401  (registers layers/models)
from hive.model_eval.frontend.models.qwen3_06b import CONFIG
from hive.model_eval.frontend.registry import get_model
from hive.model_eval.frontend.spec import MXFP8
from hive.model_eval.hw.geometry import load_array_geometry
from hive.model_eval.l1.compose import lower_plan
from hive.model_eval.l1.emitters import coverage


def _small_plan():
    return get_model("Qwen3-0.6B", config={**CONFIG, "num_hidden_layers": 1})


class TestComposeStructure(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.geometry = load_array_geometry()
        cls.plan = _small_plan()

    def test_kv_append_owns_its_cache_outputs(self):
        new = lower_plan(self.plan, MXFP8, decode(5), attention="vpu",
                         geometry=self.geometry)
        append = next(task for task in new.tasks
                      if task.id.endswith("/kv_transfer/append"))
        self.assertEqual(tuple(ref.tensor_id for ref in append.outputs),
                         ("block-00:K-cache", "block-00:V-cache"))
        av = next(task for task in new.tasks if task.id.endswith("/av/execute"))
        self.assertIn("block-00/kv_transfer/append", av.dependencies)

    def test_full_plan_graph_counts(self):
        graph = lower_plan(get_model("Qwen3-0.6B"), MXFP8, decode(0),
                           geometry=self.geometry)
        self.assertEqual(len(graph.operations), 423)
        self.assertEqual(len(graph.tasks), 733)
        self.assertEqual(len(graph.tensors), 845)

    def test_all_plan_layers_have_l0_and_l1_coverage(self):
        report = coverage(get_model("Qwen3-0.6B"))
        self.assertEqual(report["L0"], ())
        self.assertEqual(report["L1"], ())


if __name__ == "__main__":
    unittest.main()
