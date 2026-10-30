#ifndef GEM5_WORKERBEE_MODEL_STREAM_HH
#define GEM5_WORKERBEE_MODEL_STREAM_HH

#include <cstdint>
#include <deque>
#include <functional>
#include <vector>

#include "base/types.hh"

namespace gem5::workerbee
{

enum class DmaId : int
{
    A = 0,
    B = 1,
    C = 2,
    ScaleA = 3,
    ScaleB = 4,
    ScaleC = 5
};

constexpr int numDmaPorts = 6;

struct DmaStats
{
    uint64_t requests = 0;
    uint64_t responses = 0;
    uint64_t retries = 0;
    uint64_t bytes = 0;
    uint64_t errors = 0;
};

/* A send returns false when the downstream port cannot accept this cycle. The
 * caller must keep the request and try again after recvReqRetry. */
using SendFn = std::function<bool(DmaId, Addr, int, bool,
                                  const std::vector<bool> &)>;

/* Read side of one DMA port: logical beats addressed by a caller-supplied
 * function, in-order production, and a gather path that matches the hardware
 * (drain outstanding reads, then lo/hi aligned reads). */
class ReadStream
{
  public:
    ReadStream(DmaId id, int beatBytes, int depth, int outstandingMax,
               SendFn send);

    void configure(uint64_t count, const std::function<Addr(uint64_t)> &addrOf);
    void tick();
    void onResponse(bool err);
    void onRetry();

    bool hasBeat() const { return occupancy_ > 0; }
    bool pop();
    bool drained() const { return consumed_ == count_; }
    bool idle() const
    {
        return produced_ == count_ && reserved_ == 0 && inFlight_ == 0;
    }
    int occupancy() const { return occupancy_; }
    int inFlight() const { return inFlight_; }
    int reserved() const { return reserved_; }
    uint64_t produced() const { return produced_; }
    uint64_t consumed() const { return consumed_; }
    uint64_t count() const { return count_; }

    const DmaStats &stats() const { return stats_; }
    void clearStats() { stats_ = DmaStats(); }

  private:
    bool issue(Addr addr);

    DmaId id_;
    int beatBytes_;
    int depth_;
    int outstandingMax_;
    SendFn send_;

    uint64_t count_ = 0;
    std::function<Addr(uint64_t)> addrOf_;
    uint64_t produced_ = 0;
    uint64_t consumed_ = 0;
    int occupancy_ = 0;
    int reserved_ = 0;
    int inFlight_ = 0;
    bool retry_ = false;
    bool gatherActive_ = false;
    int gatherStage_ = 0;
    DmaStats stats_;
};

/* Write side of one DMA port. A row becomes one or more port-width beats; the
 * byte-enable mask keeps bytes outside the logical row unmodified. */
class WriteStream
{
  public:
    explicit WriteStream(DmaId id, int beatBytes, int depth, int outstandingMax,
                         SendFn send);

    bool pushRow(Addr addr, int validBytes);
    void tick();
    void onResponse(bool err);
    void onRetry();

    bool idle() const;
    int pendingRows() const { return int(rows_.size()) + (active_ ? 1 : 0); }

    const DmaStats &stats() const { return stats_; }
    void clearStats() { stats_ = DmaStats(); }

  private:
    struct Row
    {
        Addr addr = 0;
        int bytes = 0;
    };

    DmaId id_;
    int beatBytes_;
    int depth_;
    int outstandingMax_;
    SendFn send_;

    std::deque<Row> rows_;
    bool active_ = false;
    Row activeRow_;
    int beatIndex_ = 0;
    int inFlight_ = 0;
    bool retry_ = false;
    DmaStats stats_;
};

} // namespace gem5::workerbee

#endif
