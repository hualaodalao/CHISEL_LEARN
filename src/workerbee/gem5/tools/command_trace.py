"""Cycle-annotated four-lane command traces for the WorkerBee GEMM model.

The trace is the only path commands take into the model. `pack` renders the
same batch list that `write_jsonl` records, so a recorded trace and a generated
one share one decoder. Batch cycles are earliest-cycle floors: the model holds
a batch until the register file can accept it.
"""

from dataclasses import dataclass, field
import argparse
import json
from pathlib import Path

SCHEMA_VERSION = 1
HARDWARE_PROFILE = "workerbee_default_v4"

class TraceError(ValueError):
    """A request that is unsupported or cannot be made legal."""


FORMATS = {
    "FP32": 0, "FP16": 1, "BF16": 2, "MXE4M3": 3, "MXE5M2": 4, "MXE2M1": 5,
    "INT32": 6, "INT16": 7, "INT8": 8, "INT4": 9, "FP8E4M3": 10, "FP8E5M2": 11,
}
ELEMENT_BITS = {
    "FP32": 32, "FP16": 16, "BF16": 16, "MXE4M3": 8, "MXE5M2": 8, "MXE2M1": 4,
    "INT32": 32, "INT16": 16, "INT8": 8, "INT4": 4, "FP8E4M3": 8, "FP8E5M2": 8,
}
MX_FORMATS = {"MXE4M3", "MXE5M2", "MXE2M1"}
FP8_FORMATS = {"FP8E4M3", "FP8E5M2"}
INT_FORMATS = {"INT8", "INT4"}
INPUT_FORMATS = ("BF16", "FP8E4M3", "FP8E5M2", "INT4", "INT8",
                 "MXE2M1", "MXE4M3", "MXE5M2")
# WbOutCvt has an fp32 path, an int32/packed16 path and MX block quantization.
OUTPUT_FORMATS = ("FP32", "INT32", "BF16", "INT16", "INT8", "INT4",
                  "MXE4M3", "MXE5M2", "MXE2M1")

TOTAL_N = 16
MIN_M, MAX_M = 1, 48

OP_WRITE1, OP_WRITE2, OP_READ = 0, 1, 2

REG = {
    "M": 0x00, "NK": 0x01, "A_ADDR": 0x02, "B_ADDR": 0x03, "C_ADDR": 0x04,
    "CONTROL": 0x05, "SCALE_A_ADDR": 0x06, "SCALE_B_ADDR": 0x07,
    "SCALE_C_ADDR": 0x08, "BIAS_ADDR": 0x09, "RQ_M_ADDR": 0x0A,
    "RQ_PARAM": 0x0B, "START": 0x10, "STATUS": 0x1E,
    "IRQ_RAW": 0x30, "IRQ_MASK": 0x31, "IRQ_MASKED": 0x32,
    "IRQ_CLEAR": 0x33, "ERROR_CODE": 0x34,
}
IRQ_ALL = 0x7FF


@dataclass(frozen=True)
class Job:
    """One GEMM command set. Addresses default to zero and are usually filled
    in by `layout`."""
    m: int = 16
    n: int = 16
    k: int = 64
    a_fmt: str = "INT8"
    b_fmt: str = "INT8"
    c_fmt: str = "INT32"
    load_w: int = 0
    bias: bool = False
    requant: bool = False
    relu: bool = False
    blk_a: int = 16
    blk_b: int = 16
    blk_c: int = 16
    rnd: int = 0
    rq_shift: int = 0
    zero_point: int = 0
    a_addr: int = 0
    b_addr: int = 0
    c_addr: int = 0
    scale_a_addr: int = 0
    scale_b_addr: int = 0
    scale_c_addr: int = 0
    bias_addr: int = 0
    rq_m_addr: int = 0
    tag: int = 0


@dataclass(frozen=True)
class Command:
    op: int
    reg0: int = 0
    reg1: int = 0
    data0: int = 0
    data1: int = 0
    pc: int = 0
    tag: int = 0


@dataclass(frozen=True)
class Batch:
    cycle: int
    lanes: dict = field(default_factory=dict)


