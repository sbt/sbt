/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp
/**
 * Contains scala-specific metadata for compiling a target containing Scala sources.
 * This metadata is embedded in the data: Option[Json] field of the BuildTarget definition,
 * when the dataKind field contains "scala".
 * @param scalaOrganization The Scala organization that is used for a target.
 * @param scalaVersion The scala version to compile this target
 * @param scalaBinaryVersion The binary version of scalaVersion.
                             For example, 2.12 if scalaVersion is 2.12.4.
 * @param platform The target platform for this target
 * @param jars A sequence of Scala jars such as scala-library, scala-compiler and scala-reflect.
 * @param jvmBuildTarget The jvm build target describing jdk to be used
 */
final class ScalaBuildTarget private (
  val scalaOrganization: String,
  val scalaVersion: String,
  val scalaBinaryVersion: String,
  val platform: Int,
  val jars: Vector[String],
  val jvmBuildTarget: Option[sbt.internal.bsp.JvmBuildTarget]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: ScalaBuildTarget => (this.scalaOrganization == x.scalaOrganization) && (this.scalaVersion == x.scalaVersion) && (this.scalaBinaryVersion == x.scalaBinaryVersion) && (this.platform == x.platform) && (this.jars == x.jars) && (this.jvmBuildTarget == x.jvmBuildTarget)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (37 * (37 * (17 + "sbt.internal.bsp.ScalaBuildTarget".##) + scalaOrganization.##) + scalaVersion.##) + scalaBinaryVersion.##) + platform.##) + jars.##) + jvmBuildTarget.##)
  }
  override def toString: String = {
    "ScalaBuildTarget(" + scalaOrganization + ", " + scalaVersion + ", " + scalaBinaryVersion + ", " + platform + ", " + jars + ", " + jvmBuildTarget + ")"
  }
  private def copy(scalaOrganization: String = scalaOrganization, scalaVersion: String = scalaVersion, scalaBinaryVersion: String = scalaBinaryVersion, platform: Int = platform, jars: Vector[String] = jars, jvmBuildTarget: Option[sbt.internal.bsp.JvmBuildTarget] = jvmBuildTarget): ScalaBuildTarget = {
    new ScalaBuildTarget(scalaOrganization, scalaVersion, scalaBinaryVersion, platform, jars, jvmBuildTarget)
  }
  def withScalaOrganization(scalaOrganization: String): ScalaBuildTarget = {
    copy(scalaOrganization = scalaOrganization)
  }
  def withScalaVersion(scalaVersion: String): ScalaBuildTarget = {
    copy(scalaVersion = scalaVersion)
  }
  def withScalaBinaryVersion(scalaBinaryVersion: String): ScalaBuildTarget = {
    copy(scalaBinaryVersion = scalaBinaryVersion)
  }
  def withPlatform(platform: Int): ScalaBuildTarget = {
    copy(platform = platform)
  }
  def withJars(jars: Vector[String]): ScalaBuildTarget = {
    copy(jars = jars)
  }
  def withJvmBuildTarget(jvmBuildTarget: Option[sbt.internal.bsp.JvmBuildTarget]): ScalaBuildTarget = {
    copy(jvmBuildTarget = jvmBuildTarget)
  }
  def withJvmBuildTarget(jvmBuildTarget: sbt.internal.bsp.JvmBuildTarget): ScalaBuildTarget = {
    copy(jvmBuildTarget = Option(jvmBuildTarget))
  }
}
object ScalaBuildTarget {
  
  def apply(scalaOrganization: String, scalaVersion: String, scalaBinaryVersion: String, platform: Int, jars: Vector[String], jvmBuildTarget: Option[sbt.internal.bsp.JvmBuildTarget]): ScalaBuildTarget = new ScalaBuildTarget(scalaOrganization, scalaVersion, scalaBinaryVersion, platform, jars, jvmBuildTarget)
  def apply(scalaOrganization: String, scalaVersion: String, scalaBinaryVersion: String, platform: Int, jars: Vector[String], jvmBuildTarget: sbt.internal.bsp.JvmBuildTarget): ScalaBuildTarget = new ScalaBuildTarget(scalaOrganization, scalaVersion, scalaBinaryVersion, platform, jars, Option(jvmBuildTarget))
}
