package lmcoursier.internal

import coursier.cache.FileCache
import coursier.core.{ Classifier, Dependency, Extension, Publication, Type }
import coursier.util.Artifact
import sbt.util.Logger

import java.io.File
import scala.concurrent.{ Await, ExecutionContext }
import scala.concurrent.duration.Duration

object LockedArtifactsRun {

  def fetchFromLockFile(
      lockFileData: LockFileData,
      cache: FileCache[coursier.util.Task],
      verbosityLevel: Int,
      log: Logger
  ): Either[String, Seq[(Dependency, Publication, Artifact, Option[File])]] = {
    implicit val ec: ExecutionContext = cache.ec

    if (verbosityLevel >= 1) {
      log.info("Fetching artifacts from lock file")
    }

    val artifactsToFetch = for {
      configLock <- lockFileData.configurations
      depLock <- configLock.dependencies
      artLock <- depLock.artifacts
    } yield {
      val module = coursier.Module(
        coursier.Organization(depLock.organization),
        coursier.ModuleName(depLock.name),
        Map.empty[String, String]
      )

      val dependency = Dependency(
        module = module,
        version = depLock.version
      )

      val classifier = Classifier(artLock.classifier.getOrElse(""))
      val extension = Extension(artLock.extension)
      val tpe = Type(artLock.tpe)

      val publication = Publication(
        name = depLock.name,
        `type` = tpe,
        ext = extension,
        classifier = classifier
      )

      val artifact = Artifact(
        url = artLock.url,
        checksumUrls = Map.empty,
        extra = Map.empty,
        changing = false,
        optional = false,
        authentication = None
      )

      (dependency, publication, artifact)
    }

    val fetchTasks = artifactsToFetch.map { case (dep, pub, art) =>
      cache.file(art).run.map { result =>
        result match {
          case Left(err) =>
            if (verbosityLevel >= 2) {
              log.debug(s"Failed to fetch ${art.url}: ${err.describe}")
            }
            (dep, pub, art, None: Option[File])
          case Right(file) =>
            (dep, pub, art, Some(file))
        }
      }
    }

    try {
      val results = fetchTasks.map { task =>
        Await.result(task.future(), Duration.Inf)
      }

      val failures = results.filter(_._4.isEmpty)
      if (failures.nonEmpty && verbosityLevel >= 1) {
        log.warn(s"Failed to fetch ${failures.size} artifacts from lock file")
      }

      Right(results)
    } catch {
      case ex: Exception =>
        Left(s"Failed to fetch artifacts: ${ex.getMessage}")
    }
  }
}
