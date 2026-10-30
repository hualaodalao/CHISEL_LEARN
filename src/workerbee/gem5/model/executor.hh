#ifndef GEM5_WORKERBEE_MODEL_EXECUTOR_HH
#define GEM5_WORKERBEE_MODEL_EXECUTOR_HH

#include <cstdint>
#include <deque>

#include "workerbee/gem5/model/geometry.hh"
#include "workerbee/gem5/model/stream.hh"

namespace gem5::workerbee
{

/* Coarse executor: one cycle per A beat and per B beat, a window barrier that
 * matches the hardware N-tile -> K-pass -> (T B beats, M A beats) order, and
 * C write-back only on the last K pass. Per-cycle skew, wavefront and deskew
 * detail is deliberately not modelled; the pipeline fill is a single
 * configurable latency after an output row is produced. */
class Executor
{
  public:
    Executor(const Hardware &hw, ReadStream &a, ReadStream &b,
             ReadStream &scaleA, ReadStream &scaleB, WriteStream &c,
             WriteStream &scaleC);

    void start(const Job &job, const Geometry &geo, int arrayLatency);
    void tick();

    bool running() const { return state_ != State::Idle; }
    bool donePulse() const { return donePulse_; }
    uint16_t progress() const;
    uint64_t cycles() const { return cycles_; }
    uint64_t arrayBusy() const { return arrayBusy_; }
    uint64_t stallA() const { return stallA_; }
    uint64_t stallB() const { return stallB_; }

  private:
    enum class State { Idle, LoadB, LoadA, NextTile, Draining };

    struct DelayedRow
    {
        uint64_t eligible = 0;
        Addr cAddr = 0;
        int cBytes = 0;
        bool hasScale = false;
        Addr scaleAddr = 0;
        bool scalePushed = false;
        bool cPushed = false;
    };

    void startNt();
    void enqueueOutput(uint64_t row);
    void pumpOutput();
    bool outputRoom() const;

    Hardware hw_;
    ReadStream &a_;
    ReadStream &b_;
    ReadStream &scaleA_;
    ReadStream &scaleB_;
    WriteStream &c_;
    WriteStream &scaleC_;

    State state_ = State::Idle;
    Job job_;
    Geometry geo_;
    int arrayLatency_ = 0;

    int nt_ = 0;
    int kt_ = 0;
    int row_ = 0;
    bool armed_ = false;
    int bLeft_ = 0;
    int aLeft_ = 0;

    uint64_t saBiasLeft_ = 0;
    uint64_t saRqLeft_ = 0;

    std::deque<DelayedRow> delayed_;

    bool donePulse_ = false;
    uint64_t cycles_ = 0;
    uint64_t arrayBusy_ = 0;
    uint64_t stallA_ = 0;
    uint64_t stallB_ = 0;
};

} // namespace gem5::workerbee

#endif
