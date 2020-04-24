lazy val compile2 = taskKey[Unit]("")

lazy val root = (project in file("."))
  .aggregate(foo, bar, client)
  .settings(
    crossScalaVersions := Nil,
    addCommandAlias("build", "compile2"),
  )

lazy val foo = project
  .settings(
    crossScalaVersions := Seq("2.12.11", "2.13.1"),
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.1.0",
    compile2 := {
      // This tests that +build will ignore bar's crossScalaVersions and use root's like sbt 0.13
      val x = (LocalProject("bar") / scalaVersion).value
      assert(x == scalaVersion.value, s"$x == ${scalaVersion.value}")
      (Compile / compile).value
    },
  )

lazy val bar = project
  .settings(
    crossScalaVersions := Seq("2.12.11"),
    compile2 := (Compile / compile).value,
  )

lazy val client = project
  .settings(
    crossScalaVersions := Seq("2.12.11", "2.13.1"),
    compile2 := (Compile / compile).value,
  )
