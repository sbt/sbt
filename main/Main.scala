/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010, 2011  Mark Harrah
 */
package sbt

	import Execute.NodeView
	import complete.{DefaultParsers, HistoryCommands, Parser}
	import HistoryCommands.{Start => HistoryPrefix}
	import compiler.EvalImports
	import Types.{const,idFun}

	import Command.applyEffect
	import Keys.{analysis,historyPath,globalLogging,shellPrompt}
	import scala.annotation.tailrec
	import scala.collection.JavaConversions._
	import Function.tupled
	import java.net.URI
	import java.lang.reflect.InvocationTargetException
	import Path._

	import java.io.File

/** This class is the entry point for sbt.*/
final class xMain extends xsbti.AppMain
{
	def run(configuration: xsbti.AppConfiguration): xsbti.MainResult =
	{
		import BuiltinCommands.{initialAttributes, initialize, defaults, DefaultBootCommands}
		import CommandSupport.{DefaultsCommand, InitCommand}
		val initialCommandDefs = Seq(initialize, defaults)
		val commands = DefaultsCommand +: InitCommand +: (DefaultBootCommands ++ configuration.arguments.map(_.trim))
		val state = State( configuration, initialCommandDefs, Set.empty, None, commands, State.newHistory, initialAttributes, State.Continue )
		MainLoop.runLogged(state)
	}
}
final class ScriptMain extends xsbti.AppMain
{
	def run(configuration: xsbti.AppConfiguration): xsbti.MainResult =
	{
		import BuiltinCommands.{initialAttributes, ScriptCommands}
		val commands = Script.Name +: configuration.arguments.map(_.trim)
		val state = State( configuration, ScriptCommands, Set.empty, None, commands, State.newHistory, initialAttributes, State.Continue )
		MainLoop.runLogged(state)
	}	
}
final class ConsoleMain extends xsbti.AppMain
{
	def run(configuration: xsbti.AppConfiguration): xsbti.MainResult =
	{
		import BuiltinCommands.{initialAttributes, ConsoleCommands}
		val commands = IvyConsole.Name +: configuration.arguments.map(_.trim)
		val state = State( configuration, ConsoleCommands, Set.empty, None, commands, State.newHistory, initialAttributes, State.Continue )
		MainLoop.runLogged(state)
	}
}
object MainLoop
{
	/** Entry point to run the remaining commands in State with managed global logging.*/
	def runLogged(state: State): xsbti.MainResult =
		runLoggedLoop(state, GlobalLogBacking(newBackingFile(), None))

	/** Constructs a new, (weakly) unique, temporary file to use as the backing for global logging. */
	def newBackingFile(): File = File.createTempFile("sbt",".log")

	/** Run loop that evaluates remaining commands and manages changes to global logging configuration.*/
	@tailrec def runLoggedLoop(state: State, logBacking: GlobalLogBacking): xsbti.MainResult =
		runAndClearLast(state, logBacking) match {
			case ret: Return =>  // delete current and last log files when exiting normally
				logBacking.file.delete()
				deleteLastLog(logBacking) 
				ret.result
			case clear: ClearGlobalLog => // delete previous log file, move current to previous, and start writing to a new file
				deleteLastLog(logBacking)
				runLoggedLoop(clear.state, logBacking shift newBackingFile())
			case keep: KeepGlobalLog => // make previous log file the current log file
				logBacking.file.delete
				runLoggedLoop(keep.state, logBacking.unshift)
		}
		
	/** Runs the next sequence of commands, cleaning up global logging after any exceptions. */
	def runAndClearLast(state: State, logBacking: GlobalLogBacking): RunNext =
		try
			runWithNewLog(state, logBacking)
		catch {
			case e: xsbti.FullReload =>
				deleteLastLog(logBacking)
				throw e // pass along a reboot request
			case e =>
				System.err.println("sbt appears to be exiting abnormally.\n  The log file for this session is at " + logBacking.file)
				deleteLastLog(logBacking)
				throw e
		}

	/** Deletes the previous global log file. */
	def deleteLastLog(logBacking: GlobalLogBacking): Unit =
		logBacking.last.foreach(_.delete())

