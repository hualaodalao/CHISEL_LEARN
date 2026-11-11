"""Model-agnostic layers shared by transformer families."""

from __future__ import annotations

from dataclasses import dataclass

from .base import LayerDefinition, LayerGeometry, LayerInstance, LayerL0, LAYERS


@dataclass(frozen=True)
class LinearGeometry(LayerGeometry):
    in_features: int
    out_features: int
    bias: bool = False

    def validate(self) -> None:
        if self.in_features <= 0 or self.out_features <= 0:
            raise ValueError("linear dimensions must be positive")


@dataclass(frozen=True)
class RmsNormGeometry(LayerGeometry):
    width: int
    quantize: bool = False
    last_position_only: bool = False

    def validate(self) -> None:
        if self.width <= 0:
            raise ValueError("norm width must be positive")


@dataclass(frozen=True)
class HeadRmsNormGeometry(LayerGeometry):
    heads: int
    kv_heads: int
    head_dim: int

    def validate(self) -> None:
        if self.heads <= 0 or self.kv_heads <= 0 or self.head_dim <= 0:
            raise ValueError("head norm dimensions must be positive")
        if self.heads % self.kv_heads:
            raise ValueError("heads must be divisible by kv_heads")


@dataclass(frozen=True)
class RopeGeometry(LayerGeometry):
    heads: int
    kv_heads: int
    head_dim: int

    def validate(self) -> None:
        if self.heads <= 0 or self.kv_heads <= 0 or self.head_dim <= 0:
            raise ValueError("rope dimensions must be positive")


@dataclass(frozen=True)
class ResidualGeometry(LayerGeometry):
    width: int

    def validate(self) -> None:
        if self.width <= 0:
            raise ValueError("residual width must be positive")


@dataclass(frozen=True)
class SoftmaxGeometry(LayerGeometry):
    heads: int

    def validate(self) -> None:
        if self.heads <= 0:
            raise ValueError("softmax heads must be positive")


@dataclass(frozen=True)
class AttentionMatmulGeometry(LayerGeometry):
    heads: int
    head_dim: int
    mode: str

    def validate(self) -> None:
        if self.heads <= 0 or self.head_dim <= 0:
            raise ValueError("attention matmul dimensions must be positive")
        if self.mode not in ("qk", "av"):
            raise ValueError("attention matmul mode must be qk or av")


@dataclass(frozen=True)
class ActivationGateGeometry(LayerGeometry):
    intermediate: int

    def validate(self) -> None:
        if self.intermediate <= 0:
            raise ValueError("activation gate width must be positive")


@dataclass(frozen=True)
class KvAppendGeometry(LayerGeometry):
    kv_heads: int
    head_dim: int
    elem_bytes: int = 2

    def validate(self) -> None:
        if self.kv_heads <= 0 or self.head_dim <= 0 or self.elem_bytes <= 0:
            raise ValueError("KV dimensions must be positive")


@dataclass(frozen=True)
class SamplingGeometry(LayerGeometry):
    vocab_size: int

    def validate(self) -> None:
        if self.vocab_size <= 0:
            raise ValueError("vocab size must be positive")


def _linear_l0(instance: LayerInstance) -> LayerL0:
    geometry = instance.geometry
    assert isinstance(geometry, LinearGeometry)
    params = geometry.in_features * geometry.out_features
    params += geometry.out_features if geometry.bias else 0
    return LayerL0(mac_fixed=params, params_streamed_per_token=params)


def _lm_head_l0(instance: LayerInstance) -> LayerL0:
    geometry = instance.geometry
    assert isinstance(geometry, LinearGeometry)
    params = geometry.in_features * geometry.out_features
    params += geometry.out_features if geometry.bias else 0
    return LayerL0(mac_output=params, params_streamed_per_token=params)


def _rms_norm_l0(instance: LayerInstance) -> LayerL0:
    geometry = instance.geometry
    assert isinstance(geometry, RmsNormGeometry)
    return LayerL0(mac_fixed=2 * geometry.width, params_streamed_per_token=geometry.width)


def _head_rms_norm_l0(instance: LayerInstance) -> LayerL0:
    geometry = instance.geometry
    assert isinstance(geometry, HeadRmsNormGeometry)
    return LayerL0(
        mac_fixed=2 * geometry.head_dim * (geometry.heads + geometry.kv_heads),
        params_streamed_per_token=2 * geometry.head_dim,
    )


