import contextlib
import io
import json
import tempfile
import unittest
from dataclasses import replace
from pathlib import Path

from hive.model_eval.common.opspec import GemmGroup, OpSpec
from hive.model_eval.common.workload import decode, frame, prefill
from hive.model_eval.frontend.qwen3_06b import build
from hive.model_eval.frontend.spec import FORMATS, INT8, MXFP8
from hive.model_eval.hw.geometry import load_array_geometry
from hive.model_eval.hw.profiles import G0, G0_PRO, MODEL_ANALYSIS_310
from hive.model_eval.l0.accounts import work_account
from hive.model_eval.l1.kernels import _scale_requests, gemm_cost
from hive.model_eval.l1.ledger import Charge, Ledger, Origin, fold
from hive.model_eval.l1.mapping import map_model
from hive.model_eval.l1.params import ModelParams
from hive.model_eval.l1.residency import coupling, place
from hive.model_eval.l1.run import main
from hive.model_eval.l1.timeline import compose, evaluate


class TestMapping(unittest.TestCase):
    def setUp(self):
        self.model = build()

    def test_decode_work_matches_l0_exactly(self):
        for seq in (0, 1, 4096, 131072):
            for batch in (1, 3):
                point = decode(seq, batch)
                operations = map_model(self.model, MXFP8, point)
                self.assertEqual(sum(op.mac for op in operations), work_account(self.model, point).total)

    def test_prefill_adds_explicit_causal_softmax_not_silent_l0_change(self):
        point = prefill(49, 2)
        operations = map_model(self.model, MXFP8, point)
        softmax = sum(op.mac for op in operations if op.kind == "softmax")
        causal_diagonal = 28 * 16 * 128 * 49 * 2
        self.assertEqual(sum(op.mac for op in operations) - softmax,
                         work_account(self.model, point).total + causal_diagonal)
        self.assertEqual(softmax, 28 * 16 * 49 * 50 * 2 // 2)

    def test_norm_and_softmax_call_anchors(self):
        operations = map_model(self.model, MXFP8, decode(4096))
        self.assertEqual(sum(op.vectors for op in operations if op.kind == "norm"), 729)
        self.assertEqual(sum(op.vectors for op in operations if op.kind == "softmax"), 448)

    def test_seven_weight_gemms_per_layer_and_one_head(self):
        operations = map_model(self.model, MXFP8, prefill(128))
        gemms = [op for op in operations if op.kind == "weight_gemm"]
        self.assertEqual(len(gemms), 28 * 7 + 1)
        self.assertEqual(gemms[-1].gemms, (GemmGroup(1, 151936, 1024),))
        self.assertEqual(gemms[0].gemms[0].m, 128)

    def test_dependencies_are_unique_topological_chain(self):
        operations = map_model(self.model, MXFP8, decode(1))
        self.assertEqual(len({op.id for op in operations}), len(operations))
        for i, op in enumerate(operations):
            self.assertEqual(op.dependencies, () if i == 0 else (operations[i - 1].id,))

    def test_layer_count_comes_from_model(self):
        model = build()
        model = replace(model, layer_types=(replace(model.layer_types[0], count=3),))
        operations = map_model(model, MXFP8, decode(1))
        self.assertEqual(len({op.layer for op in operations}), 6)

    def test_quantized_attention_is_explicit_not_fp16_input_substitution(self):
        ops = map_model(self.model, MXFP8, prefill(128), "quantized_gemm")
        op = next(op for op in ops if op.kind == "attention_gemm")
        group = op.gemms[0]
        self.assertEqual(group.m * group.n * group.k * group.repeats,
                         16 * 128 * 128 * 128)
        self.assertEqual(op.mac, 16 * 128 * 128 * 129 // 2)
        self.assertIn("dense masked", op.evidence)

    def test_prefill_one_contains_diagonal_in_both_variants(self):
        for variant in ("vpu", "quantized_gemm"):
            operations = map_model(self.model, MXFP8, prefill(1), variant)
            qk = next(op for op in operations if op.name == "qk")
            self.assertEqual(qk.mac, 16 * 128)

    def test_operator_boundary_staging_is_explicit(self):
        operations = map_model(self.model, MXFP8, decode(1))
        norm = next(op for op in operations if op.name == "qk_norm")
        self.assertEqual(norm.dram_read_bytes, 4 * (16 + 8) * 128)
        self.assertEqual(norm.dram_write_bytes, norm.write_bytes)
        sampling = operations[-1]
        self.assertEqual(sampling.dram_read_bytes, 4 * self.model.vocab_size)

    def test_no_fake_frame_or_unknown_variant_coverage(self):
        with self.assertRaises(ValueError):
            map_model(self.model, MXFP8, frame())
        with self.assertRaises(ValueError):
            map_model(self.model, MXFP8, decode(1), "bf16_gemm")


class TestLedger(unittest.TestCase):
    def charges(self):
        origin = Origin("weight", "dma_b")
        return [Charge("dram", "core0", origin, "dram", 2),
                Charge("port", "core0", origin, "gemm", 2)]

    def test_same_source_is_max_not_sum(self):
        charges = self.charges()
        ledger = Ledger(charges, coupling(charges))
        total, shares, resources = compose(ledger)
        self.assertEqual(total, 2)
        self.assertNotEqual(total, 4)
        self.assertEqual(sum(shares.values()), total)

    def test_independent_origins_sum_by_default(self):
        charges = self.charges()
        charges[1] = replace(charges[1], origin=Origin("local_reload", "sram"))
        total, _, _ = compose(Ledger(charges, coupling(charges)))
        self.assertEqual(total, 4)

    def test_same_source_without_coupling_is_rejected(self):
        with self.assertRaises(ValueError):
            fold(Ledger(self.charges()))

    def test_same_resource_duplicate_is_not_discarded_by_max(self):
        charges = self.charges()
        charges[1] = replace(charges[1], resource="dram")
        with self.assertRaises(ValueError):
            fold(Ledger(charges))

    def test_explicit_overlap_uses_max_but_not_below_longest_interval(self):
        charges = self.charges()
        charges[1] = replace(charges[1], origin=Origin("other", "sram"))
        charges = [replace(c, overlap_class="buffered", evidence="test explicit two buffers") for c in charges]
        total, shares, _ = compose(Ledger(charges, coupling(charges)))
        self.assertEqual(total, 2)
        self.assertEqual(sum(shares.values()), total)

    def test_shared_physical_port_cannot_overlap_independent_demand(self):
        charges = self.charges()
        charges = [replace(c, resource="port", overlap_class="buffered", evidence="test") for c in charges]
        charges[1] = replace(charges[1], origin=Origin("other", "sram"))
        with self.assertRaises(ValueError):
            compose(Ledger(charges))

    def test_unknown_charge_not_claimed_as_zero(self):
        charges = [replace(c, seconds=None) for c in self.charges()]
        self.assertEqual(fold(Ledger(charges, coupling(charges))), [])
        self.assertIsNone(charges[0].seconds)

    def test_invalid_resource_quantities_and_overlap_are_rejected(self):
        for seconds in (float("nan"), -1, float("inf")):
            with self.assertRaises(ValueError):
                replace(self.charges()[0], seconds=seconds)
        with self.assertRaises(ValueError):
            replace(self.charges()[0], overlap_class="unproved")


class TestPlacementAndKernels(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.geometry = load_array_geometry()

    def op(self, m=1, n=16, k=32):
        return OpSpec("test/gemm", "test", "gemm", "GEMM", "weight_gemm",
                      mac=m * n * k, gemms=(GemmGroup(m, n, k),))

    def test_310_cannot_infer_residency_from_bandwidth(self):
        placement = place(MODEL_ANALYSIS_310, ModelParams(weight_policy="resident"),
                          weight_bytes=1024, activation_bytes=32, window_bytes=512, output_tile_bytes=64)
        self.assertFalse(placement.weight_resident)
        self.assertFalse(placement.activation_resident)
        self.assertFalse(placement.double_buffer_fits)

    def test_residency_capacity_and_generated_coupling(self):
        op = self.op(m=49)
        stream, sm = gemm_cost(op, G0, "MXFP8", self.geometry, ModelParams())
        resident, rm = gemm_cost(op, G0, "MXFP8", self.geometry, ModelParams(weight_policy="resident"))
        self.assertEqual(sm["weight_dram_bytes"], 2 * rm["weight_dram_bytes"])
        self.assertEqual(stream.coupling[("0:dram.b", "0:port.dma_b")], "same_source")
        self.assertEqual(resident.coupling[("0:dram.b", "0:port.dma_b")], "independent")

    def test_large_weight_remains_streamed_even_when_residency_requested(self):
        _, metrics = gemm_cost(self.op(n=2048, k=1024), G0, "MXFP8", self.geometry,
                               ModelParams(weight_policy="resident"))
        self.assertFalse(metrics["placements"][0]["weight_resident"])

    def test_scale_logical_bytes_are_not_four_byte_port_width(self):
        _, metrics = gemm_cost(self.op(k=128), G0, "MXFP8", self.geometry, ModelParams())
        self.assertEqual(metrics["scale_b_logical_bytes"], 64)
        self.assertEqual(metrics["scale_b_physical_bytes"], 4 * (16 * 7))
        self.assertEqual(metrics["scale_a_logical_bytes"], 4)
        self.assertEqual(metrics["scale_a_physical_bytes"], 28)

    def test_scale_gather_arithmetic_matches_address_enumeration(self):
        for rows in range(1, 12):
            for passes in range(1, 12):
                for stride in (1, 2, 3, 4, 9):
                    for step in (1, 2, 4):
                        exact = sum(1 + int((r * stride + k * step) % 4 != 0)
                                    for r in range(rows) for k in range(passes))
                        self.assertEqual(_scale_requests(rows, passes, stride, step), exact)

    def test_prefill_weight_reloads_and_c_rows(self):
        _, metrics = gemm_cost(self.op(m=49), G0, "INT8", self.geometry, ModelParams())
        self.assertEqual(metrics["commands"], 2)
        self.assertEqual(metrics["c_rows"], 49)
        self.assertEqual(metrics["weight_reload_bytes"], 512)

    def test_independent_k_chunks_charge_fp32_accumulation(self):
        _, metrics = gemm_cost(self.op(k=65536), G0, "INT8", self.geometry, ModelParams())
        self.assertEqual(metrics["commands"], 2)
        self.assertEqual(metrics["c_rows"], 2)
        self.assertEqual(metrics["k_split_accum_bytes"], 12 * 16)

    def test_loadw_remains_serial_when_consume_write_overlap_enabled(self):
        ledger, _ = gemm_cost(self.op(m=8), G0, "INT8", self.geometry,
                              ModelParams(overlap=True, overlap_evidence="test verified buffer schedule"))
        for charge in ledger.charges:
            if charge.resource in ("0:array.loadW", "0:port.dma_b", "0:dram.b"):
                self.assertEqual(charge.overlap_class, "serial")
        total, _, _ = compose(ledger)
        load = next(c.seconds for c in ledger.charges if c.resource == "0:array.loadW")
        consume = next(c.seconds for c in ledger.charges if c.resource == "0:array.consume")
        self.assertGreaterEqual(total, load + consume)

    def test_overlap_rejects_unknown_or_insufficient_capacity(self):
        params = ModelParams(overlap=True, overlap_evidence="test")
        for profile in (MODEL_ANALYSIS_310, replace(G0, sram_bytes_per_core=1)):
            with self.assertRaises(ValueError):
                gemm_cost(self.op(), profile, "INT8", self.geometry, params)


class TestTimeline(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.model = build()
        cls.geometry = load_array_geometry()

    def test_weight_only_anchors_survive_integrated_accounting(self):
        result = evaluate(self.model, G0, MXFP8, decode(0), geometry=self.geometry)
        self.assertEqual(result.metrics["array_cycles"], 19788544)
        self.assertEqual(result.metrics["b_logical_bytes"], 595984384)
        self.assertEqual(result.metrics["c_rows"], 31000)
        self.assertEqual(result.metrics["norm_calls"], 729)
        self.assertEqual(result.metrics["array_utilization"], 1 / 17)

    def test_known_profiles_never_beat_same_input_l0_bound(self):
        for profile in (G0, G0_PRO):
            for fmt in FORMATS.values():
                for point in (decode(0), decode(4096, 2), prefill(49)):
                    for attention in ("vpu", "quantized_gemm"):
                        result = evaluate(self.model, profile, fmt, point, attention=attention,
                                          geometry=self.geometry)
                        self.assertGreaterEqual(result.t_s, result.l0_t_s)
                        self.assertTrue(result.cross_level_comparable)

    def test_five_categories_sum_to_timeline_and_no_overlap_sums_ops(self):
        result = evaluate(self.model, G0, INT8, decode(16), geometry=self.geometry)
        self.assertAlmostEqual(sum(result.attribution_s.values()), result.t_s)
        self.assertAlmostEqual(sum(op.t_s for op in result.operations), result.t_s)
        self.assertAlmostEqual(result.operations[-1].end_s, result.t_s)

    def test_handoff_scenario_counts_real_engine_transitions(self):
        base = evaluate(self.model, G0, INT8, decode(1), geometry=self.geometry)
        scenario = evaluate(self.model, G0, INT8, decode(1), geometry=self.geometry,
                            params=ModelParams(sync_us=0.5))
        self.assertAlmostEqual(scenario.t_s - base.t_s, base.handoffs * 0.5e-6)
        self.assertAlmostEqual(scenario.attribution_s["handoff"], base.handoffs * 0.5e-6)
        self.assertGreater(base.handoffs, 0)

    def test_debug_output_does_not_change_any_totals(self):
        result = evaluate(self.model, G0, MXFP8, decode(16), geometry=self.geometry)
        brief, debug = result.to_dict(), result.to_dict(True)
        self.assertEqual(brief["metrics"], debug["metrics"])
        self.assertEqual(brief["t_modeled_s"], debug["t_modeled_s"])
        self.assertNotIn("ledger", brief["operators"][0])
        self.assertIn("ledger", debug["operators"][0])

    def test_310_unknown_rates_are_not_guessed_or_clamped_to_l0(self):
        result = evaluate(self.model, MODEL_ANALYSIS_310, MXFP8, prefill(16), geometry=self.geometry)
        self.assertFalse(result.cross_level_comparable)
        self.assertIsNone(result.modeled_throughput)
        self.assertTrue(any(c.seconds is None for op in result.operations for c in op.ledger.charges))

    def test_validity_and_numeric_assumptions_remain_explicit(self):
        result = evaluate(self.model, G0, MXFP8, decode(1), geometry=self.geometry)
        self.assertFalse(result.performance_valid)
        self.assertFalse(result.data_valid)
        self.assertFalse(result.to_dict()["coverage_complete"])
        self.assertTrue(any("rsqrt" in item for item in result.excluded_costs))
        self.assertTrue(any("sampling" in item for item in result.excluded_costs))
        self.assertEqual(ModelParams().named_inputs()["service_assumptions"]["vpu_non_fma"]["source"], "fallback")

    def test_vpu_boundary_staging_and_spills_are_charged(self):
        result = evaluate(self.model, G0, MXFP8, decode(1), geometry=self.geometry)
        norm = next(op for op in result.operations if op.op.name == "qk_norm")
        staged = [charge for charge in norm.ledger.charges if charge.resource.startswith("dram_activation")]
        self.assertEqual(sum(charge.bytes for charge in staged), 2 * 4 * (16 + 8) * 128)
        self.assertEqual(norm.metrics["dram_bytes"], sum(c.bytes for c in norm.ledger.charges if c.category == "dram"))

    def test_dense_masked_attention_extra_work_is_reported(self):
        result = evaluate(self.model, G0, MXFP8, prefill(17), attention="quantized_gemm",
                          geometry=self.geometry)
        self.assertGreater(result.metrics["masked_macs"], 0)
        self.assertGreater(result.metrics["padded_macs"], 0)
        self.assertTrue(any("quantized attention" in item for item in result.excluded_costs))

    def test_same_input_reproducible(self):
        first = evaluate(self.model, G0, INT8, decode(16), geometry=self.geometry)
        second = evaluate(self.model, G0, INT8, decode(16), geometry=self.geometry)
        self.assertEqual(first.to_dict(True), second.to_dict(True))


class TestCli(unittest.TestCase):
    def invoke(self, *args):
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            code = main(list(args))
        self.assertEqual(code, 0)
        return output.getvalue()

    def test_json_contains_expanded_layers_operators_and_manifest(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "l1.json"
            self.invoke("--seq", "0,16", "--json", str(path), "--debug-ledger", "--layer-detail")
            payload = json.loads(path.read_text())
        self.assertEqual(len(payload["results"]), 4)
        self.assertEqual(len(payload["results"][0]["layers"]), 31)
        self.assertEqual(payload["manifest"]["layer"], "L1")
        self.assertFalse(payload["manifest"]["performance_valid"])
        self.assertIn("ledger", payload["results"][0]["operators"][0])
        self.assertTrue(any(source["path"].endswith("l1/kernels.py") for source in payload["manifest"]["sources"]))

    def test_scan_format_profile_phase_and_batch(self):
        for profile in ("G0", "G0_PRO", "310"):
            for fmt in FORMATS:
                for phase in ("decode", "prefill"):
                    output = self.invoke("--seq", "16", "--profile", profile,
                                         "--fmt", fmt, "--workload", phase, "--batch", "2")
                    self.assertIn("未经 RTL 校准", output)
                    self.assertIn("五类归因", output)

    def test_invalid_inputs_use_argparse_errors(self):
        for args in (("--seq", ""), ("--seq", "-1"), ("--batch", "0"),
                     ("--overlap",), ("--sync-us", "nan"), ("--workload", "prefill", "--seq", "0")):
            with contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit) as caught:
                main(list(args))
            self.assertEqual(caught.exception.code, 2)

    def test_json_digest_and_output_are_deterministic(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "l1.json"
            args = ("--seq", "1", "--attention", "vpu", "--json", str(path))
            first = self.invoke(*args)
            payload = path.read_text()
            self.assertEqual(self.invoke(*args), first)
            self.assertEqual(path.read_text(), payload)


if __name__ == "__main__":
    unittest.main()
