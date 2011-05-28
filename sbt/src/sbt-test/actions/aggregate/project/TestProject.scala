import sbt._

object SingleBuild extends Build with Marker
{
	override def projects = if(file("multi").exists) Seq(root, sub, sub2) else Seq(root)
	lazy val root = Project("root", file("."), aggregate = if(file("aggregate").exists) Seq(sub) else Nil )
	lazy val sub = Project("sub", file("sub"), aggregate = Seq(sub2))
	lazy val sub2 = Project("sub2", file("sub") / "sub")
}
