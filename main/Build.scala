/* sbt -- Simple Build Tool
 * Copyright 2011 Mark Harrah
 */
package sbt

	import java.io.File
	import java.net.URI
	import compile.{Discovered,Discovery,Eval}
	import classpath.ClasspathUtilities
	import inc.Analysis
	import scala.annotation.tailrec
	import Compile.{Compilers,Inputs}
	import Project.{ScopedKey, Setting}
	import TypeFunctions.{Endo,Id}
	import tools.nsc.reporters.ConsoleReporter

// name is more like BuildDefinition, but that is too long
trait Build
{
	def projects: Seq[Project]
}
trait Plugin
{
	def settings: Seq[Project.Setting[_]]
}

object Build
{
	def default(base: File): Build = new Build { def projects = defaultProject("default", base) :: Nil }
	def defaultProject(id: String, base: File): Project = Project(id, base)
}
object RetrieveUnit
{
	def apply(tempDir: File, base: URI): File =
	{
		lazy val tmp = temporary(tempDir, base)
		base.getScheme match
		{
			case "file" => val f = new File(base); if(f.isDirectory) f else error("Not a directory: '" + base + "'")
			case "git" => gitClone(base, tmp); tmp
			case "http" | "https" => downloadAndExtract(base, tmp); tmp
			case _ => error("Unknown scheme in '" + base + "'")
		}
	}
	def downloadAndExtract(base: URI, tempDir: File): Unit = if(!tempDir.exists) IO.unzipURL(base.toURL, tempDir)
	def temporary(tempDir: File, uri: URI): File = new File(tempDir, hash(uri))
	def hash(uri: URI): String = Hash.toHex(Hash(uri.toASCIIString))

	import Process._
	def gitClone(base: URI, tempDir: File): Unit =
		if(!tempDir.exists)  ("git" :: "clone" :: base.toASCIIString :: tempDir.getAbsolutePath :: Nil) ! ;
}
object EvaluateConfigurations
{
	def apply(eval: Eval, srcs: Seq[File]): Seq[Setting[_]] =
		srcs flatMap { src =>  evaluateConfiguration(eval, src) }
	def evaluateConfiguration(eval: Eval, src: File): Seq[Setting[_]] =
		evaluateConfiguration(eval, src.getPath, IO.readLines(src))
	def evaluateConfiguration(eval: Eval, name: String, lines: Seq[String]): Seq[Setting[_]] =
	{
		val (importExpressions, settingExpressions) = splitExpressions(name, lines)
		for(settingExpression <- settingExpressions) yield evaluateSetting(eval, name, importExpressions, settingExpression)
	}

	def evaluateSetting(eval: Eval, name: String, imports: Seq[String], expression: String): Setting[_] =
	{
		// TODO: Eval needs to be expanded to be able to:
		//   handle multiple expressions at once (for efficiency and better error handling)
		//   accept the source name for error display
		//   accept imports to use
		eval.eval[Setting[_]](expression)
	}
	def splitExpressions(name: String, lines: Seq[String]): (Seq[String], Seq[String]) =
	{
		val blank = (_: String).trim.isEmpty
		val importOrBlank = (t: String) => blank(t) || (t.trim startsWith "import ")

		val (imports, settings) = lines span importOrBlank
		(imports dropWhile blank, groupedLines(settings, blank))
	}
	def groupedLines(lines: Seq[String], delimiter: String => Boolean): Seq[String] =
	{
		@tailrec def group0(lines: Seq[String], delimiter: String => Boolean, accum: Seq[String]): Seq[String] =
		{
			val start = lines dropWhile delimiter
			val (next, tail) = start.span (s => !delimiter(s))
			group0(tail, delimiter, next.mkString("\n") +: accum)
		}
		group0(lines, delimiter, Nil)
	}
}
object EvaluateTask
{
	import Load.BuildStructure
	import Project.display
	import std.{TaskExtra,Transform}
	import TaskExtra._
	
	type Streams = std.Streams[ScopedKey[Task[_]]]
	type TaskStreams = std.TaskStreams[ScopedKey[Task[_]]]

	val SystemProcessors = Runtime.getRuntime.availableProcessors
	val PluginTaskKey = TaskKey[(Seq[File], Analysis)]("plugin-task")
	val GlobalPath = "$global"
	
	val (state, dummyState) = dummy[State]("state")
	val (streams, dummyStreams) = dummy[TaskStreams]("streams")

