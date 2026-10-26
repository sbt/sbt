package lmcoursier.internal

import coursier.{ CoursierEnv, Resolution, Resolve }
import coursier.cache.CacheEnv
import coursier.cache.internal.ThreadUtil
import coursier.cache.loggers.{ FallbackRefreshDisplay, ProgressBarRefreshDisplay, RefreshLogger }
import coursier.core.*
import coursier.error.ResolutionError
import coursier.error.ResolutionError.CantDownloadModule
import coursier.ivy.IvyRepository
import coursier.maven.MavenRepositoryLike
import coursier.params.Mirror
import coursier.params.rule.RuleResolution
import coursier.util.{ EnvValues, Task }
import sbt.util.Logger

import scala.annotation.nowarn
import scala.concurrent.duration.FiniteDuration
import scala.collection.mutable

// private[coursier]
object ResolutionRun:

  /**
   * Mirrors from the coursier configuration, plus the ones of Maven's settings.xml
   * only when COURSIER_MAVEN_SETTINGS (or coursier.maven-settings) is set explicitly,
   * since a settings.xml mirror of `*` would otherwise shadow the resolvers of the build.
   * See https://github.com/sbt/sbt/issues/9821
   */
  lazy val defaultMirrors: Seq[Mirror] =
    CoursierEnv.defaultMirrors(
      CoursierEnv.mirrors.read(),
      CoursierEnv.mirrorsExtra.read(),
      CoursierEnv.scalaCliConfig.read(),
      CacheEnv.configDir.read()
    ) ++
      mavenSettingsMirrors(
        CoursierEnv.mavenSettings.read(),
        CoursierEnv.mavenHome.read(),
        CoursierEnv.mavenHomeFallback.read()
      )

  private[lmcoursier] def mavenSettingsMirrors(
      mavenSettings: EnvValues,
      mavenHome: EnvValues,
      mavenHomeFallback: EnvValues
  ): Seq[Mirror] =
    val explicit = mavenSettings.env.orElse(mavenSettings.prop).exists(_.trim.nonEmpty)
    if explicit then
      CoursierEnv.defaultMavenSettingsMirrors(mavenSettings, mavenHome, mavenHomeFallback)
    else Nil

  private def resolution(
      params: ResolutionParams,
      verbosityLevel: Int,
      log: Logger,
      configs: Set[Configuration],
      startingResolutionOpt: Option[Resolution]
  ): Either[coursier.error.ResolutionError, Resolution] =

    val isScalaToolConfig = configs(Configuration("scala-tool"))
    // Ref coursier/coursier#1340 coursier/coursier#1442
    // This treats ScalaTool as a sandbox configuration isolated from other subprojects.
    // Likely this behavior is needed only for ScalaTool configuration where the scala-xml
    // build's ScalaTool configuration transitively loops back to scala-xml's Compile artifacts.
    // In most other cases, it's desirable to allow "x->compile" relationship.
    def isSandboxConfig: Boolean = isScalaToolConfig

    val repositories =
      params.internalRepositories.drop(if isSandboxConfig then 1 else 0) ++
        params.mainRepositories ++
        params.fallbackDependenciesRepositories

    val rules =
      params.params.rules ++ params.strictOpt.map(s => Seq((s, RuleResolution.Fail))).getOrElse(Nil)

    val printOptionalMessage = verbosityLevel >= 0 && verbosityLevel <= 1

    @nowarn
    def depsRepr(deps: Seq[(Configuration, Dependency)]) =
      deps
        .map { (config, dep) =>
          s"${dep.module}:${dep.version}:${config.value}->${dep.configuration.value}"
        }
        .sorted
        .distinct

    val initialMessage =
      Seq(
        if verbosityLevel >= 0 then
          Seq(
            s"Updating ${params.projectName}" + (if params.sbtClassifiers then " (sbt classifiers)"
                                                 else "")
          )
        else Nil,
        if verbosityLevel >= 2 then depsRepr(params.dependencies).map(depRepr => s"  $depRepr")
        else Nil
      ).flatten.mkString("\n")

    if verbosityLevel >= 2 then
      val repoReprs = repositories.map {
        case r: IvyRepository =>
          s"ivy:${r.pattern}"
        case _: InterProjectRepository =>
          "inter-project"
        case r: MavenRepositoryLike =>
          r.root
        case r =>
          // should not happen
          r.toString
      }

      log.info(
        "Repositories:\n" +
          repoReprs.map("  " + _).mkString("\n")
      )

    if verbosityLevel >= 2 then log.info(initialMessage)

    @nowarn
    val resolveTask: Resolve[Task] =
      Resolve()
        .copy(
          // re-using various caches from a resolution of a configuration we extend
          initialResolution = startingResolutionOpt,
          dependencies = params.dependencies.collect {
            case (config, dep) if configs(config) =>
              dep
          },
          boms = params.boms,
          repositories = repositories,
          mirrors = defaultMirrors,
          resolutionParams = params.params
            .addForceVersion(
              (if isSandboxConfig then Nil
               else params.interProjectDependencies.map(_.moduleVersion))*
            )
            .withForceScalaVersion(params.autoScalaLibOpt.nonEmpty)
            .withScalaVersionOpt(params.autoScalaLibOpt.map(_._2))
            .copy(typelevel = params.params.typelevel, rules = rules),
          cache = params.cache
            .copy(logger = params.loggerOpt.getOrElse {
              RefreshLogger.create(
                if RefreshLogger.defaultFallbackMode then new FallbackRefreshDisplay()
                else
                  ProgressBarRefreshDisplay.create(
                    if printOptionalMessage then log.info(initialMessage),
                    if printOptionalMessage || verbosityLevel >= 2 then
                      log.info(s"Resolved ${params.projectName} dependencies")
                  )
              )
            })
        )

    val (period, maxAttempts) = params.retry
    val finalResult: Either[ResolutionError, Resolution] =

      def retry(
          attempt: Int,
          waitOnError: FiniteDuration
      ): Task[Either[ResolutionError, Resolution]] =
        resolveTask.io.attempt
          .flatMap {
            case Left(e: ResolutionError) =>
              if isTransientResolutionError(e) then
                if attempt + 1 >= maxAttempts then
                  log.error(s"Failed, maximum iterations ($maxAttempts) reached")
                  Task.point(Left(e))
                else
                  log.warn(s"Attempt ${attempt + 1} failed: $e")
                  Task.completeAfter(retryScheduler, waitOnError).flatMap { _ =>
                    retry(attempt + 1, waitOnError * 2)
                  }
              else Task.point(Left(e))
            case Left(ex) =>
              Task.fail(ex)
            case Right(value) =>
              Task.point(Right(value))
          }

      retry(0, period).unsafeRun()(using resolveTask.cache.ec)
    end finalResult

    finalResult match
      case Left(err) if params.missingOk => Right(err.resolution)
      case others                        => others
  end resolution

  @nowarn
  def resolutions(
      params: ResolutionParams,
      verbosityLevel: Int,
      log: Logger
  ): Either[coursier.error.ResolutionError, Map[Configuration, Resolution]] =

    // TODO Warn about possible duplicated modules from source repositories?

    if verbosityLevel >= 2 then
      log.info("InterProjectRepository")
      for p <- params.interProjectDependencies do log.info(s"  ${p.module}:${p.version}")

    SbtCoursierCache.default.resolutionOpt(params.resolutionKey).map(Right(_)).getOrElse {
      val resOrError =
        Lock.maybeSynchronized(needsLock =
          Lock.progressBarActive(
            hasCustomLogger = params.loggerOpt.nonEmpty,
            fallbackMode = RefreshLogger.defaultFallbackMode
          )
        ) {
          val map = new mutable.HashMap[Configuration, Resolution]
          val either = params.orderedConfigs.foldLeft[Either[coursier.error.ResolutionError, Unit]](
            Right(())
          ) { case (acc, (config, extends0)) =>
            for
              _ <- acc
              initRes =
                val it = extends0.iterator.flatMap(map.get(_).iterator)
                if it.hasNext then Some(it.next())
                else None
              allExtends = params.allConfigExtends.getOrElse(config, Set.empty)
              res <- resolution(params, verbosityLevel, log, allExtends, initRes)
            yield
              map += config -> res
              ()
          }
          either.map(_ => map.toMap)
        }
      for res <- resOrError do SbtCoursierCache.default.putResolution(params.resolutionKey, res)
      resOrError
    }
  end resolutions

  def resolutionsWithLockFile(
      params: ResolutionParams,
      verbosityLevel: Int,
      log: Logger,
      lockFileOpt: Option[java.io.File],
      scalaVersion: Option[String]
  ): Either[coursier.error.ResolutionError, (Map[Configuration, Resolution], Boolean)] =
    resolutionsWithLockFileData(params, verbosityLevel, log, lockFileOpt, scalaVersion)
      .map { case (res, lockDataOpt) => (res, lockDataOpt.isDefined) }

  def resolutionsWithLockFileData(
      params: ResolutionParams,
      verbosityLevel: Int,
      log: Logger,
      lockFileOpt: Option[java.io.File],
      scalaVersion: Option[String]
  ): Either[
    coursier.error.ResolutionError,
    (Map[Configuration, Resolution], Option[LockFileData])
  ] =
    lockFileOpt
      .flatMap { lockFile =>
        LockFile.read(lockFile) match
          case Right(lockData) =>
            if BuildClock.matches(
                lockData,
                params.dependencies,
                params.mainRepositories,
                scalaVersion,
                params
              )
            then
              if verbosityLevel >= 1 then log.info(s"Using lock file: ${lockFile.getAbsolutePath}")
              val reconstructed = ResolutionSerializer.reconstructResolutions(lockData, params)
              Some(Right((reconstructed, Some(lockData))))
            else
              if verbosityLevel >= 1 then log.info(s"Lock file outdated, performing resolution")
              None
          case Left(err) =>
            if verbosityLevel >= 2 then log.debug(s"Lock file error: $err")
            None
      }
      .getOrElse {
        resolutions(params, verbosityLevel, log).map(res => (res, None))
      }

  private lazy val retryScheduler = ThreadUtil.fixedScheduledThreadPool(1)

  private[internal] def isTransientResolutionError(e: ResolutionError): Boolean =
    e.errors.exists {
      case err: CantDownloadModule => isTimeout(err) || isServerError(err)
      case _                       => false
    }

  private def isTimeout(err: CantDownloadModule): Boolean =
    err.perRepositoryErrors.exists(_.contains("Connection timed out"))

  private def isServerError(err: CantDownloadModule): Boolean =
    err.perRepositoryErrors.exists(_.contains("Server returned HTTP response code: 5"))
end ResolutionRun
