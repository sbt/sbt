/* sbt -- Simple Build Tool
 * Copyright 2011  Mark Harrah
 */
package sbt

	import java.io.File

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
object UpdateReport
{
	implicit def richUpdateReport(report: UpdateReport): RichUpdateReport = new RichUpdateReport(report)
	final class RichUpdateReport(report: UpdateReport)
	{
			import DependencyFilter._

		def allFiles: Seq[File] = matching(DependencyFilter.allPass)
		def matching(f: DependencyFilter): Seq[File] = select0(f).distinct
		def select(configuration: ConfigurationFilter = configurationFilter(), module: ModuleFilter = moduleFilter(), artifact: ArtifactFilter = artifactFilter()): Seq[File] =
			matching(DependencyFilter.make(configuration, module, artifact))

		private[this] def select0(f: DependencyFilter): Seq[File] =
			for(cReport <- report.configurations; mReport <- cReport.modules; (artifact, file) <- mReport.artifacts if f(cReport.configuration, mReport.module, artifact))  yield {
				if(file == null) error("Null file: conf=" + cReport.configuration + ", module=" + mReport.module + ", art: " + artifact)
				file
			}
			
		def filter(f: DependencyFilter): UpdateReport =
		{
			val newConfigurations = report.configurations.map { confReport =>
				import confReport._
				val newModules =
					modules map { modReport =>
						import modReport._
						val newArtifacts = artifacts filter { case (art, file) => f(configuration, module, art) }
						val newMissing = missingArtifacts filter { art => f(configuration, module, art) }
						new ModuleReport(module, newArtifacts, newMissing)
					}
				new ConfigurationReport(configuration, newModules)
			}
			new UpdateReport(newConfigurations)
		}
	}
}