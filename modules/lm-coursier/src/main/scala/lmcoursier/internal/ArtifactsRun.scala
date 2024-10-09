package lmcoursier.internal

import coursier.Artifacts
import coursier.cache.CacheLogger
import coursier.cache.loggers.{FallbackRefreshDisplay, ProgressBarRefreshDisplay, RefreshLogger}
import coursier.core.Type
import sbt.util.Logger

// private[lmcoursier]
object ArtifactsRun {

  def apply(
    params: ArtifactsParams,
    verbosityLevel: Int,
    log: Logger
  ): Either[coursier.error.FetchError, Artifacts.Result] = {

    val printOptionalMessage = verbosityLevel >= 0 && verbosityLevel <= 1

    val artifactInitialMessage =
      if (verbosityLevel >= 0)
        s"Fetching artifacts of ${params.projectName}" +
          (if (params.sbtClassifiers) " (sbt classifiers)" else "")
      else
        ""

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

    Lock.maybeSynchronized(needsLock = params.loggerOpt.nonEmpty || !RefreshLogger.defaultFallbackMode){
      result(params, coursierLogger)
    }
  }

  private def result(
    params: ArtifactsParams,
    coursierLogger: CacheLogger
  ): Either[coursier.error.FetchError, Artifacts.Result] =
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

}
