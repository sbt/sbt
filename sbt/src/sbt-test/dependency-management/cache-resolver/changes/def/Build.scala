import sbt._
import Keys._

object B extends Build {

	override def settings = super.settings ++ Seq(
		organization := "org.example",
		version := "2.0"
	)

	lazy val root = proj("root", ".") aggregate(a,b)
	lazy val a = proj("a", "a") dependsOn(b)
	lazy val b = proj("b", "b")
	private[this] def proj(id: String, f: String): Project = Project(id, file(f)).settings( ivyPaths <<= ivyPaths in ThisBuild )
}
