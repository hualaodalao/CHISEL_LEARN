import chisel3._
import chisel3.util._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec

import scala.collection.mutable

/** Direct coverage of the byte reader/converter/writer, without the command engine.
  * The taps observe handshakes; they do not add another consumer to the stream.
  */
class ForagerBeeBytePipeHarness(cfg: ForagerBeeConfig) extends Module {
  val io = IO(new Bundle {
    val start = Input(Bool())
    val srcAddr = Input(UInt(cfg.addressWidth.W))
    val dstAddr = Input(UInt(cfg.addressWidth.W))
    val srcBytes = Input(UInt(24.W))
    val dstBytes = Input(UInt(24.W))
    val srcFmt = Input(FbDataFmt())
    val dstFmt = Input(FbDataFmt())
    val bus = Vec(2, master(new FbStreamBus(cfg.dataWidth, cfg.addressWidth)))
    val readerDone = Output(Bool())
    val writerDone = Output(Bool())
    val err = Output(Bool())
    val packetValid = Output(Bool())
    val packetReady = Output(Bool())
    val packet = Output(new FbBytePacket(cfg.dataWidth))
  })
  val reader = Module(new FbByteReader(cfg))
  val converter = Module(new FbFormatConverter(cfg.dataWidth, cfg.converterPipeStages))
  val writer = Module(new FbByteWriter(cfg))
  reader.io.start := io.start
  reader.io.srcAddr := io.srcAddr
  reader.io.byteCount := io.srcBytes
  writer.io.start := io.start
  writer.io.dstAddr := io.dstAddr
  writer.io.byteCount := io.dstBytes
  converter.io.flush := io.start
  converter.io.cfg_enable := true.B
  converter.io.cfg_srcFmt := io.srcFmt
  converter.io.cfg_dstFmt := io.dstFmt
  converter.io.in.valid := reader.io.out.valid
  converter.io.in.bits := reader.io.out.bits.data
  converter.io.inBytes := reader.io.out.bits.bytes
  converter.io.inLast := reader.io.out.bits.last
  reader.io.out.ready := converter.io.in.ready
  writer.io.in.valid := converter.io.out.valid
  writer.io.in.bits.data := converter.io.out.bits
  writer.io.in.bits.bytes := converter.io.outBytes
  writer.io.in.bits.last := converter.io.outLast
  converter.io.out.ready := writer.io.in.ready
  io.bus(0) <> reader.io.bus
  io.bus(1) <> writer.io.bus
  io.readerDone := reader.io.done
  io.writerDone := writer.io.done
  io.err := reader.io.err || writer.io.err
  io.packetValid := converter.io.out.valid
  io.packetReady := writer.io.in.ready
  io.packet := writer.io.in.bits
}

/** Golden results use logical elements/byte addresses, never rounded bus lengths.
  * All destinations, source gaps, descriptor high bytes and two-beat margins are
  * poisoned. A strobe must write an expected byte exactly once, including scales.
  * These are contract regressions for the byte-aware RTL, not legacy alignment
  * tests. No chain, atomic, or global conversion fields are referenced here.
  */
class ForagerBeeByteSpec extends AnyFlatSpec {
  behavior of "ForagerBee exact byte transfers"

  private case class Cmd(
      tag: Int = 0, op: String = "COPY", shape: Vector[Int] = Vector(1),
      srcStride: Vector[Int] = Vector(1), dstStride: Vector[Int] = Vector(1),
      srcAddr: Int = 0, dstAddr: Int = 0,
      start: Vector[Int] = Vector.empty, perm: Vector[Int] = Vector.empty,
      before: Vector[Int] = Vector(0, 0), after: Vector[Int] = Vector(0, 0),
      padValue: Long = 0, srcFmt: Int = 3, dstFmt: Int = 3,
      kernel: Vector[Int] = Vector(0, 0), stride: Vector[Int] = Vector(0, 0),
      pad: Vector[Int] = Vector(0, 0), dilation: Vector[Int] = Vector(0, 0),
      inShape: Vector[Int] = Vector(0, 0, 0),
      listAddr: Int = 0, entryCount: Int = 0, scaleAddr: Int = 0)
  private case class Job(name: String, cmd: Cmd, writes: Map[Int, Int], error: Boolean = false)
  private case class Suite(initial: Map[Int, Int], jobs: Vector[Job]) {
    val writes: Map[Int, Int] = jobs.flatMap(_.writes).toMap
    require(writes.size == jobs.map(_.writes.size).sum, "jobs must have disjoint destinations")
    val golden: Map[Int, Int] = initial ++ writes
  }

  private def at(xs: Vector[Int], i: Int): Int = xs.lift(i).getOrElse(0)
  private def elemBytes(fmt: Int): Int = fmt match {
    case 0 | 8 => 4
    case 1 | 2 | 9 => 2
    case 3 | 4 | 5 => 1
    case _ => throw new IllegalArgumentException(s"no byte-sized element for format $fmt")
  }
  private def rowBytes(n: Int, fmt: Int): Int = if (fmt == 10) n / 2 else n * elemBytes(fmt)
  private def le(value: Long, n: Int): Vector[Int] =
    Vector.tabulate(n)(i => ((value >>> (8 * i)) & 255).toInt)
  private def readLe(mem: collection.Map[Int, Int], addr: Int, n: Int): Int =
    (0 until n).foldLeft(0)((v, i) => v | (mem(addr + i) << (8 * i)))
  private def packed(bytes: Seq[Int]): BigInt =
    bytes.zipWithIndex.foldLeft(BigInt(0)) { case (v, (b, i)) => v | (BigInt(b) << (8 * i)) }

  // Test only normal floats, signed zeros and infinities in float conversion:
  // FP16 subnormal interpretation/NaN payload policy is deliberately not assumed.
  private val floats32 = Vector(0x00000000, 0x80000000, 0x3f800000, 0xbf000000,
    0x3f801000, 0x3f803000, 0x3ffff000, 0x40000000, 0xc0400000,
    0x3e800000, 0x477fe000, 0x7f800000, 0xff800000)
  private val floats16 = Vector(0x0000, 0x8000, 0x3c00, 0xb800, 0x3c01, 0x3fff,
    0x4000, 0xc200, 0x3400, 0x7bff, 0x7c00, 0xfc00)
  private val floatsBf = Vector(0x0000, 0x8000, 0x3f80, 0xbf00, 0x3f81,
    0x3fff, 0x4000, 0xc040, 0x3e80, 0x4080)

