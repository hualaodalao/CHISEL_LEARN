#include "workerbee/gem5/model/workerbee.hh"

#include "base/logging.hh"

namespace gem5::workerbee
{

WorkerBee::DmaPort::DmaPort(const std::string &name) : RequestPort(name)
{
}

bool
WorkerBee::DmaPort::recvTimingResp(PacketPtr)
{
    panic("%s: unexpected DMA response; P0 does not issue requests", name());
}

void
WorkerBee::DmaPort::recvReqRetry()
{
    panic("%s: unexpected DMA retry; P0 does not issue requests", name());
}

WorkerBee::WorkerBee(const WorkerBeeGemmParams &p)
    : ClockedObject(p),
      dmaA(name() + ".dma_a"),
      dmaB(name() + ".dma_b"),
      dmaC(name() + ".dma_c"),
      dmaScaleA(name() + ".dma_scale_a"),
      dmaScaleB(name() + ".dma_scale_b"),
      dmaScaleC(name() + ".dma_scale_c")
{
    fatal_if(p.hardware_profile != "workerbee_default_v4",
             "%s: unsupported P0 hardware profile %s",
             name(), p.hardware_profile);
}

Port &
WorkerBee::getPort(const std::string &if_name, PortID idx)
{
    if (if_name == "dma_a") return dmaA;
    if (if_name == "dma_b") return dmaB;
    if (if_name == "dma_c") return dmaC;
    if (if_name == "dma_scale_a") return dmaScaleA;
    if (if_name == "dma_scale_b") return dmaScaleB;
    if (if_name == "dma_scale_c") return dmaScaleC;
    return ClockedObject::getPort(if_name, idx);
}

} // namespace gem5::workerbee
