/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
final class IvyScala private (
  val scalaFullVersion: String,
  val scalaBinaryVersion: String,
  val configurations: Vector[sbt.librarymanagement.Configuration],
  val checkExplicit: Boolean,
  val filterImplicit: Boolean,
  val overrideScalaVersion: Boolean,
  val scalaOrganization: String,
  val scalaArtifacts: scala.Vector[String]) extends Serializable {
  
  private def this(scalaFullVersion: String, scalaBinaryVersion: String, configurations: Vector[sbt.librarymanagement.Configuration], checkExplicit: Boolean, filterImplicit: Boolean, overrideScalaVersion: Boolean) = this(scalaFullVersion, scalaBinaryVersion, configurations, checkExplicit, filterImplicit, overrideScalaVersion, sbt.librarymanagement.ScalaArtifacts.Organization, sbt.librarymanagement.ScalaArtifacts.Artifacts)
  
  override def equals(o: Any): Boolean = o match {
    case x: IvyScala => (this.scalaFullVersion == x.scalaFullVersion) && (this.scalaBinaryVersion == x.scalaBinaryVersion) && (this.configurations == x.configurations) && (this.checkExplicit == x.checkExplicit) && (this.filterImplicit == x.filterImplicit) && (this.overrideScalaVersion == x.overrideScalaVersion) && (this.scalaOrganization == x.scalaOrganization) && (this.scalaArtifacts == x.scalaArtifacts)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (17 + "IvyScala".##) + scalaFullVersion.##) + scalaBinaryVersion.##) + configurations.##) + checkExplicit.##) + filterImplicit.##) + overrideScalaVersion.##) + scalaOrganization.##) + scalaArtifacts.##)
  }
  override def toString: String = {
    "IvyScala(" + scalaFullVersion + ", " + scalaBinaryVersion + ", " + configurations + ", " + checkExplicit + ", " + filterImplicit + ", " + overrideScalaVersion + ", " + scalaOrganization + ", " + scalaArtifacts + ")"
  }
  protected[this] def copy(scalaFullVersion: String = scalaFullVersion, scalaBinaryVersion: String = scalaBinaryVersion, configurations: Vector[sbt.librarymanagement.Configuration] = configurations, checkExplicit: Boolean = checkExplicit, filterImplicit: Boolean = filterImplicit, overrideScalaVersion: Boolean = overrideScalaVersion, scalaOrganization: String = scalaOrganization, scalaArtifacts: scala.Vector[String] = scalaArtifacts): IvyScala = {
    new IvyScala(scalaFullVersion, scalaBinaryVersion, configurations, checkExplicit, filterImplicit, overrideScalaVersion, scalaOrganization, scalaArtifacts)
  }
  def withScalaFullVersion(scalaFullVersion: String): IvyScala = {
    copy(scalaFullVersion = scalaFullVersion)
  }
  def withScalaBinaryVersion(scalaBinaryVersion: String): IvyScala = {
    copy(scalaBinaryVersion = scalaBinaryVersion)
  }
  def withConfigurations(configurations: Vector[sbt.librarymanagement.Configuration]): IvyScala = {
    copy(configurations = configurations)
  }
  def withCheckExplicit(checkExplicit: Boolean): IvyScala = {
    copy(checkExplicit = checkExplicit)
  }
  def withFilterImplicit(filterImplicit: Boolean): IvyScala = {
    copy(filterImplicit = filterImplicit)
  }
  def withOverrideScalaVersion(overrideScalaVersion: Boolean): IvyScala = {
    copy(overrideScalaVersion = overrideScalaVersion)
  }
  def withScalaOrganization(scalaOrganization: String): IvyScala = {
    copy(scalaOrganization = scalaOrganization)
  }
  def withScalaArtifacts(scalaArtifacts: scala.Vector[String]): IvyScala = {
    copy(scalaArtifacts = scalaArtifacts)
  }
}
object IvyScala extends sbt.librarymanagement.IvyScalaFunctions {
  
  def apply(scalaFullVersion: String, scalaBinaryVersion: String, configurations: Vector[sbt.librarymanagement.Configuration], checkExplicit: Boolean, filterImplicit: Boolean, overrideScalaVersion: Boolean): IvyScala = new IvyScala(scalaFullVersion, scalaBinaryVersion, configurations, checkExplicit, filterImplicit, overrideScalaVersion, sbt.librarymanagement.ScalaArtifacts.Organization, sbt.librarymanagement.ScalaArtifacts.Artifacts)
  def apply(scalaFullVersion: String, scalaBinaryVersion: String, configurations: Vector[sbt.librarymanagement.Configuration], checkExplicit: Boolean, filterImplicit: Boolean, overrideScalaVersion: Boolean, scalaOrganization: String, scalaArtifacts: scala.Vector[String]): IvyScala = new IvyScala(scalaFullVersion, scalaBinaryVersion, configurations, checkExplicit, filterImplicit, overrideScalaVersion, scalaOrganization, scalaArtifacts)
}
