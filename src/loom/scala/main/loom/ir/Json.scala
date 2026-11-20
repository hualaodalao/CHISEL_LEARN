package loom.ir

/** Minimal JSON AST + parser.
  *
  * The suite must build offline and produce byte-identical canonical output,
  * so we avoid pulling a JSON library. Objects keep field order for parsing,
  * but canonical rendering always sorts keys.
  */
sealed trait JValue

final case class JObj(fields: Vector[(String, JValue)]) extends JValue {
  def get(key: String): Option[JValue] = fields.collectFirst { case (k, v) if k == key => v }
}

final case class JArr(items: Vector[JValue]) extends JValue
final case class JStr(value: String) extends JValue
final case class JNum(value: BigDecimal) extends JValue
final case class JBool(value: Boolean) extends JValue
case object JNull extends JValue

object Json {
  final class ParseException(msg: String) extends Exception(msg)

  def parse(text: String): JValue = new Parser(text).parseDocument()

  private final class Parser(s: String) {
    private var i = 0

    def parseDocument(): JValue = {
      skipWs()
      val v = parseValue()
      skipWs()
      if (i != s.length) fail("trailing characters")
      v
    }

    private def fail(msg: String): Nothing =
      throw new ParseException(s"$msg at offset $i")

    private def skipWs(): Unit =
      while (i < s.length && s.charAt(i).isWhitespace) i += 1

    private def parseValue(): JValue = {
      skipWs()
      if (i >= s.length) fail("unexpected end of input")
      s.charAt(i) match {
        case '{' => parseObject()
        case '[' => parseArray()
        case '"' => JStr(parseString())
        case 't' => expect("true"); JBool(true)
        case 'f' => expect("false"); JBool(false)
        case 'n' => expect("null"); JNull
        case c if c == '-' || c.isDigit => parseNumber()
        case c => fail(s"unexpected character '$c'")
      }
    }

    private def expect(lit: String): Unit = {
      if (!s.startsWith(lit, i)) fail(s"expected $lit")
      i += lit.length
    }

    private def parseObject(): JObj = {
      i += 1
      skipWs()
      val buf = Vector.newBuilder[(String, JValue)]
      val seen = scala.collection.mutable.HashSet.empty[String]
      if (i < s.length && s.charAt(i) == '}') {
        i += 1
        return JObj(buf.result())
      }
      var done = false
      while (!done) {
        skipWs()
        if (i >= s.length || s.charAt(i) != '"') fail("expected string key")
        val k = parseString()
        skipWs()
        if (i >= s.length || s.charAt(i) != ':') fail("expected ':'")
        i += 1
        val v = parseValue()
        skipWs()
        if (!seen.add(k)) fail(s"duplicate key '$k'")
        buf += (k -> v)
        if (i >= s.length) fail("unterminated object")
        s.charAt(i) match {
          case ',' => i += 1
          case '}' => i += 1; done = true
          case c => fail(s"expected ',' or '}' but got '$c'")
        }
      }
      JObj(buf.result())
    }

    private def parseArray(): JArr = {
      i += 1
      skipWs()
      val buf = Vector.newBuilder[JValue]
      if (i < s.length && s.charAt(i) == ']') {
        i += 1
        return JArr(buf.result())
      }
      var done = false
      while (!done) {
        buf += parseValue()
        skipWs()
        if (i >= s.length) fail("unterminated array")
        s.charAt(i) match {
          case ',' => i += 1
          case ']' => i += 1; done = true
          case c => fail(s"expected ',' or ']' but got '$c'")
        }
      }
      JArr(buf.result())
    }

    private def parseString(): String = {
      if (i >= s.length || s.charAt(i) != '"') fail("expected '\"'")
      i += 1
      val sb = new StringBuilder
      while (i < s.length && s.charAt(i) != '"') {
        val c = s.charAt(i)
        if (c == '\\') {
          i += 1
          if (i >= s.length) fail("unterminated escape")
          s.charAt(i) match {
            case '"' => sb.append('"'); i += 1
            case '\\' => sb.append('\\'); i += 1
            case '/' => sb.append('/'); i += 1
            case 'b' => sb.append('\b'); i += 1
            case 'f' => sb.append('\f'); i += 1
            case 'n' => sb.append('\n'); i += 1
            case 'r' => sb.append('\r'); i += 1
            case 't' => sb.append('\t'); i += 1
            case 'u' =>
              if (i + 4 >= s.length) fail("bad unicode escape")
              val hex = s.substring(i + 1, i + 5)
              sb.append(Integer.parseInt(hex, 16).toChar)
              i += 5
            case c2 => fail(s"bad escape \\$c2")
          }
        } else {
          sb.append(c)
          i += 1
        }
      }
      if (i >= s.length) fail("unterminated string")
      i += 1
      sb.toString
    }

    private def parseNumber(): JNum = {
      val start = i
      if (s.charAt(i) == '-') i += 1
      if (i >= s.length || !s.charAt(i).isDigit) fail("bad number")
      while (i < s.length && s.charAt(i).isDigit) i += 1
      if (i < s.length && s.charAt(i) == '.') {
        i += 1
        if (i >= s.length || !s.charAt(i).isDigit) fail("bad fraction")
        while (i < s.length && s.charAt(i).isDigit) i += 1
      }
      if (i < s.length && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
        i += 1
        if (i < s.length && (s.charAt(i) == '+' || s.charAt(i) == '-')) i += 1
        if (i >= s.length || !s.charAt(i).isDigit) fail("bad exponent")
        while (i < s.length && s.charAt(i).isDigit) i += 1
      }
      val raw = s.substring(start, i)
      try JNum(BigDecimal(raw))
      catch { case _: NumberFormatException => fail("bad number") }
    }
  }
}
