"""可选的 torch 交叉校验：只用来对工作量，不用来测时间。

torch 的 wall time 与 Hive 无关——它不知道 T=16 补齐、512 KiB 驻留、显式 SRAM
搬运。这里只拿 `FlopCounterMode` 的 MAC 计数和 `ModelPlan` 对表。torch 或权重缺失
时返回 skipped，绝不让主流程失败。
"""

from __future__ import annotations

from dataclasses import dataclass

from ..common.workload import Phase, WorkloadPoint
from ..frontend.plan import ModelPlan
from .accounts import work_account

TOLERANCE = 0.02


@dataclass(frozen=True)
class CrossCheck:
    status: str
    reason: str
    spec_mac: float | None = None
    torch_mac: float | None = None
    rel_error: float | None = None

    @property
    def ok(self) -> bool:
        return self.status in ("ok", "skipped")


def _import_torch():
    try:
        import torch
        from torch.utils.flop_counter import FlopCounterMode
    except ImportError as exc:
        return None, str(exc)
    return (torch, FlopCounterMode), None


def cross_check(
    model: ModelPlan,
    point: WorkloadPoint,
    *,
    module=None,
    example_inputs=None,
    tolerance: float = TOLERANCE,
) -> CrossCheck:
    """用 FlopCounterMode 数一遍 MAC，与闭式结果对表。

    `module` 由调用方提供（例如 transformers 加载的 Qwen3ForCausalLM）。本函数不去
    下载权重，也不猜模型实现——拿不到就 skipped。
    """
    imported, error = _import_torch()
    if imported is None:
        return CrossCheck("skipped", f"torch 不可用：{error}")
    if module is None or example_inputs is None:
        return CrossCheck("skipped", "调用方未提供 torch module 与示例输入")
    if point.phase is Phase.FRAME:
        return CrossCheck("skipped", "frame 相位的 torch 对表未实现")

    torch, FlopCounterMode = imported
    spec_mac = work_account(model, point).total

    counter = FlopCounterMode(display=False)
    with counter, torch.no_grad():
        module(*example_inputs)
    # FlopCounterMode 记的是 FLOP，矩阵乘按 2 FLOP/MAC。
    torch_mac = counter.get_total_flops() / 2.0

    if torch_mac == 0:
        return CrossCheck("skipped", "FlopCounterMode 返回 0，可能未捕获到算子")

    rel = abs(torch_mac - spec_mac) / spec_mac
    status = "ok" if rel <= tolerance else "mismatch"
    reason = (
        f"相对差 {rel:.4%}，容差 {tolerance:.2%}"
        if status == "ok"
        else f"相对差 {rel:.4%} 超过容差 {tolerance:.2%}；附录 B 为真源，先查 torch 侧口径"
    )
    return CrossCheck(status, reason, spec_mac, torch_mac, rel)
