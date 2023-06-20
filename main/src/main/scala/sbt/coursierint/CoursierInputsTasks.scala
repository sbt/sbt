/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package coursierint

import java.net.URL
import sbt.librarymanagement._
import sbt.util.Logger
import sbt.Keys._
import lmcoursier.definitions.{
  Classifier => CClassifier,
  Configuration => CConfiguration,
  Dependency => CDependency,
  Extension => CExtension,
  Info => CInfo,
  Module => CModule,
  ModuleName => CModuleName,
  Organization => COrganization,
  Project => CProject,
  Publication => CPublication,
  Type => CType,
  Strict => CStrict,
}
import lmcoursier.credentials.DirectCredentials
import lmcoursier.{ FallbackDependency, FromSbt, Inputs }
import sbt.internal.librarymanagement.mavenint.SbtPomExtraProperties
import sbt.librarymanagement.ivy.{
  FileCredentials,
  Credentials,
  DirectCredentials => IvyDirectCredentials
}
import sbt.ScopeFilter.Make._
import scala.collection.JavaConverters._

object CoursierInputsTasks {
  private def coursierProject0(
      projId: ModuleID,
      dependencies: Seq[ModuleID],
      configurations: Seq[sbt.librarymanagement.Configuration],
      sv: String,
      sbv: String,
      auOpt: Option[URL],
      rnOpt: Option[URL],
      description: String,
      homepage: Option[URL],
      vsOpt: Option[String],
      log: Logger
  ): CProject = {

    val configMap = Inputs.configExtendsSeq(configurations).toMap

    val proj0 = FromSbt.project(
      projId,
      dependencies,
      configMap,
      sv,
      sbv
    )
    val proj1 = auOpt match {
      case Some(au) =>
        proj0.withProperties(proj0.properties :+ (SbtPomExtraProperties.POM_API_KEY -> au.toString))
      case _ => proj0
    }
    val proj2 = vsOpt match {
      case Some(vs) =>
        proj1.withProperties(proj1.properties :+ (SbtPomExtraProperties.VERSION_SCHEME_KEY -> vs))
      case _ => proj1
    }
    val proj3 = rnOpt match {
      case Some(rn) =>
        proj2.withProperties(
          proj2.properties :+ (SbtPomExtraProperties.POM_RELEASE_NOTES_KEY -> rn.toString)
        )
      case _ => proj2
    }
    proj3.withInfo(
      proj3.info.withDescription(description).withHomePage(homepage.fold("")(_.toString))
    )
  }

  def coursierProjectTask: Def.Initialize[sbt.Task[CProject]] =
    Def.task {
      coursierProject0(
        projectID.value,
        allDependencies.value,
        ivyConfigurations.value,
        scalaVersion.value,
        scalaBinaryVersion.value,
        apiURL.value,
        releaseNotesURL.value,
        description.value,
        homepage.value,
        versionScheme.value,
        streams.value.log
      )
    }

  private def moduleFromIvy(id: org.apache.ivy.core.module.id.ModuleRevisionId): CModule =
    CModule(
      COrganization(id.getOrganisation),
      CModuleName(id.getName),
      id.getExtraAttributes.asScala.map {
        case (k0, v0) => k0.asInstanceOf[String] -> v0.asInstanceOf[String]
      }.toMap
    )

