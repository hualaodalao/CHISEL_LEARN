"""报告骨架。L0 与 L1 共用：头部声明 + 表格 + 未建模项清单。"""

from __future__ import annotations

from dataclasses import dataclass

from .manifest import Manifest


def format_time(seconds: float) -> str:
    if seconds >= 1.0:
        return f"{seconds:.3f} s"
    if seconds >= 1e-3:
        return f"{seconds * 1e3:.3f} ms"
    return f"{seconds * 1e6:.3f} µs"


def format_bytes(count: float) -> str:
    if count >= 1e9:
        return f"{count / 1e9:.4f} GB"
    if count >= 1e6:
        return f"{count / 1e6:.3f} MB"
    if count >= 1e3:
        return f"{count / 1e3:.3f} kB"
    return f"{count:.0f} B"


def format_mac(count: float) -> str:
    if count >= 1e12:
        return f"{count / 1e12:.4f} TMAC"
    if count >= 1e9:
        return f"{count / 1e9:.4f} GMAC"
    if count >= 1e6:
        return f"{count / 1e6:.3f} MMAC"
    return f"{count:.0f} MAC"


@dataclass
class Table:
    headers: tuple[str, ...]
    rows: list[tuple[str, ...]]
    align_right_from: int = 1

    def render(self) -> str:
        widths = [len(h) for h in self.headers]
        for row in self.rows:
            for i, cell in enumerate(row):
                widths[i] = max(widths[i], len(cell))

        def line(cells: tuple[str, ...]) -> str:
            parts = []
            for i, cell in enumerate(cells):
                parts.append(
                    cell.rjust(widths[i]) if i >= self.align_right_from else cell.ljust(widths[i])
                )
            return "  ".join(parts).rstrip()

        out = [line(self.headers), "  ".join("-" * w for w in widths)]
        out.extend(line(row) for row in self.rows)
        return "\n".join(out)


def header_block(manifest: Manifest) -> str:
    lines = [
        f"[{manifest.notice}]",
        f"layer={manifest.layer}  model={manifest.model}  profile={manifest.profile}",
        f"trust_level={manifest.trust_level}  performance_valid={manifest.performance_valid}"
        f"  data_valid={manifest.data_valid}",
        f"未校准原因：{manifest.not_calibrated_reason}",
    ]
    if manifest.named_inputs:
        named = "  ".join(f"{k}={v}" for k, v in sorted(manifest.named_inputs.items()))
        lines.append(f"命名输入：{named}")
    lines.append(f"manifest_digest={manifest.digest[:16]}")
    return "\n".join(lines)


def excluded_block(manifest: Manifest) -> str:
    if not manifest.excluded_costs:
        return "未建模项：无（这通常说明漏列了，而不是真的全覆盖）"
    lines = ["未建模项（列明，不记 0）："]
    lines.extend(f"  - {item}" for item in manifest.excluded_costs)
    return "\n".join(lines)
