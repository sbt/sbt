/* sbt -- Simple Build Tool
 * Copyright 2011  Mark Harrah
 */
package sbt

	import java.io.File

/** Provides information about dependency resolution.
* It does not include information about evicted modules, only about the modules ultimately selected by the conflict manager.
* This means that for a given configuration, there should only be one revision for a given organization and module name.
* @param cachedDescriptor the location of the resolved module descriptor in the cache
* @param configurations a sequence containing one report for each configuration resolved.
* @see sbt.RichUpdateReport
 */
final class UpdateReport(val cachedDescriptor: File, val configurations: Seq[ConfigurationReport])
{
	override def toString = "Update report:\n" + configurations.mkString

	/** All resolved modules in all configurations. */
	def allModules: Seq[ModuleID] = configurations.flatMap(_.allModules).distinct

	def retrieve(f: (String, ModuleID, Artifact, File) => File): UpdateReport =
		new UpdateReport(cachedDescriptor, configurations map { _ retrieve f} )

	/** Gets the report for the given configuration, or `None` if the configuration was not resolved.*/
	def configuration(s: String) = configurations.find(_.configuration == s)

	/** Gets the names of all resolved configurations.  This `UpdateReport` contains one `ConfigurationReport` for each configuration in this list. */
	def allConfigurations: Seq[String] = configurations.map(_.configuration)
}

/** Provides information about resolution of a single configuration.
* @param configuration the configuration this report is for.
* @param modules a seqeuence containing one report for each module resolved for this configuration.
*/
final class ConfigurationReport(val configuration: String, val modules: Seq[ModuleReport])
{
	override def toString = "\t" + configuration + ":\n" + modules.mkString

	/** All resolved modules for this configuration.
	* For a given organization and module name, there is only one revision/`ModuleID` in this sequence.
	*/
	def allModules: Seq[ModuleID] = modules.map(_.module)

	def retrieve(f: (String, ModuleID, Artifact, File) => File): ConfigurationReport =
		new ConfigurationReport(configuration, modules map { _.retrieve( (mid,art,file) => f(configuration, mid, art, file)) })
}

/** Provides information about the resolution of a module.
* This information is in the context of a specific configuration.
* @param module the `ModuleID` this report is for.
* @param artifacts the resolved artifacts for this module, paired with the File the artifact was retrieved to.  This File may be in the
*/
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

	/** Provides extra methods for filtering the contents of an `UpdateReport` and for obtaining references to a selected subset of the underlying files. */
	final class RichUpdateReport(report: UpdateReport)
	{
			import DependencyFilter._
		/** Obtains all successfully retrieved files in all configurations and modules. */
		def allFiles: Seq[File] = matching(DependencyFilter.allPass)

		/** Obtains all successfully retrieved files in configurations, modules, and artifacts matching the specified filter. */
		def matching(f: DependencyFilter): Seq[File] = select0(f).distinct

		/** Obtains all successfully retrieved files matching all provided filters.  An unspecified argument matches all files. */
		def select(configuration: ConfigurationFilter = configurationFilter(), module: ModuleFilter = moduleFilter(), artifact: ArtifactFilter = artifactFilter()): Seq[File] =
			matching(DependencyFilter.make(configuration, module, artifact))

		private[this] def select0(f: DependencyFilter): Seq[File] =
			for(cReport <- report.configurations; mReport <- cReport.modules; (artifact, file) <- mReport.artifacts if f(cReport.configuration, mReport.module, artifact))  yield {
				if(file == null) error("Null file: conf=" + cReport.configuration + ", module=" + mReport.module + ", art: " + artifact)
				file
			}
			
		/** Constructs a new report that only contains files matching the specified filter.*/
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
			new UpdateReport(report.cachedDescriptor, newConfigurations)
		}
		def substitute(f: (String, ModuleID, Seq[(Artifact, File)]) => Seq[(Artifact, File)]): UpdateReport =
		{
			val newConfigurations = report.configurations.map { confReport =>
				import confReport._
				val newModules =
					modules map { modReport =>
						val newArtifacts = f(configuration, modReport.module, modReport.artifacts)
						new ModuleReport(modReport.module, newArtifacts, Nil)
					}
				new ConfigurationReport(configuration, newModules)
			}
			new UpdateReport(report.cachedDescriptor, newConfigurations)
		}

		def toSeq: Seq[(String, ModuleID, Artifact, File)] =
			for(confReport <- report.configurations; modReport <- confReport.modules; (artifact, file) <- modReport.artifacts) yield
				(confReport.configuration, modReport.module, artifact, file)
	}
}
