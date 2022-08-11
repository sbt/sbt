/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import java.io.{ File, IOException }
import java.net.URI
import java.nio.channels.ClosedChannelException
import java.nio.file.{ FileAlreadyExistsException, FileSystems, Files }
import java.util.Properties
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.atomic.AtomicBoolean

import sbt.BasicCommandStrings.{ JavaClient, Shell, Shutdown, TemplateCommand }
import sbt.Project.LoadAction
import sbt.compiler.EvalImports
import sbt.internal.Aggregation.AnyKeys
import sbt.internal.CommandStrings.BootCommand
import sbt.internal._
import sbt.internal.client.BspClient
import sbt.internal.inc.ScalaInstance
import sbt.internal.io.Retry
import sbt.internal.nio.{ CheckBuildSources, FileTreeRepository }
import sbt.internal.server.{ BuildServerProtocol, NetworkChannel }
import sbt.internal.util.Types.{ const, idFun }
import sbt.internal.util.complete.{ Parser, SizeParser }
import sbt.internal.util.{ Terminal => ITerminal, _ }
import sbt.io._
import sbt.io.syntax._
import sbt.util.{ Level, Logger, Show }
import xsbti.AppProvider
import xsbti.compile.CompilerCache

import scala.annotation.{ nowarn, tailrec }
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration
import scala.util.control.NonFatal

/** This class is the entry point for sbt. */
final class xMain extends xsbti.AppMain {
  def run(configuration: xsbti.AppConfiguration): xsbti.MainResult =
    new XMainConfiguration().run("xMain", configuration)
}
private[sbt] object xMain {
  private[sbt] def dealiasBaseDirectory(config: xsbti.AppConfiguration): xsbti.AppConfiguration = {
    val dealiasedBase = config.baseDirectory.getCanonicalFile
    if (config.baseDirectory == dealiasedBase) config
    else
      new xsbti.AppConfiguration {
        override def arguments: Array[String] = config.arguments()
        override val baseDirectory: File = dealiasedBase
        override def provider: AppProvider = config.provider()
      }
  }
  private[sbt] def run(configuration: xsbti.AppConfiguration): xsbti.MainResult = {
    try {
      import BasicCommandStrings.{ DashDashClient, DashDashServer, runEarly }
      import BasicCommands.early
      import BuiltinCommands.defaults
      import sbt.internal.CommandStrings.{ BootCommand, DefaultsCommand, InitCommand }
      import sbt.internal.client.NetworkClient

      // if we detect -Dsbt.client=true or -client, run thin client.
      val clientModByEnv = SysProp.client
      val userCommands = configuration.arguments
        .map(_.trim)
        .filterNot(_ == DashDashServer)
      val isClient: String => Boolean = cmd => (cmd == JavaClient) || (cmd == DashDashClient)
      val isBsp: String => Boolean = cmd => (cmd == "-bsp") || (cmd == "--bsp")
      val isNew: String => Boolean = cmd => (cmd == "new")
      lazy val isServer = !userCommands.exists(c => isBsp(c) || isClient(c))
      // keep this lazy to prevent project directory created prematurely
      lazy val bootServerSocket = if (isServer) getSocketOrExit(configuration) match {
        case (_, Some(e)) => return e
        case (s, _)       => s
      }
      else None
      lazy val detachStdio = userCommands.exists(_ == BasicCommandStrings.DashDashDetachStdio)
      def withStreams[A](f: => A): A =
        try {
          bootServerSocket.foreach(l => ITerminal.setBootStreams(l.inputStream, l.outputStream))
          ITerminal.withStreams(true, isSubProcess = detachStdio) {
            f
          }
        } finally {
          if (ITerminal.isAnsiSupported) {
            // Clear any stray progress lines
            System.out.print(ConsoleAppender.ClearScreenAfterCursor)
            System.out.flush()
          }
        }

      userCommands match {
        case cmds if cmds.exists(isBsp) =>
          BspClient.run(dealiasBaseDirectory(configuration))
        case cmds if cmds.exists(isNew) =>
          IO.withTemporaryDirectory { tempDir =>
            val rebasedConfig = new xsbti.AppConfiguration {
              override def arguments: Array[String] = configuration.arguments()
              override val baseDirectory: File = tempDir / "new"
              override def provider: AppProvider = configuration.provider()
            }
            val state = StandardMain
              .initialState(
                rebasedConfig,
                Seq(defaults, early),
                runEarly(DefaultsCommand) :: runEarly(InitCommand) :: BootCommand :: Nil
              )
            StandardMain.runManaged(state)
          }
        case _ if clientModByEnv || userCommands.exists(isClient) =>
          withStreams {
            val args = userCommands.toList.filterNot(isClient)
            Exit(NetworkClient.run(dealiasBaseDirectory(configuration), args))
          }
        case _ =>
          withStreams {
            val state0 = StandardMain
              .initialState(
                dealiasBaseDirectory(configuration),
                Seq(defaults, early),
                runEarly(DefaultsCommand) :: runEarly(InitCommand) :: BootCommand :: Nil
              )
              .put(BasicKeys.detachStdio, detachStdio)
            val state = bootServerSocket match {
              case Some(l) => state0.put(Keys.bootServerSocket, l)
              case _       => state0
            }
            try StandardMain.runManaged(state)
            finally bootServerSocket.foreach(_.close())
          }
      }
    } finally {
      ShutdownHooks.close()
    }
  }

  private def getSocketOrExit(
      configuration: xsbti.AppConfiguration
  ): (Option[BootServerSocket], Option[Exit]) = {
    def printThrowable(e: Throwable): Unit = {
      println("sbt thinks that server is already booting because of this exception:")
      e.printStackTrace()
    }

    try Some(new BootServerSocket(configuration)) -> None
    catch {
      case e: ServerAlreadyBootingException
          if System.console != null && !ITerminal.startedByRemoteClient =>
        printThrowable(e)
        println("Create a new server? y/n (default y)")
        val exit =
          if (ITerminal.get.withRawInput(System.in.read) == 'n'.toInt) Some(Exit(1))
          else None
        (None, exit)
      case e: ServerAlreadyBootingException =>
        if (SysProp.forceServerStart) (None, None)
        else {
          printThrowable(e)
          (None, Some(Exit(2)))
        }
      case _: UnsatisfiedLinkError => (None, None)
    }
  }
}

