/* sbt -- Simple Build Tool
 * Copyright 2011 Mark Harrah
 */
package sbt

	import java.io.File
	import java.net.URI
	import Project._
	import Types.Endo
	import compiler.Eval

	import SessionSettings._

final case class SessionSettings(currentBuild: URI, currentProject: Map[URI, String], original: Seq[Setting[_]], append: SessionMap, currentEval: () => Eval)
{
	assert(currentProject contains currentBuild, "Current build (" + currentBuild + ") not associated with a current project.")
	def setCurrent(build: URI, project: String, eval: () => Eval): SessionSettings = copy(currentBuild = build, currentProject = currentProject.updated(build, project), currentEval = eval)
	def current: ProjectRef = ProjectRef(currentBuild, currentProject(currentBuild))
	def appendSettings(s: Seq[SessionSetting]): SessionSettings = copy(append = modify(append, _ ++ s))
	def mergeSettings: Seq[Setting[_]] = original ++ merge(append)
	def clearExtraSettings: SessionSettings = copy(append = Map.empty)

	private[this] def merge(map: SessionMap): Seq[Setting[_]] = map.values.toSeq.flatten[SessionSetting].map(_._1)
	private[this] def modify(map: SessionMap, onSeq: Endo[Seq[SessionSetting]]): SessionMap =
	{
		val cur = current
		map.updated(cur, onSeq(map.getOrElse( cur, Nil)))
	}
}
object SessionSettings
{
	type SessionSetting = (Setting[_], String)
	type SessionMap = Map[ProjectRef, Seq[SessionSetting]]

	def reapply(session: SessionSettings, s: State): State =
		BuiltinCommands.reapply(session, Project.structure(s), s)

	def clearSettings(s: State): State =
		withSettings(s)(session => reapply(session.copy(append = session.append - session.current), s))
	def clearAllSettings(s: State): State =
		withSettings(s)(session => reapply(session.clearExtraSettings, s))

	def withSettings(s: State)(f: SessionSettings => State): State =
	{
		val extracted = Project extract s
		import extracted._
		if(session.append.isEmpty)
		{
			s.log.info("No session settings defined.")
			s
		}
		else
			f(session)
	}

	def pluralize(size: Int, of: String) = size.toString + (if(size == 1) of else (of + "s"))
	def checkSession(newSession: SessionSettings, oldState: State)
	{
		val oldSettings = (oldState get Keys.sessionSettings).toList.flatMap(_.append).flatMap(_._2)
		if(newSession.append.isEmpty && !oldSettings.isEmpty)
			oldState.log.warn("Discarding " + pluralize(oldSettings.size, " session setting") + ".  Use 'session save' to persist session settings.")
	}
	def removeRanges[T](in: Seq[T], ranges: Seq[(Int,Int)]): Seq[T] =
	{
		val asSet = (Set.empty[Int] /: ranges) { case (s, (hi,lo)) => s ++ (hi to lo) }
		in.zipWithIndex.flatMap { case (t, index) => if(asSet(index+1)) Nil else t :: Nil  }
	}
	def removeSettings(s: State, ranges: Seq[(Int,Int)]): State =
		withSettings(s) { session =>
			val current = session.current
			val newAppend = session.append.updated(current, removeRanges(session.append.getOrElse(current, Nil), ranges))
			reapply(session.copy( append = newAppend ), s)
		}
	def saveAllSettings(s: State): State = saveSomeSettings(s)(_ => true)
	def saveSettings(s: State): State =
	{
		val current = Project.session(s).current
		saveSomeSettings(s)( _ == current)
	}
	def saveSomeSettings(s: State)(include: ProjectRef => Boolean): State =
		withSettings(s){session =>
			for( (ref, settings) <- session.append if !settings.isEmpty && include(ref) )
				writeSettings(ref, settings, Project.structure(s))
			reapply(session.copy(original = session.mergeSettings, append = Map.empty), s)
		}
	def writeSettings(pref: ProjectRef, settings: Seq[SessionSetting], structure: Load.BuildStructure)
	{
		val project = Project.getProject(pref, structure).getOrElse(error("Invalid project reference " + pref))
		val appendTo: File = BuildPaths.configurationSources(project.base).headOption.getOrElse(new File(project.base, "build.sbt"))
		val baseAppend = settingStrings(settings).flatMap("" :: _ :: Nil)
		val adjustedLines = if(appendTo.isFile && hasTrailingBlank(IO readLines appendTo) ) baseAppend else "" +: baseAppend
		IO.writeLines(appendTo, adjustedLines, append = true)
	}
	def hasTrailingBlank(lines: Seq[String]) = lines.takeRight(1).exists(_.trim.isEmpty)
	def printAllSettings(s: State): State =
		withSettings(s){ session =>
			for( (ref, settings) <- session.append if !settings.isEmpty) {
				println("In " + Project.display(ref))
				printSettings(settings)
			}
			s
		}
	def printSettings(s: State): State =
		withSettings(s){ session =>
			printSettings(session.append.getOrElse(session.current, Nil))
			s
		}
	def printSettings(settings: Seq[SessionSetting]): Unit =
		for((stringRep, index) <- settingStrings(settings).zipWithIndex)
			println("  " + (index+1) + ". " + stringRep)

