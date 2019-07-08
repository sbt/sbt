package lmcoursier.internal

import coursier.cache.internal.ThreadUtil
import coursier.{Resolution, Resolve}
import coursier.cache.loggers.{FallbackRefreshDisplay, ProgressBarRefreshDisplay, RefreshLogger}
import coursier.core._
import coursier.ivy.IvyRepository
import coursier.maven.MavenRepository
import coursier.params.rule.RuleResolution
import sbt.util.Logger

// private[coursier]
object ResolutionRun {

  private def resolution(
    params: ResolutionParams,
    verbosityLevel: Int,
    log: Logger,
    configs: Set[Configuration]
  ): Either[coursier.error.ResolutionError, Resolution] = {

    val rules = params.strictOpt.map(s => Seq((s, RuleResolution.Fail))).getOrElse(Nil)

    val isCompileConfig =
      configs(Configuration.compile) || configs(Configuration("scala-tool"))

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
      val repoReprs = params.repositories.map {
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

    ThreadUtil.withFixedThreadPool(params.parallel) { pool =>

      Resolve()
        .withDependencies(
          params.dependencies.collect {
            case (config, dep) if configs(config) =>
              dep
          }
        )
        .withRepositories(params.repositories)
        .withResolutionParams(
          params
            .params
            .addForceVersion(params.interProjectDependencies.map(_.moduleVersion): _*)
            .withForceScalaVersion(isCompileConfig && params.autoScalaLibOpt.nonEmpty)
            .withScalaVersion(params.autoScalaLibOpt.map(_._2))
            .withTypelevel(params.params.typelevel && isCompileConfig)
            .withRules(rules)
        )
        .withCache(
          params
            .cache
            .withPool(pool)
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
        .either()
    }
  }

  def resolutions(
    params: ResolutionParams,
    verbosityLevel: Int,
    log: Logger
  ): Either[coursier.error.ResolutionError, Map[Set[Configuration], Resolution]] = {

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
          params.configGraphs.foldLeft[Either[coursier.error.ResolutionError, Map[Set[Configuration], Resolution]]](Right(Map())) {
            case (acc, config) =>
              for {
                m <- acc
                res <- resolution(params, verbosityLevel, log, config)
              } yield m + (config -> res)
          }
        }
      for (res <- resOrError)
        SbtCoursierCache.default.putResolution(params.resolutionKey, res)
      resOrError
    }
  }

}
