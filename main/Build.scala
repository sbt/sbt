/* sbt -- Simple Build Tool
 * Copyright 2011 Mark Harrah
 */
package sbt

	import java.io.File
	import java.net.URI
	import compile.{Discovered,Discovery,Eval,EvalImports}
	import classpath.ClasspathUtilities
	import inc.Analysis
	import scala.annotation.tailrec
	import collection.mutable
	import Compile.{Compilers,Inputs}
	import Project.{AppConfig, Config, ScopedKey, Setting, ThisProject, ThisProjectRef}
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
	def apply(eval: Eval, srcs: Seq[File], imports: Seq[String]): Seq[Setting[_]] =
		srcs flatMap { src =>  evaluateConfiguration(eval, src, imports) }
	def evaluateConfiguration(eval: Eval, src: File, imports: Seq[String]): Seq[Setting[_]] =
		evaluateConfiguration(eval, src.getPath, IO.readLines(src), imports)
	def evaluateConfiguration(eval: Eval, name: String, lines: Seq[String], imports: Seq[String]): Seq[Setting[_]] =
	{
		val (importExpressions, settingExpressions) = splitExpressions(name, lines)
		for((settingExpression,line) <- settingExpressions) yield
			evaluateSetting(eval, name, (imports.map(s => (s, -1)) ++ importExpressions), settingExpression, line)
	}

	def evaluateSetting(eval: Eval, name: String, imports: Seq[(String,Int)], expression: String, line: Int): Setting[_] =
	{
		val result = eval.eval(expression, imports = new EvalImports(imports, name), srcName = name, tpeName = Some("sbt.Project.Setting[_]"), line = line)
		result.value.asInstanceOf[Setting[_]]
	}
	private[this] def fstS(f: String => Boolean): ((String,Int)) => Boolean = { case (s,i) => f(s) }
	def splitExpressions(name: String, lines: Seq[String]): (Seq[(String,Int)], Seq[(String,Int)]) =
	{
		val blank = (_: String).trim.isEmpty
		val importOrBlank = fstS(t => blank(t) || (t.trim startsWith "import "))

		val (imports, settings) = lines.zipWithIndex span importOrBlank
		(imports filterNot fstS(blank), groupedLines(settings, blank))
	}
	def groupedLines(lines: Seq[(String,Int)], delimiter: String => Boolean): Seq[(String,Int)] =
	{
		val fdelim = fstS(delimiter)
		@tailrec def group0(lines: Seq[(String,Int)], accum: Seq[(String,Int)]): Seq[(String,Int)] =
			if(lines.isEmpty) accum.reverse
			else
			{
				val start = lines dropWhile fstS(delimiter)
				val (next, tail) = start.span { case (s,_) => !delimiter(s) }
				val grouped = if(next.isEmpty) accum else (next.map(_._1).mkString("\n"), next.head._2) +: accum
				group0(tail, grouped)
			}
		group0(lines, Nil)
	}
}
object EvaluateTask
{
	import Load.BuildStructure
	import Project.display
	import std.{TaskExtra,Transform}
	import TaskExtra._
	import BuildStreams.{Streams, TaskStreams}
	
	val SystemProcessors = Runtime.getRuntime.availableProcessors
	// TODO: we should use a Seq[Attributed[File]] so that we don't throw away Analysis information
	val PluginDefinition = TaskKey[(Seq[File], Analysis)]("plugin-definition")
	
	val (state, dummyState) = dummy[State]("state")
	val (streams, dummyStreams) = dummy[TaskStreams]("streams")

	def injectSettings: Seq[Project.Setting[_]] = Seq(
		(state in Scope.GlobalScope) :== dummyState,
		(streams in Scope.GlobalScope) :== dummyStreams
	)
	
	def dummy[T](name: String): (TaskKey[T], Task[T]) = (TaskKey[T](name), dummyTask(name))
	def dummyTask[T](name: String): Task[T] = task( error("Dummy task '" + name + "' did not get converted to a full task.") ) named name