	def settingStrings(s: Seq[SessionSetting]): Seq[String] = s.map(_._2)

	def Help = """session <command>

Manipulates session settings, which are temporary settings that do not persist past the current sbt execution (that is, the current session).
Valid commands are:

clear, clear-all

	Removes temporary settings added using 'set' and re-evaluates all settings.
	For 'clear', only the settings defined for the current project are cleared.
	For 'clear-all', all settings in all projects are cleared.

list, list-all

	Prints a numbered list of session settings defined.
	The numbers may be used to remove individual settings or ranges of settings using 'remove'.
	For 'list', only the settings for the current project are printed.
	For 'list-all', all settings in all projets are printed.

remove <range-spec>

	<range-spec> is a comma-separated list of individual numbers or ranges of numbers.
	For example, 'remove 1,3,5-7'.
	The temporary settings at the given indices for the current project are removed and all settings are re-evaluated.
	Use the 'list' command to see a numbered list of settings for the current project.

save, save-all

	Makes the session settings permanent by writing them to a '.sbt' configuration file.
	For 'save', only the current project's settings are saved (the settings for other projects are left alone).
	For 'save-all', the session settings are saved for all projects.
	The session settings defined for a project are appended to the first '.sbt' configuration file in that project.
	If no '.sbt' configuration file exists, the settings are written to 'build.sbt' in the project's base directory."""

	sealed trait SessionCommand
	final class Print(val all: Boolean) extends SessionCommand
	final class Clear(val all: Boolean) extends SessionCommand
	final class Save(val all: Boolean) extends SessionCommand
	final class Remove(val ranges: Seq[(Int,Int)]) extends SessionCommand

	import complete._
	import DefaultParsers._

	lazy val parser =
		token(Space) ~>
		(token("list-all" ^^^ new Print(true)) | token("list" ^^^ new Print(false)) | token("clear" ^^^ new Clear(false)) |
		token("save-all" ^^^ new Save(true)) | token("save" ^^^ new Save(false)) | token("clear-all" ^^^ new Clear(true)) |
		remove)

	lazy val remove = token("remove") ~> token(Space) ~> natSelect.map(ranges => new Remove(ranges))
	def natSelect = rep1sep(token(range, "<range>"), ',')
	def range: Parser[(Int,Int)] = (NatBasic ~ ('-' ~> NatBasic).?).map { case lo ~ hi => (lo, hi getOrElse lo)}

	def command(s: State) = Command.applyEffect(parser){
		case p: Print => if(p.all) printAllSettings(s) else printSettings(s)
		case v: Save => if(v.all) saveAllSettings(s) else saveSettings(s)
		case c: Clear => if(c.all) clearAllSettings(s) else clearSettings(s)
		case r: Remove => removeSettings(s,r.ranges)
	}
}