  private def roundEven(x: Long, shift: Int): Long = {
    val q = x >>> shift
    val rem = x & ((1L << shift) - 1)
    val half = 1L << (shift - 1)
    q + (if (rem > half || (rem == half && (q & 1) != 0)) 1 else 0)
  }
  private def toFloat32(bits: Int, fmt: Int): Int = fmt match {
    case 0 => bits
    case 2 => bits << 16
    case 1 =>
      val sign = (bits & 0x8000) << 16
      val exp = (bits >>> 10) & 31
      val mant = bits & 1023
      require(exp != 0 || mant == 0, "float conversion fixtures must not contain subnormals")
      if (exp == 0) sign
      else sign | ((if (exp == 31) 255 else exp + 112) << 23) | (mant << 13)
    case _ => throw new IllegalArgumentException(s"not a float: $fmt")
  }
  private def convert(bits: Int, src: Int, dst: Int): Int = {
    if (src == dst) bits
    else {
      val f = toFloat32(bits, src)
      dst match {
        case 0 => f
        case 2 => roundEven(f.toLong & 0xffffffffL, 16).toInt & 0xffff
        case 1 =>
          val sign = (f >>> 16) & 0x8000
          val exp = (f >>> 23) & 255
          val mant = f & 0x7fffff
          require(exp != 255 || mant == 0, "NaN payload is not part of these fixtures")
          if (exp < 113) sign // documented FTZ conversion
          else if (exp > 142) sign | 0x7c00
          else {
            val significand = roundEven(0x800000L | mant, 13).toInt
            val carry = if (significand == 2048) 1 else 0
            val e16 = exp - 112 + carry
            if (e16 >= 31) sign | 0x7c00
            else sign | (e16 << 10) | (significand & 1023)
          }
        case _ => throw new IllegalArgumentException(s"unsupported conversion $src->$dst")
      }
    }
  }

  /** Independent Scala adaptation of the existing private mxQuantizeRef golden.
    * RNE precedes FTZ/overflow; zero/subnormal/NaN do not contribute to scale.
    */
  private def quantize(block: Vector[Int], dst: Int): (Vector[Int], Int) = {
    val mantBits = if (dst == 4) 3 else 2
    val bias = if (dst == 4) 7 else 15
    val maxNormal = if (dst == 4) 14 else 30
    val maxExp = block.foldLeft(-128) { (max, raw) =>
      val e = (raw >>> 23) & 255
      val m = raw & 0x7fffff
      if (e == 0 || (e == 255 && m != 0)) max
      else math.max(max, if (e == 255) 127 else e - 127)
    }
    val bytes = block.map { raw =>
      val sign = (raw >>> 31) << 7
      val e = (raw >>> 23) & 255
      val m = raw & 0x7fffff
      val rounded = roundEven(m.toLong, 23 - mantBits).toInt
      val carry = rounded >>> mantBits
      val newExp = e - 127 - maxExp + bias + carry
      val sat = sign | (maxNormal << mantBits) | ((1 << mantBits) - 1)
      if (e == 0) sign
      else if (e == 255 && m != 0 && dst == 4) sign | 0x7f
      else if (e == 255 || newExp > maxNormal) sat
      else if (newExp <= 0) sign
      else sign | (newExp << mantBits) | (rounded & ((1 << mantBits) - 1))
    }
    (bytes, math.max(0, maxExp + 127) & 255)
  }

  private def rows(shape: Vector[Int]): Vector[Vector[Int]] =
    (1 until shape.size).foldLeft(Vector(Vector.fill(shape.size)(0))) { (rs, d) =>
      rs.flatMap(idx => Vector.tabulate(shape(d))(i => idx.updated(d, i)))
    }
  private def srcRow(c: Cmd, idx: Vector[Int]): Int = {
    val off = (1 until c.shape.size).map { d =>
      val sd = if (c.op == "PERMUTE") c.perm(d) else d
      (idx(d) + at(c.start, d)) * at(c.srcStride, sd)
    }.sum
    c.srcAddr + off + rowBytes(at(c.start, 0), c.srcFmt)
  }
  private def dstRow(c: Cmd, idx: Vector[Int]): Int =
    c.dstAddr + (1 until c.shape.size).map(d => idx(d) * at(c.dstStride, d)).sum

