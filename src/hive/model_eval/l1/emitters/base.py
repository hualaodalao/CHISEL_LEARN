"""L1 emitter contracts and registry.

Emitters are registered separately from the frontend layer registry so the
frontend never depends on L1 concepts.  An aggregate emitter delegates to its
children; a leaf emitter creates operations and primitive tasks.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Mapping, Protocol

from ...common.opspec import LogicalOp
from ...common.workload import Phase, WorkloadPoint
from ...frontend.layers.base import LAYERS, LayerInstance
from ...frontend.plan import ModelPlan
from ...frontend.spec import NumFormat
from ...hw.geometry import ArrayGeometry
from ..graph_builder import GraphBuilder


class MissingL1Coverage(RuntimeError):
    pass


@dataclass(frozen=True)
class EmitContext:
    plan: ModelPlan
    point: WorkloadPoint
    fmt: NumFormat
    geometry: ArrayGeometry
    variant: str
    builder: GraphBuilder

    @property
    def rows(self) -> int:
        return self.point.batch * (self.point.seq_len
                                   if self.point.phase is Phase.PREFILL else 1)

    @property
    def output_dtype(self) -> str:
        return "INT32" if self.fmt.name.startswith("INT") else "FP32"

    @property
    def block_size(self) -> int:
        return self.builder.block_size

    def op_id(self, instance: LayerInstance) -> str:
        return instance.op_name or instance.id

    def scope(self, instance: LayerInstance) -> str:
        op_id = self.op_id(instance)
        return op_id.rsplit("/", 1)[0] if "/" in op_id else op_id

    def operation(self, instance: LayerInstance, engine: str, kind: str,
                  **values) -> LogicalOp:
        op_id = self.op_id(instance)
        layer, _, name = op_id.rpartition("/")
        if not layer:
            layer, name = op_id, op_id
        if engine in ("VPU", "CPU"):
            elements = values.get("elements", 0)
            values["dram_read_bytes"] = (4 * elements if kind in ("norm", "softmax")
                                         else values.get("read_bytes", 0))
            values["dram_write_bytes"] = values.get("write_bytes", 0)
        return self.builder.add_op(LogicalOp(op_id, layer, name, kind, **values))

    def op(self, instance: LayerInstance) -> LogicalOp:
        return self.builder.op(self.op_id(instance))

    def child(self, parent: LayerInstance, suffix: str) -> LayerInstance:
        definition = LAYERS.get(parent.type, parent.version)
        if definition.children is None:
            raise ValueError(f"{parent.id}: {parent.type} is not an aggregate layer")
        target = f"{parent.id}/{suffix}"
        for child in definition.children(parent):
            if child.id == target:
                return child
        raise KeyError(f"{parent.id}: no child {suffix}")

    def emit(self, instance: LayerInstance,
             inputs: Mapping[str, str]) -> Mapping[str, str]:
        emitter = EMITTERS.get((instance.type, instance.version))
        if emitter is None:
            raise MissingL1Coverage(
                f"{instance.id}: no L1 emitter for {instance.type}@{instance.version}")
        return emitter(self, instance, inputs)


class LayerEmitter(Protocol):
    def __call__(self, ctx: EmitContext, instance: LayerInstance,
                 inputs: Mapping[str, str]) -> Mapping[str, str]: ...


EMITTERS: dict[tuple[str, int], LayerEmitter] = {}


def register_emitter(name: str, version: int, emitter: LayerEmitter, *,
                     replace: bool = False) -> None:
    key = (name, version)
    if key in EMITTERS and not replace:
        raise ValueError(f"emitter already registered: {key}")
    EMITTERS[key] = emitter


def emitter(name: str, version: int = 1):
    def decorate(fn: LayerEmitter) -> LayerEmitter:
        register_emitter(name, version, fn)
        return fn
    return decorate


def covered(instance: LayerInstance) -> bool:
    return (instance.type, instance.version) in EMITTERS


def coverage(plan: ModelPlan) -> dict[str, tuple[str, ...]]:
    missing = {"L0": [], "L1": []}
    for layer in plan.iter_layers():
        definition = LAYERS.get(layer.type, layer.version)
        if definition.l0 is None:
            missing["L0"].append(layer.id)
        if not covered(layer):
            missing["L1"].append(layer.id)
    return {key: tuple(value) for key, value in missing.items()}
