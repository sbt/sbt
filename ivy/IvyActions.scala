package xsbt

import java.io.File

import org.apache.ivy.{core, plugins, util, Ivy}
import core.cache.DefaultRepositoryCacheManager
import core.LogOptions
import core.deliver.DeliverOptions
import core.module.descriptor.{DefaultArtifact, DefaultDependencyArtifactDescriptor, MDArtifact}
import core.module.descriptor.{DefaultDependencyDescriptor, DefaultModuleDescriptor, DependencyDescriptor, ModuleDescriptor}
import core.module.id.{ArtifactId,ModuleId, ModuleRevisionId}
import core.publish.PublishOptions
import core.resolve.ResolveOptions
import core.retrieve.RetrieveOptions
import plugins.parser.m2.{PomModuleDescriptorParser,PomModuleDescriptorWriter}

final class UpdateConfiguration(val retrieveDirectory: File, val outputPattern: String, val synchronize: Boolean, val quiet: Boolean) extends NotNull

object IvyActions
{
	def basicPublishLocal(moduleID: ModuleID, dependencies: Iterable[ModuleID], artifactFiles: Iterable[File], log: IvyLogger)
	{
		val artifacts = artifactFiles.map(Artifact.defaultArtifact)
		val (ivy, local) = basicLocalIvy(log)
		val module = new ivy.Module(ModuleConfiguration(moduleID, dependencies, artifacts))
		val srcArtifactPatterns = artifactFiles.map(_.getAbsolutePath)
		publish(module, local.name, srcArtifactPatterns, None, None)
	}
	def basicRetrieveLocal(moduleID: ModuleID, dependencies: Iterable[ModuleID], to: File, log: IvyLogger)
	{
		val (ivy, local) = basicLocalIvy(log)
		val module = new ivy.Module(ModuleConfiguration(moduleID, dependencies, Nil))
		val up = new UpdateConfiguration(to, defaultOutputPattern, false, true)
		update(module, up)
	}
	def defaultOutputPattern = "[artifact]-[revision](-[classifier]).[ext]"
	private def basicLocalIvy(log: IvyLogger) =
	{
		val local = Resolver.defaultLocal
		val paths = new IvyPaths(new File("."), None)
		val conf = new IvyConfiguration(paths, Seq(local), log)
		(new IvySbt(conf), local)
	}

	/** Clears the Ivy cache, as configured by 'config'. */
	def cleanCache(ivy: IvySbt) = ivy.withIvy { _.getSettings.getRepositoryCacheManagers.foreach(_.clean()) }
	
