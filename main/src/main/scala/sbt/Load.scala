/* sbt -- Simple Build Tool
 * Copyright 2011 Mark Harrah
 */
package sbt

	import java.io.File
	import java.net.{URI,URL}
	import compiler.{Eval,EvalImports}
	import xsbt.api.{Discovered,Discovery}
	import xsbti.compile.CompileOrder
	import classpath.ClasspathUtilities
	import scala.annotation.tailrec
	import collection.mutable
	import Compiler.{Compilers,Inputs}
	import inc.{FileValueCache, Locate}
	import Project.{inScope,makeSettings}
	import Def.{ScopedKey, ScopeLocal, Setting}
	import Keys.{appConfiguration, baseDirectory, configuration, fullResolvers, fullClasspath, pluginData, streams, thisProject, thisProjectRef, update}
	import Keys.{exportedProducts, isDummy, loadedBuild, resolvedScoped, taskDefinitionKey}
	import tools.nsc.reporters.ConsoleReporter
	import Build.analyzed
	import Attributed.data
	import Scope.{GlobalScope, ThisScope}
	import Types.const
	import BuildPaths._
	import BuildStreams._
	import Locate.DefinesClass

object Load
{
	// note that there is State passed in but not pulled out
	def defaultLoad(state: State, baseDirectory: File, log: Logger, isPlugin: Boolean = false, topLevelExtras: List[URI] = Nil): (() => Eval, sbt.BuildStructure) =
	{
		val globalBase = getGlobalBase(state)
		val base = baseDirectory.getCanonicalFile
		val definesClass = FileValueCache(Locate.definesClass _)
		val rawConfig = defaultPreGlobal(state, base, definesClass.get, globalBase, log)
		val config0 = defaultWithGlobal(state, base, rawConfig, globalBase, log)
		val config = if(isPlugin) enableSbtPlugin(config0) else config0.copy(extraBuilds = topLevelExtras)
		val result = apply(base, state, config)
		definesClass.clear()
		result
	}
	def defaultPreGlobal(state: State, baseDirectory: File, definesClass: DefinesClass, globalBase: File, log: Logger): sbt.LoadBuildConfiguration =
	{
		val provider = state.configuration.provider
		val scalaProvider = provider.scalaProvider
		val stagingDirectory = getStagingDirectory(state, globalBase).getCanonicalFile
		val loader = getClass.getClassLoader
		val classpath = Attributed.blankSeq(provider.mainClasspath ++ scalaProvider.jars)
		val compilers = Compiler.compilers(ClasspathOptions.boot)(state.configuration, log)
		val evalPluginDef = EvaluateTask.evalPluginDef(log) _
		val delegates = defaultDelegates
		val initialID = baseDirectory.getName
		val pluginMgmt = PluginManagement(loader)
		val inject = InjectSettings(injectGlobal(state), Nil, const(Nil))
		new sbt.LoadBuildConfiguration(stagingDirectory, classpath, loader, compilers, evalPluginDef, definesClass, delegates,
			EvaluateTask.injectStreams, pluginMgmt, inject, None, Nil, log)
	}
	def injectGlobal(state: State): Seq[Setting[_]] =
		(appConfiguration in GlobalScope :== state.configuration) +:
		EvaluateTask.injectSettings
	def defaultWithGlobal(state: State, base: File, rawConfig: sbt.LoadBuildConfiguration, globalBase: File, log: Logger): sbt.LoadBuildConfiguration =
	{
		val globalPluginsDir = getGlobalPluginsDirectory(state, globalBase)
		val withGlobal = loadGlobal(state, base, globalPluginsDir, rawConfig)
		val globalSettings = configurationSources(getGlobalSettingsDirectory(state, globalBase))
		loadGlobalSettings(base, globalBase, globalSettings, withGlobal)
	}

	def loadGlobalSettings(base: File, globalBase: File, files: Seq[File], config: sbt.LoadBuildConfiguration): sbt.LoadBuildConfiguration =
	{
		val compiled: ClassLoader => Seq[Setting[_]]  =
			if(files.isEmpty || base == globalBase) const(Nil) else buildGlobalSettings(globalBase, files, config)
		config.copy(injectSettings = config.injectSettings.copy(projectLoaded = compiled))
	}
	def buildGlobalSettings(base: File, files: Seq[File], config: sbt.LoadBuildConfiguration): ClassLoader => Seq[Setting[_]] =
	{	
		val eval = mkEval(data(config.classpath), base, defaultEvalOptions)
		val imports = BuildUtil.baseImports ++ BuildUtil.importAllRoot(config.globalPluginNames)
		loader => EvaluateConfigurations(eval, files, imports)(loader).settings
	}
	def loadGlobal(state: State, base: File, global: File, config: sbt.LoadBuildConfiguration): sbt.LoadBuildConfiguration =
		if(base != global && global.exists) {
			val gp = GlobalPlugin.load(global, state, config)
			val pm = setGlobalPluginLoader(gp, config.pluginManagement)
			val cp = (gp.data.fullClasspath ++ config.classpath).distinct
			config.copy(globalPlugin = Some(gp), pluginManagement = pm, classpath = cp)
		} else
			config

