/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010, 2011  Mark Harrah
 */
package sbt

	import Execute.NodeView
	import complete.HistoryCommands
	import HistoryCommands.{Start => HistoryPrefix}
	import sbt.build.{AggressiveCompile, Auto, BuildException, LoadCommand, Parse, ParseException, ProjectLoad, SourceLoad}
	import compile.EvalImports
	import sbt.complete.{DefaultParsers, Parser}

	import Command.{applyEffect,Analysis,HistoryPath,Logged,Watch}
	import scala.annotation.tailrec
	import scala.collection.JavaConversions._
	import Function.tupled
	import java.net.URI
	import Path._

	import java.io.File

/** This class is the entry point for sbt.*/
class xMain extends xsbti.AppMain
{
	final def run(configuration: xsbti.AppConfiguration): xsbti.MainResult =
	{
		import BuiltinCommands.{initialize, defaults, DefaultBootCommands}
		import CommandSupport.{DefaultsCommand, InitCommand}
		val initialCommandDefs = Seq(initialize, defaults)
		val commands = DefaultsCommand +: InitCommand +: (DefaultBootCommands ++ configuration.arguments.map(_.trim))
		val state = State( configuration, initialCommandDefs, Set.empty, None, commands, initialAttributes, Next.Continue )
		run(state)
	}
	def initialAttributes = AttributeMap.empty.put(Logged, ConsoleLogger())
		
	@tailrec final def run(state: State): xsbti.MainResult =
	{
		import Next._
		state.next match
		{
			case Continue => run(next(state))
			case Fail => Exit(1)
			case Done => Exit(0)
			case Reload =>
				val app = state.configuration.provider
				new Reboot(app.scalaProvider.version, state.commands, app.id, state.configuration.baseDirectory)
		}
	}
	def next(state: State): State =
		ErrorHandling.wideConvert { state.process(Command.process) } match
		{
			case Right(s) => s
			case Left(t) => BuiltinCommands.handleException(t, state)
		}
}

	import DefaultParsers._
	import CommandSupport._
object BuiltinCommands
{
	def DefaultCommands: Seq[Command] = Seq(ignore, help, reboot, read, history, continuous, exit, loadCommands, loadProject, loadProjectImpl, loadFailed, compile, discover,
		projects, project, setOnFailure, clearOnFailure, ifLast, multi, shell, set, inspect, eval, alias, append, last, lastGrep, nop, sessionCommand, act)
	def DefaultBootCommands: Seq[String] = LoadProject :: (IfLast + " " + Shell) :: Nil

	def nop = Command.custom(s => success(() => s))
	def ignore = Command.command(FailureWall)(identity)

	def detail(selected: Iterable[String])(h: Help): Option[String] =
		h.detail match { case (commands, value) => if( selected exists commands ) Some(value) else None }

	def help = Command.make(HelpCommand, helpBrief, helpDetailed)(helpParser)

	def helpParser(s: State) =
	{
		val h = s.processors.flatMap(_.help)
		val helpCommands = h.flatMap(_.detail._1)
		val args = (token(Space) ~> token( OpOrID.examples(helpCommands : _*) )).*
		applyEffect(args)(runHelp(s, h))
	}
	
	def runHelp(s: State, h: Seq[Help])(args: Seq[String]): State =
	{
		val message =
			if(args.isEmpty)
				h.map( _.brief match { case (a,b) => a + " : " + b } ).mkString("\n", "\n", "\n")
			else
				h flatMap detail(args) mkString("\n", "\n\n", "\n")
		System.out.println(message)
		s
	}

