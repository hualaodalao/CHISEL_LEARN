/** HiveCore 完整仿真用例：M=32, N=16, K=16, FP16, totalN=16
  *
  * 验证 FSM 完整流程：寄存器配置 → EXECUTE → DMA 加载/存储 → 完成
  * 外部存储器由测试 Memory Model 模拟（地址寻址）：
  *   - 软件侧按与硬件相同的地址公式（stride 寄存器仅 4bit，本用例退化
  *     为 0）逐 beat 推导期望地址；dmaA/dmaB 响应时先读 cmd.addr 与
  *     期望对账（未知/不符地址直接报错），再供对应行数据，防止
  *     「按序供数掩盖地址公式 bug」
  *   - dmaC 逐 beat 校验写地址/len 后记录，C 矩阵重建同时依赖
  *     地址对账与严格 FIFO 顺序
  */

import chisel3._
import chisel3.simulator.scalatest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.io._
import java.nio.file._

class HiveCoreSimCase extends AnyFlatSpec with Matchers with ChiselSim with HiveFstSim with HiveStatusPollApi {

  // ========== 测试参数 ==========
  val M = 32
  val N = 32
  val K = 32

  val A_BASE: Long = 0x00000000L
  val B_BASE: Long = 0x00100000L
  val C_BASE: Long = 0x00200000L

  val simDir = "/Users/wangliutailong/Qoder_Proj/Chisel_Learn/sim"

  // 工具方法（floatToFp16/fp16ToFloat/writeMatrix*/writeBigIntMatrixHex/
  // extractBits/fp32BitsToFloat）统一收敛至公共类 HiveSimCommon（限定调用）

  // ========== 主测试 ==========

  behavior of "HiveCore GEMM Simulation"

