ThisBuild / scalaVersion := "2.13.16"

lazy val root = project
  .in(file("."))
  .aggregate(ThisProject)
  .settings(
    name := "root-project"
  )

lazy val check = taskKey[Unit]("Verify ThisProject in aggregate works")

root / check := {
  val n = (root / name).value
  assert(n == "root-project", s"Expected 'root-project' but got '$n'")
}
