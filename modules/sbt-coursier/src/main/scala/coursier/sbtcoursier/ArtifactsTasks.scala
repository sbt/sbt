package coursier.sbtcoursier

import java.io.File

import coursier.{Artifact, FileError}
import coursier.core._
import coursier.lmcoursier._
import coursier.params.CacheParams
import coursier.sbtcoursier.Keys._
import coursier.sbtcoursiershared.SbtCoursierShared.autoImport.{coursierCache, coursierCreateLogger}
import sbt.Def
import sbt.Keys._

object ArtifactsTasks {

  def artifactsTask(
    withClassifiers: Boolean,
    sbtClassifiers: Boolean = false,
    ignoreArtifactErrors: Boolean = false,
    includeSignatures: Boolean = false
  ): Def.Initialize[sbt.Task[Map[Artifact, Either[FileError, File]]]] = {

    val resTask: sbt.Def.Initialize[sbt.Task[Seq[Resolution]]] =
      if (withClassifiers && sbtClassifiers)
        Def.task(Seq(coursierSbtClassifiersResolution.value))
      else
        Def.task(coursierResolutions.value.values.toVector)

    val classifiersTask: sbt.Def.Initialize[sbt.Task[Option[Seq[Classifier]]]] =
      if (withClassifiers) {
        if (sbtClassifiers)
          Def.task(Some(coursierSbtClassifiersModule.value.classifiers.map(Classifier(_))))
        else
          Def.task(Some(transitiveClassifiers.value.map(Classifier(_))))
      } else
        Def.task(None)

    Def.task {

      val projectName = thisProjectRef.value.project

      val parallelDownloads = coursierParallelDownloads.value
      val artifactsChecksums = coursierArtifactsChecksums.value
      val cachePolicies = coursierCachePolicies.value
      val ttl = coursierTtl.value
      val cache = coursierCache.value
      val createLogger = coursierCreateLogger.value

      val log = streams.value.log

      val verbosityLevel = coursierVerbosity.value

      val classifiers = classifiersTask.value
      val res = resTask.value

      val params = ArtifactsParams(
        classifiers = classifiers,
        res = res,
        includeSignatures = includeSignatures,
        createLogger = createLogger.create,
        projectName = projectName,
        sbtClassifiers = sbtClassifiers,
        cacheParams = CacheParams(
          parallel = parallelDownloads,
          cacheLocation = cache,
          checksum = artifactsChecksums,
          ttl = ttl,
          cachePolicies = cachePolicies
        )
      )

      val resOrError = ArtifactsRun.artifacts(
        params,
        verbosityLevel,
        log
      )

      resOrError match {
        case Left(err) =>
          err.throwException()
        case Right(res) =>
          res
      }
    }
  }

}
