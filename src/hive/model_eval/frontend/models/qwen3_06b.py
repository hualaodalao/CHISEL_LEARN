"""Qwen3-0.6B as a ModelPlan demo.

Geometry and L0 coefficients follow
`src/hive/doc/model_analysis/Qwen3-0.6B-逐层计算拆解.md#附录B`.
This module only composes registered layers; it contains no closed-form maths.
"""

from __future__ import annotations

from ..layers.base import LayerInstance, WeightAllocation, WeightSpec
from ..layers.generic import (LinearGeometry, RmsNormGeometry, SamplingGeometry)
from ..layers.qwen3 import Qwen3BlockGeometry
from ..plan import ModelPlan
from ..registry import register_model

SOURCE = "src/hive/doc/model_analysis/Qwen3-0.6B-逐层计算拆解.md#附录B"

CONFIG = {
    "hidden_size": 1024,
    "intermediate_size": 3072,
    "vocab_size": 151936,
    "num_hidden_layers": 28,
    "num_attention_heads": 16,
    "num_key_value_heads": 8,
    "head_dim": 128,
}


def build(config: dict | None = None) -> ModelPlan:
    cfg = dict(CONFIG if config is None else config)
    hidden = cfg["hidden_size"]
    intermediate = cfg["intermediate_size"]
    vocab = cfg["vocab_size"]
    layers = cfg["num_hidden_layers"]

    block = Qwen3BlockGeometry(
        hidden=hidden,
        heads=cfg["num_attention_heads"],
        kv_heads=cfg["num_key_value_heads"],
        head_dim=cfg["head_dim"],
        intermediate=intermediate,
    )
    stack = tuple(
        LayerInstance(id=f"block-{index:02d}", type="qwen3.decoder_block", geometry=block)
        for index in range(layers)
    )
    head = (
        LayerInstance(
            id="final-norm",
            type="generic.rms_norm",
            geometry=RmsNormGeometry(hidden, last_position_only=True),
            weights=(WeightSpec("weight", (hidden,)),),
            op_name="final-norm/norm",
        ),
        LayerInstance(
            id="lm-head",
            type="generic.lm_head",
            geometry=LinearGeometry(hidden, vocab),
            weights=(WeightSpec("weight", (vocab, hidden),
                                allocation="embedding"),),
            op_name="lm-head/projection",
        ),
        LayerInstance(
            id="sampling",
            type="generic.sampling",
            geometry=SamplingGeometry(vocab),
            op_name="sampling/sampling",
        ),
    )
    return ModelPlan(
        name="Qwen3-0.6B",
        layers=stack + head,
        allocations=(WeightAllocation("embedding", (vocab, hidden),
                                      checkpoint_copies=2),),
        source=SOURCE,
    )


register_model("Qwen3-0.6B", build, source=SOURCE)
