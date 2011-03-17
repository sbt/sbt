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
 
	def toModuleID(revID: ModuleRevisionId): ModuleID =
		ModuleID(revID.getOrganisation, revID.getName, revID.getRevision)
		
	def toArtifact(art: IvyArtifact): Artifact =
	{
		import art._
		Artifact(getName, getType, getExt, Option(getExtraAttribute("classifier")), getConfigurations map Configurations.config, Option(getUrl))
	}

	def updateReport(report: ResolveReport): UpdateReport =
		new UpdateReport(reports(report) map configurationReport)

	def configurationReport(confReport: ConfigurationResolveReport): ConfigurationReport =
		new ConfigurationReport(confReport.getConfiguration, moduleReports(confReport))
}

final class UpdateReport(val configurations: Seq[ConfigurationReport])
{
	override def toString = "Update report:\n" + configurations.mkString
	def allModules: Seq[ModuleID] = configurations.flatMap(_.allModules).distinct
	def retrieve(f: (String, ModuleID, Artifact, File) => File): UpdateReport =
		new UpdateReport(configurations map { _ retrieve f} )
	def configuration(s: String) = configurations.find(_.configuration == s)
}
final class ConfigurationReport(val configuration: String, val modules: Seq[ModuleReport])
{
	override def toString = "\t" + configuration + ":\n" + modules.mkString
	def allModules: Seq[ModuleID] = modules.map(_.module)
	def retrieve(f: (String, ModuleID, Artifact, File) => File): ConfigurationReport =
		new ConfigurationReport(configuration, modules map { _.retrieve( (mid,art,file) => f(configuration, mid, art, file)) })
}
final class ModuleReport(val module: ModuleID, val artifacts: Seq[(Artifact, File)], val missingArtifacts: Seq[Artifact])
{
	override def toString =
	{
		val arts = artifacts.map(_.toString) ++ missingArtifacts.map(art => "(MISSING) " + art)
		"\t\t" + module + ": " +
			(if(arts.size <= 1) "" else "\n\t\t\t") + arts.mkString("\n\t\t\t") + "\n"
	}
	def retrieve(f: (ModuleID, Artifact, File) => File): ModuleReport =
		new ModuleReport(module, artifacts.map { case (art,file) => (art, f(module, art, file)) }, missingArtifacts)
}