"""L0 回归锚点：附录 B 的参数量/MAC 与 §9.2 prefill 表逐位复现。

本机未安装 pytest，且 src/workerbee/gem5/tests 已经是标准库 unittest + 扁平
test_*.py，这里沿用同一形态：`PYTHONPATH=src python3 -m unittest discover
-s src/hive/model_eval/tests`。
"""

import io
import json
import tempfile
import unittest
from contextlib import redirect_stderr, redirect_stdout
from fractions import Fraction
from pathlib import Path

from hive.model_eval.common import manifest, workload
from hive.model_eval.frontend import qwen3_06b, spec
from hive.model_eval.hw import geometry, profiles
from hive.model_eval.l0 import accounts, bottleneck, excluded, roofline, run, torch_check

# src/hive/doc/model_analysis/Qwen3-0.6B-逐层计算拆解.md 附录 B
APPENDIX_B = {
    "layer": 15_730_944,
    "body": 440_466_432,
    "emb": 155_582_464,
    "uniq": 596_049_920,
    "ckpt": 751_632_384,
    "fixed": 15_750_144,
    "s_term": 4_112,
    "kv_tok": 114_688,
    "weight_bytes": 614_527_468,
}

# 附录 B 的 MAC(S) 四档
DECODE_MAC = {
    0: 596_588_544,
    4096: 1_068_185_600,
    32768: 4_369_364_992,
    131072: 15_687_694_336,
}

# 附录 B 注释里的 310 口径吞吐与算术强度
DECODE_310 = {
    0: (504.5, 0.971),
    4096: (285.9, None),
    32768: (70.9, None),
    131072: (19.8, None),
}

# §9.2 prefill 表：线性项 / 二次项 / 合计 GMAC、25 TOPS 下的毫秒数、二次项占比
# 表里不含 lm_head（prefill 只有最后一个 token 需要），所以与 linear+quadratic 对表。
PREFILL_92 = {
    1024: (451.6, 60.1, 511.7, 40.9, 11.7),
    4096: (1806.4, 962.1, 2768.5, 221.5, 34.7),
    32768: (14450.8, 61572.4, 76023.2, 6081.9, 81.0),
}
# 逐行的绝对容差，单位 GMAC。文档按一位小数给数（±0.05），合计列又是分项舍入后
# 相加（再叠一层），所以 0.1 兜住前两行。S=32768 行另有约 0.34 GMAC 的转录漂移：
# 线性项精确值 14,450.9 而表里写 14,450.8，二次项 61,572.7 写成 61,572.4。代码一侧
# 是精确的，按规矩不改文档数字，这里把漂移量显式记下来而不是放宽成相对误差。
PREFILL_TOL_GMAC = {1024: 0.1, 4096: 0.1, 32768: 0.4}


class TestAppendixBParams(unittest.TestCase):
    """参数量：逐位相同，差一位就是前端算错了。"""

    @classmethod
    def setUpClass(cls):
        cls.model = qwen3_06b.build()

    def test_layer_and_body(self):
        layer_types = self.model.layer_types
        self.assertEqual(len(layer_types), 1)
        self.assertEqual(layer_types[0].params_unique, APPENDIX_B["layer"])
        self.assertEqual(layer_types[0].count * layer_types[0].params_unique, APPENDIX_B["body"])

    def test_embedding_and_lm_head(self):
        self.assertEqual(self.model.embedding_params, APPENDIX_B["emb"])
        self.assertEqual(self.model.lm_head_params, APPENDIX_B["emb"])

    def test_unique_and_checkpoint(self):
        self.assertEqual(self.model.params_unique, APPENDIX_B["uniq"])
        self.assertEqual(self.model.params_checkpoint, APPENDIX_B["ckpt"])

    def test_mac_coefficients(self):
        self.assertEqual(self.model.layer_types[0].mac_fixed, APPENDIX_B["fixed"])
        self.assertEqual(self.model.layer_types[0].mac_s_coef, APPENDIX_B["s_term"])
        self.assertEqual(self.model.body_mac_fixed(), 28 * APPENDIX_B["fixed"])
        self.assertEqual(self.model.body_mac_s_coef(), 28 * APPENDIX_B["s_term"])

    def test_kv_bytes_per_token(self):
        self.assertEqual(self.model.kv_bytes_per_token, APPENDIX_B["kv_tok"])

    def test_decode_streams_full_unique_weights(self):
        # tied embedding 不参与 decode 的权重流量，但 lm_head 要读
        self.assertEqual(self.model.params_streamed_per_token, APPENDIX_B["uniq"])

    def test_weight_bytes_mxfp8(self):
        acct = accounts.traffic_account(
            self.model, spec.MXFP8, workload.decode(0), use_doc_bytes=True
        )
        self.assertEqual(round(acct.breakdown["weights"]), APPENDIX_B["weight_bytes"])


