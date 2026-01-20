/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.librarymanagement

import java.io.File
import sbt.io.IO
import sbt.librarymanagement.*
import sbt.util.Logger
import sjsonnew.support.scalajson.unsafe.{ Converter, CompactPrinter, Parser }

object DependencyLockManager:
  import DependencyLockCodec.given

  def read(lockFile: File, log: Logger): Option[DependencyLockFile] =
    if lockFile.exists() then
      try
        val content = IO.read(lockFile)
        val json = Parser.parseUnsafe(content)
        Some(Converter.fromJsonUnsafe[DependencyLockFile](json))
      catch
        case e: Exception =>
          log.warn(s"Failed to read lock file ${lockFile.getAbsolutePath}: ${e.getMessage}")
          None
    else None

  def write(lockFile: File, lock: DependencyLockFile, log: Logger): Unit =
    try
      val json = Converter.toJsonUnsafe(lock)
      val content = CompactPrinter(json)
      IO.write(lockFile, formatJson(content))
      log.info(s"Wrote dependency lock file to ${lockFile.getAbsolutePath}")
    catch
      case e: Exception =>
        log.error(s"Failed to write lock file ${lockFile.getAbsolutePath}: ${e.getMessage}")
        throw e

  def validate(
      lockFile: File,
      currentBuildClock: String,
      log: Logger
  ): Option[DependencyLockFile] =
    read(lockFile, log).filter { lock =>
      val isValid = lock.isValid(currentBuildClock)
      if !isValid then
        log.debug(
          s"Lock file is stale (buildClock mismatch: ${lock.buildClock} != $currentBuildClock)"
        )
      isValid
    }

  def createFromUpdateReport(
      projectId: String,
      report: UpdateReport,
      sbtVersion: String,
      buildClock: String,
      resolvers: Seq[Resolver],
      log: Logger
  ): DependencyLockFile =
    val lockedResolvers = resolvers.collect { case m: MavenRepository =>
      LockedResolver(m.name, m.root)
    }.toVector

    val lockedDeps = for
      configReport <- report.configurations
      moduleReport <- configReport.modules
    yield
      val artifacts = moduleReport.artifacts.map { case (artifact, file) =>
        LockedArtifact(
          classifier = artifact.classifier,
          extension = artifact.extension,
          url = file.toURI.toString,
          sha256 = None
        )
      }.toVector

      LockedDependency(
        organization = moduleReport.module.organization,
        name = moduleReport.module.name,
        version = moduleReport.module.revision,
        configurations = Some(configReport.configuration.name),
        artifacts = artifacts
      )

    val projectLock = ProjectLock(
      projectId = projectId,
      dependencies = lockedDeps.toVector
    )

    DependencyLockFile(
      lockVersion = DependencyLockFile.CurrentLockVersion,
      sbtVersion = sbtVersion,
      buildClock = buildClock,
      resolvers = lockedResolvers,
      projects = Vector(projectLock)
    )

  def mergeProjectLock(
      existing: DependencyLockFile,
      projectLock: ProjectLock
  ): DependencyLockFile =
    val updatedProjects =
      existing.projects.filterNot(_.projectId == projectLock.projectId) :+ projectLock
    existing.copy(projects = updatedProjects)

  def getLockedVersions(
      lock: DependencyLockFile,
      projectId: String
  ): Map[(String, String), String] =
    lock.projects
      .find(_.projectId == projectId)
      .map { projectLock =>
        projectLock.dependencies
          .map(dep => (dep.organization, dep.name) -> dep.version)
          .toMap
      }
      .getOrElse(Map.empty)

  private def formatJson(compact: String): String =
    import sjsonnew.support.scalajson.unsafe.{ Parser as JsonParser, PrettyPrinter }
    try
      val json = JsonParser.parseUnsafe(compact)
      PrettyPrinter(json)
    catch case _: Exception => compact
