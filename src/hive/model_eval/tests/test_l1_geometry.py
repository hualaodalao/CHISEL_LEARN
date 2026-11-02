"""L1 analytical geometry anchors; no simulator or timing model required."""

import unittest
from dataclasses import FrozenInstanceError, replace
from fractions import Fraction
from itertools import islice
from unittest.mock import patch

from hive.model_eval.frontend.qwen3_06b import build
from hive.model_eval.hw.geometry import _load_gem5_hardware, load_array_geometry, repo_root
from hive.model_eval.l1 import windows
from hive.model_eval.l1.gemm_geom import (
    Command, GemmShape, format_elem_bytes, format_is_mx, legalize,
)
from hive.model_eval.l1.windows import Window, WindowCounts, count_windows, iter_windows


class GeometryTestCase(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.geometry = load_array_geometry()

    def counts(self, shape, geometry=None):
        return count_windows(shape, legalize(shape, geometry or self.geometry))


class TestQwenAnchors(GeometryTestCase):
    def test_seven_projection_shapes_and_layer_totals(self):
        tensors = build().layer_types[0].tensors
        self.assertEqual(
            [(tensor.name, tensor.shape) for tensor in tensors],
            [("q_proj", (2048, 1024)), ("k_proj", (1024, 1024)),
             ("v_proj", (1024, 1024)), ("o_proj", (1024, 2048)),
             ("gate_proj", (3072, 1024)), ("up_proj", (3072, 1024)),
             ("down_proj", (1024, 3072))],
        )
        counts = [self.counts(GemmShape(1, *tensor.shape)) for tensor in tensors]
        self.assertEqual([c.windows for c in counts],
                         [4096, 2048, 2048, 4096, 6144, 6144, 6144])
        expected = {"commands": 7, "windows": 30720, "a_beats": 30720,
                    "b_beats": 491520, "c_rows": 768, "c_beats": 1536,
                    "compute_cycles": 30720, "load_cycles": 491520,
                    "array_cycles": 522240, "padded_macs": 0}
        for name, value in expected.items():
            with self.subTest(field=name):
                self.assertEqual(sum(getattr(c, name) for c in counts), value)

    def test_head_splits_n_without_changing_totals(self):
        shape = GemmShape(1, 151936, 1024)
        commands = legalize(shape, self.geometry)
        self.assertEqual([c.n for c in commands], [65520, 65520, 20896])
        self.assertEqual([c.n_offset for c in commands], [0, 65520, 131040])
        counts = count_windows(shape, commands)
        self.assertEqual(counts.commands, 3)
        self.assertEqual(counts.windows, 303872)
        self.assertEqual(counts.a_beats, 303872)
        self.assertEqual(counts.b_beats, 4861952)
        self.assertEqual(counts.c_rows, 9496)
        self.assertEqual(counts.c_beats, 18992)
        self.assertEqual(counts.array_cycles, 5165824)
        self.assertEqual(counts.padded_macs, 0)

    def test_whole_28_layers_plus_head(self):
        model = build()
        layer = [self.counts(GemmShape(1, *t.shape))
                 for t in model.layer_types[0].tensors]
        head = self.counts(GemmShape(1, model.vocab_size, model.hidden_size))
        self.assertEqual(28 * sum(c.array_cycles for c in layer) + head.array_cycles,
                         19788544)
        b_beats = 28 * sum(c.b_beats for c in layer) + head.b_beats
        self.assertEqual(b_beats * self.geometry.b_bytes_per_beat, 595984384)


class TestFrozenGeometry(GeometryTestCase):
    def test_new_fields_come_from_frozen_profile(self):
        profile = _load_gem5_hardware(repo_root()).DEFAULT_PROFILE
        parameters = profile["parameters"]
        for field, key in (("a_buffer_depth", "aBufferDepth"),
                           ("b_buffer_depth", "bBufferDepth"),
                           ("c_buffer_depth", "cBufferDepth"),
                           ("c_dma_ext_w", "cDmaExtW")):
            with self.subTest(field=field):
                self.assertEqual(getattr(self.geometry, field), parameters[key])
        self.assertEqual(self.geometry.max_m, 48)
        self.assertEqual(self.geometry.c_bytes_per_beat, 32)
        self.assertEqual(self.geometry.c_ext_w, 640)
        self.assertNotEqual(self.geometry.c_ext_w, self.geometry.c_dma_ext_w)

    def test_geometry_shape_command_window_and_counts_are_frozen(self):
        shape = GemmShape(1, 16, 32)
        command = legalize(shape, self.geometry)[0]
        window = next(iter_windows((command,)))
        counts = count_windows(shape, (command,))
        for value, field in ((self.geometry, "total_n"), (shape, "m"),
                             (command, "m"), (window, "n_tile"),
                             (counts, "windows")):
            with self.subTest(type=type(value).__name__):
                with self.assertRaises(FrozenInstanceError):
                    setattr(value, field, 99)


class TestFormats(GeometryTestCase):
    def test_element_bytes_and_mx_flags(self):
        for name, size, mx in (("INT8", 1, False), ("INT4", Fraction(1, 2), False),
                               ("FP8", 1, False), ("MXFP8", 1, True),
                               ("MXFP4", Fraction(1, 2), True), ("BF16", 2, False),
                               ("MXE4M3", 1, True), ("MXE5M2", 1, True),
                               ("MXE2M1", Fraction(1, 2), True),
                               ("FP8E4M3", 1, False), ("FP8E5M2", 1, False)):
            with self.subTest(format=name):
                self.assertEqual(format_elem_bytes(name), size)
                self.assertIsInstance(format_elem_bytes(name), Fraction)
                self.assertEqual(format_is_mx(name), mx)

    def test_same_format_k_geometry(self):
        for fmt, k_per_beat in (("INT8", 32), ("FP8", 32), ("MXFP8", 32),
                                ("INT4", 64), ("MXFP4", 64), ("BF16", 16)):
            with self.subTest(format=fmt):
                command = legalize(GemmShape(1, 1, 1, fmt, fmt), self.geometry)[0]
                self.assertEqual(command.k_per_beat, k_per_beat)
                self.assertEqual(command.k, k_per_beat)
                self.assertEqual(command.k_tiles, 1)

    def test_forward_w4a8_granularity_is_not_k_per_beat(self):
        for a_fmt, b_fmt in (("INT8", "INT4"), ("MXFP8", "MXFP4")):
            with self.subTest(formats=(a_fmt, b_fmt)):
                command = legalize(GemmShape(1, 16, 33, a_fmt, b_fmt),
                                   self.geometry)[0]
                self.assertEqual(command.k_per_beat, 32)
                self.assertEqual(command.k, 64)
                self.assertEqual(command.k_tiles, 2)

    def test_aliases_and_cross_variants(self):
        for a_fmt, b_fmt in (("MXE4M3", "MXE5M2"), ("FP8E4M3", "FP8E5M2"),
                             ("MXE5M2", "MXE2M1"),
                             ("MXFP8E4M3", "MXFP8E5M2")):
            with self.subTest(formats=(a_fmt, b_fmt)):
                self.assertTrue(legalize(GemmShape(1, 16, 64, a_fmt, b_fmt),
                                         self.geometry))
        generic_geometry = replace(self.geometry, supported_fmts=("MXFP8", "MXFP4"))
        self.assertTrue(legalize(GemmShape(1, 16, 32), generic_geometry))

    def test_supported_formats_are_enforced_on_both_operands(self):
        geometry = replace(self.geometry, supported_fmts=("INT8",))
        self.assertTrue(legalize(GemmShape(1, 16, 32, "INT8", "INT8"), geometry))
        for a_fmt, b_fmt in (("MXFP8", "MXFP8"), ("INT8", "INT4")):
            with self.subTest(formats=(a_fmt, b_fmt)):
                with self.assertRaises(ValueError):
                    legalize(GemmShape(1, 16, 32, a_fmt, b_fmt), geometry)

    def test_explicit_variant_cannot_use_an_unsupported_variant(self):
        geometry = replace(self.geometry, supported_fmts=("MXE4M3", "MXE2M1"))
        self.assertTrue(legalize(GemmShape(1, 16, 32), geometry))
        with self.assertRaises(ValueError):
            legalize(GemmShape(1, 16, 32, "MXE5M2", "MXE5M2"), geometry)

    def test_invalid_and_reverse_mixes(self):
        for a_fmt, b_fmt in (("INT4", "INT8"), ("MXFP4", "MXFP8"),
                             ("BF16", "INT8"), ("INT8", "BF16"),
                             ("FP8", "MXFP8"), ("MXFP8", "FP8"),
                             ("INT8", "FP8"), ("MXFP8", "INT4")):
            with self.subTest(formats=(a_fmt, b_fmt)):
                with self.assertRaises(ValueError):
                    legalize(GemmShape(1, 16, 64, a_fmt, b_fmt), self.geometry)

    def test_unknown_formats_are_rejected_by_all_entry_points(self):
        for name in ("FP16", "FP32", "garbage", "", None):
            with self.subTest(format=name):
                with self.assertRaises(ValueError):
                    format_elem_bytes(name)
                with self.assertRaises(ValueError):
                    format_is_mx(name)
                with self.assertRaises(ValueError):
                    legalize(GemmShape(1, 16, 32, name, name), self.geometry)

    def test_mx_blocks_16_and_32(self):
        for a_fmt, b_fmt in (("MXFP8", "MXFP8"), ("MXFP4", "MXFP4"),
                             ("MXFP8", "MXFP4")):
            for block in (16, 32):
                with self.subTest(formats=(a_fmt, b_fmt), block=block):
                    command = legalize(GemmShape(1, 16, 17, a_fmt, b_fmt,
                                                block_size=block), self.geometry)[0]
                    self.assertEqual(command.k % block, 0)
                    self.assertEqual(command.block_size, block)
        for block in (8, 64):
            with self.subTest(block=block):
                with self.assertRaises(ValueError):
                    legalize(GemmShape(1, 16, 32, block_size=block), self.geometry)

    def test_mx_block_cannot_exceed_lane_window(self):
        geometry = replace(self.geometry, array_n=4, total_n=8)
        with self.assertRaises(ValueError):
            legalize(GemmShape(1, 8, 32, block_size=32), geometry)
        command = legalize(GemmShape(1, 8, 17, block_size=16), geometry)[0]
        self.assertEqual(command.k_per_beat, 16)
        self.assertEqual(command.k, 32)


class TestLegalization(GeometryTestCase):
    def test_small_command_has_exact_public_fields(self):
        shape = GemmShape(3, 17, 33)
        command = legalize(shape, self.geometry)[0]
        self.assertIsInstance(command, Command)
        self.assertEqual((command.m_offset, command.n_offset, command.k_offset), (0, 0, 0))
        self.assertEqual((command.m, command.n, command.k), (3, 32, 64))
        self.assertEqual((command.n_tiles, command.k_tiles, command.k_per_beat,
                          command.tile_n), (2, 2, 32, 16))
        self.assertEqual((command.a_fmt, command.b_fmt, command.c_bits,
                          command.block_size, command.final_k),
                         ("MXFP8", "MXFP8", 32, 32, True))
        self.assertIsInstance(legalize(shape, self.geometry), tuple)

    def test_command_c_dma_width_is_explicit(self):
        command = Command(0, 0, 0, 1, 16, 32, 1, 1, 32, 16,
                          "MXFP8", "MXFP8", 32, 32, True, 32)
        counts = count_windows(GemmShape(1, 16, 32), (command,))
        self.assertEqual(counts, WindowCounts(1, 1, 1, 16, 1, 2, 17, 512, 0, 1, 16))

    def test_padding_and_extra_macs_are_explicit(self):
        shape = GemmShape(3, 17, 33)
        counts = self.counts(shape)
        self.assertEqual(counts.useful_macs, 3 * 17 * 33)
        self.assertEqual(counts.padded_macs, 3 * 32 * 64 - 3 * 17 * 33)
        self.assertEqual(counts.windows, 4)
        self.assertEqual(counts.a_beats, 12)
        self.assertEqual(counts.b_beats, 64)
        self.assertEqual(counts.c_rows, 6)
        self.assertEqual(counts.array_cycles, 76)

    def test_m49_outputs_total_m_rows_not_ncommands_times_m(self):
        shape = GemmShape(49, 16, 64)
        commands = legalize(shape, self.geometry)
        self.assertEqual([(c.m_offset, c.m) for c in commands], [(0, 48), (48, 1)])
        counts = count_windows(shape, commands)
        self.assertEqual(counts.c_rows, 49)
        self.assertEqual(counts.c_beats, 98)
        self.assertEqual(counts.a_beats, 98)
        self.assertEqual(counts.windows, 4)
        self.assertEqual(counts.array_cycles, 162)

    def test_all_split_axes_have_deterministic_m_n_k_order(self):
        geometry = replace(self.geometry, mnk_width=6)
        shape = GemmShape(49, 49, 65)
        commands = legalize(shape, geometry)
        self.assertEqual(
            [(c.m_offset, c.n_offset, c.k_offset) for c in commands],
            [(m, n, k) for m in (0, 48) for n in (0, 48) for k in (0, 32, 64)],
        )
        for command in commands:
            self.assertLessEqual(max(command.m, command.n, command.k), 63)
            self.assertEqual(command.n % 16, 0)
            self.assertEqual(command.k % 32, 0)
            self.assertEqual(command.final_k, command.k_offset == 64)
        counts = count_windows(shape, commands)
        self.assertEqual(counts.c_rows, 49 * 4 * 3)
        self.assertEqual(counts.padded_macs, 49 * 64 * 96 - 49 * 49 * 65)

    def test_k_split_is_independent_commands_with_external_c(self):
        geometry = replace(self.geometry, mnk_width=8)
        shape = GemmShape(2, 16, 500)
        commands = legalize(shape, geometry)
        self.assertEqual([c.k for c in commands], [224, 224, 64])
        self.assertEqual([c.k_offset for c in commands], [0, 224, 448])
        self.assertEqual([c.final_k for c in commands], [False, False, True])
        outputs = [(w.command_index, w.k_pass, w.c_rows)
                   for w in iter_windows(commands) if w.c_rows]
        self.assertEqual(outputs, [(0, 6, 2), (1, 6, 2), (2, 1, 2)])
        counts = count_windows(shape, commands)
        self.assertEqual(counts.c_rows, 6)
        self.assertEqual(counts.c_beats, 12)
        self.assertEqual(counts.windows, 16)
        self.assertEqual(counts.array_cycles, 288)
        self.assertEqual(counts.padded_macs, 2 * 16 * 12)

    def test_w4a8_k_split_uses_64_granules(self):
        geometry = replace(self.geometry, mnk_width=8)
        for a_fmt, b_fmt in (("INT8", "INT4"), ("MXFP8", "MXFP4")):
            with self.subTest(formats=(a_fmt, b_fmt)):
                commands = legalize(GemmShape(1, 16, 257, a_fmt, b_fmt), geometry)
                self.assertEqual([c.k for c in commands], [192, 128])
                self.assertEqual([c.k_tiles for c in commands], [6, 4])

    def test_default_k_width_boundary(self):
        for fmt, expected in (("BF16", [65520, 16]), ("MXFP8", [65504, 32]),
                               ("MXFP4", [65472, 64])):
            with self.subTest(format=fmt):
                commands = legalize(GemmShape(1, 16, 65536, fmt, fmt), self.geometry)
                self.assertEqual([c.k for c in commands], expected)

    def test_invalid_shape_dimensions(self):
        for axis in ("m", "n", "k"):
            for invalid in (0, -1, 1.5, True, "1", None):
                with self.subTest(axis=axis, value=invalid):
                    kwargs = {"m": 1, "n": 16, "k": 32, axis: invalid}
                    with self.assertRaises(ValueError):
                        legalize(GemmShape(**kwargs), self.geometry)

    def test_invalid_output_widths_and_blocks(self):
        for bits in (0, 1, 3, 64, 8.0, True, None):
            with self.subTest(c_bits=bits):
                with self.assertRaises(ValueError):
                    legalize(GemmShape(1, 16, 32, c_bits=bits), self.geometry)
        for block in (0, -1, 16.0, True, None):
            with self.subTest(block=block):
                with self.assertRaises(ValueError):
                    legalize(GemmShape(1, 16, 32, block_size=block), self.geometry)

    def test_geometry_that_cannot_represent_a_granule_is_rejected(self):
        for geometry in (replace(self.geometry, mnk_width=4),
                         replace(self.geometry, mnk_width=0),
                         replace(self.geometry, c_buffer_depth=16)):
            with self.subTest(geometry=geometry):
                with self.assertRaises(ValueError):
                    legalize(GemmShape(1, 16, 32), geometry)


class TestWindows(GeometryTestCase):
    def test_exact_nt_then_kt_order_and_aggregate(self):
        shape = GemmShape(3, 17, 65)
        commands = legalize(shape, self.geometry)
        actual = list(iter_windows(commands))
        expected = [Window(0, n, k, 16, 3, 3 if k == 2 else 0)
                    for n in range(2) for k in range(3)]
        self.assertEqual(actual, expected)
        counts = count_windows(shape, commands)
        self.assertIsInstance(counts, WindowCounts)
        self.assertEqual(counts.windows, len(actual))
        self.assertEqual(counts.a_beats, sum(w.consume_beats for w in actual))
        self.assertEqual(counts.b_beats, sum(w.load_beats for w in actual))
        self.assertEqual(counts.c_rows, sum(w.c_rows for w in actual))
        self.assertEqual(counts.c_beats, sum(w.c_rows * 2 for w in actual))
        self.assertEqual(counts.compute_cycles, counts.a_beats)
        self.assertEqual(counts.load_cycles, counts.b_beats)
        self.assertEqual(counts.array_cycles, counts.compute_cycles + counts.load_cycles)

    def test_utilization_m1_and_m48(self):
        self.assertEqual(self.counts(GemmShape(1, 16, 32)).utilization, 1 / 17)
        self.assertEqual(self.counts(GemmShape(48, 16, 32)).utilization, 48 / 64)

    def test_c_beats_use_physical_dma_width_for_every_output_size(self):
        for bits, per_row in ((4, 1), (8, 1), (16, 1), (32, 2)):
            with self.subTest(bits=bits):
                counts = self.counts(GemmShape(3, 17, 32, c_bits=bits))
                self.assertEqual(counts.c_rows, 6)
                self.assertEqual(counts.c_beats, 6 * per_row)
        narrow = replace(self.geometry, c_dma_ext_w=64)
        for bits, per_row in ((4, 1), (8, 2), (16, 4), (32, 8)):
            with self.subTest(bits=bits, dma=64):
                counts = self.counts(GemmShape(3, 17, 32, c_bits=bits), narrow)
                self.assertEqual(counts.c_beats, 6 * per_row)
        odd_bytes = replace(self.geometry, c_dma_ext_w=24)
        self.assertEqual(self.counts(GemmShape(1, 16, 32, c_bits=4), odd_bytes).c_beats, 3)
        wide = replace(self.geometry, c_dma_ext_w=1024)
        self.assertEqual(self.counts(GemmShape(1, 16, 32, c_bits=4), wide).c_beats, 1)

    def test_aggregation_does_not_construct_or_iterate_windows(self):
        shape = GemmShape(1, 151936, 1024)
        commands = legalize(shape, self.geometry)
        with patch.object(windows, "iter_windows", side_effect=AssertionError("enumerated")), \
             patch.object(windows, "Window", side_effect=AssertionError("materialized")):
            self.assertEqual(count_windows(shape, iter(commands)).windows, 303872)

    def test_iterator_is_lazy_and_empty_input_is_empty(self):
        shape = GemmShape(1, 65520, 65504)
        iterator = iter_windows(legalize(shape, self.geometry))
        self.assertIs(iter(iterator), iterator)
        first = list(islice(iterator, 2))
        self.assertEqual([(w.n_tile, w.k_pass) for w in first], [(0, 0), (0, 1)])
        self.assertEqual(list(iter_windows(())), [])


if __name__ == "__main__":
    unittest.main()
