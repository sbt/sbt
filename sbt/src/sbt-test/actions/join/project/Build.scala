import sbt._
import Keys._

object Build extends Build
{
	lazy val root = Project("root", file(".")) dependsOn(b,c) settings(
		compile in Compile <<= Seq(b, c).map(p => compile in (p, Compile)).join.map( as => (inc.Analysis.Empty /: as)(_ ++ _) )
	)
	lazy val b = Project("b", file("b"))
	lazy val c = Project("c", file("c"))
}