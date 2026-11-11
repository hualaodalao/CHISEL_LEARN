"""VPU operator-form formulas; one entry per VpuSpec kind."""
from __future__ import annotations

from ....common.opspec import PrimitiveTask, VpuSpec
from ... import dram
from ..registry import register_operator_estimator
from ..services import (ServiceBuilder as _ServiceBuilder,
                        boundary_read as _boundary_read,
                        boundary_write as _boundary_write,
                        primitive_flow as _primitive_flow)
from ...ledger import EngineCost

_FP32_REF = (
    "arch.md:975-978: FP32 vector-pass lane-rate fallback; "
    "not measured non-FMA service time (lane widths: arch.md:937-945)"
)
_SPECIAL_REF = "AR-HIVE-S022; arch.md:958,975-978"
_CONVERSION_REF = "AR-HIVE-C035; arch.md:954-956,975-978"
_KINDS = {"norm", "rope", "residual", "silu_gate", "softmax", "attention_vpu", "k_accum", "convert"}


def _cycles_seconds(builder, cycles):
    value = dram.cycle_seconds(cycles, builder.config.profile)
    if value is None:
        builder.excluded.append(f"{builder.task.id}: profile freq_hz is unknown")
    return value


def _conversion_cycles(spec, params):
    lanes = params.vpu_conversion_elements_per_cycle
    if lanes is None:
        return None, 0
    units = spec.elements or spec.mac or spec.read_bytes + spec.write_bytes
    return (units + lanes - 1) // lanes, units


def _special(builder, function, count, per_call, parameter, dependencies):
    cycles = count * per_call if per_call is not None else 0
    evidence = (f"{_SPECIAL_REF}; {count} calls; {parameter}="
                f"{per_call if per_call is not None else 'unset'}; serial dependent service")
    value = _cycles_seconds(builder, cycles) if per_call is not None else None
    return builder.add(function, value, "vpu", dependencies,
                       raw_resource=f"vpu_{function}", cycles=cycles, ops=count,
                       source="scenario" if per_call is not None else "unknown", evidence=evidence)


