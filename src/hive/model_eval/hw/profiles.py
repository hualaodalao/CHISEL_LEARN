"""共享硬件 profile：命名常数的唯一入口。

每个数字都带 arch.md / arch_roadmap.md 的行号出处。本模块不推导、不平均、不互相
覆盖两套口径——G0 与 MODEL_ANALYSIS_310 并列存在，调用方必须显式选一个。
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Mapping

GB = 10**9
KIB = 1024
MIB = 1024 * 1024

ARCH = "src/hive/doc/arch.md"
ROADMAP = "src/hive/doc/arch_roadmap.md"
ANALYSIS = "src/hive/doc/model_analysis/Qwen3-0.6B-逐层计算拆解.md"

MAC_FAMILY_INT8 = "int8_fp8_mxfp8_w4a8"
MAC_FAMILY_INT4 = "int4_mxfp4"


@dataclass(frozen=True)
class Citation:
    doc: str
    line: int
    quantity: str


@dataclass(frozen=True)
class HwProfile:
    name: str
    dram_peak_bytes_per_s: float
    dram_eta: float
    n_core: int
    freq_hz: int | None
    mac_per_cycle_per_core: Mapping[str, int]
    mac_per_s_declared: float | None
    sram_bytes_per_core: int | None
    sram_peak_bytes_per_s_per_core: float | None
    local_iface_bytes_per_cycle: int | None
    citations: tuple[Citation, ...]

    @property
    def dram_effective_bytes_per_s(self) -> float:
        return self.dram_peak_bytes_per_s * self.dram_eta

    @property
    def format_agnostic_compute(self) -> bool:
        return self.mac_per_s_declared is not None

    def mac_per_second(self, mac_family: str) -> float:
        if self.mac_per_s_declared is not None:
            return self.mac_per_s_declared
        if mac_family not in self.mac_per_cycle_per_core:
            raise KeyError(
                f"profile {self.name} 未记载 MAC 族 {mac_family!r}；"
                f"已记载 {sorted(self.mac_per_cycle_per_core)}。"
                " arch_roadmap.md 没给的档位不得猜测。"
            )
        return self.mac_per_cycle_per_core[mac_family] * self.freq_hz * self.n_core

    @property
    def local_iface_bytes_per_s_per_core(self) -> float | None:
        if self.local_iface_bytes_per_cycle is None or self.freq_hz is None:
            return None
        return self.local_iface_bytes_per_cycle * self.freq_hz


_G0_MAC = {MAC_FAMILY_INT8: 512, MAC_FAMILY_INT4: 1024}

G0 = HwProfile(
    name="G0",
    dram_peak_bytes_per_s=17.064 * GB,
    dram_eta=1.0,
    n_core=2,
    freq_hz=500_000_000,
    mac_per_cycle_per_core=_G0_MAC,
    mac_per_s_declared=None,
    sram_bytes_per_core=512 * KIB,
    sram_peak_bytes_per_s_per_core=128.0 * GB,
    local_iface_bytes_per_cycle=32,
    citations=(
        Citation(ARCH, 895, "4 GiB LPDDR4x x32 @4266 MT/s → 17.064 GB/s 理论峰值"),
        Citation(ARCH, 869, "8 banks × 32 B/cycle = 128 GB/s/core"),
        Citation(ARCH, 943, "local 数据接口 32 B/cycle；读写合计 16 GB/s"),
        Citation(ROADMAP, 74, "INT8/FP8/MXFP8/合法 W4A8 512 MAC/cycle/core"),
        Citation(ROADMAP, 75, "INT4/MXFP4 1024 MAC/cycle/core"),
    ),
)

G0_PRO = HwProfile(
    name="G0_PRO",
    dram_peak_bytes_per_s=34.128 * GB,
    dram_eta=1.0,
    n_core=4,
    freq_hz=500_000_000,
    mac_per_cycle_per_core=_G0_MAC,
    mac_per_s_declared=None,
    sram_bytes_per_core=1 * MIB,
    sram_peak_bytes_per_s_per_core=128.0 * GB,
    local_iface_bytes_per_cycle=32,
    citations=(
        Citation(ARCH, 895, "8 GiB LPDDR4x x64 @4266 MT/s → 34.128 GB/s 理论峰值"),
        Citation(ARCH, 869, "8 banks × 32 B/cycle = 128 GB/s/core"),
        Citation(ARCH, 943, "local 数据接口 32 B/cycle"),
        Citation(ROADMAP, 74, "4 cores 1.024 TMAC/s = 2.048 TOPS"),
    ),
)

# model_analysis/ 全部 11 份逐层拆解按这一套算。算力是 flat 25 TOPS，不分格式，
# 所以用 declared 而非 mac_per_cycle × f × n_core 推导；SRAM/端口口径文档未给，留空。
MODEL_ANALYSIS_310 = HwProfile(
    name="MODEL_ANALYSIS_310",
    dram_peak_bytes_per_s=310.0 * GB,
    dram_eta=1.0,
    n_core=1,
    freq_hz=None,
    mac_per_cycle_per_core={},
    mac_per_s_declared=12.5e12,
    sram_bytes_per_core=None,
    sram_peak_bytes_per_s_per_core=None,
    local_iface_bytes_per_cycle=None,
    citations=(
        Citation(ANALYSIS, 1470, "310e9 B/s 带宽口径"),
        Citation(ANALYSIS, 1210, "25 TOPS 峰值 = 12.5 TMAC/s"),
    ),
)

PROFILES: Mapping[str, HwProfile] = {
    "G0": G0,
    "G0_PRO": G0_PRO,
    "MODEL_ANALYSIS_310": MODEL_ANALYSIS_310,
    "310": MODEL_ANALYSIS_310,
}


def get_profile(name: str) -> HwProfile:
    try:
        return PROFILES[name]
    except KeyError:
        raise KeyError(f"未知 profile {name!r}；可选 {sorted(PROFILES)}") from None
