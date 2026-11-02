"""Manifest：来源指纹、口径声明、可信度。

这是唯一能给结论贴可信度的出口。任何模块都不得自行声明已校准——L2/L3 不在本工程
范围内（见 model_eval_plan.md §1.2），所以 `performance_valid` 恒为 False，
报告头部必须打印未校准声明。
"""

from __future__ import annotations

import hashlib
import json
from dataclasses import asdict, dataclass, field
from enum import Enum
from pathlib import Path

from ..hw.geometry import gem5_release, repo_root
from ..hw.profiles import HwProfile


class TrustLevel(Enum):
    ANALYTIC_ONLY = "analytic_only"
    ENGINEERING_ESTIMATE = "engineering_estimate"


UNCALIBRATED_NOTICE = "未经 RTL 校准的工程估算"

_NOT_CALIBRATED_REASON = (
    "L2 周期模型与 L3 边沿对拍不在 model_eval 范围内；"
    "本报告未导入或应用 RTL 校准证据，外部 gem5 验收状态不代表本报告已校准"
)

# 报告要追溯到的真源。缺文件即报错，不静默跳过。
PROVENANCE_PATHS = (
    "src/hive/doc/arch.md",
    "src/hive/doc/arch_roadmap.md",
    "src/hive/doc/model_analysis/Qwen3-0.6B-逐层计算拆解.md",
    "src/workerbee/gem5/configs/hardware.py",
)


@dataclass(frozen=True)
class SourceFingerprint:
    path: str
    sha256: str
    bytes: int


@dataclass
class Manifest:
    profile: str
    model: str
    layer: str
    trust_level: str
    performance_valid: bool
    data_valid: bool
    notice: str
    not_calibrated_reason: str
    named_inputs: dict[str, object]
    excluded_costs: list[str]
    sources: list[SourceFingerprint]
    gem5_release: str
    gem5_commit: str
    citations: list[dict[str, object]] = field(default_factory=list)

    def to_dict(self) -> dict:
        payload = asdict(self)
        return payload

    def to_json(self, indent: int = 2) -> str:
        return json.dumps(self.to_dict(), ensure_ascii=False, indent=indent, sort_keys=True)

    @property
    def digest(self) -> str:
        encoded = json.dumps(self.to_dict(), sort_keys=True, separators=(",", ":")).encode()
        return hashlib.sha256(encoded).hexdigest()


def _fingerprint(root: Path, rel: str) -> SourceFingerprint:
    path = root / rel
    if not path.is_file():
        raise FileNotFoundError(f"manifest 真源缺失：{path}")
    payload = path.read_bytes()
    return SourceFingerprint(rel, hashlib.sha256(payload).hexdigest(), len(payload))


def build(
    *,
    profile: HwProfile,
    model_name: str,
    layer: str,
    named_inputs: dict[str, object],
    excluded_costs: list[str],
    root: Path | None = None,
) -> Manifest:
    root = root or repo_root()
    release, commit = gem5_release(root)
    return Manifest(
        profile=profile.name,
        model=model_name,
        layer=layer,
        trust_level=TrustLevel.ENGINEERING_ESTIMATE.value,
        performance_valid=False,
        data_valid=False,
        notice=UNCALIBRATED_NOTICE,
        not_calibrated_reason=_NOT_CALIBRATED_REASON,
        named_inputs=dict(named_inputs),
        excluded_costs=list(excluded_costs),
        sources=[_fingerprint(root, rel) for rel in PROVENANCE_PATHS],
        gem5_release=release,
        gem5_commit=commit,
        citations=[
            {"doc": c.doc, "line": c.line, "quantity": c.quantity}
            for c in profile.citations
        ],
    )
