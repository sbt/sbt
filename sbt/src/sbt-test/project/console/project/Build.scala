import sbt._

object B extends Build
{
	lazy val projects = Seq(root, sub1, sub2, sub3)

	lazy val root = Project("root", file("."))
	lazy val sub1 = Project("sub1", file("sub1"))
	lazy val sub2 = Project("sub2", file("sub2"))
	lazy val sub3 = Project("sub3", file("sub3"))
}