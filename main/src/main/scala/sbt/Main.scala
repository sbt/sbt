/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt

import sbt.internal.{
  Act,
  Aggregation,
  BuildStructure,
  BuildUnit,
  CommandExchange,
  CommandStrings,
  DefaultBackgroundJobService,
  EvaluateConfigurations,
  Inspect,
  IvyConsole,
  Load,
  LoadedBuildUnit,
  LogManager,
  Output,
  PluginsDebug,
  ProjectNavigation,
  Script,
  SessionSettings,
  SetResult,
  SettingCompletions
}
import sbt.internal.util.{
  AttributeKey,
  AttributeMap,
  ConsoleOut,
  GlobalLogging,
  LineRange,
  MainAppender,
  SimpleReader,
  Types
}
import sbt.util.{ Level, Logger, Show }

import sbt.internal.util.complete.{ DefaultParsers, Parser }
import sbt.internal.inc.ScalaInstance
import sbt.compiler.EvalImports
import Types.{ const, idFun }
import Aggregation.AnyKeys
import Project.LoadAction
import xsbti.compile.CompilerCache

import scala.annotation.tailrec
import sbt.io.IO
import sbt.io.syntax._
import StandardMain._

import java.io.{ File, IOException }
import java.net.URI
import java.util.{ Locale, Properties }

import scala.util.control.NonFatal
import BasicCommandStrings.{ Shell, TemplateCommand }
import CommandStrings.BootCommand

/** This class is the entry point for sbt. */
final class xMain extends xsbti.AppMain {
  def run(configuration: xsbti.AppConfiguration): xsbti.MainResult = {
    import BasicCommands.early
    import BasicCommandStrings.runEarly
    import BuiltinCommands.defaults
    import sbt.internal.CommandStrings.{ BootCommand, DefaultsCommand, InitCommand }
    val state = initialState(
      configuration,
      Seq(defaults, early),
      runEarly(DefaultsCommand) :: runEarly(InitCommand) :: BootCommand :: Nil)
    runManaged(state)
  }
}

final class ScriptMain extends xsbti.AppMain {
  def run(configuration: xsbti.AppConfiguration): xsbti.MainResult = {
    import BasicCommandStrings.runEarly
    runManaged(
      initialState(
        configuration,
        BuiltinCommands.ScriptCommands,
        runEarly(Level.Error.toString) :: Script.Name :: Nil
      ))
  }
}

final class ConsoleMain extends xsbti.AppMain {
  def run(configuration: xsbti.AppConfiguration): xsbti.MainResult =
    runManaged(
      initialState(
        configuration,
        BuiltinCommands.ConsoleCommands,
        IvyConsole.Name :: Nil
      ))
}

object StandardMain {
  private[sbt] lazy val exchange = new CommandExchange()

  def runManaged(s: State): xsbti.MainResult = {
    val previous = TrapExit.installManager()
    try {
      try {
        try {
          MainLoop.runLogged(s)
        } finally exchange.shutdown
      } finally DefaultBackgroundJobService.backgroundJobService.shutdown()
    } finally TrapExit.uninstallManager(previous)
  }

  /** The common interface to standard output, used for all built-in ConsoleLoggers. */
  val console: ConsoleOut =
    ConsoleOut.systemOutOverwrite(ConsoleOut.overwriteContaining("Resolving "))

  def initialGlobalLogging: GlobalLogging =
    GlobalLogging.initial(MainAppender.globalDefault(console),
                          File.createTempFile("sbt", ".log"),
                          console)

  def initialState(configuration: xsbti.AppConfiguration,
                   initialDefinitions: Seq[Command],
                   preCommands: Seq[String]): State = {
    import BasicCommandStrings.isEarlyCommand
    val userCommands = configuration.arguments.map(_.trim)
    val (earlyCommands, normalCommands) = (preCommands ++ userCommands).partition(isEarlyCommand)
    val commands = (earlyCommands ++ normalCommands).toList map { x =>
      Exec(x, None)
    }
    val initAttrs = BuiltinCommands.initialAttributes
    val s = State(
      configuration,
      initialDefinitions,
      Set.empty,
      None,
      commands,
      State.newHistory,
      initAttrs,
      initialGlobalLogging,
      None,
      State.Continue
    )
    s.initializeClassLoaderCache
  }
}