	def injectSettings = Seq(
		state :== dummyState,
		streams :== dummyStreams
	)
	
	def dummy[T](name: String): (TaskKey[T], Task[T]) = (TaskKey[T](name), dummyTask(name))
	def dummyTask[T](name: String): Task[T] = task( error("Dummy task '" + name + "' did not get converted to a full task.") ) named name

	def evalPluginDef(state: State, log: Logger)(pluginDef: BuildStructure): (Seq[File], Analysis) =
	{
		val evaluated = evaluateTask(pluginDef, ScopedKey(Scope.ThisScope, PluginTaskKey.key), state)
		val result = evaluated getOrElse error("Plugin task does not exist for plugin definition at " + pluginDef.root)
		processResult(result, log)
	}
	def evaluateTask[T](structure: BuildStructure, taskKey: ScopedKey[Task[T]], state: State, checkCycles: Boolean = false, maxWorkers: Int = SystemProcessors): Option[Result[T]] =
		for( (task, toNode) <- getTask(structure, taskKey, state) ) yield
			runTask(task, checkCycles, maxWorkers)(toNode)
	
	def getTask[T](structure: BuildStructure, taskKey: ScopedKey[Task[T]], state: State): Option[(Task[T], Execute.NodeView[Task])] =
	{
		val x = transform(structure, "target" :: "log" :: Nil, dummyStreams, dummyState, state)
		val thisScope = Scope(Select(Project.currentRef(state)), Global, Global, Global)
		val resolvedScope = Scope.replaceThis(thisScope)( taskKey.scope )
		for( t <- structure.data.get(resolvedScope, taskKey.key)) yield
			(t, x)
	}

	def runTask[Task[_] <: AnyRef, T](root: Task[T], checkCycles: Boolean, maxWorkers: Int)(implicit taskToNode: Execute.NodeView[Task]): Result[T] =
	{
		val (service, shutdown) = CompletionService[Task[_], Completed](maxWorkers)

		val x = new Execute[Task](checkCycles)(taskToNode)
		try { x.run(root)(service) } finally { shutdown() }
	}

	def transform(structure: BuildStructure, logRelativePath: Seq[String], streamsDummy: Task[TaskStreams], stateDummy: Task[State], state: State) =
	{
		val streams = mkStreams(structure, logRelativePath)
		val dummies = new Transform.Dummies(stateDummy :^: KNil, streamsDummy)
		val inject = new Transform.Injected(state :+: HNil, streams)
		Transform(dummies, inject)(structure.index.taskToKey)
	}
	
	def mkStreams(structure: BuildStructure, logRelativePath: Seq[String]): Streams =
		std.Streams( path(structure, logRelativePath), display, LogManager.construct(structure.data) )

	def path(structure: BuildStructure, sep: Seq[String])(scoped: ScopedKey[_]): File =
	{
		val (base, sub) = projectPath(structure, scoped)
		resolvePath(base, sep ++ sub ++ nonProjectPath(scoped) )
	}
	def resolvePath(base: File, components: Seq[String]): File =
		(base /: components)( (b,p) => new File(b,p) )

