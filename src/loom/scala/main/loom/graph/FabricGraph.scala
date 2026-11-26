package loom.graph

import loom.ir.{Ir, IrV2, JBool, JArr, JNum, JObj, JStr, JValue}
import scala.collection.mutable

/** A directed link between two fabric nodes.
  *
  * v1 CDG vertices are directed links (`cls` empty). v2 CDG vertices are
  * `(link, channel class, direction, VC half)` (GEN-10): direction is encoded
  * in `from`/`to`, `cls`/`half` carry the extra dimensions and the label is
  * upgraded to `from->to/class/h<half>` (GEN-11).
  */
final case class Channel(
    id: String,
    label: String,
    from: String,
    to: String,
    cls: String = "",
    half: Int = 0
)

/** Fabric connectivity plus its channel dependency graph (CDG).
  *
  * A CDG vertex is a directed link. At each router, an incoming link can
  * wait for any outgoing link except a U-turn back to the node it came from.
  *
  * A simple cycle (each vertex has one predecessor and one successor inside
  * the cycle, all vertices are router-to-router links) is constructively
  * deadlock free once one of its links is a dateline cut. A non-simple SCC
  * needs a turn model or turn VCs and is still rejected.
  */
final case class FabricGraph(
    nodes: Seq[String],
    channels: Seq[Channel],
    dependencies: Map[String, Seq[String]],
    datelines: Seq[String] = Nil,
    autoDatelines: Seq[String] = Nil,
    ringLinks: Seq[String] = Nil,
    errors: Seq[String] = Nil
) {
  private def labelOf(id: String): String =
    channels.find(_.id == id).map(_.label).getOrElse(id)

  /** Deterministic DFS: first cycle found in sorted vertex order. */
  def findCycle: Option[Seq[String]] = findCycleIn(channels.map(_.id))

  /** Per-class deterministic DFS over the v2 four-tuple vertices (GEN-11,
    * CHK-35). Classes are independent subgraphs, so this proves one class.
    */
  def findCycleOfClass(cls: String): Option[Seq[String]] =
    findCycleIn(channels.filter(_.cls == cls).map(_.id))

  private def findCycleIn(vertexIds: Seq[String]): Option[Seq[String]] = {
    val color = mutable.Map.empty[String, Int]
    val stack = mutable.ArrayBuffer.empty[String]

    def dfs(u: String): Option[Seq[String]] = {
      color(u) = 1
      stack += u
      val next = dependencies.getOrElse(u, Nil).sorted
      var i = 0
      var found = Option.empty[Seq[String]]
      while (i < next.length && found.isEmpty) {
        val v = next(i)
        color.getOrElse(v, 0) match {
          case 0 => found = dfs(v)
          case 1 =>
            val start = stack.indexOf(v)
            found = Some(stack.drop(start).toSeq :+ v)
          case _ =>
        }
        i += 1
      }
      if (found.isEmpty) {
        stack.remove(stack.length - 1)
        color(u) = 2
      }
      found
    }

    val vertices = vertexIds.sorted
    var i = 0
    var found = Option.empty[Seq[String]]
    while (i < vertices.length && found.isEmpty) {
      if (color.getOrElse(vertices(i), 0) == 0) found = dfs(vertices(i))
      i += 1
    }
    found.map(_.map(labelOf))
  }

  def toJson: JValue = {
    val nodeJson = JArr(nodes.map(n => JStr(n)).toVector)
    val chJson = JArr(channels.map { c =>
      val base = Vector("from" -> JStr(c.from), "to" -> JStr(c.to))
      // v2 four-tuple labels are additive keys (MIG-10); v1 rendering unchanged.
      val extra =
        if (c.cls.nonEmpty)
          Vector("label" -> JStr(c.label), "class" -> JStr(c.cls), "vc" -> JNum(c.half))
        else Vector.empty
      JObj(base ++ extra)
    }.toVector)
    val edges = dependencies.toSeq
      .sortBy(_._1)
      .flatMap { case (from, tos) =>
        tos.sorted.map(to => (labelOf(from), labelOf(to)))
      }
    val edgeJson = JArr(edges.map { case (from, to) =>
      JObj(Vector("from" -> JStr(from), "to" -> JStr(to)))
    }.toVector)
    JObj(Vector(
      "nodes" -> nodeJson,
      "channels" -> chJson,
      "cdg" -> edgeJson,
      "datelines" -> JArr(datelines.map(l => JStr(l)).toVector),
      "autoDatelines" -> JArr(autoDatelines.map(l => JStr(l)).toVector),
      "acyclic" -> JBool(findCycle.isEmpty && errors.isEmpty)
    ))
  }
}

