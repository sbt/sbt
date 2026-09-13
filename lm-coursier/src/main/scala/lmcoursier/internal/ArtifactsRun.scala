package lmcoursier.internal

import coursier.Artifacts
import coursier.cache.CacheLogger
import coursier.cache.loggers.{ FallbackRefreshDisplay, ProgressBarRefreshDisplay, RefreshLogger }
import coursier.core.Type
import sbt.util.Logger

// private[lmcoursier]
object ArtifactsRun:

  def apply(
      params: ArtifactsParams,
      verbosityLevel: Int,
      log: Logger
  ): Either[coursier.error.FetchError, Artifacts.Result] =

    val printOptionalMessage = verbosityLevel >= 0 && verbosityLevel <= 1

    val artifactInitialMessage =
      if verbosityLevel >= 0 then
        s"Fetching artifacts of ${params.projectName}" +
          (if params.sbtClassifiers then " (sbt classifiers)" else "")
      else ""

    val coursierLogger = params.loggerOpt.getOrElse {
      RefreshLogger.create(
        if RefreshLogger.defaultFallbackMode then new FallbackRefreshDisplay()
        else
          ProgressBarRefreshDisplay.create(
            if printOptionalMessage then log.info(artifactInitialMessage),
            if printOptionalMessage || verbosityLevel >= 2 then
              log.info(
                s"Fetched artifacts of ${params.projectName}" +
                  (if params.sbtClassifiers then " (sbt classifiers)" else "")
              )
          )
      )
    }

    Lock.maybeSynchronized(needsLock =
      Lock.progressBarActive(
        hasCustomLogger = params.loggerOpt.nonEmpty,
        fallbackMode = RefreshLogger.defaultFallbackMode
      )
    ) {
      result(params, coursierLogger)
    }
  end apply

  private def result(
      params: ArtifactsParams,
      coursierLogger: CacheLogger
  ): Either[coursier.error.FetchError, Artifacts.Result] =
    coursier
      .Artifacts()
      .withResolutions(params.resolutions)
      .withArtifactTypes(Set(Type.all))
      .withClassifiers(params.classifiers.getOrElse(Nil).toSet)
      .withClasspathOrder(params.classpathOrder)
      .addExtraArtifacts { l =>
        if params.includeSignatures then l.flatMap(_._3.extra.get("sig").toSeq)
        else Nil
      }
      .addTransformArtifacts { artifacts =>
        if params.missingOk then
          artifacts.map { (dependency, publication, artifact) =>
            (dependency, publication, artifact.withOptional(true))
          }
        else artifacts
      }
      .withCache(params.cache.withLogger(coursierLogger))
      .eitherResult()
end ArtifactsRun
