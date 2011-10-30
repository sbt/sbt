/* sbt -- Simple Build Tool
 * Copyright 2011 Mark Harrah
 */
package sbt

	import java.io.File
	import java.net.URI
	import compiler.{Eval,EvalImports}
	import xsbt.api.{Discovered,Discovery}
	import classpath.ClasspathUtilities
	import scala.annotation.tailrec
	import collection.mutable
	import Compiler.{Compilers,Inputs}
	import inc.{FileValueCache, Locate}
	import Project.{inScope, ScopedKey, ScopeLocal, Setting}
	import Keys.{appConfiguration, baseDirectory, configuration, streams, Streams, thisProject, thisProjectRef}
	import Keys.{globalLogging, isDummy, loadedBuild, parseResult, resolvedScoped, taskDefinitionKey}
	import tools.nsc.reporters.ConsoleReporter
	import Build.{analyzed, data}
	import Scope.{GlobalScope, ThisScope}
	import Types.const

object Load
{
	import BuildPaths._
	import BuildStreams._
	import Locate.DefinesClass
	
	// note that there is State passed in but not pulled out
	def defaultLoad(state: State, baseDirectory: File, log: Logger): (() => Eval, BuildStructure) =
	{
		val globalBase = BuildPaths.getGlobalBase(state)
		val base = baseDirectory.getCanonicalFile
		val definesClass = FileValueCache(Locate.definesClass _)
		val rawConfig = defaultPreGlobal(state, base, definesClass.get, globalBase, log)
		val config = defaultWithGlobal(state, base, rawConfig, globalBase, log)
		val result = apply(base, state, config)
		definesClass.clear()
		result
	}
	def defaultPreGlobal(state: State, baseDirectory: File, definesClass: DefinesClass, globalBase: File, log: Logger): LoadBuildConfiguration =
	{
		val provider = state.configuration.provider
		val scalaProvider = provider.scalaProvider
		val stagingDirectory = defaultStaging(globalBase).getCanonicalFile
		val loader = getClass.getClassLoader
		val classpath = Attributed.blankSeq(provider.mainClasspath ++ scalaProvider.jars)
		val compilers = Compiler.compilers(ClasspathOptions.boot)(state.configuration, log)
		val evalPluginDef = EvaluateTask.evalPluginDef(log) _
		val delegates = defaultDelegates
		val inject = InjectSettings(injectGlobal(state), Nil, const(Nil))
		new LoadBuildConfiguration(stagingDirectory, classpath, loader, compilers, evalPluginDef, definesClass, delegates, EvaluateTask.injectStreams, inject, None, log)
	}
	def injectGlobal(state: State): Seq[Project.Setting[_]] =
		(appConfiguration in GlobalScope :== state.configuration) +:
		EvaluateTask.injectSettings
	def defaultWithGlobal(state: State, base: File, rawConfig: LoadBuildConfiguration, globalBase: File, log: Logger): LoadBuildConfiguration =
	{
		val withGlobal = loadGlobal(state, base, defaultGlobalPlugins(globalBase), rawConfig)
		loadGlobalSettings(base, globalBase, defaultGlobalSettings(globalBase), withGlobal)
	}