class TestDecodeMac(unittest.TestCase):
    """MAC(S) = fixed*L + lm + nrm + s_term*L*S，四档逐位。"""

    @classmethod
    def setUpClass(cls):
        cls.model = qwen3_06b.build()

    def test_four_tiers(self):
        for seq, expected in DECODE_MAC.items():
            with self.subTest(S=seq):
                acct = accounts.work_account(self.model, workload.decode(seq))
                self.assertEqual(int(acct.total), expected)

    def test_lm_head_share_at_s0(self):
        # 文档结论：lm_head 占 decode 权重流量 26.1%
        acct = accounts.work_account(self.model, workload.decode(0))
        self.assertAlmostEqual(acct.share("lm_head"), 0.261, places=3)


class TestAccountInvariants(unittest.TestCase):
    """分项之和必须等于总量，否则归因表会骗人。"""

    @classmethod
    def setUpClass(cls):
        cls.model = qwen3_06b.build()

    def test_breakdown_sums_to_total(self):
        points = [workload.decode(0), workload.decode(4096), workload.prefill(1024)]
        for point in points:
            with self.subTest(point=point.label):
                work = accounts.work_account(self.model, point)
                traffic = accounts.traffic_account(
                    self.model, spec.MXFP8, point, use_doc_bytes=True
                )
                self.assertAlmostEqual(sum(work.breakdown.values()), work.total, places=6)
                self.assertAlmostEqual(
                    sum(traffic.breakdown.values()), traffic.total, places=6
                )

    def test_decode_weight_traffic_is_batch_independent(self):
        one = accounts.traffic_account(
            self.model, spec.MXFP8, workload.decode(4096, batch=1), use_doc_bytes=True
        )
        four = accounts.traffic_account(
            self.model, spec.MXFP8, workload.decode(4096, batch=4), use_doc_bytes=True
        )
        self.assertEqual(one.breakdown["weights"], four.breakdown["weights"])
        self.assertEqual(one.breakdown["kv_read"] * 4, four.breakdown["kv_read"])


class TestRoofline310(unittest.TestCase):
    """310 口径：附录 B 注释里的吞吐与 AI。"""

    @classmethod
    def setUpClass(cls):
        cls.model = qwen3_06b.build()
        cls.profile = profiles.get_profile("310")

    def test_profile_is_format_agnostic(self):
        # 25 TOPS 是文档给的整机口径，不是 mac_per_cycle×f×n_core 推出来的
        self.assertTrue(self.profile.format_agnostic_compute)
        self.assertEqual(self.profile.mac_per_second(spec.MXFP8.mac_family), 12.5e12)

    def test_decode_throughput(self):
        for seq, (tok_s, ai) in DECODE_310.items():
            with self.subTest(S=seq):
                result = roofline.evaluate(
                    self.model, self.profile, spec.MXFP8, workload.decode(seq)
                )
                self.assertAlmostEqual(result.throughput, tok_s, places=1)
                self.assertEqual(result.bottleneck, "dram")
                if ai is not None:
                    self.assertAlmostEqual(result.arithmetic_intensity, ai, places=3)

    def test_prefill_table(self):
        mac_rate = self.profile.mac_per_second(spec.MXFP8.mac_family)
        for seq, (linear, quad, total, ms, quad_pct) in PREFILL_92.items():
            with self.subTest(S=seq):
                work = accounts.work_account(self.model, workload.prefill(seq))
                doc_mac = work.breakdown["linear"] + work.breakdown["quadratic"]
                got = {
                    "linear": (work.breakdown["linear"] / 1e9, linear),
                    "quadratic": (work.breakdown["quadratic"] / 1e9, quad),
                    "total": (doc_mac / 1e9, total),
                }
                for name, (value, expected) in got.items():
                    self.assertAlmostEqual(
                        value,
                        expected,
                        delta=PREFILL_TOL_GMAC[seq],
                        msg=f"{name}: {value} GMAC vs 文档 {expected} GMAC",
                    )
                self.assertAlmostEqual(doc_mac / mac_rate * 1e3, ms, places=1)
                self.assertAlmostEqual(
                    work.breakdown["quadratic"] / doc_mac * 100, quad_pct, delta=0.1
                )

    def test_prefill_is_compute_bound(self):
        result = roofline.evaluate(
            self.model, self.profile, spec.MXFP8, workload.prefill(4096)
        )
        self.assertEqual(result.bottleneck, "compute")

    def test_kv_weight_crossover(self):
        # 文档结论：KV 与权重流量在 S≈5358 追平
        crossover = bottleneck.kv_weight_crossover(self.model, spec.MXFP8.bytes_per_param(
            use_doc_value=True
        ))
        self.assertEqual(round(crossover), 5358)


