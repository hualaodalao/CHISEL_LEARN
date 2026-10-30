import importlib.util
from pathlib import Path
import unittest

MODULE = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location(
    "workerbee_command_trace", MODULE / "tools/command_trace.py")
trace = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(trace)


class WordTests(unittest.TestCase):
    def test_control_word_field_placement(self):
        job = trace.Job(a_fmt="INT8", b_fmt="INT8", c_fmt="INT4", load_w=0,
                        requant=True, bias=True, relu=True, rnd=3,
                        blk_a=32, blk_b=16, blk_c=32)
        word = trace.control_word(job)
        self.assertEqual(word & 0xF, trace.FORMATS["INT8"])
        self.assertEqual((word >> 4) & 0xF, trace.FORMATS["INT8"])
        self.assertEqual((word >> 8) & 0xF, trace.FORMATS["INT4"])
        self.assertEqual((word >> 15) & 1, 1)
        self.assertEqual((word >> 16) & 0x7, 3)
        self.assertEqual((word >> 22) & 1, 1)
        self.assertEqual((word >> 24) & 1, 0)
        self.assertEqual((word >> 26) & 1, 1)
        self.assertEqual((word >> 28) & 1, 0)
        self.assertEqual((word >> 29) & 1, 1)
        self.assertEqual((word >> 30) & 1, 1)

    def test_nk_word_splits_n_low_k_high(self):
        self.assertEqual(trace.nk_word(trace.Job(n=32, k=96)), 32 | 96 << 16)

    def test_rq_param_packs_zero_point_high(self):
        job = trace.Job(rq_shift=7, zero_point=-3)
        word = trace.rq_param_word(job)
        self.assertEqual(word & 0x3F, 7)
        self.assertEqual((word >> 16) & 0xFFFF, 0xFFFD)


class ValidateTests(unittest.TestCase):
    def test_accepts_documented_pairs(self):
        for a, b, load_w in (("INT8", "INT8", 0), ("INT8", "INT4", 1),
                             ("BF16", "BF16", 0), ("FP8E4M3", "FP8E4M3", 1),
                             ("MXE4M3", "MXE2M1", 1), ("MXE5M2", "MXE5M2", 1)):
            with self.subTest(a=a, b=b):
                trace.validate(trace.Job(a_fmt=a, b_fmt=b, load_w=load_w))

    def test_rejects_shape_outside_hardware_domain(self):
        for job in (trace.Job(m=0), trace.Job(m=49), trace.Job(n=24),
                    trace.Job(k=0)):
            with self.subTest(m=job.m, n=job.n, k=job.k):
                with self.assertRaises(trace.TraceError):
                    trace.validate(job)

    def test_rejects_mixed_families(self):
        for a, b, load_w in (("INT8", "BF16", 0), ("MXE4M3", "FP8E4M3", 1),
                             ("INT4", "INT8", 1), ("MXE2M1", "MXE4M3", 1)):
            with self.subTest(a=a, b=b):
                with self.assertRaises(trace.TraceError):
                    trace.validate(trace.Job(a_fmt=a, b_fmt=b, load_w=load_w))

    def test_requires_load_w_for_mx_and_fp8(self):
        for fmt in ("MXE4M3", "MXE2M1", "FP8E5M2"):
            with self.subTest(fmt=fmt), self.assertRaises(trace.TraceError):
                trace.validate(trace.Job(a_fmt=fmt, b_fmt=fmt, load_w=0))


class GeometryTests(unittest.TestCase):
    def test_int8_counts_packed_beats(self):
        geo = trace.geometry(trace.Job(m=16, n=16, k=64))
        self.assertEqual(geo["k_per_beat_a"], 32)
        self.assertEqual(geo["k_tiles"], 2)
        self.assertEqual(geo["a_beats"], 32)
        self.assertEqual(geo["b_beats"], 32)
        self.assertEqual(geo["c_beats"], 32)
        self.assertEqual(geo["scheduled_ops"], 2 * 16 * 16 * 64)

    def test_int4_counts_quarter_beats(self):
        job = trace.Job(m=8, n=32, k=128, a_fmt="INT4", b_fmt="INT4")
        geo = trace.geometry(job)
        self.assertEqual(geo["k_per_beat_a"], 64)
        self.assertEqual(geo["n_tiles"], 2)
        self.assertEqual(geo["k_tiles"], 2)
        self.assertEqual(geo["a_beats"], 2 * 2 * 8)
        self.assertEqual(geo["b_beats"], 2 * 2 * 16)

    def test_bf16_rounds_k_up_to_total_n(self):
        geo = trace.geometry(trace.Job(m=16, n=16, k=48, a_fmt="BF16",
                                       b_fmt="BF16", c_fmt="BF16"))
        self.assertEqual(geo["k_per_beat_a"], 16)
        self.assertEqual(geo["k_tiles"], 3)
        self.assertEqual(geo["c_bytes_per_row"], 32)
        self.assertEqual(geo["c_beats"], 16)

    def test_int32_output_rows_need_two_beats(self):
        geo = trace.geometry(trace.Job(m=16, n=32, k=64))
        self.assertEqual(geo["c_bytes_per_row"], 64)
        self.assertEqual(geo["c_beats"], 2 * 16 * 2)