object FabricGraph {
  def fromIr(ir: Ir): FabricGraph = {
    val nodes = (ir.mnets.map(_.name) ++ ir.snets.map(_.name) ++ ir.routers.map(_.name)).sorted
    val sortedLinks = ir.links.sortBy(l => (l.from, l.to))
    val channels = sortedLinks.zipWithIndex.map { case (l, i) =>
      Channel(s"ch$i", s"${l.from}->${l.to}", l.from, l.to)
    }

    val routerByName = ir.routers.map(r => r.name -> r).toMap
    val deps = buildDependencies(channels, ir.routers.map(r => (r.name, r.routing, r.pos)))

    val selfLink = channels.filter(c => c.from == c.to)
    val errors = mutable.ListBuffer.empty[String]
    selfLink.foreach(c => errors += s"LM-G1: self-link ${c.label} is not allowed")

    // SCC over the CDG. Only simple router-router cycles may be cut by a
    // dateline; anything denser needs a turn model or turn VCs.
    val datelineSpec = ir.links.map(l => s"${l.from}->${l.to}" -> l.dateline).toMap
    val rings = analyzeRings(channels, deps, datelineSpec,
      c => routerByName.contains(c.from) && routerByName.contains(c.to),
      path => s"LM-G1: CDG cycle: $path (not a ring; use a turn model or turn VCs)")
    errors ++= rings.errors

    // A cut removes the CDG edges leaving that link: packets crossing the
    // dateline continue in the second VC half, whose subgraph is a chain.
    val cutIds = rings.datelines.map(l => channels.find(_.label == l).get.id).toSet
    val cutDeps = applyCuts(deps, cutIds)

    FabricGraph(nodes, channels, cutDeps,
      rings.datelines, rings.autoDatelines, rings.ringLinks, errors.toList)
  }

  /** GEN-10/GEN-11 (S3): the loom/v2 CDG with vertices
    * `(link, channel class, direction, VC half)`.
    *
    * Every link expands to both directions (HW-07/SEM-13); node access links
    * expand too, but nodes are not routers so those extra vertices are
    * harmless sources/sinks. A vertex exists only for classes in the link's
    * `channels` (IR-62), snp only when `chi.dvmEnable` (D11/SEM-07). Ring
    * detection runs on the class-agnostic topology projection with the shared
    * SCC + simpleRing + autoDateline algorithm; links on a detected simple
    * ring (either direction) get halves {0,1}, all other links only half 0
    * (AQ-02①). Dependency edges are per (class, half) with the existing
    * U-turn exclusion and xy turn model; classes never cross.
    */
  def fromV2(v2: IrV2): FabricGraph = {
    val nodes = (v2.nodes.map(_.id) ++ v2.routers.map(_.name)).sorted
    val routerNames = v2.routers.map(_.name).toSet
    val routerTuples = v2.routers.map(r => (r.name, r.routing, r.pos))
    val sortedLinks = v2.links.sortBy(l => (l.from, l.to))

    // Topology projection: one vertex per link direction, class/half agnostic.
    val topo = sortedLinks.flatMap { l =>
      Seq(
        Channel(s"${l.from}->${l.to}", s"${l.from}->${l.to}", l.from, l.to),
        Channel(s"${l.to}->${l.from}", s"${l.to}->${l.from}", l.to, l.from))
    }.distinctBy(_.id)
    val topoDeps = buildDependencies(topo, routerTuples)
    // An explicit dateline cuts only its declared direction; the reverse
    // cycle of a bidirectional ring gets its own autoDateline (SEM-09).
    val datelineSpec = sortedLinks.filter(_.dateline).map(l => s"${l.from}->${l.to}" -> true).toMap
    val rings = analyzeRings(topo, topoDeps, datelineSpec,
      c => routerNames.contains(c.from) && routerNames.contains(c.to),
      path => s"LM-G1: [CHK-38] CDG cycle: $path (not a ring; use a turn model or turn VCs)")

    val errors = mutable.ListBuffer.empty[String]
    // CHK-36: self-links.
    sortedLinks.filter(l => l.from == l.to).map(l => s"${l.from}->${l.to}").distinct.foreach(l =>
      errors += s"LM-G1: [CHK-36] self-link $l is not allowed")
    // CHK-37: dateline=true outside a simple ring (covers non-router edges,
    // which can never be ring members).
    sortedLinks.filter(_.dateline).map(l => s"${l.from}->${l.to}").distinct
      .filterNot(rings.ringLinks.contains).foreach(l =>
        errors += s"LM-G1: [CHK-37] dateline link $l is not part of a ring")
    errors ++= rings.errors

    // AQ-02①: a link on a detected simple ring (either direction) carries
    // both VC halves; every other link only half 0 (no nonexistent paths).
    def pairKey(a: String, b: String): (String, String) = if (a <= b) (a, b) else (b, a)
    val ringPairs = rings.ringLinks.flatMap { label =>
      topo.find(_.id == label).map(c => pairKey(c.from, c.to))
    }.toSet

    val activeClasses = Seq("req", "rsp", "dat") ++ (if (v2.chi.dvmEnable) Seq("snp") else Nil)
    val channels = sortedLinks.flatMap { l =>
      val classes = activeClasses.filter(l.channels.contains)
      val halves = if (ringPairs.contains(pairKey(l.from, l.to))) Seq(0, 1) else Seq(0)
      for {
        (f, t) <- Seq((l.from, l.to), (l.to, l.from))
        c <- classes
        h <- halves
      } yield Channel(s"$f->$t/$c/h$h", s"$f->$t/$c/h$h", f, t, c, h)
    }.distinctBy(_.id)

    // Per-(class, half) router dependencies; no cross-class edges (GEN-10).
    val deps = mutable.Map.empty[String, mutable.Set[String]]
    for (c <- activeClasses; h <- Seq(0, 1)) {
      val vs = channels.filter(ch => ch.cls == c && ch.half == h)
      if (vs.nonEmpty) buildDependencies(vs, routerTuples).foreach { case (k, v) =>
        deps.getOrElseUpdate(k, mutable.Set.empty) ++= v
      }
    }

    // Cut semantics (SEM-09): a dateline direction L removes the outgoing
    // dependency edges of BOTH (L,class,h0) and (L,class,h1) and adds the
    // transition (L,class,h0) -> (L,class,h1); half 1 never transitions back.
    val cutIds = (for (l <- rings.datelines; c <- activeClasses; h <- Seq(0, 1))
      yield s"$l/$c/h$h").toSet
    val cutDeps = applyCuts(deps, cutIds)
    val ids = channels.map(_.id).toSet
    val finalDeps = (for (l <- rings.datelines; c <- activeClasses
        if ids.contains(s"$l/$c/h0") && ids.contains(s"$l/$c/h1"))
      yield (s"$l/$c/h0", s"$l/$c/h1")).foldLeft(cutDeps) { case (m, (f, t)) =>
        m.updated(f, (m.getOrElse(f, Nil) :+ t).sorted)
    }

    FabricGraph(nodes, channels, finalDeps,
      rings.datelines, rings.autoDatelines, rings.ringLinks, errors.toList)
  }