class TestG0Profile(unittest.TestCase):
    """G0 口径：常数对 arch.md，上界约 28 tok/s。"""

    @classmethod
    def setUpClass(cls):
        cls.model = qwen3_06b.build()

    def test_constants_match_arch(self):
        g0 = profiles.get_profile("G0")
        self.assertAlmostEqual(g0.dram_peak_bytes_per_s, 17.064e9)
        self.assertEqual(g0.n_core, 2)
        self.assertEqual(g0.freq_hz, 500_000_000)
        self.assertEqual(g0.sram_bytes_per_core, 512 * 1024)
        self.assertEqual(g0.local_iface_bytes_per_cycle, 32)
        # arch_roadmap.md 第 74/75 行
        self.assertEqual(g0.mac_per_cycle_per_core[profiles.MAC_FAMILY_INT8], 512)
        self.assertEqual(g0.mac_per_cycle_per_core[profiles.MAC_FAMILY_INT4], 1024)
        self.assertAlmostEqual(g0.mac_per_second(profiles.MAC_FAMILY_INT8), 0.512e12)

        pro = profiles.get_profile("G0_PRO")
        self.assertAlmostEqual(pro.dram_peak_bytes_per_s, 34.128e9)
        self.assertEqual(pro.n_core, 4)
        self.assertEqual(pro.sram_bytes_per_core, 1024 * 1024)
        self.assertAlmostEqual(pro.mac_per_second(profiles.MAC_FAMILY_INT8), 1.024e12)

    def test_decode_upper_bound(self):
        result = roofline.evaluate(
            self.model, profiles.get_profile("G0"), spec.MXFP8, workload.decode(0)
        )
        self.assertEqual(result.bottleneck, "dram")
        self.assertAlmostEqual(result.throughput, 27.8, places=1)

    def test_bandwidth_gap_versus_310(self):
        # 两套口径必须并存且差约 18 倍，不许互相覆盖
        ratio = (
            profiles.get_profile("310").dram_peak_bytes_per_s
            / profiles.get_profile("G0").dram_peak_bytes_per_s
        )
        self.assertAlmostEqual(ratio, 18.2, places=1)

    def test_switching_profile_needs_no_code_change(self):
        # 同一个 evaluate 调用，只换 profile 实例
        for name in ("G0", "G0_PRO", "310"):
            with self.subTest(profile=name):
                result = roofline.evaluate(
                    self.model,
                    profiles.get_profile(name),
                    spec.MXFP8,
                    workload.decode(4096),
                )
                self.assertGreater(result.throughput, 0.0)
                self.assertTrue(result.is_upper_bound)


