/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import java.io.File
import java.util.concurrent.Callable

import sbt.SlashSyntax0._
import sbt.internal.librarymanagement._
import sbt.librarymanagement._
import sbt.librarymanagement.syntax._
import sbt.util.{ CacheStore, CacheStoreFactory, Level, Logger, Tracked }
import sbt.io.IO
import sbt.io.syntax._
import sbt.Project.richInitializeTask
import sjsonnew.JsonFormat
import scala.compat.Platform.EOL

private[sbt] object LibraryManagement {
  implicit val linter: sbt.dsl.LinterLevel.Ignore.type = sbt.dsl.LinterLevel.Ignore

  private type UpdateInputs = (Long, ModuleSettings, UpdateConfiguration)

  def cachedUpdate(
      lm: DependencyResolution,
      module: ModuleDescriptor,
      cacheStoreFactory: CacheStoreFactory,
      label: String,
      updateConfig: UpdateConfiguration,
      transform: UpdateReport => UpdateReport,
      skip: Boolean,
      force: Boolean,
      depsUpdated: Boolean,
      uwConfig: UnresolvedWarningConfiguration,
      evictionLevel: Level.Value,
      versionSchemeOverrides: Seq[ModuleID],
      assumedEvictionErrorLevel: Level.Value,
      assumedVersionScheme: String,
      assumedVersionSchemeJava: String,
      mavenStyle: Boolean,
      compatWarning: CompatibilityWarningOptions,
      includeCallers: Boolean,
      includeDetails: Boolean,
      log: Logger
  ): UpdateReport = {

    /* Resolve the module settings from the inputs. */
    def resolve: UpdateReport = {
      import sbt.util.ShowLines._

      log.debug(s"Updating $label...")
      val reportOrUnresolved: Either[UnresolvedWarning, UpdateReport] =
        lm.update(module, updateConfig, uwConfig, log)
      val report = reportOrUnresolved match {
        case Right(report0) => report0
        case Left(unresolvedWarning) =>
          unresolvedWarning.lines.foreach(log.warn(_))
          throw unresolvedWarning.resolveException
      }
      log.debug(s"Done updating $label")
      val report1 = transform(report)

      // Warn of any eviction and compatibility warnings
      val evictionError = EvictionError(
        report1,
        module,
        versionSchemeOverrides,
        assumedVersionScheme,
        assumedVersionSchemeJava,
        assumedEvictionErrorLevel
      )
      def extraLines = List(
        "",
        "this can be overridden using libraryDependencySchemes or evictionErrorLevel"
      )
      val errorLines: Seq[String] =
        (if (evictionError.incompatibleEvictions.isEmpty
             || evictionLevel != Level.Error) Nil
         else evictionError.lines) ++
          (if (evictionError.assumedIncompatibleEvictions.isEmpty
               || assumedEvictionErrorLevel != Level.Error) Nil
           else evictionError.toAssumedLines)
      if (errorLines.nonEmpty) sys.error((errorLines ++ extraLines).mkString(EOL))
      else {
        if (evictionError.incompatibleEvictions.isEmpty) ()
        else evictionError.lines.foreach(log.log(evictionLevel, _: String))

        if (evictionError.assumedIncompatibleEvictions.isEmpty) ()
        else evictionError.toAssumedLines.foreach(log.log(assumedEvictionErrorLevel, _: String))
      }
      CompatibilityWarning.run(compatWarning, module, mavenStyle, log)
      val report2 = transformDetails(report1, includeCallers, includeDetails)
      report2
    }

    /* Check if a update report is still up to date or we must resolve again. */
    def upToDate(inChanged: Boolean, out: UpdateReport): Boolean = {
      !force &&
      !depsUpdated &&
      !inChanged &&
      out.allFiles.forall(f => fileUptodate(f, out.stamps, log)) &&
      fileUptodate(out.cachedDescriptor, out.stamps, log)
    }

    /* Skip resolve if last output exists, otherwise error. */
    def skipResolve(cache: CacheStore): UpdateInputs => UpdateReport = {
      import sbt.librarymanagement.LibraryManagementCodec._
      Tracked.lastOutput[UpdateInputs, UpdateReport](cache) {
        case (_, Some(out)) => markAsCached(out)
        case _ =>
          sys.error("Skipping update requested, but update has not previously run successfully.")
      }
    }

    // Mark UpdateReport#stats as "cached." This is used by the dependers later
    // to determine whether they now need to run update in the above `upToDate`.
    def markAsCached(ur: UpdateReport): UpdateReport =
      ur.withStats(ur.stats.withCached(true))

    def doResolve(cache: CacheStore): UpdateInputs => UpdateReport = {
      val doCachedResolve = { (inChanged: Boolean, updateInputs: UpdateInputs) =>
        import sbt.librarymanagement.LibraryManagementCodec._
        val cachedResolve = Tracked.lastOutput[UpdateInputs, UpdateReport](cache) {
          case (_, Some(out)) if upToDate(inChanged, out) => markAsCached(out)
          case pair =>
            log.debug(s"""not up to date. inChanged = $inChanged, force = $force""")
            resolve
        }
        import scala.util.control.Exception.catching
        catching(classOf[NullPointerException], classOf[OutOfMemoryError])
          .withApply { t =>
            val resolvedAgain = resolve
            val culprit = t.getClass.getSimpleName
            log.warn(s"Update task caching failed due to $culprit.")
            log.warn("Report the following output to sbt:")
            resolvedAgain.toString.linesIterator.foreach(log.warn(_))
            log.trace(t)
            resolvedAgain
          }
          .apply(cachedResolve(updateInputs))
      }
      import LibraryManagementCodec._
      Tracked.inputChanged(cacheStoreFactory.make("inputs"))(doCachedResolve)
    }

    // Get the handler to use and feed it in the inputs
    // This is lm-engine specific input hashed into Long
    val extraInputHash = module.extraInputHash
    val settings = module.moduleSettings
    val outStore = cacheStoreFactory.make("output")
    val handler = if (skip && !force) skipResolve(outStore) else doResolve(outStore)
    // Remove clock for caching purpose
    val withoutClock = updateConfig.withLogicalClock(LogicalClock.unknown)
    handler((extraInputHash, settings, withoutClock))
  }

  private[this] def fileUptodate(file: File, stamps: Map[File, Long], log: Logger): Boolean = {
    val exists = file.exists
    // https://github.com/sbt/sbt/issues/5292 warn the user that the file is missing since this indicates
    // that UpdateReport was persisted but Coursier cache was not.
    if (!exists) {
      log.warn(s"${file.getName} no longer exists at $file")
    }
    // coursier doesn't populate stamps
    val timeStampIsSame = stamps
      .get(file)
      .forall(_ == IO.getModifiedTimeOrZero(file))
    exists && timeStampIsSame
  }

  private[sbt] def transitiveScratch(
      lm: DependencyResolution,
      label: String,
      config: GetClassifiersConfiguration,
      uwconfig: UnresolvedWarningConfiguration,
      log: Logger
  ): Either[UnresolvedWarning, UpdateReport] = {
    import config.{ updateConfiguration => c, module => mod }
    import mod.{ id, dependencies => deps, scalaModuleInfo }
    val base = restrictedCopy(id, true).withName(id.name + "$" + label)
    val module = lm.moduleDescriptor(base, deps, scalaModuleInfo)
    val report = lm.update(module, c, uwconfig, log) match {
      case Right(r) => r
      case Left(w) =>
        throw w.resolveException
    }
    val newConfig = config
      .withModule(mod.withDependencies(report.allModules))
    lm.updateClassifiers(newConfig, uwconfig, Vector(), log)
  }

  private[sbt] def restrictedCopy(m: ModuleID, confs: Boolean) =
    ModuleID(m.organization, m.name, m.revision)
      .withCrossVersion(m.crossVersion)
      .withExtraAttributes(m.extraAttributes)
      .withConfigurations(if (confs) m.configurations else None)
      .branch(m.branchName)

  private[this] def transformDetails(
      ur: UpdateReport,
      includeCallers: Boolean,
      includeDetails: Boolean
  ): UpdateReport = {
    val crs0 = ur.configurations
    val crs1 =
      if (includeDetails) crs0
      else crs0 map { _.withDetails(Vector()) }
    val crs2 =
      if (includeCallers) crs1
      else
        crs1 map { cr =>
          val mrs0 = cr.modules
          val mrs1 = mrs0 map { _.withCallers(Vector()) }
          cr.withModules(mrs1)
        }
    ur.withConfigurations(crs2)
  }

  val moduleIdJsonKeyFormat: sjsonnew.JsonKeyFormat[ModuleID] =
    new sjsonnew.JsonKeyFormat[ModuleID] {
      import LibraryManagementCodec._
      import sjsonnew.support.scalajson.unsafe._
      val moduleIdFormat: JsonFormat[ModuleID] = implicitly[JsonFormat[ModuleID]]
      def write(key: ModuleID): String =
        CompactPrinter(Converter.toJsonUnsafe(key)(moduleIdFormat))
      def read(key: String): ModuleID =
        Converter.fromJsonUnsafe[ModuleID](Parser.parseUnsafe(key))(moduleIdFormat)
    }

  /**
   * Resolves and optionally retrieves classified artifacts, such as javadocs and sources,
   * for dependency definitions, transitively.
   */
  def updateClassifiersTask: Def.Initialize[Task[UpdateReport]] =
    (Def.task {
      import Keys._
      val s = streams.value
      val cacheDirectory = streams.value.cacheDirectory
      val csr = useCoursier.value
      val lm = dependencyResolution.value

      if (csr) {
        // following copied from https://github.com/coursier/sbt-coursier/blob/9173406bb399879508aa481fed16efda72f55820/modules/sbt-lm-coursier/src/main/scala/sbt/hack/Foo.scala
        val isRoot = executionRoots.value contains resolvedScoped.value
        val shouldForce = isRoot || {
          forceUpdatePeriod.value match {
            case None => false
            case Some(period) =>
              val fullUpdateOutput = cacheDirectory / "out"
              val now = System.currentTimeMillis
              val diff = now - fullUpdateOutput.lastModified()
              val elapsedDuration = new scala.concurrent.duration.FiniteDuration(
                diff,
                java.util.concurrent.TimeUnit.MILLISECONDS
              )
              fullUpdateOutput.exists() && elapsedDuration > period
          }
        }
        val state0 = state.value
        val updateConf = {
          import UpdateLogging.{ Full, DownloadOnly, Default }
          val conf = updateConfiguration.value
          val maybeUpdateLevel = (update / logLevel).?.value
          val conf1 = maybeUpdateLevel.orElse(state0.get(logLevel.key)) match {
            case Some(Level.Debug) if conf.logging == Default => conf.withLogging(logging = Full)
            case Some(_) if conf.logging == Default           => conf.withLogging(logging = DownloadOnly)
            case _                                            => conf
          }
          // logical clock is folded into UpdateConfiguration
          conf1.withLogicalClock(LogicalClock(state0.hashCode))
        }
        cachedUpdate(
          // LM API
          lm = lm,
          // Ivy-free ModuleDescriptor
          module = ivyModule.value,
          s.cacheStoreFactory.sub(updateCacheName.value),
          Reference.display(thisProjectRef.value),
          updateConf,
          identity,
          skip = (update / skip).value,
          force = shouldForce,
          depsUpdated = transitiveUpdate.value.exists(!_.stats.cached),
          uwConfig = (update / unresolvedWarningConfiguration).value,
          evictionLevel = Level.Debug,
          versionSchemeOverrides = Nil,
          assumedEvictionErrorLevel = Level.Debug,
          assumedVersionScheme = VersionScheme.Always,
          assumedVersionSchemeJava = VersionScheme.Always,
          mavenStyle = publishMavenStyle.value,
          compatWarning = compatibilityWarningOptions.value,
          includeCallers = false,
          includeDetails = false,
          log = s.log
        )
      } else {
        val is = ivySbt.value
        val mod = classifiersModule.value
        val updateConfig0 = updateConfiguration.value
        lazy val updateConfig = updateConfig0
          .withMetadataDirectory(dependencyCacheDirectory.value)
          .withArtifactFilter(
            updateConfig0.artifactFilter.map(af => af.withInverted(!af.inverted))
          )
        val app = appConfiguration.value
        val srcTypes = sourceArtifactTypes.value
        val docTypes = docArtifactTypes.value
        val uwConfig = (update / unresolvedWarningConfiguration).value
        val out = is.withIvy(s.log)(_.getSettings.getDefaultIvyUserDir)
        withExcludes(out, mod.classifiers, lock(app)) { excludes =>
          lm.updateClassifiers(
            GetClassifiersConfiguration(
              mod,
              excludes.toVector,
              updateConfig,
              srcTypes.toVector,
              docTypes.toVector
            ),
            uwConfig,
            Vector.empty,
            s.log
          ) match {
            case Left(_)   => ???
            case Right(ur) => ur
          }
        }
      }
    } tag (Tags.Update, Tags.Network))

  def withExcludes(out: File, classifiers: Seq[String], lock: xsbti.GlobalLock)(
      f: Map[ModuleID, Vector[ConfigRef]] => UpdateReport
  ): UpdateReport = {
    import sbt.librarymanagement.LibraryManagementCodec._
    import sbt.util.FileBasedStore
    val exclName = "exclude_classifiers"
    val file = out / exclName
    val store = new FileBasedStore(file)
    lock(
      out / (exclName + ".lock"),
      new Callable[UpdateReport] {
        def call = {
          implicit val midJsonKeyFmt: sjsonnew.JsonKeyFormat[ModuleID] = moduleIdJsonKeyFormat
          val excludes =
            store
              .read[Map[ModuleID, Vector[ConfigRef]]](
                default = Map.empty[ModuleID, Vector[ConfigRef]]
              )
          val report = f(excludes)
          val allExcludes: Map[ModuleID, Vector[ConfigRef]] = excludes ++ IvyActions
            .extractExcludes(report)
            .mapValues(cs => cs.map(c => ConfigRef(c)).toVector)
          store.write(allExcludes)
          IvyActions
            .addExcluded(
              report,
              classifiers.toVector,
              allExcludes.mapValues(_.map(_.name).toSet).toMap
            )
        }
      }
    )
  }

  def lock(app: xsbti.AppConfiguration): xsbti.GlobalLock =
    app.provider.scalaProvider.launcher.globalLock
}
