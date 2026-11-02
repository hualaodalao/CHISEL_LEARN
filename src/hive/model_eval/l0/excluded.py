"""L0 看不见什么——列明，不记 0。

两类条目：一是 L0 这个范式结构上不可能覆盖的（tile、补齐、搬运顺序）；二是
`model_analysis/` 文档口径本身的已知缺项，照搬时一并继承，不偷偷修掉。
"""

from __future__ import annotations

from ..common.workload import Phase, WorkloadPoint

# L0 是闭式 roofline，这些项不是漏算，是这个范式看不见。归 L1。
PARADIGM_BLIND_SPOTS = (
    "N 方向补齐到 T=16 的无效 MAC（L1 的 pad_macs）",
    "权重按窗口重装的 loadW 拍数与 1/17 阵列利用率（L1 第二层）",
    "local SRAM 驻留与否导致的重复搬运（容量按 profile，L1 residency）",
    "六路 DMA 端口的分口带宽与同源约束取 max（L1 ledger）",
    "VPU 非 GEMM 代价：RMSNorm / softmax / RoPE / SwiGLU（L1 vpu）",
    "引擎交接气泡与跨算子串行化（L1 timeline）",
)

# 四层之外、本工程整体不覆盖的。
OUT_OF_SCOPE = (
    "DRAM controller / system fabric 效率（arch.md 第 895 行注明实际可用带宽另受约束）",
    "周期级握手、FIFO 瞬时占用、gather 排空、retry（L2）",
    "结构延迟的真实值（L3 未校准，L1 用带 spec 引用的回退值）",
)

_DECODE_ONLY = (
    "采样：全词表 logits 的 argmax/top-p 比较，归 CPU",
    "embedding 查表的离散访存（非连续，带宽效率未建模）",
)

_PREFILL_ONLY = (
    # Qwen3-0.6B 文档 §9.2 的 57,344·S² 只含 QK^T 与 AV 两个矩阵，softmax 的
    # n_h·S²/2（本模型 224·S²）不在其中。照搬文档口径，差额在此列明而非默默补上。
    "softmax 的 S² 项（Qwen3-0.6B 为 224·S²，文档 §9.2 二次项未计入，约占 0.39%）",
    "分块 prefill 时 KV 的重复读取（文档只记一次写入）",
    "attention mask 构造与因果裁剪的元素级代价",
)


def for_workload(point: WorkloadPoint) -> list[str]:
    items = list(PARADIGM_BLIND_SPOTS)
    if point.phase is Phase.DECODE:
        items.extend(_DECODE_ONLY)
    elif point.phase is Phase.PREFILL:
        items.extend(_PREFILL_ONLY)
    items.extend(OUT_OF_SCOPE)
    return items
