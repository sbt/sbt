package sbt

import cross.CrossVersionUtil
import sbt.serialization._

final case class ScalaVersion(full: String, binary: String)

/** Configures how a module will be cross-versioned. */
sealed trait CrossVersion

object CrossVersion {

  /** The first `major.minor` Scala version that the Scala binary version should be used for cross-versioning instead of the full version. */
  val TransitionScalaVersion = CrossVersionUtil.TransitionScalaVersion

  /** The first `major.minor` sbt version that the sbt binary version should be used for cross-versioning instead of the full version. */
  val TransitionSbtVersion = CrossVersionUtil.TransitionSbtVersion

  /** Disables cross versioning for a module.*/
  object Disabled extends CrossVersion { override def toString = "disabled" }

  /**
    * Cross-versions a module using the result of applying `remapVersion` to the binary version.
    * For example, if `remapVersion = v => "2.10"` and the binary version is "2.9.2" or "2.10",
    * the module is cross-versioned with "2.10".
    */
  final class Binary(val remapVersion: String => String) extends CrossVersion {
    override def toString = "Binary"
  }

  /**
    * Cross-versions a module with the result of applying `remapVersion` to the full version.
    * For example, if `remapVersion = v => "2.10"` and the full version is "2.9.2" or "2.10.3",
    * the module is cross-versioned with "2.10".
    */
  final class Full(val remapVersion: String => String) extends CrossVersion {
    override def toString = "Full"
  }

  private val disabledTag = implicitly[FastTypeTag[Disabled.type]]
  private val binaryTag = implicitly[FastTypeTag[Binary]]
  private val fullTag = implicitly[FastTypeTag[Full]]
  implicit val pickler: Pickler[CrossVersion] = new Pickler[CrossVersion] {
    val tag = implicitly[FastTypeTag[CrossVersion]]
    def pickle(a: CrossVersion, builder: PBuilder): Unit = {
      builder.pushHints()
      builder.hintTag(a match {
        case Disabled  => disabledTag
        case x: Binary => binaryTag
        case x: Full   => fullTag
      })
      builder.beginEntry(a)
      builder.endEntry()
      builder.popHints()
    }
  }
  implicit val unpickler: Unpickler[CrossVersion] = new Unpickler[CrossVersion] {
    val tag = implicitly[FastTypeTag[CrossVersion]]
    def unpickle(tpe: String, reader: PReader): Any = {
      reader.pushHints()
      reader.hintTag(tag)
      val tpeStr = reader.beginEntry()
      val tpe = scala.pickling.FastTypeTag(tpeStr)
      // sys.error(tpe.toString)
      val result = tpe match {
        case t if t == disabledTag => Disabled
        case t if t == binaryTag   => binary
        case t if t == fullTag     => full
      }
      reader.endEntry()
      reader.popHints()
      result
    }
  }

  /** Cross-versions a module with the full version (typically the full Scala version). */
  def full: CrossVersion = new Full(idFun)

  /**
    * Cross-versions a module with the result of applying `remapVersion` to the full version
    * (typically the full Scala version).  See also [[sbt.CrossVersion.Full]].
    */
  def fullMapped(remapVersion: String => String): CrossVersion = new Full(remapVersion)

  /** Cross-versions a module with the binary version (typically the binary Scala version).  */
  def binary: CrossVersion = new Binary(idFun)

  /**
    * Cross-versions a module with the result of applying `remapVersion` to the binary version
    * (typically the binary Scala version).  See also [[sbt.CrossVersion.Binary]].
    */
  def binaryMapped(remapVersion: String => String): CrossVersion = new Binary(remapVersion)

  private[this] def idFun[T]: T => T = x => x

  /**
    * Cross-versions a module with the full Scala version excluding any `-bin` suffix.
    */
  def patch: CrossVersion = new Full(patchFun)

  private[this] def patchFun(fullVersion: String): String = {
    val BinCompatV = """(\d+)\.(\d+)\.(\d+)(-\w+)??-bin(-.*)?""".r
    fullVersion match {
      case BinCompatV(x, y, z, w, _) => s"""$x.$y.$z${if (w == null) "" else w}"""
      case other                     => other
    }
  }

  @deprecated("Will be made private.", "0.13.1")
  def append(s: String): Option[String => String] = Some(x => crossName(x, s))

  /**
    * Construct a cross-versioning function given cross-versioning configuration `cross`,
    * full version `fullVersion` and binary version `binaryVersion`.  The behavior of the
    * constructed function is as documented for the [[sbt.CrossVersion]] datatypes.
    */
  def apply(cross: CrossVersion, fullVersion: String, binaryVersion: String): Option[String => String] =
    cross match {
      case Disabled  => None
      case b: Binary => append(b.remapVersion(binaryVersion))
      case f: Full   => append(f.remapVersion(fullVersion))
    }

  /** Constructs the cross-version function defined by `module` and `is`, if one is configured. */
  def apply(module: ModuleID, is: IvyScala): Option[String => String] =
    CrossVersion(module.crossVersion, is.scalaFullVersion, is.scalaBinaryVersion)

