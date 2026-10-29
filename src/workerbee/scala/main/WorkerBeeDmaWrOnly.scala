/** WorkerBeeDmaScaleWr — C 侧量化 scale 写回 DMA（dmaCScale 通道）。
  *
  * 量化输出（outFmt 1xx）时 cDma 每写出一行量化密集行即同拍产出一行 E8M0 块 scale
  * （cvt.io.scaleOut），宽 cScaleRowW = (totalN/minBlockSize)*8 = totalN/2 **bit**
  * = totalN/16 **字节**；经顶层深 2 cScaleFifo 缓冲后写到外部存储（基址 = cScaleAddr）。
  *
  * 遍历与 cDma 的 C 数据写回同序以保证 scale 行与量化数据行逐行对应：内层沿 M 方向（实际
  * M 行 = regFile.m，每行一拍、地址 + cScaleRowAddressOffset = ceil(n/blk) 字节），外层扫
  * nTile 个 N tile（轮间跳 cScaleColTileAddressOffset = totalN/blk 字节）。地址取「基址 +
  * 轮偏移」线性模型：curAddr := baseAddr + colAddr; colAddr += colTileOffset，每轮起点恒
  * = baseAddr + nt*colTileOffset，无累积误差。
  *
  * req 仅在 sTRANSFER 且 bufPop 有数据时发出，并被在途上限（cScaleFifo 深度 2）门控，防
  * rsp 延迟堆积超出计数位宽；ostCredit 记在途写拍数（req.fire +1 / rsp.fire −1），sDONE
  * 等 credit==0 后发 done 回 sIDLE（写通道无回读数据，完成判据只看在途排空）。start 任意
  * 状态可重新装载，重载拍同周期锁存新参数并复位计数/credit/错误。
  *
  * config-err（blk>totalN/不整除、mxfp4 奇 n）下 cScaleFifo 恒空 ⟹ 停留 sTRANSFER（busy
  * 恒高、done 不发），io.done 不得在该场景接入完成链；恢复靠下次 start 重载或 io.abort。
  *
  * @param cfg WorkerBee 配置（行宽固定 = cfg.cScaleRowW）
  */

import chisel3._
import chisel3.util._

