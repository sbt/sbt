/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010 Mark Harrah
 */
package sbt

import java.io.File
import scala.xml.{Node => XNode,NodeSeq}

import org.apache.ivy.{core, plugins, Ivy}
import core.cache.DefaultRepositoryCacheManager
import core.{IvyPatternHelper,LogOptions}
import core.deliver.DeliverOptions
import core.install.InstallOptions
import core.module.descriptor.{Artifact => IArtifact, DefaultArtifact, DefaultDependencyArtifactDescriptor, MDArtifact}
import core.module.descriptor.{DefaultDependencyDescriptor, DefaultModuleDescriptor, DependencyDescriptor, ModuleDescriptor}
import core.module.id.{ArtifactId,ModuleId, ModuleRevisionId}
import core.publish.PublishOptions
import core.report.{ArtifactDownloadReport,ResolveReport}
import core.resolve.ResolveOptions
import core.retrieve.RetrieveOptions
import plugins.parser.m2.{PomModuleDescriptorParser,PomModuleDescriptorWriter}
import plugins.resolver.DependencyResolver

final class DeliverConfiguration(val deliverIvyPattern: String, val status: String, val configurations: Option[Seq[Configuration]], val logging: UpdateLogging.Value)
final class PublishConfiguration(val ivyFile: Option[File], val resolverName: String, val artifacts: Map[Artifact, File], val logging: UpdateLogging.Value)

