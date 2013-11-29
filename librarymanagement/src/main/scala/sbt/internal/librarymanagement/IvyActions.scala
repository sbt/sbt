/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010 Mark Harrah
 */
package sbt.internal.librarymanagement

import java.io.File
import scala.xml.{ Node => XNode, NodeSeq }
import collection.mutable
import ivyint.CachedResolutionResolveEngine

import org.apache.ivy.Ivy
import org.apache.ivy.core.{ IvyPatternHelper, LogOptions }
import org.apache.ivy.core.deliver.DeliverOptions
import org.apache.ivy.core.install.InstallOptions
import org.apache.ivy.core.module.descriptor.{ Artifact => IArtifact, MDArtifact, ModuleDescriptor, DefaultModuleDescriptor }
import org.apache.ivy.core.report.ResolveReport
import org.apache.ivy.core.resolve.ResolveOptions
import org.apache.ivy.plugins.resolver.{ BasicResolver, DependencyResolver }
import sbt.io.{ IO, PathFinder }
import sbt.util.Logger
import sbt.internal.util.{ ShowLines, SourcePosition, LinePosition, RangePosition, LineRange }
import sbt.librarymanagement._
import sbt.internal.librarymanagement.syntax._

final class DeliverConfiguration(val deliverIvyPattern: String, val status: String, val configurations: Option[Seq[Configuration]], val logging: UpdateLogging.Value)
final class PublishConfiguration(val ivyFile: Option[File], val resolverName: String, val artifacts: Map[Artifact, File], val checksums: Seq[String], val logging: UpdateLogging.Value,
  val overwrite: Boolean) {
  def this(ivyFile: Option[File], resolverName: String, artifacts: Map[Artifact, File], checksums: Seq[String], logging: UpdateLogging.Value) =
    this(ivyFile, resolverName, artifacts, checksums, logging, false)
}

final class UpdateConfiguration(val retrieve: Option[RetrieveConfiguration], val missingOk: Boolean, val logging: UpdateLogging.Value, val artifactFilter: ArtifactTypeFilter) {
  @deprecated("You should use the constructor that provides an artifactFilter", "1.0.x")
  def this(retrieve: Option[RetrieveConfiguration], missingOk: Boolean, logging: UpdateLogging.Value) {
    this(retrieve, missingOk, logging, ArtifactTypeFilter.forbid(Set("src", "doc"))) // allow everything but "src", "doc" by default
  }

  private[sbt] def copy(
    retrieve: Option[RetrieveConfiguration] = this.retrieve,
    missingOk: Boolean = this.missingOk,
    logging: UpdateLogging.Value = this.logging,
    artifactFilter: ArtifactTypeFilter = this.artifactFilter
  ): UpdateConfiguration =
    new UpdateConfiguration(retrieve, missingOk, logging, artifactFilter)
}
final class RetrieveConfiguration(val retrieveDirectory: File, val outputPattern: String, val sync: Boolean, val configurationsToRetrieve: Option[Set[Configuration]]) {
  def this(retrieveDirectory: File, outputPattern: String) = this(retrieveDirectory, outputPattern, false, None)
  def this(retrieveDirectory: File, outputPattern: String, sync: Boolean) = this(retrieveDirectory, outputPattern, sync, None)
}
final case class MakePomConfiguration(file: File, moduleInfo: ModuleInfo, configurations: Option[Seq[Configuration]] = None, extra: NodeSeq = NodeSeq.Empty, process: XNode => XNode = n => n, filterRepositories: MavenRepository => Boolean = _ => true, allRepositories: Boolean, includeTypes: Set[String] = Set(Artifact.DefaultType, Artifact.PomType))
/** @param exclude is a map from ModuleID to classifiers that were previously tried and failed, so should now be excluded */
final case class GetClassifiersConfiguration(module: GetClassifiersModule, exclude: Map[ModuleID, Set[String]], configuration: UpdateConfiguration, ivyScala: Option[IvyScala], sourceArtifactTypes: Set[String], docArtifactTypes: Set[String])
final case class GetClassifiersModule(id: ModuleID, modules: Seq[ModuleID], configurations: Seq[Configuration], classifiers: Seq[String])