  private class Build(cfg: ForagerBeeConfig) {
    val mem = mutable.HashMap.empty[Int, Int]
    val jobs = mutable.ArrayBuffer.empty[Job]
    private var cursor = 0x1000
    private var seed = 0
    private val margin = 2 * cfg.beatBytes
    def alloc(size: Int, skew: Int): Int = {
      require(size > 0 && skew >= 0 && skew < cfg.beatBytes)
      val aligned = ((cursor + cfg.beatBytes - 1) / cfg.beatBytes) * cfg.beatBytes
      val base = aligned + margin + skew
      for (a <- base - margin until base + size + margin) mem(a) = 0xa5
      cursor = base + size + margin + cfg.beatBytes
      base
    }
    def put(base: Int, bytes: Seq[Int]): Unit =
      bytes.zipWithIndex.foreach { case (b, i) => mem(base + i) = b }
    private def register(name: String, c: Cmd, output: collection.Map[Int, Int], err: Boolean = false): Unit =
      jobs += Job(name, c.copy(tag = jobs.size + 1), output.toMap, err)
    private def emit(out: mutable.Map[Int, Int], base: Int, bytes: Seq[Int]): Unit =
      bytes.zipWithIndex.foreach { case (b, i) =>
        require(!out.contains(base + i), s"overlapping golden output at ${base + i}")
        out(base + i) = b
      }
    private def sourcePattern(base: Int, size: Int): Unit = {
      seed += 1
      for (i <- 0 until size) mem(base + i) = (i * 37 + seed * 29 + 11) & 255
    }
    private def floatSource(c: Cmd): Unit = {
      if (Set(0, 1, 2).contains(c.srcFmt)) {
        val values = c.srcFmt match {
          case 0 => floats32
          case 1 => floats16
          case 2 => floatsBf
        }
        for ((idx, r) <- rows(c.shape).zipWithIndex; i <- 0 until c.shape.head)
          put(srcRow(c, idx) + i * elemBytes(c.srcFmt),
            le(values((r * 3 + i) % values.size).toLong, elemBytes(c.srcFmt)))
      }
    }
    def tensor(name: String, template: Cmd, srcSkew: Int, dstSkew: Int): Unit = {
      val rs = rows(template.shape)
      val sourceSize = rs.map(srcRow(template, _)).max + rowBytes(template.shape.head, template.srcFmt)
      val paddedCols = template.shape.updated(0, template.shape.head + template.before(0) + template.after(0))
      val paddedShape = if (template.shape.size > 1)
        paddedCols.updated(1, template.shape(1) + template.before(1) + template.after(1))
      else paddedCols
      val destSize = if (template.op == "TRANSPOSE") {
        rs.map(idx => template.dstAddr +
          (2 until template.shape.size).map(d => idx(d) * template.dstStride(d)).sum).max +
          (template.shape.head - 1) * template.dstStride.head + template.shape(1) * elemBytes(template.dstFmt)
      } else rows(paddedShape).map(dstRow(template, _)).max + rowBytes(paddedShape.head, template.dstFmt)
      val c0 = template.copy(srcAddr = alloc(sourceSize, srcSkew), dstAddr = alloc(destSize, dstSkew))
      val c = if (Set(4, 5).contains(template.dstFmt)) {
        val scaleSize = rs.map(idx => dstRow(template, idx)).max + template.shape.head / 16
        c0.copy(scaleAddr = alloc(scaleSize, 0))
      } else c0
      sourcePattern(c.srcAddr, sourceSize)
      floatSource(c)
      val out = mutable.HashMap.empty[Int, Int]
      if (c.op == "TRANSPOSE") {
        val eb = elemBytes(c.srcFmt)
        for (idx <- rs if idx(1) == 0; r <- 0 until c.shape(1); col <- 0 until c.shape.head) {
          val src = srcRow(c, idx.updated(1, r)) + col * eb
          val dst = c.dstAddr + (2 until c.shape.size).map(d => idx(d) * c.dstStride(d)).sum +
            col * c.dstStride.head + r * eb
          emit(out, dst, Vector.tabulate(eb)(i => mem(src + i)))
        }
      } else if (Set(4, 5).contains(c.dstFmt)) {
        // Three independent scales per row for FP32 and two for FP16.
        // Include RNE ties/carry, signed zero, FTZ, NaN and infinity in MX only.
        val mxVals = Vector(0x3f800000, 0x3f880000, 0x3f980000, 0x3ffc0000,
          0xbf800000, 0x00000000, 0x80000000, 0x00000001,
          0x3b800000, 0x40000000, 0xc1000000, 0x3f000000,
          0x3e000000, 0x3fe00000, 0x3ff80000, 0x41000000)
        for ((idx, r) <- rs.zipWithIndex; b <- 0 until c.shape.head / 16) {
          val raw32 = Vector.tabulate(16) { i =>
            if (c.srcFmt == 0 && r == 1 && b == 1 && i == 14) 0x7fc00000
            else if (c.srcFmt == 0 && r == 1 && b == 2 && i == 15) 0xff800000
            else if (r == 0 && b == 1) (if (i % 2 == 0) 0 else 0x80000000)
            else {
              val raw = mxVals((i + r * 3) % mxVals.size)
              val exp = (raw >>> 23) & 255
              if (exp == 0) raw else raw + ((b - r) << 23)
            }
          }
          val encoded = raw32.map(v => if (c.srcFmt == 0) v else convert(v, 0, c.srcFmt))
          val normalized = encoded.map { v =>
            if (c.srcFmt == 0) v
            else if (((v >>> 10) & 31) == 0) (v & 0x8000) << 16
            else toFloat32(v, c.srcFmt)
          }
          for (i <- 0 until 16) put(srcRow(c, idx) + (b * 16 + i) * elemBytes(c.srcFmt),
            le(encoded(i).toLong, elemBytes(c.srcFmt)))
          val (bytes, scale) = quantize(normalized, c.dstFmt)
          emit(out, dstRow(c, idx) + b * 16, bytes)
          val scaleRow = c.scaleAddr + (1 until c.shape.size).map(d => idx(d) * c.dstStride(d)).sum
          emit(out, scaleRow + b, Vector(scale))
        }
      } else if (c.srcFmt == 10) {
        require(c.before.forall(_ == 0) && c.after.forall(_ == 0))
        for (idx <- rs) emit(out, dstRow(c, idx),
          Vector.tabulate(rowBytes(c.shape.head, 10))(i => mem(srcRow(c, idx) + i)))
      } else {
        val se = elemBytes(c.srcFmt)
        val de = elemBytes(c.dstFmt)
        for (idx <- rows(paddedShape); col <- 0 until paddedShape.head) {
          val row = if (c.shape.size > 1) idx(1) else 0
          val inside = col >= c.before(0) && col < c.before(0) + c.shape.head &&
            row >= c.before(1) && row < c.before(1) + (if (c.shape.size > 1) c.shape(1) else 1)
          val value = if (!inside) c.padValue.toInt else {
            val srcIdx = if (c.shape.size > 1) idx.updated(1, row - c.before(1)) else idx
            val bits = readLe(mem, srcRow(c, srcIdx) + (col - c.before(0)) * se, se)
            convert(bits, c.srcFmt, c.dstFmt)
          }
          emit(out, dstRow(c, idx) + col * de, le(value.toLong, de))
        }
      }
      register(name, c, out)
    }

    def im2col(name: String, template: Cmd): Unit = {
      val Vector(channels, h, w) = template.inShape
      val Vector(kh, kw) = template.kernel
      val Vector(sh, sw) = template.stride
      val Vector(ph, pw) = template.pad
      val Vector(dh, dw) = template.dilation
      val ohCount = (h + 2 * ph - dh * (kh - 1) - 1) / sh + 1
      val owCount = (w + 2 * pw - dw * (kw - 1) - 1) / sw + 1
      val count = channels * h * w
      val outputCount = ohCount * owCount * channels * kh * kw
      val se = elemBytes(template.srcFmt)
      val de = elemBytes(template.dstFmt)
      val c = template.copy(op = "IM2COL", shape = Vector(count),
        srcStride = Vector(count * se), dstStride = Vector(outputCount * de),
        srcAddr = alloc(count * se, cfg.beatBytes - 3),
        dstAddr = alloc(outputCount * de, cfg.beatBytes - 1))
      for (i <- 0 until count) {
        val raw = c.srcFmt match {
          case 0 => floats32((i + 2) % floats32.size)
          case 1 => floats16((i + 2) % floats16.size)
          case _ => (i * 173 + 19) & 0xffff
        }
        put(c.srcAddr + i * se, le(raw.toLong, se))
      }
      val out = mutable.HashMap.empty[Int, Int]
      var element = 0
      // CHW input, output flatten order [oh, ow, c, kh, kw].
      for (oh <- 0 until ohCount; ow <- 0 until owCount; ch <- 0 until channels;
           kr <- 0 until kh; kc <- 0 until kw) {
        val y = oh * sh + kr * dh - ph
        val x = ow * sw + kc * dw - pw
        val v = if (y < 0 || y >= h || x < 0 || x >= w) c.padValue.toInt
          else convert(readLe(mem, c.srcAddr + ((ch * h + y) * w + x) * se, se), c.srcFmt, c.dstFmt)
        emit(out, c.dstAddr + element * de, le(v.toLong, de))
        element += 1
      }
      register(name, c, out)
    }

