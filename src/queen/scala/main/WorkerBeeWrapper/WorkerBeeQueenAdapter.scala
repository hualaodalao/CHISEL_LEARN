
//adapter for queen cmdq mode
import chisel3._
import chisel3.util._

/*

class WorkerBeeQueenAdapter(config: WbConfig, qconfig: QueenConfig, fifoDepth: Int = 8) extends Module {
    val io = IO(new Bundle {
        val queenIf = Flipped(new QueenCpuInterface(qconfig))
        val wbReqIf  = master(Stream(new WorkerBeeCmd))
        val wbRespIf = slave(Stream(new WorkerBeeResp))
        val error = input(Bool())
        val donePluse = input(Bool())
        val tracingIf = output(new WorkerBeeRespForTracing)
    })

    
    val queenQueue = Module(new StreamFifo(new QueenCpuReq(qconfig), fifoDepth))
    val qDecoder = Module(new QueenCpuDecode(qconfig))
    qDecoder.cpuReq := queenQueue.io.pop.payload

    queenQueue.io.push.valid := io.queenIf.req.valid
    queenQueue.io.push.payload := io.queenIf.req.bits
    io.queenIf.req.ready := queenQueue.io.push.ready
    
    
    io.wbReqIf.ready := queenQueue.io.push.ready

    val busy = RegInit(false.B)    
    
    
    queenQueue.io.pop.ready := !busy
    wbReqIf.valid := false.B
    wbReqIf.bits := 0
    

    
    switch(state) {
        is(sIDLE) {
            when(queenQueue.io.pop.fire & (qPayload.funct3 === 0.U || qPayload.funct3 === 1.U)){
                wbReqIf.valid := true.B
                wbReqIf.op := REG_WRITE2
                wbReqIf.reg0Addr := qPayload.rs1Addr
                wbReqIf.reg1Addr := qPayload.rs2Addr
                wbReqIf.data0 := qPayload.rs1
                wbReqIf.data1 := qPayload.rs2
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