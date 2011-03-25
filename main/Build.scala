/* sbt -- Simple Build Tool
 * Copyright 2011 Mark Harrah
 */
package sbt

	import java.io.File
	import java.net.URI
	import compiler.{Discovered,Discovery,Eval,EvalImports}
	import classpath.ClasspathUtilities
	import scala.annotation.tailrec
	import collection.mutable
	import complete.DefaultParsers.validID
	import Compiler.{Compilers,Inputs}
	import Project.{inScope, ScopedKey, ScopeLocal, Setting}
	import Keys.{appConfiguration, baseDirectory, configuration, streams, thisProject, thisProjectRef}
	import TypeFunctions.{Endo,Id}
	import tools.nsc.reporters.ConsoleReporter
	import Build.{analyzed, data}
	import Scope.{GlobalScope, ThisScope}

// name is more like BuildDefinition, but that is too long
trait Build
{
	def projects: Seq[Project]
	def settings: Seq[Setting[_]] = Defaults.buildCore
}
trait Plugin
{
	def settings: Seq[Project.Setting[_]] = Nil
}

object Build
{
	def default(base: File): Build = new Build { def projects = defaultProject("default", base) :: Nil }
	def defaultProject(id: String, base: File): Project = Project(id, base)

	def data[T](in: Seq[Attributed[T]]): Seq[T] = in.map(_.data)
	def analyzed(in: Seq[Attributed[_]]): Seq[inc.Analysis] = in.flatMap{ _.metadata.get(Keys.analysis) }
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

	val isDummyTask = AttributeKey[Boolean]("is-dummy-task")
	val taskDefinitionKey = AttributeKey[ScopedKey[_]]("task-definition-key")
	val (state, dummyState) = dummy[State]("state")
	val (streamsManager, dummyStreamsManager) = dummy[Streams]("streams-manager")
	val resolvedScoped = SettingKey[ScopedKey[_]]("resolved-scoped")
	private[sbt] val parseResult: TaskKey[_] = TaskKey("$parse-result")

	def injectSettings: Seq[Project.Setting[_]] = Seq(
		(state in GlobalScope) ::= dummyState,
		(streamsManager in GlobalScope) ::= dummyStreamsManager
	)
	
	def dummy[T](name: String): (TaskKey[T], Task[T]) = (TaskKey[T](name), dummyTask(name))
	def dummyTask[T](name: String): Task[T] =
	{
		val base: Task[T] = task( error("Dummy task '" + name + "' did not get converted to a full task.") ) named name
		base.copy(info = base.info.set(isDummyTask, true))
	}
	def isDummy(t: Task[_]): Boolean = t.info.attributes.get(isDummyTask) getOrElse false

	def evalPluginDef(log: Logger)(pluginDef: BuildStructure, state: State): Seq[Attributed[File]] =
	{
		val root = ProjectRef(pluginDef.root, Load.getRootProject(pluginDef.units)(pluginDef.root))
		val pluginKey = Keys.fullClasspath in Configurations.Compile
		val evaluated = evaluateTask(pluginDef, ScopedKey(pluginKey.scope, pluginKey.key), state, root)
		val result = evaluated getOrElse error("Plugin classpath does not exist for plugin definition at " + pluginDef.root)
		processResult(result, log)
	}
	def evaluateTask[T](structure: BuildStructure, taskKey: ScopedKey[Task[T]], state: State, ref: ProjectRef, checkCycles: Boolean = false, maxWorkers: Int = SystemProcessors): Option[Result[T]] =
		withStreams(structure) { str =>
			for( (task, toNode) <- getTask(structure, taskKey, state, str, ref) ) yield
				runTask(task, str, checkCycles, maxWorkers)(toNode)
		}
	def logIncResult(result: Result[_], streams: Streams) = result match { case Inc(i) => logIncomplete(i, streams); case _ => () }
	def logIncomplete(result: Incomplete, streams: Streams)
	{
		val log = streams(ScopedKey(GlobalScope, Keys.logged)).log
		val all = Incomplete linearize result
		val keyed = for(Incomplete(Some(key: Project.ScopedKey[_]), _, msg, _, ex) <- all) yield (key, msg, ex)
		val un = all.filter { i => i.node.isEmpty || i.message.isEmpty }
		for( (key, _, Some(ex)) <- keyed)
			getStreams(key, streams).log.trace(ex)
		log.error("Incomplete task(s):")
		for( (key, msg, ex) <- keyed if(msg.isDefined || ex.isDefined) )
			getStreams(key, streams).log.error("  " + Project.display(key) + ": " + (msg.toList ++ ex.toList).mkString("\n\t"))
		for(u <- un)
			log.debug(u.toString)
		log.error("Run 'last <task>' for the full log(s).")
	}
	def getStreams(key: ScopedKey[_], streams: Streams): TaskStreams =
		streams(ScopedKey(Project.fillTaskAxis(key).scope, Keys.streams.key))
	def withStreams[T](structure: BuildStructure)(f: Streams => T): T =
	{
		val str = std.Streams.closeable(structure.streams)
		try { f(str) } finally { str.close() }
	}
	
