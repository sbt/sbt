package sbt.ivyint

import org.apache.ivy.core.event.EventManager
import org.apache.ivy.core.resolve._
import org.apache.ivy.core.report._
import org.apache.ivy.util.filter.Filter
import org.apache.ivy.core.event.download.PrepareDownloadEvent
import org.apache.ivy.core.module.descriptor.Artifact
import org.apache.ivy.core.sort.SortEngine
import org.apache.ivy.util.Message

import scala.collection.parallel.mutable.ParArray

private[ivyint] case class DownloadResult(dep: IvyNode,
  report: DownloadReport,
  totalSizeDownloaded: Long)

/** Define an ivy [[ResolveEngine]] that resolves dependencies in parallel. */
private[sbt] class ParallelResolveEngine(
    settings: ResolveEngineSettings,
    eventManager: EventManager,
    sortEngine: SortEngine) extends ResolveEngine(settings, eventManager, sortEngine) {

  override def downloadArtifacts(report: ResolveReport,
    artifactFilter: Filter,
    options: DownloadOptions): Unit = {

    val start = System.currentTimeMillis
    val dependencies0 = report.getDependencies
    val dependencies = dependencies0
      .toArray(new Array[IvyNode](dependencies0.size))
    val artifacts = report.getArtifacts
      .toArray(new Array[Artifact](report.getArtifacts.size))

    getEventManager.fireIvyEvent(new PrepareDownloadEvent(artifacts))

    // Farm out the dependencies for parallel download
    val allDownloads = dependencies.par.flatMap { dep =>
      if (!(dep.isCompletelyEvicted || dep.hasProblem) &&
        dep.getModuleRevision != null) {
        ParArray(downloadNodeArtifacts(dep, artifactFilter, options))
      } else ParArray.empty[DownloadResult]
    }

    // Force parallel downloads
    val forcedDownloads = allDownloads.toArray

    var totalSize = 0L
    forcedDownloads.foreach { download =>
      val dependency = download.dep
      val moduleConfigurations = dependency.getRootModuleConfigurations
      totalSize += download.totalSizeDownloaded

      moduleConfigurations.foreach { configuration =>
        val configurationReport = report.getConfigurationReport(configuration)

        // Take into account artifacts required by the given configuration
        if (dependency.isEvicted(configuration) ||
          dependency.isBlacklisted(configuration)) {
          configurationReport.addDependency(dependency)
        } else configurationReport.addDependency(dependency, download.report)
      }
    }

    report.setDownloadTime(System.currentTimeMillis() - start)
    report.setDownloadSize(totalSize)
  }

  /**
   * Download all the artifacts associated with an ivy node.
   *
   * Return the report and the total downloaded size.
   */
  private def downloadNodeArtifacts(
    dependency: IvyNode,
    artifactFilter: Filter,
    options: DownloadOptions): DownloadResult = {

    val resolver = dependency.getModuleRevision.getArtifactResolver
    val selectedArtifacts = dependency.getSelectedArtifacts(artifactFilter)
    val downloadReport = resolver.download(selectedArtifacts, options)
    val artifactReports = downloadReport.getArtifactsReports

    val totalSize = artifactReports.foldLeft(0L) { (size, artifactReport) =>
      // Check download status and report resolution failures
      artifactReport.getDownloadStatus match {
        case DownloadStatus.SUCCESSFUL =>
          size + artifactReport.getSize
        case DownloadStatus.FAILED =>
          val artifact = artifactReport.getArtifact
          val mergedAttribute = artifact.getExtraAttribute("ivy:merged")
          if (mergedAttribute != null) {
            Message.warn(
              s"\tMissing merged artifact: $artifact, required by $mergedAttribute.")
          } else {
            Message.warn(s"\tDetected merged artifact: $artifactReport.")
            resolver.reportFailure(artifactReport.getArtifact)
          }
          size
        case _ => size
      }
    }

    DownloadResult(dependency, downloadReport, totalSize)
  }
}
