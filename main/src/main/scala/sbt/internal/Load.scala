/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import sbt.BuildPaths._
import sbt.Def.{ ScopeLocal, ScopedKey, Setting, isDummy }
import sbt.Keys._
import sbt.Project.inScope
import sbt.Scope.GlobalScope
import sbt.SlashSyntax0._
import sbt.compiler.{ Eval, EvalReporter }
import sbt.internal.BuildStreams._
import sbt.internal.inc.classpath.ClasspathUtil
import sbt.internal.inc.{ ScalaInstance, ZincLmUtil, ZincUtil }
import sbt.internal.server.BuildServerEvalReporter
import sbt.internal.util.Attributed.data
import sbt.internal.util.Types.const
import sbt.internal.util.{ Attributed, Settings, ~> }
import sbt.io.{ GlobFilter, IO, Path }
import sbt.librarymanagement.ivy.{ InlineIvyConfiguration, IvyDependencyResolution, IvyPaths }
import sbt.librarymanagement.{ Configuration, Configurations, Resolver }
import sbt.nio.Settings
import sbt.util.{ Logger, Show }
import xsbti.compile.{ ClasspathOptionsUtil, Compilers }

import java.io.File
import java.net.URI
import scala.annotation.{ nowarn, tailrec }
import scala.collection.mutable
import scala.tools.nsc.reporters.ConsoleReporter

