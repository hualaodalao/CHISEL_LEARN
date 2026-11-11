"""Neutral model registry: model names map to ModelPlan builders."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Callable

from .plan import ModelPlan


@dataclass(frozen=True)
class ModelDefinition:
    name: str
    version: int
    build: Callable[[Any], ModelPlan]
    source: str

    def __post_init__(self) -> None:
        if not self.name or self.version < 1:
            raise ValueError("model definition requires a name and positive version")
        if not self.source:
            raise ValueError("model definition requires a source for provenance")


MODELS: dict[tuple[str, int], ModelDefinition] = {}


def register_model(name: str, build: Callable[[Any], ModelPlan], *,
                   version: int = 1, source: str) -> None:
    key = (name, version)
    if key in MODELS:
        raise ValueError(f"model already registered: {key}")
    MODELS[key] = ModelDefinition(name, version, build, source)


def get_model(name: str, *, version: int = 1, config: Any = None) -> ModelPlan:
    try:
        definition = MODELS[(name, version)]
    except KeyError:
        raise KeyError(f"unregistered model: {name}@{version}; "
                       f"available {sorted(MODELS)}") from None
    return definition.build(config)


def list_models() -> tuple[tuple[str, int], ...]:
    return tuple(sorted(MODELS))
