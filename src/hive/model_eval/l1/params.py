from __future__ import annotations

import math
from dataclasses import asdict, dataclass


@dataclass(frozen=True)
class ModelParams:
    weight_policy: str = "stream"
    overlap: bool = False
    overlap_evidence: str = ""
    sync_us: float | None = None
    exp_cycles_per_element: float | None = None
    reciprocal_cycles_per_vector: float | None = None
    rsqrt_cycles_per_vector: float | None = None
    cpu_cycles_per_comparison: float | None = None
    cpu_freq_hz: float | None = None
    vpu_fp32_lanes: int = 8
    vpu_fp16_lanes: int = 16
    vpu_conversion_elements_per_cycle: int | None = None
    vpu_fp32_accum_elements_per_cycle: int | None = None
    block_size: int = 32

    def __post_init__(self) -> None:
        if self.weight_policy not in ("stream", "resident"):
            raise ValueError("weight_policy 必须为 stream 或 resident")
        if self.overlap and not self.overlap_evidence.strip():
            raise ValueError("重叠必须提供明确的双缓冲依据")
        for name in ("sync_us", "exp_cycles_per_element", "reciprocal_cycles_per_vector",
                     "rsqrt_cycles_per_vector", "cpu_cycles_per_comparison", "cpu_freq_hz"):
            value = getattr(self, name)
            if value is not None and (not math.isfinite(value) or value < 0):
                raise ValueError(f"{name} 必须为有限非负数")
        if self.cpu_freq_hz == 0:
            raise ValueError("CPU 时钟必须为正")
        if self.vpu_fp32_lanes < 1 or self.vpu_fp16_lanes < 1:
            raise ValueError("VPU lane 数必须为正")
        for name in ("vpu_conversion_elements_per_cycle",
                     "vpu_fp32_accum_elements_per_cycle"):
            value = getattr(self, name)
            if value is not None and (type(value) is not int or value < 1):
                raise ValueError(f"{name} 必须为正整数或省略")
        if self.block_size not in (16, 32):
            raise ValueError("MX block_size 仅支持 16/32")

    def named_inputs(self) -> dict:
        inputs = asdict(self)
        inputs["numeric_source"] = "exact element bytes + logical scale bytes"
        inputs["service_assumptions"] = {
            "vpu_fp32_lanes": {"source": "architecture_peak", "ref": "arch.md:937-945"},
            "vpu_fp16_lanes": {"source": "architecture_peak", "ref": "arch.md:937-945"},
            "vpu_non_fma": {"source": "fallback", "ref": "arch.md:975-978",
                            "value": "vector passes at lane rate; not measured service time"},
            "special_functions": {"source": "scenario" if any(
                value is not None for value in (self.exp_cycles_per_element,
                self.reciprocal_cycles_per_vector, self.rsqrt_cycles_per_vector)) else "excluded",
                "ref": "AR-HIVE-S022"},
            "vpu_conversion": {"source": "scenario" if self.vpu_conversion_elements_per_cycle
                               is not None else "excluded", "unit": "elements/cycle"},
            "vpu_fp32_accumulation": {"source": "scenario" if self.vpu_fp32_accum_elements_per_cycle
                                      is not None else "excluded", "unit": "elements/cycle"},
            "cpu_sampling": {"source": "scenario" if self.cpu_cycles_per_comparison is not None
                             else "excluded", "algorithm": "full_sort_comparison_budget"},
            "handoff": {"source": "scenario" if self.sync_us is not None else "unknown",
                        "value_us": self.sync_us},
        }
        return inputs
