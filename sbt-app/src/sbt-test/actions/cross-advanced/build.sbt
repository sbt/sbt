lazy val check = taskKey[Unit]("")
lazy val compile2 = taskKey[Unit]("")

lazy val root = (project in file("."))
  .aggregate(foo, bar, client)
  .settings(
    crossScalaVersions := Nil,
    addCommandAlias("build", "compile2"),
  )

lazy val foo = project
  .settings(
    crossScalaVersions := Seq("2.12.14", "2.13.1"),
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.1.0",

    check := {
      // This tests that +check will respect bar's crossScalaVersions and not switch
      val x = (LocalProject("bar") / scalaVersion).value
      assert(x == "2.12.14", s"$x == 2.12.12")
      (Compile / compile).value
    },
    (Test / testOnly) := {
      // This tests that +testOnly will respect bar's crossScalaVersions and not switch
      val x = (LocalProject("bar") / scalaVersion).value
      assert(x == "2.12.14", s"$x == 2.12.12")
      val _ = (Test / testOnly).evaluated
    },
    compile2 := {
      // This tests that +build will ignore bar's crossScalaVersions and use root's like sbt 0.13
      val x = (LocalProject("bar") / scalaVersion).value
      assert(x == scalaVersion.value, s"$x == ${scalaVersion.value}")
      (Compile / compile).value
    },
  )

lazy val bar = project
  .settings(
    crossScalaVersions := Seq("2.12.14"),
    check := (Compile / compile).value,
    compile2 := (Compile / compile).value,
  )

lazy val baz = project
  .settings(
    crossScalaVersions := Seq("2.13.1"),
    check := {
      // This tests that +baz/check will respect bar's crossScalaVersions and not switch
      val x = (LocalProject("bar") / scalaVersion).value
      assert(x == "2.12.14", s"$x == 2.12.14")
      (Compile / compile).value
    },
  )

lazy val client = project
  .settings(
    crossScalaVersions := Seq("2.12.14", "2.13.1"),
    check := (Compile / compile).value,
    compile2 := (Compile / compile).value,
  )
