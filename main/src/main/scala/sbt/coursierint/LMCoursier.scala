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
  Project => CProject,
  ModuleMatchers,
  Reconciliation,
}
import lmcoursier._
import lmcoursier.credentials.Credentials
import Keys._
import sbt.librarymanagement._
import sbt.librarymanagement.ivy.{
  Credentials => IvyCredentials,
  DirectCredentials,
  FileCredentials
}
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

  def relaxedForAllModules: Seq[(ModuleMatchers, Reconciliation)] =
    Vector((ModuleMatchers.all, Reconciliation.Relaxed))

  def coursierConfiguration(
      rs: Seq[Resolver],
      interProjectDependencies: Seq[CProject],
      extraProjects: Seq[CProject],
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
      reconciliation: Seq[(ModuleMatchers, Reconciliation)],
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
      .withExtraProjects(extraProjects.toVector)
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
      .withReconciliation(reconciliation.toVector)
      .withLog(log)
  }

  def coursierConfigurationTask: Def.Initialize[Task[CoursierConfiguration]] = Def.task {
    coursierConfiguration(
      csrRecursiveResolvers.value,
      csrInterProjectDependencies.value.toVector,
      csrExtraProjects.value.toVector,
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
      csrReconciliations.value,
      streams.value.log
    )
  }

  def updateClassifierConfigurationTask: Def.Initialize[Task[CoursierConfiguration]] = Def.task {
    coursierConfiguration(
      csrRecursiveResolvers.value,
      csrInterProjectDependencies.value.toVector,
      csrExtraProjects.value.toVector,
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
      csrReconciliations.value,
      streams.value.log
    )
  }

  def updateSbtClassifierConfigurationTask: Def.Initialize[Task[CoursierConfiguration]] = Def.task {
    coursierConfiguration(
      csrSbtResolvers.value,
      Vector(),
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
      csrReconciliations.value,
      streams.value.log
    )
  }

  def scalaCompilerBridgeConfigurationTask: Def.Initialize[Task[CoursierConfiguration]] = Def.task {
    coursierConfiguration(
      csrResolvers.value,
      Vector(),
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
      csrReconciliations.value,
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

  // This emulates Ivy's credential registration which basically keeps mutating global registry
  def allCredentialsTask: Def.Initialize[Task[Seq[IvyCredentials]]] = Def.task {
    val st = streams.value
    def registerCredentials(creds: IvyCredentials): Unit = {
      (creds match {
        case dc: DirectCredentials => Right[String, DirectCredentials](dc)
        case fc: FileCredentials   => IvyCredentials.loadCredentials(fc.path)
      }) match {
        case Left(err) => st.log.warn(err)
        case Right(d) =>
          credentialRegistry.put((d.host, d.realm), d)
          ()
      }
    }
    import scala.collection.JavaConverters._
    (Keys.credentials in ThisBuild).value foreach registerCredentials
    (Keys.credentials in LocalRootProject).value foreach registerCredentials
    Keys.credentials.value foreach registerCredentials
    credentialRegistry.values.asScala.toVector
  }
}