/** WorkerBeeDmaWrOnly — 只写 DMA 引擎（多拍发射器）。
  *
  * 消费顶层 WbOutCvt 输出的转换后密集行（行粒度握手），按 tile 自动地址生成、以 cDmaExtW
  * 位宽逐拍发往外部存储。精度转换与行寄存器归顶层 WbOutCvt，拍拆分归本模块；bufPop 宽
  * totalN×32（按 outFmt 选段、高位已由 cvt 填零），格式族判据单源化 io.outFmt。
  *
  * 搬运对象固定为 C[mTile, nTile]。C buffer 为严格 FIFO，写回顺序与 loopMode 无关，固定
  * 「内 M 外 N」遍历。地址模型（字节地址）：
  *   第 nt 轮块首址 = io.regFile.cAddr + nt*calcConfig.cColTileAddressOffset
  *   块内行首址     = 块首址 + lineCounter*calcConfig.cRowAddressOffset
  *   拍内地址       = curAddr + beatCnt × calcConfig.cBeatAddrStep
  * 块间跳转用「基址 + 轮偏移」线性模型：sNEXT_N 拍执行 curAddr := cAddr + colAddr;
  * colAddr += cColTileAddressOffset（colAddr 锁存初值 = 1 轮偏移），无累积误差。C 侧地址
  * 语义的唯一来源是 regFile.cAddr 与 calcConfig 的 cRow/cColTile/cBeatAddrStep 三偏移。
  *
  * 运行期拍数：beatsPerRowEff = ceil(totalN×elemBits/cDmaExtW)，由 io.outFmt 解码（256
  * 默认 × totalN=16 下 fp32/int32=2 拍、其余 1 拍，半/四分之一占用高位零填充，禁止双行
  * 合拍）。「占用高位零填充」仅是【内部 wire dmaExtWrIF.req.payload.data】口径；【对外端口
  * io.dmaExtWrIF.req.payload.data】已按 addrShiftBytes 同源左移到它在总线字内应在的 byte
  * lane，对外【不是低位口径】。末拍推进单一判据：req.fire && beatCnt===beatsPerRowEff-1。
  *
  * 反压策略（唯一权威 = 下方 `io.bufPop.ready := ...` 那一行）：
  *   bufPop.ready = (state === sTRANSFER_M) & dmaExtWrIF.req.ready & lastBeat，即【组合直通
  *   + 末拍弹出】：拍数据 = io.bufPop.payload >> (beatCnt×cDmaExtW) 取低 cDmaExtW 位，本
  *   模块【不自行锁存整行】；一行在全部 beatsPerRowEff 拍期间由上游（cvt 行寄存 → C FIFO）
  *   持稳不弹，只有末拍（lastBeat = beatCnt === calcConfig.cDmaBufBeatTar）且外部 req.ready
  *   为高那一拍才弹出 ⟹ 行粒度握手、每行只收一次，收行时刻在【末拍】而非首拍。
  *   三条通道的 credit 口径【互不对称，勿互相套用】：RdOnly 的 ostCredit 参与 req.valid
  *   门控；本模块 req.valid 与 bufPop.ready 都不含 ostCredit，ostCredit 只在 sDONE 用作
  *   「在途写拍已排空」判据；WorkerBeeDmaScaleWr 的 req.valid 含 `ostCredit =/= depth`。
  *
  * 门控语义：storeGate 门控位于上游（顶层 cPop mux ready 源 + cvt.in.valid 门），本模块由
  * peekBlock/doneBlock 块协议与 Executor 同步。内层行数锁存实际 M 行（regFile.m）：边界 M
  * 下 Executor 只 push 实际 M 行，若期望 mTile*totalN 行会永久等待挂死。启动后自主逐 tile
  * 块连续写回、块间无上游握手，搬完发一拍 done。io.start 任意状态均可重新装载。
  *
  * 【外部写通道契约】写回拍须按地址顺序提交（零填充拍与密集行步进的正确性依赖保序写）。
  * 对外 data 已按本拍地址的 byte lane 偏移【同源左移】就位、与 strb 逐拍同源；下游若忽略
  * wstrb、按「行数据在总线字低位」整字落盘，将在 off≠0 的拍上错位。
  *
  * @param cfg      WorkerBee 配置
  * @param bufWidth 签名保留参数（bufPop 位宽固定 = totalN×32 密集行）
  * @param hasFp    签名保留参数（规格化硬件在顶层 cvt 内）
  * @param hasMx    签名保留参数（量化支路在顶层 cvt 内，scale 由 cvt.scaleOut → cScaleFifo）
  */
class WorkerBeeDmaWrOnly(cfg: WorkerBeeConfig, bufWidth: Int = 0, hasFp: Boolean = false, hasMx: Boolean = false) extends Module {
  val extW = cfg.cDmaExtW
  val popW = cfg.totalN * 32
  private val beatCntW = math.max(1, log2Up(cfg.cBeatsPerRowMax))

  val io = IO(new Bundle {
    val start = Input(Bool())                        // 启动脉冲（任意状态可重载）
    val peekBlock  = Input(Bool())                   // 写出一个M维Block控制脉冲
    val doneBlock = Output(Bool())                   // 完成一个M维Block控制脉冲
    val done  = Output(Bool())                       // 完成脉冲
    val busy  = Output(Bool())                       // 忙标志
    val err   = Output(Bool())                       // 错误标志
    val dmaExtWrIF = new WorkerBeeDMAExtWriteOnlyIF(cfg, extW)
    val calcConfig = Input(WorkerBeeExePreCalcConfig(cfg))  // tile 数、地址偏移与 cBeatAddrStep
    val regFile = Input(WorkerBeeRegister(cfg))           // 仅使用 cAddr/m（loopMode 不影响 C 写回遍历）
    val bufPop = slave(Stream(UInt(popW.W)))         // 消费 cvt.out 转换后整行（行粒度）
    val bufOccupancy = Input(UInt(log2Up(cfg.cBufferDepth + 1).W))  // 本模块内无消费者（保顶层接线稳定）
    val nCntOut = Output(UInt(cfg.mnkWidth.W))       // 回供当前 N tile 序号，顶层算 cvt.nEff
  })

  

