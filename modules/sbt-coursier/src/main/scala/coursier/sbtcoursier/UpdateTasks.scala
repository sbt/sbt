package coursier.sbtcoursier

import coursier.core._
import coursier.sbtcoursier.Keys._
import coursier.sbtcoursiershared.SbtCoursierShared.autoImport._
import lmcoursier.definitions.ToCoursier
import lmcoursier.Inputs
import lmcoursier.internal.{SbtBootJars, SbtCoursierCache, UpdateParams, UpdateRun}
import sbt.Def
import sbt.Keys._
import sbt.librarymanagement.UpdateReport

object UpdateTasks {

  def updateTask(
    withClassifiers: Boolean,
    sbtClassifiers: Boolean = false,
    includeSignatures: Boolean = false
  ): Def.Initialize[sbt.Task[UpdateReport]] = {

    val currentProjectTask =
      if (sbtClassifiers)
        Def.task {
          val sv = scalaVersion.value
          val sbv = scalaBinaryVersion.value
          SbtCoursierFromSbt.sbtClassifiersProject(coursierSbtClassifiersModule.value, sv, sbv)
        }
      else
        Def.task {
          val proj = coursierProject.value
          val publications = coursierPublications.value
          proj.withPublications(publications)
        }

    val resTask =
      if (withClassifiers && sbtClassifiers)
        coursierSbtClassifiersResolutions
      else
        coursierResolutions

    // we should be able to call .value on that one here, its conditions don't originate from other tasks
    val artifactFilesOrErrors0Task =
      if (withClassifiers) {
        if (sbtClassifiers)
          Keys.coursierSbtClassifiersArtifacts
        else
          Keys.coursierClassifiersArtifacts
      } else if (includeSignatures)
        Keys.coursierSignedArtifacts
      else
        Keys.coursierArtifacts

    val configsTask: sbt.Def.Initialize[sbt.Task[Map[Configuration, Set[Configuration]]]] =
      if (withClassifiers && sbtClassifiers)
        Def.task {
          val cm = coursierSbtClassifiersModule.value
          cm.configurations.map(c => Configuration(c.name) -> Set(Configuration(c.name))).toMap
        }
      else
        coursierConfigurations

    val classifiersTask: sbt.Def.Initialize[sbt.Task[Option[Seq[Classifier]]]] =
      if (withClassifiers) {
        if (sbtClassifiers)
          Def.task {
            val cm = coursierSbtClassifiersModule.value
            Some(cm.classifiers.map(Classifier(_)))
          }
        else
          Def.task(Some(transitiveClassifiers.value.map(Classifier(_))))
      } else
        Def.task(None)

    Def.taskDyn {

      val so = Organization(scalaOrganization.value)
      val internalSbtScalaProvider = appConfiguration.value.provider.scalaProvider
      val sbtBootJarOverrides = SbtBootJars(
        so, // this seems plain wrong - this assumes that the scala org of the project is the same
        // as the one that started SBT. This will scrap the scala org specific JARs by the ones
        // that booted SBT, even if the latter come from the standard org.scala-lang org.
        // But SBT itself does it this way, and not doing so may make two different versions
        // of the scala JARs land in the classpath...
        internalSbtScalaProvider.version(),
        internalSbtScalaProvider.jars()
      )

      val log = streams.value.log

      val verbosityLevel = coursierVerbosity.value
      val p = ToCoursier.project(currentProjectTask.value)
      val dependencies = p.dependencies
      val res = resTask.value

      val key = SbtCoursierCache.ReportKey(
        dependencies,
        res,
        withClassifiers,
        sbtClassifiers,
        includeSignatures
      )

      val interProjectDependencies = coursierInterProjectDependencies.value.map(ToCoursier.project)

      SbtCoursierCache.default.reportOpt(key) match {
        case Some(report) =>
          Def.task(report)
        case None =>
          Def.task {

            val artifactFilesOrErrors0 = artifactFilesOrErrors0Task.value
            val classifiers = classifiersTask.value
            val configs = configsTask.value
            val sv = scalaVersion.value
            val sbv = scalaBinaryVersion.value
            val forceVersions = Inputs.forceVersions(dependencyOverrides.value, sv, sbv)
              .map {
                case (m, v) =>
                  (ToCoursier.module(m), v)
              }
              .toMap

            val params = UpdateParams(
              (p.module, p.version),
              artifactFilesOrErrors0,
              None,
              classifiers,
              configs,
              dependencies,
              forceVersions,
              interProjectDependencies,
              res,
              includeSignatures,
              sbtBootJarOverrides,
              classpathOrder = true,
              missingOk = sbtClassifiers,
              classLoaders = Nil,
            )

            val rep = UpdateRun.update(params, verbosityLevel, log)
            SbtCoursierCache.default.putReport(key, rep)
            rep
          }
      }
    }
  }

}