final class ScriptMain extends xsbti.AppMain {
  def run(configuration: xsbti.AppConfiguration): xsbti.MainResult =
    new XMainConfiguration().run("ScriptMain", configuration)
}
private[sbt] object ScriptMain {
  private[sbt] def run(configuration: xsbti.AppConfiguration): xsbti.MainResult = {
    import BasicCommandStrings.runEarly
    val state = StandardMain.initialState(
      xMain.dealiasBaseDirectory(configuration),
      BuiltinCommands.ScriptCommands,
      runEarly(Level.Error.toString) :: Script.Name :: Nil
    )
    StandardMain.runManaged(state)
  }
}

final class ConsoleMain extends xsbti.AppMain {
  def run(configuration: xsbti.AppConfiguration): xsbti.MainResult =
    new XMainConfiguration().run("ConsoleMain", configuration)
}
private[sbt] object ConsoleMain {
  private[sbt] def run(configuration: xsbti.AppConfiguration): xsbti.MainResult = {
    val state = StandardMain.initialState(
      xMain.dealiasBaseDirectory(configuration),
      BuiltinCommands.ConsoleCommands,
      IvyConsole.Name :: Nil
    )
    StandardMain.runManaged(state)
  }
}

object StandardMain {
  private[sbt] lazy val exchange = new CommandExchange()
  // The access to the pool should be thread safe because lazy val instantiation is thread safe
  // and pool is only referenced directly in closeRunnable after the executionContext is sure
  // to have been instantiated
  private[this] var pool: Option[ForkJoinPool] = None
  private[sbt] lazy val executionContext: ExecutionContext = ExecutionContext.fromExecutor({
    val p = new ForkJoinPool
    pool = Some(p)
    p
  })

  private[this] val closeRunnable = () => {
    exchange.shutdown()
    pool.foreach(_.shutdownNow())
  }