	/** Runs the next sequence of commands with global logging in place. */
	def runWithNewLog(state: State, logBacking: GlobalLogBacking): RunNext =
		Using.fileWriter(append = true)(logBacking.file) { writer =>
			val out = new java.io.PrintWriter(writer)
			val loggedState = state.put(globalLogging, LogManager.globalDefault(out, logBacking))
			try run(loggedState) finally out.close()
		}
	sealed trait RunNext
	final class ClearGlobalLog(val state: State) extends RunNext
	final class KeepGlobalLog(val state: State) extends RunNext
	final class Return(val result: xsbti.MainResult) extends RunNext

	/** Runs the next sequence of commands that doesn't require global logging changes.*/
	@tailrec def run(state: State): RunNext =
		state.next match
		{
			case State.Continue => run(next(state))
			case State.ClearGlobalLog => new ClearGlobalLog(state.continue)
			case State.KeepLastLog => new KeepGlobalLog(state.continue)
			case ret: State.Return => new Return(ret.result)
		}

	def next(state: State): State =
		ErrorHandling.wideConvert { state.process(Command.process) } match
		{
			case Right(s) => s
			case Left(t: xsbti.FullReload) => throw t
			case Left(t) => BuiltinCommands.handleException(t, state)
		}
}

	import DefaultParsers._
	import CommandSupport._
object BuiltinCommands
{
	def initialAttributes = AttributeMap.empty

	def ConsoleCommands: Seq[Command] = Seq(ignore, exit, IvyConsole.command, act, nop)
	def ScriptCommands: Seq[Command] = Seq(ignore, exit, Script.command, act, nop)
	def DefaultCommands: Seq[Command] = Seq(ignore, help, about, reboot, read, history, continuous, exit, loadProject, loadProjectImpl, loadFailed, Cross.crossBuild, Cross.switchVersion,
		projects, project, setOnFailure, clearOnFailure, ifLast, multi, shell, set, tasks, inspect, eval, alias, append, last, lastGrep, nop, sessionCommand, act)
	def DefaultBootCommands: Seq[String] = LoadProject :: (IfLast + " " + Shell) :: Nil

	def nop = Command.custom(s => success(() => s))
	def ignore = Command.command(FailureWall)(idFun)

	def detail(selected: Seq[String], detailMap: Map[String, String]): Seq[String] =
		selected.distinct flatMap { detailMap get _ }

	def help = Command.make(HelpCommand, helpBrief, helpDetailed)(helpParser)
	def about = Command.command(AboutCommand, aboutBrief, aboutDetailed) { s => logger(s).info(aboutString(s)); s }

	def helpParser(s: State) =
	{
		val h = (Help.empty /: s.definedCommands)(_ ++ _.help(s))
		val helpCommands = h.detail.keySet
		val args = (token(Space) ~> token( NotSpace examples helpCommands  )).*
		applyEffect(args)(runHelp(s, h))
	}
	
	def runHelp(s: State, h: Help)(args: Seq[String]): State =
	{
		val message =
			if(args.isEmpty)
				aligned("  ", "   ", h.brief).mkString("\n", "\n", "\n")
			else
				detail(args, h.detail) mkString("\n", "\n\n", "\n")
		System.out.println(message)
		s
	}
	def sbtVersion(s: State): String = s.configuration.provider.id.version
	def scalaVersion(s: State): String = s.configuration.provider.scalaProvider.version
	def aboutString(s: State): String =
	{
		"This is sbt " + sbtVersion(s) + "\n" +
		aboutProject(s) +
		"sbt, sbt plugins, and build definitions are using Scala " + scalaVersion(s) + "\n"
	}
	def aboutProject(s: State): String =
		if(Project.isProjectLoaded(s))
		{
			val e = Project.extract(s)
			val current = "The current project is " + Project.display(e.currentRef) + "\n"
			val sc = aboutScala(s, e)
			val built = if(sc.isEmpty) "" else "The current project is built against " + sc + "\n"
			current + built + aboutPlugins(e)
		}
		else "No project is currently loaded.\n"

