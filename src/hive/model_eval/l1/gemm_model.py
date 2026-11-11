from __future__ import annotations

import math
from collections.abc import Iterable, Iterator
from dataclasses import asdict, dataclass
from fractions import Fraction
from itertools import combinations
from typing import TYPE_CHECKING

from ..common.opspec import GemmSpec, PrimitiveTask
from ..hw.geometry import ArrayGeometry
from .ledger import BufferRequest, Charge, EngineCost, Ledger, Origin, Phase, ResourceClaim, Traffic

if TYPE_CHECKING:
    from ..hw.profiles import HwProfile


_FORMAT_NAMES = {
    "INT8": "INT8", "INT4": "INT4", "BF16": "BF16",
    "FP8": "FP8", "FP8E4M3": "FP8E4M3", "FP8E5M2": "FP8E5M2",
    "MXFP8": "MXFP8", "MXE4M3": "MXE4M3", "MXE5M2": "MXE5M2",
    "MXFP8E4M3": "MXE4M3", "MXFP8E5M2": "MXE5M2",
    "MXFP4": "MXFP4", "MXE2M1": "MXE2M1", "MXFP4E2M1": "MXE2M1",
}
_FORMAT_FAMILIES = {
    "FP8E4M3": "FP8", "FP8E5M2": "FP8",
    "MXE4M3": "MXFP8", "MXE5M2": "MXFP8", "MXE2M1": "MXFP4",
}
_ELEM_BYTES = {
    "INT8": Fraction(1), "INT4": Fraction(1, 2), "FP8": Fraction(1),
    "MXFP8": Fraction(1), "MXFP4": Fraction(1, 2), "BF16": Fraction(2),
}
_FORWARD_W4A8 = {("INT8", "INT4"), ("MXFP8", "MXFP4")}


def _format_name(name: str) -> str:
    if not isinstance(name, str):
        raise ValueError(f"Unknown input format: {name!r}")
    try:
        return _FORMAT_NAMES[name.upper()]
    except KeyError:
        raise ValueError(f"Unknown input format: {name!r}") from None


def _format_family(name: str) -> str:
    normalized = _format_name(name)
    return _FORMAT_FAMILIES.get(normalized, normalized)


def format_elem_bytes(name: str) -> Fraction:
    return _ELEM_BYTES[_format_family(name)]


def format_is_mx(name: str) -> bool:
    return _format_family(name) in ("MXFP8", "MXFP4")


def _positive_integer(name: str, value: int) -> None:
    if isinstance(value, bool) or not isinstance(value, int) or value <= 0:
        raise ValueError(f"{name} must be a positive integer, got {value!r}")


@dataclass(frozen=True)
class GemmShape:
    m: int
    n: int
    k: int
    a_fmt: str = "MXFP8"
    b_fmt: str = "MXFP8"
    c_bits: int = 32
    block_size: int = 32

    def __post_init__(self) -> None:
        for name in ("m", "n", "k"):
            _positive_integer(name, getattr(self, name))
        _positive_integer("c_bits", self.c_bits)
        if self.c_bits not in (4, 8, 16, 32):
            raise ValueError(f"c_bits must be 4, 8, 16 or 32, got {self.c_bits!r}")
        _positive_integer("block_size", self.block_size)


@dataclass(frozen=True)
class Command:
    m_offset: int
    n_offset: int
    k_offset: int
    m: int
    n: int
    k: int
    n_tiles: int
    k_tiles: int
    k_per_beat: int
    tile_n: int
    a_fmt: str
    b_fmt: str
    c_bits: int
    block_size: int
    final_k: bool
    c_bytes_per_beat: int


def _supported(name: str, supported: tuple[str, ...]) -> bool:
    normalized = _format_name(name)
    family = _format_family(name)
    for candidate in supported:
        try:
            candidate_name = _format_name(candidate)
        except ValueError:
            continue
        if normalized == candidate_name or candidate_name == family:
            return True
        if normalized == family and _format_family(candidate) == family:
            return True
    return False


