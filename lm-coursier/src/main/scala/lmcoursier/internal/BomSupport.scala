/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package lmcoursier.internal

import java.io.File

import coursier.{ Artifacts, Resolve, Resolution }
import coursier.core.{ Classifier, Type }
import coursier.util.Task
import lmcoursier.definitions.{ Module as LmModule, ModuleName, Organization }
import lmcoursier.{ FromSbt, credentials }
import sbt.util.Logger
import sbt.librarymanagement.ModuleID
import coursier.cache.FileCache

/**
 * Resolves Maven BOM (Bill of Materials) POMs and extracts their dependencyManagement
 * section as force versions for use in the main resolution.
 */
object BomSupport {

  /**
   * For each BOM module in `bomModules`, resolves its POM, reads dependencyManagement,
   * and returns (lmcoursier Module, version) pairs to be used as force versions.
   */
  def bomForceVersions(
      resolvers: Seq[sbt.librarymanagement.Resolver],
      bomModules: Seq[ModuleID],
      cache: File,
      log: Logger,
      scalaVersion: String,
      scalaBinaryVersion: String,
      ivyProperties: Map[String, String],
      credentialsSeq: Seq[credentials.Credentials],
  ): Vector[(LmModule, String)] = {
    if (bomModules.isEmpty) return Vector.empty

    val authByRepoId = Map.empty[String, coursier.core.Authentication]
    val classLoaders = Seq.empty[ClassLoader]

    val repositories = resolvers.flatMap { resolver =>
      Resolvers.repository(
        resolver,
        ivyProperties,
        log,
        None,
        classLoaders,
      )
    }

    if (repositories.isEmpty) {
      log.warn("No repositories available for BOM resolution")
      return Vector.empty
    }

    val cache0 = FileCache()
      .withLocation(cache)
      .withCredentials(credentialsSeq.map(lmcoursier.definitions.ToCoursier.credentials))

    val result = Vector.newBuilder[(LmModule, String)]

    for (bomModule <- bomModules) {
      val (mod, ver) = FromSbt.moduleVersion(bomModule, scalaVersion, scalaBinaryVersion)
      val bomDep = coursier.core
        .Dependency(
          lmcoursier.definitions.ToCoursier.module(mod),
          ver,
        )
        .withAttributes(coursier.core.Attributes(coursier.core.Type("pom"), Classifier.empty))

      val resolveTask = Resolve()
        .withDependencies(Seq(bomDep))
        .withRepositories(repositories)
        .withCache(cache0)

      val resolutionEither = resolveTask.io.attempt.unsafeRun()(using cache0.ec)

      resolutionEither match {
        case Right(resolution: Resolution) =>
          val bomKey = (lmcoursier.definitions.ToCoursier.module(mod), ver)
          val fromProject = resolution.projectCache.get(bomKey).flatMap { case (_, project) =>
            val dm = project.dependencyManagement
            if (dm.nonEmpty) Some(dm.map { case (_, dep) =>
              (
                LmModule(
                  Organization(dep.module.organization.value),
                  ModuleName(dep.module.name.value),
                  dep.module.attributes,
                ),
                dep.version
              )
            }.toVector)
            else None
          }
          fromProject match {
            case Some(entries) =>
              entries.foreach(result += _)
              log.debug(
                s"BOM ${bomModule.organization}:${bomModule.name}:${bomModule.revision} contributed ${entries.size} managed dependencies"
              )
            case None =>
              // Coursier often does not populate dependencyManagement for BOM POMs (coursier#1390). Fallback: fetch POM and parse.
              fallbackParseBomPom(resolution, bomDep, cache0, log, bomModule).foreach(result += _)
          }
        case Left(err) =>
          log.warn(
            s"Failed to resolve BOM ${bomModule.organization}:${bomModule.name}:${bomModule.revision}: $err"
          )
      }
    }

    result.result()
  }

  /**
   * When Coursier does not populate Project.dependencyManagement (e.g. BOM POMs, coursier#1390),
   * fetch the POM artifact and parse dependencyManagement with PomParser.
   */
  private def fallbackParseBomPom(
      resolution: Resolution,
      bomDep: coursier.core.Dependency,
      cache0: FileCache[Task],
      log: Logger,
      bomModule: ModuleID,
  ): Vector[(LmModule, String)] = {
    val artifactsEither = Artifacts()
      .withResolutions(Seq(resolution))
      .withArtifactTypes(Set(Type.all))
      .withCache(cache0)
      .eitherResult()

    artifactsEither match {
      case Right(artResult) =>
        val detailed = artResult.fullDetailedArtifacts
        val pomFileOpt = detailed.collectFirst {
          case (dep, _, _, Some(f))
              if dep.module == bomDep.module && dep.version == bomDep.version =>
            f
        }
        pomFileOpt match {
          case Some(pomFile) =>
            try {
              val entries =
                PomParser.dependencyManagement(pomFile).map { case (groupId, artifactId, version) =>
                  (LmModule(Organization(groupId), ModuleName(artifactId), Map.empty), version)
                }
              if (entries.nonEmpty) {
                log.debug(
                  s"BOM ${bomModule.organization}:${bomModule.name}:${bomModule.revision} (POM fallback) contributed ${entries.size} managed dependencies"
                )
              }
              entries
            } catch {
              case e: Exception =>
                log.warn(
                  s"Failed to parse BOM POM ${bomModule.organization}:${bomModule.name}:${bomModule.revision}: $e"
                )
                Vector.empty
            }
          case None =>
            log.warn(
              s"BOM ${bomModule.organization}:${bomModule.name}:${bomModule.revision} POM artifact not found in fetch result"
            )
            Vector.empty
        }
      case Left(err) =>
        log.warn(
          s"Failed to fetch BOM POM ${bomModule.organization}:${bomModule.name}:${bomModule.revision}: $err"
        )
        Vector.empty
    }
  }
}
