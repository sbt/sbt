package coursier.sbtlmcoursier

import coursier.lmcoursier.{CoursierConfiguration, CoursierDependencyResolution, Inputs}
import coursier.sbtcoursiershared.SbtCoursierShared
import sbt.{AutoPlugin, Classpaths, Def, Setting, Task, taskKey}
import sbt.KeyRanks.DTask
import sbt.Keys.{dependencyResolution, excludeDependencies, fullResolvers, otherResolvers, scalaBinaryVersion, scalaVersion, streams}
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
  override def projectSettings = Seq[Setting[_]](
    dependencyResolution := mkDependencyResolution.value,
    coursierConfiguration := mkCoursierConfiguration.value
  )


  private def mkCoursierConfiguration: Def.Initialize[Task[CoursierConfiguration]] =
    Def.task {
      val rs = coursierRecursiveResolvers.value
      val interProjectDependencies = coursierInterProjectDependencies.value
      val excludeDeps = Inputs.exclusions(
        excludeDependencies.value,
        scalaVersion.value,
        scalaBinaryVersion.value,
        streams.value.log
      )
      val s = streams.value
      Classpaths.warnResolversConflict(rs, s.log)
      CoursierConfiguration()
        .withResolvers(rs.toVector)
        .withInterProjectDependencies(interProjectDependencies.toVector)
        .withExcludeDependencies(
          excludeDeps
            .toVector
            .sorted
            .map {
              case (o, n) =>
                (o.value, n.value)
            }
        )
        .withLog(s.log)
    }
  private def mkDependencyResolution: Def.Initialize[Task[DependencyResolution]] =
    Def.task {
      CoursierDependencyResolution(coursierConfiguration.value)
    }

}
