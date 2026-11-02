from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class GemmGroup:
    m: int
    n: int
    k: int
    repeats: int = 1


@dataclass(frozen=True)
class OpSpec:
    id: str
    layer: str
    name: str
    engine: str
    kind: str
    mac: int = 0
    elements: int = 0
    vectors: int = 0
    length: int = 0
    read_bytes: int = 0
    write_bytes: int = 0
    dram_read_bytes: int = 0
    dram_write_bytes: int = 0
    weight_bytes: float = 0
    gemms: tuple[GemmGroup, ...] = ()
    dependencies: tuple[str, ...] = ()
    evidence: str = ""