  it should "run complete GEMM M=32 N=16 K=16 FP16 totalN=16" in {

    // --- 配置 ---
    // 注意: DMA 的 bufAvailability 端口宽度由 aBufferDepth 决定（设计缺陷），
    // 因此 aBufferDepth 必须 >= cBufferDepth 以避免信号截断。
    val cfg = HiveCoreConfig(
      arrayN = 8,
      clusterM = 2,         // totalN = 16
      aBufferDepth = 2048,
      cBufferDepth = 2048,
      aW = 16,
      bW = 16,
      cW = 40
    )

    val totalN = cfg.totalN     // 16
    val aEffW  = cfg.aEffW      // 16
    val cEffW  = cfg.cEffW      // 40（C buffer 行宽口径；写回经顶层 cvt 40→32 转换）
    // P3 迁移：写回走新架构（cvt 行寄存器+格式转换 → cDma WrOnly 行内拆拍）；
    // cDmaExtW 默认 256（P0 契约，只允许 2 的 N 次幂，旧 640 满宽与 5B/元素布局废止）：
    // fp32 默认输出 = 每行 2 拍（拍内 8 元素、低列号在低位）、行步进 n*4、tile 步进 totalN*4

    // --- 生成矩阵 ---
    // element[row][col] = (row + 1.0) + col * 0.1，以 FP16 编码
    val aMatrix = Array.tabulate(M, K)((i, j) => HiveSimCommon.floatToFp16((i + 1.0f) + j * 0.1f))
    val bMatrix = Array.tabulate(K, N)((i, j) => HiveSimCommon.floatToFp16((i + 1.0f) + j * 0.1f))

    // --- 写入输入矩阵文件 ---
    Files.createDirectories(Paths.get(simDir))
    HiveSimCommon.writeMatrixHex(s"$simDir/A_matrix.txt", aMatrix, 16)
    HiveSimCommon.writeMatrixHex(s"$simDir/B_matrix.txt", bMatrix, 16)

    // 浮点数格式的 A/B 矩阵
    val aFloat = aMatrix.map(_.map(h => HiveSimCommon.fp16ToFloat(h)))
    val bFloat = bMatrix.map(_.map(h => HiveSimCommon.fp16ToFloat(h)))
    HiveSimCommon.writeMatrixFloat(s"$simDir/A_matrix_float.txt", aFloat)
    HiveSimCommon.writeMatrixFloat(s"$simDir/B_matrix_float.txt", bFloat)

    // --- 计算 stride ---
    val aStride = K * (aEffW / 8)       // 16*2 = 32 bytes
    val bStride = N * (aEffW / 8)       // 16*2 = 32 bytes
    val cStride = N * (cEffW / 8)       // 16*4 = 64 bytes
    // 注意：寄存器映射中每个 stride 仅 4 bit（regs(6) 的 [3:0]/[7:4]/[11:8]），
    // 只能表达 0~15 字节。本用例 stride(32/64) 无法完整写入，截断后
    // 三个 stride 低 4 位均为 0，行步长项为 0，故行步进不参与地址计算
    println(s"[HiveCoreSimCase] WARNING: stride 字段仅 4bit，实际 stride 超出表达范围（既有寄存器映射限制）")

    // --- Memory Model: 地址寻址（软件侧按硬件同公式推导期望地址） ---
    // stride 寄存器仅 4bit，本用例 aStride/bStride/cStride 实际写入 0，
    // 故行步进为 0（块内各行地址退化重合）；块间跳转仍由 aCol/bCol/cCol
    // 偏移贡献。期望地址序列与硬件 aDma/bDma/cDma 的地址生成公式逐一对账
    val cWriteLog = scala.collection.mutable.ArrayBuffer[(Long, BigInt)]()

    // --- DMA-A (A buffer) 期望地址序列：aDma 扫描 = nTile 外 → kTile 中 →
    // 实际 M 行内；addr = aAddr + mIdx*aRowOff + kt*aColOff。
    // 行步长与硬件 HiveCore calcConfig 新公式对齐：
    // aRowAddressOffset := regFile.k*(aW/8)，stride 寄存器不参与
    // （stride 相关代码用户后续自行删除） ---
    val aKTiles = (K + totalN - 1) / totalN
    val aNTiles = (N + totalN - 1) / totalN
    val aRowOff = K.toLong * (cfg.aW / 8)             // = regFile.k*(aW/8)
    val aColOff = totalN.toLong * (aEffW / 8)
    val expAAddrSeq = scala.collection.mutable.ArrayBuffer[Long]()
    // A 轮间行为（与硬件 RdOnly sNEXT_COL 同源）：每 nTile 轮末 curAddr 复位回
    // aAddr（不带轮间列偏移），各轮地址序列完全重复；供数内容同样每轮重复。
    // 供数/计数必须由 req.valid 拍驱动（rsp.ready 恒真，按它计数会把 sNEXT_COL/
    // 启动空拍误计进来致下标错位）
    for (_ <- 0 until aNTiles; kt <- 0 until aKTiles; m <- 0 until M)
      expAAddrSeq += A_BASE + m * aRowOff + kt * aColOff
    val dmaATotalBeats = expAAddrSeq.size
    // beat 数据 = 行 m 的 kt 列切片（顺序与期望地址序列同下标）
    def genDmaABeat(idx: Int): BigInt = {
      // 各 nTile 轮重复供同一 A 行集，轮信息需模掉（与 genDmaBWeightBeat 对称）
      val kt = (idx % (aKTiles * M)) / M
      val row = idx % M
      var data = BigInt(0)
      for (i <- 0 until totalN) {
        val col = kt * totalN + i
        val value = if (row < M && col < K) aMatrix(row)(col) else 0
        data = data | (BigInt(value & 0xFFFF) << (i * aEffW))
      }
      data
    }

    // --- DMA-B (B buffer) 期望地址序列（按 HiveCoreDmaRdOnly 现行硬件行为闭式推演）---
    // 【#136-KS8 订正：下方机制描述原文是 B5 修复【前】的旧机制，与代码已不符，现按
    //   HiveCoreDmaRdOnly 的 lastBTileStartAddr 模型重写。原文另指「HiveCoreDma.scala」，
    //   该文件已在 672fb37 拆分重构中分为 RdOnly/WrOnly/DmaScaleWr 三个模块，B 通道现属
    //   HiveCoreDmaRdOnly.scala。】
    // 硬件 B 路径（HiveCoreDmaRdOnly，本套件 loadWMode=0）：外层共 nTile 轮（终止判定
    // nCnt === calcConfig.nTile-1），每轮内 kTile 块，块内恒 totalN 行【降序】
    // （lineTarget = totalN）。
    // 现行机制 = lastBTileStartAddr 模型（该寄存器恒存【当前块的降序起点】，即块内最高行
    // 地址；因不做末拍多减，故无需旧码的 ×2 降序补偿）：
    //   sIDLE 锁存    curAddr = lastBTileStartAddr = bAddr + (totalN-1)*bRowOff；
    //                 colAddr = bColTileOff（仅 loadWMode=0 分支消费）
    //   sTRANSFER     每 req.fire 拍 curAddr -= bRowOff（降序）
    //   轮内切 k-tile lastBTileStartAddr += totalN*bRowOff
    //                 （旧机制为 curAddr += 2*totalN*bRowOff，靠「末拍已多减一拍」凑出
    //                  等效结果；现机制直接由块起点累加，无补偿项）
    //   轮间切 n-tile lastBTileStartAddr = bAddr + colAddr + (totalN-1)*bRowOff；
    //                 colAddr += bColTileOff
    //                 （旧机制把降序起点项 (totalN-1)*bRowOff 藏在 colAddr 初值里，
    //                  即 colAddr 初值 = bColTileOff + (totalN-1)*bRowOff；现机制把它
    //                  显式写在跳转式里、colAddr 初值收窄为纯 bColTileOff，两者等价）
    // 由此推出的闭式（与下方 for 循环逐项相同，亦与 #133 恢复的 4 条 require 一致）：
    //   blockStart(r, kt) = bAddr + r*bColTileOff + (totalN-1)*bRowOff
    //                       + kt*totalN*bRowOff
    // 块内每拍 -bRowOff（偏移由 HiveCore calcConfig 公式直接推导：loadWMode=0 时
    // bRowOff = regFile.n*(bW/8)【正比于 N，非 M】；bColTileOff = totalN*(bW/8)）。
    // ★ loadWMode=1（MX 转置）走【另一支】跳转（切 k-tile 为 += bColTileOff、切 n-tile 为
    //   += bColTileOff + (totalN-1)*bRowOff），闭式不同；本套件固定 loadWMode=0，勿套用。
    val bKTiles = (K + totalN - 1) / totalN
    val nTilesB = (N + totalN - 1) / totalN
    val bRowOff = N.toLong * (cfg.bW / 8)             // = regFile.n*(bW/8)
    val bColTileOff = totalN.toLong * (cfg.bW / 8)    // = totalN*(bW/8)
    val expBAddrSeq = scala.collection.mutable.ArrayBuffer[Long]()
    val bNRounds = nTilesB                            // 与 HiveCoreDma 终止判定 nCnt === nTile-1 对齐
    for (r <- 0 until bNRounds; kt <- 0 until bKTiles) {
      val blockStart = B_BASE + r.toLong * bColTileOff +
        (totalN - 1).toLong * bRowOff + kt.toLong * totalN * bRowOff
      for (r2 <- 0 until totalN)
        expBAddrSeq += blockStart - r2 * bRowOff
    }
    val dmaBTotalBeats = expBAddrSeq.size
    // 自检：首四块块起点（参数化闭式，N=32/K=32 时 = 0x1003c0/0x1007c0/
    // 0x1003e0/0x1007e0；依赖 bKTiles >= 2 且 nTilesB >= 2）。
    // 【#133 P0-6 恢复并更新到当前模型】这 4 条 require 此前被整块注释掉（长期失效）。
    // 复核结论：它们与当前 HiveCoreDmaRdOnly 的 lastBTileStartAddr 模型【一致】，但
    // 下标写法隐含「bKTiles == 2」假设（用 2*totalN / 3*totalN 直接当 r=1 的块起点，
    // 只在 bKTiles==2 时成立）。现改为按遍历序 (r 外 × kt 中 × 块内 totalN 行降序)
    // 显式换算下标 blkIdx(r,kt)，对任意 bKTiles/nTilesB >= 2 都成立，并补 nTilesB 前置
    // require。本用例 K=32/totalN=16 → bKTiles=2、N=32 → nTilesB=2，四条全部生效。
    def blkIdx(r: Int, kt: Int): Int = (r * bKTiles + kt) * totalN
    require(bKTiles >= 2, s"B 期望序列自检要求 K($K) > totalN($totalN)，当前 bKTiles=$bKTiles")
    require(nTilesB >= 2, s"B 期望序列自检要求 N($N) > totalN($totalN)，当前 nTilesB=$nTilesB")
    require(expBAddrSeq(blkIdx(0, 0)) == B_BASE + (totalN - 1).toLong * bRowOff,
      f"B block(r=0,kt=0) start 0x${expBAddrSeq(blkIdx(0, 0))}%X mismatch")
    require(expBAddrSeq(blkIdx(0, 1)) == B_BASE + (totalN - 1).toLong * bRowOff + totalN.toLong * bRowOff,
      f"B block(r=0,kt=1) start 0x${expBAddrSeq(blkIdx(0, 1))}%X mismatch")
    require(expBAddrSeq(blkIdx(1, 0)) == B_BASE + (totalN - 1).toLong * bRowOff + bColTileOff,
      f"B block(r=1,kt=0) start 0x${expBAddrSeq(blkIdx(1, 0))}%X mismatch")
    require(expBAddrSeq(blkIdx(1, 1)) == B_BASE + (totalN - 1).toLong * bRowOff + bColTileOff +
      totalN.toLong * bRowOff,
      f"B block(r=1,kt=1) start 0x${expBAddrSeq(blkIdx(1, 1))}%X mismatch")
    // beat 数据（与期望地址序列同下标）：nt 固定，块内 K 行降序。
    // 外部 B 布局 = 硬件地址公式蕴含的行主序（行步进 n*(bW/8)、tile 间跳
    // totalN*(bW/8)，恰好拼成 K 行×N 列连续布局）：beat 对应外存行 =
    // kt*totalN + (rows-1-idxInBlock)，列 = nt tile 区间。
    def genDmaBWeightBeat(idx: Int): BigInt = {
      // 每轮（N tile）硬件扫 bKTiles*totalN 拍（块内恒 totalN 行）；
      // nTileIdx 决定供数列区间（col = nTileIdx*totalN + i），不可模掉轮信息
      val beatsPerRound = bKTiles * totalN
      val nTileIdx = idx / beatsPerRound
      var idxInBlock = idx % beatsPerRound
      val rowsLast = K - (bKTiles - 1) * totalN
      var row = K    // 默认越界行（供数 0），对应末 kTile 块超 K 范围的 padding 拍
      var kt = 0
      var resolved = false
      while (kt < bKTiles && !resolved) {
        val rows = if (kt == bKTiles - 1) rowsLast else totalN
        if (idxInBlock < rows) {
          row = kt * totalN + (rows - 1 - idxInBlock)
          resolved = true
        } else {
          idxInBlock -= rows
          kt += 1
        }
      }
      var data = BigInt(0)
      for (i <- 0 until totalN) {
        val col = nTileIdx * totalN + i
        val value = if (row < K && col < N) bMatrix(row)(col) else 0
        data = data | (BigInt(value & 0xFFFF) << (i * cfg.bW))
      }
      data
    }

    // --- DMA-C (C store) 期望地址序列（P3 密集布局，公共助手唯一推演源）：
    // fp32 默认输出 256-bit 总线 → 2 拍/行（拍内步进 32B）；cRowOff=n*4、
    // cColOff=totalN*4、块间跳转 = 张量语义绝对式 nt*cColOff（#133 订正：旧注
    // 「三角累积闭式（金锚）」是对当时缺陷 RTL 的反向拟合，已废止），
    // 遍历序 nt 外 × m 中 × 行内拍内 ---
    val expCAddrSeq = HiveSimCommon.cAddrSeqDense(
      C_BASE, M, N, totalN, outFmt = DataFormat.FP32, cfg.cDmaExtW)

    // --- 仿真 ---
    println(s"[HiveCoreSimCase] Starting simulation: M=$M, N=$N, K=$K, totalN=$totalN")
    println(s"[HiveCoreSimCase] Config: aEffW=$aEffW, cEffW=$cEffW, aExtW=${cfg.aExtW}, bExtW=${cfg.bExtW}, cExtW=${cfg.cExtW}")
    println(s"[HiveCoreSimCase] Strides: A=$aStride, B=$bStride, C=$cStride")

    // 【任务102】mismatchCnt 声明上移至 try 外：断言位于 finally（copyTrace）之后，
    // 需跨 try 作用域引用；声明无副作用，执行顺序与旧代码逐字一致。
    var mismatchCnt = 0

    // 【任务102】失败路径也 dump 波形（#94 已在 MxSpec 验证的 try/finally 范式）：
    // 超时/块内断言（done/地址对账等）抛出异常时，旧代码 copyTrace 位于顺序路径
    // 末段、永不执行 → sim/ 无波形可查。try 包住 simulate 与其后 C 重建
    // （copyTrace 之前的全部顺序代码），copyTrace 移入 finally：无论 pass/fail
    // 均转存，异常自然向上重抛保持 ScalaTest fail 语义；成功路径语句顺序与旧
    // 代码逐字一致（后续 println 列表与 mismatchCnt 断言仍在转存之后）。
    try {
    simulate(new HiveCore(cfg)) { dut =>

      // ===== DMA 通道状态 =====
      // DMA-A (A RdOnly)：逐拍响应 rsp 流（地址寻址，cmd.addr 与期望对账）
      var dmaABeatsSent = 0

      // DMA-C (C WrOnly)：逐 beat 接收 store（len=1 req/grant，地址逐拍对账）
      var dmaCStoreBeats = 0

      // DMA-B (B 权重 RdOnly)：逐拍响应 rsp 流（地址寻址，cmd.addr 与期望对账）
      var dmaBBeatsSent = 0

      // ===== Reset =====
      dut.reset.poke(true.B)
      dut.io.cmd.valid.poke(false.B)
      dut.io.resp.ready.poke(true.B)
      // DMA-A（A 只读通道）：req 常就绪，rsp 初始无效
      dut.io.dmaA.req.ready.poke(true.B)
      dut.io.dmaA.rsp.valid.poke(false.B)
      dut.io.dmaA.rsp.payload.data.poke(0.U)
      dut.io.dmaA.rsp.payload.err.poke(false.B)
      // DMA-C（C 写回通道，req{addr,data}/rsp{err}）：req 常接收，rsp 初始无效
      dut.io.dmaC.req.ready.poke(true.B)
      dut.io.dmaC.rsp.valid.poke(false.B)
      dut.io.dmaC.rsp.payload.err.poke(false.B)
      // DMA-B（B 权重只读通道）：req 常就绪，rsp 初始无效
      dut.io.dmaB.req.ready.poke(true.B)
      dut.io.dmaB.rsp.valid.poke(false.B)
      dut.io.dmaB.rsp.payload.data.poke(0.U)
      dut.io.dmaB.rsp.payload.err.poke(false.B)
      dut.clock.step(3)
      dut.reset.poke(false.B)
      dut.clock.step(2)

      // ===== Helper: 发送命令 =====
      def sendCmd(op: HiveCoreOp.Type, reg0Addr: Int, reg1Addr: Int, data0: Long, data1: Long): Unit = {
        dut.io.cmd.valid.poke(true.B)
        dut.io.cmd.payload.op.poke(op)
        dut.io.cmd.payload.reg0Addr.poke(reg0Addr.U)
        dut.io.cmd.payload.reg1Addr.poke(reg1Addr.U)
        dut.io.cmd.payload.data0.poke(data0.U)
        dut.io.cmd.payload.data1.poke(data1.U)
        dut.clock.step()
        dut.io.cmd.valid.poke(false.B)
        // 等待 resp 被消费
        dut.clock.step(2)
      }

      // ===== 配置寄存器 =====
      // 寄存器映射（与 HiveCoreRegister 一致）：
      //   regs(0)=M, regs(1)=N, regs(2)=K, regs(3)=aAddr, regs(4)=bAddr, regs(5)=cAddr,
      //   regs(6)=[11:8]=cStride, [7:4]=bStride, [3:0]=aStride, regs(7)=fmt/rnd/loopMode
      println("[HiveCoreSimCase] Configuring registers...")
      val stridePacked = (aStride & 0xF) | ((bStride & 0xF) << 4) | ((cStride & 0xF) << 8)
      sendCmd(HiveCoreOp.REG_WRITE2, 0x00, 0x01, M, N)          // REG_M, REG_N
      sendCmd(HiveCoreOp.REG_WRITE2, 0x02, 0x03, K, A_BASE)     // REG_K, A_ADDR
      sendCmd(HiveCoreOp.REG_WRITE2, 0x04, 0x05, B_BASE, C_BASE) // B_ADDR, C_ADDR
      // regs(7) 新 REG_CONTROL 布局：aFmt=bits[3:0]、bFmt=bits[7:4]、cFmt=bits[11:8]、
      // rnd=bits[14:12]（3-bit，#120 C2）。data1=0x11 = aFmt=FP16(1) | bFmt=FP16(1)<<4 | cFmt=FP32(0) | rnd=RNE(0)，
      // 逐字保持旧 data1=0（旧枚举 FP16=0、cFmt 默认 0000=fp32）的「FP16 输入 + fp32 输出」语义。
      sendCmd(HiveCoreOp.REG_WRITE2, 0x06, 0x07, stridePacked, 0x11L) // 三个 stride 打包进 regs(6)

      // ===== 发送 EXECUTE =====
      println("[HiveCoreSimCase] Sending EXECUTE command...")
      dut.io.cmd.valid.poke(true.B)
      dut.io.cmd.payload.op.poke(HiveCoreOp.EXECUTE)
      dut.io.cmd.payload.reg0Addr.poke(0.U)
      dut.io.cmd.payload.reg1Addr.poke(0.U)
      dut.io.cmd.payload.data0.poke(0.U)
      dut.io.cmd.payload.data1.poke(0.U)
      dut.clock.step()
      dut.io.cmd.valid.poke(false.B)

      // ===== 主仿真循环: DMA 响应 + 等待完成 =====
      // 【任务101】EXECUTE 已改为立即返回响应（不代表完成），完成判定改为
      // REG_READ(0x08) 轮询 STATUS（statusPollStep 跨拍状态机，见 HiveSimCommon）
      var done = false
      var cycle = 0
      var pollSt = 0
      var pollCool = 0
      // 诊断留痕：最近一次轮询读到的 STATUS（timeout 时打印，定性 config-err）
      var lastStatus: Option[HiveSimCommon.HiveStatusBits] = None
      val maxCycles = 500000

      println("[HiveCoreSimCase] Entering DMA response loop...")

      while (!done && cycle < maxCycles) {
        // --- DMA-A (A RdOnly) 响应逻辑 ---
        // 地址寻址：req.valid 拍读 req.addr 与软件同公式期望硬对账，
        // 不符直接 fail（防止按序供数掩盖地址公式 bug）；随后按同下标供行数据。
        // 供数/计数由 req.valid 拍驱动（与 DMA-B 同模式：RdOnly rsp.ready 恒真，
        // 若按 rsp.ready 计数会与 req 节奏脱钩）
        if (dmaABeatsSent < dmaATotalBeats) {
          if (dut.io.dmaA.req.valid.peek().litToBoolean) {
            val cmdAddr = dut.io.dmaA.req.payload.addr.peek().litValue.toLong
            withClue(s"DMA-A beat #$dmaABeatsSent addr mismatch: ") {
              cmdAddr should be(expAAddrSeq(dmaABeatsSent))
            }
            dut.io.dmaA.rsp.valid.poke(true.B)
            dut.io.dmaA.rsp.payload.data.poke(genDmaABeat(dmaABeatsSent).U)
            dut.io.dmaA.rsp.payload.err.poke(false.B)
            if (dmaABeatsSent < 4)
              println(f"[cycle=$cycle%6d] DMA-A A beat #$dmaABeatsSent delivered")
            dmaABeatsSent += 1
          } else {
            dut.io.dmaA.rsp.valid.poke(false.B)
            dut.io.dmaA.rsp.payload.data.poke(0.U)
            dut.io.dmaA.rsp.payload.err.poke(false.B)
          }
        } else {
          dut.io.dmaA.rsp.valid.poke(false.B)
          dut.io.dmaA.rsp.payload.data.poke(0.U)
          dut.io.dmaA.rsp.payload.err.poke(false.B)
        }

      
        // --- DMA-C (C WrOnly) 响应逻辑：逐 beat 接收 store ---
        // P3 新架构：cDma 逐 beat 发 req{addr,data}（无 len，行内按 beatsPerRowEff
        // 拆拍、拍内地址步进 cBeatAddrStep），req.ready 常开，req 拍硬对账地址后记录
        dut.io.dmaC.req.ready.poke(true.B)
        dut.io.dmaC.rsp.valid.poke(false.B)
        dut.io.dmaC.rsp.payload.err.poke(false.B)

        if (dut.io.dmaC.req.valid.peek().litToBoolean) {
          val reqAddr = dut.io.dmaC.req.payload.addr.peek().litValue.toLong
          // P3 迁移：临时观察升级为硬对账（期望序列已换密集布局公共助手）
          withClue(s"DMA-C store beat #$dmaCStoreBeats addr mismatch: ") {
            reqAddr should be(expCAddrSeq(dmaCStoreBeats))
          }
          if (dmaCStoreBeats < 4)
            println(f"[cycle=$cycle%6d] DMA-C store req addr=0x$reqAddr%08X")
          val writeData = dut.io.dmaC.req.payload.data.peek().litValue
          // store payload 采样在 step 前；日志地址记硬件实发值（避免期望序列越界）
          cWriteLog += ((reqAddr, writeData))
          dmaCStoreBeats += 1
        }


        if (dut.io.dmaC.req.valid.peek().litToBoolean) {
          dut.io.dmaC.rsp.valid.poke(true.B)
        }
        else{
          dut.io.dmaC.rsp.valid.poke(false.B)
        }


        // --- DMA-B (B 权重 RdOnly) 响应逻辑 ---
        // 地址寻址：req.valid 拍（req.ready 常开，valid 即 fire 拍）读
        // req.addr 与软件同公式期望对账（硬断言），随后按同下标供权重行
        // 数据（块内 K 行降序）。注意：硬件 sNEXT_COL 拍 req.valid 拉低，
        // 而 rsp.ready 在 RdOnly 内恒真，若按 rsp.ready 计数会在每个块间
        // 空拍多计一拍 → beat 索引错位，故供数/计数必须由 req.valid 拍驱动
        if (dmaBBeatsSent < dmaBTotalBeats) {
          if (dut.io.dmaB.req.valid.peek().litToBoolean) {
            val cmdAddr = dut.io.dmaB.req.payload.addr.peek().litValue.toLong
            withClue(s"DMA-B weight beat #$dmaBBeatsSent addr mismatch: ") {
              cmdAddr should be(expBAddrSeq(dmaBBeatsSent))
            }
            dut.io.dmaB.rsp.valid.poke(true.B)
            // 硬件外层共 nTile 轮（与 HiveCoreDma 终止判定 nCnt === nTile-1
            // 对齐），每轮读不同 N tile 列区间，供数下标直接取全序列 beat 号
            // （轮信息由 nTileIdx 体现，不可模掉）
            dut.io.dmaB.rsp.payload.data.poke(genDmaBWeightBeat(dmaBBeatsSent).U)
            dut.io.dmaB.rsp.payload.err.poke(false.B)
            if (dmaBBeatsSent < 4 || dmaBBeatsSent == dmaBTotalBeats - 1)
              println(f"[cycle=$cycle%6d] DMA-B weight beat #$dmaBBeatsSent addr=0x$cmdAddr%X delivered")
            dmaBBeatsSent += 1
          } else {
            // sNEXT_COL 块间空拍：硬件不发 req，不供数不计数
            dut.io.dmaB.rsp.valid.poke(false.B)
            dut.io.dmaB.rsp.payload.data.poke(0.U)
            dut.io.dmaB.rsp.payload.err.poke(false.B)
          }
        } else {
          dut.io.dmaB.rsp.valid.poke(false.B)
          dut.io.dmaB.rsp.payload.data.poke(0.U)
          dut.io.dmaB.rsp.payload.err.poke(false.B)
        }


        // --- 检查完成：STATUS 寄存器轮询（bit1=done 粘滞位、bit2=err）---
        val (stNext, coolNext, statusOpt) = statusPollStep(dut.io.cmd, dut.io.resp, pollSt, pollCool)
        pollSt = stNext
        pollCool = coolNext
        statusOpt.foreach { st =>
          lastStatus = Some(st)
          if (st.done) {
            println(f"[cycle=$cycle%6d] EXECUTE complete (STATUS.done=1, err=${st.err}, progress=${st.progress})! " +
                    s"DMA-A beats=$dmaABeatsSent, C store beats=$dmaCStoreBeats")
            // 完成 STATUS 必须不带 err：防止 FSM 异常退出被下游数值校验兑底掩盖
            st.err should be(false)
            done = true
          }
        }

        // 进度打印
        if (cycle > 0 && cycle % (maxCycles/10) == 0) {
          val progress = dut.io.status.progress.peek().litValue.toInt
          val busy = dut.io.status.busy.peek().litToBoolean
          val aOcc = dut.io.status.aOccupancy.peek().litValue.toInt
          val bOcc = dut.io.status.bOccupancy.peek().litValue.toInt
          val cOcc = dut.io.status.cOccupancy.peek().litValue.toInt
          val err  = dut.io.status.err.peek().litToBoolean
          println(f"[cycle=$cycle%6d] progress=$progress busy=$busy err=$err aOcc=$aOcc bOcc=$bOcc cOcc=$cOcc dmaABeats=$dmaABeatsSent storeBeats=$dmaCStoreBeats dmaBBeats=$dmaBBeatsSent")
        }

        dut.clock.step()
        cycle += 1
      }

      dut.io.dmaC.rsp.valid.poke(false.B)
      dut.io.cmd.valid.poke(false.B)   // 轮询命令用后拉低（防残留影响后续拍）

      if (!done) {
        println(s"[HiveCoreSimCase] WARNING: Simulation timed out at $maxCycles cycles")
        println(s"  DMA-A beats=$dmaABeatsSent/$dmaATotalBeats, store beats=$dmaCStoreBeats")
        val progress = dut.io.status.progress.peek().litValue.toInt
        val busy = dut.io.status.busy.peek().litToBoolean
        println(s"  Status: busy=$busy progress=$progress")
        // 【任务101】诊断：打印轮询链路最后一次读到的 STATUS（轮询路径生效留痕；
        // config-err（如 errCode=5 FP32 输入拦截）下 doneFlag/errFlag 均不置位，
        // 只能靠 errCode/progress 定性硬件未启动根因）
        lastStatus.foreach { st =>
          println(f"  Last polled STATUS(0x08): busy=${st.busy} done=${st.done} err=${st.err} errCode=${st.errCode} progress=${st.progress}")
        }
      }

      done should be(true)
      // 地址寻址完整性：三路 DMA 的 beat 数均与期望序列一致（P3 迁移恢复硬断言）
      dmaABeatsSent should be(dmaATotalBeats)
      dmaBBeatsSent should be(dmaBTotalBeats)
      dmaCStoreBeats should be(expCAddrSeq.size)
    }

    // ===== 写入 C 结果文件 =====
    println(s"[HiveCoreSimCase] Writing C result: ${cWriteLog.size} beats captured")

    val cWriteLogSeq: Seq[(Long, BigInt)] = cWriteLog.toSeq
    // P3 公共解析器：256-bit 密集拍 → (row, col) 元素矩阵（fp32 = 32-bit bits，
    // 2 拍/行、拍内 8 元素低列号在低位），地址对账已在仿真循环内硬断言完成
    val cBits = HiveSimCommon.parseDenseCWriteLog(cWriteLogSeq, outFmt = DataFormat.FP32, M, N, totalN, cfg.cDmaExtW)
    val cResult = cBits.map(_.map(b => BigInt(b & 0xFFFFFFFFL)))

    HiveSimCommon.writeBigIntMatrixHex(s"$simDir/C_matrix.txt", cResult, 32)

    // C 矩阵浮点文件 - FP32 bit 转 float 后输出（与 A/B 侧 writeMatrixFloat 对称）
    val cFloat = cResult.map(_.map(b => HiveSimCommon.fp32BitsToFloat((b & 0xFFFFFFFFL).toLong)))
    HiveSimCommon.writeMatrixFloat(s"$simDir/C_matrix_float.txt", cFloat)
    // 另存一份十进制 bit 视图（原 C_matrix_float.txt 旧格式）
    val cLong = cResult.map(_.map(_.toLong))
    HiveSimCommon.writeMatrixLong(s"$simDir/C_matrix_bits.txt", cLong)

    // ===== 数值校验：C = A × B（FP16 输入，fp32 累加）=====
    // 参考结果用 double 计算；简化浮点硬件（无 denorm/特殊值、RNE 简化为 guard+1）
    // 允许少量误差：相对误差 <= 1e-2 或绝对误差 <= 1e-2

    var maxRelErr = 0.0
    // 多假设比对：定位硬件结果的排列规律
    // H0: C = A·B（标准）
    // H1: B 行反转（权重下沉落位 PE(x)=B[K-1-x]）
    // H2: H1 且结果行反转
    // H3: H1 且结果列反转
    def gemm(aRev: Boolean, bRowRev: Boolean, cRowRev: Boolean, cColRev: Boolean): Array[Array[Double]] = {
      Array.tabulate(M, N) { (i0, j0) =>
        val i = if (cRowRev) M - 1 - i0 else i0
        val j = if (cColRev) N - 1 - j0 else j0
        (0 until K).map { kk =>
          val a = aFloat(if (aRev) M - 1 - i else i)(kk).toDouble
          val b = bFloat(if (bRowRev) K - 1 - kk else kk)(j).toDouble
          a * b
        }.sum
      }
    }
    val hyps = Seq(
      ("H0: A·B", gemm(false, false, false, false)),
      ("H1: B行反转", gemm(false, true, false, false)),
      ("H2: B行反转+C行反转", gemm(false, true, true, false)),
      ("H3: B行反转+C列反转", gemm(false, true, false, true)),
      ("H4: A行反转+B行反转", gemm(true, true, false, false))
    )
    for ((name, refM) <- hyps) {
      var mc = 0; var me = 0.0
      for (i <- 0 until M; j <- 0 until N) {
        val ref = refM(i)(j)
        val hw = HiveSimCommon.fp32BitsToFloat((cResult(i)(j) & 0xFFFFFFFFL).toLong).toDouble
        val absErr = math.abs(hw - ref)
        val relErr = if (math.abs(ref) > 1e-6) absErr / math.abs(ref) else absErr
        if (relErr > 1e-2 && absErr > 1e-2) mc += 1
        if (relErr > me) me = relErr
      }
      println(f"[HiveCoreSimCase] $name%-28s mismatches=$mc%3d maxRelErr=$me%.2e")
    }
    for (i <- 0 until M; j <- 0 until N) {
      val ref = hyps(0)._2(i)(j)
      val hwBits = (cResult(i)(j) & 0xFFFFFFFFL).toLong
      val hw = HiveSimCommon.fp32BitsToFloat(hwBits).toDouble
      val absErr = math.abs(hw - ref)
      val relErr = if (math.abs(ref) > 1e-6) absErr / math.abs(ref) else absErr
      if (relErr > 1e-2 && absErr > 1e-2) {
        if (mismatchCnt < 5) {
          println(f"[HiveCoreSimCase] MISMATCH C($i,$j): hw=$hw%.6f ref=$ref%.6f bits=0x$hwBits%08X")
        }
        mismatchCnt += 1
      }
      if (relErr > maxRelErr) maxRelErr = relErr
    }
    println(f"[HiveCoreSimCase] Numerical check vs H0: ${M * N} elems, mismatches=$mismatchCnt, maxRelErr=$maxRelErr%.2e")
    // 打印 hw/ref 前 4 行 × 4 列便于人工找规律
    for (i <- 0 until 4) {
      val rowStr = (0 until 4).map { j =>
        val hw = HiveSimCommon.fp32BitsToFloat((cResult(i)(j) & 0xFFFFFFFFL).toLong)
        f"$hw%.2f"
      }.mkString(" ")
      val refStr = (0 until 4).map(j => f"${hyps(0)._2(i)(j)}%.2f").mkString(" ")
      println(s"[HiveCoreSimCase] hw row$i : $rowStr")
      println(s"[HiveCoreSimCase] ref row$i: $refStr")
    }
    

    } finally {
      // --- 复制 FST 波形到 sim/ 目录 ---
      // 锚定：源路径段 should-run-* 目录名由上方 it should 用例名 mangle 而来
      // （空格与 `=` 转 `-`，其余字符保留），二者必须逐字一致；
      // 改动用例名时须同步修改此源路径，否则拷贝将静默走 NOTE 分支。
      // FST 须在 simulate 块退出（verilator flush/close trace.fst）后再复制；
      // 【任务102】置于 finally：超时/断言失败异常上抛后同样在此转存（Verilator
      // 已对失败拍之前的周期持续写出 trace.fst，文件非空即可拷出供回放）。
      HiveSimCommon.copyTrace(
        "build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-16-K-16-FP16-totalN-16/workdir-verilator/trace.fst",
        s"$simDir/hivecore_sim.fst",
        "HiveCoreSimCase"
      )
    }

    println(s"[HiveCoreSimCase] Output files written to $simDir/")
    println(s"  - A_matrix.txt (${M}x${K}, 16-bit hex)")
    println(s"  - B_matrix.txt (${K}x${N}, 16-bit hex)")
    println(s"  - C_matrix.txt (${M}x${N}, 32-bit hex)")
    println(s"  - A_matrix_float.txt (${M}x${K}, float %.2f)")
    println(s"  - B_matrix_float.txt (${K}x${N}, float %.2f)")
    println(s"  - C_matrix_float.txt (${M}x${N}, float %.2f)")
    println(s"  - C_matrix_bits.txt (${M}x${N}, decimal long, FP32 原始 bit)")
    println(s"  - hivecore_sim.fst (waveform, run with -DemitFst=1)")

    mismatchCnt should be(0)
  }

