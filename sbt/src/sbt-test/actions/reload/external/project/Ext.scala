import sbt._

object Ext extends Build
{
	lazy val root2 = Project("root2", file("root2")) settings(
		TaskKey[Unit]("g") := {}
	)
}