  /** Constructs the cross-version function defined by `module` and `is`, if one is configured. */
  def apply(module: ModuleID, is: Option[IvyScala]): Option[String => String] =
    is flatMap { i =>
      apply(module, i)
    }

  /** Cross-version each `Artifact` in `artifacts` according to cross-version function `cross`. */
  def substituteCross(artifacts: Seq[Artifact], cross: Option[String => String]): Seq[Artifact] =
    cross match {
      case None     => artifacts
      case Some(is) => substituteCrossA(artifacts, cross)
    }

  @deprecated("Will be made private.", "0.13.1")
  def applyCross(s: String, fopt: Option[String => String]): String =
    fopt match {
      case None       => s
      case Some(fopt) => fopt(s)
    }

  @deprecated("Will be made private.", "0.13.1")
  def crossName(name: String, cross: String): String =
    name + "_" + cross

  /** Cross-versions `exclude` according to its `crossVersion`. */
  private[sbt] def substituteCross(exclude: SbtExclusionRule, is: Option[IvyScala]): SbtExclusionRule = {
    val fopt: Option[String => String] =
      is flatMap { i =>
        CrossVersion(exclude.crossVersion, i.scalaFullVersion, i.scalaBinaryVersion)
      }
    exclude.copy(name = applyCross(exclude.name, fopt))
  }

  /** Cross-versions `a` according to cross-version function `cross`. */
  def substituteCross(a: Artifact, cross: Option[String => String]): Artifact =
    a.copy(name = applyCross(a.name, cross))

  @deprecated("Will be made private.", "0.13.1")
  def substituteCrossA(as: Seq[Artifact], cross: Option[String => String]): Seq[Artifact] =
    as.map(art => substituteCross(art, cross))

  /**
    * Constructs a function that will cross-version a ModuleID
    * for the given full and binary Scala versions `scalaFullVersion` and `scalaBinaryVersion`
    * according to the ModuleID's cross-versioning setting.
    */
  def apply(scalaFullVersion: String, scalaBinaryVersion: String): ModuleID => ModuleID = m => {
    val cross = apply(m.crossVersion, scalaFullVersion, scalaBinaryVersion)
    if (cross.isDefined)
      m.copy(
        name = applyCross(m.name, cross),
        explicitArtifacts = substituteCrossA(m.explicitArtifacts, cross)
      )
    else
      m
  }

  @deprecated("Use CrossVersion.isScalaApiCompatible or CrossVersion.isSbtApiCompatible", "0.13.0")
  def isStable(v: String): Boolean = isScalaApiCompatible(v)

  @deprecated("Use CrossVersion.scalaApiVersion or CrossVersion.sbtApiVersion", "0.13.0")
  def selectVersion(full: String, binary: String): String = if (isStable(full)) binary else full

  def isSbtApiCompatible(v: String): Boolean = CrossVersionUtil.isSbtApiCompatible(v)

  /**
    * Returns sbt binary interface x.y API compatible with the given version string v.
    * RCs for x.y.0 are considered API compatible.
    * Compatibile versions include 0.12.0-1 and 0.12.0-RC1 for Some(0, 12).
    */
  def sbtApiVersion(v: String): Option[(Int, Int)] = CrossVersionUtil.sbtApiVersion(v)

  def isScalaApiCompatible(v: String): Boolean = CrossVersionUtil.isScalaApiCompatible(v)

  /**
    * Returns Scala binary interface x.y API compatible with the given version string v.
    * Compatibile versions include 2.10.0-1 and 2.10.1-M1 for Some(2, 10), but not 2.10.0-RC1.
    */
  def scalaApiVersion(v: String): Option[(Int, Int)] = CrossVersionUtil.scalaApiVersion(v)

  /** Regular expression that extracts the major and minor components of a version into matched groups 1 and 2.*/
  val PartialVersion = CrossVersionUtil.PartialVersion

  /** Extracts the major and minor components of a version string `s` or returns `None` if the version is improperly formatted. */
  def partialVersion(s: String): Option[(Int, Int)] = CrossVersionUtil.partialVersion(s)

  /**
    * Computes the binary Scala version from the `full` version.
    * Full Scala versions earlier than [[sbt.CrossVersion.TransitionScalaVersion]] are returned as is.
    */
  def binaryScalaVersion(full: String): String = CrossVersionUtil.binaryScalaVersion(full)

  /**
    * Computes the binary sbt version from the `full` version.
    * Full sbt versions earlier than [[sbt.CrossVersion.TransitionSbtVersion]] are returned as is.
    */
  def binarySbtVersion(full: String): String = CrossVersionUtil.binarySbtVersion(full)

  @deprecated("Use CrossVersion.scalaApiVersion or CrossVersion.sbtApiVersion", "0.13.0")
  def binaryVersion(full: String, cutoff: String): String = CrossVersionUtil.binaryVersion(full, cutoff)

}
