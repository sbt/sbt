package sbt
package librarymanagement

import java.io.File
import sbt.io.IO

/**
 * Provides extra methods for filtering the contents of an `UpdateReport`
 * and for obtaining references to a selected subset of the underlying files.
 */
final class RichUpdateReport(report: UpdateReport) {
  private[sbt] def recomputeStamps(): UpdateReport = {
    val files = report.cachedDescriptor +: allFiles
    val stamps = files
      .map(f =>
        (
          f.toString,
          // TODO: The list of files may also contain some odd files that do not actually exist like:
          // "./target/ivyhome/resolution-cache/com.example/foo/0.4.0/resolved.xml.xml".
          // IO.getModifiedTimeOrZero() will just return zero, but the list of files should not contain such
          // files to begin with, in principle.
          IO.getModifiedTimeOrZero(f)
        )
      )
      .toMap
    UpdateReport(report.cachedDescriptor, report.configurations, report.stats, stamps)
  }

  import DependencyFilter.*

  /** Obtains all successfully retrieved files in all configurations and modules. */
  def allFiles: Vector[File] = matching(DependencyFilter.allPass)

  /** Obtains all successfully retrieved files in configurations, modules, and artifacts matching the specified filter. */
  def matching(f: DependencyFilter): Vector[File] = select0(f).distinct

  /** Obtains all successfully retrieved files matching all provided filters. */
  def select(
      configuration: ConfigurationFilter,
      module: ModuleFilter,
      artifact: ArtifactFilter
  ): Vector[File] =
    matching(DependencyFilter.make(configuration, module, artifact))

  def select(configuration: ConfigurationFilter): Vector[File] =
    select(configuration, moduleFilter(), artifactFilter())
  def select(module: ModuleFilter): Vector[File] =
    select(configurationFilter(), module, artifactFilter())
  def select(artifact: ArtifactFilter): Vector[File] =
    select(configurationFilter(), moduleFilter(), artifact)

  private def select0(f: DependencyFilter): Vector[File] =
    for {
      cReport <- report.configurations
      mReport <- cReport.modules
      (artifact, file) <- mReport.artifacts
      if f(cReport.configuration, mReport.module, artifact)
    } yield {
      if (file == null) {
        sys.error(
          s"Null file: conf=${cReport.configuration}, module=${mReport.module}, art: $artifact"
        )
      }
      file
    }

  /** Constructs a new report that only contains files matching the specified filter. */
  def filter(f: DependencyFilter): UpdateReport =
    moduleReportMap { (configuration, modReport) =>
      modReport
        .withArtifacts(
          modReport.artifacts filter { case (art, _) =>
            f(configuration, modReport.module, art)
          }
        )
        .withMissingArtifacts(
          modReport.missingArtifacts filter { art =>
            f(configuration, modReport.module, art)
          }
        )
    }

  private[sbt] def substitute(
      f: (ConfigRef, ModuleID, Vector[(Artifact, File)]) => Vector[(Artifact, File)]
  ): UpdateReport =
    moduleReportMap { (configuration, modReport) =>
      val newArtifacts = f(configuration, modReport.module, modReport.artifacts)
      modReport
        .withArtifacts(newArtifacts)
        .withMissingArtifacts(modReport.missingArtifacts)
    }

  def toSeq: Seq[(ConfigRef, ModuleID, Artifact, File)] = toVector
  def toVector: Vector[(ConfigRef, ModuleID, Artifact, File)] =
    for {
      confReport <- report.configurations
      modReport <- confReport.modules
      (artifact, file) <- modReport.artifacts
    } yield (confReport.configuration, modReport.module, artifact, file)

  def allMissing: Vector[(ConfigRef, ModuleID, Artifact)] =
    for {
      confReport <- report.configurations
      modReport <- confReport.modules
      artifact <- modReport.missingArtifacts
    } yield (confReport.configuration, modReport.module, artifact)

  private[sbt] def addMissing(f: ModuleID => Seq[Artifact]): UpdateReport =
    moduleReportMap { (configuration, modReport) =>
      modReport
        .withMissingArtifacts((modReport.missingArtifacts ++ f(modReport.module)).distinct)
    }

  private[sbt] def moduleReportMap(f: (ConfigRef, ModuleReport) => ModuleReport): UpdateReport = {
    val newConfigurations = report.configurations.map { confReport =>
      import confReport.*
      val newModules = modules map { modReport =>
        f(configuration, modReport)
      }
      ConfigurationReport(configuration, newModules, details)
    }
    UpdateReport(report.cachedDescriptor, newConfigurations, report.stats, report.stamps)
  }
}
