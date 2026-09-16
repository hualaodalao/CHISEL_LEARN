/** ForagerBee 链式描述符控制器 —— 在 io.cmd 与 Dispatcher 之间插入，
  * 支持通过独立读总线从内存中自动拉取后续描述符，串联执行多个 DMA 命令。
  *
  * FSM：
  *   sPASS    → 透传模式（cmdIn→cmdOut / doneIn→doneOut）
  *   sWAIT_DONE → 等待当前描述符执行完成
  *   sFETCH   → 从 chainBus 读取下一描述符
  *   sINJECT  → 将描述符打包为 FbCmd 注入 cmdOut
  *
  * @param cfg ForagerBee 配置
  */

import chisel3._
import chisel3.util._

/** 描述符内存布局：定义 FbCmd 各字段在描述符 beat 序列中的 bit 偏移（单一真源）。
  * 字段按 FbCmd 声明顺序紧密排列，末尾追加 nextDescAddr。
  *
  * srcFmt/dstFmt 随 [[FbDataFmt]] 扩为 ChiselEnum(4-bit) 而由 3-bit 拓宽到 4-bit。
  *
  * **v3 布局（单向二进制不兼容，方向务必看清）**：仅从描述符**尾部**删除 cvtScale(32-bit) 与
  * cvtZeroPoint(32-bit) 两个字段——量化参数改由 ForagerBee 顶层 IO 全局供给
  * （`io.cvtScale`/`io.cvtZeroPoint`，仅 enableConversion 时存在），不再随每条命令/描述符携带。
  * 所有保留字段的 bit 偏移与 v2 逐一相同，dstFmt 成为末字段，故 **v2 打包的描述符镜像在 v3
  * 硬件下仍可正确解码**（descBeats 6→5 只是少取尾部一拍）——旧镜像无需重打。
  * totalBits 相应缩减 64 bit：maxDims=5 时 661→597，maxDims=4 时 578→514。
  * **真正被破坏的方向**：(a) v3 镜像跑在 v2 硬件（v2 会多读一拍尾部量化字段）；
  * (b) 任何按 totalBits/descBeats 计算**槽位间距或缓冲尺寸**的软件（dw=128+maxDims=5 时
  * descBeats 6→5，每描述符少 1 拍读延迟属正向；出厂 dw=256 时 3→3 不变）。
  * 生成 v3 新镜像的软件侧打包见测试 serializeDescriptor。
  *
  * 历史：**v2** 已删除 elemBytesLog2(2-bit) 与 cvtEnable(1-bit)——元素粒度改由
  * srcFmt/dstFmt 单一真源派生、转换激活派生为 srcFmt =/= dstFmt。
  *
  * 已知限制（本次不改动）：opBits 仍为 2，而 [[FbOp]] 已是 8 个成员的 3-bit 枚举，
  * 故链式描述符目前只能编码 COPY/TRANSPOSE/PERMUTE(0..2)；扩展 op 宽度需同步
  * 软件打包与本文两处 opBits 常量。
  *
  * **已知限制：链式描述符不支持 MX 量化（仅 enableConversion=true 构建）**。该构建下布局不序列化
  * scaleAddr/scaleBlockSize，而 MX 量化激活已由 dstFmt 派生（isMx(dstFmt)），若直接放行会向物理
  * 地址 0x0 发出 E8M0 scale 写突发（内存破坏）。故 FbChainController 在 injCmd 组装末尾做「入口自守」：
  * isMx(dstFmt) 时强制 dimCount=0，由引擎 cBadBase 拒绝并回 err done（该自守仅 enableConversion=true
  * 时生成，且会截断整条链——见 FSM sWAIT_DONE 对 doneIn.err 的处理）。如需链式 MX 量化，
  * 须先扩展描述符布局（新增 scaleAddr/scaleBlockSize 字段）再解除该自守。
  *
  * **enableConversion=false 的 copy-only 构建不适用上述限制**：该构建无量化器与 sSWRITE 通路，
  * MX 编码退化为 1 字节/元素裸格式，srcFmt==dstFmt==MXE4M3/MXE5M2 是**合法纯字节拷贝**，
  * 入口自守不生成，链式 MX 裸拷贝与经 io.cmd 直连路径行为一致（见 ForagerBeeConfig 契约）。
  */