class LayoutTests(unittest.TestCase):
    def test_banked_regions_are_disjoint_and_aligned(self):
        job = trace.Job(m=16, n=16, k=64, bias=True)
        addresses, span, bank = trace.banked_layout(job)
        self.assertEqual(span, bank * 6)
        self.assertEqual(bank & (bank - 1), 0)
        sizes = dict(trace.region_sizes(job))
        for name, address in addresses.items():
            with self.subTest(region=name):
                self.assertEqual(address % 4096, 0)
                self.assertLess(address, span)
        for left, left_addr in addresses.items():
            for right, right_addr in addresses.items():
                if left != right:
                    with self.subTest(left=left, right=right):
                        self.assertNotEqual(left_addr, right_addr)

    def test_regions_stay_inside_their_bank(self):
        job = trace.Job(m=48, n=32, k=256, bias=True, requant=False)
        addresses, span, bank = trace.banked_layout(job)
        for name, size in trace.region_sizes(job):
            with self.subTest(region=name):
                bank_index = trace.REGION_BANK[name]
                self.assertEqual(addresses[name] // bank, bank_index)
                self.assertLessEqual(addresses[name] % bank + size, bank)

    def test_every_dma_port_gets_its_own_bank(self):
        job = trace.Job()
        addresses, span, bank = trace.banked_layout(job)
        used = {trace.REGION_BANK[name] for name in addresses}
        self.assertEqual(used, {0, 1, 2, 3, 4, 5})
        self.assertEqual(span, bank * 6)
        flat, flat_span = trace.layout(job)
        self.assertGreaterEqual(span, flat_span)


class ProgramTests(unittest.TestCase):
    def test_start_lands_in_the_last_batch_with_full_configuration(self):
        job = trace.Job(m=16, n=16, k=64, a_addr=0x10000, b_addr=0x20000,
                        c_addr=0x30000, tag=5)
        batches = trace.program(job)
        self.assertEqual(batches[0].cycle, 0)
        writes = {}
        for batch in batches:
            for lane, cmd in batch.lanes.items():
                writes[cmd.reg0] = cmd.data0
                if cmd.op == trace.OP_WRITE2:
                    writes[cmd.reg1] = cmd.data1
                if cmd.reg0 == trace.REG["START"]:
                    start_batch = batches.index(batch)
                    start_lane = lane
        self.assertEqual(start_batch, len(batches) - 1)
        self.assertEqual(start_lane, 3)
        self.assertEqual(writes[trace.REG["START"]], 1)
        self.assertEqual(writes[trace.REG["A_ADDR"]], 0x10000)
        self.assertEqual(writes[trace.REG["C_ADDR"]], 0x30000)
        self.assertEqual(writes[trace.REG["NK"]], 16 | 64 << 16)

    def test_pack_round_trips_through_the_model_layout(self):
        job = trace.Job(m=16, n=16, k=64, a_addr=0x1000, b_addr=0x2000,
                        c_addr=0x3000, tag=9)
        batches = trace.program(job)
        words = trace.pack(batches)
        per_batch = 2 + 5 * 4
        self.assertEqual(len(words), len(batches) * per_batch)
        for index, batch in enumerate(batches):
            base = index * per_batch
            self.assertEqual(words[base], batch.cycle)
            self.assertEqual(words[base + 1], sum(1 << lane for lane in batch.lanes))
            for lane in range(4):
                at = base + 2 + lane * 5
                cmd = batch.lanes[lane]
                self.assertEqual(words[at], cmd.op | cmd.reg0 << 8 | cmd.reg1 << 16)
                self.assertEqual(words[at + 1], cmd.data0)
                self.assertEqual(words[at + 2], cmd.data1)
                self.assertEqual(words[at + 4], cmd.tag)

    def test_pack_rejects_empty_and_out_of_range_lanes(self):
        with self.assertRaises(trace.TraceError):
            trace.pack([trace.Batch(0, {})])
        with self.assertRaises(trace.TraceError):
            trace.pack([trace.Batch(0, {4: trace.read("STATUS")})])

    def test_jsonl_records_bracket_batches_with_task_markers(self):
        job = trace.Job()
        records = trace.jsonl_records(job, trace.program(job), task_id="t7")
        self.assertEqual(records[0]["type"], "header")
        self.assertEqual(records[1]["type"], "task_begin")
        self.assertEqual(records[-1]["type"], "wait_completion")
        self.assertTrue(all(r["task_id"] == "t7" for r in records[1:]))
        self.assertEqual(records[2]["commands"][0]["reg0Addr"], "0x00")


if __name__ == "__main__":
    unittest.main()
