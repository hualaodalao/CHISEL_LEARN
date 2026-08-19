import chisel3._
import chisel3.util._

case class LlcConfig(
  capacity:      Int = 256 * 1024,  // Total capacity in bytes (256KB)
  ways:          Int = 8,           // Associativity
  lineBytes:     Int = 64,          // Cache line size in bytes
  addressWidth:  Int = 32,          // Address width
  dataWidth:     Int = 256,         // AXI4 data bus width
  idWidth:       Int = 4,           // AXI4 ID width
  mshrEntries:   Int = 4,           // Number of MSHR entries
  wbBufEntries:  Int = 4,           // Write-back buffer entries
  wayPartitions: Int = 2,           // Number of way partitions for QoS
  ctrlAddrWidth: Int = 8,           // AxiLite4 address width for ctrl port (256 bytes)
  ctrlDataWidth: Int = 32           // AxiLite4 data width for ctrl port
) {
  import LlcConfig.isPow2

  // Derived parameters
  val sets: Int = capacity / (ways * lineBytes)           // 512
  val offsetBits: Int = log2Ceil(lineBytes)               // 6
  val setBits: Int = log2Ceil(sets)                       // 9
  val tagBits: Int = addressWidth - setBits - offsetBits  // 17
  val beatsPerLine: Int = lineBytes * 8 / dataWidth       // 2 (for 256-bit bus)
  val bytePerBeat: Int = dataWidth / 8                    // 32

  // Way partition derived
  val waysPerPartition: Int = ways / wayPartitions  // 4

  // AXI4 config for upstream slave port
  val axiSlaveConfig: Axi4Config = Axi4Config(
    addressWidth = addressWidth,
    dataWidth = dataWidth,
    idWidth = idWidth,
    useId = true, useLen = true, useBurst = true,
    useSize = true, useLast = true, useResp = true, useStrb = true
  )

  // AXI4 config for downstream master port
  val axiMasterConfig: Axi4Config = Axi4Config(
    addressWidth = addressWidth,
    dataWidth = dataWidth,
    idWidth = idWidth,
    useId = true, useLen = true, useBurst = true,
    useSize = true, useLast = true, useResp = true, useStrb = true
  )

  // AXI4-Lite config for the control/status register port
  val axiLiteConfig: AxiLite4Config = AxiLite4Config(
    addressWidth = ctrlAddrWidth,
    dataWidth = ctrlDataWidth
  )

  // Validation
  require(isPow2(capacity), s"capacity must be power of 2, got $capacity")
  require(isPow2(ways), s"ways must be power of 2, got $ways")
  require(isPow2(lineBytes), s"lineBytes must be power of 2, got $lineBytes")
  require(isPow2(sets), s"sets must be power of 2, got $sets")
  require(isPow2(dataWidth) && dataWidth >= 8, s"dataWidth must be power of 2 >= 8, got $dataWidth")
  require(ways % wayPartitions == 0, s"ways($ways) must be divisible by wayPartitions($wayPartitions)")
  require(mshrEntries >= 1, s"mshrEntries must be >= 1, got $mshrEntries")
  require(wbBufEntries >= 1, s"wbBufEntries must be >= 1, got $wbBufEntries")
  require(lineBytes * 8 >= dataWidth, s"lineBytes*8(${lineBytes * 8}) must be >= dataWidth($dataWidth)")
  require(addressWidth > setBits + offsetBits, s"addressWidth too small for sets and lineBytes")
}

object LlcConfig {
  // Helper to check isPow2 at elaboration time (chisel3.util.isPow2 is for hardware Booleans)
  private[LlcConfig] def isPow2(n: Int): Boolean = n > 0 && (n & (n - 1)) == 0
}
