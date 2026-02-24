/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.librarymanagement

import java.io.File
import lmcoursier.internal.*
import sbt.librarymanagement.*
import sbt.util.Logger

object DependencyLockManager:

  def read(lockFile: File, log: Logger): Option[LockFileData] =
    LockFile.read(lockFile) match
      case Right(data) => Some(data)
      case Left(err) =>
        if lockFile.exists() then log.warn(s"Failed to read lock file: $err")
        None

  def write(lockFile: File, lock: LockFileData, log: Logger): Unit =
    LockFile.write(lockFile, lock) match
      case Right(_) =>
        log.info(s"Wrote dependency lock file to ${lockFile.getAbsolutePath}")
      case Left(err) =>
        log.error(s"Failed to write lock file: $err")
        throw new RuntimeException(err)

  def validate(
      lockFile: File,
      currentBuildClock: String,
      log: Logger
  ): Option[LockFileData] =
    read(lockFile, log).filter { lock =>
      val isValid = lock.buildClock == currentBuildClock
      if !isValid then
        log.debug(
          s"Lock file is stale (buildClock mismatch: ${lock.buildClock} != $currentBuildClock)"
        )
      isValid
    }

  private def artifactUrlForLock(
      rawUrl: String,
      cacheDir: Option[File],
      moduleDesc: String
  ): String =
    if !rawUrl.startsWith(CacheUrlConversion.FileUrlPrefix) then rawUrl
    else
      cacheDir match
        case None =>
          throw new RuntimeException(
            s"Cannot create dependency lock file: artifact has file URL (e.g. local cache path). " +
              s"Lock files must use portable repository URLs. Module: $moduleDesc. " +
              s"Run 'update' first or ensure dependencies are resolved from remote repositories."
          )
        case Some(dir) =>
          val converted = CacheUrlConversion.cacheFileToOriginalUrl(rawUrl, dir)
          if !CacheUrlConversion.isPortableUrl(converted) then
            throw new RuntimeException(
              s"Cannot create dependency lock file: artifact path is not under Coursier cache. " +
                s"Module: $moduleDesc. URL: $rawUrl"
            )
          converted

  def createFromUpdateReport(
      projectId: String,
      report: UpdateReport,
      sbtVersion: String,
      scalaVersion: Option[String],
      buildClock: String,
      log: Logger,
      cacheDir: Option[File] = None
  ): LockFileData =
    val configurations = report.configurations.map { configReport =>
      val deps = configReport.modules.map { moduleReport =>
        val artifacts = moduleReport.artifacts.map { case (artifact, file) =>
          val url = artifactUrlForLock(file.toURI.toString, cacheDir, moduleReport.module.toString)
          ArtifactLock(
            url = url,
            classifier = artifact.classifier,
            extension = artifact.extension,
            tpe = artifact.`type`
          )
        }

        DependencyLock(
          organization = moduleReport.module.organization,
          name = moduleReport.module.name,
          version = moduleReport.module.revision,
          configuration = configReport.configuration.name,
          classifier = None,
          tpe = "jar",
          transitives = Vector.empty,
          artifacts = artifacts
        )
      }

      ConfigurationLock(
        name = configReport.configuration.name,
        dependencies = deps
      )
    }

    val metadata = LockFileMetadata(
      sbtVersion = sbtVersion,
      scalaVersion = scalaVersion
    )

    LockFileData(
      version = LockFileConstants.currentVersion,
      buildClock = buildClock,
      configurations = configurations,
      metadata = metadata
    )

  def getLockedVersions(
      lock: LockFileData
  ): Map[(String, String), String] =
    lock.configurations.flatMap { config =>
      config.dependencies.map { dep =>
        (dep.organization, dep.name) -> dep.version
      }
    }.toMap
