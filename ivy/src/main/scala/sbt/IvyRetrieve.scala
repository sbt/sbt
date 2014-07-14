/* sbt -- Simple Build Tool
 * Copyright 2010  Mark Harrah
 */
package sbt

import java.io.File
import collection.mutable
import org.apache.ivy.core.{ module, report, resolve }
import module.descriptor.{ Artifact => IvyArtifact }
import module.id.ModuleRevisionId
import resolve.IvyNode
import report.{ ArtifactDownloadReport, ConfigurationResolveReport, ResolveReport }

object IvyRetrieve {
  def reports(report: ResolveReport): Seq[ConfigurationResolveReport] =
    report.getConfigurations map report.getConfigurationReport

  def moduleReports(confReport: ConfigurationResolveReport): Seq[ModuleReport] =
    for (revId <- confReport.getModuleRevisionIds.toArray collect { case revId: ModuleRevisionId => revId }) yield artifactReports(toModuleID(revId), confReport getDownloadReports revId)

  def artifactReports(mid: ModuleID, artReport: Seq[ArtifactDownloadReport]): ModuleReport =
    {
      val missing = new mutable.ListBuffer[Artifact]
      val resolved = new mutable.ListBuffer[(Artifact, File)]
      for (r <- artReport) {
        val file = r.getLocalFile
        val art = toArtifact(r.getArtifact)
        if (file eq null)
          missing += art
        else
          resolved += ((art, file))
      }
      new ModuleReport(mid, resolved.toSeq, missing.toSeq)
    }

  def evicted(confReport: ConfigurationResolveReport): Seq[ModuleID] =
    confReport.getEvictedNodes.map(node => toModuleID(node.getId))

  def toModuleID(revID: ModuleRevisionId): ModuleID =
    ModuleID(revID.getOrganisation, revID.getName, revID.getRevision, extraAttributes = IvySbt.getExtraAttributes(revID))

  def toArtifact(art: IvyArtifact): Artifact =
    {
      import art._
      Artifact(getName, getType, getExt, Option(getExtraAttribute("classifier")), getConfigurations map Configurations.config, Option(getUrl))
    }

  def updateReport(report: ResolveReport, cachedDescriptor: File): UpdateReport =
    new UpdateReport(cachedDescriptor, reports(report) map configurationReport, updateStats(report), Map.empty) recomputeStamps ()
  def updateStats(report: ResolveReport): UpdateStats =
    new UpdateStats(report.getResolveTime, report.getDownloadTime, report.getDownloadSize, false)
  def configurationReport(confReport: ConfigurationResolveReport): ConfigurationReport =
    new ConfigurationReport(confReport.getConfiguration, moduleReports(confReport), evicted(confReport))

  /**
   * Tries to find Ivy graph path the from node to target.
   */
  def findPath(target: IvyNode, from: ModuleRevisionId): List[IvyNode] = {
    def doFindPath(current: IvyNode, path: List[IvyNode]): List[IvyNode] = {
      val callers = current.getAllRealCallers.toList
      // Ivy actually returns non-direct callers here.
      // that's why we have to calculate all possible paths below and pick the longest path.
      val directCallers = callers filter { caller =>
        val md = caller.getModuleDescriptor
        val dd = md.getDependencies.toList find { dd =>
          (dd.getDependencyRevisionId == current.getId) &&
            (dd.getParentRevisionId == caller.getModuleRevisionId)
        }
        dd.isDefined
      }
      val directCallersRevId = (directCallers map { _.getModuleRevisionId }).distinct
      val paths: List[List[IvyNode]] = ((directCallersRevId map { revId =>
        val node = current.findNode(revId)
        if (revId == from) node :: path
        else if (node == node.getRoot) Nil
        else if (path contains node) path
        else doFindPath(node, node :: path)
      }) sortBy { _.size }).reverse
      paths.headOption getOrElse Nil
    }
    if (target.getId == from) List(target)
    else doFindPath(target, List(target))
  }
}
