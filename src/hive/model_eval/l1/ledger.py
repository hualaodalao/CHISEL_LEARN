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
    seconds: float | None
    category: str
    dominant_resource: str
    overlap_class: str
    evidence: str
    known_seconds: float = 0.0


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
        dominant = max(known, key=lambda c: c.seconds) if known else charges[0]
        budget = dominant.seconds if known else 0.0
        folded.append(FoldedCharge(dominant.origin,
                                   budget if len(known) == len(charges) else None,
                                   dominant.category, dominant.resource,
                                   dominant.overlap_class, dominant.evidence, budget))
    return folded


@dataclass(frozen=True)
class ResourceClaim:
    resource: str
    amount: float = 1.0

    def __post_init__(self) -> None:
        if not self.resource or not math.isfinite(self.amount) or self.amount <= 0:
            raise ValueError("resource claims require finite positive amounts")


@dataclass(frozen=True)
class Traffic:
    stream_id: str
    owner_task_id: str
    tensor_id: str | None
    interface: str
    memory_domain: str
    direction: str
    logical_bytes: float
    physical_bytes: float
    physical_beats: int = 0
    segment: str = ""
    evidence: str = ""

    def __post_init__(self) -> None:
        if not self.stream_id or not self.owner_task_id or self.direction not in ("read", "write"):
            raise ValueError("traffic requires stream identity, owner and direction")
        if any(not math.isfinite(n) or n < 0 for n in
               (self.logical_bytes, self.physical_bytes, self.physical_beats)):
            raise ValueError("traffic counts must be finite and nonnegative")


@dataclass(frozen=True)
class Phase:
    id: str
    seconds: float | None
    category: str
    dependencies: tuple[str, ...] = ()
    claims: tuple[ResourceClaim, ...] = ()
    known_seconds: float = 0.0
    source: str = "structural"
    evidence: str = ""

    def __post_init__(self) -> None:
        if not self.id or self.category not in CATEGORIES:
            raise ValueError("phase requires an ID and attribution category")
        if self.seconds is not None and (not math.isfinite(self.seconds) or self.seconds < 0):
            raise ValueError("phase time must be nonnegative or unknown")
        if not math.isfinite(self.known_seconds) or self.known_seconds < 0:
            raise ValueError("known service budget must be nonnegative")

    @property
    def budget_s(self) -> float:
        return self.seconds if self.seconds is not None else self.known_seconds


@dataclass(frozen=True)
class BufferRequest:
    id: str
    memory_domain: str
    size_bytes: int
    alignment: int = 1
    acquire_phase: str | None = None
    release_phase: str | None = None
    tensor_id: str | None = None

    def __post_init__(self) -> None:
        if not self.id or type(self.size_bytes) is not int or self.size_bytes < 0:
            raise ValueError("buffer sizes must be nonnegative integers")
        if type(self.alignment) is not int or self.alignment < 1:
            raise ValueError("buffer alignment must be a positive integer")

    @property
    def allocated_bytes(self) -> int:
        return math.ceil(self.size_bytes / self.alignment) * self.alignment


@dataclass(frozen=True)
class EngineCost:
    task_id: str
    instance_id: str
    phases: tuple[Phase, ...]
    lease_resources: tuple[str, ...] = ()
    traffic: tuple[Traffic, ...] = ()
    buffers: tuple[BufferRequest, ...] = ()
    metrics: dict = field(default_factory=dict)
    ledger: Ledger = field(default_factory=Ledger)
    excluded_costs: tuple[str, ...] = ()

    def __post_init__(self) -> None:
        ids = {phase.id for phase in self.phases}
        if len(ids) != len(self.phases):
            raise ValueError("duplicate phase ID")
        pending = {p.id: set(p.dependencies) for p in self.phases}
        if any(dep not in ids for deps in pending.values() for dep in deps):
            raise ValueError("missing phase dependency")
        while pending:
            ready = {key for key, deps in pending.items() if not deps}
            if not ready:
                raise ValueError("phase dependency cycle")
            for key in ready:
                del pending[key]
            for deps in pending.values():
                deps.difference_update(ready)
        for buffer in self.buffers:
            if any(endpoint is not None and endpoint not in ids
                   for endpoint in (buffer.acquire_phase, buffer.release_phase)):
                raise ValueError("buffer lifecycle refers to missing phase")
        owners = {(t.stream_id, t.owner_task_id) for t in self.traffic}
        if any(owner != self.task_id for _, owner in owners):
            raise ValueError("traffic owner disagrees with task")

    @property
    def known_service_s(self) -> float:
        ends: dict[str, float] = {}
        pending = {p.id: p for p in self.phases}
        while pending:
            for key, phase in tuple(pending.items()):
                if all(dep in ends for dep in phase.dependencies):
                    ends[key] = max((ends[dep] for dep in phase.dependencies), default=0.0) + phase.budget_s
                    del pending[key]
        return max(ends.values(), default=0.0)

    @property
    def complete(self) -> bool:
        return all(phase.seconds is not None for phase in self.phases)
