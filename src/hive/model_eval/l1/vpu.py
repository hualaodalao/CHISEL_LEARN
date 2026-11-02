"""Single-core analytic VPU/CPU budgets, not calibrated operator service times."""

from __future__ import annotations

import math

from ..common.opspec import OpSpec
from ..hw.profiles import HwProfile
from . import dram
from .ledger import Charge, Ledger, Origin
from .params import ModelParams


_FP32_REF = (
    "arch.md:975-978: FP32 vector-pass lane-rate fallback; "
    "not measured non-FMA service time (lane widths: arch.md:937-945)"
)
_LOCAL_REF = "arch.md:942-947: single local request port, read/write combined"
_SPECIAL_REF = "AR-HIVE-S022; arch.md:958,975-978"
_VPU_KINDS = {"norm", "rope", "residual", "silu_gate", "softmax", "attention_vpu"}


def cost(op: OpSpec, profile: HwProfile, params: ModelParams) -> Ledger:
    """特殊函数服务独立计账，不以 FMA 峰值代替。"""
    engine = op.engine.lower()
    if engine == "cpu" and op.kind == "sampling":
        return _sampling(op, profile, params)
    if engine != "vpu" or op.kind not in _VPU_KINDS:
        raise ValueError(f"Unsupported VPU/CPU operation: {op.engine}/{op.kind}")

    ledger = Ledger()
    overlap = params.overlap and bool(params.overlap_evidence.strip())
    overlap_class = f"{op.id}:core_local" if overlap else "serial"
    overlap_evidence = f"; overlap scenario: {params.overlap_evidence}" if overlap else ""

    if op.kind in ("norm", "softmax"):
        # Prefill softmax vectors have variable lengths: never use vectors*length.
        cycles = 3 * _ceil_div(op.elements, params.vpu_fp32_lanes)
        ops = 3 * op.elements
        passes = "sum squares, scale, affine" if op.kind == "norm" else "max, exp sum, normalize"
        resource = "vpu_fp32"
        source = "fallback"
        evidence = f"{_FP32_REF}; three arithmetic passes: {passes}; SF service separate"
    elif op.kind == "attention_vpu":
        cycles = _ceil_div(op.mac, params.vpu_fp16_lanes)
        ops = 2 * op.mac
        resource = "vpu_fp16_fma"
        source = "architecture_peak"
        evidence = "arch.md:937-947: FP16 FMA peak, register-resident independent operations only"
        ledger.excluded.extend([
            f"{op.id}: FP16<->FP32 conversion service excluded (AR-HIVE-C035; arch.md:954-956,975-978)",
            f"{op.id}: FP32 accumulation/ reduction constraints excluded; FP16 FMA peak does not "
            "supply mixed-precision accumulation throughput (AR-HIVE-C035; arch.md:954-956,975-978)",
        ])
    else:
        cycles = _ceil_div(op.mac, params.vpu_fp32_lanes)
        ops = op.mac
        resource = "vpu_fp32"
        source = "fallback"
        evidence = f"{_FP32_REF}; {op.kind} arithmetic budget from op.mac; SF service separate"

    ledger.charges.append(Charge(
        resource=resource, instance="core0", origin=Origin(f"{op.id}:arithmetic", "vpu"),
        category="vpu", seconds=_cycle_seconds(cycles, op, profile, ledger),
        cycles=cycles, ops=ops, source=source, overlap_class=overlap_class,
        evidence=evidence + overlap_evidence,
    ))
    _local_charge(op, profile, ledger, engine="vpu", byte_count=op.read_bytes + op.write_bytes,
                  stage="local_read_write", overlap_class=overlap_class,
                  evidence=_LOCAL_REF + overlap_evidence)

    if op.kind == "norm":
        _special(op, profile, ledger, "rsqrt", op.vectors,
                 params.rsqrt_cycles_per_vector, "rsqrt_cycles_per_vector")
        if op.weight_bytes:
            ledger.charges.append(Charge(
                resource="dram_read", instance="system",
                origin=Origin(f"{op.id}:norm_coefficients", "vpu"), category="dram",
                seconds=dram.seconds(op.weight_bytes, profile), bytes=op.weight_bytes,
                evidence="op.weight_bytes: separate norm coefficients DRAM read at profile effective bandwidth",
            ))
    elif op.kind in ("softmax", "silu_gate"):
        _special(op, profile, ledger, "exp", op.elements,
                 params.exp_cycles_per_element, "exp_cycles_per_element")
        # The parameter name is vector-based for softmax; SiLU needs one per element.
        count = op.vectors if op.kind == "softmax" else op.elements
        _special(op, profile, ledger, "reciprocal", count,
                 params.reciprocal_cycles_per_vector, "reciprocal_cycles_per_vector")
    _staging(op, profile, ledger)
    return ledger