  private def labelOfChannel(channels: Seq[Channel], id: String): String =
    channels.find(_.id == id).map(_.label).getOrElse(id)

  /** Router turn dependencies shared by fromIr/fromV2. Routers are passed as
    * (name, routing, pos) tuples so both IR versions reuse the same algorithm.
    */
  private def buildDependencies(
      channels: Seq[Channel],
      routers: Seq[(String, String, Option[(Int, Int)])]
  ): mutable.Map[String, mutable.Set[String]] = {
    val routerByName = routers.map(r => r._1 -> r).toMap

    /** Sign of the hop from `from` to `to` when both have coordinates. */
    def hopDir(from: String, to: String): Option[(Int, Int)] =
      for {
        a <- routerByName.get(from).flatMap(_._3)
        b <- routerByName.get(to).flatMap(_._3)
      } yield (Integer.signum(b._1 - a._1), Integer.signum(b._2 - a._2))

    /** XY turn model: X links may turn to Y, Y links may only continue in Y;
      * no 180-degree reversal. Non-router peers are unrestricted.
      */
    def turnAllowed(r: (String, String, Option[(Int, Int)]), in: Channel, out: Channel): Boolean =
      if (r._2 != "xy") true
      else if (routerByName.get(in.from).isEmpty || routerByName.get(out.to).isEmpty) true
      else (hopDir(in.from, r._1), hopDir(r._1, out.to)) match {
        case (Some((idx, idy)), Some((odx, ody))) =>
          val inHorizontal = idy == 0 && idx != 0
          val inVertical = idx == 0 && idy != 0
          if (inHorizontal) if (odx == 0) ody != 0 else odx == idx
          else if (inVertical) odx == 0 && ody == idy
          else false
        case _ => true
      }

    val deps = mutable.Map.empty[String, mutable.Set[String]]
    for (r <- routers) {
      val incoming = channels.filter(_.to == r._1).sortBy(_.id)
      val outgoing = channels.filter(_.from == r._1).sortBy(_.id)
      for (in <- incoming; out <- outgoing) {
        // A packet never turns back to the node it just came from; a
        // self-link is a real loop and is not excluded.
        val uTurn = in.from == out.to && in.from != r._1
        if (!uTurn && turnAllowed(r, in, out)) {
          deps.getOrElseUpdate(in.id, mutable.Set.empty) += out.id
        }
      }
    }
    deps
  }