  // ==========================================================================
  // P1 缩位写回（cDmaExtW）端到端仿真：行内多拍拆分（新架构 256-bit 密集布局）
  // 【P3 迁移留痕】旧哨兵配置 320/160 违反 P0 isPow2 require（非 2 幂位宽），
  // 用例改按合法 2 幂位宽承载原语义：256（默认，fp32 = E8 每行 2 拍，与原用例名
  // 行为一致）与 128（最深合法拆分，fp32 = E4 每行 4 拍；原 160 哨兵位宽废止）。
  // 断言：逐拍写地址序列（拍内步进 32B、行步进 n*4、tile 跳转 totalN*4；块间跳转为
  // 张量语义绝对式 nt*cColOff，#133 订正旧注「三角累积闭式」）、
  // 拍数 = nTile×M×beatsPerRow、密集打包内容（低列号在低位；fp32 行 512bit 恒被 2 幂总线
  // 整除、无尾拍填充）与软件参考模型（double GEMM + fp32 容差）对拍。
  // ==========================================================================

  it should "run shrunk C writeback E8 2beats per row" in {
    // P3 迁移：原 320 哨兵违反 isPow2 → 改默认 256：fp32 每行 2 拍（E=8，与原用例名行为一致）
    runShrunkGemm(cDmaExtW = 256,
      waveName = "hivecore_shrunk_e8_sim.fst",
      testMangle = "should-run-shrunk-C-writeback-E8-2beats-per-row")
  }

