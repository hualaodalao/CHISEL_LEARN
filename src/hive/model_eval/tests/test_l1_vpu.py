"""Typed single-engine VPU/CPU anchors, explicit unknowns and buffer lifetimes."""

import math
import unittest
from dataclasses import replace

from hive.model_eval.common.opspec import CpuSpec, PrimitiveTask, TensorRef, VpuSpec
from hive.model_eval.hw.geometry import load_array_geometry
from hive.model_eval.hw.profiles import G0, G0_PRO, MODEL_ANALYSIS_310
from hive.model_eval.hw.topology import default_topology
from hive.model_eval.l1 import cpu_model, vpu_model
from hive.model_eval.l1.ledger import fold
from hive.model_eval.l1.params import ModelParams
from hive.model_eval.l1.residency import ExecutionPlan


GEOMETRY = load_array_geometry()


def task(kind, *, task_id=None, **kwargs):
    engine = "CPU" if kind == "sampling" else "VPU"
    spec = CpuSpec(**kwargs) if engine == "CPU" else VpuSpec(kind, **kwargs)
    return PrimitiveTask(task_id or f"test:{kind}", "test:op", engine, spec)


def estimate(work, profile=G0, params=None, *, alignment=32, config=None):
    params = params or ModelParams()
    config = config or default_topology(profile, GEOMETRY, params).instances[work.engine_kind.lower() + "0"]
    model = cpu_model if work.engine_kind == "CPU" else vpu_model
    return model.estimate(work, config, ExecutionPlan(activation_policy="stream", alignment=alignment))


def stage(cost, name):
    return next(c for c in cost.ledger.charges if c.origin.stream == f"{cost.task_id}:{name}")


def phase(cost, name):
    return next(p for p in cost.phases if p.id == f"{cost.task_id}:{name}")


def softmax():
    return task("softmax", vectors=448, length=4096, elements=1835008,
                read_bytes=1835008 * 4, write_bytes=1835008 * 4)


