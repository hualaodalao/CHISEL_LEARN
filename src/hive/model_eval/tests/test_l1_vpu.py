"""Analytic VPU/CPU anchors and exclusions; no calibration or gem5 required."""

import math
import unittest
from dataclasses import replace

from hive.model_eval.common.opspec import OpSpec
from hive.model_eval.hw.profiles import G0, G0_PRO, MODEL_ANALYSIS_310
from hive.model_eval.l1 import dram
from hive.model_eval.l1.ledger import fold
from hive.model_eval.l1.params import ModelParams
from hive.model_eval.l1.vpu import cost


def operation(kind, **kwargs):
    fields = dict(id=f"test:{kind}", layer="test", name=kind,
                  engine="CPU" if kind == "sampling" else "VPU", kind=kind)
    fields.update(kwargs)
    return OpSpec(**fields)


def stage(ledger, name):
    return next(charge for charge in ledger.charges if charge.origin.stream.endswith(f":{name}"))


def softmax():
    return operation("softmax", vectors=448, length=4096, elements=1835008,
                     read_bytes=1835008 * 4, write_bytes=1835008 * 4)


class TestArithmetic(unittest.TestCase):
    def test_default_softmax_4096_has_688128_cycles_at_500mhz(self):
        ledger = cost(softmax(), G0, ModelParams())
        arithmetic = stage(ledger, "arithmetic")
        self.assertEqual(arithmetic.cycles, 688128)
        self.assertEqual(arithmetic.seconds, 688128 / 500_000_000)
        self.assertEqual(arithmetic.ops, 3 * 1835008)
        self.assertEqual(sum(c.cycles for c in ledger.charges), 688128)

    def test_norm_three_passes_round_each_pass_using_elements(self):
        op = operation("norm", vectors=3, length=17, elements=51, mac=99999)
        arithmetic = stage(cost(op, G0, ModelParams()), "arithmetic")
        self.assertEqual(arithmetic.cycles, 3 * math.ceil(51 / 8))
        self.assertEqual(arithmetic.cycles, 21)

    def test_prefill_softmax_uses_exact_causal_total_not_vectors_times_length(self):
        op = operation("softmax", vectors=10, length=5, elements=2 * sum(range(1, 6)))
        params = ModelParams(exp_cycles_per_element=1.25, reciprocal_cycles_per_vector=2)
        ledger = cost(op, G0, params)
        self.assertNotEqual(op.elements, op.vectors * op.length)
        self.assertEqual(stage(ledger, "arithmetic").cycles, 12)
        self.assertEqual(stage(ledger, "exp").cycles, 37.5)
        self.assertEqual(stage(ledger, "reciprocal").cycles, 20)

    def test_rope_residual_silu_gate_use_mac_at_fp32_lane_rate(self):
        for kind in ("rope", "residual", "silu_gate"):
            with self.subTest(kind=kind):
                op = operation(kind, elements=1000, mac=17)
                self.assertEqual(stage(cost(op, G0, ModelParams()), "arithmetic").cycles, 3)

    def test_all_non_fma_arithmetic_is_fallback_with_service_reference(self):
        for kind in ("norm", "rope", "residual", "silu_gate", "softmax"):
            with self.subTest(kind=kind):
                op = operation(kind, elements=33, vectors=3, length=11, mac=66)
                charge = stage(cost(op, G0, ModelParams()), "arithmetic")
                self.assertEqual(charge.source, "fallback")
                self.assertIn("arch.md:975-978", charge.evidence)
                self.assertIn("not measured non-FMA service time", charge.evidence)

    def test_attention_fp16_fma_peak_excludes_conversions_and_fp32_accumulation(self):
        ledger = cost(operation("attention_vpu", mac=33), G0, ModelParams())
        charge = stage(ledger, "arithmetic")
        self.assertEqual(charge.cycles, 3)
        self.assertEqual(charge.ops, 66)
        self.assertEqual(charge.source, "architecture_peak")
        self.assertIn("arch.md:937-947", charge.evidence)
        self.assertTrue(any("conversion" in item and "AR-HIVE-C035" in item for item in ledger.excluded))
        self.assertTrue(any("FP32 accumulation" in item and "AR-HIVE-C035" in item
                            for item in ledger.excluded))

    def test_lane_parameters_are_used_without_multicore_speedup(self):
        params = ModelParams(vpu_fp32_lanes=4, vpu_fp16_lanes=8)
        self.assertEqual(stage(cost(operation("norm", elements=17), G0, params), "arithmetic").cycles, 15)
        self.assertEqual(stage(cost(operation("attention_vpu", mac=17), G0, params), "arithmetic").cycles, 3)
        a = cost(softmax(), G0, ModelParams()).charges
        b = cost(softmax(), G0_PRO, ModelParams()).charges
        self.assertEqual(a, b)

    def test_unsupported_kind_or_engine_is_rejected(self):
        for op in (operation("gemm"), operation("norm", engine="CPU"),
                   operation("sampling", engine="VPU"), operation("norm", engine="GEMM")):
            with self.subTest(op=op):
                with self.assertRaises(ValueError):
                    cost(op, G0, ModelParams())