  val sIDLE      = 0.U(3.W)
  val sFIRST_PEEK = 1.U(3.W)
  val sTRANSFER_M  = 2.U(3.W)   // pop buffer 驱动 writeData、推进地址  
  val sNEXT_N = 3.U(3.W)
  val sDONE      = 4.U(3.W)

  val state = RegInit(sIDLE)

  val dmaExtWrIF = Wire(new WorkerBeeDMAExtWriteOnlyIF(cfg, extW))
  val dmaCurStrb = Wire(UInt((cfg.cDmaExtW / 8).W))
  val dmaMaxStrb = Wire(UInt((cfg.cDmaExtW / 8).W))
  val dma2Strb = Wire(UInt((cfg.cDmaExtW / 8 / 2).W))
  val dma4Strb = Wire(UInt((cfg.cDmaExtW / 8 / 4).W))
  dmaMaxStrb := ((1L << (cfg.cDmaExtW / 8)) - 1L).U
  dma2Strb := ((1L << (cfg.cDmaExtW / 8 / 2)) - 1L).U
  dma4Strb := ((1L << (cfg.cDmaExtW / 8 / 4)) - 1L).U
  io.dmaExtWrIF.req.valid := dmaExtWrIF.req.valid
  io.dmaExtWrIF.req.payload.addr := dmaExtWrIF.req.payload.addr
  dmaExtWrIF.req.ready := io.dmaExtWrIF.req.ready