class TestArithmetic(unittest.TestCase):
    def test_default_softmax_4096_has_688128_cycles_at_500mhz(self):
        cost = estimate(softmax())
        arithmetic = stage(cost, "arithmetic")
        self.assertEqual(arithmetic.cycles, 688128)
        self.assertEqual(arithmetic.seconds, 688128 / 500_000_000)
        self.assertEqual(arithmetic.ops, 3 * 1835008)
        self.assertEqual(sum(c.cycles for c in cost.ledger.charges), 688128)
        self.assertFalse(cost.complete)

    def test_norm_three_passes_round_each_pass_using_elements(self):
        work = task("norm", vectors=3, length=17, elements=51, mac=99999)
        arithmetic = stage(estimate(work), "arithmetic")
        self.assertEqual(arithmetic.cycles, 3 * math.ceil(51 / 8))
        self.assertEqual(arithmetic.cycles, 21)
        self.assertEqual(arithmetic.ops, 153)

    def test_prefill_softmax_uses_causal_elements_not_vectors_times_length(self):
        work = task("softmax", vectors=10, length=5, elements=2 * sum(range(1, 6)))
        params = ModelParams(exp_cycles_per_element=1.25, reciprocal_cycles_per_vector=2)
        cost = estimate(work, params=params)
        self.assertNotEqual(work.spec.elements, work.spec.vectors * work.spec.length)
        self.assertEqual(stage(cost, "arithmetic").cycles, 12)
        self.assertEqual(stage(cost, "exp").cycles, 37.5)
        self.assertEqual(stage(cost, "exp").ops, 30)
        self.assertEqual(stage(cost, "reciprocal").cycles, 20)
        self.assertTrue(cost.complete)

    def test_rope_residual_silu_gate_use_mac_at_fp32_lane_rate(self):
        for kind in ("rope", "residual", "silu_gate"):
            with self.subTest(kind=kind):
                cost = estimate(task(kind, elements=1000, mac=17))
                self.assertEqual(stage(cost, "arithmetic").cycles, 3)
                self.assertEqual(stage(cost, "arithmetic").ops, 17)

    def test_non_fma_arithmetic_is_a_cited_fallback_not_measured_service(self):
        for kind in ("norm", "rope", "residual", "silu_gate", "softmax", "k_accum"):
            with self.subTest(kind=kind):
                charge = stage(estimate(task(kind, elements=33, vectors=3, length=11, mac=66)), "arithmetic")
                self.assertEqual(charge.source, "fallback")
                self.assertIn("arch.md:975-978", charge.evidence)
                self.assertIn("not measured non-FMA service time", charge.evidence)

    def test_fp16_attention_peak_has_no_implicit_mixed_accumulation(self):
        work = task("attention_vpu", mac=33, input_dtype="FP16", output_dtype="FP16")
        cost = estimate(work)
        charge = stage(cost, "arithmetic")
        self.assertEqual(charge.cycles, 3)
        self.assertEqual(charge.ops, 66)
        self.assertEqual(charge.source, "architecture_peak")
        self.assertIn("arch.md:937-947", charge.evidence)
        self.assertTrue(cost.complete)
        self.assertEqual(len(cost.phases), 1)

    def test_fp16_attention_fp32_output_requires_unknown_conversion_and_accumulation(self):
        cost = estimate(task("attention_vpu", mac=33, input_dtype="FP16", output_dtype="FP32"))
        arithmetic = phase(cost, "arithmetic")
        conversion = phase(cost, "conversion")
        accumulation = phase(cost, "fp32_accumulation")
        self.assertIsNone(conversion.seconds)
        self.assertIsNone(accumulation.seconds)
        self.assertEqual(conversion.dependencies, (arithmetic.id,))
        self.assertEqual(accumulation.dependencies, (conversion.id,))
        self.assertIn("AR-HIVE-C035", conversion.evidence)
        self.assertIn("mixed-precision accumulation", accumulation.evidence)
        self.assertFalse(cost.complete)
        self.assertEqual(cost.known_service_s, 3 / G0.freq_hz)

    def test_lane_parameters_are_used_without_multicore_speedup(self):
        params = ModelParams(vpu_fp32_lanes=4, vpu_fp16_lanes=8)
        self.assertEqual(stage(estimate(task("norm", elements=17), params=params), "arithmetic").cycles, 15)
        work = task("attention_vpu", mac=17, input_dtype="FP16", output_dtype="FP16")
        self.assertEqual(stage(estimate(work, params=params), "arithmetic").cycles, 3)
        self.assertEqual(estimate(softmax()).ledger.to_dict(), estimate(softmax(), G0_PRO).ledger.to_dict())

    def test_fp32_k_merge_uses_elements_and_int32_is_never_silently_fp32(self):
        cost = estimate(task("k_accum", elements=17, mac=999))
        self.assertEqual(stage(cost, "arithmetic").cycles, 3)
        self.assertEqual(stage(cost, "arithmetic").ops, 17)
        self.assertTrue(cost.complete)
        for output in ("INT32", "FP32"):
            with self.subTest(output=output):
                cost = estimate(task("k_accum", elements=17, input_dtype="INT32", output_dtype=output))
                charge = stage(cost, "arithmetic")
                self.assertEqual(charge.resource, "vpu_k_accum")
                self.assertEqual(charge.source, "unknown")
                self.assertIsNone(charge.seconds)
                self.assertEqual(charge.ops, 17)
                self.assertIn("no silent FP32 typecast", charge.evidence)
                self.assertFalse(cost.complete)
                if output == "FP32":
                    self.assertIsNone(phase(cost, "conversion").seconds)

    def test_unsupported_integer_arithmetic_and_attention_are_unknown(self):
        for kind in ("residual", "attention_vpu"):
            with self.subTest(kind=kind):
                cost = estimate(task(kind, elements=8, mac=8, input_dtype="INT8", output_dtype="INT8"))
                self.assertIsNone(phase(cost, "arithmetic").seconds)
                self.assertEqual(stage(cost, "arithmetic").source, "unknown")
                self.assertFalse(cost.complete)

    def test_unsupported_kind_and_wrong_engine_config_are_rejected(self):
        with self.assertRaises(ValueError):
            estimate(task("gemm"))
        topology = default_topology(G0, GEOMETRY, ModelParams())
        plan = ExecutionPlan(activation_policy="stream")
        with self.assertRaises(ValueError):
            vpu_model.estimate(task("norm"), topology.instances["cpu0"], plan)
        with self.assertRaises(ValueError):
            vpu_model.estimate(task("sampling"), topology.instances["vpu0"], plan)
        with self.assertRaises(ValueError):
            cpu_model.estimate(task("norm"), topology.instances["cpu0"], plan)


