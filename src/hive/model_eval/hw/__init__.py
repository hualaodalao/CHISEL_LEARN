"""共享硬件事实：命名 profile 与阵列几何。不属于任何单层。"""

from .geometry import ArrayGeometry, load_array_geometry, repo_root
from .profiles import (
    G0,
    G0_PRO,
    MAC_FAMILY_INT4,
    MAC_FAMILY_INT8,
    MODEL_ANALYSIS_310,
    PROFILES,
    HwProfile,
    get_profile,
)

__all__ = [
    "ArrayGeometry",
    "G0",
    "G0_PRO",
    "HwProfile",
    "MAC_FAMILY_INT4",
    "MAC_FAMILY_INT8",
    "MODEL_ANALYSIS_310",
    "PROFILES",
    "get_profile",
    "load_array_geometry",
    "repo_root",
]
