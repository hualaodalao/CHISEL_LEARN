from __future__ import annotations

from ..common.opspec import PrimitiveTask
from ..hw.topology import EngineConfig
from . import cpu_model, dma_model, gemm_model, vpu_model
from .ledger import EngineCost
from .residency import ExecutionPlan


_MODELS = {"GEMM": gemm_model, "VPU": vpu_model, "CPU": cpu_model, "DMA": dma_model}


def estimate(task: PrimitiveTask, engine_config: EngineConfig,
             execution_plan: ExecutionPlan) -> EngineCost:
    if engine_config.kind != task.engine_kind:
        raise ValueError("task is assigned to the wrong engine kind")
    return _MODELS[task.engine_kind].estimate(task, engine_config, execution_plan)