class TestSpecialFunctions(unittest.TestCase):
    def test_norm_missing_rsqrt_is_specific_exclusion_not_zero_charge(self):
        ledger = cost(operation("norm", vectors=729, length=128, elements=729 * 128), G0, ModelParams())
        self.assertTrue(any("rsqrt_cycles_per_vector" in item and "729 calls" in item
                            for item in ledger.excluded))
        self.assertFalse(any(c.resource == "vpu_rsqrt" for c in ledger.charges))

    def test_missing_softmax_and_silu_exp_reciprocal_have_no_service_charges(self):
        for kind in ("softmax", "silu_gate"):
            with self.subTest(kind=kind):
                ledger = cost(operation(kind, elements=20, vectors=4, length=5, mac=40), G0, ModelParams())
                for name in ("exp_cycles_per_element", "reciprocal_cycles_per_vector"):
                    self.assertTrue(any(name in item and "AR-HIVE-S022" in item for item in ledger.excluded))
                self.assertFalse(any(c.resource in ("vpu_exp", "vpu_reciprocal") for c in ledger.charges))

    def test_exp_1_point_25_adds_2293760_cycles_for_448_vectors_of_4096(self):
        ledger = cost(softmax(), G0, ModelParams(exp_cycles_per_element=1.25))
        exp = stage(ledger, "exp")
        self.assertEqual(exp.cycles, 2293760)
        self.assertEqual(exp.ops, 1835008)
        self.assertEqual(exp.seconds, 2293760 / 500_000_000)
        self.assertEqual(exp.source, "scenario")
        self.assertEqual(sum(c.cycles for c in ledger.charges), 688128 + 2293760)
        self.assertFalse(any("exp_cycles_per_element is unset" in item for item in ledger.excluded))
        self.assertTrue(any("reciprocal_cycles_per_vector is unset" in item for item in ledger.excluded))

    def test_softmax_reciprocal_is_once_per_vector(self):
        ledger = cost(softmax(), G0, ModelParams(reciprocal_cycles_per_vector=2.5))
        charge = stage(ledger, "reciprocal")
        self.assertEqual(charge.ops, 448)
        self.assertEqual(charge.cycles, 1120)
        self.assertEqual(charge.source, "scenario")

    def test_norm_729_calls_come_from_input_vectors_across_norm_shapes(self):
        # 28 pre/post norms, 28*16 Q norms, 28*8 K norms, one final norm.
        shapes = ((28, 1024), (28, 1024), (448, 128), (224, 128), (1, 1024))
        params = ModelParams(rsqrt_cycles_per_vector=2.5)
        charges = []
        for index, (vectors, length) in enumerate(shapes):
            op = operation("norm", id=f"norm:{index}", vectors=vectors, length=length,
                           elements=vectors * length, mac=2 * vectors * length)
            ledger = cost(op, G0, params)
            charge = stage(ledger, "rsqrt")
            self.assertEqual(charge.ops, vectors)
            self.assertEqual(charge.cycles, 2.5 * vectors)
            self.assertFalse(any("rsqrt" in item for item in ledger.excluded))
            charges.append(charge)
        self.assertEqual(sum(c.ops for c in charges), 729)
        self.assertEqual(sum(c.cycles for c in charges), 1822.5)

    def test_silu_gate_exp_and_reciprocal_each_apply_to_elements(self):
        op = operation("silu_gate", elements=17, vectors=1, length=17, mac=51)
        ledger = cost(op, G0, ModelParams(exp_cycles_per_element=1.25, reciprocal_cycles_per_vector=2))
        self.assertEqual(stage(ledger, "arithmetic").cycles, 7)
        self.assertEqual(stage(ledger, "exp").cycles, 21.25)
        self.assertEqual(stage(ledger, "reciprocal").cycles, 34)
        self.assertEqual(stage(ledger, "reciprocal").ops, 17)

    def test_explicit_zero_is_a_scenario_not_an_unset_input(self):
        params = ModelParams(exp_cycles_per_element=0, reciprocal_cycles_per_vector=0)
        ledger = cost(softmax(), G0, params)
        for name in ("exp", "reciprocal"):
            self.assertEqual(stage(ledger, name).cycles, 0)
            self.assertEqual(stage(ledger, name).source, "scenario")
        self.assertFalse(ledger.excluded)


