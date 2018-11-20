package coursier.sbtlmcoursier

import coursier.lmcoursier.{CoursierConfiguration, CoursierDependencyResolution}
import sbt.{AutoPlugin, Classpaths, Def, Task, taskKey}
import sbt.KeyRanks.DTask
import sbt.Keys.{dependencyResolution, fullResolvers, otherResolvers, streams}
import sbt.librarymanagement.DependencyResolution

object LmCoursierPlugin extends AutoPlugin {

  object autoImport {
    val coursierConfiguration = taskKey[CoursierConfiguration]("General dependency management (Coursier) settings, such as the resolvers and options to use.").withRank(DTask)
  }

  import autoImport._


  override def trigger = allRequirements

  // requiring IvyPlugin… to override it, and so that it doesn't override us :|
  override def requires = sbt.plugins.IvyPlugin

  // putting this in projectSettings like sbt.plugins.IvyPlugin does :|
  override def projectSettings = Seq(
    dependencyResolution := mkDependencyResolution.value,
    coursierConfiguration := mkCoursierConfiguration.value
  )


  private def mkCoursierConfiguration: Def.Initialize[Task[CoursierConfiguration]] =
    Def.task {
      val (rs, other) = (fullResolvers.value.toVector, otherResolvers.value.toVector)
      val s = streams.value
      Classpaths.warnResolversConflict(rs ++: other, s.log)
      CoursierConfiguration()
        .withResolvers(rs)
        .withOtherResolvers(other)
        .withLog(s.log)
    }
  private def mkDependencyResolution: Def.Initialize[Task[DependencyResolution]] =
    Def.task {
      CoursierDependencyResolution(coursierConfiguration.value)
    }

}