  // AXI byte lane 契约：wstrb 第 i 位选通 wdata[8i+7:8i]，故 data 与 strb 必须做【同源】
  //   移位——两者共用同一个 addrShiftBytes（= addr mod dmaBytes，本拍数据在总线字内的起始
  //   byte lane 偏移），data 侧再 ×8 换成 bit 单位。只移 strb、data 直连总线字低位会使选通
  //   位与数据字节错位：strb 命中的 lane 取到别的列的数据，未命中的 lane 反而压着有效数据。
  // 位宽验算（勿删）：addrShiftBytes = addr(log2Up(dmaBytes)-1, 0) = addr(4,0)（dmaBytes=32）
  //   是【显式 5-bit 切片】，值域 0..31 由【类型系统】保证、【无需取模】；`addrShiftBytes
  //   << 3` 是静态移位 → 8-bit；`data << 8-bit UInt` 是动态移位 → 展宽到 cDmaExtW +
  //   (2^8 - 1) = 511 bit，故【必须】显式 (cDmaExtW-1, 0) 截回总线位宽。
  //   ★切片【不得】退回 addr(log2Up(cDmaExtW/8), 0)（6-bit = addr mod 64）那种写法：那里的
  //     `% dmaBytes` 是【承重的】（唯一作用是丢掉 bit 5）。只删取模不改切片 ⟹ off ∈ [0,63]，
  //     off>=32 时 strb(32-bit) << off 被 32-bit sink 截成 0、data 的 (cDmaExtW-1,0) 窗口
  //     内全 0 ⟹ strb 与 data【双双归零】：外部内存一个字节都不写且不报 err（静默全丢）。
  //   恒等性依据：require(isPow2(cDmaExtW)) ⟹ addr mod dmaBytes ≡ addr(log2Up(dmaBytes)-1,
  //     0)；require(totalN*8 <= cDmaExtW) ⟹ dmaBytes >= 4，切片不越界。
  // 截回所丢弃的高位有两条独立可达路径，丢弃条件 = laneOffset + lanes > dmaBytes
  //   （lanes 由 cDmaDmaBeatTar 三档决定 ∈ {8,16,32}）：
  //   (a) cAddr % dmaBytes ≠ 0 —— off 来自运行期寄存器 cAddr，与 N/totalN/cFmt 无关。
  //       fp32/int32 输出下 cWriteBytes = 64 > dmaBytes = 32 ⟹ cBeatAddrStep = 32、2 拍/行、
  //       lanes = 32（满档），此时【任何 off > 0】都触发截断：每拍静默丢 off 字节。该路径由
  //       【errCode 10】显式报错（WorkerBeeCalcConfig 的 C 写回地址对齐守卫）。
  //   (b) N 非 totalN 整数倍 ⟹ rowOff 不是 cWriteBytes 整数倍，属「strb 档位不按 N 尾数细化」
  //       缺陷族，硬件侧【仍无 errCode】，仿真期由 auditCBeatLanes 的 STRB-OVERFLOW 判据可见。
  // 本模块对同一条总线字有【两套独立】的地址→数据映射，必须保持【互补】，否则 data 与
  //   strb 会一起对准错误的字节：(1) 拍内分段用【编译期常量】cDmaExtW（payload.data :=
  //   bufPop.payload >> (beatCnt * cDmaExtW)）；(2) lane 偏移用【运行期寄存器】addr =
  //   curAddr + beatCnt * calcConfig.cBeatAddrStep。互补性由 CalcConfig 的派生式保证：
  //   cWriteBytes > cDmaBytes ⟹ cBeatAddrStep = cDmaBytes 且多拍；cWriteBytes <= cDmaBytes
  //   ⟹ cDmaBufBeatTar = 0 ⟹ beatCnt ≡ 0，(1) 恒为 0 偏移。
  //   ★二者【无 require / 断言绑定】，改 cBeatAddrStep 公式必须同步复核 (1)(2)；机器可检的
  //     不变式须取【纯张量语义】（同行相邻两拍 addr 差 = cDmaExtW/8），不得从同一条派生链
  //     取值，否则共模失效。
  val dmaBytes = cfg.cDmaExtW / 8
  val addrShiftBytes = dmaExtWrIF.req.payload.addr(log2Up(dmaBytes) - 1, 0)
  io.dmaExtWrIF.req.payload.data := (dmaExtWrIF.req.payload.data << (addrShiftBytes << 3))(cfg.cDmaExtW - 1, 0)
  when(io.calcConfig.cDmaDmaBeatTar === 0.U) { //full
    dmaCurStrb := dmaMaxStrb
  }.elsewhen(io.calcConfig.cDmaDmaBeatTar === 1.U) { // 1/2
    dmaCurStrb := dma2Strb
  }.otherwise { // 1/4
    dmaCurStrb := dma4Strb
  }
  io.dmaExtWrIF.req.payload.strb := dmaCurStrb << (addrShiftBytes)

  dmaExtWrIF.rsp.valid := false.B
  dmaExtWrIF.rsp.ready := false.B
  dmaExtWrIF.rsp.payload.err := false.B  
  dmaExtWrIF.req.payload.strb := 0.U

  val curAddr = RegInit(0.U(cfg.addrWidth.W))  // 当前写地址
  val colAddr = RegInit(0.U(cfg.addrWidth.W))  // 当前写列地址
  val rowStep = RegInit(0.U(cfg.addrWidth.W))  // 内层每拍地址步进
  val lineCounter = RegInit(0.U(cfg.mnkWidth.W))
  val nCnt = RegInit(0.U(cfg.mnkWidth.W))
  val errReg = RegInit(false.B)
  // credit 记账口径 = 「拍数」而非行数：窄总线下每行只 pop 一次但拆多拍写出，最大在途拍数
  // = cBufferDepth × 每行拍数；按行数口径会在多拍下发时回绕，致 sDONE 误判 ostCredit===0
  // 提前放行。+2 行余量吸收拍/行推进与外部 rsp 延迟的瞬态峰值。
  val ostCredit = RegInit(0.U(log2Up((cfg.cBufferDepth + 2) * cfg.cBeatsPerRowMax + 1).W))

