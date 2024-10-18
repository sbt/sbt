ThisBuild / scalaVersion        := "2.10.4"
ThisBuild / dependencyOverrides += "com.github.nscala-time" %% "nscala-time" % "1.0.0"
ThisBuild / csrCacheDirectory   := (ThisBuild / baseDirectory).value / "coursier-cache"

def localCache =
  ivyPaths := IvyPaths(baseDirectory.value.toString, Some(((ThisBuild / baseDirectory).value / "ivy" / "cache").toString))

lazy val root = (project in file("."))
  .dependsOn(p1 % Compile)
  .settings(
    inThisBuild(List(
      organizationName := "eed3si9n",
      organizationHomepage := Some(url("http://example.com/")),
      homepage := Some(url("https://github.com/example/example")),
      scmInfo := Some(ScmInfo(uri("https://github.com/example/example"), "git@github.com:example/example.git")),
      developers := List(
        Developer("harrah", "Mark Harrah", "@harrah", uri("https://github.com/harrah")),
        Developer("eed3si9n", "Eugene Yokota", "@eed3si9n", uri("https://github.com/eed3si9n")),
        Developer("jsuereth", "Josh Suereth", "@jsuereth", uri("https://github.com/jsuereth")),
        Developer("dwijnand", "Dale Wijnand", "@dwijnand", uri("https://github.com/dwijnand")),
        Developer("gkossakowski", "Grzegorz Kossakowski", "@gkossakowski", uri("https://github.com/gkossakowski")),
        Developer("Duhemm", "Martin Duhem", "@Duhemm", uri("https://github.com/Duhemm"))
      ),
      version := "0.3.1-SNAPSHOT",
      description := "An HTTP client for Scala with Async Http Client underneath.",
      licenses := Seq(LicenseInfo("Apache 2", uri("http://www.apache.org/licenses/LICENSE-2.0.txt"), None, None)),
    )),
    localCache,
    libraryDependencies += "com.github.nscala-time" %% "nscala-time" % "1.0.0",

    // https://github.com/sbt/sbt/pull/1620
    // sbt resolves dependencies every compile when using %% with dependencyOverrides
    TaskKey[Unit]("check") := {
      val s = (update / streams).value

      val cacheDirectory = crossTarget.value / "update" / updateCacheName.value
      val cacheStoreFactory = sbt.util.CacheStoreFactory.directory(cacheDirectory)
      val module = ivyModule.value
      val updateConfig = updateConfiguration.value
      val extraInputHash0 = module.extraInputHash
      val moduleSettings0 = module.moduleSettings
      val inline0 = moduleSettings0 match { case x: InlineConfiguration => x }
      // Remove clock for caching purpose
      val updateConfig0 = updateConfig
        .withLogicalClock(LogicalClock.unknown)
        .withMetadataDirectory(dependencyCacheDirectory.value)

      import sbt.librarymanagement.{ ModuleSettings, UpdateConfiguration, LibraryManagementCodec }
      type In = (Long, ModuleSettings, UpdateConfiguration)

      import LibraryManagementCodec._

      val f: In => Unit =
        Tracked.inputChanged(cacheStoreFactory make "inputs") { (inChanged: Boolean, in: In) =>
          val extraInputHash1 = in._1
          val moduleSettings1 = in._2
          val inline1 = moduleSettings1 match { case x: InlineConfiguration => x }
          val updateConfig1 = in._3

          if (inChanged) {
            sys.error(s"""
extraInputHash1 == extraInputHash0: ${extraInputHash1 == extraInputHash0}

extraInputHash1:
$extraInputHash1

extraInputHash0
$extraInputHash0
-----
inline1 == inline0: ${inline1 == inline0}

inline1:
$inline1

inline0
$inline0
-----
updateConfig1 == updateConfig0: ${updateConfig1 == updateConfig0}

updateConfig1:
$updateConfig1

updateConfig0
$updateConfig0
""")
          }
        }

      f((extraInputHash0, (inline0: ModuleSettings), updateConfig0))
    },

    // https://github.com/sbt/sbt/issues/3226
    // update caching is not working on sbt 1.0.x
    TaskKey[Unit]("check2") := {
      val ur = update.value
      if (!ur.stats.cached) {
        sys.error(s"update.value is not cached! $ur")
      }
      val tu = transitiveUpdate.value
      if (tu.exists(!_.stats.cached)) {
        sys.error(s"uncached transitiveUpdate exists! $tu")
      }
    }
  )

lazy val p1 = project
  .settings(
    libraryDependencies += "com.novocode" % "junit-interface" % "0.11"
  )