	def getTask[T](structure: BuildStructure, taskKey: ScopedKey[Task[T]], state: State, streams: Streams, ref: ProjectRef): Option[(Task[T], Execute.NodeView[Task])] =
	{
		val thisScope = Load.projectScope(ref)
		val resolvedScope = Scope.replaceThis(thisScope)( taskKey.scope )
		for( t <- structure.data.get(resolvedScope, taskKey.key)) yield
			(t, nodeView(state, streams))
	}
	def nodeView[HL <: HList](state: State, streams: Streams, extraDummies: KList[Task, HL] = KNil, extraValues: HL = HNil): Execute.NodeView[Task] =
		Transform(dummyStreamsManager :^: KCons(dummyState, extraDummies), streams :+: HCons(state, extraValues))

	def runTask[Task[_] <: AnyRef, T](root: Task[T], streams: Streams, checkCycles: Boolean = false, maxWorkers: Int = SystemProcessors)(implicit taskToNode: Execute.NodeView[Task]): Result[T] =
	{
		val (service, shutdown) = CompletionService[Task[_], Completed](maxWorkers)

		val x = new Execute[Task](checkCycles)(taskToNode)
		val result = try { x.run(root)(service) } finally { shutdown() }
		val replaced = transformInc(result)
		logIncResult(replaced, streams)
		replaced
	}
	def transformInc[T](result: Result[T]): Result[T] =
		result.toEither.left.map { i =>
			Incomplete.transform(i) {
				case in @ Incomplete(Some(node: Task[_]), _, _, _, _) => in.copy(node = transformNode(node))
				case _ => i
			}
		}
	def transformNode(node: Task[_]): Option[ScopedKey[_]] =
		node.info.attributes get taskDefinitionKey

	def processResult[T](result: Result[T], log: Logger, show: Boolean = false): T =
		onResult(result, log) { v => if(show) println("Result: " + v); v }
	def onResult[T, S](result: Result[T], log: Logger)(f: T => S): S =
		result match
		{
			case Value(v) => f(v)
			case Inc(inc) => throw inc
		}

	// if the return type Seq[Setting[_]] is not explicitly given, scalac hangs
	val injectStreams: ScopedKey[_] => Seq[Setting[_]] = scoped =>
		if(scoped.key == streams.key)
			Seq(streams in scoped.scope <<= streamsManager map { mgr =>
				val stream = mgr(scoped)
				stream.open()
				stream
			})
		else if(scoped.key == resolvedScoped.key)
			Seq(resolvedScoped in scoped.scope :== scoped)
		else if(scoped.key == parseResult.key)
			Seq(parseResult in scoped.scope := error("Unsubstituted parse result for " + Project.display(scoped)) )
		else
			Nil
}
object Index
{
	def taskToKeyMap(data: Settings[Scope]): Map[Task[_], ScopedKey[Task[_]]] =
	{
		// AttributeEntry + the checked type test 'value: Task[_]' ensures that the cast is correct.
		//  (scalac couldn't determine that 'key' is of type AttributeKey[Task[_]] on its own and a type match still required the cast)
		val pairs = for( scope <- data.scopes; AttributeEntry(key, value: Task[_]) <- data.data(scope).entries ) yield
			(value, ScopedKey(scope, key.asInstanceOf[AttributeKey[Task[_]]])) // unclear why this cast is needed even with a type test in the above filter
		pairs.toMap[Task[_], ScopedKey[Task[_]]]
	}
	def stringToKeyMap(settings: Settings[Scope]): Map[String, AttributeKey[_]] =
	{
		val multiMap = settings.data.values.flatMap(_.keys).toList.distinct.groupBy(_.label)
		val duplicates = multiMap collect { case (k, x1 :: x2 :: _) => k }
		if(duplicates.isEmpty)
			multiMap.collect { case (k, v) if validID(k) => (k, v.head) } toMap;
		else
			error(duplicates.mkString("AttributeKey ID collisions detected for '", "', '", "'"))
	}
}
object Load
{
	import BuildPaths._
	import BuildStreams._

