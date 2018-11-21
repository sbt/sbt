package coursier.sbtlmcoursier

import coursier.lmcoursier.{CoursierConfiguration, CoursierDependencyResolution, Inputs}
import coursier.sbtcoursiershared.SbtCoursierShared
import sbt.{AutoPlugin, Classpaths, Def, Setting, Task, taskKey}
import sbt.Project.inTask
import sbt.KeyRanks.DTask
import sbt.Keys.{appConfiguration, autoScalaLibrary, dependencyResolution, excludeDependencies, scalaBinaryVersion, scalaVersion, streams, updateSbtClassifiers}
import sbt.librarymanagement.DependencyResolution

object LmCoursierPlugin extends AutoPlugin {

  object autoImport {
    val coursierConfiguration = taskKey[CoursierConfiguration]("General dependency management (Coursier) settings, such as the resolvers and options to use.").withRank(DTask)
  }

  import autoImport._
  import SbtCoursierShared.autoImport._


  override def trigger = allRequirements

  // this transitively requires IvyPlugin… which is needed to override it,
  // so that it doesn't override us :|
  override def requires = SbtCoursierShared

  // putting this in projectSettings like sbt.plugins.IvyPlugin does :|
  override def projectSettings: Seq[Setting[_]] =
    Seq(
      dependencyResolution := mkDependencyResolution.value,
      coursierConfiguration := mkCoursierConfiguration().value
    ) ++
    inTask(updateSbtClassifiers)(
      Seq(
        dependencyResolution := mkDependencyResolution.value,
        coursierConfiguration := mkCoursierConfiguration(sbtClassifiers = true).value
      )
    )


  private def mkCoursierConfiguration(sbtClassifiers: Boolean = false): Def.Initialize[Task[CoursierConfiguration]] =
    Def.taskDyn {
      val resolversTask =
        if (sbtClassifiers)
          coursierSbtResolvers
        else
          coursierRecursiveResolvers
      Def.task {
        val rs = resolversTask.value
        val interProjectDependencies = coursierInterProjectDependencies.value
        val excludeDeps = Inputs.exclusions(
          excludeDependencies.value,
          scalaVersion.value,
          scalaBinaryVersion.value,
          streams.value.log
        )
        val fallbackDeps = coursierFallbackDependencies.value
        val autoScalaLib = autoScalaLibrary.value

        val internalSbtScalaProvider = appConfiguration.value.provider.scalaProvider
        val sbtBootJars = internalSbtScalaProvider.jars()
        val sbtScalaVersion = internalSbtScalaProvider.version()
        val sbtScalaOrganization = "org.scala-lang" // always assuming sbt uses mainline scala
        val s = streams.value
        Classpaths.warnResolversConflict(rs, s.log)
        CoursierConfiguration()
          .withResolvers(rs.toVector)
          .withInterProjectDependencies(interProjectDependencies.toVector)
          .withFallbackDependencies(fallbackDeps.toVector)
          .withExcludeDependencies(
            excludeDeps
              .toVector
              .sorted
              .map {
                case (o, n) =>
                  (o.value, n.value)
              }
          )
          .withAutoScalaLibrary(autoScalaLib)
          .withSbtScalaJars(sbtBootJars.toVector)
          .withSbtScalaVersion(sbtScalaVersion)
          .withSbtScalaOrganization(sbtScalaOrganization)
          .withLog(s.log)
      }
    }
  private def mkDependencyResolution: Def.Initialize[Task[DependencyResolution]] =
    Def.task {
      CoursierDependencyResolution(coursierConfiguration.value)
    }

}
