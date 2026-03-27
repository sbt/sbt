/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package coursierint

import java.net.URI
import sbt.librarymanagement.{ Credentials as IvyCredentials, * }
import sbt.util.Logger
import sbt.Keys.*
import lmcoursier.definitions.{ Project as CProject, Strict as CStrict }
import lmcoursier.credentials.DirectCredentials
import lmcoursier.{ FallbackDependency, FromSbt, Inputs }
import sbt.internal.librarymanagement.mavenint.SbtPomExtraProperties
import sbt.ProjectExtra.transitiveInterDependencies
import sbt.ScopeFilter.Make.*

object CoursierInputsTasks {
  private def coursierProject0(
      projId: ModuleID,
      dependencies: Seq[ModuleID],
      configurations: Seq[sbt.librarymanagement.Configuration],
      sv: String,
      sbv: String,
      auOpt: Option[URI],
      rnOpt: Option[URI],
      description: String,
      homepage: Option[URI],
      vsOpt: Option[String],
      projectPlatform: Option[String],
      log: Logger
  ): CProject = {

    val configMap = Inputs.configExtendsSeq(configurations).toMap
    val privConfigs = Inputs.privateConfigs(configurations)

    val proj0 = FromSbt
      .project(
        projId,
        dependencies,
        configMap,
        sv,
        sbv,
        projectPlatform,
      )
      .withPrivateConfigs(privConfigs)
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
        scalaModuleInfo.value.flatMap(_.platform),
        streams.value.log
      )
    }

  private[sbt] def coursierInterProjectDependenciesTask: Def.Initialize[sbt.Task[Seq[CProject]]] =
    (Def
      .task {
        val state = sbt.Keys.state.value
        val projectRef = sbt.Keys.thisProjectRef.value
        val projectRefs = Project.transitiveInterDependencies(state, projectRef)
        ScopeFilter(inProjects(projectRefs :+ projectRef*))
      })
      .flatMapTask { case filter =>
        Def.task {
          csrProject.all(filter).value
        }
      }

  private[sbt] def coursierExtraProjectsTask: Def.Initialize[sbt.Task[Seq[CProject]]] = {
    Def.task {
      // Coursier handles inter-project dependencies natively via csrInterProjectDependencies.
      // When IvyDependencyPlugin is enabled, it overrides csrExtraProjects with Ivy descriptors.
      Seq.empty[CProject]
    }
  }

  private[sbt] def coursierFallbackDependenciesTask
      : Def.Initialize[sbt.Task[Seq[FallbackDependency]]] =
    (Def
      .task {
        val s = state.value
        val projectRef = thisProjectRef.value
        val projects = Project.transitiveInterDependencies(s, projectRef)
        ScopeFilter(inProjects(projectRef +: projects*))
      })
      .flatMapTask { case filter =>
        Def.task {
          val allDeps =
            allDependencies.all(filter).value.flatten

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
        case dc: IvyCredentials.DirectCredentials => List(dc)
        case fc: IvyCredentials.FileCredentials =>
          sbt.librarymanagement.CredentialUtils.loadCredentials(fc.path) match {
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