class TestSpecialFunctions(unittest.TestCase):
    def test_missing_rsqrt_is_an_explicit_unknown_phase_not_zero_service(self):
        cost = estimate(task("norm", vectors=729, length=128, elements=729 * 128))
        charge = stage(cost, "rsqrt")
        self.assertEqual(charge.ops, 729)
        self.assertIsNone(charge.seconds)
        self.assertIsNone(phase(cost, "rsqrt").seconds)
        self.assertEqual(charge.source, "unknown")
        self.assertTrue(any("rsqrt_cycles_per_vector" in item and "729 calls" in item
                            for item in cost.excluded_costs))
        self.assertFalse(cost.complete)
        self.assertGreater(cost.known_service_s, 0)

    def test_missing_exp_and_reciprocal_are_explicit_unknown_phases(self):
        for kind in ("softmax", "silu_gate"):
            with self.subTest(kind=kind):
                cost = estimate(task(kind, elements=20, vectors=4, length=5, mac=40))
                for name, count in (("exp", 20), ("reciprocal", 4 if kind == "softmax" else 20)):
                    charge = stage(cost, name)
                    self.assertIsNone(charge.seconds)
                    self.assertIsNone(phase(cost, name).seconds)
                    self.assertEqual(charge.ops, count)
                    self.assertEqual(charge.source, "unknown")
                    self.assertIn("AR-HIVE-S022", charge.evidence)
                self.assertFalse(cost.complete)

    def test_exp_1_point_25_adds_2293760_cycles_for_4096_softmax(self):
        cost = estimate(softmax(), params=ModelParams(exp_cycles_per_element=1.25))
        exp = stage(cost, "exp")
        self.assertEqual(exp.cycles, 2293760)
        self.assertEqual(exp.ops, 1835008)
        self.assertEqual(exp.seconds, 2293760 / 500_000_000)
        self.assertEqual(exp.source, "scenario")
        self.assertEqual(sum(c.cycles for c in cost.ledger.charges), 688128 + 2293760)
        self.assertIsNone(phase(cost, "reciprocal").seconds)
        self.assertFalse(cost.complete)

    def test_softmax_reciprocal_is_once_per_vector(self):
        cost = estimate(softmax(), params=ModelParams(reciprocal_cycles_per_vector=2.5))
        charge = stage(cost, "reciprocal")
        self.assertEqual(charge.ops, 448)
        self.assertEqual(charge.cycles, 1120)
        self.assertEqual(charge.source, "scenario")

    def test_norm_729_calls_come_from_input_vectors_across_norm_shapes(self):
        shapes = ((28, 1024), (28, 1024), (448, 128), (224, 128), (1, 1024))
        params = ModelParams(rsqrt_cycles_per_vector=2.5)
        charges = []
        for index, (vectors, length) in enumerate(shapes):
            work = task("norm", task_id=f"norm:{index}", vectors=vectors, length=length,
                        elements=vectors * length, mac=2 * vectors * length)
            cost = estimate(work, params=params)
            charge = stage(cost, "rsqrt")
            self.assertEqual(charge.ops, vectors)
            self.assertEqual(charge.cycles, 2.5 * vectors)
            self.assertTrue(cost.complete)
            charges.append(charge)
        self.assertEqual(sum(c.ops for c in charges), 729)
        self.assertEqual(sum(c.cycles for c in charges), 1822.5)

    def test_silu_exp_and_reciprocal_each_apply_to_elements(self):
        work = task("silu_gate", elements=17, vectors=1, length=17, mac=51)
        cost = estimate(work, params=ModelParams(exp_cycles_per_element=1.25, reciprocal_cycles_per_vector=2))
        self.assertEqual(stage(cost, "arithmetic").cycles, 7)
        self.assertEqual(stage(cost, "exp").cycles, 21.25)
        self.assertEqual(stage(cost, "reciprocal").cycles, 34)
        self.assertEqual(stage(cost, "reciprocal").ops, 17)
        self.assertTrue(cost.complete)

    def test_explicit_zero_is_a_scenario_not_an_unset_input(self):
        cost = estimate(softmax(), params=ModelParams(exp_cycles_per_element=0, reciprocal_cycles_per_vector=0))
        for name in ("exp", "reciprocal"):
            self.assertEqual(stage(cost, name).seconds, 0)
            self.assertEqual(stage(cost, name).source, "scenario")
        self.assertTrue(cost.complete)
        self.assertFalse(any("unknown service" in item for item in cost.excluded_costs))

    def test_convert_is_explicit_unknown_even_for_same_dtype_layout_work(self):
        for input_dtype, output_dtype in (("FP32", "FP16"), ("FP32", "MXFP8"), ("FP32", "FP32")):
            with self.subTest(types=(input_dtype, output_dtype)):
                cost = estimate(task("convert", elements=17, read_bytes=68, write_bytes=34,
                                     input_dtype=input_dtype, output_dtype=output_dtype))
                self.assertIsNone(phase(cost, "conversion").seconds)
                self.assertEqual(stage(cost, "conversion").source, "unknown")
                self.assertIn("AR-HIVE-C035", phase(cost, "conversion").evidence)
                self.assertFalse(cost.complete)
                self.assertGreater(cost.known_service_s, 0)

    def test_fp16_norm_keeps_fp32_lane_budget_and_requires_conversion(self):
        cost = estimate(task("norm", elements=17, vectors=1, length=17,
                             input_dtype="FP16", output_dtype="FP16"),
                        params=ModelParams(rsqrt_cycles_per_vector=2))
        self.assertEqual(stage(cost, "arithmetic").cycles, 9)
        self.assertIsNone(phase(cost, "conversion").seconds)
        self.assertFalse(cost.complete)


