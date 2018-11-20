package coursier.sbtcoursiershared

import coursier.core.Project
import sbt.{AutoPlugin, TaskKey}

object SbtCoursierShared extends AutoPlugin {

  override def trigger = allRequirements

  override def requires = sbt.plugins.JvmPlugin

  object autoImport {
    val coursierProject = TaskKey[Project]("coursier-project")
    val coursierInterProjectDependencies = TaskKey[Seq[Project]]("coursier-inter-project-dependencies", "Projects the current project depends on, possibly transitively")
  }

  import autoImport._

  override def projectSettings = Seq(
    coursierProject := InputsTasks.coursierProjectTask.value,
    coursierInterProjectDependencies := InputsTasks.coursierInterProjectDependenciesTask.value
  )

}