	def loadGlobalSettings(base: File, globalBase: File, files: Seq[File], config: LoadBuildConfiguration): LoadBuildConfiguration =
	{
		val compiled: ClassLoader => Seq[Setting[_]]  =
			if(files.isEmpty || base == globalBase) const(Nil) else buildGlobalSettings(globalBase, files, config)
		config.copy(injectSettings = config.injectSettings.copy(projectLoaded = compiled))
	}
	def buildGlobalSettings(base: File, files: Seq[File], config: LoadBuildConfiguration): ClassLoader => Seq[Setting[_]] =
	{	
		val eval = mkEval(data(config.globalPluginClasspath), base, defaultEvalOptions)
		val imports = baseImports ++ importAllRoot(config.globalPluginNames)
		EvaluateConfigurations(eval, files, imports)
	}
	def loadGlobal(state: State, base: File, global: File, config: LoadBuildConfiguration): LoadBuildConfiguration =
		if(base != global && global.exists)
			config.copy(globalPlugin = Some(GlobalPlugin.load(global, state, config)))
		else
			config
	def defaultDelegates: LoadedBuild => Scope => Seq[Scope] = (lb: LoadedBuild) => {
		val rootProject = getRootProject(lb.units)
		def resolveRef(project: Reference): ResolvedReference = Scope.resolveReference(lb.root, rootProject, project)
		Scope.delegates(
			lb.allProjectRefs,
			(_: ResolvedProject).configurations.map(c => ConfigKey(c.name)),
			resolveRef,
			rootProject,
			project => projectInherit(lb, project),
			(project, config) => configInherit(lb, project, config, rootProject),
			task => task.extend,
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
		configurationOpt(lb.units, ref.build, ref.project, config).toList.flatMap(_.extendsConfigs).map(c => ConfigKey(c.name))

	def projectInherit(lb: LoadedBuild, ref: ProjectRef): Seq[ProjectRef] =
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
		val settings = finalTransforms(buildConfigurations(loaded, getRootProject(projects), rootEval, config.injectSettings))
		val delegates = config.delegates(loaded)
		val data = Project.makeSettings(settings, delegates, config.scopeLocal)( Project.showLoadingKey( loaded ) )
		val index = structureIndex(data, settings)
		val streams = mkStreams(projects, loaded.root, data)
		(rootEval, new BuildStructure(projects, loaded.root, settings, data, index, streams, delegates, config.scopeLocal))
	}

	// map dependencies on the special tasks:
	// 1. the scope of 'streams' is the same as the defining key and has the task axis set to the defining key
	// 2. the defining key is stored on constructed tasks
	// 3. resolvedScoped is replaced with the defining key as a value
	// 4. parseResult is replaced with a task that provides the result of parsing for the defined InputTask
	// Note: this must be idempotent.
	def finalTransforms(ss: Seq[Setting[_]]): Seq[Setting[_]] =
	{
		def mapSpecial(to: ScopedKey[_]) = new (ScopedKey ~> ScopedKey){ def apply[T](key: ScopedKey[T]) =
			if(key.key == streams.key)
				ScopedKey(Scope.fillTaskAxis(Scope.replaceThis(to.scope)(key.scope), to.key), key.key)
			else key
		}
		def setDefining[T] = (key: ScopedKey[T], value: T) => value match {
			case tk: Task[t] => setDefinitionKey(tk, key).asInstanceOf[T]
			case ik: InputTask[t] => ik.mapTask( tk => setDefinitionKey(tk, key) ).asInstanceOf[T]
			case _ => value
		}
		def setResolved(defining: ScopedKey[_]) = new (ScopedKey ~> Option) { def apply[T](key: ScopedKey[T]): Option[T] =
			key.key match
			{
				case resolvedScoped.key => Some(defining.asInstanceOf[T])
				case parseResult.key =>
						import std.TaskExtra._
					val getResult = InputTask.inputMap map { m => m get defining getOrElse error("No parsed value for " + Project.displayFull(defining) + "\n" + m) }
					Some(getResult.asInstanceOf[T])
				case _ => None
			}
		}
		ss.map(s => s mapConstant setResolved(s.key) mapReferenced mapSpecial(s.key) mapInit setDefining )
	}
	def setDefinitionKey[T](tk: Task[T], key: ScopedKey[_]): Task[T] =
		if(isDummy(tk)) tk else Task(tk.info.set(Keys.taskDefinitionKey, key), tk.work)

	def structureIndex(data: Settings[Scope], settings: Seq[Setting[_]]): StructureIndex =
	{
		val keys = Index.allKeys(settings)
		val attributeKeys = Index.attributeKeys(data) ++ keys.map(_.key)
		val scopedKeys = keys ++ data.allKeys( (s,k) => ScopedKey(s,k))
		new StructureIndex(Index.stringToKeyMap(attributeKeys), Index.taskToKeyMap(data), Index.triggers(data), KeyIndex(scopedKeys))
	}

		// Reevaluates settings after modifying them.  Does not recompile or reload any build components.
	def reapply(newSettings: Seq[Setting[_]], structure: BuildStructure)(implicit display: Show[ScopedKey[_]]): BuildStructure =
	{
		val transformed = finalTransforms(newSettings)
		val newData = Project.makeSettings(transformed, structure.delegates, structure.scopeLocal)
		val newIndex = structureIndex(newData, transformed)
		val newStreams = mkStreams(structure.units, structure.root, newData)
		new BuildStructure(units = structure.units, root = structure.root, settings = transformed, data = newData, index = newIndex, streams = newStreams, delegates = structure.delegates, scopeLocal = structure.scopeLocal)
	}

	def isProjectThis(s: Setting[_]) = s.key.scope.project match { case This | Select(ThisProject) => true; case _ => false }
	def buildConfigurations(loaded: LoadedBuild, rootProject: URI => String, rootEval: () => Eval, injectSettings: InjectSettings): Seq[Setting[_]] =
		((loadedBuild in GlobalScope :== loaded) +:
		transformProjectOnly(loaded.root, rootProject, injectSettings.global)) ++ 
		loaded.units.toSeq.flatMap { case (uri, build) =>
			val eval = if(uri == loaded.root) rootEval else lazyEval(build.unit)
			val pluginSettings = build.unit.plugins.plugins
			val (pluginThisProject, pluginNotThis) = pluginSettings partition isProjectThis
			val projectSettings = build.defined flatMap { case (id, project) =>
				val srcs = configurationSources(project.base)
				val ref = ProjectRef(uri, id)
				val defineConfig = for(c <- project.configurations) yield ( (configuration in (ref, ConfigKey(c.name))) :== c)
				val loader = build.unit.definitions.loader
				val settings =
					(thisProject :== project) +:
					(thisProjectRef :== ref) +:
					(defineConfig ++ project.settings ++ injectSettings.projectLoaded(loader) ++ pluginThisProject ++ configurations(srcs, eval, build.imports)(loader) ++ injectSettings.project)
				 
				// map This to thisScope, Select(p) to mapRef(uri, rootProject, p)
				transformSettings(projectScope(ref), uri, rootProject, settings)
			}
			val buildScope = Scope(Select(BuildRef(uri)), Global, Global, Global)
			val buildBase = baseDirectory :== build.localBase
			val buildSettings = transformSettings(buildScope, uri, rootProject, pluginNotThis ++ (buildBase +: build.buildSettings))
			buildSettings ++ projectSettings
		}
	def transformProjectOnly(uri: URI, rootProject: URI => String, settings: Seq[Setting[_]]): Seq[Setting[_]] =
		Project.transform(Scope.resolveProject(uri, rootProject), settings)
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
		mkEval(defs.target ++ plugs.classpath, defs.base, options)
	def mkEval(classpath: Seq[File], base: File, options: Seq[String]): Eval =
		new Eval(options, classpath, s => new ConsoleReporter(s), Some(evalOutputDirectory(base)))

	def configurations(srcs: Seq[File], eval: () => Eval, imports: Seq[String]): ClassLoader => Seq[Setting[_]] =
		if(srcs.isEmpty) const(Nil) else EvaluateConfigurations(eval(), srcs, imports)

	def load(file: File, s: State, config: LoadBuildConfiguration): PartBuild =
	{
		val fail = (uri: URI) => error("Invalid build URI (no handler available): " + uri)
		val resolver = (info: BuildLoader.ResolveInfo) => RetrieveUnit(info.staging, info.uri)
		val build = (info: BuildLoader.BuildInfo) => Some(() => loadUnit(info.uri, info.base, info.state, info.config))
		val components = BuildLoader.components(resolver, build, full = BuildLoader.componentLoader)
		val builtinLoader = BuildLoader(components, fail, s, config)
		load(file, builtinLoader)
	}
	def load(file: File, loaders: BuildLoader): PartBuild = loadURI(IO.directoryURI(file), loaders)
	def loadURI(uri: URI, loaders: BuildLoader): PartBuild =
	{
		IO.assertAbsolute(uri)
		val (referenced, map, newLoaders) = loadAll(uri :: Nil, Map.empty, loaders, Map.empty)
		checkAll(referenced, map)
		val build = new PartBuild(uri, map)
		newLoaders transformAll build
	}
	def addResolvers(unit: BuildUnit, isRoot: Boolean, loaders: BuildLoader): BuildLoader =
		unit.definitions.builds.flatMap(_.buildLoaders) match
		{
			case Nil => loaders
			case x :: xs =>
				import Alternatives._
				val resolver = (x /: xs){ _ | _ }
				if(isRoot) loaders.setRoot(resolver) else loaders.addNonRoot(unit.uri, resolver)
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

	@tailrec def loadAll(bases: List[URI], references: Map[URI, List[ProjectReference]], loaders: BuildLoader, builds: Map[URI, PartBuildUnit]): (Map[URI, List[ProjectReference]], Map[URI, PartBuildUnit], BuildLoader) =
		bases match
		{
			case b :: bs =>
				if(builds contains b)
					loadAll(bs, references, loaders, builds)
				else
				{
					val (loadedBuild, refs) = loaded(loaders(b))
					checkBuildBase(loadedBuild.unit.localBase)
					val newLoader = addResolvers(loadedBuild.unit, builds.isEmpty, loaders)
					loadAll(refs.flatMap(Reference.uri) reverse_::: bs, references.updated(b, refs), newLoader, builds.updated(b, loadedBuild))
				}
			case Nil => (references, builds, loaders)
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
		new LoadedBuildUnit(unit.unit, unit.defined mapValues resolve toMap, unit.rootProjects, unit.buildSettings)
	}
	def projects(unit: BuildUnit): Seq[Project] =
	{
		// we don't have the complete build graph loaded, so we don't have the rootProject function yet.
		//  Therefore, we use resolveProjectBuild instead of resolveProjectRef.  After all builds are loaded, we can fully resolve ProjectReferences.
		val resolveBuild = (_: Project).resolveBuild(ref => Scope.resolveProjectBuild(unit.uri, ref))
		val resolve = resolveBuild compose resolveBase(unit.localBase)
		unit.definitions.builds.flatMap(_.projectDefinitions(unit.localBase) map resolve)
	}
	def getRootProject(map: Map[URI, BuildUnitBase]): URI => String =
		uri => getBuild(map, uri).rootProjects.headOption getOrElse emptyBuild(uri)
	def getConfiguration(map: Map[URI, LoadedBuildUnit], uri: URI, id: String, conf: ConfigKey): Configuration =
		configurationOpt(map, uri, id, conf) getOrElse noConfiguration(uri, id, conf.name)
	def configurationOpt(map: Map[URI, LoadedBuildUnit], uri: URI, id: String, conf: ConfigKey): Option[Configuration] =
		getProject(map, uri, id).configurations.find(_.name == conf.name)

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
		val (plugs, defs) = if(pluginDir.exists) loadUnitOld(defDir, pluginDir, s, config) else loadUnitNew(defDir, s, config)
		new BuildUnit(uri, normBase, defs, plugs)
	}
	def loadUnitNew(defDir: File, s: State, config: LoadBuildConfiguration): (LoadedPlugins, LoadedDefinitions) =
	{
		val plugs = plugins(defDir, s, config)
		val defNames = analyzed(plugs.fullClasspath) flatMap findDefinitions
		val defs = if(defNames.isEmpty) Build.default :: Nil else loadDefinitions(plugs.loader, defNames)
		val loadedDefs = new LoadedDefinitions(defDir, Nil, plugs.loader, defs, defNames)
		(plugs, loadedDefs)
	}
	def loadUnitOld(defDir: File, pluginDir: File, s: State, config: LoadBuildConfiguration): (LoadedPlugins, LoadedDefinitions) =
	{
		val plugs = plugins(pluginDir, s, config)
		val defs = definitionSources(defDir)
		val target = buildOutputDirectory(defDir, config.compilers)
		IO.createDirectory(target)
		val loadedDefs =
			if(defs.isEmpty)
				new LoadedDefinitions(defDir, target :: Nil, plugs.loader, Build.default :: Nil, Nil)
			else
				definitions(defDir, target, defs, plugs, config.definesClass, config.compilers, config.log)
		(plugs, loadedDefs)
	}

	def globalPluginClasspath(globalPlugin: Option[GlobalPlugin]): Seq[Attributed[File]] =
		globalPlugin match
		{
			case Some(cp) => cp.data.fullClasspath
			case None => Nil
		}
	val autoPluginSettings: Seq[Setting[_]] = inScope(GlobalScope in LocalRootProject)(Seq(
		Keys.sbtPlugin :== true,
		Keys.onLoadMessage <<= Keys.baseDirectory("Loading project definition from " + _)
	))
	def enableSbtPlugin(config: LoadBuildConfiguration): LoadBuildConfiguration =
		config.copy(injectSettings = config.injectSettings.copy(global = autoPluginSettings ++ config.injectSettings.global))
	def activateGlobalPlugin(config: LoadBuildConfiguration): LoadBuildConfiguration =
		config.globalPlugin match
		{
			case Some(gp) => config.copy(injectSettings = config.injectSettings.copy(project = gp.inject))
			case None => config
		}
	def plugins(dir: File, s: State, config: LoadBuildConfiguration): LoadedPlugins =
		if(hasDefinition(dir))
			buildPlugins(dir, s, enableSbtPlugin(activateGlobalPlugin(config)))
		else
			noPlugins(dir, config)

	def hasDefinition(dir: File) =
	{
		import Path._
		!(dir * -GlobFilter(DefaultTargetName)).get.isEmpty
	}
	def noPlugins(dir: File, config: LoadBuildConfiguration): LoadedPlugins = loadPluginDefinition(dir, config, config.globalPluginClasspath)
	def buildPlugins(dir: File, s: State, config: LoadBuildConfiguration): LoadedPlugins =
		loadPluginDefinition(dir, config, buildPluginDefinition(dir, s, config))

	def loadPluginDefinition(dir: File, config: LoadBuildConfiguration, pluginClasspath: Seq[Attributed[File]]): LoadedPlugins =
	{
		val (definitionClasspath, pluginLoader) = pluginDefinitionLoader(config, pluginClasspath)
		loadPlugins(dir, definitionClasspath, pluginLoader)
	}
	def pluginDefinitionLoader(config: LoadBuildConfiguration, pluginClasspath: Seq[Attributed[File]]): (Seq[Attributed[File]], ClassLoader) =
	{
		val definitionClasspath = if(pluginClasspath.isEmpty) config.classpath else (pluginClasspath ++ config.classpath).distinct
		val pluginLoader = if(pluginClasspath.isEmpty) config.loader else ClasspathUtilities.toLoader(data(pluginClasspath), config.loader)
		(definitionClasspath, pluginLoader)
	}
	def buildPluginDefinition(dir: File, s: State, config: LoadBuildConfiguration): Seq[Attributed[File]] =
	{
		val (eval,pluginDef) = apply(dir, s, config)
		val pluginState = Project.setProject(Load.initialSession(pluginDef, eval), pluginDef, s)
		config.evalPluginDef(pluginDef, pluginState)
	}

	def definitions(base: File, targetBase: File, srcs: Seq[File], plugins: LoadedPlugins, definesClass: DefinesClass, compilers: Compilers, log: Logger): LoadedDefinitions =
	{
		val (inputs, defAnalysis) = build(plugins.fullClasspath, srcs, targetBase, compilers, definesClass, log)
		val target = inputs.config.classesDirectory
		val definitionLoader = ClasspathUtilities.toLoader(target :: Nil, plugins.loader)
		val defNames = findDefinitions(defAnalysis)
		val defs = if(defNames.isEmpty) Build.default :: Nil else loadDefinitions(definitionLoader, defNames)
		new LoadedDefinitions(base, target :: Nil, definitionLoader, defs, defNames)
	}

	def loadDefinitions(loader: ClassLoader, defs: Seq[String]): Seq[Build] =
		defs map { definition => loadDefinition(loader, definition) }
	def loadDefinition(loader: ClassLoader, definition: String): Build =
		ModuleUtilities.getObject(definition, loader).asInstanceOf[Build]

	def build(classpath: Seq[Attributed[File]], sources: Seq[File], target: File, compilers: Compilers, definesClass: DefinesClass, log: Logger): (Inputs, inc.Analysis) =
	{
		// TODO: make used of classpath metadata for recompilation
		val inputs = Compiler.inputs(data(classpath), sources, target, Nil, Nil, definesClass, Compiler.DefaultMaxErrors, CompileOrder.Mixed)(compilers, log)
		val analysis =
			try { Compiler(inputs, log) }
			catch { case _: xsbti.CompileFailed => throw new NoMessageException } // compiler already logged errors
		(inputs, analysis)
	}

	def loadPlugins(dir: File, classpath: Seq[Attributed[File]], loader: ClassLoader): LoadedPlugins =
	{
		val (pluginNames, plugins) = if(classpath.isEmpty) (Nil, Nil) else {
			val names = getPluginNames(classpath, loader)
			(names, loadPlugins(loader, names) )
		}
		new LoadedPlugins(dir, classpath, loader, plugins, pluginNames)
	}
	def getPluginNames(classpath: Seq[Attributed[File]], loader: ClassLoader): Seq[String] =
		 ( binaryPlugins(loader) ++ (analyzed(classpath) flatMap findPlugins) ).distinct

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
	def importAllRoot(values: Seq[String]) = importAll(values map rootedName)
	def rootedName(s: String) = if(s contains '.') "_root_." + s else s
		
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
		new SessionSettings(structure.root, rootProjectMap(structure.units), structure.settings, Map.empty, rootEval)
		
	def rootProjectMap(units: Map[URI, LoadedBuildUnit]): Map[URI, String] =
	{
		val getRoot = getRootProject(units)
		units.keys.map(uri => (uri, getRoot(uri))).toMap
	}

	def defaultEvalOptions: Seq[String] = Nil
	def baseImports = "import sbt._, Process._, Keys._" :: Nil

	final class EvaluatedConfigurations(val eval: Eval, val settings: Seq[Setting[_]])
	final class LoadedDefinitions(val base: File, val target: Seq[File], val loader: ClassLoader, val builds: Seq[Build], val buildNames: Seq[String])
	final class LoadedPlugins(val base: File, val fullClasspath: Seq[Attributed[File]], val loader: ClassLoader, val plugins: Seq[Setting[_]], val pluginNames: Seq[String])
	{
		def classpath = data(fullClasspath)
	}
	final class BuildUnit(val uri: URI, val localBase: File, val definitions: LoadedDefinitions, val plugins: LoadedPlugins)
	{
		override def toString = if(uri.getScheme == "file") localBase.toString else (uri + " (locally: " + localBase +")")
	}
	
	final class LoadedBuild(val root: URI, val units: Map[URI, LoadedBuildUnit])
	{
		checkCycles(units)
		def allProjectRefs: Seq[(ProjectRef, ResolvedProject)] = for( (uri, unit) <- units.toSeq; (id, proj) <- unit.defined ) yield ProjectRef(uri, id) -> proj
	}
	def checkCycles(units: Map[URI, LoadedBuildUnit])
	{
		def getRef(pref: ProjectRef) = units(pref.build).defined(pref.project)
		def deps(proj: ResolvedProject)(base: ResolvedProject => Seq[ProjectRef]): Seq[ResolvedProject]  =  Dag.topologicalSort(proj)(p => base(p) map getRef)
		 // check for cycles
		for( (_, lbu) <- units; proj <- lbu.defined.values) {
			deps(proj)(_.dependencies.map(_.project))
			deps(proj)(_.delegates)
			deps(proj)(_.aggregate)
		}
	}
	final class PartBuild(val root: URI, val units: Map[URI, PartBuildUnit])
	sealed trait BuildUnitBase { def rootProjects: Seq[String]; def buildSettings: Seq[Setting[_]] }
	final class PartBuildUnit(val unit: BuildUnit, val defined: Map[String, Project], val rootProjects: Seq[String], val buildSettings: Seq[Setting[_]]) extends BuildUnitBase
	{
		def resolve(f: Project => ResolvedProject): LoadedBuildUnit = new LoadedBuildUnit(unit, defined mapValues f toMap, rootProjects, buildSettings)
		def resolveRefs(f: ProjectReference => ProjectRef): LoadedBuildUnit = resolve(_ resolve f)
	}
	final class LoadedBuildUnit(val unit: BuildUnit, val defined: Map[String, ResolvedProject], val rootProjects: Seq[String], val buildSettings: Seq[Setting[_]]) extends BuildUnitBase
	{
		assert(!rootProjects.isEmpty, "No root projects defined for build unit " + unit)
		def localBase = unit.localBase
		def classpath: Seq[File] = unit.definitions.target ++ unit.plugins.classpath
		def loader = unit.definitions.loader
		def imports = getImports(unit)
		override def toString = unit.toString
	}
	def getImports(unit: BuildUnit) = baseImports ++ importAllRoot(unit.plugins.pluginNames ++ unit.definitions.buildNames)

	def referenced[PR <: ProjectReference](definitions: Seq[ProjectDefinition[PR]]): Seq[PR] = definitions flatMap { _.referenced }
	
	final class BuildStructure(val units: Map[URI, LoadedBuildUnit], val root: URI, val settings: Seq[Setting[_]], val data: Settings[Scope], val index: StructureIndex, val streams: State => Streams, val delegates: Scope => Seq[Scope], val scopeLocal: ScopeLocal)
	{
		val rootProject: URI => String = Load getRootProject units
		def allProjects: Seq[ResolvedProject] = units.values.flatMap(_.defined.values).toSeq
		def allProjects(build: URI): Seq[ResolvedProject] = units(build).defined.values.toSeq
		def allProjectRefs: Seq[ProjectRef] = units.toSeq flatMap { case (build, unit) => refs(build, unit.defined.values.toSeq) }
		def allProjectRefs(build: URI): Seq[ProjectRef] = refs(build, allProjects(build))
		private[this] def refs(build: URI, projects: Seq[ResolvedProject]): Seq[ProjectRef] = projects.map { p => ProjectRef(build, p.id) }
	}
	final case class LoadBuildConfiguration(stagingDirectory: File, classpath: Seq[Attributed[File]], loader: ClassLoader, compilers: Compilers, evalPluginDef: (BuildStructure, State) => Seq[Attributed[File]], definesClass: DefinesClass, delegates: LoadedBuild => Scope => Seq[Scope], scopeLocal: ScopeLocal, injectSettings: InjectSettings, globalPlugin: Option[GlobalPlugin], log: Logger)
	{
		lazy val (globalPluginClasspath, globalPluginLoader) = pluginDefinitionLoader(this, Load.globalPluginClasspath(globalPlugin))
		lazy val globalPluginNames = if(globalPluginClasspath.isEmpty) Nil else getPluginNames(globalPluginClasspath, globalPluginLoader)
	}
	final case class InjectSettings(global: Seq[Setting[_]], project: Seq[Setting[_]], projectLoaded: ClassLoader => Seq[Setting[_]])

	// information that is not original, but can be reconstructed from the rest of BuildStructure
	final class StructureIndex(val keyMap: Map[String, AttributeKey[_]], val taskToKey: Map[Task[_], ScopedKey[Task[_]]], val triggers: Triggers[Task], val keyIndex: KeyIndex)
}