def estimate(task: PrimitiveTask, engine_config, execution_plan) -> EngineCost:
    if task.engine_kind != "VPU" or not isinstance(task.spec, VpuSpec) or engine_config.kind != "VPU":
        raise ValueError("VPU estimate requires a VPU task/spec/config")
    spec, params = task.spec, engine_config.params
    if spec.kind not in _KINDS:
        raise ValueError(f"unsupported VPU kind: {spec.kind}")
    builder = _ServiceBuilder(task, engine_config, execution_plan)
    ingress = _boundary_read(builder, spec)
    cycles, ops = 0, 0
    conversion = False
    accumulation_unknown = False

    if spec.kind == "convert":
        name, raw_resource = "conversion", "vpu_conversion"
        cycles, ops = _conversion_cycles(spec, params)
        seconds = _cycles_seconds(builder, cycles) if cycles is not None else None
        source = "scenario" if cycles is not None else "unknown"
        if cycles is None:
            evidence = (f"{_CONVERSION_REF}: explicit activation quantization/packing/dequantization "
                        f"or dtype conversion {spec.input_dtype}->{spec.output_dtype}; "
                        "shapes/bytes alone do not establish conversion cycles")
        else:
            evidence = (f"{_CONVERSION_REF}: explicit conversion {spec.input_dtype}->"
                        f"{spec.output_dtype}; {ops} units; vpu_conversion_elements_per_cycle="
                        f"{params.vpu_conversion_elements_per_cycle}")
    elif spec.kind == "k_accum" and (spec.input_dtype, spec.output_dtype) != ("FP32", "FP32"):
        name, raw_resource, source = "arithmetic", "vpu_k_accum", "unknown"
        ops = spec.elements or spec.mac
        seconds = None
        evidence = ("AR-HIVE-S018: K partial-sum merge requires explicit supported accumulator service; "
                    f"{spec.input_dtype}->{spec.output_dtype} has no supported merge throughput; no silent FP32 typecast")
        conversion = spec.input_dtype != spec.output_dtype
    elif spec.kind == "attention_vpu":
        name, raw_resource, source = "arithmetic", "vpu_fp16_fma", "architecture_peak"
        cycles = (spec.mac + params.vpu_fp16_lanes - 1) // params.vpu_fp16_lanes
        ops = 2 * spec.mac
        seconds = _cycles_seconds(builder, cycles)
        evidence = "arch.md:937-947: FP16 FMA peak, register-resident independent operations only"
        # The peak does not price input promotion/demotion or FP32 output accumulation.
        conversion = (spec.input_dtype, spec.output_dtype) != ("FP16", "FP16")
        accumulation_unknown = spec.output_dtype == "FP32"
        if spec.input_dtype not in ("FP16", "BF16", "FP32") or spec.output_dtype not in ("FP16", "BF16", "FP32"):
            seconds, cycles, source = None, 0, "unknown"
            evidence += "; integer/quantized attention is not supported by this FP16 peak"
    else:
        name, raw_resource, source = "arithmetic", "vpu_fp32", "fallback"
        if spec.kind in ("norm", "softmax"):
            cycles = 3 * ((spec.elements + params.vpu_fp32_lanes - 1) // params.vpu_fp32_lanes)
            ops = 3 * spec.elements
            passes = "sum squares, scale, affine" if spec.kind == "norm" else "max, exp sum, normalize"
            evidence = f"{_FP32_REF}; three arithmetic passes: {passes}; effective elements, SF service separate"
        else:
            ops = (spec.elements or spec.mac) if spec.kind == "k_accum" else spec.mac
            cycles = (ops + params.vpu_fp32_lanes - 1) // params.vpu_fp32_lanes
            evidence = f"{_FP32_REF}; {spec.kind} arithmetic budget; SF service separate"
        seconds = _cycles_seconds(builder, cycles)
        conversion = (spec.input_dtype, spec.output_dtype) != ("FP32", "FP32")
        if spec.input_dtype not in ("FP16", "BF16", "FP32") or spec.output_dtype not in ("FP16", "BF16", "FP32"):
            seconds, cycles, source = None, 0, "unknown"
            evidence += "; non-floating arithmetic service unsupported; no silent FP32 typecast"

    local_bytes = spec.read_bytes + spec.write_bytes
    overlap = (spec.kind != "convert" and local_bytes > 0 and params.overlap
               and bool(params.overlap_evidence.strip())
               and builder.engine != builder.local_resource())
    overlap_class = f"{task.id}:core_local" if overlap else "serial"
    if overlap:
        evidence += f"; overlap scenario: {params.overlap_evidence}; different physical resources"
    arithmetic = builder.add(name, seconds, "vpu", ingress, raw_resource=raw_resource,
                             cycles=cycles, ops=ops, source=source, evidence=evidence,
                             overlap_class=overlap_class)
    local = None
    dependencies = (arithmetic,)
    if local_bytes:
        local = builder.local_phase("local_read_write", local_bytes, "vpu",
                                    ingress if overlap else dependencies,
                                    overlap_class=overlap_class,
                                    evidence=f"overlap scenario: {params.overlap_evidence}" if overlap else "")
        dependencies = (arithmetic, local) if overlap else (local,)

    if spec.kind == "norm":
        dependencies = (_special(builder, "rsqrt", spec.vectors, params.rsqrt_cycles_per_vector,
                                 "rsqrt_cycles_per_vector", dependencies),)
    elif spec.kind in ("softmax", "silu_gate"):
        dependencies = (_special(builder, "exp", spec.elements, params.exp_cycles_per_element,
                                 "exp_cycles_per_element", dependencies),)
        count = spec.vectors if spec.kind == "softmax" else spec.elements
        dependencies = (_special(builder, "reciprocal", count, params.reciprocal_cycles_per_vector,
                                 "reciprocal_cycles_per_vector", dependencies),)
    if conversion:
        conv_cycles, conv_units = _conversion_cycles(spec, params)
        conv_seconds = (_cycles_seconds(builder, conv_cycles)
                        if conv_cycles is not None else None)
        conv_source = "scenario" if conv_cycles is not None else "unknown"
        if conv_cycles is None:
            conv_evidence = (f"{_CONVERSION_REF}: FP16<->FP32 or {spec.input_dtype}->"
                             f"{spec.output_dtype} conversion service unknown; no implicit typecast")
        else:
            conv_evidence = (f"{_CONVERSION_REF}: FP16<->FP32 or {spec.input_dtype}->"
                             f"{spec.output_dtype}; {conv_units} units; "
                             f"vpu_conversion_elements_per_cycle="
                             f"{params.vpu_conversion_elements_per_cycle}")
        dependencies = (builder.add(
            "conversion", conv_seconds, "vpu", dependencies, raw_resource="vpu_conversion",
            cycles=conv_cycles or 0, ops=conv_units, source=conv_source,
            evidence=conv_evidence),)
    if accumulation_unknown:
        lanes = params.vpu_fp32_accum_elements_per_cycle
        units = spec.mac
        if lanes is not None:
            acc_cycles = (units + lanes - 1) // lanes
            acc_seconds = _cycles_seconds(builder, acc_cycles)
            acc_source = "scenario"
            acc_evidence = (f"{_CONVERSION_REF}: FP32 accumulation/reduction; {units} elements; "
                            f"vpu_fp32_accum_elements_per_cycle={lanes}")
        else:
            acc_cycles, acc_seconds, acc_source = 0, None, "unknown"
            acc_evidence = (f"{_CONVERSION_REF}: FP32 accumulation/reduction constraints; "
                            "FP16 FMA peak does not supply mixed-precision accumulation throughput")
        dependencies = (builder.add(
            "fp32_accumulation", acc_seconds, "vpu", dependencies,
            raw_resource="vpu_fp32_accumulation", cycles=acc_cycles, ops=units,
            source=acc_source, evidence=acc_evidence),)
    if len(dependencies) > 1:
        # One release endpoint must join all outstanding scratch users.
        dependencies = (builder.add(
            "compute_complete", 0.0, "vpu", dependencies, raw_resource="vpu_dependency_join",
            evidence="zero-service dependency join; scratch held through arithmetic and local I/O"),)
    compute_end = dependencies[-1]
    end = _boundary_write(builder, spec, dependencies)[-1]
    _primitive_flow(builder, spec, ingress[0] if ingress else arithmetic, local or arithmetic, end,
                    read_end=compute_end, chunk_bytes=max(32, params.vpu_fp32_lanes * 4), double_buffer=overlap)
    builder.chunk("arithmetic_scratch", engine_config.memory_domain,
                  params.vpu_fp32_lanes * 4, arithmetic, compute_end,
                  chunk_bytes=params.vpu_fp32_lanes * 4)
    return builder.finish({"kind": spec.kind, "cycles": cycles, "ops": ops,
                           "elements": spec.elements, "vectors": spec.vectors,
                           "overlap": overlap, "input_dtype": spec.input_dtype,
                           "output_dtype": spec.output_dtype, "evidence": spec.evidence})


for _kind in _KINDS:
    register_operator_estimator(_kind, "VPU", estimate, replace=True)
