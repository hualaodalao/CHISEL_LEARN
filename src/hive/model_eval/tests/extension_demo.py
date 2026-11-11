"""Test-only extension module: registers new layers and a model without touching core.

Load it explicitly with ``--extension hive.model_eval.tests.extension_demo`` or
an ``importlib.import_module`` call.
"""

from __future__ import annotations

from dataclasses import dataclass

from hive.model_eval.frontend.layers import (LayerDefinition, LayerGeometry,
                                             LayerInstance, LayerL0, WeightSpec,
                                             register_layer, sum_l0)
from hive.model_eval.frontend.layers.generic import (ActivationGateGeometry,
                                                     LinearGeometry,
                                                     RmsNormGeometry,
                                                     SamplingGeometry)
from hive.model_eval.frontend.plan import ModelPlan, WeightAllocation
from hive.model_eval.frontend.registry import register_model
from hive.model_eval.l1.emitters import register_emitter


@dataclass(frozen=True)
class ScaleGeometry(LayerGeometry):
    width: int

    def validate(self) -> None:
        if self.width <= 0:
            raise ValueError("scale width must be positive")


@dataclass(frozen=True)
class GeluMlpGeometry(LayerGeometry):
    hidden: int
    intermediate: int

    def validate(self) -> None:
        if self.hidden <= 0 or self.intermediate <= 0:
            raise ValueError("MLP dimensions must be positive")


def _scale_l0(instance: LayerInstance) -> LayerL0:
    assert isinstance(instance.geometry, ScaleGeometry)
    return LayerL0(mac_fixed=instance.geometry.width)


def _mlp_children(instance: LayerInstance) -> tuple[LayerInstance, ...]:
    assert isinstance(instance.geometry, GeluMlpGeometry)
    geometry = instance.geometry
    prefix = instance.id
    return (
        LayerInstance(f"{prefix}/gate_proj", "generic.linear",
                      LinearGeometry(geometry.hidden, geometry.intermediate),
                      weights=(WeightSpec("weight", (geometry.intermediate, geometry.hidden)),),
                      op_name=f"{prefix}/gate_proj"),
        LayerInstance(f"{prefix}/up_proj", "generic.linear",
                      LinearGeometry(geometry.hidden, geometry.intermediate),
                      weights=(WeightSpec("weight", (geometry.intermediate, geometry.hidden)),),
                      op_name=f"{prefix}/up_proj"),
        LayerInstance(f"{prefix}/silu_gate", "generic.activation_gate",
                      ActivationGateGeometry(geometry.intermediate),
                      op_name=f"{prefix}/silu_gate"),
        LayerInstance(f"{prefix}/down_proj", "generic.linear",
                      LinearGeometry(geometry.intermediate, geometry.hidden),
                      weights=(WeightSpec("weight", (geometry.hidden, geometry.intermediate)),),
                      op_name=f"{prefix}/down_proj"),
    )


def _mlp_l0(instance: LayerInstance) -> LayerL0:
    return sum_l0(_mlp_children(instance))


register_layer(LayerDefinition(
    name="acme.scale",
    version=1,
    geometry_type=ScaleGeometry,
    input_roles=("hidden",),
    output_roles=("hidden",),
    weight_roles=(),
    l0=_scale_l0,
    source=__file__,
))

register_layer(LayerDefinition(
    name="acme.gelu_mlp",
    version=1,
    geometry_type=GeluMlpGeometry,
    input_roles=("hidden",),
    output_roles=("hidden",),
    weight_roles=(),
    children=_mlp_children,
    l0=_mlp_l0,
    source=__file__,
))


def emit_scale(ctx, instance, inputs):
    geometry = instance.geometry
    rows = ctx.rows
    op = ctx.operation(instance, "VPU", "residual",
                       mac=geometry.width * rows, elements=geometry.width * rows,
                       read_bytes=8 * geometry.width * rows,
                       write_bytes=4 * geometry.width * rows)
    return {"hidden": ctx.builder.vector(op, (inputs["hidden"],),
                                         (rows, geometry.width))}


def emit_mlp(ctx, instance, inputs):
    hidden = inputs["hidden"]
    gate = ctx.emit(ctx.child(instance, "gate_proj"), {"x": hidden})["out"]
    up = ctx.emit(ctx.child(instance, "up_proj"), {"x": hidden})["out"]
    gated = ctx.emit(ctx.child(instance, "silu_gate"),
                     {"gate": gate, "up": up})["hidden"]
    return {"hidden": ctx.emit(ctx.child(instance, "down_proj"),
                               {"x": gated})["out"]}


register_emitter("acme.scale", 1, emit_scale)
register_emitter("acme.gelu_mlp", 1, emit_mlp)


def build(config: dict | None = None) -> ModelPlan:
    cfg = {"hidden_size": 64, "intermediate_size": 128, "vocab_size": 256}
    cfg.update(config or {})
    hidden = cfg["hidden_size"]
    intermediate = cfg["intermediate_size"]
    vocab = cfg["vocab_size"]
    return ModelPlan(
        name="Acme-Demo",
        layers=(
            LayerInstance("scale-0", "acme.scale", ScaleGeometry(hidden)),
            LayerInstance("mlp-0", "acme.gelu_mlp",
                          GeluMlpGeometry(hidden, intermediate)),
            LayerInstance("final-norm", "generic.rms_norm",
                          RmsNormGeometry(hidden, last_position_only=True),
                          weights=(WeightSpec("weight", (hidden,)),),
                          op_name="final-norm/norm"),
            LayerInstance("lm-head", "generic.lm_head",
                          LinearGeometry(hidden, vocab),
                          weights=(WeightSpec("weight", (vocab, hidden),
                                              allocation="embedding"),),
                          op_name="lm-head/projection"),
            LayerInstance("sampling", "generic.sampling",
                          SamplingGeometry(vocab), op_name="sampling/sampling"),
        ),
        allocations=(WeightAllocation("embedding", (vocab, hidden),
                                      checkpoint_copies=2),),
        source=__file__,
    )


register_model("Acme-Demo", build, source=__file__)
