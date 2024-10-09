package coursier.sbtlmcoursier

import lmcoursier.definitions.{Authentication, ModuleMatchers, Reconciliation}
import lmcoursier.{CoursierConfiguration, CoursierDependencyResolution, Inputs}
import lmcoursier.syntax._
import coursier.sbtcoursiershared.InputsTasks.{credentialsTask, strictTask}
import coursier.sbtcoursiershared.{InputsTasks, SbtCoursierShared}
import sbt.{AutoPlugin, Classpaths, Def, Setting, Task, taskKey}
import sbt.Project.inTask
import sbt.KeyRanks.DTask
import sbt.Keys.{appConfiguration, autoScalaLibrary, classpathTypes, dependencyOverrides, dependencyResolution, ivyPaths, scalaBinaryVersion, scalaModuleInfo, scalaOrganization, scalaVersion, streams, updateClassifiers, updateConfiguration, updateSbtClassifiers}
import sbt.librarymanagement.DependencyResolution

import scala.language.reflectiveCalls
import sbt.TaskKey

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

  private lazy val scalaCompilerBridgeScopeOpt: Option[TaskKey[Unit]] =
    try {
      // only added in sbt 1.3.x
      val key = sbt.Keys
        .asInstanceOf[{ def scalaCompilerBridgeScope: TaskKey[Unit] }]
        .scalaCompilerBridgeScope
      Some(key)
    }
    catch {
      case _: NoSuchMethodError | _: NoSuchMethodException =>
        None
    }

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
    setCsrConfiguration ++
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
    ) ++ {
      scalaCompilerBridgeScopeOpt match {
        case None => Nil
        case Some(scalaCompilerBridgeScopeKey) =>
          inTask(scalaCompilerBridgeScopeKey)(
            Seq(
              dependencyResolution := mkDependencyResolution.value,
              coursierConfiguration := mkCoursierConfiguration().value
            )
          )
      }
    }

  private def setCsrConfiguration: Seq[Setting[_]] = {
    val csrConfigurationOpt = try {
      val key = sbt.Keys.asInstanceOf[{
        def csrConfiguration: TaskKey[CoursierConfiguration]
      }].csrConfiguration
      Some(key)
    } catch {
      case _: NoSuchMethodException =>
        None
    }

    csrConfigurationOpt.toSeq.map { csrConfiguration =>
      csrConfiguration := coursierConfiguration.value
    }
  }

  private def mkCoursierConfiguration(withClassifiers: Boolean = false, sbtClassifiers: Boolean = false): Def.Initialize[Task[CoursierConfiguration]] =
    Def.taskDyn {
      val resolversTask =
        if (sbtClassifiers)
          coursierSbtResolvers
        else
          coursierRecursiveResolvers
      val interProjectDependenciesTask: sbt.Def.Initialize[sbt.Task[Seq[lmcoursier.definitions.Project]]] =
        if (sbtClassifiers)
          Def.task(Seq.empty[lmcoursier.definitions.Project])
        else
          Def.task(coursierInterProjectDependencies.value)
      val classifiersTask: sbt.Def.Initialize[sbt.Task[Option[Seq[String]]]] =
        if (withClassifiers && !sbtClassifiers)
          Def.task(Some(sbt.Keys.transitiveClassifiers.value))
        else
          Def.task(None)
      Def.task {
        val rs = resolversTask.value
        val scalaOrg = scalaOrganization.value
        val scalaVer = scalaVersion.value
        val sbv = scalaBinaryVersion.value
        val interProjectDependencies = interProjectDependenciesTask.value
        val extraProjects = coursierExtraProjects.value
        val excludeDeps = Inputs.exclusions(
          InputsTasks.actualExcludeDependencies.value,
          scalaVer,
          scalaBinaryVersion.value,
          streams.value.log
        )
        val fallbackDeps = coursierFallbackDependencies.value
        val autoScalaLib = autoScalaLibrary.value && scalaModuleInfo.value.forall(_.overrideScalaVersion)
        val profiles = mavenProfiles.value
        val versionReconciliations0 = versionReconciliation.value.map { mod =>
          Reconciliation(mod.revision) match {
            case Some(rec) =>
              val (mod0, _) = lmcoursier.FromSbt.moduleVersion(mod, scalaVer, sbv)
              val matcher = ModuleMatchers.only(mod0)
              matcher -> rec
            case None =>
              throw new Exception(s"Unrecognized reconciliation: '${mod.revision}'")
          }
        }


        val userForceVersions = Inputs.forceVersions(dependencyOverrides.value, scalaVer, sbv)

        val authenticationByRepositoryId = actualCoursierCredentials.value.mapValues { c =>
          val a = c.authentication
          Authentication(a.user, a.password, a.optional, a.realmOpt, headers = Nil, httpsOnly = true, passOnRedirect = false)
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
        val updateConfig = updateConfiguration.value
        val s = streams.value
        Classpaths.warnResolversConflict(rs, s.log)
        CoursierConfiguration()
          .withResolvers(rs.toVector)
          .withInterProjectDependencies(interProjectDependencies.toVector)
          .withExtraProjects(extraProjects.toVector)
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
          .withReconciliation(versionReconciliations0.toVector)
          .withScalaOrganization(scalaOrg)
          .withScalaVersion(scalaVer)
          .withAuthenticationByRepositoryId(authenticationByRepositoryId.toVector.sortBy(_._1))
          .withCredentials(credentials)
          .withLogger(createLogger)
          .withCache(cache)
          .withLog(s.log)
          .withIvyHome(ivyPaths.value.ivyHome)
          .withStrict(strict)
          .withForceVersions(userForceVersions.toVector)
          .withSbtClassifiers(sbtClassifiers)
          // seems missingOk is false in the updateConfig of updateSbtClassifiers?
          .withUpdateConfiguration(updateConfig.withMissingOk(updateConfig.missingOk || sbtClassifiers))
      }
    }
  private def mkDependencyResolution: Def.Initialize[Task[DependencyResolution]] =
    Def.task {
      CoursierDependencyResolution(coursierConfiguration.value, None)
    }

}
