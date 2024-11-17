/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import java.io.File
import java.nio.file.{ Path => NioPath }
import java.net.URI
// import Project._
import Keys.{
  stateBuildStructure,
  bspEnabled,
  cacheStores,
  colorShellPrompt,
  commands,
  historyPath,
  projectCommand,
  sessionSettings,
  shellPrompt,
  templateResolverInfos,
  autoStartServer,
  rootOutputDirectory,
  serverHost,
  serverIdleTimeout,
  serverLog,
  serverPort,
  serverUseJni,
  serverAuthentication,
  serverConnectionType,
  fullServerHandlers,
  logLevel,
  windowsServerSecurityLevel,
}
import Project.LoadAction
import Scope.{ Global, ThisScope }
import sbt.SlashSyntax0.given
import Def.{ Flattened, Initialize, ScopedKey, Setting }
import sbt.internal.{
  Load,
  BuildStructure,
  LoadedBuild,
  LoadedBuildUnit,
  SettingGraph,
  SessionSettings
}
import sbt.internal.util.{ AttributeKey, AttributeMap, Relation }
import sbt.internal.util.Types.const
import sbt.internal.server.ServerHandler
import sbt.librarymanagement.Configuration
import sbt.util.{ ActionCacheStore, Show, Level }
import sjsonnew.JsonFormat
import scala.annotation.targetName
import scala.concurrent.{ Await, TimeoutException }
import scala.concurrent.duration.*
import ClasspathDep.*
import xsbti.FileConverter

/*
sealed trait Project extends ProjectDefinition[ProjectReference] with CompositeProject {
  def componentProjects: Seq[Project] = this :: Nil

  private[sbt] def copy(
      id: String = id,
      base: File = base,
      aggregate: Seq[ProjectReference] = aggregate,
      dependencies: Seq[ClasspathDep[ProjectReference]] = dependencies,
      settings: Seq[Setting[_]] = settings,
      configurations: Seq[Configuration] = configurations
  ): Project =
    copy2(id, base, aggregate, dependencies, settings, configurations)

  private def copy2(
      id: String = id,
      base: File = base,
      aggregate: Seq[ProjectReference] = aggregate,
      dependencies: Seq[ClasspathDep[ProjectReference]] = dependencies,
      settings: Seq[Setting[_]] = settings,
      configurations: Seq[Configuration] = configurations,
      plugins: Plugins = plugins,
      autoPlugins: Seq[AutoPlugin] = autoPlugins,
      projectOrigin: ProjectOrigin = projectOrigin,
  ): Project =
    unresolved(
      id,
      base,
      aggregate = aggregate,
      dependencies = dependencies,
      settings = settings,
      configurations,
      plugins,
      autoPlugins,
      projectOrigin
    )
 */

/*
  /** Adds new configurations directly to this project.  To override an existing configuration, use `overrideConfigs`. */
  def configs(cs: Configuration*): Project = copy(configurations = configurations ++ cs)

  /** Adds classpath dependencies on internal or external projects. */
  def dependsOn(deps: ClasspathDep[ProjectReference]*): Project =
    copy(dependencies = dependencies ++ deps)

  /**
 * Adds projects to be aggregated.  When a user requests a task to run on this project from the command line,
 * the task will also be run in aggregated projects.
 */
  def aggregate(refs: ProjectReference*): Project =
    copy(aggregate = (aggregate: Seq[ProjectReference]) ++ refs)

  /** Appends settings to the current settings sequence for this project. */
  def settings(ss: Def.SettingsDefinition*): Project =
    copy(settings = (settings: Seq[Def.Setting[_]]) ++ Def.settings(ss: _*))

  /**
 * Sets the [[AutoPlugin]]s of this project.
 * A [[AutoPlugin]] is a common label that is used by plugins to determine what settings, if any, to enable on a project.
 */
  def enablePlugins(ns: Plugins*): Project = setPlugins(ns.foldLeft(plugins)(Plugins.and))

  /** Disable the given plugins on this project. */
  def disablePlugins(ps: AutoPlugin*): Project =
    setPlugins(Plugins.and(plugins, Plugins.And(ps.map(p => Plugins.Exclude(p)).toList)))

  private[sbt] def setPlugins(ns: Plugins): Project = copy2(plugins = ns)

  /** Definitively set the [[AutoPlugin]]s for this project. */
  private[sbt] def setAutoPlugins(autos: Seq[AutoPlugin]): Project = copy2(autoPlugins = autos)

  /** Definitively set the [[ProjectOrigin]] for this project. */
  private[sbt] def setProjectOrigin(origin: ProjectOrigin): Project = copy2(projectOrigin = origin)
}

