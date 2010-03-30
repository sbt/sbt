/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010 Mark Harrah
 */
package sbt

import java.io.File
import scala.xml.NodeSeq

import org.apache.ivy.{core, plugins, util, Ivy}
import core.cache.DefaultRepositoryCacheManager
import core.LogOptions
import core.deliver.DeliverOptions
import core.install.InstallOptions
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
	/** Installs the dependencies of the given 'module' from the resolver named 'from' to the resolver named 'to'.*/
	def install(module: IvySbt#Module, from: String, to: String)
	{
		module.withModule { (ivy, md, default) =>
			for(dependency <- md.getDependencies)
			{
				module.logger.info("Installing " + dependency)
				val options = new InstallOptions
				options.setValidate(module.moduleSettings.validate)
				options.setTransitive(dependency.isTransitive)
				ivy.install(dependency.getDependencyRevisionId, from, to, options)
			}
		}
	}

	/** Clears the Ivy cache, as configured by 'config'. */
	def cleanCache(ivy: IvySbt) = ivy.withIvy { iv =>
		iv.getSettings.getResolutionCacheManager.clean()
		iv.getSettings.getRepositoryCacheManagers.foreach(_.clean())
	}

	/** Creates a Maven pom from the given Ivy configuration*/
	def makePom(module: IvySbt#Module, extraDependencies: Iterable[ModuleID], configurations: Option[Iterable[Configuration]], extra: NodeSeq, output: File)
	{
		module.withModule { (ivy, md, default) =>
			addLateDependencies(ivy, md, default, extraDependencies)
			(new MakePom).write(ivy, md, configurations, extra, output)
			module.logger.info("Wrote " + output.getAbsolutePath)
		}
	}
	// todo: correct default configuration for extra dependencies
	private def addLateDependencies(ivy: Ivy, module: DefaultModuleDescriptor, defaultConfiguration: String, extraDependencies: Iterable[ModuleID])
	{
		val parser = new CustomXmlParser.CustomParser(ivy.getSettings, Some(defaultConfiguration))
		parser.setMd(module)
		IvySbt.addDependencies(module, extraDependencies, parser)
	}

	def deliver(module: IvySbt#Module, status: String, deliverIvyPattern: String, extraDependencies: Iterable[ModuleID], configurations: Option[Iterable[Configuration]], quiet: Boolean)
	{
		module.withModule { case (ivy, md, default) =>
			addLateDependencies(ivy, md, default, extraDependencies)
			resolve(quiet)(ivy, md, default) // todo: set download = false for resolve
			val revID = md.getModuleRevisionId
			val options = DeliverOptions.newInstance(ivy.getSettings).setStatus(status)
			options.setConfs(IvySbt.getConfigurations(md, configurations))
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
			options.setConfs(IvySbt.getConfigurations(md, configurations))
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
			throw new ResolveException(resolveReport.getAllProblemMessages.toArray.map(_.toString).toList.removeDuplicates)
	}
}
final class ResolveException(messages: List[String]) extends RuntimeException(messages.mkString("\n"))