	def pathComponent[T](axis: ScopeAxis[T], scoped: ScopedKey[_], label: String)(show: T => String): String =
		axis match
		{
			case Global => GlobalPath
			case This => error("Unresolved This reference for " + label + " in " + display(scoped))
			case Select(t) => show(t)
		}
	def nonProjectPath[T](scoped: ScopedKey[T]): Seq[String] =
	{
		val scope = scoped.scope
		pathComponent(scope.config, scoped, "config")(_.name) ::
		pathComponent(scope.task, scoped, "task")(_.label) ::
		pathComponent(scope.extra, scoped, "extra")(_ => error("Unimplemented")) ::
		Nil
	}
	def projectPath(structure: BuildStructure, scoped: ScopedKey[_]): (File, Seq[String]) =
		scoped.scope.project match
		{
			case Global => (structure.units(structure.root).base, GlobalPath :: Nil)
			case Select(ProjectRef(Some(uri), Some(id))) => (structure.units(uri).defined(id).base, Nil)
			case Select(pr) => error("Unresolved project reference (" + pr + ") in " + display(scoped))
			case This => error("Unresolved project reference (This) in " + display(scoped))
		}
	def processResult[T](result: Result[T], log: Logger): T =
		result match
		{
			case Value(v) => v
			case Inc(inc) =>
				log.error(Incomplete.show(inc, true))
				error("Task did not complete successfully")
		}
}
object Index
{
	def taskToKeyMap(data: Settings[Scope]): Map[Task[_], ScopedKey[Task[_]]] =
	{
		// AttributeEntry + the checked type test 'value: Task[_]' ensures that the cast is correct.
		//  (scalac couldn't determine that 'key' is of type AttributeKey[Task[_]] on its own and a type match didn't still required the cast)
		val pairs = for( scope <- data.scopes; AttributeEntry(key, value: Task[_]) <- data.data(scope).entries ) yield
			(value, ScopedKey(scope, key.asInstanceOf[AttributeKey[Task[_]]])) // unclear why this cast is needed even with a type test in the above filter
		pairs.toMap[Task[_], ScopedKey[Task[_]]]
	}
	def stringToKeyMap(settings: Settings[Scope]): Map[String, AttributeKey[_]] =
	{
		val multiMap = settings.data.values.flatMap(_.keys).toList.removeDuplicates.groupBy(_.label)
		val duplicates = multiMap collect { case (k, x1 :: x2 :: _) => println(k + ": " + x1 + ", " + x2); k }
		if(duplicates.isEmpty)
			multiMap.mapValues(_.head)
		else
			error(duplicates.mkString("AttributeKey ID collisions detected for '", "', '", "'"))
	}
}
object Load
{
	import BuildPaths._

	def defaultLoad(state: State, log: Logger): BuildStructure =
	{
		val stagingDirectory = defaultStaging // TODO: properly configurable
		val base = state.configuration.baseDirectory
		val loader = getClass.getClassLoader
		val provider = state.configuration.provider
		val classpath = provider.mainClasspath ++ provider.scalaProvider.jars
		val compilers = Compile.compilers(state.configuration, log)
		val evalPluginDef = EvaluateTask.evalPluginDef(state, log) _
		val config = new LoadBuildConfiguration(stagingDirectory, classpath, loader, compilers, evalPluginDef, defaultDelegates, EvaluateTask.injectSettings, log)
		apply(base, config)
	}
	def defaultDelegates: LoadedBuild => Scope => Seq[Scope] = (lb: LoadedBuild) => {
		def resolveRef(project: ProjectRef) = Scope.resolveRef(lb.root, getRootProject(lb.units), project)
		Scope.delegates(
			project => projectInherit(lb, resolveRef(project)),
			(project, config) => configInherit(lb, resolveRef(project), config),
			(project, task) => Nil,
			(project, extra) => Nil
		)
	}
	def configInherit(lb: LoadedBuild, ref: (URI, String), config: ConfigKey): Seq[ConfigKey] =
		getConfiguration(lb.units, ref._1, ref._2, config).extendsConfigs.map(c => ConfigKey(c.name))
		
	def projectInherit(lb: LoadedBuild, ref: (URI, String)): Seq[ProjectRef] =
		getProject(lb.units, ref._1, ref._2).inherits

		// build, load, and evaluate all units.
		//  1) Compile all plugin definitions
		//  2) Evaluate plugin definitions to obtain and compile plugins and get the resulting classpath for the build definition
		//  3) Instantiate Plugins on that classpath
		//  4) Compile all build definitions using plugin classpath
		//  5) Load build definitions.
		//  6) Load all configurations using build definitions and plugins (their classpaths and loaded instances).
		//  7) Combine settings from projects, plugins, and configurations
		//  8) Evaluate settings
	def apply(rootBase: File, config: LoadBuildConfiguration): BuildStructure =
	{
		val loaded = load(rootBase, config)
		val projects = loaded.units
		val settings = buildConfigurations(loaded, getRootProject(projects), config.injectSettings)
		val data = Project.make(settings)(config.delegates(loaded))
		val index = structureIndex(data)
		new BuildStructure(projects, loaded.root, settings, data, index)
	}

	def structureIndex(settings: Settings[Scope]): StructureIndex =
		new StructureIndex(Index.stringToKeyMap(settings), Index.taskToKeyMap(settings))

