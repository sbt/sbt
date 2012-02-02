import sbt._

object Build1 extends Build
{
	lazy val root1 = Project("root1", file("root1")) settings(
		TaskKey[Unit]("g") := {}
	)
}
