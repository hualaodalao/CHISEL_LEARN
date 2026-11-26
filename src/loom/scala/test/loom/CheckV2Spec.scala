package loom

import java.nio.file.{Files, Paths}

import loom.check.ChecksV2
import loom.chi.ChiWidths
import loom.ir._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** S2 gates: CHK-01–CHK-34 + CHK-40–CHK-43 (ir_gen_spec §6).
  * Every CHK id has a negative case asserting the exact §6.2 message template
  * and a positive side. Baseline inputs are mutations of examples/ring_4.json
  * (appendix A) plus the §6.3–§6.9 fragments.
  */
class CheckV2Spec extends AnyFlatSpec with Matchers {
  private val v2Path = Paths.get("src/loom/examples/ring_4.json")
  private def base: IrV2 = IrV2.load(v2Path)
  private def v2Text: String = Files.readString(v2Path)

  private def withNode(id: String)(f: NodeV2 => NodeV2): IrV2 = {
    val v = base
    v.copy(nodes = v.nodes.map(n => if (n.id == id) f(n) else n))
  }
  private def withChi(f: ChiBlockV2 => ChiBlockV2): IrV2 = {
    val v = base
    v.copy(chi = f(v.chi))
  }
  private def withLink(from: String, to: String)(f: LinkV2 => LinkV2): IrV2 = {
    val v = base
    v.copy(links = v.links.map(l => if (l.from == from && l.to == to) f(l) else l))
  }
  private def withClasses(f: QosClassV2 => QosClassV2): IrV2 = {
    val v = base
    v.copy(qos = v.qos.copy(classes = v.qos.classes.map(f)))
  }
  private def withMapping(ms: Seq[QosMappingV2]): IrV2 = {
    val v = base
    v.copy(qos = v.qos.copy(mapping = ms))
  }

  behavior of "ChecksV2 C0 (CHK-01–CHK-23)"

  it should "accept the ring_4 v2 baseline with zero C0 errors (P-C0, CHK-01–23 positive)" in {
    ChecksV2.runC0(base) shouldBe Nil
  }

  it should "reject a foreign schema with CHK-01" in {
    val v = base
    val bad = v.copy(meta = v.meta.copy(schema = "loom/v3"))
    ChecksV2.runC0(bad) should contain(
      "C0: [CHK-01] schema must be 'loom/v2' (or 'loom/v1' for upgrade), got 'loom/v3'")
  }

  it should "reject an illegal meta.name with CHK-02" in {
    val v = base
    val bad = v.copy(meta = v.meta.copy(name = "bad name!"))
    ChecksV2.runC0(bad) should contain(
      "C0: [CHK-02] meta.name 'bad name!' must match [A-Za-z0-9_.-]+")
  }

  it should "reject duplicate clockDomain ids and non-positive freqMHz with CHK-03" in {
    val v = base
    val dup = v.copy(clockDomains = v.clockDomains :+ ClockDomain("core", 1000))
    ChecksV2.runC0(dup) should contain("C0: [CHK-03] clockDomain 'core' duplicate id")
    val zero = v.copy(clockDomains = v.clockDomains.map(c => c.copy(freqMHz = 0)))
    ChecksV2.runC0(zero) should contain("C0: [CHK-03] clockDomain 'core' freqMHz must be > 0")
  }

  it should "reject duplicate or colliding node/router names with CHK-04" in {
    ChecksV2.runC0(withNode("m1")(_.copy(id = "m0"))) should contain(
      "C0: [CHK-04] duplicate node/router name 'm0'")
    ChecksV2.runC0(withNode("m1")(_.copy(id = "r0"))) should contain(
      "C0: [CHK-04] duplicate node/router name 'r0'")
    ChecksV2.runC0(withNode("m1")(_.copy(id = "bad id!"))) should contain(
      "C0: [CHK-04] node/router name 'bad id!' must match [A-Za-z0-9_.-]+")
  }

  it should "reject unknown kinds and the hn-f placeholder with CHK-05" in {
    ChecksV2.runC0(withNode("m0")(_.copy(kind = "hn-f"))) should contain(
      "C0: [CHK-05] node 'm0' kind 'hn-f' invalid; hn-f is a reserved placeholder")
    ChecksV2.runC0(withNode("m0")(_.copy(kind = "foo"))) should contain(
      "C0: [CHK-05] node 'm0' kind 'foo' invalid; hn-f is a reserved placeholder")
  }

  it should "reject out-of-range and duplicate explicit NodeIDs with CHK-06 (N-C0-b)" in {
    ChecksV2.runC0(withNode("m0")(_.copy(nodeId = Some(130)))) should contain(
      "C0: [CHK-06] node 'm0' nodeId 130 out of range [0,127]")
    val v = base
    val dup = v.copy(nodes = v.nodes.map(n =>
      if (n.id == "m0" || n.id == "m1") n.copy(nodeId = Some(5)) else n))
    ChecksV2.runC0(dup) should contain("C0: [CHK-06] node 'm1' duplicate nodeId 5")
    ChecksV2.runC0(withNode("m0")(_.copy(nodeId = Some(-1)))) should contain(
      "C0: [CHK-06] node 'm0' nodeId -1 out of range [0,127]")
  }

  it should "accept mixed explicit/auto NodeIDs skipping reserved ids (SEM-03 positive)" in {
    val v = withNode("m0")(_.copy(nodeId = Some(1)))
    ChecksV2.runC0(v) shouldBe Nil
    // m1 must not be auto-assigned the reserved 1; elaboration stays consistent.
    val ids = loom.gen.ChiAssemble.chiParams(v).rnNodes.map(n => (n.id, n.nodeId))
    ids shouldBe Seq(("m0", 1), ("m1", 0))
  }

  it should "reject an undeclared node clock with CHK-07" in {
    ChecksV2.runC0(withNode("m0")(_.copy(clock = "ghost"))) should contain(
      "C0: [CHK-07] node 'm0' clock 'ghost' is not declared")
  }

  it should "reject a retired node protocol with CHK-08 (N-C0-c)" in {
    ChecksV2.runC0(withNode("m0")(_.copy(protocol = "native"))) should contain(
      "C0: [CHK-08] node 'm0' protocol 'native' must be axi4 or chi")
  }

  it should "reject an off-tier node dataWidth with CHK-09" in {
    ChecksV2.runC0(withNode("m0")(_.copy(dataWidth = 100))) should contain(
      "C0: [CHK-09] node 'm0' dataWidth 100 must be one of 32,64,128,256,512,1024")
    ChecksV2.runC0(withNode("m0")(_.copy(dataWidth = 256))) shouldBe Nil
  }

  it should "reject node outstanding beyond the TxnID space with CHK-10" in {
    ChecksV2.runC0(withNode("m0")(_.copy(outstanding = 300))) should contain(
      "C0: [CHK-10] node 'm0' outstanding 300 must be in [1,256]")
    ChecksV2.runC0(withNode("m0")(_.copy(outstanding = 256))) shouldBe Nil
  }