	def alias = Command.make(AliasCommand, AliasBrief, AliasDetailed) { s =>
		val name = token(OpOrID.examples( aliasNames(s) : _*) )
		val assign = token(Space ~ '=' ~ OptSpace)
		val sfree = removeAliases(s)
		val to = matched(Command.combine(sfree.processors)(sfree), partial = true) | any.+.string
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
		val historyPath = (s get HistoryPath.key) getOrElse Some((s.baseDir / ".history").asFile)
		val parser = Command.combine(s.processors)
		val reader = new FullReader(historyPath, parser(s))
		val line = reader.readLine("> ")
		line match {
			case Some(line) => s.copy(onFailure = Some(Shell), commands = line +: Shell +: s.commands)
			case None => s
		}
	}
	
	// TODO: this should nest Parsers for other commands
	def multi = Command.single(Multi, MultiBrief, MultiDetailed) { (s,arg) =>
		arg.split(";").toSeq ::: s
	}
	
	// TODO: nest
	def ifLast = Command.single(IfLast, IfLastBrief, IfLastDetailed) { (s, arg) =>
		if(s.commands.isEmpty) arg :: s else s
	}
	// TODO: nest
	def append = Command.single(Append, AppendLastBrief, AppendLastDetailed) { (s, arg) =>
		s.copy(commands = s.commands :+ arg)
	}
	
	// TODO: nest
	def setOnFailure = Command.single(OnFailure, OnFailureBrief, OnFailureDetailed) { (s, arg) =>
		s.copy(onFailure = Some(arg))
	}
	def clearOnFailure = Command.command(ClearOnFailure)(s => s.copy(onFailure = None))

	def reboot = Command.command(RebootCommand, RebootBrief, RebootDetailed) { s =>
		s.runExitHooks().reload
	}

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

	// TODO: nest
	def continuous =
		Command.single(ContinuousExecutePrefix, Help(continuousBriefHelp) ) { (s, arg) =>
			withAttribute(s, Watch.key, "Continuous execution not configured.") { w =>
				val repeat = ContinuousExecutePrefix + (if(arg.startsWith(" ")) arg else " " + arg)
				Watched.executeContinuously(w, s, arg, repeat)
			}
		}

