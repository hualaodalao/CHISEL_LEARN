"""Layer contracts shared by model plans and the L0/L1 expanders.

This module deliberately has no dependency on ``l1``.  Layer definitions own
geometry, weight roles and closed-form L0 accounting; L1 emitters are registered
separately by the L1 package.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Callable


@dataclass(frozen=True)
class LayerGeometry:
    def validate(self) -> None:
        raise NotImplementedError


@dataclass(frozen=True)
class WeightAllocation:
    """A model-level unique weight allocation.

    ``checkpoint_copies`` covers tensors that are stored more than once in a
    checkpoint, e.g. a tied embedding and its lm-head projection.
    """

    id: str
    shape: tuple[int, ...]
    checkpoint_copies: int = 1

    def __post_init__(self) -> None:
        if not self.id or any(type(dim) is not int or dim <= 0 for dim in self.shape):
            raise ValueError("weight allocation requires an ID and positive dimensions")
        if type(self.checkpoint_copies) is not int or self.checkpoint_copies < 1:
            raise ValueError("checkpoint_copies must be a positive integer")


@dataclass(frozen=True)
class WeightSpec:
    role: str
    shape: tuple[int, ...]
    allocation: str | None = None

    def __post_init__(self) -> None:
        if not self.role or any(type(dim) is not int or dim <= 0 for dim in self.shape):
            raise ValueError("weight requires a role and positive dimensions")


@dataclass(frozen=True)
class LayerInstance:
    id: str
    type: str
    geometry: LayerGeometry
    weights: tuple[WeightSpec, ...] = ()
    version: int = 1
    source: str = ""
    op_name: str | None = None

    def __post_init__(self) -> None:
        if not self.id or not self.type:
            raise ValueError("layer instance requires an ID and type")
        if self.version < 1:
            raise ValueError("layer version must be positive")
        roles = [weight.role for weight in self.weights]
        if len(roles) != len(set(roles)):
            raise ValueError(f"{self.id}: duplicate weight roles")


@dataclass(frozen=True)
class LayerL0:
    """One instance's closed-form work, per newly produced token.

    ``mac_output`` is per output position and is not multiplied by the prompt
    length during prefill.  The lm-head uses this bucket; all body layers use
    ``mac_fixed`` and therefore scale with prefill tokens.
    """

    mac_fixed: int = 0
    mac_s_coef: int = 0
    mac_s2_coef: int = 0
    mac_output: int = 0
    params_streamed_per_token: int = 0
    kv_bytes_per_token: int = 0
    state_bytes: int = 0

    def __post_init__(self) -> None:
        for name in ("mac_fixed", "mac_s_coef", "mac_s2_coef", "mac_output",
                     "params_streamed_per_token", "kv_bytes_per_token", "state_bytes"):
            value = getattr(self, name)
            if type(value) is not int or value < 0:
                raise ValueError(f"{name} must be a nonnegative integer")

    def __add__(self, other: "LayerL0") -> "LayerL0":
        if not isinstance(other, LayerL0):
            return NotImplemented
        return LayerL0(
            mac_fixed=self.mac_fixed + other.mac_fixed,
            mac_s_coef=self.mac_s_coef + other.mac_s_coef,
            mac_s2_coef=self.mac_s2_coef + other.mac_s2_coef,
            mac_output=self.mac_output + other.mac_output,
            params_streamed_per_token=(self.params_streamed_per_token
                                        + other.params_streamed_per_token),
            kv_bytes_per_token=self.kv_bytes_per_token + other.kv_bytes_per_token,
            state_bytes=self.state_bytes + other.state_bytes,
        )


@dataclass(frozen=True)
class LayerDefinition:
    name: str
    version: int
    geometry_type: type[LayerGeometry]
    input_roles: tuple[str, ...]
    output_roles: tuple[str, ...]
    weight_roles: tuple[str, ...]
    l0: Callable[[LayerInstance], LayerL0] | None
    optional_weight_roles: tuple[str, ...] = ()
    children: Callable[[LayerInstance], tuple[LayerInstance, ...]] | None = None
    l0_excluded: tuple[str, ...] = ()
    source: str = ""

    def __post_init__(self) -> None:
        if not self.name or "." not in self.name:
            raise ValueError("layer names require a namespace, e.g. generic.linear")
        if self.version < 1:
            raise ValueError("layer version must be positive")
        if not issubclass(self.geometry_type, LayerGeometry):
            raise ValueError("geometry_type must inherit LayerGeometry")
        if len(set(self.input_roles)) != len(self.input_roles):
            raise ValueError("duplicate input roles")
        if len(set(self.output_roles)) != len(self.output_roles):
            raise ValueError("duplicate output roles")
        if len(set(self.weight_roles)) != len(self.weight_roles):
            raise ValueError("duplicate weight roles")
        if len(set(self.optional_weight_roles)) != len(self.optional_weight_roles):
            raise ValueError("duplicate optional weight roles")
        if set(self.weight_roles) & set(self.optional_weight_roles):
            raise ValueError("required and optional weight roles must not overlap")
        if not self.source:
            raise ValueError("layer definitions require a source for provenance")


class LayerRegistry:
    def __init__(self) -> None:
        self._defs: dict[tuple[str, int], LayerDefinition] = {}
        self._aliases: dict[tuple[str, int], tuple[str, int]] = {}

    def register(self, definition: LayerDefinition, *, replace: bool = False) -> None:
        key = (definition.name, definition.version)
        if key in self._defs and not replace:
            raise ValueError(f"layer already registered: {key}")
        self._defs[key] = definition

    def alias(self, alias_name: str, target_name: str, *,
              alias_version: int = 1, target_version: int = 1) -> None:
        # Legacy names predate the namespace rule, so aliases only need to be
        # non-empty; canonical definitions still require a namespace.
        if not alias_name:
            raise ValueError("alias name must not be empty")
        target = (target_name, target_version)
        if target not in self._defs:
            raise KeyError(f"alias target is not registered: {target}")
        key = (alias_name, alias_version)
        if key in self._defs:
            raise ValueError(f"alias collides with a registered layer: {key}")
        self._aliases[key] = target

    def get(self, name: str, version: int = 1) -> LayerDefinition:
        key = self._aliases.get((name, version), (name, version))
        try:
            return self._defs[key]
        except KeyError:
            raise KeyError(f"unregistered layer: {name}@{version}") from None

    def canonical(self, name: str, version: int = 1) -> tuple[str, int]:
        return self._aliases.get((name, version), (name, version))

    def names(self) -> tuple[tuple[str, int], ...]:
        return tuple(sorted((*self._defs, *self._aliases)))


LAYERS = LayerRegistry()


def register_layer(definition: LayerDefinition, *, replace: bool = False) -> LayerDefinition:
    LAYERS.register(definition, replace=replace)
    return definition


def layer_l0(instance: LayerInstance) -> LayerL0:
    definition = LAYERS.get(instance.type, instance.version)
    if definition.l0 is None:
        raise ValueError(f"{instance.id}: no L0 accounting for {instance.type}@{instance.version}")
    return definition.l0(instance)


def sum_l0(instances: tuple[LayerInstance, ...] | list[LayerInstance]) -> LayerL0:
    total = LayerL0()
    for instance in instances:
        total = total + layer_l0(instance)
    return total
