package coursier.sbtcoursiershared

import coursier.core._
import coursier.lmcoursier._
import coursier.sbtcoursiershared.SbtCoursierShared.autoImport._
import coursier.sbtcoursiershared.Structure._
import sbt.Def
import sbt.Keys._

object InputsTasks {

  def coursierProjectTask: Def.Initialize[sbt.Task[Project]] =
    Def.taskDyn {

      val state = sbt.Keys.state.value
      val projectRef = sbt.Keys.thisProjectRef.value

      val allDependenciesTask = allDependencies.in(projectRef).get(state)

      Def.task {
        Inputs.coursierProject(
          projectID.in(projectRef).get(state),
          allDependenciesTask.value,
          excludeDependencies.in(projectRef).get(state),
          // should projectID.configurations be used instead?
          ivyConfigurations.in(projectRef).get(state),
          scalaVersion.in(projectRef).get(state),
          scalaBinaryVersion.in(projectRef).get(state),
          state.log
        )
      }
    }

  def coursierInterProjectDependenciesTask: Def.Initialize[sbt.Task[Seq[Project]]] =
    Def.taskDyn {

      val state = sbt.Keys.state.value
      val projectRef = sbt.Keys.thisProjectRef.value

      val projects = Structure.allRecursiveInterDependencies(state, projectRef)

      val t = coursierProject.forAllProjects(state, projects).map(_.values.toVector)

      Def.task(t.value)
    }

}
