package sbt.librarymanagement

/**
 * Represents a logical time point for dependency resolution.
 * This is used to cache dependencies across subproject resolution which may change over time.
 */
trait LogicalClock {
  def toString: String
}

object LogicalClock {
  def apply(hashCode: Int): LogicalClock = {
    def intToByteArray(x: Int): Array[Byte] =
      Array((x >>> 24).toByte, (x >> 16 & 0xff).toByte, (x >> 8 & 0xff).toByte, (x & 0xff).toByte)
    apply(sbt.io.Hash.toHex(intToByteArray(hashCode)))
  }
  def apply(x: String): LogicalClock = new LogicalClock {
    override def toString: String = x
  }
  def unknown: LogicalClock = apply("unknown")
}