	def aboutPlugins(e: Extracted): String =
	{
		val allPluginNames = e.structure.units.values.flatMap(_.unit.plugins.pluginNames).toSeq.distinct
		if(allPluginNames.isEmpty) "" else allPluginNames.mkString("Available Plugins: ", ", ", "\n")
	}
	def aboutScala(s: State, e: Extracted): String =
	{
		val scalaVersion = e.getOpt(Keys.scalaVersion)
		val scalaHome = e.getOpt(Keys.scalaHome).flatMap(idFun)
		val instance = e.getOpt(Keys.scalaInstance.task).flatMap(_ => quiet(e.evalTask(Keys.scalaInstance, s)))
		(scalaVersion, scalaHome, instance) match {
			case (sv, Some(home), Some(si)) => "local Scala version " + selectScalaVersion(sv, si) + " at " + home.getAbsolutePath
			case (_, Some(home), None) => "a local Scala build at " + home.getAbsolutePath
			case (sv, None, Some(si)) => "Scala " + selectScalaVersion(sv, si)
			case (Some(sv), None, None) => "Scala " + sv
			case (None, None, None) => ""
		}
	}
	private[this] def selectScalaVersion(sv: Option[String], si: ScalaInstance): String  =  sv match { case Some(si.version) => si.version; case _ => si.actualVersion }
	private[this] def quiet[T](t: => T): Option[T] = try { Some(t) } catch { case e: Exception => None }

	def tasks = Command.command(TasksCommand, tasksBrief, tasksDetailed) { s =>
		System.out.println(tasksPreamble)
		System.out.println(tasksHelp(s))
		s
	}
	def taskDetail(s: State): Seq[(String,String)] = taskKeys(s) flatMap taskStrings
	def taskKeys(s: State): Seq[AttributeKey[_]] =
	{
		val extracted = Project.extract(s)
		import extracted._
		val index = structure.index
		index.keyIndex.keys(Some(currentRef)).toSeq map index.keyMap sortBy(_.label)
	}
	def tasksHelp(s: State): String =	
		aligned("  ", "   ", taskDetail(s)) mkString("\n", "\n", "")

	def taskStrings(key: AttributeKey[_]): Option[(String, String)]  =  key.description map { d => (key.label, d) }
	def aligned(pre: String, sep: String, in: Seq[(String, String)]): Seq[String] =
	{
		val width = in.map(_._1.length).max
		in.map { case (a, b) => ("  " + fill(a, width) + sep + b) }
	}
	def fill(s: String, size: Int)  =  s + " " * math.max(size - s.length, 0)

	def alias = Command.make(AliasCommand, AliasBrief, AliasDetailed) { s =>
		val name = token(OpOrID.examples( aliasNames(s) : _*) )
		val assign = token(OptSpace ~ '=' ~ OptSpace)
		val sfree = removeAliases(s)
		val to = matched(sfree.combinedParser, partial = true) | any.+.string
		val base = (OptSpace ~> (name ~ (assign ~> to.?).?).?)
		applyEffect(base)(t => runAlias(s, t) )
	}

	def runAlias(s: State, args: Option[(String, Option[Option[String]])]): State =
		args match
		{
			case None => printAliases(s); s
			case Some(x ~ None) if !x.isEmpty => printAlias(s, x.trim); s
			case Some(name ~ Some(None)) => removeAlias(s, name.trim)
			case Some(name ~ Some(Some(value))) => addAlias(s, name.trim, value.trim)
		}
	
	def shell = Command.command(Shell, ShellBrief, ShellDetailed) { s =>
		val history = (s get historyPath.key) getOrElse Some((s.baseDir / ".history").asFile)
		val prompt = (s get shellPrompt.key) match { case Some(pf) => pf(s); case None => "> " }
		val reader = new FullReader(history, s.combinedParser)
		val line = reader.readLine(prompt)
		line match {
			case Some(line) =>
				val newState = s.copy(onFailure = Some(Shell), remainingCommands = line +: Shell +: s.remainingCommands)
				if(line.trim.isEmpty) newState else newState.clearGlobalLog
			case None => s
		}
	}