def _round_up(value: int, granule: int) -> int:
    return (value + granule - 1) // granule * granule


def legalize(shape: GemmShape, geometry: ArrayGeometry) -> tuple[Command, ...]:
    a_family = _format_family(shape.a_fmt)
    b_family = _format_family(shape.b_fmt)
    pair = (a_family, b_family)
    if a_family != b_family and pair not in _FORWARD_W4A8:
        raise ValueError(f"Unsupported A/B format combination: {shape.a_fmt} x {shape.b_fmt}")
    for name in (shape.a_fmt, shape.b_fmt):
        if not _supported(name, geometry.supported_fmts):
            raise ValueError(f"Input format {name!r} is not in supported_fmts")
    _positive_integer("tile_n", geometry.tile_n)
    _positive_integer("max_m", geometry.max_m)
    _positive_integer("mnk_width", geometry.mnk_width)
    _positive_integer("c_dma_ext_w", geometry.c_dma_ext_w)
    if geometry.c_dma_ext_w % 8:
        raise ValueError("C DMA width must be an integral number of bytes")
    p_a = int(Fraction(2) / format_elem_bytes(shape.a_fmt))
    k_per_beat = geometry.tile_n * p_a
    granule = k_per_beat
    if format_is_mx(shape.a_fmt):
        if shape.block_size not in (16, 32):
            raise ValueError("MX block_size must be 16 or 32")
        if shape.block_size > k_per_beat:
            raise ValueError("MX block_size cannot exceed the effective K window")
        granule = max(granule, shape.block_size)
    if pair in _FORWARD_W4A8:
        # B stretch follows A's 2T consumption but requires complete 4T groups.
        granule = max(granule, 4 * geometry.tile_n)
    max_dimension = (1 << geometry.mnk_width) - 1
    max_m = min(geometry.max_m, max_dimension)
    max_n = max_dimension // geometry.tile_n * geometry.tile_n
    max_k = max_dimension // granule * granule
    if max_n == 0 or max_k == 0:
        raise ValueError("mnk_width cannot represent an aligned N/K command")
    padded_n = _round_up(shape.n, geometry.tile_n)
    padded_k = _round_up(shape.k, granule)
    commands = []
    for m_offset in range(0, shape.m, max_m):
        m = min(max_m, shape.m - m_offset)
        for n_offset in range(0, padded_n, max_n):
            n = min(max_n, padded_n - n_offset)
            for k_offset in range(0, padded_k, max_k):
                k = min(max_k, padded_k - k_offset)
                commands.append(Command(
                    m_offset, n_offset, k_offset, m, n, k, n // geometry.tile_n,
                    k // k_per_beat, k_per_beat, geometry.tile_n, shape.a_fmt,
                    shape.b_fmt, shape.c_bits, shape.block_size,
                    k_offset + k == padded_k, geometry.c_bytes_per_beat,
                ))
    return tuple(commands)


@dataclass(frozen=True)
class Window:
    command_index: int
    n_tile: int
    k_pass: int
    load_beats: int
    consume_beats: int
    c_rows: int


@dataclass(frozen=True)
class WindowCounts:
    commands: int
    windows: int
    a_beats: int
    b_beats: int
    c_rows: int
    c_beats: int
    array_cycles: int
    useful_macs: int
    padded_macs: int
    compute_cycles: int
    load_cycles: int

    @property
    def utilization(self) -> float:
        return self.compute_cycles / self.array_cycles if self.array_cycles else 0.0


def iter_windows(commands: Iterable[Command]) -> Iterator[Window]:
    for index, command in enumerate(commands):
        for n_tile in range(command.n_tiles):
            for k_pass in range(command.k_tiles):
                yield Window(index, n_tile, k_pass, command.tile_n, command.m,
                             command.m if k_pass == command.k_tiles - 1 else 0)


