package sbt.internal.librarymanagement
package cross

object CrossVersionUtil {
  val trueString = "true"
  val falseString = "false"
  val fullString = "full"
  val noneString = "none"
  val disabledString = "disabled"
  val binaryString = "binary"
  val TransitionDottyVersion = "" // Dotty always respects binary compatibility
  val TransitionScalaVersion = "2.10" // ...but scalac doesn't until Scala 2.10
  val TransitionSbtVersion = "0.12"

  def isFull(s: String): Boolean = (s == trueString) || (s == fullString)

  def isDisabled(s: String): Boolean =
    (s == falseString) || (s == noneString) || (s == disabledString)

  def isBinary(s: String): Boolean = (s == binaryString)

  private val longPattern = """\d{1,19}"""
  private val basicVersion = raw"""($longPattern)\.($longPattern)\.($longPattern)"""
  private val ReleaseV = raw"""$basicVersion(-\d+)?""".r
  private val BinCompatV = raw"""$basicVersion-bin(-.*)?""".r
  private val CandidateV = raw"""$basicVersion(-RC\d+)""".r
  private val NonReleaseV_n = raw"""$basicVersion([-\w]*)""".r // 0-n word suffixes, with leading dashes
  private val NonReleaseV_1 = raw"""$basicVersion(-\w+)""".r // 1 word suffix, after a dash
  private[sbt] val PartialVersion = raw"""($longPattern)\.($longPattern)(?:\..+)?""".r

  private[sbt] def isSbtApiCompatible(v: String): Boolean = sbtApiVersion(v).isDefined

  /**
   * Returns sbt binary interface x.y API compatible with the given version string v.
   * RCs for x.y.0 are considered API compatible.
   * Compatible versions include 0.12.0-1 and 0.12.0-RC1 for Some(0, 12).
   */
  private[sbt] def sbtApiVersion(v: String): Option[(Long, Long)] = v match {
    case ReleaseV(x, y, _, _)                     => Some(sbtApiVersion(x.toLong, y.toLong))
    case CandidateV(x, y, _, _)                   => Some(sbtApiVersion(x.toLong, y.toLong))
    case NonReleaseV_n(x, y, z, _) if z.toInt > 0 => Some(sbtApiVersion(x.toLong, y.toLong))
    case _                                        => None
  }

  private def sbtApiVersion(x: Long, y: Long) = {
    // Prior to sbt 1 the "sbt api version" was the X.Y in the X.Y.Z version.
    // For example for sbt 0.13.x releases, the sbt api version is 0.13
    // As of sbt 1 it is now X.0.
    // This means, for example, that all versions of sbt 1.x have sbt api version 1.0
    if (x > 0) (x, 0L) else (x, y)
  }

  private[sbt] def isScalaApiCompatible(v: String): Boolean = scalaApiVersion(v).isDefined

  /**
   * Returns Scala binary interface x.y API compatible with the given version string v.
   * Compatible versions include 2.10.0-1 and 2.10.1-M1 for Some(2, 10), but not 2.10.0-RC1.
   */
  private[sbt] def scalaApiVersion(v: String): Option[(Long, Long)] = v match {
    case ReleaseV(x, y, _, _)                     => Some((x.toLong, y.toLong))
    case BinCompatV(x, y, _, _)                   => Some((x.toLong, y.toLong))
    case NonReleaseV_1(x, y, z, _) if z.toInt > 0 => Some((x.toLong, y.toLong))
    case _                                        => None
  }

  private[sbt] def partialVersion(s: String): Option[(Long, Long)] =
    s match {
      case PartialVersion(major, minor) => Some((major.toLong, minor.toLong))
      case _                            => None
    }

  def binaryScalaVersion(full: String): String = {
    val cutoff = if (full.startsWith("0.")) TransitionDottyVersion else TransitionScalaVersion
    binaryVersionWithApi(full, cutoff)(scalaApiVersion)
  }

  def binarySbtVersion(full: String): String =
    binaryVersionWithApi(full, TransitionSbtVersion)(sbtApiVersion)

  private[this] def isNewer(major: Long, minor: Long, minMajor: Long, minMinor: Long): Boolean =
    major > minMajor || (major == minMajor && minor >= minMinor)

  private[this] def binaryVersionWithApi(full: String, cutoff: String)(
      apiVersion: String => Option[(Long, Long)]
  ): String = {
    (apiVersion(full), partialVersion(cutoff)) match {
      case (Some((major, minor)), None) => s"$major.$minor"
      case (Some((major, minor)), Some((minMajor, minMinor)))
          if isNewer(major, minor, minMajor, minMinor) =>
        s"$major.$minor"
      case _ => full
    }
  }
}
