/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010 Mark Harrah
 */
package sbt

import java.io.File
import scala.xml.{Node => XNode, NodeSeq}

import org.apache.ivy.{core, plugins, Ivy}
import core.{IvyPatternHelper, LogOptions}
import core.deliver.DeliverOptions
import core.install.InstallOptions
import core.module.descriptor.{Artifact => IArtifact, MDArtifact, ModuleDescriptor, DefaultModuleDescriptor}
import core.report.ResolveReport
import core.resolve.ResolveOptions
import plugins.resolver.{BasicResolver, DependencyResolver}

final class DeliverConfiguration(val deliverIvyPattern: String, val status: String, val configurations: Option[Seq[Configuration]], val logging: UpdateLogging.Value)
final class PublishConfiguration(val ivyFile: Option[File], val resolverName: String, val artifacts: Map[Artifact, File], val checksums: Seq[String], val logging: UpdateLogging.Value)

final case class UpdateConfiguration(retrieve: Option[RetrieveConfiguration], missingOk: Boolean, logging: UpdateLogging.Value, artifactFilter: ArtifactTypeFilter)
final class RetrieveConfiguration(val retrieveDirectory: File, val outputPattern: String)
final case class MakePomConfiguration(file: File, moduleInfo: ModuleInfo, configurations: Option[Seq[Configuration]] = None, extra: NodeSeq = NodeSeq.Empty, process: XNode => XNode = n => n, filterRepositories: MavenRepository => Boolean = _ => true, allRepositories: Boolean, includeTypes: Set[String] = Set(Artifact.DefaultType, Artifact.PomType))
/** @param exclude is a map from ModuleID to classifiers that were previously tried and failed, so should now be excluded */
final case class GetClassifiersConfiguration(module: GetClassifiersModule, exclude: Map[ModuleID, Set[String]], configuration: UpdateConfiguration, ivyScala: Option[IvyScala], sourceArtifactTypes: Set[String], docArtifactTypes: Set[String])
final case class GetClassifiersModule(id: ModuleID, modules: Seq[ModuleID], configurations: Seq[Configuration], classifiers: Seq[String])

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
		import configuration.{allRepositories, moduleInfo, configurations, extra, file, filterRepositories, process, includeTypes}
		module.withModule(log) { (ivy, md, default) =>
			(new MakePom(log)).write(ivy, md, moduleInfo, configurations, includeTypes, extra, process, filterRepositories, allRepositories, file)
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
			if(resolver eq null) error("Undefined resolver '" + resolverName + "'")
			val ivyArtifact = ivyFile map { file => (MDArtifact.newIvyArtifact(md), file) }
			val cross = crossVersionMap(module.moduleSettings)
			val as = mapArtifacts(md, cross, artifacts) ++ ivyArtifact.toList
			withChecksums(resolver, checksums) { publish(md, as, resolver, overwrite = true) }
		}
	}
	private[this] def withChecksums[T](resolver: DependencyResolver, checksums: Seq[String])(act: => T): T =
		resolver match { case br: BasicResolver => withChecksums(br, checksums)(act); case _ => act }
	private[this] def withChecksums[T](resolver: BasicResolver, checksums: Seq[String])(act: => T): T =
	{
		val previous = resolver.getChecksumAlgorithms
		resolver.setChecksums(checksums mkString ",")
		try { act }
		finally { resolver.setChecksums(previous mkString ",") }
	}
	private def crossVersionMap(moduleSettings: ModuleSettings): Option[String => String] =
		moduleSettings match {
			case i: InlineConfiguration => CrossVersion(i.module, i.ivyScala)
			case e: EmptyConfiguration => CrossVersion(e.module, e.ivyScala)
			case _ => None
		}
	def mapArtifacts(module: ModuleDescriptor, cross: Option[String => String], artifacts: Map[Artifact, File]): Seq[(IArtifact, File)] =
	{
		val rawa = artifacts.keys.toSeq
		val seqa = CrossVersion.substituteCross(rawa, cross)
		val zipped = rawa zip IvySbt.mapArtifacts(module, seqa)
		zipped map { case (a, ivyA) => (ivyA, artifacts(a)) }
	}
	/** Resolves and retrieves dependencies.  'ivyConfig' is used to produce an Ivy file and configuration.
	* 'updateConfig' configures the actual resolution and retrieval process. */
	def update(module: IvySbt#Module, configuration: UpdateConfiguration, log: Logger): UpdateReport =
		module.withModule(log) { case (ivy, md, default) =>
			val (report, err) = resolve(configuration.logging)(ivy, md, default, configuration.artifactFilter)

			err match
			{
				case Some(x) if !configuration.missingOk =>
					processUnresolved(x, log)
					throw x
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

	def processUnresolved(err: ResolveException, log: Logger)
	{
		val withExtra = err.failed.filter(!_.extraDependencyAttributes.isEmpty)
		if(!withExtra.isEmpty)
		{
			log.warn("\n\tNote: Some unresolved dependencies have extra attributes.  Check that these dependencies exist with the requested attributes.")
			withExtra foreach { id => log.warn("\t\t" + id) }
			log.warn("")
		}
	}
	def groupedConflicts[T](moduleFilter: ModuleFilter, grouping: ModuleID => T)(report: UpdateReport): Map[T, Set[String]] =
		report.configurations.flatMap { confReport =>
			val evicted = confReport.evicted.filter(moduleFilter)
			val evictedSet = evicted.map( m => (m.organization, m.name) ).toSet
			val conflicted = confReport.allModules.filter( mod => evictedSet( (mod.organization, mod.name) ) )
			grouped(grouping)(conflicted ++ evicted)
		} toMap;

	def grouped[T](grouping: ModuleID => T)(mods: Seq[ModuleID]): Map[T, Set[String]] =
		mods groupBy(grouping) mapValues(_.map(_.revision).toSet)

	def transitiveScratch(ivySbt: IvySbt, label: String, config: GetClassifiersConfiguration, log: Logger): UpdateReport =
	{
		import config.{configuration => c, ivyScala, module => mod}
		import mod.{id, modules => deps}
		val base = restrictedCopy(id, true).copy(name = id.name + "$" + label)
		val module = new ivySbt.Module(InlineConfiguration(base, ModuleInfo(base.name), deps).copy(ivyScala = ivyScala))
		val report = update(module, c, log)
		val newConfig = config.copy(module = mod.copy(modules = report.allModules))
		updateClassifiers(ivySbt, newConfig, log)
	}

	/**
	 * Creates explicit artifacts for each classifier in `config.module`, and then attempts to resolve them directly. This
	 * is for Maven compatibility, where these artifacts are not "published" in the POM, so they don't end up in the Ivy
	 * that sbt generates for them either.<br>
	 * In addition, retrieves specific Ivy artifacts if they have one of the requested `config.configuration.types`.
	 * @param config important to set `config.configuration.types` to only allow artifact types that can correspond to
	 *               "classified" artifacts (sources and javadocs).
	 */
	def updateClassifiers(ivySbt: IvySbt, config: GetClassifiersConfiguration, log: Logger): UpdateReport =
	{
		import config.{configuration => c, module => mod, _}
		import mod.{configurations => confs, _}
		assert(!classifiers.isEmpty, "classifiers cannot be empty")
		assert(c.artifactFilter.types.nonEmpty, "UpdateConfiguration must filter on some types")
		val baseModules = modules map { m => restrictedCopy(m, confs = true) }
		val deps = baseModules.distinct
		val classifiedDeps = deps flatMap classifiedArtifacts(classifiers, exclude)
		val base = restrictedCopy(id, true).copy(name = id.name + classifiers.mkString("$","_",""))
		val module = new ivySbt.Module(InlineConfiguration(base, ModuleInfo(base.name), classifiedDeps, ivyScala = ivyScala, configurations = confs))
		// This includes c.types which should be passed in too
		val upConf = c.copy(missingOk = true)
		val report = update(module, upConf, log)
		// The artifacts that came from Ivy don't have their classifier set, let's set it according to their types
		// FIXME: this is only done because IDE plugins depend on `classifier` to determine type. They should now look at the type instead, in relation with (source|doc)ArtifactTypes
		val typeClassifierMap: Map[String, String] =
			((sourceArtifactTypes.toIterable map (_ -> Artifact.SourceClassifier))
			 :: (docArtifactTypes.toIterable map (_ -> Artifact.DocClassifier)) :: Nil).flatten.toMap
		report.substitute { (conf, mid, artFileSeq) =>
			artFileSeq map { case (art, f) =>
				// Deduce the classifier from the type if no classifier is present already
				art.copy(classifier = art.classifier orElse typeClassifierMap.get(art.`type`)) -> f
			}
		}
	}

	def classifiedArtifacts(classifiers: Seq[String], exclude: Map[ModuleID, Set[String]])(m: ModuleID): Option[ModuleID] =
	{
		val excluded = exclude getOrElse(restrictedCopy(m, false), Set.empty)
		val included = classifiers filterNot excluded

		if (included.isEmpty)
			Some(m.copy(isTransitive = false))
		else {
			val classifiedArts = classifiedArtifacts(m.name, included)
			/** Explicitly set an "include all" rule (the default) because otherwise, if we declare ANY explicitArtifacts,
			  * [[org.apache.ivy.core.resolve.IvyNode#getArtifacts]] (in Ivy 2.3.0-rc1) will not merge in the descriptor's
			  * artifacts and will only keep the explicitArtifacts */
			Some(m.copy(isTransitive = false, explicitArtifacts = classifiedArts, inclusions = InclExclRule.everything :: Nil))
		}
	}
  
	def addExcluded(report: UpdateReport, classifiers: Seq[String], exclude: Map[ModuleID, Set[String]]): UpdateReport =
		report.addMissing { id => classifiedArtifacts(id.name, classifiers filter getExcluded(id, exclude)) }
  
	def classifiedArtifacts(name: String, classifiers: Seq[String]): Seq[Artifact] =
		classifiers map { c => Artifact.classified(name, c) }
  
	private[this] def getExcluded(id: ModuleID, exclude: Map[ModuleID, Set[String]]): Set[String] =
		exclude.getOrElse(restrictedCopy(id, false), Set.empty[String])

	def extractExcludes(report: UpdateReport): Map[ModuleID, Set[String]] =
		report.allMissing flatMap { case (_, mod, art) => art.classifier.map { c => (restrictedCopy(mod, false), c) } } groupBy(_._1) map { case (mod, pairs) => (mod, pairs.map(_._2).toSet) }

	private[this] def restrictedCopy(m: ModuleID, confs: Boolean) =
		ModuleID(m.organization, m.name, m.revision, crossVersion = m.crossVersion, extraAttributes = m.extraAttributes, configurations = if(confs) m.configurations else None)

	private[this] def resolve(logging: UpdateLogging.Value)(
      ivy: Ivy, module: DefaultModuleDescriptor, defaultConf: String, filter: ArtifactTypeFilter): (ResolveReport, Option[ResolveException]) =
	{  
		val resolveOptions = new ResolveOptions
		val resolveId = ResolveOptions.getDefaultResolveId(module)
		resolveOptions.setResolveId(resolveId)
		resolveOptions.setArtifactFilter(filter)
		resolveOptions.setLog(ivyLogLevel(logging))
		ResolutionCache.cleanModule(module.getModuleRevisionId, resolveId, ivy.getSettings.getResolutionCacheManager)
		val resolveReport = ivy.resolve(module, resolveOptions)
		val err =
			if(resolveReport.hasError)
			{
				val messages = resolveReport.getAllProblemMessages.toArray.map(_.toString).distinct
				val failed = resolveReport.getUnresolvedDependencies.map(node => IvyRetrieve.toModuleID(node.getId))
				Some(new ResolveException(messages, failed))
			}
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

	def publish(module: ModuleDescriptor, artifacts: Seq[(IArtifact, File)], resolver: DependencyResolver, overwrite: Boolean): Unit =
	{
		checkFilesPresent(artifacts)
		try {
			resolver.beginPublishTransaction(module.getModuleRevisionId(), overwrite);
			for( (artifact, file) <- artifacts)
				resolver.publish(artifact, file, overwrite)
			resolver.commitPublishTransaction()
		} catch {
			case e: Throwable =>
				try { resolver.abortPublishTransaction() }
				finally { throw e }
		}
	}
	private[this] def checkFilesPresent(artifacts: Seq[(IArtifact, File)])
	{
		val missing = artifacts filter { case (a, file) => !file.exists }
		if(missing.nonEmpty)
			error("Missing files for publishing:\n\t" + missing.map(_._2.getAbsolutePath).mkString("\n\t"))
	}
}
final class ResolveException(val messages: Seq[String], val failed: Seq[ModuleID]) extends RuntimeException(messages.mkString("\n"))
