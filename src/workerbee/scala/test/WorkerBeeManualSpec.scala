class WorkerBeeManualSpec extends WorkerBeeCaseSpec {
  override protected def TAG: String = "MANUAL"

  runCase(
    m = 1,
    n = 32,
    k = 64,
    aFmt = DataFormat.BF16,
    bFmt = DataFormat.BF16,
    cFmt = DataFormat.FP32,
    aBlk = 16,
    bBlk = 16,
    cBlk = 16,
    addrMode = AddrMode.MAT,
    loadW = Some(1),
    osM1 = true,
    osFmtKeep = false,
    biasOn = false,
    requantOn = false,
    reluOn = false,
    rqShift = 0,
    rqZp = 0,
    nLogical = 0,
    rnd = RoundingMode.RNE,
    bBase = 0x01000000L,
    seed = 1234L,
    bpPeriod = 0,
    strictAddr = true,
    maxCycles = 20000L,
    wave = "sim/debug/manual.fst"
  )
}