    def scatterGather(gather: Boolean, zero: Boolean = false): Unit = {
      val bb = cfg.beatBytes
      val lengths = if (zero) Vector(0) else Vector(1, bb - 3, bb + 5, 2 * bb + 7, 3)
      val addresses = lengths.zipWithIndex.map { case (n, i) => alloc(math.max(1, n), (bb - 2 + i * 5) % bb) }
      val linear = alloc(math.max(1, lengths.sum), bb - 1)
      val table = alloc(lengths.size * bb, 0)
      // V1: one descriptor per beat; low addressWidth bits, then 16-bit BYTE length.
      for (i <- lengths.indices) {
        val descriptor = BigInt(addresses(i)) | (BigInt(lengths(i)) << cfg.addressWidth)
        put(table + i * bb, Vector.tabulate((cfg.addressWidth + 16 + 7) / 8)(j =>
          ((descriptor >> (8 * j)) & 255).toInt))
      }
      val out = mutable.HashMap.empty[Int, Int]
      var offset = 0
      for (i <- lengths.indices) {
        val src = if (gather) addresses(i) else linear + offset
        val dst = if (gather) linear + offset else addresses(i)
        for (j <- 0 until lengths(i)) mem(src + j) = (i * 53 + j * 7 + 23) & 255
        emit(out, dst, Vector.tabulate(lengths(i))(j => mem(src + j)))
        offset += lengths(i) // no beat rounding between entries
      }
      val op = if (gather) "GATHER" else "SCATTER"
      val c = Cmd(op = op, shape = Vector(math.max(1, lengths.sum)),
        srcAddr = if (gather) addresses.head else linear,
        dstAddr = if (gather) linear else addresses.head,
        srcStride = Vector(math.max(1, lengths.sum)), dstStride = Vector(math.max(1, lengths.sum)),
        listAddr = table, entryCount = lengths.size)
      register(s"$op ${if (zero) "zero length error" else "arbitrary byte descriptors"}", c, out, zero)
    }
    def result: Suite = Suite(mem.toMap, jobs.toVector)
  }

  private def suite(cfg: ForagerBeeConfig): Suite = {
    val b = new Build(cfg)
    val bb = cfg.beatBytes
    val n = 2 * bb + 7
    val ss = Vector(n, n + 29, 2 * (n + 29) + 31,
      4 * (n + 29) + 99, 8 * (n + 29) + 241)
    val ds = Vector(n, n + 13, 2 * (n + 13) + 19,
      4 * (n + 13) + 71, 8 * (n + 13) + 181)
    b.tensor("5D cropped COPY independent strides", Cmd(shape = Vector(n, 2, 2, 1, 2),
      srcStride = ss, dstStride = ds, start = Vector(3, 1, 1, 0, 1)), bb - 2, 5)
    b.tensor("one-byte COPY at last source lane", Cmd(), bb - 1, 3)
    b.tensor("even INT4 short packed rows", Cmd(shape = Vector(10, 3),
      srcStride = Vector(5, 17), dstStride = Vector(5, 11), start = Vector(2, 1),
      srcFmt = 10, dstFmt = 10), bb - 2, bb - 1)
    b.tensor("5D PERMUTE with cropped source", Cmd(op = "PERMUTE", shape = Vector(9, 2, 2, 1, 2),
      srcStride = Vector(9, 31, 89, 211, 503), dstStride = Vector(9, 17, 47, 113, 263),
      start = Vector(2, 1, 0, 0, 1), perm = Vector(0, 4, 1, 3, 2)), bb - 3, 7)
    val odd = 2 * (bb / 4) + 1
    b.tensor("FP32 to FP16 odd compression rows and RNE", Cmd(shape = Vector(odd, 3),
      srcStride = Vector(odd * 4, odd * 4 + 11), dstStride = Vector(odd * 2, odd * 2 + 7),
      srcFmt = 0, dstFmt = 1), bb - 3, bb - 1)
    val expand = bb / 2 + 3
    b.tensor("FP16 to FP32 expanding tail rows", Cmd(shape = Vector(expand, 2),
      srcStride = Vector(expand * 2, expand * 2 + 9), dstStride = Vector(expand * 4, expand * 4 + 13),
      srcFmt = 1, dstFmt = 0), 1, bb - 2)
    b.tensor("BF16 to FP16 same-width short rows", Cmd(shape = Vector(7, 2),
      srcStride = Vector(14, 23), dstStride = Vector(14, 29), srcFmt = 2, dstFmt = 1), bb - 1, 3)
    for ((sf, df, value, name) <- Vector(
      (3, 3, 0xdeadbe6dL, "INT8"), (9, 9, 0xcafe8123L, "INT16"),
      (0, 1, 0xa5a5bc00L, "FP32 to FP16"))) {
      val se = elemBytes(sf)
      val de = elemBytes(df)
      b.tensor(s"$name low padValue before/after/whole rows across slices",
        Cmd(shape = Vector(5, 2, 2), srcStride = Vector(5 * se, 5 * se + 11, 4 * (5 * se + 11) + 17),
          dstStride = Vector(10 * de, 10 * de + 5, 5 * (10 * de + 5) + 13),
          start = Vector(1, 1, 1), before = Vector(3, 1), after = Vector(2, 2),
          padValue = value, srcFmt = sf, dstFmt = df), bb - 3, bb - 1)
    }
    b.im2col("IM2COL horizontal padding with multiple channels", Cmd(padValue = 0xdeadbe6dL,
      inShape = Vector(2, 3, 5), kernel = Vector(2, 3), stride = Vector(1, 1),
      pad = Vector(0, 1), dilation = Vector(1, 1)))
    b.im2col("IM2COL dW=2 with horizontal padding", Cmd(srcFmt = 9, dstFmt = 9, padValue = 0xcafe8123L,
      inShape = Vector(1, 3, 5), kernel = Vector(2, 3), stride = Vector(1, 2),
      pad = Vector(0, 2), dilation = Vector(1, 2)))
    b.im2col("IM2COL dH=2 vertical padding FP32 to FP16 kw=3", Cmd(srcFmt = 0, dstFmt = 1,
      inShape = Vector(1, 3, 4), kernel = Vector(3, 3), stride = Vector(2, 2),
      pad = Vector(2, 1), dilation = Vector(2, 1)))
    b.im2col("IM2COL FP16 to FP32 kw=1 with vertical padding", Cmd(srcFmt = 1, dstFmt = 0,
      inShape = Vector(1, 2, 3), kernel = Vector(2, 1), stride = Vector(1, 2),
      pad = Vector(1, 0), dilation = Vector(1, 2)))
    b.scatterGather(gather = true)
    b.scatterGather(gather = false)
    b.scatterGather(gather = true, zero = true)
    b.scatterGather(gather = false, zero = true)
    b.tensor("cropped unaligned TRANSPOSE tails in both tile directions",
      Cmd(op = "TRANSPOSE", shape = Vector(11, 7), srcStride = Vector(11, 30),
        dstStride = Vector(18, 0), start = Vector(2, 1)), bb - 1, bb - 3)
    // TRANSPOSE remains a 2D operation; exercise several tiles in BOTH axes.
    b.tensor("INT16 TRANSPOSE multiple row/column tiles with cropped source and tails",
      Cmd(op = "TRANSPOSE", shape = Vector(7, 9),
        srcStride = Vector(14, 23), dstStride = Vector(23, 0),
        start = Vector(1, 2), srcFmt = 9, dstFmt = 9), bb - 3, bb - 1)
    b.tensor("MX FP32 E4M3 unaligned rows three block16 scales",
      Cmd(shape = Vector(48, 2), srcStride = Vector(192, 205), dstStride = Vector(48, 59),
        srcFmt = 0, dstFmt = 4), bb - 3, bb - 1)
    b.tensor("MX FP16 E5M2 unaligned rows two block16 scales",
      Cmd(shape = Vector(32, 3), srcStride = Vector(64, 73), dstStride = Vector(32, 43),
        srcFmt = 1, dstFmt = 5), 1, bb - 2)
    b.tensor("short COPY recovery after SG errors and MX", Cmd(shape = Vector(3),
      srcStride = Vector(3), dstStride = Vector(3)), bb - 1, bb - 1)
    b.result
  }