import DefaultParsers._
import sbt.internal.CommandStrings._
import BasicCommandStrings._
import BasicCommands._
import CommandUtil._
import TemplateCommandUtil.templateCommand

object BuiltinCommands {
  def initialAttributes = AttributeMap.empty

  def ConsoleCommands: Seq[Command] =
    Seq(ignore, exit, IvyConsole.command, setLogLevel, early, act, nop)

  def ScriptCommands: Seq[Command] =
    Seq(ignore, exit, Script.command, setLogLevel, early, act, nop)

  def DefaultCommands: Seq[Command] =
    Seq(
      about,
      tasks,
      settingsCommand,
      loadProject,
      templateCommand,
      projects,
      project,
      set,
      sessionCommand,
      inspect,
      loadProjectImpl,
      loadFailed,
      Cross.crossBuild,
      Cross.switchVersion,
      PluginCross.pluginCross,
      PluginCross.pluginSwitch,
      Cross.crossRestoreSession,
      setLogLevel,
      plugin,
      plugins,
      writeSbtVersion,
      notifyUsersAboutShell,
      shell,
      startServer,
      eval,
      last,
      lastGrep,
      export,
      boot,
      initialize,
      act
    ) ++ allBasicCommands

  def DefaultBootCommands: Seq[String] =
    WriteSbtVersion :: LoadProject :: NotifyUsersAboutShell :: s"$IfLast $Shell" :: Nil

  def boot = Command.make(BootCommand)(bootParser)

  def about = Command.command(AboutCommand, aboutBrief, aboutDetailed) { s =>
    s.log.info(aboutString(s)); s
  }

  def setLogLevel = Command.arb(const(logLevelParser), logLevelHelp)(LogManager.setGlobalLogLevel)
  private[this] def logLevelParser: Parser[Level.Value] =
    oneOf(Level.values.toSeq.map(v => v.toString ^^^ v))

  // This parser schedules the default boot commands unless overridden by an alias
  def bootParser(s: State) = {
    val orElse = () => DefaultBootCommands.toList ::: s
    delegateToAlias(BootCommand, success(orElse))(s)
  }

  def sbtName(s: State): String = s.configuration.provider.id.name
  def sbtVersion(s: State): String = s.configuration.provider.id.version
  def scalaVersion(s: State): String = s.configuration.provider.scalaProvider.version
  def aboutProject(s: State): String =
    if (Project.isProjectLoaded(s)) {
      val e = Project.extract(s)
      val version = e.getOpt(Keys.version) match { case None => ""; case Some(v) => " " + v }
      val current = "The current project is " + Reference.display(e.currentRef) + version + "\n"
      val sc = aboutScala(s, e)
      val built = if (sc.isEmpty) "" else "The current project is built against " + sc + "\n"
      current + built + aboutPlugins(e)
    } else "No project is currently loaded"

  def aboutPlugins(e: Extracted): String = {
    def list(b: BuildUnit) = b.plugins.detected.autoPlugins.map(_.value.label)
    val allPluginNames = e.structure.units.values.flatMap(u => list(u.unit)).toSeq.distinct
    if (allPluginNames.isEmpty) "" else allPluginNames.mkString("Available Plugins: ", ", ", "")
  }

  def aboutScala(s: State, e: Extracted): String = {
    val scalaVersion = e.getOpt(Keys.scalaVersion)
    val scalaHome = e.getOpt(Keys.scalaHome).flatMap(idFun)
    val instance =
      e.getOpt(Keys.scalaInstance).flatMap(_ => quiet(e.runTask(Keys.scalaInstance, s)._2))
    (scalaVersion, scalaHome, instance) match {
      case (sv, Some(home), Some(si)) =>
        "local Scala version " + selectScalaVersion(sv, si) + " at " + home.getAbsolutePath
      case (_, Some(home), None)  => "a local Scala build at " + home.getAbsolutePath
      case (sv, None, Some(si))   => "Scala " + selectScalaVersion(sv, si)
      case (Some(sv), None, None) => "Scala " + sv
      case (None, None, None)     => ""
    }
  }