class TestTrafficAndUnknownTimes(unittest.TestCase):
    def test_local_reads_and_writes_use_one_combined_port(self):
        cost = estimate(task("residual", mac=8, read_bytes=65, write_bytes=31))
        local = [c for c in cost.ledger.charges if c.resource == "local_port"]
        self.assertEqual(len(local), 1)
        self.assertEqual(local[0].bytes, 96)
        self.assertEqual(local[0].seconds, 96 / (32 * 500_000_000))
        self.assertEqual(local[0].category, "vpu")
        self.assertIn("read/write combined", local[0].evidence)

    def test_boundary_ingress_precedes_local_reuse_and_egress_follows_compute(self):
        work = task("residual", mac=8, read_bytes=65, write_bytes=31,
                    dram_read_bytes=17, dram_write_bytes=9)
        work = replace(work, inputs=(TensorRef("source"),), outputs=(TensorRef("dest"),))
        cost = estimate(work)
        ingress = phase(cost, "activation_stage_read")
        arithmetic = phase(cost, "arithmetic")
        local = phase(cost, "local_read_write")
        egress = phase(cost, "activation_stage_write")
        self.assertEqual(arithmetic.dependencies, (ingress.id,))
        self.assertEqual(local.dependencies, (arithmetic.id,))
        self.assertEqual(egress.dependencies, (local.id,))
        self.assertEqual(cost.known_service_s, sum(p.seconds for p in cost.phases))
        self.assertEqual(len(fold(cost.ledger)), len(cost.phases))
        for direction, boundary_bytes, local_bytes, tensor in (("read", 17, 65, "source"),
                                                              ("write", 9, 31, "dest")):
            records = [t for t in cost.traffic if t.direction == direction]
            self.assertEqual(len(records), 2)
            self.assertEqual({t.stream_id for t in records}, {f"{work.id}:{direction}"})
            self.assertEqual({t.owner_task_id for t in records}, {work.id})
            self.assertEqual({t.tensor_id for t in records}, {tensor})
            self.assertEqual({t.memory_domain: t.physical_bytes for t in records},
                             {"dram": boundary_bytes, "core0.sram": local_bytes})

    def test_norm_coefficients_must_be_owned_by_separate_dma_not_vpu(self):
        work = task("norm", vectors=3, length=17, elements=51, read_bytes=204, write_bytes=204)
        cost = estimate(work, params=ModelParams(weight_policy="resident", rsqrt_cycles_per_vector=2))
        self.assertTrue(all(c.category == "vpu" for c in cost.ledger.charges))
        self.assertEqual(stage(cost, "local_read_write").bytes, 408)
        with self.assertRaises(TypeError):
            VpuSpec("norm", weight_bytes=68)

    def test_profile_310_unknown_vpu_and_local_times_are_none_not_fabricated(self):
        params = ModelParams(exp_cycles_per_element=1.25, reciprocal_cycles_per_vector=2)
        cost = estimate(softmax(), MODEL_ANALYSIS_310, params)
        self.assertTrue(all(p.seconds is None for p in cost.phases))
        self.assertEqual(stage(cost, "arithmetic").cycles, 688128)
        self.assertEqual(stage(cost, "exp").cycles, 2293760)
        self.assertEqual(stage(cost, "local_read_write").bytes, 2 * 1835008 * 4)
        self.assertTrue(any("freq_hz" in item for item in cost.excluded_costs))
        self.assertTrue(any("local_iface_bytes_per_s_per_core" in item for item in cost.excluded_costs))
        self.assertTrue(all(c.seconds is None for c in fold(cost.ledger)))
        self.assertFalse(cost.complete)

    def test_unknown_vpu_clock_preserves_known_boundary_time(self):
        work = task("norm", elements=32, vectors=2, length=16, read_bytes=128,
                    write_bytes=128, dram_read_bytes=64, dram_write_bytes=32)
        cost = estimate(work, MODEL_ANALYSIS_310, ModelParams(rsqrt_cycles_per_vector=2))
        for name in ("arithmetic", "rsqrt", "local_read_write"):
            self.assertIsNone(phase(cost, name).seconds)
        self.assertAlmostEqual(cost.known_service_s, 96 / 310e9, delta=1e-22)
        self.assertFalse(cost.complete)

    def test_known_clock_unknown_local_rate_keeps_arithmetic_time(self):
        profile = replace(G0, local_iface_bytes_per_cycle=None)
        cost = estimate(task("rope", mac=17, read_bytes=64), profile)
        self.assertEqual(stage(cost, "arithmetic").seconds, 3 / G0.freq_hz)
        self.assertIsNone(phase(cost, "local_read_write").seconds)
        self.assertEqual(cost.known_service_s, 3 / G0.freq_hz)
        self.assertFalse(cost.complete)

    def test_chunk_padding_affects_allocations_only_and_lifetimes_are_explicit(self):
        work = task("residual", mac=17, read_bytes=100, write_bytes=65,
                    dram_read_bytes=33, dram_write_bytes=17)
        base, aligned = estimate(work), estimate(work, alignment=128)
        self.assertEqual(base.traffic, aligned.traffic)
        self.assertEqual(base.ledger.to_dict(), aligned.ledger.to_dict())
        self.assertEqual([b.size_bytes for b in base.buffers], [b.size_bytes for b in aligned.buffers])
        self.assertTrue(all(b.size_bytes == 32 and b.allocated_bytes == 128 for b in aligned.buffers))
        buffers = {b.id.rsplit(":", 1)[-1]: b for b in aligned.buffers}
        self.assertEqual(buffers["read_chunk"].acquire_phase, phase(aligned, "activation_stage_read").id)
        self.assertEqual(buffers["read_chunk"].release_phase, phase(aligned, "local_read_write").id)
        self.assertEqual(buffers["write_chunk"].release_phase, phase(aligned, "activation_stage_write").id)
        self.assertEqual(buffers["arithmetic_scratch"].acquire_phase, phase(aligned, "arithmetic").id)
        self.assertEqual(buffers["arithmetic_scratch"].release_phase, phase(aligned, "local_read_write").id)


