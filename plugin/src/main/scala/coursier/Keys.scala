package coursier

import java.io.File
import sbt.{ GetClassifiersModule, Resolver, SettingKey, TaskKey }

object Keys {
  val coursierParallelDownloads = SettingKey[Int]("coursier-parallel-downloads", "") // 6
  val coursierMaxIterations = SettingKey[Int]("coursier-max-iterations", "") // 50
  val coursierChecksums = SettingKey[Seq[Option[String]]]("coursier-checksums", "") //Seq(Some("SHA-1"), Some("MD5"))
  val coursierCachePolicy = SettingKey[CachePolicy]("coursier-cache-policy", "") // = CachePolicy.FetchMissing

  val coursierVerbosity = SettingKey[Int]("coursier-verbosity", "")

  val coursierResolvers = TaskKey[Seq[Resolver]]("coursier-resolvers", "")

  val coursierCache = SettingKey[File]("coursier-cache", "")

  val coursierProject = TaskKey[(Project, Seq[(String, Seq[Artifact])])]("coursier-project", "")
  val coursierProjects = TaskKey[Seq[(Project, Seq[(String, Seq[Artifact])])]]("coursier-projects", "")

  val coursierSbtClassifiersModule = TaskKey[GetClassifiersModule]("coursier-sbt-classifiers-module", "")
}