  def aboutString(s: State): String = {
    val (name, ver, scalaVer, about) =
      (sbtName(s), sbtVersion(s), scalaVersion(s), aboutProject(s))
    """This is %s %s
			|%s
			|%s, %s plugins, and build definitions are using Scala %s
			|""".stripMargin.format(name, ver, about, name, name, scalaVer)
  }

  private[this] def selectScalaVersion(sv: Option[String], si: ScalaInstance): String =
    sv match {
      case Some(si.version) => si.version
      case _                => si.actualVersion
    }

  private[this] def quiet[T](t: => T): Option[T] = try { Some(t) } catch {
    case e: Exception => None
  }

  def settingsCommand: Command =
    showSettingLike(SettingsCommand,
                    settingsPreamble,
                    KeyRanks.MainSettingCutoff,
                    key => !isTask(key.manifest))

  def tasks: Command =
    showSettingLike(TasksCommand,
                    tasksPreamble,
                    KeyRanks.MainTaskCutoff,
                    key => isTask(key.manifest))

  def showSettingLike(command: String,
                      preamble: String,
                      cutoff: Int,
                      keep: AttributeKey[_] => Boolean): Command =
    Command(command, settingsBrief(command), settingsDetailed(command))(showSettingParser(keep)) {
      case (s: State, (verbosity: Int, selected: Option[String])) =>
        if (selected.isEmpty) System.out.println(preamble)
        val prominentOnly = verbosity <= 1
        val verboseFilter = if (prominentOnly) highPass(cutoff) else topNRanked(25 * verbosity)
        System.out.println(tasksHelp(s, keys => verboseFilter(keys filter keep), selected))
        System.out.println()
        if (prominentOnly) System.out.println(moreAvailableMessage(command, selected.isDefined))
        s
    }
  def showSettingParser(keepKeys: AttributeKey[_] => Boolean)(
      s: State): Parser[(Int, Option[String])] =
    verbosityParser ~ selectedParser(s, keepKeys).?
  def selectedParser(s: State, keepKeys: AttributeKey[_] => Boolean): Parser[String] =
    singleArgument(allTaskAndSettingKeys(s).filter(keepKeys).map(_.label).toSet)
  def verbosityParser: Parser[Int] =
    success(1) | ((Space ~ "-") ~> (
      'v'.id.+.map(_.size + 1) |
        ("V" ^^^ Int.MaxValue)
    ))
  def taskDetail(keys: Seq[AttributeKey[_]]): Seq[(String, String)] =
    sortByLabel(withDescription(keys)) flatMap taskStrings

  def allTaskAndSettingKeys(s: State): Seq[AttributeKey[_]] = {
    val extracted = Project.extract(s)
    import extracted._
    val index = structure.index
    index.keyIndex
      .keys(Some(currentRef))
      .toSeq
      .map { key =>
        try Some(index.keyMap(key))
        catch {
          case NonFatal(ex) =>
            s.log debug ex.getMessage
            None
        }
      }
      .collect { case Some(s) => s }
      .distinct
  }

  def sortByLabel(keys: Seq[AttributeKey[_]]): Seq[AttributeKey[_]] = keys.sortBy(_.label)
  def sortByRank(keys: Seq[AttributeKey[_]]): Seq[AttributeKey[_]] = keys.sortBy(_.rank)
  def withDescription(keys: Seq[AttributeKey[_]]): Seq[AttributeKey[_]] =
    keys.filter(_.description.isDefined)
  def isTask(mf: Manifest[_])(implicit taskMF: Manifest[Task[_]],
                              inputMF: Manifest[InputTask[_]]): Boolean =
    mf.runtimeClass == taskMF.runtimeClass || mf.runtimeClass == inputMF.runtimeClass
  def topNRanked(n: Int) = (keys: Seq[AttributeKey[_]]) => sortByRank(keys).take(n)
  def highPass(rankCutoff: Int) =
    (keys: Seq[AttributeKey[_]]) => sortByRank(keys).takeWhile(_.rank <= rankCutoff)