object FbDescLayout {
  // --- 各字段位宽常量（纯 Scala，编译期可求值） ---
  val opBits = 2
  val tagBits = 8
  val dimCountBits = 3
  def shapeBits(cfg: ForagerBeeConfig): Int = cfg.maxDims * 16
  def srcStrideBits(cfg: ForagerBeeConfig): Int = cfg.maxDims * cfg.addressWidth
  def dstStrideBits(cfg: ForagerBeeConfig): Int = cfg.maxDims * cfg.addressWidth
  def srcAddrBits(cfg: ForagerBeeConfig): Int = cfg.addressWidth
  def dstAddrBits(cfg: ForagerBeeConfig): Int = cfg.addressWidth
  def permVecBits(cfg: ForagerBeeConfig): Int = cfg.maxDims * 3
  val padBeforeBits = 2 * 16
  val padAfterBits = 2 * 16
  def nextDescAddrBits(cfg: ForagerBeeConfig): Int = cfg.addressWidth
  val chainModeBits = 1
  val srcFmtBits = FbDataFmt.getWidth
  val dstFmtBits = FbDataFmt.getWidth

  /** 所有字段偏移（纯 Scala case class，FbChainController 与 Spec 共同消费） */
  case class Offsets(cfg: ForagerBeeConfig) {
    val offOp: Int = 0
    val offTag: Int = offOp + opBits
    val offDimCount: Int = offTag + tagBits
    val offShape: Int = offDimCount + dimCountBits
    val offSrcStride: Int = offShape + shapeBits(cfg)
    val offDstStride: Int = offSrcStride + srcStrideBits(cfg)
    val offSrcAddr: Int = offDstStride + dstStrideBits(cfg)
    val offDstAddr: Int = offSrcAddr + srcAddrBits(cfg)
    val offPermVec: Int = offDstAddr + dstAddrBits(cfg)
    val offPadBefore: Int = offPermVec + permVecBits(cfg)
    val offPadAfter: Int = offPadBefore + padBeforeBits
    val offNextDescAddr: Int = offPadAfter + padAfterBits
    val offChainMode: Int = offNextDescAddr + nextDescAddrBits(cfg)
    val offSrcFmt: Int = offChainMode + chainModeBits
    val offDstFmt: Int = offSrcFmt + srcFmtBits
    /** v3：dstFmt 为最后一个序列化字段（cvtScale/cvtZeroPoint 已上移到顶层 IO） */
    val end: Int = offDstFmt + dstFmtBits
  }

  /** 获取指定配置的偏移表（elaboration 期自检：字段宽度之和 == totalBits） */
  def offsets(cfg: ForagerBeeConfig): Offsets = {
    val o = new Offsets(cfg)
    require(o.end == totalBits(cfg),
      s"FbDescLayout: 字段宽度之和(${o.end}) != totalBits(${totalBits(cfg)})，布局计算有误")
    o
  }

  def totalBits(cfg: ForagerBeeConfig): Int = {
    opBits + tagBits + dimCountBits + shapeBits(cfg) + srcStrideBits(cfg) + dstStrideBits(cfg) +
      srcAddrBits(cfg) + dstAddrBits(cfg) + permVecBits(cfg) +
      padBeforeBits + padAfterBits + nextDescAddrBits(cfg) + chainModeBits +
      srcFmtBits + dstFmtBits
  }

  def descBeats(cfg: ForagerBeeConfig): Int = {
    if (cfg.chainDescBeats > 0) cfg.chainDescBeats
    else {
      val total = totalBits(cfg)
      (total + cfg.dataWidth - 1) / cfg.dataWidth
    }
  }
}

