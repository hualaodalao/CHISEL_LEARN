#include "workerbee/gem5/model/executor.hh"

#include <algorithm>

namespace gem5::workerbee
{

namespace
{

Addr
aAddress(const Job &job, const Geometry &geo, uint64_t beat)
{
    const uint64_t perNt = uint64_t(geo.kt) * geo.m;
    if (perNt == 0) {
        return Addr(job.aAddr);
    }
    const uint64_t nt = beat / perNt;
    const uint64_t rest = beat % perNt;
    const uint64_t kt = rest / geo.m;
    const uint64_t row = rest % geo.m;
    (void)nt; // A is rescanned for every N tile; the addresses repeat.
    return Addr(job.aAddr + kt * geo.aKtStride + row * geo.aRowStride);
}

Addr
bAddress(const Job &job, const Geometry &geo, uint64_t beat)
{
    const uint64_t perNt = uint64_t(geo.kt) * geo.t;
    if (perNt == 0) {
        return Addr(job.bAddr);
    }
    const uint64_t nt = beat / perNt;
    const uint64_t rest = beat % perNt;
    const uint64_t kt = rest / geo.t;
    const uint64_t j = rest % geo.t;
    if (job.loadWMode) {
        return Addr(job.bAddr +
                    (nt + 1) * (geo.t - 1) * geo.bRowStride +
                    (nt * geo.kt + kt) * geo.bKtStride -
                    j * geo.bRowStride);
    }
    return Addr(job.bAddr + nt * geo.bKtStride +
                (geo.t - 1 - j) * geo.bRowStride +
                kt * geo.t * geo.bRowStride);
}

Addr
scaleBAddress(const Job &job, const Geometry &geo, uint64_t beat)
{
    const uint64_t perNt = uint64_t(geo.kt) * geo.t;
    if (perNt == 0) {
        return Addr(job.scaleBAddr);
    }
    const uint64_t nt = beat / perNt;
    const uint64_t rest = beat % perNt;
    const uint64_t kt = rest / geo.t;
    const uint64_t j = rest % geo.t;
    return Addr(job.scaleBAddr +
                ((nt + 1) * (geo.t - 1) - j) * geo.scaleRRowStride +
                (nt * geo.kt + kt) * geo.scaleRKtStride);
}

} // namespace

Executor::Executor(const Hardware &hw, ReadStream &a, ReadStream &b,
                   ReadStream &scaleA, ReadStream &scaleB, WriteStream &c,
                   WriteStream &scaleC)
    : hw_(hw), a_(a), b_(b), scaleA_(scaleA), scaleB_(scaleB), c_(c),
      scaleC_(scaleC)
{
}

void
Executor::start(const Job &job, const Geometry &geo, int arrayLatency)
{
    job_ = job;
    geo_ = geo;
    arrayLatency_ = arrayLatency;
    nt_ = 0;
    kt_ = 0;
    row_ = 0;
    armed_ = false;
    bLeft_ = 0;
    aLeft_ = 0;
    cycles_ = 0;
    arrayBusy_ = 0;
    stallA_ = 0;
    stallB_ = 0;
    donePulse_ = false;
    delayed_.clear();

    const Job j = job_;
    const Geometry g = geo_;
    a_.configure(g.aBeats(), [j, g](uint64_t p) { return aAddress(j, g, p); });
    b_.configure(g.bBeats(), [j, g](uint64_t p) { return bAddress(j, g, p); });
    scaleB_.configure(g.mxB ? g.bBeats() : 0,
                      [j, g](uint64_t p) { return scaleBAddress(j, g, p); });
    startNt();

    if (g.nt == 0 || g.kt == 0 || g.m == 0) {
        state_ = State::Draining;
    } else {
        state_ = State::LoadB;
    }
}

void
Executor::startNt()
{
    const Job j = job_;
    const Geometry g = geo_;
    const int nt = nt_;
    const uint64_t biasCount = j.biasOn ? uint64_t(g.t) : 0;
    const uint64_t rqCount = j.requantOn ? uint64_t(g.t) : 0;
    const uint64_t scaleCount = g.mxA ? uint64_t(g.kt) * g.m : 0;
    saBiasLeft_ = biasCount;
    saRqLeft_ = rqCount;

    scaleA_.configure(biasCount + rqCount + scaleCount,
        [j, g, nt, biasCount, rqCount](uint64_t p) -> Addr {
            if (p < biasCount) {
                return Addr(j.biasAddr + uint64_t(nt) * g.t * 4 + p * 4);
            }
            p -= biasCount;
            if (p < rqCount) {
                return Addr(j.rqMAddr + uint64_t(nt) * g.t * 4 + p * 4);
            }
            p -= rqCount;
            if (g.m == 0) {
                return Addr(j.scaleAAddr);
            }
            return Addr(j.scaleAAddr + (p / g.m) * g.scaleAKtStride +
                        (p % g.m) * g.scaleARowStride);
        });
}

void
Executor::enqueueOutput(uint64_t row)
{
    DelayedRow entry;
    entry.eligible = cycles_ + arrayLatency_;
    entry.cAddr = job_.cAddr + uint64_t(nt_) * geo_.cColTileStride +
                  row * geo_.cRowStride;
    entry.cBytes = geo_.cWriteBytes;
    if (geo_.mxOut) {
        entry.hasScale = true;
        entry.scaleAddr = job_.scaleCAddr + row * geo_.scaleCRowStride +
                          (uint64_t(nt_) * geo_.t) / geo_.cBlock;
    }
    delayed_.push_back(entry);
}

bool
Executor::outputRoom() const
{
    return uint64_t(delayed_.size()) + c_.pendingRows() <
           uint64_t(hw_.cBufferDepth) + 1;
}

void
Executor::pumpOutput()
{
    while (!delayed_.empty() && delayed_.front().eligible <= cycles_) {
        DelayedRow &entry = delayed_.front();
        if (entry.hasScale && !entry.scalePushed) {
            if (!scaleC_.pushRow(entry.scaleAddr, 1)) {
                return;
            }
            entry.scalePushed = true;
        }
        if (!entry.cPushed) {
            if (!c_.pushRow(entry.cAddr, entry.cBytes)) {
                return;
            }
            entry.cPushed = true;
        }
        delayed_.pop_front();
    }
}

void
Executor::tick()
{
    donePulse_ = false;
    if (state_ == State::Idle) {
        return;
    }
    cycles_++;
    pumpOutput();

    while (saBiasLeft_ != 0 && scaleA_.hasBeat()) {
        scaleA_.pop();
        saBiasLeft_--;
    }
    while (saRqLeft_ != 0 && scaleA_.hasBeat()) {
        scaleA_.pop();
        saRqLeft_--;
    }

    switch (state_) {
      case State::LoadB:
        if (!armed_) {
            const bool ready = b_.occupancy() >= geo_.t &&
                (!geo_.mxB || scaleB_.occupancy() >= geo_.t);
            if (!ready) {
                stallB_++;
                break;
            }
            armed_ = true;
            bLeft_ = geo_.t;
        }
        if (bLeft_ == 0) {
            armed_ = false;
            state_ = State::LoadA;
            aLeft_ = geo_.m;
            row_ = 0;
        } else if (b_.hasBeat() && (!geo_.mxB || scaleB_.hasBeat())) {
            b_.pop();
            if (geo_.mxB) {
                scaleB_.pop();
            }
            bLeft_--;
        } else {
            stallB_++;
        }
        break;

      case State::LoadA:
        if (aLeft_ == 0) {
            state_ = State::NextTile;
            break;
        }
        if (a_.hasBeat() && (!geo_.mxA || scaleA_.hasBeat()) &&
            saBiasLeft_ == 0 && saRqLeft_ == 0 && outputRoom()) {
            a_.pop();
            if (geo_.mxA) {
                scaleA_.pop();
            }
            arrayBusy_++;
            if (kt_ == geo_.kt - 1) {
                enqueueOutput(uint64_t(row_));
            }
            row_++;
            aLeft_--;
        } else {
            stallA_++;
        }
        break;

      case State::NextTile:
        kt_++;
        if (kt_ == geo_.kt) {
            kt_ = 0;
            nt_++;
            if (nt_ == geo_.nt) {
                state_ = State::Draining;
                break;
            }
            startNt();
        }
        state_ = State::LoadB;
        armed_ = false;
        break;

      case State::Draining:
        if (delayed_.empty() && c_.idle() && scaleC_.idle()) {
            donePulse_ = true;
            state_ = State::Idle;
        }
        break;

      case State::Idle:
        break;
    }
}

uint16_t
Executor::progress() const
{
    const uint64_t done = uint64_t(nt_) * geo_.kt + kt_;
    return uint16_t(std::min<uint64_t>(done, 0xFFFF));
}

} // namespace gem5::workerbee
