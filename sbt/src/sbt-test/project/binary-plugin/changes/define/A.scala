package sbttest // you need package http://stackoverflow.com/questions/9822008/

import sbt._
import Keys._

object C extends AutoPlugin {
	object autoImport {
		object bN extends AutoPlugin {
			def requires = empty
			def trigger = allRequirements
		}
		lazy val check = taskKey[Unit]("Checks that the AutoPlugin and Build are automatically added.")			
	}
	def requires = empty
	def trigger = noTrigger
}

	import C.autoImport._

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
