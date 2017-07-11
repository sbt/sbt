package sbt.librarymanagement

/**
 * An enumeration defining the tracking of dependencies.  A level includes all of the levels
 * with id larger than its own id.  For example, Warn (id=3) includes Error (id=4).
 */
object TrackLevel {
  case object NoTracking extends TrackLevel {
    override def id: Int = 0
  }
  case object TrackIfMissing extends TrackLevel {
    override def id: Int = 1
  }
  case object TrackAlways extends TrackLevel {
    override def id: Int = 10
  }

  private[sbt] def apply(x: Int): TrackLevel =
    x match {
      case 0  => NoTracking
      case 1  => TrackIfMissing
      case 10 => TrackAlways
    }

  def intersection(a: TrackLevel, b: TrackLevel): TrackLevel =
    if (a.id < b.id) a
    else b
  def intersectionAll(vs: List[TrackLevel]): TrackLevel = vs reduceLeft intersection
}

sealed trait TrackLevel {
  def id: Int
}