  // 本模块【无行锁存】：发拍段组合直通 io.bufPop.payload，bufPop.ready 只在 lastBeat 那一拍
  // 拉高，整行错位由【上游（cvt 行寄存 → C FIFO）持稳】避免。★勿把 lineData/lineValid 一类
  // 行锁存接回数据路：那会造出第二份行锁存，重新引入 cvt.in.ready 组合直通 ∧ cStoreGate
  // 寄存 ∧ FIFO 未空的同拍竞态（见 WorkerBee.scala 同名禁动区声明）。
  val beatCnt   = RegInit(0.U(beatCntW.W))

  val lastBeat: Bool = beatCnt === io.calcConfig.cDmaBufBeatTar 

  io.nCntOut := nCnt  // 回供顶层算 cvt.nEff

  io.dmaExtWrIF.rsp.ready        := true.B


  io.bufPop.ready := (state === sTRANSFER_M) & io.dmaExtWrIF.req.ready & lastBeat
  dmaExtWrIF.req.valid        := (state === sTRANSFER_M) & io.bufPop.valid
  dmaExtWrIF.req.payload.addr := curAddr + beatCnt * io.calcConfig.cBeatAddrStep
  dmaExtWrIF.req.payload.data := (io.bufPop.payload >> (beatCnt * cfg.cDmaExtW.U))(cfg.cDmaExtW - 1, 0)

  when((state === sTRANSFER_M) && io.dmaExtWrIF.req.fire) {
    when(lastBeat) {
      beatCnt   := 0.U
    }.otherwise {
      beatCnt := beatCnt + 1.U
    }
  }

  io.done := false.B
  io.busy := state =/= sIDLE
  io.err  := errReg
  io.doneBlock := false.B

  when(state === sIDLE){
    ostCredit := 0.U
  }.otherwise{
    when(io.dmaExtWrIF.req.fire & io.dmaExtWrIF.rsp.fire){
      ostCredit := ostCredit
    }.elsewhen(io.dmaExtWrIF.rsp.fire){
      ostCredit := ostCredit - 1.U
    }.elsewhen(io.dmaExtWrIF.req.fire){
      ostCredit := ostCredit + 1.U
    }
  }

  when(io.dmaExtWrIF.rsp.fire){
    when(io.dmaExtWrIF.rsp.payload.err){
      errReg := true.B
    }
  }
  

  // start 重载（任意状态生效）：用于 Executor 异常回 idle 后强制重启。
  private def startReload(): Unit = {
    state       := sFIRST_PEEK
    errReg      := false.B
    lineCounter := 0.U
    // 固定「内 M 外 N」遍历：内层沿实际 M 行（regFile.m，不含 padding），外层扫 nTile 个 tile
    curAddr := io.regFile.cAddr
    rowStep := io.calcConfig.cRowAddressOffset
    colAddr := io.calcConfig.cColTileAddressOffset
    nCnt    := 0.U
    // 清拍计数：beatCnt 残留会让重启后首行拍序错位、收行时刻错拍
    beatCnt   := 0.U
  }

  switch(state) {

    is(sIDLE) { 
      when(io.start) {
        startReload()
      }


    }
    
    is(sFIRST_PEEK) {
      when(io.peekBlock) {
        state := sTRANSFER_M
      }
    }

    is(sTRANSFER_M){
        // 末拍（行内最后一拍）才做行推进；拍计数清除在上方拍推进 when 块处理
        when(dmaExtWrIF.req.fire && lastBeat) {
          when(lineCounter === io.regFile.m - 1.U) {
            lineCounter := 0.U
            state := sNEXT_N
            io.doneBlock := true.B
          }.otherwise {
            lineCounter := lineCounter + 1.U
            curAddr := curAddr + rowStep
          }
        }
    }


    is(sNEXT_N) {
      when(nCnt === io.calcConfig.nTile - 1.U){
        state := sDONE
      }.otherwise{
        when(io.peekBlock){
          nCnt := nCnt + 1.U
          curAddr := io.regFile.cAddr + colAddr
          colAddr := colAddr + io.calcConfig.cColTileAddressOffset
          state := sTRANSFER_M
        }
      }
      
    }

 
    is(sDONE) {
      // start 重载绕过 sIDLE ⟹ 此处另需 ostCredit := 0.U（last-connect 覆盖同拍增减）
      when(io.start) {
        startReload()
        ostCredit := 0.U
      }.elsewhen(ostCredit === 0.U) {
        io.done := true.B
        state := sIDLE
      }
    }
  }
}



