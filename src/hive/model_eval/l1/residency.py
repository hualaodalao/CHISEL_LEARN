from __future__ import annotations

from dataclasses import dataclass
from itertools import combinations

from ..hw.profiles import HwProfile
from .ledger import Charge
from .params import ModelParams


@dataclass(frozen=True)
class Placement:
    weight_resident: bool
    activation_resident: bool
    double_buffer_fits: bool
    capacity_bytes: int | None
    footprint_bytes: float
    evidence: str


def place(profile: HwProfile, params: ModelParams, *, weight_bytes: float,
          activation_bytes: float, window_bytes: float, output_tile_bytes: float) -> Placement:
    capacity = profile.sram_bytes_per_core
    active = activation_bytes + window_bytes + output_tile_bytes
    activation_fits = capacity is not None and active <= capacity
    weight_fits = capacity is not None and active + weight_bytes <= capacity
    weight_resident = params.weight_policy == "resident" and weight_fits
    footprint = active + (weight_bytes if weight_resident else 0)
    double_fits = capacity is not None and 2 * footprint <= capacity
    if params.overlap and not double_fits:
        raise ValueError("重叠情景缺少可容纳完整双缓冲活跃区的 SRAM 容量依据")
    evidence = ("explicit per-command A staging; weight residency requested and fits"
                if weight_resident else "stream B; A staged per command only when active set fits")
    if capacity is None:
        evidence = "capacity unknown: no residency or double buffering inferred"
    return Placement(weight_resident, activation_fits, double_fits, capacity, footprint, evidence)


def coupling(charges: list[Charge]) -> dict[tuple[str, str], str]:
    relations = {}
    for a, b in combinations(charges, 2):
        if a.resource == b.resource:
            continue
        relation = "same_source" if a.origin == b.origin else "independent"
        pair = (a.resource, b.resource)
        previous = relations.get(pair, relations.get(pair[::-1]))
        if previous is not None and previous != relation:
            raise ValueError("同一资源对有多个关系，须先按具体流实例划分资源")
        relations[pair] = relation
    return relations