  private[this] val isShutdown = new AtomicBoolean(false)
  def runManaged(s: State): xsbti.MainResult = {
    val hook = ShutdownHooks.add(closeRunnable)
    try {
      MainLoop.runLogged(s)
    } catch {
      case _: InterruptedException if isShutdown.get =>
        new xsbti.Exit { override def code(): Int = 0 }
    } finally {
      try DefaultBackgroundJobService.shutdown()
      finally hook.close()
      ()
    }
  }

  /** The common interface to standard output, used for all built-in ConsoleLoggers. */
  val console: ConsoleOut =
    ConsoleOut.systemOutOverwrite(ConsoleOut.overwriteContaining("Resolving "))
  ConsoleOut.setGlobalProxy(console)

  private[this] def initialGlobalLogging(file: Option[File]): GlobalLogging = {
    def createTemp(attempt: Int = 0): File = Retry {
      file.foreach(f => if (!f.exists()) IO.createDirectory(f))
      File.createTempFile("sbt-global-log", ".log", file.orNull)
    }
    GlobalLogging.initial(
      MainAppender.globalDefault(ConsoleOut.globalProxy),
      createTemp(),
      ConsoleOut.globalProxy
    )
  }
  def initialGlobalLogging(file: File): GlobalLogging = initialGlobalLogging(Option(file))
  @deprecated("use version that takes file argument", "1.4.0")
  def initialGlobalLogging: GlobalLogging = initialGlobalLogging(None)

  def initialState(
      configuration: xsbti.AppConfiguration,
      initialDefinitions: Seq[Command],
      preCommands: Seq[String]
  ): State = {
    // This is to workaround https://github.com/sbt/io/issues/110
    sys.props.put("jna.nosys", "true")

    import BasicCommandStrings.{ DashDashDetachStdio, DashDashServer, isEarlyCommand }
    val userCommands =
      configuration.arguments
        .map(_.trim)
        .filterNot(c => c == DashDashDetachStdio || c == DashDashServer)
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
      initialGlobalLogging(BuildPaths.globalLoggingStandard(configuration.baseDirectory)),
      None,
      State.Continue
    )
    s.initializeClassLoaderCache
  }
}

import sbt.BasicCommandStrings._
import sbt.BasicCommands._
import sbt.CommandUtil._
import sbt.TemplateCommandUtil.templateCommand
import sbt.internal.CommandStrings._
import sbt.internal.util.complete.DefaultParsers._

object BuiltinCommands {
  def initialAttributes = AttributeMap.empty
  import BasicCommands.exit
  def ConsoleCommands: Seq[Command] =
    Seq(ignore, exit, IvyConsole.command, setLogLevel, early, act, nop)

  def ScriptCommands: Seq[Command] =
    Seq(ignore, exit, Script.command, setLogLevel, early, act, nop)

  @nowarn
  def DefaultCommands: Seq[Command] =
    Seq(
      multi,
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
      oldLoadFailed,
      Cross.crossBuild,
      Cross.switchVersion,
      CrossJava.switchJavaHome,
      CrossJava.crossJavaHome,
      PluginCross.pluginCross,
      PluginCross.pluginSwitch,
      Cross.crossRestoreSession,
      setLogLevel,
      plugin,
      plugins,
      addPluginSbtFile,
      writeSbtVersion,
      skipBanner,
      notifyUsersAboutShell,
      shell,
      rebootNetwork,
      startServer,
      eval,
      last,
      oldLastGrep,
      lastGrep,
      export,
      boot,
      initialize,
      act,
      continuous,
      clearCaches,
      NetworkChannel.disconnect,
      waitCmd,
      promptChannel,
    ) ++
      allBasicCommands ++
      ContinuousCommands.value ++
      BuildServerProtocol.commands

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
    def plugins(lbu: LoadedBuildUnit) =
      lbu.unit.plugins.detected.autoPlugins
        .map(_.value.label)

    val allPluginNames =
      e.structure.units.values
        .flatMap(plugins)
        .toList
        .distinct
        .sorted

    if (allPluginNames.isEmpty)
      ""
    else
      ("Available Plugins" +: allPluginNames)
        .mkString("\n - ")
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