  it should "reject node qos outside [0,15] with CHK-11" in {
    ChecksV2.runC0(withNode("m0")(_.copy(qos = 16))) should contain(
      "C0: [CHK-11] node 'm0' qos 16 must be in [0,15]")
  }

  it should "reject an attach to an undeclared router with CHK-12" in {
    ChecksV2.runC0(withNode("m0")(_.copy(attach = "rX"))) should contain(
      "C0: [CHK-12] node 'm0' attach 'rX' is not a declared router")
  }

  it should "reject off-tier chi widths with CHK-13 (N-C0-c, ENC golden anchor)" in {
    val bad = withChi(_.copy(datWidth = 64, txnIdWidth = 10, nodeIdWidth = 9, addrWidth = 64))
    val errs = ChecksV2.runC0(bad)
    errs should contain("C0: [CHK-13] chi.datWidth must be one of 128,256,512, got 64")
    errs should contain("C0: [CHK-13] chi.txnIdWidth must be one of 8,12, got 10")
    errs should contain("C0: [CHK-13] chi.nodeIdWidth must be one of 7,11, got 9")
    errs should contain("C0: [CHK-13] chi.addrWidth must be one of 32,40,48, got 64")
    // positive: every legal tier parses without CHK-13
    Seq(128, 256, 512).foreach(w =>
      ChecksV2.runC0(withChi(_.copy(datWidth = w))).filter(_.contains("[CHK-13]")) shouldBe Nil)
  }

  it should "reject illegal chi scalars with CHK-14 (N-C0-c)" in {
    val bad = withChi(_.copy(outstanding = 0, atomicEntries = 0, pcrdTypes = 17, lcreditDepth = 16))
    val errs = ChecksV2.runC0(bad)
    errs should contain("C0: [CHK-14] chi.outstanding must be in [1,inf), got 0")
    errs should contain("C0: [CHK-14] chi.atomicEntries must be in [1,inf), got 0")
    errs should contain("C0: [CHK-14] chi.pcrdTypes must be in [1,16], got 17")
    errs should contain("C0: [CHK-14] chi.lcreditDepth must be in [1,15], got 16")
    ChecksV2.runC0(withChi(_.copy(pcrdTypes = 16, lcreditDepth = 15))) shouldBe Nil
  }

  it should "reject an unknown chi.portMode with CHK-15" in {
    ChecksV2.runC0(withChi(_.copy(portMode = "tlul"))) should contain(
      "C0: [CHK-15] chi.portMode 'tlul' unknown")
  }

  it should "reject broken SAM window math with CHK-16 (N-C0-d)" in {
    val v = base
    val bad = v.copy(sam = Seq(SamWindowV2(2048L, 3000L, "s0"), SamWindowV2(4096L, 4096L, "s1")))
    val errs = ChecksV2.runC0(bad)
    errs should contain("C0: [CHK-16] sam entry 's0' size 3000 must be a power of two")
    errs should contain("C0: [CHK-16] sam entry 's0' size 3000 must be >= 4096")
    errs should contain("C0: [CHK-16] sam entry 's0' base 2048 must be aligned to size 3000")
    // capacity: addrWidth=32, base=4294963200 size=8192 (spec §6.3 d)
    val over = v.copy(sam = Seq(SamWindowV2(4294963200L, 8192L, "s0"), SamWindowV2(4096L, 4096L, "s1")))
    ChecksV2.runC0(over) should contain(
      "C0: [CHK-16] sam entry 's0' base 4294963200 + size 8192 exceeds addrWidth=32")
  }

  it should "reject a non-subordinate sam tgtNode with CHK-17 (N-C0-d)" in {
    val v = base
    val bad = v.copy(sam = v.sam.map(w => if (w.tgtNode == "s0") w.copy(tgtNode = "m0") else w))
    ChecksV2.runC0(bad) should contain("C0: [CHK-17] sam tgtNode 'm0' is not a subordinate node")
    val ghost = v.copy(sam = v.sam.map(w => if (w.tgtNode == "s0") w.copy(tgtNode = "ghost") else w))
    ChecksV2.runC0(ghost) should contain("C0: [CHK-17] sam tgtNode 'ghost' is not a subordinate node")
  }

  it should "reject illegal channel sets with CHK-18 (N-C0-e)" in {
    val errs = ChecksV2.runC0(withLink("r0", "r1")(_.copy(channels = Seq("req", "snp", "snp"))))
    errs should contain("C0: [CHK-18] link 'r0->r1' channels duplicate channel 'snp'")
    errs should contain("C0: [CHK-18] link 'r0->r1' channels missing required channel 'rsp'")
    errs should contain("C0: [CHK-18] link 'r0->r1' channels missing required channel 'dat'")
    ChecksV2.runC0(withLink("r0", "r1")(_.copy(channels = Seq("req", "rsp", "dat", "foo")))) should contain(
      "C0: [CHK-18] link 'r0->r1' channels unknown channel 'foo'")
    ChecksV2.runC0(withLink("r0", "r1")(_.copy(channels = Nil))) should contain(
      "C0: [CHK-18] link 'r0->r1' channels must not be empty")
  }

  it should "tie snp/rn-d/mn to dvmEnable with CHK-19 (SEM-07)" in {
    val rnD = withNode("m0")(_.copy(kind = "rn-d"))
    ChecksV2.runC0(rnD) should contain("C0: [CHK-19] snp/rn-d/mn requires chi.dvmEnable=true")
    val snpLink = withLink("r0", "r1")(_.copy(channels = Seq("req", "rsp", "dat", "snp")))
    ChecksV2.runC0(snpLink) should contain("C0: [CHK-19] snp/rn-d/mn requires chi.dvmEnable=true")
    // positive: rn-d + snp channels + exactly one mn with dvmEnable=true
    val ok = rnD.copy(chi = rnD.chi.copy(dvmEnable = true),
      nodes = rnD.nodes :+ NodeV2("mn0", "mn", None, "core", "r0", "chi", 256, 64, 0))
    val ok2 = ok.copy(links = ok.links.map(l =>
      if (l.from == "r0" && l.to == "r1") l.copy(channels = Seq("req", "rsp", "dat", "snp")) else l))
    ChecksV2.runC0(ok2) shouldBe Nil
  }