  private def dependencyFromIvy(
      desc: org.apache.ivy.core.module.descriptor.DependencyDescriptor
  ): Seq[(CConfiguration, CDependency)] = {

    val id = desc.getDependencyRevisionId
    val module = moduleFromIvy(id)
    val exclusions = desc.getAllExcludeRules.map { rule =>
      // we're ignoring rule.getConfigurations and rule.getMatcher here
      val modId = rule.getId.getModuleId
      // we're ignoring modId.getAttributes here
      (COrganization(modId.getOrganisation), CModuleName(modId.getName))
    }.toSet

    val configurations = desc.getModuleConfigurations.toVector
      .flatMap(Inputs.ivyXmlMappings)

    def dependency(conf: CConfiguration, pub: CPublication) = CDependency(
      module,
      id.getRevision,
      conf,
      exclusions,
      pub,
      optional = false,
      desc.isTransitive
    )

    val publications: CConfiguration => CPublication = {

      val artifacts = desc.getAllDependencyArtifacts

      val m = artifacts.toVector.flatMap { art =>
        val pub =
          CPublication(art.getName, CType(art.getType), CExtension(art.getExt()), CClassifier(""))
        art.getConfigurations.map(CConfiguration(_)).toVector.map { conf =>
          conf -> pub
        }
      }.toMap

      c => m.getOrElse(c, CPublication("", CType(""), CExtension(""), CClassifier("")))
    }

    configurations.map {
      case (from, to) =>
        from -> dependency(to, publications(to))
    }
  }

  private[sbt] def coursierInterProjectDependenciesTask: Def.Initialize[sbt.Task[Seq[CProject]]] =
    Def.taskDyn {
      val state = sbt.Keys.state.value
      val projectRef = sbt.Keys.thisProjectRef.value
      val projectRefs = Project.transitiveInterDependencies(state, projectRef)
      Def.task {
        csrProject.all(ScopeFilter(inProjects(projectRefs :+ projectRef: _*))).value
      }
    }

  private[sbt] def coursierExtraProjectsTask: Def.Initialize[sbt.Task[Seq[CProject]]] = {
    Def.task {
      val projects = csrInterProjectDependencies.value
      val projectModules = projects.map(_.module).toSet

      // this includes org.scala-sbt:global-plugins referenced from meta-builds in particular
      sbt.Keys.projectDescriptors.value
        .map {
          case (k, v) =>
            moduleFromIvy(k) -> v
        }
        .filter {
          case (module, _) =>
            !projectModules(module)
        }
        .toVector
        .map {
          case (module, v) =>
            val configurations = v.getConfigurations.map { c =>
              CConfiguration(c.getName) -> c.getExtends.map(CConfiguration(_)).toSeq
            }.toMap
            val deps = v.getDependencies.flatMap(dependencyFromIvy)
            CProject(
              module,
              v.getModuleRevisionId.getRevision,
              deps,
              configurations,
              Nil,
              None,
              Nil,
              CInfo("", "", Nil, Nil, None)
            )
        }
    }
  }

  private[sbt] def coursierFallbackDependenciesTask
      : Def.Initialize[sbt.Task[Seq[FallbackDependency]]] =
    Def.taskDyn {
      val s = state.value
      val projectRef = thisProjectRef.value
      val projects = Project.transitiveInterDependencies(s, projectRef)

      Def.task {
        val allDeps =
          allDependencies.all(ScopeFilter(inProjects(projectRef +: projects: _*))).value.flatten

        FromSbt.fallbackDependencies(
          allDeps,
          scalaVersion.value,
          scalaBinaryVersion.value
        )
      }
    }

  val credentialsTask = Def.task {
    val log = streams.value.log
    val creds = sbt.Keys.allCredentials.value
      .flatMap {
        case dc: IvyDirectCredentials => List(dc)
        case fc: FileCredentials =>
          Credentials.loadCredentials(fc.path) match {
            case Left(err) =>
              log.warn(s"$err, ignoring it")
              Nil
            case Right(dc) => List(dc)
          }
      }
      .map { c =>
        DirectCredentials()
          .withHost(c.host)
          .withUsername(c.userName)
          .withPassword(c.passwd)
          .withRealm(Option(c.realm).filter(_.nonEmpty))
          .withHttpsOnly(false)
          .withMatchHost(true)
      }
    creds ++ csrExtraCredentials.value
  }

  val strictTask = Def.task {
    val cm = conflictManager.value
    val log = streams.value.log

    cm.name match {
      case ConflictManager.latestRevision.name =>
        None
      case ConflictManager.strict.name =>
        val strict = CStrict()
          .withInclude(Set((cm.organization, cm.module)))
        Some(strict)
      case other =>
        log.warn(s"Unsupported conflict manager $other")
        None
    }
  }
}
