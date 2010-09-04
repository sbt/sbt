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
		reports map( _.getLocalFile )

	def cachePaths(report: ResolveReport): Map[String, Seq[File]] =
		reports(report).mapValues(confReport => cachePath(artifactReports(confReport)))

	def copy(files: Set[File], cacheBase: File, to: File): Map[File, File] =
	{
		import Path._
		val copyDef = files x rebase(cacheBase, to)
		IO.copy( copyDef, overwrite = true, preserveLastModified = true )
		copyDef.toMap
	}

	// TODO: not a sufficient way to do it: cacheBase is not necessarily common to all files
	def retrieve(result: Map[String, Seq[File]], cacheBase: File, to: File): Map[String, Seq[File]] =
	{
		val all = result.values.flatten.toSet
		val copyMap = copy(all, cacheBase, to)
		result mapValues (_ map copyMap)
	}
}