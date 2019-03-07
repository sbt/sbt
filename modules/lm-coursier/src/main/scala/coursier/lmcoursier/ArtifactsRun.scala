package coursier.lmcoursier

import java.io.File

import coursier.Artifact
import coursier.cache.loggers.{ProgressBarRefreshDisplay, RefreshLogger}
import coursier.core.Type
import coursier.util.Sync
import sbt.util.Logger

object ArtifactsRun {

  def artifacts(
    params: ArtifactsParams,
    verbosityLevel: Int,
    log: Logger
  ): Either[coursier.error.FetchError, Map[Artifact, File]] =
    // let's update only one module at once, for a better output
    // Downloads are already parallel, no need to parallelize further anyway
    Lock.lock.synchronized {

      val printOptionalMessage = verbosityLevel >= 0 && verbosityLevel <= 1

      val artifactInitialMessage =
        if (verbosityLevel >= 0)
          s"Fetching artifacts of ${params.projectName}" +
            (if (params.sbtClassifiers) " (sbt classifiers)" else "")
        else
          ""

      Sync.withFixedThreadPool(params.parallel) { pool =>

        coursier.Artifacts()
          .withResolutions(params.resolutions)
          .withArtifactTypes(Set(Type.all))
          .withClassifiers(params.classifiers.getOrElse(Nil).toSet)
          .transformArtifacts { l =>
            val l0 =
              if (params.includeSignatures)
                l.flatMap { a =>
                  val sigOpt = a.extra.get("sig")
                  Seq(a) ++ sigOpt.toSeq
                }
              else
                l
            l0.distinct // temporary, until we can use https://github.com/coursier/coursier/pull/1077 from here
          }
          .withCache(
            params
              .cache
              .withPool(pool)
              .withLogger(
                params.loggerOpt.getOrElse {
                  RefreshLogger.create(
                    ProgressBarRefreshDisplay.create(
                      if (printOptionalMessage) log.info(artifactInitialMessage),
                      if (printOptionalMessage || verbosityLevel >= 2)
                        log.info(
                          s"Fetched artifacts of ${params.projectName}" +
                            (if (params.sbtClassifiers) " (sbt classifiers)" else "")
                        )
                    )
                  )
                }
              )
          )
          .either()
          .map(_.toMap)
      }
    }

}