  it should "run shrunk C writeback E4 4beats per row" in {
    // P3 迁移：原 160 哨兵违反 isPow2 废止 → 改最深合法拆分 128：fp32 每行 4 拍（E=4）
    runShrunkGemm(cDmaExtW = 128,
      waveName = "hivecore_shrunk_e4_sim.fst",
      testMangle = "should-run-shrunk-C-writeback-E4-4beats-per-row")
  }

  private def runShrunkGemm(cDmaExtW: Int, waveName: String, testMangle: String): Unit = {

    // --- 配置（与主用例同参数，仅追加 cDmaExtW 缩位）---
    val cfg = HiveCoreConfig(
      arrayN = 8,
      clusterM = 2,         // totalN = 16
      aBufferDepth = 2048,
      cBufferDepth = 2048,
      aW = 16,
      bW = 16,
      cW = 40,
      cDmaExtW = cDmaExtW
    )

    val totalN = cfg.totalN         // 16
    val aEffW  = cfg.aEffW          // 16
    val cEffW  = cfg.cEffW          // 40（C buffer 行宽口径，写回经顶层 cvt 40→32 转换）
    // P3 迁移：拍数/元素数改用公共助手（运行期语义 = fp32 32-bit 元素口径，
    // 与硬件 WrOnly beatsPerRowEff 同源），旧局部复刻哨兵语义已删。
    val elemE  = cfg.cDmaExtW / 32
    val beatsPerRow = HiveSimCommon.cBeatsPerRow(DataFormat.FP32, totalN, cfg.cDmaExtW)
    require(beatsPerRow > 1, s"缩位用例要求 cBeatsPerRow>1，当前=$beatsPerRow")

    // --- 生成矩阵（与主用例同源：FP16，element = (row+1) + col*0.1）---
    val aMatrix = Array.tabulate(M, K)((i, j) => HiveSimCommon.floatToFp16((i + 1.0f) + j * 0.1f))
    val bMatrix = Array.tabulate(K, N)((i, j) => HiveSimCommon.floatToFp16((i + 1.0f) + j * 0.1f))
    val aFloat = aMatrix.map(_.map(h => HiveSimCommon.fp16ToFloat(h)))
    val bFloat = bMatrix.map(_.map(h => HiveSimCommon.fp16ToFloat(h)))

    // --- DMA-A 期望地址序列（与主用例同公式：nTile 外 → kTile 中 → M 内）---
    val aKTiles = (K + totalN - 1) / totalN
    val aNTiles = (N + totalN - 1) / totalN
    val aRowOff = K.toLong * (cfg.aW / 8)
    val aColOff = totalN.toLong * (aEffW / 8)
    val expAAddrSeq = scala.collection.mutable.ArrayBuffer[Long]()
    // A 轮间行为（与主用例/硬件 RdOnly sNEXT_COL 同源）：每 nTile 轮末 curAddr 复位回
    // aAddr（不带轮间列偏移），各轮地址序列完全重复；供数内容同样每轮重复。
    // 【P3 迁移更正】原稿误加 nt*aColOff 轮间偏移致 beat#64 起地址对账失败，
    // 现与主用例同公式对齐（轮信息只体现在供数内容模掉轮号，地址不含轮项）。
    for (nt <- 0 until aNTiles; kt <- 0 until aKTiles; m <- 0 until M)
      expAAddrSeq += A_BASE + m * aRowOff + kt * aColOff
    val dmaATotalBeats = expAAddrSeq.size
    def genDmaABeat(idx: Int): BigInt = {
      val kt = (idx % (aKTiles * M)) / M
      val row = idx % M
      var data = BigInt(0)
      for (i <- 0 until totalN) {
        val col = kt * totalN + i
        val value = if (row < M && col < K) aMatrix(row)(col) else 0
        data = data | (BigInt(value & 0xFFFF) << (i * aEffW))
      }
      data
    }

    // --- DMA-B 期望地址序列（与主用例同公式：轮外 × kTile 块内降序）---
    val bKTiles = (K + totalN - 1) / totalN
    val nTilesB = (N + totalN - 1) / totalN
    val bRowOff = N.toLong * (cfg.bW / 8)
    val bColTileOff = totalN.toLong * (cfg.bW / 8)
    val expBAddrSeq = scala.collection.mutable.ArrayBuffer[Long]()
    for (r <- 0 until nTilesB; kt <- 0 until bKTiles) {
      val blockStart = B_BASE + r.toLong * bColTileOff +
        (totalN - 1).toLong * bRowOff + kt.toLong * totalN * bRowOff
      for (r2 <- 0 until totalN)
        expBAddrSeq += blockStart - r2 * bRowOff
    }
    val dmaBTotalBeats = expBAddrSeq.size
    def genDmaBWeightBeat(idx: Int): BigInt = {
      val beatsPerRound = bKTiles * totalN
      val nTileIdx = idx / beatsPerRound
      var idxInBlock = idx % beatsPerRound
      val rowsLast = K - (bKTiles - 1) * totalN
      var row = K
      var kt = 0
      var resolved = false
      while (kt < bKTiles && !resolved) {
        val rows = if (kt == bKTiles - 1) rowsLast else totalN
        if (idxInBlock < rows) {
          row = kt * totalN + (rows - 1 - idxInBlock)
          resolved = true
        } else {
          idxInBlock -= rows
          kt += 1
        }
      }
      var data = BigInt(0)
      for (i <- 0 until totalN) {
        val col = nTileIdx * totalN + i
        val value = if (row < K && col < N) bMatrix(row)(col) else 0
        data = data | (BigInt(value & 0xFFFF) << (i * cfg.bW))
      }
      data
    }

    // --- DMA-C 期望地址序列（P3 密集布局，公共助手唯一推演源）：
    // fp32 32-bit 元素口径：行步进 = n*4、tile 步进 = totalN*4、拍内步进 = 32B，
    // 块间跳转 = 张量语义绝对式 nt*cColOff（#133 订正：旧注「三角累积闭式（金锚）」
    // 是对当时缺陷 RTL 的反向拟合，已废止），遍历序 nt 外 × m 中 × 行内拍内 ---
    val expCAddrSeq = HiveSimCommon.cAddrSeqDense(
      C_BASE, M, N, totalN, outFmt = DataFormat.FP32, cfg.cDmaExtW)
    val cWriteLog = scala.collection.mutable.ArrayBuffer[(Long, BigInt)]()

    println(s"[Shrunk-$elemE] Starting: M=$M N=$N K=$K totalN=$totalN cDmaExtW=$cDmaExtW E=$elemE beatsPerRow=$beatsPerRow")
    println(s"[Shrunk-$elemE] beats: dmaA=$dmaATotalBeats dmaB=$dmaBTotalBeats C=${expCAddrSeq.size} (legacy 一拍一行时 C=${aNTiles * M})")

    // --- 仿真 ---
    // 【任务102】失败路径也 dump 波形（#94 范式）：try 包 simulate，copyTrace 移入
    // finally——超时/断言失败异常上抛时同样转存，成功路径转存时机（块退出后）
    // 与旧代码完全一致。
    try {
    simulate(new HiveCore(cfg)) { dut =>

      var dmaABeatsSent = 0
      var dmaCStoreBeats = 0
      var dmaBBeatsSent = 0

      // ===== Reset =====
      dut.reset.poke(true.B)
      dut.io.cmd.valid.poke(false.B)
      dut.io.resp.ready.poke(true.B)
      dut.io.dmaA.req.ready.poke(true.B)
      dut.io.dmaA.rsp.valid.poke(false.B)
      dut.io.dmaA.rsp.payload.data.poke(0.U)
      dut.io.dmaA.rsp.payload.err.poke(false.B)
      dut.io.dmaC.req.ready.poke(true.B)
      dut.io.dmaC.rsp.valid.poke(false.B)
      dut.io.dmaC.rsp.payload.err.poke(false.B)
      dut.io.dmaB.req.ready.poke(true.B)
      dut.io.dmaB.rsp.valid.poke(false.B)
      dut.io.dmaB.rsp.payload.data.poke(0.U)
      dut.io.dmaB.rsp.payload.err.poke(false.B)
      dut.clock.step(3)
      dut.reset.poke(false.B)
      dut.clock.step(2)

      def sendCmd(op: HiveCoreOp.Type, reg0Addr: Int, reg1Addr: Int, data0: Long, data1: Long): Unit = {
        dut.io.cmd.valid.poke(true.B)
        dut.io.cmd.payload.op.poke(op)
        dut.io.cmd.payload.reg0Addr.poke(reg0Addr.U)
        dut.io.cmd.payload.reg1Addr.poke(reg1Addr.U)
        dut.io.cmd.payload.data0.poke(data0.U)
        dut.io.cmd.payload.data1.poke(data1.U)
        dut.clock.step()
        dut.io.cmd.valid.poke(false.B)
        dut.clock.step(2)
      }

      // ===== 配置寄存器（FP16 输入 + fp32 输出 + RNE；缩位是编译期配置，无运行期寄存器）=====
      // regs(7)=0x11：aFmt=FP16(1)@bits[3:0] | bFmt=FP16(1)@bits[7:4] | cFmt=FP32(0)@bits[11:8] | rnd=RNE(0)。
      sendCmd(HiveCoreOp.REG_WRITE2, 0x00, 0x01, M, N)
      sendCmd(HiveCoreOp.REG_WRITE2, 0x02, 0x03, K, A_BASE)
      sendCmd(HiveCoreOp.REG_WRITE2, 0x04, 0x05, B_BASE, C_BASE)
      sendCmd(HiveCoreOp.REG_WRITE2, 0x06, 0x07, 0L, 0x11L)

      // ===== EXECUTE =====
      dut.io.cmd.valid.poke(true.B)
      dut.io.cmd.payload.op.poke(HiveCoreOp.EXECUTE)
      dut.io.cmd.payload.reg0Addr.poke(0.U)
      dut.io.cmd.payload.reg1Addr.poke(0.U)
      dut.io.cmd.payload.data0.poke(0.U)
      dut.io.cmd.payload.data1.poke(0.U)
      dut.clock.step()
      dut.io.cmd.valid.poke(false.B)

      // 【任务101】EXECUTE 立即响应语义：完成判定 = STATUS 轮询（statusPollStep）
      var done = false
      var cycle = 0
      var pollSt = 0
      var pollCool = 0
      val maxCycles = 8000

      while (!done && cycle < maxCycles) {

        // --- DMA-A（同主用例：req.valid 拍地址硬对账 + 按同下标供数；
        // 供数/计数由 req.valid 驱动，避免 rsp.ready 恒真致空拍误计）---
        if (dmaABeatsSent < dmaATotalBeats) {
          if (dut.io.dmaA.req.valid.peek().litToBoolean) {
            val cmdAddr = dut.io.dmaA.req.payload.addr.peek().litValue.toLong
            withClue(s"DMA-A beat #$dmaABeatsSent addr mismatch: ") {
              cmdAddr should be(expAAddrSeq(dmaABeatsSent))
            }
            dut.io.dmaA.rsp.valid.poke(true.B)
            dut.io.dmaA.rsp.payload.data.poke(genDmaABeat(dmaABeatsSent).U)
            dut.io.dmaA.rsp.payload.err.poke(false.B)
            dmaABeatsSent += 1
          } else {
            dut.io.dmaA.rsp.valid.poke(false.B)
            dut.io.dmaA.rsp.payload.data.poke(0.U)
            dut.io.dmaA.rsp.payload.err.poke(false.B)
          }
        } else {
          dut.io.dmaA.rsp.valid.poke(false.B)
          dut.io.dmaA.rsp.payload.data.poke(0.U)
          dut.io.dmaA.rsp.payload.err.poke(false.B)
        }

        // --- DMA-C：逐拍写地址硬对账 + 密集打包内容记录 ---
        dut.io.dmaC.req.ready.poke(true.B)
        if (dut.io.dmaC.req.valid.peek().litToBoolean) {
          val reqAddr = dut.io.dmaC.req.payload.addr.peek().litValue.toLong
          withClue(s"DMA-C shrunk store beat #$dmaCStoreBeats addr mismatch: ") {
            reqAddr should be(expCAddrSeq(dmaCStoreBeats))
          }
          val writeData = dut.io.dmaC.req.payload.data.peek().litValue
          // 打包契约：E×32-bit 密集段放总线低位；fp32 行宽 512bit 被 2 幂总线整除，
          // 每拍恰好满占用（无尾拍填充），高位越界部分天然为零（保留断言防回归）
          withClue(s"DMA-C shrunk store beat #$dmaCStoreBeats upper bits not zero-padded: ") {
            (writeData >> (elemE * 32)) should be(BigInt(0))
          }
          cWriteLog += ((reqAddr, writeData))
          dmaCStoreBeats += 1
          dut.io.dmaC.rsp.valid.poke(true.B)
        } else {
          dut.io.dmaC.rsp.valid.poke(false.B)
        }

        // --- DMA-B（同主用例：req.valid 拍地址硬对账 + 按同下标供权重行）---
        if (dmaBBeatsSent < dmaBTotalBeats) {
          if (dut.io.dmaB.req.valid.peek().litToBoolean) {
            val cmdAddr = dut.io.dmaB.req.payload.addr.peek().litValue.toLong
            withClue(s"DMA-B weight beat #$dmaBBeatsSent addr mismatch: ") {
              cmdAddr should be(expBAddrSeq(dmaBBeatsSent))
            }
            dut.io.dmaB.rsp.valid.poke(true.B)
            dut.io.dmaB.rsp.payload.data.poke(genDmaBWeightBeat(dmaBBeatsSent).U)
            dut.io.dmaB.rsp.payload.err.poke(false.B)
            dmaBBeatsSent += 1
          } else {
            dut.io.dmaB.rsp.valid.poke(false.B)
            dut.io.dmaB.rsp.payload.data.poke(0.U)
            dut.io.dmaB.rsp.payload.err.poke(false.B)
          }
        } else {
          dut.io.dmaB.rsp.valid.poke(false.B)
          dut.io.dmaB.rsp.payload.data.poke(0.U)
          dut.io.dmaB.rsp.payload.err.poke(false.B)
        }

        // --- 完成检查：STATUS 寄存器轮询（【任务101】EXECUTE 立即响应语义）---
        val (stNext, coolNext, statusOpt) = statusPollStep(dut.io.cmd, dut.io.resp, pollSt, pollCool)
        pollSt = stNext
        pollCool = coolNext
        statusOpt.foreach { st =>
          if (st.done) {
            println(f"[Shrunk-$elemE cycle=$cycle%5d] EXECUTE complete (STATUS.done=1, err=${st.err})! A=$dmaABeatsSent B=$dmaBBeatsSent C=$dmaCStoreBeats")
            st.err should be(false)
            done = true
          }
        }

        dut.clock.step()
        cycle += 1
      }

      dut.io.dmaC.rsp.valid.poke(false.B)
      dut.io.cmd.valid.poke(false.B)   // 轮询命令用后拉低（防残留影响后续拍）

      if (!done) println(s"[Shrunk-$elemE] WARNING: timeout at $maxCycles cycles (A=$dmaABeatsSent B=$dmaBBeatsSent C=$dmaCStoreBeats)")

      done should be(true)
      dmaABeatsSent should be(dmaATotalBeats)
      dmaBBeatsSent should be(dmaBTotalBeats)
      // 拍数断言：缩位后每行 totalN/E 拍，总拍数 = nTile × M × beatsPerRow
      dmaCStoreBeats should be(expCAddrSeq.size)
      dmaCStoreBeats should be(aNTiles * M * beatsPerRow)

      // ===== C 重建：P3 公共解析器（遍历序/拍内列位语义与硬件同源）=====
      val cBits = HiveSimCommon.parseDenseCWriteLog(cWriteLog.toSeq, outFmt = DataFormat.FP32, M, N, totalN, cfg.cDmaExtW)
      val cResult = cBits.map(_.map(b => BigInt(b & 0xFFFFFFFFL)))

      // ===== 数值校验：C = A×B（FP16 输入，fp32 累加，经 40→32 规格化写出）=====
      // 参考结果用 double 计算，容差与主用例一致（相对 ≤1e-2 或绝对 ≤1e-2）
      var mismatchCnt = 0
      var maxRelErr = 0.0
      for (i <- 0 until M; j <- 0 until N) {
        val ref = (0 until K).map(kk => aFloat(i)(kk).toDouble * bFloat(kk)(j).toDouble).sum
        val hwBits = (cResult(i)(j) & 0xFFFFFFFFL).toLong
        val hw = HiveSimCommon.fp32BitsToFloat(hwBits).toDouble
        val absErr = math.abs(hw - ref)
        val relErr = if (math.abs(ref) > 1e-6) absErr / math.abs(ref) else absErr
        if (relErr > 1e-2 && absErr > 1e-2) {
          if (mismatchCnt < 5)
            println(f"[Shrunk-$elemE] MISMATCH C($i,$j): hw=$hw%.6f ref=$ref%.6f bits=0x$hwBits%08X")
          mismatchCnt += 1
        }
        if (relErr > maxRelErr) maxRelErr = relErr
      }
      println(f"[Shrunk-$elemE] Numerical check: ${M * N} elems, mismatches=$mismatchCnt, maxRelErr=$maxRelErr%.2e")

      mismatchCnt should be(0)
    }
    } finally {
      // --- 复制 FST 波形（源路径段由 testMangle 逐字锚定 it should 用例名）---
      // FST 须在 simulate 块退出（verilator flush/close trace.fst）后再复制；
      // 【任务102】置于 finally：超时/断言失败异常上抛后同样在此转存（Verilator
      // 已对失败拍之前的周期持续写出 trace.fst，文件非空即可拷出供回放）。
      HiveSimCommon.copyTrace(
        s"build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/$testMangle/workdir-verilator/trace.fst",
        s"$simDir/$waveName",
        s"Shrunk-$elemE"
      )
    }
  }