  private final case class RingAnalysis(
      datelines: Seq[String],
      autoDatelines: Seq[String],
      ringLinks: Seq[String],
      errors: Seq[String])

  /** Shared SCC + simple-ring + dateline selection (SEM-09). Only simple
    * router-router cycles may be cut by a dateline; anything denser needs a
    * turn model or turn VCs. Explicit datelines win, otherwise the
    * lexicographically smallest ring label is the autoDateline.
    */
  private def analyzeRings(
      channels: Seq[Channel],
      deps: mutable.Map[String, mutable.Set[String]],
      datelineSpec: Map[String, Boolean],
      isRouterHop: Channel => Boolean,
      cycleError: String => String
  ): RingAnalysis = {
    val sccs = stronglyConnected(channels.map(_.id).sorted, deps.map { case (k, v) => k -> v.toSeq }.toMap)
    val datelines = mutable.ListBuffer.empty[String]
    val autoDatelines = mutable.ListBuffer.empty[String]
    val ringLinks = mutable.ListBuffer.empty[String]
    val errors = mutable.ListBuffer.empty[String]

    sccs.filter(_.size > 1).foreach { scc =>
      val inSet = scc.toSet
      val succIn = scc.map(v => v -> deps.getOrElse(v, mutable.Set.empty).filter(inSet.contains).toSeq.sorted).toMap
      val predIn = mutable.Map.empty[String, Int].withDefaultValue(0)
      succIn.values.flatten.foreach(v => predIn(v) += 1)
      val simpleRing = scc.forall(v => succIn(v).size == 1 && predIn(v) == 1)
      val allRouterHops = scc.forall(id => isRouterHop(channels.find(_.id == id).get))
      if (!simpleRing || !allRouterHops) {
        val path = walkCycle(scc, succIn).map(labelOfChannel(channels, _))
        errors += cycleError(path.mkString(" -> "))
      } else {
        val labels = scc.map(labelOfChannel(channels, _)).sorted
        ringLinks ++= labels
        val explicit = labels.filter(l => datelineSpec.getOrElse(l, false))
        if (explicit.nonEmpty) datelines ++= explicit
        else {
          val auto = labels.head
          datelines += auto
          autoDatelines += auto
        }
      }
    }
    RingAnalysis(datelines.toList, autoDatelines.toList, ringLinks.toList, errors.toList)
  }

  /** A cut removes the CDG edges leaving the cut vertices (v1: the directed
    * link; v2: both VC halves of the cut direction). Keys are preserved.
    */
  private def applyCuts(
      deps: mutable.Map[String, mutable.Set[String]],
      cutIds: Set[String]
  ): Map[String, Seq[String]] =
    deps.map { case (k, v) =>
      if (cutIds.contains(k)) k -> Seq.empty[String] else k -> v.toSeq.sorted
    }.toMap

  /** Deterministic cycle walk inside one SCC (used for error text only). */
  private def walkCycle(scc: Seq[String], succ: Map[String, Seq[String]]): Seq[String] = {
    val start = scc.sorted.head
    val path = mutable.ArrayBuffer[String](start)
    val pos = mutable.Map(start -> 0)
    var cur = start
    var cycle: Seq[String] = Nil
    while (cycle.isEmpty) {
      val next = succ.getOrElse(cur, Nil).headOption.getOrElse(cur)
      if (pos.contains(next)) cycle = path.drop(pos(next)).toSeq :+ next
      else {
        pos(next) = path.length
        path += next
        cur = next
      }
    }
    cycle
  }

  private def stronglyConnected(
      vertices: Seq[String],
      edges: Map[String, Seq[String]]
  ): Seq[Seq[String]] = {
    var index = 0
    val idx = mutable.Map.empty[String, Int]
    val low = mutable.Map.empty[String, Int]
    val onStack = mutable.Set.empty[String]
    val stack = mutable.ArrayBuffer.empty[String]
    val result = mutable.ListBuffer.empty[Seq[String]]

    def visit(v: String): Unit = {
      idx(v) = index
      low(v) = index
      index += 1
      stack += v
      onStack += v
      for (w <- edges.getOrElse(v, Nil).distinct.sorted) {
        if (!idx.contains(w)) {
          visit(w)
          low(v) = math.min(low(v), low(w))
        } else if (onStack.contains(w)) {
          low(v) = math.min(low(v), idx(w))
        }
      }
      if (low(v) == idx(v)) {
        val comp = mutable.ListBuffer.empty[String]
        var w = ""
        do {
          w = stack.remove(stack.length - 1)
          onStack -= w
          comp += w
        } while (w != v)
        result += comp.toList
      }
    }

    vertices.foreach(v => if (!idx.contains(v)) visit(v))
    result.toSeq
  }
}