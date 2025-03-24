package sbt.librarymanagement

import sbt.internal.librarymanagement.cross.CrossVersionUtil
import sbt.librarymanagement.syntax.*

final case class ScalaVersion(full: String, binary: String)

private[librarymanagement] abstract class CrossVersionFunctions {

  /** Compatibility with 0.13 */
  @deprecated(
    "use CrossVersion.disabled instead. prior to sbt 1.3.0, Disabled did not work without apply(). sbt/sbt#4977",
    "1.3.0"
  )
  final val Disabled = sbt.librarymanagement.Disabled
  final val Binary = sbt.librarymanagement.Binary
  final val Constant = sbt.librarymanagement.Constant
  final val Full = sbt.librarymanagement.Full
  final val Patch = sbt.librarymanagement.Patch
  type Binary = sbt.librarymanagement.Binary
  type Constant = sbt.librarymanagement.Constant
  type Full = sbt.librarymanagement.Full
  type Patch = sbt.librarymanagement.Patch

  /** The first `major.minor` Scala version that the Scala binary version should be used for cross-versioning instead of the full version. */
  val TransitionScalaVersion = CrossVersionUtil.TransitionScalaVersion

  /** The first `major.minor` sbt version that the sbt binary version should be used for cross-versioning instead of the full version. */
  val TransitionSbtVersion = CrossVersionUtil.TransitionSbtVersion

  /** Cross-versions a module with the full version (typically the full Scala version). */
  def full: CrossVersion = Full()

  /**
   * Cross-versions a module with the result of prepending `prefix` and appending `suffix` to the full version.
   * (typically the full Scala version).  See also [[sbt.librarymanagement.Full]]
   */
  def fullWith(prefix: String, suffix: String): CrossVersion = Full(prefix, suffix)

  /** Cross-versions a module with the binary version (typically the binary Scala version). */
  def binary: CrossVersion = Binary()

  /** Disables cross versioning for a module. */
  def disabled: CrossVersion = sbt.librarymanagement.Disabled

  /** Cross-versions a module with a constant string (typically the binary Scala version). */
  def constant(value: String): CrossVersion = Constant(value)

  /**
   * Cross-versions a module with the result of prepending `prefix` and appending `suffix` to the binary version
   * (typically the binary Scala version).  See also [[sbt.librarymanagement.Binary]].
   */
  def binaryWith(prefix: String, suffix: String): CrossVersion = Binary(prefix, suffix)

  /**
   * Cross-versions a module with the full Scala version excluding any `-bin` suffix.
   */
  def patch: CrossVersion = Patch()

  /**
   * Cross-versions a module with the binary version but
   * if the binary version is 3 (or of the form 3.0.0-x), cross-versions it with 2.13 instead
   */
  def for3Use2_13: CrossVersion = For3Use2_13()

  /**
   * Cross-versions a module with the binary version but
   * if the binary version is 3 (or of the form 3.0.0-x), cross-versions it with 2.13 instead
   * Always prepend `prefix` and append `suffix`
   */
  def for3Use2_13With(prefix: String, suffix: String): CrossVersion = For3Use2_13(prefix, suffix)

  /**
   * Cross-versions a module with the binary version but
   * if the binary version is 2.13 cross-versions it with 3 instead
   */
  def for2_13Use3: CrossVersion = For2_13Use3()

  /**
   * Cross-versions a module with the binary version but
   * if the binary version is 2.13 cross-versions it with 3 instead
   * Always prepend `prefix` and append `suffix`
   */
  def for2_13Use3With(prefix: String, suffix: String): CrossVersion = For2_13Use3(prefix, suffix)

  private[sbt] def getPrefixSuffix(value: CrossVersion): (String, String) =
    value match {
      case (_: Disabled | _: Constant | _: Patch) => ("", "")
      case b: Binary                              => (b.prefix, b.suffix)
      case f: Full                                => (f.prefix, f.suffix)
      case c: For3Use2_13                         => (c.prefix, c.suffix)
      case c: For2_13Use3                         => (c.prefix, c.suffix)
    }

  private[sbt] def setPrefixSuffix(value: CrossVersion, p: String, s: String): CrossVersion =
    value match {
      case (_: Disabled | _: Constant | _: Patch) => value
      case b: Binary                              => b.withPrefix(p).withSuffix(s)
      case f: Full                                => f.withPrefix(p).withSuffix(s)
      case c: For3Use2_13                         => c.withPrefix(p).withSuffix(s)
      case c: For2_13Use3                         => c.withPrefix(p).withSuffix(s)
    }

  private[sbt] def patchFun(fullVersion: String): String = {
    import sbt.internal.librarymanagement.cross.CrossVersionUtil.BinCompatV
    fullVersion match {
      case BinCompatV(x, y, z, w, _) => s"""$x.$y.$z${if (w == null) "" else w}"""
      case other                     => other
    }
  }

  private[sbt] def append(s: String): Option[String => String] = Some(x => crossName(x, s))

  /**
   * Construct a cross-versioning function given cross-versioning configuration `cross`,
   * full version `fullVersion` and binary version `binaryVersion`.  The behavior of the
   * constructed function is as documented for the [[sbt.librarymanagement.CrossVersion]] datatypes.
   */
  def apply(
      cross: CrossVersion,
      fullVersion: String,
      binaryVersion: String
  ): Option[String => String] =
    cross match {
      case _: Disabled => None
      case b: Binary   => append(b.prefix + binaryVersion + b.suffix)
      case c: Constant => append(c.value)
      case _: Patch    => append(patchFun(fullVersion))
      case f: Full     => append(f.prefix + fullVersion + f.suffix)
      case c: For3Use2_13 =>
        val compat =
          if (binaryVersion == "3" || binaryVersion.startsWith("3.0.0")) "2.13"
          else binaryVersion
        append(c.prefix + compat + c.suffix)
      case c: For2_13Use3 =>
        val compat =
          if (binaryVersion == "2.13") "3"
          else binaryVersion
        append(c.prefix + compat + c.suffix)
    }

  /** Constructs the cross-version function defined by `module` and `is`, if one is configured. */
  def apply(module: ModuleID, is: ScalaModuleInfo): Option[String => String] =
    CrossVersion(module.crossVersion, is.scalaFullVersion, is.scalaBinaryVersion)

  /** Constructs the cross-version function defined by `module` and `is`, if one is configured. */
  def apply(module: ModuleID, is: Option[ScalaModuleInfo]): Option[String => String] =
    is.flatMap(i => apply(module, i))

  /** Cross-version each `Artifact` in `artifacts` according to cross-version function `cross`. */
  def substituteCross(
      artifacts: Vector[Artifact],
      cross: Option[String => String]
  ): Vector[Artifact] =
    cross match {
      case None    => artifacts
      case Some(_) => substituteCrossA(artifacts, cross)
    }

  private[sbt] def applyCross(s: String, fopt: Option[String => String]): String =
    fopt match {
      case None       => s
      case Some(fopt) => fopt(s)
    }

  private[sbt] def crossName(name: String, cross: String): String =
    name + "_" + cross

  /** Cross-versions `exclude` according to its `crossVersion`. */
  private[sbt] def substituteCross(
      exclude: ExclusionRule,
      is: Option[ScalaModuleInfo]
  ): ExclusionRule = {
    val fopt: Option[String => String] =
      is flatMap { i =>
        CrossVersion(exclude.crossVersion, i.scalaFullVersion, i.scalaBinaryVersion)
      }
    exclude.withName(applyCross(exclude.name, fopt))
  }

  /** Cross-versions `a` according to cross-version function `cross`. */
  def substituteCross(a: Artifact, cross: Option[String => String]): Artifact =
    a.withName(applyCross(a.name, cross))

  private[sbt] def substituteCrossA(
      as: Vector[Artifact],
      cross: Option[String => String]
  ): Vector[Artifact] = as.map(art => substituteCross(art, cross))

  /**
   * Constructs a function that will cross-version a ModuleID
   * for the given full and binary Scala versions `scalaFullVersion` and `scalaBinaryVersion`
   * according to the ModuleID's cross-versioning setting.
   */
  def apply(scalaFullVersion: String, scalaBinaryVersion: String): ModuleID => ModuleID = m => {
    val cross = apply(m.crossVersion, scalaFullVersion, scalaBinaryVersion)
    if (cross.isDefined)
      m.withName(applyCross(m.name, cross))
        .withExplicitArtifacts(substituteCrossA(m.explicitArtifacts, cross))
    else
      m
  }

  def isSbtApiCompatible(v: String): Boolean = CrossVersionUtil.isSbtApiCompatible(v)

  /**
   * Returns sbt binary interface x.y API compatible with the given version string v.
   * RCs for x.y.0 are considered API compatible.
   * Compatible versions include 0.12.0-1 and 0.12.0-RC1 for Some(0, 12).
   */
  def sbtApiVersion(v: String): Option[(Long, Long)] = CrossVersionUtil.sbtApiVersion(v)

  def isScalaApiCompatible(v: String): Boolean = CrossVersionUtil.isScalaApiCompatible(v)

  /**
   * Returns Scala binary interface x.y API compatible with the given version string v.
   * Compatible versions include 2.10.0-1 and 2.10.1-M1 for Some(2, 10), but not 2.10.0-RC1.
   */
  def scalaApiVersion(v: String): Option[(Long, Long)] = CrossVersionUtil.scalaApiVersion(v)

  /** Regular expression that extracts the major and minor components of a version into matched groups 1 and 2. */
  val PartialVersion = CrossVersionUtil.PartialVersion

  /** Extracts the major and minor components of a version string `s` or returns `None` if the version is improperly formatted. */
  def partialVersion(s: String): Option[(Long, Long)] = CrossVersionUtil.partialVersion(s)

  /**
   * Computes the binary Scala version from the `full` version.
   * Full Scala versions earlier than [[sbt.librarymanagement.CrossVersion.TransitionScalaVersion]] are returned as is.
   */
  def binaryScalaVersion(full: String): String = CrossVersionUtil.binaryScalaVersion(full)

  /**
   * Computes the binary sbt version from the `full` version.
   * Full sbt versions earlier than [[sbt.librarymanagement.CrossVersion.TransitionSbtVersion]] are returned as is.
   */
  def binarySbtVersion(full: String): String = CrossVersionUtil.binarySbtVersion(full)

  /**
   * Returns `true` if a project targeting version `origVersion` can run with version `newVersion`.
   */
  def isScalaBinaryCompatibleWith(newVersion: String, origVersion: String): Boolean =
    CrossVersionUtil.isScalaBinaryCompatibleWith(newVersion, origVersion)
}
