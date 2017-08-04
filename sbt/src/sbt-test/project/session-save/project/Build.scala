import sbt._

object TestBuild extends Build {
	val k1 = TaskKey[Unit]("k1")
	val k2 = TaskKey[Unit]("k2")
  val k3 = TaskKey[Unit]("k3")
  val k4 = TaskKey[Unit]("k4")
  val k5 = TaskKey[Unit]("k4")

	lazy val root = Project("root", file("."))
}
