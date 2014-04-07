import sbt._
import Keys.name

object TestBuild extends MakeBuild
{
	lazy val a = proj("a", ".")
}
object SecondBuild extends MakeBuild
{
	lazy val b = proj("b", "b")
}
trait MakeBuild extends Build
{
	import AddSettings._
	def proj(id: String, dir: String) = Project(id, file(dir), settings = Seq( name := id ) ).settingSets(buildScalaFiles, defaultSbtFiles)
}
