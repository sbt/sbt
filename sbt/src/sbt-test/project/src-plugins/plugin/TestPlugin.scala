import sbt._

object TestPlugin extends Plugin
{
	val Check = TaskKey[Unit]("check")
	override def settings = Seq(
		Check := assert(JavaTest.X == 9)
	)
}