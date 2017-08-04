import sbt._

object TestBuild extends Build
{
	lazy val root1 = Project("root1", file(".")) dependsOn(root2)
	lazy val root2 = ProjectRef(uri("external"), "root2")
}
