#include "workerbee/gem5/model/workerbee.hh"

#include <cstring>
#include <fstream>
#include <sstream>

#include "base/logging.hh"
#include "mem/packet.hh"
#include "mem/request.hh"
#include "sim/sim_exit.hh"

namespace gem5::workerbee
{

namespace
{

/* Packed trace layout, one batch per record:
 *   [0] earliest cycle, [1] lane mask, then five words per lane
 *   {op|reg0<<8|reg1<<16, data0, data1, pc, tag}. */
constexpr size_t wordsPerLane = 5;
constexpr size_t wordsPerBatch = 2 + wordsPerLane * numLanes;

/* The configuration check runs two cycles after the START pulse (CalcConfig
 * C0/C1). The error is queued at that point and becomes visible in IRQ_RAW on
 * the following edge. */
constexpr uint64_t configErrorLatency = 2;

const char *
portName(DmaId id)
{
    switch (id) {
      case DmaId::A: return "a";
      case DmaId::B: return "b";
      case DmaId::C: return "c";
      case DmaId::ScaleA: return "scale_a";
      case DmaId::ScaleB: return "scale_b";
      case DmaId::ScaleC: return "scale_c";
    }
    return "unknown";
}

std::string
statJson(const DmaStats &s)
{
    std::ostringstream os;
    os << "{\"requests\":" << s.requests << ",\"responses\":" << s.responses
       << ",\"retries\":" << s.retries << ",\"bytes\":" << s.bytes
       << ",\"errors\":" << s.errors << "}";
    return os.str();
}

std::string
jobJson(const Job &job, const Geometry &geo)
{
    std::ostringstream os;
    os << "{\"m\":" << job.m << ",\"n\":" << job.n << ",\"k\":" << job.k
       << ",\"a_fmt\":" << int(job.aFmt) << ",\"b_fmt\":" << int(job.bFmt)
       << ",\"c_fmt\":" << int(job.cFmt)
       << ",\"load_w\":" << (job.loadWMode ? "true" : "false")
       << ",\"bias\":" << (job.biasOn ? "true" : "false")
       << ",\"requant\":" << (job.requantOn ? "true" : "false")
       << ",\"a_addr\":" << job.aAddr << ",\"b_addr\":" << job.bAddr
       << ",\"c_addr\":" << job.cAddr
       << ",\"t\":" << geo.t << ",\"n_tiles\":" << geo.nt
       << ",\"k_tiles\":" << geo.kt << ",\"k_per_beat_a\":"
       << geo.kPerBeatA << ",\"k_per_beat_b\":" << geo.kPerBeatB
       << ",\"a_beats\":" << geo.aBeats() << ",\"b_beats\":" << geo.bBeats()
       << ",\"c_rows\":" << geo.cRows() << ",\"c_beats\":" << geo.cBeats()
       << "}";
    return os.str();
}

} // namespace

WorkerBee::DmaPort::DmaPort(const std::string &name, WorkerBee &owner, DmaId id)
    : RequestPort(name), owner_(owner), id_(id)
{
}

bool
WorkerBee::DmaPort::recvTimingResp(PacketPtr pkt)
{
    owner_.dmaResponse(id_, pkt);
    /* dmaResponse deletes the packet. */
    return true;
}

void
WorkerBee::DmaPort::recvReqRetry()
{
    owner_.dmaRetry(id_);
}

WorkerBee::WorkerBee(const WorkerBeeGemmParams &p)
    : ClockedObject(p),
      hw_(),
      regs_(hw_),
      dmaA_(name() + ".dma_a", *this, DmaId::A),
      dmaB_(name() + ".dma_b", *this, DmaId::B),
      dmaC_(name() + ".dma_c", *this, DmaId::C),
      dmaScaleA_(name() + ".dma_scale_a", *this, DmaId::ScaleA),
      dmaScaleB_(name() + ".dma_scale_b", *this, DmaId::ScaleB),
      dmaScaleC_(name() + ".dma_scale_c", *this, DmaId::ScaleC),
      a_(DmaId::A, hw_.beatBytes(), hw_.aBufferDepth, p.dma_outstanding,
         [this](DmaId id, Addr addr, int size, bool write,
                const std::vector<bool> &be) {
             return send(id, addr, size, write, be);
         }),
      b_(DmaId::B, hw_.beatBytes(), hw_.bBufferDepth, p.dma_outstanding,
         [this](DmaId id, Addr addr, int size, bool write,
                const std::vector<bool> &be) {
             return send(id, addr, size, write, be);
         }),
      scaleA_(DmaId::ScaleA, hw_.scaleBeatBytes(), hw_.scaleBufferDepth,
              p.dma_outstanding,
              [this](DmaId id, Addr addr, int size, bool write,
                     const std::vector<bool> &be) {
                  return send(id, addr, size, write, be);
              }),
      scaleB_(DmaId::ScaleB, hw_.scaleBeatBytes(), hw_.scaleBufferDepth,
              p.dma_outstanding,
              [this](DmaId id, Addr addr, int size, bool write,
                     const std::vector<bool> &be) {
                  return send(id, addr, size, write, be);
              }),
      c_(DmaId::C, hw_.cBeatBytes(), hw_.cBufferDepth, p.dma_outstanding,
         [this](DmaId id, Addr addr, int size, bool write,
                const std::vector<bool> &be) {
             return send(id, addr, size, write, be);
         }),
      scaleC_(DmaId::ScaleC, 1, 2, p.dma_outstanding,
              [this](DmaId id, Addr addr, int size, bool write,
                     const std::vector<bool> &be) {
                  return send(id, addr, size, write, be);
              }),
      executor_(hw_, a_, b_, scaleA_, scaleB_, c_, scaleC_),
      tickEvent_(*this)
{
    fatal_if(p.hardware_profile != "workerbee_default_v4",
             "%s: unsupported hardware profile %s",
             name(), p.hardware_profile);
    arrayLatency_ = p.array_pipeline_latency;
    exitOnComplete_ = p.exit_on_complete;
    resultPath_ = p.result_path;
    responseReady_ = p.response_ready;
    loadTrace(p.command_batches);
}

void
WorkerBee::loadTrace(const std::vector<uint64_t> &words)
{
    fatal_if(words.size() % wordsPerBatch != 0,
             "%s: command_batches has %d words, expected a multiple of %d",
             name(), (int)words.size(), (int)wordsPerBatch);
    for (size_t base = 0; base < words.size(); base += wordsPerBatch) {
        TraceBatch entry;
        entry.cycle = words[base];
        const uint64_t mask = words[base + 1];
        bool anyLane = false;
        for (int lane = 0; lane < numLanes; lane++) {
            entry.lanes[lane] = ((mask >> lane) & 1) != 0;
            anyLane = anyLane || entry.lanes[lane];
            const size_t w = base + 2 + wordsPerLane * lane;
            Command &cmd = entry.commands[lane];
            cmd.op = uint8_t(words[w] & 0xFF);
            cmd.reg0 = uint8_t((words[w] >> 8) & 0xFF);
            cmd.reg1 = uint8_t((words[w] >> 16) & 0xFF);
            cmd.data0 = uint32_t(words[w + 1]);
            cmd.data1 = uint32_t(words[w + 2]);
            cmd.pc = uint32_t(words[w + 3]);
            cmd.tag = uint8_t(words[w + 4] & 0xFF);
        }
        fatal_if(!anyLane, "%s: trace batch %d selects no lane", name(),
                 (int)(base / wordsPerBatch));
        trace_.push_back(entry);
    }
}

Port &
WorkerBee::getPort(const std::string &if_name, PortID idx)
{
    if (if_name == "dma_a") return dmaA_;
    if (if_name == "dma_b") return dmaB_;
    if (if_name == "dma_c") return dmaC_;
    if (if_name == "dma_scale_a") return dmaScaleA_;
    if (if_name == "dma_scale_b") return dmaScaleB_;
    if (if_name == "dma_scale_c") return dmaScaleC_;
    return ClockedObject::getPort(if_name, idx);
}

RequestPort &
WorkerBee::port(DmaId id)
{
    switch (id) {
      case DmaId::A: return dmaA_;
      case DmaId::B: return dmaB_;
      case DmaId::C: return dmaC_;
      case DmaId::ScaleA: return dmaScaleA_;
      case DmaId::ScaleB: return dmaScaleB_;
      case DmaId::ScaleC: return dmaScaleC_;
    }
    panic("%s: invalid DMA port id", name());
    return dmaA_;
}

bool
WorkerBee::send(DmaId id, Addr addr, int size, bool write,
                const std::vector<bool> &byteEnable)
{
    Request::Flags flags;
    RequestPtr req =
        std::make_shared<Request>(addr, size, flags, Request::funcRequestorId);
    PacketPtr pkt =
        new Packet(req, write ? MemCmd::WriteReq : MemCmd::ReadReq);
    pkt->allocate();
    if (write) {
        std::memset(pkt->getPtr<uint8_t>(), 0, size);
        if (!byteEnable.empty()) {
            req->setByteEnable(byteEnable);
        }
    }
    if (!port(id).sendTimingReq(pkt)) {
        delete pkt;
        return false;
    }
    return true;
}

void
WorkerBee::dmaResponse(DmaId id, PacketPtr pkt)
{
    const bool err = pkt->isError();
    switch (id) {
      case DmaId::A: a_.onResponse(err); break;
      case DmaId::B: b_.onResponse(err); break;
      case DmaId::C: c_.onResponse(err); break;
      case DmaId::ScaleA: scaleA_.onResponse(err); break;
      case DmaId::ScaleB: scaleB_.onResponse(err); break;
      case DmaId::ScaleC: scaleC_.onResponse(err); break;
    }

    if (err) {
        /* The ScaleA port is shared by ScaleA, bias and requant multipliers.
         * Classify the failing read by the programmed address windows; an
         * error outside both belongs to the ScaleA matrix stream. */
        int bit = int(id);
        if (id == DmaId::ScaleA && haveJob_) {
            const Addr addr = pkt->getAddr();
            const Addr span = Addr(geo_.nt) * geo_.t * 4;
            if (job_.biasOn && addr >= job_.biasAddr &&
                addr < job_.biasAddr + span) {
                bit = IrqDmaBias - IrqDmaA;
            } else if (job_.requantOn && addr >= job_.rqMAddr &&
                       addr < job_.rqMAddr + span) {
                bit = IrqDmaRqM - IrqDmaA;
            }
        }
        regs_.queueDmaError(bit);
    }
    delete pkt;
}

void
WorkerBee::dmaRetry(DmaId id)
{
    switch (id) {
      case DmaId::A: a_.onRetry(); break;
      case DmaId::B: b_.onRetry(); break;
      case DmaId::C: c_.onRetry(); break;
      case DmaId::ScaleA: scaleA_.onRetry(); break;
      case DmaId::ScaleB: scaleB_.onRetry(); break;
      case DmaId::ScaleC: scaleC_.onRetry(); break;
    }
}

bool
WorkerBee::inputSupported(const Job &job) const
{
    /* cFmt is an output encoding, not an input format: FP32 output is legal
     * but FP32 is not in the input format set. */
    return job.m >= 1 && job.n >= 1 && job.k >= 1 &&
           job.n % uint32_t(hw_.totalN) == 0 &&
           hw_.supports(job.aFmt) && hw_.supports(job.bFmt);
}

void
WorkerBee::startup()
{
    ClockedObject::startup();
    schedule(tickEvent_, clockEdge(Cycles(1)));
}

void
WorkerBee::tick()
{
    cycle_++;

    regs_.tick(responseReady_);

    if (regs_.startPulse()) {
        const Job job = regs_.job();
        if (!inputSupported(job)) {
            inputErrors_++;
            lastStartErrored_ = true;
            std::ostringstream os;
            os << "input: unsupported job m=" << job.m << " n=" << job.n
               << " k=" << job.k << " a_fmt=" << int(job.aFmt)
               << " b_fmt=" << int(job.bFmt) << " c_fmt=" << int(job.cFmt);
            errors_.push_back(os.str());
            regs_.abortStart();
        } else if (const auto code = configError(job, hw_)) {
            configErrors_++;
            lastStartErrored_ = true;
            lastErrorCode_ = *code;
            configErrorAt_ = cycle_ + configErrorLatency;
            std::ostringstream os;
            os << "config: errCode " << *code << " for m=" << job.m
               << " n=" << job.n << " k=" << job.k
               << " a_fmt=" << int(job.aFmt) << " b_fmt=" << int(job.bFmt)
               << " c_fmt=" << int(job.cFmt);
            errors_.push_back(os.str());
        } else {
            job_ = job;
            geo_ = geometryOf(job, hw_);
            haveJob_ = true;
            lastStartErrored_ = false;
            lastErrorCode_ = 0;
            scheduledOps_ += 2ull * job.m * job.n * job.k;
            executor_.start(job_, geo_, arrayLatency_);
            jobsStarted_++;
            startCycle_ = cycle_;
        }
    }

    if (configErrorAt_ != 0 && cycle_ >= configErrorAt_) {
        regs_.queueConfigError(lastErrorCode_);
        configErrorAt_ = 0;
    }

    executor_.tick();
    if (executor_.donePulse()) {
        regs_.queueDone();
        jobsDone_++;
        executorCycles_ += executor_.cycles();
        arrayBusy_ += executor_.arrayBusy();
        stallA_ += executor_.stallA();
        stallB_ += executor_.stallB();
        doneCycle_ = cycle_ + 1;
        gemmCycles_ = doneCycle_ - startCycle_ + 1;
    }

    a_.tick();
    b_.tick();
    scaleA_.tick();
    scaleB_.tick();
    c_.tick();
    scaleC_.tick();

    regs_.setStatus(executor_.running(), executor_.progress());

    if (!trace_.empty() && regs_.canAccept() &&
        trace_.front().cycle <= cycle_) {
        const TraceBatch entry = trace_.front();
        trace_.pop_front();
        if (!regs_.accept(entry.commands, entry.lanes)) {
            fatal("%s: batch accepted while the register file was busy",
                  name());
        }
    }

    if (exitOnComplete_ && trace_.empty() && regs_.canAccept() &&
        !regs_.inFlight() && !executor_.running() && a_.idle() && b_.idle() &&
        scaleA_.idle() && scaleB_.idle() && c_.idle() && scaleC_.idle()) {
        finish();
        return;
    }

    schedule(tickEvent_, clockEdge(Cycles(1)));
}

void
WorkerBee::finish()
{
    if (finished_) {
        return;
    }
    finished_ = true;

    std::ostringstream os;
    os << "{\n"
       << "  \"schema_version\": 1,\n"
       << "  \"model_kind\": \"timing_loose\",\n"
       << "  \"model_phase\": \"L1\",\n"
       << "  \"hardware_profile\": \"workerbee_default_v4\",\n"
       << "  \"data_valid\": false,\n"
       << "  \"performance_valid\": false,\n"
       << "  \"array_pipeline_latency\": " << arrayLatency_ << ",\n"
       << "  \"simulated_cycles\": " << cycle_ << ",\n"
       << "  \"jobs_started\": " << jobsStarted_ << ",\n"
       << "  \"jobs_done\": " << jobsDone_ << ",\n"
       << "  \"config_errors\": " << configErrors_ << ",\n"
       << "  \"input_errors\": " << inputErrors_ << ",\n"
       << "  \"last_error_code\": " << lastErrorCode_ << ",\n"
       << "  \"last_start_errored\": "
       << (lastStartErrored_ ? "true" : "false") << ",\n"
       << "  \"gemm\": {\"start_cycle\": " << startCycle_
       << ", \"done_cycle\": " << doneCycle_
       << ", \"latency_cycles\": " << gemmCycles_
       << ", \"executor_cycles\": " << executorCycles_
       << ", \"array_busy_cycles\": " << arrayBusy_
       << ", \"stall_a_cycles\": " << stallA_
       << ", \"stall_b_cycles\": " << stallB_
       << ", \"scheduled_ops\": " << scheduledOps_ << "},\n"
       << "  \"job\": " << (haveJob_ ? jobJson(job_, geo_) : "null") << ",\n"
       << "  \"commands\": {\"accepted_batches\": " << regs_.acceptedBatches()
       << ", \"responses\": " << regs_.responsesSent()
       << ", \"rejected_writes\": " << regs_.rejectedLaneWrites()
       << ", \"write_conflicts\": " << regs_.writeConflicts() << "},\n"
       << "  \"irq\": {\"raw\": " << regs_.irqRaw()
       << ", \"masked\": " << regs_.irqMasked()
       << ", \"error_code\": " << regs_.errorCode() << "},\n"
       << "  \"dma\": {";
    bool first = true;
    const std::pair<DmaId, const DmaStats *> ports[] = {
        {DmaId::A, &a_.stats()},         {DmaId::B, &b_.stats()},
        {DmaId::C, &c_.stats()},         {DmaId::ScaleA, &scaleA_.stats()},
        {DmaId::ScaleB, &scaleB_.stats()}, {DmaId::ScaleC, &scaleC_.stats()},
    };
    for (const auto &entry : ports) {
        if (!first) {
            os << ", ";
        }
        first = false;
        os << "\"" << portName(entry.first) << "\": " << statJson(*entry.second);
    }
    os << "},\n"
       << "  \"errors\": [";
    for (size_t i = 0; i < errors_.size(); i++) {
        os << (i ? ", " : "") << "\"" << errors_[i] << "\"";
    }
    os << "]\n}\n";

    if (!resultPath_.empty()) {
        std::ofstream out(resultPath_);
        fatal_if(!out, "%s: cannot open result path %s", name(), resultPath_);
        out << os.str();
        out.close();
        inform("%s: wrote %s", name(), resultPath_);
    } else {
        inform("%s: no result_path set; result JSON follows\n%s", name(),
               os.str());
    }

    exitSimLoop("workerbee: trace complete");
}

} // namespace gem5::workerbee
