/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
final class ScalaModuleInfo private (
  val scalaFullVersion: String,
  val scalaBinaryVersion: String,
  val configurations: Vector[sbt.librarymanagement.Configuration],
  val checkExplicit: Boolean,
  val filterImplicit: Boolean,
  val overrideScalaVersion: Boolean,
  val scalaOrganization: String,
  val scalaArtifacts: scala.Vector[String],
  val platform: Option[String]) extends Serializable {
  
  private def this(scalaFullVersion: String, scalaBinaryVersion: String, configurations: Vector[sbt.librarymanagement.Configuration], checkExplicit: Boolean, filterImplicit: Boolean, overrideScalaVersion: Boolean) = this(scalaFullVersion, scalaBinaryVersion, configurations, checkExplicit, filterImplicit, overrideScalaVersion, sbt.librarymanagement.ScalaArtifacts.Organization, sbt.librarymanagement.ScalaArtifacts.Artifacts, None)
  private def this(scalaFullVersion: String, scalaBinaryVersion: String, configurations: Vector[sbt.librarymanagement.Configuration], checkExplicit: Boolean, filterImplicit: Boolean, overrideScalaVersion: Boolean, scalaOrganization: String, scalaArtifacts: scala.Vector[String]) = this(scalaFullVersion, scalaBinaryVersion, configurations, checkExplicit, filterImplicit, overrideScalaVersion, scalaOrganization, scalaArtifacts, None)
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: ScalaModuleInfo => (this.scalaFullVersion == x.scalaFullVersion) && (this.scalaBinaryVersion == x.scalaBinaryVersion) && (this.configurations == x.configurations) && (this.checkExplicit == x.checkExplicit) && (this.filterImplicit == x.filterImplicit) && (this.overrideScalaVersion == x.overrideScalaVersion) && (this.scalaOrganization == x.scalaOrganization) && (this.scalaArtifacts == x.scalaArtifacts) && (this.platform == x.platform)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (17 + "sbt.librarymanagement.ScalaModuleInfo".##) + scalaFullVersion.##) + scalaBinaryVersion.##) + configurations.##) + checkExplicit.##) + filterImplicit.##) + overrideScalaVersion.##) + scalaOrganization.##) + scalaArtifacts.##) + platform.##)
  }
  override def toString: String = {
    "ScalaModuleInfo(" + scalaFullVersion + ", " + scalaBinaryVersion + ", " + configurations + ", " + checkExplicit + ", " + filterImplicit + ", " + overrideScalaVersion + ", " + scalaOrganization + ", " + scalaArtifacts + ", " + platform + ")"
  }
  private[this] def copy(scalaFullVersion: String = scalaFullVersion, scalaBinaryVersion: String = scalaBinaryVersion, configurations: Vector[sbt.librarymanagement.Configuration] = configurations, checkExplicit: Boolean = checkExplicit, filterImplicit: Boolean = filterImplicit, overrideScalaVersion: Boolean = overrideScalaVersion, scalaOrganization: String = scalaOrganization, scalaArtifacts: scala.Vector[String] = scalaArtifacts, platform: Option[String] = platform): ScalaModuleInfo = {
    new ScalaModuleInfo(scalaFullVersion, scalaBinaryVersion, configurations, checkExplicit, filterImplicit, overrideScalaVersion, scalaOrganization, scalaArtifacts, platform)
  }
  def withScalaFullVersion(scalaFullVersion: String): ScalaModuleInfo = {
    copy(scalaFullVersion = scalaFullVersion)
  }
  def withScalaBinaryVersion(scalaBinaryVersion: String): ScalaModuleInfo = {
    copy(scalaBinaryVersion = scalaBinaryVersion)
  }
  def withConfigurations(configurations: Vector[sbt.librarymanagement.Configuration]): ScalaModuleInfo = {
    copy(configurations = configurations)
  }
  def withCheckExplicit(checkExplicit: Boolean): ScalaModuleInfo = {
    copy(checkExplicit = checkExplicit)
  }
  def withFilterImplicit(filterImplicit: Boolean): ScalaModuleInfo = {
    copy(filterImplicit = filterImplicit)
  }
  def withOverrideScalaVersion(overrideScalaVersion: Boolean): ScalaModuleInfo = {
    copy(overrideScalaVersion = overrideScalaVersion)
  }
  def withScalaOrganization(scalaOrganization: String): ScalaModuleInfo = {
    copy(scalaOrganization = scalaOrganization)
  }
  def withScalaArtifacts(scalaArtifacts: scala.Vector[String]): ScalaModuleInfo = {
    copy(scalaArtifacts = scalaArtifacts)
  }
  def withPlatform(platform: Option[String]): ScalaModuleInfo = {
    copy(platform = platform)
  }
}
object ScalaModuleInfo {
  
  def apply(scalaFullVersion: String, scalaBinaryVersion: String, configurations: Vector[sbt.librarymanagement.Configuration], checkExplicit: Boolean, filterImplicit: Boolean, overrideScalaVersion: Boolean): ScalaModuleInfo = new ScalaModuleInfo(scalaFullVersion, scalaBinaryVersion, configurations, checkExplicit, filterImplicit, overrideScalaVersion)
  def apply(scalaFullVersion: String, scalaBinaryVersion: String, configurations: Vector[sbt.librarymanagement.Configuration], checkExplicit: Boolean, filterImplicit: Boolean, overrideScalaVersion: Boolean, scalaOrganization: String, scalaArtifacts: scala.Vector[String]): ScalaModuleInfo = new ScalaModuleInfo(scalaFullVersion, scalaBinaryVersion, configurations, checkExplicit, filterImplicit, overrideScalaVersion, scalaOrganization, scalaArtifacts)
  def apply(scalaFullVersion: String, scalaBinaryVersion: String, configurations: Vector[sbt.librarymanagement.Configuration], checkExplicit: Boolean, filterImplicit: Boolean, overrideScalaVersion: Boolean, scalaOrganization: String, scalaArtifacts: scala.Vector[String], platform: Option[String]): ScalaModuleInfo = new ScalaModuleInfo(scalaFullVersion, scalaBinaryVersion, configurations, checkExplicit, filterImplicit, overrideScalaVersion, scalaOrganization, scalaArtifacts, platform)
}
