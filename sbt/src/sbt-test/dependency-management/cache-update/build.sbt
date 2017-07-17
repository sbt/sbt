scalaVersion in ThisBuild        := "2.10.4"
dependencyOverrides in ThisBuild += "com.github.nscala-time" %% "nscala-time" % "1.0.0"

lazy val root = (project in file("."))
  .dependsOn(p1 % Compile)
  .settings(
    inThisBuild(List(
      organizationName := "eed3si9n",
      organizationHomepage := Some(url("http://example.com/")),
      homepage := Some(url("https://github.com/example/example")),
      scmInfo := Some(ScmInfo(url("https://github.com/example/example"), "git@github.com:example/example.git")),
      developers := List(
        Developer("harrah", "Mark Harrah", "@harrah", url("https://github.com/harrah")),
        Developer("eed3si9n", "Eugene Yokota", "@eed3si9n", url("https://github.com/eed3si9n")),
        Developer("jsuereth", "Josh Suereth", "@jsuereth", url("https://github.com/jsuereth")),
        Developer("dwijnand", "Dale Wijnand", "@dwijnand", url("https://github.com/dwijnand")),
        Developer("gkossakowski", "Grzegorz Kossakowski", "@gkossakowski", url("https://github.com/gkossakowski")),
        Developer("Duhemm", "Martin Duhem", "@Duhemm", url("https://github.com/Duhemm"))
      ),
      version := "0.3.1-SNAPSHOT",
      description := "An HTTP client for Scala with Async Http Client underneath.",
      licenses := Seq("Apache 2" -> new URL("http://www.apache.org/licenses/LICENSE-2.0.txt")),
    )),
    ivyPaths := IvyPaths(
      (baseDirectory in ThisBuild).value,
      Some((baseDirectory in LocalRootProject).value / "ivy-cache")
    ),
    libraryDependencies += "com.github.nscala-time" %% "nscala-time" % "1.0.0",

    // https://github.com/sbt/sbt/pull/1620
    // sbt resolves dependencies every compile when using %% with dependencyOverrides
    TaskKey[Unit]("check") := {
      val s = (streams in update).value
      val cacheStoreFactory = s.cacheStoreFactory sub updateCacheName.value
      val module = ivyModule.value
      val updateConfig = updateConfiguration.value
      val ivyConfiguration0 = module.owner.configuration
      val moduleSettings0 = module.moduleSettings
      val inline0 = moduleSettings0 match { case x: InlineConfiguration => x }
      // Remove clock for caching purpose
      val updateConfig0 = updateConfig.withLogicalClock(LogicalClock.unknown)

      import sbt.librarymanagement.ivy.IvyConfiguration
      import sbt.librarymanagement.{ ModuleSettings, UpdateConfiguration }
      import sbt.internal.util.HListFormats._

      type In = IvyConfiguration :+: ModuleSettings :+: UpdateConfiguration :+: HNil

      import sbt.util.CacheImplicits._
      import sbt.internal.util.HListFormats._
      import sbt.internal.AltLibraryManagementCodec._

      val f: In => Unit =
        Tracked.inputChanged(cacheStoreFactory make "inputs") { (inChanged: Boolean, in: In) =>
          val ivyConfiguration1 = in.head
          val moduleSettings1 = in.tail.head
          val inline1 = moduleSettings1 match { case x: InlineConfiguration => x }
          val updateConfig1 = in.tail.tail.head

          if (inChanged) {
            sys.error(s"""
ivyConfiguration1 == ivyConfiguration0: ${ivyConfiguration1 == ivyConfiguration0}

ivyConfiguration1:
$ivyConfiguration1

ivyConfiguration0
$ivyConfiguration0
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

      f(ivyConfiguration0 :+: (inline0: ModuleSettings) :+: updateConfig0 :+: HNil)
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
