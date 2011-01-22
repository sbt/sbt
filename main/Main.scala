/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010, 2011  Mark Harrah
 */
package sbt

	import Execute.NodeView
	import complete.HistoryCommands
	import HistoryCommands.{Start => HistoryPrefix}
	import Project.{SessionKey, StructureKey}
	import sbt.build.{AggressiveCompile, Auto, BuildException, LoadCommand, Parse, ParseException, ProjectLoad, SourceLoad}
	import sbt.complete.{DefaultParsers, Parser}

	import Command.{applyEffect,Analysis,HistoryPath,Logged,Watch}
	import scala.annotation.tailrec
	import scala.collection.JavaConversions._
	import Function.tupled
	import Path._

	import java.io.File

/** This class is the entry point for sbt.*/
class xMain extends xsbti.AppMain
{
	final def run(configuration: xsbti.AppConfiguration): xsbti.MainResult =
	{
		import Commands.{initialize, defaults}
		import CommandSupport.{DefaultsCommand, InitCommand}
		val initialCommandDefs = Seq(initialize, defaults)
		val commands = DefaultsCommand :: InitCommand :: configuration.arguments.map(_.trim).toList
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
			case Left(t) => Commands.handleException(t, state)
		}
}

	import DefaultParsers._
	import CommandSupport._
object Commands
{
	def DefaultCommands: Seq[Command] = Seq(ignore, help, reload, read, history, continuous, exit, loadCommands, loadProject, compile, discover,
		projects, project, setOnFailure, ifLast, multi, shell, alias, append, nop)

	def nop = Command.custom(s => success(() => s), Nil)
	def ignore = Command.command(FailureWall)(identity)

	def detail(selected: Iterable[String])(h: Help): Option[String] =
		h.detail match { case (commands, value) => if( selected exists commands ) Some(value) else None }

	// TODO: tab complete on command names
	def help = Command.args(HelpCommand, helpBrief, helpDetailed, "<command>") { (s, args) =>

		val h = s.processors.flatMap(_.help)
		val message =
			if(args.isEmpty)
				h.map( _.brief match { case (a,b) => a + " : " + b } ).mkString("\n", "\n", "\n")
			else
				h flatMap detail(args) mkString("\n", "\n\n", "\n")
		System.out.println(message)
		s
	}

	def alias = Command(AliasCommand, AliasBrief, AliasDetailed) { s =>
		val name = token(OpOrID.examples( aliasNames(s) : _*) )
		val assign = token(Space ~ '=' ~ Space) ~> matched(Command.combine(s.processors)(s), partial = true)
		val base = (OptSpace ~> (name ~ assign.?).?)
		applyEffect(base)(t => runAlias(s, t) )
	}
	def runAlias(s: State, args: Option[(String, Option[String])]): State =
		args match
		{
			case Some((name, Some(value))) => addAlias(s, name.trim, value.trim)
			case Some((x, None)) if !x.isEmpty=> printAlias(s, x.trim); s
			case None => printAliases(s); s
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

	def reload = Command.command(ReloadCommand, ReloadBrief, ReloadDetailed) { s =>
		runExitHooks(s).reload
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

	def read = Command(ReadCommand, ReadBrief, ReadDetailed)(s => applyEffect(readParser(s))(doRead(s)) )

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
	/*def history = Command( historyHelp: _* ) { case (in, s) if in.line startsWith "!" =>
		val logError = (msg: String) => CommandSupport.logger(s).error(msg)
		HistoryCommands(in.line.substring(HistoryPrefix.length).trim, (s get HistoryPath.key) getOrElse None, 500/*JLine.MaxHistorySize*/, logError) match
		{
			case Some(commands) =>
				commands.foreach(println)  //printing is more appropriate than logging
				(commands ::: s).continue
			case None => s.fail
		}
	}*/

	def indent(withStar: Boolean) = if(withStar) "\t*" else "\t "
	def listProject(name: String, current: Boolean, log: Logger) = log.info( indent(current) + name )

	def act = error("TODO")
	def projects = Command.command(ProjectsCommand, projectsBrief, projectsDetailed ) { s =>
		val log = logger(s)
		val session = Project.session(s)
		val structure = Project.structure(s)
		val (curi, cid) = session.current
		for( (uri, build) <- structure.units)
		{
			log.info("In " + uri)
			for(id <- build.defined.keys) listProject(id, cid == id, log)
		}
		s
	}
	def withAttribute[T](s: State, key: AttributeKey[T], ifMissing: String)(f: T => State): State =
		(s get key) match {
			case None => logger(s).error(ifMissing); s.fail
			case Some(nav) => f(nav)
		}

	def project = Command(ProjectCommand, projectBrief, projectDetailed)(ProjectNavigation.command)

	def exit = Command.command(TerminateAction, Help(exitBrief) :: Nil ) ( doExit )

	def doExit(s: State): State  =  runExitHooks(s).exit(true)

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

	def loadProject = Command.command(LoadProject, LoadProjectBrief, LoadProjectDetailed) { s =>
		val structure = Load.defaultLoad(s, logger(s))
		val session = Load.initialSession(structure)
		val newAttrs = s.attributes.put(StructureKey, structure).put(SessionKey, session)
		val newState = s.copy(attributes = newAttrs)
		Project.updateCurrent(runExitHooks(newState))
	}
	
	def handleException(e: Throwable, s: State, trace: Boolean = true): State = {
		val log = logger(s)
		if(trace) log.trace(e)
		log.error(e.toString)
		s.fail
	}
	
	def runExitHooks(s: State): State = {
		ExitHooks.runExitHooks(s.exitHooks.toSeq)
		s.copy(exitHooks = Set.empty)
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
	{
		if(Command validID name) {
			val removed = removeAlias(s, name)
			if(value.isEmpty) removed else removed.copy(processors = newAlias(name, value) +: removed.processors)
		} else {
			System.err.println("Invalid alias name '" + name + "'.")
			s.fail
		}
	}
	def removeAlias(s: State, name: String): State  =  s.copy(processors = s.processors.filter(c => !isAliasNamed(name, c)) )
	def isAliasNamed(name: String, c: Command): Boolean  =  isNamed(name, getAlias(c))
	def isNamed(name: String, alias: Option[(String,String)]): Boolean  =  alias match { case None => false; case Some((alias,_)) => name != alias }

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
		Command(name, (name, "<alias>"), "Alias of '" + value + "'")(aliasBody(name, value)).tag(CommandAliasKey, (name, value))
	def aliasBody(name: String, value: String)(state: State): Parser[() => State] =
		Parser(Command.combine(removeAlias(state,name).processors)(state))(value)
		
	val CommandAliasKey = AttributeKey[(String,String)]("is-command-alias")
}