class TestOverlapAndOrigins(unittest.TestCase):
    def test_default_serial_phases_have_distinct_origins_and_task_ownership(self):
        params = ModelParams(exp_cycles_per_element=1.25, reciprocal_cycles_per_vector=2)
        cost = estimate(softmax(), params=params)
        self.assertTrue(all(c.overlap_class == "serial" for c in cost.ledger.charges))
        self.assertEqual(len({c.origin.key() for c in cost.ledger.charges}), len(cost.phases))
        self.assertTrue(all(c.origin.producer == cost.task_id for c in cost.ledger.charges))
        self.assertAlmostEqual(sum(c.seconds for c in fold(cost.ledger)), cost.known_service_s)
        self.assertFalse(cost.ledger.coupling)

    def test_overlap_requires_nonempty_evidence(self):
        for evidence in ("", "   "):
            with self.subTest(evidence=evidence), self.assertRaises(ValueError):
                ModelParams(overlap=True, overlap_evidence=evidence)

    def test_evidence_alone_does_not_enable_overlap(self):
        cost = estimate(softmax(), params=ModelParams(overlap_evidence="hypothetical double buffer"))
        self.assertFalse(cost.metrics["overlap"])
        self.assertTrue(all(c.overlap_class == "serial" for c in cost.ledger.charges))

    def test_evidenced_overlap_joins_before_special_functions_after_ingress(self):
        evidence = "explicit double-buffer scenario with independent local transfers"
        params = ModelParams(overlap=True, overlap_evidence=evidence,
                             exp_cycles_per_element=1.25, reciprocal_cycles_per_vector=2)
        work = replace(softmax(), spec=replace(softmax().spec, dram_read_bytes=64))
        cost = estimate(work, params=params)
        arithmetic, local = phase(cost, "arithmetic"), phase(cost, "local_read_write")
        ingress = phase(cost, "activation_stage_read")
        self.assertEqual(arithmetic.dependencies, (ingress.id,))
        self.assertEqual(local.dependencies, (ingress.id,))
        self.assertEqual(phase(cost, "exp").dependencies, (arithmetic.id, local.id))
        self.assertEqual(phase(cost, "reciprocal").dependencies, (phase(cost, "exp").id,))
        overlapped = [c for c in cost.ledger.charges if c.overlap_class != "serial"]
        self.assertEqual({c.resource for c in overlapped}, {"vpu_fp32", "local_port"})
        self.assertEqual(len({c.overlap_class for c in overlapped}), 1)
        self.assertTrue(all(evidence in c.evidence for c in overlapped))
        expected = ingress.seconds + max(arithmetic.seconds, local.seconds)
        expected += phase(cost, "exp").seconds + phase(cost, "reciprocal").seconds
        self.assertAlmostEqual(cost.known_service_s, expected)
        self.assertEqual(len(fold(cost.ledger)), len(cost.phases))
        self.assertEqual(len([b for b in cost.buffers if "alternate" in b.id]), 2)

    def test_same_physical_engine_and_local_port_cannot_overlap(self):
        params = ModelParams(overlap=True, overlap_evidence="double-buffer scenario")
        config = default_topology(G0, GEOMETRY, params).instances["vpu0"]
        config = replace(config, resources={**config.resources, "port.local": config.resolve("engine")})
        cost = estimate(task("residual", mac=8, read_bytes=64), config=config)
        self.assertFalse(cost.metrics["overlap"])
        self.assertEqual(phase(cost, "local_read_write").dependencies, (phase(cost, "arithmetic").id,))

    def test_parallel_arithmetic_and_io_join_before_buffer_release_without_special_functions(self):
        params = ModelParams(overlap=True, overlap_evidence="double-buffer scenario")
        for kind in ("residual", "rope", "k_accum", "attention_vpu"):
            with self.subTest(kind=kind):
                dtype = "FP16" if kind == "attention_vpu" else "FP32"
                work = task(kind, elements=1000000, mac=1000000, read_bytes=100,
                            write_bytes=65, dram_read_bytes=33, input_dtype=dtype, output_dtype=dtype)
                cost = estimate(work, params=params)
                arithmetic, local = phase(cost, "arithmetic"), phase(cost, "local_read_write")
                join = phase(cost, "compute_complete")
                self.assertEqual(join.dependencies, (arithmetic.id, local.id))
                self.assertEqual(join.seconds, 0)
                self.assertGreater(arithmetic.seconds, local.seconds)
                self.assertTrue(all(b.release_phase == join.id for b in cost.buffers))
                self.assertEqual(cost.known_service_s,
                                 phase(cost, "activation_stage_read").seconds + arithmetic.seconds)
                self.assertTrue(cost.complete)

    def test_norm_rsqrt_stays_serial_under_overlap(self):
        params = ModelParams(overlap=True, overlap_evidence="double-buffer scenario", rsqrt_cycles_per_vector=2)
        cost = estimate(task("norm", elements=32, vectors=2, length=16, read_bytes=128, write_bytes=128),
                        params=params)
        self.assertEqual(stage(cost, "rsqrt").overlap_class, "serial")
        self.assertEqual(phase(cost, "rsqrt").dependencies,
                         (phase(cost, "arithmetic").id, phase(cost, "local_read_write").id))


