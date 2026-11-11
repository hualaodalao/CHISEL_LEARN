"""Ordered model plans: layers plus explicit weight allocations."""

from __future__ import annotations

from dataclasses import dataclass
from math import prod

from .layers.base import (LAYERS, LayerInstance, LayerL0, WeightAllocation,
                          layer_l0)


@dataclass(frozen=True)
class ModelPlan:
    name: str
    layers: tuple[LayerInstance, ...]
    allocations: tuple[WeightAllocation, ...] = ()
    input_roles: tuple[str, ...] = ("hidden",)
    output_roles: tuple[str, ...] = ("hidden",)
    source: str = ""

    def __post_init__(self) -> None:
        if not self.name or not self.layers:
            raise ValueError("model plan requires a name and at least one layer")
        if not self.source:
            raise ValueError("model plan requires a source for provenance")
        if len(set(self.input_roles)) != len(self.input_roles):
            raise ValueError("duplicate model input roles")
        if len(set(self.output_roles)) != len(self.output_roles):
            raise ValueError("duplicate model output roles")

    def iter_layers(self):
        stack = list(reversed(self.layers))
        seen = set()
        while stack:
            layer = stack.pop()
            if layer.id in seen:
                raise ValueError(f"duplicate layer ID: {layer.id}")
            seen.add(layer.id)
            yield layer
            definition = LAYERS.get(layer.type, layer.version)
            if definition.children is not None:
                stack.extend(reversed(definition.children(layer)))

    def iter_leaves(self):
        """Yield only layers that own compute, so aggregates are counted once."""
        for layer in self.iter_layers():
            if LAYERS.get(layer.type, layer.version).children is None:
                yield layer

    def validate(self) -> None:
        allocations = {item.id: item for item in self.allocations}
        reference_count = {key: 0 for key in allocations}
        for layer in self.iter_layers():
            definition = LAYERS.get(layer.type, layer.version)
            if not isinstance(layer.geometry, definition.geometry_type):
                raise ValueError(f"{layer.id}: geometry type does not match {layer.type}")
            layer.geometry.validate()
            allowed = set(definition.weight_roles) | set(definition.optional_weight_roles)
            roles = {weight.role for weight in layer.weights}
            missing = set(definition.weight_roles) - roles
            unknown = roles - allowed
            if missing:
                raise ValueError(f"{layer.id}: missing weight roles {sorted(missing)}")
            if unknown:
                raise ValueError(f"{layer.id}: unknown weight roles {sorted(unknown)}")
            for weight in layer.weights:
                if weight.allocation is None:
                    continue
                if weight.allocation not in allocations:
                    raise ValueError(f"{layer.id}: undeclared allocation {weight.allocation}")
                if allocations[weight.allocation].shape != weight.shape:
                    raise ValueError(f"{layer.id}: allocation shape mismatch")
                reference_count[weight.allocation] += 1
        for key, allocation in allocations.items():
            if reference_count[key] == 0:
                raise ValueError(f"allocation {key} is not referenced by any layer")

    def unique_params(self) -> int:
        unique = sum(prod(allocation.shape) for allocation in self.allocations)
        for layer in self.iter_leaves():
            unique += sum(prod(weight.shape) for weight in layer.weights
                          if weight.allocation is None)
        return unique

    def checkpoint_params(self) -> int:
        checkpoint = 0
        references = {allocation.id: 0 for allocation in self.allocations}
        for layer in self.iter_leaves():
            for weight in layer.weights:
                checkpoint += prod(weight.shape)
                if weight.allocation is not None:
                    references[weight.allocation] += 1
        for allocation in self.allocations:
            extra = max(0, allocation.checkpoint_copies - references[allocation.id])
            checkpoint += extra * prod(allocation.shape)
        return checkpoint

    def mac_fixed(self) -> int:
        return sum(layer_l0(layer).mac_fixed for layer in self.iter_leaves())

    def mac_s_coef(self) -> int:
        return sum(layer_l0(layer).mac_s_coef for layer in self.iter_leaves())

    def mac_s2_coef(self) -> int:
        return sum(layer_l0(layer).mac_s2_coef for layer in self.iter_leaves())

    def mac_output(self) -> int:
        return sum(layer_l0(layer).mac_output for layer in self.iter_leaves())

    # The following names mirror ModelSpec so L0 accounting can consume either.
    @property
    def params_unique(self) -> int:
        return self.unique_params()

    @property
    def params_checkpoint(self) -> int:
        return self.checkpoint_params()

    @property
    def params_streamed_per_token(self) -> int:
        return sum(layer_l0(layer).params_streamed_per_token for layer in self.iter_leaves())

    @property
    def kv_bytes_per_token(self) -> int:
        return sum(layer_l0(layer).kv_bytes_per_token for layer in self.iter_leaves())

    @property
    def state_bytes(self) -> int:
        return sum(layer_l0(layer).state_bytes for layer in self.iter_leaves())

    @property
    def layer_count(self) -> int:
        return sum(1 for _ in self.iter_layers())

    @property
    def final_norm_mac(self) -> int:
        return sum(layer_l0(layer).mac_fixed for layer in self.layers
                   if getattr(layer.geometry, "last_position_only", False))

    @property
    def lm_head_mac(self) -> int:
        return self.mac_output()

    def body_mac_fixed(self) -> int:
        return self.mac_fixed() - self.final_norm_mac

    def body_mac_s_coef(self) -> int:
        return self.mac_s_coef()

    def body_mac_s2_coef(self) -> int:
        return self.mac_s2_coef()

    def l0(self, layer: LayerInstance) -> LayerL0:
        return layer_l0(layer)

    def provenance_paths(self) -> tuple[str, ...]:
        paths = {self.source}
        for layer in self.iter_layers():
            definition = LAYERS.get(layer.type, layer.version)
            if definition.source:
                paths.add(definition.source)
        return tuple(sorted(paths))
