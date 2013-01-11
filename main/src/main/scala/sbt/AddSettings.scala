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

	/** Combines two automatic setting configurations. */
	def append(a: AddSettings, b: AddSettings): AddSettings = (a,b) match {
		case (sa: Sequence, sb: Sequence) => seq(sa.sequence ++ sb.sequence : _*)
		case (sa: Sequence, _) => seq(sa.sequence :+ b : _*)
		case (_, sb: Sequence) => seq(a +: sb.sequence : _*)
		case _ => seq(a,b)
	}

	def clearSbtFiles(a: AddSettings): AddSettings = tx(a) {
		case _: DefaultSbtFiles | _: SbtFiles => None
		case x => Some(x)
	} getOrElse seq()

	private[sbt] def tx(a: AddSettings)(f: AddSettings => Option[AddSettings]): Option[AddSettings] = a match {
		case s: Sequence =>
			s.sequence.flatMap { b => tx(b)(f) } match {
				case Seq() => None
				case Seq(x) => Some(x)
				case ss => Some(new Sequence(ss))
			}
		case x => f(x)
	}
}

