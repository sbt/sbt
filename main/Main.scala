/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010  Mark Harrah
 */
package sbt

import Execute.NodeView
import complete.HistoryCommands
import HistoryCommands.{Start => HistoryPrefix}
import Project.{SessionKey, StructureKey}
import sbt.build.{AggressiveCompile, Auto, BuildException, LoadCommand, Parse, ParseException, ProjectLoad, SourceLoad}
import Command.{Analysis,HistoryPath,Logged,Watch}
import scala.annotation.tailrec
import scala.collection.JavaConversions._
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
		ErrorHandling.wideConvert { state.process(process) } match
		{
			case Right(s) => s
			case Left(t) => Commands.handleException(t, state)
		}
	def process(command: String, state: State): State =
	{
		val in = Input(command, None)
		Commands.applicable(state).flatMap( _.run(in, state) ).headOption.getOrElse {
			if(command.isEmpty) state
			else {
				System.err.println("Unknown command '" + command + "'")
				state.fail
			}
		}
	}
}

import CommandSupport._
object Commands
{
	def DefaultCommands: Seq[Command] = Seq(ignore, help, reload, read, history, continuous, exit, loadCommands, loadProject, compile, discover,
		projects, project, setOnFailure, ifLast, multi, shell, alias, append)

	def ignore = nothing(Set(FailureWall))

	def nothing(ignore: Set[String]) = Command(){ case (in, s) if ignore(in.line) => s }

	def applicable(state: State): Stream[Command]  =  state.processors.toStream

	def detail(selected: Iterable[String])(h: Help): Option[String] =
		h.detail match { case (commands, value) => if( selected exists commands ) Some(value) else None }

	def help = Command.simple(HelpCommand, helpBrief, helpDetailed) { (in, s) =>

		val h = applicable(s).flatMap(_.help(s))
		val argStr = (in.line stripPrefix HelpCommand).trim
		
		val message =
			if(argStr.isEmpty)
				h.map( _.brief match { case (a,b) => a + " : " + b } ).mkString("\n", "\n", "\n")
			else
				h flatMap detail( argStr.split("""\s+""", 0) ) mkString("\n", "\n\n", "\n")
		System.out.println(message)
		s
	}

	def alias = Command.simple(AliasCommand, AliasBrief, AliasDetailed) { (in, s) =>
		in.arguments.split("""\s*=\s*""",2).toSeq match {
			case Seq(name, value) => addAlias(s, name.trim, value.trim)
			case Seq(x) if !x.isEmpty=> printAlias(s, x.trim); s
			case _ => printAliases(s); s
		}
	}
	
	def shell = Command.simple(Shell, ShellBrief, ShellDetailed) { (in, s) =>
		val historyPath = (s get HistoryPath.key) getOrElse Some((s.baseDir / ".history").asFile)
		val reader = new LazyJLineReader(historyPath)
		val line = reader.readLine("> ")
		line match {
			case Some(line) => s.copy(onFailure = Some(Shell), commands = line +: Shell +: s.commands)
			case None => s
		}
	}
	
	def multi = Command.simple(Multi, MultiBrief, MultiDetailed) { (in, s) =>
		in.arguments.split(";").toSeq ::: s
	}
	
	def ifLast = Command.simple(IfLast, IfLastBrief, IfLastDetailed) { (in, s) =>
		if(s.commands.isEmpty) in.arguments :: s else s
	}
	def append = Command.simple(Append, AppendLastBrief, AppendLastDetailed) { (in, s) =>
		s.copy(commands = s.commands :+ in.arguments)
	}
	
	def setOnFailure = Command.simple(OnFailure, OnFailureBrief, OnFailureDetailed) { (in, s) =>
		s.copy(onFailure = Some(in.arguments))
	}

	def reload = Command.simple(ReloadCommand, ReloadBrief, ReloadDetailed) { (in, s) =>
		runExitHooks(s).reload
	}

	def defaults = Command.simple(DefaultsCommand) { (in, s) =>
		s ++ DefaultCommands
	}

	def initialize = Command.simple(InitCommand) { (in, s) =>
		/*"load-commands -base ~/.sbt/commands" :: */readLines( readable( sbtRCs(s) ) ) ::: s
	}

