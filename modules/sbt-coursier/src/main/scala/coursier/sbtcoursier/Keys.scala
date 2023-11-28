package coursier.sbtcoursier

import java.io.File

import coursier.cache.CachePolicy
import coursier.ProjectCache
import coursier.core._
import coursier.util.Artifact
import sbt.librarymanagement.{GetClassifiersModule, Resolver}
import sbt.{InputKey, SettingKey, TaskKey}

import scala.concurrent.duration.{Duration, FiniteDuration}

object Keys {
  val coursierParallelDownloads = SettingKey[Int]("coursier-parallel-downloads")
  val coursierMaxIterations = SettingKey[Int]("coursier-max-iterations")
  val coursierChecksums = SettingKey[Seq[Option[String]]]("coursier-checksums")
  val coursierArtifactsChecksums = SettingKey[Seq[Option[String]]]("coursier-artifacts-checksums")
  val coursierCachePolicies = SettingKey[Seq[CachePolicy]]("coursier-cache-policies")
  val coursierTtl = SettingKey[Option[Duration]]("coursier-ttl")

  val coursierVerbosity = SettingKey[Int]("coursier-verbosity")

  val coursierConfigGraphs = TaskKey[Seq[(Configuration, Seq[Configuration])]]("coursier-config-graphs")

  val coursierSbtClassifiersModule = TaskKey[GetClassifiersModule]("coursier-sbt-classifiers-module")

  val coursierConfigurations = TaskKey[Map[Configuration, Set[Configuration]]]("coursier-configurations")


  val coursierParentProjectCache = TaskKey[Map[Seq[Resolver], Seq[ProjectCache]]]("coursier-parent-project-cache")
  val coursierResolutions = TaskKey[Map[Configuration, Resolution]]("coursier-resolutions")

  private[coursier] val actualCoursierResolution = TaskKey[Resolution]("coursier-resolution")

  val coursierSbtClassifiersResolutions = TaskKey[Map[Configuration, Resolution]]("coursier-sbt-classifiers-resolution")

  val coursierDependencyTree = TaskKey[Unit](
    "coursier-dependency-tree",
    "Prints dependencies and transitive dependencies as a tree"
  )
  val coursierDependencyInverseTree = TaskKey[Unit](
    "coursier-dependency-inverse-tree",
    "Prints dependencies and transitive dependencies as an inverted tree (dependees as children)"
  )

  val coursierWhatDependsOn = InputKey[String](
    "coursier-what-depends-on",
    "Prints dependencies and transitive dependencies as an inverted tree for a specific module (dependees as children)"
  )
  val coursierArtifacts = TaskKey[Map[Artifact, File]]("coursier-artifacts")
  val coursierSignedArtifacts = TaskKey[Map[Artifact, File]]("coursier-signed-artifacts")
  val coursierClassifiersArtifacts = TaskKey[Map[Artifact, File]]("coursier-classifiers-artifacts")
  val coursierSbtClassifiersArtifacts = TaskKey[Map[Artifact, File]]("coursier-sbt-classifiers-artifacts")
}
