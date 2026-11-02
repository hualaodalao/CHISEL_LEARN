"""工作点：三相位。decode 按新 token 计，prefill 按整段 prompt 计，frame 按帧计。"""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum


class Phase(Enum):
    DECODE = "decode"
    PREFILL = "prefill"
    FRAME = "frame"


@dataclass(frozen=True)
class WorkloadPoint:
    phase: Phase
    seq_len: int = 0
    batch: int = 1

    def __post_init__(self) -> None:
        if self.seq_len < 0:
            raise ValueError(f"seq_len 不能为负：{self.seq_len}")
        if self.batch < 1:
            raise ValueError(f"batch 至少为 1：{self.batch}")
        if self.phase is Phase.PREFILL and self.seq_len == 0:
            raise ValueError("prefill 需要非零 prompt 长度")

    @property
    def label(self) -> str:
        if self.phase is Phase.FRAME:
            return f"frame×{self.batch}"
        return f"{self.phase.value}@S={self.seq_len}"

    @property
    def throughput_unit(self) -> str:
        return "fps" if self.phase is Phase.FRAME else "tok/s"

    @property
    def tokens_produced(self) -> int:
        """一次该工作点产出多少 token。decode 每次 1 个，prefill 整段。"""
        if self.phase is Phase.DECODE:
            return self.batch
        if self.phase is Phase.PREFILL:
            return self.seq_len * self.batch
        return 0


def decode(seq_len: int, batch: int = 1) -> WorkloadPoint:
    return WorkloadPoint(Phase.DECODE, seq_len, batch)


def prefill(seq_len: int, batch: int = 1) -> WorkloadPoint:
    return WorkloadPoint(Phase.PREFILL, seq_len, batch)


def frame(batch: int = 1) -> WorkloadPoint:
    return WorkloadPoint(Phase.FRAME, 0, batch)
