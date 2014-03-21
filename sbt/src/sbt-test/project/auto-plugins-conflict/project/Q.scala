	import sbt._

object AI extends AutoImport
{
	trait EmptyAutoPlugin extends AutoPlugin {
		def requires = empty
		def trigger = noTrigger
	}
	object A extends EmptyAutoPlugin
	object B extends EmptyAutoPlugin

	lazy val check = settingKey[Unit]("Verifies settings are as they should be.")
}

	import AI._

object Q extends AutoPlugin
{
	def requires: Plugins = A && B
	def trigger = allRequirements
}

object R extends AutoPlugin
{
	def requires = Q
	def trigger = allRequirements
}

// This is an opt-in plugin with a requirement
// Unless explicitly loaded by the build user, this will not be activated.
object S extends AutoPlugin
{
	def requires = Q && !R
	def trigger = noTrigger
}
