/** ForagerBee Scatter/Gather 描述符控制模块。
  *
  * 独立的纯控制模块，不包含数据路径。负责从内存中的「地址描述符表」
  * 逐条 fetch 出 (addr, len) 条目，并按 start/valid/advance/done 协议
  * 向 Engine 输出当前散列端条目信息，由 Engine 完成实际数据搬运后 advance。
  *
  * 与 [[FbIm2colAddrGen]] 一致的握手协议：
  *   - start   ：一拍脉冲，锁存 listAddr/entryCount 并复位内部计数器；
  *   - valid   ：当前条目就绪（curAddr/curLen 有效）；
  *   - advance ：Engine 完成当前条目传输，推进到下一条目；
  *   - done    ：全部条目处理完毕（持续到下一次 start）。
  *
  * 描述符表内存格式（V1 最简方案：每条目占 1 个 beat）：
  *   - beat 低 addressWidth 位 = 散列端地址（addr）；
  *   - beat 接下来的 16 位     = 本条目字节数（len）；
  *   - beat 高位忽略。
  *   因此第 i 条目内存地址 = listAddr + i × beatBytes，单 beat 突发读取。
  *
  * 与 Engine 的连接：fetchRd/fetchRdData 直接对接 Engine 的 bus.rd/bus.rdData
  * （或经仲裁），复用现有 FbStreamBus 读通道 fetch 描述符表。
  *
  * @param addressWidth 地址位宽（bit）
  * @param dataWidth    数据位宽（bit），必须 ≥ addressWidth + 16（单 beat 可容纳一条目）
  */

import chisel3._
import chisel3.util._

class FbScatterGatherCtrl(addressWidth: Int, dataWidth: Int) extends Module {

  require(addressWidth >= 12, s"FbScatterGatherCtrl: addressWidth($addressWidth) 必须 ≥ 12")
  require(dataWidth >= addressWidth + 16,
    s"FbScatterGatherCtrl: dataWidth($dataWidth) 必须 ≥ addressWidth+16(${addressWidth + 16})，" +
      "以保证单 beat 可容纳 (addr + 16bit len) 条目")

  /** 每 beat 字节数 */
  private val beatBytes = dataWidth / 8

  val io = IO(new Bundle {
    // 配置（start 脉冲时锁存）
    val start      = Input(Bool())
    val listAddr   = Input(UInt(addressWidth.W))
    val entryCount = Input(UInt(16.W))

    // 与 Engine bus.rd/rdData 连接（用于 fetch 描述符表）
    val fetchRd     = master(Stream(new FbReadReq(addressWidth)))     // 读请求（master 输出）
    val fetchRdData = slave(Stream(new FbReadData(dataWidth)))        // 读数据（slave 输入）

    // 输出当前条目信息
    val valid    = Output(Bool())               // 当前条目就绪
    val advance  = Input(Bool())                // Engine 完成当前条目传输，推进到下一条目
    val curAddr  = Output(UInt(addressWidth.W)) // 当前散列端地址
    val curLen   = Output(UInt(16.W))           // 当前条目字节数
    val done     = Output(Bool())               // 全部条目处理完毕
    val entryIdx = Output(UInt(16.W))           // 当前条目索引（调试）
  })

  // ==========================================================================
  // FSM 状态（显式二进制编码，与 FbEngine 保持一致风格）
  // ==========================================================================
  val sIdle   = 0.U(2.W)
  val sFetch  = 1.U(2.W)
  val sOutput = 2.U(2.W)
  val state   = RegInit(sIdle)

  // ==========================================================================
  // 锁存配置与内部游标
  // ==========================================================================
  val listAddrReg   = RegInit(0.U(addressWidth.W))
  val entryCountReg = RegInit(0.U(16.W))
  val entryIdxReg   = RegInit(0.U(16.W))

  // 当前条目寄存器（sFetch 收到 rdData 后解析写入）
  val curAddrReg = RegInit(0.U(addressWidth.W))
  val curLenReg  = RegInit(0.U(16.W))

  // 读请求握手：本次 fetch 的 rd 请求是否已发出
  val arIssued = RegInit(false.B)

  // 全部完成标志（持续到下一次 start）
  val doneReg = RegInit(false.B)

  // ==========================================================================
  // 默认信号（各状态按需覆盖，last-connect 生效）
  // ==========================================================================
  io.fetchRd.valid           := false.B
  io.fetchRd.payload.addr    := DontCare
  io.fetchRd.payload.len     := DontCare
  io.fetchRdData.ready       := false.B

  io.valid    := state === sOutput
  io.curAddr  := curAddrReg
  io.curLen   := curLenReg
  io.done     := doneReg
  io.entryIdx := entryIdxReg

  // ==========================================================================
  // sIdle：等待 start 脉冲
  // ==========================================================================
  when(io.start) {
    listAddrReg   := io.listAddr
    entryCountReg := io.entryCount
    entryIdxReg   := 0.U
    arIssued      := false.B
    doneReg       := false.B
    // entryCount==0 视为空表，直接完成
    when(io.entryCount === 0.U) {
      doneReg := true.B
      state   := sIdle
    }.otherwise {
      state := sFetch
    }
  }

  // ==========================================================================
  // sFetch：读 1 个 beat（= 1 条目），解析后转 sOutput
  //   条目内存地址 = listAddr + entryIdx × beatBytes
  //   单 beat 突发：len = 0
  // ==========================================================================
  when(state === sFetch) {
    // 条目字节偏移：entryIdx × beatBytes
    val fetchOffset = (entryIdxReg * beatBytes.U).pad(addressWidth)
    val fetchAddr   = (listAddrReg.pad(addressWidth) + fetchOffset)(addressWidth - 1, 0)

    io.fetchRd.valid        := !arIssued
    io.fetchRd.payload.addr := fetchAddr
    io.fetchRd.payload.len  := 0.U // 单 beat 突发
    when(io.fetchRd.fire) {
      arIssued := true.B
    }

    // 接收读数据（单 beat）：解析出 addr/len 后进入 sOutput
    io.fetchRdData.ready := arIssued
    when(io.fetchRdData.fire) {
      curAddrReg := io.fetchRdData.payload.data(addressWidth - 1, 0)
      curLenReg  := io.fetchRdData.payload.data(addressWidth + 15, addressWidth)
      arIssued   := false.B
      state      := sOutput
    }
  }

  // ==========================================================================
  // sOutput：输出当前条目，等待 advance 推进
  // ==========================================================================
  when(state === sOutput) {
    when(io.advance) {
      val isLast = (entryIdxReg + 1.U) >= entryCountReg
      when(isLast) {
        // 全部条目处理完毕
        doneReg := true.B
        state   := sIdle
      }.otherwise {
        // V1 无预取 buffer：逐条 fetch，推进索引并回 sFetch
        entryIdxReg := entryIdxReg + 1.U
        arIssued    := false.B
        state       := sFetch
      }
    }
  }
}

object FbScatterGatherCtrl {
  def apply(addressWidth: Int, dataWidth: Int): FbScatterGatherCtrl =
    new FbScatterGatherCtrl(addressWidth, dataWidth)
}
