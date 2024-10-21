scalaVersion := "3.3.3"

lazy val someTask = taskKey[Unit]("")

lazy val root = (project in file("."))
  .aggregate(foo, bar, baz)
  .settings(
    name := "root",
  )

lazy val foo = project
lazy val bar = project
lazy val baz = project
  .settings(
    scalaVersion := "2.12.20",
  )

someTask := {
  val x = target.value / (name.value + ".txt")
  val s = streams.value
  s.log.info(s"writing $x")
  IO.touch(x)
}