def _staging(op: OpSpec, profile: HwProfile, ledger: Ledger) -> None:
    for direction, byte_count in (("read", op.dram_read_bytes), ("write", op.dram_write_bytes)):
        if byte_count:
            ledger.charges.append(Charge(
                resource=f"dram_activation_{direction}", instance="system",
                origin=Origin(f"{op.id}:activation_stage_{direction}", "dma"), category="dram",
                seconds=dram.seconds(byte_count, profile), bytes=byte_count,
                evidence="explicit operator-boundary staging/spill; no inter-operator cache residency",
            ))


def _ceil_div(count: int, lanes: int) -> int:
    return (count + lanes - 1) // lanes


def _cycle_seconds(cycles: float, op: OpSpec, profile: HwProfile, ledger: Ledger) -> float | None:
    value = dram.cycle_seconds(cycles, profile)
    if value is None:
        message = f"{op.id}: VPU seconds excluded: profile {profile.name} freq_hz is unknown"
        if message not in ledger.excluded:
            ledger.excluded.append(message)
    return value


def _local_charge(op: OpSpec, profile: HwProfile, ledger: Ledger, *, engine: str,
                  byte_count: int, stage: str, overlap_class: str = "serial",
                  evidence: str = _LOCAL_REF) -> None:
    if not byte_count:
        return
    value = dram.local_seconds(byte_count, profile)
    if value is None:
        ledger.excluded.append(
            f"{op.id}: {engine.upper()} local transfer seconds excluded: profile {profile.name} "
            "local_iface_bytes_per_s_per_core is unknown"
        )
    ledger.charges.append(Charge(
        resource="local_port", instance="core0", origin=Origin(f"{op.id}:{stage}", engine),
        category=engine, seconds=value, bytes=byte_count, overlap_class=overlap_class,
        evidence=evidence, source="architecture_peak",
    ))


def _special(op: OpSpec, profile: HwProfile, ledger: Ledger, function: str, count: int,
             cycles_per_call: float | None, parameter: str) -> None:
    if cycles_per_call is None:
        ledger.excluded.append(
            f"{op.id}: {function} service for {count} calls excluded: "
            f"{parameter} is unset ({_SPECIAL_REF})"
        )
        return
    cycles = cycles_per_call * count
    ledger.charges.append(Charge(
        resource=f"vpu_{function}", instance="core0", origin=Origin(f"{op.id}:{function}", "vpu"),
        category="vpu", seconds=_cycle_seconds(cycles, op, profile, ledger),
        cycles=cycles, ops=count, source="scenario",
        evidence=f"{_SPECIAL_REF}: explicit {parameter}={cycles_per_call}, {count} calls; serial dependent service",
    ))


def _sampling(op: OpSpec, profile: HwProfile, params: ModelParams) -> Ledger:
    ledger = Ledger()
    missing = [name for name in ("cpu_cycles_per_comparison", "cpu_freq_hz")
               if getattr(params, name) is None]
    if missing:
        ledger.excluded.append(
            f"{op.id}: CPU sampling algorithm/service numeric estimate excluded: "
            f"unset {', '.join(missing)}; full_sort_comparison_budget requires both inputs"
        )
    elif op.length <= 0 or op.vectors <= 0:
        ledger.excluded.append(
            f"{op.id}: CPU full_sort_comparison_budget excluded: positive length (vocab) and vectors (batch) required"
        )
    else:
        # Independent per-vector sort budgets, not a sort of the flattened batch.
        comparisons = op.vectors * math.ceil(op.length * math.log2(max(2, op.length)))
        cycles = comparisons * params.cpu_cycles_per_comparison
        ledger.charges.append(Charge(
            resource="cpu_comparisons", instance="core0", origin=Origin(f"{op.id}:comparisons", "cpu"),
            category="cpu", seconds=cycles / params.cpu_freq_hz, cycles=cycles, ops=comparisons,
            source="scenario", evidence="full_sort_comparison_budget: vectors*ceil(length*log2(max(2,length))); "
            f"cpu_cycles_per_comparison={params.cpu_cycles_per_comparison}, cpu_freq_hz={params.cpu_freq_hz}; "
            "scenario, not a measured sampling algorithm",
        ))
    _local_charge(op, profile, ledger, engine="cpu", byte_count=op.read_bytes,
                  stage="logit_read", evidence=_LOCAL_REF + "; CPU logit reads at supplied per-core local interface rate")
    _staging(op, profile, ledger)
    return ledger
