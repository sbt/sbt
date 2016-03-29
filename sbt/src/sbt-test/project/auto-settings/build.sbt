import AddSettings._

// version should be from explicit/a.txt
lazy val root = proj("root", "1.4").settingSets(
  buildScalaFiles,
  userSettings,
  sbtFiles(file("explicit/a.txt"))
)

// version should be from global/user.sbt
lazy val a = proj("a", "1.1").
  settingSets( buildScalaFiles, userSettings )

// version should be the default 0.1-SNAPSHOT
lazy val b = proj("b", "0.1-SNAPSHOT").
  settingSets(buildScalaFiles)

// version should be from the explicit settings call
lazy val c = proj("c", "0.9").settings(version := "0.9").
  settingSets(buildScalaFiles)

// version should be from d/build.sbt
lazy val d = proj("d", "1.3").settings(version := "0.9").
  settingSets( buildScalaFiles, defaultSbtFiles )

// version should be from global/user.sbt
lazy val e = proj("e", "1.1").settings(version := "0.9").
  settingSets( buildScalaFiles, defaultSbtFiles, sbtFiles(file("../explicit/a.txt")), userSettings )

def proj(id: String, expectedVersion: String): Project =
  Project(id, if(id == "root") file(".") else file(id)).
  settings(
    TaskKey[Unit]("check") := {
      assert(version.value == expectedVersion,
        "Expected version '" + expectedVersion + "', got: " + version.value + " in " + id)
    }
  )
