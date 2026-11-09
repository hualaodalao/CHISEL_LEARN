"""阵列几何：只从 gem5 的冻结 profile 读，eval 侧不复制数值。

抄一份几何就会漂，所以这里用 importlib 直接加载
`src/workerbee/gem5/configs/hardware.py`，并对派生关系做结构校验。
"""

from __future__ import annotations

import importlib.util
from dataclasses import dataclass
from pathlib import Path
from typing import Any

GEM5_HARDWARE = Path("src/workerbee/gem5/configs/hardware.py")


def repo_root(start: Path | None = None) -> Path:
    here = (start or Path(__file__)).resolve()
    for candidate in (here, *here.parents):
        if (candidate / "build.sbt").is_file():
            return candidate
    raise RuntimeError(f"从 {here} 向上找不到含 build.sbt 的仓库根")


def _load_gem5_hardware(root: Path) -> Any:
    path = root / GEM5_HARDWARE
    if not path.is_file():
        raise FileNotFoundError(f"gem5 冻结 profile 不存在：{path}")
    spec = importlib.util.spec_from_file_location("hive_eval_gem5_hardware", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


@dataclass(frozen=True)
class ArrayGeometry:
    profile_name: str
    spec_version: str
    array_n: int
    array_m: int
    total_n: int
    a_ext_w: int
    b_ext_w: int
    c_eff_w: int
    c_ext_w: int
    scale_a_w: int
    scale_b_w: int
    mnk_width: int
    supported_fmts: tuple[str, ...]
    source_path: str
    a_buffer_depth: int
    b_buffer_depth: int
    c_buffer_depth: int
    c_dma_ext_w: int

    @property
    def tile_n(self) -> int:
        """L1 的 T：一个窗口装载的 B 拍数，也是 N 方向 tile 宽度。"""
        return self.total_n

    @property
    def a_bytes_per_beat(self) -> int:
        return self.a_ext_w // 8

    @property
    def b_bytes_per_beat(self) -> int:
        return self.b_ext_w // 8

    @property
    def max_m(self) -> int:
        """单命令可容纳的 M；为阵列波前保留 T 行 partial-sum 空间。"""
        return self.c_buffer_depth - self.tile_n

    @property
    def c_bytes_per_beat(self) -> int:
        """物理 C DMA 拍宽，不是内部累加行的 cExtW。"""
        return self.c_dma_ext_w // 8


def load_array_geometry(root: Path | None = None) -> ArrayGeometry:
    root = root or repo_root()
    module = _load_gem5_hardware(root)
    profile = module.DEFAULT_PROFILE
    params = profile["parameters"]
    derived = profile["derived"]

    geometry = ArrayGeometry(
        profile_name=profile["name"],
        spec_version=profile["spec_version"],
        array_n=params["arrayN"],
        array_m=params["arrayM"],
        total_n=derived["totalN"],
        a_ext_w=derived["aExtW"],
        b_ext_w=derived["bExtW"],
        c_eff_w=derived["cEffW"],
        c_ext_w=derived["cExtW"],
        scale_a_w=derived["scaleAW"],
        scale_b_w=derived["scaleBW"],
        mnk_width=derived["mnkWidth"],
        supported_fmts=tuple(params["supportedFmts"]),
        source_path=GEM5_HARDWARE.as_posix(),
        a_buffer_depth=params["aBufferDepth"],
        b_buffer_depth=params["bBufferDepth"],
        c_buffer_depth=params["cBufferDepth"],
        c_dma_ext_w=params["cDmaExtW"],
    )
    _check_derivations(geometry)
    return geometry


def _check_derivations(g: ArrayGeometry) -> None:
    if g.total_n != g.array_n or g.array_m != g.array_n:
        raise ValueError(
            f"方形契约破坏：totalN {g.total_n} 与 arrayM {g.array_m} 都必须 == arrayN {g.array_n}；"
            " gem5 profile 的派生关系变了，eval 侧的 T 不再可信"
        )
    if g.a_buffer_depth <= 0 or g.b_buffer_depth <= 0:
        raise ValueError("A/B buffer 深度必须为正数")
    if g.c_buffer_depth < 2 * g.tile_n:
        raise ValueError("C buffer 深度必须至少为 2T")
    if g.c_dma_ext_w <= 0 or g.c_dma_ext_w % 8:
        raise ValueError(f"C DMA 位宽必须为正整字节：{g.c_dma_ext_w}")
    if g.mnk_width <= 0:
        raise ValueError(f"M/N/K 字段位宽必须为正数：{g.mnk_width}")
    if g.a_ext_w % 8 or g.b_ext_w % 8:
        raise ValueError(f"A/B 外部位宽非整字节：{g.a_ext_w}/{g.b_ext_w}")
    if g.a_bytes_per_beat != 32 or g.b_bytes_per_beat != 32:
        raise ValueError(
            f"A/B 每拍字节数为 {g.a_bytes_per_beat}/{g.b_bytes_per_beat}，不是 32；"
            " 同源取 max 的前提（单口 32 B/cycle）需重新推导"
        )


def gem5_release(root: Path | None = None) -> tuple[str, str]:
    module = _load_gem5_hardware(root or repo_root())
    return module.GEM5_RELEASE, module.GEM5_COMMIT
