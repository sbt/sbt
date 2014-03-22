	import sbt._

object AI extends AutoImport
{
	trait EmptyAutoPlugin extends AutoPlugin {
		def requires = empty
		def trigger = noTrigger
	}
	object A extends EmptyAutoPlugin {
	  val a = settingKey[String]("")
	  override def projectSettings = Seq(a := "a")
	}
	object B extends EmptyAutoPlugin {
	  val b = settingKey[String]("")
	  override def projectSettings = Seq(b := "b")
	}

	lazy val check = settingKey[Unit]("Verifies settings are as they should be.")
}

	import AI._

object Q extends AutoPlugin
{
	def requires: Plugins = A && B
	def trigger = allRequirements
	val q = settingKey[String]("")
	override def projectSettings = Seq(q := "q")
}

object R extends AutoPlugin
{
	def requires = Q
	def trigger = allRequirements
	val r = settingKey[String]("")
	override def projectSettings = Seq(r := "r")
}

// This is an opt-in plugin with a requirement
// Unless explicitly loaded by the build user, this will not be activated.
object S extends AutoPlugin
{
	def requires = Q && !R
	def trigger = noTrigger
	val s = settingKey[String]("")
	override def projectSettings = Seq(s := "s")
}

// This is an opt-in plugin with a requirement
// Unless explicitly loaded by the build user, this will not be activated.
object T extends AutoPlugin
{
	def requires = A && !Q
	def trigger = noTrigger

	val t = settingKey[String]("")
	override def projectSettings = Seq(t := "T")
}
