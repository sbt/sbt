import Tests._
import Defaults._

scalaVersion := "3.8.4"
organization := "com.example"

val check = TaskKey[Unit]("check", "Check that the two runs shared the same worker JVM")
val checkDistinct = TaskKey[Unit]("checkDistinct", "Check that the two runs used different worker JVMs")
val clearPids = TaskKey[Unit]("clearPids", "Delete the pids marker file")

Test / fork := true
Global / concurrentRestrictions += Tags.limit(Tags.ForkedTestGroup, 4)

libraryDependencies += "org.scalameta" %% "munit" % "1.0.4" % Test

check := Def.uncached {
  val lines = IO.readLines(file("pids")).filter(_.nonEmpty)
  if lines.size != 2 then
    sys.error(s"Expected exactly 2 recorded runs, saw ${lines.size}: $lines")
  if lines(0) != lines(1) then
    sys.error(s"Expected the same worker JVM to be reused, but saw ${lines(0)} then ${lines(1)}")
}

checkDistinct := Def.uncached {
  val lines = IO.readLines(file("pids")).filter(_.nonEmpty)
  if lines.size != 2 then
    sys.error(s"Expected exactly 2 recorded runs, saw ${lines.size}: $lines")
  if lines(0) == lines(1) then
    sys.error(s"Expected a fresh worker JVM per run, but both used ${lines(0)}")
}

clearPids := Def.uncached {
  IO.delete(file("pids"))
}