	/** Creates a Maven pom from the given Ivy configuration*/
	def makePom(module: IvySbt#Module, extraDependencies: Iterable[ModuleID], configurations: Option[Iterable[Configuration]], output: File)
	{
		module.withModule { (ivy, md, default) =>
			addLateDependencies(ivy, md, default, extraDependencies)
			val pomModule = keepConfigurations(md, configurations)
			PomModuleDescriptorWriter.write(pomModule, DefaultConfigurationMapping, output)
			module.logger.info("Wrote " + output.getAbsolutePath)
		}
	}
	// todo: correct default configuration for extra dependencies
	private def addLateDependencies(ivy: Ivy, module: DefaultModuleDescriptor, defaultConfiguration: String, extraDependencies: Iterable[ModuleID])
	{
		val parser = new CustomXmlParser.CustomParser(ivy.getSettings)
		parser.setMd(module)
		val defaultConf = if(defaultConfiguration.contains("->")) defaultConfiguration else (defaultConfiguration + "->default(compile)")
		parser.setDefaultConf(defaultConf)
		IvySbt.addDependencies(module, extraDependencies, parser)
	}
	private def getConfigurations(module: ModuleDescriptor, configurations: Option[Iterable[Configuration]]) =
		configurations match
		{
			case Some(confs) => confs.map(_.name).toList.toArray
			case None => module.getPublicConfigurationsNames
		}
	/** Retain dependencies only with the configurations given, or all public configurations of `module` if `configurations` is None.
	* This currently only preserves the information required by makePom*/
	private def keepConfigurations(module: ModuleDescriptor, configurations: Option[Iterable[Configuration]]): ModuleDescriptor =
	{
		val keepConfigurations = getConfigurations(module, configurations)
		val keepSet = Set(keepConfigurations.toSeq : _*)
		def translate(dependency: DependencyDescriptor) =
		{
			val keep = dependency.getModuleConfigurations.filter(keepSet.contains)
			if(keep.isEmpty)
				None
			else // TODO: translate the dependency to contain only configurations to keep
				Some(dependency)
		}
		val newModule = new DefaultModuleDescriptor(module.getModuleRevisionId, "", null)
		newModule.setHomePage(module.getHomePage)
		for(dependency <- module.getDependencies; translated <- translate(dependency))
			newModule.addDependency(translated)
		newModule
	}
	
	def deliver(module: IvySbt#Module, status: String, deliverIvyPattern: String, extraDependencies: Iterable[ModuleID], configurations: Option[Iterable[Configuration]], quiet: Boolean)
	{
		module.withModule { case (ivy, md, default) =>
			addLateDependencies(ivy, md, default, extraDependencies)
			resolve(quiet)(ivy, md, default) // todo: set download = false for resolve
			val revID = md.getModuleRevisionId
			val options = DeliverOptions.newInstance(ivy.getSettings).setStatus(status)
			options.setConfs(getConfigurations(md, configurations))
			ivy.deliver(revID, revID.getRevision, deliverIvyPattern, options)
		}
	}
	// todo: map configurations, extra dependencies
	def publish(module: IvySbt#Module, resolverName: String, srcArtifactPatterns: Iterable[String], deliveredIvyPattern: Option[String], configurations: Option[Iterable[Configuration]])
	{
		module.withModule { case (ivy, md, default) =>
			val revID = md.getModuleRevisionId
			val patterns = new java.util.ArrayList[String]
			srcArtifactPatterns.foreach(pattern => patterns.add(pattern))
			val options = (new PublishOptions).setOverwrite(true)
			deliveredIvyPattern.foreach(options.setSrcIvyPattern)
			options.setConfs(getConfigurations(md, configurations))
			ivy.publish(revID, patterns, resolverName, options)
		}
	}
	/** Resolves and retrieves dependencies.  'ivyConfig' is used to produce an Ivy file and configuration.
	* 'updateConfig' configures the actual resolution and retrieval process. */
	def update(module: IvySbt#Module, configuration: UpdateConfiguration)
	{
		module.withModule { case (ivy, md, default) =>
			import configuration._
			resolve(quiet)(ivy, md, default)
			val retrieveOptions = new RetrieveOptions
			retrieveOptions.setSync(synchronize)
			val patternBase = retrieveDirectory.getAbsolutePath
			val pattern =
				if(patternBase.endsWith(File.separator))
					patternBase + outputPattern
				else
					patternBase + File.separatorChar + outputPattern
			ivy.retrieve(md.getModuleRevisionId, pattern, retrieveOptions)
		}
	}
	private def resolve(quiet: Boolean)(ivy: Ivy, module: DefaultModuleDescriptor, defaultConf: String) =
	{
		val resolveOptions = new ResolveOptions
		if(quiet)
			resolveOptions.setLog(LogOptions.LOG_DOWNLOAD_ONLY)
		val resolveReport = ivy.resolve(module, resolveOptions)
		if(resolveReport.hasError)
			error(Set(resolveReport.getAllProblemMessages.toArray: _*).mkString("\n"))
	}
}

private object DefaultConfigurationMapping extends PomModuleDescriptorWriter.ConfigurationScopeMapping(new java.util.HashMap)
{
	override def getScope(confs: Array[String]) =
	{
		Configurations.defaultMavenConfigurations.find(conf => confs.contains(conf.name)) match
		{
			case Some(conf) => conf.name
			case None =>
				if(confs.isEmpty || confs(0) == Configurations.Default.name)
					null
				else
					confs(0)
		}
	}
	override def isOptional(confs: Array[String]) = confs.isEmpty || (confs.length == 1 && confs(0) == Configurations.Optional.name)
}