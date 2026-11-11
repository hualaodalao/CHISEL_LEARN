"""Phase 4: explicit extension modules can add layers and models without core edits."""

import contextlib
import importlib
import io
import unittest

from hive.model_eval.frontend import builtins  # noqa: F401  (registers built-ins)
from hive.model_eval.frontend.registry import get_model, list_models
from hive.model_eval.frontend.spec import MXFP8
from hive.model_eval.common.workload import decode, prefill
from hive.model_eval.hw.geometry import load_array_geometry
from hive.model_eval.hw.profiles import G0
from hive.model_eval.l0.roofline import evaluate as evaluate_l0
from hive.model_eval.l1.emitters import coverage
from hive.model_eval.l1.timeline import evaluate

EXTENSION = "hive.model_eval.tests.extension_demo"


class TestExtensionWorkflow(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        importlib.import_module(EXTENSION)
        cls.plan = get_model("Acme-Demo")
        cls.geometry = load_array_geometry()

    def test_model_and_layers_register_without_core_changes(self):
        self.assertIn(("Acme-Demo", 1), list_models())
        self.plan.validate()
        self.assertEqual(coverage(self.plan), {"L0": (), "L1": ()})

    def test_l0_and_l1_run(self):
        l0 = evaluate_l0(self.plan, G0, MXFP8, decode(4))
        self.assertGreater(l0.mac, 0)
        for point in (decode(4), prefill(8, 2)):
            with self.subTest(point=point):
                result = evaluate(self.plan, G0, MXFP8, point, geometry=self.geometry)
                self.assertTrue(result.graph.tasks)
                self.assertGreater(result.metrics["semantic_macs"], 0)
                self.assertTrue(any(row["aggregate"] for row in result.to_dict()["layer_tree"]))

    def test_cli_extension_flag_reaches_both_levels(self):
        from hive.model_eval.l0.run import main as l0_main
        from hive.model_eval.l1.run import main as l1_main
        with contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(l0_main(["--extension", EXTENSION, "--model", "Acme-Demo",
                                      "--seq", "4"]), 0)
            self.assertEqual(l1_main(["--extension", EXTENSION, "--model", "Acme-Demo",
                                      "--workload", "decode", "--seq", "4",
                                      "--attention", "vpu"]), 0)

    def test_unknown_extension_is_rejected(self):
        from hive.model_eval.l0.run import main as l0_main
        with self.assertRaises(SystemExit), contextlib.redirect_stderr(io.StringIO()):
            l0_main(["--extension", "hive.model_eval.tests.no_such_extension"])


if __name__ == "__main__":
    unittest.main()
