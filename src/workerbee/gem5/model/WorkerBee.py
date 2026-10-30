from m5.objects.ClockedObject import ClockedObject
from m5.params import Param, RequestPort, VectorParam


class WorkerBeeGemm(ClockedObject):
    type = "WorkerBeeGemm"
    cxx_header = "workerbee/gem5/model/workerbee.hh"
    cxx_class = "gem5::workerbee::WorkerBee"

    hardware_profile = Param.String("workerbee_default_v4", "Frozen hardware profile")
    dma_a = RequestPort("A read DMA")
    dma_b = RequestPort("B read DMA")
    dma_c = RequestPort("C write DMA")
    dma_scale_a = RequestPort("ScaleA / bias / requant multiplier read DMA")
    dma_scale_b = RequestPort("ScaleB read DMA")
    dma_scale_c = RequestPort("ScaleC write DMA")

    # Cycle-annotated four-lane command trace, 22 words per batch:
    # [0] earliest cycle, [1] lane mask, then five words per lane
    # {op | reg0 << 8 | reg1 << 16, data0, data1, pc, tag}.
    command_batches = VectorParam.UInt64([], "Packed command batches")

    dma_outstanding = Param.Unsigned(4, "Outstanding requests per DMA port")
    array_pipeline_latency = Param.Unsigned(
        31, "Cycles from an output row's last A beat to its write-back"
    )
    response_ready = Param.Bool(True, "Command response stream always ready")
    exit_on_complete = Param.Bool(True, "Exit the simulation when the trace drains")
    result_path = Param.String("", "Path of the result JSON (empty: log only)")