	def evalPluginDef(log: Logger)(pluginDef: BuildStructure, state: State): (Seq[File], Analysis) =
	{
		val root = ProjectRef(pluginDef.root, Load.getRootProject(pluginDef.units)(pluginDef.root))
		val evaluated = evaluateTask(pluginDef, ScopedKey(Scope.ThisScope, PluginDefinition.key), state, root)
		val result = evaluated getOrElse error("Plugin task does not exist for plugin definition at " + pluginDef.root)
		processResult(result, log)
	}
	def evaluateTask[T](structure: BuildStructure, taskKey: ScopedKey[Task[T]], state: State, thisProject: ProjectRef, checkCycles: Boolean = false, maxWorkers: Int = SystemProcessors): Option[Result[T]] =
		for( (task, toNode) <- getTask(structure, taskKey, state, thisProject) ) yield
			runTask(task, checkCycles, maxWorkers)(toNode)
	
	def getTask[T](structure: BuildStructure, taskKey: ScopedKey[Task[T]], state: State, thisProject: ProjectRef): Option[(Task[T], Execute.NodeView[Task])] =
	{
		val thisScope = Scope(Select(thisProject), Global, Global, Global)
		val resolvedScope = Scope.replaceThis(thisScope)( taskKey.scope )
		for( t <- structure.data.get(resolvedScope, taskKey.key)) yield
			(t, nodeView(structure, state))
	}
	def nodeView(structure: BuildStructure, state: State): Execute.NodeView[Task] =
		transform(structure, dummyStreams, dummyState, state)

	def runTask[Task[_] <: AnyRef, T](root: Task[T], checkCycles: Boolean = false, maxWorkers: Int = SystemProcessors)(implicit taskToNode: Execute.NodeView[Task]): Result[T] =
	{
		val (service, shutdown) = CompletionService[Task[_], Completed](maxWorkers)

		val x = new Execute[Task](checkCycles)(taskToNode)
		try { x.run(root)(service) } finally { shutdown() }
	}

	def transform(structure: BuildStructure, streamsDummy: Task[TaskStreams], stateDummy: Task[State], state: State) =
	{
		val dummies = new Transform.Dummies(stateDummy :^: KNil, streamsDummy)
		val inject = new Transform.Injected(state :+: HNil, structure.streams)
		Transform(dummies, inject)(structure.index.taskToKey)
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
		val duplicates = multiMap collect { case (k, x1 :: x2 :: _) => k }
		if(duplicates.isEmpty)
			multiMap.mapValues(_.head)
		else
			error(duplicates.mkString("AttributeKey ID collisions detected for '", "', '", "'"))
	}
}
object Load
{
	import BuildPaths._
	import BuildStreams._