def count_windows(shape: GemmShape, commands: Iterable[Command]) -> WindowCounts:
    command_count = window_count = a_beats = b_beats = c_rows = c_beats = 0
    issued_macs = 0
    for command in commands:
        command_count += 1
        n_windows = command.n_tiles * command.k_tiles
        window_count += n_windows
        a_beats += n_windows * command.m
        b_beats += n_windows * command.tile_n
        output_rows = command.n_tiles * command.m
        c_rows += output_rows
        bits_per_beat = 8 * command.c_bytes_per_beat
        beats_per_row = max(1, (command.tile_n * command.c_bits + bits_per_beat - 1)
                            // bits_per_beat)
        c_beats += output_rows * beats_per_row
        issued_macs += command.m * command.n * command.k
    useful_macs = shape.m * shape.n * shape.k
    return WindowCounts(command_count, window_count, a_beats, b_beats, c_rows,
                        c_beats, b_beats + a_beats, useful_macs,
                        issued_macs - useful_macs, a_beats, b_beats)


def _scale_requests(rows: int, passes: int, stride: int, step: int, offset: int = 0,
                    *, start: int = 0) -> int:
    aligned = 0
    for r in range(4):
        row_count = rows // 4 + int(r < rows % 4)
        for k in range(4):
            pass_count = passes // 4 + int(k < passes % 4)
            if (start + r * stride + (k + offset) * step) % 4 == 0:
                aligned += row_count * pass_count
    return 2 * rows * passes - aligned


def _cycle_seconds(cycles: int, profile: HwProfile) -> float | None:
    return cycles / profile.freq_hz if profile.freq_hz is not None else None


def _local_seconds(byte_count: int, profile: HwProfile) -> float | None:
    rate = profile.local_iface_bytes_per_s_per_core
    return byte_count / rate if rate is not None else None


def estimate(task: PrimitiveTask, engine_config, execution_plan) -> EngineCost:
    if task.engine_kind != "GEMM" or not isinstance(task.spec, GemmSpec):
        raise ValueError("GEMM estimator requires a GEMM primitive")
    if engine_config.kind != "GEMM":
        raise ValueError("GEMM estimator requires a GEMM engine instance")
    spec = task.spec
    integer = _format_family(spec.a_fmt) in ("INT8", "INT4")
    if spec.c_dtype != ("INT32" if integer else "FP32"):
        raise ValueError("GEMM accumulation dtype does not match input formats")
    if task.allowed_instances and engine_config.id not in task.allowed_instances:
        raise ValueError("GEMM instance is not allowed for task")
    weight_policy = execution_plan.weight_policy
    activation_policy = execution_plan.activation_policy
    alignment = execution_plan.alignment
    if weight_policy not in ("stream", "resident") or activation_policy not in ("stream", "stage"):
        raise ValueError("unsupported GEMM execution policy")
    _positive_integer("alignment", alignment)
    profile, geometry, params = engine_config.profile, engine_config.geometry, engine_config.params
    shape = GemmShape(spec.m, spec.n, spec.k, spec.a_fmt, spec.b_fmt, 32, spec.block_size)
    from .split_policies import split_commands
    commands = split_commands(shape, geometry, spec.split_policy)
    counts = count_windows(shape, commands)
    a_elem, b_elem = format_elem_bytes(spec.a_fmt), format_elem_bytes(spec.b_fmt)
    a_mx, b_mx = format_is_mx(spec.a_fmt), format_is_mx(spec.b_fmt)
    resident, staged, overlap = weight_policy == "resident", activation_policy == "stage", params.overlap
    if overlap and not params.overlap_evidence.strip():
        raise ValueError("consume/write overlap requires explicit hardware evidence")
    n_prog = sum(c.n for c in commands if c.m_offset == 0 and c.k_offset == 0)
    k_prog = sum(c.k for c in commands if c.m_offset == 0 and c.n_offset == 0)
    for storage, logical, required in (
            (spec.a_storage_shape, (spec.m, spec.k), (spec.m, k_prog)),
            (spec.b_storage_shape, (spec.k, spec.n), (k_prog, n_prog))):
        if (storage or logical) != required:
            raise ValueError("GEMM padded operands require explicit zero-padded backing storage and producer")
    weight_unique = int(n_prog * k_prog * b_elem) + (n_prog * k_prog // spec.block_size if b_mx else 0)
    metrics = asdict(counts)
    for name in (
        "a_logical_bytes", "b_logical_bytes", "c_logical_bytes", "a_physical_bytes",
        "b_physical_bytes", "c_physical_bytes", "scale_a_logical_bytes", "scale_b_logical_bytes",
        "scale_a_physical_bytes", "scale_b_physical_bytes", "dram_bytes", "k_split_cells",
    ):
        metrics[name] = 0
    excluded = [
        "GEMM command setup, prologue/epilogue, drain/cvt/control bubbles are unmodeled",
        "SRAM bank conflicts, DRAM first-byte latency, physical DRAM burst rounding are unmodeled",
        "scale gather request counts are modeled; response latency/serialization stalls are not",
        "command phase budgets aggregate repeated windows without materializing their interleaving",
    ]
    if profile.freq_hz is None:
        excluded.append("profile has no GEMM clock: loadW/consume/scale cycle times are unknown")
    if profile.local_iface_bytes_per_s_per_core is None:
        excluded.append("profile has no local interface rate: GEMM port service time is unknown")
    if integer:
        excluded.append("INT32 to FP32 conversion must be an explicit downstream primitive")
    if profile.sram_bytes_per_core is None:
        excluded.append("local scratch capacity is unknown")
    ledger = Ledger(excluded=excluded)
    phases: list[Phase] = []
    traffic: list[Traffic] = []
    buffers: list[BufferRequest] = []
    previous: tuple[str, ...] = ()
    tensor_ids = {
        "a": task.inputs[0].tensor_id if task.inputs else None,
        "b": task.inputs[1].tensor_id if len(task.inputs) > 1 else None,
        "c": task.outputs[0].tensor_id if task.outputs else None,
    }
    resource_names = {
        "dram": "dram", "port.dma_a": "port.local", "port.dma_b": "port.local",
        "port.dma_c": "port.local", "port.stage_a": "port.local", "port.stage_b": "port.local",
        "port.scale_a": "port.scale_a", "port.scale_b": "port.scale_b",
        "array.loadW": "engine", "array.consume": "engine",
    }

    def physical(resource: str) -> str:
        name = "dram" if resource.startswith("dram.") else resource_names[resource]
        if name not in engine_config.resources:
            raise ValueError(f"GEMM engine has no registered resource {name!r}")
        return engine_config.resolve(name)

    engine_resource = engine_config.resolve("engine")
    if "engine" not in engine_config.resources:
        raise ValueError("GEMM engine has no registered engine resource")

    def charge(resource, origin, seconds, *, byte_count=0, beats=0, cycles=0, ops=0, overlap_class="serial"):
        return Charge(resource, engine_config.id, origin,
                      "dram" if resource.startswith("dram.") else "gemm", seconds,
                      beats=beats, bytes=byte_count, cycles=cycles, ops=ops,
                      overlap_class=overlap_class,
                      evidence=params.overlap_evidence if overlap_class != "serial" else geometry.source_path)

    def stream(resource, segment, tensor, logical, physical_bytes, beats=0):
        if not logical and not physical_bytes:
            return
        interface = physical(resource)
        traffic.append(Traffic(
            f"{task.id}:{segment}:{tensor}", task.id, tensor_ids[tensor], interface,
            "dram" if resource.startswith("dram.") else engine_config.memory_domain,
            "write" if tensor == "c" or resource.startswith("port.stage_") else "read",
            logical, physical_bytes, beats, segment, geometry.source_path,
        ))

    def emit(segment: str, charges: list[Charge]) -> str:
        nonlocal previous
        phase_id = f"{task.id}:{segment}"
        known: dict[str, float] = {}
        unknown = False
        dram_demands: dict[str, float] = {}
        claims: dict[str, float] = {}
        for item in charges:
            key = physical(item.resource)
            claims[key] = 1.0
            known[key] = known.get(key, 0.0) + (item.seconds or 0.0)
            unknown |= item.seconds is None
            if item.resource.startswith("dram."):
                dram_demands[key] = dram_demands.get(key, 0.0) + item.bytes
        budget = max(known.values(), default=0.0)
        for key, byte_count in dram_demands.items():
            if budget:
                claims[key] = byte_count / budget
            else:
                claims.pop(key, None)
        # Separate logical streams can share a port; their service sums, never folds by max.
        for a, b in combinations(charges, 2):
            if a.origin == b.origin and a.resource != b.resource:
                ledger.coupling[(a.resource, b.resource)] = "same_source"
        ledger.charges.extend(charges)
        dominant = max(known, key=known.get)
        category = next(item.category for item in charges if physical(item.resource) == dominant)
        phases.append(Phase(phase_id, None if unknown else budget, category, previous,
                            tuple(ResourceClaim(key, value) for key, value in sorted(claims.items())),
                            budget, evidence=charges[0].evidence))
        previous = (phase_id,)
        return phase_id

    def request(name: str, size: int, acquire: str, release: str, tensor: str | None = None):
        buffers.append(BufferRequest(f"{task.id}:{name}", engine_config.memory_domain, size,
                                     alignment, acquire, release, tensor_ids.get(tensor)))

    def stage(tensor: str, segment: str, byte_count: int) -> str:
        origin = Origin(f"{task.id}:{segment}", "dram")
        dram_resource, local_resource = f"dram.{tensor}", f"port.stage_{tensor}"
        result = emit(segment, [
            charge(dram_resource, origin, byte_count / profile.dram_effective_bytes_per_s,
                   byte_count=byte_count),
            charge(local_resource, origin, _local_seconds(byte_count, profile), byte_count=byte_count),
        ])
        stream(dram_resource, segment, tensor, byte_count, byte_count)
        stream(local_resource, segment, tensor, byte_count, byte_count)
        metrics["dram_bytes"] += byte_count
        return result

    weight_acquire = (stage("b", "weight-stage", weight_unique)
                      if resident and spec.b_domain == "dram" else None)
    command_footprints = []
    b_dram = weight_unique if resident else 0
    for index, c in enumerate(commands):
        prefix = f"command-{index}"
        command_start = len(phases)
        a_command = int(c.m * c.k * a_elem) + (c.m * c.k // spec.block_size if a_mx else 0)
        if staged and spec.a_domain == "dram":
            stage("a", f"{prefix}:a-stage", a_command)
        for part, passes, offset, output in (
            ("internal", c.k_tiles - 1, 0, False),
            ("output", 1, c.k_tiles - 1, True),
        ):
            if not passes:
                continue
            segment = f"{prefix}:{part}"
            a_beats, b_beats = c.n_tiles * passes * c.m, c.n_tiles * passes * c.tile_n
            a_bytes, b_bytes = int(a_beats * c.k_per_beat * a_elem), int(b_beats * c.k_per_beat * b_elem)
            step = c.k_per_beat // spec.block_size
            sa_bytes, sb_bytes = (a_beats * step if a_mx else 0), (b_beats * step if b_mx else 0)
            a_stride = (c.k if staged else k_prog) // spec.block_size
            a_start = 0 if staged else c.m_offset * a_stride + c.k_offset // spec.block_size
            b_stride = k_prog // spec.block_size
            b_start = c.n_offset * b_stride + c.k_offset // spec.block_size
            sa_requests = c.n_tiles * _scale_requests(c.m, passes, a_stride, step, offset,
                                                     start=a_start) if a_mx else 0
            sb_requests = _scale_requests(c.n, passes, b_stride, step, offset,
                                          start=b_start) if b_mx else 0
            a_physical, b_physical = a_beats * geometry.a_bytes_per_beat, b_beats * geometry.b_bytes_per_beat
            b_origin = Origin(f"{task.id}:{segment}:loadW", "local" if resident else "stream")
            b_charges = [
                charge("port.dma_b", b_origin, _local_seconds(b_physical, profile), byte_count=b_physical, beats=b_beats),
                charge("array.loadW", b_origin, _cycle_seconds(b_beats, profile), cycles=b_beats),
            ]
            if not resident and spec.b_domain == "dram":
                b_total = b_bytes + sb_bytes
                b_charges.insert(0, charge("dram.b", b_origin, b_total / profile.dram_effective_bytes_per_s, byte_count=b_total))
                stream("dram.b", f"{segment}:loadW", "b", b_total, b_total)
                metrics["dram_bytes"] += b_total
                b_dram += b_total
            if sb_requests:
                b_charges.append(charge("port.scale_b", b_origin, _cycle_seconds(sb_requests, profile), byte_count=4 * sb_requests, beats=sb_requests))
                stream("port.scale_b", f"{segment}:loadW", "b", sb_bytes, 4 * sb_requests, sb_requests)
            emit(f"{segment}:loadW", b_charges)
            stream("port.dma_b", f"{segment}:loadW", "b", b_bytes, b_physical, b_beats)
            a_origin = Origin(f"{task.id}:{segment}:consume", "local" if staged else "stream")
            overlap_class = f"{task.id}:{segment}:consume_write" if overlap and output else "serial"
            a_charges = [
                charge("port.dma_a", a_origin, _local_seconds(a_physical, profile), byte_count=a_physical, beats=a_beats, overlap_class=overlap_class),
                charge("array.consume", a_origin, _cycle_seconds(a_beats, profile), cycles=a_beats,
                       ops=c.m * c.n * passes * c.k_per_beat, overlap_class=overlap_class),
            ]
            if not staged and spec.a_domain == "dram":
                a_total = a_bytes + sa_bytes
                a_charges.insert(0, charge("dram.a", a_origin, a_total / profile.dram_effective_bytes_per_s,
                                          byte_count=a_total, overlap_class=overlap_class))
                stream("dram.a", f"{segment}:consume", "a", a_total, a_total)
                metrics["dram_bytes"] += a_total
            if sa_requests:
                a_charges.append(charge("port.scale_a", a_origin, _cycle_seconds(sa_requests, profile),
                                        byte_count=4 * sa_requests, beats=sa_requests, overlap_class=overlap_class))
                stream("port.scale_a", f"{segment}:consume", "a", sa_bytes, 4 * sa_requests, sa_requests)
            stream("port.dma_a", f"{segment}:consume", "a", a_bytes, a_physical, a_beats)
            c_bytes = c_physical = 0
            if output:
                c_bytes = c.m * c.n * 4
                c_beats = c.m * c.n_tiles * max(1, math.ceil(c.tile_n * 4 / geometry.c_bytes_per_beat))
                c_physical = c_beats * geometry.c_bytes_per_beat
                c_origin = Origin(f"{task.id}:{segment}:write", "dma_c")
                c_charges = [
                    charge("port.dma_c", c_origin, _local_seconds(c_physical, profile), byte_count=c_physical,
                           beats=c_beats, overlap_class=overlap_class),
                ]
                if spec.c_domain == "dram":
                    c_charges.append(charge("dram.c", c_origin, c_bytes / profile.dram_effective_bytes_per_s,
                                            byte_count=c_bytes, overlap_class=overlap_class))
                if overlap:
                    emit(f"{segment}:consume_write", a_charges + c_charges)
                else:
                    emit(f"{segment}:consume", a_charges)
                    emit(f"{segment}:write", c_charges)
                stream("port.dma_c", f"{segment}:write", "c", c_bytes, c_physical, c_beats)
                if spec.c_domain == "dram":
                    stream("dram.c", f"{segment}:write", "c", c_bytes, c_bytes)
                    metrics["dram_bytes"] += c_bytes
            else:
                emit(f"{segment}:consume", a_charges)
            values = {
                "a_logical_bytes": a_bytes, "b_logical_bytes": b_bytes, "c_logical_bytes": c_bytes,
                "a_physical_bytes": a_physical, "b_physical_bytes": b_physical, "c_physical_bytes": c_physical,
                "scale_a_logical_bytes": sa_bytes, "scale_b_logical_bytes": sb_bytes,
                "scale_a_physical_bytes": 4 * sa_requests, "scale_b_physical_bytes": 4 * sb_requests,
            }
            for name, value in values.items():
                metrics[name] += value
        start, end = phases[command_start].id, phases[-1].id
        a_size = a_command if staged else c.m * geometry.a_bytes_per_beat + (c.m * step if a_mx else 0)
        b_size = c.tile_n * geometry.b_bytes_per_beat + (c.tile_n * step if b_mx else 0)
        c_size = c.m * c.tile_n * 4
        request(f"{prefix}:a-{'stage' if staged else 'window'}", a_size, start, end, "a")
        request(f"{prefix}:b-window", b_size, start, end)
        request(f"{prefix}:c-window", c_size, start, end)
        if overlap:
            request(f"{prefix}:c-overlap", c_size, start, end)
        command_footprints.append(sum(_round_up(size, alignment) for size in
                                      (a_size, b_size, c_size, *((c_size,) if overlap else ()))))
        if c.k_offset:
            metrics["k_split_cells"] += c.m * c.n
    if resident:
        request("weight-resident", weight_unique, weight_acquire or phases[0].id,
                phases[-1].id, "b")
    footprint = max(command_footprints) + (_round_up(weight_unique, alignment) if resident else 0)
    capacity = profile.sram_bytes_per_core
    if capacity is None and (resident or staged or overlap):
        raise ValueError("GEMM capacity failure: explicit residency/staging/overlap requires known SRAM capacity")
    if capacity is not None and footprint > capacity:
        raise ValueError(f"GEMM capacity failure: {weight_policy}/{activation_policy} needs {footprint} bytes; capacity is {capacity}")
    metrics.update({
        "issued_macs": counts.useful_macs + counts.padded_macs,
        "semantic_macs": spec.semantic_macs if spec.semantic_macs is not None else counts.useful_macs,
        "masked_macs": counts.useful_macs - (spec.semantic_macs if spec.semantic_macs is not None else counts.useful_macs),
        "array_utilization": counts.utilization,
        "weight_unique_bytes": weight_unique if spec.weight else 0,
        "weight_dram_bytes": b_dram if spec.weight else 0,
        "weight_reload_bytes": max(0, b_dram - weight_unique) if spec.weight else 0,
        "k_split_accum_bytes": 12 * metrics["k_split_cells"],
        "placement": {"weight_policy": weight_policy, "activation_policy": activation_policy,
                      "capacity_bytes": capacity, "footprint_bytes": footprint, "alignment": alignment},
    })
    if any(domain != "dram" for domain in (spec.a_domain, spec.b_domain, spec.c_domain)):
        excluded.append("non-DRAM GEMM operands skip external traffic; local port service remains analytical")
    if metrics["k_split_cells"]:
        excluded.append("independent K commands require explicit downstream merge tasks and partial-sum placement")
    return EngineCost(task.id, engine_config.id, tuple(phases), (engine_resource,), tuple(traffic),
                      tuple(buffers), metrics, ledger, tuple(sorted(set(excluded))))