	def multiParser(s: State): Parser[Seq[String]] =
		( token(';' ~> OptSpace) flatMap { _ => matched(s.combinedParser | token(charClass(_ != ';').+, hide= const(true))) <~ token(OptSpace) } map (_.trim) ).+
	def multiApplied(s: State) = 
		Command.applyEffect( multiParser(s) )( _ ::: s )

	def multi = Command.custom(multiApplied, Help(Multi, MultiBrief, MultiDetailed) )
	
	lazy val otherCommandParser = (s: State) => token(OptSpace ~> matched(s.combinedParser) )

	def ifLast = Command(IfLast, IfLastBrief, IfLastDetailed)(otherCommandParser) { (s, arg) =>
		if(s.remainingCommands.isEmpty) arg :: s else s
	}
	def append = Command(AppendCommand, AppendLastBrief, AppendLastDetailed)(otherCommandParser) { (s, arg) =>
		s.copy(remainingCommands = s.remainingCommands :+ arg)
	}
	
	def setOnFailure = Command(OnFailure, OnFailureBrief, OnFailureDetailed)(otherCommandParser) { (s, arg) =>
		s.copy(onFailure = Some(arg))
	}
	def clearOnFailure = Command.command(ClearOnFailure)(s => s.copy(onFailure = None))

	def reboot = Command(RebootCommand, RebootBrief, RebootDetailed)(rebootParser) { (s, full) =>
		s.runExitHooks().reboot(full)
	}
	def rebootParser(s: State) = token(Space ~> "full" ^^^ true) ?? false

	def defaults = Command.command(DefaultsCommand) { s =>
		s ++ DefaultCommands
	}

	def initialize = Command.command(InitCommand) { s =>
		/*"load-commands -base ~/.sbt/commands" :: */readLines( readable( sbtRCs(s) ) ) ::: s
	}

	def readParser(s: State) =
	{
		val files = (token(Space) ~> fileParser(s.baseDir)).+
		val portAndSuccess = token(OptSpace) ~> Port
		portAndSuccess || files
	}

	def read = Command.make(ReadCommand, ReadBrief, ReadDetailed)(s => applyEffect(readParser(s))(doRead(s)) )

	def doRead(s: State)(arg: Either[Int, Seq[File]]): State =
		arg match
		{
			case Left(portAndSuccess) =>
				val port = math.abs(portAndSuccess)
				val previousSuccess = portAndSuccess >= 0
				readMessage(port, previousSuccess) match
				{
					case Some(message) => (message :: (ReadCommand + " " + port) :: s).copy(onFailure = Some(ReadCommand + " " + (-port)))
					case None =>
						System.err.println("Connection closed.")
						s.fail
				}
			case Right(from) =>
				val notFound = notReadable(from)
				if(notFound.isEmpty)
					readLines(from) ::: s // this means that all commands from all files are loaded, parsed, and inserted before any are executed
				else {
					logger(s).error("Command file(s) not readable: \n\t" + notFound.mkString("\n\t"))
					s
				}
		}
	private def readMessage(port: Int, previousSuccess: Boolean): Option[String] =
	{
		// split into two connections because this first connection ends the previous communication
		xsbt.IPC.client(port) { _.send(previousSuccess.toString) }
		//   and this second connection starts the next communication
		xsbt.IPC.client(port) { ipc =>
			val message = ipc.receive
			if(message eq null) None else Some(message)
		}
	}

	def continuous =
		Command(ContinuousExecutePrefix, Help(continuousBriefHelp) )(otherCommandParser) { (s, arg) =>
			withAttribute(s, Watched.Configuration, "Continuous execution not configured.") { w =>
				val repeat = ContinuousExecutePrefix + (if(arg.startsWith(" ")) arg else " " + arg)
				Watched.executeContinuously(w, s, arg, repeat)
			}
		}

