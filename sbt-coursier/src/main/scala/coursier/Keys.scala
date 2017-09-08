package coursier

import java.io.File
import java.net.URL

import coursier.core.Publication
import sbt.{Resolver, SettingKey, TaskKey}

import scala.concurrent.duration.Duration
import scalaz.\/

import SbtCompatibility._

object Keys {
  val coursierParallelDownloads = SettingKey[Int]("coursier-parallel-downloads")
  val coursierMaxIterations = SettingKey[Int]("coursier-max-iterations")
  val coursierChecksums = SettingKey[Seq[Option[String]]]("coursier-checksums")
  val coursierArtifactsChecksums = SettingKey[Seq[Option[String]]]("coursier-artifacts-checksums")
  val coursierCachePolicies = SettingKey[Seq[CachePolicy]]("coursier-cache-policies")
  val coursierTtl = SettingKey[Option[Duration]]("coursier-ttl")
  val coursierKeepPreloaded = SettingKey[Boolean]("coursier-keep-preloaded", "Whether to take into account sbt preloaded repositories or not")

  val coursierVerbosity = SettingKey[Int]("coursier-verbosity")

  val mavenProfiles = SettingKey[Set[String]]("maven-profiles")

  val coursierReorderResolvers = SettingKey[Boolean](
    "coursier-reorder-resolvers",
    "Whether resolvers should be re-ordered so that typically slow ones are given a lower priority"
  )
  val coursierResolvers = TaskKey[Seq[Resolver]]("coursier-resolvers")
  val coursierRecursiveResolvers = TaskKey[Seq[Resolver]]("coursier-recursive-resolvers", "Resolvers of the current project, plus those of all from its inter-dependency projects")
  val coursierSbtResolvers = TaskKey[Seq[Resolver]]("coursier-sbt-resolvers")
  val coursierUseSbtCredentials = SettingKey[Boolean]("coursier-use-sbt-credentials")
  val coursierCredentials = TaskKey[Map[String, Credentials]]("coursier-credentials")

  val coursierCache = SettingKey[File]("coursier-cache")

  val coursierFallbackDependencies = TaskKey[Seq[(Module, String, URL, Boolean)]]("coursier-fallback-dependencies")

  val coursierProject = TaskKey[Project]("coursier-project")
  val coursierConfigGraphs = TaskKey[Seq[Set[String]]]("coursier-config-graphs")
  val coursierInterProjectDependencies = TaskKey[Seq[Project]]("coursier-inter-project-dependencies", "Projects the current project depends on, possibly transitively")
  val coursierPublications = TaskKey[Seq[(String, Publication)]]("coursier-publications")

  val coursierSbtClassifiersModule = TaskKey[GetClassifiersModule]("coursier-sbt-classifiers-module")

  val coursierConfigurations = TaskKey[Map[String, Set[String]]]("coursier-configurations")


  val coursierParentProjectCache = TaskKey[Map[Seq[Resolver], Seq[ProjectCache]]]("coursier-parent-project-cache")
  val coursierResolutions = TaskKey[Map[Set[String], Resolution]]("coursier-resolutions")

  private[coursier] val actualCoursierResolution = TaskKey[Resolution]("coursier-resolution")

  @deprecated("Use coursierResolutions instead", "1.0.0-RC4")
  val coursierResolution = actualCoursierResolution
  val coursierSbtClassifiersResolution = TaskKey[Resolution]("coursier-sbt-classifiers-resolution")

  val coursierDependencyTree = TaskKey[Unit](
    "coursier-dependency-tree",
    "Prints dependencies and transitive dependencies as a tree"
  )
  val coursierDependencyInverseTree = TaskKey[Unit](
    "coursier-dependency-inverse-tree",
    "Prints dependencies and transitive dependencies as an inverted tree (dependees as children)"
  )

  val coursierArtifacts = TaskKey[Map[Artifact, FileError \/ File]]("coursier-artifacts")
  val coursierSignedArtifacts = TaskKey[Map[Artifact, FileError \/ File]]("coursier-signed-artifacts")
  val coursierClassifiersArtifacts = TaskKey[Map[Artifact, FileError \/ File]]("coursier-classifiers-artifacts")
  val coursierSbtClassifiersArtifacts = TaskKey[Map[Artifact, FileError \/ File]]("coursier-sbt-classifiers-artifacts")
}
