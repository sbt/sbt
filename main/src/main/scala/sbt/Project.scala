/* sbt -- Simple Build Tool
 * Copyright 2011 Mark Harrah
 */
package sbt

	import java.io.File
	import java.net.URI
	import Project.{Initialize => _, Setting => _, _}
	import Keys.{appConfiguration, stateBuildStructure, commands, configuration, historyPath, projectCommand, sessionSettings, shellPrompt, thisProject, thisProjectRef, watch}
	import Scope.{GlobalScope,ThisScope}
	import Def.{Flattened, Initialize, ScopedKey, Setting}
	import Types.idFun
	import complete.DefaultParsers

	import language.experimental.macros

sealed trait ProjectDefinition[PR <: ProjectReference]
{
	/** The project ID is used to uniquely identify a project within a build.
	* It is used to refer to a project from the command line and in the scope of keys. */
	def id: String

	/** The base directory for the project.*/
	def base: File

	/** The configurations for this project.  These are groups of related tasks and the main reason
	* to list them here is when one configuration extends another.  In this case, a setting lookup
	* in one configuration will fall back to the configurations it extends configuration if the setting doesn't exist. */
	def configurations: Seq[Configuration]

	/** The explicitly defined sequence of settings that configure this project.
	* These do not include the automatically appended settings as configured by `auto`. */
	def settings: Seq[Setting[_]]

	/** The references to projects that are aggregated by this project.
	* When a task is run on this project, it will also be run on aggregated projects. */
	def aggregate: Seq[PR]

	@deprecated("Delegation between projects should be replaced by directly sharing settings.", "0.13.0")
	def delegates: Seq[PR]

	/** The references to projects that are classpath dependencies of this project. */
	def dependencies: Seq[ClasspathDep[PR]]

	/** The references to projects that are aggregate and classpath dependencies of this project. */
	def uses: Seq[PR] = aggregate ++ dependencies.map(_.project)
	def referenced: Seq[PR] = delegates ++ uses

	/** Configures the sources of automatically appended settings.*/
	def auto: AddSettings

	override final def hashCode: Int = id.hashCode ^ base.hashCode ^ getClass.hashCode
	override final def equals(o: Any) = o match {
		case p: ProjectDefinition[_] => p.getClass == this.getClass && p.id == id && p.base == base
		case _ => false
	}
	override def toString = "Project(id: " + id + ", base: " + base + ", aggregate: " + aggregate + ", dependencies: " + dependencies + ", configurations: " + configurations + ")"
}
sealed trait Project extends ProjectDefinition[ProjectReference]
{
	def copy(id: String = id, base: File = base, aggregate: => Seq[ProjectReference] = aggregate, dependencies: => Seq[ClasspathDep[ProjectReference]] = dependencies,
		delegates: => Seq[ProjectReference] = delegates, settings: => Seq[Setting[_]] = settings, configurations: Seq[Configuration] = configurations,
		auto: AddSettings = auto): Project =
			Project(id, base, aggregate = aggregate, dependencies = dependencies, delegates = delegates, settings, configurations, auto)

	def resolve(resolveRef: ProjectReference => ProjectRef): ResolvedProject =
	{
		def resolveRefs(prs: Seq[ProjectReference]) = prs map resolveRef
		def resolveDeps(ds: Seq[ClasspathDep[ProjectReference]]) = ds map resolveDep
		def resolveDep(d: ClasspathDep[ProjectReference]) = ResolvedClasspathDependency(resolveRef(d.project), d.configuration)
		resolved(id, base, aggregate = resolveRefs(aggregate), dependencies = resolveDeps(dependencies), delegates = resolveRefs(delegates), settings, configurations, auto)
	}
	def resolveBuild(resolveRef: ProjectReference => ProjectReference): Project =
	{
		def resolveRefs(prs: Seq[ProjectReference]) = prs map resolveRef
		def resolveDeps(ds: Seq[ClasspathDep[ProjectReference]]) = ds map resolveDep
		def resolveDep(d: ClasspathDep[ProjectReference]) = ClasspathDependency(resolveRef(d.project), d.configuration)
		apply(id, base, aggregate = resolveRefs(aggregate), dependencies = resolveDeps(dependencies), delegates = resolveRefs(delegates), settings, configurations, auto)
	}

