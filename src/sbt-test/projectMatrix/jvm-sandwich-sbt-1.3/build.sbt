lazy val check = taskKey[Unit]("")
lazy val scala3M1 = "3.0.0-M1"
lazy val scala3M2 = "3.0.0-M2"
lazy val scala213 = "2.13.4"

lazy val fooApp = (projectMatrix in file("foo-app"))
  .dependsOn(fooCore)
  .settings(
    name := "foo app",
  )
  .jvmPlatform(scalaVersions = Seq(scala3M1, scala3M2))

lazy val fooApp3 = fooApp.jvm(scala3M1)
  .settings(
    test := { () },
  )

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
  .jvmPlatform(scalaVersions = Seq(scala3M2))

// choose 2.13 when bazCore offers both 2.13 and Dotty
lazy val bazApp = (projectMatrix in file("baz-app"))
  .dependsOn(bazCore)
  .settings(
    name := "baz app",
    check := {
      val cp = (Compile / fullClasspath).value
        .map(_.data.getName)

      assert(cp.contains("baz-core_2.13-0.1.0-SNAPSHOT.jar"), s"$cp")
      assert(!cp.contains("baz-core_3.0.0-M1-0.1.0-SNAPSHOT.jar"), s"$cp")
    },
  )
  .jvmPlatform(scalaVersions = Seq(scala213))

lazy val bazCore = (projectMatrix in file("baz-core"))
  .settings(
    name := "baz core",
    exportJars := true,
  )
  .jvmPlatform(scalaVersions = Seq(scala213, scala3M1))