class TestTrafficAndUnknownTimes(unittest.TestCase):
    def test_local_reads_and_writes_use_one_combined_port(self):
        ledger = cost(operation("residual", mac=8, read_bytes=65, write_bytes=31), G0, ModelParams())
        local = [c for c in ledger.charges if c.resource == "local_port"]
        self.assertEqual(len(local), 1)
        self.assertEqual(local[0].bytes, 96)
        self.assertEqual(local[0].seconds, 96 / (32 * 500_000_000))
        self.assertEqual(local[0].category, "vpu")
        self.assertIn("read/write combined", local[0].evidence)

    def test_norm_coefficients_are_a_distinct_dram_read_not_local_traffic(self):
        op = operation("norm", vectors=3, length=17, elements=51,
                       read_bytes=204, write_bytes=204, weight_bytes=68)
        ledger = cost(op, G0, ModelParams(weight_policy="resident", rsqrt_cycles_per_vector=2))
        coefficients = stage(ledger, "norm_coefficients")
        self.assertEqual(coefficients.category, "dram")
        self.assertEqual(coefficients.bytes, 68)
        self.assertEqual(coefficients.seconds, dram.seconds(68, G0))
        self.assertEqual(coefficients.origin.key(), (f"{op.id}:norm_coefficients", "vpu"))
        self.assertEqual(stage(ledger, "local_read_write").bytes, 408)
        self.assertEqual(len(fold(ledger)), len(ledger.charges))

    def test_profile_310_unknown_vpu_and_local_times_are_none_not_fabricated(self):
        params = ModelParams(exp_cycles_per_element=1.25, reciprocal_cycles_per_vector=2)
        ledger = cost(softmax(), MODEL_ANALYSIS_310, params)
        self.assertTrue(all(c.seconds is None for c in ledger.charges))
        self.assertEqual(stage(ledger, "arithmetic").cycles, 688128)
        self.assertEqual(stage(ledger, "exp").cycles, 2293760)
        self.assertEqual(stage(ledger, "local_read_write").bytes, 2 * 1835008 * 4)
        self.assertTrue(any("freq_hz" in item for item in ledger.excluded))
        self.assertTrue(any("local_iface_bytes_per_s_per_core" in item for item in ledger.excluded))
        self.assertEqual(fold(ledger), [])

    def test_unknown_vpu_clock_does_not_erase_known_norm_dram_time(self):
        ledger = cost(operation("norm", elements=32, vectors=2, length=16,
                                read_bytes=128, write_bytes=128, weight_bytes=64),
                      MODEL_ANALYSIS_310, ModelParams(rsqrt_cycles_per_vector=2))
        self.assertIsNone(stage(ledger, "arithmetic").seconds)
        self.assertIsNone(stage(ledger, "rsqrt").seconds)
        self.assertIsNone(stage(ledger, "local_read_write").seconds)
        self.assertEqual(stage(ledger, "norm_coefficients").seconds, 64 / 310e9)

    def test_known_clock_but_unknown_local_rate_keeps_arithmetic_time(self):
        profile = replace(G0, local_iface_bytes_per_cycle=None)
        ledger = cost(operation("rope", mac=17, read_bytes=64), profile, ModelParams())
        self.assertEqual(stage(ledger, "arithmetic").seconds, 3 / G0.freq_hz)
        self.assertIsNone(stage(ledger, "local_read_write").seconds)
        self.assertTrue(any("local_iface_bytes_per_s_per_core" in item for item in ledger.excluded))
        self.assertFalse(any("freq_hz" in item for item in ledger.excluded))