	def history = Command.custom(historyParser, historyHelp)
	def historyParser(s: State): Parser[() => State] =
		Command.applyEffect(HistoryCommands.actionParser) { histFun =>
			val logError = (msg: String) => s.log.error(msg)
			val hp = s get historyPath.key getOrElse None
			val lines = hp.toList.flatMap( p => IO.readLines(p) ).toIndexedSeq
			histFun( complete.History(lines, hp, logError) ) match
			{
				case Some(commands) =>
					commands foreach println  //printing is more appropriate than logging
					(commands ::: s).continue
				case None => s.fail
			}
		}

	def eval = Command.single(EvalCommand, evalBrief, evalDetailed) { (s, arg) =>
		val log = logger(s)
		val extracted = Project extract s
		import extracted._
		val result = session.currentEval().eval(arg, srcName = "<eval>", imports = autoImports(extracted))
		log.info("ans: " + result.tpe + " = " + result.getValue(currentLoader))
		s
	}
	def sessionCommand = Command.make(SessionCommand, sessionBrief, SessionSettings.Help)(SessionSettings.command)
	def reapply(newSession: SessionSettings, structure: Load.BuildStructure, s: State): State =
	{
		logger(s).info("Reapplying settings...")
		val newStructure = Load.reapply(newSession.mergeSettings, structure)( Project.showContextKey(newSession, structure) )
		Project.setProject(newSession, newStructure, s)
	}
	def set = Command.single(SetCommand, setBrief, setDetailed) { (s, arg) =>
		val extracted = Project extract s
		import extracted._
		val settings = EvaluateConfigurations.evaluateSetting(session.currentEval(), "<set>", imports(extracted), arg, 0)(currentLoader)
		val append = Load.transformSettings(Load.projectScope(currentRef), currentRef.build, rootProject, settings)
		val newSession = session.appendSettings( append map (a => (a, arg)))
		reapply(newSession, structure, s)
	}
	def inspect = Command(InspectCommand, inspectBrief, inspectDetailed)(inspectParser) {
		case (s, (InspectOption.DependencyTree, sk)) =>
		  val basedir = new File(Project.session(s).current.build)
			val treeString = Project.settingGraph(Project.structure(s), basedir, sk)( Project.showContextKey(s) ).dependsAscii
			logger(s).info(treeString)
			s
		case (s, (InspectOption.Details(actual), sk)) =>
			val detailString = Project.details(Project.structure(s), actual, sk.scope, sk.key)( Project.showContextKey(s) )
			logger(s).info(detailString)
			s
	}
	def lastGrep = Command(LastGrepCommand, lastGrepBrief, lastGrepDetailed)(lastGrepParser) {
		case (s, (pattern,Some(sk))) =>
			val (str, ref, display) = extractLast(s)
			Output.lastGrep(sk, str, str.streams(s), pattern, printLast(s))(display)
			keepLastLog(s)
		case (s, (pattern, None)) =>
			for(logFile <- lastLogFile(s)) yield
				Output.lastGrep(logFile, pattern, printLast(s))
			keepLastLog(s)
	}
	def extractLast(s: State) = {
		val ext = Project.extract(s)
		(ext.structure, Select(ext.currentRef), ext.showKey)
	}
	def inspectParser = (s: State) => spacedInspectOptionParser(s) ~ spacedKeyParser(s)
	val spacedInspectOptionParser: (State => Parser[InspectOption]) = (s: State) => {
		import InspectOption._
		val actual = "actual" ^^^ Details(true)
		val tree = "tree" ^^^ DependencyTree
		token(Space ~> (tree | actual)) ?? Details(false)
	}
	val spacedKeyParser = (s: State) => Act.requireSession(s, token(Space) ~> Act.scopedKeyParser(s))
	val optSpacedKeyParser = (s: State) => spacedKeyParser(s).?
	def lastGrepParser(s: State) = Act.requireSession(s, (token(Space) ~> token(NotSpace, "<pattern>")) ~ optSpacedKeyParser(s))
	def last = Command(LastCommand, lastBrief, lastDetailed)(optSpacedKeyParser) {
		case (s,Some(sk)) =>
			val (str, ref, display) = extractLast(s)
			Output.last(sk, str, str.streams(s), printLast(s))(display)
			keepLastLog(s)
		case (s, None) =>
			for(logFile <- lastLogFile(s)) yield
				Output.last( logFile, printLast(s) )
			keepLastLog(s)
	}

