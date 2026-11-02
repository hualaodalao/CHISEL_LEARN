from __future__ import annotations

import math
from dataclasses import asdict, dataclass, field

CATEGORIES = ("dram", "gemm", "vpu", "cpu", "handoff")


@dataclass(frozen=True)
class Origin:
    stream: str
    producer: str

    def key(self) -> tuple[str, str]:
        return self.stream, self.producer


@dataclass(frozen=True)
class Charge:
    resource: str
    instance: str
    origin: Origin
    category: str
    seconds: float | None
    beats: int = 0
    bytes: float = 0
    cycles: float = 0
    ops: int = 0
    overlap_class: str = "serial"
    evidence: str = ""
    source: str = "structural"

    def __post_init__(self) -> None:
        if self.category not in CATEGORIES:
            raise ValueError(f"未知归因类别 {self.category}")
        for value in (self.beats, self.bytes, self.cycles, self.ops):
            if not math.isfinite(value) or value < 0:
                raise ValueError("资源量必须为有限非负数")
        if self.seconds is not None and (not math.isfinite(self.seconds) or self.seconds < 0):
            raise ValueError("资源时间必须为有限非负数或 None")
        if self.overlap_class != "serial" and not self.evidence.strip():
            raise ValueError("重叠账目必须带依据")


@dataclass
class Ledger:
    charges: list[Charge] = field(default_factory=list)
    coupling: dict[tuple[str, str], str] = field(default_factory=dict)
    excluded: list[str] = field(default_factory=list)

    def to_dict(self) -> dict:
        return {"charges": [asdict(c) for c in self.charges],
                "coupling": [{"resources": list(pair), "relation": relation}
                             for pair, relation in sorted(self.coupling.items())],
                "excluded": sorted(set(self.excluded))}


@dataclass(frozen=True)
class FoldedCharge:
    origin: Origin
    seconds: float
    category: str
    dominant_resource: str
    overlap_class: str
    evidence: str


def fold(ledger: Ledger) -> list[FoldedCharge]:
    groups: dict[tuple[str, str], list[Charge]] = {}
    for charge in ledger.charges:
        groups.setdefault(charge.origin.key(), []).append(charge)
    folded = []
    for charges in groups.values():
        classes = {c.overlap_class for c in charges}
        if len(classes) != 1:
            raise ValueError("同源资源必须属于相同的重叠类别")
        for i, a in enumerate(charges):
            for b in charges[i + 1:]:
                if a.resource == b.resource:
                    raise ValueError("同源同一资源的串行量应先求和，不能以 max 丢失")
                pair = (a.resource, b.resource)
                relation = ledger.coupling.get(pair, ledger.coupling.get(pair[::-1]))
                if relation != "same_source":
                    raise ValueError(f"同源折叠缺少耦合依据：{pair}")
        known = [c for c in charges if c.seconds is not None]
        if not known:
            continue
        dominant = max(known, key=lambda c: c.seconds)
        folded.append(FoldedCharge(dominant.origin, dominant.seconds,
                                   dominant.category, dominant.resource,
                                   dominant.overlap_class, dominant.evidence))
    return folded
