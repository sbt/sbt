package sbt
package internal
package librarymanagement

import java.io.File
import java.net.URL
import sbt.librarymanagement._

/** Provides extra methods for filtering the contents of an `UpdateReport` and for obtaining references to a selected subset of the underlying files. */
final class RichUpdateReport(report: UpdateReport) {
  def recomputeStamps(): UpdateReport =
    {
      val files = report.cachedDescriptor +: allFiles
      val stamps = files.map(f => (f, f.lastModified)).toMap
      new UpdateReport(report.cachedDescriptor, report.configurations, report.stats, stamps)
    }

  import DependencyFilter._

  /** Obtains all successfully retrieved files in all configurations and modules. */
  def allFiles: Seq[File] = matching(DependencyFilter.allPass)

  /** Obtains all successfully retrieved files in configurations, modules, and artifacts matching the specified filter. */
  def matching(f: DependencyFilter): Seq[File] = select0(f).distinct

  /** Obtains all successfully retrieved files matching all provided filters.  An unspecified argument matches all files. */
  def select(configuration: ConfigurationFilter = configurationFilter(), module: ModuleFilter = moduleFilter(), artifact: ArtifactFilter = artifactFilter()): Seq[File] =
    matching(DependencyFilter.make(configuration, module, artifact))

  private[this] def select0(f: DependencyFilter): Seq[File] =
    for (cReport <- report.configurations; mReport <- cReport.modules; (artifact, file) <- mReport.artifacts if f(cReport.configuration, mReport.module, artifact)) yield {
      if (file == null) sys.error("Null file: conf=" + cReport.configuration + ", module=" + mReport.module + ", art: " + artifact)
      file
    }

  /** Constructs a new report that only contains files matching the specified filter.*/
  private[sbt] def filter(f: DependencyFilter): UpdateReport =
    moduleReportMap { (configuration, modReport) =>
      modReport.copy(
        artifacts = modReport.artifacts filter { case (art, file) => f(configuration, modReport.module, art) },
        missingArtifacts = modReport.missingArtifacts filter { art => f(configuration, modReport.module, art) }
      )
    }
  def substitute(f: (String, ModuleID, Seq[(Artifact, File)]) => Seq[(Artifact, File)]): UpdateReport =
    moduleReportMap { (configuration, modReport) =>
      val newArtifacts = f(configuration, modReport.module, modReport.artifacts)
      modReport.copy(
        artifacts = newArtifacts,
        missingArtifacts = modReport.missingArtifacts
      )
    }

  def toSeq: Seq[(String, ModuleID, Artifact, File)] =
    for (confReport <- report.configurations; modReport <- confReport.modules; (artifact, file) <- modReport.artifacts) yield (confReport.configuration, modReport.module, artifact, file)

  def allMissing: Seq[(String, ModuleID, Artifact)] =
    for (confReport <- report.configurations; modReport <- confReport.modules; artifact <- modReport.missingArtifacts) yield (confReport.configuration, modReport.module, artifact)

  def addMissing(f: ModuleID => Seq[Artifact]): UpdateReport =
    moduleReportMap { (configuration, modReport) =>
      modReport.copy(
        missingArtifacts = (modReport.missingArtifacts ++ f(modReport.module)).distinct
      )
    }

  def moduleReportMap(f: (String, ModuleReport) => ModuleReport): UpdateReport =
    {
      val newConfigurations = report.configurations.map { confReport =>
        import confReport._
        val newModules = modules map { modReport => f(configuration, modReport) }
        new ConfigurationReport(configuration, newModules, details)
      }
      new UpdateReport(report.cachedDescriptor, newConfigurations, report.stats, report.stamps)
    }
}