class TestArrayGeometry(unittest.TestCase):
    """阵列几何只能从 gem5 的 DEFAULT_PROFILE 读，不许在 eval 侧重定义。"""

    @classmethod
    def setUpClass(cls):
        cls.geom = geometry.load_array_geometry()
        root = geometry.repo_root()
        cls.gem5 = geometry._load_gem5_hardware(root).DEFAULT_PROFILE

    def test_fields_match_gem5_profile(self):
        params = self.gem5["parameters"]
        derived = self.gem5["derived"]
        self.assertEqual(self.geom.profile_name, self.gem5["name"])
        self.assertEqual(self.geom.spec_version, self.gem5["spec_version"])
        self.assertEqual(self.geom.array_n, params["arrayN"])
        self.assertEqual(self.geom.array_m, params["arrayM"])
        self.assertEqual(self.geom.total_n, derived["totalN"])
        self.assertEqual(self.geom.a_ext_w, derived["aExtW"])
        self.assertEqual(self.geom.b_ext_w, derived["bExtW"])
        self.assertEqual(self.geom.c_eff_w, derived["cEffW"])
        self.assertEqual(self.geom.c_ext_w, derived["cExtW"])
        self.assertEqual(self.geom.mnk_width, derived["mnkWidth"])
        self.assertEqual(list(self.geom.supported_fmts), list(params["supportedFmts"]))

    def test_tile_n_is_total_n(self):
        self.assertEqual(self.geom.tile_n, 16)
        self.assertEqual(self.geom.total_n, self.geom.array_n)
        self.assertEqual(self.geom.array_m, self.geom.array_n)

    def test_beat_bytes_are_32(self):
        # 同源取 max 的前提就是单口 32 B/cycle
        self.assertEqual(self.geom.a_bytes_per_beat, 32)
        self.assertEqual(self.geom.b_bytes_per_beat, 32)


class TestFormatSwitching(unittest.TestCase):
    """换格式只换 NumFormat 实例，mac_family 跟着换算力档位。"""

    @classmethod
    def setUpClass(cls):
        cls.model = qwen3_06b.build()
        cls.profile = profiles.get_profile("G0")

    def test_mxfp4_halves_compute_time(self):
        # arch_roadmap.md：INT4/MXFP4 是 1024 MAC/cycle，INT8 族是 512
        fp8 = roofline.evaluate(
            self.model, self.profile, spec.MXFP8, workload.decode(0)
        )
        fp4 = roofline.evaluate(
            self.model, self.profile, spec.MXFP4, workload.decode(0)
        )
        self.assertAlmostEqual(fp8.t_compute_s / fp4.t_compute_s, 2.0, places=9)
        self.assertEqual(fp8.work.total, fp4.work.total)

    def test_bytes_per_param_falls_back_to_exact(self):
        # 文档没给 MXFP4 的口径，必须退回精确值并如实报出来源
        self.assertIsNone(spec.MXFP4.doc_bytes_per_param)
        self.assertEqual(spec.MXFP4.bytes_per_param(use_doc_value=True), 17 / 32)
        self.assertEqual(
            spec.MXFP4.bytes_per_param_source(use_doc_value=True), "exact(17/32)"
        )
        self.assertEqual(
            spec.MXFP8.bytes_per_param_source(use_doc_value=True), "doc(1.031)"
        )
        self.assertEqual(
            spec.MXFP8.bytes_per_param_source(use_doc_value=False), "exact(33/32)"
        )

    def test_scale_bytes_are_separate(self):
        # MX 族的 scale 是独立流量，不许折进 elem_bytes
        self.assertEqual(spec.MXFP8.elem_bytes, 1)
        self.assertEqual(spec.MXFP8.scale_bytes_per_elem, Fraction(1, 32))
        self.assertEqual(spec.INT8.scale_bytes_per_elem, 0)


class TestManifestAndExcluded(unittest.TestCase):
    """可信度与未建模项是单点出口，不能被绕过。"""

    @classmethod
    def setUpClass(cls):
        cls.model = qwen3_06b.build()

    def _manifest(self):
        return manifest.build(
            profile=profiles.get_profile("G0"),
            model_name=self.model.name,
            layer="L0",
            named_inputs={"dram_eta": 1.0, "fmt": "MXFP8"},
            excluded_costs=excluded.for_workload(workload.decode(4096)),
        )

    def test_never_claims_validity(self):
        m = self._manifest()
        self.assertFalse(m.performance_valid)
        self.assertFalse(m.data_valid)
        self.assertEqual(m.trust_level, manifest.TrustLevel.ENGINEERING_ESTIMATE.value)
        self.assertEqual(m.notice, manifest.UNCALIBRATED_NOTICE)
        self.assertIn("本报告未导入或应用 RTL 校准证据", m.not_calibrated_reason)

    def test_excluded_costs_are_listed(self):
        for point in (workload.decode(4096), workload.prefill(1024)):
            with self.subTest(point=point.label):
                items = excluded.for_workload(point)
                self.assertGreater(len(items), 0)

    def test_prefill_lists_softmax_gap(self):
        # 文档 §9.2 的 57,344 二次项系数不含 softmax 的 224·S²，列明而不是偷偷补上
        text = "\n".join(excluded.for_workload(workload.prefill(1024)))
        self.assertIn("softmax", text.lower())

    def test_manifest_digest_is_deterministic(self):
        self.assertEqual(self._manifest().digest, self._manifest().digest)

    def test_sources_are_fingerprinted(self):
        paths = {s.path for s in self._manifest().sources}
        self.assertIn("src/workerbee/gem5/configs/hardware.py", paths)
        self.assertIn("src/hive/doc/arch.md", paths)


