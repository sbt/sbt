/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package coursierint

import java.io.File
import java.util.concurrent.ConcurrentHashMap
import lmcoursier.definitions.{
  Classifier,
  Configuration => CConfiguration,
  CacheLogger,
  Project => CProject
}
import lmcoursier._
import lmcoursier.credentials.Credentials
import Keys._
import sbt.librarymanagement._
import sbt.librarymanagement.ivy.{ Credentials => IvyCredentials }
import sbt.util.Logger
import sbt.io.syntax._
import xsbti.AppConfiguration
import sbt.SlashSyntax0._

object LMCoursier {
  private[this] val credentialRegistry: ConcurrentHashMap[(String, String), IvyCredentials] =
    new ConcurrentHashMap

  def defaultCacheLocation: File =
    sys.props.get("sbt.coursier.home") match {
      case Some(home) => new File(home).getAbsoluteFile / "cache"
      case _          => CoursierDependencyResolution.defaultCacheLocation
    }

  def coursierConfiguration(
      rs: Seq[Resolver],
      interProjectDependencies: Seq[CProject],
      fallbackDeps: Seq[FallbackDependency],
      appConfig: AppConfiguration,
      classifiers: Option[Seq[Classifier]],
      profiles: Set[String],
      scalaOrg: String,
      scalaVer: String,
      scalaBinaryVer: String,
      autoScalaLib: Boolean,
      scalaModInfo: Option[ScalaModuleInfo],
      excludeDeps: Seq[InclExclRule],
      credentials: Seq[Credentials],
      createLogger: Option[CacheLogger],
      cacheDirectory: File,
      log: Logger
  ): CoursierConfiguration = {
    val coursierExcludeDeps = Inputs
      .exclusions(
        excludeDeps,
        scalaVer,
        scalaBinaryVer,
        log
      )
      .toVector
      .map {
        case (o, n) =>
          (o.value, n.value)
      }
      .sorted
    val autoScala = autoScalaLib && scalaModInfo.forall(
      _.overrideScalaVersion
    )
    val internalSbtScalaProvider = appConfig.provider.scalaProvider
    val sbtBootJars = internalSbtScalaProvider.jars()
    val sbtScalaVersion = internalSbtScalaProvider.version()
    val sbtScalaOrganization = "org.scala-lang" // always assuming sbt uses mainline scala
    Classpaths.warnResolversConflict(rs, log)
    CoursierConfiguration()
      .withResolvers(rs.toVector)
      .withInterProjectDependencies(interProjectDependencies.toVector)
      .withFallbackDependencies(fallbackDeps.toVector)
      .withExcludeDependencies(coursierExcludeDeps)
      .withAutoScalaLibrary(autoScala)
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
      .withCache(cacheDirectory)
      .withLog(log)
  }

  def coursierConfigurationTask: Def.Initialize[Task[CoursierConfiguration]] = Def.task {
    coursierConfiguration(
      csrRecursiveResolvers.value,
      csrInterProjectDependencies.value.toVector,
      csrFallbackDependencies.value,
      appConfiguration.value,
      None,
      csrMavenProfiles.value,
      scalaOrganization.value,
      scalaVersion.value,
      scalaBinaryVersion.value,
      autoScalaLibrary.value,
      scalaModuleInfo.value,
      allExcludeDependencies.value,
      CoursierInputsTasks.credentialsTask.value,
      csrLogger.value,
      csrCacheDirectory.value,
      streams.value.log
    )
  }

  def updateClassifierConfigurationTask: Def.Initialize[Task[CoursierConfiguration]] = Def.task {
    coursierConfiguration(
      csrRecursiveResolvers.value,
      csrInterProjectDependencies.value.toVector,
      csrFallbackDependencies.value,
      appConfiguration.value,
      Some(transitiveClassifiers.value.map(Classifier(_))),
      csrMavenProfiles.value,
      scalaOrganization.value,
      scalaVersion.value,
      scalaBinaryVersion.value,
      autoScalaLibrary.value,
      scalaModuleInfo.value,
      allExcludeDependencies.value,
      CoursierInputsTasks.credentialsTask.value,
      csrLogger.value,
      csrCacheDirectory.value,
      streams.value.log
    )
  }

  def updateSbtClassifierConfigurationTask: Def.Initialize[Task[CoursierConfiguration]] = Def.task {
    coursierConfiguration(
      csrSbtResolvers.value,
      Vector(),
      csrFallbackDependencies.value,
      appConfiguration.value,
      None,
      csrMavenProfiles.value,
      scalaOrganization.value,
      scalaVersion.value,
      scalaBinaryVersion.value,
      autoScalaLibrary.value,
      scalaModuleInfo.value,
      allExcludeDependencies.value,
      CoursierInputsTasks.credentialsTask.value,
      csrLogger.value,
      csrCacheDirectory.value,
      streams.value.log
    )
  }

  def scalaCompilerBridgeConfigurationTask: Def.Initialize[Task[CoursierConfiguration]] = Def.task {
    coursierConfiguration(
      csrResolvers.value,
      Vector(),
      csrFallbackDependencies.value,
      appConfiguration.value,
      None,
      csrMavenProfiles.value,
      scalaOrganization.value,
      scalaVersion.value,
      scalaBinaryVersion.value,
      autoScalaLibrary.value,
      scalaModuleInfo.value,
      allExcludeDependencies.value,
      CoursierInputsTasks.credentialsTask.value,
      csrLogger.value,
      csrCacheDirectory.value,
      streams.value.log
    )
  }

  def coursierLoggerTask: Def.Initialize[Task[Option[CacheLogger]]] = Def.task {
    val st = Keys.streams.value
    val progress = (ThisBuild / useSuperShell).value
    if (progress) None
    else Some(new CoursierLogger(st.log))
  }

  class CoursierLogger(logger: Logger) extends CacheLogger {
    override def downloadedArtifact(url: String, success: Boolean): Unit =
      logger.debug(s"downloaded $url")
  }

  def publicationsSetting(packageConfigs: Seq[(Configuration, CConfiguration)]): Def.Setting[_] = {
    csrPublications := CoursierArtifactsTasks.coursierPublicationsTask(packageConfigs: _*).value
  }

  private[sbt] def registerCredentials(creds: IvyCredentials): Unit = {
    val d = IvyCredentials.toDirect(creds)
    credentialRegistry.put((d.host, d.realm), d)
    ()
  }

  // This emulates Ivy's credential registration which basically keeps mutating global registry
  def allCredentialsTask: Def.Initialize[Task[Seq[IvyCredentials]]] = Def.task {
    import scala.collection.JavaConverters._
    (Keys.credentials in ThisBuild).value foreach registerCredentials
    (Keys.credentials in LocalRootProject).value foreach registerCredentials
    Keys.credentials.value foreach registerCredentials
    credentialRegistry.values.asScala.toVector
  }
}