final class UpdateConfiguration(val retrieve: Option[RetrieveConfiguration], val missingOk: Boolean, val logging: UpdateLogging.Value)
final class RetrieveConfiguration(val retrieveDirectory: File, val outputPattern: String)
final case class MakePomConfiguration(file: File, configurations: Option[Iterable[Configuration]] = None, extra: NodeSeq = NodeSeq.Empty, process: XNode => XNode = n => n, filterRepositories: MavenRepository => Boolean = _ => true)

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
	def install(module: IvySbt#Module, from: String, to: String, log: Logger)
	{
		module.withModule(log) { (ivy, md, default) =>
			for(dependency <- md.getDependencies)
			{
				log.info("Installing " + dependency)
				val options = new InstallOptions
				options.setValidate(module.moduleSettings.validate)
				options.setTransitive(dependency.isTransitive)
				ivy.install(dependency.getDependencyRevisionId, from, to, options)
			}
		}
	}

	/** Clears the Ivy cache, as configured by 'config'. */
	def cleanCache(ivy: IvySbt, log: Logger) = ivy.withIvy(log) { iv =>
		iv.getSettings.getResolutionCacheManager.clean()
		iv.getSettings.getRepositoryCacheManagers.foreach(_.clean())
	}

	/** Creates a Maven pom from the given Ivy configuration*/
	def makePom(module: IvySbt#Module, configuration: MakePomConfiguration, log: Logger)
	{
		import configuration.{configurations, extra, file, filterRepositories, process}
		module.withModule(log) { (ivy, md, default) =>
			(new MakePom).write(ivy, md, configurations, extra, process, filterRepositories, file)
			log.info("Wrote " + file.getAbsolutePath)
		}
	}

	def deliver(module: IvySbt#Module, configuration: DeliverConfiguration, log: Logger): File =
	{
		import configuration._
		module.withModule(log) { case (ivy, md, default) =>
			val revID = md.getModuleRevisionId
			val options = DeliverOptions.newInstance(ivy.getSettings).setStatus(status)
			options.setConfs(IvySbt.getConfigurations(md, configurations))
			ivy.deliver(revID, revID.getRevision, deliverIvyPattern, options)
			deliveredFile(ivy, deliverIvyPattern, md)
		}
	}
	def deliveredFile(ivy: Ivy, pattern: String, md: ModuleDescriptor): File =
		ivy.getSettings.resolveFile(IvyPatternHelper.substitute(pattern, md.getResolvedModuleRevisionId))

	def publish(module: IvySbt#Module, configuration: PublishConfiguration, log: Logger)
	{
		import configuration._
		module.withModule(log) { case (ivy, md, default) =>
			val resolver = ivy.getSettings.getResolver(resolverName)
			val ivyArtifact = ivyFile map { file => (MDArtifact.newIvyArtifact(md), file) }
			val is = crossIvyScala(module.moduleSettings)
			val as = mapArtifacts(md, is, artifacts) ++ ivyArtifact.toList
			publish(md, as, resolver, overwrite = true)
		}
	}
	private def crossIvyScala(moduleSettings: ModuleSettings): Option[IvyScala] =
		moduleSettings match {
			case i: InlineConfiguration if i.module.crossVersion => i.ivyScala
			case e: EmptyConfiguration if e.module.crossVersion => e.ivyScala
			case _ => None
		}
	def substituteCross(ivyScala: Option[IvyScala], artifacts: Seq[Artifact]): Seq[Artifact] =
		ivyScala match { case None => artifacts; case Some(is) => IvySbt.substituteCrossA(artifacts, is.scalaVersion) }
	def mapArtifacts(module: ModuleDescriptor, ivyScala: Option[IvyScala], artifacts: Map[Artifact, File]): Seq[(IArtifact, File)] =
	{
		val rawa = artifacts.keys.toSeq
		val seqa = substituteCross(ivyScala, rawa)
		val zipped = rawa zip IvySbt.mapArtifacts(module, seqa)
		zipped map { case (a, ivyA) => (ivyA, artifacts(a)) }
	}
	/** Resolves and retrieves dependencies.  'ivyConfig' is used to produce an Ivy file and configuration.
	* 'updateConfig' configures the actual resolution and retrieval process. */
	def update(module: IvySbt#Module, configuration: UpdateConfiguration, log: Logger): UpdateReport =
		module.withModule(log) { case (ivy, md, default) =>
			val (report, err) = resolve(configuration.logging)(ivy, md, default)
			err match
			{
				case Some(x) if !configuration.missingOk => throw x
				case _ =>
					val cachedDescriptor = ivy.getSettings.getResolutionCacheManager.getResolvedIvyFileInCache(md.getModuleRevisionId)
					val uReport = IvyRetrieve.updateReport(report, cachedDescriptor)
					configuration.retrieve match
					{
						case Some(rConf) => retrieve(ivy, uReport, rConf)
						case None => uReport
					}
			}
		}

	def transitiveScratch(ivySbt: IvySbt, id: ModuleID, label: String, deps: Seq[ModuleID], classifiers: Seq[String], c: UpdateConfiguration, ivyScala: Option[IvyScala], log: Logger): UpdateReport =
	{
		val base = id.copy(name = id.name + "$" + label)
		val module = new ivySbt.Module(InlineConfiguration(base, deps).copy(ivyScala = ivyScala))
		val report = update(module, c, log)
		transitive(ivySbt, id, report, classifiers, c, ivyScala, log)
	}
	def transitive(ivySbt: IvySbt, module: ModuleID, report: UpdateReport, classifiers: Seq[String], c: UpdateConfiguration, ivyScala: Option[IvyScala], log: Logger): UpdateReport =
		updateClassifiers(ivySbt, module, report.allModules, classifiers, new UpdateConfiguration(c.retrieve, true, c.logging), ivyScala, log)
	def updateClassifiers(ivySbt: IvySbt, id: ModuleID, modules: Seq[ModuleID], classifiers: Seq[String], configuration: UpdateConfiguration, ivyScala: Option[IvyScala], log: Logger): UpdateReport =
	{
		assert(!classifiers.isEmpty, "classifiers cannot be empty")
		val baseModules = modules map { m => ModuleID(m.organization, m.name, m.revision, crossVersion = m.crossVersion) }
		val deps = baseModules.distinct map { m => m.copy(explicitArtifacts = classifiers map { c => Artifact.classified(m.name, c) }) }
		val base = id.copy(name = id.name + classifiers.mkString("$","_",""))
		val module = new ivySbt.Module(InlineConfiguration(base, deps).copy(ivyScala = ivyScala))
		update(module, configuration, log)
	}
	private def resolve(logging: UpdateLogging.Value)(ivy: Ivy, module: DefaultModuleDescriptor, defaultConf: String): (ResolveReport, Option[ResolveException]) =
	{
		val resolveOptions = new ResolveOptions
		resolveOptions.setLog(ivyLogLevel(logging))
		val resolveReport = ivy.resolve(module, resolveOptions)
		val err =
			if(resolveReport.hasError)
				Some(new ResolveException(resolveReport.getAllProblemMessages.toArray.map(_.toString).distinct))
			else None
		(resolveReport, err)
	}
	private def retrieve(ivy: Ivy, report: UpdateReport, config: RetrieveConfiguration): UpdateReport =
		retrieve(ivy, report, config.retrieveDirectory, config.outputPattern)
	
	private def retrieve(ivy: Ivy, report: UpdateReport, base: File, pattern: String): UpdateReport =
	{
		val toCopy = new collection.mutable.HashSet[(File,File)]
		val retReport = report retrieve { (conf, mid, art, cached) =>
			val to = retrieveTarget(conf, mid, art, base, pattern)
			toCopy += ((cached, to))
			to
		}
		IO.copy( toCopy )
		retReport
	}
	private def retrieveTarget(conf: String, mid: ModuleID, art: Artifact, base: File, pattern: String): File =
		new File(base, substitute(conf, mid, art, pattern))

	private def substitute(conf: String, mid: ModuleID, art: Artifact, pattern: String): String =
	{
		val mextra = IvySbt.javaMap(mid.extraAttributes, true)
		val aextra = IvySbt.extra(art, true)
		IvyPatternHelper.substitute(pattern, mid.organization, mid.name, mid.revision, art.name, art.`type`, art.extension, conf, mextra, aextra)
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

	def publish(module: ModuleDescriptor, artifacts: Iterable[(IArtifact, File)], resolver: DependencyResolver, overwrite: Boolean): Unit =
		try {
			resolver.beginPublishTransaction(module.getModuleRevisionId(), overwrite);
			for( (artifact, file) <- artifacts) if(file.exists)
				resolver.publish(artifact, file, overwrite)
			resolver.commitPublishTransaction()
		} catch {
			case e =>
				try { resolver.abortPublishTransaction() }
				finally { throw e }
		}

}
final class ResolveException(messages: Seq[String]) extends RuntimeException(messages.mkString("\n"))