class TestOverlapAndOrigins(unittest.TestCase):
    def test_default_serial_charges_have_distinct_stage_origins(self):
        params = ModelParams(exp_cycles_per_element=1.25, reciprocal_cycles_per_vector=2)
        ledger = cost(softmax(), G0, params)
        self.assertTrue(all(c.overlap_class == "serial" for c in ledger.charges))
        self.assertEqual(len({c.origin.key() for c in ledger.charges}), len(ledger.charges))
        self.assertTrue(all(c.origin.producer == "vpu" for c in ledger.charges))
        self.assertEqual(sum(c.seconds for c in fold(ledger)), sum(c.seconds for c in ledger.charges))
        self.assertFalse(ledger.coupling)

    def test_overlap_requires_nonempty_evidence(self):
        for evidence in ("", "   "):
            with self.subTest(evidence=evidence):
                with self.assertRaises(ValueError):
                    ModelParams(overlap=True, overlap_evidence=evidence)

    def test_evidence_alone_does_not_enable_overlap(self):
        ledger = cost(softmax(), G0, ModelParams(overlap_evidence="hypothetical double buffer"))
        self.assertTrue(all(c.overlap_class == "serial" for c in ledger.charges))

    def test_only_arithmetic_and_combined_local_share_evidenced_overlap(self):
        evidence = "explicit double-buffer scenario with independent local transfers"
        params = ModelParams(overlap=True, overlap_evidence=evidence,
                             exp_cycles_per_element=1.25, reciprocal_cycles_per_vector=2)
        ledger = cost(softmax(), G0, params)
        overlapped = [c for c in ledger.charges if c.overlap_class != "serial"]
        self.assertEqual(len(overlapped), 2)
        self.assertEqual({c.resource for c in overlapped}, {"vpu_fp32", "local_port"})
        self.assertEqual(len({c.overlap_class for c in overlapped}), 1)
        self.assertTrue(all(evidence in c.evidence for c in overlapped))
        self.assertEqual(stage(ledger, "exp").overlap_class, "serial")
        self.assertEqual(stage(ledger, "reciprocal").overlap_class, "serial")
        self.assertEqual(len(fold(ledger)), len(ledger.charges))

    def test_norm_rsqrt_and_coefficients_stay_serial_under_overlap(self):
        op = operation("norm", elements=32, vectors=2, length=16,
                       read_bytes=128, write_bytes=128, weight_bytes=64)
        params = ModelParams(overlap=True, overlap_evidence="double-buffer scenario", rsqrt_cycles_per_vector=2)
        ledger = cost(op, G0, params)
        self.assertEqual(stage(ledger, "rsqrt").overlap_class, "serial")
        self.assertEqual(stage(ledger, "norm_coefficients").overlap_class, "serial")


