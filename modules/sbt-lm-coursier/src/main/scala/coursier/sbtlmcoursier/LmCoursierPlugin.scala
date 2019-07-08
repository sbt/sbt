package coursier.sbtlmcoursier

import lmcoursier.definitions.Authentication
import lmcoursier.{CoursierConfiguration, CoursierDependencyResolution, Inputs}
import coursier.sbtcoursiershared.InputsTasks.{credentialsTask, strictTask}
import coursier.sbtcoursiershared.{InputsTasks, SbtCoursierShared}
import sbt.{AutoPlugin, Classpaths, Def, Setting, Task, taskKey}
import sbt.Project.inTask
import sbt.KeyRanks.DTask
import sbt.Keys.{appConfiguration, autoScalaLibrary, classpathTypes, dependencyResolution, ivyPaths, scalaBinaryVersion, scalaModuleInfo, scalaOrganization, scalaVersion, streams, updateClassifiers, updateSbtClassifiers}
import sbt.librarymanagement.DependencyResolution

import scala.language.reflectiveCalls

object LmCoursierPlugin extends AutoPlugin {

  import SbtCoursierShared.autoImport._

  object autoImport {
    val coursierConfiguration = taskKey[CoursierConfiguration]("General dependency management (Coursier) settings, such as the resolvers and options to use.").withRank(DTask)

    val addSbtCoursier: Seq[Def.Setting[_]] = {
      import sbt._
      Seq(
        addSbtPlugin("io.get-coursier" % "sbt-lm-coursier" % sbtCoursierVersion),
        // seems needed for some sbt plugins (https://github.com/coursier/coursier/issues/450)
        classpathTypes += "maven-plugin"
      )
    }
  }

  import autoImport._


  override def trigger = allRequirements

  // this transitively requires IvyPlugin… which is needed to override it,
  // so that it doesn't override us :|
  override def requires = SbtCoursierShared

  // putting this in projectSettings like sbt.plugins.IvyPlugin does :|
  override def projectSettings: Seq[Setting[_]] =
    Seq(
      dependencyResolution := mkDependencyResolution.value,
      coursierConfiguration := mkCoursierConfiguration().value,
      updateClassifiers := Def.taskDyn {
        val lm = dependencyResolution.in(updateClassifiers).value
        Def.task(sbt.hack.Foo.updateTask(lm).value)
      }.value
    ) ++
    inTask(updateClassifiers)(
      Seq(
        dependencyResolution := mkDependencyResolution.value,
        coursierConfiguration := mkCoursierConfiguration(withClassifiers = true).value
      )
    ) ++
    inTask(updateSbtClassifiers)(
      Seq(
        dependencyResolution := mkDependencyResolution.value,
        coursierConfiguration := mkCoursierConfiguration(sbtClassifiers = true).value
      )
    )


  private def mkCoursierConfiguration(withClassifiers: Boolean = false, sbtClassifiers: Boolean = false): Def.Initialize[Task[CoursierConfiguration]] =
    Def.taskDyn {
      val resolversTask =
        if (sbtClassifiers)
          coursierSbtResolvers
        else
          coursierRecursiveResolvers
      val classifiersTask: sbt.Def.Initialize[sbt.Task[Option[Seq[String]]]] =
        if (withClassifiers && !sbtClassifiers)
          Def.task(Some(sbt.Keys.transitiveClassifiers.value))
        else
          Def.task(None)
      Def.task {
        val rs = resolversTask.value
        val scalaOrg = scalaOrganization.value
        val scalaVer = scalaVersion.value
        val interProjectDependencies = coursierInterProjectDependencies.value
        val excludeDeps = Inputs.exclusions(
          InputsTasks.actualExcludeDependencies.value,
          scalaVer,
          scalaBinaryVersion.value,
          streams.value.log
        )
        val fallbackDeps = coursierFallbackDependencies.value
        val autoScalaLib = autoScalaLibrary.value && scalaModuleInfo.value.forall(_.overrideScalaVersion)
        val profiles = mavenProfiles.value

        val authenticationByRepositoryId = coursierCredentials.value.mapValues { c =>
          val a = c.authentication
          Authentication(a.user, a.password, a.optional, a.realmOpt)
        }
        val credentials = credentialsTask.value
        val strict = strictTask.value

        val createLogger = coursierLogger.value

        val cache = coursierCache.value

        val internalSbtScalaProvider = appConfiguration.value.provider.scalaProvider
        val sbtBootJars = internalSbtScalaProvider.jars()
        val sbtScalaVersion = internalSbtScalaProvider.version()
        val sbtScalaOrganization = "org.scala-lang" // always assuming sbt uses mainline scala
        val classifiers = classifiersTask.value
        val s = streams.value
        Classpaths.warnResolversConflict(rs, s.log)
        CoursierConfiguration()
          .withResolvers(rs.toVector)
          .withInterProjectDependencies(interProjectDependencies.toVector)
          .withFallbackDependencies(fallbackDeps.toVector)
          .withExcludeDependencies(
            excludeDeps
              .toVector
              .map {
                case (o, n) =>
                  (o.value, n.value)
              }
              .sorted
          )
          .withAutoScalaLibrary(autoScalaLib)
          .withSbtScalaJars(sbtBootJars.toVector)
          .withSbtScalaVersion(sbtScalaVersion)
          .withSbtScalaOrganization(sbtScalaOrganization)
          .withClassifiers(classifiers.toVector.flatten)
          .withHasClassifiers(classifiers.nonEmpty)
          .withMavenProfiles(profiles.toVector.sorted)
          .withScalaOrganization(scalaOrg)
          .withScalaVersion(scalaVer)
          .withAuthenticationByRepositoryId(authenticationByRepositoryId.toVector.sortBy(_._1))
          .withCredentials(credentials)
          .withLogger(createLogger)
          .withCache(cache)
          .withLog(s.log)
          .withIvyHome(ivyPaths.value.ivyHome)
          .withStrict(strict)
      }
    }
  private def mkDependencyResolution: Def.Initialize[Task[DependencyResolution]] =
    Def.task {
      CoursierDependencyResolution(coursierConfiguration.value)
    }

}