  private def pokeCmd(p: FbCmd, c: Cmd): Unit = {
    val op = c.op match {
      case "COPY" => FbOp.COPY
      case "PERMUTE" => FbOp.PERMUTE
      case "TRANSPOSE" => FbOp.TRANSPOSE
      case "IM2COL" => FbOp.IM2COL
      case "GATHER" => FbOp.GATHER
      case "SCATTER" => FbOp.SCATTER
    }
    p.op.poke(op)
    p.tag.poke(c.tag.U)
    p.dimCount.poke(c.shape.size.U)
    p.srcAddr.poke(c.srcAddr.U)
    p.dstAddr.poke(c.dstAddr.U)
    p.srcFmt.poke(BigInt(c.srcFmt))
    p.dstFmt.poke(BigInt(c.dstFmt))
    for (d <- 0 until p.shape.length) {
      p.shape(d).poke(at(c.shape, d).U)
      p.srcStride(d).poke(at(c.srcStride, d).U)
      p.dstStride(d).poke(at(c.dstStride, d).U)
      p.srcStartIdx(d).poke(at(c.start, d).U)
      p.permVec(d).poke(c.perm.lift(d).getOrElse(d).U)
    }
    for (d <- 0 until 2) {
      p.padBefore(d).poke(c.before(d).U)
      p.padAfter(d).poke(c.after(d).U)
      p.im2colKernel(d).poke(c.kernel(d).U)
      p.im2colStride(d).poke(c.stride(d).U)
      p.im2colPad(d).poke(c.pad(d).U)
      p.im2colDilation(d).poke(c.dilation(d).U)
    }
    for (d <- 0 until 3) p.im2colInShape(d).poke(c.inShape(d).U)
    p.padValue.poke(BigInt(c.padValue))
    p.sgListAddr.poke(c.listAddr.U)
    p.sgEntryCount.poke(c.entryCount.U)
    p.scaleAddr.poke(c.scaleAddr.U)
    p.scaleBlockSize.poke(0.U) // block16
    p.t2lMatCols.poke(0.U)
    p.t2lTileRows.poke(0.U)
    p.t2lTileCols.poke(0.U)
    p.t2lNumTileRows.poke(0.U)
    p.t2lNumTileCols.poke(0.U)
  }

  private class Scoreboard(s: Suite, bb: Int) {
    val mem: mutable.Map[Int, Int] = mutable.HashMap.from(s.initial)
    val touched = mutable.HashSet.empty[Int]
    private val owners = s.jobs.flatMap(j => j.writes.keys.map(_ -> j.cmd.tag)).toMap
    private val pending = mutable.HashMap.empty[Int, Int]
    private val masks = mutable.HashMap.empty[Int, BigInt]
    private val expectedMasks = s.writes.keys.toVector.groupBy(a => a / bb * bb).map { case (base, as) =>
      base -> as.foldLeft(BigInt(0))((m, a) => m.setBit(a - base))
    }
    def beat(addr: Int): BigInt = packed(Vector.tabulate(bb)(i => mem.getOrElse(addr + i, 0xa5)))
    def write(addr: Int, data: BigInt, strb: BigInt, clue: String): Unit = {
      assert(strb != 0 && strb < (BigInt(1) << bb), s"$clue empty/oversized strobe")
      for (lane <- 0 until bb if strb.testBit(lane)) {
        val a = addr + lane
        assert(s.writes.contains(a), f"$clue stray strobe at 0x$a%x")
        assert(!touched(a), f"$clue duplicate strobe at 0x$a%x")
        val value = ((data >> (8 * lane)) & 255).toInt
        assert(value == s.writes(a), f"$clue byte 0x$a%x: $value%02x != ${s.writes(a)}%02x")
        touched += a
        mem(a) = value
      }
      masks(addr) = masks.getOrElse(addr, BigInt(0)) | strb
    }
    def owner(addr: Int, strb: BigInt): Int = {
      val tags = (0 until bb).filter(strb.testBit).map(lane => owners(addr + lane)).distinct
      assert(tags.size == 1, "a write beat spans multiple commands or has no strobes")
      tags.head
    }
    def burstEnded(tag: Int): Unit = pending(tag) = pending.getOrElse(tag, 0) + 1
    def responded(tag: Int): Unit = {
      assert(pending.getOrElse(tag, 0) > 0, s"response without pending burst for tag $tag")
      pending(tag) -= 1
    }
    def checkJob(j: Job): Unit = {
      assert(j.writes.keys.forall(touched), s"${j.name}: done before every golden byte was strobed")
      assert(pending.getOrElse(j.cmd.tag, 0) == 0, s"${j.name}: done before delayed write responses drained")
    }
    def finish(): Unit = {
      assert(pending.values.forall(_ == 0), "pending command write responses")
      assert(touched.toSet == s.writes.keySet, "missing destination/scale strobes")
      assert(masks.toMap == expectedMasks, "aggregate per-beat strobes differ from exact byte footprint")
      assert(mem.toMap == s.golden, "memory differs from golden (including sentinel margins/gaps)")
    }
  }