def write1(reg, data, pc=0, tag=0):
    return Command(OP_WRITE1, REG[reg], 0, data & 0xFFFFFFFF, 0, pc, tag)


def write2(reg0, data0, reg1, data1, pc=0, tag=0):
    return Command(OP_WRITE2, REG[reg0], REG[reg1], data0 & 0xFFFFFFFF,
                   data1 & 0xFFFFFFFF, pc, tag)


def read(reg, tag=0):
    return Command(OP_READ, REG[reg], 0, 0, 0, 0, tag)


def k_per_beat(fmt):
    """K elements advanced by one packed beat: totalN x elements per lane."""
    return TOTAL_N * (16 // ELEMENT_BITS[fmt])


def is_packed(fmt):
    return ELEMENT_BITS[fmt] <= 8


def control_word(job):
    def block(bits):
        return 1 if bits == 32 else 0

    return (FORMATS[job.a_fmt] | FORMATS[job.b_fmt] << 4 |
            FORMATS[job.c_fmt] << 8 | int(job.relu) << 15 |
            (job.rnd & 0x7) << 16 | block(job.blk_a) << 22 |
            block(job.blk_b) << 24 | block(job.blk_c) << 26 |
            int(job.load_w) << 28 | int(job.requant) << 29 |
            int(job.bias) << 30)


def rq_param_word(job):
    return ((job.zero_point & 0xFFFF) << 16) | (job.rq_shift & 0x3F)


def nk_word(job):
    return (job.n & 0xFFFF) | ((job.k & 0xFFFF) << 16)


def validate(job):
    """Reject what the hardware has no errCode for or what cannot be legal.

    Hardware-detectable violations (bad alignment, unsupported mixing, K not
    divisible) are left to the model so the negative tests can see the real
    errCode; only inputs outside the documented legal domain are rejected here.
    """
    if not MIN_M <= job.m <= MAX_M:
        raise TraceError(f"M={job.m} outside the supported range {MIN_M}..{MAX_M}")
    if job.n < TOTAL_N or job.n % TOTAL_N:
        raise TraceError(f"N={job.n} must be a positive multiple of {TOTAL_N}")
    if job.k < 1:
        raise TraceError("K must be positive")
    for name, fmt in (("a_fmt", job.a_fmt), ("b_fmt", job.b_fmt)):
        if fmt not in INPUT_FORMATS:
            raise TraceError(f"{name}={fmt} is not a supported input format")
    if job.c_fmt not in OUTPUT_FORMATS:
        raise TraceError(f"c_fmt={job.c_fmt} is not a supported output format")
    for name, bits in (("blk_a", job.blk_a), ("blk_b", job.blk_b),
                       ("blk_c", job.blk_c)):
        if bits not in (16, 32):
            raise TraceError(f"{name}={bits} must be 16 or 32")
    if job.a_fmt == "MXE2M1" and job.b_fmt != "MXE2M1":
        raise TraceError("A=MXE2M1 with B other than MXE2M1 is not reachable")
    if job.a_fmt == "INT4" and job.b_fmt == "INT8":
        raise TraceError("A=INT4 with B=INT8 is not reachable")
    families = [
        ("mx", job.a_fmt in MX_FORMATS, job.b_fmt in MX_FORMATS),
        ("fp8", job.a_fmt in FP8_FORMATS, job.b_fmt in FP8_FORMATS),
        ("int", job.a_fmt in INT_FORMATS, job.b_fmt in INT_FORMATS),
        ("bf16", job.a_fmt == "BF16", job.b_fmt == "BF16"),
    ]
    for name, left, right in families:
        if left != right:
            raise TraceError(f"{name} format on one side only")
    if sum(1 for _, left, _ in families if left) != 1:
        raise TraceError(f"unsupported A/B format pairing {job.a_fmt}x{job.b_fmt}")
    if (job.a_fmt in MX_FORMATS or job.a_fmt in FP8_FORMATS) and not job.load_w:
        raise TraceError("MX and FP8 inputs require load_w=1")
    return job


def geometry(job):
    """Beat counts the hardware issues for one job, in the model's terms."""
    bits = ELEMENT_BITS[job.a_fmt]
    nt = (job.n + TOTAL_N - 1) // TOTAL_N
    if is_packed(job.a_fmt):
        kt = job.k // k_per_beat(job.a_fmt)
    else:
        kt = (job.k + TOTAL_N - 1) // TOTAL_N
    c_bits = ELEMENT_BITS[job.c_fmt]
    row_bytes = TOTAL_N * c_bits // 8
    beats_per_row = 2 if c_bits == 32 else 1
    return {
        "t": TOTAL_N,
        "n_tiles": nt,
        "k_tiles": kt,
        "k_per_beat_a": k_per_beat(job.a_fmt),
        "k_per_beat_b": k_per_beat(job.b_fmt),
        "a_beats": nt * kt * job.m,
        "b_beats": nt * kt * TOTAL_N,
        "c_rows": nt * job.m,
        "c_bytes_per_row": row_bytes,
        "c_beats": nt * job.m * beats_per_row,
        "scheduled_ops": 2 * job.m * job.n * job.k,
        "elem_bits_a": bits,
    }


REGION_BANK = {
    "a_addr": 0, "b_addr": 1, "c_addr": 2,
    "scale_a_addr": 3, "bias_addr": 3, "rq_m_addr": 3,
    "scale_b_addr": 4, "scale_c_addr": 5,
}


def region_sizes(job):
    def span(elements, bits):
        return (elements * bits + 7) // 8

    return [
        ("a_addr", span(job.m * job.k, ELEMENT_BITS[job.a_fmt])),
        ("b_addr", span(job.n * job.k, ELEMENT_BITS[job.b_fmt])),
        ("c_addr", span(job.m * job.n, ELEMENT_BITS[job.c_fmt])),
        ("scale_a_addr", job.m * (job.k // job.blk_a)),
        ("scale_b_addr", job.n * (job.k // job.blk_b)),
        ("scale_c_addr", job.m * ((job.n + job.blk_c - 1) // job.blk_c)),
        ("bias_addr", job.n * 4),
        ("rq_m_addr", job.n * 4),
    ]


def _pack(regions, base, bank_size):
    addresses = {}
    if bank_size is None:
        cursor = 0
        for name, size in regions:
            cursor = (cursor + 4095) // 4096 * 4096
            addresses[name] = base + cursor
            cursor += max(size, 1)
        return addresses, cursor
    cursor = {}
    for name, size in regions:
        bank = REGION_BANK[name]
        offset = (cursor.get(bank, 0) + 4095) // 4096 * 4096
        addresses[name] = base + bank * bank_size + offset
        cursor[bank] = offset + max(size, 1)
    return addresses, cursor


def layout(job, base=0, align=4096):
    """Place every operand in one flat region and return its addresses.

    Regions are disjoint and aligned so the C, bias and requant base-address
    checks pass without special cases.
    """
    addresses, end = _pack(region_sizes(job), base, None)
    return addresses, (end + align - 1) // align * align


def banked_layout(job, base=0):
    """Place every operand in the private SRAM bank of the port that reads it.

    Returns (addresses, span, bank_bytes). The six banks are disjoint and each
    is a power of two, so one memory per DMA port can cover them without an
    interconnect in the way.
    """
    regions = region_sizes(job)
    _, need = _pack(regions, 0, None)
    bank = 1 << max(12, (max(need, 1) - 1).bit_length())
    addresses, _ = _pack(regions, base, bank)
    return addresses, bank * 6, bank


def program(job):
    """Two register batches that program and start one GEMM.

    Every write lands in the batch that carries START, so the job snapshot the
    register file takes at accept time sees the complete configuration.
    """
    validate(job)
    setup = {
        0: write2("M", job.m, "NK", nk_word(job)),
        1: write2("A_ADDR", job.a_addr, "B_ADDR", job.b_addr),
        2: write2("C_ADDR", job.c_addr, "CONTROL", control_word(job)),
        3: write2("SCALE_A_ADDR", job.scale_a_addr,
                  "SCALE_B_ADDR", job.scale_b_addr),
    }
    start = {
        0: write2("SCALE_C_ADDR", job.scale_c_addr,
                  "BIAS_ADDR", job.bias_addr),
        1: write2("RQ_M_ADDR", job.rq_m_addr, "RQ_PARAM", rq_param_word(job)),
        2: write1("IRQ_CLEAR", IRQ_ALL),
        3: write1("START", 1, tag=job.tag),
    }
    return [Batch(0, setup), Batch(0, start)]


def observe(cycle, task_id="gemm0"):
    """A read batch for status and interrupt inspection."""
    return Batch(cycle, {0: read("IRQ_RAW"), 1: read("STATUS"),
                         2: read("ERROR_CODE"), 3: read("IRQ_MASKED")})


def pack(batches):
    """Render batches into the model's VectorParam.UInt64 payload.

    Layout per batch: earliest cycle, lane mask, then five words per lane:
    {op | reg0 << 8 | reg1 << 16, data0, data1, pc, tag}.
    """
    words = []
    for batch in batches:
        if not batch.lanes:
            raise TraceError("a batch must select at least one lane")
        mask = 0
        for lane in batch.lanes:
            if not 0 <= lane < 4:
                raise TraceError(f"lane {lane} outside 0..3")
            mask |= 1 << lane
        words += [batch.cycle, mask]
        for lane in range(4):
            cmd = batch.lanes.get(lane, Command(OP_WRITE1))
            words += [cmd.op | cmd.reg0 << 8 | cmd.reg1 << 16,
                      cmd.data0, cmd.data1, cmd.pc, cmd.tag]
    return words


def _hex(value, width):
    return f"0x{value:0{width}x}"


def header(clock_period_ticks, source_hash=None, task_id="gemm0"):
    return {
        "type": "header", "schema_version": SCHEMA_VERSION,
        "hardware_profile": HARDWARE_PROFILE,
        "source_hash": source_hash, "clock_period_ticks": clock_period_ticks,
        "model_kind": "timing_loose", "task_id": task_id,
    }


def jsonl_records(job, batches, task_id="gemm0", clock_period_ticks=2,
                  source_hash=None, begin_cycle=0):
    """Spec section 9.2 records for one job: header, markers, batches."""
    records = [header(clock_period_ticks, source_hash, task_id)]
    records.append({"type": "task_begin", "earliest_cycle": begin_cycle,
                    "task_id": task_id, "kernel_id": task_id,
                    "logical_shape": {"m": job.m, "n": job.n, "k": job.k}})
    for batch in batches:
        commands = []
        for lane in sorted(batch.lanes):
            cmd = batch.lanes[lane]
            commands.append({
                "lane": lane, "op": cmd.op,
                "reg0Addr": _hex(cmd.reg0, 2), "reg1Addr": _hex(cmd.reg1, 2),
                "data0": _hex(cmd.data0, 8), "data1": _hex(cmd.data1, 8),
                "pc": _hex(cmd.pc, 8), "tag": _hex(cmd.tag, 2),
            })
        records.append({"type": "batch", "earliest_cycle": batch.cycle,
                        "task_id": task_id, "commands": commands})
    records.append({"type": "wait_completion", "task_id": task_id})
    return records


def write_jsonl(path, records):
    path = Path(path)
    with path.open("w", encoding="utf-8") as sink:
        for record in records:
            sink.write(json.dumps(record, sort_keys=True) + "\n")
    return path


class _ArgumentParser(argparse.ArgumentParser):
    def error(self, message):
        raise TraceError(message)


def main(argv=None):
    parser = _ArgumentParser(description=__doc__)
    parser.add_argument("--shape", required=True, help="MxNxK")
    parser.add_argument("--a-fmt", default="INT8")
    parser.add_argument("--b-fmt", default="INT8")
    parser.add_argument("--c-fmt", default="INT32")
    parser.add_argument("--load-w", type=int, default=0)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args(argv)
    m, n, k = (int(part) for part in args.shape.lower().split("x"))
    job = Job(m=m, n=n, k=k, a_fmt=args.a_fmt, b_fmt=args.b_fmt,
              c_fmt=args.c_fmt, load_w=args.load_w)
    addresses, _ = layout(job)
    job = Job(**{**job.__dict__, **addresses})
    records = jsonl_records(job, program(job))
    write_jsonl(args.output, records)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
