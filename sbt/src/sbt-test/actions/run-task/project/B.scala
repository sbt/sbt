import sbt._
import Keys._

object B extends Build
{
	lazy val projects = Seq(root)
	lazy val root = Project("root", file(".")) settings(
		myRun,
		fork in demo := true,
		javaOptions in demo := "-Dsbt.check.forked=true" :: Nil,
		myIn
	)

	lazy val demoIn = InputKey[Unit]("demo-in", "Demo run input task", demo)
	lazy val demo = TaskKey[Unit]("demo", "Demo run task")

	def myRun = fullRunTask( demo, Compile, "A", "1", "1")
	def myIn = fullRunInputTask( demoIn, Compile, "A", "1")
}
