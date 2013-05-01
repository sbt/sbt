import sbt._

object TestBuild extends Build {
	val k1 = TaskKey[Unit]("k1")
	val k2 = TaskKey[Unit]("k2")

	lazy val root = Project("root", file("."))
}
