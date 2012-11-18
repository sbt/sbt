import sbt._
import Keys._

object B extends Build
{
	override def rootProject = Some(a)

	lazy val a = Project("a", file("a")) settings(
		TaskKey[Unit]("taskA") := {}
	)
	lazy val b = Project("b", file("b")) settings(
		TaskKey[Unit]("taskB") := {}
	)
}
