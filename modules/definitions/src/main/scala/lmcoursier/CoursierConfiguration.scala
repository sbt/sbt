package lmcoursier

import java.io.File

import dataclass.data
import coursier.cache.CacheDefaults
import lmcoursier.credentials.Credentials
import lmcoursier.definitions.{Authentication, CacheLogger, CachePolicy, FromCoursier, Module, ModuleMatchers, Project, Reconciliation, Strict}
import sbt.librarymanagement.{Resolver, UpdateConfiguration, ModuleID, CrossVersion, ModuleInfo, ModuleDescriptorConfiguration}
import xsbti.Logger

import scala.concurrent.duration.Duration
import java.net.URL
import java.net.URLClassLoader

@data class CoursierConfiguration(
  log: Option[Logger] = None,
  resolvers: Vector[Resolver] = Resolver.defaults,
  parallelDownloads: Int = 6,
  maxIterations: Int = 100,
  sbtScalaOrganization: Option[String] = None,
  sbtScalaVersion: Option[String] = None,
  sbtScalaJars: Vector[File] = Vector.empty,
  interProjectDependencies: Vector[Project] = Vector.empty,
  excludeDependencies: Vector[(String, String)] = Vector.empty,
  fallbackDependencies: Vector[FallbackDependency] = Vector.empty,
  autoScalaLibrary: Boolean = true,
  hasClassifiers: Boolean = false,
  classifiers: Vector[String] = Vector.empty,
  mavenProfiles: Vector[String] = Vector.empty,
  scalaOrganization: Option[String] = None,
  scalaVersion: Option[String] = None,
  authenticationByRepositoryId: Vector[(String, Authentication)] = Vector.empty,
  credentials: Seq[Credentials] = Vector.empty,
  logger: Option[CacheLogger] = None,
  cache: Option[File] = None,
  @since
  ivyHome: Option[File] = None,
  @since
  followHttpToHttpsRedirections: Option[Boolean] = None,
  @since
  strict: Option[Strict] = None,
  extraProjects: Vector[Project] = Vector.empty,
  forceVersions: Vector[(Module, String)] = Vector.empty,
  @since
  reconciliation: Vector[(ModuleMatchers, Reconciliation)] = Vector.empty,
  @since
  classpathOrder: Boolean = true,
  @since
  verbosityLevel: Int = 0,
  ttl: Option[Duration] = CacheDefaults.ttl,
  checksums: Vector[Option[String]] = CacheDefaults.checksums.toVector,
  cachePolicies: Vector[CachePolicy] = CacheDefaults.cachePolicies.toVector.map(FromCoursier.cachePolicy),
  @since
  missingOk: Boolean = false,
  @since
  sbtClassifiers: Boolean = false,
  @since
  providedInCompile: Boolean = false, // unused, kept for binary compatibility
  @since
  protocolHandlerDependencies: Seq[ModuleID] = Vector.empty,
) {

  def withLog(log: Logger): CoursierConfiguration =
    withLog(Option(log))
  def withSbtScalaOrganization(sbtScalaOrganization: String): CoursierConfiguration =
    withSbtScalaOrganization(Option(sbtScalaOrganization))
  def withSbtScalaVersion(sbtScalaVersion: String): CoursierConfiguration =
    withSbtScalaVersion(Option(sbtScalaVersion))
  def withScalaOrganization(scalaOrganization: String): CoursierConfiguration =
    withScalaOrganization(Option(scalaOrganization))
  def withScalaVersion(scalaVersion: String): CoursierConfiguration =
    withScalaVersion(Option(scalaVersion))
  def withLogger(logger: CacheLogger): CoursierConfiguration =
    withLogger(Option(logger))
  def withCache(cache: File): CoursierConfiguration =
    withCache(Option(cache))
  def withIvyHome(ivyHome: File): CoursierConfiguration =
    withIvyHome(Option(ivyHome))
  def withFollowHttpToHttpsRedirections(followHttpToHttpsRedirections: Boolean): CoursierConfiguration =
    withFollowHttpToHttpsRedirections(Some(followHttpToHttpsRedirections))
  def withFollowHttpToHttpsRedirections(): CoursierConfiguration =
    withFollowHttpToHttpsRedirections(Some(true))
  def withStrict(strict: Strict): CoursierConfiguration =
    withStrict(Some(strict))
  def withTtl(ttl: Duration): CoursierConfiguration =
    withTtl(Some(ttl))
  def addRepositoryAuthentication(repositoryId: String, authentication: Authentication): CoursierConfiguration =
    withAuthenticationByRepositoryId(authenticationByRepositoryId :+ (repositoryId, authentication))

  def withUpdateConfiguration(conf: UpdateConfiguration): CoursierConfiguration =
    withMissingOk(conf.missingOk)
}

object CoursierConfiguration {

  @deprecated("Legacy cache location support was dropped, this method does nothing.", "2.0.0-RC6-10")
  def checkLegacyCache(): Unit = ()

  def apply(
    log: Logger,
    resolvers: Vector[Resolver],
    parallelDownloads: Int,
    maxIterations: Int,
    sbtScalaOrganization: String,
    sbtScalaVersion: String,
    sbtScalaJars: Vector[File],
    interProjectDependencies: Vector[Project],
    excludeDependencies: Vector[(String, String)],
    fallbackDependencies: Vector[FallbackDependency],
    autoScalaLibrary: Boolean,
    hasClassifiers: Boolean,
    classifiers: Vector[String],
    mavenProfiles: Vector[String],
    scalaOrganization: String,
    scalaVersion: String,
    authenticationByRepositoryId: Vector[(String, Authentication)],
    credentials: Seq[Credentials],
    logger: CacheLogger,
    cache: File
  ): CoursierConfiguration =
    CoursierConfiguration(
      Option(log),
      resolvers,
      parallelDownloads,
      maxIterations,
      Option(sbtScalaOrganization),
      Option(sbtScalaVersion),
      sbtScalaJars,
      interProjectDependencies,
      excludeDependencies,
      fallbackDependencies,
      autoScalaLibrary,
      hasClassifiers,
      classifiers,
      mavenProfiles,
      Option(scalaOrganization),
      Option(scalaVersion),
      authenticationByRepositoryId,
      credentials,
      Option(logger),
      Option(cache)
    ) /* no need to touch this 'apply'; @data above is doing the hard work */
}