	/** Applies the given functions to this Project.
	* The second function is applied to the result of applying the first to this Project and so on.
	* The intended use is a convenience for applying default configuration provided by a plugin. */
	def configure(transforms: (Project => Project)*): Project = Function.chain(transforms)(this)

	/** Sets the base directory for this project.*/
	def in(dir: File): Project = copy(base = dir)

	/** Adds configurations to this project.  Added configurations replace existing configurations with the same name.*/
	def overrideConfigs(cs: Configuration*): Project = copy(configurations = Defaults.overrideConfigs(cs : _*)(configurations))

	/** Adds new configurations directly to this project.  To override an existing configuration, use `overrideConfigs`. */
	def configs(cs: Configuration*): Project = copy(configurations = configurations ++ cs)

	/** Adds classpath dependencies on internal or external projects. */
	def dependsOn(deps: ClasspathDep[ProjectReference]*): Project = copy(dependencies = dependencies ++ deps)

	@deprecated("Delegation between projects should be replaced by directly sharing settings.", "0.13.0")
	def delegateTo(from: ProjectReference*): Project = copy(delegates = delegates ++ from)

	/** Adds projects to be aggregated.  When a user requests a task to run on this project from the command line,
	* the task will also be run in aggregated projects. */
	def aggregate(refs: ProjectReference*): Project = copy(aggregate = (aggregate: Seq[ProjectReference]) ++ refs)

	/** Appends settings to the current settings sequence for this project. */
	def settings(ss: Setting[_]*): Project = copy(settings = (settings: Seq[Setting[_]]) ++ ss)

	/** Configures how settings from other sources, such as .sbt files, are appended to the explicitly specified settings for this project. */
	def autoSettings(select: AddSettings*): Project = copy(auto = AddSettings.seq(select : _*))

	/** Adds a list of .sbt files whose settings will be appended to the settings of this project.
	* They will be appended after the explicit settings and already defined automatic settings sources. */
	def addSbtFiles(files: File*): Project = copy(auto = AddSettings.append(auto, AddSettings.sbtFiles(files: _*)) )

	/** Sets the list of .sbt files to parse for settings to be appended to this project's settings.
	* Any configured .sbt files are removed from this project's list.*/
	def setSbtFiles(files: File*): Project = copy(auto = AddSettings.append( AddSettings.clearSbtFiles(auto), AddSettings.sbtFiles(files: _*)) )
}
sealed trait ResolvedProject extends ProjectDefinition[ProjectRef]

sealed trait ClasspathDep[PR <: ProjectReference] { def project: PR; def configuration: Option[String] }
final case class ResolvedClasspathDependency(project: ProjectRef, configuration: Option[String]) extends ClasspathDep[ProjectRef]
final case class ClasspathDependency(project: ProjectReference, configuration: Option[String]) extends ClasspathDep[ProjectReference]

object Project extends ProjectExtra
{
	@deprecated("Use Def.Setting", "0.13.0")
	type Setting[T] = Def.Setting[T]

	@deprecated("Use Def.Initialize", "0.13.0")
	type Initialize[T] = Def.Initialize[T]

	def showContextKey(state: State): Show[ScopedKey[_]] =
		showContextKey(state, None)

	def showContextKey(state: State, keyNameColor: Option[String]): Show[ScopedKey[_]] =
		if(isProjectLoaded(state)) showContextKey( session(state), structure(state), keyNameColor ) else Def.showFullKey

	def showContextKey(session: SessionSettings, structure: BuildStructure, keyNameColor: Option[String] = None): Show[ScopedKey[_]] =
		Def.showRelativeKey(session.current, structure.allProjects.size > 1, keyNameColor)

	def showLoadingKey(loaded: LoadedBuild, keyNameColor: Option[String] = None): Show[ScopedKey[_]] =
		Def.showRelativeKey( ProjectRef(loaded.root, loaded.units(loaded.root).rootProjects.head), loaded.allProjectRefs.size > 1, keyNameColor)

