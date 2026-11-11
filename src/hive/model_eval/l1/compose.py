"""Compose a ModelPlan into a TaskGraph by recursive emitter dispatch."""

from __future__ import annotations

from ..common.opspec import TaskGraph
from ..common.workload import WorkloadPoint
from ..frontend.plan import ModelPlan
from ..frontend.spec import NumFormat
from ..hw.geometry import ArrayGeometry
from .emitters import EmitContext
from .graph_builder import GraphBuilder


def _hidden_width(plan: ModelPlan) -> int:
    geometry = plan.layers[0].geometry
    for attribute in ("hidden", "width"):
        value = getattr(geometry, attribute, None)
        if value is not None:
            return value
    raise ValueError(f"{plan.name}: cannot infer hidden width from {type(geometry).__name__}")


def lower_plan(plan: ModelPlan, fmt: NumFormat, point: WorkloadPoint, *,
               attention: str = "vpu", geometry: ArrayGeometry | None = None,
               block_size: int = 32) -> TaskGraph:
    if geometry is None:
        from ..hw.geometry import load_array_geometry
        geometry = load_array_geometry()
    plan.validate()
    builder = GraphBuilder(block_size)
    ctx = EmitContext(plan, point, fmt, geometry, attention, builder)
    current = builder.tensor("input:hidden", (ctx.rows, _hidden_width(plan)),
                             "FP32", external=True)
    inputs = {"hidden": current}
    for layer in plan.layers:
        outputs = ctx.emit(layer, inputs)
        inputs = {**inputs, **outputs}
    graph = TaskGraph(tuple(builder.operations), tuple(builder.tasks),
                      tuple(builder.tensors.values()),
                      "ModelPlan emitter composition; external DRAM intermediates; "
                      "no implicit SRAM cache")
    graph.validate()
    return graph
