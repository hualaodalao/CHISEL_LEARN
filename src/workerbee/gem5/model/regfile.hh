#ifndef GEM5_WORKERBEE_MODEL_REGFILE_HH
#define GEM5_WORKERBEE_MODEL_REGFILE_HH

#include <array>
#include <cstdint>

#include "workerbee/gem5/model/geometry.hh"

namespace gem5::workerbee
{

/* Four-lane command bank and the IRQ/ERROR_CODE state. The class mirrors the
 * hardware RegIf contract: a batch is accepted only when no response is
 * pending, writes are arbitrated by lane, reads snapshot pre-batch values, and
 * the response stream drains one lane per cycle in ascending order. */
class RegFile
{
  public:
    explicit RegFile(const Hardware &hw);

    bool canAccept() const { return respCount_ == 0 && !respFired_; }
    bool accept(const Batch &batch, const LaneMask &valid);

    /* Advances the response stream and the IRQ/inFlight state by one cycle.
     * Returns true when a response fires this cycle. */
    bool tick(bool respReady);

    bool respValid() const { return respCount_ != 0; }
    const Response &response() const { return resp_; }

    bool startPulse() const { return startPulse_; }
    const Job &job() const { return job_; }
    uint32_t startPc() const { return startPc_; }
    uint8_t startTag() const { return startTag_; }

    /* Environment-level abort: the model refuses an unsupported input, which
     * must not be reported as a hardware config error. */
    void abortStart() { inFlight_ = false; startPending_ = false; }

    void queueDone() { doneQueued_ = true; }
    void queueConfigError(int code) { configQueued_ = true; configCode_ = code; }
    void queueDmaError(int bit) { dmaQueued_[bit] = true; }

    uint32_t irqRaw() const { return irqRaw_; }
    uint32_t irqMasked() const { return irqRaw_ & ~irqMask_; }
    uint32_t errorCode() const { return errorCode_; }
    bool inFlight() const { return inFlight_; }
    bool busy() const { return busy_; }
    uint16_t progress() const { return progress_; }
    uint32_t reg(int index) const { return regs_[index & 0x3F]; }

    void setStatus(bool busy, uint16_t progress)
    {
        busy_ = busy;
        progress_ = progress;
    }

    uint64_t acceptedBatches() const { return acceptedBatches_; }
    uint64_t rejectedLaneWrites() const { return rejectedWrites_; }
    uint64_t writeConflicts() const { return writeConflicts_; }
    uint64_t responsesSent() const { return responsesSent_; }

  private:
    bool rejectWrite(uint8_t addr, uint32_t data) const;
    void writeRegister(uint8_t addr, uint32_t data);
    uint32_t readRegister(uint8_t addr) const;

    Hardware hw_;
    std::array<uint32_t, 64> regs_{};
    uint32_t irqMask_ = irqMaskAll;
    uint32_t irqRaw_ = 0;
    uint32_t errorCode_ = 0;
    bool inFlight_ = false;

    std::array<bool, numLanes> respPending_{};
    std::array<uint32_t, numLanes> respData_{};
    std::array<bool, numLanes> respErr_{};
    int respCount_ = 0;
    Response resp_{};
    bool respFired_ = false;

    bool startPending_ = false;
    bool startPulse_ = false;
    Job job_;
    uint32_t startPc_ = 0;
    uint8_t startTag_ = 0;

    uint32_t clearThisCycle_ = 0;
    bool doneQueued_ = false;
    bool configQueued_ = false;
    bool conflictQueued_ = false;
    int configCode_ = 0;
    std::array<bool, 8> dmaQueued_{};

    bool busy_ = false;
    uint16_t progress_ = 0;

    uint64_t acceptedBatches_ = 0;
    uint64_t rejectedWrites_ = 0;
    uint64_t writeConflicts_ = 0;
    uint64_t responsesSent_ = 0;
};

} // namespace gem5::workerbee

#endif