	/** Determines the log file that last* commands should operate on.  See also isLastOnly. */
	def lastLogFile(s: State) =
	{
		val backing = CommandSupport.globalLogging(s).backing
		if(isLastOnly(s)) backing.last else Some(backing.file)
	}

	/** If false, shift the current log file to be the log file that 'last' will operate on.
	* If true, keep the previous log file as the one 'last' operates on because there is nothing useful in the current one.*/
	def keepLastLog(s: State): State = if(isLastOnly(s)) s.keepLastLog else s

	/** The last* commands need to determine whether to read from the current log file or the previous log file
	* and whether to keep the previous log file or not.  This is selected based on whether the previous command
	* was 'shell', which meant that the user directly entered the 'last' command.  If it wasn't directly entered,
	* the last* commands operate on any output since the last 'shell' command and do shift the log file.
	* Otherwise, the output since the previous 'shell' command is used and the log file is not shifted.*/
	def isLastOnly(s: State): Boolean = s.history.previous.forall(_ == Shell)
		
	def printLast(s: State): Seq[String] => Unit = _ foreach println

	def autoImports(extracted: Extracted): EvalImports  =  new EvalImports(imports(extracted), "<auto-imports>")
	def imports(extracted: Extracted): Seq[(String,Int)] =
	{
		val curi = extracted.currentRef.build
		extracted.structure.units(curi).imports.map(s => (s, -1))
	}

	def listBuild(uri: URI, build: Load.LoadedBuildUnit, current: Boolean, currentID: String, log: Logger) =
	{
		log.info("In " + uri)
		def prefix(id: String) = if(currentID != id) "   " else if(current) " * " else "(*)"
		for(id <- build.defined.keys.toSeq.sorted) log.info("\t" + prefix(id) + id)
	}

	def act = Command.customHelp(Act.actParser, actHelp)
	def actHelp = (s: State) => CommandSupport.showHelp ++ keysHelp(s)
	def keysHelp(s: State): Help =
		if(Project.isProjectLoaded(s))
			Help.detailOnly(taskDetail(s))
		else
			Help.empty

	def projects = Command.command(ProjectsCommand, projectsBrief, projectsDetailed ) { s =>
		val extracted = Project extract s
		import extracted._
		import currentRef.{build => curi, project => cid}
		val log = logger(s)
		listBuild(curi, structure.units(curi), true, cid, log)
		for( (uri, build) <- structure.units if curi != uri) listBuild(uri, build, false, cid, log)
		s
	}
	def withAttribute[T](s: State, key: AttributeKey[T], ifMissing: String)(f: T => State): State =
		(s get key) match {
			case None => logger(s).error(ifMissing); s.fail
			case Some(nav) => f(nav)
		}

	def project = Command.make(ProjectCommand, projectBrief, projectDetailed)(ProjectNavigation.command)

	def exit = Command.command(TerminateAction, exitBrief, exitBrief ) ( doExit )

	def doExit(s: State): State  =  s.runExitHooks().exit(true)

	def loadFailed = Command.command(LoadFailed)(handleLoadFailed)
	@tailrec def handleLoadFailed(s: State): State =
	{
		val result = (SimpleReader.readLine("Project loading failed: (r)etry, (q)uit, (l)ast, or (i)gnore? ") getOrElse Quit).toLowerCase
		def matches(s: String) = !result.isEmpty && (s startsWith result)
		
		if(result.isEmpty || matches("retry"))
			LoadProject :: s.clearGlobalLog
		else if(matches(Quit))
			s.exit(ok = false)
		else if(matches("ignore"))
		{
			val hadPrevious = Project.isProjectLoaded(s)
			logger(s).warn("Ignoring load failure: " + (if(hadPrevious) "using previously loaded project." else "no project loaded."))
			s
		}
		else if(matches("last"))
			LastCommand :: LoadFailed :: s
		else
		{
			println("Invalid response.")
			handleLoadFailed(s)
		}
	}