  it should "reject illegal link parameters with CHK-20 (N-C0-e)" in {
    val errs = ChecksV2.runC0(withLink("r0", "r1")(_.copy(
      width = 128, pipe = 9, cdc = "async", asyncFifoDepth = 1, lcreditDepth = 0)))
    errs should contain("C0: [CHK-20] link 'r0->r1' width 128 must equal chi.datWidth 256")
    errs should contain("C0: [CHK-20] link 'r0->r1' pipe 9 must be in [0,8]")
    errs should contain("C0: [CHK-20] link 'r0->r1' asyncFifoDepth 1 must be >= 2 for cdc async")
    errs should contain("C0: [CHK-20] link 'r0->r1' lcreditDepth 0 must be in [1,15]")
    ChecksV2.runC0(withLink("r0", "r1")(_.copy(width = 100))) should contain(
      "C0: [CHK-20] link 'r0->r1' width 100 must be one of 128,256,512")
    ChecksV2.runC0(withLink("r0", "r1")(_.copy(cdc = "weird"))) should contain(
      "C0: [CHK-20] link 'r0->r1' cdc 'weird' must be sync or async")
    // positive: async with fifo >= 2 across domains is C0-legal
    ChecksV2.runC0(withLink("r0", "r1")(_.copy(cdc = "async", asyncFifoDepth = 2))) shouldBe Nil
  }

  it should "reject the retired direction key with CHK-20 (MIG-06)" in {
    val bad = IrV2.fromJson(Json.parse(v2Text.replaceFirst(
      "\"from\": \"r0\", \"to\": \"r1\"",
      "\"from\": \"r0\", \"to\": \"r1\", \"direction\": \"forward\"")))
    ChecksV2.runC0(bad) should contain(
      "C0: [CHK-20] link 'r0->r1' direction is retired (MIG-06); CHI links are always bidirectional")
  }

  it should "reject illegal router declarations with CHK-21 (N-C0-f)" in {
    val v = base
    def withR0(f: RouterV2 => RouterV2): IrV2 =
      v.copy(routers = v.routers.map(r => if (r.name == "r0") f(r) else r))
    ChecksV2.runC0(withR0(_.copy(in = Some(0)))) should contain(
      "C0: [CHK-21] router 'r0' in 0 must be in [1,64]")
    ChecksV2.runC0(withR0(_.copy(out = Some(65)))) should contain(
      "C0: [CHK-21] router 'r0' out 65 must be in [1,64]")
    ChecksV2.runC0(withR0(_.copy(clock = "ghost"))) should contain(
      "C0: [CHK-21] router 'r0' clock 'ghost' is not declared")
    ChecksV2.runC0(withR0(_.copy(routing = "foo"))) should contain(
      "C0: [CHK-21] router 'r0' routing 'foo' must be any or xy")
    ChecksV2.runC0(withR0(_.copy(routing = "xy"))) should contain(
      "C0: [CHK-21] router 'r0' xy routing requires pos {x,y}")
  }

  it should "reject the retired switch block with CHK-21 (MIG-06)" in {
    val bad = IrV2.fromJson(Json.parse(v2Text.replaceFirst(
      "\"name\": \"r0\", \"clock\": \"core\"",
      "\"name\": \"r0\", \"clock\": \"core\", \"switch\": { \"kind\": \"flat\", \"banks\": 1 }")))
    ChecksV2.runC0(bad) should contain(
      "C0: [CHK-21] router 'r0' switch block is retired (MIG-06); CHI router is flat")
  }

  it should "check Manhattan adjacency for xy routing with CHK-21" in {
    val v = base
    val bad = v.copy(routers = v.routers.map { r =>
      if (r.name == "r0") r.copy(routing = "xy", pos = Some((0, 0)))
      else if (r.name == "r1") r.copy(routing = "xy", pos = Some((2, 0)))
      else r
    })
    ChecksV2.runC0(bad) should contain(
      "C0: [CHK-21] router 'r0' xy mesh link 'r0->r1' must connect Manhattan neighbors, got distance 2")
    // positive: unit square ring, every router link is a Manhattan neighbor
    val posOf = Map("r0" -> (0, 0), "r1" -> (1, 0), "r2" -> (1, 1), "r3" -> (0, 1))
    val ok = v.copy(routers = v.routers.map(r =>
      r.copy(routing = "xy", pos = Some(posOf(r.name)))))
    ChecksV2.runC0(ok) shouldBe Nil
  }

  it should "reject out-of-range timing budgets with CHK-22 (N-C0-f)" in {
    val v = base
    val bad = v.copy(timing = TimingSpec(65, 0))
    val errs = ChecksV2.runC0(bad)
    errs should contain("C0: [CHK-22] fabric.timing.maxFanin must be in [1,64]")
    errs should contain("C0: [CHK-22] fabric.timing.maxCombDepth must be in [1,256]")
  }

  it should "require at least one RN and one SN with CHK-23 (N-C0-b)" in {
    val v = base
    val noSn = v.copy(nodes = v.nodes.map(n =>
      if (n.kind == "sn-f") n.copy(kind = "rn-i") else n))
    ChecksV2.runC0(noSn) should contain(
      "C0: [CHK-23] at least one request node and one subordinate node are required")
    val noRn = v.copy(nodes = v.nodes.map(n =>
      if (n.kind == "rn-i") n.copy(kind = "sn-f") else n))
    ChecksV2.runC0(noRn) should contain(
      "C0: [CHK-23] at least one request node and one subordinate node are required")
  }

  it should "keep unknown keys as C0 errors (spec §3, S1 behavior)" in {
    val bad = IrV2.fromJson(Json.parse(v2Text.replaceFirst("\"meta\"", "\"bogus\": 1, \"meta\"")))
    ChecksV2.runC0(bad) should contain("C0: unknown field '$.bogus'")
  }

  behavior of "ChecksV2 C1 (CHK-24–CHK-31)"

  it should "accept the baseline graph with zero C1 errors (P-C1)" in {
    ChecksV2.runC1(base) shouldBe Nil
  }

  it should "reject unknown endpoints and illegal node direction with CHK-24 (N-C1-a)" in {
    val v = base
    val ghost = v.copy(links = v.links :+ v.links.head.copy(from = "r0", to = "ghost"))
    ChecksV2.runC1(ghost) should contain("C1: [CHK-24] link 'r0->ghost' unknown endpoint 'ghost'")
    val rnInTo = v.copy(links = v.links :+ v.links.head.copy(from = "r0", to = "m0"))
    ChecksV2.runC1(rnInTo) should contain(
      "C1: [CHK-24] link 'r0->m0' request node 'm0' must not appear as link target")
    val snInFrom = v.copy(links = v.links :+ v.links.head.copy(from = "s0", to = "r2"))
    ChecksV2.runC1(snInFrom) should contain(
      "C1: [CHK-24] link 's0->r2' subordinate node 's0' must not appear as link source")
  }

  it should "reject an access edge away from the attach router with CHK-24/26 (N-C1-a)" in {
    val bad = withNode("m0")(_.copy(attach = "r1"))
    val errs = ChecksV2.runC1(bad)
    errs should contain("C1: [CHK-24] link 'm0->r0' node 'm0' attaches to 'r1', not 'r0'")
    errs should contain("C1: [CHK-26] node 'm0' has no attachment link to router 'r1'")
  }

