#include "workerbee/gem5/model/stream.hh"

#include <algorithm>

namespace gem5::workerbee
{

ReadStream::ReadStream(DmaId id, int beatBytes, int depth, int outstandingMax,
                       SendFn send)
    : id_(id), beatBytes_(beatBytes), depth_(depth),
      outstandingMax_(outstandingMax), send_(std::move(send))
{
}

void
ReadStream::configure(uint64_t count,
                      const std::function<Addr(uint64_t)> &addrOf)
{
    count_ = count;
    addrOf_ = addrOf;
    produced_ = 0;
    consumed_ = 0;
    occupancy_ = 0;
    reserved_ = 0;
    inFlight_ = 0;
    retry_ = false;
    gatherActive_ = false;
    gatherStage_ = 0;
}

bool
ReadStream::issue(Addr addr)
{
    const std::vector<bool> byteEnable;
    if (!send_(id_, addr, beatBytes_, false, byteEnable)) {
        retry_ = true;
        return false;
    }
    stats_.requests++;
    stats_.bytes += beatBytes_;
    inFlight_++;
    return true;
}

void
ReadStream::tick()
{
    if (retry_) {
        return;
    }
    /* The gather hi read must still be issued after produced_ reaches count_:
     * when the final logical beat is unaligned, its lo read already consumed
     * the last production slot, so checking the count first would strand the
     * beat in reserved_ forever. */
    if (gatherActive_) {
        if (gatherStage_ == 1 && inFlight_ < outstandingMax_) {
            const Addr hi = (addrOf_(produced_ - 1) & ~Addr(beatBytes_ - 1)) +
                            beatBytes_;
            if (issue(hi)) {
                gatherStage_ = 2;
            }
        }
        return;
    }
    if (produced_ == count_) {
        return;
    }
    if (occupancy_ + reserved_ >= depth_ || inFlight_ >= outstandingMax_) {
        return;
    }

    const Addr addr = addrOf_(produced_);
    if (addr % beatBytes_ != 0) {
        /* Hardware drains outstanding reads before gathering an unaligned beat
         * so the lo/hi pair cannot interleave with older responses. */
        if (inFlight_ != 0) {
            return;
        }
        if (!issue(addr & ~Addr(beatBytes_ - 1))) {
            return;
        }
        produced_++;
        reserved_++;
        gatherActive_ = true;
        gatherStage_ = 0;
        return;
    }

    if (!issue(addr)) {
        return;
    }
    produced_++;
    reserved_++;
}

void
ReadStream::onResponse(bool err)
{
    inFlight_--;
    stats_.responses++;
    if (err) {
        stats_.errors++;
    }
    if (gatherActive_) {
        if (gatherStage_ == 0) {
            gatherStage_ = 1;
        } else {
            gatherActive_ = false;
            gatherStage_ = 0;
            occupancy_++;
            reserved_--;
        }
    } else {
        occupancy_++;
        reserved_--;
    }
}

void
ReadStream::onRetry()
{
    retry_ = false;
    stats_.retries++;
}

bool
ReadStream::pop()
{
    if (occupancy_ == 0) {
        return false;
    }
    occupancy_--;
    consumed_++;
    return true;
}

WriteStream::WriteStream(DmaId id, int beatBytes, int depth,
                         int outstandingMax, SendFn send)
    : id_(id), beatBytes_(beatBytes), depth_(depth),
      outstandingMax_(outstandingMax), send_(std::move(send))
{
}

bool
WriteStream::pushRow(Addr addr, int validBytes)
{
    if (pendingRows() >= depth_) {
        return false;
    }
    rows_.push_back({addr, validBytes});
    return true;
}

void
WriteStream::tick()
{
    if (retry_ || inFlight_ >= outstandingMax_) {
        return;
    }
    if (!active_) {
        if (rows_.empty()) {
            return;
        }
        activeRow_ = rows_.front();
        rows_.pop_front();
        active_ = true;
        beatIndex_ = 0;
    }

    const Addr beatAddr = activeRow_.addr + Addr(beatIndex_) * beatBytes_;
    const int lanes = std::min(beatBytes_,
                               activeRow_.bytes - beatIndex_ * beatBytes_);
    const Addr base = beatAddr & ~Addr(beatBytes_ - 1);
    const int off = int(beatAddr - base);
    const int valid = std::min(lanes, beatBytes_ - off);
    std::vector<bool> byteEnable(beatBytes_, false);
    for (int i = off; i < off + valid; i++) {
        byteEnable[i] = true;
    }

    if (!send_(id_, base, beatBytes_, true, byteEnable)) {
        retry_ = true;
        return;
    }
    stats_.requests++;
    stats_.bytes += beatBytes_;
    inFlight_++;
    beatIndex_++;
    if (beatIndex_ * beatBytes_ >= activeRow_.bytes) {
        active_ = false;
        beatIndex_ = 0;
    }
}

void
WriteStream::onResponse(bool err)
{
    inFlight_--;
    stats_.responses++;
    if (err) {
        stats_.errors++;
    }
}

void
WriteStream::onRetry()
{
    retry_ = false;
    stats_.retries++;
}

bool
WriteStream::idle() const
{
    return rows_.empty() && !active_ && inFlight_ == 0;
}

} // namespace gem5::workerbee
