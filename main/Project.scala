/* sbt -- Simple Build Tool
 * Copyright 2011 Mark Harrah
 */
package sbt

	import java.io.File
	import java.net.URI
	import Project._
	import Types.Endo
	import Command.{HistoryPath,Watch}
	import CommandSupport.logger
	import compile.Eval

final case class Project(id: String, base: File, aggregate: Seq[ProjectRef] = Nil, dependencies: Seq[Project.ClasspathDependency] = Nil, inherits: Seq[ProjectRef] = Nil,
	settings: Seq[Project.Setting[_]] = Project.defaultSettings, configurations: Seq[Configuration] = Configurations.default)
{
	def dependsOn(deps: Project.ClasspathDependency*): Project = copy(dependencies = dependencies ++ deps)
	def inherits(from: ProjectRef*): Project = copy(inherits = inherits ++ from)
	def aggregate(refs: ProjectRef*): Project = copy(aggregate = aggregate ++ refs)
	def configs(cs: Configuration*): Project = copy(configurations = configurations ++ cs)
	def settings(ss: Project.Setting[_]*): Project = copy(settings = settings ++ ss)
	
	def uses = aggregate ++ dependencies.map(_.project)
}
final case class Extracted(structure: Load.BuildStructure, session: SessionSettings, curi: URI, cid: String, rootProject: URI => String)

object Project extends Init[Scope]
{
	def defaultSettings: Seq[Setting[_]] = Default.defaultSettings

	final case class ClasspathDependency(project: ProjectRef, configuration: Option[String])
	final class Constructor(p: ProjectRef) {
		def %(conf: String): ClasspathDependency = new ClasspathDependency(p, Some(conf))
	}

	def getOrError[T](state: State, key: AttributeKey[T], msg: String): T = state get key getOrElse error(msg)
	def structure(state: State): Load.BuildStructure = getOrError(state, StructureKey, "No build loaded.")
	def session(state: State): SessionSettings = getOrError(state, SessionKey, "Session not initialized.")
	def extract(state: State): Extracted =
	{
		val se = session(state)
		val (curi, cid) = se.current
		val st = structure(state)
		Extracted(st, se, curi, cid, Load.getRootProject(st.units))
	}

	def getProject(ref: ProjectRef, structure: Load.BuildStructure): Option[Project] =
		ref match {
			case ProjectRef(Some(uri), Some(id)) => (structure.units get uri).flatMap(_.defined get id)
			case _ => None
		}

	def setProject(session: SessionSettings, structure: Load.BuildStructure, s: State): State =
	{
		val newAttrs = s.attributes.put(StructureKey, structure).put(SessionKey, session)
		val newState = s.copy(attributes = newAttrs)
		updateCurrent(newState.runExitHooks())
	}
	def current(state: State): (URI, String) = session(state).current
	def currentRef(state: State): ProjectRef =
	{
		val (unit, it) = current(state)
		ProjectRef(Some(unit), Some(it))
	}
	def updateCurrent(s: State): State =
	{
		val structure = Project.structure(s)
		val (uri, id) = Project.current(s)
		val ref = ProjectRef(uri, id)
		val project = Load.getProject(structure.units, uri, id)
		logger(s).info("Set current project to " + id + " (in build " + uri +")")

		val data = structure.data
		val historyPath = HistoryPath in ref get data flatMap identity
		val newAttrs = s.attributes.put(Watch.key, makeWatched(data, ref, project)).put(HistoryPath.key, historyPath)
		s.copy(attributes = newAttrs)
	}
	def makeSettings(settings: Seq[Setting[_]], delegates: Scope => Seq[Scope], scopeLocal: ScopedKey[_] => Seq[Setting[_]]) =
		translateUninitialized( make(settings)(delegates, scopeLocal) )

	def makeWatched(data: Settings[Scope], ref: ProjectRef, project: Project): Watched =
	{
		def getWatch(ref: ProjectRef) = Watch in ref get data
		getWatch(ref) match
		{
			case Some(currentWatch) =>
				val subWatches = project.uses flatMap { p => getWatch(p) }
				Watched.multi(currentWatch, subWatches)
			case None => Watched.empty
		}
	}
	def display(scoped: ScopedKey[_]): String = Scope.display(scoped.scope, scoped.key.label)
	def display(ref: ProjectRef): String = "(" + (ref.uri map (_.toString) getOrElse "<this>") + ")" + (ref.id getOrElse "<root>")

	def mapScope(f: Scope => Scope) = new  (ScopedKey ~> ScopedKey) { def apply[T](key: ScopedKey[T]) =
		ScopedKey( f(key.scope), key.key)
	}
	def resolveThis(thisScope: Scope) = mapScope(Scope.replaceThis(thisScope))
	def transform(g: Scope => Scope, ss: Seq[Setting[_]]): Seq[Setting[_]] = {
		val f = mapScope(g)
		ss.map(_ mapKey f mapReferenced f)
	}
	def translateUninitialized[T](f: => T): T =
		try { f } catch { case u: Project.Uninitialized =>
			val msg = "Uninitialized reference to " + display(u.key) + " from " + display(u.refKey)
			throw new Uninitialized(u.key, u.refKey, msg)
		}