class TestCpuSampling(unittest.TestCase):
    def test_missing_cpu_budget_remains_unknown_but_charges_local_io(self):
        work = task("sampling", vectors=1, length=151936, read_bytes=151936 * 4, write_bytes=4)
        cost = estimate(work)
        compute = phase(cost, "comparisons")
        self.assertIsNone(compute.seconds)
        self.assertEqual(stage(cost, "comparisons").source, "unknown")
        self.assertEqual(set(cost.metrics["missing_inputs"]), {"cpu_cycles_per_comparison", "cpu_freq_hz"})
        self.assertEqual(stage(cost, "local_read_write").bytes, work.spec.read_bytes + work.spec.write_bytes)
        self.assertEqual(compute.dependencies, (phase(cost, "local_read_write").id,))
        self.assertGreater(cost.known_service_s, 0)
        self.assertFalse(cost.complete)
        self.assertTrue(all(c.category == "cpu" for c in cost.ledger.charges))

    def test_scenario_is_deterministic_and_sorts_each_vector_separately(self):
        work = task("sampling", length=10, vectors=3, read_bytes=120)
        params = ModelParams(cpu_cycles_per_comparison=2.5, cpu_freq_hz=1_000_000_000)
        cost = estimate(work, params=params)
        self.assertEqual(cost, estimate(work, params=params))
        charge = stage(cost, "comparisons")
        self.assertEqual(charge.ops, 3 * math.ceil(10 * math.log2(10)))
        self.assertEqual(charge.ops, 102)
        self.assertNotEqual(charge.ops, math.ceil(30 * math.log2(30)))
        self.assertEqual(charge.cycles, 255)
        self.assertEqual(charge.seconds, 255 / params.cpu_freq_hz)
        self.assertNotEqual(charge.seconds, 255 / G0.freq_hz)
        self.assertEqual(charge.source, "scenario")
        self.assertIn("full_sort_comparison_budget", charge.evidence)
        self.assertTrue(cost.complete)

    def test_either_missing_cpu_input_keeps_explicit_unknown_computation(self):
        work = task("sampling", length=50, vectors=2)
        cases = ((ModelParams(cpu_cycles_per_comparison=2), "cpu_freq_hz"),
                 (ModelParams(cpu_freq_hz=1e9), "cpu_cycles_per_comparison"))
        for params, missing in cases:
            with self.subTest(missing=missing):
                cost = estimate(work, params=params)
                self.assertIsNone(phase(cost, "comparisons").seconds)
                self.assertFalse(cost.complete)
                self.assertEqual(cost.metrics["missing_inputs"], (missing,))
                self.assertTrue(any(missing in item and "unknown service" in item for item in cost.excluded_costs))
                self.assertEqual(stage(cost, "comparisons").ops, 2 * math.ceil(50 * math.log2(50)))

    def test_cpu_budget_is_independent_of_vpu_clock_lanes_and_special_functions(self):
        work = task("sampling", vectors=2, length=8)
        params = ModelParams(cpu_cycles_per_comparison=2, cpu_freq_hz=1e9)
        modified = replace(params, vpu_fp32_lanes=1, vpu_fp16_lanes=2,
                           exp_cycles_per_element=999, reciprocal_cycles_per_vector=888)
        base = estimate(work, params=params)
        other = estimate(work, MODEL_ANALYSIS_310, modified)
        self.assertEqual(stage(base, "comparisons"), stage(other, "comparisons"))
        self.assertTrue(other.complete)
        self.assertEqual(other.known_service_s, 96 / 1e9)

    def test_known_cpu_clock_on_310_does_not_price_unknown_local_service(self):
        params = ModelParams(cpu_cycles_per_comparison=2, cpu_freq_hz=1e9)
        cost = estimate(task("sampling", vectors=2, length=8, read_bytes=64), MODEL_ANALYSIS_310, params)
        self.assertEqual(stage(cost, "comparisons").cycles, 96)
        self.assertEqual(phase(cost, "comparisons").seconds, 96 / 1e9)
        self.assertIsNone(phase(cost, "local_read_write").seconds)
        self.assertFalse(cost.complete)
        self.assertEqual(cost.known_service_s, 96 / 1e9)

    def test_cpu_sampling_stays_serial_even_with_vpu_overlap(self):
        params = ModelParams(cpu_cycles_per_comparison=2, cpu_freq_hz=1e9,
                             overlap=True, overlap_evidence="VPU double-buffer scenario")
        cost = estimate(task("sampling", vectors=1, length=8, read_bytes=32,
                             dram_read_bytes=17, write_bytes=4, dram_write_bytes=4), params=params)
        self.assertTrue(all(c.overlap_class == "serial" for c in cost.ledger.charges))
        phases = cost.phases
        self.assertEqual([p.id.rsplit(":", 1)[-1] for p in phases],
                         ["activation_stage_read", "local_read_write", "comparisons", "activation_stage_write"])
        self.assertTrue(all(p.dependencies == (previous.id,) for previous, p in zip(phases, phases[1:])))
        self.assertEqual(cost.known_service_s, sum(p.seconds for p in phases))

    def test_comparison_and_io_scratch_have_explicit_ranges_and_allocation_only_padding(self):
        work = task("sampling", vectors=1, length=8, read_bytes=100, write_bytes=65,
                    dram_read_bytes=33, dram_write_bytes=17)
        params = ModelParams(cpu_cycles_per_comparison=2, cpu_freq_hz=1e9)
        base, aligned = estimate(work, params=params), estimate(work, params=params, alignment=128)
        self.assertEqual(base.traffic, aligned.traffic)
        self.assertEqual([b.size_bytes for b in base.buffers], [b.size_bytes for b in aligned.buffers])
        self.assertTrue(all(b.allocated_bytes == 128 for b in aligned.buffers))
        scratch = next(b for b in aligned.buffers if b.id.endswith(":comparison_scratch"))
        self.assertEqual(scratch.acquire_phase, phase(aligned, "comparisons").id)
        self.assertEqual(scratch.release_phase, scratch.acquire_phase)
        read = next(b for b in aligned.buffers if b.id.endswith(":read_chunk"))
        self.assertEqual(read.acquire_phase, phase(aligned, "activation_stage_read").id)
        self.assertEqual(read.release_phase, phase(aligned, "comparisons").id)

    def test_zero_comparison_cost_is_explicit_and_zero_vector_shapes_are_invalid(self):
        cost = estimate(task("sampling", vectors=1, length=8),
                        params=ModelParams(cpu_cycles_per_comparison=0, cpu_freq_hz=1e9))
        self.assertTrue(cost.complete)
        self.assertEqual(phase(cost, "comparisons").seconds, 0)
        self.assertEqual(stage(cost, "comparisons").source, "scenario")
        for shape in ({"vectors": 0}, {"length": 0}):
            with self.subTest(shape=shape), self.assertRaises(ValueError):
                CpuSpec(**shape)


if __name__ == "__main__":
    unittest.main()
