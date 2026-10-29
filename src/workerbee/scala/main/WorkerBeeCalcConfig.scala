
import chisel3._
import chisel3.util._

class WorkerBeeCalcConfig(cfg: WorkerBeeConfig) extends Module {

    val io = IO(new Bundle {
        val startIn = Input(Bool())
        val startOut = Output(Bool())
        val err = Output(Bool())
        val errPluse = Output(Bool())
        val errCode = Output(UInt(4.W))
        val calcConfig = Output(WorkerBeeExePreCalcConfig(cfg))  // tile 数、地址偏移与 cBeatAddrStep
        val regFile = Input(WorkerBeeRegister(cfg))           // 仅使用 cAddr/m（loopMode 不影响 C 写回遍历）
  })

    val err = RegInit(false.B)
    val errNext = RegNext(err)
    val errCode = Reg(UInt(4.W))
    val calcConfig = Reg(WorkerBeeExePreCalcConfig(cfg)) 
    val errComb = Wire(Bool())
    val errCodeComb = Wire(UInt(4.W))

    val sIDLE      = 0.U(2.W)
    val sC0    = 1.U(2.W)
    val sC1    = 2.U(2.W)   // pop buffer 驱动 writeData、推进地址  
    val sDONE      = 3.U(2.W)
    val state = RegInit(sIDLE)

io.err := err
io.errCode := errCode
io.calcConfig := calcConfig
io.errPluse := err & !errNext


def getBlockSize(t: MXBlockSize.Type) : UInt = {
    val ret = Wire(UInt(8.W))
    when(t === MXBlockSize.M32){
        ret := 32.U
    }.otherwise{
        ret :=  16.U
    }
    ret
}
def getKLoadNum(t: DataFormat.Type) : UInt = {
    // aKLoadNum/bKLoadNum = 一个 kTile（单 pass）推进的 K 元素数 = kPerBeat
    //   = totalN × elemsPerLane(t)。is1B 二分：单字节族（INT8/MX8/纯 FP8）dual-lane
    //   → totalN×2；其余落 else → totalN×4（半字节族 INT4/MXE2M1 quad-lane 正确）。
    // 非打包格式（FP16/BF16/INT32）落 else 也得 totalN×4，但它们的 kTile 在下游走
    //   ceil(k/totalN) 路径，本返回值不参与其几何，属无害兜底——勿当作其 kPerBeat 用。
    // 下方 require 是纯 Scala 编译期自证：本二分依赖 elemsPerLaneLit 取值
    //   INT4/MXE2M1==4、INT8/MX8/FP8==2；该表变动而此处未同步，elaboration 期即炸。
    if (cfg.hasMx4 || cfg.hasInt4 || cfg.hasFp8) {
      require(DataFormat.elemsPerLaneLit(DataFormat.MXE2M1) == 4 &&
              DataFormat.elemsPerLaneLit(DataFormat.INT4) == 4 &&
              DataFormat.elemsPerLaneLit(DataFormat.MXE4M3) == 2 &&
              DataFormat.elemsPerLaneLit(DataFormat.MXE5M2) == 2 &&
              DataFormat.elemsPerLaneLit(DataFormat.INT8) == 2 &&
              DataFormat.elemsPerLaneLit(DataFormat.FP8E4M3) == 2 &&
              DataFormat.elemsPerLaneLit(DataFormat.FP8E5M2) == 2,
        "getKLoadNum is1B split relies on elemsPerLane: INT4/MXE2M1==4 and INT8/MX8/pure-FP8==2; " +
        "if the elemsPerLaneLit single-source table changes, this function must be updated too")
    }
    val ret = Wire(UInt(8.W))
    when(DataFormat.is1B(t)){
        ret := cfg.totalN.U  * 2.U
    }.otherwise{
        ret := cfg.totalN.U  * 4.U
    }
    assert(cfg.totalN > 0, "totalN must be greater than 0")
    assert(cfg.totalN < 64, "totalN must be less than 64, otherwise ret overflow")
    ret
}


/** 外部内存每元素字节数（运行期解码）：FP32/INT32→4、FP16/BF16/INT16→2、
  * INT8/MXE4M3/MXE5M2→1、MXE2M1/INT4（isHalfB 半字节族）→0。
  * 分支序：半字节 → 1B → 2B(含 INT16) → otherwise 4B；otherwise 兜住 FP32/INT32
  *   与 reserved 10..15（DataFormat 枚举无成员）。
  * ★半字节族返回 0 使 colTile 偏移退化为 0（该族不作为输入格式支持）；需要半字节
  *   几何时用 memElemBitsOf。 */
def memElemBytesOf(t: DataFormat.Type) : UInt = {
    val ret = Wire(UInt(3.W))
    when(DataFormat.isHalfB(t)){
        ret := 0.U
    }.elsewhen(DataFormat.is1B(t)){
        ret := 1.U
    }.elsewhen(DataFormat.is2B(t) || t === DataFormat.INT16){
        ret := 2.U
    }.otherwise{
        ret := 4.U
    }
    ret
}

/** 每 16-bit lane 打包的元素数（MXFP8 dual-lane=2、MXFP4/INT4 quad-lane=4、其余=1）；
  * kPerBeat = totalN × 本量。委托到 DataFormat.elemsPerLane 单一真源（Executor kTile
  * 分母共用）。 */
def elemsPerLaneOf(t: DataFormat.Type) : UInt = DataFormat.elemsPerLane(t, cfg.hasMx4)

/** 外部内存每元素【位宽】（运行期解码）。与 memElemBytesOf 互补：字节对齐族
  * memElemBits = memElemBytes×8（恒等），亚字节族 memElemBytes=0 但 memElemBits=4
  * （MXE2M1/INT4）。colTile 偏移的亚字节分支必须用 bits 口径
  * totalN×elemsPerLane×memElemBits/8 才得到 16×4×4/8=32（bytes 口径会退化到 0）。
  * 分支序与 memElemBytesOf 对齐（半字节→1B→2B(含INT16)→otherwise 4B）。 */
def memElemBitsOf(t: DataFormat.Type) : UInt = {
    val ret = Wire(UInt(6.W))
    when(DataFormat.isHalfB(t)){
        ret := 4.U
    }.elsewhen(DataFormat.is1B(t)){
        ret := 8.U
    }.elsewhen(DataFormat.is2B(t) || t === DataFormat.INT16){
        ret := 16.U
    }.otherwise{
        ret := 32.U
    }
    ret
}

//如果是mx类型的，K维至少大于编码block
val aMxBlockSize = WireInit(0.U(8.W))
val aKLoadNum = WireInit(0.U(8.W))
val bMxBlockSize = WireInit(0.U(8.W))
val bKLoadNum = WireInit(0.U(8.W))
val cMxBlockSize = WireInit(0.U(8.W))
val cNWriteNum = WireInit(0.U(8.W))
aMxBlockSize := getBlockSize(io.regFile.aMxBlockSize)
aKLoadNum := getKLoadNum(io.regFile.aFmt)
bMxBlockSize := getBlockSize(io.regFile.bMxBlockSize)
bKLoadNum := getKLoadNum(io.regFile.bFmt)
cMxBlockSize := getBlockSize(io.regFile.cMxBlockSize)
cNWriteNum := cfg.totalN.U

// A=MX8×B=MX4 正向混配谓词，与 WorkerBee.w4a8Mix 同式；hasMx4=false 时恒 false、不生成
//   消费逻辑。下方 bColTile/scaleBColTile 覆盖共用：混配下 B/scaleB 的 kTile 步进按 A 侧
//   aKLoadNum 阵列窗口推导，不用 fp4 源字口径 bKLoadNum。
private val w4a8Mix = if (cfg.hasMx4)
  DataFormat.isMx8(io.regFile.aFmt) && DataFormat.isMx4(io.regFile.bFmt)
else false.B

// A=INT8×B=INT4 正向混配谓词，与 WorkerBee.intW4a8Mix 同式（bDma stretch 端口仅 hasMx4
//   配置生成，故门控 hasInt&&hasMx4）。bColTile 同款阵列窗口口径覆盖；scaleB 无 INT 对应
//   臂（INT 无 scale 通道）。
private val intW4a8Mix = if (cfg.hasInt && cfg.hasMx4)
  io.regFile.aFmt === DataFormat.INT8 && io.regFile.bFmt === DataFormat.INT4
else false.B

// colTile 偏移的两个运行期因子（A/B 各自解码，勿共用：aFmt/bFmt 可异构）。
// 放在模块级无条件赋值，使 sC0 分支内只做一次乘法、不在 when 里生成解码逻辑。
// laneElemsW：任一亚字节格式（MXE2M1/INT4）在场即需 3-bit 容纳打包数 4，否则 2-bit 足够。
val laneElemsW = if(cfg.hasMx4 || cfg.hasInt4) 3 else 2
val aLaneElems = WireInit(0.U(laneElemsW.W))
val aElemBytes = WireInit(0.U(3.W))
val bLaneElems = WireInit(0.U(laneElemsW.W))
val bElemBytes = WireInit(0.U(3.W))
aLaneElems := elemsPerLaneOf(io.regFile.aFmt)
aElemBytes := memElemBytesOf(io.regFile.aFmt)
bLaneElems := elemsPerLaneOf(io.regFile.bFmt)
bElemBytes := memElemBytesOf(io.regFile.bFmt)

    
//Error config check
{
    errComb := false.B
    errCodeComb := 0.U
    val isAMx = DataFormat.isMx(io.regFile.aFmt)
    val isBMx = DataFormat.isMx(io.regFile.bFmt)
    val isMx = isAMx || isBMx
    
    val errMXLargerThanKLoadNum = ((aMxBlockSize > aKLoadNum) & DataFormat.isMx(io.regFile.aFmt))|
    ((bMxBlockSize > bKLoadNum) & DataFormat.isMx(io.regFile.bFmt))|
    ((cMxBlockSize > cNWriteNum) & DataFormat.isMx(io.regFile.cFmt))
    when(errMXLargerThanKLoadNum){
        errComb := true.B
        errCodeComb := 0.U
    }
     
    when(isAMx ^ isBMx){
        errComb := true.B
        errCodeComb := 3.U
    }
    //mx的时候必须loadWMode为1，代表B为转置矩阵，k维切分
    when(isMx & io.regFile.loadWMode === 0.U){
        errComb := true.B
        errCodeComb := 4.U
    }
    // 纯 FP8 运行同样强制 loadWMode=1（阵列侧经 fp8FmtRemap 后跑的就是 MX8×MX8 数据
    //   通路，继承 MX8 的 B 转置布局 + loadW 窗口语义），复用 code 4 语义。
    if (cfg.hasFp8) {
        when(DataFormat.isFp8(io.regFile.aFmt) & io.regFile.loadWMode === 0.U){
            errComb := true.B
            errCodeComb := 4.U
        }
    }
    // errCode 4 仅拦 MX（isMx）与纯 FP8；BF16 与 INT 族两种装载模式均合法。
    when(io.regFile.aFmt === DataFormat.FP32 | io.regFile.bFmt === DataFormat.FP32){
        errComb := true.B
        errCodeComb := 5.U
    }
    //容量不够
    when(io.regFile.m > (cfg.cBufferDepth - cfg.totalN).U){
        errComb := true.B
        errCodeComb := 7.U
    }

    // errCode 10 —— C 写回基址对齐守护（无门控）：cAddr 低 log2Up(dmaBytes) 位必须全零，
    //   否则写回字节被静默丢弃、err 仍为 0（下方 C WRITE-BACK ALIGNMENT 条款 W0）。
    //   dmaBytes 是 2 的幂（require isPow2(cDmaExtW)），故位切片即精确取模；
    //   math.min 仅为 addrWidth < log2Up(dmaBytes) 的退化配置钳位。
    // errCode 码位：0 blocksize 违约（1/2 保留不分配）；3 isAMx^isBMx 与各族反向/
    //   跨族混配；4 isMx/FP8 且 loadWMode==0；5 aFmt/bFmt 为 FP32；6 不可再分配；
    //   7 容量 m > cBufferDepth-totalN；8 K 不被 kPerBeat 整除；9 预留；10 本守护；
    //   11 int/非 int 跨界；12 bias；13 requant。errCode 为 UInt(4.W)，新码从 14 起。
    val cAlignBitsW = math.min(log2Up(cfg.cDmaExtW / 8), cfg.addrWidth)
    when(io.regFile.cAddr(cAlignBitsW - 1, 0) =/= 0.U){
        errComb := true.B
        errCodeComb := 10.U
    }

    // errCode 12 —— fused bias 守护。对齐臂：bias 取数复用 dmaScaleA 32b 只读端口，
    //   单字读契约要求 biasAddr 4B 对齐。!hasMx 臂：取数器随 dmaScaleA 端口只在 hasMx
    //   配置生成，无 MX 配置下 biasOn 无处取数、会永久卡死 Executor 的 sLOAD_A 供数门控。
    when(io.regFile.biasOn && io.regFile.biasAddr(1, 0) =/= 0.U){
        errComb := true.B
        errCodeComb := 12.U
    }
    if(!cfg.hasMx){
        when(io.regFile.biasOn){
            errComb := true.B
            errCodeComb := 12.U
        }
    }

    // errCode 13 —— fused requant 守护。格式臂：requant 链只定义 INT8/INT4 两档饱和语义，
    //   其它 cFmt 无从落码（requantOn+INT 输出会先被优先级更高的 errCode 11 截走，故本臂
    //   在 MX-only 配置下仍需独立存在）。对齐臂：rqM 向量取数复用 dmaScaleA 32b 只读端口，
    //   要求 rqMBase 4B 对齐。!hasMx 臂：同 bias，无 MX 配置下 requantOn 无处取数。
    when(io.regFile.requantOn && !(io.regFile.cFmt === DataFormat.INT8 || io.regFile.cFmt === DataFormat.INT4)){
        errComb := true.B
        errCodeComb := 13.U
    }
    when(io.regFile.requantOn && io.regFile.rqMBase(1, 0) =/= 0.U){
        errComb := true.B
        errCodeComb := 13.U
    }
    if(!cfg.hasMx){
        when(io.regFile.requantOn){
            errComb := true.B
            errCodeComb := 13.U
        }
    }

    // errCode 8 —— K 整除运行期守护（打包格式族）。Executor 的
    //   kTiles = K / (totalN×elemsPerLane) 是【地板除】：K 非 kPerBeat 整除时尾部 K 静默
    //   丢弃；K < kPerBeat 时 newKTiles=0 ⟹ kTiles := 0-1 在 16-bit 下回绕为 65535 ⟹
    //   65536 轮 pass 跑飞 + DMA 越界读。各臂用自身 kPerBeat（totalN×2 或 totalN×4）取模
    //   并一并拒 K==0，按 hasMx/hasFp8/hasMx4/hasInt 编译期门控。
    if(cfg.hasMx){
        when(DataFormat.isMx8(io.regFile.aFmt) &&
             ((io.regFile.k(15, 0) % (cfg.totalN * 2).U =/= 0.U) ||
              (io.regFile.k(15, 0) === 0.U))){
            errComb := true.B
            errCodeComb := 8.U
        }
    }    
    // 纯 FP8 与上方 MX8 臂同构：FP8E4M3/E5M2 dual-lane kPerBeat=totalN×2。
    //   aFmt=FP8 × bFmt 非 FP8 的组合由链尾 code3 新臂以更高优先级截走。
    if(cfg.hasFp8){
        when(DataFormat.isFp8(io.regFile.aFmt) &&
             ((io.regFile.k(15, 0) % (cfg.totalN * 2).U =/= 0.U) ||
              (io.regFile.k(15, 0) === 0.U))){
            errComb := true.B
            errCodeComb := 8.U
        }
    }

    if(cfg.hasMx4){
        when(DataFormat.isMx4(io.regFile.aFmt) &&
             ((io.regFile.k(15, 0) % (cfg.totalN * 4).U =/= 0.U) ||
              (io.regFile.k(15, 0) === 0.U))){
            errComb := true.B
            errCodeComb := 8.U
        }
        // 混配 stretch 单读契约守护（A=MX8×B=MX4）：B DMA 走 stretch，逻辑拍 = 16B
        //   半字片、物理读 = 32B 对齐单读（不走 gather 两读）。两条守护：
        //   ① K%(totalN*4)==0：窗口粒度虽是 32，K%64==32（kTile 奇数）会使末窗口的
        //      32B 单读越出张量界 16B，故保持源粒度 64 不放宽；
        //   ② bAddr%32==0：半字拍片契约（L mod 32 ∈ {0,16}）与「读严格落张量界内」的
        //      前提（portBytes=totalN*bW/8）。
        when(DataFormat.isMx8(io.regFile.aFmt) && DataFormat.isMx4(io.regFile.bFmt) &&
             ((io.regFile.k(15, 0) % (cfg.totalN * 4).U =/= 0.U) ||
              (io.regFile.k(15, 0) === 0.U) ||
              ((io.regFile.bAddr % (cfg.totalN * cfg.bW / 8).U) =/= 0.U))){
            errComb := true.B
            errCodeComb := 8.U
        }
    }
    // INT8/INT4 errCode 8 与上方 MX 两块同构：INT8 dual-lane kPerBeat=totalN×2、
    //   INT4 quad-lane kPerBeat=totalN×4。
    if(cfg.hasInt){
        when(io.regFile.aFmt === DataFormat.INT8 &&
             ((io.regFile.k(15, 0) % (cfg.totalN * 2).U =/= 0.U) ||
              (io.regFile.k(15, 0) === 0.U))){
            errComb := true.B
            errCodeComb := 8.U
        }
        when(io.regFile.aFmt === DataFormat.INT4 &&
             ((io.regFile.k(15, 0) % (cfg.totalN * 4).U =/= 0.U) ||
              (io.regFile.k(15, 0) === 0.U))){
            errComb := true.B
            errCodeComb := 8.U
        }
        // INT 混配 stretch 单读契约守护（A=INT8×B=INT4），与上方 MX 混配臂同构。三条：
        //   ⓪ loadWMode=1：stretch 半字切片取「转置布局中一行内连续 32 个 K 元素」——
        //      loadW=0 的 K×N 行主序里同列 K 元素跨行步进 bRowOff，32 连续 nibble 是 32 个
        //      N 元素而非 K 窗口，几何不可达（且混配 bColTile 覆盖会破坏 n-tile 语义）；
        //   ① K%(totalN*4)==0：kTile 奇数（K%64==32）时末窗口 32B 单读越出张量界 16B；
        //   ② bAddr%32==0：半字拍片契约（L mod 32 ∈ {0,16}）与读落张量界内的前提。
        //   纯 INT8/INT4 不命中本臂，两种 loadW 布局均合法。
        when(io.regFile.aFmt === DataFormat.INT8 && io.regFile.bFmt === DataFormat.INT4 &&
             (io.regFile.loadWMode === 0.U ||
              (io.regFile.k(15, 0) % (cfg.totalN * 4).U) =/= 0.U ||
              (io.regFile.k(15, 0) === 0.U) ||
              (io.regFile.bAddr % (cfg.totalN * cfg.bW / 8).U) =/= 0.U)){
            errComb := true.B
            errCodeComb := 8.U
        }
    }
    // BF16 errCode 8 同码但不同根因：BF16 是【非打包】格式（16-bit lane 单元素）⟹
    //   Executor.newKTiles 走 otherwise 支 ceil(K/totalN)，没有丢尾故障。两条真实故障：
    //     ① K % totalN != 0：ceil 多算一个【部分填充】的尾 kTile，阵列按整 tile 供数，
    //        超出张量界的 K 元素由 DMA 越界读入并被真实累加进结果；
    //     ② K == 0：newKTiles=0 ⟹ kTiles := 0-1 回绕为 65535 ⟹ pass 跑飞 + 越界读。
    //   判据分母 = totalN×1（elemsPerLane(BF16)=1）。【不可】复用 getKLoadNum(BF16)：
    //     该函数对非打包格式返回 totalN×4 的兜底值，不是 BF16 的 kPerBeat。
    if(cfg.hasBf16){
        when(io.regFile.aFmt === DataFormat.BF16 &&
             ((io.regFile.k(15, 0) % cfg.totalN.U =/= 0.U) ||
              (io.regFile.k(15, 0) === 0.U))){
            errComb := true.B
            errCodeComb := 8.U
        }
    }
    // errCode 11 —— int / 非 int 格式跨界守护（XOR，无门控）。WbOutCvt 的两条互斥输出
    //   路径（fp32Seq = fpNormalize40to32(slot(39,0)) 与 int32Seq = slot(31,0)）选择子
    //   【只看 cFmt】：整数计算配 fp32 路径（或反之）会静默产出语义错误的位型。判据
    //   err = runIsInt ^ cIsIntOut。runIsInt 必须排除 isFp8：FP8E4M3/E5M2 不属
    //   is2B/FP32/isMx 任一谓词，不排除则纯 FP8 运行被误判 INT 族 ⟹ fp32 输出假报、INT
    //   输出漏报（与 Executor.biasLazy 同式）。cFmt=INT16 在 runIsInt 下放行无害：
    //   WbOutCvt 的 packedInt16 路径存在且语义正确（int32Seq 截到 16b）。
    val cIsIntOut = DataFormat.isInt(io.regFile.cFmt) || io.regFile.cFmt === DataFormat.INT32
    val runIsInt  = !DataFormat.is2B(io.regFile.aFmt) && io.regFile.aFmt =/= DataFormat.FP32 &&
      !DataFormat.isMx(io.regFile.aFmt) && !DataFormat.isFp8(io.regFile.aFmt)
    when(runIsInt ^ cIsIntOut){
        errComb := true.B
        errCodeComb := 11.U
    }

    // errCode 3 —— MX 反向混配（A=MX4 × B=MX8）拒绝。WbWorkUnit 的运行期选择子
    //   Mux(isMx4(io.aFmt), quadResult, dualLaneMx()) 只看 aFmt，而被选支路内 b 侧解码器
    //   接 io.bFmt ⟹ lane 粒度与解码格式矛盾、静默算错。裸 errCode3（isAMx ^ isBMx）对此
    //   漏检：isMx 已含 MXE2M1，两侧同 true ⟹ XOR=0。正向混配（A=MX8 × B=MX4）合法，由
    //   fp4→fp8 push 侧展开路落地。本块及其后各 code3 臂位于链尾，last-connect 使其成为
    //   最高优先级：3（各族反向/跨族混配） > 11 > 8 > 13 > 12 > 10 > 7 > 5 > 4 >
    //   3(isAMx^isBMx) > 0。
    if(cfg.hasMx4){
        when(DataFormat.isMx4(io.regFile.aFmt) && !DataFormat.isMx4(io.regFile.bFmt)){
            errComb := true.B
            errCodeComb := 3.U
        }
    }

    // errCode 3 —— INT 反向混配（A=INT4 × B=INT8）拒绝，与上方 MX 反向混配臂同构。
    //   正向（A=INT8×B=INT4）合法，由 bDma stretch 展开路落地（B 半字片 16B 经
    //   int4SliceToInt8Beat sext 加宽成 32B INT8 阵列拍）。反向下 K 窗口 = aKLoadNum=64
    //   个元素，B 每行窗口切片 = 64 个 INT8 = 64B，超出 B 拍宽（totalN*bW/8=32B）两倍，
    //   须 B 侧 push 连推 2 拍 —— 源字跨窗口会产生行交错序，几何不可达。
    if(cfg.hasInt){
        when(io.regFile.aFmt === DataFormat.INT4 && io.regFile.bFmt === DataFormat.INT8){
            errComb := true.B
            errCodeComb := 3.U
        }
    }

    // errCode 3 —— FP8 跨族混配 + FP8 输出编码拒绝（两臂均封堵静默算错）：
    //   ① isFp8(aFmt) ^ isFp8(bFmt)：FP8×{INT8/INT4/FP16/BF16} 混跑时 0/3/4/5/11 全不
    //      命中（isMx(FP8)=false、runIsInt=false）⟹ 阵列侧 remap 成 MX8 而 B 侧解码落
    //      FusionMacFp 的 fp4 支路，乘积槽全零 + scaleB 注入 127 与 B 实际格式矛盾。
    //      FP8×MX8 已由裸 errCode3 覆盖；纯 FP8×FP8（含 E4M3×E5M2 异构）合法放行。
    //   ② isFp8(cFmt)：输出侧无 FP8 通路 —— 量化输出是 MX 块 scale 语义（dmaCScale 每
    //      16/32 元素块写 E8M0 字节），per-tensor 纯 FP8 输出不拒绝会静默落 fp32 直出。
    //      合法输出集：cFmt=FP32 直出或 cFmt ∈ {MXE4M3,MXE5M2,MXE2M1} 量化。
    if(cfg.hasFp8){
        when((DataFormat.isFp8(io.regFile.aFmt) ^ DataFormat.isFp8(io.regFile.bFmt)) ||
             DataFormat.isFp8(io.regFile.cFmt)){
            errComb := true.B
            errCodeComb := 3.U
        }
    }

    // errCode 3 —— BF16 跨族混配拒绝，与上方 FP8 臂 ① 同构。BF16×{INT8/INT4} 混跑时
    //   0/3/4/5/8/11 全不命中（isMx 两侧皆 false；runIsInt 对 BF16 取 false；BF16 的
    //   errCode 8/4 只看 aFmt），漏网后唯一拦截点是 WbFusionMacFp lane0 的 assert（综合后
    //   消失）⟹ 硅上静默算错。判据只用单边异或、【不】连带拒 cFmt：BF16 是合法输出编码
    //   （WbOutCvt 有 packed16 直出臂）。本臂只补 BF16×INT。
    if(cfg.hasBf16){
        when((io.regFile.aFmt === DataFormat.BF16) =/= (io.regFile.bFmt === DataFormat.BF16)){
            errComb := true.B
            errCodeComb := 3.U
        }
    }

}



switch(state) {

    // sIDLE 无条件清 err/errCode，使 io.err 成为「本次 EXECUTE 的配置判决」而非 idle 期
    // 当前配置是否合法（上方裸块每拍评估并置位，装配阶段写坏 blocksize 即会误报 io.err）。
    // 本 switch 在裸块之后，last-connect 使 sIDLE 分支覆盖裸块置位；errCode Reg 无
    // RegInit，上电首拍 state=sIDLE 即由本分支初始化为 0。
    is(sIDLE) { 
      err := false.B
      errCode := 0.U
      when(io.startIn) {
        state := sC0
      }
    }

    is(sC0){
      
      
      when(errComb){
        err := errComb
        errCode := errCodeComb
      }
      
      state := sC1
      calcConfig.mTile := (io.regFile.m + cfg.totalN.U - 1.U) / cfg.totalN.U
      calcConfig.nTile := (io.regFile.n + cfg.totalN.U - 1.U) / cfg.totalN.U
    
      calcConfig.isAX2 := DataFormat.is1B(io.regFile.aFmt)
      calcConfig.isBX2 := DataFormat.is1B(io.regFile.bFmt)
      calcConfig.isAX4 := DataFormat.isHalfB(io.regFile.aFmt)
      calcConfig.isBX4 := DataFormat.isHalfB(io.regFile.bFmt)

    // kTile 分母判据 = 「打包格式」= is1B || isHalfB（已完整覆盖全部 MX 与 INT8/INT4）。
    //   打包格式每拍推进 aKLoadNum=kPerBeat 个 K 元素 ⟹ kTile = k / aKLoadNum（地板除，
    //   与 Executor newKTiles 同源）；非打包格式每拍 totalN 个 K，走 ceil(k/totalN)。
    when(DataFormat.is1B(io.regFile.aFmt) || DataFormat.isHalfB(io.regFile.aFmt)){
        calcConfig.kTile := (io.regFile.k / aKLoadNum)
    }.
    otherwise{
        calcConfig.kTile := (io.regFile.k  + cfg.totalN.U - 1.U) / cfg.totalN.U
    }



  
  // A 矩阵 row major 行步进偏移 = K × memElemBytes(aFmt)。
  // 分支序按 memElemBytes 排：4B → 2B(含 INT16) → 1B → otherwise（半字节/reserved，
  //   不作为输入格式支持，保留 /2）。FP32 作输入已被 errCode=5 拦死，is4B 实达 INT32。
  when(DataFormat.is4B(io.regFile.aFmt)){
    calcConfig.aRowAddressOffset := io.regFile.k * 4.U
  }.elsewhen(DataFormat.is2B(io.regFile.aFmt) || io.regFile.aFmt === DataFormat.INT16){
    calcConfig.aRowAddressOffset := io.regFile.k * 2.U
  }.elsewhen(DataFormat.is1B(io.regFile.aFmt)){
    calcConfig.aRowAddressOffset := io.regFile.k * 1.U
  }.otherwise{
    calcConfig.aRowAddressOffset := io.regFile.k / 2.U
  }
  // aColTileAddressOffset = 一个 kTile 在外部内存中跨过的字节数（消费者 =
  //   WorkerBeeDmaRdOnly isA=true，闭式 块首址(nt,kt) = base + kt*C）。A 行主序 M×K、
  //   每元素 aEb 字节、一个 kTile 推进 kPerBeat = totalN × elemsPerLane 个 K 元素
  //   ⟹ C = kPerBeat × aEb。必须是【运行期】量：用编译期 totalN*(aW/8) 会恒等于 32、
  //   与 aFmt 无关（INT8 应 16、INT32 应 64）。kTile 步进一律用「每拍 K 元素数」而非
  //   totalN，与 scaleAColTileAddressOffset = aKLoadNum / blk 同口径。
  // 亚字节族 memElemBytes=0 会让 bytes 口径退化到 0，故在 hasMx4||hasInt4 下改用 bits
  //   口径 totalN × elemsPerLane × memElemBits / 8；fp4: 16×4×4/8 = 32。字节对齐族
  //   memElemBits/8 == memElemBytes，故两支逐值恒等（MX8=32/FP16=32/INT8=16/INT32=64）。
  if(cfg.hasMx4 || cfg.hasInt4){
    calcConfig.aColTileAddressOffset := cfg.totalN.U * (aLaneElems * memElemBitsOf(io.regFile.aFmt)) / 8.U
  } else {
    calcConfig.aColTileAddressOffset := cfg.totalN.U * (aLaneElems * aElemBytes)
  }

  
// B 行步进偏移链：loadWMode=0 时 B 为 K×N 行主序、行长 = N ⟹ 行步进 = n × bEb
//   （不是 m；M≠N 时用 m 会错位）；loadWMode=1 时 B 按 N×K 转置存储（errCode 4
//   强制 MX 必走此模式）⟹ 行步进 = k × bEb。bEb 取 memElemBytes：INT16→2、INT32→4。
when(io.regFile.loadWMode === 0.U){
  when(DataFormat.is4B(io.regFile.bFmt)){
    calcConfig.bRowAddressOffset := io.regFile.n * 4.U
  }.elsewhen(DataFormat.is2B(io.regFile.bFmt) || io.regFile.bFmt === DataFormat.INT16){
    calcConfig.bRowAddressOffset := io.regFile.n * 2.U
  }.elsewhen(DataFormat.is1B(io.regFile.bFmt)){
    calcConfig.bRowAddressOffset := io.regFile.n * 1.U
  }.otherwise{
    calcConfig.bRowAddressOffset := io.regFile.n / 2.U
  }
}.otherwise{
  //B矩阵col major转置 偏移计算
  when(DataFormat.is4B(io.regFile.bFmt)){
    calcConfig.bRowAddressOffset := io.regFile.k * 4.U
  }.elsewhen(DataFormat.is2B(io.regFile.bFmt) || io.regFile.bFmt === DataFormat.INT16){
    calcConfig.bRowAddressOffset := io.regFile.k * 2.U
  }.elsewhen(DataFormat.is1B(io.regFile.bFmt)){
    calcConfig.bRowAddressOffset := io.regFile.k * 1.U
  }.otherwise{
    calcConfig.bRowAddressOffset := io.regFile.k / 2.U
  }
}
// bColTileAddressOffset = totalN × elemsPerLane(bFmt) × memElemBytes(bFmt)，必须是
//   【运行期】量（编译期 bW 会使整式恒为 32，与运行期 bFmt 无关）。
// 本偏移在两种 loadWMode 下语义不同，但同一个式子精确覆盖两支（两支持有的 elemsPerLane
//   天然互斥，不需要 Mux）：
//   · loadWMode=0（B 为 K×N 行主序）：【n-tile 步进】= 跨过 totalN 个 N 列 = totalN × bEb；
//     此支恒非 MX（errCode 4 拦 MX+loadW=0）⟹ elemsPerLane=1 自动退化。
//   · loadWMode=1（B 为 N×K 转置，MX 强制走此模式）：【k-tile 步进】= 一个 kTile 推进
//     bKpb = totalN × elemsPerLane 个 K 元素的字节数；MX8 下 2×16×1 = 32。
// 亚字节族 memElemBytes=0 会让 bytes 口径退化到 0，故在 hasMx4||hasInt4 下改用 bits 口径
//   totalN × elemsPerLane × memElemBits / 8；fp4: 16×4×4/8 = 32。字节对齐族
//   memElemBits/8 == memElemBytes，故两支逐值恒等（MX8=32/FP16=32/INT8=16/INT32=64）。
// 勿用 Mux(loadWMode, totalN×k×(bW/8), …) 这类「n-tile 周期语义」式子：与
//   WorkerBeeDmaRdOnly lastBTileStartAddr 的累加口径不兼容。colTileOffset 必须保持
//   单一运行期量、由消费者按语义解释。
if(cfg.hasMx4 || cfg.hasInt4){
  calcConfig.bColTileAddressOffset := cfg.totalN.U * (bLaneElems * memElemBitsOf(io.regFile.bFmt)) / 8.U
  if (cfg.hasMx4) {
    // 混配 bDma stretch：k 窗口步进 = aKLoadNum(32) 个 K 元素 × 0.5B = 16B（半字片），
    //   取 A=MX8 阵列窗口口径而非 fp4 源字口径（64 元素 32B）。非混配 last-connect 不到。
    when(w4a8Mix) {
      calcConfig.bColTileAddressOffset := aKLoadNum * memElemBitsOf(io.regFile.bFmt) / 8.U
    }
    // INT8×INT4 混配同款覆盖：k 窗口步进 = aKLoadNum(32) × 0.5B = 16B，取 A=INT8 阵列
    //   窗口口径而非 INT4 源字口径。INT 无 scale 通道，故 scaleB 无对应覆盖。
    if (cfg.hasInt) {
      when(intW4a8Mix) {
        calcConfig.bColTileAddressOffset := aKLoadNum * memElemBitsOf(io.regFile.bFmt) / 8.U
      }
    }
  }
} else {
  calcConfig.bColTileAddressOffset := cfg.totalN.U * (bLaneElems * bElemBytes)
}

// C 行主序偏移链，分支顺序「半字节 → 1B → 2B(含 INT16) → otherwise(4B)」：
//   otherwise 必须存在并兜底 4B 档，覆盖 is4B(FP32/INT32) 与 reserved 编码
//   （1011..1111），否则这些 cFmt 下 cRowAddressOffset/cColTileAddressOffset
//   无赋值、保留 last-connect 垃圾值。INT16 不被 is2B 覆盖，须显式并入 2B 档。
when(DataFormat.isHalfB(io.regFile.cFmt)){
  calcConfig.cRowAddressOffset := io.regFile.n >> 1
  calcConfig.cColTileAddressOffset := cfg.totalN.U >> 1
}.elsewhen(DataFormat.is1B(io.regFile.cFmt)){
  calcConfig.cRowAddressOffset := io.regFile.n * 1.U
  calcConfig.cColTileAddressOffset := cfg.totalN.U * 1.U
}.elsewhen(DataFormat.is2B(io.regFile.cFmt) || io.regFile.cFmt === DataFormat.INT16){
  calcConfig.cRowAddressOffset := io.regFile.n * 2.U
  calcConfig.cColTileAddressOffset := cfg.totalN.U * 2.U
}.otherwise{
  // is4B(FP32/INT32) + reserved(含 1011..1111) → 4B 档（旧 MuxCase 默认）
  calcConfig.cRowAddressOffset := io.regFile.n * 4.U
  calcConfig.cColTileAddressOffset := cfg.totalN.U * 4.U
}

// MX SCALE READ ALIGNMENT (ASCII only).
// SINGLE SOURCE of the two scale strides consumed by WorkerBeeDmaRdOnly (scaleA
// isA=true, scaleB isA=false): rowStride = scaleXRowAddressOffset = K/blk bytes,
// kTileStride = scaleXColTileAddressOffset = kLoadNum/blk bytes, with blk = 32
// (M32) / 16 (M16) and kLoadNum = totalN*elemsPerLane = kPerBeat (32 MXFP8 / 64
// MXFP4 at totalN=16) over a DENSE E8M0 layout, one byte per MX block.
// The DMA issues ONE 32-BIT read per beat at addr = BASE + row*rowStride +
// kt*kTileStride, touching addr..addr+3 while only the first kPerBeat/blk bytes
// are selected by the Executor byte mux idx = (lane*elemsPerLane)/blk; the rest
// are don't-care and may run past the end of the tensor on the last kTile of the
// last row.
// The dense layout does NOT keep these reads 4-byte aligned. Full alignment needs
// (S0) BASE%4==0, (S1) rowStride%4==0 OR a single row, (S2) kTileStride%4==0 OR
// kTiles==1; substituting gives (S1) <=> K%(4*blk)==0 and (S2) <=>
// (kPerBeat/blk)%4==0 = 1 or 2 for MXFP8 (never 0 mod 4), 2 (blk=32) / 4 (blk=16)
// for MXFP4. So MXFP4+blk=16 is the ONLY non-degenerate combination satisfying
// all three (errCode 8 already forces K%64==0); MXFP8 and MXFP4+blk=32 are
// aligned only when kTiles==1 AND a single row.
// No errCode guards this: DmaRdOnly serves a misaligned logical beat at L by
// aligned physical reads [L-off, L-off+4) (off = L mod 4) plus a byte-shift merge.
// That GATHER path costs one HARDWARE-SIDE obligation: the loadW window pops
// scaleB one-per-cycle while gather supply can drop to 1 word / 2 cycles, so
// WorkerBeeExecutor gates sLOAD_B entry on scaleB/B FIFO occupancy >= totalN.
//A矩阵 scale 偏移计算
when(MXBlockSize.M32 === io.regFile.aMxBlockSize){
    calcConfig.scaleARowAddressOffset     := io.regFile.k / 32.U
    calcConfig.scaleAColTileAddressOffset := aKLoadNum / 32.U
}.otherwise{
    calcConfig.scaleARowAddressOffset     := io.regFile.k / 16.U
    calcConfig.scaleAColTileAddressOffset := aKLoadNum / 16.U
}
//B矩阵 scale 偏移计算
when(MXBlockSize.M32 === io.regFile.bMxBlockSize){
    calcConfig.scaleBRowAddressOffset     := io.regFile.k / 32.U
    calcConfig.scaleBColTileAddressOffset := bKLoadNum / 32.U
}.
otherwise{
    calcConfig.scaleBRowAddressOffset     := io.regFile.k / 16.U
    calcConfig.scaleBColTileAddressOffset := bKLoadNum / 16.U
}
// 混配 scaleB kTile 步进 = 阵列窗口口径 aKLoadNum/blk（blk16→2B、blk32→1B；窗口 w
//   覆盖 K 块 {2w,2w+1}(blk16) / {w}(blk32)，相邻窗口读相邻 scale 字），而非 fp4 源字
//   口径 bKLoadNum/blk。非混配 last-connect 不到。
if (cfg.hasMx4) {
  when(w4a8Mix) {
    calcConfig.scaleBColTileAddressOffset :=
      aKLoadNum / Mux(io.regFile.bMxBlockSize === MXBlockSize.M32, 32.U, 16.U)
  }
}
//C矩阵 scale 偏移计算
when(MXBlockSize.M32 === io.regFile.cMxBlockSize){
    calcConfig.scaleCRowAddressOffset     := (io.regFile.n +& 31.U) / 32.U
    calcConfig.scaleCColTileAddressOffset := cfg.totalN.U / 32.U
}.
otherwise{
    calcConfig.scaleCRowAddressOffset     := (io.regFile.n +& 15.U) / 16.U
    calcConfig.scaleCColTileAddressOffset := cfg.totalN.U / 16.U
}


// C WRITE-BACK ALIGNMENT (ASCII only).
// SINGLE SOURCE of the five quantities WorkerBeeDmaWrOnly consumes to place a C
// beat on the external bus word:
//   cWriteBytes     bytes of one dense output row  (totalN * elemBytes(cFmt))
//   cDmaBytes       = cDmaExtW/8 = dmaBytes, the bus word size in bytes (32)
//   cBeatAddrStep   address increment between two beats of the SAME row
//   cDmaBufBeatTar  last beat index inside a row   (beats/row - 1)
//   cDmaDmaBeatTar  lane-occupancy selector: 0 -> full, 1 -> 1/2, else -> 1/4
// addr(nt,row,beat) = cAddr + nt*cColTileAddressOffset + row*cRowAddressOffset
//                     + beat*cBeatAddrStep
// WorkerBeeDmaWrOnly shifts BOTH wdata and wstrb left by off = addr mod dmaBytes
// before truncating back to the bus word; asserted strb bits are
// lanes = dmaBytes >> cDmaDmaBeatTar.
// HARM CONDITION: off + lanes > dmaBytes -- the top (off + lanes - dmaBytes)
//   bytes fall outside the (cDmaExtW-1,0) wdata slice and the dmaBytes-wide strb
//   sink and are SILENTLY DROPPED (err stays 0, done still pulses).
// The beat term NEVER contributes to off: cWriteBytes > cDmaBytes =>
//   cBeatAddrStep = cDmaBytes (a multiple of dmaBytes); otherwise
//   cDmaBufBeatTar = 0 so beatCnt is always 0.
// Per cFmt class at totalN=16 / cDmaExtW=256, as (cWriteBytes, beats/row, lanes,
//   cRowAddrOff, cColTileAddrOff, slack = dmaBytes-lanes):
//   half-byte INT4/MXE2M1    8, 1,  8, n>>1,  8, 24
//   1-byte    INT8/MXFP8    16, 1, 16, n*1,  16, 16
//   2-byte    FP16/BF16/I16 32, 1, 32, n*2,  32,  0
//   4-byte    FP32/I32/rsvd 64, 2, 32, n*4,  64,  0
// Three independent clauses, each sufficient alone to keep off == 0 for the WHOLE
// run (and so make the harm condition unreachable):
//   (W0) cAddr % dmaBytes == 0 -- the only one checked in hardware (errCode 10,
//        blocks startOut).
//   (W1) cRowAddressOffset % dmaBytes == 0 OR M == 1 -- software obligation. At
//        totalN=16/dmaBytes=32: 4-byte class -> n % 8 == 0; 2-byte -> n % 16;
//        1-byte -> n % 32; half-byte -> n % 64.
//   (W2) cColTileAddressOffset % dmaBytes == 0 OR nTile == 1 -- software
//        obligation. The 2-byte / 4-byte classes satisfy it identically; the
//        1-byte (16) and half-byte (8) classes do NOT, so a multi-n-tile run
//        there relies on the slack column instead (lanes=16 tolerates off <= 16,
//        lanes=8 tolerates off <= 24). AXI permits that since wstrb is honoured,
//        but a format/stride change shrinking the slack to 0 turns it into silent
//        byte loss with no RTL edit.
// W1/W2 depend on M / nTile, known only inside the traversal, so guarding them
// would need per-beat arithmetic rather than a config-time predicate.
// SEPARATE, UNGUARDED PATH (do not conflate with W0/W1/W2): when N is not a
//   multiple of totalN the LAST beat of a row asserts more lanes than the row
//   still has bytes, i.e. `lanes` overshoots the valid data (off is unaffected).
// The derivation below is one half of an IMPLICIT contract with
// WorkerBeeDmaWrOnly, which maps the same bus word twice: intra-beat segmentation
// by the COMPILE-TIME constant cDmaExtW, and byte-lane offset by the RUNTIME
// cBeatAddrStep derived here. No require/assertion binds the two -- editing
// cBeatAddrStep or cDmaBufBeatTar MUST re-verify both paths there.
  val cWriteBytes = Wire(UInt((8.W)))
  val cDmaBytes = Wire(UInt((8.W)))
  cDmaBytes := cfg.cDmaExtW.U / 8.U
  
  // cWriteBytes 分支顺序同 C 偏移链：半字节 → 1B → 2B(含 INT16) → otherwise(4B)。
  //   otherwise 必须兜底 4B 而非半字节，否则 reserved/INT16 被算成 totalN/2，
  //   cDmaBufBeatTar 变 0（单拍），beat#1 走行步进而非拍步进。
  when(DataFormat.isHalfB(io.regFile.cFmt)){
    cWriteBytes := cfg.totalN.U / 2.U
  }.elsewhen(DataFormat.is1B(io.regFile.cFmt)){
    cWriteBytes := 1.U * cfg.totalN.U
  }.elsewhen(DataFormat.is2B(io.regFile.cFmt) || io.regFile.cFmt === DataFormat.INT16){
    cWriteBytes := 2.U * cfg.totalN.U
  }.otherwise{
    cWriteBytes := 4.U * cfg.totalN.U
  }

  when(cWriteBytes >  cDmaBytes){
    calcConfig.cBeatAddrStep := cDmaBytes
    calcConfig.cDmaBufBeatTar := cWriteBytes / cDmaBytes - 1.U
    calcConfig.cDmaDmaBeatTar := 0.U
  }.otherwise{
    calcConfig.cBeatAddrStep := cWriteBytes
    calcConfig.cDmaBufBeatTar := 0.U
    calcConfig.cDmaDmaBeatTar := cDmaBytes / cWriteBytes - 1.U
  }

   
}

    is(sC1){
        state := sDONE
    }

    // sDONE 重新接受 startIn，使重复 EXECUTE 可再次拉高 io.startOut。进入 sC0 前清除
    // 上一轮 err/errCode（sticky 语义：err 保持到下一次 start 才清）。
    is(sDONE){
        when(io.startIn) {
            err := false.B
            errCode := 0.U
            state := sC0
        }
    }

}

io.startOut := state === sC1 && !err
  
  
}