/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010 Mark Harrah
 */
package sbt

import java.io.File
import scala.xml.{Node,NodeSeq}

import org.apache.ivy.{core, plugins, Ivy}
import core.cache.DefaultRepositoryCacheManager
import core.LogOptions
import core.deliver.DeliverOptions
import core.install.InstallOptions
import core.module.descriptor.{DefaultArtifact, DefaultDependencyArtifactDescriptor, MDArtifact}
import core.module.descriptor.{DefaultDependencyDescriptor, DefaultModuleDescriptor, DependencyDescriptor, ModuleDescriptor}
import core.module.id.{ArtifactId,ModuleId, ModuleRevisionId}
import core.publish.PublishOptions
import core.report.{ArtifactDownloadReport,ResolveReport}
import core.resolve.ResolveOptions
import core.retrieve.RetrieveOptions
import plugins.parser.m2.{PomModuleDescriptorParser,PomModuleDescriptorWriter}

final class UpdateConfiguration(val retrieve: Option[RetrieveConfiguration], val logging: UpdateLogging.Value)
final class RetrieveConfiguration(val retrieveDirectory: File, val outputPattern: String, val synchronize: Boolean)
final class MakePomConfiguration(val extraDependencies: Iterable[ModuleID], val configurations: Option[Iterable[Configuration]],
	val extra: NodeSeq, val process: Node => Node, val filterRepositories: MavenRepository => Boolean)
object MakePomConfiguration
{
	def apply(extraDependencies: Iterable[ModuleID], configurations: Option[Iterable[Configuration]], extra: NodeSeq) =
		new MakePomConfiguration(extraDependencies, configurations, extra, identity, _ => true)
}
/** Configures logging during an 'update'.  `level` determines the amount of other information logged.
* `Full` is the default and logs the most.
* `DownloadOnly` only logs what is downloaded.
* `Quiet` only displays errors.*/
object UpdateLogging extends Enumeration
{
	val Full, DownloadOnly, Quiet = Value
}

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
	def makePom(module: IvySbt#Module, configuration: MakePomConfiguration,  output: File)
	{
		import configuration.{configurations, extra, extraDependencies, filterRepositories, process}
		module.withModule { (ivy, md, default) =>
			addLateDependencies(ivy, md, default, extraDependencies)
			(new MakePom).write(ivy, md, configurations, extra, process, filterRepositories, output)
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

	def deliver(module: IvySbt#Module, status: String, deliverIvyPattern: String, extraDependencies: Iterable[ModuleID], configurations: Option[Iterable[Configuration]], logging: UpdateLogging.Value)
	{
		module.withModule { case (ivy, md, default) =>
			addLateDependencies(ivy, md, default, extraDependencies)
			resolve(logging)(ivy, md, default) // todo: set download = false for resolve
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
	def update(module: IvySbt#Module, configuration: UpdateConfiguration) =
	{
		module.withModule { case (ivy, md, default) =>
			import configuration.{retrieve => rConf, logging}
			val report = resolve(logging)(ivy, md, default)
			rConf match
			{
				case None => IvyRetrieve.cachePaths(report)
				case Some(conf) => retrieve(ivy, md, conf, logging)
			}
		}
	}
	// doesn't work.  perhaps replace retrieve/determineArtifactsToCopy with custom code
	private def retrieve(ivy: Ivy, md: ModuleDescriptor, conf: RetrieveConfiguration, logging: UpdateLogging.Value) =
	{
			import conf._
			val retrieveOptions = new RetrieveOptions
			retrieveOptions.setSync(synchronize)
			val patternBase = retrieveDirectory.getAbsolutePath
			val pattern =
				if(patternBase.endsWith(File.separator))
					patternBase + outputPattern
				else
					patternBase + File.separatorChar + outputPattern

			val engine = ivy.getRetrieveEngine
			engine.retrieve(md.getModuleRevisionId, pattern, retrieveOptions)

			//TODO: eliminate the duplication for better efficiency (retrieve already calls determineArtifactsToCopy once)
			val rawMap = engine.determineArtifactsToCopy(md.getModuleRevisionId, pattern, retrieveOptions)
			val map = rawMap.asInstanceOf[java.util.Map[ArtifactDownloadReport,java.util.Set[String]]]
			val confMap = new collection.mutable.HashMap[String, Seq[File]]

				import collection.JavaConversions.{asScalaMap,asScalaSet}
			for( (report, all) <- map; retrieved <- all; val file = new File(retrieved); conf <- report.getArtifact.getConfigurations)
				confMap.put(conf, file +: confMap.getOrElse(conf, Nil))
			confMap.toMap
	}
	private def resolve(logging: UpdateLogging.Value)(ivy: Ivy, module: DefaultModuleDescriptor, defaultConf: String): ResolveReport =
	{
		val resolveOptions = new ResolveOptions
		resolveOptions.setLog(ivyLogLevel(logging))
		val resolveReport = ivy.resolve(module, resolveOptions)
		if(resolveReport.hasError)
			throw new ResolveException(resolveReport.getAllProblemMessages.toArray.map(_.toString).distinct)
		resolveReport
	}

	import UpdateLogging.{Quiet, Full, DownloadOnly}
	import LogOptions.{LOG_QUIET, LOG_DEFAULT, LOG_DOWNLOAD_ONLY}
	private def ivyLogLevel(level: UpdateLogging.Value) =
		level match
		{
			case Quiet => LOG_QUIET
			case DownloadOnly => LOG_DOWNLOAD_ONLY
			case Full => LOG_DEFAULT
		}
}
final class ResolveException(messages: Seq[String]) extends RuntimeException(messages.mkString("\n"))