  private[this] def quiet[T](t: => T): Option[T] =
    try Some(t)
    catch { case _: Exception => None }

  def settingsCommand: Command =
    showSettingLike(
      SettingsCommand,
      settingsPreamble,
      KeyRanks.MainSettingCutoff,
      key => !isTask(key.manifest)
    )

  def tasks: Command =
    showSettingLike(
      TasksCommand,
      tasksPreamble,
      KeyRanks.MainTaskCutoff,
      key => isTask(key.manifest)
    )

  def showSettingLike(
      command: String,
      preamble: String,
      cutoff: Int,
      keep: AttributeKey[_] => Boolean
  ): Command =
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
  def showSettingParser(
      keepKeys: AttributeKey[_] => Boolean
  )(s: State): Parser[(Int, Option[String])] =
    verbosityParser ~ selectedParser(s, keepKeys).?
  def selectedParser(s: State, keepKeys: AttributeKey[_] => Boolean): Parser[String] =
    singleArgument(allTaskAndSettingKeys(s).filter(keepKeys).map(_.label).toSet)
  def verbosityParser: Parser[Int] =
    success(1) | ((Space ~ "-") ~> (
      'v'.id.+.map(_.size + 1) |
        ("V" ^^^ Int.MaxValue)
    ))

  def taskDetail(keys: Seq[AttributeKey[_]], firstOnly: Boolean): Seq[(String, String)] =
    sortByLabel(withDescription(keys)) flatMap { t =>
      taskStrings(t, firstOnly)
    }

  def taskDetail(keys: Seq[AttributeKey[_]]): Seq[(String, String)] =
    taskDetail(keys, false)

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
  def isTask(
      mf: Manifest[_]
  )(implicit taskMF: Manifest[Task[_]], inputMF: Manifest[InputTask[_]]): Boolean =
    mf.runtimeClass == taskMF.runtimeClass || mf.runtimeClass == inputMF.runtimeClass
  def topNRanked(n: Int) = (keys: Seq[AttributeKey[_]]) => sortByRank(keys).take(n)
  def highPass(rankCutoff: Int) =
    (keys: Seq[AttributeKey[_]]) => sortByRank(keys).takeWhile(_.rank <= rankCutoff)

  def tasksHelp(
      s: State,
      filter: Seq[AttributeKey[_]] => Seq[AttributeKey[_]],
      arg: Option[String]
  ): String = {
    val commandAndDescription = taskDetail(filter(allTaskAndSettingKeys(s)), true)
    arg match {
      case Some(selected) => detail(selected, commandAndDescription.toMap)
      case None           => aligned("  ", "   ", commandAndDescription) mkString ("\n", "\n", "")
    }
  }

  def taskStrings(key: AttributeKey[_], firstOnly: Boolean): Option[(String, String)] =
    key.description map { d =>
      if (firstOnly) (key.label, d.split("\r?\n")(0)) else (key.label, d)
    }

  def taskStrings(key: AttributeKey[_]): Option[(String, String)] = taskStrings(key, false)

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