  /** Drive ALL slave inputs, then sample ALL handshakes, then step the clock.
    * Valid responses/data remain asserted with identical payload until accepted.
    * Read requests, inter-beat data gaps, writes and responses have independent
    * deterministic schedules; responses are delayed and FIFO ordered per port.
    */
  private class Slave(cfg: ForagerBeeConfig, port: Int, score: Scoreboard) {
    private case class Read(base: Int, beats: Int, var index: Int, var due: Int)
    private var read: Option[Read] = None
    private var heldData: Option[(BigInt, Boolean, Boolean)] = None
    private val responses = mutable.Queue.empty[(Int, Int)] // (earliest cycle, command tag)
    private var heldResponse = false
    private var writeBase = 0
    private var writeBeats = 0
    private var writeIndex = 0
    private var writeTag = 0
    private var previousRead: Option[Vector[BigInt]] = None
    private var previousWrite: Option[Vector[BigInt]] = None
    var readStalls = 0
    var writeStalls = 0
    var responseCount = 0
    var burstCount = 0
    var peakPending = 0
    def drained: Boolean = read.isEmpty && heldData.isEmpty && responses.isEmpty && writeBeats == 0
    def initialize(bus: FbStreamBus): Unit = {
      bus.rd.ready.poke(false.B)
      bus.wr.ready.poke(false.B)
      bus.rdData.valid.poke(false.B)
      bus.rdData.payload.data.poke(0.U)
      bus.rdData.payload.sof.poke(false.B)
      bus.rdData.payload.eof.poke(false.B)
      bus.rdData.payload.err.poke(false.B)
      bus.wrResp.valid.poke(false.B)
      bus.wrResp.payload.err.poke(false.B)
    }
    private def permitted(cycle: Int, period: Int, blocked: Int, salt: Int): Boolean =
      (cycle + port * salt) % period >= blocked
    def drive(bus: FbStreamBus, cycle: Int): Unit = {
      bus.rd.ready.poke((read.isEmpty && permitted(cycle, 7, 3, 2)).B)
      bus.wr.ready.poke(permitted(cycle, 9, 4, 5).B)
      if (heldData.isEmpty) read.foreach { r =>
        if (cycle >= r.due && permitted(cycle, 5, 2, 3))
          heldData = Some((score.beat(r.base + r.index * cfg.beatBytes), r.index == 0, r.index == r.beats - 1))
      }
      bus.rdData.valid.poke(heldData.nonEmpty.B)
      bus.rdData.payload.data.poke(heldData.map(_._1).getOrElse(BigInt(0)))
      bus.rdData.payload.sof.poke(heldData.exists(_._2).B)
      bus.rdData.payload.eof.poke(heldData.exists(_._3).B)
      bus.rdData.payload.err.poke(false.B)
      if (!heldResponse && responses.headOption.exists(_._1 <= cycle) && permitted(cycle, 6, 2, 7))
        heldResponse = true
      bus.wrResp.valid.poke(heldResponse.B)
      bus.wrResp.payload.err.poke(false.B)
    }
    private def readPayload(b: FbStreamBus): Vector[BigInt] =
      Vector(b.rd.payload.addr.peek().litValue, b.rd.payload.len.peek().litValue)
    private def writePayload(b: FbStreamBus): Vector[BigInt] = Vector(
      b.wr.payload.addr.peek().litValue, b.wr.payload.len.peek().litValue,
      b.wr.payload.data.peek().litValue, b.wr.payload.strb.peek().litValue,
      b.wr.payload.sof.peek().litValue, b.wr.payload.eof.peek().litValue)
    private def bound(addr: BigInt, len: BigInt, clue: String): Int = {
      assert(addr.isValidInt && addr >= 0 && addr % cfg.beatBytes == 0, s"$clue unaligned bus address $addr")
      assert(len >= 0 && len + 1 <= cfg.maxBurstLen, s"$clue burst len+1=${len + 1} exceeds ${cfg.maxBurstLen}")
      addr.toInt
    }
    def sample(b: FbStreamBus, cycle: Int): Unit = {
      val clue = s"port $port cycle $cycle"
      val rv = b.rd.valid.peek().litToBoolean
      val rr = b.rd.ready.peek().litToBoolean
      previousRead.foreach { old =>
        assert(rv && readPayload(b) == old, s"$clue read request changed under stall")
      }
      if (rv) {
        val p = readPayload(b)
        val addr = bound(p(0), p(1), clue + " rd")
        if (rr) {
          assert(read.isEmpty, s"$clue overlapping read bursts")
          read = Some(Read(addr, p(1).toInt + 1, 0, cycle + 2 + port))
        } else readStalls += 1
      }
      previousRead = if (rv && !rr) Some(readPayload(b)) else None
      if (heldData.nonEmpty && b.rdData.ready.peek().litToBoolean) {
        val r = read.get
        r.index += 1
        r.due = cycle + 1 + (r.index + port) % 3
        if (r.index == r.beats) read = None
        heldData = None
      }
      val wv = b.wr.valid.peek().litToBoolean
      val wr = b.wr.ready.peek().litToBoolean
      previousWrite.foreach { old =>
        assert(wv && writePayload(b) == old, s"$clue write beat changed under stall")
      }
      if (wv) {
        val p = writePayload(b)
        // addr/len are meaningful only on SOF; continuation payload is opaque.
        if (p(4) != 0) bound(p(0), p(1), clue + " wr")
        if (wr) {
          val sof = p(4) != 0
          val eof = p(5) != 0
          assert(sof == (writeBeats == 0), s"$clue misplaced SOF")
          if (sof) {
            writeBase = p(0).toInt
            writeBeats = p(1).toInt + 1
            writeIndex = 0
          }
          assert(eof == (writeIndex == writeBeats - 1), s"$clue EOF/len mismatch")
          val addr = writeBase + writeIndex * cfg.beatBytes
          score.write(addr, p(2), p(3), clue)
          val tag = score.owner(addr, p(3))
          if (sof) writeTag = tag
          assert(tag == writeTag, s"$clue burst changed command owner")
          writeIndex += 1
          if (eof) {
            writeBeats = 0
            score.burstEnded(writeTag)
            // Long enough to overlap subsequent bursts with NB depth four.
            responses.enqueue((cycle + 31 + (burstCount * 7 + port * 11) % 23, writeTag))
            burstCount += 1
            peakPending = math.max(peakPending, responses.size)
          }
        } else writeStalls += 1
      }
      previousWrite = if (wv && !wr) Some(writePayload(b)) else None
      if (heldResponse && b.wrResp.ready.peek().litToBoolean) {
        assert(responses.nonEmpty, s"$clue unexpected response handshake")
        val (_, tag) = responses.dequeue()
        score.responded(tag)
        heldResponse = false
        responseCount += 1
      }
    }
  }

