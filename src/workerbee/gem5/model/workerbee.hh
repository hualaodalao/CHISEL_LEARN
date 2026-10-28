#ifndef GEM5_WORKERBEE_MODEL_WORKERBEE_HH
#define GEM5_WORKERBEE_MODEL_WORKERBEE_HH

#include <string>

#include "mem/port.hh"
#include "params/WorkerBeeGemm.hh"
#include "sim/clocked_object.hh"

namespace gem5::workerbee
{

class WorkerBee : public ClockedObject
{
  private:
    class DmaPort : public RequestPort
    {
      public:
        explicit DmaPort(const std::string &name);

      protected:
        bool recvTimingResp(PacketPtr pkt) override;
        void recvReqRetry() override;
    };

    DmaPort dmaA;
    DmaPort dmaB;
    DmaPort dmaC;
    DmaPort dmaScaleA;
    DmaPort dmaScaleB;
    DmaPort dmaScaleC;

  public:
    explicit WorkerBee(const WorkerBeeGemmParams &p);
    Port &getPort(const std::string &if_name,
                  PortID idx = InvalidPortID) override;
};

} // namespace gem5::workerbee

#endif
