from m5.objects.ClockedObject import ClockedObject
from m5.params import Param, RequestPort


class WorkerBeeGemm(ClockedObject):
    type = "WorkerBeeGemm"
    cxx_header = "workerbee/gem5/model/workerbee.hh"
    cxx_class = "gem5::workerbee::WorkerBee"

    hardware_profile = Param.String("workerbee_default_v4", "Frozen P0 hardware profile")
    dma_a = RequestPort("A read DMA")
    dma_b = RequestPort("B read DMA")
    dma_c = RequestPort("C write DMA")
    dma_scale_a = RequestPort("ScaleA / bias / requant multiplier read DMA")
    dma_scale_b = RequestPort("ScaleB read DMA")
    dma_scale_c = RequestPort("ScaleC write DMA")