  it should "reject duplicate (from,to) links with CHK-25 (N-C1-a)" in {
    val v = base
    val dup = v.copy(links = v.links :+ v.links.head)
    ChecksV2.runC1(dup) should contain("C1: [CHK-25] duplicate link 'm0' -> 'r0'")
  }

  it should "reject a node without its attachment link with CHK-26 (N-C1-a)" in {
    val v = base
    val bad = v.copy(links = v.links.filterNot(l => l.from == "m0" && l.to == "r0"))
    ChecksV2.runC1(bad) should contain(
      "C1: [CHK-26] node 'm0' has no attachment link to router 'r0'")
  }

  it should "reject an unreferenced router with CHK-27 (N-C1-a)" in {
    val v = base
    val bad = v.copy(routers = v.routers :+ RouterV2("rX", "core", None, None, None, "any"))
    ChecksV2.runC1(bad) should contain("C1: [CHK-27] router 'rX' is not referenced by any link")
  }

  it should "reject a sync link across clock domains with CHK-28 (N-C1-b)" in {
    val v = base
    val bad = v.copy(
      clockDomains = v.clockDomains :+ ClockDomain("slow", 600),
      routers = v.routers.map(r => if (r.name == "r1") r.copy(clock = "slow") else r))
    ChecksV2.runC1(bad) should contain(
      "C1: [CHK-28] sync link 'r0'(core) -> 'r1'(slow) crosses clock domains")
  }

  it should "reject overlapping sam windows with CHK-29 (N-C1-b)" in {
    val v = base
    val bad = v.copy(sam = Seq(SamWindowV2(0L, 8192L, "s0"), SamWindowV2(4096L, 4096L, "s0")))
    ChecksV2.runC1(bad) should contain("C1: [CHK-29] sam windows of 's0' and 's0' overlap")
  }

  it should "accept multiple non-overlapping windows per SN (P-C1 multi-window)" in {
    val v = base
    val ok = v.copy(sam = Seq(
      SamWindowV2(0L, 4096L, "s0"), SamWindowV2(65536L, 4096L, "s0"),
      SamWindowV2(4096L, 4096L, "s1")))
    ChecksV2.runC0(ok) shouldBe Nil
    ChecksV2.runC1(ok) shouldBe Nil
  }

  it should "reject an SN without any sam window with CHK-30 (N-C1-b)" in {
    val v = base
    val bad = v.copy(sam = v.sam.filterNot(_.tgtNode == "s1"))
    ChecksV2.runC1(bad) should contain("C1: [CHK-30] subordinate node 's1' has no SAM window")
  }

  it should "compare declared router in/out against derived ports with CHK-31 (N-C1-a)" in {
    val v = base
    def withR0(f: RouterV2 => RouterV2): IrV2 =
      v.copy(routers = v.routers.map(r => if (r.name == "r0") f(r) else r))
    // r0: nLocal=1 (m0) + r3->r0 in + r0->r1 out => derived in=out=2
    ChecksV2.runC1(withR0(_.copy(in = Some(5)))) should contain(
      "C1: [CHK-31] router 'r0' in=5 does not match derived 2")
    ChecksV2.runC1(withR0(_.copy(out = Some(7)))) should contain(
      "C1: [CHK-31] router 'r0' out=7 does not match derived 2")
    ChecksV2.runC1(withR0(_.copy(in = Some(2), out = Some(2)))) shouldBe Nil
  }

  behavior of "ChecksV2 C2 (CHK-32–CHK-34)"

  it should "accept the default mapping written explicitly or omitted (P-C2)" in {
    ChecksV2.runC2(base) shouldBe Nil
    val v = base
    val omitted = v.copy(qos = QosV2(V2Defaults.QosMapping, V2Defaults.QosClasses))
    ChecksV2.runC2(omitted) shouldBe Nil
    // order-insensitive: same segments in another declaration order
    ChecksV2.runC2(withMapping(V2Defaults.QosMapping.reverse)) shouldBe Nil
  }

  it should "reject a mapping hole with CHK-32 (N-C2)" in {
    val bad = withMapping(Seq(QosMappingV2(9, 15, "ctl"), QosMappingV2(4, 8, "lat")))
    ChecksV2.runC2(bad) should contain("C2: [CHK-32] qos.mapping gap at [0,3]")
  }

  it should "reject a mapping overlap with CHK-32" in {
    val bad = withMapping(Seq(
      QosMappingV2(8, 15, "ctl"), QosMappingV2(4, 8, "lat"), QosMappingV2(0, 3, "bulk")))
    ChecksV2.runC2(bad) should contain("C2: [CHK-32] qos.mapping overlap at [8,8]")
  }

  it should "reject out-of-range and inverted segments and undeclared classes with CHK-32" in {
    ChecksV2.runC2(withMapping(Seq(
      QosMappingV2(8, 15, "ctl"), QosMappingV2(4, 7, "lat"), QosMappingV2(0, 16, "bulk")))) should contain(
      "C2: [CHK-32] qos.mapping out of range [0,15] at [0,16]")
    ChecksV2.runC2(withMapping(Seq(
      QosMappingV2(8, 15, "ctl"), QosMappingV2(7, 4, "lat"), QosMappingV2(0, 3, "bulk")))) should contain(
      "C2: [CHK-32] qos.mapping qosMin > qosMax at [7,4]")
    ChecksV2.runC2(withMapping(Seq(
      QosMappingV2(8, 15, "foo"), QosMappingV2(4, 7, "lat"), QosMappingV2(0, 3, "bulk")))) should contain(
      "C2: [CHK-32] qos.mapping class 'foo' is not declared at [8,15]")
  }

  it should "require exactly the classes ctl/lat/bulk with CHK-33 (N-C2)" in {
    val v = base
    val missing = v.copy(qos = v.qos.copy(classes = v.qos.classes.filterNot(_.name == "bulk")))
    ChecksV2.runC2(missing) should contain(
      "C2: [CHK-33] qos class 'bulk' is missing; classes must be exactly ctl,lat,bulk")
    val dup = v.copy(qos = v.qos.copy(classes = v.qos.classes :+ v.qos.classes.head))
    ChecksV2.runC2(dup) should contain("C2: [CHK-33] qos class 'ctl' duplicate name")
    val extra = v.copy(qos = v.qos.copy(classes = v.qos.classes :+ QosClassV2("foo", 1, 1, "wrr", 1)))
    ChecksV2.runC2(extra) should contain("C2: [CHK-33] qos class 'foo' is not a supported class")
  }

  it should "reject out-of-range class vcs/depth/weight with CHK-33 (N-C2)" in {
    ChecksV2.runC2(withClasses(c => if (c.name == "ctl") c.copy(depth = 0) else c)) should contain(
      "C2: [CHK-33] qos class 'ctl' depth must be in [1,64]")
    ChecksV2.runC2(withClasses(c => if (c.name == "lat") c.copy(vcs = 9) else c)) should contain(
      "C2: [CHK-33] qos class 'lat' vcs must be in [1,8]")
    ChecksV2.runC2(withClasses(c => if (c.name == "bulk") c.copy(weight = 65) else c)) should contain(
      "C2: [CHK-33] qos class 'bulk' weight must be in [1,64]")
    ChecksV2.runC2(withClasses(c => c.copy(vcs = 8, depth = 64, weight = 64))) shouldBe Nil
  }

