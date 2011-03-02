import sbt._
import Keys.name

object TestBuild extends Build
{
	lazy val projects = Seq(
		proj("a", "."),
		proj("b", "b")
	)
	def proj(id: String, dir: String) = Project(id, file(dir), settings = Seq( name :== id ) )
}