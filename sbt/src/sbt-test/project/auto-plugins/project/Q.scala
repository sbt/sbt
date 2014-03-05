	import sbt._
	import sbt.Keys.{name, resolvedScoped}
	import java.util.concurrent.atomic.{AtomicInteger => AInt}

object AI extends AutoImport
{
	trait EmptyAutoPlugin extends AutoPlugin {
		def select = Plugins.empty
	}
	object A extends EmptyAutoPlugin
	object B extends EmptyAutoPlugin
	object E extends EmptyAutoPlugin

	lazy val q = config("q")
	lazy val p = config("p").extend(q)

	lazy val demo = settingKey[String]("A demo setting.")
	lazy val del = settingKey[String]("Another demo setting.")

	lazy val check = settingKey[Unit]("Verifies settings are as they should be.")
}

	import AI._

object D extends AutoPlugin {
	def select: Plugins = E
}

object Q extends AutoPlugin
{
	def select: Plugins = A && B

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
	def select = Q && !D

	override def projectSettings = Seq(
		// tests proper ordering: R requires C, so C settings should come first
		del in q += " R",
		// tests that configurations are properly registered, enabling delegation from p to q
		demo += (del in p).value
	)
}