  private val configs = Vector(
    "128b one port blocking direct" -> ForagerBeeConfig(numPorts = 1, queueDepth = 4, maxDims = 5,
      tileSize = 4, dataWidth = 128, maxBurstLen = 2, dataQueueDepth = 1, enableConversion = true),
    "128b two ports NB direct" -> ForagerBeeConfig(numPorts = 2, queueDepth = 4, maxDims = 5,
      tileSize = 4, dataWidth = 128, maxBurstLen = 3, dataQueueDepth = 2,
      enableConversion = true, converterPipeStages = 2, writeOutstandingDepth = 4),
    "256b two ports blocking shared" -> ForagerBeeConfig(numPorts = 2, queueDepth = 4, maxDims = 5,
      tileSize = 4, dataWidth = 256, maxBurstLen = 4, dataQueueDepth = 1,
      enableConversion = true, enableSharedWritePorts = true),
    "256b two ports NB shared" -> ForagerBeeConfig(numPorts = 2, queueDepth = 4, maxDims = 5,
      tileSize = 4, dataWidth = 256, maxBurstLen = 2, dataQueueDepth = 2,
      enableConversion = true, converterPipeStages = 2,
      enableSharedWritePorts = true, writeOutstandingDepth = 4))

  for ((label, cfg) <- configs) {
    it should s"match all golden byte footprints with $label" in {
      val s = suite(cfg)
      assert(s.jobs.size == 23)
      val score = new Scoreboard(s, cfg.beatBytes)
      implicit val testingDirectory: chisel3.testing.HasTestingDirectory = chisel3.testing.HasTestingDirectory.timestamp
      (new chisel3.simulator.ChiselSim {}).simulateRaw(new ForagerBee(cfg)) { dut =>
        val slaves = Vector.tabulate(cfg.numPorts)(p => new Slave(cfg, p, score))
        val jobsByTag = s.jobs.map(j => j.cmd.tag -> j).toMap
        val completed = mutable.HashSet.empty[Int]
        pokeCmd(dut.io.cmd.payload, s.jobs.head.cmd)
        dut.io.cmd.valid.poke(false.B)
        dut.io.done.ready.poke(false.B)
        slaves.zipWithIndex.foreach { case (m, p) => m.initialize(dut.io.bus(p)) }
        dut.reset.poke(true.B)
        dut.clock.step(2)
        dut.reset.poke(false.B)
        assert(dut.io.idle.peek().litToBoolean, "reset must leave top idle")
        var sent = 0
        var cycle = 0
        var doneAge = 0
        var heldDone: Option[(Int, Boolean)] = None
        while (completed.size < s.jobs.size && cycle < 60000) {
          if (sent < s.jobs.size) pokeCmd(dut.io.cmd.payload, s.jobs(sent).cmd)
          dut.io.cmd.valid.poke((sent < s.jobs.size).B)
          dut.io.done.ready.poke(false.B)
          slaves.zipWithIndex.foreach { case (m, p) => m.drive(dut.io.bus(p), cycle) }
          val valid = dut.io.done.valid.peek().litToBoolean
          if (valid) {
            val value = (dut.io.done.payload.tag.peek().litValue.toInt, dut.io.done.payload.err.peek().litToBoolean)
            heldDone.foreach(old => assert(value == old, "done payload changed while stalled"))
            assert(!dut.io.idle.peek().litToBoolean, "idle must be false while done is stalled/pending")
            heldDone = Some(value)
            if (doneAge >= 7) dut.io.done.ready.poke(true.B)
          } else assert(heldDone.isEmpty, "done valid withdrawn under backpressure")
          slaves.zipWithIndex.foreach { case (m, p) => m.sample(dut.io.bus(p), cycle) }
          // Only commands accepted on PREVIOUS edges count as outstanding here.
          if (sent > completed.size)
            assert(!dut.io.idle.peek().litToBoolean, "accepted work must keep idle false")
          if (sent < s.jobs.size && dut.io.cmd.ready.peek().litToBoolean) sent += 1
          if (valid && dut.io.done.ready.peek().litToBoolean) {
            val (tag, error) = heldDone.get
            assert(jobsByTag.contains(tag) && !completed(tag), s"unexpected/duplicate done tag $tag")
            val j = jobsByTag(tag)
            assert(error == j.error, s"${j.name}: done.err=$error expected ${j.error}")
            score.checkJob(j)
            completed += tag
            heldDone = None
            doneAge = 0
          } else if (valid) doneAge += 1
          dut.clock.step()
          cycle += 1
        }
        val missing = s.jobs.filterNot(j => completed(j.cmd.tag)).map(_.name).mkString(", ")
        assert(completed.size == s.jobs.size, s"$label timed out after $cycle cycles: $missing")
        assert(sent == s.jobs.size)
        dut.io.cmd.valid.poke(false.B)
        dut.io.done.ready.poke(true.B)
        for (_ <- 0 until 12) {
          slaves.zipWithIndex.foreach { case (m, p) => m.drive(dut.io.bus(p), cycle) }
          slaves.zipWithIndex.foreach { case (m, p) => m.sample(dut.io.bus(p), cycle) }
          assert(!dut.io.done.valid.peek().litToBoolean, "extra completion")
          dut.clock.step()
          cycle += 1
        }
        assert(dut.io.idle.peek().litToBoolean && !dut.io.busy.peek().litToBoolean, "top did not return idle")
        assert(dut.io.occupancy.peek().litValue == 0)
        assert(slaves.forall(m => m.drained && m.burstCount == m.responseCount), "done left bus work undrained")
        assert(slaves.map(_.readStalls).sum > 0 && slaves.map(_.writeStalls).sum > 0,
          "deterministic read/write backpressure was not exercised")
        if (cfg.writeOutstandingDepth > 1)
          assert(slaves.exists(_.peakPending > 1), "NB configuration did not overlap delayed write responses")
        score.finish()
      }
    }
  }

