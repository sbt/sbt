package sbt

import sbt.librarymanagement.CrossVersion.partialVersion

/** Virtual Axis represents a parameter to a project matrix row. */
sealed abstract class VirtualAxis {
  def directorySuffix: String

  def idSuffix: String

  /* The order to sort the suffixes if there were multiple axes. */
  def suffixOrder: Int = 50
}

object VirtualAxis {
  /**
   * WeakAxis allows a row to depend on another row with Zero value.
   * For example, Scala version can be Zero for Java project, and it's ok.
   */
  abstract class WeakAxis extends VirtualAxis

  /** StrongAxis requires a row to depend on another row with the same selected value. */
  abstract class StrongAxis extends VirtualAxis


  def isMatch(lhs: Seq[VirtualAxis], rhs: Seq[VirtualAxis]): Boolean =
    lhs.forall(isStronglyCompatible(_, rhs)) && rhs.forall(isStronglyCompatible(_, lhs))

  private[sbt] def isStronglyCompatible(v: VirtualAxis, stack: Seq[VirtualAxis]): Boolean =
    v match {
      case v: WeakAxis =>
        val clazz = v.getClass
        stack.contains(v) || !stack.exists(_.getClass == clazz)
      case v: StrongAxis =>
        stack.contains(v)
    }

  def isSecondaryMatch(lhs: Seq[VirtualAxis], rhs: Seq[VirtualAxis]): Boolean =
    lhs.forall(isSecondaryCompatible(_, rhs)) && rhs.forall(isSecondaryCompatible(_, lhs))

  def isSecondaryCompatible(v: VirtualAxis, stack: Seq[VirtualAxis]): Boolean =
    v match {
      case v: ScalaVersionAxis =>
        val thatSVOpt = (stack collect {
          case x: ScalaVersionAxis => x
        }).headOption
        thatSVOpt match {
          case Some(ScalaVersionAxis(sv, _)) =>
            (v.scalaVersion == sv) ||
            isScala2Scala3Sandwich(partialVersion(v.scalaVersion), partialVersion(sv))
          case _ => true
        }
      case _ =>
        isStronglyCompatible(v, stack)
    }

  private[sbt] def isScala2Scala3Sandwich(sbv1: Option[(Long, Long)], sbv2: Option[(Long, Long)]): Boolean = {
    def str(x: Option[(Long, Long)]): String =
      x match {
        case Some((a, b)) => s"$a.$b"
        case _            => "0.0"
      }
    isScala2Scala3Sandwich(str(sbv1), str(sbv2))
  }

  private[sbt] def isScala2Scala3Sandwich(sbv1: String, sbv2: String): Boolean = {
    def compare(a: String, b: String): Boolean =
      a == "2.13" && (b.startsWith("0.") || b.startsWith("3.0"))
    compare(sbv1, sbv2) || compare(sbv2, sbv1)
  }

  case class ScalaVersionAxis(scalaVersion: String, value: String) extends WeakAxis {
    override def idSuffix: String = directorySuffix.replaceAll("""\W+""", "_")
    override val suffixOrder: Int = 100
    override def directorySuffix: String = value

    // use only the scalaVersion field for equality
    override def equals(obj: Any): Boolean = {
      if (obj.isInstanceOf[AnyRef] && (this eq obj.asInstanceOf[AnyRef])) true
      else if (!obj.isInstanceOf[ScalaVersionAxis]) false
      else {
        val o = obj.asInstanceOf[ScalaVersionAxis]
        this.scalaVersion == o.scalaVersion
      }
    }
    override def hashCode: Int = {
      37 * (17 + "sbt.ScalaVersionAxis".hashCode()) + scalaVersion.hashCode()
    }
  }

  case class PlatformAxis(value: String, idSuffix: String, directorySuffix: String) extends StrongAxis {
    override val suffixOrder: Int = 80
  }

  def scalaPartialVersion(scalaVersion: String): ScalaVersionAxis =
    partialVersion(scalaVersion) match {
      case Some((m, n)) => scalaVersionAxis(scalaVersion, s"$m.$n")
      case _            => scalaVersionAxis(scalaVersion, scalaVersion)
    }
  def scalaVersionAxis(scalaVersion: String, value: String) =
    ScalaVersionAxis(scalaVersion, value)

  val jvm: PlatformAxis = PlatformAxis("jvm", "JVM", "jvm")
  val js: PlatformAxis = PlatformAxis("js", "JS", "js")
  val native: PlatformAxis = PlatformAxis("native", "Native", "native")
}