	def details(structure: Load.BuildStructure, scope: Scope, key: AttributeKey[_]): String =
	{
		val scoped = ScopedKey(scope,key)
		val value = 
			(structure.data.get(scope, key)) match {
				case None => "No entry for key."
				case Some(v: Task[_]) => "Task"
				case Some(v: InputTask[_]) => "Input task"
				case Some(v) => "Value:\n\t" + v.toString
			}
		val definedIn = structure.data.definingScope(scope, key) match { case Some(sc) => "Provided by:\n\t" + display(scoped); case None => "" }
		val cMap = compiled(structure.settings)(structure.delegates, structure.scopeLocal)
		val related = cMap.keys.filter(k => k.key == key && k.scope != scope)
		val depends = cMap.get(scoped) match { case Some(c) => c.dependencies.toSet; case None => Set.empty }
		def printScopes(label: String, scopes: Iterable[ScopedKey[_]]) =
			if(scopes.isEmpty) "" else scopes.map(display).mkString(label + ":\n\t", "\n\t", "\n")
		value + "\n" + definedIn + "\n" + printScopes("Dependencies", depends) + printScopes("Related", related)
	}

	val SessionKey = AttributeKey[SessionSettings]("session-settings")
	val StructureKey = AttributeKey[Load.BuildStructure]("build-structure")
	val AppConfig = SettingKey[xsbti.AppConfiguration]("app-configuration")
	val ThisProject = SettingKey[Project]("project")
	val ThisProjectRef = SettingKey[ProjectRef]("project-ref")
	val Config = SettingKey[Configuration]("configuration")
}

	import SessionSettings._

final case class SessionSettings(currentBuild: URI, currentProject: Map[URI, String], original: Seq[Setting[_]], prepend: SessionMap, append: SessionMap, currentEval: () => Eval)
{
	assert(currentProject contains currentBuild, "Current build (" + currentBuild + ") not associated with a current project.")
	def setCurrent(build: URI, project: String, eval: () => Eval): SessionSettings = copy(currentBuild = build, currentProject = currentProject.updated(build, project), currentEval = eval)
	def current: (URI, String) = (currentBuild, currentProject(currentBuild))
	def appendSettings(s: Seq[SessionSetting]): SessionSettings = copy(append = modify(append, _ ++ s))
	def prependSettings(s: Seq[SessionSetting]): SessionSettings = copy(prepend = modify(prepend, s ++ _))
	def mergeSettings: Seq[Setting[_]] = merge(prepend) ++ original ++ merge(append)
	def clearExtraSettings: SessionSettings = copy(prepend = Map.empty, append = Map.empty)

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
	type SessionMap = Map[(URI, String), Seq[SessionSetting]]

	def reapply(session: SessionSettings, s: State): State =
		Commands.reapply(session, Project.structure(s), s)
	
	def clearSettings(s: State): State =
		withSettings(s)(session => reapply(session.copy(append = session.append - session.current), s))
	def clearAllSettings(s: State): State =
		withSettings(s)(session => reapply(session.clearExtraSettings, s))

	def withSettings(s: State)(f: SessionSettings => State): State =
	{
		val extracted = Project extract s
		import extracted._
		if(session.prepend.isEmpty && session.append.isEmpty)
		{
			logger(s).info("No session settings defined.")
			s
		}
		else
			f(session)
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
	def saveAllSettings(s: State): State = saveSomeSettings(s)((_,_) => true)
	def saveSettings(s: State): State =
	{
		val (curi,cid) = Project.session(s).current
		saveSomeSettings(s)( (uri,id) => uri == curi && id == cid)
	}
	def saveSomeSettings(s: State)(include: (URI,String) => Boolean): State =
		withSettings(s){session =>
			for( ((uri,id), settings) <- session.append if !settings.isEmpty && include(uri,id) )
				writeSettings(ProjectRef(uri, id), settings, Project.structure(s))
			reapply(session.copy(original = session.mergeSettings, append = Map.empty, prepend = Map.empty), s)
		}
	def writeSettings(pref: ProjectRef, settings: Seq[SessionSetting], structure: Load.BuildStructure)
	{
		val project = Project.getProject(pref, structure).getOrElse(error("Invalid project reference " + pref))
		val appendTo: File = BuildPaths.configurationSources(project.base).headOption.getOrElse(new File(project.base, "build.sbt"))
		val sbtAppend = settingStrings(settings).flatMap("" :: _ :: Nil)
		IO.writeLines(appendTo, sbtAppend, append = true)
	}
	def printAllSettings(s: State): State =
		withSettings(s){ session =>
			for( ((uri,id), settings) <- session.append if !settings.isEmpty) {
				println("In " + Project.display(ProjectRef(uri,id)))
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

trait ProjectConstructors
{
	implicit def configDependencyConstructor[T <% ProjectRef](p: T): Project.Constructor = new Project.Constructor(p)
	implicit def classpathDependency[T <% ProjectRef](p: T): Project.ClasspathDependency = new Project.ClasspathDependency(p, None)
}
// the URI must be resolved and normalized before it is definitive
final case class ProjectRef(uri: Option[URI], id: Option[String])
object ProjectRef
{
	def apply(base: URI, id: String): ProjectRef = ProjectRef(Some(base), Some(id))
	/** Reference to the project with 'id' in the current build unit.*/
	def apply(id: String): ProjectRef = ProjectRef(None, Some(id))
	def apply(base: File, id: String): ProjectRef = ProjectRef(Some(IO.toURI(base)), Some(id))
	/** Reference to the root project at 'base'.*/
	def apply(base: URI): ProjectRef = ProjectRef(Some(base), None)
	/** Reference to the root project at 'base'.*/
	def apply(base: File): ProjectRef = ProjectRef(Some(IO.toURI(base)), None)
	/** Reference to the root project in the current build unit.*/
	def root = ProjectRef(None, None)

	implicit def stringToRef(s: String): ProjectRef = ProjectRef(s)
	implicit def projectToRef(p: Project): ProjectRef = ProjectRef(p.id)
	implicit def uriToRef(u: URI): ProjectRef = ProjectRef(u)
	implicit def fileToRef(f: File): ProjectRef = ProjectRef(f)
}