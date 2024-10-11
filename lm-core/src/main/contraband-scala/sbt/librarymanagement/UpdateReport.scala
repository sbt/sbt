/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
/**
 * Provides information about dependency resolution.
 * It does not include information about evicted modules, only about the modules ultimately selected by the conflict manager.
 * This means that for a given configuration, there should only be one revision for a given organization and module name.
 * @param cachedDescriptor the location of the resolved module descriptor in the cache
 * @param configurations a sequence containing one report for each configuration resolved.
 * @param stats stats information about the update that produced this report
 */
final class UpdateReport private (
  val cachedDescriptor: java.io.File,
  val configurations: Vector[sbt.librarymanagement.ConfigurationReport],
  val stats: sbt.librarymanagement.UpdateStats,
  val stamps: Map[String, Long]) extends sbt.librarymanagement.UpdateReportExtra with Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: UpdateReport => (this.cachedDescriptor == x.cachedDescriptor) && (this.configurations == x.configurations) && (this.stats == x.stats) && (this.stamps == x.stamps)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (17 + "sbt.librarymanagement.UpdateReport".##) + cachedDescriptor.##) + configurations.##) + stats.##) + stamps.##)
  }
  override def toString: String = {
    "Update report:\n\t" + stats + "\n" + configurations.mkString
  }
  private[this] def copy(cachedDescriptor: java.io.File = cachedDescriptor, configurations: Vector[sbt.librarymanagement.ConfigurationReport] = configurations, stats: sbt.librarymanagement.UpdateStats = stats, stamps: Map[String, Long] = stamps): UpdateReport = {
    new UpdateReport(cachedDescriptor, configurations, stats, stamps)
  }
  def withCachedDescriptor(cachedDescriptor: java.io.File): UpdateReport = {
    copy(cachedDescriptor = cachedDescriptor)
  }
  def withConfigurations(configurations: Vector[sbt.librarymanagement.ConfigurationReport]): UpdateReport = {
    copy(configurations = configurations)
  }
  def withStats(stats: sbt.librarymanagement.UpdateStats): UpdateReport = {
    copy(stats = stats)
  }
  def withStamps(stamps: Map[String, Long]): UpdateReport = {
    copy(stamps = stamps)
  }
}
object UpdateReport {
  
  def apply(cachedDescriptor: java.io.File, configurations: Vector[sbt.librarymanagement.ConfigurationReport], stats: sbt.librarymanagement.UpdateStats, stamps: Map[String, Long]): UpdateReport = new UpdateReport(cachedDescriptor, configurations, stats, stamps)
}
