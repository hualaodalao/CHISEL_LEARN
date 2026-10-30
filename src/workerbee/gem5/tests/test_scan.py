import importlib.util
from pathlib import Path
import unittest

MODULE = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location("workerbee_scan", MODULE / "tools/scan.py")
scan = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(scan)


class ArgumentTests(unittest.TestCase):
    def test_shape_parsing_and_rejection(self):
        self.assertEqual(scan.parse_shapes(["16x32x64", "48X16X256"]),
                         [(16, 32, 64), (48, 16, 256)])
        for value in ("16x32", "16x32x64x1", "axbxc"):
            with self.subTest(value=value), self.assertRaises(ValueError):
                scan.parse_shapes([value])

    def test_preset_grid_is_cartesian(self):
        shapes = scan.preset_shapes("int8")
        self.assertEqual(len(shapes), 3 * 3 * 2)
        self.assertIn((48, 128, 256), shapes)

    def test_explicit_shapes_override_preset(self):
        args = scan.arguments(["--gem5", "/tmp/gem5.opt", "--output", "/tmp/out",
                               "--shape", "16x16x64"])
        self.assertEqual(args.shapes, [(16, 16, 64)])

    def test_unknown_preset_is_rejected(self):
        with self.assertRaises(ValueError):
            scan.arguments(["--gem5", "/tmp/gem5.opt", "--output", "/tmp/out",
                            "--preset", "nope"])


class StructuralTests(unittest.TestCase):
    def test_counts_windows_latency_and_last_window_tail(self):
        geo = {"n_tiles": 2, "k_tiles": 3, "t": 16, "c_beats": 2 * 16 * 2}
        # 2*3 windows x (16 B beats + 16 A beats) + 31 latency + one window of C
        self.assertEqual(scan.structural_cycles(geo, 31, 16),
                         6 * 32 + 31 + 32)

    def test_missing_geometry_is_not_an_error(self):
        self.assertIsNone(scan.structural_cycles({}, 31, 16))


class MarkdownTests(unittest.TestCase):
    def test_failed_rows_stay_in_the_table(self):
        report = {"rows": [
            {"task_id": "INT8_16x16x64", "m": 16, "n": 16, "k": 64, "ok": True,
             "gemm_cycles": 141, "structural": 127, "structural_overhead": 1.11,
             "macs_per_cycle": 116.2, "utilization": 0.227,
             "dma_bytes_per_cycle": 25.4, "stall_b_cycles": 24},
            {"task_id": "INT8_16x16x65", "m": 16, "n": 16, "k": 65, "ok": False},
        ]}
        text = scan.markdown(report)
        self.assertIn("| INT8_16x16x64 | 16 | 16 | 64 | 141 |", text)
        self.assertIn("| INT8_16x16x65 | 16 | 16 | 65 | failed |", text)


if __name__ == "__main__":
    unittest.main()
