import sbt.internal.librarymanagement._

lazy val check = taskKey[Unit]("Runs the check")

def commonSettings: Seq[Def.Setting[_]] =
  Seq(
    ivyPaths := new IvyPaths( (baseDirectory in ThisBuild).value, Some((baseDirectory in LocalRootProject).value / "ivy-cache")),
    scalaVersion := "2.10.4",
    resolvers += Resolver.sonatypeRepo("snapshots")
  )

// #1620
lazy val root = (project in file(".")).
  settings(
    dependencyOverrides in ThisBuild += "com.github.nscala-time" %% "nscala-time" % "1.0.0",
    libraryDependencies += "com.github.nscala-time" %% "nscala-time" % "1.0.0",
    check := {
      import Cache._, CacheIvy.updateIC
      implicit val updateCache = updateIC
      type In = IvyConfiguration :+: ModuleSettings :+: UpdateConfiguration :+: HNil
      val s = (streams in update).value
      val cacheFile = s.cacheDirectory / updateCacheName.value
      val module = ivyModule.value
      val config = updateConfiguration.value
      val f: In => Unit =
        Tracked.inputChanged(cacheFile / "inputs") { (inChanged: Boolean, in: In) =>
          if (inChanged) {
            sys.error(s"Update cache is invalidated: ${module.owner.configuration}, ${module.moduleSettings}, $config")
          }
        }
      f(module.owner.configuration :+: module.moduleSettings :+: config :+: HNil)
    }
  )
