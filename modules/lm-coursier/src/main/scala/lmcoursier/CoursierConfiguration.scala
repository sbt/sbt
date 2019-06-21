/**
 * This code USED TO BE generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO EDIT MANUALLY from now on
package lmcoursier

import java.io.File

import lmcoursier.credentials.Credentials
import lmcoursier.definitions.{Authentication, CacheLogger, Project}
import sbt.librarymanagement.Resolver
import xsbti.Logger

final class CoursierConfiguration private (
  val log: Option[Logger],
  val resolvers: Vector[Resolver],
  val parallelDownloads: Int,
  val maxIterations: Int,
  val sbtScalaOrganization: Option[String],
  val sbtScalaVersion: Option[String],
  val sbtScalaJars: Vector[File],
  val interProjectDependencies: Vector[Project],
  val excludeDependencies: Vector[(String, String)],
  val fallbackDependencies: Vector[FallbackDependency],
  val autoScalaLibrary: Boolean,
  val hasClassifiers: Boolean,
  val classifiers: Vector[String],
  val mavenProfiles: Vector[String],
  val scalaOrganization: Option[String],
  val scalaVersion: Option[String],
  val authenticationByRepositoryId: Vector[(String, Authentication)],
  val credentials: Seq[Credentials],
  val logger: Option[CacheLogger],
  val cache: Option[File],
  val ivyHome: Option[File],
  val followHttpToHttpsRedirections: Option[Boolean]
) extends Serializable {
  
  private def this() =
    this(
      None,
      Resolver.defaults,
      6,
      100,
      None,
      None,
      Vector.empty,
      Vector.empty,
      Vector.empty,
      Vector.empty,
      true,
      false,
      Vector.empty,
      Vector.empty,
      None,
      None,
      Vector.empty,
      Vector.empty,
      None,
      None,
      None,
      None
    )
  
  override def equals(o: Any): Boolean =
    o match {
      case other: CoursierConfiguration =>
        log == other.log &&
          resolvers == other.resolvers &&
          parallelDownloads == other.parallelDownloads &&
          maxIterations == other.maxIterations &&
          sbtScalaOrganization == other.sbtScalaOrganization &&
          sbtScalaVersion == other.sbtScalaVersion &&
          sbtScalaJars == other.sbtScalaJars &&
          interProjectDependencies == other.interProjectDependencies &&
          excludeDependencies == other.excludeDependencies &&
          fallbackDependencies == other.fallbackDependencies &&
          autoScalaLibrary == other.autoScalaLibrary &&
          hasClassifiers == other.hasClassifiers &&
          classifiers == other.classifiers &&
          mavenProfiles == other.mavenProfiles &&
          scalaOrganization == other.scalaOrganization &&
          scalaVersion == other.scalaVersion &&
          authenticationByRepositoryId == other.authenticationByRepositoryId &&
          credentials == other.credentials &&
          logger == other.logger &&
          cache == other.cache &&
          ivyHome == other.ivyHome &&
          followHttpToHttpsRedirections == other.followHttpToHttpsRedirections
      case _ => false
    }

  override def hashCode: Int = {
    var code = 37 * (17 + "lmcoursier.CoursierConfiguration".##)
    code = 37 * (code + log.##)
    code = 37 * (code + resolvers.##)
    code = 37 * (code + parallelDownloads.##)
    code = 37 * (code + maxIterations.##)
    code = 37 * (code + sbtScalaOrganization.##)
    code = 37 * (code + sbtScalaVersion.##)
    code = 37 * (code + sbtScalaJars.##)
    code = 37 * (code + interProjectDependencies.##)
    code = 37 * (code + excludeDependencies.##)
    code = 37 * (code + fallbackDependencies.##)
    code = 37 * (code + autoScalaLibrary.##)
    code = 37 * (code + hasClassifiers.##)
    code = 37 * (code + classifiers.##)
    code = 37 * (code + mavenProfiles.##)
    code = 37 * (code + scalaOrganization.##)
    code = 37 * (code + scalaVersion.##)
    code = 37 * (code + authenticationByRepositoryId.##)
    code = 37 * (code + credentials.##)
    code = 37 * (code + logger.##)
    code = 37 * (code + cache.##)
    code = 37 * (code + ivyHome.##)
    code = 37 * (code + followHttpToHttpsRedirections.##)
    code
  }

  override def toString: String =
    s"CoursierConfiguration($log, $resolvers, $parallelDownloads, $maxIterations, $sbtScalaOrganization, $sbtScalaVersion, $sbtScalaJars, $interProjectDependencies, $excludeDependencies, $fallbackDependencies, $autoScalaLibrary, $hasClassifiers, $classifiers, $mavenProfiles, $scalaOrganization, $scalaVersion, $authenticationByRepositoryId, $credentials, $logger, $cache, $ivyHome, $followHttpToHttpsRedirections)"

  private[this] def copy(
    log: Option[Logger] = log,
    resolvers: Vector[Resolver] = resolvers,
    parallelDownloads: Int = parallelDownloads,
    maxIterations: Int = maxIterations,
    sbtScalaOrganization: Option[String] = sbtScalaOrganization,
    sbtScalaVersion: Option[String] = sbtScalaVersion,
    sbtScalaJars: Vector[File] = sbtScalaJars,
    interProjectDependencies: Vector[Project] = interProjectDependencies,
    excludeDependencies: Vector[(String, String)] = excludeDependencies,
    fallbackDependencies: Vector[FallbackDependency] = fallbackDependencies,
    autoScalaLibrary: Boolean = autoScalaLibrary,
    hasClassifiers: Boolean = hasClassifiers,
    classifiers: Vector[String] = classifiers,
    mavenProfiles: Vector[String] = mavenProfiles,
    scalaOrganization: Option[String] = scalaOrganization,
    scalaVersion: Option[String] = scalaVersion,
    authenticationByRepositoryId: Vector[(String, Authentication)] = authenticationByRepositoryId,
    credentials: Seq[Credentials] = credentials,
    logger: Option[CacheLogger] = logger,
    cache: Option[File] = cache,
    ivyHome: Option[File] = ivyHome,
    followHttpToHttpsRedirections: Option[Boolean] = followHttpToHttpsRedirections
  ): CoursierConfiguration =
    new CoursierConfiguration(
      log,
      resolvers,
      parallelDownloads,
      maxIterations,
      sbtScalaOrganization,
      sbtScalaVersion,
      sbtScalaJars,
      interProjectDependencies,
      excludeDependencies,
      fallbackDependencies,
      autoScalaLibrary,
      hasClassifiers,
      classifiers,
      mavenProfiles,
      scalaOrganization,
      scalaVersion,
      authenticationByRepositoryId,
      credentials,
      logger,
      cache,
      ivyHome,
      followHttpToHttpsRedirections
    )

  def withLog(log: Option[Logger]): CoursierConfiguration =
    copy(log = log)

  def withLog(log: Logger): CoursierConfiguration =
    copy(log = Option(log))

  def withResolvers(resolvers: Vector[Resolver]): CoursierConfiguration =
    copy(resolvers = resolvers)

  def withParallelDownloads(parallelDownloads: Int): CoursierConfiguration =
    copy(parallelDownloads = parallelDownloads)

  def withMaxIterations(maxIterations: Int): CoursierConfiguration =
    copy(maxIterations = maxIterations)

  def withSbtScalaOrganization(sbtScalaOrganization: Option[String]): CoursierConfiguration =
    copy(sbtScalaOrganization = sbtScalaOrganization)

  def withSbtScalaOrganization(sbtScalaOrganization: String): CoursierConfiguration =
    copy(sbtScalaOrganization = Option(sbtScalaOrganization))

  def withSbtScalaVersion(sbtScalaVersion: Option[String]): CoursierConfiguration =
    copy(sbtScalaVersion = sbtScalaVersion)

  def withSbtScalaVersion(sbtScalaVersion: String): CoursierConfiguration =
    copy(sbtScalaVersion = Option(sbtScalaVersion))

  def withSbtScalaJars(sbtScalaJars: Vector[File]): CoursierConfiguration =
    copy(sbtScalaJars = sbtScalaJars)

  def withInterProjectDependencies(interProjectDependencies: Vector[Project]): CoursierConfiguration =
    copy(interProjectDependencies = interProjectDependencies)

  def withExcludeDependencies(excludeDependencies: Vector[(String, String)]): CoursierConfiguration =
    copy(excludeDependencies = excludeDependencies)

  def withFallbackDependencies(fallbackDependencies: Vector[FallbackDependency]): CoursierConfiguration =
    copy(fallbackDependencies = fallbackDependencies)

  def withAutoScalaLibrary(autoScalaLibrary: Boolean): CoursierConfiguration =
    copy(autoScalaLibrary = autoScalaLibrary)

  def withHasClassifiers(hasClassifiers: Boolean): CoursierConfiguration =
    copy(hasClassifiers = hasClassifiers)

  def withClassifiers(classifiers: Vector[String]): CoursierConfiguration =
    copy(classifiers = classifiers)

  def withMavenProfiles(mavenProfiles: Vector[String]): CoursierConfiguration =
    copy(mavenProfiles = mavenProfiles)

  def withScalaOrganization(scalaOrganization: Option[String]): CoursierConfiguration =
    copy(scalaOrganization = scalaOrganization)

  def withScalaOrganization(scalaOrganization: String): CoursierConfiguration =
    copy(scalaOrganization = Option(scalaOrganization))

  def withScalaVersion(scalaVersion: Option[String]): CoursierConfiguration =
    copy(scalaVersion = scalaVersion)

  def withScalaVersion(scalaVersion: String): CoursierConfiguration =
    copy(scalaVersion = Option(scalaVersion))

  def withAuthenticationByRepositoryId(authenticationByRepositoryId: Vector[(String, Authentication)]): CoursierConfiguration =
    copy(authenticationByRepositoryId = authenticationByRepositoryId)

  def withCredentials(credentials: Seq[Credentials]): CoursierConfiguration =
    copy(credentials = credentials)

  def withLogger(logger: Option[CacheLogger]): CoursierConfiguration =
    copy(logger = logger)

  def withLogger(logger: CacheLogger): CoursierConfiguration =
    copy(logger = Option(logger))

  def withCache(cache: Option[File]): CoursierConfiguration =
    copy(cache = cache)

  def withCache(cache: File): CoursierConfiguration =
    copy(cache = Option(cache))

  def withIvyHome(ivyHomeOpt: Option[File]): CoursierConfiguration =
    copy(ivyHome = ivyHomeOpt)
  def withIvyHome(ivyHome: File): CoursierConfiguration =
    copy(ivyHome = Option(ivyHome))

  def withFollowHttpToHttpsRedirections(followHttpToHttpsRedirectionsOpt: Option[Boolean]): CoursierConfiguration =
    copy(followHttpToHttpsRedirections = followHttpToHttpsRedirectionsOpt)
  def withFollowHttpToHttpsRedirections(followHttpToHttpsRedirections: Boolean): CoursierConfiguration =
    copy(followHttpToHttpsRedirections = Some(followHttpToHttpsRedirections))
  def withFollowHttpToHttpsRedirections(): CoursierConfiguration =
    copy(followHttpToHttpsRedirections = Some(true))
}

object CoursierConfiguration {

  def apply(): CoursierConfiguration =
    new CoursierConfiguration()

  def apply(
    log: Option[Logger],
    resolvers: Vector[Resolver],
    parallelDownloads: Int, 
    maxIterations: Int, 
    sbtScalaOrganization: Option[String], 
    sbtScalaVersion: Option[String], 
    sbtScalaJars: Vector[File],
    interProjectDependencies: Vector[Project],
    excludeDependencies: Vector[(String, String)], 
    fallbackDependencies: Vector[FallbackDependency],
    autoScalaLibrary: Boolean, 
    hasClassifiers: Boolean, 
    classifiers: Vector[String], 
    mavenProfiles: Vector[String], 
    scalaOrganization: Option[String], 
    scalaVersion: Option[String], 
    authenticationByRepositoryId: Vector[(String, Authentication)],
    credentials: Seq[Credentials],
    logger: Option[CacheLogger],
    cache: Option[File]
  ): CoursierConfiguration =
    new CoursierConfiguration(
      log, 
      resolvers, 
      parallelDownloads, 
      maxIterations, 
      sbtScalaOrganization, 
      sbtScalaVersion, 
      sbtScalaJars, 
      interProjectDependencies, 
      excludeDependencies, 
      fallbackDependencies, 
      autoScalaLibrary, 
      hasClassifiers, 
      classifiers, 
      mavenProfiles, 
      scalaOrganization, 
      scalaVersion, 
      authenticationByRepositoryId, 
      credentials, 
      logger, 
      cache,
      None,
      None
    )
  
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
    new CoursierConfiguration(
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
      Option(cache),
      None,
      None
    )

  def apply(
    log: Option[Logger],
    resolvers: Vector[Resolver],
    parallelDownloads: Int,
    maxIterations: Int,
    sbtScalaOrganization: Option[String],
    sbtScalaVersion: Option[String],
    sbtScalaJars: Vector[File],
    interProjectDependencies: Vector[Project],
    excludeDependencies: Vector[(String, String)],
    fallbackDependencies: Vector[FallbackDependency],
    autoScalaLibrary: Boolean,
    hasClassifiers: Boolean,
    classifiers: Vector[String],
    mavenProfiles: Vector[String],
    scalaOrganization: Option[String],
    scalaVersion: Option[String],
    authenticationByRepositoryId: Vector[(String, Authentication)],
    credentials: Seq[Credentials],
    logger: Option[CacheLogger],
    cache: Option[File],
    ivyHome: Option[File]
  ): CoursierConfiguration =
    new CoursierConfiguration(
      log,
      resolvers,
      parallelDownloads,
      maxIterations,
      sbtScalaOrganization,
      sbtScalaVersion,
      sbtScalaJars,
      interProjectDependencies,
      excludeDependencies,
      fallbackDependencies,
      autoScalaLibrary,
      hasClassifiers,
      classifiers,
      mavenProfiles,
      scalaOrganization,
      scalaVersion,
      authenticationByRepositoryId,
      credentials,
      logger,
      cache,
      ivyHome,
      None
    )
}
