ThisBuild / scalaVersion := "2.13.3"
ThisBuild / usePipelining := true

lazy val root = (project in file("."))
  .aggregate(dep, use)
  .settings(
    name := "pipelining basics",
  )

lazy val dep = project

lazy val use = project
  .dependsOn(dep)
  .settings(
    TaskKey[Unit]("checkPickle") := {
      val s = streams.value
      val x = (dep / Compile / compile).value
      val picklePath = (Compile / internalDependencyPicklePath).value
      assert(picklePath.size == 1 &&
        picklePath.head.data.name == "dep_2.13-0.1.0-SNAPSHOT.jar", s"picklePath = ${picklePath}")
    },
  )
