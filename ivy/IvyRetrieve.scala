/* sbt -- Simple Build Tool
 * Copyright 2010  Mark Harrah
 */
package sbt

import java.io.File

import org.apache.ivy.core.{module, report}
import module.id.ModuleRevisionId
import report.{ArtifactDownloadReport, ConfigurationResolveReport, ResolveReport}

object IvyRetrieve
{
	def reports(report: ResolveReport): Map[String, ConfigurationResolveReport] =
		( for( conf <- report.getConfigurations) yield (conf, report.getConfigurationReport(conf)) ).toMap

	def artifactReports(confReport: ConfigurationResolveReport): Seq[ArtifactDownloadReport] =
	{
		val all = new scala.collection.mutable.HashSet[ArtifactDownloadReport]
		for( revId <- confReport.getModuleRevisionIds.toArray collect { case revId: ModuleRevisionId => revId })
			all ++= confReport.getDownloadReports(revId)
		all.toSeq
	}

	def cachePath(reports: Seq[ArtifactDownloadReport]): Seq[File] =
		for(r <- reports) yield
		{
			val file = r.getLocalFile
			if(file eq null) error("No file for " + r.getArtifact) else file
		}

	def cachePaths(report: ResolveReport): Map[String, Seq[File]] =
		reports(report).mapValues(confReport => cachePath(artifactReports(confReport)))
}