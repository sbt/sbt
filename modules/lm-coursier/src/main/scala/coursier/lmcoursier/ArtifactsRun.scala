package coursier.lmcoursier

import java.io.File
import java.util.concurrent.ExecutorService

import coursier.cache.CacheLogger
import coursier.{Artifact, Cache, CachePolicy, FileError}
import coursier.util.{Schedulable, Task}
import sbt.util.Logger

import scala.concurrent.ExecutionContext

object ArtifactsRun {

  def artifacts(
    params: ArtifactsParams,
    verbosityLevel: Int,
    log: Logger
  ): Either[ResolutionError.UnknownDownloadException, Map[Artifact, Either[FileError, File]]] = {

    val allArtifacts0 = params.resolutions.flatMap(_.dependencyArtifacts(params.classifiers)).map(_._3)

    val allArtifacts =
      if (params.includeSignatures)
        allArtifacts0.flatMap { a =>
          val sigOpt = a.extra.get("sig")
          Seq(a) ++ sigOpt.toSeq
        }
      else
        allArtifacts0

    // let's update only one module at once, for a better output
    // Downloads are already parallel, no need to parallelize further anyway
    Lock.lock.synchronized {

      var pool: ExecutorService = null
      var artifactsLogger: CacheLogger = null

      val printOptionalMessage = verbosityLevel >= 0 && verbosityLevel <= 1

      val artifactFilesOrErrors = try {
        pool = Schedulable.fixedThreadPool(params.cacheParams.parallel)

        val artifactFileOrErrorTasks = allArtifacts.toVector.distinct.map { a =>
          Cache.file[Task](
            a,
            params.cacheParams.cacheLocation,
            params.cacheParams.cachePolicies,
            checksums = params.cacheParams.checksum,
            logger = Some(params.logger),
            pool = pool,
            ttl = params.cacheParams.ttl
          )
            .run
            .map((a, _))
        }

        val artifactInitialMessage =
          if (verbosityLevel >= 0)
            s"Fetching artifacts of ${params.projectName}" +
              (if (params.sbtClassifiers) " (sbt classifiers)" else "")
          else
            ""

        if (verbosityLevel >= 2)
          log.info(artifactInitialMessage)

        artifactsLogger = params.logger
        artifactsLogger.init(if (printOptionalMessage) log.info(artifactInitialMessage))

        Task.gather.gather(artifactFileOrErrorTasks).attempt.unsafeRun()(ExecutionContext.fromExecutorService(pool)) match {
          case Left(ex) =>
            Left(ResolutionError.UnknownDownloadException(ex))
          case Right(l) =>
            Right(l.toMap)
        }
      } finally {
        if (pool != null)
          pool.shutdown()
        if (artifactsLogger != null)
          if ((artifactsLogger.stopDidPrintSomething() && printOptionalMessage) || verbosityLevel >= 2)
            log.info(
              s"Fetched artifacts of ${params.projectName}" +
                (if (params.sbtClassifiers) " (sbt classifiers)" else "")
            )
      }

      artifactFilesOrErrors
    }
  }

}