class WorkerBeeDmaScaleWr(cfg: WorkerBeeConfig) extends Module {
  private val rowW = cfg.cScaleRowW
  // 唯一 scale 缓冲 = 顶层深 2 cScaleFifo，故在途上限/credit 位宽 = log2Up(2+1) = 2 bit。
  private val depth = 2

  val io = IO(new Bundle {
    val start = Input(Bool())                        // 启动脉冲（任意状态可重载）
    val done  = Output(Bool())                       // 完成脉冲
    val busy  = Output(Bool())                       // 忙标志
    val err   = Output(Bool())                       // 错误标志（rsp.err 锁存）
    val dmaExtWrIF = WorkerBeeDMAExtWriteOnlyIF(cfg, rowW)  // 外部写通道（行宽 = cScaleRowW）
    val calcConfig = Input(WorkerBeeExePreCalcConfig(cfg))  // nTile + cScale 行/列步进
    val baseAddr = Input(UInt(cfg.addrWidth.W))      // scale 写回基址（← regFile.cScaleAddr）
    val regFile = Input(WorkerBeeRegister(cfg))       // 仅使用 m（内层行数）
    val bufPop = slave(Stream(UInt(rowW.W)))         // 从 cScaleFifo pop 数据
  })

  val sIDLE     = 0.U(2.W)
  val sTRANSFER = 1.U(2.W)   // pop FIFO 发写拍、推进地址
  val sNEXT_N   = 2.U(2.W)   // N tile 轮间跳转（线性模型）
  val sDONE     = 3.U(2.W)

  val state = RegInit(sIDLE)

  val curAddr = RegInit(0.U(cfg.addrWidth.W))  // 当前写地址（行首址）
  val colAddr = RegInit(0.U(cfg.addrWidth.W))  // 「下一轮」累计列偏移（线性模型）
  val rowStep = RegInit(0.U(cfg.addrWidth.W))  // 内层每行地址步进
  val lineTarget  = RegInit(0.U(16.W))         // 内层行数 = regFile.m
  val lineCounter = RegInit(0.U(16.W))
  val nCnt = RegInit(0.U(cfg.mnkWidth.W))
  val ostCredit = RegInit(0.U(log2Up(depth + 1).W))  // 在途写拍数（上限 = 顶层 FIFO 深度 2）
  val errReg = RegInit(false.B)

  when(state === sIDLE) {
    ostCredit := 0.U
    errReg := false.B
  }.otherwise {
    when(io.dmaExtWrIF.req.fire & io.dmaExtWrIF.rsp.fire) {
      ostCredit := ostCredit
    }.elsewhen(io.dmaExtWrIF.req.fire) {
      ostCredit := ostCredit + 1.U
    }.elsewhen(io.dmaExtWrIF.rsp.fire) {
      ostCredit := ostCredit - 1.U
    }
    when(io.dmaExtWrIF.rsp.fire) {
      when(io.dmaExtWrIF.rsp.payload.err) {
        errReg := true.B
      }
    }
  }

