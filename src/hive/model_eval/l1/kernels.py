from __future__ import annotations

import math
from dataclasses import asdict

from ..common.opspec import OpSpec
from ..hw.geometry import ArrayGeometry
from ..hw.profiles import HwProfile
from . import dram, residency
from .gemm_geom import GemmShape, format_elem_bytes, format_is_mx, legalize
from .ledger import Charge, Ledger, Origin
from .params import ModelParams
from .windows import count_windows


def _scale_requests(rows: int, passes: int, stride: int, step: int) -> int:
    aligned = 0
    for r in range(4):
        row_count = rows // 4 + int(r < rows % 4)
        for k in range(4):
            pass_count = passes // 4 + int(k < passes % 4)
            if (r * stride + k * step) % 4 == 0:
                aligned += row_count * pass_count
    return 2 * rows * passes - aligned


def gemm_cost(op: OpSpec, profile: HwProfile, fmt_name: str,
              geometry: ArrayGeometry, params: ModelParams) -> tuple[Ledger, dict]:
    charges = []
    metrics = {name: 0 for name in (
        "commands", "windows", "a_beats", "b_beats", "c_rows", "c_beats", "array_cycles",
        "compute_cycles", "load_cycles", "useful_macs", "padded_macs", "a_logical_bytes",
        "b_logical_bytes", "c_logical_bytes", "a_physical_bytes", "b_physical_bytes",
        "c_physical_bytes", "scale_a_logical_bytes", "scale_b_logical_bytes",
        "scale_a_physical_bytes", "scale_b_physical_bytes", "dram_bytes", "weight_unique_bytes",
        "weight_dram_bytes", "weight_reload_bytes", "k_split_accum_bytes")}
    placements = []
    excluded = ["GEMM command setup, prologue/epilogue, drain/cvt/control bubbles are unmodeled",
                "SRAM bank conflicts, DRAM first-byte latency, physical DRAM burst rounding are unmodeled",
                "scale gather request counts are modeled; response latency/serialization stalls are not"]
    for group_index, group in enumerate(op.gemms):
        shape = GemmShape(group.m, group.n, group.k, fmt_name, fmt_name,
                          c_bits=32, block_size=params.block_size)
        commands = legalize(shape, geometry)
        counts = count_windows(shape, commands)
        repeats = group.repeats
        for name, value in asdict(counts).items():
            metrics[name] += value * repeats
        a_elem = float(format_elem_bytes(shape.a_fmt))
        b_elem = float(format_elem_bytes(shape.b_fmt))
        a_mx, b_mx = format_is_mx(shape.a_fmt), format_is_mx(shape.b_fmt)
        n_prog = sum(c.n for c in commands if c.m_offset == 0 and c.k_offset == 0)
        k_prog = sum(c.k for c in commands if c.m_offset == 0 and c.n_offset == 0)
        weight_unique = n_prog * k_prog * (b_elem + (1 / params.block_size if b_mx else 0))
        activation_max = max(c.m * c.k * (a_elem + (1 / params.block_size if a_mx else 0))
                             for c in commands)
        output_tile = max(c.m for c in commands) * geometry.tile_n * 4
        placement = residency.place(profile, params, weight_bytes=weight_unique,
                                    activation_bytes=activation_max,
                                    window_bytes=geometry.tile_n * (geometry.b_bytes_per_beat
                                        + (commands[0].k_per_beat // params.block_size if b_mx else 0)),
                                    output_tile_bytes=output_tile)
        placements.append(asdict(placement))
        a_bytes = b_bytes = c_bytes = sa_bytes = sb_bytes = 0
        sa_requests = sb_requests = a_stage_bytes = 0
        k_split_cells = 0
        for c in commands:
            a_beats = c.n_tiles * c.k_tiles * c.m
            b_beats = c.n_tiles * c.k_tiles * c.tile_n
            a_bytes += a_beats * c.k_per_beat * a_elem
            b_bytes += b_beats * c.k_per_beat * b_elem
            c_bytes += c.n * c.m * 4
            if a_mx:
                step = c.k_per_beat // params.block_size
                sa_bytes += a_beats * step
                sa_requests += c.n_tiles * _scale_requests(c.m, c.k_tiles,
                                                           c.k // params.block_size, step)
            if b_mx:
                step = c.k_per_beat // params.block_size
                sb_bytes += b_beats * step
                sb_requests += _scale_requests(c.n, c.k_tiles,
                                              c.k // params.block_size, step)
            a_stage_bytes += c.m * c.k * (a_elem + (1 / params.block_size if a_mx else 0))
            if c.k_offset:
                k_split_cells += c.m * c.n
        a_dram = a_stage_bytes if placement.activation_resident else a_bytes + sa_bytes
        b_dram = weight_unique if placement.weight_resident else b_bytes + sb_bytes
        a_physical = counts.a_beats * geometry.a_bytes_per_beat
        b_physical = counts.b_beats * geometry.b_bytes_per_beat
        c_physical = counts.c_beats * geometry.c_bytes_per_beat
        metric_values = {
            "a_logical_bytes": a_bytes, "b_logical_bytes": b_bytes, "c_logical_bytes": c_bytes,
            "a_physical_bytes": a_physical, "b_physical_bytes": b_physical,
            "c_physical_bytes": c_physical, "scale_a_logical_bytes": sa_bytes,
            "scale_b_logical_bytes": sb_bytes, "scale_a_physical_bytes": 4 * sa_requests,
            "scale_b_physical_bytes": 4 * sb_requests,
            "weight_unique_bytes": weight_unique if op.kind == "weight_gemm" else 0,
            "weight_dram_bytes": b_dram if op.kind == "weight_gemm" else 0,
            "weight_reload_bytes": max(0, b_dram - weight_unique) if op.kind == "weight_gemm" else 0,
            "dram_bytes": a_dram + b_dram + c_bytes,
            "k_split_accum_bytes": 12 * k_split_cells,
        }
        for name, value in metric_values.items():
            metrics[name] += value * repeats
        prefix = f"{op.id}:{group_index}"
        source = params.overlap_evidence if params.overlap else "workerbee/doc/spec.md:201-251"
        consume_overlap = f"{prefix}:consume_write" if params.overlap else "serial"
        weight_origin = Origin(prefix + ":weight-load", "stream" if not placement.weight_resident else "sram")
        a_origin = Origin(prefix + ":consume", "sram" if placement.activation_resident else "stream")
        c_origin = Origin(prefix + ":write", "dma_c")

        def add(resource, origin, category, seconds, *, byte_count=0, beats=0,
                cycles=0, ops=0, overlap="serial", evidence=source, cost_source="structural"):
            charges.append(Charge(resource=f"{group_index}:{resource}", instance=op.id,
                                  origin=origin, category=category,
                                  seconds=None if seconds is None else seconds * repeats,
                                  bytes=byte_count * repeats, beats=beats * repeats,
                                  cycles=cycles * repeats, ops=ops * repeats,
                                  overlap_class=overlap, evidence=evidence, source=cost_source))

        add("dram.b", Origin(prefix + ":weight-stage", "dram") if placement.weight_resident
            else weight_origin, "dram", dram.seconds(b_dram, profile), byte_count=b_dram)
        add("port.dma_b", weight_origin, "gemm", dram.local_seconds(b_physical, profile),
            byte_count=b_physical, beats=counts.b_beats)
        add("array.loadW", weight_origin, "gemm", dram.cycle_seconds(counts.load_cycles, profile),
            cycles=counts.load_cycles)
        if sb_requests:
            add("port.scale_b", weight_origin, "gemm", dram.cycle_seconds(sb_requests, profile),
                byte_count=4 * sb_requests, beats=sb_requests)
        if placement.activation_resident:
            add("dram.a", Origin(prefix + ":a-stage", "dram"), "dram",
                dram.seconds(a_dram, profile), byte_count=a_dram)
        else:
            add("dram.a", a_origin, "dram", dram.seconds(a_dram, profile),
                byte_count=a_dram, overlap=consume_overlap)
        add("port.dma_a", a_origin, "gemm", dram.local_seconds(a_physical, profile),
            byte_count=a_physical, beats=counts.a_beats, overlap=consume_overlap)
        add("array.consume", a_origin, "gemm", dram.cycle_seconds(counts.compute_cycles, profile),
            cycles=counts.compute_cycles, ops=counts.useful_macs + counts.padded_macs,
            overlap=consume_overlap)
        if sa_requests:
            add("port.scale_a", a_origin, "gemm", dram.cycle_seconds(sa_requests, profile),
                byte_count=4 * sa_requests, beats=sa_requests, overlap=consume_overlap)
        add("port.dma_c", c_origin, "gemm", dram.local_seconds(c_physical, profile),
            byte_count=c_physical, beats=counts.c_beats, overlap=consume_overlap)
        add("dram.c", c_origin, "dram", dram.seconds(c_bytes, profile),
            byte_count=c_bytes, overlap=consume_overlap)
        if k_split_cells:
            add("vpu.k_accum_port", Origin(prefix + ":k-accum-io", "vpu"), "vpu",
                dram.local_seconds(12 * k_split_cells, profile), byte_count=12 * k_split_cells,
                cycles=12 * k_split_cells / geometry.c_bytes_per_beat,
                evidence="AR-HIVE-S018: independent K commands require explicit FP32 accumulation")
            add("vpu.k_accum_add", Origin(prefix + ":k-accum-add", "vpu"), "vpu",
                dram.cycle_seconds(math.ceil(k_split_cells / params.vpu_fp32_lanes), profile),
                cycles=math.ceil(k_split_cells / params.vpu_fp32_lanes), ops=k_split_cells,
                evidence="arch.md:975-978 non-FMA service uncalibrated", cost_source="fallback")
            excluded.append("independent K partial-sum external placement/spill/reload schedule is unverified")
    if fmt_name in ("INT8", "INT4"):
        excluded.append("Integer GEMM outputs INT32; dequantization/conversion to VPU FP32 service is unmodeled")
    if op.kind == "attention_gemm":
        excluded.append("quantized attention format accuracy, FP16 KV conversion/packing and activation quantization service are unmodeled")
    if profile.freq_hz is None:
        excluded.append("profile has no GEMM clock: loadW/consume/scale cycle times are unknown")
    if profile.local_iface_bytes_per_s_per_core is None:
        excluded.append("profile has no local interface rate: GEMM port service time is unknown")
    metrics["issued_macs"] = metrics["useful_macs"] + metrics["padded_macs"]
    metrics["semantic_macs"] = op.mac
    metrics["masked_macs"] = max(0, metrics["useful_macs"] - op.mac)
    metrics["array_utilization"] = (metrics["compute_cycles"] / metrics["array_cycles"]
                                     if metrics["array_cycles"] else 0)
    metrics["placements"] = placements
    return Ledger(charges, residency.coupling(charges), sorted(set(excluded))), metrics


def cost(op: OpSpec, profile: HwProfile, fmt_name: str,
         geometry: ArrayGeometry, params: ModelParams) -> tuple[Ledger, dict]:
    if op.engine == "GEMM":
        return gemm_cost(op, profile, fmt_name, geometry, params)
    if op.engine in ("VPU", "CPU"):
        from .vpu import cost as vector_cost
        ledger = vector_cost(op, profile, params)
        metrics = {"dram_bytes": sum(c.bytes for c in ledger.charges if c.category == "dram"),
                   "semantic_macs": op.mac,
                   "norm_calls": op.vectors if op.kind == "norm" else 0,
                   "softmax_reductions": op.vectors if op.kind == "softmax" else 0}
        return ledger, metrics
    if op.engine == "DMA":
        byte_count = op.read_bytes + op.write_bytes
        origin = Origin(op.id + ":kv", "dma")
        charges = [Charge("dram.kv", op.id, origin, "dram", dram.seconds(byte_count, profile),
                          bytes=byte_count, evidence=op.evidence),
                   Charge("port.kv", op.id, origin, "dram", dram.local_seconds(byte_count, profile),
                          bytes=byte_count, evidence=op.evidence)]
        excluded = ["KV DMA latency and bank conflicts are unmodeled"]
        if profile.local_iface_bytes_per_s_per_core is None:
            excluded.append("profile has no KV local interface service rate")
        return Ledger(charges, residency.coupling(charges), excluded), {"dram_bytes": byte_count,
                                                                     "semantic_macs": 0}
    raise ValueError(f"没有成本函数：{op.engine}")
