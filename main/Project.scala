/* sbt -- Simple Build Tool
 * Copyright 2011 Mark Harrah
 */
package sbt

	import java.io.File
	import java.net.URI
	import Project._
	import Types.Endo
	import Keys.{appConfiguration, buildStructure, commands, configuration, historyPath, projectCommand, sessionSettings, shellPrompt, thisProject, thisProjectRef, watch}
	import Scope.ThisScope
	import CommandSupport.logger
	import compiler.Eval

sealed trait ProjectDefinition[PR <: ProjectReference]
{
	def id: String
	def base: File
	def configurations: Seq[Configuration]
	def settings: Seq[Project.Setting[_]]
	def aggregate: Seq[PR]
	def delegates: Seq[PR]
	def dependencies: Seq[Project.ClasspathDep[PR]]
	def uses: Seq[PR] = aggregate ++ dependencies.map(_.project)
	def referenced: Seq[PR] = delegates ++ uses
}
final case class ResolvedProject(id: String, base: File, aggregate: Seq[ProjectRef], dependencies: Seq[Project.ResolvedClasspathDependency], delegates: Seq[ProjectRef],
	settings: Seq[Project.Setting[_]], configurations: Seq[Configuration]) extends ProjectDefinition[ProjectRef]

final case class Project(id: String, base: File, aggregate: Seq[ProjectReference] = Nil, dependencies: Seq[Project.ClasspathDependency] = Nil, delegates: Seq[ProjectReference] = Nil,
	settings: Seq[Project.Setting[_]] = Project.defaultSettings, configurations: Seq[Configuration] = Configurations.default) extends ProjectDefinition[ProjectReference]
{
	def dependsOn(deps: Project.ClasspathDependency*): Project = copy(dependencies = dependencies ++ deps)
	def delegates(from: ProjectReference*): Project = copy(delegates = delegates ++ from)
	def aggregate(refs: ProjectReference*): Project = copy(aggregate = aggregate ++ refs)
	def configs(cs: Configuration*): Project = copy(configurations = configurations ++ cs)
	def settings(ss: Project.Setting[_]*): Project = copy(settings = settings ++ ss)
	def resolve(resolveRef: ProjectReference => ProjectRef): ResolvedProject =
	{
		def resolveRefs(prs: Seq[ProjectReference]) = prs map resolveRef
		def resolveDeps(ds: Seq[Project.ClasspathDependency]) = ds map resolveDep
		def resolveDep(d: Project.ClasspathDependency) = Project.ResolvedClasspathDependency(resolveRef(d.project), d.configuration)
		ResolvedProject(id, base, aggregate = resolveRefs(aggregate), dependencies = resolveDeps(dependencies), delegates = resolveRefs(delegates), settings, configurations)
	}
	def resolveBuild(resolveRef: ProjectReference => ProjectReference): Project =
	{
		def resolveRefs(prs: Seq[ProjectReference]) = prs map resolveRef
		def resolveDeps(ds: Seq[Project.ClasspathDependency]) = ds map resolveDep
		def resolveDep(d: Project.ClasspathDependency) = Project.ClasspathDependency(resolveRef(d.project), d.configuration)
		copy(aggregate = resolveRefs(aggregate), dependencies = resolveDeps(dependencies), delegates = resolveRefs(delegates))
	}
}
final case class Extracted(structure: Load.BuildStructure, session: SessionSettings, currentRef: ProjectRef, rootProject: URI => String)
{
	lazy val currentUnit = structure units curi
	lazy val currentProject = currentUnit defined cid
	def curi = currentRef.build
	def cid = currentRef.project
}

object Project extends Init[Scope]
{
	def defaultSettings: Seq[Setting[_]] = Defaults.defaultSettings

	sealed trait ClasspathDep[PR <: ProjectReference] { def project: PR; def configuration: Option[String] }
	final case class ResolvedClasspathDependency(project: ProjectRef, configuration: Option[String]) extends ClasspathDep[ProjectRef]
	final case class ClasspathDependency(project: ProjectReference, configuration: Option[String]) extends ClasspathDep[ProjectReference]
	final class Constructor(p: ProjectReference) {
		def %(conf: String): ClasspathDependency = new ClasspathDependency(p, Some(conf))
	}

	def getOrError[T](state: State, key: AttributeKey[T], msg: String): T = state get key getOrElse error(msg)
	def structure(state: State): Load.BuildStructure = getOrError(state, buildStructure, "No build loaded.")
	def session(state: State): SessionSettings = getOrError(state, sessionSettings, "Session not initialized.")
	def extract(state: State): Extracted =
	{
		val se = session(state)
		val st = structure(state)
		Extracted(st, se, se.current, Load.getRootProject(st.units))
	}

	def getProjectForReference(ref: Reference, structure: Load.BuildStructure): Option[ResolvedProject] =
		ref match { case pr: ProjectRef => getProject(pr, structure); case _ => None }
	def getProject(ref: ProjectRef, structure: Load.BuildStructure): Option[ResolvedProject] =
		(structure.units get ref.build).flatMap(_.defined get ref.project)