	private abstract class ProjectDef[PR <: ProjectReference](val id: String, val base: File, aggregate0: => Seq[PR], dependencies0: => Seq[ClasspathDep[PR]],
		delegates0: => Seq[PR], settings0: => Seq[Def.Setting[_]], val configurations: Seq[Configuration], val auto: AddSettings) extends ProjectDefinition[PR]
	{
		lazy val aggregate = aggregate0
		lazy val dependencies = dependencies0
		lazy val delegates = delegates0
		lazy val settings = settings0
	
		Dag.topologicalSort(configurations)(_.extendsConfigs) // checks for cyclic references here instead of having to do it in Scope.delegates
	}

	def apply(id: String, base: File, aggregate: => Seq[ProjectReference] = Nil, dependencies: => Seq[ClasspathDep[ProjectReference]] = Nil,
		delegates: => Seq[ProjectReference] = Nil, settings: => Seq[Def.Setting[_]] = defaultSettings, configurations: Seq[Configuration] = Configurations.default,
		auto: AddSettings = AddSettings.allDefaults): Project =
	{
		DefaultParsers.parse(id, DefaultParsers.ID).left.foreach(errMsg => sys.error("Invalid project ID: " + errMsg))
		new ProjectDef[ProjectReference](id, base, aggregate, dependencies, delegates, settings, configurations, auto) with Project
	}

	def resolved(id: String, base: File, aggregate: => Seq[ProjectRef], dependencies: => Seq[ResolvedClasspathDependency], delegates: => Seq[ProjectRef],
		settings: Seq[Def.Setting[_]], configurations: Seq[Configuration], auto: AddSettings): ResolvedProject =
			new ProjectDef[ProjectRef](id, base, aggregate, dependencies, delegates, settings, configurations, auto) with ResolvedProject

	def defaultSettings: Seq[Def.Setting[_]] = Defaults.defaultSettings

	final class Constructor(p: ProjectReference) {
		def %(conf: Configuration): ClasspathDependency = %(conf.name)

		def %(conf: String): ClasspathDependency = new ClasspathDependency(p, Some(conf))
	}

	def getOrError[T](state: State, key: AttributeKey[T], msg: String): T = state get key getOrElse sys.error(msg)
	def structure(state: State): BuildStructure = getOrError(state, stateBuildStructure, "No build loaded.")
	def session(state: State): SessionSettings = getOrError(state, sessionSettings, "Session not initialized.")
	def isProjectLoaded(state: State): Boolean = (state has sessionSettings) && (state has stateBuildStructure)

	def extract(state: State): Extracted  =  extract( session(state), structure(state) )
	def extract(se: SessionSettings, st: BuildStructure): Extracted  =  Extracted(st, se, se.current)( showContextKey(se, st) )

	def getProjectForReference(ref: Reference, structure: BuildStructure): Option[ResolvedProject] =
		ref match { case pr: ProjectRef => getProject(pr, structure); case _ => None }
	def getProject(ref: ProjectRef, structure: BuildStructure): Option[ResolvedProject] = getProject(ref, structure.units)
	def getProject(ref: ProjectRef, structure: LoadedBuild): Option[ResolvedProject] = getProject(ref, structure.units)
	def getProject(ref: ProjectRef, units: Map[URI, LoadedBuildUnit]): Option[ResolvedProject] =
		(units get ref.build).flatMap(_.defined get ref.project)

	def runUnloadHooks(s: State): State =
	{
		val previousOnUnload = orIdentity(s get Keys.onUnload.key)
		previousOnUnload(s.runExitHooks())
	}
	def setProject(session: SessionSettings, structure: BuildStructure, s: State): State =
	{
		val unloaded = runUnloadHooks(s)
		val (onLoad, onUnload) = getHooks(structure.data)
		val newAttrs = unloaded.attributes.put(stateBuildStructure, structure).put(sessionSettings, session).put(Keys.onUnload.key, onUnload)
		val newState = unloaded.copy(attributes = newAttrs)
		onLoad(updateCurrent( newState ))
	}
	def orIdentity[T](opt: Option[T => T]): T => T = opt getOrElse idFun
	def getHook[T](key: SettingKey[T => T], data: Settings[Scope]): T => T  =  orIdentity(key in GlobalScope get data)
	def getHooks(data: Settings[Scope]): (State => State, State => State)  =  (getHook(Keys.onLoad, data), getHook(Keys.onUnload, data))

