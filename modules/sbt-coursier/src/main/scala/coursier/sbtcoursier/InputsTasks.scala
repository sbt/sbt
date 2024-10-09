package coursier.sbtcoursier

import coursier.ProjectCache
import coursier.core._
import lmcoursier._
import lmcoursier.definitions.ToCoursier
import coursier.sbtcoursier.Keys._
import coursier.sbtcoursiershared.SbtCoursierShared.autoImport._
import coursier.sbtcoursiershared.Structure._
import sbt.librarymanagement.{Configuration => _, _}
import sbt.Def
import sbt.Keys._

object InputsTasks {

  def coursierConfigurationsTask: Def.Initialize[sbt.Task[Map[Configuration, Set[Configuration]]]] =
    Def.task {
      Inputs.coursierConfigurationsMap(ivyConfigurations.value).map {
        case (k, v) =>
          ToCoursier.configuration(k) -> v.map(ToCoursier.configuration)
      }
    }

  def ivyGraphsTask: Def.Initialize[sbt.Task[Seq[(Configuration, Seq[Configuration])]]] =
    Def.task {
      val p = coursierProject.value
      Inputs.orderedConfigurations(p.configurations.toSeq).map {
        case (config, extends0) =>
          (ToCoursier.configuration(config), extends0.map(ToCoursier.configuration))
      }
    }

  def parentProjectCacheTask: Def.Initialize[sbt.Task[Map[Seq[sbt.librarymanagement.Resolver], Seq[coursier.ProjectCache]]]] =
    Def.taskDyn {

      val state = sbt.Keys.state.value
      val projectRef = sbt.Keys.thisProjectRef.value

      val projectDeps = structure(state).allProjects
        .find(_.id == projectRef.project)
        .map(_.dependencies.map(_.project.project).toSet)
        .getOrElse(Set.empty)

      val projects = structure(state).allProjectRefs.filter(p => projectDeps(p.project))

      val t =
        for {
          m <- coursierRecursiveResolvers.forAllProjects(state, projects)
          n <- coursierResolutions.forAllProjects(state, m.keys.toSeq)
        } yield
          n.foldLeft(Map.empty[Seq[Resolver], Seq[ProjectCache]]) {
            case (caches, (ref, resolutions)) =>
              val mainResOpt = resolutions.collectFirst {
                case (Configuration.compile, v) => v
              }

              val r = for {
                resolvers <- m.get(ref)
                resolution <- mainResOpt
              } yield
                caches.updated(resolvers, resolution.projectCache +: caches.getOrElse(resolvers, Seq.empty))

              r.getOrElse(caches)
          }

      Def.task(t.value)
    }

}
