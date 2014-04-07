import sbt._
import Keys.name
import AddSettings._

object TestBuild extends Build
{
	override def projects = Seq(
		proj("a", "."),
		proj("b", "b")
	)
	def proj(id: String, dir: String) = Project(id, file(dir), settings = Seq( name := id ) ).settingSets(buildScalaFiles)
}
