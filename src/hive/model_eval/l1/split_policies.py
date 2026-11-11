"""GEMM command-sequence registries.

The builder and the estimator must consume the same legalize result.  Both go
through ``split_commands``; a custom policy is therefore visible to task-graph
construction and cost estimation without touching either caller.
"""

from __future__ import annotations

from typing import Callable

from ..hw.geometry import ArrayGeometry
from .gemm_model import Command, GemmShape

SplitPolicy = Callable[[GemmShape, ArrayGeometry], tuple[Command, ...]]
SPLIT_POLICIES: dict[str, SplitPolicy] = {}


def register_split_policy(name: str, policy: SplitPolicy, *, replace: bool = False) -> None:
    if not name:
        raise ValueError("split policy requires a name")
    if name in SPLIT_POLICIES and not replace:
        raise ValueError(f"split policy already registered: {name}")
    SPLIT_POLICIES[name] = policy


def split_commands(shape: GemmShape, geometry: ArrayGeometry,
                   policy: str = "default") -> tuple[Command, ...]:
    try:
        implementation = SPLIT_POLICIES[policy]
    except KeyError:
        raise KeyError(f"unregistered GEMM split policy: {policy}") from None
    return implementation(shape, geometry)


def _default(shape: GemmShape, geometry: ArrayGeometry) -> tuple[Command, ...]:
    from .gemm_model import legalize
    return legalize(shape, geometry)


register_split_policy("default", _default)
