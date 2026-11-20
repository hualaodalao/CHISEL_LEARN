package loom.ir

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** Deterministic JSON rendering and hashing.
  *
  * Canonical form: keys sorted, no whitespace, numbers without trailing
  * zeros, strings with minimal escaping. The hash is the config identity
  * used for output paths and regression baselines.
  */
object Canonical {
  def render(v: JValue): String = v match {
    case JObj(fields) =>
      fields.sortBy(_._1).map { case (k, x) => quote(k) + ":" + render(x) }.mkString("{", ",", "}")
    case JArr(items) =>
      items.map(render).mkString("[", ",", "]")
    case JStr(s) => quote(s)
    case JNum(n) =>
      val plain = n.bigDecimal.stripTrailingZeros.toPlainString
      if (plain == "-0") "0" else plain
    case JBool(b) => b.toString
    case JNull => "null"
  }

  def sha256(s: String): String = {
    val md = MessageDigest.getInstance("SHA-256")
    md.digest(s.getBytes(StandardCharsets.UTF_8)).map(b => f"${b & 0xff}%02x").mkString
  }

  def hash(v: JValue): String = sha256(render(v))

  /** Stable, human-readable form for report files. Keys are sorted so that
    * two runs produce byte-identical reports.
    */
  def pretty(v: JValue, indent: Int = 2): String = {
    def pad(level: Int): String = " " * (indent * level)
    def go(x: JValue, level: Int): String = x match {
      case JObj(fields) if fields.nonEmpty =>
        val body = fields
          .sortBy(_._1)
          .map { case (k, y) => pad(level + 1) + quote(k) + ": " + go(y, level + 1) }
          .mkString(",\n")
        "{\n" + body + "\n" + pad(level) + "}"
      case JObj(_) => "{}"
      case JArr(items) if items.nonEmpty =>
        val body = items.map(y => pad(level + 1) + go(y, level + 1)).mkString(",\n")
        "[\n" + body + "\n" + pad(level) + "]"
      case JArr(_) => "[]"
      case other => render(other)
    }
    go(v, 0)
  }

  private def quote(s: String): String = {
    val sb = new StringBuilder("\"")
    s.foreach {
      case '"' => sb.append("\\\"")
      case '\\' => sb.append("\\\\")
      case '\b' => sb.append("\\b")
      case '\f' => sb.append("\\f")
      case '\n' => sb.append("\\n")
      case '\r' => sb.append("\\r")
      case '\t' => sb.append("\\t")
      case c if c < ' ' => sb.append(f"\\u${c.toInt}%04x")
      case c => sb.append(c)
    }
    sb.append('"').toString
  }
}
