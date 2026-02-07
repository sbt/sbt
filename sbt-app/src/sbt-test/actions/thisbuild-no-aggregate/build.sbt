ThisBuild / scalaVersion := "2.13.16"

lazy val mark = taskKey[Unit]("Creates a marker file to track where this task ran")

lazy val root = (project in file("."))
  .aggregate(sub)
  .settings(
    name := "root",
    mark := {
      val toMark = baseDirectory.value / "root-ran"
      if (toMark.exists) sys.error(s"Already ran ($toMark exists)")
      else IO.touch(toMark)
    }
  )

lazy val sub = (project in file("sub"))
  .settings(
    name := "sub",
    mark := {
      val toMark = baseDirectory.value / "sub-ran"
      if (toMark.exists) sys.error(s"Already ran ($toMark exists)")
      else IO.touch(toMark)
    }
  )

ThisBuild / mark := {
  val base = (ThisBuild / baseDirectory).value
  val toMark = base / "build-ran"
  if (toMark.exists) sys.error(s"Already ran ($toMark exists)")
  else IO.touch(toMark)
}