  def continuous: Command = Continuous.continuous

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
    val show = Project.showContextKey2(newSession)
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
        else SettingCompletions.setThis(extracted, settings, arg)
      s.log.info(setResult.quietSummary)
      s.log.debug(setResult.verboseSummary)
      reapply(setResult.session, structure, s)
  }

  @deprecated("Use variant that doesn't take a State", "1.1.1")
  def setThis(
      s: State,
      extracted: Extracted,
      settings: Seq[Def.Setting[_]],
      arg: String
  ): SetResult =
    setThis(extracted, settings, arg)

  def setThis(
      extracted: Extracted,
      settings: Seq[Def.Setting[_]],
      arg: String
  ): SetResult =
    SettingCompletions.setThis(extracted, settings, arg)

  def inspect: Command = Command(InspectCommand, inspectBrief, inspectDetailed)(Inspect.parser) {
    case (s, f) =>
      s.log.info(f())
      s
  }

  @deprecated("Use `lastGrep` instead.", "1.2.0")
  def oldLastGrep: Command =
    lastGrepCommand(OldLastGrepCommand, oldLastGrepBrief, oldLastGrepDetailed, { s =>
      lastGrepParser(s)
    })

  def lastGrep: Command =
    lastGrepCommand(LastGrepCommand, lastGrepBrief, lastGrepDetailed, lastGrepParser)

  private def lastGrepCommand(
      name: String,
      briefHelp: (String, String),
      detail: String,
      parser: State => Parser[(String, Option[AnyKeys])]
  ): Command =
    Command(name, briefHelp, detail)(parser) { (s: State, sks: (String, Option[AnyKeys])) =>
      {
        if (name == OldLastGrepCommand)
          s.log.warn(deprecationWarningText(OldLastGrepCommand, LastGrepCommand))

        (s, sks) match {
          case (s, (pattern, Some(sks))) =>
            val (str, _, display) = extractLast(s)
            Output.lastGrep(sks, str.streams(s), pattern, printLast)(display)
            keepLastLog(s)
          case (s, (pattern, None)) =>
            for (logFile <- lastLogFile(s)) yield Output.lastGrep(logFile, pattern, printLast)
            keepLastLog(s)
        }
      }
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
      else Aggregation.evaluatingParser(s, show)(kvs)
    } yield () => {
      def export0(s: State): State = lastImpl(s, kvs, Some(ExportStream))
      val newS = try f()
      catch {
        case NonFatal(e) =>
          try export0(s)
          finally {
            throw e
          }
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
      for (logFile <- lastLogFile(s)) yield Output.last(logFile, printLast)
      keepLastLog(s)
  }

  def export: Command =
    Command(ExportCommand, exportBrief, exportDetailed)(exportParser)((_, f) => f())

  private[this] def lastImpl(s: State, sks: AnyKeys, sid: Option[String]): State = {
    val (str, _, display) = extractLast(s)
    Output.last(sks, str.streams(s), printLast, sid)(display)
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

  @deprecated("Use variant that doesn't take the state", "1.1.1")
  def printLast(s: State): Seq[String] => Unit = printLast

  def printLast: Seq[String] => Unit = _ foreach println

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

  def actHelp: State => Help = { s =>
    CommandStrings.showHelp ++ CommandStrings.printHelp ++ CommandStrings.multiTaskHelp ++
      keysHelp(s)
  }

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
    Command(ProjectsCommand, (ProjectsCommand, projectsBrief), projectsDetailed)(
      s => projectsParser(s).?
    ) {
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
      case _: Exception =>
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
  @deprecated("Use `loadFailed` instead.", "1.2.0")
  def oldLoadFailed: Command =
    Command(OldLoadFailed) { s =>
      loadProjectParser(s)
    } { (s: State, loadArg: String) =>
      s.log.warn(
        deprecationWarningText(OldLoadFailed, LoadFailed)
      )
      doLoadFailed(s, loadArg)
    }

  private[this] def deprecationWarningText(oldCommand: String, newCommand: String) = {
    s"The `$oldCommand` command is deprecated in favor of `$newCommand` and will be removed in a later version"
  }

  @tailrec
  private[this] def doLoadFailed(s: State, loadArg: String): State = {
    s.log.warn("Project loading failed: (r)etry, (q)uit, (l)ast, or (i)gnore? (default: r)")
    val result = try ITerminal.get.withRawInput(System.in.read) match {
      case -1 => 'q'.toInt
      case b  => b
    } catch { case _: ClosedChannelException => 'q' }
    def retry: State = loadProjectCommand(LoadProject, loadArg) :: s.clearGlobalLog
    def ignoreMsg: String =
      if (Project.isProjectLoaded(s)) "using previously loaded project" else "no project loaded"

    result.toChar match {
      case '\n' | '\r' => retry
      case 'r' | 'R'   => retry
      case 'q' | 'Q'   => s.exit(ok = false)
      case 'i' | 'I'   => s.log.warn(s"Ignoring load failure: $ignoreMsg."); s
      case 'l' | 'L'   => LastCommand :: loadProjectCommand(LoadFailed, loadArg) :: s
      case c           => println(s"Invalid response: '$c'"); doLoadFailed(s, loadArg)
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
    Command(LoadProject, LoadProjectBrief, LoadProjectDetailed)(loadProjectParser)(
      (s, arg) => loadProjectCommands(arg) ::: s
    )

  private[this] def loadProjectParser: State => Parser[String] =
    _ => matched(Project.loadActionParser)

  private[this] def loadProjectCommand(command: String, arg: String): String =
    s"$command $arg".trim

  def loadProjectImpl: Command =
    Command(LoadProjectImpl)(_ => Project.loadActionParser)(doLoadProject)

  def checkSBTVersionChanged(state: State): Unit = {
    import sbt.io.syntax._
    val sbtVersionProperty = "sbt.version"

    // Don't warn if current version has been set in system properties
    val sbtVersionSystemOpt =
      Option(System.getProperty(sbtVersionProperty))

    // the intention is to warn if build.properties file has changed during current sbt session
    // a `reload` will not respect this change while `reboot` will.
    val buildProps = state.baseDir / "project" / "build.properties"
    val sbtVersionBuildOpt = if (buildProps.exists) {
      val buildProperties = new Properties()
      IO.load(buildProperties, buildProps)
      Option(buildProperties.getProperty(sbtVersionProperty))
    } else None

    val sbtVersionOpt = sbtVersionSystemOpt.orElse(sbtVersionBuildOpt)

    sbtVersionOpt.foreach { version =>
      val appVersion = state.configuration.provider.id.version()
      if (version != appVersion) {
        state.log.warn(
          s"sbt version mismatch, using: $appVersion, " +
            s"""in build.properties: "$version", use 'reboot' to use the new value.""".stripMargin
        )
      }
    }
  }

  private def welcomeBanner(state: State): Unit = {
    import scala.util.Properties
    val appVersion = state.configuration.provider.id.version()
    val javaVersion = s"${Properties.javaVendor} Java ${Properties.javaVersion}"
    state.log.info(s"welcome to sbt $appVersion ($javaVersion)")
  }

  def doLoadProject(s0: State, action: LoadAction.Value): State = {
    welcomeBanner(s0)
    checkSBTVersionChanged(s0)
    val (s1, base) = Project.loadAction(SessionVar.clear(s0), action)
    IO.createDirectory(base)
    val s2 = if (s1 has Keys.stateCompilerCache) s1 else registerCompilerCache(s1)

    val (eval, structure) =
      try Load.defaultLoad(s2, base, s2.log, Project.inPluginProject(s2), Project.extraBuilds(s2))
      catch {
        case ex: compiler.EvalException =>
          s0.log.debug(ex.getMessage)
          ex.getStackTrace map (ste => s"\tat $ste") foreach (s0.log.debug(_))
          ex.setStackTrace(Array.empty)
          throw ex
      }

    val session = Load.initialSession(structure, eval, s0)
    SessionSettings.checkSession(session, s2)
    val s3 = Project.setProject(
      session,
      structure,
      s2,
      st => setupGlobalFileTreeRepository(addCacheStoreFactoryFactory(st))
    )
    val s4 = s3.put(Keys.useLog4J.key, Project.extract(s3).get(Keys.useLog4J))
    addSuperShellParams(CheckBuildSources.init(LintUnused.lintUnusedFunc(s4)))
  }

  private val setupGlobalFileTreeRepository: State => State = { state =>
    state.get(sbt.nio.Keys.globalFileTreeRepository).foreach(_.close())
    state.put(sbt.nio.Keys.globalFileTreeRepository, FileTreeRepository.default)
  }
  private val addSuperShellParams: State => State = (s: State) => {
    val extracted = Project.extract(s)
    import scala.concurrent.duration._
    val sleep = extracted.getOpt(Keys.superShellSleep).getOrElse(SysProp.supershellSleep.millis)
    val threshold =
      extracted.getOpt(Keys.superShellThreshold).getOrElse(SysProp.supershellThreshold)
    val maxItems = extracted.getOpt(Keys.superShellMaxTasks).getOrElse(SysProp.supershellMaxTasks)
    ITerminal.setConsoleProgressState(new ProgressState(1, maxItems))
    s.put(Keys.superShellSleep.key, sleep)
      .put(Keys.superShellThreshold.key, threshold)
      .put(Keys.superShellMaxTasks.key, maxItems)
  }
  private val addCacheStoreFactoryFactory: State => State = (s: State) => {
    val size = Project
      .extract(s)
      .getOpt(Keys.fileCacheSize)
      .flatMap(SizeParser(_))
      .getOrElse(SysProp.fileCacheSize)
    s.get(Keys.cacheStoreFactoryFactory).foreach(_.close())
    s.put(Keys.cacheStoreFactoryFactory, InMemoryCacheStore.factory(size))
  }

  def registerCompilerCache(s: State): State = {
    s.get(Keys.stateCompilerCache).foreach(_.clear())
    s.put(Keys.stateCompilerCache, CompilerCache.fresh)
  }

  def clearCaches: Command = {
    val help = Help.more(ClearCaches, ClearCachesDetailed)
    val f: State => State = registerCompilerCache _ andThen (_.initializeClassLoaderCache) andThen addCacheStoreFactoryFactory
    Command.command(ClearCaches, help)(f)
  }

  private[sbt] def waitCmd: Command =
    Command.arb(
      _ => ContinuousCommands.waitWatch.examples() ~> " ".examples() ~> matched(any.*).examples()
    ) { (s0, channel) =>
      val exchange = StandardMain.exchange
      exchange.channelForName(channel) match {
        case Some(c) if ContinuousCommands.isInWatch(s0, c) =>
          if (c.terminal.prompt != Prompt.Watch) {
            c.terminal.setPrompt(Prompt.Watch)
            c.prompt(ConsolePromptEvent(s0))
          } else if (c.terminal.isSupershellEnabled) {
            c.terminal.printStream.print(ConsoleAppender.ClearScreenAfterCursor)
            c.terminal.printStream.flush()
          }

          val s1 = exchange.run(s0)
          val exec: Exec = getExec(s1, Duration.Inf)
          val wait = s"${ContinuousCommands.waitWatch} $channel"
          val onFailure =
            s1.onFailure.map(of => if (of.commandLine == Shell) of.withCommandLine(wait) else of)
          val waitExec = Exec(wait, None)
          val remaining: List[Exec] = Exec(FailureWall, None) :: waitExec :: s1.remainingCommands
          val newState = s1.copy(remainingCommands = exec +: remaining, onFailure = onFailure)
          if (exec.commandLine.trim.isEmpty) newState
          else newState.clearGlobalLog
        case _ => s0
      }
    }

  private[sbt] def promptChannel = Command.arb(_ => reportParser(PromptChannel)) {
    (state, channel) =>
      if (channel == ConsoleChannel.defaultName) {
        if (!state.remainingCommands.exists(_.commandLine == Shell))
          state.copy(remainingCommands = state.remainingCommands ::: (Exec(Shell, None) :: Nil))
        else state
      } else {
        StandardMain.exchange.channelForName(channel) match {
          case Some(nc: NetworkChannel) => nc.prompt()
          case _                        =>
        }
        state
      }
  }

  private def getExec(state: State, interval: Duration): Exec = {
    StandardMain.exchange.blockUntilNextExec(interval, Some(state), state.globalLogging.full)
  }

  def shell: Command = Command.command(Shell, Help.more(Shell, ShellDetailed)) { s0 =>
    import sbt.internal.ConsolePromptEvent
    val exchange = StandardMain.exchange
    val welcomeState = displayWelcomeBanner(s0)
    val s1 = exchange run welcomeState
    /*
     * It is possible for sbt processes to leak if two are started simultaneously
     * by a remote client and only one is able to start a server. This seems to
     * happen primarily on windows.
     */
    if (ITerminal.startedByRemoteClient && !exchange.hasServer) {
      Exec(Shutdown, None) +: s1
    } else {
      if (ITerminal.console.prompt == Prompt.Batch) ITerminal.console.setPrompt(Prompt.Pending)
      exchange prompt ConsolePromptEvent(s0)
      val minGCInterval = Project
        .extract(s1)
        .getOpt(Keys.minForcegcInterval)
        .getOrElse(GCUtil.defaultMinForcegcInterval)
      val exec: Exec = getExec(s1, minGCInterval)
      val newState = s1
        .copy(
          onFailure = Some(Exec(Shell, None)),
          remainingCommands = exec +: Exec(Shell, None) +: s1.remainingCommands
        )
        .setInteractive(true)
      val res =
        if (exec.commandLine.trim.isEmpty) newState
        else newState.clearGlobalLog
      res
    }
  }

  def rebootNetwork: Command = Command.arb(_ => (RebootNetwork: Parser[String]).examples()) {
    (s, _) =>
      StandardMain.exchange.reboot(s)
      s
  }
  def startServer: Command =
    Command.command(StartServer, Help.more(StartServer, StartServerDetailed)) { s0 =>
      val exchange = StandardMain.exchange
      exchange.runServer(s0)
    }

  private val sbtVersionRegex = """sbt\.version\s*=.*""".r
  private def isSbtVersionLine(s: String) = sbtVersionRegex.pattern.matcher(s).matches()

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
        if (isSbtBuild(baseDir)) {
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
    if (SysProp.genBuildProps && !intendsToInvokeNew(state)) {
      writeSbtVersionUnconditionally(state)
    }

  private def checkRoot(state: State): Unit =
    if (SysProp.allowRootDir) ()
    else {
      val baseDir = state.baseDir
      import scala.collection.JavaConverters._
      // this should return / on Unix and C:\ for Windows.
      val rootOpt = FileSystems.getDefault.getRootDirectories.asScala.toList.headOption
      rootOpt foreach { root =>
        if (baseDir.getAbsolutePath == root.toString) {
          throw new IllegalStateException(
            "cannot run sbt from root directory without -Dsbt.rootdir=true; see sbt/sbt#1458"
          )
        }
      }
    }

  private def WriteSbtVersion = "writeSbtVersion"

  private def writeSbtVersion: Command =
    Command.command(WriteSbtVersion) { state =>
      checkRoot(state)
      writeSbtVersion(state)
      state
    }

  private def intendsToInvokeCompile(state: State) =
    state.remainingCommands exists (_.commandLine == Keys.compile.key.label)
  private def hasRebooted(state: State) =
    state.remainingCommands exists (_.commandLine == StartServer)

  private def notifyUsersAboutShell(state: State): Unit = {
    val suppress = Project extract state getOpt Keys.suppressSbtShellNotification getOrElse false
    if (!suppress && intendsToInvokeCompile(state) && !hasRebooted(state))
      state.log info "Executing in batch mode. For better performance use sbt's shell"
  }

  private def NotifyUsersAboutShell = "notifyUsersAboutShell"

  private def notifyUsersAboutShell: Command =
    Command.command(NotifyUsersAboutShell) { state =>
      notifyUsersAboutShell(state); state
    }

  private[this] def skipWelcomeFile(state: State, version: String) = {
    val base = BuildPaths.getGlobalBase(state).toPath
    base.resolve("preferences").resolve(version).resolve(SkipBannerFileName)
  }
  private def displayWelcomeBanner(state: State): State = {
    if (!state.get(bannerHasBeenShown).getOrElse(false)) {
      try {
        val version = sbtVersion(state)
        val skipFile = skipWelcomeFile(state, version)
        Files.createDirectories(skipFile.getParent)
        val suppress = !SysProp.banner || Files.exists(skipFile)
        if (!suppress) {
          Banner(version).foreach(banner => state.log.info(banner))
        }
      } catch { case _: IOException => /* Don't let errors in this command prevent startup */ }
      state.put(bannerHasBeenShown, true)
    } else state
  }
  private[this] val bannerHasBeenShown =
    AttributeKey[Boolean]("banner-has-been-shown", Int.MaxValue)
  private[this] val SkipBannerFileName = "skip-banner"
  private[this] val SkipBanner = "skipBanner"
  private[this] def skipBanner: Command = Command.command(SkipBanner)(skipBanner)
  private def skipBanner(state: State): State = {
    val skipFile = skipWelcomeFile(state, sbtVersion(state))
    try Files.createFile(skipFile)
    catch {
      case _: FileAlreadyExistsException =>
      case e: IOException                => state.log.error(s"Couldn't create file $skipFile: $e")
    }
    state
  }
}
