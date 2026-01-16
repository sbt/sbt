lazy val check = taskKey[Unit]("")
lazy val compile2 = taskKey[Unit]("")
lazy val scala212 = "2.12.21"
lazy val scala213 = "2.13.12"

ThisBuild / scalaVersion := scala212

lazy val root = (project in file("."))
  .aggregate(foo, bar, client)
  .settings(
    crossScalaVersions := Nil,
    addCommandAlias("build", "compile2"),
  )

lazy val foo = project
  .settings(
    crossScalaVersions := Seq(scala212, scala213),
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.1.0",
    check := Def.uncached {
      // This tests that +check will respect bar's crossScalaVersions and not switch
      val x = (LocalProject("bar") / scalaVersion).value
      assert(x == scala212, s"$x == $scala212")
      Def.unit((Compile / compile).value)
    },
    (Test / testOnly) := {
      // This tests that +testOnly will respect bar's crossScalaVersions and not switch
      val x = (LocalProject("bar") / scalaVersion).value
      assert(x == scala212, s"$x == $scala212")
      (Test / testOnly).evaluated
    },
    compile2 := Def.uncached {
      // This tests that +build will ignore bar's crossScalaVersions and use root's like sbt 0.13
      val x = (LocalProject("bar") / scalaVersion).value
      assert(x == scalaVersion.value, s"$x == ${scalaVersion.value}")
      Def.unit((Compile / compile).value)
    },
  )

lazy val bar = project
  .settings(
    crossScalaVersions := Seq(scala212),
    check := Def.uncached {
      Def.unit((Compile / compile).value)
    },
    compile2 := Def.uncached(Def.unit((Compile / compile).value)),
  )

lazy val baz = project
  .settings(
    crossScalaVersions := Seq(scala213),
    check := Def.uncached {
      // This tests that +baz/check will respect bar's crossScalaVersions and not switch
      val x = (LocalProject("bar") / scalaVersion).value
      assert(x == scala212, s"$x == $scala212")
      Def.unit((Compile / compile).value)
    },
  )

lazy val client = project
  .settings(
    crossScalaVersions := Seq(scala212, scala213),
    check := Def.uncached(Def.unit((Compile / compile).value)),
    compile2 := Def.uncached(Def.unit((Compile / compile).value)),
  )