final class UnresolvedWarningConfiguration private[sbt] (
  val modulePositions: Map[ModuleID, SourcePosition]
)
object UnresolvedWarningConfiguration {
  def apply(): UnresolvedWarningConfiguration = apply(Map())
  def apply(modulePositions: Map[ModuleID, SourcePosition]): UnresolvedWarningConfiguration =
    new UnresolvedWarningConfiguration(modulePositions)
}

/**
 * Configures logging during an 'update'.  `level` determines the amount of other information logged.
 * `Full` is the default and logs the most.
 * `DownloadOnly` only logs what is downloaded.
 * `Quiet` only displays errors.
 * `Default` uses the current log level of `update` task.
 */
object UpdateLogging extends Enumeration {
  val Full, DownloadOnly, Quiet, Default = Value
}

object IvyActions {
  /** Installs the dependencies of the given 'module' from the resolver named 'from' to the resolver named 'to'.*/
  def install(module: IvySbt#Module, from: String, to: String, log: Logger): Unit = {
    module.withModule(log) { (ivy, md, default) =>
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
    module.withModule(log) { (ivy, md, default) =>
      module.owner.cleanCachedResolutionCache(md, log)
    }

  /** Creates a Maven pom from the given Ivy configuration*/
  def makePom(module: IvySbt#Module, configuration: MakePomConfiguration, log: Logger): Unit = {
    import configuration.{ allRepositories, moduleInfo, configurations, extra, file, filterRepositories, process, includeTypes }
    module.withModule(log) { (ivy, md, default) =>
      (new MakePom(log)).write(ivy, md, moduleInfo, configurations, includeTypes, extra, process, filterRepositories, allRepositories, file)
      log.info("Wrote " + file.getAbsolutePath)
    }
  }

  def deliver(module: IvySbt#Module, configuration: DeliverConfiguration, log: Logger): File =
    {
      import configuration._
      module.withModule(log) {
        case (ivy, md, default) =>
          val revID = md.getModuleRevisionId
          val options = DeliverOptions.newInstance(ivy.getSettings).setStatus(status)
          options.setConfs(IvySbt.getConfigurations(md, configurations))
          ivy.deliver(revID, revID.getRevision, deliverIvyPattern, options)
          deliveredFile(ivy, deliverIvyPattern, md)
      }
    }
  def deliveredFile(ivy: Ivy, pattern: String, md: ModuleDescriptor): File =
    ivy.getSettings.resolveFile(IvyPatternHelper.substitute(pattern, md.getResolvedModuleRevisionId))

  def publish(module: IvySbt#Module, configuration: PublishConfiguration, log: Logger): Unit = {
    import configuration._
    module.withModule(log) {
      case (ivy, md, default) =>
        val resolver = ivy.getSettings.getResolver(resolverName)
        if (resolver eq null) sys.error("Undefined resolver '" + resolverName + "'")
        val ivyArtifact = ivyFile map { file => (MDArtifact.newIvyArtifact(md), file) }
        val cross = crossVersionMap(module.moduleSettings)
        val as = mapArtifacts(md, cross, artifacts) ++ ivyArtifact.toSeq
        withChecksums(resolver, checksums) { publish(md, as, resolver, overwrite = overwrite) }
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
      case i: InlineConfiguration             => CrossVersion(i.module, i.ivyScala)
      case i: InlineConfigurationWithExcludes => CrossVersion(i.module, i.ivyScala)
      case _                                  => None
    }
  def mapArtifacts(module: ModuleDescriptor, cross: Option[String => String], artifacts: Map[Artifact, File]): Seq[(IArtifact, File)] =
    {
      val rawa = artifacts.keys.toSeq
      val seqa = CrossVersion.substituteCross(rawa, cross)
      val zipped = rawa zip IvySbt.mapArtifacts(module, seqa)
      zipped map { case (a, ivyA) => (ivyA, artifacts(a)) }
    }
  /**
   * Resolves and retrieves dependencies.  'ivyConfig' is used to produce an Ivy file and configuration.
   * 'updateConfig' configures the actual resolution and retrieval process.
   */
  @deprecated("This is no longer public.", "0.13.6")
  def update(module: IvySbt#Module, configuration: UpdateConfiguration, log: Logger): UpdateReport =
    updateEither(module, configuration, UnresolvedWarningConfiguration(), LogicalClock.unknown, None, log) match {
      case Right(r) => r
      case Left(w) =>
        throw w.resolveException
    }

  /**
   * Resolves and retrieves dependencies.  'ivyConfig' is used to produce an Ivy file and configuration.
   * 'updateConfig' configures the actual resolution and retrieval process.
   */
  private[sbt] def updateEither(module: IvySbt#Module, configuration: UpdateConfiguration,
    uwconfig: UnresolvedWarningConfiguration, logicalClock: LogicalClock, depDir: Option[File], log: Logger): Either[UnresolvedWarning, UpdateReport] =
    module.withModule(log) {
      case (ivy, md, default) if module.owner.configuration.updateOptions.cachedResolution && depDir.isDefined =>
        ivy.getResolveEngine match {
          case x: CachedResolutionResolveEngine =>
            val iw = IvySbt.inconsistentDuplicateWarning(md)
            iw foreach { log.warn(_) }
            val resolveOptions = new ResolveOptions
            val resolveId = ResolveOptions.getDefaultResolveId(md)
            resolveOptions.setResolveId(resolveId)
            resolveOptions.setArtifactFilter(configuration.artifactFilter)
            resolveOptions.setLog(ivyLogLevel(configuration.logging))
            x.customResolve(md, configuration.missingOk, logicalClock, resolveOptions, depDir getOrElse { sys.error("dependency base directory is not specified") }, log) match {
              case Left(x) =>
                Left(UnresolvedWarning(x, uwconfig))
              case Right(uReport) =>
                configuration.retrieve match {
                  case Some(rConf) => Right(retrieve(log, ivy, uReport, rConf))
                  case None        => Right(uReport)
                }
            }
        }
      case (ivy, md, default) =>
        val iw = IvySbt.inconsistentDuplicateWarning(md)
        iw foreach { log.warn(_) }
        val (report, err) = resolve(configuration.logging)(ivy, md, default, configuration.artifactFilter)
        err match {
          case Some(x) if !configuration.missingOk =>
            Left(UnresolvedWarning(x, uwconfig))
          case _ =>
            val cachedDescriptor = ivy.getSettings.getResolutionCacheManager.getResolvedIvyFileInCache(md.getModuleRevisionId)
            val uReport = IvyRetrieve.updateReport(report, cachedDescriptor)
            configuration.retrieve match {
              case Some(rConf) => Right(retrieve(log, ivy, uReport, rConf))
              case None        => Right(uReport)
            }
        }
    }
  @deprecated("No longer used.", "0.13.6")
  def processUnresolved(err: ResolveException, log: Logger): Unit = ()
  def groupedConflicts[T](moduleFilter: ModuleFilter, grouping: ModuleID => T)(report: UpdateReport): Map[T, Set[String]] =
    report.configurations.flatMap { confReport =>
      val evicted = confReport.evicted.filter(moduleFilter)
      val evictedSet = evicted.map(m => (m.organization, m.name)).toSet
      val conflicted = confReport.allModules.filter(mod => evictedSet((mod.organization, mod.name)))
      grouped(grouping)(conflicted ++ evicted)
    }.toMap

  def grouped[T](grouping: ModuleID => T)(mods: Seq[ModuleID]): Map[T, Set[String]] =
    mods groupBy (grouping) mapValues (_.map(_.revision).toSet)

  @deprecated("This is no longer public.", "0.13.6")
  def transitiveScratch(ivySbt: IvySbt, label: String, config: GetClassifiersConfiguration, log: Logger): UpdateReport =
    transitiveScratch(ivySbt, label, config, UnresolvedWarningConfiguration(), LogicalClock.unknown, None, log)

  private[sbt] def transitiveScratch(ivySbt: IvySbt, label: String, config: GetClassifiersConfiguration,
    uwconfig: UnresolvedWarningConfiguration, logicalClock: LogicalClock, depDir: Option[File], log: Logger): UpdateReport =
    {
      import config.{ configuration => c, ivyScala, module => mod }
      import mod.{ id, modules => deps }
      val base = restrictedCopy(id, true).copy(name = id.name + "$" + label)
      val module = new ivySbt.Module(InlineConfigurationWithExcludes(base, ModuleInfo(base.name), deps).copy(ivyScala = ivyScala))
      val report = updateEither(module, c, uwconfig, logicalClock, depDir, log) match {
        case Right(r) => r
        case Left(w) =>
          throw w.resolveException
      }
      val newConfig = config.copy(module = mod.copy(modules = report.allModules))
      updateClassifiers(ivySbt, newConfig, uwconfig, logicalClock, depDir, Vector(), log)
    }
  @deprecated("This is no longer public.", "0.13.6")
  def updateClassifiers(ivySbt: IvySbt, config: GetClassifiersConfiguration, log: Logger): UpdateReport =
    updateClassifiers(ivySbt, config, UnresolvedWarningConfiguration(), LogicalClock.unknown, None, Vector(), log)

  // artifacts can be obtained from calling toSeq on UpdateReport
  private[sbt] def updateClassifiers(ivySbt: IvySbt, config: GetClassifiersConfiguration,
    uwconfig: UnresolvedWarningConfiguration, logicalClock: LogicalClock, depDir: Option[File],
    artifacts: Vector[(String, ModuleID, Artifact, File)],
    log: Logger): UpdateReport =
    {
      import config.{ configuration => c, module => mod, _ }
      import mod.{ configurations => confs, _ }
      assert(classifiers.nonEmpty, "classifiers cannot be empty")
      val baseModules = modules map { m => restrictedCopy(m, true) }
      // Adding list of explicit artifacts here.
      val deps = baseModules.distinct flatMap classifiedArtifacts(classifiers, exclude, artifacts)
      val base = restrictedCopy(id, true).copy(name = id.name + classifiers.mkString("$", "_", ""))
      val module = new ivySbt.Module(InlineConfigurationWithExcludes(base, ModuleInfo(base.name), deps).copy(ivyScala = ivyScala, configurations = confs))
      val upConf = new UpdateConfiguration(c.retrieve, true, c.logging)
      updateEither(module, upConf, uwconfig, logicalClock, depDir, log) match {
        case Right(r) => r
        case Left(w) =>
          throw w.resolveException
      }
    }
  // This version adds explicit artifact
  private[sbt] def classifiedArtifacts(
    classifiers: Seq[String],
    exclude: Map[ModuleID, Set[String]],
    artifacts: Vector[(String, ModuleID, Artifact, File)]
  )(m: ModuleID): Option[ModuleID] = {
    def sameModule(m1: ModuleID, m2: ModuleID): Boolean = m1.organization == m2.organization && m1.name == m2.name && m1.revision == m2.revision
    def explicitArtifacts =
      {
        val arts = (artifacts collect { case (_, x, art, _) if sameModule(m, x) && art.classifier.isDefined => art }).distinct
        if (arts.isEmpty) None
        else Some(m.copy(isTransitive = false, explicitArtifacts = arts))
      }
    def hardcodedArtifacts = classifiedArtifacts(classifiers, exclude)(m)
    explicitArtifacts orElse hardcodedArtifacts
  }
  private def classifiedArtifacts(classifiers: Seq[String], exclude: Map[ModuleID, Set[String]])(m: ModuleID): Option[ModuleID] =
    {
      val excluded = exclude getOrElse (restrictedCopy(m, false), Set.empty)
      val included = classifiers filterNot excluded
      if (included.isEmpty) None else Some(m.copy(isTransitive = false, explicitArtifacts = classifiedArtifacts(m.name, included)))
    }
  def addExcluded(report: UpdateReport, classifiers: Seq[String], exclude: Map[ModuleID, Set[String]]): UpdateReport =
    report.addMissing { id => classifiedArtifacts(id.name, classifiers filter getExcluded(id, exclude)) }
  def classifiedArtifacts(name: String, classifiers: Seq[String]): Seq[Artifact] =
    classifiers map { c => Artifact.classified(name, c) }
  private[this] def getExcluded(id: ModuleID, exclude: Map[ModuleID, Set[String]]): Set[String] =
    exclude.getOrElse(restrictedCopy(id, false), Set.empty[String])

  def extractExcludes(report: UpdateReport): Map[ModuleID, Set[String]] =
    report.allMissing flatMap { case (_, mod, art) => art.classifier.map { c => (restrictedCopy(mod, false), c) } } groupBy (_._1) map { case (mod, pairs) => (mod, pairs.map(_._2).toSet) }

  private[this] def restrictedCopy(m: ModuleID, confs: Boolean) =
    ModuleID(m.organization, m.name, m.revision, crossVersion = m.crossVersion, extraAttributes = m.extraAttributes, configurations = if (confs) m.configurations else None)

  private[this] def resolve(logging: UpdateLogging.Value)(ivy: Ivy, module: DefaultModuleDescriptor, defaultConf: String, filter: ArtifactTypeFilter): (ResolveReport, Option[ResolveException]) =
    {
      val resolveOptions = new ResolveOptions
      val resolveId = ResolveOptions.getDefaultResolveId(module)
      resolveOptions.setResolveId(resolveId)
      resolveOptions.setArtifactFilter(filter)
      resolveOptions.setLog(ivyLogLevel(logging))
      ResolutionCache.cleanModule(module.getModuleRevisionId, resolveId, ivy.getSettings.getResolutionCacheManager)
      val resolveReport = ivy.resolve(module, resolveOptions)
      val err =
        if (resolveReport.hasError) {
          val messages = resolveReport.getAllProblemMessages.toArray.map(_.toString).distinct
          val failedPaths = Map(resolveReport.getUnresolvedDependencies map { node =>
            val m = IvyRetrieve.toModuleID(node.getId)
            val path = IvyRetrieve.findPath(node, module.getModuleRevisionId) map { x =>
              IvyRetrieve.toModuleID(x.getId)
            }
            m -> path
          }: _*)
          val failed = failedPaths.keys.toSeq
          Some(new ResolveException(messages, failed, failedPaths))
        } else None
      (resolveReport, err)
    }
  private def retrieve(log: Logger, ivy: Ivy, report: UpdateReport, config: RetrieveConfiguration): UpdateReport =
    retrieve(log, ivy, report, config.retrieveDirectory, config.outputPattern, config.sync, config.configurationsToRetrieve)

  private def retrieve(log: Logger, ivy: Ivy, report: UpdateReport, base: File, pattern: String, sync: Boolean, configurationsToRetrieve: Option[Set[Configuration]]): UpdateReport =
    {
      val configurationNames = configurationsToRetrieve match {
        case None          => None
        case Some(configs) => Some(configs.map(_.name))
      }
      val existingFiles = PathFinder(base).allPaths.get filterNot { _.isDirectory }
      val toCopy = new collection.mutable.HashSet[(File, File)]
      val retReport = report retrieve { (conf, mid, art, cached) =>
        configurationNames match {
          case None                       => performRetrieve(conf, mid, art, base, pattern, cached, toCopy)
          case Some(names) if names(conf) => performRetrieve(conf, mid, art, base, pattern, cached, toCopy)
          case _                          => cached
        }
      }
      IO.copy(toCopy)
      val resolvedFiles = toCopy.map(_._2)
      if (sync) {
        val filesToDelete = existingFiles.filterNot(resolvedFiles.contains)
        filesToDelete foreach { f =>
          log.info(s"Deleting old dependency: ${f.getAbsolutePath}")
          f.delete()
        }
      }

      retReport
    }

  private def performRetrieve(conf: String, mid: ModuleID, art: Artifact, base: File, pattern: String, cached: File, toCopy: collection.mutable.HashSet[(File, File)]): File = {
    val to = retrieveTarget(conf, mid, art, base, pattern)
    toCopy += ((cached, to))
    to
  }

  private def retrieveTarget(conf: String, mid: ModuleID, art: Artifact, base: File, pattern: String): File =
    new File(base, substitute(conf, mid, art, pattern))

  private def substitute(conf: String, mid: ModuleID, art: Artifact, pattern: String): String =
    {
      val mextra = IvySbt.javaMap(mid.extraAttributes, true)
      val aextra = IvySbt.extra(art, true)
      IvyPatternHelper.substitute(pattern, mid.organization, mid.name, mid.revision, art.name, art.`type`, art.extension, conf, mextra, aextra)
    }

  import UpdateLogging.{ Quiet, Full, DownloadOnly, Default }
  import LogOptions.{ LOG_QUIET, LOG_DEFAULT, LOG_DOWNLOAD_ONLY }
  private def ivyLogLevel(level: UpdateLogging.Value) =
    level match {
      case Quiet        => LOG_QUIET
      case DownloadOnly => LOG_DOWNLOAD_ONLY
      case Full         => LOG_DEFAULT
      case Default      => LOG_DOWNLOAD_ONLY
    }

  def publish(module: ModuleDescriptor, artifacts: Seq[(IArtifact, File)], resolver: DependencyResolver, overwrite: Boolean): Unit =
    {
      if (artifacts.nonEmpty) {
        checkFilesPresent(artifacts)
        try {
          resolver.beginPublishTransaction(module.getModuleRevisionId(), overwrite);
          for ((artifact, file) <- artifacts)
            resolver.publish(artifact, file, overwrite)
          resolver.commitPublishTransaction()
        } catch {
          case e: Throwable =>
            try { resolver.abortPublishTransaction() }
            finally { throw e }
        }
      }
    }
  private[this] def checkFilesPresent(artifacts: Seq[(IArtifact, File)]): Unit = {
    val missing = artifacts filter { case (a, file) => !file.exists }
    if (missing.nonEmpty)
      sys.error("Missing files for publishing:\n\t" + missing.map(_._2.getAbsolutePath).mkString("\n\t"))
  }
}
final class ResolveException(
  val messages: Seq[String],
  val failed: Seq[ModuleID],
  val failedPaths: Map[ModuleID, Seq[ModuleID]]
) extends RuntimeException(messages.mkString("\n")) {
  def this(messages: Seq[String], failed: Seq[ModuleID]) =
    this(messages, failed, Map(failed map { m => m -> Nil }: _*))
}
/**
 * Represents unresolved dependency warning, which displays reconstructed dependency tree
 * along with source position of each node.
 */
final class UnresolvedWarning private[sbt] (
  val resolveException: ResolveException,
  val failedPaths: Seq[Seq[(ModuleID, Option[SourcePosition])]]
)
object UnresolvedWarning {
  private[sbt] def apply(err: ResolveException, config: UnresolvedWarningConfiguration): UnresolvedWarning = {
    def modulePosition(m0: ModuleID): Option[SourcePosition] =
      config.modulePositions.find {
        case (m, p) =>
          (m.organization == m0.organization) &&
            (m0.name startsWith m.name) &&
            (m.revision == m0.revision)
      } map {
        case (m, p) => p
      }
    val failedPaths = err.failed map { x: ModuleID =>
      err.failedPaths(x).toList.reverse map { id =>
        (id, modulePosition(id))
      }
    }
    apply(err, failedPaths)
  }
  private[sbt] def apply(err: ResolveException, failedPaths: Seq[Seq[(ModuleID, Option[SourcePosition])]]): UnresolvedWarning =
    new UnresolvedWarning(err, failedPaths)
  private[sbt] def sourcePosStr(posOpt: Option[SourcePosition]): String =
    posOpt match {
      case Some(LinePosition(path, start)) => s" ($path#L$start)"
      case Some(RangePosition(path, LineRange(start, end))) => s" ($path#L$start-$end)"
      case _ => ""
    }
  implicit val unresolvedWarningLines: ShowLines[UnresolvedWarning] = ShowLines { a =>
    val withExtra = a.resolveException.failed.filter(_.extraDependencyAttributes.nonEmpty)
    val buffer = mutable.ListBuffer[String]()
    if (withExtra.nonEmpty) {
      buffer += "\n\tNote: Some unresolved dependencies have extra attributes.  Check that these dependencies exist with the requested attributes."
      withExtra foreach { id => buffer += "\t\t" + id }
    }
    if (a.failedPaths.nonEmpty) {
      buffer += "\n\tNote: Unresolved dependencies path:"
      a.failedPaths foreach { path =>
        if (path.nonEmpty) {
          val head = path.head
          buffer += "\t\t" + head._1.toString + sourcePosStr(head._2)
          path.tail foreach {
            case (m, pos) =>
              buffer += "\t\t  +- " + m.toString + sourcePosStr(pos)
          }
        }
      }
    }
    buffer.toList
  }
}
