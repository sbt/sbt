package coursier.sbtcoursiershared

import coursier.core.{Configuration, Project, Publication}
import sbt.{AutoPlugin, Compile, Setting, TaskKey, Test, settingKey}

object SbtCoursierShared extends AutoPlugin {

  override def trigger = allRequirements

  override def requires = sbt.plugins.JvmPlugin

  object autoImport {
    val coursierGenerateIvyXml = settingKey[Boolean]("")
    val coursierProject = TaskKey[Project]("coursier-project")
    val coursierInterProjectDependencies = TaskKey[Seq[Project]]("coursier-inter-project-dependencies", "Projects the current project depends on, possibly transitively")
    val coursierPublications = TaskKey[Seq[(Configuration, Publication)]]("coursier-publications")
  }

  import autoImport._

  def publicationsSetting(packageConfigs: Seq[(sbt.Configuration, Configuration)]): Setting[_] =
    coursierPublications := ArtifactsTasks.coursierPublicationsTask(packageConfigs: _*).value

  override def projectSettings =
    Seq[Setting[_]](
      coursierGenerateIvyXml := true,
      coursierProject := InputsTasks.coursierProjectTask.value,
      coursierInterProjectDependencies := InputsTasks.coursierInterProjectDependenciesTask.value,
      publicationsSetting(Seq(Compile, Test).map(c => c -> Configuration(c.name)))
    ) ++
    IvyXml.generateIvyXmlSettings()

}