  def tasksHelp(s: State,
                filter: Seq[AttributeKey[_]] => Seq[AttributeKey[_]],
                arg: Option[String]): String = {
    val commandAndDescription = taskDetail(filter(allTaskAndSettingKeys(s)))
    arg match {
      case Some(selected) => detail(selected, commandAndDescription.toMap)
      case None           => aligned("  ", "   ", commandAndDescription) mkString ("\n", "\n", "")
    }
  }

  def taskStrings(key: AttributeKey[_]): Option[(String, String)] = key.description map { d =>
    (key.label, d)
  }

  def defaults = Command.command(DefaultsCommand) { s =>
    s.copy(definedCommands = DefaultCommands)
  }

  def initialize: Command = Command.command(InitCommand) { s =>
    /*"load-commands -base ~/.sbt/commands" :: */
    readLines(readable(sbtRCs(s))).toList ::: s
  }

  def eval: Command = Command.single(EvalCommand, Help.more(EvalCommand, evalDetailed)) {
    (s, arg) =>
      if (Project.isProjectLoaded(s)) loadedEval(s, arg) else rawEval(s, arg)
      s
  }

  private[this] def loadedEval(s: State, arg: String): Unit = {
    val extracted = Project extract s
    import extracted._
    val result =
      session.currentEval().eval(arg, srcName = "<eval>", imports = autoImports(extracted))
    s.log.info(s"ans: ${result.tpe} = ${result.getValue(currentLoader)}")
  }

  private[this] def rawEval(s: State, arg: String): Unit = {
    val app = s.configuration.provider
    val classpath = app.mainClasspath ++ app.scalaProvider.jars
    val result = Load
      .mkEval(classpath, s.baseDir, Nil)
      .eval(arg, srcName = "<eval>", imports = new EvalImports(Nil, ""))
    s.log.info(s"ans: ${result.tpe} = ${result.getValue(app.loader)}")
  }

  def sessionCommand: Command =
    Command.make(SessionCommand, sessionBrief, SessionSettings.Help)(SessionSettings.command)

  def reapply(newSession: SessionSettings, structure: BuildStructure, s: State): State = {
    s.log.info("Reapplying settings...")
    // For correct behavior, we also need to re-inject a settings logger, as we'll be re-evaluating settings
    val loggerInject = LogManager.settingsLogger(s)
    val withLogger = newSession.appendRaw(loggerInject :: Nil)
    val show = Project.showContextKey(newSession, structure)
    val newStructure = Load.reapply(withLogger.mergeSettings, structure)(show)
    Project.setProject(newSession, newStructure, s)
  }

  def set: Command = Command(SetCommand, setBrief, setDetailed)(setParser) {
    case (s, (all, arg)) =>
      val extracted = Project extract s
      import extracted._
      val dslVals = extracted.currentUnit.unit.definitions.dslDefinitions
      // TODO - This is possibly inefficient (or stupid).  We should try to only attach the
      // classloader + imports NEEDED to compile the set command, rather than
      // just ALL of them.
      val ims = (imports(extracted) ++ dslVals.imports.map(i => (i, -1)))
      val cl = dslVals.classloader(currentLoader)
      val settings = EvaluateConfigurations.evaluateSetting(
        session.currentEval(),
        "<set>",
        ims,
        arg,
        LineRange(0, 0)
      )(cl)
      val setResult =
        if (all) SettingCompletions.setAll(extracted, settings)
        else SettingCompletions.setThis(s, extracted, settings, arg)
      s.log.info(setResult.quietSummary)
      s.log.debug(setResult.verboseSummary)
      reapply(setResult.session, structure, s)
  }

