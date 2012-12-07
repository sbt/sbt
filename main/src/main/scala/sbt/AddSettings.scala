package sbt

	import Types.const
	import java.io.File

/** Represents how settings from various sources are automatically merged into a Project's settings.
* This only configures per-project settings and not global or per-build settings. */
sealed abstract class AddSettings
	
object AddSettings
{
	private[sbt] final class Sequence(val sequence: Seq[AddSettings]) extends AddSettings
	private[sbt] final object User extends AddSettings
	private[sbt] final class Plugins(val include: Plugin => Boolean) extends AddSettings
	private[sbt] final class DefaultSbtFiles(val include: File => Boolean) extends AddSettings
	private[sbt] final class SbtFiles(val files: Seq[File]) extends AddSettings

	/** Adds all settings from a plugin to a project. */
	val allPlugins: AddSettings = plugins(const(true))

	/** Allows the plugins whose names match the `names` filter to automatically add settings to a project. */
	def plugins(include: Plugin => Boolean): AddSettings = new Plugins(include)

	/** Includes user settings in the project. */
	val userSettings: AddSettings = User

	/** Includes the settings from all .sbt files in the project's base directory. */
	val defaultSbtFiles: AddSettings = new DefaultSbtFiles(const(true))

	/** Includes the settings from the .sbt files given by `files`. */
	def sbtFiles(files: File*): AddSettings = new SbtFiles(files)

	/** Includes settings automatically*/
	def seq(autos: AddSettings*): AddSettings = new Sequence(autos)

	val allDefaults: AddSettings = seq(userSettings, allPlugins, defaultSbtFiles)
}

