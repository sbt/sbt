package coursier.sbtcoursiershared

import lmcoursier.definitions.{Attributes, Classifier, Configuration, Dependency, Extension, Info, Module, ModuleName, Organization, Project, Publication, Strict, Type}
import lmcoursier.{FallbackDependency, FromSbt, Inputs}
import coursier.sbtcoursiershared.SbtCoursierShared.autoImport._
import coursier.sbtcoursiershared.Structure._
import lmcoursier.credentials.DirectCredentials
import sbt.{Def, SettingKey}
import sbt.Keys._
import sbt.librarymanagement.{ConflictManager, InclExclRule, ModuleID}
import sbt.util.Logger

import scala.collection.JavaConverters._
import scala.language.reflectiveCalls

object InputsTasks {

  lazy val actualExcludeDependencies =
    try {
      sbt.Keys
        .asInstanceOf[{ def allExcludeDependencies: SettingKey[scala.Seq[InclExclRule]] }]
        .allExcludeDependencies
    } catch {
      case _: NoSuchMethodException =>
        excludeDependencies
    }

  private def coursierProject0(
    projId: ModuleID,
    dependencies: Seq[ModuleID],
    configurations: Seq[sbt.librarymanagement.Configuration],
    sv: String,
    sbv: String,
    log: Logger
  ): Project = {

    val configMap = Inputs.configExtendsSeq(configurations).toMap

    FromSbt.project(
      projId,
      dependencies,
      configMap,
      sv,
      sbv
    )
  }

  private[sbtcoursiershared] def coursierProjectTask: Def.Initialize[sbt.Task[Project]] =
    Def.taskDyn {

      val state = sbt.Keys.state.value
      val projectRef = sbt.Keys.thisProjectRef.value

      val allDependenciesTask = allDependencies.in(projectRef).get(state)

      Def.task {
        coursierProject0(
          projectID.in(projectRef).get(state),
          allDependenciesTask.value,
          // should projectID.configurations be used instead?
          ivyConfigurations.in(projectRef).get(state),
          scalaVersion.in(projectRef).get(state),
          scalaBinaryVersion.in(projectRef).get(state),
          state.log
        )
      }
    }

  private def moduleFromIvy(id: org.apache.ivy.core.module.id.ModuleRevisionId): Module =
    Module(
      Organization(id.getOrganisation),
      ModuleName(id.getName),
      id.getExtraAttributes
        .asScala
        .map {
          case (k0, v0) => k0.asInstanceOf[String] -> v0.asInstanceOf[String]
        }
        .toMap
    )

  private def dependencyFromIvy(desc: org.apache.ivy.core.module.descriptor.DependencyDescriptor): Seq[(Configuration, Dependency)] = {

    val id = desc.getDependencyRevisionId
    val module = moduleFromIvy(id)
    val exclusions = desc
      .getAllExcludeRules
      .map { rule =>
        // we're ignoring rule.getConfigurations and rule.getMatcher here
        val modId = rule.getId.getModuleId
        // we're ignoring modId.getAttributes here
        (Organization(modId.getOrganisation), ModuleName(modId.getName))
      }
      .toSet

    val configurations = desc
      .getModuleConfigurations
      .toVector
      .flatMap(Inputs.ivyXmlMappings)

    def dependency(conf: Configuration, pub: Publication) = Dependency(
      module,
      id.getRevision,
      conf,
      exclusions,
      pub,
      optional = false,
      desc.isTransitive
    )

    val publications: Configuration => Publication = {

      val artifacts = desc.getAllDependencyArtifacts

      val m = artifacts.toVector.flatMap { art =>
        val pub = Publication(art.getName, Type(art.getType), Extension(art.getExt), Classifier(""))
        art.getConfigurations.map(Configuration(_)).toVector.map { conf =>
          conf -> pub
        }
      }.toMap

      c => m.getOrElse(c, Publication("", Type(""), Extension(""), Classifier("")))
    }

    configurations.map {
      case (from, to) =>
        from -> dependency(to, publications(to))
    }
  }

  private[sbtcoursiershared] def coursierInterProjectDependenciesTask: Def.Initialize[sbt.Task[Seq[Project]]] =
    Def.taskDyn {

      val state = sbt.Keys.state.value
      val projectRef = sbt.Keys.thisProjectRef.value

      val projectRefs = Structure.allRecursiveInterDependencies(state, projectRef)

      val t = coursierProject.forAllProjectsOpt(state, projectRefs :+ projectRef)

      Def.task {
        t.value.toVector.flatMap {
          case (ref, None) =>
            if (ref.build != projectRef.build)
              state.log.warn(s"Cannot get coursier info for project under ${ref.build}, is sbt-coursier also added to it?")
            Nil
          case (_, Some(p)) =>
            Seq(p)
        }
      }
    }

  private[sbtcoursiershared] def coursierExtraProjectsTask: Def.Initialize[sbt.Task[Seq[Project]]] =
    Def.task {
      val projects = coursierInterProjectDependencies.value
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
            val configurations = v
              .getConfigurations
              .map { c =>
                Configuration(c.getName) -> c.getExtends.map(Configuration(_)).toSeq
              }
              .toMap
            val deps = v.getDependencies.flatMap(dependencyFromIvy)
            Project(
              module,
              v.getModuleRevisionId.getRevision,
              deps,
              configurations,
              Nil,
              None,
              Nil,
              Info("", "", Nil, Nil, None)
            )
        }
    }

  private[sbtcoursiershared] def coursierFallbackDependenciesTask: Def.Initialize[sbt.Task[Seq[FallbackDependency]]] =
    Def.taskDyn {

      val state = sbt.Keys.state.value
      val projectRef = sbt.Keys.thisProjectRef.value

      val projects = allRecursiveInterDependencies(state, projectRef)

      val allDependenciesTask = allDependencies
        .forAllProjects(state, projectRef +: projects)
        .map(_.values.toVector.flatten)

      Def.task {
        val allDependencies = allDependenciesTask.value

        FromSbt.fallbackDependencies(
          allDependencies,
          scalaVersion.in(projectRef).get(state),
          scalaBinaryVersion.in(projectRef).get(state)
        )
      }
    }

  val credentialsTask = Def.taskDyn {

    val useSbtCredentials = coursierUseSbtCredentials.value

    val fromSbt =
      if (useSbtCredentials)
        Def.task {
          val log = streams.value.log

          sbt.Keys.credentials.value
            .flatMap {
              case dc: sbt.DirectCredentials => List(dc)
              case fc: sbt.FileCredentials =>
                sbt.Credentials.loadCredentials(fc.path) match {
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
                .withRealm(Some(c.realm).filter(_.nonEmpty))
                .withHttpsOnly(false)
                .withMatchHost(true)
            }
        }
      else
        Def.task(Seq.empty[DirectCredentials])

    Def.task {
      fromSbt.value ++ coursierExtraCredentials.value
    }
  }


  def strictTask = Def.task {
    val cm = conflictManager.value
    val log = streams.value.log

    cm.name match {
      case ConflictManager.latestRevision.name =>
        None
      case ConflictManager.strict.name =>
        val strict = Strict()
          .withInclude(Set((cm.organization, cm.module)))
        Some(strict)
      case other =>
        log.warn(s"Unsupported conflict manager $other")
        None
    }
  }


}