  def setThis(
      s: State,
      extracted: Extracted,
      settings: Seq[Def.Setting[_]],
      arg: String
  ): SetResult =
    SettingCompletions.setThis(s, extracted, settings, arg)

  def inspect: Command = Command(InspectCommand, inspectBrief, inspectDetailed)(Inspect.parser) {
    case (s, (option, sk)) =>
      s.log.info(Inspect.output(s, option, sk))
      s
  }

  def lastGrep: Command =
    Command(LastGrepCommand, lastGrepBrief, lastGrepDetailed)(lastGrepParser) {
      case (s, (pattern, Some(sks))) =>
        val (str, _, display) = extractLast(s)
        Output.lastGrep(sks, str.streams(s), pattern, printLast(s))(display)
        keepLastLog(s)
      case (s, (pattern, None)) =>
        for (logFile <- lastLogFile(s)) yield Output.lastGrep(logFile, pattern, printLast(s))
        keepLastLog(s)
    }

  def extractLast(s: State): (BuildStructure, Select[ProjectRef], Show[Def.ScopedKey[_]]) = {
    val ext = Project.extract(s)
    (ext.structure, Select(ext.currentRef), ext.showKey)
  }

  def setParser = (s: State) => {
    val extracted = Project.extract(s)
    import extracted._
    token(Space ~> flag("every" ~ Space)) ~
      SettingCompletions.settingParser(structure.data, structure.index.keyMap, currentProject)
  }

  import Def.ScopedKey
  type KeysParser = Parser[Seq[ScopedKey[T]] forSome { type T }]

  val spacedAggregatedParser: State => KeysParser = (s: State) =>
    Act.requireSession(s, token(Space) ~> Act.aggregatedKeyParser(s))

  val aggregatedKeyValueParser: State => Parser[Option[AnyKeys]] = (s: State) =>
    spacedAggregatedParser(s).map(x => Act.keyValues(s)(x)).?

  val exportParser: State => Parser[() => State] = (s: State) =>
    Act.requireSession(s, token(Space) ~> exportParser0(s))

  private[sbt] def exportParser0(s: State): Parser[() => State] = {
    val extracted = Project extract s
    import extracted.{ showKey, structure }
    val keysParser = token(flag("--last" <~ Space)) ~ Act.aggregatedKeyParser(extracted)
    val show = Aggregation.ShowConfig(
      settingValues = true,
      taskValues = false,
      print = println(_),
      success = false
    )
    for {
      lastOnly_keys <- keysParser
      kvs = Act.keyValues(structure)(lastOnly_keys._2)
      f <- if (lastOnly_keys._1) success(() => s)
      else Aggregation.evaluatingParser(s, structure, show)(kvs)
    } yield
      () => {
        def export0(s: State): State = lastImpl(s, kvs, Some(ExportStream))
        val newS = try f()
        catch {
          case NonFatal(e) =>
            try export0(s)
            finally { throw e }
        }
        export0(newS)
      }
  }

  def lastGrepParser(s: State): Parser[(String, Option[AnyKeys])] =
    Act.requireSession(
      s,
      (token(Space) ~> token(NotSpace, "<pattern>")) ~ aggregatedKeyValueParser(s)
    )

  def last: Command = Command(LastCommand, lastBrief, lastDetailed)(aggregatedKeyValueParser) {
    case (s, Some(sks)) => lastImpl(s, sks, None)
    case (s, None) =>
      for (logFile <- lastLogFile(s)) yield Output.last(logFile, printLast(s))
      keepLastLog(s)
  }

  def export: Command =
    Command(ExportCommand, exportBrief, exportDetailed)(exportParser)((_, f) => f())

  private[this] def lastImpl(s: State, sks: AnyKeys, sid: Option[String]): State = {
    val (str, _, display) = extractLast(s)
    Output.last(sks, str.streams(s), printLast(s), sid)(display)
    keepLastLog(s)
  }

  /** Determines the log file that last* commands should operate on.  See also isLastOnly. */
  def lastLogFile(s: State): Option[File] = {
    val backing = s.globalLogging.backing
    if (isLastOnly(s)) backing.last else Some(backing.file)
  }

