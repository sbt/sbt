/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
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
 */
final class ScalaBuildTarget private (
  val scalaOrganization: String,
  val scalaVersion: String,
  val scalaBinaryVersion: String,
  val platform: Int,
  val jars: Vector[String]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: ScalaBuildTarget => (this.scalaOrganization == x.scalaOrganization) && (this.scalaVersion == x.scalaVersion) && (this.scalaBinaryVersion == x.scalaBinaryVersion) && (this.platform == x.platform) && (this.jars == x.jars)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (37 * (17 + "sbt.internal.bsp.ScalaBuildTarget".##) + scalaOrganization.##) + scalaVersion.##) + scalaBinaryVersion.##) + platform.##) + jars.##)
  }
  override def toString: String = {
    "ScalaBuildTarget(" + scalaOrganization + ", " + scalaVersion + ", " + scalaBinaryVersion + ", " + platform + ", " + jars + ")"
  }
  private[this] def copy(scalaOrganization: String = scalaOrganization, scalaVersion: String = scalaVersion, scalaBinaryVersion: String = scalaBinaryVersion, platform: Int = platform, jars: Vector[String] = jars): ScalaBuildTarget = {
    new ScalaBuildTarget(scalaOrganization, scalaVersion, scalaBinaryVersion, platform, jars)
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
}
object ScalaBuildTarget {
  
  def apply(scalaOrganization: String, scalaVersion: String, scalaBinaryVersion: String, platform: Int, jars: Vector[String]): ScalaBuildTarget = new ScalaBuildTarget(scalaOrganization, scalaVersion, scalaBinaryVersion, platform, jars)
}
