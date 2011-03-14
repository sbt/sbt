/* sbt -- Simple Build Tool
 * Copyright 2010  Mark Harrah
 */
package sbt

import java.io.File

import org.apache.ivy.core.{module, report}
import module.descriptor.{Artifact => IvyArtifact}
import module.id.ModuleRevisionId
import report.{ArtifactDownloadReport, ConfigurationResolveReport, ResolveReport}

object IvyRetrieve
{
	def reports(report: ResolveReport): Map[String, ConfigurationResolveReport] =
		( for( conf <- report.getConfigurations) yield (conf, report.getConfigurationReport(conf)) ).toMap

	def moduleReports(confReport: ConfigurationResolveReport): Map[ModuleID, ModuleReport] =
		moduleReportMap(confReport) map { case (mid, arts) => (mid, new ModuleReport(mid, artifactReports(arts)) ) }

	def moduleReportMap(confReport: ConfigurationResolveReport): Map[ModuleID, Seq[ArtifactDownloadReport]] =
	{
		val modules =
			for( revId <- confReport.getModuleRevisionIds.toArray collect { case revId: ModuleRevisionId => revId }) yield
				(toModuleID(revId), (confReport getDownloadReports revId).toSeq)
		modules.toMap
	}
	def artifactReports(artReport: Seq[ArtifactDownloadReport]): Map[Artifact, File] =
		artReport map { r =>
			val art = r.getArtifact
			val file0 = r.getLocalFile
			val file = if(file0 eq null) error("No file for " + art) else file0
			(toArtifact(art), file)
		} toMap;
 
	def toModuleID(revID: ModuleRevisionId): ModuleID =
		ModuleID(revID.getOrganisation, revID.getName, revID.getRevision)
		
	def toArtifact(art: IvyArtifact): Artifact =
	{
		import art._
		Artifact(getName, getType, getExt, Option(getExtraAttribute("classifier")), getConfigurations map Configurations.config, Option(getUrl))
	}

	def updateReport(report: ResolveReport): UpdateReport =
		new UpdateReport(reports(report) mapValues configurationReport)

	def configurationReport(confReport: ConfigurationResolveReport): ConfigurationReport =
		new ConfigurationReport(confReport.getConfiguration, moduleReports(confReport))
}

final class UpdateReport(val configurations: Map[String, ConfigurationReport])
{
	override def toString = "Update report:\n" + configurations.values.mkString
}
final class ConfigurationReport(val configuration: String, val modules: Map[ModuleID, ModuleReport])
{
	override def toString = "\t" + configuration + ":\n" + modules.values.mkString
}
final class ModuleReport(val module: ModuleID, val artifacts: Map[Artifact, File])
{
	override def toString = "\t\t" + module + ": " + (if(artifacts.size <= 1) "" else "\n") + artifacts.mkString("\n\t\t\t") + "\n"
}