  /**
   * If false, shift the current log file to be the log file that 'last' will operate on.
   * If true, keep the previous log file as the one 'last' operates on because there is nothing useful in the current one.
   */
  def keepLastLog(s: State): State = if (isLastOnly(s)) s.keepLastLog else s

  /**
   * The last* commands need to determine whether to read from the current log file or the previous log file
   * and whether to keep the previous log file or not.  This is selected based on whether the previous command
   * was 'shell', which meant that the user directly entered the 'last' command.  If it wasn't directly entered,
   * the last* commands operate on any output since the last 'shell' command and do shift the log file.
   * Otherwise, the output since the previous 'shell' command is used and the log file is not shifted.
   */
  def isLastOnly(s: State): Boolean = s.history.previous.forall(_.commandLine == Shell)

  def printLast(s: State): Seq[String] => Unit = _ foreach println

  def autoImports(extracted: Extracted): EvalImports =
    new EvalImports(imports(extracted), "<auto-imports>")

  def imports(extracted: Extracted): Seq[(String, Int)] = {
    val curi = extracted.currentRef.build
    extracted.structure.units(curi).imports.map(s => (s, -1))
  }

  def listBuild(
      uri: URI,
      build: LoadedBuildUnit,
      current: Boolean,
      currentID: String,
      log: Logger
  ): Unit = {
    log.info(s"In $uri")
    def prefix(id: String) = if (currentID != id) "   " else if (current) " * " else "(*)"
    for (id <- build.defined.keys.toSeq.sorted) log.info("\t" + prefix(id) + id)
  }

  def act: Command = Command.customHelp(Act.actParser, actHelp)

  def actHelp: State => Help =
    s => CommandStrings.showHelp ++ CommandStrings.multiTaskHelp ++ keysHelp(s)

  def keysHelp(s: State): Help =
    if (Project.isProjectLoaded(s))
      Help.detailOnly(taskDetail(allTaskAndSettingKeys(s)))
    else
      Help.empty

  def plugins: Command = Command.command(PluginsCommand, pluginsBrief, pluginsDetailed) { s =>
    val helpString = PluginsDebug.helpAll(s)
    System.out.println(helpString)
    s
  }

  val pluginParser: State => Parser[AutoPlugin] = s => {
    val autoPlugins: Map[String, AutoPlugin] = PluginsDebug.autoPluginMap(s)
    token(Space) ~> Act.knownPluginParser(autoPlugins, "plugin")
  }

  def plugin: Command = Command(PluginCommand)(pluginParser) { (s, plugin) =>
    val helpString = PluginsDebug.help(plugin, s)
    System.out.println(helpString)
    s
  }

  def projects: Command =
    Command(ProjectsCommand, (ProjectsCommand, projectsBrief), projectsDetailed)(s =>
      projectsParser(s).?) {
      case (s, Some(modifyBuilds)) => transformExtraBuilds(s, modifyBuilds)
      case (s, None)               => showProjects(s); s
    }

  def showProjects(s: State): Unit = {
    val extracted = Project extract s
    import extracted._
    import currentRef.{ build => curi, project => cid }
    listBuild(curi, structure.units(curi), true, cid, s.log)
    for ((uri, build) <- structure.units if curi != uri) listBuild(uri, build, false, cid, s.log)
  }

  def transformExtraBuilds(s: State, f: List[URI] => List[URI]): State = {
    val original = Project.extraBuilds(s)
    val extraUpdated = Project.updateExtraBuilds(s, f)
    try doLoadProject(extraUpdated, LoadAction.Current)
    catch {
      case e: Exception =>
        s.log.error("Project loading failed: reverting to previous state.")
        Project.setExtraBuilds(s, original)
    }
  }