	def loadProjectCommands(arg: String) = (OnFailure + " " + LoadFailed) :: (LoadProjectImpl + " " + arg).trim :: ClearOnFailure :: FailureWall :: Nil
	def loadProject = Command(LoadProject, LoadProjectBrief, LoadProjectDetailed)(_ => matched(Project.loadActionParser)) { (s,arg) => loadProjectCommands(arg) ::: s }

	def loadProjectImpl = Command(LoadProjectImpl)(_ => Project.loadActionParser) { (s0, action) =>
		val (s, base) = Project.loadAction(SessionVar.clear(s0), action)
		IO.createDirectory(base)
		val (eval, structure) = Load.defaultLoad(s, base, logger(s))
		val session = Load.initialSession(structure, eval)
		SessionSettings.checkSession(session, s)
		Project.setProject(session, structure, s)
	}
	
	def handleException(e: Throwable, s: State): State =
		handleException(e, s, logger(s))
	def handleException(e: Throwable, s: State, log: Logger): State =
	{
		e match
		{
			case _: Incomplete => () // already handled by evaluateTask
			case _: NoMessageException => ()
			case ite: InvocationTargetException =>
				val cause = ite.getCause
				if(cause == null || cause == ite) logFullException(ite, log) else handleException(cause, s, log)
			case _: MessageOnlyException => log.error(e.toString)
			case _: Project.Uninitialized => logFullException(e, log, true)
			case _ => logFullException(e, log)
		}
		s.fail
	}
	def logFullException(e: Throwable, log: Logger, messageOnly: Boolean = false)
	{
		log.trace(e)
		log.error(if(messageOnly) e.getMessage else ErrorHandling reducedToString e)
		log.error("Use 'last' for the full log.")
	}
	
	def addAlias(s: State, name: String, value: String): State =
		if(Command validID name) {
			val removed = removeAlias(s, name)
			if(value.isEmpty) removed else removed.copy(definedCommands = newAlias(name, value) +: removed.definedCommands)
		} else {
			System.err.println("Invalid alias name '" + name + "'.")
			s.fail
		}

	def removeAliases(s: State): State  =  removeTagged(s, CommandAliasKey)
	def removeAlias(s: State, name: String): State  =  s.copy(definedCommands = s.definedCommands.filter(c => !isAliasNamed(name, c)) )
	
	def removeTagged(s: State, tag: AttributeKey[_]): State = s.copy(definedCommands = removeTagged(s.definedCommands, tag))
	def removeTagged(as: Seq[Command], tag: AttributeKey[_]): Seq[Command] = as.filter(c => ! (c.tags contains tag))

	def isAliasNamed(name: String, c: Command): Boolean  =  isNamed(name, getAlias(c))
	def isNamed(name: String, alias: Option[(String,String)]): Boolean  =  alias match { case None => false; case Some((n,_)) => name == n }

	def getAlias(c: Command): Option[(String,String)]  =  c.tags get CommandAliasKey
	def printAlias(s: State, name: String): Unit  =  printAliases(aliases(s,(n,v) => n == name) )
	def printAliases(s: State): Unit  =  printAliases(allAliases(s))
	def printAliases(as: Seq[(String,String)]): Unit =
		for( (name,value) <- as)
			println("\t" + name + " = " + value)

	def aliasNames(s: State): Seq[String] = allAliases(s).map(_._1)
	def allAliases(s: State): Seq[(String,String)]  =  aliases(s, (n,v) => true)
	def aliases(s: State, pred: (String,String) => Boolean): Seq[(String,String)] =
		s.definedCommands.flatMap(c => getAlias(c).filter(tupled(pred)))

	def newAlias(name: String, value: String): Command =
		Command.make(name, (name, "'" + value + "'"), "Alias of '" + value + "'")(aliasBody(name, value)).tag(CommandAliasKey, (name, value))
	def aliasBody(name: String, value: String)(state: State): Parser[() => State] =
		OptSpace ~> Parser(Command.combine(removeAlias(state,name).definedCommands)(state))(value)

	val CommandAliasKey = AttributeKey[(String,String)]("is-command-alias", "Internal: marker for Commands created as aliases for another command.")
}