// #1620

ivyPaths := IvyPaths(
  (baseDirectory in ThisBuild).value,
  Some((baseDirectory in LocalRootProject).value / "ivy-cache")
)

scalaVersion := "2.10.4"

dependencyOverrides in ThisBuild += "com.github.nscala-time" %% "nscala-time" % "1.0.0"
libraryDependencies              += "com.github.nscala-time" %% "nscala-time" % "1.0.0"

TaskKey[Unit]("check") := {
  val s = (streams in update).value
  val cacheStoreFactory = s.cacheStoreFactory sub updateCacheName.value
  val module = ivyModule.value
  val config = updateConfiguration.value

  import sbt.internal.librarymanagement.IvyConfiguration
  import sbt.librarymanagement.{ ModuleSettings, UpdateConfiguration }

  type In = IvyConfiguration :+: ModuleSettings :+: UpdateConfiguration :+: HNil

  import sbt.util.CacheImplicits._
  import sbt.Classpaths.AltLibraryManagementCodec._

  val f: In => Unit =
    Tracked.inputChanged(cacheStoreFactory make "inputs") { (inChanged: Boolean, in: In) =>
      if (inChanged)
        sys.error(s"Update cache is invalidated: ${module.owner.configuration}, ${module.moduleSettings}, $config")
    }
  f(module.owner.configuration :+: module.moduleSettings :+: config :+: HNil)
}