	private[this] def setGlobalPluginLoader(gp: GlobalPlugin, pm: PluginManagement): PluginManagement =
	{
		val newLoader = ClasspathUtilities.toLoader(data(gp.data.fullClasspath), pm.initialLoader)
		pm.copy(initialLoader = newLoader)
	}

	def defaultDelegates: sbt.LoadedBuild => Scope => Seq[Scope] = (lb: sbt.LoadedBuild) => {
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
	def configInherit(lb: sbt.LoadedBuild, ref: ResolvedReference, config: ConfigKey, rootProject: URI => String): Seq[ConfigKey] =
		ref match
		{
			case pr: ProjectRef => configInheritRef(lb, pr, config)
			case BuildRef(uri) => configInheritRef(lb, ProjectRef(uri, rootProject(uri)), config)
		}
	def configInheritRef(lb: sbt.LoadedBuild, ref: ProjectRef, config: ConfigKey): Seq[ConfigKey] =
		configurationOpt(lb.units, ref.build, ref.project, config).toList.flatMap(_.extendsConfigs).map(c => ConfigKey(c.name))

	def projectInherit(lb: sbt.LoadedBuild, ref: ProjectRef): Seq[ProjectRef] =
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
	def apply(rootBase: File, s: State, config: sbt.LoadBuildConfiguration): (() => Eval, sbt.BuildStructure) =
	{
		// load, which includes some resolution, but can't fill in project IDs yet, so follow with full resolution
		val loaded = resolveProjects(load(rootBase, s, config))
		val projects = loaded.units
		lazy val rootEval = lazyEval(loaded.units(loaded.root).unit)
		val settings = finalTransforms(buildConfigurations(loaded, getRootProject(projects), config.injectSettings))
		val delegates = config.delegates(loaded)
		val data = Def.make(settings)(delegates, config.scopeLocal, Project.showLoadingKey( loaded ) )
		Project.checkTargets(data) foreach error
		val index = structureIndex(data, settings, loaded.extra(data))
		val streams = mkStreams(projects, loaded.root, data)
		(rootEval, new sbt.BuildStructure(projects, loaded.root, settings, data, index, streams, delegates, config.scopeLocal))
	}

	// map dependencies on the special tasks:
	// 1. the scope of 'streams' is the same as the defining key and has the task axis set to the defining key
	// 2. the defining key is stored on constructed tasks: used for error reporting among other things
	// 3. resolvedScoped is replaced with the defining key as a value
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
				case _ => None
			}
		}
		ss.map(s => s mapConstant setResolved(s.key) mapReferenced mapSpecial(s.key) mapInit setDefining )
	}
	def setDefinitionKey[T](tk: Task[T], key: ScopedKey[_]): Task[T] =
		if(isDummy(tk)) tk else Task(tk.info.set(Keys.taskDefinitionKey, key), tk.work)

	def structureIndex(data: Settings[Scope], settings: Seq[Setting[_]], extra: KeyIndex => BuildUtil[_]): sbt.StructureIndex =
	{
		val keys = Index.allKeys(settings)
		val attributeKeys = Index.attributeKeys(data) ++ keys.map(_.key)
		val scopedKeys = keys ++ data.allKeys( (s,k) => ScopedKey(s,k))
		val keyIndex = KeyIndex(scopedKeys)
		val aggIndex = KeyIndex.aggregate(scopedKeys, extra(keyIndex))
		new sbt.StructureIndex(Index.stringToKeyMap(attributeKeys), Index.taskToKeyMap(data), Index.triggers(data), keyIndex, aggIndex)
	}

		// Reevaluates settings after modifying them.  Does not recompile or reload any build components.
	def reapply(newSettings: Seq[Setting[_]], structure: sbt.BuildStructure)(implicit display: Show[ScopedKey[_]]): sbt.BuildStructure =
	{
		val transformed = finalTransforms(newSettings)
		val newData = makeSettings(transformed, structure.delegates, structure.scopeLocal)
		val newIndex = structureIndex(newData, transformed, index => BuildUtil(structure.root, structure.units, index, newData))
		val newStreams = mkStreams(structure.units, structure.root, newData)
		new sbt.BuildStructure(units = structure.units, root = structure.root, settings = transformed, data = newData, index = newIndex, streams = newStreams, delegates = structure.delegates, scopeLocal = structure.scopeLocal)
	}

	def isProjectThis(s: Setting[_]) = s.key.scope.project match { case This | Select(ThisProject) => true; case _ => false }
	def buildConfigurations(loaded: sbt.LoadedBuild, rootProject: URI => String, injectSettings: InjectSettings): Seq[Setting[_]] =
	{
		((loadedBuild in GlobalScope :== loaded) +:
		transformProjectOnly(loaded.root, rootProject, injectSettings.global)) ++ 
		inScope(GlobalScope)( pluginGlobalSettings(loaded) ) ++
		loaded.units.toSeq.flatMap { case (uri, build) =>
			val plugins = build.unit.plugins.plugins
			val pluginBuildSettings = plugins.flatMap(_.buildSettings)
			val pluginNotThis = plugins.flatMap(_.settings) filterNot isProjectThis
			val projectSettings = build.defined flatMap { case (id, project) =>
				val ref = ProjectRef(uri, id)
				val defineConfig: Seq[Setting[_]] = for(c <- project.configurations) yield ( (configuration in (ref, ConfigKey(c.name))) :== c)
				val builtin: Seq[Setting[_]] = (thisProject :== project) +: (thisProjectRef :== ref) +: defineConfig
				val settings = builtin ++ project.settings ++ injectSettings.project
				// map This to thisScope, Select(p) to mapRef(uri, rootProject, p)
				transformSettings(projectScope(ref), uri, rootProject, settings)
			}
			val buildScope = Scope(Select(BuildRef(uri)), Global, Global, Global)
			val buildBase = baseDirectory :== build.localBase
			val buildSettings = transformSettings(buildScope, uri, rootProject, pluginNotThis ++ pluginBuildSettings ++ (buildBase +: build.buildSettings))
			buildSettings ++ projectSettings
		}
	}
	def pluginGlobalSettings(loaded: sbt.LoadedBuild): Seq[Setting[_]] =
		loaded.units.toSeq flatMap { case (_, build) =>
			build.unit.plugins.plugins flatMap { _.globalSettings }
		}

	@deprecated("No longer used.", "0.13.0")
	def extractSettings(plugins: Seq[Plugin]): (Seq[Setting[_]], Seq[Setting[_]], Seq[Setting[_]]) =
		(plugins.flatMap(_.settings), plugins.flatMap(_.projectSettings), plugins.flatMap(_.buildSettings))

	def transformProjectOnly(uri: URI, rootProject: URI => String, settings: Seq[Setting[_]]): Seq[Setting[_]] =
		Project.transform(Scope.resolveProject(uri, rootProject), settings)
	def transformSettings(thisScope: Scope, uri: URI, rootProject: URI => String, settings: Seq[Setting[_]]): Seq[Setting[_]] =
		Project.transform(Scope.resolveScope(thisScope, uri, rootProject), settings)
	def projectScope(project: Reference): Scope  =  Scope(Select(project), Global, Global, Global)
	
	def lazyEval(unit: sbt.BuildUnit): () => Eval =
	{
		lazy val eval = mkEval(unit)
		() => eval
	}
	def mkEval(unit: sbt.BuildUnit): Eval = mkEval(unit.definitions, unit.plugins, Nil)
	def mkEval(defs: sbt.LoadedDefinitions, plugs: sbt.LoadedPlugins, options: Seq[String]): Eval =
		mkEval(defs.target ++ plugs.classpath, defs.base, options)
	def mkEval(classpath: Seq[File], base: File, options: Seq[String]): Eval =
		new Eval(options, classpath, s => new ConsoleReporter(s), Some(evalOutputDirectory(base)))

	def configurations(srcs: Seq[File], eval: () => Eval, imports: Seq[String]): ClassLoader => LoadedSbtFile =
		if(srcs.isEmpty) const(LoadedSbtFile.empty) else EvaluateConfigurations(eval(), srcs, imports)

	def load(file: File, s: State, config: sbt.LoadBuildConfiguration): sbt.PartBuild =
		load(file, builtinLoader(s, config.copy(pluginManagement = config.pluginManagement.shift, extraBuilds = Nil)), config.extraBuilds.toList )
	def builtinLoader(s: State, config: sbt.LoadBuildConfiguration): BuildLoader =
	{
		val fail = (uri: URI) => sys.error("Invalid build URI (no handler available): " + uri)
		val resolver = (info: BuildLoader.ResolveInfo) => RetrieveUnit(info)
		val build = (info: BuildLoader.BuildInfo) => Some(() => loadUnit(info.uri, info.base, info.state, info.config))
		val components = BuildLoader.components(resolver, build, full = BuildLoader.componentLoader)
		BuildLoader(components, fail, s, config)
	}
	def load(file: File, loaders: BuildLoader, extra: List[URI]): sbt.PartBuild = loadURI(IO.directoryURI(file), loaders, extra)
	def loadURI(uri: URI, loaders: BuildLoader, extra: List[URI]): sbt.PartBuild =
	{
		IO.assertAbsolute(uri)
		val (referenced, map, newLoaders) = loadAll(uri :: extra, Map.empty, loaders, Map.empty)
		checkAll(referenced, map)
		val build = new sbt.PartBuild(uri, map)
		newLoaders transformAll build
	}
	def addOverrides(unit: sbt.BuildUnit, loaders: BuildLoader): BuildLoader =
		loaders updatePluginManagement PluginManagement.extractOverrides(unit.plugins.fullClasspath)

	def addResolvers(unit: sbt.BuildUnit, isRoot: Boolean, loaders: BuildLoader): BuildLoader =
		unit.definitions.builds.flatMap(_.buildLoaders) match
		{
			case Nil => loaders
			case x :: xs =>
				import Alternatives._
				val resolver = (x /: xs){ _ | _ }
				if(isRoot) loaders.setRoot(resolver) else loaders.addNonRoot(unit.uri, resolver)
		}

	def loaded(unit: sbt.BuildUnit): (sbt.PartBuildUnit, List[ProjectReference]) =
	{
		val defined = projects(unit)
		if(defined.isEmpty) sys.error("No projects defined in build unit " + unit)

		// since base directories are resolved at this point (after 'projects'),
		//   we can compare Files instead of converting to URIs
		def isRoot(p: Project) = p.base == unit.localBase

		val externals = referenced(defined).toList
		val explicitRoots = unit.definitions.builds.flatMap(_.rootProject)
		val projectsInRoot = if(explicitRoots.isEmpty) defined.filter(isRoot) else explicitRoots
		val rootProjects = if(projectsInRoot.isEmpty) defined.head :: Nil else projectsInRoot
		(new sbt.PartBuildUnit(unit, defined.map(d => (d.id, d)).toMap, rootProjects.map(_.id), buildSettings(unit)), externals)
	}
	def buildSettings(unit: sbt.BuildUnit): Seq[Setting[_]] =
	{
		val buildScope = GlobalScope.copy(project = Select(BuildRef(unit.uri)))
		val resolve = Scope.resolveBuildScope(buildScope, unit.uri)
		Project.transform(resolve, unit.definitions.builds.flatMap(_.settings))
	}

	@tailrec def loadAll(bases: List[URI], references: Map[URI, List[ProjectReference]], loaders: BuildLoader, builds: Map[URI, sbt.PartBuildUnit]): (Map[URI, List[ProjectReference]], Map[URI, sbt.PartBuildUnit], BuildLoader) =
		bases match
		{
			case b :: bs =>
				if(builds contains b)
					loadAll(bs, references, loaders, builds)
				else
				{
					val (loadedBuild, refs) = loaded(loaders(b))
					checkBuildBase(loadedBuild.unit.localBase)
					val newLoader = addOverrides(loadedBuild.unit, addResolvers(loadedBuild.unit, builds.isEmpty, loaders))
					// it is important to keep the load order stable, so we sort the remaining URIs
					val remainingBases = (refs.flatMap(Reference.uri) reverse_::: bs).sorted
					loadAll(remainingBases, references.updated(b, refs), newLoader, builds.updated(b, loadedBuild))
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
			sys.error("Not a directory: " + base)
		else if(!base.exists)
			IO createDirectory base
	}
	def resolveAll(builds: Map[URI, sbt.PartBuildUnit]): Map[URI, sbt.LoadedBuildUnit] =
	{
		val rootProject = getRootProject(builds)
		builds map { case (uri,unit) =>
			(uri, unit.resolveRefs( ref => Scope.resolveProjectRef(uri, rootProject, ref) ))
		} toMap;
	}
	def checkAll(referenced: Map[URI, List[ProjectReference]], builds: Map[URI, sbt.PartBuildUnit])
	{
		val rootProject = getRootProject(builds)
		for( (uri, refs) <- referenced; ref <- refs)
		{
			val ProjectRef(refURI, refID) = Scope.resolveProjectRef(uri, rootProject, ref)
			val loadedUnit = builds(refURI)
			if(! (loadedUnit.defined contains refID) ) {
				val projectIDs = loadedUnit.defined.keys.toSeq.sorted
				sys.error("No project '" + refID + "' in '" + refURI + "'.\nValid project IDs: " + projectIDs.mkString(", "))
			}
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
	def resolveProjects(loaded: sbt.PartBuild): sbt.LoadedBuild =
	{
		val rootProject = getRootProject(loaded.units)
		new sbt.LoadedBuild(loaded.root, loaded.units map { case (uri, unit) =>
			IO.assertAbsolute(uri)
			(uri, resolveProjects(uri, unit, rootProject))
		})
	}
	def resolveProjects(uri: URI, unit: sbt.PartBuildUnit, rootProject: URI => String): sbt.LoadedBuildUnit =
	{
		IO.assertAbsolute(uri)
		val resolve = (_: Project).resolve(ref => Scope.resolveProjectRef(uri, rootProject, ref))
		new sbt.LoadedBuildUnit(unit.unit, unit.defined mapValues resolve toMap, unit.rootProjects, unit.buildSettings)
	}
	def projects(unit: sbt.BuildUnit): Seq[Project] =
	{
		// we don't have the complete build graph loaded, so we don't have the rootProject function yet.
		//  Therefore, we use resolveProjectBuild instead of resolveProjectRef.  After all builds are loaded, we can fully resolve ProjectReferences.
		val resolveBuild = (_: Project).resolveBuild(ref => Scope.resolveProjectBuild(unit.uri, ref))
		// although the default loader will resolve the project base directory, other loaders may not, so run resolveBase here as well
		unit.definitions.projects.map(resolveBuild compose resolveBase(unit.localBase))
	}
	def getRootProject(map: Map[URI, sbt.BuildUnitBase]): URI => String =
		uri => getBuild(map, uri).rootProjects.headOption getOrElse emptyBuild(uri)
	def getConfiguration(map: Map[URI, sbt.LoadedBuildUnit], uri: URI, id: String, conf: ConfigKey): Configuration =
		configurationOpt(map, uri, id, conf) getOrElse noConfiguration(uri, id, conf.name)
	def configurationOpt(map: Map[URI, sbt.LoadedBuildUnit], uri: URI, id: String, conf: ConfigKey): Option[Configuration] =
		getProject(map, uri, id).configurations.find(_.name == conf.name)

	def getProject(map: Map[URI, sbt.LoadedBuildUnit], uri: URI, id: String): ResolvedProject =
		getBuild(map, uri).defined.getOrElse(id, noProject(uri, id))
	def getBuild[T](map: Map[URI, T], uri: URI): T =
		map.getOrElse(uri, noBuild(uri))

	def emptyBuild(uri: URI) = sys.error("No root project defined for build unit '" + uri + "'")
	def noBuild(uri: URI) = sys.error("Build unit '" + uri + "' not defined.")
	def noProject(uri: URI, id: String) = sys.error("No project '" + id + "' defined in '" + uri + "'.")
	def noConfiguration(uri: URI, id: String, conf: String) = sys.error("No configuration '" + conf + "' defined in project '" + id + "' in '" + uri +"'")

	def loadUnit(uri: URI, localBase: File, s: State, config: sbt.LoadBuildConfiguration): sbt.BuildUnit =
	{
		val normBase = localBase.getCanonicalFile
		val defDir = projectStandard(normBase)

		val plugs = plugins(defDir, s, config.copy(pluginManagement = config.pluginManagement.forPlugin))
		val defNames = analyzed(plugs.fullClasspath) flatMap findDefinitions
		val defsScala = if(defNames.isEmpty) Nil else loadDefinitions(plugs.loader, defNames)
		val imports = BuildUtil.getImports(plugs.pluginNames, defNames)

		lazy val eval = mkEval(plugs.classpath, defDir, Nil)
		val initialProjects = defsScala.flatMap(b => projectsFromBuild(b, normBase))

		val memoSettings = new mutable.HashMap[File, LoadedSbtFile]
		def loadProjects(ps: Seq[Project]) = loadTransitive(ps, normBase, imports, plugs, () => eval, config.injectSettings, Nil, memoSettings)
		val loadedProjectsRaw = loadProjects(initialProjects)
		val hasRoot = loadedProjectsRaw.exists(_.base == normBase) || defsScala.exists(_.rootProject.isDefined)
		val (loadedProjects, defaultBuildIfNone) =
			if(hasRoot)
				(loadedProjectsRaw, Build.defaultEmpty)
			else {
				val existingIDs = loadedProjectsRaw.map(_.id)
				val refs = existingIDs.map(id => ProjectRef(uri, id))
				val defaultID = autoID(normBase, config.pluginManagement.context, existingIDs)
				val b = Build.defaultAggregated(defaultID, refs)
				val defaultProjects = loadProjects(projectsFromBuild(b, normBase))
				(defaultProjects ++ loadedProjectsRaw, b)
			}

		val defs = if(defsScala.isEmpty) defaultBuildIfNone :: Nil else defsScala
		val loadedDefs = new sbt.LoadedDefinitions(defDir, Nil, plugs.loader, defs, loadedProjects, defNames)
		new sbt.BuildUnit(uri, normBase, loadedDefs, plugs)
	}

	private[this] def autoID(localBase: File, context: PluginManagement.Context, existingIDs: Seq[String]): String = 
	{
		def normalizeID(f: File) = Project.normalizeProjectID(f.getName) match {
			case Right(id) => id
			case Left(msg) => error(autoIDError(f, msg))
		}
		def nthParentName(f: File, i: Int): String =
			if(f eq null) Build.defaultID(localBase) else if(i <= 0) normalizeID(f) else nthParentName(f.getParentFile, i - 1)
		val pluginDepth = context.pluginProjectDepth
		val postfix = "-build" * pluginDepth
		val idBase = if(context.globalPluginProject) "global-plugins" else nthParentName(localBase, pluginDepth)
		val tryID = idBase + postfix
		if(existingIDs.contains(tryID)) Build.defaultID(localBase) else tryID
	}

	private[this] def autoIDError(base: File, reason: String): String = 
		"Could not derive root project ID from directory " + base.getAbsolutePath + ":\n" +
			reason + "\nRename the directory or explicitly define a root project."

	private[this] def projectsFromBuild(b: Build, base: File): Seq[Project] = 
		b.projectDefinitions(base).map(resolveBase(base))

	private[this] def loadTransitive(newProjects: Seq[Project], buildBase: File, imports: Seq[String], plugins: sbt.LoadedPlugins, eval: () => Eval, injectSettings: InjectSettings, acc: Seq[Project], memoSettings: mutable.Map[File, LoadedSbtFile]): Seq[Project] =
	{
		def loadSbtFiles(auto: AddSettings, base: File): LoadedSbtFile =
			loadSettings(auto, base, imports, plugins, eval, injectSettings, memoSettings)
		def loadForProjects = newProjects map { project =>
			val loadedSbtFiles = loadSbtFiles(project.auto, project.base)
			val transformed = project.copy(settings = (project.settings: Seq[Setting[_]]) ++ loadedSbtFiles.settings)
			(transformed, loadedSbtFiles.projects)
		}
		def defaultLoad = loadSbtFiles(AddSettings.defaultSbtFiles, buildBase).projects
		val (nextProjects, loadedProjects) =
			if(newProjects.isEmpty) // load the .sbt files in the root directory to look for Projects
				(defaultLoad, acc)
			else {
				val (transformed, np) = loadForProjects.unzip
				(np.flatten, transformed ++ acc)
			}

		if(nextProjects.isEmpty)
			loadedProjects
		else
			loadTransitive(nextProjects, buildBase, imports, plugins, eval, injectSettings, loadedProjects, memoSettings)
	}
		
	private[this] def loadSettings(auto: AddSettings, projectBase: File, buildImports: Seq[String], loadedPlugins: sbt.LoadedPlugins, eval: ()=>Eval, injectSettings: InjectSettings, memoSettings: mutable.Map[File, LoadedSbtFile]): LoadedSbtFile =
	{
		lazy val defaultSbtFiles = configurationSources(projectBase)
		def settings(ss: Seq[Setting[_]]) = new LoadedSbtFile(ss, Nil, Nil)
		val loader = loadedPlugins.loader

		def merge(ls: Seq[LoadedSbtFile]): LoadedSbtFile = (LoadedSbtFile.empty /: ls) { _ merge _ }
		def loadSettings(fs: Seq[File]): LoadedSbtFile =
			merge( fs.sortBy(_.getName).map(memoLoadSettingsFile) )
		def memoLoadSettingsFile(src: File): LoadedSbtFile = memoSettings.get(src) getOrElse {
			val lf = loadSettingsFile(src)
			memoSettings.put(src, lf.clearProjects) // don't load projects twice
			lf
		}
		def loadSettingsFile(src: File): LoadedSbtFile =
			EvaluateConfigurations.evaluateSbtFile(eval(), src, IO.readLines(src), buildImports, 0)(loader)

			import AddSettings.{User,SbtFiles,DefaultSbtFiles,Plugins,Sequence}
		def expand(auto: AddSettings): LoadedSbtFile = auto match {
			case User => settings(injectSettings.projectLoaded(loader))
			case sf: SbtFiles => loadSettings( sf.files.map(f => IO.resolve(projectBase, f)))
			case sf: DefaultSbtFiles => loadSettings( defaultSbtFiles.filter(sf.include))
			case f: Plugins => settings(loadedPlugins.plugins.filter(f.include).flatMap(p => p.settings.filter(isProjectThis) ++ p.projectSettings))
			case q: Sequence => (LoadedSbtFile.empty /: q.sequence) { (b,add) => b.merge( expand(add) ) }
		}
		expand(auto)
	}

	@deprecated("No longer used.", "0.13.0")
	def globalPluginClasspath(globalPlugin: Option[GlobalPlugin]): Seq[Attributed[File]] =
		globalPlugin match
		{
			case Some(cp) => cp.data.fullClasspath
			case None => Nil
		}
	val autoPluginSettings: Seq[Setting[_]] = inScope(GlobalScope in LocalRootProject)(Seq(
		Keys.sbtPlugin :== true,
		pluginData <<= (exportedProducts in Configurations.Runtime, fullClasspath in Configurations.Runtime, update, fullResolvers) map ( (prod, cp, rep, rs) =>
			PluginData(removeEntries(cp, prod), prod, Some(rs), Some(rep))
		),
		Keys.onLoadMessage <<= Keys.baseDirectory("Loading project definition from " + _)
	))
	private[this] def removeEntries(cp: Seq[Attributed[File]], remove: Seq[Attributed[File]]): Seq[Attributed[File]] =
	{
		val files = data(remove).toSet
		cp filter { f => !files.contains(f.data) }
	}

	def enableSbtPlugin(config: sbt.LoadBuildConfiguration): sbt.LoadBuildConfiguration =
		config.copy(injectSettings = config.injectSettings.copy(
			global = autoPluginSettings ++ config.injectSettings.global,
			project = config.pluginManagement.inject ++ config.injectSettings.project
		))
	def activateGlobalPlugin(config: sbt.LoadBuildConfiguration): sbt.LoadBuildConfiguration =
		config.globalPlugin match
		{
			case Some(gp) => config.copy(injectSettings = config.injectSettings.copy(project = gp.inject))
			case None => config
		}
	def plugins(dir: File, s: State, config: sbt.LoadBuildConfiguration): sbt.LoadedPlugins =
		if(hasDefinition(dir))
			buildPlugins(dir, s, enableSbtPlugin(activateGlobalPlugin(config)))
		else
			noPlugins(dir, config)

	def hasDefinition(dir: File) =
	{
		import Path._
		!(dir * -GlobFilter(DefaultTargetName)).get.isEmpty
	}
	def noPlugins(dir: File, config: sbt.LoadBuildConfiguration): sbt.LoadedPlugins =
		loadPluginDefinition(dir, config, PluginData(config.classpath, None, None))
	def buildPlugins(dir: File, s: State, config: sbt.LoadBuildConfiguration): sbt.LoadedPlugins =
		loadPluginDefinition(dir, config, buildPluginDefinition(dir, s, config))

	def loadPluginDefinition(dir: File, config: sbt.LoadBuildConfiguration, pluginData: PluginData): sbt.LoadedPlugins =
	{
		val (definitionClasspath, pluginLoader) = pluginDefinitionLoader(config, pluginData)
		loadPlugins(dir, pluginData.copy(dependencyClasspath = definitionClasspath), pluginLoader)
	}
	def pluginDefinitionLoader(config: sbt.LoadBuildConfiguration, dependencyClasspath: Seq[Attributed[File]]): (Seq[Attributed[File]], ClassLoader) =
		pluginDefinitionLoader(config, dependencyClasspath, Nil)
	def pluginDefinitionLoader(config: sbt.LoadBuildConfiguration, pluginData: PluginData): (Seq[Attributed[File]], ClassLoader) =
		pluginDefinitionLoader(config, pluginData.dependencyClasspath, pluginData.definitionClasspath)	
	def pluginDefinitionLoader(config: sbt.LoadBuildConfiguration, depcp: Seq[Attributed[File]], defcp: Seq[Attributed[File]]): (Seq[Attributed[File]], ClassLoader) =
	{
		val definitionClasspath =
			if(depcp.isEmpty)
				config.classpath
			else
				(depcp ++ config.classpath).distinct
		val pm = config.pluginManagement
		// only the dependencyClasspath goes in the common plugin class loader ...
		def addToLoader() = pm.loader add Path.toURLs(data(depcp))

		val parentLoader = if(depcp.isEmpty) pm.initialLoader else { addToLoader(); pm.loader }
		val pluginLoader =
			if(defcp.isEmpty)
				parentLoader
			else {
				// ... the build definition classes get their own loader so that they don't conflict with other build definitions (#511)
				ClasspathUtilities.toLoader(data(defcp), parentLoader)
			}
		(definitionClasspath, pluginLoader)
	}
	def buildPluginDefinition(dir: File, s: State, config: sbt.LoadBuildConfiguration): PluginData =
	{
		val (eval,pluginDef) = apply(dir, s, config)
		val pluginState = Project.setProject(Load.initialSession(pluginDef, eval), pluginDef, s)
		config.evalPluginDef(pluginDef, pluginState)
	}

	def loadDefinitions(loader: ClassLoader, defs: Seq[String]): Seq[Build] =
		defs map { definition => loadDefinition(loader, definition) }
	def loadDefinition(loader: ClassLoader, definition: String): Build =
		ModuleUtilities.getObject(definition, loader).asInstanceOf[Build]

	def loadPlugins(dir: File, data: PluginData, loader: ClassLoader): sbt.LoadedPlugins =
	{
		val (pluginNames, plugins) = if(data.classpath.isEmpty) (Nil, Nil) else {
			val names = getPluginNames(data.classpath, loader)
			val loaded =
				try loadPlugins(loader, names)
				catch { case e: LinkageError => incompatiblePlugins(data, e) }
			(names, loaded)
		}
		new sbt.LoadedPlugins(dir, data, loader, plugins, pluginNames)
	}
	private[this] def incompatiblePlugins(data: PluginData, t: LinkageError): Nothing =
	{
		val evicted = data.report.toList.flatMap(_.configurations.flatMap(_.evicted))
		val evictedModules = evicted map { id => (id.organization, id.name) } distinct ;
		val evictedStrings = evictedModules map { case (o,n) => o + ":" + n }
		val msgBase = "Binary incompatibility in plugins detected."
		val msgExtra = if(evictedStrings.isEmpty) "" else "\nNote that conflicts were resolved for some dependencies:\n\t" + evictedStrings.mkString("\n\t")
		throw new IncompatiblePluginsException(msgBase + msgExtra, t)
	}
	def getPluginNames(classpath: Seq[Attributed[File]], loader: ClassLoader): Seq[String] =
		 ( binaryPlugins(data(classpath), loader) ++ (analyzed(classpath) flatMap findPlugins) ).distinct

	def binaryPlugins(classpath: Seq[File], loader: ClassLoader): Seq[String] =
	{
		import collection.JavaConversions._
		loader.getResources("sbt/sbt.plugins").toSeq.filter(onClasspath(classpath)) flatMap { u =>
			IO.readLinesURL(u).map( _.trim).filter(!_.isEmpty)
		}
	}
	def onClasspath(classpath: Seq[File])(url: URL): Boolean =
		IO.urlAsFile(url) exists (classpath.contains _)

	def loadPlugins(loader: ClassLoader, pluginNames: Seq[String]): Seq[Plugin] =
		pluginNames.map(pluginName => loadPlugin(pluginName, loader))

	def loadPlugin(pluginName: String, loader: ClassLoader): Plugin =
		ModuleUtilities.getObject(pluginName, loader).asInstanceOf[Plugin]

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

	def initialSession(structure: sbt.BuildStructure, rootEval: () => Eval, s: State): SessionSettings = {
		val session = s get Keys.sessionSettings
		val currentProject = session map (_.currentProject) getOrElse Map.empty
		val currentBuild = session map (_.currentBuild) filter (uri => structure.units.keys exists (uri ==)) getOrElse structure.root
		new SessionSettings(currentBuild, projectMap(structure, currentProject), structure.settings, Map.empty, Nil, rootEval)
	}

	def initialSession(structure: sbt.BuildStructure, rootEval: () => Eval): SessionSettings =
		new SessionSettings(structure.root, projectMap(structure, Map.empty), structure.settings, Map.empty, Nil, rootEval)
		
	def projectMap(structure: sbt.BuildStructure, current: Map[URI, String]): Map[URI, String] =
	{
		val units = structure.units
		val getRoot = getRootProject(units)
		def project(uri: URI) = {
			current get uri filter {
				p => structure allProjects uri map (_.id) contains p
			} getOrElse getRoot(uri)
		}
		units.keys.map(uri => (uri, project(uri))).toMap
	}

	def defaultEvalOptions: Seq[String] = Nil

	@deprecated("Use BuildUtil.baseImports", "0.13.0")
	def baseImports = BuildUtil.baseImports
	@deprecated("Use BuildUtil.checkCycles", "0.13.0")
	def checkCycles(units: Map[URI, sbt.LoadedBuildUnit]): Unit = BuildUtil.checkCycles(units)
	@deprecated("Use BuildUtil.importAll", "0.13.0")
	def importAll(values: Seq[String]): Seq[String] = BuildUtil.importAll(values)
	@deprecated("Use BuildUtil.importAllRoot", "0.13.0")
	def importAllRoot(values: Seq[String]): Seq[String] = BuildUtil.importAllRoot(values)
	@deprecated("Use BuildUtil.rootedNames", "0.13.0")
	def rootedName(s: String): String = BuildUtil.rootedName(s)
	@deprecated("Use BuildUtil.getImports", "0.13.0")
	def getImports(unit: sbt.BuildUnit): Seq[String] = BuildUtil.getImports(unit)

	def referenced[PR <: ProjectReference](definitions: Seq[ProjectDefinition[PR]]): Seq[PR] = definitions flatMap { _.referenced }
	
	@deprecated("LoadedBuildUnit is now top-level", "0.13.0")
	type LoadedBuildUnit = sbt.LoadedBuildUnit

	@deprecated("BuildStructure is now top-level", "0.13.0")
	type BuildStructure = sbt.BuildStructure

	@deprecated("StructureIndex is now top-level", "0.13.0")
	type StructureIndex = sbt.StructureIndex

	@deprecated("LoadBuildConfiguration is now top-level", "0.13.0")
	type LoadBuildConfiguration = sbt.LoadBuildConfiguration
	@deprecated("LoadBuildConfiguration is now top-level", "0.13.0")	
	val LoadBuildConfiguration = sbt.LoadBuildConfiguration

	final class EvaluatedConfigurations(val eval: Eval, val settings: Seq[Setting[_]])
	final case class InjectSettings(global: Seq[Setting[_]], project: Seq[Setting[_]], projectLoaded: ClassLoader => Seq[Setting[_]])

	@deprecated("LoadedDefinitions is now top-level", "0.13.0")
	type LoadedDefinitions = sbt.LoadedDefinitions
	@deprecated("LoadedPlugins is now top-level", "0.13.0")
	type LoadedPlugins = sbt.LoadedPlugins
	@deprecated("BuildUnit is now top-level", "0.13.0")
	type BuildUnit = sbt.BuildUnit
	@deprecated("LoadedBuild is now top-level", "0.13.0")
	type LoadedBuild = sbt.LoadedBuild
	@deprecated("PartBuild is now top-level", "0.13.0")
	type PartBuild = sbt.PartBuild
	@deprecated("BuildUnitBase is now top-level", "0.13.0")
	type BuildUnitBase = sbt.BuildUnitBase
	@deprecated("PartBuildUnit is now top-level", "0.13.0")
	type PartBuildUnit = sbt.PartBuildUnit
	@deprecated("Use BuildUtil.apply", "0.13.0")
	def buildUtil(root: URI, units: Map[URI, sbt.LoadedBuildUnit], keyIndex: KeyIndex, data: Settings[Scope]): BuildUtil[ResolvedProject] = BuildUtil(root, units, keyIndex, data)
}

final case class LoadBuildConfiguration(stagingDirectory: File, classpath: Seq[Attributed[File]], loader: ClassLoader,
	compilers: Compilers, evalPluginDef: (sbt.BuildStructure, State) => PluginData, definesClass: DefinesClass,
	delegates: sbt.LoadedBuild => Scope => Seq[Scope], scopeLocal: ScopeLocal,
	pluginManagement: PluginManagement, injectSettings: Load.InjectSettings, globalPlugin: Option[GlobalPlugin], extraBuilds: Seq[URI],
	log: Logger)
{
	@deprecated("Use `classpath`.", "0.13.0")
	lazy val globalPluginClasspath = classpath
	@deprecated("Use `pluginManagement.initialLoader`.", "0.13.0")
	lazy val globalPluginLoader = pluginManagement.initialLoader
	lazy val globalPluginNames = if(classpath.isEmpty) Nil else Load.getPluginNames(classpath, pluginManagement.initialLoader)
}

final class IncompatiblePluginsException(msg: String, cause: Throwable) extends Exception(msg, cause)