	// note that there is State is passed in but not pulled out
	def defaultLoad(state: State, log: Logger): (() => Eval, BuildStructure) =
	{
		val stagingDirectory = defaultStaging.getCanonicalFile // TODO: properly configurable
		val base = state.configuration.baseDirectory.getCanonicalFile
		val loader = getClass.getClassLoader
		val provider = state.configuration.provider
		val classpath = provider.mainClasspath ++ provider.scalaProvider.jars
		val compilers = Compile.compilers(state.configuration, log)
		val evalPluginDef = EvaluateTask.evalPluginDef(log) _
		val delegates = memo(defaultDelegates)
		val inject: Seq[Project.Setting[_]] = ((AppConfig in Scope.GlobalScope) :== state.configuration) +: EvaluateTask.injectSettings
		val config = new LoadBuildConfiguration(stagingDirectory, classpath, loader, compilers, evalPluginDef, delegates, inject, log)
		apply(base, state, config)
	}
	def defaultDelegates: LoadedBuild => Scope => Seq[Scope] = (lb: LoadedBuild) => {
		val rootProject = getRootProject(lb.units)
		def resolveRef(project: ProjectRef) = Scope.resolveRef(lb.root, rootProject, project)
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
	def apply(rootBase: File, s: State, config: LoadBuildConfiguration): (() => Eval, BuildStructure) =
	{
		// load, which includes some resolution, but can't fill in project IDs yet, so follow with full resolution
		val loaded = resolveProjects(load(rootBase, s, config))
		val projects = loaded.units
		lazy val rootEval = lazyEval(loaded.units(loaded.root).unit)
		val settings = config.injectSettings ++ buildConfigurations(loaded, getRootProject(projects), rootEval)
		val delegates = config.delegates(loaded)
		val data = Project.makeSettings(settings, delegates)
		val index = structureIndex(data)
		val streams = mkStreams(projects, loaded.root, data)
		(rootEval, new BuildStructure(projects, loaded.root, settings, data, index, streams, delegates))
	}

	def structureIndex(settings: Settings[Scope]): StructureIndex =
		new StructureIndex(Index.stringToKeyMap(settings), Index.taskToKeyMap(settings), KeyIndex(settings.allKeys( (s,k) => ScopedKey(s,k))))

		// Reevaluates settings after modifying them.  Does not recompile or reload any build components.
	def reapply(newSettings: Seq[Setting[_]], structure: BuildStructure): BuildStructure =
	{
		val newData = Project.makeSettings(newSettings, structure.delegates)
		val newIndex = structureIndex(newData)
		val newStreams = mkStreams(structure.units, structure.root, newData)
		new BuildStructure(units = structure.units, root = structure.root, settings = newSettings, data = newData, index = newIndex, streams = newStreams, delegates = structure.delegates)
	}

	def isProjectThis(s: Setting[_]) = s.key.scope.project == This
	def buildConfigurations(loaded: LoadedBuild, rootProject: URI => String, rootEval: () => Eval): Seq[Setting[_]] =
		loaded.units.toSeq flatMap { case (uri, build) =>
			val eval = if(uri == loaded.root) rootEval else lazyEval(build.unit)
			val pluginSettings = build.unit.plugins.plugins
			val (pluginThisProject, pluginGlobal) = pluginSettings partition isProjectThis
			val projectSettings = build.defined flatMap { case (id, project) =>
				val srcs = configurationSources(project.base)
				val ref = ProjectRef(Some(uri), Some(id))
				val defineConfig = for(c <- project.configurations) yield ( (Config in (ref, ConfigKey(c.name))) :== c)
				val settings =
					(ThisProject :== project) +:
					(ThisProjectRef :== ref) +:
					(defineConfig ++ project.settings ++ pluginThisProject ++ configurations(srcs, eval, build.imports))
				 
				// map This to thisScope, Select(p) to mapRef(uri, rootProject, p)
				transformSettings(projectScope(uri, id), uri, rootProject, settings)
			}
			pluginGlobal ++ projectSettings
		}
	def transformSettings(thisScope: Scope, uri: URI, rootProject: URI => String, settings: Seq[Setting[_]]): Seq[Setting[_]] =
		Project.transform(Scope.resolveScope(thisScope, uri, rootProject), settings)
	def projectScope(uri: URI, id: String): Scope  =  projectScope(ProjectRef(uri, id))
	def projectScope(project: ProjectRef): Scope  =  Scope(Select(project), Global, Global, Global)

	
	def lazyEval(unit: BuildUnit): () => Eval =
	{
		lazy val eval = mkEval(unit)
		() => eval
	}
	def mkEval(unit: BuildUnit): Eval = mkEval(unit.definitions, unit.plugins, Nil)
	def mkEval(defs: LoadedDefinitions, plugs: LoadedPlugins, options: Seq[String]): Eval =
		new Eval(options, defs.target +: plugs.classpath, s => new ConsoleReporter(s), defs.loader, Some(evalOutputDirectory(defs.base)))

	def configurations(srcs: Seq[File], eval: () => Eval, imports: Seq[String]): Seq[Setting[_]] =
		if(srcs.isEmpty) Nil else EvaluateConfigurations(eval(), srcs, imports)

	def load(file: File, s: State, config: LoadBuildConfiguration): LoadedBuild =
		load(file, uri => loadUnit(uri, RetrieveUnit(config.stagingDirectory, uri), s, config) )
	def load(file: File, loader: URI => BuildUnit): LoadedBuild = loadURI(IO.directoryURI(file), loader)
	def loadURI(uri: URI, loader: URI => BuildUnit): LoadedBuild =
	{
		IO.assertAbsolute(uri)
		val (referenced, map) = loadAll(uri :: Nil, Map.empty, loader, Map.empty)
		checkAll(referenced, map)
		new LoadedBuild(uri, map)
	}
	def loaded(unit: BuildUnit): (LoadedBuildUnit, List[ProjectRef]) =
	{
		val defined = projects(unit)
		if(defined.isEmpty) error("No projects defined in build unit " + unit)

		// since base directories are resolved at this point (after 'projects'),
		//   we can compare Files instead of converting to URIs
		def isRoot(p: Project) = p.base == unit.localBase

		val externals = referenced(defined).toList
		val projectsInRoot = defined.filter(isRoot).map(_.id)
		val rootProjects = if(projectsInRoot.isEmpty) defined.head.id :: Nil else projectsInRoot
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
					checkBuildBase(loadedBuild.localBase)
					loadAll(refs.flatMap(_.uri) reverse_::: bs, references.updated(b, refs), externalLoader, builds.updated(b, loadedBuild))
				}
			case Nil => (references, builds)
		}
	def checkProjectBase(buildBase: File, projectBase: File)
	{
		checkDirectory(projectBase)
		assert(buildBase == projectBase || IO.relativize(buildBase, projectBase).isDefined, "Directory " + projectBase + " is not contained in build root " + buildBase)
	}
	def checkBuildBase(base: File) = checkDirectory(base)
	def checkDirectory(base: File)
	{
		assert(base.isDirectory, "Not an existing directory: " + base)
		assert(base.isAbsolute, "Not absolute: " + base)
	}
	def checkAll(referenced: Map[URI, List[ProjectRef]], builds: Map[URI, LoadedBuildUnit])
	{
		val rootProject = getRootProject(builds)
		for( (uri, refs) <- referenced; ref <- refs)
		{
			val (refURI, refID) = Scope.resolveRef(uri, rootProject, ref)
			val loadedUnit = builds(refURI)
			if(! (loadedUnit.defined contains refID) )
				error("No project '" + refID + "' in '" + refURI + "'")
		}
	}

