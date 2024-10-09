package sbt.internal.librarymanagement.ivyint

import java.util.concurrent.Executors

import org.apache.ivy.core.event.EventManager
import org.apache.ivy.core.event.download.PrepareDownloadEvent
import org.apache.ivy.core.module.descriptor.Artifact
import org.apache.ivy.core.report._
import org.apache.ivy.core.resolve._
import org.apache.ivy.core.sort.SortEngine
import org.apache.ivy.util.filter.Filter

import scala.concurrent.duration.Duration
import scala.concurrent.{ Await, ExecutionContext, Future }

private[ivyint] case class DownloadResult(
    dep: IvyNode,
    report: DownloadReport,
    totalSizeDownloaded: Long
)

object ParallelResolveEngine {
  private lazy val resolveExecutionContext: ExecutionContext = {
    // This throttles the connection number, especially when Gigahorse is not used.
    val maxConnectionCount = 6
    val executor = Executors.newFixedThreadPool(maxConnectionCount)
    ExecutionContext.fromExecutor(executor)
  }
}

/** Define an ivy [[ResolveEngine]] that resolves dependencies in parallel. */
private[sbt] class ParallelResolveEngine(
    settings: ResolveEngineSettings,
    eventManager: EventManager,
    sortEngine: SortEngine
) extends ResolveEngine(settings, eventManager, sortEngine) {

  override def downloadArtifacts(
      report: ResolveReport,
      artifactFilter: Filter,
      options: DownloadOptions
  ): Unit = {
    import scala.jdk.CollectionConverters._
    val start = System.currentTimeMillis
    report.getArtifacts match {
      case typed: java.util.List[Artifact @unchecked] =>
        new PrepareDownloadEvent(typed.asScala.toArray)
    }
    // Farm out the dependencies for parallel download
    implicit val ec = ParallelResolveEngine.resolveExecutionContext
    val allDownloadsFuture = Future.traverse(report.getDependencies.asScala) { case dep: IvyNode =>
      Future {
        if (
          !(dep.isCompletelyEvicted || dep.hasProblem) &&
          dep.getModuleRevision != null
        ) {
          Some(downloadNodeArtifacts(dep, artifactFilter, options))
        } else None
      }
    }
    val allDownloads = Await.result(allDownloadsFuture, Duration.Inf)
    // compute total downloaded size
    val totalSize = allDownloads.foldLeft(0L) {
      case (size, Some(download)) =>
        val dependency = download.dep
        val moduleConfigurations = dependency.getRootModuleConfigurations
        moduleConfigurations.foreach { configuration =>
          val configurationReport = report.getConfigurationReport(configuration)

          // Take into account artifacts required by the given configuration
          if (
            dependency.isEvicted(configuration) ||
            dependency.isBlacklisted(configuration)
          ) {
            configurationReport.addDependency(dependency)
          } else configurationReport.addDependency(dependency, download.report)
        }

        size + download.totalSizeDownloaded
      case (size, None) => size
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
      options: DownloadOptions
  ): DownloadResult = {

    val resolver = dependency.getModuleRevision.getArtifactResolver
    val selectedArtifacts = dependency.getSelectedArtifacts(artifactFilter)
    val downloadReport = resolver.download(selectedArtifacts, options)
    val artifactReports = downloadReport.getArtifactsReports

    val totalSize = artifactReports.foldLeft(0L) { (size, artifactReport) =>
      // Check download status and report resolution failures
      artifactReport.getDownloadStatus match {
        case DownloadStatus.SUCCESSFUL =>
          size + artifactReport.getSize
        case _ => size
      }
    }

    DownloadResult(dependency, downloadReport, totalSize)
  }
}
