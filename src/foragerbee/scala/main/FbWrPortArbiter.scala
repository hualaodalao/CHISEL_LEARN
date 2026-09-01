/** ForagerBee 写端口共享仲裁器。
  *
  * 允许多个引擎的写突发（burst）交织地分配到多个物理写端口上，
  * 提升写带宽利用率（仅在 `cfg.enableSharedWritePorts = true` 时例化；
  * 开关关闭时顶层保持每引擎 1:1 直连，本模块不生成）。
  *
  * 仲裁模型（阻塞式，`writeOutstandingDepth == 1`，现有路径逐字保持）：
  *   - 仲裁粒度 = 整个突发（SOF 到 wrResp 返回），突发中间不切换端口。
  *   - 每个物理端口维护 `owner`（当前占用引擎编号）与 `locked`（占用中）。
  *   - 引擎拉高 `wr.valid && wr.payload.sof` 时，按 round-robin 在
  *     `!locked` 的空闲端口中竞争一个端口；授权当拍即锁定（`owner := e`、
  *     `locked := true`），写数据直通选中端口。
  *   - 端口在 `wrResp` 返回（fire）后才释放锁定（即使 EOF 拍已完成也不
  *     立即释放，保证响应能正确路由回发起引擎）。
  *
  * 仲裁模型（非阻塞式，`writeOutstandingDepth > 1`）：
  *   - 数据通路锁（`locked`）= SOF → EOF：端口在写突发 EOF 拍即释放，
  *     同引擎下一突发可立即复用（锁当拍重新授予）。
  *   - 每端口新增归属 FIFO（深度 = writeOutstandingDepth）= SOF → wrResp：
  *     每发出一个写突发（SOF 授权拍）记录归属引擎，响应到达时按队首
  *     （head）路由回正确引擎后出队——即响应按突发发出顺序返回。
  *   - 协议假设：同一物理写端口的响应必须按突发发出顺序返回
  *     （FbWriteResp 无 id 字段）；乱序响应需未来在响应协议加 ID，
  *     本期以断言防护（归属 FIFO 空时不许收响应）。
  *   - 引擎归属绑定（`engHome`）：引擎有在途响应期间（按每引擎在途条目计数判定：
  *     任意端口归属 FIFO 入队该引擎 +1、出队 -1，计数归零才解绑），其新突发只会
  *     被授权到原归属端口，保证引擎侧响应单流保序（`engOwns` 语义保留：
  *     引擎占用期间不被其他端口抢占）。
  *
  * 数据通路为纯组合选择器（无数据 FIFO / 无缓冲）：
  *   - 端口 `wr.valid` = 锁定引擎（或当拍授权引擎）的 `wr.valid`
  *   - 引擎 `wr.ready` = 其占用/授权端口的 `wr.ready`（未被选中时为 0，产生反压）
  *   - 端口 `wrResp` 按 `owner`（depth=1）或归属 FIFO 队首（depth>1）转发给对应引擎
  *
  * 同一时刻一个端口只服务一个引擎；一个引擎至多占用一个端口
  * （引擎本身一次只发起一个突发，且请求条件中显式排除已占用端口的引擎）。
  */

import chisel3._
import chisel3.util._

class FbWrPortArbiter(cfg: ForagerBeeConfig) extends Module {
  val numEngines = cfg.numPorts // 引擎数 = 通道数
  val numPorts = cfg.numPorts   // 物理写端口数 = 引擎数
  val engW = math.max(1, log2Up(numEngines))

  val io = IO(new Bundle {

    /** 引擎写输出（numEngines 路，引擎 → 仲裁器） */
    val engWr = Vec(numEngines, slave(Stream(new FbWriteBeat(cfg.dataWidth, cfg.addressWidth))))

    /** 写响应回送（numEngines 路，仲裁器 → 引擎） */
    val engWrResp = Vec(numEngines, master(Stream(new FbWriteResp)))

    /** 物理写端口输出（numPorts 路，仲裁器 → 物理端口） */
    val portWr = Vec(numPorts, master(Stream(new FbWriteBeat(cfg.dataWidth, cfg.addressWidth))))

    /** 物理写端口响应（numPorts 路，物理端口 → 仲裁器） */
    val portWrResp = Vec(numPorts, slave(Stream(new FbWriteResp)))
  })

