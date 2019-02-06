package coursier.lmcoursier

import java.util.concurrent.ExecutorService

import coursier.cache.CacheLogger
import coursier.{Cache, Resolution}
import coursier.core._
import coursier.ivy.IvyRepository
import coursier.maven.MavenRepository
import coursier.util.{Print, Schedulable, Task}
import sbt.util.Logger

import scala.concurrent.ExecutionContext

object ResolutionRun {

  def resolution(
    params: ResolutionParams,
    verbosityLevel: Int,
    log: Logger,
    startRes: Resolution
  ): Either[ResolutionError, Resolution] = {

    // TODO Re-use the thread pool across resolutions / downloads?
    var pool: ExecutorService = null

    var resLogger: CacheLogger = null

    val printOptionalMessage = verbosityLevel >= 0 && verbosityLevel <= 1

    val resOrError: Either[ResolutionError, Resolution] = try {
      pool = Schedulable.fixedThreadPool(params.cacheParams.parallel)
      resLogger = params.createLogger()

      val fetchs = Cache.fetchs[Task](params.cacheParams.cacheLocation, params.cacheParams.cachePolicies, checksums = params.cacheParams.checksum, logger = Some(resLogger), pool = pool, ttl = params.cacheParams.ttl)

      val fetch = ResolutionProcess.fetch(
        params.repositories,
        fetchs.head, fetchs.tail: _*
      )

      def depsRepr(deps: Seq[(Configuration, Dependency)]) =
        deps.map { case (config, dep) =>
          s"${dep.module}:${dep.version}:${config.value}->${dep.configuration.value}"
        }.sorted.distinct

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

      if (verbosityLevel >= 2)
        log.info(initialMessage)

      resLogger.init(if (printOptionalMessage) log.info(initialMessage))

      startRes
        .process
        .run(fetch, params.params.maxIterations)
        .attempt
        .unsafeRun()(ExecutionContext.fromExecutorService(pool))
        .left
        .map(ex =>
          ResolutionError.UnknownException(ex)
        )
    } finally {
      if (pool != null)
        pool.shutdown()
      if (resLogger != null)
        if ((resLogger.stopDidPrintSomething() && printOptionalMessage) || verbosityLevel >= 2)
          log.info(s"Resolved ${params.projectName} dependencies")
    }

    resOrError.flatMap { res =>
      if (!res.isDone)
        Left(
          ResolutionError.MaximumIterationsReached
        )
      else if (res.conflicts.nonEmpty) {
        val projCache = res.projectCache.mapValues { case (_, p) => p }

        Left(
          ResolutionError.Conflicts(
            "Conflict(s) in dependency resolution:\n  " +
              Print.dependenciesUnknownConfigs(res.conflicts.toVector, projCache)
          )
        )
      } else if (res.errors.nonEmpty) {
        val internalRepositoriesLen = params.internalRepositories.length
        val errors =
          if (params.repositories.length > internalRepositoriesLen)
          // drop internal repository errors
            res.errors.map {
              case (dep, errs) =>
                dep -> errs.drop(internalRepositoriesLen)
            }
          else
            res.errors

        Left(
          ResolutionError.MetadataDownloadErrors(errors)
        )
      } else
        Right(res)
    }
  }

  def resolutions(
    params: ResolutionParams,
    verbosityLevel: Int,
    log: Logger
  ): Either[ResolutionError, Map[Set[Configuration], Resolution]] = {

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
          params.allStartRes.foldLeft[Either[ResolutionError, Map[Set[Configuration], Resolution]]](Right(Map())) {
            case (acc, (config, startRes)) =>
              for {
                m <- acc
                res <- resolution(params, verbosityLevel, log, startRes)
              } yield m + (config -> res)
          }
        }
      for (res <- resOrError)
        SbtCoursierCache.default.putResolution(params.resolutionKey, res)
      resOrError
    }
  }

}