class FbChainController(cfg: ForagerBeeConfig) extends Module {
  private val addrW = cfg.addressWidth
  private val dataW = cfg.dataWidth
  private val descBeats = FbDescLayout.descBeats(cfg)

  val io = IO(new Bundle {
    /** 来自顶层 io.cmd */
    val cmdIn = slave(Stream(new FbCmd(cfg)))
    /** 输出到 Dispatcher */
    val cmdOut = master(Stream(new FbCmd(cfg)))
    /** 拦截 engine done */
    val doneIn = slave(Stream(new FbDone(cfg)))
    /** 上报给顶层 io.done */
    val doneOut = master(Stream(new FbDone(cfg)))
    /** 独立读总线（仅用 rd/rdData） */
    val chainBus = master(new FbStreamBus(dataW, addrW))
  })

  // --- FSM 状态 ---
  val sPASS      = 0.U(3.W)
  val sWAIT_DONE = 1.U(3.W)
  val sFETCH     = 2.U(3.W)
  val sINJECT    = 3.U(3.W)

  val state = RegInit(sPASS)

  // --- 链式状态寄存器 ---
  val nextDescAddr = RegInit(0.U(addrW.W))
  val chainTag     = RegInit(0.U(8.W))
  val chainLen     = RegInit(0.U(16.W))

  // --- 描述符数据寄存器 ---
  val descRegs  = Reg(Vec(descBeats, UInt(dataW.W)))
  val fetchCnt  = RegInit(0.U(log2Up(descBeats + 1).W))
  val fetchSent = RegInit(false.B) // rd 请求已发出

  // --- Tie off chainBus wr/wrResp ---
  io.chainBus.wr.valid := false.B
  io.chainBus.wr.payload.addr := 0.U
  io.chainBus.wr.payload.len  := 0.U
  io.chainBus.wr.payload.data := 0.U
  io.chainBus.wr.payload.strb := 0.U
  io.chainBus.wr.payload.sof  := false.B
  io.chainBus.wr.payload.eof  := false.B
  io.chainBus.wrResp.ready := false.B

  // --- 默认输出 ---
  io.cmdOut.valid   := false.B
  io.cmdOut.payload := 0.U.asTypeOf(new FbCmd(cfg))
  io.cmdIn.ready    := false.B

  io.doneOut.valid   := false.B
  io.doneOut.payload := 0.U.asTypeOf(new FbDone(cfg))
  io.doneIn.ready    := false.B

  io.chainBus.rd.valid        := false.B
  io.chainBus.rd.payload.addr := 0.U
  io.chainBus.rd.payload.len  := 0.U
  io.chainBus.rdData.ready    := false.B

  // --- 描述符解析：从 descRegs 中提取 FbCmd ---
  // 将所有 beat 拼接为一个大的 bit 向量（little-endian beat 顺序）
  val descBitsWire = Wire(UInt((descBeats * dataW).W))
  descBitsWire := descRegs.asUInt

  // 从 bit 向量中按顺序提取各字段
  private def extract(offset: Int, width: Int): UInt = descBitsWire(offset + width - 1, offset)

  // 偏移单一真源：从 FbDescLayout 获取（elaboration 期自检宽度一致性）
  private val lo = FbDescLayout.offsets(cfg)

