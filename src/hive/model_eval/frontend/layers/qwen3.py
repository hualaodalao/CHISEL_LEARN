"""Reusable Qwen3-family layer definitions.

The block is deliberately a composite layer over generic primitives.  It is not
an "attention" layer: it contains two norms, attention, a SwiGLU MLP and two
residuals.
"""

from __future__ import annotations

from dataclasses import dataclass

from .base import (LayerDefinition, LayerGeometry, LayerInstance, LayerL0,
                   LAYERS, WeightSpec, sum_l0)
from .generic import (ActivationGateGeometry, AttentionMatmulGeometry,
                      HeadRmsNormGeometry, KvAppendGeometry, LinearGeometry,
                      ResidualGeometry, RmsNormGeometry, RopeGeometry,
                      SoftmaxGeometry)


@dataclass(frozen=True)
class GqaAttentionGeometry(LayerGeometry):
    hidden: int
    heads: int
    kv_heads: int
    head_dim: int
    qk_norm: bool = True
    rope: bool = True

    def validate(self) -> None:
        for value in (self.hidden, self.heads, self.kv_heads, self.head_dim):
            if value <= 0:
                raise ValueError("attention dimensions must be positive")
        if self.heads % self.kv_heads:
            raise ValueError("heads must be divisible by kv_heads")
        # Qwen3-0.6B has hidden=1024 but q_width=16*128=2048.
        # Do not require hidden == heads * head_dim.


@dataclass(frozen=True)
class SwigluGeometry(LayerGeometry):
    hidden: int
    intermediate: int

    def validate(self) -> None:
        if self.hidden <= 0 or self.intermediate <= 0:
            raise ValueError("SwiGLU dimensions must be positive")


@dataclass(frozen=True)
class Qwen3BlockGeometry(LayerGeometry):
    hidden: int
    heads: int
    kv_heads: int
    head_dim: int
    intermediate: int
    qk_norm: bool = True
    rope: bool = True

    def validate(self) -> None:
        GqaAttentionGeometry(self.hidden, self.heads, self.kv_heads,
                             self.head_dim, self.qk_norm, self.rope).validate()
        SwigluGeometry(self.hidden, self.intermediate).validate()


def _rms_norm(instance_id: str, width: int, *, quantize: bool = False,
              op_name: str | None = None) -> LayerInstance:
    return LayerInstance(
        id=instance_id,
        type="generic.rms_norm",
        geometry=RmsNormGeometry(width, quantize=quantize),
        weights=(WeightSpec("weight", (width,)),),
        op_name=op_name,
    )


def _linear(instance_id: str, in_features: int, out_features: int, *,
            op_name: str | None = None) -> LayerInstance:
    return LayerInstance(
        id=instance_id,
        type="generic.linear",
        geometry=LinearGeometry(in_features, out_features),
        weights=(WeightSpec("weight", (out_features, in_features)),),
        op_name=op_name,
    )


def _scope(instance_id: str) -> str:
    """Legacy op namespace: attention/MLP leaves report under the block ID."""
    return instance_id.rsplit("/", 1)[0] if "/" in instance_id else instance_id


def qwen3_attention_children(instance: LayerInstance) -> tuple[LayerInstance, ...]:
    geometry = instance.geometry
    assert isinstance(geometry, GqaAttentionGeometry)
    prefix = instance.id
    scope = _scope(prefix)
    q_width = geometry.heads * geometry.head_dim
    kv_width = geometry.kv_heads * geometry.head_dim

    def op(name: str) -> str:
        return f"{scope}/{name}"

    children = [
        _linear(f"{prefix}/q_proj", geometry.hidden, q_width, op_name=op("q_proj")),
        _linear(f"{prefix}/k_proj", geometry.hidden, kv_width, op_name=op("k_proj")),
        _linear(f"{prefix}/v_proj", geometry.hidden, kv_width, op_name=op("v_proj")),
    ]
    if geometry.qk_norm:
        children.append(LayerInstance(
            id=f"{prefix}/qk_norm",
            type="generic.head_rms_norm",
            geometry=HeadRmsNormGeometry(geometry.heads, geometry.kv_heads,
                                         geometry.head_dim),
            weights=(WeightSpec("weight", (2 * geometry.head_dim,)),),
            op_name=op("qk_norm"),
        ))
    if geometry.rope:
        children.append(LayerInstance(
            id=f"{prefix}/rope",
            type="generic.rope",
            geometry=RopeGeometry(geometry.heads, geometry.kv_heads, geometry.head_dim),
            op_name=op("rope"),
        ))
    children.extend((
        LayerInstance(
            id=f"{prefix}/kv_transfer",
            type="generic.kv_append",
            geometry=KvAppendGeometry(geometry.kv_heads, geometry.head_dim),
            op_name=op("kv_transfer"),
        ),
        LayerInstance(
            id=f"{prefix}/qk",
            type="generic.attention_matmul",
            geometry=AttentionMatmulGeometry(geometry.heads, geometry.head_dim, "qk"),
            op_name=op("qk"),
        ),
        LayerInstance(
            id=f"{prefix}/softmax",
            type="generic.softmax",
            geometry=SoftmaxGeometry(geometry.heads),
            op_name=op("softmax"),
        ),
        LayerInstance(
            id=f"{prefix}/av",
            type="generic.attention_matmul",
            geometry=AttentionMatmulGeometry(geometry.heads, geometry.head_dim, "av"),
            op_name=op("av"),
        ),
        _linear(f"{prefix}/o_proj", q_width, geometry.hidden, op_name=op("o_proj")),
    ))
    return tuple(children)