  for ((label, cfg) <- Vector(configs(1), configs(3))) {
    it should s"preserve byte packet counts and last through reader/converter/writer with $label" in {
      val b = new Build(cfg)
      val bb = cfg.beatBytes
      val odd = 2 * (bb / 4) + 1
      b.tensor("packet compression odd count", Cmd(shape = Vector(odd), srcStride = Vector(odd * 4),
        dstStride = Vector(odd * 2), srcFmt = 0, dstFmt = 1), bb - 3, bb - 1)
      val expand = bb / 2 + 3
      b.tensor("packet expansion tail", Cmd(shape = Vector(expand), srcStride = Vector(expand * 2),
        dstStride = Vector(expand * 4), srcFmt = 1, dstFmt = 0), 1, bb - 2)
      b.tensor("packet bypass crossing beats", Cmd(shape = Vector(bb + 5), srcStride = Vector(bb + 5),
        dstStride = Vector(bb + 5)), bb - 1, 3)
      b.tensor("packet same-width float tail", Cmd(shape = Vector(7), srcStride = Vector(14),
        dstStride = Vector(14), srcFmt = 2, dstFmt = 1), bb - 1, 5)
      val s = b.result
      val score = new Scoreboard(s, bb)
      implicit val testingDirectory: chisel3.testing.HasTestingDirectory = chisel3.testing.HasTestingDirectory.timestamp
      (new chisel3.simulator.ChiselSim {}).simulateRaw(new ForagerBeeBytePipeHarness(cfg)) { dut =>
        val slaves = Vector.tabulate(2)(p => new Slave(cfg, p, score))
        dut.io.start.poke(false.B)
        dut.io.srcAddr.poke(0.U)
        dut.io.dstAddr.poke(0.U)
        dut.io.srcBytes.poke(0.U)
        dut.io.dstBytes.poke(0.U)
        dut.io.srcFmt.poke(FbDataFmt.INT8)
        dut.io.dstFmt.poke(FbDataFmt.INT8)
        slaves.zipWithIndex.foreach { case (m, p) => m.initialize(dut.io.bus(p)) }
        dut.reset.poke(true.B)
        dut.clock.step(2)
        dut.reset.poke(false.B)
        var cycle = 0
        for (j <- s.jobs) {
          val c = j.cmd
          val inputBytes = rowBytes(c.shape.head, c.srcFmt)
          val outputBytes = rowBytes(c.shape.head, c.dstFmt)
          dut.io.srcAddr.poke(c.srcAddr.U)
          dut.io.dstAddr.poke(c.dstAddr.U)
          dut.io.srcBytes.poke(inputBytes.U)
          dut.io.dstBytes.poke(outputBytes.U)
          dut.io.srcFmt.poke(BigInt(c.srcFmt))
          dut.io.dstFmt.poke(BigInt(c.dstFmt))
          dut.io.start.poke(true.B)
          slaves.zipWithIndex.foreach { case (m, p) => m.drive(dut.io.bus(p), cycle) }
          slaves.zipWithIndex.foreach { case (m, p) => m.sample(dut.io.bus(p), cycle) }
          dut.clock.step()
          cycle += 1
          dut.io.start.poke(false.B)
          var count = 0
          var lastCount = 0
          var ticks = 0
          var previous: Option[(BigInt, Int, Boolean)] = None
          while (!dut.io.writerDone.peek().litToBoolean && ticks < 5000) {
            slaves.zipWithIndex.foreach { case (m, p) => m.drive(dut.io.bus(p), cycle) }
            val valid = dut.io.packetValid.peek().litToBoolean
            val ready = dut.io.packetReady.peek().litToBoolean
            val data = dut.io.packet.data.peek().litValue
            val bytes = dut.io.packet.bytes.peek().litValue.toInt
            val last = dut.io.packet.last.peek().litToBoolean
            previous.foreach(old => assert(valid && (data, bytes, last) == old, s"${j.name}: stalled packet changed"))
            if (valid) {
              assert(bytes > 0 && bytes <= bb, s"${j.name}: illegal byte count $bytes")
              assert(count + bytes <= outputBytes, s"${j.name}: excess converted bytes")
              assert(last == (count + bytes == outputBytes), s"${j.name}: wrong packet last")
              for (lane <- 0 until bytes)
                assert(((data >> (8 * lane)) & 255).toInt == j.writes(c.dstAddr + count + lane),
                  s"${j.name}: converted packet byte ${count + lane} differs")
              if (ready) {
                count += bytes
                if (last) lastCount += 1
              }
            }
            previous = if (valid && !ready) Some((data, bytes, last)) else None
            slaves.zipWithIndex.foreach { case (m, p) => m.sample(dut.io.bus(p), cycle) }
            dut.clock.step()
            cycle += 1
            ticks += 1
          }
          assert(ticks < 5000, s"${j.name}: packet path timeout")
          assert(count == outputBytes && lastCount == 1, s"${j.name}: missing bytes/last")
          assert(dut.io.readerDone.peek().litToBoolean && !dut.io.err.peek().litToBoolean)
          assert(slaves.forall(_.drained), s"${j.name}: writer done before all responses")
          score.checkJob(j)
          // No stale converted packet may escape after a row has finished.
          for (_ <- 0 until 3) {
            slaves.zipWithIndex.foreach { case (m, p) => m.drive(dut.io.bus(p), cycle) }
            slaves.zipWithIndex.foreach { case (m, p) => m.sample(dut.io.bus(p), cycle) }
            assert(!dut.io.packetValid.peek().litToBoolean, s"${j.name}: stale packet after last")
            dut.clock.step()
            cycle += 1
          }
        }
        assert(slaves.map(_.writeStalls).sum > 0)
        score.finish()
      }
    }
  }
}
