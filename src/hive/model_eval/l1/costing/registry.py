"""Operator-form cost registry and a minimal scalar EngineCost helper.

The registry is keyed by (spec kind, engine kind), so an operator form remains
the authoring unit while the engine class stays a deployment/resource tag.
"""
from __future__ import annotations

import math
from typing import Callable

from ...common.opspec import PrimitiveTask
from ..ledger import (BufferRequest, Charge, EngineCost, Ledger, Origin, Phase,
                      ResourceClaim, Traffic)

OperatorEstimator = Callable[[PrimitiveTask, object, object], EngineCost]
OPERATOR_ESTIMATORS: dict[tuple[str, str], OperatorEstimator] = {}

_CATEGORY = {"GEMM": "gemm", "VPU": "vpu", "CPU": "cpu", "DMA": "handoff"}


def task_spec_kind(task: PrimitiveTask) -> str:
    kind = getattr(task.spec, "kind", None)
    if kind:
        return str(kind)
    return "transfer" if task.engine_kind == "DMA" else task.engine_kind.lower()


def register_operator_estimator(spec_kind: str, engine_kind: str,
                                estimator: OperatorEstimator, *,
                                replace: bool = False) -> None:
    if not spec_kind or not engine_kind:
        raise ValueError("operator and engine kinds must be named")
    key = (spec_kind, engine_kind)
    if key in OPERATOR_ESTIMATORS and not replace:
        raise ValueError(f"operator estimator already registered: {key}")
    OPERATOR_ESTIMATORS[key] = estimator


def lookup_operator_estimator(task: PrimitiveTask) -> OperatorEstimator | None:
    return OPERATOR_ESTIMATORS.get((task_spec_kind(task), task.engine_kind))


def list_operator_estimators() -> tuple[tuple[str, str], ...]:
    return tuple(sorted(OPERATOR_ESTIMATORS))


def simple_cost(task: PrimitiveTask, engine_config, seconds: float | None, *,
                resource: str | None = None, dram_read_bytes: int = 0,
                dram_write_bytes: int = 0, local_bytes: int = 0,
                footprint_bytes: int = 0, alignment: int = 32,
                unknown: tuple[str, ...] = (), evidence: str = "",
                source: str = "structural", metrics: dict | None = None,
                category: str | None = None) -> EngineCost:
    """Build a one-phase EngineCost for a coarse operator formula.

    ``seconds`` already prices the operator; DRAM/local byte counts are bookkeeping
    for shared-bandwidth and report views, not extra serial phases.
    """
    if seconds is not None and (not math.isfinite(seconds) or seconds < 0):
        raise ValueError("operator seconds must be finite and nonnegative")
    for name, value in (("dram_read_bytes", dram_read_bytes),
                        ("dram_write_bytes", dram_write_bytes),
                        ("local_bytes", local_bytes),
                        ("footprint_bytes", footprint_bytes)):
        if type(value) is not int or value < 0:
            raise ValueError(f"{name} must be a nonnegative integer")
    if type(alignment) is not int or alignment < 1:
        raise ValueError("alignment must be a positive integer")
    category = category or _CATEGORY.get(task.engine_kind, "handoff")
    phase_id = f"{task.id}:execute"
    excluded = list(unknown)
    if seconds is None and not excluded:
        excluded.append(f"{phase_id}: unknown service: {evidence or 'scalar cost has no duration'}")
    charges = [Charge(resource or "engine", engine_config.id, Origin(phase_id, task.id),
                      category, seconds, bytes=dram_read_bytes + dram_write_bytes + local_bytes,
                      evidence=evidence, source=source)]
    phases = [Phase(phase_id, seconds, category, (),
                    (ResourceClaim("engine"),), source=source, evidence=evidence)]
    traffic = []
    tensor_in = task.inputs[0].tensor_id if len(task.inputs) == 1 else None
    tensor_out = task.outputs[0].tensor_id if len(task.outputs) == 1 else None
    if dram_read_bytes:
        traffic.append(Traffic(f"{task.id}:read", task.id, tensor_in, "dram", "dram",
                               "read", dram_read_bytes, dram_read_bytes,
                               segment="scalar-cost", evidence=evidence))
    if dram_write_bytes:
        traffic.append(Traffic(f"{task.id}:write", task.id, tensor_out, "dram", "dram",
                               "write", dram_write_bytes, dram_write_bytes,
                               segment="scalar-cost", evidence=evidence))
    if local_bytes:
        traffic.append(Traffic(f"{task.id}:local", task.id, tensor_in, "local",
                               engine_config.memory_domain, "read", local_bytes, local_bytes,
                               segment="scalar-cost", evidence=evidence))
    buffers = []
    if footprint_bytes:
        buffers.append(BufferRequest(f"{task.id}:scratch", engine_config.memory_domain,
                                     footprint_bytes, alignment, phase_id, phase_id,
                                     tensor_in))
    lease = (resource,) if resource and resource != "engine" else ()
    counts = {"dram_read_bytes": dram_read_bytes, "dram_write_bytes": dram_write_bytes,
              "local_bytes": local_bytes, "footprint_bytes": footprint_bytes}
    counts.update(metrics or {})
    ledger = Ledger(charges=charges, excluded=sorted(set(excluded)))
    return EngineCost(task.id, engine_config.id, tuple(phases), lease, tuple(traffic),
                      tuple(buffers), counts, ledger, tuple(sorted(set(excluded))))