def qwen3_attention_l0(instance: LayerInstance) -> LayerL0:
    return sum_l0(qwen3_attention_children(instance))


def qwen3_mlp_children(instance: LayerInstance) -> tuple[LayerInstance, ...]:
    geometry = instance.geometry
    assert isinstance(geometry, SwigluGeometry)
    prefix = instance.id
    scope = _scope(prefix)

    def op(name: str) -> str:
        return f"{scope}/{name}"

    return (
        _linear(f"{prefix}/gate_proj", geometry.hidden, geometry.intermediate,
                op_name=op("gate_proj")),
        _linear(f"{prefix}/up_proj", geometry.hidden, geometry.intermediate,
                op_name=op("up_proj")),
        LayerInstance(
            id=f"{prefix}/silu_gate",
            type="generic.activation_gate",
            geometry=ActivationGateGeometry(geometry.intermediate),
            op_name=op("silu_gate"),
        ),
        _linear(f"{prefix}/down_proj", geometry.intermediate, geometry.hidden,
                op_name=op("down_proj")),
    )


def qwen3_mlp_l0(instance: LayerInstance) -> LayerL0:
    return sum_l0(qwen3_mlp_children(instance))


def qwen3_block_children(instance: LayerInstance) -> tuple[LayerInstance, ...]:
    geometry = instance.geometry
    assert isinstance(geometry, Qwen3BlockGeometry)
    prefix = instance.id
    attention = GqaAttentionGeometry(
        geometry.hidden, geometry.heads, geometry.kv_heads,
        geometry.head_dim, geometry.qk_norm, geometry.rope,
    )
    mlp = SwigluGeometry(geometry.hidden, geometry.intermediate)
    return (
        _rms_norm(f"{prefix}/input_norm", geometry.hidden, quantize=True),
        LayerInstance(
            id=f"{prefix}/attention",
            type="qwen3.gqa_attention",
            geometry=attention,
        ),
        LayerInstance(
            id=f"{prefix}/attention_residual",
            type="generic.residual",
            geometry=ResidualGeometry(geometry.hidden),
        ),
        _rms_norm(f"{prefix}/post_norm", geometry.hidden, quantize=True),
        LayerInstance(
            id=f"{prefix}/mlp",
            type="qwen3.swiglu_mlp",
            geometry=mlp,
        ),
        LayerInstance(
            id=f"{prefix}/mlp_residual",
            type="generic.residual",
            geometry=ResidualGeometry(geometry.hidden),
        ),
    )


def qwen3_block_l0(instance: LayerInstance) -> LayerL0:
    return sum_l0(qwen3_block_children(instance))


LAYERS.register(LayerDefinition(
    name="qwen3.gqa_attention",
    version=1,
    geometry_type=GqaAttentionGeometry,
    input_roles=("hidden",),
    output_roles=("hidden",),
    weight_roles=(),
    children=qwen3_attention_children,
    l0=qwen3_attention_l0,
    source=__file__,
))

LAYERS.register(LayerDefinition(
    name="qwen3.swiglu_mlp",
    version=1,
    geometry_type=SwigluGeometry,
    input_roles=("hidden",),
    output_roles=("hidden",),
    weight_roles=(),
    children=qwen3_mlp_children,
    l0=qwen3_mlp_l0,
    source=__file__,
))

LAYERS.register(LayerDefinition(
    name="qwen3.decoder_block",
    version=1,
    geometry_type=Qwen3BlockGeometry,
    input_roles=("hidden",),
    output_roles=("hidden",),
    weight_roles=(),
    children=qwen3_block_children,
    l0=qwen3_block_l0,
    source=__file__,
))

LAYERS.alias("qwen3_full_attention", "qwen3.decoder_block", target_version=1)