  // ==========================================================================
  // P2 精度转换（outFmt）端到端仿真：regs(7) 运行期选择写回格式（新 REG_CONTROL 布局：
  // outFmt = regs(7)(10,8) 3-bit，1=fp16/2=bf16）。
  // 断言：逐拍写地址序列（行步进 n*outElemBytes、tile 跳转 totalN*outElemBytes、
  // 拍内步进 = (cDmaExtW/16)×2B）、拍数 = ceil(totalN×16/cDmaExtW)、
  // 16-bit 密集打包内容与高位零填充；crafted 矩阵构造严格锚点：
  //   fp16：C[0][0]饱和+65504(0x7BFF)、C[1][0]饱和-65504(0xFBFF)、
  //         j=0 且 i>=4 均饱和（512×行和 > 65504）、row2 全零（A row2=0）、
  //         C[3][1]=32×0.0005×0.002=3.2e-5 < 2^-14 → FTZ 零；
  //   其余元素解码后与 double GEMM 容差对比（相对 ≤1e-2 或绝对 ≤1e-2）。
  // 【P3 迁移留痕】旧哨兵 0（满宽）/320 违反 P0 isPow2 require：满宽语义由默认 256 承载（16-bit ×
  // totalN=16 恰好 1 拍/行满占用）；原 320 的 E8 深拆分语义由 128（最深合法 2 幂、
  // 16-bit 下 E=8 每行 2 拍）承载。
  // outFmt 默认 0000 等价性：既有主用例与缩位用例 regs(7)=0（不写 outFmt 位）
  // 全绿，已证明默认行为与重构前逐字一致，不再单独设用例。
  // ==========================================================================

