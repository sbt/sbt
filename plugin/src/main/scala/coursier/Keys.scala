package coursier

import java.io.File
import sbt.{ GetClassifiersModule, Resolver, SettingKey, TaskKey }

object Keys {
  val coursierParallelDownloads = SettingKey[Int]("coursier-parallel-downloads", "")
  val coursierMaxIterations = SettingKey[Int]("coursier-max-iterations", "")
  val coursierChecksums = SettingKey[Seq[Option[String]]]("coursier-checksums", "")
  val coursierCachePolicy = SettingKey[CachePolicy]("coursier-cache-policy", "")

  val coursierVerbosity = SettingKey[Int]("coursier-verbosity", "")

  val coursierResolvers = TaskKey[Seq[Resolver]]("coursier-resolvers", "")

  val coursierCache = SettingKey[File]("coursier-cache", "")

  val coursierProject = TaskKey[(Project, Seq[(String, Seq[Artifact])])]("coursier-project", "")
  val coursierProjects = TaskKey[Seq[(Project, Seq[(String, Seq[Artifact])])]]("coursier-projects", "")

  val coursierSbtClassifiersModule = TaskKey[GetClassifiersModule]("coursier-sbt-classifiers-module", "")
}
