"""Engine-estimator registry.

A new engine kind must be registered in ``common.opspec.register_engine_kind``
for the task/spec pair and topology instance, then here for cost estimation.
"""

from __future__ import annotations

from typing import Callable

from ..common.opspec import ENGINE_SPECS, PrimitiveTask
from ..hw.topology import EngineConfig
from . import cpu_model, dma_model, gemm_model, vpu_model
from .costing.registry import lookup_operator_estimator
from .ledger import EngineCost
from .residency import ExecutionPlan

EngineEstimator = Callable[[PrimitiveTask, EngineConfig, ExecutionPlan], EngineCost]

ENGINE_ESTIMATORS: dict[str, EngineEstimator] = {}


def register_estimator(kind: str, estimator: EngineEstimator, *,
                       replace: bool = False) -> None:
    if kind not in ENGINE_SPECS:
        raise KeyError(f"engine kind is not registered in common.opspec: {kind}")
    if kind in ENGINE_ESTIMATORS and not replace:
        raise ValueError(f"engine estimator already registered: {kind}")
    ENGINE_ESTIMATORS[kind] = estimator


def estimate(task: PrimitiveTask, engine_config: EngineConfig,
             execution_plan: ExecutionPlan) -> EngineCost:
    if engine_config.kind != task.engine_kind:
        raise ValueError("task is assigned to the wrong engine kind")
    operator = lookup_operator_estimator(task)
    if operator is not None:
        return operator(task, engine_config, execution_plan)
    try:
        estimator = ENGINE_ESTIMATORS[task.engine_kind]
    except KeyError:
        raise KeyError(f"no cost estimator for engine kind {task.engine_kind!r}") from None
    return estimator(task, engine_config, execution_plan)


register_estimator("GEMM", gemm_model.estimate)
register_estimator("VPU", vpu_model.estimate)
register_estimator("CPU", cpu_model.estimate)
register_estimator("DMA", dma_model.estimate)

# Importing built-in forms registers operator-form entries.  The engine
# estimators above remain as a compatibility fallback.
from .costing import forms as _forms  # noqa: E402,F401
