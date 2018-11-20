package coursier.sbtcoursiershared

import coursier.core.{Configuration, Project, Publication}
import coursier.lmcoursier.SbtCoursierCache
import sbt.{AutoPlugin, Compile, Setting, TaskKey, Test, settingKey, taskKey}
import sbt.Keys.{classpathTypes, clean}
import sbt.librarymanagement.Resolver

object SbtCoursierShared extends AutoPlugin {

  override def trigger = allRequirements

  override def requires = sbt.plugins.JvmPlugin

  object autoImport {
    val coursierGenerateIvyXml = settingKey[Boolean]("")
    val coursierProject = TaskKey[Project]("coursier-project")
    val coursierInterProjectDependencies = TaskKey[Seq[Project]]("coursier-inter-project-dependencies", "Projects the current project depends on, possibly transitively")
    val coursierPublications = TaskKey[Seq[(Configuration, Publication)]]("coursier-publications")

    val coursierKeepPreloaded = settingKey[Boolean]("Whether to take into account sbt preloaded repositories or not")
    val coursierReorderResolvers = settingKey[Boolean](
      "Whether resolvers should be re-ordered so that typically slow ones are given a lower priority"
    )
    val coursierResolvers = taskKey[Seq[Resolver]]("")
    val coursierRecursiveResolvers = taskKey[Seq[Resolver]]("Resolvers of the current project, plus those of all from its inter-dependency projects")
  }

  import autoImport._

  def publicationsSetting(packageConfigs: Seq[(sbt.Configuration, Configuration)]): Setting[_] =
    coursierPublications := ArtifactsTasks.coursierPublicationsTask(packageConfigs: _*).value

  override def buildSettings: Seq[Setting[_]] =
    Seq(
      coursierReorderResolvers := true,
      coursierKeepPreloaded := false
    )

  override def projectSettings = settings(pubSettings = true)

  def settings(pubSettings: Boolean) =
    Seq[Setting[_]](
      clean := {
        val noWarningPlz = clean.value
        SbtCoursierCache.default.clear()
      },
      coursierGenerateIvyXml := true,
      coursierProject := InputsTasks.coursierProjectTask.value,
      coursierInterProjectDependencies := InputsTasks.coursierInterProjectDependenciesTask.value
    ) ++ {
      if (pubSettings)
        Seq(
          publicationsSetting(Seq(Compile, Test).map(c => c -> Configuration(c.name)))
        )
      else
        Nil
    } ++ Seq(
      // Tests artifacts from Maven repositories are given this type.
      // Adding it here so that these work straightaway.
      classpathTypes += "test-jar", // FIXME Should this go in buildSettings?
      coursierResolvers := RepositoriesTasks.coursierResolversTask.value,
      coursierRecursiveResolvers := RepositoriesTasks.coursierRecursiveResolversTask.value,
    ) ++ {
      if (pubSettings)
        IvyXml.generateIvyXmlSettings()
      else
        Nil
    }

}
