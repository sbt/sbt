package sbttest // you need package http://stackoverflow.com/questions/9822008/

	import sbt._, Keys._
	import java.util.concurrent.atomic.{AtomicInteger => AInt}

	object A extends AutoPlugin { override def requires: Plugins = empty }
	object B extends AutoPlugin { override def requires: Plugins = empty }
	object E extends AutoPlugin { override def requires: Plugins = empty }

object Imports
{
	lazy val Quux = config("q")
	lazy val Pippy = config("p").extend(Quux)

	lazy val demo = settingKey[String]("A demo setting.")
	lazy val del = settingKey[String]("Another demo setting.")

	lazy val check = taskKey[Unit]("Verifies settings are as they should be.")
}

object OrgPlugin extends AutoPlugin {
	override def trigger = allRequirements
	override def requires: Plugins = empty
	override def projectSettings = Seq(
      organization := "override"
	)
}

object X extends AutoPlugin {
	val autoImport = Imports
}

	import Imports._

object D extends AutoPlugin {
	override def requires: Plugins = E
	override def trigger = allRequirements

	object autoImport {
		lazy val keyTest = settingKey[String]("Another demo setting.")
	}
}

object Q extends AutoPlugin
{
	override def requires: Plugins = A && B
	override def trigger = allRequirements

	override def projectConfigurations: Seq[Configuration] =
		Pippy ::
		Quux ::
		Nil

   override def projectSettings: Seq[Setting[_]] =
		(demo := s"project ${name.value}") ::
		(del in Quux := " Q") ::
		Nil

   override def buildSettings: Seq[Setting[_]] =
		(demo := s"build ${buildCount.getAndIncrement}") ::
		Nil

   override def globalSettings: Seq[Setting[_]] =
		(demo := s"global ${globalCount.getAndIncrement}") ::
		Nil

	// used to ensure the build-level and global settings are only added once
	private[this] val buildCount = new AInt(0)
	private[this] val globalCount = new AInt(0)
}

object R extends AutoPlugin
{
	// NOTE - Only plugins themselves support exclusions...
	override def requires = Q
	override def trigger = allRequirements

	override def projectSettings = Seq(
		// tests proper ordering: R requires Q, so Q settings should come first
		del in Quux += " R",
		// tests that configurations are properly registered, enabling delegation from p to q
		demo += (del in Pippy).value
	)
}

// This is an opt-in plugin with a requirement
// Unless explicitly loaded by the build user, this will not be activated.
object S extends AutoPlugin
{
	override def requires = Q
	override def trigger = noTrigger

	override def projectSettings = Seq(
		del in Quux += " S",
		organization := "S"
	)
}
