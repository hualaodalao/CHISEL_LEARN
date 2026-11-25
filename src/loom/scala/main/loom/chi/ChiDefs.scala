package loom.chi

/** CHI node roles. RnF/HnF are port placeholders only (coherency reserved). */
object ChiNodeKind extends Enumeration {
  val RnI, RnD, RnF, SnF, SnI, HnI, HnF, Mn = Value

  def isRequest(k: Value): Boolean = k == RnI || k == RnD || k == RnF
  def isSubordinate(k: Value): Boolean = k == SnF || k == SnI
  def isHome(k: Value): Boolean = k == HnI || k == HnF
  def hasSnpRx(k: Value): Boolean = k == RnD || k == RnF
}

/** The four CHI channel classes. */
object ChiChannel extends Enumeration {
  val Req, Rsp, Snp, Dat = Value
}

/** Opcode names by channel, split into supported / reserved / unsupported.
  * Hex encodings are transcribed from IHI0050 at implementation time; this
  * table fixes the name set and its classification only.
  */
object ChiOpcode {
  val atomicAlu: Seq[String] = Seq("Add", "Clr", "Eor", "Set", "Smax", "Smin", "Umax", "Umin")

  val reqSupported: Seq[String] =
    Seq("ReadNoSnp", "WriteNoSnpFull", "WriteNoSnpPtl") ++
      atomicAlu.map("AtomicStore" + _) ++
      atomicAlu.map("AtomicLoad" + _) ++
      Seq("AtomicSwap", "AtomicCompare", "DVMOp")

  val rspSupported: Seq[String] =
    Seq("Comp", "DBIDResp", "CompDBIDResp", "RetryAck", "PCrdGrant", "ReadReceipt", "CompAck", "SnpResp")

  val datSupported: Seq[String] = Seq("CompData", "NonCopyBackWrData")

  val snpSupported: Seq[String] = Seq("SnpDVMOp")

  /** Coherency / optimization opcodes: encoding space reserved, not implemented. */
  val reserved: Seq[String] = Seq(
    "SnpShared", "SnpUnique", "SnpCleanInvalid", "SnpMakeInvalid", "SnpOnce",
    "ReadOnce", "ReadShared", "ReadUnique", "ReadNotSharedDirty", "ReadClean",
    "WriteBack", "WriteClean", "WriteEvictFull", "WriteUniqueFull", "WriteUniquePtl",
    "DataSepResp", "RespSepData", "SnpRespData", "CopyBackWrData",
    "MakeReadUnique", "CleanUnique", "StashOnceShared", "StashOnceUnique",
    "CleanShared", "CleanInvalid", "MakeInvalid", "CleanSharedPersist"
  )

  /** Declared unsupported (recorded, gated against use). */
  val unsupported: Seq[String] = Seq("Exclusive", "CMO", "Stash", "MPAM", "MTE", "DWT", "DCT", "DMT")

  def isSupported(channel: ChiChannel.Value, name: String): Boolean = channel match {
    case ChiChannel.Req => reqSupported.contains(name)
    case ChiChannel.Rsp => rspSupported.contains(name)
    case ChiChannel.Dat => datSupported.contains(name)
    case ChiChannel.Snp => snpSupported.contains(name)
  }
}

/** Size field: log2 of the transaction byte count, max 64B. */
object ChiSize {
  val maxBytes: Int = 64
  def encode(bytes: Int): Int = {
    require(bytes >= 1 && bytes <= maxBytes && (bytes & (bytes - 1)) == 0,
      s"CHI size must be a power of two in [1,64], got $bytes")
    31 - Integer.numberOfLeadingZeros(bytes)
  }
  def decode(code: Int): Int = 1 << code
}

object ChiRespErr {
  val OK: Int = 0
  val EXOK: Int = 1
  val DERR: Int = 2
  val NDERR: Int = 3
}

/** MemAttr = {Allocate, Cacheable, Device, EWA}. */
object ChiMemAttr {
  def device(m: Int): Boolean = (m & 0x2) != 0
  def cacheable(m: Int): Boolean = (m & 0x4) != 0
  val normalNonCacheable: Int = 0x0
  val deviceNRnE: Int = 0x2
}

object ChiOrder {
  val NoOrder: Int = 0
  val RequestOrder: Int = 2
  val EndpointOrder: Int = 3
}

object ChiWidths {
  def beWidth(datWidth: Int): Int = datWidth / 8
  def dataIdWidth: Int = 2
  /** DAT flits carrying a transaction of the given byte size. */
  def flitCount(bytes: Int, datWidth: Int): Int = {
    val perFlit = datWidth / 8
    (bytes + perFlit - 1) / perFlit
  }
}

/** Loom-internal symbolic opcode encodings for the supported subset. The
  * externally CHI-compliant hex mapping is aligned at the bridge (S11); the
  * engines here use these distinct local codes.
  */
object ChiOp {
  val ReadNoSnp = 0x04
  val WriteNoSnpFull = 0x18
  val WriteNoSnpPtl = 0x19
  val DVMOp = 0x14
  val AtomicSwap = 0x38
  val AtomicCompare = 0x39

  val RspComp = 0x04
  val RspDBIDResp = 0x06
  val RspCompDBIDResp = 0x07
  val RspRetryAck = 0x03
  val RspPCrdGrant = 0x08
  val RspReadReceipt = 0x09
  val RspCompAck = 0x0a
  val RspSnpResp = 0x01

  val DatCompData = 0x04
  val DatNonCopyBackWrData = 0x02

  val SnpDVMOp = 0x01
}