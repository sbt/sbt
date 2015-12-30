package coursier

import sbt.{Classpaths, Resolver, Def}
import Structure._
import Keys._
import sbt.Keys._

object Tasks {

  def coursierResolversTask: Def.Initialize[sbt.Task[Seq[Resolver]]] = Def.task {
    var l = externalResolvers.value
    if (sbtPlugin.value)
      l = Seq(
        sbtResolver.value,
        Classpaths.sbtPluginReleases
      ) ++ l
    l
  }

  def coursierProjectTask: Def.Initialize[sbt.Task[(Project, Seq[(String, Seq[Artifact])])]] =
    (
      sbt.Keys.state,
      sbt.Keys.thisProjectRef
    ).flatMap { (state, projectRef) =>

      // should projectID.configurations be used instead?
      val configurations = ivyConfigurations.in(projectRef).get(state)

      // exportedProducts looks like what we want, but depends on the update task, which
      // make the whole thing run into cycles...
      val artifacts = configurations.map { cfg =>
        cfg.name -> Option(classDirectory.in(projectRef).in(cfg).getOrElse(state, null))
      }.collect { case (name, Some(classDir)) =>
        name -> Seq(
          Artifact(
            classDir.toURI.toString,
            Map.empty,
            Map.empty,
            Attributes(),
            changing = true
          )
        )
      }

      val allDependenciesTask = allDependencies.in(projectRef).get(state)

      for {
        allDependencies <- allDependenciesTask
      } yield {

        val proj = FromSbt.project(
          projectID.in(projectRef).get(state),
          allDependencies,
          configurations.map { cfg => cfg.name -> cfg.extendsConfigs.map(_.name) }.toMap,
          scalaVersion.in(projectRef).get(state),
          scalaBinaryVersion.in(projectRef).get(state)
        )

        (proj, artifacts)
      }
    }

  def coursierProjectsTask: Def.Initialize[sbt.Task[Seq[(Project, Seq[(String, Seq[Artifact])])]]] =
    sbt.Keys.state.flatMap { state =>
      val projects = structure(state).allProjectRefs
      coursierProject.forAllProjects(state, projects).map(_.values.toVector)
    }

}
