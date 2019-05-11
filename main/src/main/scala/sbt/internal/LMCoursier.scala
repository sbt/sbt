/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import java.io.File
import lmcoursier.definitions.{ Classifier, Configuration => CConfiguration }
import lmcoursier._
import sbt.librarymanagement._
import Keys._
import sbt.internal.librarymanagement.{ CoursierArtifactsTasks, CoursierInputsTasks }

private[sbt] object LMCoursier {
  def defaultCacheLocation: File = CoursierDependencyResolution.defaultCacheLocation

  def coursierConfigurationTask(
      withClassifiers: Boolean,
      sbtClassifiers: Boolean
  ): Def.Initialize[Task[CoursierConfiguration]] =
    Def.taskDyn {
      val s = streams.value
      val log = s.log
      val resolversTask =
        if (sbtClassifiers)
          csrSbtResolvers
        else
          csrRecursiveResolvers
      val classifiersTask: sbt.Def.Initialize[sbt.Task[Option[Seq[Classifier]]]] =
        if (withClassifiers && !sbtClassifiers)
          Def.task(Some(sbt.Keys.transitiveClassifiers.value.map(Classifier(_))))
        else
          Def.task(None)
      Def.task {
        val rs = resolversTask.value
        val scalaOrg = scalaOrganization.value
        val scalaVer = scalaVersion.value
        val interProjectDependencies = csrInterProjectDependencies.value
        val excludeDeps = Inputs.exclusions(
          allExcludeDependencies.value,
          scalaVer,
          scalaBinaryVersion.value,
          streams.value.log
        )
        val fallbackDeps = csrFallbackDependencies.value
        val autoScalaLib = autoScalaLibrary.value && scalaModuleInfo.value.forall(
          _.overrideScalaVersion
        )
        val profiles = csrMavenProfiles.value
        val credentials = CoursierInputsTasks.credentialsTask.value

        val createLogger = csrLogger.value

        val cache = csrCachePath.value

        val internalSbtScalaProvider = appConfiguration.value.provider.scalaProvider
        val sbtBootJars = internalSbtScalaProvider.jars()
        val sbtScalaVersion = internalSbtScalaProvider.version()
        val sbtScalaOrganization = "org.scala-lang" // always assuming sbt uses mainline scala
        val classifiers = classifiersTask.value
        Classpaths.warnResolversConflict(rs, log)
        CoursierConfiguration()
          .withResolvers(rs.toVector)
          .withInterProjectDependencies(interProjectDependencies.toVector)
          .withFallbackDependencies(fallbackDeps.toVector)
          .withExcludeDependencies(
            excludeDeps.toVector.map {
              case (o, n) =>
                (o.value, n.value)
            }.sorted
          )
          .withAutoScalaLibrary(autoScalaLib)
          .withSbtScalaJars(sbtBootJars.toVector)
          .withSbtScalaVersion(sbtScalaVersion)
          .withSbtScalaOrganization(sbtScalaOrganization)
          .withClassifiers(classifiers.toVector.flatten.map(_.value))
          .withHasClassifiers(classifiers.nonEmpty)
          .withMavenProfiles(profiles.toVector.sorted)
          .withScalaOrganization(scalaOrg)
          .withScalaVersion(scalaVer)
          .withCredentials(credentials)
          .withLogger(createLogger)
          .withCache(cache)
          .withLog(log)
      }
    }

  def publicationsSetting(packageConfigs: Seq[(Configuration, CConfiguration)]): Def.Setting[_] = {
    csrPublications := CoursierArtifactsTasks.coursierPublicationsTask(packageConfigs: _*).value
  }
}