	// note that there is State is passed in but not pulled out
	def defaultLoad(state: State, baseDirectory: File, log: Logger): (() => Eval, BuildStructure) =
	{
		val provider = state.configuration.provider
		val scalaProvider = provider.scalaProvider
		val stagingDirectory = defaultStaging.getCanonicalFile
		val base = baseDirectory.getCanonicalFile
		val loader = getClass.getClassLoader
		val classpath = provider.mainClasspath ++ scalaProvider.jars
		val compilers = Compiler.compilers(state.configuration, log)
		val evalPluginDef = EvaluateTask.evalPluginDef(log) _
		val delegates = memo(defaultDelegates)
		val inject: Seq[Project.Setting[_]] = ((appConfiguration in GlobalScope) :== state.configuration) +: EvaluateTask.injectSettings
		val rawConfig = new LoadBuildConfiguration(stagingDirectory, Nil, classpath, loader, compilers, evalPluginDef, delegates, EvaluateTask.injectStreams, inject, log)
		val commonPlugins = buildGlobalPlugins(defaultGlobalPlugins, state, rawConfig)
		val config = rawConfig.copy(commonPluginClasspath = commonPlugins)
		apply(base, state, config)
	}
	def buildGlobalPlugins(baseDirectory: File, state: State, config: LoadBuildConfiguration): Seq[Attributed[File]] =
		if(baseDirectory.isDirectory) buildPluginDefinition(baseDirectory, state, config) else Nil
	def defaultDelegates: LoadedBuild => Scope => Seq[Scope] = (lb: LoadedBuild) => {
		val rootProject = getRootProject(lb.units)
		def resolveRef(project: Reference): ResolvedReference = Scope.resolveReference(lb.root, rootProject, project)
		Scope.delegates(
			project => projectInherit(lb, resolveRef(project)),
			(project, config) => configInherit(lb, resolveRef(project), config, rootProject),
			(project, task) => Nil,
			(project, extra) => Nil
		)
	}
	def configInherit(lb: LoadedBuild, ref: ResolvedReference, config: ConfigKey, rootProject: URI => String): Seq[ConfigKey] =
		ref match
		{
			case pr: ProjectRef => configInheritRef(lb, pr, config)
			case BuildRef(uri) => configInheritRef(lb, ProjectRef(uri, rootProject(uri)), config)
		}
	def configInheritRef(lb: LoadedBuild, ref: ProjectRef, config: ConfigKey): Seq[ConfigKey] =
		getConfiguration(lb.units, ref.build, ref.project, config).extendsConfigs.map(c => ConfigKey(c.name))