  def projectsParser(s: State): Parser[List[URI] => List[URI]] = {
    val addBase = token(Space ~> "add") ~> token(Space ~> basicUri, "<build URI>").+
    val removeBase = token(Space ~> "remove") ~> token(Space ~> Uri(Project.extraBuilds(s).toSet)).+
    addBase.map(toAdd => (xs: List[URI]) => (toAdd.toList ::: xs).distinct) |
      removeBase.map(toRemove => (xs: List[URI]) => xs.filterNot(toRemove.toSet))
  }

  def project: Command =
    Command.make(ProjectCommand, projectBrief, projectDetailed)(ProjectNavigation.command)

  def loadFailed: Command = Command(LoadFailed)(loadProjectParser)(doLoadFailed)

  @tailrec
  private[this] def doLoadFailed(s: State, loadArg: String): State = {
    val result = (SimpleReader.readLine(
      "Project loading failed: (r)etry, (q)uit, (l)ast, or (i)gnore? ") getOrElse Quit)
      .toLowerCase(Locale.ENGLISH)
    def matches(s: String) = !result.isEmpty && (s startsWith result)
    def retry = loadProjectCommand(LoadProject, loadArg) :: s.clearGlobalLog
    def ignoreMsg =
      if (Project.isProjectLoaded(s)) "using previously loaded project" else "no project loaded"

    result match {
      case ""                     => retry
      case _ if matches("retry")  => retry
      case _ if matches(Quit)     => s.exit(ok = false)
      case _ if matches("ignore") => s.log.warn(s"Ignoring load failure: $ignoreMsg."); s
      case _ if matches("last")   => LastCommand :: loadProjectCommand(LoadFailed, loadArg) :: s
      case _                      => println("Invalid response."); doLoadFailed(s, loadArg)
    }
  }

  def loadProjectCommands(arg: String): List[String] =
    StashOnFailure ::
      (OnFailure + " " + loadProjectCommand(LoadFailed, arg)) ::
      loadProjectCommand(LoadProjectImpl, arg) ::
      PopOnFailure ::
      State.FailureWall ::
      Nil

  def loadProject: Command =
    Command(LoadProject, LoadProjectBrief, LoadProjectDetailed)(loadProjectParser)((s, arg) =>
      loadProjectCommands(arg) ::: s)

  private[this] def loadProjectParser: State => Parser[String] =
    _ => matched(Project.loadActionParser)

  private[this] def loadProjectCommand(command: String, arg: String): String =
    s"$command $arg".trim

  def loadProjectImpl: Command =
    Command(LoadProjectImpl)(_ => Project.loadActionParser)(doLoadProject)

  def checkSBTVersionChanged(state: State): Unit = {
    import sbt.io.syntax._
    val app = state.configuration.provider
    val buildProps = state.baseDir / "project" / "build.properties"
    // First try reading the sbt version from build.properties file.
    val sbtVersionOpt = if (buildProps.exists) {
      val buildProperties = new Properties()
      IO.load(buildProperties, buildProps)
      Option(buildProperties.getProperty("sbt.version"))
    } else None

    sbtVersionOpt.foreach(version =>
      if (version != app.id.version()) {
        state.log.warn(s"""sbt version mismatch, current: ${app.id
          .version()}, in build.properties: "$version", use 'reboot' to use the new value.""")
    })
  }

  def doLoadProject(s0: State, action: LoadAction.Value): State = {
    checkSBTVersionChanged(s0)
    val (s1, base) = Project.loadAction(SessionVar.clear(s0), action)
    IO.createDirectory(base)
    val s = if (s1 has Keys.stateCompilerCache) s1 else registerCompilerCache(s1)

    val (eval, structure) =
      try Load.defaultLoad(s, base, s.log, Project.inPluginProject(s), Project.extraBuilds(s))
      catch {
        case ex: compiler.EvalException =>
          s0.log.debug(ex.getMessage)
          ex.getStackTrace map (ste => s"\tat $ste") foreach (s0.log.debug(_))
          ex.setStackTrace(Array.empty)
          throw ex
      }

    val session = Load.initialSession(structure, eval, s0)
    SessionSettings.checkSession(session, s)
    Project.setProject(session, structure, s)
  }