	def read = Command.simple(ReadCommand, ReadBrief, ReadDetailed) { (in, s) =>
		getSource(in, s.baseDir) match
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
	}
	private def getSource(in: Input, baseDirectory: File) =
	{
		try { Left(in.line.stripPrefix(ReadCommand).trim.toInt) }
		catch { case _: NumberFormatException => Right(in.splitArgs map { p => new File(baseDirectory, p) }) }
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
		Command( Help(continuousBriefHelp) ) { case (in, s) if in.line startsWith ContinuousExecutePrefix =>
			withAttribute(s, Watch.key, "Continuous execution not configured.") { w =>
				Watched.executeContinuously(w, s, in)
			}
		}

	def history = Command( historyHelp: _* ) { case (in, s) if in.line startsWith "!" =>
		val logError = (msg: String) => CommandSupport.logger(s).error(msg)
		HistoryCommands(in.line.substring(HistoryPrefix.length).trim, (s get HistoryPath.key) getOrElse None, 500/*JLine.MaxHistorySize*/, logError) match
		{
			case Some(commands) =>
				commands.foreach(println)  //printing is more appropriate than logging
				(commands ::: s).continue
			case None => s.fail
		}
	}

	def indent(withStar: Boolean) = if(withStar) "\t*" else "\t "
	def listProject(name: String, current: Boolean, log: Logger) = log.info( indent(current) + name )

	def act = error("TODO")
	def projects = Command.simple(ProjectsCommand, projectsBrief, projectsDetailed ) { (in,s) =>
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

	def project = Command.simple(ProjectCommand, projectBrief, projectDetailed ) { (in,s) =>
		val to = in.arguments
		val session = Project.session(s)
		val structure = Project.structure(s)
		val uri = session.currentBuild
		def setProject(id: String) = updateCurrent(s.put(SessionKey, session.setCurrent(uri, id)))
		if(to.isEmpty)
		{
			logger(s).info(session.currentProject(uri) + " (in build " + uri + ")")
			s
		}
		else if(to == "/")
		{
			val id = Load.getRootProject(structure.units)(uri)
			setProject(id)
		}
		else if(to.startsWith("^"))
		{
			val newBuild = (new java.net.URI(to substring 1)).normalize
			if(structure.units contains newBuild)
				updateCurrent(s.put(SessionKey, session.setCurrent(uri, session currentProject uri)))
			else
			{
				logger(s).error("Invalid build unit '" + newBuild + "' (type 'projects' to list available builds).")
				s
			}
		}
/*		else if(to.forall(_ == '.'))
			if(to.length > 1) gotoParent(to.length - 1, nav, s) else s */ // semantics currently undefined
		else if( structure.units(uri).defined.contains(to) )
			setProject(to)
		else
		{
			logger(s).error("Invalid project name '" + to + "' (type 'projects' to list available projects).")
			s.fail
		}
	}

	def exit = Command( Help(exitBrief) ) {
		case (in, s) if TerminateActions contains in.line =>
			runExitHooks(s).exit(true)
	}

	def discover = Command.simple(Discover, DiscoverBrief, DiscoverDetailed) { (in, s) =>
		withAttribute(s, Analysis, "No analysis to process.") { analysis =>
			val command = Parse.discover(in.arguments)
			val discovered = build.Build.discover(analysis, command)
			println(discovered.mkString("\n"))
			s
		}
	}
	def compile = Command.simple(CompileName, CompileBrief, CompileDetailed ) { (in, s) =>
		val command = Parse.compile(in.arguments)(s.baseDir)
		try {
			val analysis = build.Build.compile(command, s.configuration)
			s.put(Analysis, analysis)
		} catch { case e: xsbti.CompileFailed => s.fail /* already logged */ }
	}

	def loadProject = Command.simple(LoadProject, LoadProjectBrief, LoadProjectDetailed) { (in, s) =>
		val structure = Load.defaultLoad(s, logger(s))
		val session = Load.initialSession(structure)
		val newAttrs = s.attributes.put(StructureKey, structure).put(SessionKey, session)
		val newState = s.copy(attributes = newAttrs)
		updateCurrent(runExitHooks(newState))
	}

	def updateCurrent(s: State): State =
	{
		val structure = Project.structure(s)
		val (uri, id) = Project.current(s)
		val ref = ProjectRef(uri, id)
		val project = Load.getProject(structure.units, uri, id)
		logger(s).info("Set current project to " + id + " (in build " + uri +")")

		val data = structure.data
		val historyPath = HistoryPath(ref).get(data).flatMap(identity)
		val newAttrs = s.attributes.put(Watch.key, makeWatched(data, ref, project)).put(HistoryPath.key, historyPath)
		s.copy(attributes = newAttrs)
	}
	def makeWatched(data: Settings[Scope], ref: ProjectRef, project: Project): Watched =
	{
		def getWatch(ref: ProjectRef) = Watch(ref).get(data)
		getWatch(ref) match
		{
			case Some(currentWatch) =>
				val subWatches = project.uses flatMap { p =>  getWatch(p) }
				Watched.multi(currentWatch, subWatches)
			case None => Watched.empty
		}
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

	def loadCommands = Command.simple(LoadCommand, Parse.helpBrief(LoadCommand, LoadCommandLabel), Parse.helpDetail(LoadCommand, LoadCommandLabel, true) ) { (in, s) =>
		applyCommands(s, buildCommands(in.arguments, s.configuration))
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
		val in = Input(name, None)
		if(in.name == name) {
			val removed = removeAlias(s, name)
			if(value.isEmpty) removed else removed.copy(processors = new Alias(name, value) +: removed.processors)
		} else {
			System.err.println("Invalid alias name '" + name + "'.")
			s.fail
		}
	}
	def removeAlias(s: State, name: String): State =
		s.copy(processors = s.processors.filter { case a: Alias if a.name == name => false; case _ => true } )

	def printAliases(s: State): Unit = {
		val strings = aliasStrings(s)
		if(!strings.isEmpty) println( strings.mkString("\t", "\n\t","") )
	}

	def printAlias(s: State, name: String): Unit =
		for(a <- aliases(s)) if (a.name == name) println("\t" + name + " = " + a.value)

	def aliasStrings(s: State) = aliases(s).map(a => a.name + " = " + a.value)
	def aliases(s: State) = s.processors collect { case a: Alias => a }

	final class Alias(val name: String, val value: String) extends Command {
		assert(name.length > 0)
		assert(value.length > 0)
		def help = _ => Nil
		def run = (in, s) => if(in.name == name) Some((value + " " + in.arguments) :: s) else None
	}
}