class TestTorchCrossCheck(unittest.TestCase):
    """torch 是可选依赖，缺了只能跳过，不能让流水线失败。"""

    def test_skips_without_module(self):
        result = torch_check.cross_check(qwen3_06b.build(), workload.decode(4096))
        self.assertEqual(result.status, "skipped")
        self.assertTrue(result.reason)


class TestCli(unittest.TestCase):
    """run.py 两次改动都是静默崩在这里，端到端守住它。"""

    def _run(self, argv):
        out, err = io.StringIO(), io.StringIO()
        with redirect_stdout(out), redirect_stderr(err):
            code = run.main(argv)
        return code, out.getvalue(), err.getvalue()

    def test_every_profile_and_format_combination(self):
        for profile in ("G0", "G0_PRO", "310"):
            for fmt in sorted(spec.FORMATS):
                with self.subTest(profile=profile, fmt=fmt):
                    code, out, _ = self._run(
                        ["--profile", profile, "--fmt", fmt, "--seq", "0,4096"]
                    )
                    self.assertEqual(code, 0)
                    self.assertIn(manifest.UNCALIBRATED_NOTICE, out)

    def test_all_workload_phases(self):
        for phase, seq in (("decode", "4096"), ("prefill", "1024"), ("frame", "0")):
            with self.subTest(phase=phase):
                code, out, _ = self._run(["--workload", phase, "--seq", seq])
                self.assertEqual(code, 0)
                self.assertIn("未建模项", out)

    def test_output_is_deterministic(self):
        argv = ["--profile", "G0", "--seq", "0,4096,32768"]
        self.assertEqual(self._run(argv)[1], self._run(argv)[1])

    def test_warns_on_format_outside_gem5_supported_set(self):
        # INT8/MXFP8/MXFP4 都在 supportedFmts 里，不该报警
        for fmt in sorted(spec.FORMATS):
            with self.subTest(fmt=fmt):
                _, _, err = self._run(["--fmt", fmt, "--seq", "0"])
                self.assertNotIn("warning", err)

    def test_json_round_trip(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "result.json"
            code, _, _ = self._run(["--seq", "0,4096", "--json", str(path)])
            self.assertEqual(code, 0)
            payload = json.loads(path.read_text(encoding="utf-8"))
            self.assertFalse(payload["manifest"]["performance_valid"])
            self.assertFalse(payload["manifest"]["data_valid"])
            self.assertGreater(len(payload["manifest"]["excluded_costs"]), 0)
            self.assertEqual(len(payload["results"]), 2)


class TestUpperBoundContract(unittest.TestCase):
    """L0 的输出是上界，不是预测；调用方不能把它降级成预测。"""

    @classmethod
    def setUpClass(cls):
        cls.result = roofline.evaluate(
            qwen3_06b.build(),
            profiles.get_profile("G0"),
            spec.MXFP8,
            workload.decode(4096),
        )

    def test_is_upper_bound_not_settable(self):
        with self.assertRaises(TypeError):
            roofline.L0Result(  # type: ignore[call-arg]
                **{**vars(self.result), "is_upper_bound": False}
            )

    def test_time_is_max_of_two_rooflines(self):
        self.assertAlmostEqual(
            self.result.t_s, max(self.result.t_bandwidth_s, self.result.t_compute_s)
        )


if __name__ == "__main__":
    unittest.main()
