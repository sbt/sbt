package sbttest // you need package http://stackoverflow.com/questions/9822008/

	import sbt._
	import sbt.Keys.{name, resolvedScoped}
	import java.util.concurrent.atomic.{AtomicInteger => AInt}

object Imports
{
	object A extends AutoPlugin
	object B extends AutoPlugin
	object E extends AutoPlugin

	lazy val q = config("q")
	lazy val p = config("p").extend(q)

	lazy val demo = settingKey[String]("A demo setting.")
	lazy val del = settingKey[String]("Another demo setting.")

	lazy val check = taskKey[Unit]("Verifies settings are as they should be.")
}

object X extends AutoPlugin {
	val autoImport = Imports
}

	import Imports._

object D extends AutoPlugin {
	override def requires: Plugins = E
	override def trigger = allRequirements
}

object Q extends AutoPlugin
{
	override def requires: Plugins = A && B
	override def trigger = allRequirements

	override def projectConfigurations: Seq[Configuration] =
		p ::
		q ::
		Nil

   override def projectSettings: Seq[Setting[_]] =
		(demo := s"project ${name.value}") ::
		(del in q := " Q") ::
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
		del in q += " R",
		// tests that configurations are properly registered, enabling delegation from p to q
		demo += (del in p).value
	)
}

// This is an opt-in plugin with a requirement
// Unless explicitly loaded by the build user, this will not be activated.
object S extends AutoPlugin
{
	override def requires = Q
	override def trigger = noTrigger

	override def projectSettings = Seq(
		del in q += " S"
	)
}