private[sbt] object Load {
  // note that there is State passed in but not pulled out
  def defaultLoad(
      state: State,
      baseDirectory: File,
      log: Logger,
      isPlugin: Boolean = false,
      topLevelExtras: List[URI] = Nil
  ): (() => Eval, BuildStructure) = {
    val (base, config) = timed("Load.defaultLoad until apply", log) {
      val globalBase = getGlobalBase(state)
      val base = baseDirectory.getCanonicalFile
      val rawConfig = defaultPreGlobal(state, base, globalBase, log)
      val config0 = defaultWithGlobal(state, base, rawConfig, globalBase)
      val config =
        if (isPlugin) enableSbtPlugin(config0) else config0.copy(extraBuilds = topLevelExtras)
      (base, config)
    }
    val result = apply(base, state, config)
    result
  }

  def defaultPreGlobal(
      state: State,
      baseDirectory: File,
      globalBase: File,
      log: Logger
  ): LoadBuildConfiguration = {
    val app = state.configuration
    val provider = app.provider
    val scalaProvider = app.provider.scalaProvider
    val launcher = scalaProvider.launcher
    val stagingDirectory = getStagingDirectory(state, globalBase).getCanonicalFile
    val loader = getClass.getClassLoader
    val classpath = Attributed.blankSeq(provider.mainClasspath ++ scalaProvider.jars)
    val ivyConfiguration =
      InlineIvyConfiguration()
        .withPaths(IvyPaths(baseDirectory, bootIvyHome(state.configuration)))
        .withResolvers(Resolver.combineDefaultResolvers(Vector.empty))
        .withLog(log)
    val dependencyResolution = IvyDependencyResolution(ivyConfiguration)
    val si = ScalaInstance(scalaProvider.version, scalaProvider.launcher)
    val zincDir = BuildPaths.getZincDirectory(state, globalBase)
    val classpathOptions = ClasspathOptionsUtil.boot
    val scalac = ZincLmUtil.scalaCompiler(
      scalaInstance = si,
      classpathOptions = classpathOptions,
      globalLock = launcher.globalLock,
      componentProvider = app.provider.components,
      secondaryCacheDir = Option(zincDir),
      dependencyResolution = dependencyResolution,
      compilerBridgeSource = ZincLmUtil.getDefaultBridgeSourceModule(scalaProvider.version),
      scalaJarsTarget = zincDir,
      state.get(BasicKeys.classLoaderCache),
      log = log
    )
    val compilers = ZincUtil.compilers(
      instance = si,
      classpathOptions = classpathOptions,
      javaHome = None,
      scalac
    )
    val evalPluginDef: (BuildStructure, State) => PluginData = EvaluateTask.evalPluginDef _
    val delegates = defaultDelegates
    val pluginMgmt = PluginManagement(loader)
    val inject = InjectSettings(injectGlobal(state), Nil, const(Nil))
    System.setProperty(
      "swoval.tmpdir",
      System.getProperty("swoval.tmpdir", globalBase.getAbsolutePath.toString)
    )
    LoadBuildConfiguration(
      stagingDirectory,
      classpath,
      loader,
      compilers,
      evalPluginDef,
      delegates,
      s => EvaluateTask.injectStreams(s) ++ Settings.inject(s),
      pluginMgmt,
      inject,
      None,
      Nil,
      log
    )
  }

  private def bootIvyHome(app: xsbti.AppConfiguration): Option[File] =
    try {
      Option(app.provider.scalaProvider.launcher.ivyHome)
    } catch {
      case _: NoSuchMethodError => None
    }

  def injectGlobal(state: State): Seq[Setting[_]] =
    ((GlobalScope / appConfiguration) :== state.configuration) +:
      LogManager.settingsLogger(state) +:
      EvaluateTask.injectSettings

  def defaultWithGlobal(
      state: State,
      base: File,
      rawConfig: LoadBuildConfiguration,
      globalBase: File,
  ): LoadBuildConfiguration = {
    val globalPluginsDir = getGlobalPluginsDirectory(state, globalBase)
    val withGlobal = loadGlobal(state, base, globalPluginsDir, rawConfig)
    val globalSettings = configurationSources(getGlobalSettingsDirectory(state, globalBase))
    loadGlobalSettings(base, globalBase, globalSettings, withGlobal)
  }

  def loadGlobalSettings(
      base: File,
      globalBase: File,
      files: Seq[File],
      config: LoadBuildConfiguration
  ): LoadBuildConfiguration = {
    val compiled: ClassLoader => Seq[Setting[_]] =
      if (files.isEmpty || base == globalBase) const(Nil)
      else buildGlobalSettings(globalBase, files, config)
    config.copy(injectSettings = config.injectSettings.copy(projectLoaded = compiled))
  }

  def buildGlobalSettings(
      base: File,
      files: Seq[File],
      config: LoadBuildConfiguration
  ): ClassLoader => Seq[Setting[_]] = {
    val eval = mkEval(data(config.globalPluginClasspath), base, defaultEvalOptions)

    val imports =
      BuildUtil.baseImports ++ config.detectedGlobalPlugins.imports

    loader => {
      val loaded = EvaluateConfigurations(eval, files, imports)(loader)
      // TODO - We have a potential leak of config-classes in the global directory right now.
      // We need to find a way to clean these safely, or at least warn users about
      // unused class files that could be cleaned when multiple sbt instances are not running.
      loaded.settings
    }
  }
  def loadGlobal(
      state: State,
      base: File,
      global: File,
      config: LoadBuildConfiguration
  ): LoadBuildConfiguration =
    if (base != global && global.exists) {
      val gp = GlobalPlugin.load(global, state, config)
      config.copy(globalPlugin = Some(gp))
    } else
      config

  def defaultDelegates: LoadedBuild => Scope => Seq[Scope] = (lb: LoadedBuild) => {
    val rootProject = getRootProject(lb.units)
    def resolveRef(project: Reference): ResolvedReference =
      Scope.resolveReference(lb.root, rootProject, project)
    Scope.delegates(
      lb.allProjectRefs,
      (_: ResolvedProject).configurations.map(c => ConfigKey(c.name)),
      resolveRef,
      rootProject,
      project => projectInherit(lb, project),
      (project, config) => configInherit(lb, project, config, rootProject),
      task => task.extend,
    )
  }

  def configInherit(
      lb: LoadedBuild,
      ref: ResolvedReference,
      config: ConfigKey,
      rootProject: URI => String
  ): Seq[ConfigKey] =
    ref match {
      case pr: ProjectRef => configInheritRef(lb, pr, config)
      case BuildRef(uri)  => configInheritRef(lb, ProjectRef(uri, rootProject(uri)), config)
    }

  def configInheritRef(lb: LoadedBuild, ref: ProjectRef, config: ConfigKey): Seq[ConfigKey] =
    configurationOpt(lb.units, ref.build, ref.project, config).toList
      .flatMap(_.extendsConfigs)
      .map(c => ConfigKey(c.name))

  def projectInherit(lb: LoadedBuild, ref: ProjectRef): Seq[ProjectRef] = {
    val _ = getProject(lb.units, ref.build, ref.project)
    Nil
  }

  // build, load, and evaluate all units.
  //  1) Compile all plugin definitions
  //  2) Evaluate plugin definitions to obtain and compile plugins and get the resulting classpath for the build definition
  //  3) Instantiate Plugins on that classpath
  //  4) Compile all build definitions using plugin classpath
  //  5) Load build definitions.
  //  6) Load all configurations using build definitions and plugins (their classpaths and loaded instances).
  //  7) Combine settings from projects, plugins, and configurations
  //  8) Evaluate settings
  def apply(
      rootBase: File,
      s: State,
      config: LoadBuildConfiguration
  ): (() => Eval, BuildStructure) = {
    val log = config.log

    // load, which includes some resolution, but can't fill in project IDs yet, so follow with full resolution
    val partBuild = timed("Load.apply: load", log) { load(rootBase, s, config) }
    val loaded = timed("Load.apply: resolveProjects", log) {
      resolveProjects(partBuild)
    }
    val projects = loaded.units
    lazy val rootEval = lazyEval(loaded.units(loaded.root).unit)
    val settings = timed("Load.apply: finalTransforms", log) {
      finalTransforms(buildConfigurations(loaded, getRootProject(projects), config.injectSettings))
    }
    val delegates = timed("Load.apply: config.delegates", log) {
      config.delegates(loaded)
    }
    val (cMap, data) = timed("Load.apply: Def.make(settings)...", log) {
      // When settings.size is 100000, Def.make takes around 10s.
      if (settings.size > 10000) {
        log.info(s"resolving key references (${settings.size} settings) ...")
      }
      Def.makeWithCompiledMap(settings)(
        delegates,
        config.scopeLocal,
        Project.showLoadingKey(loaded)
      )
    }
    Project.checkTargets(data) foreach sys.error
    val index = timed("Load.apply: structureIndex", log) {
      structureIndex(data, settings, loaded.extra(data), projects)
    }
    val streams = timed("Load.apply: mkStreams", log) { mkStreams(projects, loaded.root, data) }
    val bs = new BuildStructure(
      projects,
      loaded.root,
      settings,
      data,
      index,
      streams,
      delegates,
      config.scopeLocal,
      cMap
    )
    (rootEval, bs)
  }

  // map dependencies on the special tasks:
  // 1. the scope of 'streams' is the same as the defining key and has the task axis set to the defining key
  // 2. the defining key is stored on constructed tasks: used for error reporting among other things
  // 3. resolvedScoped is replaced with the defining key as a value
  // Note: this must be idempotent.
  def finalTransforms(ss: Seq[Setting[_]]): Seq[Setting[_]] = {
    def mapSpecial(to: ScopedKey[_]) = λ[ScopedKey ~> ScopedKey](
      (key: ScopedKey[_]) =>
        if (key.key == streams.key) {
          ScopedKey(Scope.fillTaskAxis(Scope.replaceThis(to.scope)(key.scope), to.key), key.key)
        } else key
    )
    def setDefining[T] =
      (key: ScopedKey[T], value: T) =>
        value match {
          case tk: Task[t]      => setDefinitionKey(tk, key).asInstanceOf[T]
          case ik: InputTask[t] => ik.mapTask(tk => setDefinitionKey(tk, key)).asInstanceOf[T]
          case _                => value
        }
    def setResolved(defining: ScopedKey[_]) = λ[ScopedKey ~> Option](
      (key: ScopedKey[_]) =>
        key.key match {
          case resolvedScoped.key => Some(defining.asInstanceOf[A1$])
          case _                  => None
        }
    )
    ss.map(
      s => s mapConstant setResolved(s.key) mapReferenced mapSpecial(s.key) mapInit setDefining
    )
  }

  def setDefinitionKey[T](tk: Task[T], key: ScopedKey[_]): Task[T] =
    if (isDummy(tk)) tk else Task(tk.info.set(Keys.taskDefinitionKey, key), tk.work)

  def structureIndex(
      data: Settings[Scope],
      settings: Seq[Setting[_]],
      extra: KeyIndex => BuildUtil[_],
      projects: Map[URI, LoadedBuildUnit]
  ): StructureIndex = {
    val keys = Index.allKeys(settings)
    val attributeKeys = Index.attributeKeys(data) ++ keys.map(_.key)
    val scopedKeys = keys ++ data.allKeys((s, k) => ScopedKey(s, k)).toVector
    val projectsMap = projects.mapValues(_.defined.keySet).toMap
    val configsMap: Map[String, Seq[Configuration]] =
      projects.values.flatMap(bu => bu.defined map { case (k, v) => (k, v.configurations) }).toMap
    val keyIndex = KeyIndex(scopedKeys.toVector, projectsMap, configsMap)
    val aggIndex = KeyIndex.aggregate(scopedKeys.toVector, extra(keyIndex), projectsMap, configsMap)
    new StructureIndex(
      Index.stringToKeyMap(attributeKeys),
      Index.taskToKeyMap(data),
      Index.triggers(data),
      keyIndex,
      aggIndex
    )
  }

  // Reevaluates settings after modifying them.  Does not recompile or reload any build components.
  def reapply(newSettings: Seq[Setting[_]], structure: BuildStructure)(
      implicit display: Show[ScopedKey[_]]
  ): BuildStructure = {
    val transformed = finalTransforms(newSettings)
    val (cMap, newData) =
      Def.makeWithCompiledMap(transformed)(structure.delegates, structure.scopeLocal, display)
    def extra(index: KeyIndex) = BuildUtil(structure.root, structure.units, index, newData)
    val newIndex = structureIndex(newData, transformed, extra, structure.units)
    val newStreams = mkStreams(structure.units, structure.root, newData)
    new BuildStructure(
      units = structure.units,
      root = structure.root,
      settings = transformed,
      data = newData,
      index = newIndex,
      streams = newStreams,
      delegates = structure.delegates,
      scopeLocal = structure.scopeLocal,
      compiledMap = cMap,
    )
  }

  @deprecated("No longer used. For binary compatibility", "1.2.1")
  def reapply(
      newSettings: Seq[Setting[_]],
      structure: BuildStructure,
      log: Logger
  )(implicit display: Show[ScopedKey[_]]): BuildStructure = {
    reapply(newSettings, structure)
  }

  def buildConfigurations(
      loaded: LoadedBuild,
      rootProject: URI => String,
      injectSettings: InjectSettings
  ): Seq[Setting[_]] = {
    (((GlobalScope / loadedBuild) :== loaded) +:
      transformProjectOnly(loaded.root, rootProject, injectSettings.global)) ++
      inScope(GlobalScope)(loaded.autos.globalSettings) ++
      loaded.units.toSeq.flatMap {
        case (uri, build) =>
          val pluginBuildSettings = loaded.autos.buildSettings(uri)
          val projectSettings = build.defined flatMap {
            case (id, project) =>
              val ref = ProjectRef(uri, id)
              val defineConfig: Seq[Setting[_]] =
                for (c <- project.configurations)
                  yield ((ref / ConfigKey(c.name) / configuration) :== c)
              val builtin: Seq[Setting[_]] =
                (thisProject :== project) +: (thisProjectRef :== ref) +: defineConfig
              val settings = builtin ++ project.settings ++ injectSettings.project
              // map This to thisScope, Select(p) to mapRef(uri, rootProject, p)
              transformSettings(projectScope(ref), uri, rootProject, settings)
          }
          val buildScope = Scope(Select(BuildRef(uri)), Zero, Zero, Zero)
          val buildBase = baseDirectory :== build.localBase
          val settings3 = pluginBuildSettings ++ (buildBase +: build.buildSettings)
          val buildSettings = transformSettings(buildScope, uri, rootProject, settings3)
          buildSettings ++ projectSettings
      }
  }

  def transformProjectOnly(
      uri: URI,
      rootProject: URI => String,
      settings: Seq[Setting[_]]
  ): Seq[Setting[_]] =
    Project.transform(Scope.resolveProject(uri, rootProject), settings)

  def transformSettings(
      thisScope: Scope,
      uri: URI,
      rootProject: URI => String,
      settings: Seq[Setting[_]]
  ): Seq[Setting[_]] = {
    val transformed = Project.transform(Scope.resolveScope(thisScope, uri, rootProject), settings)
    Settings.inject(transformed)
  }

  def projectScope(project: Reference): Scope = Scope(Select(project), Zero, Zero, Zero)

  def lazyEval(unit: BuildUnit): () => Eval = {
    lazy val eval = mkEval(unit)
    () => eval
  }

  def mkEval(unit: BuildUnit): Eval = {
    val defs = unit.definitions
    mkEval(defs.target ++ unit.plugins.classpath, defs.base, unit.plugins.pluginData.scalacOptions)
  }

  def mkEval(classpath: Seq[File], base: File, options: Seq[String]): Eval =
    mkEval(classpath, base, options, EvalReporter.console)

  def mkEval(
      classpath: Seq[File],
      base: File,
      options: Seq[String],
      mkReporter: scala.tools.nsc.Settings => EvalReporter
  ): Eval =
    new Eval(options, classpath, mkReporter, Some(evalOutputDirectory(base)))

  /**
   * This will clean up left-over files in the config-classes directory if they are no longer used.
   *
   * @param base  The base directory for the build, should match the one passed into `mkEval` method.
   */
  def cleanEvalClasses(base: File, keep: Seq[File]): Unit = {
    val baseTarget = evalOutputDirectory(base)
    val keepSet = keep.map(_.getCanonicalPath).toSet
    // If there are no keeper files, this may be because cache was up-to-date and
    // the files aren't properly returned, even though they should be.
    // TODO - figure out where the caching of whether or not to generate classfiles occurs, and
    // put cleanups there, perhaps.
    if (keepSet.nonEmpty) {
      def keepFile(f: File) = keepSet(f.getCanonicalPath)
      import sbt.io.syntax._
      val existing = (baseTarget.allPaths.get).filterNot(_.isDirectory)
      val toDelete = existing.filterNot(keepFile)
      if (toDelete.nonEmpty) {
        IO.delete(toDelete)
      }
    }
  }

  /** Loads the unresolved build units and computes its settings.
   *
   * @param root The root directory.
   * @param s The given state.
   * @param config The configuration of the loaded build.
   * @return An instance of [[PartBuild]] with all the unresolved build units.
   */
  def load(root: File, s: State, config: LoadBuildConfiguration): PartBuild = {
    val manager = config.pluginManagement.shift
    // Forced type ascription, otherwise scalac does not compile it
    val newConfig: LoadBuildConfiguration =
      config.copy(pluginManagement = manager, extraBuilds = Nil)
    val loader = builtinLoader(s, newConfig)
    loadURI(IO.directoryURI(root), loader, config.extraBuilds.toList)
  }

  /** Creates a loader for the build.
   *
   * @param s The given state.
   * @param config The configuration of the loaded build.
   * @return A [[BuildLoader]].
   */
  def builtinLoader(s: State, config: LoadBuildConfiguration): BuildLoader = {
    val fail = (uri: URI) => sys.error("Invalid build URI (no handler available): " + uri)
    val resolver = (info: BuildLoader.ResolveInfo) => RetrieveUnit(info)
    val build = (info: BuildLoader.BuildInfo) =>
      Some(() => loadUnit(info.uri, info.base, info.state, info.config))
    val loader = BuildLoader.componentLoader
    val components = BuildLoader.components(resolver, build, full = loader)
    BuildLoader(components, fail, s, config)
  }

  private def loadURI(uri: URI, loader: BuildLoader, extra: List[URI]): PartBuild = {
    IO.assertAbsolute(uri)
    val (referenced, map, newLoaders) = loadAll(uri +: extra, Map.empty, loader, Map.empty)
    checkAll(referenced, map)
    val build = new PartBuild(uri, map)
    newLoaders transformAll build
  }

  def addOverrides(unit: BuildUnit, loaders: BuildLoader): BuildLoader =
    loaders updatePluginManagement PluginManagement.extractOverrides(unit.plugins.fullClasspath)

  def addResolvers(unit: BuildUnit, isRoot: Boolean, loaders: BuildLoader): BuildLoader =
    unit.definitions.builds.flatMap(_.buildLoaders).toList match {
      case Nil => loaders
      case x :: xs =>
        val resolver = xs.foldLeft(x) { _ | _ }
        if (isRoot) loaders.setRoot(resolver) else loaders.addNonRoot(unit.uri, resolver)
    }

  def loaded(unit: BuildUnit): (PartBuildUnit, List[ProjectReference]) = {
    val defined = projects(unit)
    val firstDefined = defined match {
      case Nil            => sys.error("No projects defined in build unit " + unit)
      case Seq(first, _*) => first
    }

    // since base directories are resolved at this point (after 'projects'),
    //   we can compare Files instead of converting to URIs
    def isRoot(p: Project) = p.base == unit.localBase

    val externals = referenced(defined).toList
    val explicitRoots = unit.definitions.builds.flatMap(_.rootProject)
    val projectsInRoot = if (explicitRoots.isEmpty) defined.filter(isRoot) else explicitRoots
    val rootProjects = if (projectsInRoot.isEmpty) firstDefined :: Nil else projectsInRoot
    val partBuildUnit = new PartBuildUnit(
      unit,
      defined.map(d => (d.id, d)).toMap,
      rootProjects.map(_.id),
      buildSettings(unit)
    )
    (partBuildUnit, externals)
  }

  def buildSettings(unit: BuildUnit): Seq[Setting[_]] = {
    val buildScope = GlobalScope.copy(project = Select(BuildRef(unit.uri)))
    val resolve = Scope.resolveBuildScope(buildScope, unit.uri)
    Project.transform(resolve, unit.definitions.builds.flatMap(_.settings))
  }

  @tailrec private def loadAll(
      bases: List[URI],
      references: Map[URI, List[ProjectReference]],
      loaders: BuildLoader,
      builds: Map[URI, PartBuildUnit]
  ): (Map[URI, List[ProjectReference]], Map[URI, PartBuildUnit], BuildLoader) = {
    def loadURI(base: URI, bases: List[URI]) = {
      val (loadedBuild, refs) = loaded(loaders(base))
      val uris = refs.flatMap(Reference.uri)
      val buildUnit = loadedBuild.unit
      checkBuildBase(buildUnit.localBase)
      val resolvers = addResolvers(buildUnit, builds.isEmpty, loaders.resetPluginDepth)
      val updatedLoader = addOverrides(buildUnit, resolvers)
      val updatedReferences = references.updated(base, refs)
      val updatedBuilds = builds.updated(base, loadedBuild)
      val sortedRemaining = uris.reverse_:::(bases).sorted
      (sortedRemaining, updatedReferences, updatedLoader, updatedBuilds)
    }

    bases match {
      case b :: bs =>
        if (builds contains b) loadAll(bs, references, loaders, builds)
        else {
          val (newBases, newReferences, newLoaders, newBuilds) = loadURI(b, bs)
          loadAll(newBases, newReferences, newLoaders, newBuilds)
        }
      case Nil => (references, builds, loaders)
    }
  }

  def checkProjectBase(buildBase: File, projectBase: File): Unit = {
    checkDirectory(projectBase)
    assert(
      buildBase == projectBase || IO.relativize(buildBase, projectBase).isDefined,
      s"directory $projectBase is not contained in build root $buildBase"
    )
  }

  def checkBuildBase(base: File) = checkDirectory(base)

  def checkDirectory(base: File): Unit = {
    assert(base.isAbsolute, "Not absolute: " + base)
    if (base.isFile)
      sys.error("Not a directory: " + base)
    else if (!base.exists)
      IO createDirectory base
  }

  def resolveAll(builds: Map[URI, PartBuildUnit]): Map[URI, LoadedBuildUnit] = {
    val rootProject = getRootProject(builds)
    builds map {
      case (uri, unit) =>
        (uri, unit.resolveRefs(ref => Scope.resolveProjectRef(uri, rootProject, ref)))
    }
  }

  def checkAll(
      referenced: Map[URI, List[ProjectReference]],
      builds: Map[URI, PartBuildUnit]
  ): Unit = {
    val rootProject = getRootProject(builds)
    for ((uri, refs) <- referenced; ref <- refs) {
      val ProjectRef(refURI, refID) = Scope.resolveProjectRef(uri, rootProject, ref)
      val loadedUnit = builds(refURI)
      if (!(loadedUnit.defined contains refID)) {
        val projectIDs = loadedUnit.defined.keys.toSeq.sorted
        sys.error(s"""No project '$refID' in '$refURI'.
                     |Valid project IDs: ${projectIDs.mkString(", ")}""".stripMargin)
      }
    }
  }

  def resolveBase(against: File): Project => Project = {
    def resolve(f: File) = {
      val fResolved = new File(IO.directoryURI(IO.resolve(against, f)))
      checkProjectBase(against, fResolved)
      fResolved
    }
    p => p.copy(base = resolve(p.base))
  }

  def resolveProjects(loaded: PartBuild): LoadedBuild = {
    val rootProject = getRootProject(loaded.units)
    val units = loaded.units map {
      case (uri, unit) =>
        IO.assertAbsolute(uri)
        (uri, resolveProjects(uri, unit, rootProject))
    }
    new LoadedBuild(loaded.root, units)
  }

  def resolveProjects(
      uri: URI,
      unit: PartBuildUnit,
      rootProject: URI => String
  ): LoadedBuildUnit = {
    IO.assertAbsolute(uri)
    val resolve = (_: Project).resolve(ref => Scope.resolveProjectRef(uri, rootProject, ref))
    new LoadedBuildUnit(
      unit.unit,
      unit.defined.mapValues(resolve).toMap,
      unit.rootProjects,
      unit.buildSettings
    )
  }

  def projects(unit: BuildUnit): Seq[Project] = {
    // we don't have the complete build graph loaded, so we don't have the rootProject function yet.
    //  Therefore, we use resolveProjectBuild instead of resolveProjectRef.  After all builds are loaded, we can fully resolve ProjectReferences.
    val resolveBuild = (_: Project).resolveBuild(ref => Scope.resolveProjectBuild(unit.uri, ref))
    // although the default loader will resolve the project base directory, other loaders may not, so run resolveBase here as well
    unit.definitions.projects.map(resolveBuild compose resolveBase(unit.localBase))
  }

  def getRootProject(map: Map[URI, BuildUnitBase]): URI => String =
    uri => getBuild(map, uri).rootProjects.headOption getOrElse emptyBuild(uri)

  def getConfiguration(
      map: Map[URI, LoadedBuildUnit],
      uri: URI,
      id: String,
      conf: ConfigKey
  ): Configuration =
    configurationOpt(map, uri, id, conf) getOrElse noConfiguration(uri, id, conf.name)

  def configurationOpt(
      map: Map[URI, LoadedBuildUnit],
      uri: URI,
      id: String,
      conf: ConfigKey
  ): Option[Configuration] =
    getProject(map, uri, id).configurations.find(_.name == conf.name)

  def getProject(map: Map[URI, LoadedBuildUnit], uri: URI, id: String): ResolvedProject =
    getBuild(map, uri).defined.getOrElse(id, noProject(uri, id))

  def getBuild[T](map: Map[URI, T], uri: URI): T = map.getOrElse(uri, noBuild(uri))

  def emptyBuild(uri: URI) = sys.error(s"no root project defined for build unit '$uri'")
  def noBuild(uri: URI) = sys.error(s"build unit '$uri' not defined.")
  def noProject(uri: URI, id: String) = sys.error(s"no project '$id' defined in '$uri'.")

  def noConfiguration(uri: URI, id: String, conf: String) =
    sys.error(s"no configuration '$conf' defined in project '$id' in '$uri'")

  // Called from builtinLoader
  def loadUnit(uri: URI, localBase: File, s: State, config: LoadBuildConfiguration): BuildUnit =
    timed(s"Load.loadUnit($uri, ...)", config.log) {
      val log = config.log
      val normBase = localBase.getCanonicalFile
      val defDir = projectStandard(normBase)

      val plugs = timed("Load.loadUnit: plugins", log) {
        plugins(defDir, s, config.copy(pluginManagement = config.pluginManagement.forPlugin))
      }
      val defsScala = timed("Load.loadUnit: defsScala", log) {
        plugs.detected.builds.values
      }
      val buildLevelExtraProjects = plugs.detected.autoPlugins flatMap { d =>
        d.value.extraProjects map { _.setProjectOrigin(ProjectOrigin.ExtraProject) }
      }

      // NOTE - because we create an eval here, we need a clean-eval later for this URI.
      lazy val eval = timed("Load.loadUnit: mkEval", log) {
        def mkReporter(settings: scala.tools.nsc.Settings): EvalReporter =
          plugs.pluginData.buildTarget match {
            case None => EvalReporter.console(settings)
            case Some(buildTarget) =>
              new BuildServerEvalReporter(buildTarget, new ConsoleReporter(settings))
          }
        mkEval(plugs.classpath, defDir, plugs.pluginData.scalacOptions, mkReporter)
      }
      val initialProjects = defsScala.flatMap(b => projectsFromBuild(b, normBase)) ++ buildLevelExtraProjects

      val hasRootAlreadyDefined = defsScala.exists(_.rootProject.isDefined)

      val memoSettings = new mutable.HashMap[File, LoadedSbtFile]
      def loadProjects(ps: Seq[Project], createRoot: Boolean) =
        loadTransitive(
          ps,
          normBase,
          plugs,
          () => eval,
          config.injectSettings,
          Nil,
          memoSettings,
          config.log,
          createRoot,
          uri,
          config.pluginManagement.context,
          Nil,
          s.get(BasicKeys.extraMetaSbtFiles).getOrElse(Nil)
        )
      val loadedProjectsRaw = timed("Load.loadUnit: loadedProjectsRaw", log) {
        loadProjects(initialProjects, !hasRootAlreadyDefined)
      }
      // TODO - As of sbt 0.13.6 we should always have a default root project from
      //        here on, so the autogenerated build aggregated can be removed from this code. ( I think)
      // We may actually want to move it back here and have different flags in loadTransitive...
      val hasRoot = loadedProjectsRaw.projects.exists(_.base == normBase) || defsScala.exists(
        _.rootProject.isDefined
      )
      val (loadedProjects, defaultBuildIfNone, keepClassFiles) =
        if (hasRoot)
          (
            loadedProjectsRaw.projects,
            BuildDef.defaultEmpty,
            loadedProjectsRaw.generatedConfigClassFiles
          )
        else {
          val existingIDs = loadedProjectsRaw.projects.map(_.id)
          val refs = existingIDs.map(id => ProjectRef(uri, id))
          val defaultID = autoID(normBase, config.pluginManagement.context, existingIDs)
          val b = BuildDef.defaultAggregated(defaultID, refs)
          val defaultProjects = timed("Load.loadUnit: defaultProjects", log) {
            loadProjects(projectsFromBuild(b, normBase), false)
          }
          (
            defaultProjects.projects ++ loadedProjectsRaw.projects,
            b,
            defaultProjects.generatedConfigClassFiles ++ loadedProjectsRaw.generatedConfigClassFiles
          )
        }
      // Now we clean stale class files.
      // TODO - this may cause issues with multiple sbt clients, but that should be deprecated pending sbt-server anyway
      timed("Load.loadUnit: cleanEvalClasses", log) {
        cleanEvalClasses(defDir, keepClassFiles)
      }
      val defs = if (defsScala.isEmpty) defaultBuildIfNone :: Nil else defsScala
      // HERE we pull out the defined vals from memoSettings and unify them all so
      // we can use them later.
      val valDefinitions = memoSettings.values.foldLeft(DefinedSbtValues.empty) { (prev, sbtFile) =>
        prev.zip(sbtFile.definitions)
      }
      val loadedDefs = new LoadedDefinitions(
        defDir,
        Nil,
        plugs.loader,
        defs,
        loadedProjects,
        plugs.detected.builds.names,
        valDefinitions
      )
      new BuildUnit(uri, normBase, loadedDefs, plugs)
    }

  private[this] def autoID(
      localBase: File,
      context: PluginManagement.Context,
      existingIDs: Seq[String]
  ): String = {
    def normalizeID(f: File) = Project.normalizeProjectID(f.getName) match {
      case Right(id) => id
      case Left(msg) => sys.error(autoIDError(f, msg))
    }
    @tailrec def nthParentName(f: File, i: Int): String =
      if (f eq null) BuildDef.defaultID(localBase)
      else if (i <= 0) normalizeID(f)
      else nthParentName(f.getParentFile, i - 1)
    val pluginDepth = context.pluginProjectDepth
    val postfix = "-build" * pluginDepth
    val idBase =
      if (context.globalPluginProject) "global-plugins" else nthParentName(localBase, pluginDepth)
    val tryID = idBase + postfix
    if (existingIDs.contains(tryID)) BuildDef.defaultID(localBase) else tryID
  }

  private[this] def autoIDError(base: File, reason: String): String =
    "Could not derive root project ID from directory " + base.getAbsolutePath + ":\n" +
      reason + "\nRename the directory or explicitly define a root project."

  private[this] def projectsFromBuild(b: BuildDef, base: File): Seq[Project] =
    b.projectDefinitions(base).map(resolveBase(base))

  // Lame hackery to keep track of our state.
  private[this] case class LoadedProjects(
      projects: Seq[Project],
      generatedConfigClassFiles: Seq[File]
  )

  /**
   * Loads a new set of projects, including any transitively defined projects underneath this one.
   *
   * We have two assumptions here:
   *
   * 1. The first `Project` instance we encounter defines AddSettings and gets to specify where we pull other settings.
   * 2. Any project manipulation (enable/disablePlugins) is ok to be added in the order we encounter it.
   *
   * Any further setting is ignored.
   *
   * Note:  Lots of internal details in here that shouldn't be otherwise exposed.
   *
   * TODO - We want to attach the known (at this time) vals/lazy vals defined in each project's
   *        build.sbt to that project so we can later use this for the `set` command.
   *
   * @param newProjects    A sequence of projects we have not yet loaded, but will try to.
   * @param buildBase      The `baseDirectory` for the entire build.
   * @param plugins        A misnomer, this is actually the compiled BuildDefinition (classpath and such) for this project.
   * @param eval           A mechanism of generating an "Eval" which can compile scala code for us.
   * @param injectSettings Settings we need to inject into projects.
   * @param acc            An accumulated list of loaded projects, originally in newProjects.
   * @param memoSettings   A recording of all sbt files that have been loaded so far.
   * @param log            The logger used for this project.
   * @param makeOrDiscoverRoot  True if we should auto-generate a root project.
   * @param buildUri            The URI of the build this is loading
   * @param context             The plugin management context for autogenerated IDs.
   * @param generatedConfigClassFiles
   * @param extraSbtFiles
   * @return The completely resolved/updated sequence of projects defined, with all settings expanded.
   */
  private[this] def loadTransitive(
      newProjects: Seq[Project],
      buildBase: File,
      plugins: LoadedPlugins,
      eval: () => Eval,
      injectSettings: InjectSettings,
      acc: Seq[Project],
      memoSettings: mutable.Map[File, LoadedSbtFile],
      log: Logger,
      makeOrDiscoverRoot: Boolean,
      buildUri: URI,
      context: PluginManagement.Context,
      generatedConfigClassFiles: Seq[File],
      extraSbtFiles: Seq[File]
  ): LoadedProjects =
    /*timed(s"Load.loadTransitive(${ newProjects.map(_.id) })", log)*/ {

      def load(newProjects: Seq[Project], acc: Seq[Project], generated: Seq[File]) = {
        loadTransitive(
          newProjects,
          buildBase,
          plugins,
          eval,
          injectSettings,
          acc,
          memoSettings,
          log,
          false,
          buildUri,
          context,
          generated,
          Nil
        )
      }

      // load all relevant configuration files (.sbt, as .scala already exists at this point)
      def discover(base: File): DiscoveredProjects = {
        val auto =
          if (base == buildBase) AddSettings.allDefaults
          else AddSettings.defaultSbtFiles

        val extraFiles =
          if (base == buildBase && isMetaBuildContext(context)) extraSbtFiles
          else Nil
        discoverProjects(auto, base, extraFiles, plugins, eval, memoSettings)
      }

      // Step two:
      // a. Apply all the project manipulations from .sbt files in order
      // b. Deduce the auto plugins for the project
      // c. Finalize a project with all its settings/configuration.
      def finalizeProject(
          p: Project,
          files: Seq[File],
          extraFiles: Seq[File],
          expand: Boolean
      ): (Project, Seq[Project]) = {
        val configFiles = files.flatMap(f => memoSettings.get(f))
        val p1: Project = Function.chain(configFiles.flatMap(_.manipulations))(p)
        val autoPlugins: Seq[AutoPlugin] =
          try plugins.detected.deducePluginsFromProject(p1, log)
          catch { case e: AutoPluginException => throw translateAutoPluginException(e, p) }
        val p2 =
          resolveProject(p1, autoPlugins, plugins, injectSettings, memoSettings, extraFiles, log)
        val projectLevelExtra =
          if (expand) {
            autoPlugins.flatMap(
              _.derivedProjects(p2).map(_.setProjectOrigin(ProjectOrigin.DerivedProject))
            )
          } else Nil
        (p2, projectLevelExtra)
      }

      // Discover any new project definition for the base directory of this project, and load all settings.
      def discoverAndLoad(p: Project, rest: Seq[Project]): LoadedProjects = {
        val DiscoveredProjects(rootOpt, discovered, files, extraFiles, generated) = discover(
          p.base
        )

        // TODO: We assume here the project defined in a build.sbt WINS because the original was a
        // phony.  However, we may want to 'merge' the two, or only do this if the original was a
        // default generated project.
        val root = rootOpt.getOrElse(p)
        val (finalRoot, projectLevelExtra) = finalizeProject(root, files, extraFiles, true)
        val newProjects = rest ++ discovered ++ projectLevelExtra
        val newAcc = acc :+ finalRoot
        val newGenerated = generated ++ generatedConfigClassFiles
        load(newProjects, newAcc, newGenerated)
      }

      // Load all config files AND finalize the project at the root directory, if it exists.
      // Continue loading if we find any more.
      newProjects match {
        case Seq(next, rest @ _*) =>
          log.debug(s"[Loading] Loading project ${next.id} @ ${next.base}")
          discoverAndLoad(next, rest)
        case Nil if makeOrDiscoverRoot =>
          log.debug(s"[Loading] Scanning directory $buildBase")
          val DiscoveredProjects(rootOpt, discovered, files, extraFiles, generated) = discover(
            buildBase
          )
          val discoveredIdsStr = discovered.map(_.id).mkString(",")
          val (root, expand, moreProjects, otherProjects) = rootOpt match {
            case Some(root) =>
              log.debug(s"[Loading] Found root project ${root.id} w/ remaining $discoveredIdsStr")
              (root, true, discovered, LoadedProjects(Nil, Nil))
            case None =>
              log.debug(s"[Loading] Found non-root projects $discoveredIdsStr")
              // Here we do something interesting... We need to create an aggregate root project
              val otherProjects = load(discovered, acc, Nil)
              val root = {
                val existingIds = otherProjects.projects.map(_.id)
                val defaultID = autoID(buildBase, context, existingIds)
                val refs = existingIds.map(id => ProjectRef(buildUri, id))
                if (discovered.isEmpty || java.lang.Boolean.getBoolean("sbt.root.ivyplugin"))
                  BuildDef.defaultAggregatedProject(defaultID, buildBase, refs)
                else BuildDef.generatedRootWithoutIvyPlugin(defaultID, buildBase, refs)
              }
              (root, false, Nil, otherProjects)
          }
          val (finalRoot, projectLevelExtra) =
            timed(s"Load.loadTransitive: finalizeProject($root)", log) {
              finalizeProject(root, files, extraFiles, expand)
            }
          val newProjects = moreProjects ++ projectLevelExtra
          val newAcc = finalRoot +: (acc ++ otherProjects.projects)
          val newGenerated =
            generated ++ otherProjects.generatedConfigClassFiles ++ generatedConfigClassFiles
          load(newProjects, newAcc, newGenerated)
        case Nil =>
          val projectIds = acc.map(_.id).mkString("(", ", ", ")")
          log.debug(s"[Loading] Done in $buildBase, returning: $projectIds")
          LoadedProjects(acc, generatedConfigClassFiles)
      }
    }

  private[this] def translateAutoPluginException(
      e: AutoPluginException,
      project: Project
  ): AutoPluginException =
    e.withPrefix(s"error determining plugins for project '${project.id}' in ${project.base}:\n")

  /**
   * Represents the results of flushing out a directory and discovering all the projects underneath it.
   *  THis will return one completely loaded project, and any newly discovered (and unloaded) projects.
   *
   *  @param root The project at "root" directory we were looking, or non if non was defined.
   *  @param nonRoot Any sub-projects discovered from this directory
   *  @param sbtFiles Any sbt file loaded during this discovery (used later to complete the project).
   *  @param generatedFiles Any .class file that was generated when compiling/discovering these projects.
   */
  private[this] case class DiscoveredProjects(
      root: Option[Project],
      nonRoot: Seq[Project],
      sbtFiles: Seq[File],
      extraSbtFiles: Seq[File],
      generatedFiles: Seq[File]
  )

  /**
   * This method attempts to resolve/apply all configuration loaded for a project. It is responsible for the following:
   *
   * Ordering all Setting[_]s for the project
   *
   * @param p  The project with manipulation.
   * @param projectPlugins The deduced list of plugins for the given project.
   * @param loadedPlugins  The project definition (and classloader) of the build.
   * @param globalUserSettings All the settings contributed from the ~/.sbt/<version> directory
   * @param memoSettings A recording of all loaded files (our files should reside in there).  We should need not load any
   *                     sbt file to resolve a project.
   * @param extraSbtFiles Extra *.sbt files.
   * @param log  A logger to report auto-plugin issues to.
   */
  private[sbt] def resolveProject(
      p: Project,
      projectPlugins: Seq[AutoPlugin],
      loadedPlugins: LoadedPlugins,
      globalUserSettings: InjectSettings,
      memoSettings: mutable.Map[File, LoadedSbtFile],
      extraSbtFiles: Seq[File],
      log: Logger
  ): Project =
    timed(s"Load.resolveProject(${p.id})", log) {
      import AddSettings._
      val autoConfigs = projectPlugins.flatMap(_.projectConfigurations)

      // 3. Use AddSettings instance to order all Setting[_]s appropriately
      val allSettings = {
        // TODO - This mechanism of applying settings could be off... It's in two places now...
        lazy val defaultSbtFiles = configurationSources(p.base)
        lazy val sbtFiles = defaultSbtFiles ++ extraSbtFiles
        // Filter the AutoPlugin settings we included based on which ones are
        // intended in the AddSettings.AutoPlugins filter.
        def autoPluginSettings(f: AutoPlugins) =
          projectPlugins.filter(f.include).flatMap(_.projectSettings)
        // Grab all the settings we already loaded from sbt files
        def settings(files: Seq[File]): Seq[Setting[_]] = {
          if (files.nonEmpty)
            log.info(
              s"${files.map(_.getName).mkString(s"loading settings for project ${p.id} from ", ",", " ...")}"
            )
          for {
            file <- files
            config <- (memoSettings get file).toSeq
            setting <- config.settings
          } yield setting
        }
        // Expand the AddSettings instance into a real Seq[Setting[_]] we'll use on the project
        def expandSettings(auto: AddSettings): Seq[Setting[_]] = auto match {
          case BuildScalaFiles     => p.settings
          case User                => globalUserSettings.cachedProjectLoaded(loadedPlugins.loader)
          case sf: SbtFiles        => settings(sf.files.map(f => IO.resolve(p.base, f)))
          case sf: DefaultSbtFiles => settings(sbtFiles.filter(sf.include))
          case p: AutoPlugins      => autoPluginSettings(p)
          case q: Sequence =>
            q.sequence.foldLeft(Seq.empty[Setting[_]]) { (b, add) =>
              b ++ expandSettings(add)
            }
        }
        val auto = AddSettings.allDefaults
        expandSettings(auto)
      }
      // Finally, a project we can use in buildStructure.
      p.copy(settings = allSettings)
        .setAutoPlugins(projectPlugins)
        .prefixConfigs(autoConfigs: _*)
    }

  /**
   * This method attempts to discover all Project/settings it can using the configured AddSettings and project base.
   *
   * @param auto  The AddSettings of the defining project (or default) we use to determine which build.sbt files to read.
   * @param projectBase  The directory we're currently loading projects/definitions from.
   * @param eval  A mechanism of executing/running scala code.
   * @param memoSettings  A recording of all files we've parsed.
   */
  private[this] def discoverProjects(
      auto: AddSettings,
      projectBase: File,
      extraSbtFiles: Seq[File],
      loadedPlugins: LoadedPlugins,
      eval: () => Eval,
      memoSettings: mutable.Map[File, LoadedSbtFile]
  ): DiscoveredProjects = {

    // Default sbt files to read, if needed
    lazy val defaultSbtFiles = configurationSources(projectBase)
    lazy val sbtFiles = defaultSbtFiles ++ extraSbtFiles

    // Classloader of the build
    val loader = loadedPlugins.loader

    // How to load an individual file for use later.
    // TODO - We should import vals defined in other sbt files here, if we wish to
    // share.  For now, build.sbt files have their own unique namespace.
    def loadSettingsFile(src: File): LoadedSbtFile =
      EvaluateConfigurations.evaluateSbtFile(
        eval(),
        src,
        IO.readLines(src),
        loadedPlugins.detected.imports,
        0
      )(loader)
    // How to merge SbtFiles we read into one thing
    def merge(ls: Seq[LoadedSbtFile]): LoadedSbtFile = ls.foldLeft(LoadedSbtFile.empty) {
      _ merge _
    }
    // Loads a given file, or pulls from the cache.

    def memoLoadSettingsFile(src: File): LoadedSbtFile =
      memoSettings.getOrElse(src, {
        val lf = loadSettingsFile(src)
        memoSettings.put(src, lf.clearProjects) // don't load projects twice
        lf
      })

    // Loads a set of sbt files, sorted by their lexical name (current behavior of sbt).
    def loadFiles(fs: Seq[File]): LoadedSbtFile =
      merge(fs.sortBy(_.getName).map(memoLoadSettingsFile))

    // Finds all the build files associated with this project
    import AddSettings.{ DefaultSbtFiles, SbtFiles, Sequence }
    def associatedFiles(auto: AddSettings): Seq[File] = auto match {
      case sf: SbtFiles        => sf.files.map(f => IO.resolve(projectBase, f)).filterNot(_.isHidden)
      case sf: DefaultSbtFiles => sbtFiles.filter(sf.include).filterNot(_.isHidden)
      case q: Sequence =>
        q.sequence.foldLeft(Seq.empty[File]) { (b, add) =>
          b ++ associatedFiles(add)
        }
      case _ => Seq.empty
    }
    val rawFiles = associatedFiles(auto)
    val loadedFiles = loadFiles(rawFiles)
    val rawProjects = loadedFiles.projects
    val (root, nonRoot) = rawProjects.partition(_.base == projectBase)
    // TODO - good error message if more than one root project
    DiscoveredProjects(
      root.headOption,
      nonRoot,
      rawFiles,
      extraSbtFiles,
      loadedFiles.generatedFiles
    )
  }

  def globalPluginClasspath(globalPlugin: Option[GlobalPlugin]): Seq[Attributed[File]] =
    globalPlugin match {
      case Some(cp) => cp.data.fullClasspath
      case None     => Nil
    }

  /** These are the settings defined when loading a project "meta" build. */
  @nowarn
  val autoPluginSettings: Seq[Setting[_]] = inScope(GlobalScope in LocalRootProject)(
    Seq(
      sbtPlugin :== true,
      isMetaBuild :== true,
      pluginData := {
        val prod = (Configurations.Runtime / exportedProducts).value
        val cp = (Configurations.Runtime / fullClasspath).value
        val opts = (Configurations.Compile / scalacOptions).value
        val unmanagedSrcDirs = (Configurations.Compile / unmanagedSourceDirectories).value
        val unmanagedSrcs = (Configurations.Compile / unmanagedSources).value
        val managedSrcDirs = (Configurations.Compile / managedSourceDirectories).value
        val managedSrcs = (Configurations.Compile / managedSources).value
        val buildTarget = (Configurations.Compile / bspTargetIdentifier).value
        PluginData(
          removeEntries(cp, prod),
          prod,
          Some(fullResolvers.value.toVector),
          Some(update.value),
          opts,
          unmanagedSrcDirs,
          unmanagedSrcs,
          managedSrcDirs,
          managedSrcs,
          Some(buildTarget)
        )
      },
      scalacOptions += "-Wconf:cat=unused-nowarn:s",
      onLoadMessage := ("loading project definition from " + baseDirectory.value)
    )
  )

  private[this] def removeEntries(
      cp: Seq[Attributed[File]],
      remove: Seq[Attributed[File]]
  ): Seq[Attributed[File]] = {
    val files = data(remove).toSet
    cp filter { f =>
      !files.contains(f.data)
    }
  }

  def enableSbtPlugin(config: LoadBuildConfiguration): LoadBuildConfiguration =
    config.copy(
      injectSettings = config.injectSettings.copy(
        global = autoPluginSettings ++ config.injectSettings.global,
        project = config.pluginManagement.inject ++ config.injectSettings.project
      )
    )

  def activateGlobalPlugin(config: LoadBuildConfiguration): LoadBuildConfiguration =
    config.globalPlugin match {
      case Some(gp) =>
        config.copy(injectSettings = config.injectSettings.copy(project = gp.inject))
      case None => config
    }

  def plugins(dir: File, s: State, config: LoadBuildConfiguration): LoadedPlugins = {
    val context = config.pluginManagement.context
    val extraSbtFiles: Seq[File] =
      if (isMetaBuildContext(context)) s.get(BasicKeys.extraMetaSbtFiles).getOrElse(Nil)
      else Nil
    if (hasDefinition(dir) || extraSbtFiles.nonEmpty)
      buildPlugins(dir, s, enableSbtPlugin(activateGlobalPlugin(config)))
    else
      noPlugins(dir, config)
  }

  private def isMetaBuildContext(context: PluginManagement.Context): Boolean =
    !context.globalPluginProject && context.pluginProjectDepth == 1

  def hasDefinition(dir: File): Boolean = {
    import sbt.io.syntax._
    (dir * -GlobFilter(DefaultTargetName)).get.nonEmpty
  }

  def noPlugins(dir: File, config: LoadBuildConfiguration): LoadedPlugins =
    loadPluginDefinition(
      dir,
      config,
      PluginData(config.globalPluginClasspath, Nil, None, None, Nil, Nil, Nil, Nil, Nil, None)
    )

  def buildPlugins(dir: File, s: State, config: LoadBuildConfiguration): LoadedPlugins =
    loadPluginDefinition(dir, config, buildPluginDefinition(dir, s, config))

  /** Loads the plugins.
   *
   * @param dir The base directory for the build.
   * @param config The configuration for the build.
   * @param pluginData The data required to load plugins.
   * @return An instance of the loaded build with plugin information.
   */
  def loadPluginDefinition(
      dir: File,
      config: LoadBuildConfiguration,
      pluginData: PluginData
  ): LoadedPlugins = {
    val definitionClasspath = pluginData.definitionClasspath
    val dependencyClasspath = pluginData.dependencyClasspath
    val pluginLoader: ClassLoader =
      pluginDefinitionLoader(config, dependencyClasspath, definitionClasspath)
    val fullDependencyClasspath: Def.Classpath =
      buildPluginClasspath(config, pluginData.dependencyClasspath)
    val newData = pluginData.copy(dependencyClasspath = fullDependencyClasspath)
    loadPlugins(dir, newData, pluginLoader)
  }

  /** Constructs the classpath required to load plugins, the so-called
   * dependency classpath, from the provided classpath and the current config.
   *
   * @param config The configuration that declares classpath entries.
   * @param depcp The user-defined dependency classpath.
   * @return A classpath aggregating both without repeated entries.
   */
  def buildPluginClasspath(
      config: LoadBuildConfiguration,
      depcp: Seq[Attributed[File]]
  ): Def.Classpath = {
    if (depcp.isEmpty) config.classpath
    else (depcp ++ config.classpath).distinct
  }

  /** Creates a classloader with a hierarchical structure, where the parent
   * classloads the dependency classpath and the return classloader classloads
   * the definition classpath.
   *
   * @param config The configuration for the whole sbt build.
   * @param dependencyClasspath The dependency classpath (sbt dependencies).
   * @param definitionClasspath The definition classpath for build definitions.
   * @return A classloader ready to class load plugins.
   */
  def pluginDefinitionLoader(
      config: LoadBuildConfiguration,
      dependencyClasspath: Def.Classpath,
      definitionClasspath: Def.Classpath
  ): ClassLoader = {
    val manager = config.pluginManagement
    val parentLoader: ClassLoader = {
      if (dependencyClasspath.isEmpty) manager.initialLoader
      else {
        // Load only the dependency classpath for the common plugin classloader
        val loader = manager.loader
        loader.add(Path.toURLs(data(dependencyClasspath)))
        loader
      }
    }

    // Load the definition classpath separately to avoid conflicts, see #511.
    if (definitionClasspath.isEmpty) parentLoader
    else ClasspathUtil.toLoader(data(definitionClasspath).map(_.toPath), parentLoader)
  }

  def buildPluginDefinition(dir: File, s: State, config: LoadBuildConfiguration): PluginData = {
    val (eval, pluginDef) = apply(dir, s, config)
    val pluginState = Project.setProject(Load.initialSession(pluginDef, eval), pluginDef, s)
    config.evalPluginDef(Project.structure(pluginState), pluginState)
  }

  def loadPlugins(dir: File, data: PluginData, loader: ClassLoader): LoadedPlugins =
    new LoadedPlugins(dir, data, loader, PluginDiscovery.discoverAll(data, loader))

  def initialSession(structure: BuildStructure, rootEval: () => Eval, s: State): SessionSettings = {
    val session = s get Keys.sessionSettings
    val currentProject = session map (_.currentProject) getOrElse Map.empty
    val currentBuild = session map (_.currentBuild) filter (
        uri => structure.units.keys exists (uri ==)
    ) getOrElse structure.root
    new SessionSettings(
      currentBuild,
      projectMap(structure, currentProject),
      structure.settings,
      Map.empty,
      Nil,
      rootEval
    )
  }

  def initialSession(structure: BuildStructure, rootEval: () => Eval): SessionSettings =
    new SessionSettings(
      structure.root,
      projectMap(structure, Map.empty),
      structure.settings,
      Map.empty,
      Nil,
      rootEval
    )

  def projectMap(structure: BuildStructure, current: Map[URI, String]): Map[URI, String] = {
    val units = structure.units
    val getRoot = getRootProject(units)
    def project(uri: URI) = {
      current get uri filter { p =>
        structure allProjects uri map (_.id) contains p
      } getOrElse getRoot(uri)
    }
    units.keys.map(uri => (uri, project(uri))).toMap
  }

  def defaultEvalOptions: Seq[String] = Nil

  def referenced[PR <: ProjectReference](definitions: Seq[ProjectDefinition[PR]]): Seq[PR] =
    definitions flatMap { _.referenced }

  final class EvaluatedConfigurations(val eval: Eval, val settings: Seq[Setting[_]])

  final case class InjectSettings(
      global: Seq[Setting[_]],
      project: Seq[Setting[_]],
      projectLoaded: ClassLoader => Seq[Setting[_]]
  ) {
    import java.net.URLClassLoader
    private val cache: mutable.Map[String, Seq[Setting[_]]] = mutable.Map.empty
    // Cache based on the underlying URL values of the classloader
    def cachedProjectLoaded(cl: ClassLoader): Seq[Setting[_]] =
      cl match {
        case cl: URLClassLoader =>
          cache.getOrElseUpdate(classLoaderToHash(Some(cl)), projectLoaded(cl))
        case _ => projectLoaded(cl)
      }
    private def classLoaderToHash(o: Option[ClassLoader]): String =
      o match {
        case Some(cl: URLClassLoader) =>
          cl.getURLs.toList.toString + classLoaderToHash(Option(cl.getParent))
        case Some(cl: ClassLoader) =>
          cl.toString + classLoaderToHash(Option(cl.getParent))
        case _ => "null"
      }
  }

  /** Variable to control the indentation of the timing logs. */
  private var timedIndentation: Int = 0

  /** Debugging method to time how long it takes to run various compilation tasks. */
  private[sbt] def timed[T](label: String, log: Logger)(t: => T): T = {
    timedIndentation += 1
    val start = System.nanoTime
    val result = t
    val elapsed = System.nanoTime - start
    timedIndentation -= 1
    val prefix = " " * 2 * timedIndentation
    log.debug(s"$prefix$label took ${elapsed / 1e6}ms")
    result
  }
}