sealed trait ResolvedProject extends ProjectDefinition[ProjectRef] {

  /** The [[AutoPlugin]]s enabled for this project as computed from [[plugins]]. */
  def autoPlugins: Seq[AutoPlugin]

}
 */

object ProjectExtra extends ProjectExtra:
  val extraBuildsKey: AttributeKey[List[URI]] = AttributeKey[List[URI]](
    "extra-builds",
    "Extra build URIs to load in addition to the ones defined by the project."
  )
  val projectReturnKey: AttributeKey[List[File]] =
    AttributeKey[List[File]]("project-return", "Maintains a stack of builds visited using reload.")

trait ProjectExtra extends Scoped.Syntax:
  import ProjectExtra.projectReturnKey

  def inConfig(conf: Configuration)(ss: Seq[Setting[?]]): Seq[Setting[?]] =
    Project.inScope(ThisScope.copy(config = Select(conf)))((Keys.configuration :== conf) +: ss)

  extension (self: Project)
    /** Adds configurations to this project.  Added configurations replace existing configurations with the same name. */
    def overrideConfigs(cs: Configuration*): Project =
      self.copy(
        configurations = Defaults.overrideConfigs(cs*)(self.configurations),
      )

    /**
     * Adds configuration at the *start* of the configuration list for this project.  Previous configurations replace this prefix
     * list with the same name.
     */
    private[sbt] def prefixConfigs(cs: Configuration*): Project =
      self.copy(
        configurations = Defaults.overrideConfigs(self.configurations*)(cs),
      )

  extension (m: Project.type)
    /*

     */

    /*
    private abstract class ProjectDef[PR <: ProjectReference](
        val id: String,
        val base: File,
        val aggregate: Seq[PR],
        val dependencies: Seq[ClasspathDep[PR]],
        val settings: Seq[Def.Setting[_]],
        val configurations: Seq[Configuration],
        val plugins: Plugins,
        val autoPlugins: Seq[AutoPlugin],
        val projectOrigin: ProjectOrigin
    ) extends ProjectDefinition[PR] {
      // checks for cyclic references here instead of having to do it in Scope.delegates
      Dag.topologicalSort(configurations)(_.extendsConfigs)
    }
     */

    def showContextKey(state: State): Show[ScopedKey[?]] =
      showContextKey(state, None)

    def showContextKey(state: State, keyNameColor: Option[String]): Show[ScopedKey[?]] =
      if (isProjectLoaded(state)) showContextKey2(session(state), keyNameColor)
      else Def.showFullKey

    // @deprecated("Use showContextKey2 which doesn't take the unused structure param", "1.1.1")
    // def showContextKey(
    //     session: SessionSettings,
    //     structure: BuildStructure,
    //     keyNameColor: Option[String] = None
    // ): Show[ScopedKey[_]] =
    //   showContextKey2(session, keyNameColor)

    def showContextKey2(
        session: SessionSettings,
        keyNameColor: Option[String] = None
    ): Show[ScopedKey[?]] =
      Def.showRelativeKey2(session.current, keyNameColor)

    def showLoadingKey(
        loaded: LoadedBuild,
        keyNameColor: Option[String] = None
    ): Show[ScopedKey[?]] =
      Def.showRelativeKey2(
        ProjectRef(loaded.root, loaded.units(loaded.root).rootProjects.head),
        keyNameColor
      )

    def getOrError[T](state: State, key: AttributeKey[T], msg: String): T =
      state.get(key).getOrElse(sys.error(msg))

    def structure(state: State): BuildStructure =
      Project.getOrError(state, Keys.stateBuildStructure, "No build loaded.")

    def session(state: State): SessionSettings =
      Project.getOrError(state, Keys.sessionSettings, "Session not initialized.")

    def isProjectLoaded(state: State): Boolean =
      (state has Keys.sessionSettings) && (state has Keys.stateBuildStructure)

    def extract(state: State): Extracted =
      Project.extract(Project.session(state), Project.structure(state))

    private[sbt] def extract(se: SessionSettings, st: BuildStructure): Extracted =
      Extracted(st, se, se.current)(using Project.showContextKey2(se))

    def getProjectForReference(ref: Reference, structure: BuildStructure): Option[ResolvedProject] =
      ref match
        case pr: ProjectRef => getProject(pr, structure)
        case _              => None

    def getProject(ref: ProjectRef, structure: BuildStructure): Option[ResolvedProject] =
      getProject(ref, structure.units)

    def getProject(ref: ProjectRef, structure: LoadedBuild): Option[ResolvedProject] =
      getProject(ref, structure.units)

    def getProject(ref: ProjectRef, units: Map[URI, LoadedBuildUnit]): Option[ResolvedProject] =
      (units get ref.build).flatMap(_.defined get ref.project)

    def runUnloadHooks(s: State): State =
      val previousOnUnload = orIdentity(s.get(Keys.onUnload.key))
      previousOnUnload(s.runExitHooks())

    def setProject(session: SessionSettings, structure: BuildStructure, s: State): State =
      setProject(session, structure, s, identity)

    def setProject(
        session: SessionSettings,
        structure: BuildStructure,
        s: State,
        preOnLoad: State => State
    ): State = {
      val unloaded = Project.runUnloadHooks(s)
      val (onLoad, onUnload) = getHooks(structure.data)
      val newAttrs = unloaded.attributes
        .put(stateBuildStructure, structure)
        .put(sessionSettings, session)
        .put(Keys.onUnload.key, onUnload)
      val newState = unloaded.copy(attributes = newAttrs)
      // TODO: Fix this
      onLoad(
        preOnLoad(
          updateCurrent(newState)
        ) /*LogManager.setGlobalLogLevels(updateCurrent(newState), structure.data)*/
      )
    }

    def orIdentity[A](opt: Option[A => A]): A => A =
      opt.getOrElse(identity)

    def getHook[A](key: SettingKey[A => A], data: Def.Settings): A => A =
      orIdentity((Global / key).get(data))

    def getHooks(data: Def.Settings): (State => State, State => State) =
      (getHook(Keys.onLoad, data), getHook(Keys.onUnload, data))

    def current(state: State): ProjectRef = session(state).current

    def updateCurrent(s: State): State = {
      val structure = Project.structure(s)
      val ref = Project.current(s)
      Load.getProject(structure.units, ref.build, ref.project)
      val msg = (ref / Keys.onLoadMessage).get(structure.data).getOrElse("")
      if (!msg.isEmpty) s.log.info(msg)
      def get[T](k: SettingKey[T]): Option[T] = (ref / k).get(structure.data)
      def commandsIn(axis: ResolvedReference) = (axis / commands).get(structure.data).toList

      val allCommands = commandsIn(ref) ++ commandsIn(
        BuildRef(ref.build)
      ) ++ (Global / commands).get(structure.data).toList
      val history = get(historyPath).flatMap(identity)
      val prompt = get(shellPrompt)
      val newPrompt = get(colorShellPrompt)
      val trs = ((Global / templateResolverInfos).get(structure.data)).toList.flatten
      val startSvr: Option[Boolean] = get(autoStartServer)
      val host: Option[String] = get(serverHost)
      val port: Option[Int] = get(serverPort)
      val enabledBsp: Option[Boolean] = get(bspEnabled)
      val timeout: Option[Option[FiniteDuration]] = get(serverIdleTimeout)
      val authentication: Option[Set[ServerAuthentication]] = get(serverAuthentication)
      val connectionType: Option[ConnectionType] = get(serverConnectionType)
      val srvLogLevel: Option[Level.Value] = (ref / serverLog / logLevel).get(structure.data)
      val hs: Option[Seq[ServerHandler]] = get(ThisBuild / fullServerHandlers)
      val caches: Option[Seq[ActionCacheStore]] = get(cacheStores)
      val rod: Option[NioPath] = get(rootOutputDirectory)
      val fileConverter: Option[FileConverter] = get(Keys.fileConverter)
      val commandDefs = allCommands.distinct.flatten[Command].map(_.tag(projectCommand, true))
      val newDefinedCommands = commandDefs ++ BasicCommands.removeTagged(
        s.definedCommands,
        projectCommand
      )
      val winSecurityLevel = get(windowsServerSecurityLevel).getOrElse(2)
      val useJni = get(serverUseJni).getOrElse(false)
      val newAttrs =
        s.attributes
          .put(historyPath.key, history)
          .put(windowsServerSecurityLevel.key, winSecurityLevel)
          .put(serverUseJni.key, useJni)
          .setCond(bspEnabled.key, enabledBsp)
          .setCond(autoStartServer.key, startSvr)
          .setCond(serverPort.key, port)
          .setCond(serverHost.key, host)
          .setCond(serverAuthentication.key, authentication)
          .setCond(serverConnectionType.key, connectionType)
          .setCond(serverIdleTimeout.key, timeout)
          .put(historyPath.key, history)
          .put(templateResolverInfos.key, trs)
          .setCond(shellPrompt.key, prompt)
          .setCond(colorShellPrompt.key, newPrompt)
          .setCond(BasicKeys.serverLogLevel, srvLogLevel)
          .setCond(fullServerHandlers.key, hs)
          .setCond(cacheStores.key, caches)
          .setCond(rootOutputDirectory.key, rod)
          .setCond(BasicKeys.fileConverter, fileConverter)
      s.copy(
        attributes = newAttrs,
        definedCommands = newDefinedCommands
      )
    }

    def setCond[T](key: AttributeKey[T], vopt: Option[T], attributes: AttributeMap): AttributeMap =
      attributes.setCond(key, vopt)

    private[sbt] def equalKeys(a: ScopedKey[?], b: ScopedKey[?], mask: ScopeMask): Boolean =
      a.key == b.key && Scope.equal(a.scope, b.scope, mask)

    def delegates(
        structure: BuildStructure,
        scope: Scope,
        key: AttributeKey[?]
    ): Seq[ScopedKey[?]] =
      structure.delegates(scope).map(d => ScopedKey(d, key))

    private[sbt] def scopedKeyData(
        structure: BuildStructure,
        key: ScopedKey[?]
    ): Option[ScopedKeyData[?]] =
      structure.data.getKeyValue(key).map((defining, value) => ScopedKeyData(key, defining, value))

    def details(structure: BuildStructure, actual: Boolean, key: ScopedKey[?])(using
        display: Show[ScopedKey[?]]
    ): String = {
      val data = scopedKeyData(structure, key).map(_.description).getOrElse("No entry for key.")
      val description = key.key.description match
        case Some(desc) => s"Description:\n\t$desc\n"
        case None       => ""

      val (definingKey, providedBy) = structure.data.definingKey(key) match
        case Some(k) => k -> s"Provided by:\n\t${Scope.display(k.scope, key.key.label)}\n"
        case None    => key -> ""
      val comp =
        Def.compiled(structure.settings, actual)(using
          structure.delegates,
          structure.scopeLocal,
          display
        )
      val definedAt = comp
        .get(definingKey)
        .map(c => Def.definedAtString(c.settings).capitalize)
        .getOrElse("")

      val cMap = Def.flattenLocals(comp)
      val related = cMap.keys.filter(k => k.key == key.key && k.scope != key.scope)
      def derivedDependencies(c: ScopedKey[?]): List[ScopedKey[?]] =
        comp
          .get(c)
          .map(_.settings.flatMap(s => if (s.isDerived) s.dependencies else Nil))
          .toList
          .flatten

      val depends = cMap.get(key) match
        case Some(c) => c.dependencies.toSet
        case None    => Set.empty
      val derivedDepends: Set[ScopedKey[?]] = derivedDependencies(definingKey).toSet

      val reverse = Project.reverseDependencies(cMap, key)
      val derivedReverse =
        reverse.filter(r => derivedDependencies(r).contains(definingKey)).toSet

      def printDepScopes(
          baseLabel: String,
          derivedLabel: String,
          scopes: Iterable[ScopedKey[?]],
          derived: Set[ScopedKey[?]]
      ): String = {
        val label = s"$baseLabel${if (derived.isEmpty) "" else s" (D=$derivedLabel)"}"
        val prefix: ScopedKey[?] => String =
          if (derived.isEmpty) const("") else sk => if (derived(sk)) "D " else "  "
        printScopes(label, scopes, prefix = prefix)
      }

      def printScopes(
          label: String,
          scopes: Iterable[ScopedKey[?]],
          max: Int = Int.MaxValue,
          prefix: ScopedKey[?] => String = const("")
      ) =
        if (scopes.isEmpty) ""
        else {
          val (limited, more) =
            if (scopes.size <= max) (scopes, "\n") else (scopes.take(max), "\n...\n")
          limited.map(sk => prefix(sk) + display.show(sk)).mkString(label + ":\n\t", "\n\t", more)
        }

      data + "\n" +
        description +
        providedBy +
        definedAt +
        printDepScopes("Dependencies", "derived from", depends, derivedDepends) +
        printDepScopes("Reverse dependencies", "derives", reverse, derivedReverse) +
        printScopes("Delegates", delegates(structure, key.scope, key.key)) +
        printScopes("Related", related, 10)
    }

    def settingGraph(structure: BuildStructure, basedir: File, scoped: ScopedKey[?])(using
        display: Show[ScopedKey[?]]
    ): SettingGraph =
      SettingGraph(structure, basedir, scoped, 0)

    /*
    def graphSettings(structure: BuildStructure, basedir: File)(implicit
        display: Show[ScopedKey[_]]
    ): Unit = {
      def graph(actual: Boolean, name: String) =
        graphSettings(structure, actual, name, new File(basedir, name + ".dot"))
      graph(true, "actual_dependencies")
      graph(false, "declared_dependencies")
    }
    def graphSettings(structure: BuildStructure, actual: Boolean, graphName: String, file: File)(
        implicit display: Show[ScopedKey[_]]
    ): Unit = {
      val rel = relation(structure, actual)
      val keyToString = display.show _
      DotGraph.generateGraph(file, graphName, rel, keyToString, keyToString)
    }
     */

    def relation(structure: BuildStructure, actual: Boolean)(using
        display: Show[ScopedKey[?]]
    ): Relation[ScopedKey[?], ScopedKey[?]] =
      relation(structure.settings, actual)(using
        structure.delegates,
        structure.scopeLocal,
        display,
      )

    private[sbt] def relation(settings: Seq[Def.Setting[?]], actual: Boolean)(using
        delegates: Scope => Seq[Scope],
        scopeLocal: Def.ScopeLocal,
        display: Show[ScopedKey[?]]
    ): Relation[ScopedKey[?], ScopedKey[?]] =
      val cMap = Def.flattenLocals(Def.compiled(settings, actual))
      val emptyRelation = Relation.empty[ScopedKey[?], ScopedKey[?]]
      cMap.foldLeft(emptyRelation) { case (r, (key, value)) =>
        r + (key, value.dependencies)
      }

    private[sbt] def showDefinitions(key: AttributeKey[?], defs: Seq[Scope])(using
        display: Show[ScopedKey[?]]
    ): String =
      showKeys(defs.map(scope => ScopedKey(scope, key)))

    private[sbt] def showUses(defs: Seq[ScopedKey[?]])(using display: Show[ScopedKey[?]]): String =
      showKeys(defs)

    private def showKeys(s: Seq[ScopedKey[?]])(using display: Show[ScopedKey[?]]): String =
      s.map(display.show).sorted.mkString("\n\t", "\n\t", "\n\n")

    private[sbt] def definitions(structure: BuildStructure, actual: Boolean, key: AttributeKey[?])(
        using display: Show[ScopedKey[?]]
    ): Seq[Scope] =
      relation(structure, actual)(using display)._1s.toSeq flatMap { sk =>
        if (sk.key == key) sk.scope :: Nil else Nil
      }

    private[sbt] def usedBy(structure: BuildStructure, actual: Boolean, key: AttributeKey[?])(using
        display: Show[ScopedKey[?]]
    ): Seq[ScopedKey[?]] =
      relation(structure, actual)(using display).all.toSeq flatMap { case (a, b) =>
        if (b.key == key) List[ScopedKey[?]](a) else Nil
      }

    def reverseDependencies(
        cMap: Map[ScopedKey[?], Flattened],
        scoped: ScopedKey[?]
    ): Iterable[ScopedKey[?]] =
      for {
        (key, compiled) <- cMap
        dep <- compiled.dependencies if dep == scoped
      } yield key

    /*
    def setAll(extracted: Extracted, settings: Seq[Def.Setting[_]]): SessionSettings =
      SettingCompletions.setAll(extracted, settings).session
     */

    def extraBuilds(s: State): List[URI] =
      getOrNil(s, ProjectExtra.extraBuildsKey)
    def getOrNil[A](s: State, key: AttributeKey[List[A]]): List[A] =
      s.get(key).getOrElse(Nil)
    def setExtraBuilds(s: State, extra: List[URI]): State =
      s.put(ProjectExtra.extraBuildsKey, extra)
    def addExtraBuilds(s: State, extra: List[URI]): State =
      setExtraBuilds(s, extra ::: extraBuilds(s))
    def removeExtraBuilds(s: State, remove: List[URI]): State =
      updateExtraBuilds(s, _.filterNot(remove.toSet))
    def updateExtraBuilds(s: State, f: List[URI] => List[URI]): State =
      setExtraBuilds(s, f(extraBuilds(s)))

    // used by Coursier integration
    private[sbt] def transitiveInterDependencies(
        state: State,
        projectRef: ProjectRef
    ): Seq[ProjectRef] = {
      def dependencies(map: Map[ProjectRef, Seq[ProjectRef]], id: ProjectRef): Set[ProjectRef] = {
        def helper(map: Map[ProjectRef, Seq[ProjectRef]], acc: Set[ProjectRef]): Set[ProjectRef] =
          if (acc.exists(map.contains)) {
            val (kept, rem) = map.partition { case (k, _) => acc(k) }
            helper(rem, acc ++ kept.valuesIterator.flatten)
          } else acc
        helper(map - id, map.getOrElse(id, Nil).toSet)
      }
      val allProjectsDeps: Map[ProjectRef, Seq[ProjectRef]] =
        (for {
          (p, ref) <- Project.structure(state).allProjectPairs
        } yield ref -> p.dependencies.map(_.project)).toMap
      val deps = dependencies(allProjectsDeps.toMap, projectRef)
      Project.structure(state).allProjectRefs.filter(p => deps(p))
    }

    def projectReturn(s: State): List[File] = getOrNil(s, projectReturnKey)
    def inPluginProject(s: State): Boolean = projectReturn(s).length > 1
    def setProjectReturn(s: State, pr: List[File]): State =
      s.copy(attributes = s.attributes.put(projectReturnKey, pr))

    def loadAction(s: State, action: LoadAction): (State, File) =
      action match
        case LoadAction.Return =>
          projectReturn(s) match
            case _ /* current */ :: returnTo :: rest =>
              (setProjectReturn(s, returnTo :: rest), returnTo)
            case _ => sys.error("Not currently in a plugin definition")

        case LoadAction.Current =>
          val base = s.configuration.baseDirectory
          projectReturn(s) match
            case Nil => (setProjectReturn(s, base :: Nil), base); case x :: _ => (s, x)

        case LoadAction.Plugins =>
          val (newBase, oldStack) =
            if Project.isProjectLoaded(s) then
              (Project.extract(s).currentUnit.unit.plugins.base, projectReturn(s))
            else // support changing to the definition project if it fails to load
              (BuildPaths.projectStandard(s.baseDir), s.baseDir :: Nil)
          val newS = setProjectReturn(s, newBase :: oldStack)
          (newS, newBase)

    /*
    def runTask[T](
        taskKey: ScopedKey[Task[T]],
        state: State,
        checkCycles: Boolean = false
    ): Option[(State, Result[T])] = {
      val extracted = Project.extract(state)
      val ch = EvaluateTask.cancelStrategy(extracted, extracted.structure, state)
      val p = EvaluateTask.executeProgress(extracted, extracted.structure, state)
      val r = EvaluateTask.restrictions(state)
      val fgc = EvaluateTask.forcegc(extracted, extracted.structure)
      val mfi = EvaluateTask.minForcegcInterval(extracted, extracted.structure)
      runTask(taskKey, state, EvaluateTaskConfig(r, checkCycles, p, ch, fgc, mfi))
    }

    def runTask[T](
        taskKey: ScopedKey[Task[T]],
        state: State,
        config: EvaluateTaskConfig
    ): Option[(State, Result[T])] = {
      val extracted = Project.extract(state)
      EvaluateTask(extracted.structure, taskKey, state, extracted.currentRef, config)
    }

    def projectToRef(p: Project): ProjectReference = LocalProject(p.id)

     */

  given projectToLocalProject: Conversion[Project, LocalProject] =
    (p: Project) => LocalProject(p.id)

  given classpathDependency[A](using
      Conversion[A, ProjectReference]
  ): Conversion[A, ClasspathDep[ProjectReference]] =
    (a: A) => ClasspathDependency(a, None)

  extension (p: ProjectReference)
    def %(conf: Configuration): ClasspathDependency = %(conf.name)
    @targetName("percentString")
    def %(conf: String): ClasspathDependency = ClasspathDependency(p, Some(conf))

  extension [A1](in: Def.Initialize[Task[A1]])
    def updateState(f: (State, A1) => State): Def.Initialize[Task[A1]] =
      in(t => SessionVar.transform(t, f))

    def storeAs(key: TaskKey[A1])(using f: JsonFormat[A1]): Def.Initialize[Task[A1]] =
      Keys.resolvedScoped.zipWith(in) { (scoped, task) =>
        SessionVar.transform(
          task,
          (state, value) =>
            SessionVar.persistAndSet(
              SessionVar.resolveContext(key, scoped.scope, state),
              state,
              value
            )(using f)
        )
      }

    def keepAs(key: TaskKey[A1]): Def.Initialize[Task[A1]] =
      in.zipWith(Keys.resolvedScoped) { (t, scoped) =>
        SessionVar.transform(
          t,
          (state, value) =>
            SessionVar.set(SessionVar.resolveContext(key, scoped.scope, state), state, value)
        )
      }

  /**
   * implicitly injected to tasks that return PromiseWrap.
   */
  extension [A1](in: Initialize[Task[PromiseWrap[A1]]])
    def await: Def.Initialize[Task[A1]] = await(Duration.Inf)
    def await(atMost: Duration): Def.Initialize[Task[A1]] =
      (Def
        .task {
          val p = in.value
          var result: Option[A1] = None
          if atMost == Duration.Inf then
            while result.isEmpty do
              try {
                result = Some(Await.result(p.underlying.future, Duration("1s")))
                Thread.sleep(10)
              } catch {
                case _: TimeoutException => ()
              }
          else result = Some(Await.result(p.underlying.future, atMost))
          result.get
        })
        .tag(Tags.Sentinel)

  /*
    import scala.reflect.macros._

    def projectMacroImpl(c: blackbox.Context): c.Expr[Project] = {
      import c.universe._
      val enclosingValName = std.KeyMacro.definingValName(
        c,
        methodName =>
          s"""$methodName must be directly assigned to a val, such as `val x = $methodName`. Alternatively, you can use `sbt.Project.apply`"""
      )
      val name = c.Expr[String](Literal(Constant(enclosingValName)))
      reify { Project(name.splice, new File(name.splice)) }
    }

  implicit def configDependencyConstructor[T](
      p: T
  )(implicit ev: T => ProjectReference): Constructor =
    new Constructor(p)
   */

  implicit def classpathDependency[T](p: T)(using
      ev: T => ProjectReference
  ): ClasspathDependency =
    ClasspathDependency(ev(p), None)

  // Duplicated with Structure

  // These used to be in Project so that they didn't need to get imported (due to Initialize being nested in Project).
  // Moving Initialize and other settings types to Def and decoupling Project, Def, and Structure means these go here for now
  // given richInitializeTask[T]: Conversion[Initialize[Task[T]], Scoped.RichInitializeTask[T]] =
  // (init: Initialize[Task[T]]) => new Scoped.RichInitializeTask(init)

  /*
  implicit def richInitializeInputTask[T](
      init: Initialize[InputTask[T]]
  ): Scoped.RichInitializeInputTask[T] =
    new Scoped.RichInitializeInputTask(init)

  implicit def richInitialize[T](i: Initialize[T]): Scoped.RichInitialize[T] =
    new Scoped.RichInitialize[T](i)

  implicit def richTaskSessionVar[T](init: Initialize[Task[T]]): Project.RichTaskSessionVar[T] =
    new Project.RichTaskSessionVar(init)

  implicit def sbtRichTaskPromise[A](
      i: Initialize[Task[PromiseWrap[A]]]
  ): Project.RichTaskPromise[A] =
    new Project.RichTaskPromise(i)
   */
end ProjectExtra