	def resolveBase(against: File): Project => Project =
	{
		def resolve(f: File) =
		{
			val fResolved = new File(IO.directoryURI(IO.resolve(against, f)))
			checkProjectBase(against, fResolved)
			fResolved
		}
		p => p.copy(base = resolve(p.base))
	}
	def resolveProjects(loaded: LoadedBuild): LoadedBuild =
	{
		val rootProject = getRootProject(loaded.units)
		new LoadedBuild(loaded.root, loaded.units.map { case (uri, unit) =>
			IO.assertAbsolute(uri)
			(uri, resolveProjects(uri, unit, rootProject))
		})
	}
	def resolveProjects(uri: URI, unit: LoadedBuildUnit, rootProject: URI => String): LoadedBuildUnit =
	{
		IO.assertAbsolute(uri)
		val resolve = resolveProject(ref => Scope.mapRef(uri, rootProject, ref))
		new LoadedBuildUnit(unit.unit, unit.defined mapValues resolve, unit.rootProjects)
	}
	def resolveProject(resolveRef: ProjectRef => ProjectRef): Project => Project =
	{
		def resolveRefs(prs: Seq[ProjectRef]) = prs map resolveRef
		def resolveDeps(ds: Seq[Project.ClasspathDependency]) = ds map resolveDep
		def resolveDep(d: Project.ClasspathDependency) = d.copy(project = resolveRef(d.project))
		p => p.copy(aggregate = resolveRefs(p.aggregate), dependencies = resolveDeps(p.dependencies), inherits = resolveRefs(p.inherits))
	}
	def projects(unit: BuildUnit): Seq[Project] =
	{
		// we don't have the complete build graph loaded, so we don't have the rootProject function yet.
		//  Therefore, we use mapRefBuild instead of mapRef.  After all builds are loaded, we can fully resolve ProjectRefs.
		val resolve = resolveProject(ref => Scope.mapRefBuild(unit.uri, ref)) compose resolveBase(unit.localBase)
		unit.definitions.builds.flatMap(_.projects map resolve)
	}
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

	def loadUnit(uri: URI, localBase: File, s: State, config: LoadBuildConfiguration): BuildUnit =
	{
		val normBase = localBase.getCanonicalFile
		val defDir = selectProjectDir(normBase)
		val pluginDir = pluginDirectory(defDir)
		val plugs = plugins(pluginDir, s, config)

		val defs = definitionSources(defDir)
		val target = buildOutputDirectory(defDir, config.compilers)
		IO.createDirectory(target)
		val loadedDefs =
			if(defs.isEmpty)
				new LoadedDefinitions(defDir, target, plugs.loader, Build.default(normBase) :: Nil, Nil)
			else
				definitions(defDir, target, defs, plugs, config.compilers, config.log, normBase)

		new BuildUnit(uri, normBase, loadedDefs, plugs)
	}

