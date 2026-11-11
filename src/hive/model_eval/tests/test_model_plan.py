"""ModelPlan / layer-registry anchors against the legacy Qwen ModelSpec.

The legacy ``frontend.qwen3_06b`` remains the L0 truth during the migration.
These tests prove the composed plan reproduces its parameter and L0 totals
without re-deriving any closed-form expression in the model file.
"""

import unittest
from dataclasses import replace

from hive.model_eval.frontend import builtins  # noqa: F401  (registers layers/models)
from hive.model_eval.frontend import qwen3_06b as legacy
from hive.model_eval.frontend.layers import LAYERS, LayerL0, layer_l0
from hive.model_eval.frontend.layers.generic import (
    AttentionMatmulGeometry,
    HeadRmsNormGeometry,
    LinearGeometry,
    RopeGeometry,
    SamplingGeometry,
)
from hive.model_eval.frontend.layers.qwen3 import Qwen3BlockGeometry
from hive.model_eval.frontend.plan import ModelPlan
from hive.model_eval.frontend.registry import get_model, list_models


class TestQwenModelPlanAnchors(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.plan = get_model("Qwen3-0.6B")
        cls.legacy = legacy.build()
        cls.blocks = tuple(layer for layer in cls.plan.iter_layers()
                           if layer.type == "qwen3.decoder_block")

    def test_plan_is_registered_and_valid(self):
        self.assertIn(("Qwen3-0.6B", 1), list_models())
        self.plan.validate()
        self.assertEqual(len(self.blocks), self.legacy.layer_types[0].count)

    def test_parameter_totals_match_legacy(self):
        self.assertEqual(self.plan.unique_params(), self.legacy.params_unique)
        self.assertEqual(self.plan.checkpoint_params(), self.legacy.params_checkpoint)
        self.assertEqual(self.plan.params_streamed_per_token,
                         self.legacy.params_streamed_per_token)
        self.assertEqual(self.plan.kv_bytes_per_token, self.legacy.kv_bytes_per_token)
        self.assertEqual(self.plan.state_bytes, self.legacy.state_bytes)

    def test_body_mac_totals_match_legacy(self):
        fixed = sum(layer_l0(block).mac_fixed for block in self.blocks)
        self.assertEqual(fixed, self.legacy.body_mac_fixed())
        self.assertEqual(sum(layer_l0(block).mac_s_coef for block in self.blocks),
                         self.legacy.body_mac_s_coef())
        self.assertEqual(sum(layer_l0(block).mac_s2_coef for block in self.blocks),
                         self.legacy.body_mac_s2_coef())

    def test_final_norm_is_separate_from_body(self):
        self.assertEqual(self.plan.mac_fixed() - self.legacy.body_mac_fixed(),
                         self.legacy.final_norm_mac)
        self.assertEqual(self.plan.mac_s_coef(), self.legacy.body_mac_s_coef())
        self.assertEqual(self.plan.mac_s2_coef(), self.legacy.body_mac_s2_coef())

    def test_lm_head_is_per_output_position(self):
        self.assertEqual(self.plan.lm_head_mac, self.legacy.lm_head_mac)
        lm_head = next(layer for layer in self.plan.iter_layers()
                       if layer.type == "generic.lm_head")
        l0 = layer_l0(lm_head)
        self.assertEqual(l0.mac_fixed, 0)
        self.assertEqual(l0.mac_s_coef, 0)
        self.assertEqual(l0.mac_output, self.legacy.lm_head_mac)

    def test_aggregate_l0_is_the_sum_of_children(self):
        block = self.blocks[0]
        definition = LAYERS.get(block.type, block.version)
        children = definition.children(block)
        self.assertEqual(layer_l0(block), sum((layer_l0(child) for child in children),
                                              LayerL0()))

    def test_aggregate_layers_own_no_weights(self):
        for block in self.blocks:
            self.assertEqual(block.weights, ())
        attention = next(layer for layer in self.plan.iter_layers()
                         if layer.type == "qwen3.gqa_attention")
        self.assertEqual(attention.weights, ())

    def test_attention_geometry_is_explicit(self):
        block = self.blocks[0]
        geometry = block.geometry
        self.assertIsInstance(geometry, Qwen3BlockGeometry)
        self.assertEqual(geometry.hidden, 1024)
        self.assertEqual(geometry.heads, 16)
        self.assertEqual(geometry.kv_heads, 8)
        self.assertEqual(geometry.head_dim, 128)
        # hidden != heads * head_dim; q_proj width comes from the head geometry.
        self.assertNotEqual(geometry.hidden, geometry.heads * geometry.head_dim)
        attention = next(child for child in
                         LAYERS.get(block.type, block.version).children(block)
                         if child.type == "qwen3.gqa_attention")
        q_proj = next(child for child in
                      LAYERS.get(attention.type, attention.version).children(attention)
                      if child.id.endswith("/q_proj"))
        self.assertIsInstance(q_proj.geometry, LinearGeometry)
        self.assertEqual(q_proj.geometry.out_features, 16 * 128)

    def test_attention_leaf_l0_matches_legacy_coefficients(self):
        attention = next(layer for layer in self.plan.iter_layers()
                         if layer.type == "qwen3.gqa_attention")
        definition = LAYERS.get(attention.type, attention.version)
        leaves = definition.children(attention)
        norm = next(child for child in leaves if child.type == "generic.head_rms_norm")
        rope = next(child for child in leaves if child.type == "generic.rope")
        qk, av = (child for child in leaves
                  if child.type == "generic.attention_matmul")
        softmax = next(child for child in leaves if child.type == "generic.softmax")
        self.assertIsInstance(norm.geometry, HeadRmsNormGeometry)
        self.assertIsInstance(rope.geometry, RopeGeometry)
        self.assertIsInstance(qk.geometry, AttentionMatmulGeometry)
        self.assertIsInstance(av.geometry, AttentionMatmulGeometry)
        self.assertEqual(layer_l0(norm).mac_fixed,
                         2 * 128 * (16 + 8))
        self.assertEqual(layer_l0(rope).mac_fixed, 4 * (128 // 2) * (16 + 8))
        self.assertEqual(layer_l0(qk).mac_s_coef + layer_l0(av).mac_s_coef,
                         2 * 16 * 128)
        self.assertEqual(layer_l0(softmax).mac_s_coef, 16)
        self.assertEqual(layer_l0(qk).mac_s2_coef + layer_l0(av).mac_s2_coef,
                         16 * 128)

    def test_tied_embedding_allocation(self):
        allocation = next(item for item in self.plan.allocations
                          if item.id == "embedding")
        self.assertEqual(allocation.shape, (151936, 1024))
        self.assertEqual(allocation.checkpoint_copies, 2)
        lm_head = next(layer for layer in self.plan.iter_leaves()
                       if layer.type == "generic.lm_head")
        self.assertEqual(lm_head.weights[0].allocation, "embedding")
        self.assertEqual(self.plan.unique_params() - 151936 * 1024,
                         self.legacy.params_unique - self.legacy.embedding_params)

    def test_sampling_has_zero_l0_and_is_documented_as_excluded(self):
        sampling = next(layer for layer in self.plan.iter_leaves()
                        if layer.type == "generic.sampling")
        self.assertIsInstance(sampling.geometry, SamplingGeometry)
        self.assertEqual(layer_l0(sampling), LayerL0())
        self.assertTrue(LAYERS.get(sampling.type, sampling.version).l0_excluded)

    def test_all_leaves_have_l0_accounting(self):
        for layer in self.plan.iter_leaves():
            definition = LAYERS.get(layer.type, layer.version)
            self.assertIsNotNone(definition.l0, f"{layer.id} lacks L0")
            self.assertIsInstance(layer_l0(layer), LayerL0)

    def test_provenance_covers_model_and_layers(self):
        paths = self.plan.provenance_paths()
        self.assertIn(self.plan.source, paths)
        self.assertTrue(all(path for path in paths))

    def test_legacy_alias_resolves_to_decoder_block(self):
        self.assertEqual(LAYERS.canonical("qwen3_full_attention"),
                         ("qwen3.decoder_block", 1))
        self.assertIs(LAYERS.get("qwen3_full_attention"),
                      LAYERS.get("qwen3.decoder_block"))


class TestModelPlanGuards(unittest.TestCase):
    def test_illegal_head_split_is_rejected(self):
        with self.assertRaises(ValueError):
            Qwen3BlockGeometry(1024, 15, 8, 128, 3072).validate()

    def test_duplicate_layer_id_is_rejected(self):
        plan = get_model("Qwen3-0.6B")
        duplicate = plan.layers + (plan.layers[0],)
        with self.assertRaisesRegex(ValueError, "duplicate layer ID"):
            tuple(replace(plan, layers=duplicate).iter_layers())

    def test_undeclared_weight_allocation_is_rejected(self):
        plan = get_model("Qwen3-0.6B")
        with self.assertRaisesRegex(ValueError, "undeclared allocation"):
            replace(plan, allocations=()).validate()

    def test_unknown_layer_type_is_rejected(self):
        plan = get_model("Qwen3-0.6B")
        broken = replace(plan.layers[0], type="missing.layer")
        with self.assertRaises(KeyError):
            replace(plan, layers=(broken,)).validate()


if __name__ == "__main__":
    unittest.main()