	def setProject(session: SessionSettings, structure: Load.BuildStructure, s: State): State =
	{
		val newAttrs = s.attributes.put(buildStructure, structure).put(sessionSettings, session)
		val newState = s.copy(attributes = newAttrs)
		updateCurrent(newState.runExitHooks())
	}
	def current(state: State): ProjectRef = session(state).current
	def updateCurrent(s: State): State =
	{
		val structure = Project.structure(s)
		val ref = Project.current(s)
		val project = Load.getProject(structure.units, ref.build, ref.project)
		logger(s).info("Set current project to " + ref.project + " (in build " + ref.build +")")
		def get[T](k: SettingKey[T]): Option[T] = k in ref get structure.data

		val history = get(historyPath) flatMap identity
		val prompt = get(shellPrompt)
		val watched = get(watch)
		val commandDefs = get(commands).toList.flatten[Command].map(_ tag (projectCommand, true))
		val newProcessors = commandDefs ++ BuiltinCommands.removeTagged(s.processors, projectCommand)
		val newAttrs = setCond(Watched.Configuration, watched, s.attributes).put(historyPath.key, history)
		s.copy(attributes = setCond(shellPrompt.key, prompt, newAttrs), processors = newProcessors)
	}
	def setCond[T](key: AttributeKey[T], vopt: Option[T], attributes: AttributeMap): AttributeMap =
		vopt match { case Some(v) => attributes.put(key, v); case None => attributes.remove(key) }
	def makeSettings(settings: Seq[Setting[_]], delegates: Scope => Seq[Scope], scopeLocal: ScopedKey[_] => Seq[Setting[_]]) =
		translateUninitialized( make(settings)(delegates, scopeLocal) )

	def display(scoped: ScopedKey[_]): String = Scope.display(scoped.scope, scoped.key.label)
	def display(ref: Reference): String =
		ref match
		{
			case pr: ProjectReference => display(pr)
			case br: BuildReference => display(br)
		}
	def display(ref: BuildReference) =
		ref match
		{
			case ThisBuild => "<this>"
			case BuildRef(uri) => "[" + uri + "]"
		}
	def display(ref: ProjectReference) =
		ref match
		{
			case ThisProject => "(<this>)<this>"
			case LocalProject(id) => "(<this>)" + id
			case RootProject(uri) => "(" + uri + ")<root>"
			case ProjectRef(uri, id) => "(" + uri + ")" + id
		}

	def fillTaskAxis(scoped: ScopedKey[_]): ScopedKey[_] =
		ScopedKey(Scope.fillTaskAxis(scoped.scope, scoped.key), scoped.key)

	def mapScope(f: Scope => Scope) = new  (ScopedKey ~> ScopedKey) { def apply[T](key: ScopedKey[T]) =
		ScopedKey( f(key.scope), key.key)
	}

	def transform(g: Scope => Scope, ss: Seq[Setting[_]]): Seq[Setting[_]] = {
		val f = mapScope(g)
		ss.map(_ mapKey f mapReferenced f)
	}
	def transformRef(g: Scope => Scope, ss: Seq[Setting[_]]): Seq[Setting[_]] = {
		val f = mapScope(g)
		ss.map(_ mapReferenced f)
	}
	def translateUninitialized[T](f: => T): T =
		try { f } catch { case u: Project.Uninitialized =>
			val msg = "Uninitialized reference to " + display(u.key) + " from " + display(u.refKey)
			throw new Uninitialized(u.key, u.refKey, msg)
		}

	def delegates(structure: Load.BuildStructure, scope: Scope, key: AttributeKey[_]): Seq[ScopedKey[_]] =
		structure.delegates(scope).map(d => ScopedKey(d, key))

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
		val reverse = reverseDependencies(cMap, scoped)
		def printScopes(label: String, scopes: Iterable[ScopedKey[_]]) =
			if(scopes.isEmpty) "" else scopes.map(display).mkString(label + ":\n\t", "\n\t", "\n")

		value + "\n" +
			definedIn + "\n" +
			printScopes("Dependencies", depends) +
			printScopes("Reverse dependencies", reverse) +
			printScopes("Delegates", delegates(structure, scope, key)) +
			printScopes("Related", related)
	}
	def reverseDependencies(cMap: CompiledMap, scoped: ScopedKey[_]): Iterable[ScopedKey[_]] =
		for( (key,compiled) <- cMap; dep <- compiled.dependencies if dep == scoped)  yield  key

	def inConfig(conf: Configuration)(ss: Seq[Setting[_]]): Seq[Setting[_]] =
		inScope(ThisScope.copy(config = Select(conf)) )( (configuration :== conf) +: ss)
	def inTask(t: Scoped)(ss: Seq[Setting[_]]): Seq[Setting[_]] =
		inScope(ThisScope.copy(task = Select(t.key)) )( ss )
	def inScope(scope: Scope)(ss: Seq[Setting[_]]): Seq[Setting[_]] =
		Project.transform(Scope.replaceThis(scope), ss)
}

	import SessionSettings._

final case class SessionSettings(currentBuild: URI, currentProject: Map[URI, String], original: Seq[Setting[_]], prepend: SessionMap, append: SessionMap, currentEval: () => Eval)
{
	assert(currentProject contains currentBuild, "Current build (" + currentBuild + ") not associated with a current project.")
	def setCurrent(build: URI, project: String, eval: () => Eval): SessionSettings = copy(currentBuild = build, currentProject = currentProject.updated(build, project), currentEval = eval)
	def current: ProjectRef = ProjectRef(currentBuild, currentProject(currentBuild))
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
			reapply(session.copy(original = session.mergeSettings, append = Map.empty, prepend = Map.empty), s)
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

trait ProjectConstructors
{
	implicit def configDependencyConstructor[T <% ProjectReference](p: T): Project.Constructor = new Project.Constructor(p)
	implicit def classpathDependency[T <% ProjectReference](p: T): Project.ClasspathDependency = new Project.ClasspathDependency(p, None)
}

