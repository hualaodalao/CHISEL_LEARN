from pathlib import Path

_MAIN = "src/workerbee/scala/main/"
_RULES = (
    ("calc_start", "WorkerBeeCalcConfig.scala", "io.startOut := state === sC1 && !err",
     "startIn enters sC0; sC0 enters sC1; startOut uses registered state and err"),
    ("scratchpad_fifo", "WorkerBeeScratchpad.scala", "val cFifo = Module(new StreamFifo",
     "A/B/C use StreamFifo with profile depths; C raw row is totalN*cEffW bits"),
    ("input_valid_skew", "WbComb.scala", "validSkewedShiftRegisters(0) := io.validIn",
     "valid shifts each edge; row i selects delay i; data shifts use shiftEnableIn"),
    ("output_deskew", "WbComb.scala", "val dly = clusterM * arrayN - 1 - i",
     "first data delay stage updates unconditionally; later stages use cOutShiftEn"),
    ("pe_valid_h", "WbWorker.scala", "io.validOut := RegNext(io.validIn, false.B)",
     "horizontal valid has one registered stage per PE"),
    ("pe_valid_v", "WbWorker.scala", "io.validOutV := RegNext(io.validInV, false.B)",
     "vertical valid has one registered stage per PE; no shape-fitted latency"),
    ("next_window_drain", "WorkerBeeExecutor.scala", "drainNextCnt := cfg.totalN.U - 1.U",
     "NEXT writes T-1 in both K-advance and N-advance branches"),
    ("horizontal_drain_reload", "WorkerBeeExecutor.scala", "drainNextCnt := (2 * (totalN - 1) + 1).U",
     "DRAIN-to-LOAD_B writes 2*(T-1)+1, subsequently overwritten in NEXT"),
    ("raw_c_fifo_pop", "WorkerBee.scala", "scratchpad.io.cPop.ready := Mux(executor.io.cStoreGate,",
     "raw C FIFO pops at cvt input fire while store gate is enabled; G5-U01 remains open"),
    ("cvt_row_release", "WbOutCvt.scala", "private val release = (io.out.fire &&",
     "held row releases at accepted final data plus scale; not the raw FIFO pop"),
    ("cvt_same_edge_reload", "WbOutCvt.scala", "io.in.ready       := !holdValid || release",
     "release permits same-edge input fire; later input assignment wins holdValid"),
    ("read_credit", "WorkerBeeDmaRdOnly.scala", "when(finalReqFire & io.bufPopFire)",
     "explicit credit uses final physical request; gather reservation requires G5-U02 edge tests"),
    ("c_write_outstanding", "WorkerBeeDmaWrOnly.scala", "val ostCredit = RegInit(0.U(log2Up((cfg.cBufferDepth + 2)",
     "C outstanding counter width is not an issuance cap; G5-U03 remains open"),
    ("parameter_owner", "WorkerBee.scala", "val biasSteal = biasFState =/= sBiasFIDLE",
     "ScaleA owner follows IDLE/WAIT/FETCH/FETCH_M; WAIT drains old reads"),
    ("scale_c_fifo", "WorkerBee.scala", "new StreamFifo(UInt(cfg.cScaleRowW.W), 2)",
     "ScaleC FIFO depth is fixed at two; external service restriction G5-U06 remains open"),
)


def collect(repo_root: Path, gem5_root: Path):
    entries = []
    rules = [(name, _MAIN + path, literal, rule) for name, path, literal, rule in _RULES]
    rules.append(("fifo_queue", "src/utils/Stream/Stream.scala",
                  "new Queue(chiselTypeOf(io.push.payload), depth, hasFlush = true)",
                  "Queue default pipe/flow semantics must be checked against Chisel 7.13 RTL"))
    api_rules = (
        ("masked_write", "src/mem/request.hh", "setByteEnable(const std::vector<bool>& be)",
         "request byte-enable length equals packet size; aligned-word translation is not implemented"),
        ("masked_data_copy", "src/mem/packet.hh", "if (req->getByteEnable()[i])",
         "Packet::writeData preserves disabled bytes; no implicit read-modify-write required"),
        ("request_retry", "src/mem/protocol/timing.hh", "virtual void recvReqRetry() = 0;",
         "failed sendTimingReq retains request and waits for retry; P0 does not send traffic"),
        ("clock_edge", "src/sim/clocked_object.hh", "clockEdge(Cycles cycles=Cycles(0)) const",
         "clockEdge aligns to current/next edge; callback staging protocol still requires edge tests"),
    )
    for root, scope, group in ((Path(repo_root), "hardware", rules),
                                (Path(gem5_root), "gem5", api_rules)):
        for name, relative, literal, rule in group:
            lines = (root / relative).read_text().splitlines()
            locations = [{"line": index + 1, "text": text.strip()}
                         for index, text in enumerate(lines) if literal in text]
            if not locations:
                raise ValueError(f"P0 evidence locator no longer matches: {relative}: {literal}")
            entries.append({"id": name, "scope": scope, "path": relative,
                            "locations": locations, "rule": rule,
                            "status": "source_located_edge_test_pending"})
    return {"entries": entries, "edge_calibrated": False,
            "open_issues": [f"G5-U0{index}" for index in range(1, 7)]}
