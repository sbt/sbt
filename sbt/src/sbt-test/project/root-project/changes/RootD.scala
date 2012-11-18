import sbt._
import Keys._

object B extends Build
{
	override def rootProject = Some(d)

	lazy val c = Project("c", file("c")) settings(
		TaskKey[Unit]("taskC") := {}
	)
	lazy val d = Project("d", file("d")) settings(
		TaskKey[Unit]("taskD") := {}
	)
}