	def current(state: State): ProjectRef = session(state).current
	def updateCurrent(s: State): State =
	{
		val structure = Project.structure(s)
		val ref = Project.current(s)
		val project = Load.getProject(structure.units, ref.build, ref.project)
		val msg = Keys.onLoadMessage in ref get structure.data getOrElse ""
		if(!msg.isEmpty) s.log.info(msg)
		def get[T](k: SettingKey[T]): Option[T] = k in ref get structure.data
		def commandsIn(axis: ResolvedReference) = commands in axis get structure.data toList ;

		val allCommands = commandsIn(ref) ++ commandsIn(BuildRef(ref.build)) ++ (commands in Global get structure.data toList )
		val history = get(historyPath) flatMap idFun
		val prompt = get(shellPrompt)
		val watched = get(watch)
		val commandDefs = allCommands.distinct.flatten[Command].map(_ tag (projectCommand, true))
		val newDefinedCommands = commandDefs ++ BasicCommands.removeTagged(s.definedCommands, projectCommand)
		val newAttrs = setCond(Watched.Configuration, watched, s.attributes).put(historyPath.key, history)
		s.copy(attributes = setCond(shellPrompt.key, prompt, newAttrs), definedCommands = newDefinedCommands)
	}
	def setCond[T](key: AttributeKey[T], vopt: Option[T], attributes: AttributeMap): AttributeMap =
		vopt match { case Some(v) => attributes.put(key, v); case None => attributes.remove(key) }
	def makeSettings(settings: Seq[Def.Setting[_]], delegates: Scope => Seq[Scope], scopeLocal: ScopedKey[_] => Seq[Def.Setting[_]])(implicit display: Show[ScopedKey[_]]) =
		Def.make(settings)(delegates, scopeLocal, display)

	def equal(a: ScopedKey[_], b: ScopedKey[_], mask: ScopeMask): Boolean =
		a.key == b.key && Scope.equal(a.scope, b.scope, mask)

	def fillTaskAxis(scoped: ScopedKey[_]): ScopedKey[_] =
		ScopedKey(Scope.fillTaskAxis(scoped.scope, scoped.key), scoped.key)

	def mapScope(f: Scope => Scope) = new  (ScopedKey ~> ScopedKey) { def apply[T](key: ScopedKey[T]) =
		ScopedKey( f(key.scope), key.key)
	}

	def transform(g: Scope => Scope, ss: Seq[Def.Setting[_]]): Seq[Def.Setting[_]] = {
		val f = mapScope(g)
		ss.map(_ mapKey f mapReferenced f)
	}
	def transformRef(g: Scope => Scope, ss: Seq[Def.Setting[_]]): Seq[Def.Setting[_]] = {
		val f = mapScope(g)
		ss.map(_ mapReferenced f)
	}

	def delegates(structure: BuildStructure, scope: Scope, key: AttributeKey[_]): Seq[ScopedKey[_]] =
		structure.delegates(scope).map(d => ScopedKey(d, key))

	def scopedKeyData(structure: BuildStructure, scope: Scope, key: AttributeKey[_]): Option[ScopedKeyData[_]] =
		structure.data.get(scope, key) map { v => ScopedKeyData(ScopedKey(scope, key), v) }