	def projectInherit(lb: LoadedBuild, ref: ResolvedReference): Seq[ProjectRef] =
		ref match
		{
			case pr: ProjectRef => projectInheritRef(lb, pr)
			case BuildRef(uri) => Nil
		}
	def projectInheritRef(lb: LoadedBuild, ref: ProjectRef): Seq[ProjectRef] =
		getProject(lb.units, ref.build, ref.project).delegates

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
		val settings = finalTransforms(config.injectSettings ++ buildConfigurations(loaded, getRootProject(projects), rootEval))
		val delegates = config.delegates(loaded)
		val data = Project.makeSettings(settings, delegates, config.scopeLocal)
		val index = structureIndex(data)
		val streams = mkStreams(projects, loaded.root, data)
		(rootEval, new BuildStructure(projects, loaded.root, settings, data, index, streams, delegates, config.scopeLocal))
	}

	// map dependencies on the special tasks so that the scope is the same as the defining key
	// additionally, set the task axis to the defining key if it is not set
	def finalTransforms(ss: Seq[Setting[_]]): Seq[Setting[_]] =
	{
		import EvaluateTask.{parseResult, resolvedScoped}
		def isSpecial(key: AttributeKey[_]) = key == streams.key || key == resolvedScoped.key || key == parseResult.key
		def mapSpecial(to: ScopedKey[_]) = new (ScopedKey ~> ScopedKey){ def apply[T](key: ScopedKey[T]) =
			if(isSpecial(key.key))
			{
				val replaced = Scope.replaceThis(to.scope)(key.scope)
				val scope = if(key.key == resolvedScoped.key) replaced else Scope.fillTaskAxis(replaced, to.key)
				ScopedKey(scope, key.key)
			}
			else key
		}
		def setDefining[T] = (key: ScopedKey[T], value: T) => value match {
			case tk: Task[t] if !EvaluateTask.isDummy(tk) => Task(tk.info.set(EvaluateTask.taskDefinitionKey, key), tk.work).asInstanceOf[T]
			case _ => value
		}
		ss.map(s => s mapReferenced mapSpecial(s.key) mapInit setDefining )
	}

	def structureIndex(settings: Settings[Scope]): StructureIndex =
		new StructureIndex(Index.stringToKeyMap(settings), Index.taskToKeyMap(settings), KeyIndex(settings.allKeys( (s,k) => ScopedKey(s,k))))

		// Reevaluates settings after modifying them.  Does not recompile or reload any build components.
	def reapply(newSettings: Seq[Setting[_]], structure: BuildStructure): BuildStructure =
	{
		val newData = Project.makeSettings(newSettings, structure.delegates, structure.scopeLocal)
		val newIndex = structureIndex(newData)
		val newStreams = mkStreams(structure.units, structure.root, newData)
		new BuildStructure(units = structure.units, root = structure.root, settings = newSettings, data = newData, index = newIndex, streams = newStreams, delegates = structure.delegates, scopeLocal = structure.scopeLocal)
	}

	def isProjectThis(s: Setting[_]) = s.key.scope.project match { case This | Select(ThisProject) => true; case _ => false }
	def buildConfigurations(loaded: LoadedBuild, rootProject: URI => String, rootEval: () => Eval): Seq[Setting[_]] =
		loaded.units.toSeq flatMap { case (uri, build) =>
			val eval = if(uri == loaded.root) rootEval else lazyEval(build.unit)
			val pluginSettings = build.unit.plugins.plugins
			val (pluginThisProject, pluginGlobal) = pluginSettings partition isProjectThis
			val projectSettings = build.defined flatMap { case (id, project) =>
				val srcs = configurationSources(project.base)
				val ref = ProjectRef(uri, id)
				val defineConfig = for(c <- project.configurations) yield ( (configuration in (ref, ConfigKey(c.name))) :== c)
				val settings =
					(thisProject :== project) +:
					(thisProjectRef :== ref) +:
					(defineConfig ++ project.settings ++ pluginThisProject ++ configurations(srcs, eval, build.imports))
				 
				// map This to thisScope, Select(p) to mapRef(uri, rootProject, p)
				transformSettings(projectScope(ref), uri, rootProject, settings)
			}
			val buildScope = Scope(Select(BuildRef(uri)), Global, Global, Global)
			val buildBase = baseDirectory :== build.localBase
			pluginGlobal ++ inScope(buildScope)(buildBase +: build.buildSettings) ++ projectSettings
		}
	def transformSettings(thisScope: Scope, uri: URI, rootProject: URI => String, settings: Seq[Setting[_]]): Seq[Setting[_]] =
		Project.transform(Scope.resolveScope(thisScope, uri, rootProject), settings)
	def projectScope(project: Reference): Scope  =  Scope(Select(project), Global, Global, Global)

	
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

	def load(file: File, s: State, config: LoadBuildConfiguration): PartBuild =
		load(file, uri => loadUnit(uri, RetrieveUnit(config.stagingDirectory, uri), s, config) )
	def load(file: File, loader: URI => BuildUnit): PartBuild = loadURI(IO.directoryURI(file), loader)
	def loadURI(uri: URI, loader: URI => BuildUnit): PartBuild =
	{
		IO.assertAbsolute(uri)
		val (referenced, map) = loadAll(uri :: Nil, Map.empty, loader, Map.empty)
		checkAll(referenced, map)
		new PartBuild(uri, map)
	}
	def loaded(unit: BuildUnit): (PartBuildUnit, List[ProjectReference]) =
	{
		val defined = projects(unit)
		if(defined.isEmpty) error("No projects defined in build unit " + unit)

		// since base directories are resolved at this point (after 'projects'),
		//   we can compare Files instead of converting to URIs
		def isRoot(p: Project) = p.base == unit.localBase

		val externals = referenced(defined).toList
		val projectsInRoot = defined.filter(isRoot).map(_.id)
		val rootProjects = if(projectsInRoot.isEmpty) defined.head.id :: Nil else projectsInRoot
		(new PartBuildUnit(unit, defined.map(d => (d.id, d)).toMap, rootProjects, buildSettings(unit)), externals)
	}
	def buildSettings(unit: BuildUnit): Seq[Setting[_]] =
	{
		val buildScope = GlobalScope.copy(project = Select(BuildRef(unit.uri)))
		val resolve = Scope.resolveBuildScope(buildScope, unit.uri)
		Project.transform(resolve, unit.definitions.builds.flatMap(_.settings))
	}

	@tailrec def loadAll(bases: List[URI], references: Map[URI, List[ProjectReference]], externalLoader: URI => BuildUnit, builds: Map[URI, PartBuildUnit]): (Map[URI, List[ProjectReference]], Map[URI, PartBuildUnit]) =
		bases match
		{
			case b :: bs =>
				if(builds contains b)
					loadAll(bs, references, externalLoader, builds)
				else
				{
					val (loadedBuild, refs) = loaded(externalLoader(b))
					checkBuildBase(loadedBuild.unit.localBase)
					loadAll(refs.flatMap(Reference.uri) reverse_::: bs, references.updated(b, refs), externalLoader, builds.updated(b, loadedBuild))
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
		assert(base.isAbsolute, "Not absolute: " + base)
		if(base.isFile)
			error("Not a directory: " + base)
		else if(!base.exists)
			IO createDirectory base
	}
	def resolveAll(builds: Map[URI, PartBuildUnit]): Map[URI, LoadedBuildUnit] =
	{
		val rootProject = getRootProject(builds)
		builds map { case (uri,unit) =>
			(uri, unit.resolveRefs( ref => Scope.resolveProjectRef(uri, rootProject, ref) ))
		} toMap;
	}
	def checkAll(referenced: Map[URI, List[ProjectReference]], builds: Map[URI, PartBuildUnit])
	{
		val rootProject = getRootProject(builds)
		for( (uri, refs) <- referenced; ref <- refs)
		{
			val ProjectRef(refURI, refID) = Scope.resolveProjectRef(uri, rootProject, ref)
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
	def resolveProjects(loaded: PartBuild): LoadedBuild =
	{
		val rootProject = getRootProject(loaded.units)
		new LoadedBuild(loaded.root, loaded.units map { case (uri, unit) =>
			IO.assertAbsolute(uri)
			(uri, resolveProjects(uri, unit, rootProject))
		})
	}
	def resolveProjects(uri: URI, unit: PartBuildUnit, rootProject: URI => String): LoadedBuildUnit =
	{
		IO.assertAbsolute(uri)
		val resolve = (_: Project).resolve(ref => Scope.resolveProjectRef(uri, rootProject, ref))
		new LoadedBuildUnit(unit.unit, unit.defined mapValues resolve, unit.rootProjects, unit.buildSettings)
	}
	def projects(unit: BuildUnit): Seq[Project] =
	{
		// we don't have the complete build graph loaded, so we don't have the rootProject function yet.
		//  Therefore, we use resolveProjectBuild instead of resolveProjectRef.  After all builds are loaded, we can fully resolve ProjectReferences.
		val resolveBuild = (_: Project).resolveBuild(ref => Scope.resolveProjectBuild(unit.uri, ref))
		val resolve = resolveBuild compose resolveBase(unit.localBase)
		unit.definitions.builds.flatMap(_.projects map resolve)
	}
	def getRootProject(map: Map[URI, BuildUnitBase]): URI => String =
		uri => getBuild(map, uri).rootProjects.headOption getOrElse emptyBuild(uri)
	def getConfiguration(map: Map[URI, LoadedBuildUnit], uri: URI, id: String, conf: ConfigKey): Configuration =
		getProject(map, uri, id).configurations.find(_.name == conf.name) getOrElse noConfiguration(uri, id, conf.name)

	def getProject(map: Map[URI, LoadedBuildUnit], uri: URI, id: String): ResolvedProject =
		getBuild(map, uri).defined.getOrElse(id, noProject(uri, id))
	def getBuild[T](map: Map[URI, T], uri: URI): T =
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

	def plugins(dir: File, s: State, config: LoadBuildConfiguration): LoadedPlugins = if(dir.exists) buildPlugins(dir, s, config) else noPlugins(dir, config)
	def noPlugins(dir: File, config: LoadBuildConfiguration): LoadedPlugins = loadPluginDefinition(dir, config, config.commonPluginClasspath)
	def buildPlugins(dir: File, s: State, config: LoadBuildConfiguration): LoadedPlugins =
		loadPluginDefinition(dir, config, buildPluginDefinition(dir, s, config))

	def loadPluginDefinition(dir: File, config: LoadBuildConfiguration, pluginClasspath: Seq[Attributed[File]]): LoadedPlugins =
	{
		val definitionClasspath = if(pluginClasspath.isEmpty) config.classpath else (data(pluginClasspath) ++ config.classpath).distinct
		val pluginLoader = if(pluginClasspath.isEmpty) config.loader else ClasspathUtilities.toLoader(definitionClasspath, config.loader)
		loadPlugins(dir, definitionClasspath, pluginLoader, analyzed(pluginClasspath))
	}
	def buildPluginDefinition(dir: File, s: State, config: LoadBuildConfiguration): Seq[Attributed[File]] =
	{
		val (eval,pluginDef) = apply(dir, s, config)
		val pluginState = Project.setProject(Load.initialSession(pluginDef, eval), pluginDef, s)
		val thisPluginClasspath = config.evalPluginDef(pluginDef, pluginState)
		(thisPluginClasspath ++ config.commonPluginClasspath).distinct
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

	def build(classpath: Seq[File], sources: Seq[File], target: File, compilers: Compilers, log: Logger): (Inputs, inc.Analysis) =
	{
		val inputs = Compiler.inputs(classpath, sources, target, Nil, Nil, Compiler.DefaultMaxErrors)(compilers, log)
		val analysis = Compiler(inputs, log)
		(inputs, analysis)
	}

	def loadPlugins(dir: File, classpath: Seq[File], loader: ClassLoader, analysis: Seq[inc.Analysis]): LoadedPlugins =
	{
		val (pluginNames, plugins) = if(classpath.isEmpty) (Nil, Nil) else {
			val names = ( binaryPlugins(loader) ++ (analysis flatMap findPlugins) ).distinct
			(names, loadPlugins(loader, names) )
		}
		new LoadedPlugins(dir, classpath, loader, plugins, pluginNames)
	}
	def binaryPlugins(loader: ClassLoader): Seq[String] =
	{
		import collection.JavaConversions._
		loader.getResources("sbt/sbt.plugins").toSeq flatMap { u => IO.readLinesURL(u) map { _.trim } filter { !_.isEmpty } };
	}

	def loadPlugins(loader: ClassLoader, pluginNames: Seq[String]): Seq[Setting[_]] =
		pluginNames.flatMap(pluginName => loadPlugin(pluginName, loader))

	def loadPlugin(pluginName: String, loader: ClassLoader): Seq[Setting[_]] =
		ModuleUtilities.getObject(pluginName, loader).asInstanceOf[Plugin].settings

	def importAll(values: Seq[String]) = if(values.isEmpty) Nil else values.map( _ + "._" ).mkString("import ", ", ", "") :: Nil
		
	def findPlugins(analysis: inc.Analysis): Seq[String]  =  discover(analysis, "sbt.Plugin")
	def findDefinitions(analysis: inc.Analysis): Seq[String]  =  discover(analysis, "sbt.Build")
	def discover(analysis: inc.Analysis, subclasses: String*): Seq[String] =
	{
		val subclassSet = subclasses.toSet
		val ds = Discovery(subclassSet, Set.empty)(Tests.allDefs(analysis))
		ds.flatMap {
			case (definition, Discovered(subs,_,_,true)) =>
				if((subs & subclassSet).isEmpty) Nil else definition.name :: Nil
			case _ => Nil
		}
	}

	def initialSession(structure: BuildStructure, rootEval: () => Eval): SessionSettings =
		new SessionSettings(structure.root, rootProjectMap(structure.units), structure.settings, Map.empty, Map.empty, rootEval)
		
	def rootProjectMap(units: Map[URI, LoadedBuildUnit]): Map[URI, String] =
	{
		val getRoot = getRootProject(units)
		units.keys.map(uri => (uri, getRoot(uri))).toMap
	}

	def baseImports = "import sbt._, Process._, Keys._" :: Nil

	final class EvaluatedConfigurations(val eval: Eval, val settings: Seq[Setting[_]])
	final class LoadedDefinitions(val base: File, val target: File, val loader: ClassLoader, val builds: Seq[Build], val buildNames: Seq[String])
	final class LoadedPlugins(val base: File, val classpath: Seq[File], val loader: ClassLoader, val plugins: Seq[Setting[_]], val pluginNames: Seq[String])
	final class BuildUnit(val uri: URI, val localBase: File, val definitions: LoadedDefinitions, val plugins: LoadedPlugins)
	{
		override def toString = if(uri.getScheme == "file") localBase.toString else (uri + " (locally: " + localBase +")")
	}
	
	final class LoadedBuild(val root: URI, val units: Map[URI, LoadedBuildUnit])
	final class PartBuild(val root: URI, val units: Map[URI, PartBuildUnit])
	sealed trait BuildUnitBase { def rootProjects: Seq[String]; def buildSettings: Seq[Setting[_]] }
	final class PartBuildUnit(val unit: BuildUnit, val defined: Map[String, Project], val rootProjects: Seq[String], val buildSettings: Seq[Setting[_]]) extends BuildUnitBase
	{
		def resolve(f: Project => ResolvedProject): LoadedBuildUnit = new LoadedBuildUnit(unit, defined mapValues f, rootProjects, buildSettings)
		def resolveRefs(f: ProjectReference => ProjectRef): LoadedBuildUnit = resolve(_ resolve f)
	}
	final class LoadedBuildUnit(val unit: BuildUnit, val defined: Map[String, ResolvedProject], val rootProjects: Seq[String], val buildSettings: Seq[Setting[_]]) extends BuildUnitBase
	{
		assert(!rootProjects.isEmpty, "No root projects defined for build unit " + unit)
		def localBase = unit.localBase
		def classpath = unit.definitions.target +: unit.plugins.classpath
		def loader = unit.definitions.loader
		def imports = getImports(unit)
		override def toString = unit.toString
	}
	def getImports(unit: BuildUnit) = baseImports ++ importAll(unit.plugins.pluginNames ++ unit.definitions.buildNames)

	def referenced[PR <: ProjectReference](definitions: Seq[ProjectDefinition[PR]]): Seq[PR] = definitions flatMap { _.referenced }
	
	final class BuildStructure(val units: Map[URI, LoadedBuildUnit], val root: URI, val settings: Seq[Setting[_]], val data: Settings[Scope], val index: StructureIndex, val streams: Streams, val delegates: Scope => Seq[Scope], val scopeLocal: ScopeLocal)
	final case class LoadBuildConfiguration(stagingDirectory: File, commonPluginClasspath: Seq[Attributed[File]], classpath: Seq[File], loader: ClassLoader, compilers: Compilers, evalPluginDef: (BuildStructure, State) => Seq[Attributed[File]], delegates: LoadedBuild => Scope => Seq[Scope], scopeLocal: ScopeLocal, injectSettings: Seq[Setting[_]], log: Logger)
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
	
	type Streams = std.Streams[ScopedKey[_]]
	type TaskStreams = std.TaskStreams[ScopedKey[_]]
	val GlobalPath = "$global"
	val BuildUnitPath = "$build"

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
			case Select(BuildRef(uri)) => (units(uri).localBase, BuildUnitPath :: Nil)
			case Select(ProjectRef(uri, id)) => (units(uri).defined(id).base, Nil)
			case Select(pr) => error("Unresolved project reference (" + pr + ") in " + display(scoped))
			case This => error("Unresolved project reference (This) in " + display(scoped))
		}
}
object BuildPaths
{
	import Path._
	import GlobFilter._

	def defaultStaging = Path.userHome / ".sbt" / "staging"
	def defaultGlobalPlugins = Path.userHome / ".sbt" / "plugins"
	
	def definitionSources(base: File): Seq[File] = (base * "*.scala").getFiles
	def configurationSources(base: File): Seq[File] = (base * "*.sbt").getFiles
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