def _rope_l0(instance: LayerInstance) -> LayerL0:
    geometry = instance.geometry
    assert isinstance(geometry, RopeGeometry)
    return LayerL0(mac_fixed=4 * (geometry.head_dim // 2) * (geometry.heads + geometry.kv_heads))


def _residual_l0(instance: LayerInstance) -> LayerL0:
    geometry = instance.geometry
    assert isinstance(geometry, ResidualGeometry)
    return LayerL0(mac_fixed=geometry.width)


def _softmax_l0(instance: LayerInstance) -> LayerL0:
    geometry = instance.geometry
    assert isinstance(geometry, SoftmaxGeometry)
    return LayerL0(mac_s_coef=geometry.heads)


def _attention_matmul_l0(instance: LayerInstance) -> LayerL0:
    geometry = instance.geometry
    assert isinstance(geometry, AttentionMatmulGeometry)
    total = geometry.heads * geometry.head_dim
    if geometry.mode == "qk":
        s2 = total // 2
    else:
        s2 = total - total // 2
    return LayerL0(mac_s_coef=total, mac_s2_coef=s2)


def _activation_gate_l0(instance: LayerInstance) -> LayerL0:
    geometry = instance.geometry
    assert isinstance(geometry, ActivationGateGeometry)
    return LayerL0(mac_fixed=geometry.intermediate)


def _kv_append_l0(instance: LayerInstance) -> LayerL0:
    geometry = instance.geometry
    assert isinstance(geometry, KvAppendGeometry)
    return LayerL0(kv_bytes_per_token=2 * geometry.kv_heads * geometry.head_dim
                   * geometry.elem_bytes)


LAYERS.register(LayerDefinition(
    name="generic.linear",
    version=1,
    geometry_type=LinearGeometry,
    input_roles=("x",),
    output_roles=("out",),
    weight_roles=("weight",),
    optional_weight_roles=("bias",),
    l0=_linear_l0,
    source=__file__,
))

LAYERS.register(LayerDefinition(
    name="generic.lm_head",
    version=1,
    geometry_type=LinearGeometry,
    input_roles=("hidden",),
    output_roles=("logits",),
    weight_roles=("weight",),
    optional_weight_roles=("bias",),
    l0=_lm_head_l0,
    source=__file__,
))

LAYERS.register(LayerDefinition(
    name="generic.rms_norm",
    version=1,
    geometry_type=RmsNormGeometry,
    input_roles=("hidden",),
    output_roles=("hidden",),
    weight_roles=("weight",),
    l0=_rms_norm_l0,
    source=__file__,
))

LAYERS.register(LayerDefinition(
    name="generic.head_rms_norm",
    version=1,
    geometry_type=HeadRmsNormGeometry,
    input_roles=("hidden",),
    output_roles=("hidden",),
    weight_roles=("weight",),
    l0=_head_rms_norm_l0,
    source=__file__,
))

LAYERS.register(LayerDefinition(
    name="generic.rope",
    version=1,
    geometry_type=RopeGeometry,
    input_roles=("hidden",),
    output_roles=("hidden",),
    weight_roles=(),
    l0=_rope_l0,
    source=__file__,
))

LAYERS.register(LayerDefinition(
    name="generic.residual",
    version=1,
    geometry_type=ResidualGeometry,
    input_roles=("x", "y"),
    output_roles=("hidden",),
    weight_roles=(),
    l0=_residual_l0,
    source=__file__,
))

LAYERS.register(LayerDefinition(
    name="generic.softmax",
    version=1,
    geometry_type=SoftmaxGeometry,
    input_roles=("logits",),
    output_roles=("probabilities",),
    weight_roles=(),
    l0=_softmax_l0,
    l0_excluded=("prefill softmax quadratic is not included in L0",),
    source=__file__,
))

LAYERS.register(LayerDefinition(
    name="generic.attention_matmul",
    version=1,
    geometry_type=AttentionMatmulGeometry,
    input_roles=("a", "b"),
    output_roles=("out",),
    weight_roles=(),
    l0=_attention_matmul_l0,
    source=__file__,
))

LAYERS.register(LayerDefinition(
    name="generic.activation_gate",
    version=1,
    geometry_type=ActivationGateGeometry,
    input_roles=("gate", "up"),
    output_roles=("hidden",),
    weight_roles=(),
    l0=_activation_gate_l0,
    source=__file__,
))

LAYERS.register(LayerDefinition(
    name="generic.kv_append",
    version=1,
    geometry_type=KvAppendGeometry,
    input_roles=("k", "v"),
    output_roles=("k_cache", "v_cache"),
    weight_roles=(),
    l0=_kv_append_l0,
    source=__file__,
))

def _sampling_l0(instance: LayerInstance) -> LayerL0:
    # CPU sampling has no accelerator work; its cost is an L1 scenario input.
    return LayerL0()


LAYERS.register(LayerDefinition(
    name="generic.sampling",
    version=1,
    geometry_type=SamplingGeometry,
    input_roles=("logits",),
    output_roles=(),
    weight_roles=(),
    l0=_sampling_l0,
    l0_excluded=("CPU sampling is an L1 scenario cost and is not included in L0",),
    source=__file__,
))
