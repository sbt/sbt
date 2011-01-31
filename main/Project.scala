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
	def defaultSettings: Seq[Setting[_]] = Nil

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
		val historyPath = HistoryPath(ref).get(data).flatMap(identity)
		val newAttrs = s.attributes.put(Watch.key, makeWatched(data, ref, project)).put(HistoryPath.key, historyPath)
		s.copy(attributes = newAttrs)
	}
	def makeSettings(settings: Seq[Setting[_]], delegates: Scope => Seq[Scope]) = translateUninitialized( make(settings)(delegates) )

	def makeWatched(data: Settings[Scope], ref: ProjectRef, project: Project): Watched =
	{
		def getWatch(ref: ProjectRef) = Watch(ref).get(data)
		getWatch(ref) match
		{
			case Some(currentWatch) =>
				val subWatches = project.uses flatMap { p => getWatch(p) }
				Watched.multi(currentWatch, subWatches)
			case None => Watched.empty
		}
	}
	def display(scoped: ScopedKey[_]): String = Scope.display(scoped.scope, scoped.key.label)

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