  it should "lock the implemented 8/4 thresholds with CHK-34 (N-C2, OQ-06)" in {
    val bad = withMapping(Seq(
      QosMappingV2(9, 15, "ctl"), QosMappingV2(4, 8, "lat"), QosMappingV2(0, 3, "bulk")))
    ChecksV2.runC2(bad) should contain(
      "C2: [CHK-34] qos.mapping deviates from the implemented ChiQosMap thresholds (8/4); custom thresholds not yet supported")
  }

  behavior of "ChecksV2 G4 (CHK-40)"

  it should "accept mixed protocols under portMode both (P-G4)" in {
    val v = withChi(_.copy(portMode = "both"))
    val mixed = v.copy(nodes = v.nodes.map(n =>
      if (n.id == "m0") n.copy(protocol = "axi4") else n))
    ChecksV2.runG4(mixed) shouldBe Nil
    ChecksV2.runG4(base) shouldBe Nil // portMode=chi, all nodes chi
  }

  it should "reject protocol/portMode mismatches with CHK-40 (N-G4)" in {
    ChecksV2.runG4(withNode("m0")(_.copy(protocol = "axi4"))) should contain(
      "LM-G4: [CHK-40] node 'm0' protocol 'axi4' incompatible with chi.portMode 'chi'")
    ChecksV2.runG4(withChi(_.copy(portMode = "axi4"))) should contain(
      "LM-G4: [CHK-40] node 'm0' protocol 'chi' incompatible with chi.portMode 'axi4'")
    ChecksV2.runG4(withNode("m0")(_.copy(protocol = "tlul"))) should contain(
      "LM-G4: [CHK-40] node 'm0' protocol 'tlul' incompatible with chi.portMode 'chi'")
  }

  behavior of "ChecksV2 G6 (CHK-41/CHK-42)"

  it should "match the ENC-01 golden anchors 128->4, 256->2, 512->1 (P-G6)" in {
    ChiWidths.flitCount(64, 128) shouldBe 4
    ChiWidths.flitCount(64, 256) shouldBe 2
    ChiWidths.flitCount(64, 512) shouldBe 1
    Seq(128, 256, 512).foreach { w =>
      ChecksV2.runG6(withChi(_.copy(datWidth = w))) shouldBe Nil
    }
  }

  it should "flag a tampered elaboration flit count with CHK-41 (N-G6 injection)" in {
    ChecksV2.flitCountError(256, 4) shouldBe Some(
      "LM-G6: [CHK-41] DAT flit count 4 inconsistent with datWidth=256 (expected 2)")
    ChecksV2.flitCountError(128, 4) shouldBe None
    ChecksV2.flitCountError(512, 1) shouldBe None
  }

  it should "reject chi.outstanding beyond the TxnID space with CHK-42 (N-G6)" in {
    ChecksV2.runG6(withChi(_.copy(outstanding = 512))) should contain(
      "LM-G6: [CHK-42] chi.outstanding 512 exceeds TxnID space 2^8=256")
    ChecksV2.runG6(withChi(_.copy(outstanding = 256))) shouldBe Nil
    ChecksV2.runG6(withChi(_.copy(txnIdWidth = 12, outstanding = 4096))) shouldBe Nil
  }

  behavior of "ChecksV2 G8 (CHK-43)"

  it should "accept wrr and rr arbitration (P-G8)" in {
    ChecksV2.runG8(base) shouldBe Nil
    ChecksV2.runG8(withClasses(_.copy(arbitration = "rr"))) shouldBe Nil
  }

  it should "reject an unsupported arbitration policy with CHK-43 (N-G8)" in {
    ChecksV2.runG8(withClasses(c => if (c.name == "ctl") c.copy(arbitration = "strict") else c)) shouldBe
      List("LM-G8: [CHK-43] qos class 'ctl' arbitration must be wrr or rr")
  }

  behavior of "ChecksV2.run aggregation (§6.1)"

  it should "report all gates green for the appendix A baseline" in {
    val r = ChecksV2.run(base)
    r.errors shouldBe Nil
    r.gates shouldBe Vector(
      "C0" -> true, "C1" -> true,
      "G1-REQ" -> true, "G1-RSP" -> true, "G1-DAT" -> true, "G1-SNP" -> true,
      "C2" -> true, "G4" -> true, "G6" -> true, "G7" -> true, "G8" -> true,
      "S1" -> true, "S2" -> true, "S3" -> true)
  }

  it should "terminate at C0 without running later gates" in {
    val v0 = base
    val v = v0.copy(
      meta = v0.meta.copy(schema = "loom/v3"),
      links = v0.links :+ v0.links.head) // would also be CHK-25
    val r = ChecksV2.run(v)
    r.gates shouldBe Vector("C0" -> false)
    r.errors shouldBe ChecksV2.runC0(v)
    r.errors.exists(_.contains("[CHK-25]")) shouldBe false
  }

  it should "collect C1/C2/G4/G6/G8 errors in gate order" in {
    val v0 = base
    val v = v0.copy(
      links = v0.links :+ v0.links.head, // CHK-25
      chi = v0.chi.copy(outstanding = 512), // CHK-42
      qos = v0.qos.copy(classes = v0.qos.classes.map(c =>
        if (c.name == "ctl") c.copy(arbitration = "strict") else c))) // CHK-43
    val r = ChecksV2.run(v)
    r.errors shouldBe List(
      "C1: [CHK-25] duplicate link 'm0' -> 'r0'",
      "LM-G6: [CHK-42] chi.outstanding 512 exceeds TxnID space 2^8=256",
      "LM-G8: [CHK-43] qos class 'ctl' arbitration must be wrr or rr")
    r.gates.toMap.apply("C1") shouldBe false
    r.gates.toMap.apply("C2") shouldBe true
    r.gates.toMap.apply("G6") shouldBe false
    r.gates.toMap.apply("G7") shouldBe true
    r.gates.toMap.apply("G8") shouldBe false
  }

  behavior of "ChecksV2 on upgraded v1 examples (MIG-01)"

  // portMode converges from the v1 protocol set (MIG-02/IR-43), so upgraded
  // examples must pass every implemented gate with zero errors.
  private val upgradeExamples = Seq(
    "minimal_2x2", "minimal_4x4", "minimal_4x4_banked", "minimal_8x8",
    "ring_3_bidir", "ring_4", "chain_4r", "tree_3r", "mesh_2x2_xy",
    "chain_split", "chain_split_async")