	def plugins(dir: File, s: State, config: LoadBuildConfiguration): LoadedPlugins = if(dir.exists) buildPlugins(dir, s, config) else noPlugins(config)
	def noPlugins(config: LoadBuildConfiguration): LoadedPlugins = new LoadedPlugins(config.classpath, config.loader, Nil, Nil)
	def buildPlugins(dir: File, s: State, config: LoadBuildConfiguration): LoadedPlugins =
	{
		val (eval,pluginDef) = apply(dir, s, config)
		val pluginState = Project.setProject(Load.initialSession(pluginDef, eval), pluginDef, s)

		val (pluginClasspath, pluginAnalysis) = config.evalPluginDef(pluginDef, pluginState)
		val definitionClasspath = (pluginClasspath ++ config.classpath).distinct
		val pluginLoader = ClasspathUtilities.toLoader(definitionClasspath, config.loader)
		loadPlugins(definitionClasspath, pluginLoader, pluginAnalysis)
	}

	def definitions(base: File, targetBase: File, srcs: Seq[File], plugins: LoadedPlugins, compilers: Compilers, log: Logger, buildBase: File): LoadedDefinitions =
	{
		val (inputs, defAnalysis) = build(plugins.classpath, srcs, targetBase, compilers, log)
		val target = inputs.config.classesDirectory
		val definitionLoader = ClasspathUtilities.toLoader(target :: Nil, plugins.loader)
		val defNames = findDefinitions(defAnalysis)
		val defs = if(defNames.isEmpty) Build.default(buildBase) :: Nil else loadDefinitions(definitionLoader, defNames)
		new LoadedDefinitions(base, target, definitionLoader, defs, defNames)
	}

	def loadDefinitions(loader: ClassLoader, defs: Seq[String]): Seq[Build] =
		defs map { definition => loadDefinition(loader, definition) }
	def loadDefinition(loader: ClassLoader, definition: String): Build =
		ModuleUtilities.getObject(definition, loader).asInstanceOf[Build]

	def build(classpath: Seq[File], sources: Seq[File], target: File, compilers: Compilers, log: Logger): (Inputs, Analysis) =
	{
		val inputs = Compile.inputs(classpath, sources, target, Nil, Nil, Nil, Compile.DefaultMaxErrors)(compilers, log)
		val analysis = Compile(inputs, log)
		(inputs, analysis)
	}

	def loadPlugins(classpath: Seq[File], loader: ClassLoader, analysis: Analysis): LoadedPlugins =
	{
		val (pluginNames, plugins) = if(classpath.isEmpty) (Nil, Nil) else {
			val names = findPlugins(analysis)
			(names, loadPlugins(loader, names) )
		}
		new LoadedPlugins(classpath, loader, plugins, pluginNames)
	}

	def loadPlugins(loader: ClassLoader, pluginNames: Seq[String]): Seq[Setting[_]] =
		pluginNames.flatMap(pluginName => loadPlugin(pluginName, loader))

	def loadPlugin(pluginName: String, loader: ClassLoader): Seq[Setting[_]] =
		ModuleUtilities.getObject(pluginName, loader).asInstanceOf[Plugin].settings

	def importAll(values: Seq[String]) = if(values.isEmpty) Nil else values.map( _ + "._" ).mkString("import ", ", ", "") :: Nil
		
	def findPlugins(analysis: Analysis): Seq[String]  =  discover(analysis, "sbt.Plugin")
	def findDefinitions(analysis: Analysis): Seq[String]  =  discover(analysis, "sbt.Build")
	def discover(analysis: Analysis, subclasses: String*): Seq[String] =
	{
		val subclassSet = subclasses.toSet
		val ds = Discovery(subclassSet, Set.empty)(Test.allDefs(analysis))
		ds.flatMap { case (definition, Discovered(subs,_,_,true)) =>
			if((subs ** subclassSet).isEmpty) Nil else definition.name :: Nil
		}
	}

	def initialSession(structure: BuildStructure, rootEval: () => Eval): SessionSettings =
		new SessionSettings(structure.root, rootProjectMap(structure.units), structure.settings, Map.empty, Map.empty, rootEval)
		
	def rootProjectMap(units: Map[URI, LoadedBuildUnit]): Map[URI, String] =
	{
		val getRoot = getRootProject(units)
		units.keys.map(uri => (uri, getRoot(uri))).toMap
	}

	def baseImports = "import sbt._, Process._, java.io.File, java.net.URI" :: Nil

	final class EvaluatedConfigurations(val eval: Eval, val settings: Seq[Setting[_]])
	final class LoadedDefinitions(val base: File, val target: File, val loader: ClassLoader, val builds: Seq[Build], val buildNames: Seq[String])
	final class LoadedPlugins(val classpath: Seq[File], val loader: ClassLoader, val plugins: Seq[Setting[_]], val pluginNames: Seq[String])
	object LoadedPlugins {
		def empty(loader: ClassLoader) = new LoadedPlugins(Nil, loader, Nil, Nil)
	}
	final class BuildUnit(val uri: URI, val localBase: File, val definitions: LoadedDefinitions, val plugins: LoadedPlugins)
	{
		override def toString = if(uri.getScheme == "file") localBase.toString else (uri + " (locally: " + localBase +")")
	}
	
