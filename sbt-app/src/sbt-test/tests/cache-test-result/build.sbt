scalaVersion := "2.12.21"

libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.5" % Test

Test / testQuick / testFilter ~= { filter => args => filter(args) }

commands ++= Seq(
  Command.command("disableTestResultCache") { state =>
    System.setProperty("sbt.cache_test_result", "false")
    state
  },
  Command.command("enableTestResultCache") { state =>
    System.clearProperty("sbt.cache_test_result")
    state
  },
  Command.command("replaceTestFilter") { state =>
    Project.extract(state).appendWithSession(
      Seq(
        Test / testQuick / testFilter := Def.uncached(
          (_: Seq[String]) => Seq((_: String) => true)
        )
      ),
      state
    )
  }
)
