package sbt
package ivyint

import org.apache.ivy.core.resolve._
import org.apache.ivy.core.event._
import org.apache.ivy.core.sort.SortEngine
import org.apache.ivy.core.report._
import org.apache.ivy.util.filter.Filter
import org.apache.ivy.core.event.download.PrepareDownloadEvent
import org.apache.ivy.core.module.descriptor.Artifact
import org.apache.ivy.core.IvyContext
import org.apache.ivy.util.Message


  
private[ivyint] case class DownloadResult(dep: IvyNode, report: DownloadReport, totalSizeDownloaded: Long)
/** A variant of the ivy resolver engine that will download artifacts in parallel. */
trait ParallelResolveEngine extends ResolveEngine {
  
  override def downloadArtifacts(
      report: ResolveReport, 
      artifactFilter: Filter,
      options: DownloadOptions): Unit = {
        val start = System.currentTimeMillis
        val dependencies = report.getDependencies().toArray(
            new Array[IvyNode](report.getDependencies.size));

        getEventManager.fireIvyEvent(new PrepareDownloadEvent(report.getArtifacts()
                .toArray(new Array[Artifact](report.getArtifacts.size))))

        var totalSize = 0L
        // Here we farm out the dependencies in parallel for download
        val allDownloads = for {
          // TODO - parallelism controls on .par
          dep <- dependencies.par
          // TODO - ivy context propogation as needed...
          if !dep.isCompletelyEvicted && !dep.hasProblem && dep.getModuleRevision != null
        } yield downloadArtifactSolo(dep, artifactFilter, options)
        // Here we force ourselves into a sequential mode again before continuing.
        for (DownloadResult(dep, dReport, depSize) <- allDownloads.toArray) {
            // download artifacts required in all asked configurations
            // update concerned reports
            val dconfs = dep.getRootModuleConfigurations
            for (conf <- dconfs) {
                // the report itself is responsible to take into account only
                // artifacts required in its corresponding configuration
                // (as described by the Dependency object)
                if (dep.isEvicted(conf)
                        || dep.isBlacklisted(conf)) {
                    report.getConfigurationReport(conf).addDependency(dep)
                } else {
                    report.getConfigurationReport(conf).addDependency(dep, dReport)
                }
            }
        }
        report.setDownloadTime(System.currentTimeMillis() - start)
        report.setDownloadSize(totalSize)
  }
  /** 
   *  given a well-structured ivy node, we download its artifacts and return the report/total size of
   *  Downloaded things.
   */
  private def downloadArtifactSolo(dep: IvyNode, artifactFilter: Filter, options: DownloadOptions): DownloadResult = {
    var totalSize = 0L
    val resolver = dep.getModuleRevision.getArtifactResolver
    val selectedArtifacts = dep.getSelectedArtifacts(artifactFilter)
    val dReport = resolver.download(selectedArtifacts, options)
    val adrs = dReport.getArtifactsReports
    for (adr <- adrs) {
      // TODO - We may need to lift all messages into something we tell ivy to do later.
      adr.getDownloadStatus match {
        case DownloadStatus.SUCCESSFUL => totalSize += adr.getSize
        case DownloadStatus.FAILED =>
          if (adr.getArtifact().getExtraAttribute("ivy:merged") != null) {
            Message.warn("\tmerged artifact not found: " + adr.getArtifact
                        + ". It was required in "
                        + adr.getArtifact.getExtraAttribute("ivy:merged"));
          } else {
              Message.warn("\t" + adr);
              resolver.reportFailure(adr.getArtifact());
          }
        case _ => // TODO - anything here?
      }
      if (adr.getDownloadStatus == DownloadStatus.FAILED) {
        if (adr.getArtifact().getExtraAttribute("ivy:merged") != null) {
          Message.warn("\tmerged artifact not found: " + adr.getArtifact
                        + ". It was required in "
                        + adr.getArtifact.getExtraAttribute("ivy:merged"));
        } else {
            Message.warn("\t" + adr);
            resolver.reportFailure(adr.getArtifact());
        }
      } else if (adr.getDownloadStatus() == DownloadStatus.SUCCESSFUL) {
        totalSize += adr.getSize
      }
    }
    DownloadResult(dep, dReport, totalSize)
  }
}