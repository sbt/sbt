/* sbt -- Simple Build Tool
 * Copyright 2011 Mark Harrah
 */
package sbt

	import java.io.File
	import java.net.URI
	import Project._
	import Command.{HistoryPath,Watch}
	import CommandSupport.logger

final case class Project(id: String, base: File, aggregate: Seq[ProjectRef] = Nil, dependencies: Seq[Project.ClasspathDependency] = Nil, inherits: Seq[ProjectRef] = Nil,
	settings: Seq[Project.Setting[_]] = Project.defaultSettings, configurations: Seq[Configuration] = Configurations.default)
{
	def uses = aggregate ++ dependencies.map(_.project)
}

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
	
	def current(state: State): (URI, String) =
	{
		val s = session(state)
		val uri = s.currentBuild
		(uri, s.currentProject(uri))
	}
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
	def transform(g: Scope => Scope, ss: Seq[Setting[_]]): Seq[Setting[_]] = {
		val f = mapScope(g)
		ss.map(_ mapKey f)
	}

	val SessionKey = AttributeKey[SessionSettings]("session-settings")
	val StructureKey = AttributeKey[Load.BuildStructure]("build-structure")
}

	import SessionSettings._

final class SessionSettings(val currentBuild: URI, val currentProject: Map[URI, String], val original: Seq[Setting[_]], val prepend: SessionMap, val append: SessionMap) {
	assert(currentProject contains currentBuild, "Current build (" + currentBuild + ") not associated with a current project.")
	def setCurrent(build: URI, project: String): SessionSettings = new SessionSettings(build, currentProject.updated(build, project), original, prepend, append)
	def current: (URI, String) = (currentBuild, currentProject(currentBuild))
}
object SessionSettings {
	type SessionSetting = (Setting[_], String)
	type SessionMap = Map[(URI, String), Seq[SessionSetting]]
}

trait ProjectConstructors
{
	implicit def configDependencyConstructor[T <% ProjectRef](p: T): Project.Constructor = new Project.Constructor(p)
	implicit def classpathDependency[T <% ProjectRef](p: T): Project.ClasspathDependency = new Project.ClasspathDependency(p, None)
}

final case class ProjectRef(uri: Option[URI], id: Option[String])
object ProjectRef
{
	def apply(base: URI, id: String): ProjectRef = ProjectRef(Some(base), Some(id))
	/** Reference to the project with 'id' in the current build unit.*/
	def apply(id: String): ProjectRef = ProjectRef(None, Some(id))
	def apply(base: File, id: String): ProjectRef = ProjectRef(Some(base.toURI), Some(id))
	/** Reference to the root project at 'base'.*/
	def apply(base: URI): ProjectRef = ProjectRef(Some(base), None)
	/** Reference to the root project at 'base'.*/
	def apply(base: File): ProjectRef = ProjectRef(Some(base.toURI), None)
	/** Reference to the root project in the current build unit.*/
	def root = ProjectRef(None, None)

	implicit def stringToRef(s: String): ProjectRef = ProjectRef(s)
	implicit def projectToRef(p: Project): ProjectRef = ProjectRef(p.id)
	implicit def uriToRef(u: URI): ProjectRef = ProjectRef(u)
	implicit def fileToRef(f: File): ProjectRef = ProjectRef(f)
}