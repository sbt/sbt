package sbttest // you need package http://stackoverflow.com/questions/9822008/

import sbt._
import Keys._

object Imports {
	object bN extends AutoPlugin {
		def select = Plugins.empty
	}
	lazy val check = taskKey[Unit]("Checks that the AutoPlugin and Build are automatically added.")	
}

object C extends AutoPlugin {
	override lazy val autoImport = Imports
	def select = Plugins.empty
}

	import Imports._

object A extends AutoPlugin {
	override def select = bN
	override def projectSettings = Seq(
		check := {}
	)
}

object B extends Build {
	lazy val extra = project.addPlugins(bN)
}
