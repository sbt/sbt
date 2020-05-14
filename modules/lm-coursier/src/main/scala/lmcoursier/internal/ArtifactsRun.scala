package lmcoursier.internal

import java.io.File

import coursier.cache.internal.ThreadUtil
import coursier.cache.loggers.{FallbackRefreshDisplay, ProgressBarRefreshDisplay, RefreshLogger}
import coursier.core.Type
import coursier.util.Artifact
import sbt.util.Logger
import coursier.core.Dependency
import coursier.core.Publication

// private[coursier]
object ArtifactsRun {

  def artifacts(
    params: ArtifactsParams,
    verbosityLevel: Int,
    log: Logger
  ): Either[coursier.error.FetchError, Map[Artifact, File]] =
    artifactsResult(params, verbosityLevel, log).map(_.collect { case (_, _, a, Some(f)) => (a, f) }.toMap)

  def artifactsResult(
    params: ArtifactsParams,
    verbosityLevel: Int,
    log: Logger
  ): Either[coursier.error.FetchError, Seq[(Dependency, Publication, Artifact, Option[File])]] =
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

      ThreadUtil.withFixedThreadPool(params.parallel) { pool =>

        coursier.Artifacts()
          .withResolutions(params.resolutions)
          .withArtifactTypes(Set(Type.all))
          .withClassifiers(params.classifiers.getOrElse(Nil).toSet)
          .withClasspathOrder(params.classpathOrder)
          .addExtraArtifacts { l =>
            if (params.includeSignatures)
              l.flatMap(_._3.extra.get("sig").toSeq)
            else
              Nil
          }
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
          .eitherResult()
          .map(_.fullDetailedArtifacts) // FIXME Misses extraArtifacts, that we don't use for now though
      }
    }

}
