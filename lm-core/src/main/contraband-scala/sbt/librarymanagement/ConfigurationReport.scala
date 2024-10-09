/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
/**
 * Provides information about resolution of a single configuration.
 * @param configuration the configuration this report is for.
 * @param modules a sequence containing one report for each module resolved for this configuration.
 * @param details a sequence containing one report for each org/name, which may or may not be part of the final resolution.
 */
final class ConfigurationReport private (
  val configuration: sbt.librarymanagement.ConfigRef,
  val modules: Vector[sbt.librarymanagement.ModuleReport],
  val details: Vector[sbt.librarymanagement.OrganizationArtifactReport]) extends sbt.librarymanagement.ConfigurationReportExtra with Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: ConfigurationReport => (this.configuration == x.configuration) && (this.modules == x.modules) && (this.details == x.details)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (17 + "sbt.librarymanagement.ConfigurationReport".##) + configuration.##) + modules.##) + details.##)
  }
  override def toString: String = {
    s"\t$configuration:\n" +
    (if (details.isEmpty) modules.mkString + details.flatMap(_.modules).filter(_.evicted).map("\t\t(EVICTED) " + _ + "\n").mkString
    else details.mkString)
  }
  private[this] def copy(configuration: sbt.librarymanagement.ConfigRef = configuration, modules: Vector[sbt.librarymanagement.ModuleReport] = modules, details: Vector[sbt.librarymanagement.OrganizationArtifactReport] = details): ConfigurationReport = {
    new ConfigurationReport(configuration, modules, details)
  }
  def withConfiguration(configuration: sbt.librarymanagement.ConfigRef): ConfigurationReport = {
    copy(configuration = configuration)
  }
  def withModules(modules: Vector[sbt.librarymanagement.ModuleReport]): ConfigurationReport = {
    copy(modules = modules)
  }
  def withDetails(details: Vector[sbt.librarymanagement.OrganizationArtifactReport]): ConfigurationReport = {
    copy(details = details)
  }
}
object ConfigurationReport {
  
  def apply(configuration: sbt.librarymanagement.ConfigRef, modules: Vector[sbt.librarymanagement.ModuleReport], details: Vector[sbt.librarymanagement.OrganizationArtifactReport]): ConfigurationReport = new ConfigurationReport(configuration, modules, details)
}