	def history = Command.command("!!")(s => s)
	//TODO: convert
	/*def history = Command.make( historyHelp: _* ) { case (in, s) if in.line startsWith "!" =>
		val logError = (msg: String) => CommandSupport.logger(s).error(msg)
		HistoryCommands(in.line.substring(HistoryPrefix.length).trim, (s get HistoryPath.key) getOrElse None, 500/*JLine.MaxHistorySize*/, logError) match
		{
			case Some(commands) =>
				commands.foreach(println)  //printing is more appropriate than logging
				(commands ::: s).continue
			case None => s.fail
		}
	}*/

	def eval = Command.single(EvalCommand, evalBrief, evalDetailed) { (s, arg) =>
		val log = logger(s)
		val extracted = Project extract s
		import extracted._
		val result = session.currentEval().eval(arg, srcName = "<eval>", imports = autoImports(extracted))
		log.info("ans: " + result.tpe + " = " + result.value.toString)
		s
	}
	def sessionCommand = Command.make(SessionCommand, sessionBrief, SessionSettings.Help)(SessionSettings.command)
	def reapply(newSession: SessionSettings, structure: Load.BuildStructure, s: State): State =
	{
		logger(s).info("Reapplying settings...")
		val newStructure = Load.reapply(newSession.mergeSettings, structure)
		Project.setProject(newSession, newStructure, s)
	}
	def set = Command.single(SetCommand, setBrief, setDetailed) { (s, arg) =>
		val extracted = Project extract s
		import extracted._
		val setting = EvaluateConfigurations.evaluateSetting(session.currentEval(), "<set>", imports(extracted), arg, 0)
		val append = Load.transformSettings(Load.projectScope(curi, cid), curi, rootProject, setting :: Nil)
		val newSession = session.appendSettings( append map (a => (a, arg)))
		reapply(newSession, structure, s)
	}
	def inspect = Command(InspectCommand, inspectBrief, inspectDetailed)(spacedKeyParser) { (s,sk) =>
		val detailString = Project.details(Project.structure(s), sk.scope, sk.key)
		logger(s).info(detailString)
		s
	}
	def lastGrep = Command(LastGrepCommand, lastGrepBrief, lastGrepDetailed)(lastGrepParser) { case (s,(pattern,sk)) =>
		Output.lastGrep(sk.scope, sk.key, Project.structure(s).streams, pattern)
		s
	}
	val spacedKeyParser = (s: State) => Act.requireSession(s, token(Space) ~> Act.scopedKeyParser(s))
	def lastGrepParser(s: State) = Act.requireSession(s, (token(Space) ~> token(NotSpace, "<pattern>")) ~ spacedKeyParser(s))
	def last = Command(LastCommand, lastBrief, lastDetailed)(spacedKeyParser) { (s,sk) =>
		Output.last(sk.scope, sk.key, Project.structure(s).streams)
		s
	}

	def autoImports(extracted: Extracted): EvalImports  =  new EvalImports(imports(extracted), "<auto-imports>")
	def imports(extracted: Extracted): Seq[(String,Int)] =
	{
		import extracted._
		structure.units(curi).imports.map(s => (s, -1))
	}

	def listBuild(uri: URI, build: Load.LoadedBuildUnit, current: Boolean, currentID: String, log: Logger) =
	{
		log.info("In " + uri)
		def prefix(id: String) = if(currentID != id) "   " else if(current) " * " else "(*)"
		for(id <- build.defined.keys) log.info("\t" + prefix(id) + id)
	}

	def act = Command.custom(Act.actParser)

	def projects = Command.command(ProjectsCommand, projectsBrief, projectsDetailed ) { s =>
		val extracted = Project extract s
		import extracted._
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

	def exit = Command.command(TerminateAction, Help(exitBrief) :: Nil ) ( doExit )

	def doExit(s: State): State  =  s.runExitHooks().exit(true)

	// TODO: tab completion, low priority
	def discover = Command.single(Discover, DiscoverBrief, DiscoverDetailed) { (s, arg) =>
		withAttribute(s, Analysis, "No analysis to process.") { analysis =>
			val command = Parse.discover(arg)
			val discovered = build.Build.discover(analysis, command)
			println(discovered.mkString("\n"))
			s
		}
	}
	// TODO: tab completion, low priority
	def compile = Command.single(CompileName, CompileBrief, CompileDetailed ) { (s, arg) =>
		val command = Parse.compile(arg)(s.baseDir)
		try {
			val analysis = build.Build.compile(command, s.configuration)
			s.put(Analysis, analysis)
		} catch { case e: xsbti.CompileFailed => s.fail /* already logged */ }
	}

	def loadFailed = Command.command(LoadFailed)(handleLoadFailed)
	@tailrec def handleLoadFailed(s: State): State =
	{
		val result = (SimpleReader.readLine("Project loading failed: (r)etry, (q)uit, or (i)gnore? ") getOrElse Quit).toLowerCase
		def matches(s: String) = !result.isEmpty && (s startsWith result)
		
		if(matches("retry"))
			LoadProject :: s
		else if(matches(Quit))
			s.exit(ok = false)
		else if(matches("ignore"))
			s
		else
		{
			println("Invalid response.")
			handleLoadFailed(s)
		}
	}

	def loadProjectCommands = (OnFailure + " " + LoadFailed) :: LoadProjectImpl :: ClearOnFailure :: FailureWall :: Nil
	def loadProject = Command.command(LoadProject, LoadProjectBrief, LoadProjectDetailed) { loadProjectCommands ::: _ }

	def loadProjectImpl = Command.command(LoadProjectImpl) { s =>
		val (eval, structure) = Load.defaultLoad(s, logger(s))
		val session = Load.initialSession(structure, eval)
		Project.setProject(session, structure, s)
	}
	
	def handleException(e: Throwable, s: State, trace: Boolean = true): State = {
		val log = logger(s)
		if(trace) log.trace(e)
		log.error(e.toString)
		s.fail
	}
	
	// TODO: tab completion, low priority
	def loadCommands = Command.single(LoadCommand, Parse.helpBrief(LoadCommand, LoadCommandLabel), Parse.helpDetail(LoadCommand, LoadCommandLabel, true) ) { (s, arg) =>
		applyCommands(s, buildCommands(arg, s.configuration))
	}
	
	def buildCommands(arguments: String, configuration: xsbti.AppConfiguration): Either[Throwable, Seq[Any]] =
		loadCommand(arguments, configuration, true, classOf[CommandDefinitions].getName)

	def applyCommands(s: State, commands: Either[Throwable, Seq[Any]]): State =
		commands match {
			case Right(newCommands) =>
				val asCommands = newCommands flatMap {
					case c: CommandDefinitions => c.commands
					case x => error("Not an instance of CommandDefinitions: " + x.asInstanceOf[AnyRef].getClass)
				}
				s.copy(processors = asCommands ++ s.processors)
			case Left(e) => handleException(e, s, false)
		}
	
	def loadCommand(line: String, configuration: xsbti.AppConfiguration, allowMultiple: Boolean, defaultSuper: String): Either[Throwable, Seq[Any]] =
		try
		{
			val parsed = Parse(line)(configuration.baseDirectory)
			Right( build.Build( translateEmpty(parsed, defaultSuper), configuration, allowMultiple) )
		}
		catch { case e @ (_: ParseException | _: BuildException | _: xsbti.CompileFailed) => Left(e) }

	def translateEmpty(load: LoadCommand, defaultSuper: String): LoadCommand =
		load match {
			case ProjectLoad(base, Auto.Explicit, "") => ProjectLoad(base, Auto.Subclass, defaultSuper)
			case s @ SourceLoad(_, _, _, _, Auto.Explicit, "")  => s.copy(auto = Auto.Subclass, name = defaultSuper)
			case x => x
		}

	def runTask[Task[_] <: AnyRef](root: Task[State], checkCycles: Boolean, maxWorkers: Int)(implicit taskToNode: NodeView[Task]): Result[State] =
	{
		val (service, shutdown) = CompletionService[Task[_], Completed](maxWorkers)

		val x = new Execute[Task](checkCycles)(taskToNode)
		try { x.run(root)(service) } finally { shutdown() }
	}
	def processResult[State](result: Result[State], original: State, onFailure: => State): State =
		result match
		{
			case Value(v) => v
			case Inc(inc) =>
				println(Incomplete.show(inc, true))
				println("Task did not complete successfully")
				onFailure
		}
		
	def addAlias(s: State, name: String, value: String): State =
		if(Command validID name) {
			val removed = removeAlias(s, name)
			if(value.isEmpty) removed else removed.copy(processors = newAlias(name, value) +: removed.processors)
		} else {
			System.err.println("Invalid alias name '" + name + "'.")
			s.fail
		}

	def removeAliases(s: State): State  =  removeTagged(s, CommandAliasKey)
	def removeAlias(s: State, name: String): State  =  s.copy(processors = s.processors.filter(c => !isAliasNamed(name, c)) )
	
	def removeTagged(s: State, tag: AttributeKey[_]): State = s.copy(processors = removeTagged(s.processors, tag))
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
		s.processors.flatMap(c => getAlias(c).filter(tupled(pred)))

	def newAlias(name: String, value: String): Command =
		Command.make(name, (name, "'" + value + "'"), "Alias of '" + value + "'")(aliasBody(name, value)).tag(CommandAliasKey, (name, value))
	def aliasBody(name: String, value: String)(state: State): Parser[() => State] =
		Parser(Command.combine(state.processors)(state))(value)

	val CommandAliasKey = AttributeKey[(String,String)]("is-command-alias")
}