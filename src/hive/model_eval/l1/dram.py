from __future__ import annotations

from ..hw.profiles import HwProfile


def seconds(byte_count: float, profile: HwProfile) -> float:
    return byte_count / profile.dram_effective_bytes_per_s


def local_seconds(byte_count: float, profile: HwProfile) -> float | None:
    rate = profile.local_iface_bytes_per_s_per_core
    return byte_count / rate if rate is not None else None


def cycle_seconds(cycles: float, profile: HwProfile) -> float | None:
    return cycles / profile.freq_hz if profile.freq_hz is not None else None