  upgradeExamples.foreach { name =>
    it should s"upgrade $name with zero gate errors" in {
      val ir = Ir.fromJson(Json.parse(
        Files.readString(Paths.get(s"src/loom/testdata/v1/$name.json"))))
      val v2 = Upgrade.v1ToV2(ir).ir
      withClue(s"example $name: ") { ChecksV2.run(v2).errors shouldBe Nil }
    }
  }

  it should "require exactly one mn for rn-d and at most one mn always (CHK-19, AQ-01)" in {
    val base = IrV2.load(Paths.get("src/loom/examples/ring_4.json"))
    val withRnD = base.copy(chi = base.chi.copy(dvmEnable = true),
      nodes = base.nodes.map(n => if (n.id == "m0") n.copy(kind = "rn-d") else n))
    ChecksV2.runC0(withRnD) should contain("C0: [CHK-19] rn-d requires exactly one mn node")
    val mn = NodeV2("mn0", "mn", None, "core", "r0", "chi", 256, 64, 0)
    val withMn = withRnD.copy(nodes = withRnD.nodes :+ mn)
    ChecksV2.runC0(withMn).filter(_.contains("CHK-19")) shouldBe Nil
    val twoMn = withMn.copy(nodes = withMn.nodes :+ mn.copy(id = "mn1"))
    ChecksV2.runC0(twoMn) should contain("C0: [CHK-19] at most one mn node allowed")
  }

  behavior of "ChecksV2 S1/S2/S3 partition gates (CHK-44–CHK-48, §6.10–6.12)"

  /** P-S1 fixture: two single-clock partitions joined by an async router cut. */
  private def partitioned: IrV2 = {
    val v = base
    v.copy(
      meta = v.meta.copy(name = "part_ok"),
      clockDomains = Seq(ClockDomain("a", 1000), ClockDomain("b", 800)),
      chi = v.chi.copy(dvmEnable = false),
      nodes = Seq(
        NodeV2("m0", "rn-i", None, "a", "r0", "chi", 256, 64, 0),
        NodeV2("s0", "sn-f", None, "b", "r1", "chi", 256, 64, 0)),
      sam = Seq(SamWindowV2(0L, 4096L, "s0")),
      routers = Seq(
        RouterV2("r0", "a", None, None, None, "any"),
        RouterV2("r1", "b", None, None, None, "any")),
      links = Seq(
        LinkV2("m0", "r0", 256, "sync", 0, 0, false, Seq("req", "rsp", "dat"), 4),
        LinkV2("r0", "r1", 256, "async", 0, 2, false, Seq("req", "rsp", "dat"), 4),
        LinkV2("r1", "s0", 256, "sync", 0, 0, false, Seq("req", "rsp", "dat"), 4)),
      partitions = Seq(
        PartitionSpec("p0", "a", Seq("m0", "r0"), 1),
        PartitionSpec("p1", "b", Seq("r1", "s0"), 1)))
  }

  it should "accept the P-S1/P-S2/P-S3 partition fixture with zero errors" in {
    ChecksV2.runS1(partitioned) shouldBe Nil
    ChecksV2.runS2(partitioned) shouldBe Nil
    ChecksV2.runS3(partitioned) shouldBe Nil
    val r = ChecksV2.run(partitioned)
    r.errors shouldBe Nil
    r.gates.toMap.apply("S1") shouldBe true
    r.gates.toMap.apply("S2") shouldBe true
    r.gates.toMap.apply("S3") shouldBe true
  }

  it should "keep S1/S2/S3 trivially green without partitions" in {
    ChecksV2.runS1(base) shouldBe Nil
    ChecksV2.runS2(base) shouldBe Nil
    ChecksV2.runS3(base) shouldBe Nil
  }

  it should "reject broken partition declarations with CHK-44 (N-S1)" in {
    val v = partitioned
    val dup = v.copy(partitions = v.partitions.map(p => p.copy(name = "p0")))
    ChecksV2.runS1(dup) should contain("LM-S1: [CHK-44] partition 'p0' duplicate name")
    val empty = v.copy(partitions = v.partitions.map(p =>
      if (p.name == "p0") p.copy(members = Nil) else p))
    ChecksV2.runS1(empty) should contain("LM-S1: [CHK-44] partition 'p0' members must not be empty")
    val ghostClock = v.copy(partitions = v.partitions.map(p =>
      if (p.name == "p0") p.copy(clock = "ghost") else p))
    ChecksV2.runS1(ghostClock) should contain(
      "LM-S1: [CHK-44] partition 'p0' clock 'ghost' is not declared")
    val ghostMember = v.copy(partitions = v.partitions.map(p =>
      if (p.name == "p0") p.copy(members = p.members :+ "ghost") else p))
    ChecksV2.runS1(ghostMember) should contain(
      "LM-S1: [CHK-44] partition 'p0' member 'ghost' is not a fabric object")
    val twice = v.copy(partitions = v.partitions.map(p =>
      if (p.name == "p0") p.copy(members = p.members :+ "r1") else p))
    ChecksV2.runS1(twice) should contain(
      "LM-S1: [CHK-44] partition 'p1' member 'r1' is already assigned to partition 'p0'")
    val uncovered = v.copy(partitions = v.partitions.map(p =>
      if (p.name == "p1") p.copy(members = Seq("s0")) else p))
    ChecksV2.runS1(uncovered) should contain(
      "LM-S1: [CHK-44] partition 'p0' fabric object 'r1' is not assigned to any partition")
  }

  it should "reject member clock mismatch and split node/attach pairs with CHK-45 (N-S1)" in {
    val v = partitioned
    val wrongClock = v.copy(partitions = v.partitions.map(p =>
      if (p.name == "p0") p.copy(clock = "b") else p))
    val errs = ChecksV2.runS1(wrongClock)
    errs should contain("LM-S1: [CHK-45] node 'm0' runs on clock 'a', expected 'b'")
    errs should contain("LM-S1: [CHK-45] router 'r0' runs on clock 'a', expected 'b'")
    val split = v.copy(partitions = Seq(
      PartitionSpec("p0", "a", Seq("m0", "r0", "s0"), 1),
      PartitionSpec("p1", "b", Seq("r1"), 1)))
    ChecksV2.runS1(split) should contain("LM-S1: [CHK-45] node 's0' crosses partition boundary")
  }