  it should "run fp16 out GEMM RNE full width" in {
    // P3 迁移：原 0 哨兵（满宽）废止 → 默认 256（16-bit 1 拍/行满占用）
    runOutFmtGemm(outFmt = DataFormat.FP16, rnd = 0, cDmaExtW = 256,
      waveName = "hivecore_outfmt_fp16_rne_sim.fst",
      testMangle = "should-run-fp16-out-GEMM-RNE-full-width")
  }

  it should "run fp16 out GEMM RTZ full width" in {
    runOutFmtGemm(outFmt = DataFormat.FP16, rnd = 1, cDmaExtW = 256,
      waveName = "hivecore_outfmt_fp16_rtz_sim.fst",
      testMangle = "should-run-fp16-out-GEMM-RTZ-full-width")
  }

  it should "run bf16 out GEMM RNE full width" in {
    runOutFmtGemm(outFmt = DataFormat.BF16, rnd = 0, cDmaExtW = 256,
      waveName = "hivecore_outfmt_bf16_rne_sim.fst",
      testMangle = "should-run-bf16-out-GEMM-RNE-full-width")
  }

  it should "run fp16 out GEMM RNE shrunk E8" in {
    // P3 迁移：原 320 哨兵违反 isPow2 → 改 128（最深合法 2 幂）：16-bit 输出下
    // E=8、每行 2 拍（与原用例名行为一致），拍内步进 16B、行步进 N×2B、
    // 高位零填充断言保留（满占用时天然为零，防回归）
    runOutFmtGemm(outFmt = DataFormat.FP16, rnd = 0, cDmaExtW = 128,
      waveName = "hivecore_outfmt_fp16_rne_shrunk_e8_sim.fst",
      testMangle = "should-run-fp16-out-GEMM-RNE-shrunk-E8")
  }

  it should "run fp16 out GEMM RUP full width" in {
    // 方向性舍入端到端（寄存器→转换模块完整链路）：fp16 + rnd=RUP，
    // 正数被截断位非零时向 +∞ 增大；期望由独立软件参考模型对拍（容差 1e-2）
    runOutFmtGemm(outFmt = DataFormat.FP16, rnd = 3, cDmaExtW = 256,
      waveName = "hivecore_outfmt_fp16_rup_sim.fst",
      testMangle = "should-run-fp16-out-GEMM-RUP-full-width")
  }

  it should "run bf16 out GEMM RNE shrunk E8" in {
    // P3 迁移：原 320 哨兵违反 isPow2 → 改 128（16-bit 打包 × 深拆分组合：
    // E=8、每行 2 拍），覆盖 packed16 选路 + 行内拍段切割（段低 E×16 位有效）与 2B 密集步进；
    // 期望由独立软件参考模型对拍（容差 1e-2）
    runOutFmtGemm(outFmt = DataFormat.BF16, rnd = 0, cDmaExtW = 128,
      waveName = "hivecore_outfmt_bf16_rne_shrunk_e8_sim.fst",
      testMangle = "should-run-bf16-out-GEMM-RNE-shrunk-E8")
  }