	def details(structure: BuildStructure, actual: Boolean, scope: Scope, key: AttributeKey[_])(implicit display: Show[ScopedKey[_]]): String =
	{
		val scoped = ScopedKey(scope,key)
		
		val data = scopedKeyData(structure, scope, key) map {_.description} getOrElse {"No entry for key."}
		val description = key.description match { case Some(desc) => "Description:\n\t" + desc + "\n"; case None => "" }

		val definingScope = structure.data.definingScope(scope, key)
		val providedBy = definingScope match {
			case Some(sc) => "Provided by:\n\t" + Scope.display(sc, key.label) + "\n"
			case None => ""
		}
		val definingScoped = definingScope match { case Some(sc) => ScopedKey(sc, key); case None => scoped }
		val comp = Def.compiled(structure.settings, actual)(structure.delegates, structure.scopeLocal, display)
		val definedAt = comp get definingScoped map { c =>
			def fmt(s: Def.Setting[_]) = s.pos match {
				case pos: FilePosition => (pos.path + ":" + pos.startLine) :: Nil
				case NoPosition => Nil
			}
			val posDefined = c.settings.flatMap(fmt)
			if (posDefined.size > 0) {
				val header = if (posDefined.size == c.settings.size) "Defined at:" else
					"Some of the defining occurrences:"
				header + (posDefined.distinct mkString ("\n\t", "\n\t", "\n"))
			} else ""
		} getOrElse ""


		val cMap = Def.flattenLocals(comp)
		val related = cMap.keys.filter(k => k.key == key && k.scope != scope)
		val depends = cMap.get(scoped) match { case Some(c) => c.dependencies.toSet; case None => Set.empty }

		val reverse = reverseDependencies(cMap, scoped)
		def printScopes(label: String, scopes: Iterable[ScopedKey[_]], max: Int = Int.MaxValue) =
			if(scopes.isEmpty) ""
			else {
				val (limited, more) = if(scopes.size <= max) (scopes, "\n") else (scopes.take(max), "\n...\n")
				limited.map(display.apply).mkString(label + ":\n\t", "\n\t", more)
			}

		data + "\n" +
			description +
			providedBy +
			definedAt +
			printScopes("Dependencies", depends) +
			printScopes("Reverse dependencies", reverse) +
			printScopes("Delegates", delegates(structure, scope, key)) +
			printScopes("Related", related, 10)
	}
	def settingGraph(structure: BuildStructure, basedir: File, scoped: ScopedKey[_])(implicit display: Show[ScopedKey[_]]): SettingGraph =
		SettingGraph(structure, basedir, scoped, 0)
	def graphSettings(structure: BuildStructure, basedir: File)(implicit display: Show[ScopedKey[_]])
	{
		def graph(actual: Boolean, name: String) = graphSettings(structure, actual, name, new File(basedir, name + ".dot"))
		graph(true, "actual_dependencies")
		graph(false, "declared_dependencies")
	}
	def graphSettings(structure: BuildStructure, actual: Boolean, graphName: String, file: File)(implicit display: Show[ScopedKey[_]])
	{
		val rel = relation(structure, actual)
		val keyToString = display.apply _
		DotGraph.generateGraph(file, graphName, rel, keyToString, keyToString)
	}
	def relation(structure: BuildStructure, actual: Boolean)(implicit display: Show[ScopedKey[_]]) =
	{
		type Rel = Relation[ScopedKey[_], ScopedKey[_]]
		val cMap = Def.flattenLocals(Def.compiled(structure.settings, actual)(structure.delegates, structure.scopeLocal, display))
		((Relation.empty: Rel) /: cMap) { case (r, (key, value)) =>
			r + (key, value.dependencies)
		}
	}

	def showDefinitions(key: AttributeKey[_], defs: Seq[Scope])(implicit display: Show[ScopedKey[_]]): String =
		showKeys(defs.map(scope => ScopedKey(scope, key)))
	def showUses(defs: Seq[ScopedKey[_]])(implicit display: Show[ScopedKey[_]]): String =
		showKeys(defs)
	private[this] def showKeys(s: Seq[ScopedKey[_]])(implicit display: Show[ScopedKey[_]]): String =
		s.map(display.apply).sorted.mkString("\n\t", "\n\t", "\n\n")

