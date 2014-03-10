import sbt._
import Keys._


object C extends AutoImport {
	object bN extends AutoPlugin {
		def select = Plugins.empty
	}
	lazy val check = taskKey[Unit]("Checks that the AutoPlugin and Build are automatically added.")
}

	import C._

object A extends AutoPlugin {
	override def select = bN
	override def projectSettings = Seq(
		check := {}
	)
}

object B extends Build {
	lazy val extra = project.addPlugins(bN)
}
