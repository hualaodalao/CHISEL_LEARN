package loom.graph

import loom.ir.{Ir, JBool, JArr, JObj, JStr, JValue}
import scala.collection.mutable

/** A directed link between two fabric nodes. */
final case class Channel(id: String, label: String, from: String, to: String)

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
  def findCycle: Option[Seq[String]] = {
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

    val vertices = channels.map(_.id).sorted
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
    val chJson = JArr(channels.map(c =>
      JObj(Vector("from" -> JStr(c.from), "to" -> JStr(c.to)))).toVector)
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

    /** Sign of the hop from `from` to `to` when both have coordinates. */
    def hopDir(from: String, to: String): Option[(Int, Int)] =
      for {
        a <- routerByName.get(from).flatMap(_.pos)
        b <- routerByName.get(to).flatMap(_.pos)
      } yield (Integer.signum(b._1 - a._1), Integer.signum(b._2 - a._2))

    /** XY turn model: X links may turn to Y, Y links may only continue in Y;
      * no 180-degree reversal. Non-router peers (mnet/snet) are unrestricted.
      */
    def turnAllowed(r: loom.ir.RouterSpec, in: Channel, out: Channel): Boolean =
      if (r.routing != "xy") true
      else if (routerByName.get(in.from).isEmpty || routerByName.get(out.to).isEmpty) true
      else (hopDir(in.from, r.name), hopDir(r.name, out.to)) match {
        case (Some((idx, idy)), Some((odx, ody))) =>
          val inHorizontal = idy == 0 && idx != 0
          val inVertical = idx == 0 && idy != 0
          if (inHorizontal) if (odx == 0) ody != 0 else odx == idx
          else if (inVertical) odx == 0 && ody == idy
          else false
        case _ => true
      }

    val deps = mutable.Map.empty[String, mutable.Set[String]]
    for (r <- ir.routers) {
      val incoming = channels.filter(_.to == r.name).sortBy(_.id)
      val outgoing = channels.filter(_.from == r.name).sortBy(_.id)
      for (in <- incoming; out <- outgoing) {
        // A packet never turns back to the node it just came from; a
        // self-link is a real loop and is not excluded.
        val uTurn = in.from == out.to && in.from != r.name
        if (!uTurn && turnAllowed(r, in, out)) {
          deps.getOrElseUpdate(in.id, mutable.Set.empty) += out.id
        }
      }
    }

    val selfLink = channels.filter(c => c.from == c.to)
    val errors = mutable.ListBuffer.empty[String]
    selfLink.foreach(c => errors += s"LM-G1: self-link ${c.label} is not allowed")

    // SCC over the CDG. Only simple router-router cycles may be cut by a
    // dateline; anything denser needs a turn model or turn VCs.
    val sccs = stronglyConnected(channels.map(_.id).sorted, deps.map { case (k, v) => k -> v.toSeq }.toMap)
    val datelineSpec = ir.links.map(l => s"${l.from}->${l.to}" -> l.dateline).toMap
    val datelines = mutable.ListBuffer.empty[String]
    val autoDatelines = mutable.ListBuffer.empty[String]
    val ringLinks = mutable.ListBuffer.empty[String]

    sccs.filter(_.size > 1).foreach { scc =>
      val inSet = scc.toSet
      val succIn = scc.map(v => v -> deps.getOrElse(v, mutable.Set.empty).filter(inSet.contains).toSeq.sorted).toMap
      val predIn = mutable.Map.empty[String, Int].withDefaultValue(0)
      succIn.values.flatten.foreach(v => predIn(v) += 1)
      val simpleRing = scc.forall(v => succIn(v).size == 1 && predIn(v) == 1)
      val allRouterHops = scc.forall { id =>
        val c = channels.find(_.id == id).get
        routerByName.contains(c.from) && routerByName.contains(c.to)
      }
      if (!simpleRing || !allRouterHops) {
        val path = walkCycle(scc, succIn).map(labelOfChannel(channels, _))
        errors += s"LM-G1: CDG cycle: ${path.mkString(" -> ")} (not a ring; use a turn model or turn VCs)"
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

    // A cut removes the CDG edges leaving that link: packets crossing the
    // dateline continue in the second VC half, whose subgraph is a chain.
    val cutIds = datelines.map(l => channels.find(_.label == l).get.id).toSet
    val cutDeps = deps.map { case (k, v) =>
      if (cutIds.contains(k)) k -> mutable.Set.empty[String] else k -> v
    }.toMap

    FabricGraph(nodes, channels, cutDeps.map { case (k, v) => k -> v.toSeq.sorted }.toMap,
      datelines.toList, autoDatelines.toList, ringLinks.toList, errors.toList)
  }

  private def labelOfChannel(channels: Seq[Channel], id: String): String =
    channels.find(_.id == id).map(_.label).getOrElse(id)

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
