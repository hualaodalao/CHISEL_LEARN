#include "workerbee/gem5/model/regfile.hh"

#include <algorithm>

namespace gem5::workerbee
{

namespace
{

constexpr uint32_t controlReadMask = 0x7577FFFF;
constexpr uint32_t rqParamReadMask = 0xFFFF003F;

int
lowestPending(const std::array<bool, numLanes> &pending)
{
    for (int lane = 0; lane < numLanes; lane++) {
        if (pending[lane]) {
            return lane;
        }
    }
    return -1;
}

} // namespace

RegFile::RegFile(const Hardware &hw) : hw_(hw)
{
}

bool
RegFile::rejectWrite(uint8_t addr, uint32_t data) const
{
    const bool readOnly = addr == RegStatus || addr == RegIrqRaw ||
                          addr == RegIrqMasked || addr == RegErrorCode;
    const bool needsIdle = addr <= RegRqParam || (addr == RegStart && data != 0);
    return readOnly || (inFlight_ && needsIdle);
}

void
RegFile::writeRegister(uint8_t addr, uint32_t data)
{
    switch (addr) {
      case RegM: regs_[RegM] = data & 0xFFFF; break;
      case RegNK: regs_[RegNK] = data; break;
      case RegAAddr: case RegBAddr: case RegCAddr: case RegControl:
      case RegScaleAAddr: case RegScaleBAddr: case RegScaleCAddr:
      case RegBiasAddr: case RegRqMAddr: case RegRqParam:
        regs_[addr] = data;
        break;
      case RegIrqMask: irqMask_ = data & irqMaskAll; break;
      default: break;
    }
}

uint32_t
RegFile::readRegister(uint8_t addr) const
{
    switch (addr) {
      case RegM: return regs_[RegM] & 0xFFFF;
      case RegNK:
        return (regs_[RegNK] & 0xFFFF0000) | (regs_[RegNK] & 0xFFFF);
      case RegAAddr: case RegBAddr: case RegCAddr:
      case RegScaleAAddr: case RegScaleBAddr: case RegScaleCAddr:
      case RegBiasAddr: case RegRqMAddr:
        return regs_[addr];
      case RegControl: return regs_[RegControl] & controlReadMask;
      case RegRqParam: return regs_[RegRqParam] & rqParamReadMask;
      case RegStatus:
        return (uint32_t(progress_) << 16) | (uint32_t(inFlight_) << 2) |
               uint32_t(busy_);
      case RegIrqRaw: return irqRaw_;
      case RegIrqMask: return irqMask_;
      case RegIrqMasked: return irqRaw_ & ~irqMask_;
      case RegErrorCode: return errorCode_;
      default: return 0;
    }
}

bool
RegFile::accept(const Batch &batch, const LaneMask &valid)
{
    if (!canAccept()) {
        return false;
    }

    std::array<uint32_t, numLanes> data{};
    std::array<bool, numLanes> err{};
    std::array<bool, numLanes> acceptedWrite{};

    /* Reads snapshot the pre-batch bank before any winning write lands. */
    for (int lane = 0; lane < numLanes; lane++) {
        if (!valid[lane]) {
            continue;
        }
        const Command &cmd = batch[lane];
        const bool write1 = cmd.op == uint8_t(Op::RegWrite1);
        const bool write2 = cmd.op == uint8_t(Op::RegWrite2);
        const bool read = cmd.op == uint8_t(Op::RegRead);
        if (read) {
            data[lane] = readRegister(cmd.reg0);
        }
        const bool rejected = rejectWrite(cmd.reg0, cmd.data0) ||
            (write2 && rejectWrite(cmd.reg1, cmd.data1));
        if (rejected) {
            rejectedWrites_++;
        }
        err[lane] = !read && (!(write1 || write2) || rejected);
        acceptedWrite[lane] = (write1 || write2) && !rejected;
    }

    uint32_t clearBits = 0;
    bool start = false;
    uint32_t startPc = 0;
    uint8_t startTag = 0;
    std::array<std::pair<uint8_t, uint8_t>, numLanes * 2> targets{};
    int targetCount = 0;

    for (int lane = 0; lane < numLanes; lane++) {
        if (!acceptedWrite[lane]) {
            continue;
        }
        const Command &cmd = batch[lane];
        writeRegister(cmd.reg0, cmd.data0);
        if (cmd.op == uint8_t(Op::RegWrite2)) {
            writeRegister(cmd.reg1, cmd.data1);
        }

        const bool clear0 = cmd.reg0 == RegIrqClear;
        const bool clear1 = cmd.op == uint8_t(Op::RegWrite2) &&
                            cmd.reg1 == RegIrqClear;
        if (clear0 || clear1) {
            clearBits = (clear0 ? cmd.data0 & irqMaskAll : 0u) |
                        (clear1 ? cmd.data1 & irqMaskAll : 0u);
        }

        const bool trigger0 = cmd.reg0 == RegStart && cmd.data0 != 0;
        const bool trigger1 = cmd.op == uint8_t(Op::RegWrite2) &&
                              cmd.reg1 == RegStart && cmd.data1 != 0;
        if (trigger0 || trigger1) {
            start = true;
            startPc = cmd.pc;
            startTag = cmd.tag;
        }

        auto target = [&](uint8_t addr, uint32_t value) {
            const bool isTarget = addr <= RegRqParam || addr == RegIrqMask ||
                addr == RegIrqClear || (addr == RegStart && value != 0);
            if (isTarget && targetCount < int(targets.size())) {
                targets[targetCount++] = {uint8_t(lane), addr};
            }
        };
        target(cmd.reg0, cmd.data0);
        if (cmd.op == uint8_t(Op::RegWrite2)) {
            target(cmd.reg1, cmd.data1);
        }
    }

    bool conflict = false;
    for (int i = 0; i < targetCount && !conflict; i++) {
        for (int j = i + 1; j < targetCount; j++) {
            if (targets[i].first != targets[j].first &&
                targets[i].second == targets[j].second) {
                conflict = true;
                break;
            }
        }
    }
    if (conflict) {
        writeConflicts_++;
        conflictQueued_ = true;
    }

    if (start) {
        job_ = jobFromRegisters(regs_.data());
        startPc_ = startPc;
        startTag_ = startTag;
        startPending_ = true;
        inFlight_ = true;
    }

    clearThisCycle_ = clearBits;

    respCount_ = 0;
    for (int lane = 0; lane < numLanes; lane++) {
        respPending_[lane] = valid[lane];
        respData_[lane] = data[lane];
        respErr_[lane] = err[lane];
        respCount_ += valid[lane] ? 1 : 0;
    }
    acceptedBatches_++;
    return true;
}

bool
RegFile::tick(bool respReady)
{
    startPulse_ = startPending_;
    startPending_ = false;

    respFired_ = false;
    if (respCount_ != 0 && respReady) {
        const int lane = lowestPending(respPending_);
        resp_.data = respData_[lane];
        resp_.lane = uint8_t(lane);
        resp_.err = respErr_[lane];
        respPending_[lane] = false;
        respCount_--;
        responsesSent_++;
        respFired_ = true;
    }

    uint32_t events = 0;
    if (doneQueued_) {
        events |= 1u << IrqDone;
    }
    if (configQueued_) {
        events |= 1u << IrqConfigError;
    }
    for (int bit = 0; bit < 8; bit++) {
        if (dmaQueued_[bit]) {
            events |= 1u << (IrqDmaA + bit);
        }
    }
    if (conflictQueued_) {
        events |= 1u << IrqWriteConflict;
    }

    const uint32_t oldRaw = irqRaw_;
    irqRaw_ = (irqRaw_ & ~clearThisCycle_) | events;
    if (clearThisCycle_ & (1u << IrqConfigError)) {
        errorCode_ = 0;
    }
    if (configQueued_ &&
        (!(oldRaw & (1u << IrqConfigError)) || (clearThisCycle_ & (1u << IrqConfigError)))) {
        errorCode_ = uint32_t(configCode_);
    }
    if (doneQueued_ || configQueued_) {
        inFlight_ = false;
    }

    clearThisCycle_ = 0;
    doneQueued_ = false;
    configQueued_ = false;
    conflictQueued_ = false;
    dmaQueued_.fill(false);
    return respFired_;
}

} // namespace gem5::workerbee
