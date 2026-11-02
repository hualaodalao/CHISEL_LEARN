"""K 子命令不保留跨命令部分和，调用方必须显式累加各自的输出。"""

from __future__ import annotations

from dataclasses import dataclass
from fractions import Fraction

from ..hw.geometry import ArrayGeometry


# Generic analytical names and the spellings used by supportedFmts. Preserve
# explicit E4M3/E5M2 capabilities when checking a particular implementation.
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
    """Packed element bytes, excluding MX block-scale bytes."""
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
        # Profiles may contain other hardware formats not modeled by L1.
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
    """每个独立 K 子命令在其最后内部 K pass 输出，而非仅 final_k 输出。"""
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

    # One 16-bit A lane packs 1 BF16, 2 eight-bit, or 4 four-bit elements.
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
                command = Command(
                    m_offset=m_offset, n_offset=n_offset, k_offset=k_offset,
                    m=m, n=n, k=k, n_tiles=n // geometry.tile_n,
                    k_tiles=k // k_per_beat, k_per_beat=k_per_beat,
                    tile_n=geometry.tile_n, a_fmt=shape.a_fmt, b_fmt=shape.b_fmt,
                    c_bits=shape.c_bits, block_size=shape.block_size,
                    final_k=k_offset + k == padded_k,
                    c_bytes_per_beat=geometry.c_bytes_per_beat,
                )
                commands.append(command)
    return tuple(commands)