  def registerCompilerCache(s: State): State = {
    val maxCompilers = System.getProperty("sbt.resident.limit")
    val cache =
      if (maxCompilers == null)
        CompilerCache.fresh
      else {
        val num = try maxCompilers.toInt
        catch {
          case e: NumberFormatException =>
            throw new RuntimeException("Resident compiler limit must be an integer.", e)
        }
        if (num <= 0) CompilerCache.fresh else CompilerCache.createCacheFor(num)
      }
    s.put(Keys.stateCompilerCache, cache)
  }

  def shell: Command = Command.command(Shell, Help.more(Shell, ShellDetailed)) { s0 =>
    import sbt.internal.{ ConsolePromptEvent, ConsoleUnpromptEvent }
    val exchange = StandardMain.exchange
    val s1 = exchange run s0
    exchange publishEventMessage ConsolePromptEvent(s0)
    val exec: Exec = exchange.blockUntilNextExec
    val newState = s1
      .copy(onFailure = Some(Exec(Shell, None)),
            remainingCommands = exec +: Exec(Shell, None) +: s1.remainingCommands)
      .setInteractive(true)
    exchange publishEventMessage ConsoleUnpromptEvent(exec.source)
    if (exec.commandLine.trim.isEmpty) newState
    else newState.clearGlobalLog
  }

  def startServer: Command =
    Command.command(StartServer, Help.more(StartServer, StartServerDetailed)) { s0 =>
      val exchange = StandardMain.exchange
      exchange.runServer(s0)
    }

  private val sbtVersionRegex = """sbt\.version\s*=.*""".r
  private def isSbtVersionLine(s: String) = sbtVersionRegex.pattern matcher s matches ()

  private def isSbtProject(baseDir: File, projectDir: File) =
    projectDir.exists() || (baseDir * "*.sbt").get.nonEmpty

  private def writeSbtVersionUnconditionally(state: State) = {
    val baseDir = state.baseDir
    val sbtVersion = BuiltinCommands.sbtVersion(state)
    val projectDir = baseDir / "project"
    val buildProps = projectDir / "build.properties"

    val buildPropsLines = if (buildProps.canRead) IO.readLines(buildProps) else Nil

    val sbtVersionAbsent = buildPropsLines forall (!isSbtVersionLine(_))

    if (sbtVersionAbsent) {
      val warnMsg = s"No sbt.version set in project/build.properties, base directory: $baseDir"
      try {
        if (isSbtProject(baseDir, projectDir)) {
          val line = s"sbt.version=$sbtVersion"
          IO.writeLines(buildProps, line :: buildPropsLines)
          state.log info s"Updated file $buildProps: set sbt.version to $sbtVersion"
        } else
          state.log warn warnMsg
      } catch {
        case _: IOException => state.log warn warnMsg
      }
    }
  }

  private def intendsToInvokeNew(state: State) =
    state.remainingCommands exists (_.commandLine == TemplateCommand)

  private def writeSbtVersion(state: State) =
    if (!java.lang.Boolean.getBoolean("sbt.skip.version.write") && !intendsToInvokeNew(state))
      writeSbtVersionUnconditionally(state)

  private def WriteSbtVersion = "write-sbt-version"

  private def writeSbtVersion: Command =
    Command.command(WriteSbtVersion) { state =>
      writeSbtVersion(state); state
    }

  private def intendsToInvokeCompile(state: State) =
    state.remainingCommands exists (_.commandLine == Keys.compile.key.label)

  private def notifyUsersAboutShell(state: State): Unit = {
    val suppress = Project extract state getOpt Keys.suppressSbtShellNotification getOrElse false
    if (!suppress && intendsToInvokeCompile(state))
      state.log info "Executing in batch mode. For better performance use sbt's shell"
  }

  private def NotifyUsersAboutShell = "notify-users-about-shell"

  private def notifyUsersAboutShell: Command =
    Command.command(NotifyUsersAboutShell) { state =>
      notifyUsersAboutShell(state); state
    }
}