  // 默认输出驱动：req 被在途上限（FIFO 深度）门控，防外部 rsp 长延迟堆积超出计数位宽
  io.dmaExtWrIF.req.valid         := (state === sTRANSFER) & io.bufPop.valid & (ostCredit =/= depth.U)
  io.dmaExtWrIF.req.payload.addr  := curAddr
  io.dmaExtWrIF.req.payload.data  := io.bufPop.payload
  // strb 满档常量必须写成 ((1 << (rowW/8)) - 1)：Scala 运算符优先级由【首字符】决定，`+ -`
  //   高于 `< >`，故 `(1 << (rowW / 8) - 1)` 解析成 1 << ((rowW/8) - 1)。配合 rowW =
  //   cScaleRowW = totalN/2 bit：totalN=16（rowW/8 = 1 字节）时两式都算 1、巧合相同；
  //   totalN=32 时错式得 0b10（lane0 不写）、totalN=64 时得 0b1000（只写 lane3）。
  // scaleC 豁免于 byte lane 同源移位契约的理由 = 【端口宽度 = 1 字节（rowW = 8 bit
  //   @ totalN=16）⟹ lane 窗口退化为单 lane、移位恒为 0】，而【不是】「strb 为常量满档」；
  //   totalN 提到 32 时须把 scaleC 纳入 lane 审计并在 Config 侧解耦 cScaleRowW。
  io.dmaExtWrIF.req.payload.strb  := ((1 << (rowW / 8)) - 1).U
  io.bufPop.ready                 := (state === sTRANSFER) & io.dmaExtWrIF.req.ready
  io.dmaExtWrIF.rsp.ready         := true.B

  io.done := false.B
  io.busy := state =/= sIDLE
  io.err  := errReg

  switch(state) {
    is(sIDLE) {
      errReg := false.B
      lineCounter := 0.U
      // 防重复 start 竞态：重载拍已直达 sTRANSFER，仅当本拍确在 sIDLE 才允许 start 推进
      when(io.start && state === sIDLE) {
        state := sTRANSFER
      }
    }

    is(sTRANSFER) {
      // start 重载：途中强制重启 → 同拍重载参数（下方 |io.start 锁存块）并复位计数/credit
      when(io.start) {
        errReg      := false.B
        lineCounter := 0.U
        nCnt        := 0.U
        ostCredit   := 0.U
      }.elsewhen(io.dmaExtWrIF.req.fire) {
        when(lineCounter === lineTarget - 1.U) {
          lineCounter := 0.U
          state := sNEXT_N
        }.otherwise {
          lineCounter := lineCounter + 1.U
          curAddr := curAddr + rowStep
        }
      }
    }

    is(sNEXT_N) {
      // sNEXT_N 为单拍过渡态；重载拍直接回 sTRANSFER 起跑（锁存块已复位回首轮参数）
      when(io.start) {
        state       := sTRANSFER
        errReg      := false.B
        lineCounter := 0.U
        nCnt        := 0.U
        ostCredit   := 0.U
      }.elsewhen(nCnt === io.calcConfig.nTile - 1.U) {
        state := sDONE
      }.otherwise {
        // 线性模型：下一轮起始 = base + (nt+1)*colTileOffset，colAddr 存累计轮偏移，无三角累积
        nCnt := nCnt + 1.U
        curAddr := io.baseAddr + colAddr
        colAddr := colAddr + io.calcConfig.scaleCColTileAddressOffset
          state := sTRANSFER
      }
    }

    is(sDONE) {
      // start 重载绕过 sIDLE ⟹ 此处另需 ostCredit := 0.U（last-connect 覆盖同拍增减）
      when(io.start) {
        state       := sTRANSFER
        errReg      := false.B
        lineCounter := 0.U
        nCnt        := 0.U
        ostCredit   := 0.U
      }.elsewhen(ostCredit === 0.U) {
        io.done := true.B
        state := sIDLE
      }
    }
  }

  // 遍历参数锁存：sIDLE 每拍刷新；重载拍（任意状态收到 start）同周期锁存——start 为单拍
  // executePulse，不能经 sIDLE 中转。
  when(state === sIDLE || io.start) {
    curAddr    := io.baseAddr
    colAddr    := io.calcConfig.scaleCColTileAddressOffset
    rowStep    := io.calcConfig.scaleCRowAddressOffset
    lineTarget := io.regFile.m
    nCnt       := 0.U
  }

}
