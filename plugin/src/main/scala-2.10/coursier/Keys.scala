package coursier

import java.io.File
import java.net.URL

import coursier.core.Publication
import sbt.{ GetClassifiersModule, Resolver, SettingKey, TaskKey }

object Keys {
  val coursierParallelDownloads = SettingKey[Int]("coursier-parallel-downloads")
  val coursierMaxIterations = SettingKey[Int]("coursier-max-iterations")
  val coursierChecksums = SettingKey[Seq[Option[String]]]("coursier-checksums")
  val coursierArtifactsChecksums = SettingKey[Seq[Option[String]]]("coursier-artifacts-checksums")
  val coursierCachePolicies = SettingKey[Seq[CachePolicy]]("coursier-cache-policies")

  val coursierVerbosity = SettingKey[Int]("coursier-verbosity")

  val coursierSourceRepositories = SettingKey[Seq[File]]("coursier-source-repositories")
  val coursierResolvers = TaskKey[Seq[Resolver]]("coursier-resolvers")
  val coursierSbtResolvers = TaskKey[Seq[Resolver]]("coursier-sbt-resolvers")
  val coursierCredentials = TaskKey[Map[String, Credentials]]("coursier-credentials")

  val coursierCache = SettingKey[File]("coursier-cache")

  val coursierFallbackDependencies = TaskKey[Seq[(Module, String, URL, Boolean)]]("coursier-fallback-dependencies")

  val coursierProject = TaskKey[Project]("coursier-project")
  val coursierProjects = TaskKey[Seq[Project]]("coursier-projects")
  val coursierPublications = TaskKey[Seq[(String, Publication)]]("coursier-publications")

  val coursierSbtClassifiersModule = TaskKey[GetClassifiersModule]("coursier-sbt-classifiers-module")

  val coursierConfigurations = TaskKey[Map[String, Set[String]]]("coursier-configurations")

  val coursierResolution = TaskKey[Resolution]("coursier-resolution")
  val coursierSbtClassifiersResolution = TaskKey[Resolution]("coursier-sbt-classifiers-resolution")

  val coursierDependencyTree = TaskKey[Unit](
    "coursier-dependency-tree",
    "Prints dependencies and transitive dependencies as a tree"
  )
  val coursierDependencyInverseTree = TaskKey[Unit](
    "coursier-dependency-inverse-tree",
    "Prints dependencies and transitive dependencies as an inverted tree (dependees as children)"
  )

  val coursierExport = TaskKey[Option[File]](
    "coursier-export",
    "Generates files allowing using these sources as a source dependency repository"
  )
  val coursierExportDirectory = TaskKey[File](
    "coursier-export-directory",
    "Base directory for the products of coursierExport"
  )
  val coursierExportJavadoc = SettingKey[Boolean](
    "coursier-export-javadoc",
    "Build javadoc packages for the coursier source dependency repository"
  )
  val coursierExportSources = SettingKey[Boolean](
    "coursier-export-sources",
    "Build sources packages for the coursier source dependency repository"
  )
}
