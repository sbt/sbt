/* sbt -- Simple Build Tool
 * Copyright 2011 Mark Harrah
 */
package sbt

	import java.io.File
	import java.net.URI
	import Project._
	import Keys.{appConfiguration, buildStructure, commands, configuration, historyPath, logged, projectCommand, sessionSettings, shellPrompt, streams, thisProject, thisProjectRef, watch}
	import Scope.{GlobalScope,ThisScope}
	import CommandSupport.logger

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
	lazy val currentUnit = structure units currentRef.build
	lazy val currentProject = currentUnit defined currentRef.project
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
	def updateCurrent(s0: State): State =
	{
		val structure = Project.structure(s0)
		val s = installGlobalLogger(s0, structure)
		val ref = Project.current(s)
		val project = Load.getProject(structure.units, ref.build, ref.project)
		logger(s).info("Set current project to " + ref.project + " (in build " + ref.build +")")
		def get[T](k: SettingKey[T]): Option[T] = k in ref get structure.data
		def commandsIn(axis: ResolvedReference) = commands in axis get structure.data toList ;

		val allCommands = commandsIn(ref) ++ commandsIn(BuildRef(ref.build)) ++ (commands in Global get structure.data toList )
		val history = get(historyPath) flatMap identity
		val prompt = get(shellPrompt)
		val watched = get(watch)
		val commandDefs = allCommands.distinct.flatten[Command].map(_ tag (projectCommand, true))
		val newDefinedCommands = commandDefs ++ BuiltinCommands.removeTagged(s.definedCommands, projectCommand)
		val newAttrs = setCond(Watched.Configuration, watched, s.attributes).put(historyPath.key, history)
		s.copy(attributes = setCond(shellPrompt.key, prompt, newAttrs), definedCommands = newDefinedCommands)
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

	object LoadAction extends Enumeration {
		val Return, Current, Plugins = Value
	}
	import LoadAction._
	import complete.DefaultParsers._

	val loadActionParser = token(Space ~> ("plugins" ^^^ Plugins | "return" ^^^ Return)) ?? Current
	
	val ProjectReturn = AttributeKey[List[File]]("project-return")
	def projectReturn(s: State): List[File] = s.attributes get ProjectReturn getOrElse Nil
	def setProjectReturn(s: State, pr: List[File]): State = s.copy(attributes = s.attributes.put( ProjectReturn, pr) )
	def loadAction(s: State, action: LoadAction.Value) = action match {
		case Return =>
			projectReturn(s) match
			{
				case current :: returnTo :: rest => (setProjectReturn(s, returnTo :: rest), returnTo)
				case _ => error("Not currently in a plugin definition")
			}
		case Current =>
			val base = s.configuration.baseDirectory
			projectReturn(s) match { case Nil => (setProjectReturn(s, base :: Nil), base); case x :: xs => (s, x) }
		case Plugins =>
			val extracted = Project.extract(s)
			val newBase = extracted.currentUnit.unit.plugins.base
			val newS = setProjectReturn(s, newBase :: projectReturn(s))
			(newS, newBase)
	}
	
	def evaluateTask[T](taskKey: ScopedKey[Task[T]], state: State, checkCycles: Boolean = false, maxWorkers: Int = EvaluateTask.SystemProcessors): Option[Result[T]] =
	{
		val extracted = Project.extract(state)
		EvaluateTask.evaluateTask(extracted.structure, taskKey, state, extracted.currentRef, checkCycles, maxWorkers)
	}
	def globalLoggerKey = fillTaskAxis(ScopedKey(GlobalScope, streams.key))
	def installGlobalLogger(s: State, structure: Load.BuildStructure): State =
	{
		val str = structure.streams(globalLoggerKey)
		str.open()
		s.put(logged, str.log).addExitHook { str.close() }
	}
}

trait ProjectConstructors
{
	implicit def configDependencyConstructor[T <% ProjectReference](p: T): Project.Constructor = new Project.Constructor(p)
	implicit def classpathDependency[T <% ProjectReference](p: T): Project.ClasspathDependency = new Project.ClasspathDependency(p, None)
}

