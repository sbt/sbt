/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010 Mark Harrah
 */
package sbt.internal.librarymanagement

import java.io.File

import ivyint.CachedResolutionResolveEngine
import org.apache.ivy.Ivy
import org.apache.ivy.core.{ IvyPatternHelper, LogOptions }
import org.apache.ivy.core.deliver.DeliverOptions
import org.apache.ivy.core.install.InstallOptions
import org.apache.ivy.core.module.descriptor.{
  DefaultModuleDescriptor,
  MDArtifact,
  ModuleDescriptor,
  Artifact => IArtifact
}
import org.apache.ivy.core.resolve.ResolveOptions
import org.apache.ivy.plugins.resolver.{ BasicResolver, DependencyResolver }
import org.apache.ivy.util.filter.{ Filter => IvyFilter }
import sbt.io.{ IO, PathFinder }
import sbt.util.Logger
import sbt.librarymanagement.{ ModuleDescriptorConfiguration => InlineConfiguration, _ }
import syntax._
import InternalDefaults._
import UpdateClassifiersUtil._
import sbt.internal.librarymanagement.IvyUtil.TransientNetworkException

object IvyActions {

  /** Installs the dependencies of the given 'module' from the resolver named 'from' to the resolver named 'to'. */
  def install(module: IvySbt#Module, from: String, to: String, log: Logger): Unit = {
    module.withModule(log) { (ivy, md, _) =>
      for (dependency <- md.getDependencies) {
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

  /**
   * Cleans the cached resolution cache, if any.
   * This is called by clean.
   */
  private[sbt] def cleanCachedResolutionCache(module: IvySbt#Module, log: Logger): Unit =
    module.withModule(log) { (_, _, _) =>
      module.owner.cleanCachedResolutionCache()
    }

  /** Creates a Maven pom from the given Ivy configuration */
  def makePomFile(module: IvySbt#Module, configuration: MakePomConfiguration, log: Logger): File = {
    import configuration.{
      allRepositories,
      configurations,
      filterRepositories,
      process,
      includeTypes
    }
    val file = configuration.file.getOrElse(sys.error("file must be specified."))
    val moduleInfo = configuration.moduleInfo.getOrElse(sys.error("moduleInfo must be specified."))
    val extra = configuration.extra.getOrElse(scala.xml.NodeSeq.Empty)
    module.withModule(log) { (ivy, md, _) =>
      (new MakePom(log)).write(
        ivy,
        md,
        moduleInfo,
        configurations,
        includeTypes,
        extra,
        process,
        filterRepositories,
        allRepositories,
        file
      )
      log.info("Wrote " + file.getAbsolutePath)
      file
    }
  }

  def deliver(module: IvySbt#Module, configuration: PublishConfiguration, log: Logger): File = {
    val deliverIvyPattern = configuration.deliverIvyPattern
      .getOrElse(sys.error("deliverIvyPattern must be specified."))
    val status = getDeliverStatus(configuration.status)
    module.withModule(log) { case (ivy, md, _) =>
      val revID = md.getModuleRevisionId
      val options = DeliverOptions.newInstance(ivy.getSettings).setStatus(status)
      options.setConfs(getConfigurations(md, configuration.configurations))
      ivy.deliver(revID, revID.getRevision, deliverIvyPattern, options)
      deliveredFile(ivy, deliverIvyPattern, md)
    }
  }

  def getConfigurations(
      module: ModuleDescriptor,
      configurations: Option[Vector[ConfigRef]]
  ): Array[String] =
    configurations match {
      case Some(confs) => (confs map { _.name }).toArray
      case None        => module.getPublicConfigurationsNames
    }

  def deliveredFile(ivy: Ivy, pattern: String, md: ModuleDescriptor): File =
    ivy.getSettings.resolveFile(
      IvyPatternHelper.substitute(pattern, md.getResolvedModuleRevisionId)
    )

  def publish(module: IvySbt#Module, configuration: PublishConfiguration, log: Logger): Unit = {
    val resolverName = configuration.resolverName match {
      case Some(x) => x
      case _       => sys.error("Resolver name is not specified")
    }

    // Todo. Fix publish ordering https://github.com/sbt/sbt/issues/2088#issuecomment-246208872
    val ivyFile: Option[File] =
      if (configuration.publishMavenStyle) None
      else {
        Option(deliver(module, configuration, log))
      }

    val artifacts = Map(configuration.artifacts: _*)
    val checksums = configuration.checksums
    module.withModule(log) { case (ivy, md, _) =>
      val resolver = ivy.getSettings.getResolver(resolverName)
      if (resolver eq null) sys.error("Undefined resolver '" + resolverName + "'")
      val ivyArtifact = ivyFile map { file =>
        (MDArtifact.newIvyArtifact(md), file)
      }
      val cross = crossVersionMap(module.moduleSettings)
      val as = mapArtifacts(md, cross, artifacts) ++ ivyArtifact.toList
      withChecksums(resolver, checksums) {
        publish(md, as, resolver, overwrite = configuration.overwrite)
      }
    }
  }
  private[this] def withChecksums[T](resolver: DependencyResolver, checksums: Vector[String])(
      act: => T
  ): T =
    resolver match { case br: BasicResolver => withChecksums(br, checksums)(act); case _ => act }
  private[this] def withChecksums[T](resolver: BasicResolver, checksums: Vector[String])(
      act: => T
  ): T = {
    val previous = resolver.getChecksumAlgorithms
    resolver.setChecksums(checksums mkString ",")
    try {
      act
    } finally {
      resolver.setChecksums(previous mkString ",")
    }
  }
  private def crossVersionMap(moduleSettings: ModuleSettings): Option[String => String] =
    moduleSettings match {
      case i: InlineConfiguration => CrossVersion(i.module, i.scalaModuleInfo)
      case _                      => None
    }
  def mapArtifacts(
      module: ModuleDescriptor,
      cross: Option[String => String],
      artifacts: Map[Artifact, File]
  ): Vector[(IArtifact, File)] = {
    val rawa = artifacts.keys.toVector
    val seqa = CrossVersion.substituteCross(rawa, cross)
    val zipped = rawa zip IvySbt.mapArtifacts(module, seqa)
    zipped map { case (a, ivyA) => (ivyA, artifacts(a)) }
  }

  /**
   * Updates one module's dependencies performing a dependency resolution and retrieval.
   *
   * The following mechanism uses ivy under the hood.
   *
   * @param module The module to be resolved.
   * @param configuration The update configuration.
   * @param uwconfig The configuration to handle unresolved warnings.
   * @param log The logger.
   * @return The result, either an unresolved warning or an update report. Note that this
   *         update report will or will not be successful depending on the `missingOk` option.
   */
  private[sbt] def updateEither(
      module: IvySbt#Module,
      configuration: UpdateConfiguration,
      uwconfig: UnresolvedWarningConfiguration,
      log: Logger
  ): Either[UnresolvedWarning, UpdateReport] = {
    module.withModule(log) { case (ivy, moduleDescriptor, _) =>
      // Warn about duplicated and inconsistent dependencies
      val iw = IvySbt.inconsistentDuplicateWarning(moduleDescriptor)
      iw.foreach(log.warn(_))

      val metadataDirectory = configuration.metadataDirectory

      // Create inputs, resolve and retrieve the module descriptor
      val inputs = ResolutionInputs(ivy, moduleDescriptor, configuration, log)
      val resolutionResult: Either[ResolveException, UpdateReport] = {
        if (
          module.owner.configuration.updateOptions.cachedResolution && metadataDirectory.isDefined
        ) {
          val cache =
            metadataDirectory.getOrElse(sys.error("Missing directory for cached resolution."))
          cachedResolveAndRetrieve(inputs, cache)
        } else resolveAndRetrieve(inputs)
      }

      // Convert to unresolved warning or retrieve update report
      resolutionResult.fold(
        exception => Left(UnresolvedWarning(exception, uwconfig)),
        ur0 => {
          val ur = configuration.retrieveManaged match {
            case Some(retrieveConf) => retrieve(log, ivy, ur0, retrieveConf)
            case _                  => ur0
          }
          Right(ur)
        }
      )
    }
  }

  def groupedConflicts[T](moduleFilter: ModuleFilter, grouping: ModuleID => T)(
      report: UpdateReport
  ): Map[T, Set[String]] =
    report.configurations.flatMap { confReport =>
      val evicted = confReport.evicted.filter(moduleFilter)
      val evictedSet = evicted.map(m => (m.organization, m.name)).toSet
      val conflicted =
        confReport.allModules.filter(mod => evictedSet((mod.organization, mod.name)))
      grouped(grouping)(conflicted ++ evicted)
    }.toMap

  def grouped[T](grouping: ModuleID => T)(mods: Seq[ModuleID]): Map[T, Set[String]] =
    mods.groupBy(grouping).view.mapValues(_.map(_.revision).toSet).toMap

  def addExcluded(
      report: UpdateReport,
      classifiers: Vector[String],
      exclude: Map[ModuleID, Set[String]]
  ): UpdateReport =
    report.addMissing { id =>
      classifiedArtifacts(id.name, classifiers filter getExcluded(id, exclude))
    }

  private[this] def getExcluded(id: ModuleID, exclude: Map[ModuleID, Set[String]]): Set[String] =
    exclude.getOrElse(restrictedCopy(id, false), Set.empty[String])

  def extractExcludes(report: UpdateReport): Map[ModuleID, Set[String]] =
    report.allMissing flatMap { case (_, mod, art) =>
      art.classifier.map { c =>
        (restrictedCopy(mod, false), c)
      }
    } groupBy (_._1) map { case (mod, pairs) => (mod, pairs.map(_._2).toSet) }

  /**
   * Represents the inputs to pass in to [[resolveAndRetrieve]] and [[cachedResolveAndRetrieve]].
   *
   * @param ivy The ivy instance to resolve and retrieve dependencies.
   * @param module The module descriptor to be resolved.
   * @param updateConfiguration The update configuration for [[ResolveOptions]].
   * @param log The logger.
   */
  private case class ResolutionInputs(
      ivy: Ivy,
      module: DefaultModuleDescriptor,
      updateConfiguration: UpdateConfiguration,
      log: Logger
  )

  implicit def toIvyFilter(f: ArtifactTypeFilter): IvyFilter = new IvyFilter {
    override def accept(o: Object): Boolean = Option(o) exists { case a: IArtifact =>
      applyFilter(a)
    }

    def applyFilter(a: IArtifact): Boolean =
      (f.types contains a.getType) ^ f.inverted
  }

  /**
   * Defines the internal entrypoint of module resolution and retrieval.
   *
   * This method is the responsible of populating [[ResolveOptions]] and pass
   * it in to the ivy instance to perform the module resolution.
   *
   * It returns an already resolved [[UpdateReport]] instead of a [[ResolveReport]]
   * like its counterpart [[CachedResolutionResolveEngine.customResolve]].
   *
   * @param inputs The resolution inputs.
   * @return The result of the resolution.
   */
  private[this] def resolveAndRetrieve(
      inputs: ResolutionInputs
  ): Either[ResolveException, UpdateReport] = {
    // Populate resolve options from the passed arguments
    val ivyInstance = inputs.ivy
    val moduleDescriptor = inputs.module
    val updateConfiguration = inputs.updateConfiguration
    val resolveOptions = new ResolveOptions
    val resolveId = ResolveOptions.getDefaultResolveId(moduleDescriptor)
    val artifactFilter = getArtifactTypeFilter(updateConfiguration.artifactFilter)
    import updateConfiguration._
    resolveOptions.setResolveId(resolveId)
    resolveOptions.setArtifactFilter(artifactFilter)
    resolveOptions.setUseCacheOnly(offline)
    resolveOptions.setLog(ivyLogLevel(logging))
    if (frozen) {
      resolveOptions.setTransitive(false)
      resolveOptions.setCheckIfChanged(false)
    }
    ResolutionCache.cleanModule(
      moduleDescriptor.getModuleRevisionId,
      resolveId,
      ivyInstance.getSettings.getResolutionCacheManager
    )

    val resolveReport = ivyInstance.resolve(moduleDescriptor, resolveOptions)
    if (resolveReport.hasError && !missingOk) {
      import scala.jdk.CollectionConverters._
      // If strict error, collect report information and generated UnresolvedWarning
      val messages = resolveReport.getAllProblemMessages.asScala.toSeq.map(_.toString).distinct
      val failedPaths = resolveReport.getUnresolvedDependencies.map { node =>
        val moduleID = IvyRetrieve.toModuleID(node.getId)
        val path = IvyRetrieve
          .findPath(node, moduleDescriptor.getModuleRevisionId)
          .map(x => IvyRetrieve.toModuleID(x.getId))
        moduleID -> path
      }.toMap
      val failedModules = failedPaths.keys.toSeq
      Left(new ResolveException(messages, failedModules, failedPaths))
    } else {
      // If no strict error, we convert the resolve report into an update report
      val cachedDescriptor = ivyInstance.getSettings.getResolutionCacheManager
        .getResolvedIvyFileInCache(moduleDescriptor.getModuleRevisionId)
      Right(IvyRetrieve.updateReport(resolveReport, cachedDescriptor))
    }
  }

  /**
   * Resolves and retrieves a module with a cache mechanism defined
   * <a href="http://www.scala-sbt.org/0.13/docs/Cached-Resolution.html">here</a>.
   *
   * It's the cached version of [[resolveAndRetrieve]].
   *
   * @param inputs The resolution inputs.
   * @param logicalClock The clock to check if a file is outdated or not.
   * @param cache The optional cache dependency.
   * @return The result of the cached resolution.
   */
  private[this] def cachedResolveAndRetrieve(
      inputs: ResolutionInputs,
      cache: File
  ): Either[ResolveException, UpdateReport] = {
    val log = inputs.log
    val descriptor = inputs.module
    val updateConfiguration = inputs.updateConfiguration
    val resolver = inputs.ivy.getResolveEngine.asInstanceOf[CachedResolutionResolveEngine]
    val resolveOptions = new ResolveOptions
    val resolveId = ResolveOptions.getDefaultResolveId(descriptor)
    val artifactFilter = getArtifactTypeFilter(updateConfiguration.artifactFilter)
    import updateConfiguration._
    resolveOptions.setResolveId(resolveId)
    resolveOptions.setArtifactFilter(artifactFilter)
    resolveOptions.setUseCacheOnly(offline)
    resolveOptions.setLog(ivyLogLevel(logging))
    if (frozen) {
      resolveOptions.setTransitive(false)
      resolveOptions.setCheckIfChanged(false)
    }
    resolver.customResolve(
      descriptor,
      missingOk,
      updateConfiguration.logicalClock,
      resolveOptions,
      cache,
      log
    )
  }

  private def retrieve(
      log: Logger,
      ivy: Ivy,
      report: UpdateReport,
      config: RetrieveConfiguration
  ): UpdateReport = {
    val copyChecksums =
      Option(ivy.getVariable(ConvertResolver.ManagedChecksums)) match {
        case Some(x) => x.toBoolean
        case _       => false
      }
    val toRetrieve: Option[Vector[ConfigRef]] = config.configurationsToRetrieve
    val base = getRetrieveDirectory(config.retrieveDirectory)
    val pattern = getRetrievePattern(config.outputPattern)
    val existingFiles = PathFinder(base).allPaths.get() filterNot { _.isDirectory }
    val toCopy = new collection.mutable.HashSet[(File, File)]
    val retReport = report retrieve { (conf: ConfigRef, mid, art, cached) =>
      toRetrieve match {
        case None => performRetrieve(conf, mid, art, base, pattern, cached, copyChecksums, toCopy)
        case Some(refs) if refs.contains[ConfigRef](conf) =>
          performRetrieve(conf, mid, art, base, pattern, cached, copyChecksums, toCopy)
        case _ => cached
      }
    }
    IO.copy(toCopy)
    val resolvedFiles = toCopy.map(_._2)
    if (config.sync) {
      val filesToDelete = existingFiles.filterNot(resolvedFiles.contains)
      filesToDelete foreach { f =>
        log.info(s"Deleting old dependency: ${f.getAbsolutePath}")
        f.delete()
      }
    }

    retReport
  }

  private def performRetrieve(
      conf: ConfigRef,
      mid: ModuleID,
      art: Artifact,
      base: File,
      pattern: String,
      cached: File,
      copyChecksums: Boolean,
      toCopy: collection.mutable.HashSet[(File, File)]
  ): File = {
    val to = retrieveTarget(conf, mid, art, base, pattern)
    toCopy += ((cached, to))

    if (copyChecksums) {
      // Copy over to the lib managed directory any checksum for a jar if it exists
      // TODO(jvican): Support user-provided checksums
      val cachePath = cached.getAbsolutePath
      IvySbt.DefaultChecksums.foreach { checksum =>
        if (cachePath.endsWith(".jar")) {
          val cacheChecksum = new File(s"$cachePath.$checksum")
          if (cacheChecksum.exists()) {
            val toChecksum = new File(s"${to.getAbsolutePath}.$checksum")
            toCopy += ((cacheChecksum, toChecksum))
          }
        }
      }
    }

    to
  }

  private def retrieveTarget(
      conf: ConfigRef,
      mid: ModuleID,
      art: Artifact,
      base: File,
      pattern: String
  ): File =
    new File(base, substitute(conf, mid, art, pattern))

  private def substitute(conf: ConfigRef, mid: ModuleID, art: Artifact, pattern: String): String = {
    val mextra = IvySbt.javaMap(mid.extraAttributes, true)
    val aextra = IvySbt.extra(art, true)
    IvyPatternHelper.substitute(
      pattern,
      mid.organization,
      mid.name,
      mid.branchName.orNull,
      mid.revision,
      art.name,
      art.`type`,
      art.extension,
      conf.name,
      null,
      mextra,
      aextra
    )
  }

  import UpdateLogging.{ Quiet, Full, DownloadOnly, Default }
  import LogOptions.{ LOG_QUIET, LOG_DEFAULT, LOG_DOWNLOAD_ONLY }
  private def ivyLogLevel(level: UpdateLogging) =
    level match {
      case Quiet        => LOG_QUIET
      case DownloadOnly => LOG_DOWNLOAD_ONLY
      case Full         => LOG_DEFAULT
      case Default      => LOG_DOWNLOAD_ONLY
    }

  def publish(
      module: ModuleDescriptor,
      artifacts: Seq[(IArtifact, File)],
      resolver: DependencyResolver,
      overwrite: Boolean
  ): Unit = {
    if (artifacts.nonEmpty) {
      checkFilesPresent(artifacts)
      try {
        resolver.beginPublishTransaction(module.getModuleRevisionId(), overwrite);
        artifacts.foreach { case (artifact, file) =>
          IvyUtil.retryWithBackoff(
            resolver.publish(artifact, file, overwrite),
            TransientNetworkException.apply,
            maxAttempts = LMSysProp.maxPublishAttempts
          )
        }
        resolver.commitPublishTransaction()
      } catch {
        case e: Throwable =>
          try {
            resolver.abortPublishTransaction()
          } finally {
            throw e
          }
      }
    }
  }
  private[this] def checkFilesPresent(artifacts: Seq[(IArtifact, File)]): Unit = {
    val missing = artifacts filter { case (_, file) => !file.exists }
    if (missing.nonEmpty)
      sys.error(
        "Missing files for publishing:\n\t" + missing.map(_._2.getAbsolutePath).mkString("\n\t")
      )
  }
}