  if (cfg.writeOutstandingDepth == 1) {
    // ========================================================================
    // 阻塞式路径（现有逻辑逐字保持，零回归基准）
    // ========================================================================

    // --- 每端口锁定状态 ---
    val locked = Seq.fill(numPorts)(RegInit(false.B))
    val owner  = Seq.fill(numPorts)(RegInit(0.U(engW.W)))

    // --- 每端口 round-robin 指针：指向最近一次授权的引擎（下次从其下一位开始轮询） ---
    val rrLast = Seq.fill(numPorts)(RegInit((numEngines - 1).U(engW.W)))

    // --- 引擎占用视图：引擎 e 是否已被某端口锁定 ---
    val engOwns = Wire(Vec(numEngines, Bool()))
    for (e <- 0 until numEngines) {
      engOwns(e) := VecInit(locked.zip(owner).map { case (l, o) => l && o === e.U }).asUInt.orR
    }

    // --- 新突发请求：valid && sof 且当前未占用任何端口 ---
    val request = Wire(Vec(numEngines, Bool()))
    for (e <- 0 until numEngines) {
      request(e) := io.engWr(e).valid && io.engWr(e).payload.sof && !engOwns(e)
    }

    // --- 组合仲裁：端口顺序扫描，每个空闲端口对请求者做 round-robin ---
    // 已授权引擎通过 avail 链式掩码逐端口剔除（纯 DAG，无组合环），
    // 保证一个引擎一拍至多拿一个端口。已锁定端口不参与授权。
    val grantValid = Wire(Vec(numPorts, Bool()))
    val grantEng   = Wire(Vec(numPorts, UInt(engW.W)))
    var avail = (0 until numEngines).map(e => request(e): Bool)
    for (p <- 0 until numPorts) {
      val req     = VecInit(avail).asUInt
      val nextPri = Mux(rrLast(p) === (numEngines - 1).U, 0.U, rrLast(p) + 1.U)
      val grantOH = OHMasking.roundRobin(req, UIntToOH(nextPri))
      val hasGrant = req.orR && !locked(p)
      grantValid(p) := hasGrant
      grantEng(p)   := OHToUInt(grantOH)
      // 本端口授权的引擎在后续端口的竞争中剔除（未授权时掩码不变）
      avail = avail.zipWithIndex.map { case (r, e) => r && !(hasGrant && grantOH(e)) }
    }

    // --- 锁定更新：授权当拍锁定端口；仅当端口 wrResp 返回后释放 ---
    for (p <- 0 until numPorts) {
      when(grantValid(p)) {
        locked(p) := true.B
        owner(p)  := grantEng(p)
        rrLast(p) := grantEng(p)
      }
      when(io.portWrResp(p).fire) {
        locked(p) := false.B
      }
    }

    // --- 写数据通路：引擎 → 物理端口（纯组合选择器，无缓冲） ---
    for (p <- 0 until numPorts) {
      val selEng = Mux(locked(p), owner(p), grantEng(p))
      io.portWr(p).valid   := Mux(locked(p), io.engWr(owner(p)).valid, grantValid(p))
      io.portWr(p).payload := io.engWr(selEng).payload
    }

    // --- 引擎侧写反压：取被选中端口的 ready，未被选中时为 0 ---
    for (e <- 0 until numEngines) {
      val sel = VecInit((0 until numPorts).map { p =>
        (locked(p) && owner(p) === e.U) || (grantValid(p) && grantEng(p) === e.U)
      })
      io.engWr(e).ready := Mux1H(sel.asUInt, io.portWr.map(_.ready))
    }

    // --- wrResp 路由：端口 p 的响应按 owner(p) 转发给对应引擎 ---
    for (e <- 0 until numEngines) {
      val selOH = VecInit((0 until numPorts).map(p => locked(p) && owner(p) === e.U))
      io.engWrResp(e).valid := VecInit((0 until numPorts).map { p =>
        io.portWrResp(p).valid && locked(p) && owner(p) === e.U
      }).asUInt.orR
      io.engWrResp(e).payload := Mux1H(selOH.asUInt, io.portWrResp.map(_.payload))
    }
    for (p <- 0 until numPorts) {
      io.portWrResp(p).ready := locked(p) && io.engWrResp(owner(p)).ready
    }

    // --- 验证断言：端口未锁定时不允许收到写响应（下游协议违例保护，不改变综合逻辑） ---
    for (p <- 0 until numPorts) {
      assert(!(io.portWrResp(p).valid && !locked(p)),
        s"FbWrPortArbiter: port $p wrResp while unlocked")
    }
  } else {
    // ========================================================================
    // 非阻塞路径（writeOutstandingDepth > 1）：
    //   锁 = SOF→EOF；归属 FIFO = SOF→wrResp，响应按队首路由（顺序响应假设）
    // ========================================================================

    // --- 每端口锁定状态（锁区间缩短为 SOF→EOF） ---
    val locked = Seq.fill(numPorts)(RegInit(false.B))
    val owner  = Seq.fill(numPorts)(RegInit(0.U(engW.W)))

    // --- 每端口 round-robin 指针：指向最近一次授权的引擎（下次从其下一位开始轮询） ---
    val rrLast = Seq.fill(numPorts)(RegInit((numEngines - 1).U(engW.W)))

    // --- 每端口归属 FIFO：SOF 授权拍入队、响应收回出队，队首即最老在途突发的归属引擎 ---
    val ownerFifo = Seq.fill(numPorts)(Module(new Queue(UInt(engW.W), cfg.writeOutstandingDepth)))

    // --- 引擎在途条目计数：任意端口归属 FIFO 入队该引擎 +1、出队 -1；
    //     计数 > 0 期间绑定原归属端口（保证引擎侧响应单流保序），归零才解绑 ---
    val engCntW = log2Up(cfg.writeOutstandingDepth * numPorts + 1)
    val engInFlight = Seq.fill(numEngines)(RegInit(0.U(engCntW.W)))
    val engHome     = Reg(Vec(numEngines, UInt(engW.W)))

    // --- 引擎占用视图：引擎 e 是否已被某端口锁定（语义保留：占用期间不抢其他端口） ---
    val engOwns = Wire(Vec(numEngines, Bool()))
    for (e <- 0 until numEngines) {
      engOwns(e) := VecInit(locked.zip(owner).map { case (l, o) => l && o === e.U }).asUInt.orR
    }

    // --- 新突发请求：valid && sof 且当前未被锁定 ---
    val request = Wire(Vec(numEngines, Bool()))
    for (e <- 0 until numEngines) {
      request(e) := io.engWr(e).valid && io.engWr(e).payload.sof && !engOwns(e)
    }

    // --- 组合仲裁：端口顺序扫描，每个空闲端口对请求者做 round-robin ---
    // 端口 p 额外资格掩码：有在途响应的引擎只能被授权回其归属端口（复用原端口）。
    // 已授权引擎通过 avail 链式掩码逐端口剔除（纯 DAG，无组合环），
    // 保证一个引擎一拍至多拿一个端口。已锁定/归属 FIFO 满的端口不参与授权。
    val grantValid = Wire(Vec(numPorts, Bool()))
    val grantEng   = Wire(Vec(numPorts, UInt(engW.W)))
    var avail = (0 until numEngines).map(e => request(e): Bool)
    for (p <- 0 until numPorts) {
      val req     = VecInit(avail.zipWithIndex.map { case (r, e) =>
        r && (engInFlight(e) === 0.U || engHome(e) === p.U)
      }).asUInt
      val nextPri = Mux(rrLast(p) === (numEngines - 1).U, 0.U, rrLast(p) + 1.U)
      val grantOH = OHMasking.roundRobin(req, UIntToOH(nextPri))
      val hasGrant = req.orR && !locked(p) && ownerFifo(p).io.enq.ready
      grantValid(p) := hasGrant
      grantEng(p)   := OHToUInt(grantOH)
      // 本端口授权的引擎在后续端口的竞争中剔除（未授权时掩码不变）
      avail = avail.zipWithIndex.map { case (r, e) => r && !(hasGrant && grantOH(e)) }
    }

    // --- 锁定更新：授权当拍锁定端口；写突发 EOF 拍即释放（不等响应） ---
    for (p <- 0 until numPorts) {
      when(grantValid(p)) {
        locked(p) := true.B
        owner(p)  := grantEng(p)
        rrLast(p) := grantEng(p)
      }
      when(io.portWr(p).fire && io.portWr(p).payload.eof) {
        locked(p) := false.B
      }
    }

    // --- 归属 FIFO 维护：SOF 拍记录归属；响应收回出队 ---
    for (p <- 0 until numPorts) {
      ownerFifo(p).io.enq.valid := io.portWr(p).fire && io.portWr(p).payload.sof
      ownerFifo(p).io.enq.bits  := Mux(locked(p), owner(p), grantEng(p))
      ownerFifo(p).io.deq.ready := io.portWrResp(p).fire
    }

    // --- 引擎在途条目计数维护：入队该引擎 +1、出队 -1，同拍抵消；
    //     归属端口（engHome）在实际入队握手时登记。计数归零自然解绑，
    //     避免仅在“恰为队尾出队”时清除导致引擎被长期钉在原端口。
    //     （归属绑定保证同一引擎的在途条目只存在于一个端口，
    //       且每拍每引擎至多一笔入队/一笔出队，计数不会下溢） ---
    for (e <- 0 until numEngines) {
      val enqHits = (0 until numPorts).map { p =>
        ownerFifo(p).io.enq.fire && ownerFifo(p).io.enq.bits === e.U
      }
      val deqHits = (0 until numPorts).map { p =>
        ownerFifo(p).io.deq.fire && ownerFifo(p).io.deq.bits === e.U
      }
      val enqN = PopCount(VecInit(enqHits))
      val deqN = PopCount(VecInit(deqHits))
      engInFlight(e) := (engInFlight(e) +& enqN -& deqN)(engCntW - 1, 0)
      // 归属端口在实际入队握手时登记（取命中的端口；每拍每引擎至多一笔入队）
      when(VecInit(enqHits).asUInt.orR) {
        engHome(e) := PriorityMux(enqHits.zipWithIndex.map { case (h, p) => (h, p.U(engW.W)) })
      }
    }

    // --- 写数据通路：引擎 → 物理端口（纯组合选择器，无缓冲） ---
    for (p <- 0 until numPorts) {
      val selEng = Mux(locked(p), owner(p), grantEng(p))
      io.portWr(p).valid   := Mux(locked(p), io.engWr(owner(p)).valid, grantValid(p))
      io.portWr(p).payload := io.engWr(selEng).payload
    }

    // --- 引擎侧写反压：取被选中端口的 ready，未被选中时为 0 ---
    for (e <- 0 until numEngines) {
      val sel = VecInit((0 until numPorts).map { p =>
        (locked(p) && owner(p) === e.U) || (grantValid(p) && grantEng(p) === e.U)
      })
      io.engWr(e).ready := Mux1H(sel.asUInt, io.portWr.map(_.ready))
    }

    // --- wrResp 路由：端口 p 的响应按归属 FIFO 队首转发给对应引擎 ---
    // （引擎归属绑定保证同一引擎的在途响应只存在于一个端口，OR/Mux1H 至多一项有效）
    for (e <- 0 until numEngines) {
      val selOH = VecInit((0 until numPorts).map { p =>
        io.portWrResp(p).valid && ownerFifo(p).io.deq.valid && ownerFifo(p).io.deq.bits === e.U
      })
      io.engWrResp(e).valid := selOH.asUInt.orR
      io.engWrResp(e).payload := Mux1H(selOH.asUInt, io.portWrResp.map(_.payload))
    }
    for (p <- 0 until numPorts) {
      io.portWrResp(p).ready := ownerFifo(p).io.deq.valid &&
        io.engWrResp(ownerFifo(p).io.deq.bits).ready
    }

    // --- 验证断言：归属 FIFO 为空时不允许收到写响应（顺序响应协议违例保护，不改变综合逻辑） ---
    for (p <- 0 until numPorts) {
      assert(!(io.portWrResp(p).valid && !ownerFifo(p).io.deq.valid),
        s"FbWrPortArbiter: port $p wrResp while owner FIFO empty")
    }
  }
}