		// Reevaluates settings after modifying them.  Does not recompile or reload any build components.
	def reapply(modifySettings: Endo[Seq[Setting[_]]], structure: BuildStructure, delegates: Scope => Seq[Scope]): BuildStructure =
	{
		val newSettings = modifySettings(structure.settings)
		val newData = Project.make(newSettings)(delegates)
		val newIndex = structureIndex(newData)
		new BuildStructure(units = structure.units, root = structure.root, settings = newSettings, data = newData, index = newIndex)
	}

	def isProjectThis(s: Setting[_]) = s.key.scope.project == This
	def buildConfigurations(loaded: LoadedBuild, rootProject: URI => String, injectSettings: Seq[Setting[_]]): Seq[Setting[_]] =
		loaded.units.toSeq flatMap { case (uri, build) =>
			val eval = mkEval(build.unit.definitions, build.unit.plugins, Nil)
			val pluginSettings = build.unit.plugins.plugins
			val (pluginThisProject, pluginGlobal) = pluginSettings partition isProjectThis
			val projectSettings = build.defined flatMap { case (id, project) =>
				val srcs = configurationSources(project.base)
				val settings = injectSettings ++ project.settings ++ pluginThisProject ++ configurations(srcs, eval)
				
				val ext = ProjectRef(uri, id)
				val thisScope = Scope(Select(ext), Global, Global, Global)
				 
				// map This to thisScope, Select(p) to mapRef(uri, rootProject, p)
				Project.transform(Scope.resolveScope(thisScope, uri, rootProject), settings)
			}
			pluginGlobal ++ projectSettings
		}
	def mkEval(defs: LoadedDefinitions, plugs: LoadedPlugins, options: Seq[String]): Eval =
	{
		val classpathString = Path.makeString(defs.target +: plugs.classpath)
		val optionsCp = "-cp" +: classpathString +: options // TODO: probably need to properly set up options with CompilerArguments
		new Eval(optionsCp, s => new ConsoleReporter(s), defs.loader)
	}
	def configurations(srcs: Seq[File], eval: Eval): Seq[Setting[_]] =
		if(srcs.isEmpty) Nil else EvaluateConfigurations(eval, srcs)

	def load(file: File, config: LoadBuildConfiguration): LoadedBuild = load(file, uri => loadUnit(RetrieveUnit(config.stagingDirectory, uri), config) )
	def load(file: File, loader: URI => BuildUnit): LoadedBuild = loadURI(file.getAbsoluteFile.toURI, loader)
	def loadURI(uri: URI, loader: URI => BuildUnit): LoadedBuild =
	{
		val (referenced, map) = loadAll(uri :: Nil, Map.empty, loader, Map.empty)
		checkAll(referenced, map)
		new LoadedBuild(uri, map)
	}
	def loaded(unit: BuildUnit): (LoadedBuildUnit, List[ProjectRef]) =
	{
		val baseURI = unit.base.toURI.normalize
		def isRoot(p: Project) = p.base.toURI.normalize == baseURI
		val defined = projects(unit)
		val externals = referenced(defined).toList
		val rootProjects = defined.filter(isRoot).map(_.id)
		(new LoadedBuildUnit(unit, defined.map(d => (d.id, d)).toMap, rootProjects), externals)
	}

	@tailrec def loadAll(bases: List[URI], references: Map[URI, List[ProjectRef]], externalLoader: URI => BuildUnit, builds: Map[URI, LoadedBuildUnit]): (Map[URI, List[ProjectRef]], Map[URI, LoadedBuildUnit]) =
		bases match
		{
			case b :: bs =>
				if(builds contains b)
					loadAll(bs, references, externalLoader, builds)
				else
				{
					val (loadedBuild, refs) = loaded(externalLoader(b))
					loadAll(refs.flatMap(_.uri) reverse_::: bs, references.updated(b, refs), externalLoader, builds.updated(b, loadedBuild))
				}
			case Nil => (references, builds)
		}
	def checkAll(referenced: Map[URI, List[ProjectRef]], builds: Map[URI, LoadedBuildUnit])
	{
		val rootProject = getRootProject(builds)
		for( (uri, refs) <- referenced; ref <- refs)
		{
			// mapRef guarantees each component is defined
			val ProjectRef(Some(refURI), Some(refID)) = Scope.mapRef(uri, rootProject, ref)
			val loadedUnit = builds(refURI)
			if(! (loadedUnit.defined contains refID) )
				error("No project '" + refID + "' in '" + refURI + "'")
		}
	}

