/* sbt -- Simple Build Tool
 * Copyright 2010  Mark Harrah
 */
package sbt

import java.io.File
import collection.mutable

import org.apache.ivy.core.{module, report}
import module.descriptor.{Artifact => IvyArtifact}
import module.id.ModuleRevisionId
import report.{ArtifactDownloadReport, ConfigurationResolveReport, ResolveReport}

object IvyRetrieve
{
	def reports(report: ResolveReport): Seq[ConfigurationResolveReport] =
		report.getConfigurations map report.getConfigurationReport

	def moduleReports(confReport: ConfigurationResolveReport): Seq[ModuleReport] =
		for( revId <- confReport.getModuleRevisionIds.toArray collect { case revId: ModuleRevisionId => revId }) yield
			artifactReports(toModuleID(revId), confReport getDownloadReports revId)

	def artifactReports(mid: ModuleID, artReport: Seq[ArtifactDownloadReport]): ModuleReport =
	{
		val missing = new mutable.ListBuffer[Artifact]
		val resolved = new mutable.ListBuffer[(Artifact,File)]
		for(r <- artReport) {
			val file = r.getLocalFile
			val art = toArtifact(r.getArtifact)
			if(file eq null)
				missing += art
			else
				resolved += ((art,file))
		}
		new ModuleReport(mid, resolved.toSeq, missing.toSeq)
	}

	def evicted(confReport: ConfigurationResolveReport): Seq[ModuleID] =
		confReport.getEvictedNodes.map(node => toModuleID(node.getId))
 
	def toModuleID(revID: ModuleRevisionId): ModuleID =
		ModuleID(revID.getOrganisation, revID.getName, revID.getRevision)
		
	def toArtifact(art: IvyArtifact): Artifact =
	{
		import art._
		Artifact(getName, getType, getExt, Option(getExtraAttribute("classifier")), getConfigurations map Configurations.config, Option(getUrl))
	}

	def updateReport(report: ResolveReport, cachedDescriptor: File): UpdateReport =
		new UpdateReport(cachedDescriptor, reports(report) map configurationReport, updateStats(report))
	def updateStats(report: ResolveReport): UpdateStats =
		new UpdateStats(report.getResolveTime, report.getDownloadTime, report.getDownloadSize)
	def configurationReport(confReport: ConfigurationResolveReport): ConfigurationReport =
		new ConfigurationReport(confReport.getConfiguration, moduleReports(confReport), evicted(confReport))
}
