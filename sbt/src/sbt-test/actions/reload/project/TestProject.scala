import sbt._

object TestBuild extends Build
{
	lazy val root = Project("root", file("."), aggregate = Seq(sub)) settings(
		TaskKey[Unit]("f") := sys.error("f")
	)
	lazy val sub = Project("sub", file("sub")) settings(
		TaskKey[Unit]("f") := {}
	)
}