	def resolveBase(against: File): Project => Project =
	{
		val uri = against.getAbsoluteFile.toURI.normalize
		p => p.copy(base = new File(uri.resolve(p.base.toURI).normalize))
	}
	def projects(unit: BuildUnit): Seq[Project] = unit.definitions.builds.flatMap(_.projects map resolveBase(unit.base))
	def getRootProject(map: Map[URI, LoadedBuildUnit]): URI => String =
		uri => getBuild(map, uri).rootProjects.headOption getOrElse emptyBuild(uri)
	def getConfiguration(map: Map[URI, LoadedBuildUnit], uri: URI, id: String, conf: ConfigKey): Configuration =
		getProject(map, uri, id).configurations.find(_.name == conf.name) getOrElse noConfiguration(uri, id, conf.name)

	def getProject(map: Map[URI, LoadedBuildUnit], uri: URI, id: String): Project =
		getBuild(map, uri).defined.getOrElse(id, noProject(uri, id))
	def getBuild(map: Map[URI, LoadedBuildUnit], uri: URI): LoadedBuildUnit =
		map.getOrElse(uri, noBuild(uri))

	def emptyBuild(uri: URI) = error("No root project defined for build unit '" + uri + "'")
	def noBuild(uri: URI) = error("Build unit '" + uri + "' not defined.")
	def noProject(uri: URI, id: String) = error("No project '" + id + "' defined in '" + uri + "'.")
	def noConfiguration(uri: URI, id: String, conf: String) = error("No configuration '" + conf + "' defined in project '" + id + "' in '" + uri +"'")

	def loadUnit(base: File, config: LoadBuildConfiguration): BuildUnit =
	{
		val defDir = selectProjectDir(base)
		val pluginDir = pluginDirectory(defDir)
		val plugs = plugins(pluginDir, config)

		val defs = definitionSources(defDir)
		val loadedDefs =
			if(defs.isEmpty)
				new LoadedDefinitions(defDir, outputDirectory(defDir), plugs.loader, Build.default(base) :: Nil)
			else
				definitions(defDir, defs, plugs, config.compilers, config.log)

		new BuildUnit(base, loadedDefs, plugs)
	}

	def plugins(dir: File, config: LoadBuildConfiguration): LoadedPlugins = if(dir.exists) buildPlugins(dir, config) else noPlugins(config)
	def noPlugins(config: LoadBuildConfiguration): LoadedPlugins = new LoadedPlugins(config.classpath, config.loader, Nil)
	def buildPlugins(dir: File, config: LoadBuildConfiguration): LoadedPlugins =
	{
		val pluginDef = apply(dir, config)
		val (pluginClasspath, pluginAnalysis) = config.evalPluginDef(pluginDef)
		val pluginLoader = ClasspathUtilities.toLoader(pluginClasspath, config.loader)
		loadPlugins(pluginClasspath, pluginLoader, pluginAnalysis)
	}

	def definitions(base: File, srcs: Seq[File], plugins: LoadedPlugins, compilers: Compilers, log: Logger): LoadedDefinitions =
	{
		val (inputs, defAnalysis) = build(plugins.classpath, srcs, outputDirectory(base), compilers, log)
		val target = inputs.config.classesDirectory
		val definitionLoader = ClasspathUtilities.toLoader(target :: Nil, plugins.loader)
		val defs = loadDefinitions(definitionLoader, findDefinitions(defAnalysis))
		new LoadedDefinitions(base, target, definitionLoader, defs)
	}

	def loadDefinitions(loader: ClassLoader, defs: Seq[String]): Seq[Build] =
		defs map { definition => loadDefinition(loader, definition) }
	def loadDefinition(loader: ClassLoader, definition: String): Build =
		ModuleUtilities.getObject(definition, loader).asInstanceOf[Build]

	def build(classpath: Seq[File], sources: Seq[File], buildDir: File, compilers: Compilers, log: Logger): (Inputs, Analysis) =
	{
		val target = crossPath(new File(buildDir, "target"), compilers.scalac.scalaInstance)
		val inputs = Compile.inputs(classpath, sources, target, Nil, Nil, Nil, Compile.DefaultMaxErrors)(compilers, log)
		val analysis = Compile(inputs, log)
		(inputs, analysis)
	}

	def loadPlugins(classpath: Seq[File], loader: ClassLoader, analysis: Analysis): LoadedPlugins =
		new LoadedPlugins(classpath, loader, if(classpath.isEmpty) Nil else loadPlugins(loader, findPlugins(analysis) ) )