  it should "reject an async link inside one clock domain with CHK-46 (N-S1)" in {
    val v = partitioned
    val same = v.copy(
      nodes = v.nodes.map(n => n.copy(clock = "a")),
      routers = v.routers.map(r => r.copy(clock = "a")),
      partitions = v.partitions.map(p => p.copy(clock = "a")))
    ChecksV2.runS1(same) should contain(
      """LM-S1: [CHK-46] async link 'r0' -> 'r1' joins clock domain 'a'; use cdc "sync"""")
  }

  it should "reject boundaryPipe out of range and router-less partitions with CHK-47 (N-S2)" in {
    val v = partitioned
    val badPipe = v.copy(partitions = v.partitions.map(p =>
      if (p.name == "p0") p.copy(boundaryPipe = 0) else p))
    ChecksV2.runS2(badPipe) should contain(
      "LM-S2: [CHK-47] partition 'p0' boundaryPipe must be in [1,8]")
    val noRouter = v.copy(partitions = v.partitions.map(p =>
      if (p.name == "p0") p.copy(members = Seq("m0")) else p))
    ChecksV2.runS2(noRouter) should contain("LM-S2: [CHK-47] partition 'p0' has no router")
  }

  it should "reject a non-router cut link with CHK-48 (N-S3)" in {
    val v = partitioned
    val badCut = v.copy(partitions = Seq(
      PartitionSpec("p0", "a", Seq("m0", "r0", "s0"), 1),
      PartitionSpec("p1", "b", Seq("r1"), 1)))
    ChecksV2.runS3(badCut) should contain(
      "LM-S3: [CHK-48] cut link 'r1' -> 's0' is not router-to-router")
  }

  it should "append S1/S2/S3 after G8 in gate order (§6.1)" in {
    val v = partitioned
    val bad = v.copy(partitions = v.partitions.map(p =>
      if (p.name == "p0") p.copy(boundaryPipe = 0) else p))
    val r = ChecksV2.run(bad)
    r.gates.map(_._1).takeRight(3) shouldBe Vector("S1", "S2", "S3")
    r.errors should contain("LM-S2: [CHK-47] partition 'p0' boundaryPipe must be in [1,8]")
  }

  it should "pass partition gates on upgraded chain_split / chain_split_async (MIG-01)" in {
    Seq("chain_split", "chain_split_async").foreach { name =>
      val ir = Ir.fromJson(Json.parse(
        Files.readString(Paths.get(s"src/loom/testdata/v1/$name.json"))))
      val v2 = Upgrade.v1ToV2(ir).ir
      withClue(s"example $name S1: ") { ChecksV2.runS1(v2) shouldBe Nil }
      withClue(s"example $name S2: ") { ChecksV2.runS2(v2) shouldBe Nil }
      withClue(s"example $name S3: ") { ChecksV2.runS3(v2) shouldBe Nil }
    }
  }

  // ---- UP-01 explicit routes / connectivity constraints (CHK-49–CHK-51) ----

  behavior of "ChecksV2 routes (CHK-49–CHK-51, UP-01)"

  private def withRoutes(rs: RouteSpec*): IrV2 = base.copy(routes = rs.toSeq)

  /** ring_4 cut into two chains: r0-r1 and r2-r3 (m0/m1 isolated from s0/s1). */
  private def disconnected: IrV2 = {
    val v = base
    v.copy(links = v.links.filterNot(l =>
      (l.from == "r1" && l.to == "r2") || (l.from == "r3" && l.to == "r0")))
  }

  it should "reject unknown or wrong-kind route endpoints with CHK-49" in {
    ChecksV2.runC0(withRoutes(RouteSpec("ghost", "s0", Seq("r0", "r1", "r2"), false))) should contain(
      "C0: [CHK-49] route 'ghost->s0' from 'ghost' is not a declared node")
    ChecksV2.runC0(withRoutes(RouteSpec("s0", "s1", Nil, true))) should contain(
      "C0: [CHK-49] route 's0->s1' from 's0' kind 'sn-f' must be rn-i or rn-d")
    ChecksV2.runC0(withRoutes(RouteSpec("m0", "ghost", Nil, true))) should contain(
      "C0: [CHK-49] route 'm0->ghost' to 'ghost' is not a declared node")
    ChecksV2.runC0(withRoutes(RouteSpec("m0", "m1", Nil, true))) should contain(
      "C0: [CHK-49] route 'm0->m1' to 'm1' kind 'rn-i' must be sn-f or sn-i")
  }

  it should "reject duplicate pairs, undeclared path routers and forbid-with-path with CHK-49" in {
    val r = RouteSpec("m0", "s0", Seq("r0", "r1", "r2"), false)
    ChecksV2.runC0(withRoutes(r, r)) should contain(
      "C0: [CHK-49] route 'm0->s0' duplicate (from,to) pair")
    ChecksV2.runC0(withRoutes(RouteSpec("m0", "s0", Seq("r0", "rX", "r2"), false))) should contain(
      "C0: [CHK-49] route 'm0->s0' path router 'rX' is not declared")
    ChecksV2.runC0(withRoutes(RouteSpec("m0", "s0", Seq("r0", "r1", "r2"), true))) should contain(
      "C0: [CHK-49] route 'm0->s0' forbid=true must not carry a path")
  }

  it should "accept a well-formed explicit route and forbid entry with zero C0 errors (P-C0 routes)" in {
    ChecksV2.runC0(withRoutes(
      RouteSpec("m0", "s0", Seq("r0", "r3", "r2"), false),
      RouteSpec("m1", "s1", Nil, true))) shouldBe Nil
  }

  it should "reject path endpoint mismatches and an empty explicit path with CHK-50" in {
    ChecksV2.runC1(withRoutes(RouteSpec("m0", "s0", Seq("r1", "r2"), false))) should contain(
      "C1: [CHK-50] route 'm0->s0' path head 'r1' is not from.attach 'r0'")
    ChecksV2.runC1(withRoutes(RouteSpec("m0", "s0", Seq("r0", "r1"), false))) should contain(
      "C1: [CHK-50] route 'm0->s0' path tail 'r1' is not to.attach 'r2'")
    ChecksV2.runC1(withRoutes(RouteSpec("m0", "s0", Nil, false))) should contain(
      "C1: [CHK-50] route 'm0->s0' path must not be empty")
  }

  it should "reject missing hops and repeated routers in a path with CHK-50" in {
    ChecksV2.runC1(withRoutes(RouteSpec("m0", "s0", Seq("r0", "r2"), false))) should contain(
      "C1: [CHK-50] route 'm0->s0' no link between path routers 'r0' and 'r2'")
    val errs = ChecksV2.runC1(withRoutes(
      RouteSpec("m0", "s0", Seq("r0", "r1", "r0", "r1", "r2"), false)))
    errs should contain("C1: [CHK-50] route 'm0->s0' path repeats router 'r0'")
    errs should contain("C1: [CHK-50] route 'm0->s0' path repeats router 'r1'")
  }

  it should "reject conflicting next hops at shared routers for one SN with CHK-50 (SEM-17)" in {
    val errs = ChecksV2.runC1(withRoutes(
      RouteSpec("m0", "s0", Seq("r0", "r1", "r2"), false),
      RouteSpec("m1", "s0", Seq("r1", "r0", "r3", "r2"), false)))
    errs should contain(
      "C1: [CHK-50] route 'm1->s0' next hop for target 's0' at router 'r1' conflicts with route 'm0->s0' ('r0' vs 'r2')")
    errs should contain(
      "C1: [CHK-50] route 'm1->s0' next hop for target 's0' at router 'r0' conflicts with route 'm0->s0' ('r3' vs 'r1')")
    errs.size shouldBe 2
  }

  it should "accept consistent explicit paths sharing routers with zero C1 errors (P-C1 routes)" in {
    ChecksV2.runC1(withRoutes(RouteSpec("m0", "s0", Seq("r0", "r3", "r2"), false))) shouldBe Nil
    ChecksV2.runC1(withRoutes(
      RouteSpec("m0", "s0", Seq("r0", "r1", "r2"), false),
      RouteSpec("m1", "s0", Seq("r1", "r2"), false))) shouldBe Nil
  }

  it should "reject conflicting reverse next hops for one RN with CHK-50 (SEM-17)" in {
    val base = IrV2.load(Paths.get("src/loom/examples/ring_4.json"))
    val twoSn = base.copy(
      nodes = base.nodes.map(n => if (n.id == "s1") n.copy(attach = "r2") else n),
      links = base.links.map(l => if (l.from == "r3" && l.to == "s1") l.copy(from = "r2") else l),
      routes = Seq(
        RouteSpec("m0", "s0", Seq("r0", "r1", "r2"), false),
        RouteSpec("m0", "s1", Seq("r0", "r3", "r2"), false)))
    ChecksV2.runC1(twoSn) should contain(
      "C1: [CHK-50] route 'm0->s1' next hop for target 'm0' at router 'r2' conflicts with route 'm0->s0' ('r3' vs 'r1')")
  }

  // CHK-51 RETIRED (ir_gen_spec v1.2.0, SEM-17): forbid is executed via
  // per-source SAM filtering (samByRn + source-NI NDERR), so a reachable
  // forbidden pair is now LEGAL. The former negative cases are inverted.
  it should "accept a reachable forbidden pair with zero C1 errors (CHK-51 retired, v1.2.0)" in {
    ChecksV2.runC1(withRoutes(RouteSpec("m0", "s0", Nil, true))) shouldBe Nil
    ChecksV2.run(withRoutes(RouteSpec("m0", "s0", Nil, true))).errors shouldBe Nil
    // same attach router (formerly "reachable") is legal too
    val v = base
    val colocated = v.copy(
      nodes = v.nodes.map(n => if (n.id == "s0") n.copy(attach = "r0") else n),
      links = v.links.map(l =>
        if (l.from == "r2" && l.to == "s0") l.copy(from = "r0") else l),
      routes = Seq(RouteSpec("m0", "s0", Nil, true)))
    ChecksV2.runC1(colocated) shouldBe Nil
    ChecksV2.run(colocated).errors shouldBe Nil
  }

  it should "still accept a forbidden pair on a disconnected topology (P-C1, CHK-51 retired)" in {
    val v = disconnected.copy(routes = Seq(RouteSpec("m0", "s0", Nil, true)))
    ChecksV2.runC0(v) shouldBe Nil
    ChecksV2.runC1(v) shouldBe Nil
    ChecksV2.run(v).errors shouldBe Nil
  }

  // ---- S8: qosGen / adaptFifoDepth domains (CHK-52/CHK-53, UP-02/UP-08) ----

  behavior of "ChecksV2 qosGen/adaptFifoDepth (CHK-52/CHK-53, UP-02/UP-08)"

  private def withQosGen(q: QosGenSpec): IrV2 =
    withNode("m0")(_.copy(qosGen = Some(q)))

  it should "reject an unknown qosGen.kind with CHK-52" in {
    ChecksV2.runC0(withQosGen(QosGenSpec("bogus", 100, 0))) should contain(
      "C0: [CHK-52] node 'm0' qosGen kind 'bogus' must be fixed, limiter or regulator")
  }

  it should "reject qosGen.bandwidth outside [1,1048576] with CHK-52" in {
    ChecksV2.runC0(withQosGen(QosGenSpec("limiter", 0, 0))) should contain(
      "C0: [CHK-52] node 'm0' qosGen bandwidth 0 must be in [1,1048576]")
    ChecksV2.runC0(withQosGen(QosGenSpec("limiter", 1048577, 0))) should contain(
      "C0: [CHK-52] node 'm0' qosGen bandwidth 1048577 must be in [1,1048576]")
  }

  it should "reject qosGen.saturation outside [0,1048576] with CHK-52" in {
    ChecksV2.runC0(withQosGen(QosGenSpec("limiter", 100, -1))) should contain(
      "C0: [CHK-52] node 'm0' qosGen saturation -1 must be in [0,1048576]")
    ChecksV2.runC0(withQosGen(QosGenSpec("regulator", 100, 1048577))) should contain(
      "C0: [CHK-52] node 'm0' qosGen saturation 1048577 must be in [0,1048576]")
  }

  it should "reject a fixed qosGen with a burst allowance with CHK-52 (SEM-18)" in {
    ChecksV2.runC0(withQosGen(QosGenSpec("fixed", 100, 64))) should contain(
      "C0: [CHK-52] node 'm0' qosGen fixed kind requires saturation 0, got 64")
  }

  it should "accept every legal qosGen kind at the domain boundaries (P-C0 qosGen)" in {
    ChecksV2.runC0(withQosGen(QosGenSpec("fixed", 1, 0))) shouldBe Nil
    ChecksV2.runC0(withQosGen(QosGenSpec("limiter", 1048576, 1048576))) shouldBe Nil
    ChecksV2.runC0(withQosGen(QosGenSpec("regulator", 1000, 0))) shouldBe Nil
    // declaration-only: a legal qosGen never disturbs the other gates
    ChecksV2.run(withQosGen(QosGenSpec("limiter", 1000, 4096))).errors shouldBe Nil
  }

  it should "reject adaptFifoDepth outside [2,64] with CHK-53" in {
    ChecksV2.runC0(withNode("m0")(_.copy(adaptFifoDepth = Some(1)))) should contain(
      "C0: [CHK-53] node 'm0' adaptFifoDepth 1 must be in [2,64]")
    ChecksV2.runC0(withNode("m0")(_.copy(adaptFifoDepth = Some(65)))) should contain(
      "C0: [CHK-53] node 'm0' adaptFifoDepth 65 must be in [2,64]")
    ChecksV2.runC0(withNode("s0")(_.copy(adaptFifoDepth = Some(0)))) should contain(
      "C0: [CHK-53] node 's0' adaptFifoDepth 0 must be in [2,64]")
  }

  it should "accept adaptFifoDepth at the domain boundaries and when omitted (P-C0 adapt)" in {
    ChecksV2.runC0(withNode("m0")(_.copy(adaptFifoDepth = Some(2)))) shouldBe Nil
    ChecksV2.runC0(withNode("m0")(_.copy(adaptFifoDepth = Some(64)))) shouldBe Nil
    ChecksV2.runC0(base) shouldBe Nil // omitted key: no CHK-53 domain applies
    ChecksV2.run(withNode("m0")(_.copy(adaptFifoDepth = Some(8)))).errors shouldBe Nil
  }
}