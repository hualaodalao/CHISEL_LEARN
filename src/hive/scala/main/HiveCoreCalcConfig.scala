
import chisel3._
import chisel3.util._

class HiveCoreCalcConfig(cfg: HiveCoreConfig) extends Module {

    val io = IO(new Bundle {
        val startIn = Input(Bool())
        val startOut = Output(Bool())
        val err = Output(Bool())
        val errCode = Output(UInt(4.W))
        val calcConfig = Output(HiveCoreExePreCalcConfig(cfg))  // tile 数、地址偏移与 cBeatAddrStep
        val regFile = Input(HiveCoreRegister(cfg))           // 仅使用 cAddr/m（loopMode 不影响 C 写回遍历）
  })

    val err = RegInit(false.B)
    val errCode = Reg(UInt(4.W))
    val calcConfig = Reg(HiveCoreExePreCalcConfig(cfg)) 
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

def getBlockSize(t: MXBlockSize.Type) : UInt = {
    //MXBlockSize
    val ret = Wire(UInt(8.W))
    when(t === MXBlockSize.M64){
        ret := 64.U
    }.elsewhen(t === MXBlockSize.M32){
        ret := 32.U
    }.elsewhen(t === MXBlockSize.M16){
        ret := 16.U
    }.otherwise{
        ret :=  8.U
    }
    ret
}
def getKLoadNum(t: DataFormat.Type) : UInt = {
    val ret = Wire(UInt(8.W))
    when(DataFormat.isMx8(t)){
        ret := cfg.totalN.U  * 2.U
    }.otherwise{
        ret := cfg.totalN.U  * 4.U
    }
    assert(cfg.totalN > 0, "totalN must be greater than 0")
    assert(cfg.totalN < 64, "totalN must be less than 64, otherwise ret overflow")
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

    
//Error config check
{
    errComb := false.B
    errCodeComb := 0.U
    when((aMxBlockSize > aKLoadNum) & DataFormat.isMx(io.regFile.aFmt)){
        err := true.B
        errCode := 0.U
    }
    when((bMxBlockSize > bKLoadNum) & DataFormat.isMx(io.regFile.bFmt)){
        err := true.B
        errCode := 1.U
    }   
    when((cMxBlockSize > cNWriteNum) & DataFormat.isMx(io.regFile.cFmt)){
        err := true.B
        errCode := 2.U
    }

}



switch(state) {

    // --- sIDLE: 等待 start ---
    is(sIDLE) { 
      when(io.startIn) {
        err := false.B
        state := sC0
      }
    }

    is(sC0){
        state := sC1
    calcConfig.mTile := (io.regFile.m + cfg.totalN.U - 1.U) / cfg.totalN.U
    calcConfig.nTile := (io.regFile.n + cfg.totalN.U - 1.U) / cfg.totalN.U
    
  

    when(DataFormat.isMx(io.regFile.aFmt)){
        calcConfig.kTile := (io.regFile.k / aKLoadNum)
    }.
    otherwise{
        calcConfig.kTile := (io.regFile.k  + cfg.totalN.U - 1.U) / cfg.totalN.U
    }



    
    val isMx = DataFormat.isMx(io.regFile.aFmt) || DataFormat.isMx(io.regFile.bFmt)
    when(DataFormat.isMx(io.regFile.aFmt) ^ DataFormat.isMx(io.regFile.bFmt)){
        err := true.B
        errCode := 3.U
    }
    //mx的时候必须loadWMode为1，代表B为转置矩阵，k维切分
    when(isMx & io.regFile.loadWMode === 0.U){
        err := true.B
        errCode := 4.U
    }

    when(io.regFile.aFmt === DataFormat.FP32 | io.regFile.bFmt === DataFormat.FP32){
        err := true.B
        errCode := 5.U
    }
  
  // ==========================================================================
  // A矩阵row major 偏移计算
  when(DataFormat.is2B(io.regFile.aFmt)){
    calcConfig.aRowAddressOffset := io.regFile.k * 2.U
  }.elsewhen(DataFormat.is1B(io.regFile.aFmt)){
    calcConfig.aRowAddressOffset := io.regFile.k * 1.U
  }.otherwise{
    calcConfig.aRowAddressOffset := io.regFile.k / 2.U
  }
  calcConfig.aColTileAddressOffset := cfg.totalN.U * (cfg.aW / 8).U
  
// B矩阵非转置 row major偏移计算
when(io.regFile.loadWMode === 0.U){
  when(DataFormat.is2B(io.regFile.bFmt)){
    calcConfig.bRowAddressOffset := io.regFile.m * 2.U
  }.elsewhen(DataFormat.is1B(io.regFile.bFmt)){
    calcConfig.bRowAddressOffset := io.regFile.m * 1.U
  }.otherwise{
    calcConfig.bRowAddressOffset := io.regFile.m / 2.U
  }
}.otherwise{
  //B矩阵col major转置 偏移计算
when(DataFormat.is2B(io.regFile.bFmt)){
    calcConfig.bRowAddressOffset := io.regFile.k * 2.U
  }.elsewhen(DataFormat.is1B(io.regFile.bFmt)){
    calcConfig.bRowAddressOffset := io.regFile.k * 1.U
  }.otherwise{
    calcConfig.bRowAddressOffset := io.regFile.k / 2.U
  }
}
calcConfig.bColTileAddressOffset := cfg.totalN.U * (cfg.bW / 8).U

//C矩阵Row Major偏移计算
when(DataFormat.is4B(io.regFile.cFmt)){
  calcConfig.cRowAddressOffset := io.regFile.n * 4.U
  calcConfig.cColTileAddressOffset := cfg.totalN.U * 4.U
}.elsewhen(DataFormat.is2B(io.regFile.cFmt)){
  calcConfig.cRowAddressOffset := io.regFile.n * 2.U
  calcConfig.cColTileAddressOffset := cfg.totalN.U * 2.U
}.elsewhen(DataFormat.is1B(io.regFile.cFmt)){
  calcConfig.cRowAddressOffset := io.regFile.n * 1.U
  calcConfig.cRowAddressOffset := io.regFile.n / 2.U
}

//A矩阵 scale 偏移计算
when(MXBlockSize.M64 === io.regFile.aMxBlockSize){
    calcConfig.scaleARowAddressOffset     := io.regFile.k / 64.U
    calcConfig.scaleAColTileAddressOffset := aKLoadNum / 64.U
}.
elsewhen(MXBlockSize.M32 === io.regFile.aMxBlockSize){
    calcConfig.scaleARowAddressOffset     := io.regFile.k / 32.U
    calcConfig.scaleAColTileAddressOffset := aKLoadNum / 32.U
}.
elsewhen(MXBlockSize.M16 === io.regFile.aMxBlockSize){
    calcConfig.scaleARowAddressOffset     := io.regFile.k / 16.U
    calcConfig.scaleAColTileAddressOffset := aKLoadNum / 16.U
}.
otherwise{
    calcConfig.scaleARowAddressOffset     := io.regFile.k / 8.U
    calcConfig.scaleAColTileAddressOffset := aKLoadNum / 8.U
}
//B矩阵 scale 偏移计算
when(MXBlockSize.M64 === io.regFile.bMxBlockSize){
    calcConfig.scaleBRowAddressOffset     := io.regFile.k / 64.U
    calcConfig.scaleBColTileAddressOffset := bKLoadNum / 64.U
}.
elsewhen(MXBlockSize.M32 === io.regFile.bMxBlockSize){
    calcConfig.scaleBRowAddressOffset     := io.regFile.k / 32.U
    calcConfig.scaleBColTileAddressOffset := bKLoadNum / 32.U
}.
elsewhen(MXBlockSize.M16 === io.regFile.bMxBlockSize){
    calcConfig.scaleBRowAddressOffset     := io.regFile.k / 16.U
    calcConfig.scaleBColTileAddressOffset := bKLoadNum / 16.U
}.
otherwise{
    calcConfig.scaleBRowAddressOffset     := io.regFile.k / 8.U
    calcConfig.scaleBColTileAddressOffset := bKLoadNum / 8.U
}
//C矩阵 scale 偏移计算 ： 当前只能支持16/8
when(MXBlockSize.M64 === io.regFile.cMxBlockSize){
    calcConfig.scaleCRowAddressOffset     := io.regFile.n / 64.U
    calcConfig.scaleCColTileAddressOffset := cfg.totalN.U / 64.U
}.
elsewhen(MXBlockSize.M32 === io.regFile.cMxBlockSize){
    calcConfig.scaleCRowAddressOffset     := io.regFile.n / 32.U
    calcConfig.scaleCColTileAddressOffset := cfg.totalN.U / 32.U
}.
elsewhen(MXBlockSize.M16 === io.regFile.cMxBlockSize){ //当前只支持16/8
    calcConfig.scaleCRowAddressOffset     := io.regFile.n / 16.U
    calcConfig.scaleCColTileAddressOffset := cfg.totalN.U / 16.U
}.
elsewhen(MXBlockSize.M8 === io.regFile.cMxBlockSize){
    calcConfig.scaleCRowAddressOffset     := io.regFile.n / 8.U
    calcConfig.scaleCColTileAddressOffset := cfg.totalN.U / 8.U
}


  val cWriteBytes = Wire(UInt((8.W)))
  val cDmaBytes = Wire(UInt((8.W)))
  cDmaBytes := cfg.cDmaExtW.U / 8.U
  
  when(DataFormat.is4B(io.regFile.cFmt)){
    cWriteBytes := 4.U * cfg.totalN.U
  }.elsewhen(DataFormat.is2B(io.regFile.cFmt)){
    cWriteBytes := 2.U * cfg.totalN.U
  }.elsewhen(DataFormat.is1B(io.regFile.cFmt)){
    cWriteBytes := 1.U * cfg.totalN.U
  }.otherwise{
    cWriteBytes := cfg.totalN.U / 2.U
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

}

io.startOut := state === sC1 && !err
  
  
}