	def loadPlugins(loader: ClassLoader, pluginNames: Seq[String]): Seq[Setting[_]] =
		pluginNames.flatMap(pluginName => loadPlugin(pluginName, loader))

	def loadPlugin(pluginName: String, loader: ClassLoader): Seq[Setting[_]] =
		ModuleUtilities.getObject(pluginName, loader).asInstanceOf[Plugin].settings

	def findPlugins(analysis: Analysis): Seq[String]  =  discover(analysis, "sbt.Plugin")
	def findDefinitions(analysis: Analysis): Seq[String]  =  discover(analysis, "sbt.Build")
	def discover(analysis: Analysis, subclasses: String*): Seq[String] =
	{
		val discovery = new Discovery(subclasses.toSet, Set.empty)
		discovery(Test.allDefs(analysis)).collect { case (definition, Discovered(_,_,_,true)) => definition.name }
	}

	def initialSession(structure: BuildStructure): SessionSettings =
		new SessionSettings(structure.root, rootProjectMap(structure.units), structure.settings, Map.empty, Map.empty)

	def rootProjectMap(units: Map[URI, LoadedBuildUnit]): Map[URI, String] =
	{
		val getRoot = getRootProject(units)
		units.keys.map(uri => (uri, getRoot(uri))).toMap
	}

	final class EvaluatedConfigurations(val eval: Eval, val settings: Seq[Setting[_]])
	final class LoadedDefinitions(val base: File, val target: File, val loader: ClassLoader, val builds: Seq[Build])
	final class LoadedPlugins(val classpath: Seq[File], val loader: ClassLoader, val plugins: Seq[Setting[_]])
	object LoadedPlugins {
		def empty(loader: ClassLoader) = new LoadedPlugins(Nil, loader, Nil)
	}
	final class BuildUnit(val base: File, val definitions: LoadedDefinitions, val plugins: LoadedPlugins)
	
	final class LoadedBuild(val root: URI, val units: Map[URI, LoadedBuildUnit])
	final class LoadedBuildUnit(val unit: BuildUnit, val defined: Map[String, Project], val rootProjects: Seq[String])
	{
		assert(!rootProjects.isEmpty, "No root projects defined for build unit '" + unit.base + "'")
		def base = unit.base
		def classpath = unit.definitions.target +: unit.plugins.classpath
		def loader = unit.definitions.loader
	}

	// these are unresolved references
	def referenced(definitions: Seq[Project]): Seq[ProjectRef] = definitions flatMap referenced
	def referenced(definition: Project): Seq[ProjectRef] = definition.inherits ++ definition.aggregate ++ definition.dependencies.map(_.project)

	
	final class BuildStructure(val units: Map[URI, LoadedBuildUnit], val root: URI, val settings: Seq[Setting[_]], val data: Settings[Scope], val index: StructureIndex)
	final class LoadBuildConfiguration(val stagingDirectory: File, val classpath: Seq[File], val loader: ClassLoader, val compilers: Compilers, val evalPluginDef: BuildStructure => (Seq[File], Analysis), val delegates: LoadedBuild => Scope => Seq[Scope], val injectSettings: Seq[Setting[_]], val log: Logger)
	// information that is not original, but can be reconstructed from the rest of BuildStructure
	final class StructureIndex(val keyMap: Map[String, AttributeKey[_]], val taskToKey: Map[Task[_], ScopedKey[Task[_]]])
}
object BuildPaths
{
	import Path._
	import GlobFilter._

	def defaultStaging = Path.userHome / ".ivy2" / "staging"
	
	def definitionSources(base: File): Seq[File] = (base * "*.scala").getFiles.toSeq
	def configurationSources(base: File): Seq[File] = (base * "*.sbt").getFiles.toSeq
	def pluginDirectory(definitionBase: Path) = definitionBase / "plugins"

	def outputDirectory(base: Path) = base / "target"
	def projectStandard(base: Path) = base / "project"
	def projectHidden(base: Path) = base / ".sbt"
	def selectProjectDir(base: Path) =
	{
		val a = projectHidden(base)
		val b = projectStandard(base)
		if(a.exists) a else b
	}

	def crossPath(base: File, instance: ScalaInstance): File = base / ("scala_" + instance.version)
}