	def definitions(structure: BuildStructure, actual: Boolean, key: AttributeKey[_])(implicit display: Show[ScopedKey[_]]): Seq[Scope] =
		relation(structure, actual)(display)._1s.toSeq flatMap { sk => if(sk.key == key) sk.scope :: Nil else Nil }
	def usedBy(structure: BuildStructure, actual: Boolean, key: AttributeKey[_])(implicit display: Show[ScopedKey[_]]): Seq[ScopedKey[_]] =
		relation(structure, actual)(display).all.toSeq flatMap { case (a,b) => if(b.key == key) List[ScopedKey[_]](a) else Nil }
	def reverseDependencies(cMap: Map[ScopedKey[_],Flattened], scoped: ScopedKey[_]): Iterable[ScopedKey[_]] =
		for( (key,compiled) <- cMap; dep <- compiled.dependencies if dep == scoped)  yield  key

	//@deprecated("Use SettingCompletions.setAll when available.", "0.13.0")
	def setAll(extracted: Extracted, settings: Seq[Def.Setting[_]]): SessionSettings =
		SettingCompletions.setAll(extracted, settings).session

	val ExtraBuilds = AttributeKey[List[URI]]("extra-builds", "Extra build URIs to load in addition to the ones defined by the project.")
	def extraBuilds(s: State): List[URI] = getOrNil(s, ExtraBuilds)
	def getOrNil[T](s: State, key: AttributeKey[List[T]]): List[T] = s get key getOrElse Nil
	def setExtraBuilds(s: State, extra: List[URI]): State = s.put(ExtraBuilds, extra)
	def addExtraBuilds(s: State, extra: List[URI]): State = setExtraBuilds(s, extra ::: extraBuilds(s))
	def removeExtraBuilds(s: State, remove: List[URI]): State = updateExtraBuilds(s, _.filterNot(remove.toSet))
	def updateExtraBuilds(s: State, f: List[URI] => List[URI]): State = setExtraBuilds(s, f(extraBuilds(s)))

	object LoadAction extends Enumeration {
		val Return, Current, Plugins = Value
	}
	import LoadAction._
	import DefaultParsers._

	val loadActionParser = token(Space ~> ("plugins" ^^^ Plugins | "return" ^^^ Return)) ?? Current
	
	val ProjectReturn = AttributeKey[List[File]]("project-return", "Maintains a stack of builds visited using reload.")
	def projectReturn(s: State): List[File] = getOrNil(s, ProjectReturn)
	def inPluginProject(s: State): Boolean = projectReturn(s).toList.length > 1
	def setProjectReturn(s: State, pr: List[File]): State = s.copy(attributes = s.attributes.put( ProjectReturn, pr) )
	def loadAction(s: State, action: LoadAction.Value) = action match {
		case Return =>
			projectReturn(s) match
			{
				case current :: returnTo :: rest => (setProjectReturn(s, returnTo :: rest), returnTo)
				case _ => sys.error("Not currently in a plugin definition")
			}
		case Current =>
			val base = s.configuration.baseDirectory
			projectReturn(s) match { case Nil => (setProjectReturn(s, base :: Nil), base); case x :: xs => (s, x) }
		case Plugins =>
			val (newBase, oldStack) = if(Project.isProjectLoaded(s))
					(Project.extract(s).currentUnit.unit.plugins.base, projectReturn(s))
				else // support changing to the definition project if it fails to load
					(BuildPaths.projectStandard(s.baseDir), s.baseDir :: Nil)
			val newS = setProjectReturn(s, newBase :: oldStack)
			(newS, newBase)
	}
	@deprecated("This method does not apply state changes requested during task execution.  Use 'runTask' instead, which does.", "0.11.1")
	def evaluateTask[T](taskKey: ScopedKey[Task[T]], state: State, checkCycles: Boolean = false, maxWorkers: Int = EvaluateTask.SystemProcessors): Option[Result[T]] =
		runTask(taskKey, state, EvaluateConfig(true, EvaluateTask.defaultRestrictions(maxWorkers), checkCycles)).map(_._2)
	def runTask[T](taskKey: ScopedKey[Task[T]], state: State, checkCycles: Boolean = false): Option[(State, Result[T])] =
		runTask(taskKey, state, EvaluateConfig(true, EvaluateTask.restrictions(state), checkCycles))
	def runTask[T](taskKey: ScopedKey[Task[T]], state: State, config: EvaluateConfig): Option[(State, Result[T])] =
	{
		val extracted = Project.extract(state)
		EvaluateTask(extracted.structure, taskKey, state, extracted.currentRef, config)
	}

