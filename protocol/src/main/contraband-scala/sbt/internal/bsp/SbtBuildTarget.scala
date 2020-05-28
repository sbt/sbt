/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp
/**
 * Contains sbt-specific metadata for providing editor support for sbt build files.
 * This metadata is embedded in the data: Option[Json] field of the BuildTarget definition
 * when the dataKind field contains "sbt".
 * @param sbtVersion The sbt version. Useful to support version-dependent syntax.
 * @param autoImports A sequence of Scala imports that are automatically imported in the sbt build files.
 * @param scalaBuildTarget The Scala build target describing the scala
                           version and scala jars used by this sbt version.
 * @param parent An optional parent if the target has an sbt meta project.
 * @param children The inverse of parent, list of targets that have this build target
                   defined as their parent. It can contain normal project targets or
                   sbt build targets if this target represents an sbt meta-meta build.
 */
final class SbtBuildTarget private (
  val sbtVersion: String,
  val autoImports: Vector[String],
  val scalaBuildTarget: sbt.internal.bsp.ScalaBuildTarget,
  val parent: Option[sbt.internal.bsp.BuildTargetIdentifier],
  val children: Vector[sbt.internal.bsp.BuildTargetIdentifier]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: SbtBuildTarget => (this.sbtVersion == x.sbtVersion) && (this.autoImports == x.autoImports) && (this.scalaBuildTarget == x.scalaBuildTarget) && (this.parent == x.parent) && (this.children == x.children)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (37 * (17 + "sbt.internal.bsp.SbtBuildTarget".##) + sbtVersion.##) + autoImports.##) + scalaBuildTarget.##) + parent.##) + children.##)
  }
  override def toString: String = {
    "SbtBuildTarget(" + sbtVersion + ", " + autoImports + ", " + scalaBuildTarget + ", " + parent + ", " + children + ")"
  }
  private[this] def copy(sbtVersion: String = sbtVersion, autoImports: Vector[String] = autoImports, scalaBuildTarget: sbt.internal.bsp.ScalaBuildTarget = scalaBuildTarget, parent: Option[sbt.internal.bsp.BuildTargetIdentifier] = parent, children: Vector[sbt.internal.bsp.BuildTargetIdentifier] = children): SbtBuildTarget = {
    new SbtBuildTarget(sbtVersion, autoImports, scalaBuildTarget, parent, children)
  }
  def withSbtVersion(sbtVersion: String): SbtBuildTarget = {
    copy(sbtVersion = sbtVersion)
  }
  def withAutoImports(autoImports: Vector[String]): SbtBuildTarget = {
    copy(autoImports = autoImports)
  }
  def withScalaBuildTarget(scalaBuildTarget: sbt.internal.bsp.ScalaBuildTarget): SbtBuildTarget = {
    copy(scalaBuildTarget = scalaBuildTarget)
  }
  def withParent(parent: Option[sbt.internal.bsp.BuildTargetIdentifier]): SbtBuildTarget = {
    copy(parent = parent)
  }
  def withParent(parent: sbt.internal.bsp.BuildTargetIdentifier): SbtBuildTarget = {
    copy(parent = Option(parent))
  }
  def withChildren(children: Vector[sbt.internal.bsp.BuildTargetIdentifier]): SbtBuildTarget = {
    copy(children = children)
  }
}
object SbtBuildTarget {
  
  def apply(sbtVersion: String, autoImports: Vector[String], scalaBuildTarget: sbt.internal.bsp.ScalaBuildTarget, parent: Option[sbt.internal.bsp.BuildTargetIdentifier], children: Vector[sbt.internal.bsp.BuildTargetIdentifier]): SbtBuildTarget = new SbtBuildTarget(sbtVersion, autoImports, scalaBuildTarget, parent, children)
  def apply(sbtVersion: String, autoImports: Vector[String], scalaBuildTarget: sbt.internal.bsp.ScalaBuildTarget, parent: sbt.internal.bsp.BuildTargetIdentifier, children: Vector[sbt.internal.bsp.BuildTargetIdentifier]): SbtBuildTarget = new SbtBuildTarget(sbtVersion, autoImports, scalaBuildTarget, Option(parent), children)
}
