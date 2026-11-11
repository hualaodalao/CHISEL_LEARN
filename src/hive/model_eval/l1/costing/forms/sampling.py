"""CPU sampling operator-form formula."""
from __future__ import annotations

import math

from ....common.opspec import CpuSpec, PrimitiveTask
from ...ledger import EngineCost
from ..registry import register_operator_estimator
from ..services import (ServiceBuilder as _ServiceBuilder,
                        boundary_read as _boundary_read,
                        boundary_write as _boundary_write,
                        primitive_flow as _primitive_flow)


def estimate(task: PrimitiveTask, engine_config, execution_plan) -> EngineCost:
    if task.engine_kind != "CPU" or not isinstance(task.spec, CpuSpec) or engine_config.kind != "CPU":
        raise ValueError("CPU estimate requires a CPU task/spec/config")
    spec, params = task.spec, engine_config.params
    if spec.kind != "sampling":
        raise ValueError("CPU only supports explicit sampling")
    builder = _ServiceBuilder(task, engine_config, execution_plan)
    comparisons = spec.vectors * math.ceil(spec.length * math.log2(max(2, spec.length)))
    missing = [name for name in ("cpu_cycles_per_comparison", "cpu_freq_hz")
               if getattr(params, name) is None]
    cycles = comparisons * params.cpu_cycles_per_comparison if params.cpu_cycles_per_comparison is not None else 0
    seconds = cycles / params.cpu_freq_hz if not missing else None
    evidence = ("full_sort_comparison_budget: vectors*ceil(length*log2(max(2,length))); "
                f"cpu_cycles_per_comparison={params.cpu_cycles_per_comparison}, cpu_freq_hz={params.cpu_freq_hz}; "
                "independent per-batch vectors, not a flattened sort; scenario, not a measured sampling algorithm")
    if missing:
        evidence += "; unknown " + ", ".join(missing)
    dependencies = _boundary_read(builder, spec)
    start = dependencies[0] if dependencies else None
    local = None
    if spec.read_bytes + spec.write_bytes:
        local = builder.local_phase("local_read_write", spec.read_bytes + spec.write_bytes,
                                    "cpu", dependencies,
                                    evidence="CPU-owned endpoint at its supplied profile local rate; no VPU route")
        dependencies = (local,)
    compute = builder.add("comparisons", seconds, "cpu", dependencies,
                          raw_resource="cpu_comparisons", cycles=cycles, ops=comparisons,
                          source="scenario" if not missing else "unknown", evidence=evidence)
    end = _boundary_write(builder, spec, (compute,))[-1]
    _primitive_flow(builder, spec, start or local or compute, local or compute, end, read_end=compute)
    builder.chunk("comparison_scratch", engine_config.memory_domain, 32, compute, compute)
    return builder.finish({"kind": spec.kind, "comparisons": comparisons, "cycles": cycles,
                           "vectors": spec.vectors, "length": spec.length,
                           "missing_inputs": tuple(missing), "evidence": spec.evidence})


register_operator_estimator("sampling", "CPU", estimate, replace=True)