	implicit def projectToRef(p: Project): ProjectReference = LocalProject(p.id)

	final class RichTaskSessionVar[S](i: Def.Initialize[Task[S]])
	{
			import SessionVar.{persistAndSet, resolveContext, set, transform => tx}

		def updateState(f: (State, S) => State): Def.Initialize[Task[S]] = i(t => tx(t, f))
		def storeAs(key: TaskKey[S])(implicit f: sbinary.Format[S]): Def.Initialize[Task[S]] = (Keys.resolvedScoped, i) { (scoped, task) =>
			tx(task, (state, value) => persistAndSet( resolveContext(key, scoped.scope, state), state, value)(f))
		}
		def keepAs(key: TaskKey[S]): Def.Initialize[Task[S]] =
			(i, Keys.resolvedScoped)( (t,scoped) => tx(t, (state,value) => set(resolveContext(key, scoped.scope, state), state, value) ) )
	}

		import scala.reflect._
		import reflect.macros._

	def projectMacroImpl(c: Context): c.Expr[Project] =
	{
		import c.universe._
		val enclosingValName = std.KeyMacro.definingValName(c, methodName => s"""$methodName must be directly assigned to a val, such as `val x = $methodName`.""")
		val name = c.Expr[String]( Literal(Constant(enclosingValName)) )
		reify { Project(name.splice, new File(name.splice)) }
	}
}

trait ProjectExtra
{
	implicit def configDependencyConstructor[T <% ProjectReference](p: T): Constructor = new Constructor(p)
	implicit def classpathDependency[T <% ProjectReference](p: T): ClasspathDependency = new ClasspathDependency(p, None)

	// These used to be in Project so that they didn't need to get imported (due to Initialize being nested in Project).
	// Moving Initialize and other settings types to Def and decoupling Project, Def, and Structure means these go here for now
	implicit def richInitializeTask[T](init: Initialize[Task[T]]): Scoped.RichInitializeTask[T] = new Scoped.RichInitializeTask(init)
	implicit def richInitializeInputTask[T](init: Initialize[InputTask[T]]): Scoped.RichInitializeInputTask[T] = new Scoped.RichInitializeInputTask(init)
	implicit def richInitialize[T](i: Initialize[T]): Scoped.RichInitialize[T] = new Scoped.RichInitialize[T](i)

	implicit def richTaskSessionVar[T](init: Initialize[Task[T]]): Project.RichTaskSessionVar[T] = new Project.RichTaskSessionVar(init)

	def inConfig(conf: Configuration)(ss: Seq[Setting[_]]): Seq[Setting[_]] =
		inScope(ThisScope.copy(config = Select(conf)) )( (configuration :== conf) +: ss)
	def inTask(t: Scoped)(ss: Seq[Setting[_]]): Seq[Setting[_]] =
		inScope(ThisScope.copy(task = Select(t.key)) )( ss )
	def inScope(scope: Scope)(ss: Seq[Setting[_]]): Seq[Setting[_]] =
		Project.transform(Scope.replaceThis(scope), ss)

	private[sbt] def inConfig[T](conf: Configuration, i: Initialize[T]): Initialize[T] =
		inScope(ThisScope.copy(config = Select(conf)), i)
	private[sbt] def inTask[T](t: Scoped, i: Initialize[T]): Initialize[T] =
		inScope(ThisScope.copy(task = Select(t.key)), i)
	private[sbt] def inScope[T](scope: Scope, i: Initialize[T]): Initialize[T] =
		i mapReferenced Project.mapScope(Scope.replaceThis(scope))

	/** Creates a new Project.  This is a macro that expects to be assigned directly to a val.
	* The name of the val is used as the project ID and the name of the base directory of the project. */
	def project: Project = macro Project.projectMacroImpl
}
