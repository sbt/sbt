/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package coursier.lmcoursier
final class CoursierConfiguration private (
  val log: Option[xsbti.Logger],
  val resolvers: Vector[sbt.librarymanagement.Resolver],
  val reorderResolvers: Boolean,
  val parallelDownloads: Int,
  val maxIterations: Int,
  val sbtScalaOrganization: Option[String],
  val sbtScalaVersion: Option[String],
  val sbtScalaJars: Vector[java.io.File],
  val interProjectDependencies: Vector[coursier.core.Project],
  val excludeDependencies: Vector[(String, String)],
  val fallbackDependencies: Vector[coursier.lmcoursier.FallbackDependency],
  val autoScalaLibrary: Boolean,
  val hasClassifiers: Boolean,
  val classifiers: Vector[String],
  val mavenProfiles: Vector[String],
  val scalaOrganization: Option[String],
  val scalaVersion: Option[String],
  val authenticationByRepositoryId: Vector[(String, coursier.core.Authentication)],
  val credentials: Seq[coursier.credentials.Credentials],
  val logger: Option[coursier.cache.CacheLogger],
  val cache: Option[java.io.File]) extends Serializable {
  
  private def this() = this(None, sbt.librarymanagement.Resolver.defaults, true, 6, 100, None, None, Vector.empty, Vector.empty, Vector.empty, Vector.empty, true, false, Vector.empty, Vector.empty, None, None, Vector.empty, Vector.empty, None, None)
  
  override def equals(o: Any): Boolean = o match {
    case x: CoursierConfiguration => (this.log == x.log) && (this.resolvers == x.resolvers) && (this.reorderResolvers == x.reorderResolvers) && (this.parallelDownloads == x.parallelDownloads) && (this.maxIterations == x.maxIterations) && (this.sbtScalaOrganization == x.sbtScalaOrganization) && (this.sbtScalaVersion == x.sbtScalaVersion) && (this.sbtScalaJars == x.sbtScalaJars) && (this.interProjectDependencies == x.interProjectDependencies) && (this.excludeDependencies == x.excludeDependencies) && (this.fallbackDependencies == x.fallbackDependencies) && (this.autoScalaLibrary == x.autoScalaLibrary) && (this.hasClassifiers == x.hasClassifiers) && (this.classifiers == x.classifiers) && (this.mavenProfiles == x.mavenProfiles) && (this.scalaOrganization == x.scalaOrganization) && (this.scalaVersion == x.scalaVersion) && (this.authenticationByRepositoryId == x.authenticationByRepositoryId) && (this.credentials == x.credentials) && (this.logger == x.logger) && (this.cache == x.cache)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (17 + "coursier.lmcoursier.CoursierConfiguration".##) + log.##) + resolvers.##) + reorderResolvers.##) + parallelDownloads.##) + maxIterations.##) + sbtScalaOrganization.##) + sbtScalaVersion.##) + sbtScalaJars.##) + interProjectDependencies.##) + excludeDependencies.##) + fallbackDependencies.##) + autoScalaLibrary.##) + hasClassifiers.##) + classifiers.##) + mavenProfiles.##) + scalaOrganization.##) + scalaVersion.##) + authenticationByRepositoryId.##) + credentials.##) + logger.##) + cache.##)
  }
  override def toString: String = {
    "CoursierConfiguration(" + log + ", " + resolvers + ", " + reorderResolvers + ", " + parallelDownloads + ", " + maxIterations + ", " + sbtScalaOrganization + ", " + sbtScalaVersion + ", " + sbtScalaJars + ", " + interProjectDependencies + ", " + excludeDependencies + ", " + fallbackDependencies + ", " + autoScalaLibrary + ", " + hasClassifiers + ", " + classifiers + ", " + mavenProfiles + ", " + scalaOrganization + ", " + scalaVersion + ", " + authenticationByRepositoryId + ", " + credentials + ", " + logger + ", " + cache + ")"
  }
  private[this] def copy(log: Option[xsbti.Logger] = log, resolvers: Vector[sbt.librarymanagement.Resolver] = resolvers, reorderResolvers: Boolean = reorderResolvers, parallelDownloads: Int = parallelDownloads, maxIterations: Int = maxIterations, sbtScalaOrganization: Option[String] = sbtScalaOrganization, sbtScalaVersion: Option[String] = sbtScalaVersion, sbtScalaJars: Vector[java.io.File] = sbtScalaJars, interProjectDependencies: Vector[coursier.core.Project] = interProjectDependencies, excludeDependencies: Vector[(String, String)] = excludeDependencies, fallbackDependencies: Vector[coursier.lmcoursier.FallbackDependency] = fallbackDependencies, autoScalaLibrary: Boolean = autoScalaLibrary, hasClassifiers: Boolean = hasClassifiers, classifiers: Vector[String] = classifiers, mavenProfiles: Vector[String] = mavenProfiles, scalaOrganization: Option[String] = scalaOrganization, scalaVersion: Option[String] = scalaVersion, authenticationByRepositoryId: Vector[(String, coursier.core.Authentication)] = authenticationByRepositoryId, credentials: Seq[coursier.credentials.Credentials] = credentials, logger: Option[coursier.cache.CacheLogger] = logger, cache: Option[java.io.File] = cache): CoursierConfiguration = {
    new CoursierConfiguration(log, resolvers, reorderResolvers, parallelDownloads, maxIterations, sbtScalaOrganization, sbtScalaVersion, sbtScalaJars, interProjectDependencies, excludeDependencies, fallbackDependencies, autoScalaLibrary, hasClassifiers, classifiers, mavenProfiles, scalaOrganization, scalaVersion, authenticationByRepositoryId, credentials, logger, cache)
  }
  def withLog(log: Option[xsbti.Logger]): CoursierConfiguration = {
    copy(log = log)
  }
  def withLog(log: xsbti.Logger): CoursierConfiguration = {
    copy(log = Option(log))
  }
  def withResolvers(resolvers: Vector[sbt.librarymanagement.Resolver]): CoursierConfiguration = {
    copy(resolvers = resolvers)
  }
  def withReorderResolvers(reorderResolvers: Boolean): CoursierConfiguration = {
    copy(reorderResolvers = reorderResolvers)
  }
  def withParallelDownloads(parallelDownloads: Int): CoursierConfiguration = {
    copy(parallelDownloads = parallelDownloads)
  }
  def withMaxIterations(maxIterations: Int): CoursierConfiguration = {
    copy(maxIterations = maxIterations)
  }
  def withSbtScalaOrganization(sbtScalaOrganization: Option[String]): CoursierConfiguration = {
    copy(sbtScalaOrganization = sbtScalaOrganization)
  }
  def withSbtScalaOrganization(sbtScalaOrganization: String): CoursierConfiguration = {
    copy(sbtScalaOrganization = Option(sbtScalaOrganization))
  }
  def withSbtScalaVersion(sbtScalaVersion: Option[String]): CoursierConfiguration = {
    copy(sbtScalaVersion = sbtScalaVersion)
  }
  def withSbtScalaVersion(sbtScalaVersion: String): CoursierConfiguration = {
    copy(sbtScalaVersion = Option(sbtScalaVersion))
  }
  def withSbtScalaJars(sbtScalaJars: Vector[java.io.File]): CoursierConfiguration = {
    copy(sbtScalaJars = sbtScalaJars)
  }
  def withInterProjectDependencies(interProjectDependencies: Vector[coursier.core.Project]): CoursierConfiguration = {
    copy(interProjectDependencies = interProjectDependencies)
  }
  def withExcludeDependencies(excludeDependencies: Vector[(String, String)]): CoursierConfiguration = {
    copy(excludeDependencies = excludeDependencies)
  }
  def withFallbackDependencies(fallbackDependencies: Vector[coursier.lmcoursier.FallbackDependency]): CoursierConfiguration = {
    copy(fallbackDependencies = fallbackDependencies)
  }
  def withAutoScalaLibrary(autoScalaLibrary: Boolean): CoursierConfiguration = {
    copy(autoScalaLibrary = autoScalaLibrary)
  }
  def withHasClassifiers(hasClassifiers: Boolean): CoursierConfiguration = {
    copy(hasClassifiers = hasClassifiers)
  }
  def withClassifiers(classifiers: Vector[String]): CoursierConfiguration = {
    copy(classifiers = classifiers)
  }
  def withMavenProfiles(mavenProfiles: Vector[String]): CoursierConfiguration = {
    copy(mavenProfiles = mavenProfiles)
  }
  def withScalaOrganization(scalaOrganization: Option[String]): CoursierConfiguration = {
    copy(scalaOrganization = scalaOrganization)
  }
  def withScalaOrganization(scalaOrganization: String): CoursierConfiguration = {
    copy(scalaOrganization = Option(scalaOrganization))
  }
  def withScalaVersion(scalaVersion: Option[String]): CoursierConfiguration = {
    copy(scalaVersion = scalaVersion)
  }
  def withScalaVersion(scalaVersion: String): CoursierConfiguration = {
    copy(scalaVersion = Option(scalaVersion))
  }
  def withAuthenticationByRepositoryId(authenticationByRepositoryId: Vector[(String, coursier.core.Authentication)]): CoursierConfiguration = {
    copy(authenticationByRepositoryId = authenticationByRepositoryId)
  }
  def withCredentials(credentials: Seq[coursier.credentials.Credentials]): CoursierConfiguration = {
    copy(credentials = credentials)
  }
  def withLogger(logger: Option[coursier.cache.CacheLogger]): CoursierConfiguration = {
    copy(logger = logger)
  }
  def withLogger(logger: coursier.cache.CacheLogger): CoursierConfiguration = {
    copy(logger = Option(logger))
  }
  def withCache(cache: Option[java.io.File]): CoursierConfiguration = {
    copy(cache = cache)
  }
  def withCache(cache: java.io.File): CoursierConfiguration = {
    copy(cache = Option(cache))
  }
}
object CoursierConfiguration {
  
  def apply(): CoursierConfiguration = new CoursierConfiguration()
  def apply(log: Option[xsbti.Logger], resolvers: Vector[sbt.librarymanagement.Resolver], reorderResolvers: Boolean, parallelDownloads: Int, maxIterations: Int, sbtScalaOrganization: Option[String], sbtScalaVersion: Option[String], sbtScalaJars: Vector[java.io.File], interProjectDependencies: Vector[coursier.core.Project], excludeDependencies: Vector[(String, String)], fallbackDependencies: Vector[coursier.lmcoursier.FallbackDependency], autoScalaLibrary: Boolean, hasClassifiers: Boolean, classifiers: Vector[String], mavenProfiles: Vector[String], scalaOrganization: Option[String], scalaVersion: Option[String], authenticationByRepositoryId: Vector[(String, coursier.core.Authentication)], credentials: Seq[coursier.credentials.Credentials], logger: Option[coursier.cache.CacheLogger], cache: Option[java.io.File]): CoursierConfiguration = new CoursierConfiguration(log, resolvers, reorderResolvers, parallelDownloads, maxIterations, sbtScalaOrganization, sbtScalaVersion, sbtScalaJars, interProjectDependencies, excludeDependencies, fallbackDependencies, autoScalaLibrary, hasClassifiers, classifiers, mavenProfiles, scalaOrganization, scalaVersion, authenticationByRepositoryId, credentials, logger, cache)
  def apply(log: xsbti.Logger, resolvers: Vector[sbt.librarymanagement.Resolver], reorderResolvers: Boolean, parallelDownloads: Int, maxIterations: Int, sbtScalaOrganization: String, sbtScalaVersion: String, sbtScalaJars: Vector[java.io.File], interProjectDependencies: Vector[coursier.core.Project], excludeDependencies: Vector[(String, String)], fallbackDependencies: Vector[coursier.lmcoursier.FallbackDependency], autoScalaLibrary: Boolean, hasClassifiers: Boolean, classifiers: Vector[String], mavenProfiles: Vector[String], scalaOrganization: String, scalaVersion: String, authenticationByRepositoryId: Vector[(String, coursier.core.Authentication)], credentials: Seq[coursier.credentials.Credentials], logger: coursier.cache.CacheLogger, cache: java.io.File): CoursierConfiguration = new CoursierConfiguration(Option(log), resolvers, reorderResolvers, parallelDownloads, maxIterations, Option(sbtScalaOrganization), Option(sbtScalaVersion), sbtScalaJars, interProjectDependencies, excludeDependencies, fallbackDependencies, autoScalaLibrary, hasClassifiers, classifiers, mavenProfiles, Option(scalaOrganization), Option(scalaVersion), authenticationByRepositoryId, credentials, Option(logger), Option(cache))
}
