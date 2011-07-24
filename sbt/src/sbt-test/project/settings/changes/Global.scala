import sbt._
import Keys._

object P extends Plugin
{
	override def settings = Seq(
		maxErrors ~= (x => x*x)
	)
}
