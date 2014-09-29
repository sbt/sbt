package sbt

/**
 * Represents a logical time point for dependency resolution.
 * This is used to cache dependencies across subproject resolution which may change over time.
 */
trait LogicalClock {
  def toString: String
}

object LogicalClock {
  def apply(x: String): LogicalClock = new LogicalClock {
    override def toString: String = x
  }
  def unknown: LogicalClock = apply("unknown")
}
