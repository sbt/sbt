lazy val check = taskKey[Unit]("")

val dottyVersion = "0.23.0"
ThisBuild / resolvers += "scala-integration" at "https://scala-ci.typesafe.com/artifactory/scala-integration/"
// TODO use 2.13.4 when it's out
lazy val scala213 = "2.13.4-bin-aeee8f0"

lazy val fooApp = (projectMatrix in file("foo-app"))
  .dependsOn(fooCore)
  .settings(
    name := "foo app",
  )
  .jvmPlatform(scalaVersions = Seq(dottyVersion))

lazy val fooCore = (projectMatrix in file("foo-core"))
  .settings(
    name := "foo core",
  )
  .jvmPlatform(scalaVersions = Seq(scala213, "2.12.12"))

lazy val barApp = (projectMatrix in file("bar-app"))
  .dependsOn(barCore)
  .settings(
    name := "bar app",
  )
  .jvmPlatform(scalaVersions = Seq(scala213))

lazy val barCore = (projectMatrix in file("bar-core"))
  .settings(
    name := "bar core",
  )
  .jvmPlatform(scalaVersions = Seq(dottyVersion))

// choose 2.13 when bazCore offers both 2.13 and Dotty
lazy val bazApp = (projectMatrix in file("baz-app"))
  .dependsOn(bazCore)
  .settings(
    name := "baz app",
    check := {
      val cp = (Compile / fullClasspath).value
        .map(_.data.getName)

      assert(cp.contains("baz-core_2.13-0.1.0-SNAPSHOT.jar"), s"$cp")
      assert(!cp.contains("baz-core_0.23-0.1.0-SNAPSHOT.jar"), s"$cp")
    },
  )
  .jvmPlatform(scalaVersions = Seq(scala213))

lazy val bazCore = (projectMatrix in file("baz-core"))
  .settings(
    name := "baz core",
    exportJars := true,
  )
  .jvmPlatform(scalaVersions = Seq(scala213, dottyVersion))
