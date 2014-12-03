package sbt

final class VersionNumber private[sbt] (
    val numbers: Seq[Long],
    val tags: Seq[String],
    val extras: Seq[String]) {
  def _1: Option[Long] = get(0)
  def _2: Option[Long] = get(1)
  def _3: Option[Long] = get(2)
  def _4: Option[Long] = get(3)
  def get(idx: Int): Option[Long] =
    if (size <= idx) None
    else Some(numbers(idx))
  def size: Int = numbers.size

  private[this] val versionStr: String =
    numbers.mkString(".") +
      (tags match {
        case Seq() => ""
        case ts    => "-" + ts.mkString("-")
      }) +
      extras.mkString("")
  override def toString: String = versionStr
  override def hashCode: Int =
    numbers.hashCode * 41 * 41 +
      tags.hashCode * 41 +
      extras.hashCode
  override def equals(o: Any): Boolean =
    o match {
      case v: VersionNumber => (this.numbers == v.numbers) && (this.tags == v.tags) && (this.extras == v.extras)
      case _                => false
    }
}

object VersionNumber {
  /**
   * @param numbers numbers delimited by a dot.
   * @param tags string prefixed by a dash.
   * @param any other strings at the end.
   */
  def apply(numbers: Seq[Long], tags: Seq[String], extras: Seq[String]): VersionNumber =
    new VersionNumber(numbers, tags, extras)
  def apply(v: String): VersionNumber =
    unapply(v) match {
      case Some((ns, ts, es)) => VersionNumber(ns, ts, es)
      case _                  => sys.error(s"Invalid version number: $v")
    }

  def unapply(v: VersionNumber): Option[(Seq[Long], Seq[String], Seq[String])] =
    Some((v.numbers, v.tags, v.extras))

  def unapply(v: String): Option[(Seq[Long], Seq[String], Seq[String])] = {
    def splitDot(s: String): Vector[Long] =
      Option(s) match {
        case Some(x) => x.split('.').toVector.filterNot(_ == "").map(_.toLong)
        case _       => Vector()
      }
    def splitDash(s: String): Vector[String] =
      Option(s) match {
        case Some(x) => x.split('-').toVector.filterNot(_ == "")
        case _       => Vector()
      }
    def splitPlus(s: String): Vector[String] =
      Option(s) match {
        case Some(x) => x.split('+').toVector.filterNot(_ == "").map("+" + _)
        case _       => Vector()
      }
    val TaggedVersion = """(\d{1,14})([\.\d{1,14}]*)((?:-\w+)*)((?:\+.+)*)""".r
    val NonSpaceString = """(\S+)""".r
    v match {
      case TaggedVersion(m, ns, ts, es) => Some((Vector(m.toLong) ++ splitDot(ns), splitDash(ts), splitPlus(es)))
      case ""                           => None
      case NonSpaceString(s)            => Some((Vector(), Vector(), Vector(s)))
      case _                            => None
    }
  }

  /**
   * Strict. Checks everythig.
   */
  object Strict extends VersionNumberCompatibility {
    def name: String = "Strict"
    def isCompatible(v1: VersionNumber, v2: VersionNumber): Boolean = v1 == v2
  }

  /**
   * Semantic versioning. See http://semver.org/spec/v2.0.0.html
   */
  object SemVer extends VersionNumberCompatibility {
    def name: String = "Semantic Versioning"
    def isCompatible(v1: VersionNumber, v2: VersionNumber): Boolean =
      doIsCompat(v1, v2) || doIsCompat(v2, v1)
    private[this] def doIsCompat(v1: VersionNumber, v2: VersionNumber): Boolean =
      (v1, v2) match {
        case (v1, v2) if (v1.size >= 2) && (v2.size >= 2) => // A normal version number MUST take the form X.Y.Z
          (v1._1.get, v1._2.get, v1._3.getOrElse(0), v1.tags, v2._1.get, v2._2.get, v2._3.getOrElse(0), v2.tags) match {
            case (0L, _, _, _, 0L, _, _, _) =>
              // Major version zero (0.y.z) is for initial development. Anything may change at any time. The public API should not be considered stable.
              equalsIgnoreExtra(v1, v2)
            case (_, 0, 0, ts1, _, 0, 0, ts2) if ts1.nonEmpty || ts2.nonEmpty =>
              // A pre-release version MAY be denoted by appending a hyphen and a series of dot separated identifiers
              equalsIgnoreExtra(v1, v2)
            case (x1, _, _, _, x2, _, _, _) =>
              // Patch version Z (x.y.Z | x > 0) MUST be incremented if only backwards compatible bug fixes are introduced. 
              // Minor version Y (x.Y.z | x > 0) MUST be incremented if new, backwards compatible functionality is introduced 
              x1 == x2
            case _ => equalsIgnoreExtra(v1, v2)
          }
        case _ => false
      }
    // Build metadata SHOULD be ignored when determining version precedence.
    private[this] def equalsIgnoreExtra(v1: VersionNumber, v2: VersionNumber): Boolean =
      (v1.numbers == v2.numbers) && (v1.tags == v2.tags)
  }

  /* A variant of SemVar that seems to be common among the Scala libraries.
   * The second segment (y in x.y.z) increments breaks the binary compatibility even when x > 0.
   * Also API comatibility is expected even when the first segment is zero.
   */
  object SecondSegment extends VersionNumberCompatibility {
    def name: String = "Second Segment Variant"
    def isCompatible(v1: VersionNumber, v2: VersionNumber): Boolean =
      doIsCompat(v1, v2) || doIsCompat(v2, v1)
    private[this] def doIsCompat(v1: VersionNumber, v2: VersionNumber): Boolean =
      (v1, v2) match {
        case (v1, v2) if (v1.size >= 3) && (v2.size >= 3) => // A normal version number MUST take the form X.Y.Z
          (v1._1.get, v1._2.get, v1._3.get, v1.tags, v2._1.get, v2._2.get, v2._3.get, v2.tags) match {
            case (x1, y1, 0, ts1, x2, y2, 0, ts2) if ts1.nonEmpty || ts2.nonEmpty =>
              // A pre-release version MAY be denoted by appending a hyphen and a series of dot separated identifiers
              equalsIgnoreExtra(v1, v2)
            case (x1, y1, _, _, x2, y2, _, _) =>
              // Patch version Z (x.y.Z | x > 0) MUST be incremented if only backwards compatible changes are introduced. 
              (x1 == x2) && (y1 == y2)
            case _ => equalsIgnoreExtra(v1, v2)
          }
        case _ => false
      }
    // Build metadata SHOULD be ignored when determining version precedence.
    private[this] def equalsIgnoreExtra(v1: VersionNumber, v2: VersionNumber): Boolean =
      (v1.numbers == v2.numbers) && (v1.tags == v2.tags)
  }
}

trait VersionNumberCompatibility {
  def name: String
  def isCompatible(v1: VersionNumber, v2: VersionNumber): Boolean
}
