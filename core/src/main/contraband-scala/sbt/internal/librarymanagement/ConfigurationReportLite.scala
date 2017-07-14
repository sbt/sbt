/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.librarymanagement
final class ConfigurationReportLite private (
  val configuration: String,
  val details: Vector[sbt.librarymanagement.OrganizationArtifactReport]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: ConfigurationReportLite => (this.configuration == x.configuration) && (this.details == x.details)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "sbt.internal.librarymanagement.ConfigurationReportLite".##) + configuration.##) + details.##)
  }
  override def toString: String = {
    "ConfigurationReportLite(" + configuration + ", " + details + ")"
  }
  protected[this] def copy(configuration: String = configuration, details: Vector[sbt.librarymanagement.OrganizationArtifactReport] = details): ConfigurationReportLite = {
    new ConfigurationReportLite(configuration, details)
  }
  def withConfiguration(configuration: String): ConfigurationReportLite = {
    copy(configuration = configuration)
  }
  def withDetails(details: Vector[sbt.librarymanagement.OrganizationArtifactReport]): ConfigurationReportLite = {
    copy(details = details)
  }
}
object ConfigurationReportLite {
  
  def apply(configuration: String, details: Vector[sbt.librarymanagement.OrganizationArtifactReport]): ConfigurationReportLite = new ConfigurationReportLite(configuration, details)
}
