import sbt._
import Keys._

object Build extends Build
{
	override def settings = super.settings ++ Seq(
		sbtBinaryVersion <<= sbtVersion
	)

	lazy val root = Project("root", file(".")) aggregate(a,b,c)
	lazy val a = Project("a", file("a"))
	lazy val b = Project("b", file("b"))
	lazy val c = Project("c", file("c"))
}