  // 组装注入 cmd
  val injCmd = Wire(new FbCmd(cfg))
  injCmd.op        := suppressEnumCastWarning { extract(lo.offOp, FbDescLayout.opBits).pad(FbOp.getWidth).asTypeOf(FbOp()) }
  injCmd.tag       := extract(lo.offTag, FbDescLayout.tagBits)
  injCmd.dimCount  := extract(lo.offDimCount, FbDescLayout.dimCountBits)
  for (d <- 0 until cfg.maxDims) {
    injCmd.shape(d)     := extract(lo.offShape + d * 16, 16)
    injCmd.srcStride(d) := extract(lo.offSrcStride + d * addrW, addrW)
    injCmd.dstStride(d) := extract(lo.offDstStride + d * addrW, addrW)
    injCmd.permVec(d)   := extract(lo.offPermVec + d * 3, 3)
  }
  injCmd.srcAddr       := extract(lo.offSrcAddr, addrW)
  injCmd.dstAddr       := extract(lo.offDstAddr, addrW)
  for (d <- 0 until 2) {
    injCmd.padBefore(d) := extract(lo.offPadBefore + d * 16, 16)
    injCmd.padAfter(d)  := extract(lo.offPadAfter + d * 16, 16)
  }
  injCmd.nextDescAddr  := extract(lo.offNextDescAddr, addrW)
  injCmd.chainMode     := extract(lo.offChainMode, FbDescLayout.chainModeBits).asBool
  // srcFmt/dstFmt：4-bit UInt → FbDataFmt ChiselEnum（枚举非满编码，故抑制 unsafe-cast 告警；
  // 未定义编码由引擎 cBad 校验与转换器 default 分支兜住）
  injCmd.srcFmt        := suppressEnumCastWarning { extract(lo.offSrcFmt, FbDescLayout.srcFmtBits).asTypeOf(FbDataFmt()) }
  injCmd.dstFmt        := suppressEnumCastWarning { extract(lo.offDstFmt, FbDescLayout.dstFmtBits).asTypeOf(FbDataFmt()) }
  // v3：量化参数 cvtScale/cvtZeroPoint 不再序列化于描述符（改由 ForagerBee 顶层 IO 全局供给）
  // im2col fields default to 0 (chain controller does not support im2col descriptors in v1)
  for (d <- 0 until 2) {
    injCmd.im2colKernel(d)   := 0.U
    injCmd.im2colStride(d)   := 0.U
    injCmd.im2colPad(d)      := 0.U
    injCmd.im2colDilation(d) := 0.U
  }
  for (d <- 0 until 3) {
    injCmd.im2colInShape(d) := 0.U
  }
  // scatter/gather fields default to 0 (chain controller does not support SG descriptors in v1)
  injCmd.sgListAddr   := 0.U
  injCmd.sgEntryCount := 0.U
  // srcStartIdx default to 0 (chain controller does not serialize this field in v1)
  injCmd.srcStartIdx.foreach(_ := 0.U)
  // tile2linear fields default to 0 (chain controller does not support TILE2LINEAR descriptors in v1)
  injCmd.t2lMatCols     := 0.U
  injCmd.t2lTileRows    := 0.U
  injCmd.t2lTileCols    := 0.U
  injCmd.t2lNumTileRows := 0.U
  injCmd.t2lNumTileCols := 0.U
  // MX quantization fields default to 0：链式描述符不序列化 scaleAddr/scaleBlockSize。
  // 仅在 enableConversion=true 构建中存在量化通路，故 MX dstFmt 需入口自守（见下）；
  // enableConversion=false 的 copy-only 构建无量化器，MX 退化为 1 字节/元素裸格式，纯拷贝合法。
  injCmd.scaleAddr      := 0.U
  injCmd.scaleBlockSize := 0.U