final case class LoadBuildConfiguration(
    stagingDirectory: File,
    classpath: Def.Classpath,
    loader: ClassLoader,
    compilers: Compilers,
    evalPluginDef: (BuildStructure, State) => PluginData,
    delegates: LoadedBuild => Scope => Seq[Scope],
    scopeLocal: ScopeLocal,
    pluginManagement: PluginManagement,
    injectSettings: Load.InjectSettings,
    globalPlugin: Option[GlobalPlugin],
    extraBuilds: Seq[URI],
    log: Logger
) {
  lazy val globalPluginClasspath: Def.Classpath =
    Load.buildPluginClasspath(this, Load.globalPluginClasspath(globalPlugin))

  lazy val detectedGlobalPlugins: DetectedPlugins = {
    val pluginData = globalPlugin match {
      case Some(info) =>
        val data = info.data
        PluginData(
          data.fullClasspath,
          data.internalClasspath,
          Some(data.resolvers),
          Some(data.updateReport),
          Nil,
          Nil,
          Nil,
          Nil,
          Nil,
          None
        )
      case None => PluginData(globalPluginClasspath)
    }
    val baseDir = globalPlugin match {
      case Some(x) => x.base
      case _       => stagingDirectory
    }
    val globalPlugins = Load.loadPluginDefinition(baseDir, this, pluginData)
    globalPlugins.detected
  }
}

final class IncompatiblePluginsException(msg: String, cause: Throwable)
    extends Exception(msg, cause)
