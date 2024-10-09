package lmcoursier

import java.io.File
import dataclass.{data, since}
import coursier.cache.CacheDefaults
import coursier.params.rule.{Rule, RuleResolution}
import lmcoursier.credentials.Credentials
import lmcoursier.definitions.{Authentication, CacheLogger, CachePolicy, FromCoursier, Module, ModuleMatchers, Project, Reconciliation, Strict}
import sbt.librarymanagement.{CrossVersion, InclExclRule, ModuleDescriptorConfiguration, ModuleID, ModuleInfo, Resolver, UpdateConfiguration}
import xsbti.Logger

import scala.concurrent.duration.{Duration, FiniteDuration}
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
  retry: Option[(FiniteDuration, Int)] = None,
  sameVersions: Seq[Set[InclExclRule]] = Nil,
)
