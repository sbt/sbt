/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010 Mark Harrah
 */
package sbt

import java.io.File
import scala.xml.{Node,NodeSeq}

import org.apache.ivy.{core, plugins, Ivy}
import core.cache.DefaultRepositoryCacheManager
import core.{IvyPatternHelper,LogOptions}
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

final class PublishPatterns(val deliverIvyPattern: Option[String], val srcArtifactPatterns: Seq[String])
final class PublishConfiguration(val patterns: PublishPatterns, val status: String, val resolverName: String, val configurations: Option[Seq[Configuration]], val logging: UpdateLogging.Value)

final class UpdateConfiguration(val retrieve: Option[RetrieveConfiguration], val logging: UpdateLogging.Value)
final class RetrieveConfiguration(val retrieveDirectory: File, val outputPattern: String, val synchronize: Boolean)
final class MakePomConfiguration(val file: File, val configurations: Option[Iterable[Configuration]] = None, val extra: NodeSeq = NodeSeq.Empty, val process: Node => Node = n => n, val filterRepositories: MavenRepository => Boolean = _ => true)

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
	def makePom(module: IvySbt#Module, configuration: MakePomConfiguration)
	{
		import configuration.{configurations, extra, file, filterRepositories, process}
		module.withModule { (ivy, md, default) =>
			(new MakePom).write(ivy, md, configurations, extra, process, filterRepositories, file)
			module.logger.info("Wrote " + file.getAbsolutePath)
		}
	}

	def deliver(module: IvySbt#Module, configuration: PublishConfiguration)
	{
		import configuration._
		import patterns._
		module.withModule { case (ivy, md, default) =>
			resolve(logging)(ivy, md, default) // todo: set download = false for resolve
			val revID = md.getModuleRevisionId
			val options = DeliverOptions.newInstance(ivy.getSettings).setStatus(status)
			options.setConfs(IvySbt.getConfigurations(md, configurations))
			ivy.deliver(revID, revID.getRevision, getDeliverIvyPattern(patterns), options)
		}
	}
	// because Ivy.deliver does not provide the delivered File location, we duplicate the logic here
	def deliverFile(module: IvySbt#Module, configuration: PublishConfiguration): File =
		module.withModule { (ivy,md,_) =>
			ivy.getSettings.resolveFile(IvyPatternHelper.substitute(getDeliverIvyPattern(configuration.patterns), md.getResolvedModuleRevisionId))
		}
	def getDeliverIvyPattern(patterns: PublishPatterns) = patterns.deliverIvyPattern.getOrElse(error("No Ivy pattern specified"))

	// todo: map configurations, extra dependencies
	def publish(module: IvySbt#Module, configuration: PublishConfiguration)
	{
		import configuration._
		import patterns._
		module.withModule { case (ivy, md, default) =>
			val revID = md.getModuleRevisionId
			val patterns = new java.util.ArrayList[String]
			srcArtifactPatterns.foreach(pattern => patterns.add(pattern))
			val options = (new PublishOptions).setOverwrite(true)
			deliverIvyPattern.foreach(options.setSrcIvyPattern)
			options.setConfs(IvySbt.getConfigurations(md, configurations))
			ivy.publish(revID, patterns, resolverName, options)
		}
	}
	/** Resolves and retrieves dependencies.  'ivyConfig' is used to produce an Ivy file and configuration.
	* 'updateConfig' configures the actual resolution and retrieval process. */
	def update(module: IvySbt#Module, configuration: UpdateConfiguration): UpdateReport =
	{
		module.withModule { case (ivy, md, default) =>
			import configuration.{retrieve => rConf, logging}
			val report = resolve(logging)(ivy, md, default)
			IvyRetrieve.updateReport(report)
		}
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