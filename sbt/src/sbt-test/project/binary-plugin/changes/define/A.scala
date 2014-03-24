import sbt._
import Keys._


object C extends AutoImport {
	object bN extends AutoPlugin {
		def requires = empty
		def trigger = allRequirements
	}
	lazy val check = taskKey[Unit]("Checks that the AutoPlugin and Build are automatically added.")
}

	import C._

object A extends AutoPlugin {
	def requires = bN
	def trigger = allRequirements
	override def projectSettings = Seq(
		check := {}
	)
}

object B extends Build {
	lazy val extra = project.addPlugins(bN)
}