  private def runOutFmtGemm(outFmt: DataFormat.Type, rnd: Int, cDmaExtW: Int,
                            waveName: String, testMangle: String): Unit = {
    require(outFmt == DataFormat.FP16 || outFmt == DataFormat.BF16,
      s"本用例族仅支持 16-bit 输出（FP16/BF16），当前=$outFmt")
    val tag = s"OutFmt-${outFmt.litValue}-rnd$rnd-E$cDmaExtW"

    // --- 配置（与主用例同参数；cDmaExtW ∈ {256, 128} 合法 2 幂位宽）---
    val cfg = HiveCoreConfig(
      arrayN = 8,
      clusterM = 2,         // totalN = 16
      aBufferDepth = 2048,
      cBufferDepth = 2048,
      aW = 16,
      bW = 16,
      cW = 40,
      cDmaExtW = cDmaExtW
    )

    val totalN = cfg.totalN
    val aEffW  = cfg.aEffW
    // P3 迁移：拍数/元素数改用公共助手（16-bit 元素口径，与硬件 WrOnly 同源），
    // 旧局部复刻哨兵语义已删。256 → 1 拍/行满占用；128 → E=8 每行 2 拍。
    val elemE  = cfg.cDmaExtW / 16
    val beatsPerRow = HiveSimCommon.cBeatsPerRow(outFmt, totalN, cfg.cDmaExtW)
    val outElemBytes = 2L                 // fp16/bf16 均 2B/元素（与 HiveCore.cOutElemBytes 同源语义）
    val outElemBits  = 16

    // --- crafted 矩阵（锚点构造：饱和/负饱和/零行/FTZ，其余常规值）---
    def aVal(i: Int, j: Int): Float = i match {
      case 0 => 256.0f                    // × col0=512 × K=32 → 4.19e6 溢出饱和 +65504
      case 1 => -256.0f                   // → -4.19e6 饱和 -65504（保号）
      case 2 => 0.0f                      // 零行 → 整行输出 0（保号零）
      case 3 => 0.0005f                   // col1: 3.2e-5 < 2^-14 → FTZ；col0: 8.192 正常值
      case _ => (i + 1.0f) + j * 0.1f
    }
    def bVal(i: Int, j: Int): Float = j match {
      case 0 => 512.0f                    // 与 i==0/1（±256）行及 i>=4 行和相乘溢出 → fp16 ±65504 锚点列
      case 1 => 0.002f
      case _ => (i + 1.0f) + j * 0.1f
    }
    val aMatrix = Array.tabulate(M, K)((i, j) => HiveSimCommon.floatToFp16(aVal(i, j)))
    val bMatrix = Array.tabulate(K, N)((i, j) => HiveSimCommon.floatToFp16(bVal(i, j)))
    val aFloat = aMatrix.map(_.map(h => HiveSimCommon.fp16ToFloat(h)))
    val bFloat = bMatrix.map(_.map(h => HiveSimCommon.fp16ToFloat(h)))

    // --- DMA-A / DMA-B 期望地址与供数（与主/缩位用例同公式同结构）---
    val aKTiles = (K + totalN - 1) / totalN
    val aNTiles = (N + totalN - 1) / totalN
    val aRowOff = K.toLong * (cfg.aW / 8)
    val aColOff = totalN.toLong * (aEffW / 8)
    val expAAddrSeq = scala.collection.mutable.ArrayBuffer[Long]()
    // A 轮间行为（与主用例/硬件 RdOnly sNEXT_COL 同源）：每 nTile 轮末 curAddr 复位回
    // aAddr（不带轮间列偏移），各轮地址序列完全重复；供数内容同样每轮重复。
    // 【P3 迁移更正】原稿误加 nt*aColOff 轮间偏移致 beat#64 起地址对账失败，
    // 现与主用例同公式对齐（轮信息只体现在供数内容模掉轮号，地址不含轮项）。
    for (nt <- 0 until aNTiles; kt <- 0 until aKTiles; m <- 0 until M)
      expAAddrSeq += A_BASE + m * aRowOff + kt * aColOff
    val dmaATotalBeats = expAAddrSeq.size
    def genDmaABeat(idx: Int): BigInt = {
      val kt = (idx % (aKTiles * M)) / M
      val row = idx % M
      var data = BigInt(0)
      for (i <- 0 until totalN) {
        val col = kt * totalN + i
        val value = if (row < M && col < K) aMatrix(row)(col) else 0
        data = data | (BigInt(value & 0xFFFF) << (i * aEffW))
      }
      data
    }
    val bKTiles = (K + totalN - 1) / totalN
    val nTilesB = (N + totalN - 1) / totalN
    val bRowOff = N.toLong * (cfg.bW / 8)
    val bColTileOff = totalN.toLong * (cfg.bW / 8)
    val expBAddrSeq = scala.collection.mutable.ArrayBuffer[Long]()
    for (r <- 0 until nTilesB; kt <- 0 until bKTiles) {
      val blockStart = B_BASE + r.toLong * bColTileOff +
        (totalN - 1).toLong * bRowOff + kt.toLong * totalN * bRowOff
      for (r2 <- 0 until totalN)
        expBAddrSeq += blockStart - r2 * bRowOff
    }
    val dmaBTotalBeats = expBAddrSeq.size
    def genDmaBWeightBeat(idx: Int): BigInt = {
      val beatsPerRound = bKTiles * totalN
      val nTileIdx = idx / beatsPerRound
      var idxInBlock = idx % beatsPerRound
      val rowsLast = K - (bKTiles - 1) * totalN
      var row = K
      var kt = 0
      var resolved = false
      while (kt < bKTiles && !resolved) {
        val rows = if (kt == bKTiles - 1) rowsLast else totalN
        if (idxInBlock < rows) {
          row = kt * totalN + (rows - 1 - idxInBlock)
          resolved = true
        } else {
          idxInBlock -= rows
          kt += 1
        }
      }
      var data = BigInt(0)
      for (i <- 0 until totalN) {
        val col = nTileIdx * totalN + i
        val value = if (row < K && col < N) bMatrix(row)(col) else 0
        data = data | (BigInt(value & 0xFFFF) << (i * cfg.bW))
      }
      data
    }

    // --- DMA-C 期望地址序列（P3 密集布局，公共助手唯一推演源）：
    // 16-bit 元素口径：行步进 = n×2B、tile 步进 = totalN×2B、拍内步进 = (cDmaExtW/16)×2B，
    // 块间跳转 = 张量语义绝对式 nt*cColOff（#133 订正：旧注「三角累积闭式（金锚）」
    // 是对当时缺陷 RTL 的反向拟合，已废止），遍历序 nt 外 × m 中 × 行内拍内 ---
    val expCAddrSeq = HiveSimCommon.cAddrSeqDense(
      C_BASE, M, N, totalN, outFmt, cfg.cDmaExtW)
    val cWriteLog = scala.collection.mutable.ArrayBuffer[(Long, BigInt)]()

    println(s"[$tag] Starting: M=$M N=$N K=$K totalN=$totalN cDmaExtW=$cDmaExtW E=$elemE beatsPerRow=$beatsPerRow outElemBytes=$outElemBytes")

    // 【任务102】失败路径也 dump 波形（#94 范式）：try 包 simulate，copyTrace 移入
    // finally——超时/断言失败异常上抛时同样转存，成功路径转存时机（块退出后）
    // 与旧代码完全一致。
    try {
    simulate(new HiveCore(cfg)) { dut =>

      var dmaABeatsSent = 0
      var dmaCStoreBeats = 0
      var dmaBBeatsSent = 0

      // ===== Reset =====
      dut.reset.poke(true.B)
      dut.io.cmd.valid.poke(false.B)
      dut.io.resp.ready.poke(true.B)
      dut.io.dmaA.req.ready.poke(true.B)
      dut.io.dmaA.rsp.valid.poke(false.B)
      dut.io.dmaA.rsp.payload.data.poke(0.U)
      dut.io.dmaA.rsp.payload.err.poke(false.B)
      dut.io.dmaC.req.ready.poke(true.B)
      dut.io.dmaC.rsp.valid.poke(false.B)
      dut.io.dmaC.rsp.payload.err.poke(false.B)
      dut.io.dmaB.req.ready.poke(true.B)
      dut.io.dmaB.rsp.valid.poke(false.B)
      dut.io.dmaB.rsp.payload.data.poke(0.U)
      dut.io.dmaB.rsp.payload.err.poke(false.B)
      dut.clock.step(3)
      dut.reset.poke(false.B)
      dut.clock.step(2)

      def sendCmd(op: HiveCoreOp.Type, reg0Addr: Int, reg1Addr: Int, data0: Long, data1: Long): Unit = {
        dut.io.cmd.valid.poke(true.B)
        dut.io.cmd.payload.op.poke(op)
        dut.io.cmd.payload.reg0Addr.poke(reg0Addr.U)
        dut.io.cmd.payload.reg1Addr.poke(reg1Addr.U)
        dut.io.cmd.payload.data0.poke(data0.U)
        dut.io.cmd.payload.data1.poke(data1.U)
        dut.clock.step()
        dut.io.cmd.valid.poke(false.B)
        dut.clock.step(2)
      }

      // ===== 配置寄存器（新 REG_CONTROL 布局）=====
      // regs(7)：aFmt=bits[3:0]、bFmt=bits[7:4]、cFmt/outFmt=bits[11:8]（4-bit）、
      // rnd=bits[14:12]（3-bit，#120 C2 扩位后支持全 5 种舍入 RNE/RTZ/RDN/RUP/RMM）。
      // 本族固定 FP16 输入（新枚举 FP16=1）：aFmt=bFmt=1；outFmt∈{FP16=1,BF16=2} 落 cFmt。
      val controlWord = (1L) |                       // aFmt = FP16(1)
        (1L << 4) |                                  // bFmt = FP16(1)
        ((outFmt.litValue.toLong & 0xF) << 8) |      // cFmt = outFmt（4-bit）
        ((rnd.toLong & 0x7) << 12)                   // rnd（3-bit，#120 C2 扩位后支持 RMM=4）
      sendCmd(HiveCoreOp.REG_WRITE2, 0x00, 0x01, M, N)
      sendCmd(HiveCoreOp.REG_WRITE2, 0x02, 0x03, K, A_BASE)
      sendCmd(HiveCoreOp.REG_WRITE2, 0x04, 0x05, B_BASE, C_BASE)
      sendCmd(HiveCoreOp.REG_WRITE2, 0x06, 0x07, 0L, controlWord)

      // ===== EXECUTE =====
      dut.io.cmd.valid.poke(true.B)
      dut.io.cmd.payload.op.poke(HiveCoreOp.EXECUTE)
      dut.io.cmd.payload.reg0Addr.poke(0.U)
      dut.io.cmd.payload.reg1Addr.poke(0.U)
      dut.io.cmd.payload.data0.poke(0.U)
      dut.io.cmd.payload.data1.poke(0.U)
      dut.clock.step()
      dut.io.cmd.valid.poke(false.B)

      // 【任务101】EXECUTE 立即响应语义：完成判定 = STATUS 轮询（statusPollStep）
      var done = false
      var cycle = 0
      var pollSt = 0
      var pollCool = 0
      val maxCycles = 8000

      while (!done && cycle < maxCycles) {

        // --- DMA-A（req.valid 拍地址硬对账 + 按同下标供数；
        // 供数/计数由 req.valid 驱动，避免 rsp.ready 恒真致空拍误计）---
        if (dmaABeatsSent < dmaATotalBeats) {
          if (dut.io.dmaA.req.valid.peek().litToBoolean) {
            val cmdAddr = dut.io.dmaA.req.payload.addr.peek().litValue.toLong
            withClue(s"[$tag] DMA-A beat #$dmaABeatsSent addr mismatch: ") {
              cmdAddr should be(expAAddrSeq(dmaABeatsSent))
            }
            dut.io.dmaA.rsp.valid.poke(true.B)
            dut.io.dmaA.rsp.payload.data.poke(genDmaABeat(dmaABeatsSent).U)
            dut.io.dmaA.rsp.payload.err.poke(false.B)
            dmaABeatsSent += 1
          } else {
            dut.io.dmaA.rsp.valid.poke(false.B)
            dut.io.dmaA.rsp.payload.data.poke(0.U)
            dut.io.dmaA.rsp.payload.err.poke(false.B)
          }
        } else {
          dut.io.dmaA.rsp.valid.poke(false.B)
          dut.io.dmaA.rsp.payload.data.poke(0.U)
          dut.io.dmaA.rsp.payload.err.poke(false.B)
        }

        // --- DMA-C：逐拍地址硬对账 + 16-bit 密集打包内容记录 + 高位零填充断言 ---
        dut.io.dmaC.req.ready.poke(true.B)
        if (dut.io.dmaC.req.valid.peek().litToBoolean) {
          val reqAddr = dut.io.dmaC.req.payload.addr.peek().litValue.toLong
          withClue(s"[$tag] DMA-C store beat #$dmaCStoreBeats addr mismatch: ") {
            reqAddr should be(expCAddrSeq(dmaCStoreBeats))
          }
          val writeData = dut.io.dmaC.req.payload.data.peek().litValue
          // 打包契约：E×16-bit 密集段放总线低位；16-bit×totalN=256bit 被 2 幂总线整除，
          // 每拍恰好满占用（无尾拍填充），高位越界部分天然为零（保留断言防回归）
          withClue(s"[$tag] DMA-C store beat #$dmaCStoreBeats upper bits not zero-padded: ") {
            (writeData >> (elemE * outElemBits)) should be(BigInt(0))
          }
          cWriteLog += ((reqAddr, writeData))
          dmaCStoreBeats += 1
          dut.io.dmaC.rsp.valid.poke(true.B)
        } else {
          dut.io.dmaC.rsp.valid.poke(false.B)
        }

        // --- DMA-B（req.valid 拍地址硬对账 + 按同下标供权重行）---
        if (dmaBBeatsSent < dmaBTotalBeats) {
          if (dut.io.dmaB.req.valid.peek().litToBoolean) {
            val cmdAddr = dut.io.dmaB.req.payload.addr.peek().litValue.toLong
            withClue(s"[$tag] DMA-B weight beat #$dmaBBeatsSent addr mismatch: ") {
              cmdAddr should be(expBAddrSeq(dmaBBeatsSent))
            }
            dut.io.dmaB.rsp.valid.poke(true.B)
            dut.io.dmaB.rsp.payload.data.poke(genDmaBWeightBeat(dmaBBeatsSent).U)
            dut.io.dmaB.rsp.payload.err.poke(false.B)
            dmaBBeatsSent += 1
          } else {
            dut.io.dmaB.rsp.valid.poke(false.B)
            dut.io.dmaB.rsp.payload.data.poke(0.U)
            dut.io.dmaB.rsp.payload.err.poke(false.B)
          }
        } else {
          dut.io.dmaB.rsp.valid.poke(false.B)
          dut.io.dmaB.rsp.payload.data.poke(0.U)
          dut.io.dmaB.rsp.payload.err.poke(false.B)
        }

        // --- 完成检查：STATUS 寄存器轮询（【任务101】EXECUTE 立即响应语义）---
        val (stNext, coolNext, statusOpt) = statusPollStep(dut.io.cmd, dut.io.resp, pollSt, pollCool)
        pollSt = stNext
        pollCool = coolNext
        statusOpt.foreach { st =>
          if (st.done) {
            println(f"[$tag cycle=$cycle%5d] EXECUTE complete (STATUS.done=1, err=${st.err})! A=$dmaABeatsSent B=$dmaBBeatsSent C=$dmaCStoreBeats")
            st.err should be(false)
            done = true
          }
        }

        dut.clock.step()
        cycle += 1
      }

      dut.io.dmaC.rsp.valid.poke(false.B)
      dut.io.cmd.valid.poke(false.B)   // 轮询命令用后拉低（防残留影响后续拍）

      if (!done) println(s"[$tag] WARNING: timeout at $maxCycles cycles (A=$dmaABeatsSent B=$dmaBBeatsSent C=$dmaCStoreBeats)")

      done should be(true)
      dmaABeatsSent should be(dmaATotalBeats)
      dmaBBeatsSent should be(dmaBTotalBeats)
      // 拍数断言：恒 nTile × M × beatsPerRow（拍数由格式与总线位宽运行期解码）
      dmaCStoreBeats should be(expCAddrSeq.size)
      dmaCStoreBeats should be(aNTiles * M * beatsPerRow)

      // ===== C 重建：P3 公共解析器（遍历序/拍内列位语义与硬件同源）=====
      val cBits = HiveSimCommon.parseDenseCWriteLog(cWriteLog.toSeq, outFmt, M, N, totalN, cfg.cDmaExtW)
      val cResult = cBits.map(_.map(_ & 0xFFFFL))

      // ===== 数值校验：严格锚点 + 容差对拍（double GEMM 参考）=====
      // 解码：fp16 → fp16ToFloat；bf16 → 高 16 位左移重建 fp32 bits
      def decodeElem(bits: Long): Double = {
        val v = bits.toInt & 0xFFFF
        if (outFmt == DataFormat.FP16) HiveSimCommon.fp16ToFloat(v).toDouble
        else HiveSimCommon.fp32BitsToFloat((v.toLong << 16) & 0xFFFFFFFFL).toDouble
      }
      // fp16 严格锚点判定（bf16 无 65504/2^-14 边界，仅保零行锚点）；
      // 饱和格 = (i==0||i==1) 且 j!=1，或 i>=4 且 j==0——256/-256 行除 j==1 列（B=0.002，结果 ±16.384）外全部溢出；
      // i>=4 行仅 j==0 列（512×行和 ≥ 107315）饱和，j>=2 列 C≈2.9e4~1.8e4 不饱和；
      // 走容差对拍（曾误将 i>=4 全列饱和致 C(4,2) 失配，修正留痕）。
      def isStrict(i: Int, j: Int): Boolean = {
        if (i == 2) true                                        // 零行（fp16/bf16 均为 +0）
        else if (outFmt == DataFormat.FP16 && (i == 0 || i == 1) && j != 1) true  // ±256 行饱和（j==1 为正常值 ±16.384）
        else if (outFmt == DataFormat.FP16 && i >= 4 && j == 0) true          // 512 列大行和饱和（正）
        else if (outFmt == DataFormat.FP16 && i == 3 && j == 1) true          // FTZ 零（3.2e-5 < 2^-14）
        else false
      }
      def strictExpect(i: Int, j: Int): Int = {
        if (i == 2 || (i == 3 && j == 1)) 0x0000                // 零行 / C[3][1] FTZ
        else if (i == 1) 0xFBFF                                 // -65504（保号饱和）
        else 0x7BFF                                             // +65504（其余饱和格均为正）
      }
      var mismatchCnt = 0
      var maxRelErr = 0.0
      for (i <- 0 until M; j <- 0 until N) {
        val hwBits = (cResult(i)(j) & 0xFFFFL).toInt
        if (isStrict(i, j)) {
          withClue(s"[$tag] strict anchor C($i,$j) bits mismatch: ") {
            hwBits should be(strictExpect(i, j))
          }
        } else {
          val ref = (0 until K).map(kk => aFloat(i)(kk).toDouble * bFloat(kk)(j).toDouble).sum
          val hw = decodeElem(cResult(i)(j))
          val absErr = math.abs(hw - ref)
          val relErr = if (math.abs(ref) > 1e-6) absErr / math.abs(ref) else absErr
          if (relErr > 1e-2 && absErr > 1e-2) {
            if (mismatchCnt < 5)
              println(f"[$tag] MISMATCH C($i,$j): hw=$hw%.6g ref=$ref%.6g bits=0x$hwBits%04X")
            mismatchCnt += 1
          }
          if (relErr > maxRelErr) maxRelErr = relErr
        }
      }
      println(f"[$tag] Numerical check: ${M * N} elems, mismatches=$mismatchCnt, maxRelErr=$maxRelErr%.2e")

      mismatchCnt should be(0)
    }
    } finally {
      // --- 复制 FST 波形（源路径段由 testMangle 逐字锚定 it should 用例名）---
      // FST 须在 simulate 块退出（verilator flush/close trace.fst）后再复制；
      // 【任务102】置于 finally：超时/断言失败异常上抛后同样在此转存（Verilator
      // 已对失败拍之前的周期持续写出 trace.fst，文件非空即可拷出供回放）。
      HiveSimCommon.copyTrace(
        s"build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/$testMangle/workdir-verilator/trace.fst",
        s"$simDir/$waveName",
        tag
      )
    }
  }
}
