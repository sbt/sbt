package lmcoursier.internal

import coursier.{Resolution, Resolve}
import coursier.cache.loggers.{FallbackRefreshDisplay, ProgressBarRefreshDisplay, RefreshLogger}
import coursier.core._
import coursier.ivy.IvyRepository
import coursier.maven.MavenRepository
import coursier.params.rule.RuleResolution
import sbt.util.Logger

import scala.collection.mutable

// private[coursier]
object ResolutionRun {

  private def resolution(
    params: ResolutionParams,
    verbosityLevel: Int,
    log: Logger,
    configs: Set[Configuration],
    startingResolutionOpt: Option[Resolution]
  ): Either[coursier.error.ResolutionError, Resolution] = {

    val isScalaToolConfig = configs(Configuration("scala-tool"))
    // Ref coursier/coursier#1340 coursier/coursier#1442
    // This treats ScalaTool as a sandbox configuration isolated from other subprojects.
    // Likely this behavior is needed only for ScalaTool configuration where the scala-xml
    // build's ScalaTool configuration transitively loops back to scala-xml's Compile artifacts.
    // In most other cases, it's desirable to allow "x->compile" relationship.
    def isSandboxConfig: Boolean = isScalaToolConfig

    val repositories =
      params.internalRepositories.drop(if (isSandboxConfig) 1 else 0) ++
        params.mainRepositories ++
        params.fallbackDependenciesRepositories

    val rules = params.strictOpt.map(s => Seq((s, RuleResolution.Fail))).getOrElse(Nil)

    val printOptionalMessage = verbosityLevel >= 0 && verbosityLevel <= 1

    def depsRepr(deps: Seq[(Configuration, Dependency)]) =
      deps.map { case (config, dep) =>
        s"${dep.module}:${dep.version}:${config.value}->${dep.configuration.value}"
      }.sorted.distinct

    val initialMessage =
      Seq(
        if (verbosityLevel >= 0)
          Seq(s"Updating ${params.projectName}" + (if (params.sbtClassifiers) " (sbt classifiers)" else ""))
        else
          Nil,
        if (verbosityLevel >= 2)
          depsRepr(params.dependencies).map(depRepr =>
            s"  $depRepr"
          )
        else
          Nil
      ).flatten.mkString("\n")

    if (verbosityLevel >= 2) {
      val repoReprs = repositories.map {
        case r: IvyRepository =>
          s"ivy:${r.pattern}"
        case _: InterProjectRepository =>
          "inter-project"
        case r: MavenRepository =>
          r.root
        case r =>
          // should not happen
          r.toString
      }

      log.info(
        "Repositories:\n" +
          repoReprs.map("  " + _).mkString("\n")
      )
    }

    if (verbosityLevel >= 2)
      log.info(initialMessage)

    Resolve()
      // re-using various caches from a resolution of a configuration we extend
      .withInitialResolution(startingResolutionOpt)
      .withDependencies(
        params.dependencies.collect {
          case (config, dep) if configs(config) =>
            dep
        }
      )
      .withRepositories(repositories)
      .withResolutionParams(
        params
          .params
          .addForceVersion((if (isSandboxConfig) Nil else params.interProjectDependencies.map(_.moduleVersion)): _*)
          .withForceScalaVersion(params.autoScalaLibOpt.nonEmpty)
          .withScalaVersionOpt(params.autoScalaLibOpt.map(_._2))
          .withTypelevel(params.params.typelevel)
          .withRules(rules)
      )
      .withCache(
        params
          .cache
          .withLogger(
            params.loggerOpt.getOrElse {
              RefreshLogger.create(
                if (RefreshLogger.defaultFallbackMode)
                  new FallbackRefreshDisplay()
                else
                  ProgressBarRefreshDisplay.create(
                    if (printOptionalMessage) log.info(initialMessage),
                    if (printOptionalMessage || verbosityLevel >= 2)
                      log.info(s"Resolved ${params.projectName} dependencies")
                  )
              )
            }
          )
      )
      .either() match {
        case Left(err) if params.missingOk => Right(err.resolution)
        case others => others
      }
  }

  def resolutions(
    params: ResolutionParams,
    verbosityLevel: Int,
    log: Logger
  ): Either[coursier.error.ResolutionError, Map[Configuration, Resolution]] = {

    // TODO Warn about possible duplicated modules from source repositories?

    if (verbosityLevel >= 2) {
      log.info("InterProjectRepository")
      for (p <- params.interProjectDependencies)
        log.info(s"  ${p.module}:${p.version}")
    }

    SbtCoursierCache.default.resolutionOpt(params.resolutionKey).map(Right(_)).getOrElse {
      // Let's update only one module at once, for a better output.
      // Downloads are already parallel, no need to parallelize further, anyway.
      val resOrError =
        Lock.lock.synchronized {
          var map = new mutable.HashMap[Configuration, Resolution]
          val either = params.orderedConfigs.foldLeft[Either[coursier.error.ResolutionError, Unit]](Right(())) {
            case (acc, (config, extends0)) =>
              for {
                _ <- acc
                initRes = {
                  val it = extends0.iterator.flatMap(map.get(_).iterator)
                  if (it.hasNext) Some(it.next())
                  else None
                }
                allExtends = params.allConfigExtends.getOrElse(config, Set.empty)
                res <- resolution(params, verbosityLevel, log, allExtends, initRes)
              } yield {
                map += config -> res
                ()
              }
          }
          val withSubResolutions = params.subConfigs.foldLeft(either) {
            case (acc, (config, parent)) =>
              for {
                _ <- acc
                initResOpt = map.get(parent)
                allExtends = params.allConfigExtends.getOrElse(config, Set.empty)
                res <- {
                  initResOpt match {
                    case None =>
                      val allExtendsWithParent = allExtends ++
                        params.allConfigExtends.getOrElse(parent, Set.empty)
                      resolution(params, verbosityLevel, log, allExtendsWithParent, None)
                    case Some(initRes) =>
                      val deps = params.dependencies.collect {
                        case (config, dep) if allExtends(config) =>
                          dep
                      }
                      Right(initRes.subset(deps))
                  }
                }
              } yield {
                map += config -> res
                ()
              }
          }
          either.map(_ => map.toMap)
        }
      for (res <- resOrError)
        SbtCoursierCache.default.putResolution(params.resolutionKey, res)
      resOrError
    }
  }

}
