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

  private val temporarySettings = {

    import sbt._
    import sbt.Classpaths.withExcludes
    import sbt.Defaults.lock
    import sbt.Keys._
    import sbt.librarymanagement.GetClassifiersConfiguration

    Seq(
      // cut-n-pasted from sbt 1.0.2
      // only the "val lm = …" line was changed
      updateClassifiers := (Def.task {
        val s = streams.value
        val is = ivySbt.value
        val lm = dependencyResolution.value
        val mod = (classifiersModule in updateClassifiers).value
        val c = updateConfiguration.value
        val app = appConfiguration.value
        val srcTypes = sourceArtifactTypes.value
        val docTypes = docArtifactTypes.value
        val out = is.withIvy(s.log)(_.getSettings.getDefaultIvyUserDir)
        val uwConfig = (unresolvedWarningConfiguration in update).value
        withExcludes(out, mod.classifiers, lock(app)) { excludes =>
          lm.updateClassifiers(
            GetClassifiersConfiguration(
              mod,
              excludes.toVector,
              c.withArtifactFilter(c.artifactFilter.map(af => af.withInverted(!af.inverted))),
              // scalaModule,
              srcTypes.toVector,
              docTypes.toVector
            ),
            uwConfig,
            Vector.empty,
            s.log
          ) match {
            case Left(_)   => ???
            case Right(ur) => ur
          }
        }
      } tag (Tags.Update, Tags.Network)).value
    )
  }

  // putting this in projectSettings like sbt.plugins.IvyPlugin does :|
  override def projectSettings: Seq[Setting[_]] =
    temporarySettings ++
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