	final class LoadedBuild(val root: URI, val units: Map[URI, LoadedBuildUnit])
	final class LoadedBuildUnit(val unit: BuildUnit, val defined: Map[String, Project], val rootProjects: Seq[String])
	{
		assert(!rootProjects.isEmpty, "No root projects defined for build unit " + unit)
		def localBase = unit.localBase
		def classpath = unit.definitions.target +: unit.plugins.classpath
		def loader = unit.definitions.loader
		def imports = baseImports ++ importAll(unit.plugins.pluginNames ++ unit.definitions.buildNames)
		override def toString = unit.toString
	}

	// these are unresolved references
	def referenced(definitions: Seq[Project]): Seq[ProjectRef] = definitions flatMap referenced
	def referenced(definition: Project): Seq[ProjectRef] = definition.inherits ++ definition.aggregate ++ definition.dependencies.map(_.project)

	
	final class BuildStructure(val units: Map[URI, LoadedBuildUnit], val root: URI, val settings: Seq[Setting[_]], val data: Settings[Scope], val index: StructureIndex, val streams: Streams, val delegates: Scope => Seq[Scope])
	final class LoadBuildConfiguration(val stagingDirectory: File, val classpath: Seq[File], val loader: ClassLoader, val compilers: Compilers, val evalPluginDef: (BuildStructure, State) => (Seq[File], Analysis), val delegates: LoadedBuild => Scope => Seq[Scope], val injectSettings: Seq[Setting[_]], val log: Logger)
	// information that is not original, but can be reconstructed from the rest of BuildStructure
	final class StructureIndex(val keyMap: Map[String, AttributeKey[_]], val taskToKey: Map[Task[_], ScopedKey[Task[_]]], val keyIndex: KeyIndex)

	private[this] def memo[A,B](implicit f: A => B): A => B =
	{
		val dcache = new mutable.HashMap[A,B]
		(a: A) => dcache.getOrElseUpdate(a, f(a))
	}
}
object BuildStreams
{
		import Load.{BuildStructure, LoadedBuildUnit}
		import Project.display
		import std.{TaskExtra,Transform}
	
	type Streams = std.Streams[ScopedKey[Task[_]]]
	type TaskStreams = std.TaskStreams[ScopedKey[Task[_]]]
	val GlobalPath = "$global"

	def mkStreams(units: Map[URI, LoadedBuildUnit], root: URI, data: Settings[Scope], logRelativePath: Seq[String] = defaultLogPath): Streams =
		std.Streams( path(units, root, logRelativePath), display, LogManager.construct(data) )
		
	def defaultLogPath = "target" :: "streams" :: Nil

	def path(units: Map[URI, LoadedBuildUnit], root: URI, sep: Seq[String])(scoped: ScopedKey[_]): File =
	{
		val (base, sub) = projectPath(units, root, scoped)
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
	def projectPath(units: Map[URI, LoadedBuildUnit], root: URI, scoped: ScopedKey[_]): (File, Seq[String]) =
		scoped.scope.project match
		{
			case Global => (units(root).localBase, GlobalPath :: Nil)
			case Select(ProjectRef(Some(uri), Some(id))) => (units(uri).defined(id).base, Nil)
			case Select(pr) => error("Unresolved project reference (" + pr + ") in " + display(scoped))
			case This => error("Unresolved project reference (This) in " + display(scoped))
		}
}
object BuildPaths
{
	import Path._
	import GlobFilter._

	def defaultStaging = Path.userHome / ".ivy2" / "staging"
	
	def definitionSources(base: File): Seq[File] = (base * "*.scala").getFiles.toSeq
	def configurationSources(base: File): Seq[File] = (base * "*.sbt").getFiles.toSeq
	def pluginDirectory(definitionBase: Path) = definitionBase / "plugins"

	def evalOutputDirectory(base: Path) = outputDirectory(base) / "config-classes"
	def outputDirectory(base: Path) = base / "target"
	def buildOutputDirectory(base: Path, compilers: Compilers) = crossPath(outputDirectory(base), compilers.scalac.scalaInstance)

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