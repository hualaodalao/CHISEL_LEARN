

object MaskedLiteral{

  def apply(str: String): MaskedLiteral = {
    val strCleaned = str.replace("_", "")

    for(c <- strCleaned) assert(c == '1' || c == '0' || c == '-', s"""M"$str" is not correctly formated.""")

    val careAbout = strCleaned.map(c => if(c == '-') '0' else '1')
    val value = strCleaned.map(c => if(c == '-') '0' else c)
    new MaskedLiteral(BigInt(value, 2),BigInt(careAbout, 2), strCleaned.length())
  }
}


/**
  * Masked Literal
  *
  *  @example {{{
  *     val itMatch = myBits === M"00--10--" // - don't care value
  * }}}
  *
  */

class MaskedBoolean(value : Boolean, careAbout : Boolean) {
  def ===(that : Bool) : Bool = if(careAbout) that === Bool(value) else True
  def =/=(that : Bool) : Bool = if(careAbout) that =/= Bool(value) else False
}

class MaskedLiteral(val value: BigInt, val careAbout: BigInt, val width: Int) {
  override def hashCode() = value.hashCode() + careAbout.hashCode() + width
  override def equals(o: scala.Any) = o match {
    case o : MaskedLiteral => this.value == o.value && this.careAbout == o.careAbout && this.width == o.width
    case _ => false
  }

  def withDontCare = careAbout != (BigInt(1) << width)-1

  def getWidth() = width
  def apply(index : Int): MaskedBoolean = {
    if(index >= width){
      SpinalError(s"Accessing MaskedLiteral at $index is outside its range ($width bits)")
    }
    new MaskedBoolean(value.testBit(index), careAbout.testBit(index))
  }
  def ===(that: BitVector): Bool = {
    if(that.getWidth != width){
      SpinalError(s"Masked literal width=$width doesn't match the one of $that")
    }
    (that.asBits & careAbout) === value
  }

  def =/=(that: BitVector): Bool = !(this === that)

  def getBitsString(width: Int, dontCareSymbol : Char) : String = {
    def bigInt2ListBoolean(value: BigInt, size: BitCount): List[Boolean] = {
      def bigInt2ListBool(that: BigInt): List[Boolean] = {
        if(that == 0)  Nil
        else List(that.testBit(0)) ::: bigInt2ListBool(that >> 1)
      }

      castListBool(bigInt2ListBool(value), size.value)
    }

    def castListBool(l: List[Boolean], size: Int): List[Boolean] = {
      if (l.length == size)     l
      else if (l.length > size) l.drop( l.length - size)
      else                      l ::: List.fill(size - l.length)(false)
    }

    val valueList = bigInt2ListBoolean(this.value, width bits)
    val careList  = bigInt2ListBoolean(this.careAbout, width bits)

    careList.zip(valueList).map {
      case (false, _)    => dontCareSymbol
      case (true, true)  => "1"
      case (true, false) => "0"
    }.reverse.mkString("")
  }

  override def toString: String = "M\"" + getBitsString(this.width, '-') + "\""

  def toBigInt(filling : Boolean = false) : BigInt = {
    var tmp = if(filling) ~careAbout else careAbout
    tmp &= ~careAbout
    tmp |= value
    tmp &= ((BigInt(1) << width)-1)
    tmp
  }

  def asUInt(filling : Boolean = false): UInt = U(toBigInt(filling).U)
  def asSInt(filling : Boolean = false): SInt = S(toBigInt(filling).S)
}
