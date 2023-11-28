package coursier.sbtcoursiershared

import java.io.File

import coursier.{Credentials => LegacyCredentials}
import lmcoursier.credentials.Credentials
import lmcoursier.{CoursierDependencyResolution, FallbackDependency}
import lmcoursier.definitions.{CacheLogger, Configuration, Project, Publication}
import lmcoursier.internal.SbtCoursierCache
import lmcoursier.syntax._
import sbt.{AutoPlugin, Classpaths, Compile, Setting, TaskKey, Test, settingKey, taskKey}
import sbt.Keys._
import sbt.librarymanagement.DependencyBuilders.OrganizationArtifactName
import sbt.librarymanagement.{ModuleID, Resolver, URLRepository}
import scala.concurrent.duration.FiniteDuration

object SbtCoursierShared extends AutoPlugin {

  override def trigger = allRequirements

  override def requires = sbt.plugins.JvmPlugin

  object autoImport {
    val coursierGenerateIvyXml = settingKey[Boolean]("")
    val coursierWriteIvyXml = taskKey[File]("")
    val coursierProject = TaskKey[Project]("coursier-project")
    val coursierInterProjectDependencies = TaskKey[Seq[Project]]("coursier-inter-project-dependencies", "Projects the current project depends on, possibly transitively")
    val coursierExtraProjects = TaskKey[Seq[Project]]("coursier-extra-projects", "")
    val coursierPublications = TaskKey[Seq[(Configuration, Publication)]]("coursier-publications")

    val coursierKeepPreloaded = settingKey[Boolean]("Whether to take into account sbt preloaded repositories or not")
    val coursierReorderResolvers = settingKey[Boolean](
      "Whether resolvers should be re-ordered so that typically slow ones are given a lower priority"
    )
    val coursierResolvers = taskKey[Seq[Resolver]]("")
    val coursierRecursiveResolvers = taskKey[Seq[Resolver]]("Resolvers of the current project, plus those of all from its inter-dependency projects")
    val coursierSbtResolvers = taskKey[Seq[Resolver]]("")

    val coursierFallbackDependencies = taskKey[Seq[FallbackDependency]]("")

    val mavenProfiles = settingKey[Set[String]]("")
    val versionReconciliation = taskKey[Seq[ModuleID]]("")

    private[coursier] val actualCoursierCredentials = TaskKey[Map[String, LegacyCredentials]]("coursierCredentials", "")

    val coursierUseSbtCredentials = settingKey[Boolean]("")
    @deprecated("Use coursierExtraCredentials rather than coursierCredentials", "1.1.0-M14")
    val coursierCredentials = actualCoursierCredentials
    val coursierExtraCredentials = taskKey[Seq[Credentials]]("")

    val coursierLogger = taskKey[Option[CacheLogger]]("")

    val coursierCache = settingKey[File]("")

    val sbtCoursierVersion = Properties.version

    val coursierRetry = taskKey[Option[(FiniteDuration, Int)]]("Retry for downloading dependencies")
  }

  import autoImport._

  def publicationsSetting(packageConfigs: Seq[(sbt.Configuration, Configuration)]): Setting[_] =
    coursierPublications := ArtifactsTasks.coursierPublicationsTask(packageConfigs: _*).value

  override def globalSettings: Seq[Setting[_]] =
    Seq(
      coursierUseSbtCredentials := true,
      actualCoursierCredentials := Map.empty,
      coursierExtraCredentials := Nil
    )

  override def buildSettings: Seq[Setting[_]] =
    Seq(
      coursierReorderResolvers := true,
      coursierKeepPreloaded := false,
      coursierLogger := None,
      coursierCache := CoursierDependencyResolution.defaultCacheLocation,
      coursierRetry := None
    )

  private val pluginIvySnapshotsBase = Resolver.SbtRepositoryRoot.stripSuffix("/") + "/ivy-snapshots"

  override def projectSettings = settings(pubSettings = true)

  def settings(pubSettings: Boolean) =
    Seq[Setting[_]](
      clean := {
        val noWarningPlz = clean.value
        SbtCoursierCache.default.clear()
      },
      onUnload := {
        SbtCoursierCache.default.clear()
        onUnload.value
      },
      coursierGenerateIvyXml := true,
      coursierWriteIvyXml := IvyXmlGeneration.writeIvyXml.value,
      coursierProject := InputsTasks.coursierProjectTask.value,
      coursierInterProjectDependencies := InputsTasks.coursierInterProjectDependenciesTask.value,
      coursierExtraProjects := InputsTasks.coursierExtraProjectsTask.value
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
      coursierSbtResolvers := {

        // TODO Add docker-based integration test for that, see https://github.com/coursier/coursier/issues/632

        val resolvers =
          sbt.Classpaths.bootRepositories(appConfiguration.value).toSeq.flatten ++ // required because of the hack above it seems
            externalResolvers.in(updateSbtClassifiers).value

        val pluginIvySnapshotsFound = resolvers.exists {
          case repo: URLRepository =>
            repo
              .patterns
              .artifactPatterns
              .headOption
              .exists(_.startsWith(pluginIvySnapshotsBase))
          case _ => false
        }

        val resolvers0 =
          if (pluginIvySnapshotsFound && !resolvers.contains(Classpaths.sbtPluginReleases))
            resolvers :+ Classpaths.sbtPluginReleases
          else
            resolvers

        if (SbtCoursierShared.autoImport.coursierKeepPreloaded.value)
          resolvers0
        else
          resolvers0.filter { r =>
            !r.name.startsWith("local-preloaded")
          }
      },
      coursierFallbackDependencies := InputsTasks.coursierFallbackDependenciesTask.value,
      ivyConfigurations := {
        val confs = ivyConfigurations.value
        val names = confs.map(_.name).toSet

        // Yes, adding those back in sbt 1.0. Can't distinguish between config test (whose jars with classifier tests ought to
        // be added), and sources / docs else (if their JARs are in compile, they would get added too then).

        val extraSources =
          if (names("sources"))
            None
          else
            Some(
              sbt.Configuration.of(
                id = "Sources",
                name = "sources",
                description = "",
                isPublic = true,
                extendsConfigs = Vector.empty,
                transitive = false
              )
            )

        val extraDocs =
          if (names("docs"))
            None
          else
            Some(
              sbt.Configuration.of(
                id = "Docs",
                name = "docs",
                description = "",
                isPublic = true,
                extendsConfigs = Vector.empty,
                transitive = false
              )
            )

        confs ++ extraSources.toSeq ++ extraDocs.toSeq
      },
      mavenProfiles := Set.empty,
      versionReconciliation := Seq.empty,
      coursierRetry := None
    ) ++ {
      if (pubSettings)
        IvyXmlGeneration.generateIvyXmlSettings
      else
        Nil
    }

}
