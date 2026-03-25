lazy val check = taskKey[Unit]("")
lazy val scala3 = "3.5.2"
lazy val scala3M2 = "3.0.0-M2"
lazy val scala213 = "2.13.14"

lazy val fooApp = (projectMatrix in file("foo-app"))
  .dependsOn(fooCore)
  .settings(
    name := "foo app",
    TaskKey[Unit]("check") := {
      val deps = (Compile / projectDependencies).value
      deps.foreach { dep =>
        println(s"$dep - ${dep.crossVersion}")
      }
    },
  )
  .jvmPlatform(scalaVersions = Seq(scala3, scala3M2))

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
  .jvmPlatform(scalaVersions = Seq(scala3))

// choose 2.13 when bazCore offers both 2.13 and Dotty
lazy val bazApp = (projectMatrix in file("baz-app"))
  .dependsOn(bazCore)
  .settings(
    name := "baz app",
    check := {
      val cp = (Compile / fullClasspath).value.map(_.data.id)
      assert(cp.exists(_.endsWith("baz-core_2.13-0.1.0-SNAPSHOT.jar")), cp)
      assert(!cp.exists(_.endsWith("baz-core_3.0.0-M1-0.1.0-SNAPSHOT.jar")), cp)
      assert(projectMatrixBaseDirectory.value == (ThisBuild / baseDirectory).value / "baz-app",
        s"projectMatrixBaseDirectory is ${projectMatrixBaseDirectory.value}")
    },
  )
  .jvmPlatform(scalaVersions = Seq(scala213))

lazy val bazCore = (projectMatrix in file("baz-core"))
  .settings(
    name := "baz core",
    exportJars := true,
  )
  .jvmPlatform(scalaVersions = Seq(scala213, scala3, scala3M2))
