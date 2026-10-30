#ifndef GEM5_WORKERBEE_MODEL_WORKERBEE_HH
#define GEM5_WORKERBEE_MODEL_WORKERBEE_HH

#include <deque>
#include <string>
#include <vector>

#include "mem/port.hh"
#include "params/WorkerBeeGemm.hh"
#include "sim/clocked_object.hh"
#include "sim/eventq.hh"
#include "workerbee/gem5/model/executor.hh"
#include "workerbee/gem5/model/geometry.hh"
#include "workerbee/gem5/model/regfile.hh"
#include "workerbee/gem5/model/stream.hh"

namespace gem5::workerbee
{

/* L1 coarse-grained WorkerBee GEMM model.
 *
 * Command batches come from a pre-packed cycle-annotated trace. The model
 * owns the four-lane register bank, mirrors the hardware configuration
 * checks, and drives six real gem5 request ports with the hardware beat
 * sequence. It does not execute any numerical work: C payloads are
 * placeholders and every result is marked data_valid=false. */
class WorkerBee : public ClockedObject
{
  private:
    class DmaPort : public RequestPort
    {
      public:
        DmaPort(const std::string &name, WorkerBee &owner, DmaId id);

      protected:
        bool recvTimingResp(PacketPtr pkt) override;
        void recvReqRetry() override;

      private:
        WorkerBee &owner_;
        DmaId id_;
    };

    class TickEvent : public Event
    {
      public:
        explicit TickEvent(WorkerBee &owner) : owner_(owner) {}
        void process() override { owner_.tick(); }
        const char *description() const override
        {
            return "WorkerBee GEMM model clock";
        }

      private:
        WorkerBee &owner_;
    };

    struct TraceBatch
    {
        uint64_t cycle = 0;
        LaneMask lanes{};
        Batch commands{};
    };

    Hardware hw_;
    RegFile regs_;
    DmaPort dmaA_;
    DmaPort dmaB_;
    DmaPort dmaC_;
    DmaPort dmaScaleA_;
    DmaPort dmaScaleB_;
    DmaPort dmaScaleC_;

    ReadStream a_;
    ReadStream b_;
    ReadStream scaleA_;
    ReadStream scaleB_;
    WriteStream c_;
    WriteStream scaleC_;
    Executor executor_;

    TickEvent tickEvent_;
    std::deque<TraceBatch> trace_;
    Job job_;
    Geometry geo_;
    bool haveJob_ = false;

    bool exitOnComplete_ = true;
    std::string resultPath_;
    int arrayLatency_ = 0;
    bool responseReady_ = true;

    uint64_t cycle_ = 0;
    uint64_t startCycle_ = 0;
    uint64_t doneCycle_ = 0;
    uint64_t gemmCycles_ = 0;
    uint64_t jobsStarted_ = 0;
    uint64_t jobsDone_ = 0;
    uint64_t configErrors_ = 0;
    uint64_t inputErrors_ = 0;
    uint64_t scheduledOps_ = 0;
    uint64_t executorCycles_ = 0;
    uint64_t arrayBusy_ = 0;
    uint64_t stallA_ = 0;
    uint64_t stallB_ = 0;
    bool lastStartErrored_ = false;
    int lastErrorCode_ = 0;
    uint64_t configErrorAt_ = 0;
    bool finished_ = false;
    std::vector<std::string> errors_;

    RequestPort &port(DmaId id);
    bool send(DmaId id, Addr addr, int size, bool write,
              const std::vector<bool> &byteEnable);
    void loadTrace(const std::vector<uint64_t> &words);
    bool inputSupported(const Job &job) const;
    void finish();

  public:
    WorkerBee(const WorkerBeeGemmParams &p);

    void startup() override;
    Port &getPort(const std::string &if_name, PortID idx = InvalidPortID) override;
    void tick();

    void dmaResponse(DmaId id, PacketPtr pkt);
    void dmaRetry(DmaId id);
};

} // namespace gem5::workerbee

#endif
