
//adapter for queen cmdq mode
import chisel3._
import chisel3.util._

/*

class HiveQueenAdapter(config: HiveConfig, qconfig: QueenConfig, fifoDepth: Int = 8) extends Module {
    val io = IO(new Bundle {
        val queenIf = Flipped(new QueenCpuInterface(qconfig))
        val hiveReqIf  = master(Stream(new HiveCoreCmd))
        val hiveRespIf = slave(Stream(new HiveCoreResp))
        val error = input(Bool())
        val donePluse = input(Bool())
        val tracingIf = output(new HiveCoreRespForTracing)
    })

    
    val queenQueue = Module(new StreamFifo(new QueenCpuReq(qconfig), fifoDepth))
    val qDecoder = Module(new QueenCpuDecode(qconfig))
    qDecoder.cpuReq := queenQueue.io.pop.payload

    queenQueue.io.push.valid := io.queenIf.req.valid
    queenQueue.io.push.payload := io.queenIf.req.bits
    io.queenIf.req.ready := queenQueue.io.push.ready
    
    
    io.hiveReqIf.ready := queenQueue.io.push.ready

    val busy = RegInit(false.B)    
    
    
    queenQueue.io.pop.ready := !busy
    hiveReqIf.valid := false.B
    hiveReqIf.bits := 0
    

    
    switch(state) {
        is(sIDLE) {
            when(queenQueue.io.pop.fire & (qPayload.funct3 === 0.U || qPayload.funct3 === 1.U)){
                hiveReqIf.valid := true.B
                hiveReqIf.op := REG_WRITE2
                hiveReqIf.reg0Addr := qPayload.rs1Addr
                hiveReqIf.reg1Addr := qPayload.rs2Addr
                hiveReqIf.data0 := qPayload.rs1
                hiveReqIf.data1 := qPayload.rs2
                when(qPayload.rs1(31,28) === 0.U){
                    popCnt :=  4
                } otherwise {
                    popCnt :=  3
                }
             
                state := sPop
            }
        }
        is(sMacroMx) {
            when(queenQueue.io.pop.fire) {
                popCnt := popCnt - 1.U
                when(popCnt === 0.U) {
                    state := sTrigger
                }
            }
        }
        is(sTrigger) {
            when(queenQueue.io.pop.fire) {
                popCnt := popCnt - 1.U
                when(popCnt === 0.U) {
                    state := sTrigger
                }
            }        
        }
    }



}*/