  // --- 入口自守（安全关键，仅 enableConversion=true 构建）：链式描述符携带 isMx(dstFmt) 时
  // 强制 dimCount=0，让引擎报 err ---
  // 量化激活已派生为 isMx(dstFmt)（原「MX 量化使能」开关字段已删除），而链式描述符不携带
  // scaleAddr/scaleBlockSize（上方硬连线为 0）。若不拦截，带 MX dstFmt 的链式描述符会真的
  // 走量化通路，并向物理地址 0x0 发出 E8M0 scale 的 strb 部分写突发（len=0 单拍）——内存破坏。
  // 沿用 FbTile2LinearExpander 的「入口自守」模式：dimCount=0 → 引擎 cBadBase 拒绝并回 err done。
  // 注意：dstFmt 是描述符 extract 出来的运行期值，本判断是组合逻辑而非 Scala 编译期判断；
  // 依赖 Wire 的 last-connect 语义覆盖前面 injCmd.dimCount 的 extract 赋值。
  //
  // **门控理由**：该内存破坏风险只在 enableConversion=true 时成立——copy-only 构建根本没有
  // 量化器与 sSWRITE 通路。按 ForagerBeeConfig/repowiki 契约，enableConversion=false 构建中
  // MX 编码退化为 1 字节/元素裸格式，srcFmt==dstFmt==MXE4M3/MXE5M2 是**合法纯字节拷贝**，
  // 且应与经 io.cmd 直连（sPASS 原样透传）路径行为一致。故自守仅在 enableConversion=true 生成。
  if (cfg.enableConversion) {
    when(FbDataFmt.isMx(injCmd.dstFmt)) {
      injCmd.dimCount := 0.U
    }
  }

  // --- FSM 逻辑 ---
  switch(state) {
    is(sPASS) {
      // 透传 cmdIn → cmdOut
      io.cmdOut.valid   := io.cmdIn.valid
      io.cmdOut.payload := io.cmdIn.payload
      io.cmdIn.ready    := io.cmdOut.ready

      // 透传 doneIn → doneOut（非链模式 chainLen=1）
      io.doneOut.valid           := io.doneIn.valid
      io.doneOut.payload         := io.doneIn.payload
      io.doneOut.payload.chainLen := 1.U
      io.doneIn.ready            := io.doneOut.ready

      // 当 cmdOut.fire 且 chainMode=true 时进入链模式
      when(io.cmdOut.fire && io.cmdOut.payload.chainMode) {
        nextDescAddr := io.cmdOut.payload.nextDescAddr
        chainTag     := io.cmdOut.payload.tag
        chainLen     := 1.U
        state        := sWAIT_DONE
      }
    }

    is(sWAIT_DONE) {
      // 等待 doneIn
      io.doneIn.ready := true.B
      // 不透传 cmdIn（阻塞）
      io.cmdIn.ready := false.B

      when(io.doneIn.fire) {
        chainLen := chainLen + 1.U
        when(nextDescAddr === 0.U || io.doneIn.payload.err) {
          // 链结束或出错：构造最终 done
          io.doneOut.valid            := true.B
          io.doneOut.payload.tag      := chainTag
          io.doneOut.payload.err      := io.doneIn.payload.err
          io.doneOut.payload.chainLen := chainLen // 包含当前这条也已+1
          state := sPASS
        }.otherwise {
          // 继续取下一描述符
          fetchCnt  := 0.U
          fetchSent := false.B
          state     := sFETCH
        }
      }
    }

    is(sFETCH) {
      // 发读请求
      when(!fetchSent) {
        io.chainBus.rd.valid        := true.B
        io.chainBus.rd.payload.addr := nextDescAddr
        io.chainBus.rd.payload.len  := (descBeats - 1).U
        when(io.chainBus.rd.fire) {
          fetchSent := true.B
        }
      }

      // 接收读数据
      io.chainBus.rdData.ready := true.B
      when(io.chainBus.rdData.fire) {
        descRegs(fetchCnt) := io.chainBus.rdData.payload.data
        fetchCnt := fetchCnt + 1.U
        when(fetchCnt === (descBeats - 1).U) {
          state := sINJECT
        }
      }
    }

    is(sINJECT) {
      // 将解析出的 cmd 推送给 Dispatcher
      io.cmdOut.valid   := true.B
      io.cmdOut.payload := injCmd

      when(io.cmdOut.fire) {
        // 锁存新的 nextDescAddr
        nextDescAddr := injCmd.nextDescAddr
        state        := sWAIT_DONE
      }
    }
  }
}
