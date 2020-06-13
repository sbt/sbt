package lmcoursier.internal

import java.io.File

import coursier.cache.CacheLogger
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
    artifactsResult(params, verbosityLevel, log)
      .map(_.collect { case (_, _, a, Some(f)) => (a, f) }.toMap)

  def artifactsResult(
    params: ArtifactsParams,
    verbosityLevel: Int,
    log: Logger
  ): Either[coursier.error.FetchError, Seq[(Dependency, Publication, Artifact, Option[File])]] = {

    val printOptionalMessage = verbosityLevel >= 0 && verbosityLevel <= 1

    val artifactInitialMessage =
      if (verbosityLevel >= 0)
        s"Fetching artifacts of ${params.projectName}" +
          (if (params.sbtClassifiers) " (sbt classifiers)" else "")
      else
        ""

    // Ensuring only one resolution / artifact fetching runs at a time when the logger
    // may rely on progress bars, as two progress bar loggers can't display stuff at the
    // same time.
    val needsLock = params.loggerOpt.nonEmpty || !RefreshLogger.defaultFallbackMode

    val coursierLogger = params.loggerOpt.getOrElse {
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

    if (needsLock)
      Lock.lock.synchronized {
        artifactsResultNoLock(params, coursierLogger)
      }
    else
      artifactsResultNoLock(params, coursierLogger)
  }

  private def artifactsResultNoLock(
    params: ArtifactsParams,
    coursierLogger: CacheLogger
  ): Either[coursier.error.FetchError, Seq[(Dependency, Publication, Artifact, Option[File])]] =
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
      .addTransformArtifacts { artifacts =>
        if (params.missingOk)
          artifacts.map {
            case (dependency, publication, artifact) =>
              (dependency, publication, artifact.withOptional(true))
          }
        else
          artifacts
      }
      .withCache(params.cache.withLogger(coursierLogger))
      .eitherResult()
      .map(_.fullDetailedArtifacts) // FIXME Misses extraArtifacts, that we don't use for now though

}
