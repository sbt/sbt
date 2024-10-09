package lmcoursier

import coursier.cache.CacheDefaults
import lmcoursier.credentials._
import lmcoursier.definitions._
import sbt.librarymanagement.{Resolver, UpdateConfiguration, ModuleID, CrossVersion, ModuleInfo, ModuleDescriptorConfiguration}
import xsbti.Logger

import scala.concurrent.duration.{Duration, FiniteDuration}
import java.io.File
import java.net.URL
import java.net.URLClassLoader

package object syntax {
  implicit class CoursierConfigurationModule(value: CoursierConfiguration.type) {
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
        Option(cache),
        ivyHome = None,
        followHttpToHttpsRedirections = None,
        strict = None,
        extraProjects = Vector.empty,
        forceVersions = Vector.empty,
        reconciliation = Vector.empty,
        classpathOrder = true,
        verbosityLevel = 0,
        ttl = CacheDefaults.ttl,
        checksums = CacheDefaults.checksums.toVector,
        cachePolicies = CacheDefaults.cachePolicies.toVector.map(FromCoursier.cachePolicy),
        missingOk = false,
        sbtClassifiers = false,
        providedInCompile = false,
        protocolHandlerDependencies = Vector.empty,
        retry = None,
        sameVersions = Nil,
      )
  }

  implicit class CoursierConfigurationOp(value: CoursierConfiguration) {
    def withLog(log: Logger): CoursierConfiguration =
      value.withLog(Option(log))
    def withSbtScalaOrganization(sbtScalaOrganization: String): CoursierConfiguration =
      value.withSbtScalaOrganization(Option(sbtScalaOrganization))
    def withSbtScalaVersion(sbtScalaVersion: String): CoursierConfiguration =
      value.withSbtScalaVersion(Option(sbtScalaVersion))
    def withScalaOrganization(scalaOrganization: String): CoursierConfiguration =
      value.withScalaOrganization(Option(scalaOrganization))
    def withScalaVersion(scalaVersion: String): CoursierConfiguration =
      value.withScalaVersion(Option(scalaVersion))
    def withLogger(logger: CacheLogger): CoursierConfiguration =
      value.withLogger(Option(logger))
    def withCache(cache: File): CoursierConfiguration =
      value.withCache(Option(cache))
    def withIvyHome(ivyHome: File): CoursierConfiguration =
      value.withIvyHome(Option(ivyHome))
    def withFollowHttpToHttpsRedirections(followHttpToHttpsRedirections: Boolean): CoursierConfiguration =
      value.withFollowHttpToHttpsRedirections(Some(followHttpToHttpsRedirections))
    def withFollowHttpToHttpsRedirections(): CoursierConfiguration =
      value.withFollowHttpToHttpsRedirections(Some(true))
    def withStrict(strict: Strict): CoursierConfiguration =
      value.withStrict(Some(strict))
    def withTtl(ttl: Duration): CoursierConfiguration =
      value.withTtl(Some(ttl))
    def addRepositoryAuthentication(repositoryId: String, authentication: Authentication): CoursierConfiguration =
      value.withAuthenticationByRepositoryId(value.authenticationByRepositoryId :+ (repositoryId, authentication))

    def withUpdateConfiguration(conf: UpdateConfiguration): CoursierConfiguration =
      value.withMissingOk(conf.missingOk)

    def withRetry(retry: (FiniteDuration, Int)): CoursierConfiguration =
      value.withRetry(Some((retry._1, retry._2)))
  }

  implicit class PublicationOp(value: Publication) {
    def attributes: Attributes =
      Attributes(value.`type`, value.classifier)

    def withAttributes(attributes: Attributes): Publication =
      value.withType(attributes.`type`)
        .withClassifier(attributes.classifier)
  }

  implicit class DependencyModule(value: Dependency.type) {
    def apply(
      module: Module,
      version: String,
      configuration: Configuration,
      exclusions: Set[(Organization, ModuleName)],
      attributes: Attributes,
      optional: Boolean,
      transitive: Boolean
    ): Dependency =
      Dependency(
        module,
        version,
        configuration,
        exclusions,
        Publication("", attributes.`type`, Extension(""), attributes.classifier),
        optional,
        transitive
      )
  }

  implicit class DependencyOp(value: Dependency) {
    def attributes: Attributes = value.publication.attributes

    def withAttributes(attributes: Attributes): Dependency =
      value.withPublication(
        value.publication
          .withType(attributes.`type`)
          .withClassifier(attributes.classifier)
      )
  }

  implicit class ModuleMatchersModule(value: ModuleMatchers.type) {
    def all: ModuleMatchers =
      ModuleMatchers(Set.empty, Set.empty)
    def only(organization: String, moduleName: String): ModuleMatchers =
      ModuleMatchers(Set.empty, Set(Module(Organization(organization), ModuleName(moduleName), Map())), includeByDefault = false)
    def only(mod: Module): ModuleMatchers =
      ModuleMatchers(Set.empty, Set(mod), includeByDefault = false)
  }

  implicit class StrictOp(value: Strict) {
    def addInclude(include: (String, String)*): Strict =
      value.withInclude(value.include ++ include)
    def addExclude(exclude: (String, String)*): Strict =
      value.withExclude(value.exclude ++ exclude)
  }

  implicit class AuthenticationModule(value: Authentication.type) {
    def apply(headers: Seq[(String, String)]): Authentication =
      Authentication("", "").withHeaders(headers)
  }

  implicit class DirectCredentialsModule(value: DirectCredentials.type) {
    def apply(host: String, username: String, password: String, realm: String): DirectCredentials =
      DirectCredentials(host, username, password, Option(realm))
    def apply(host: String, username: String, password: String, realm: String, optional: Boolean): DirectCredentials =
      DirectCredentials(host, username, password, Option(realm))
  }

  implicit class DirectCredentialsOp(value: DirectCredentials) {
    def withRealm(realm: String): DirectCredentials =
      value.withRealm(Option(realm))
  }

  implicit class CredentialsModule(value: Credentials.type) {
    def apply(): DirectCredentials = DirectCredentials()
    def apply(host: String, username: String, password: String): DirectCredentials =
      DirectCredentials(host, username, password)
    def apply(host: String, username: String, password: String, realm: Option[String]): DirectCredentials =
      DirectCredentials(host, username, password, realm)
    def apply(host: String, username: String, password: String, realm: String): DirectCredentials =
      DirectCredentials(host, username, password, Option(realm))
    def apply(host: String, username: String, password: String, realm: Option[String], optional: Boolean): DirectCredentials =
      DirectCredentials(host, username, password, realm, optional, matchHost = false, httpsOnly = true)
    def apply(host: String, username: String, password: String, realm: String, optional: Boolean): DirectCredentials =
      DirectCredentials(host, username, password, Option(realm), optional, matchHost = false, httpsOnly = true)

    def apply(f: File): FileCredentials =
      FileCredentials(f.getAbsolutePath)
    def apply(f: File, optional: Boolean): FileCredentials =
      FileCredentials(f.getAbsolutePath, optional)
  }
}
