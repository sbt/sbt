package coursier.sbtcoursier

import java.io.File
import java.util.concurrent.ExecutorService

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

    val allArtifacts0 = params.res.flatMap(_.dependencyArtifacts(params.classifiers)).map(_._3)

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
      var artifactsLogger: Cache.Logger = null

      val printOptionalMessage = verbosityLevel >= 0 && verbosityLevel <= 1

      val artifactFilesOrErrors = try {
        pool = Schedulable.fixedThreadPool(params.parallelDownloads)
        artifactsLogger = params.createLogger()

        val artifactFileOrErrorTasks = allArtifacts.toVector.distinct.map { a =>
          def f(p: CachePolicy) =
            Cache.file[Task](
              a,
              params.cache,
              p,
              checksums = params.artifactsChecksums,
              logger = Some(artifactsLogger),
              pool = pool,
              ttl = params.ttl
            )

          params.cachePolicies.tail
            .foldLeft(f(params.cachePolicies.head))(_ orElse f(_))
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