class TestCpuSampling(unittest.TestCase):
    def test_default_sampling_excludes_algorithm_and_service_but_charges_logit_reads(self):
        op = operation("sampling", elements=151936, vectors=1, length=151936, read_bytes=151936 * 4)
        ledger = cost(op, G0, ModelParams())
        self.assertTrue(any("algorithm/service" in item and "cpu_cycles_per_comparison" in item
                            and "cpu_freq_hz" in item for item in ledger.excluded))
        self.assertFalse(any(c.resource == "cpu_comparisons" for c in ledger.charges))
        self.assertEqual(len(ledger.charges), 1)
        read = stage(ledger, "logit_read")
        self.assertEqual(read.category, "cpu")
        self.assertEqual(read.origin.producer, "cpu")
        self.assertEqual(read.bytes, op.read_bytes)
        self.assertEqual(read.seconds, dram.local_seconds(op.read_bytes, G0))

    def test_scenario_is_deterministic_nonzero_and_sorts_each_vector_separately(self):
        op = operation("sampling", elements=30, length=10, vectors=3, read_bytes=120)
        params = ModelParams(cpu_cycles_per_comparison=2.5, cpu_freq_hz=1_000_000_000)
        ledger = cost(op, G0, params)
        self.assertEqual(ledger.to_dict(), cost(op, G0, params).to_dict())
        charge = stage(ledger, "comparisons")
        self.assertEqual(charge.ops, 3 * math.ceil(10 * math.log2(10)))
        self.assertEqual(charge.ops, 102)
        self.assertNotEqual(charge.ops, math.ceil(op.elements * math.log2(op.elements)))
        self.assertEqual(charge.cycles, 255)
        self.assertGreater(charge.seconds, 0)
        self.assertEqual(charge.seconds, 255 / params.cpu_freq_hz)
        self.assertNotEqual(charge.seconds, 255 / G0.freq_hz)
        self.assertEqual(charge.source, "scenario")
        self.assertIn("full_sort_comparison_budget", charge.evidence)
        self.assertFalse(ledger.excluded)
        self.assertEqual(len(fold(ledger)), len(ledger.charges))

    def test_either_missing_cpu_input_excludes_numeric_estimate(self):
        op = operation("sampling", elements=100, length=50, vectors=2)
        cases = ((ModelParams(cpu_cycles_per_comparison=2), "cpu_freq_hz"),
                 (ModelParams(cpu_freq_hz=1e9), "cpu_cycles_per_comparison"))
        for params, missing in cases:
            with self.subTest(missing=missing):
                ledger = cost(op, G0, params)
                self.assertFalse(ledger.charges)
                self.assertTrue(any(missing in item and "excluded" in item for item in ledger.excluded))

    def test_cpu_scenario_can_be_known_on_310_while_local_read_time_is_unknown(self):
        op = operation("sampling", elements=16, vectors=2, length=8, read_bytes=64)
        params = ModelParams(cpu_cycles_per_comparison=2, cpu_freq_hz=1e9)
        ledger = cost(op, MODEL_ANALYSIS_310, params)
        self.assertEqual(stage(ledger, "comparisons").cycles, 96)
        self.assertEqual(stage(ledger, "comparisons").seconds, 96 / 1e9)
        self.assertIsNone(stage(ledger, "logit_read").seconds)
        self.assertTrue(any("CPU local transfer" in item for item in ledger.excluded))

    def test_cpu_sampling_remains_serial_even_with_vpu_overlap_scenario(self):
        op = operation("sampling", elements=8, vectors=1, length=8, read_bytes=32)
        params = ModelParams(cpu_cycles_per_comparison=2, cpu_freq_hz=1e9,
                             overlap=True, overlap_evidence="VPU double-buffer scenario")
        ledger = cost(op, G0, params)
        self.assertTrue(all(c.overlap_class == "serial" for c in ledger.charges))
        self.assertEqual(len({c.origin.key() for c in ledger.charges}), len(ledger.charges))

    def test_missing_vector_shape_does_not_claim_zero_sampling_cost(self):
        ledger = cost(operation("sampling", elements=151936), G0,
                      ModelParams(cpu_cycles_per_comparison=2, cpu_freq_hz=1e9))
        self.assertFalse(ledger.charges)
        self.assertTrue(any("length (vocab)" in item for item in ledger.excluded))


if __name__ == "__main__":
    unittest.main()
