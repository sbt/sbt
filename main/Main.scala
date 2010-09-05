/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010  Mark Harrah
 */
package sbt

import Execute.NodeView
import complete.HistoryCommands
import HistoryCommands.{Start => HistoryPrefix}
import sbt.build.{AggressiveCompile, Auto, Build, BuildException, LoadCommand, Parse, ParseException, ProjectLoad, SourceLoad}
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
		val state = State( () )( configuration, initialCommandDefs, Set.empty, None, commands, AttributeMap.empty, Next.Continue )
		run(state)
	}
		
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
		Commands.applicable(state).flatMap( _.run(in) ).headOption.getOrElse {
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
	def DefaultCommands = Seq(help, reload, read, history, exit, load, loadCommands, compile, discover,
		projects, project, setOnFailure, ifLast, multi, shell, alias, append, act)

	def applicable(state: State): Stream[Apply] =
		state.processors.toStream.flatMap(_.applies(state) )

	def detail(selected: Iterable[String])(h: Help): Option[String] =
		h.detail match { case (commands, value) => if( selected exists commands ) Some(value) else None }

	def help = Command.simple(HelpCommand, helpBrief, helpDetailed) { (in, s) =>

		val h = applicable(s).flatMap(_.help)
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
		val historyPath = s.project match { case he: HistoryEnabled => he.historyPath; case _ => Some(s.baseDir / ".history") }
		val reader = new LazyJLineReader(historyPath, new LazyCompletor(completor(s)))
		val line = reader.readLine("> ")
		line match {
			case Some(line) => s.copy()(onFailure = Some(Shell), commands = line +: Shell +: s.commands)
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
		s.copy()(commands = s.commands :+ in.arguments)
	}
	
	def setOnFailure = Command.simple(OnFailure, OnFailureBrief, OnFailureDetailed) { (in, s) =>
		s.copy()(onFailure = Some(in.arguments))
	}

	def reload = Command.simple(ReloadCommand, ReloadBrief, ReloadDetailed) { (in, s) =>
		runExitHooks(s).reload
	}

	def defaults = Command.simple(DefaultsCommand, DefaultsBrief, DefaultsDetailed) { (in, s) =>
		s ++ DefaultCommands
	}

	def initialize = Command.simple(InitCommand, InitBrief, InitDetailed) { (in, s) =>
		/*"load-commands -base ~/.sbt/commands" :: */readLines( readable( sbtRCs(s) ) ) ::: s
	}

	def read = Command.simple(ReadCommand, ReadBrief, ReadDetailed) { (in, s) =>
		val from = in.splitArgs map { p => new File(s.baseDir, p) }
		val notFound = notReadable(from)
		if(notFound.isEmpty)
			readLines(from) ::: s // this means that all commands from all files are loaded, parsed, and inserted before any are executed
		else {
			logger(s).error("File(s) not readable: \n\t" + notFound.mkString("\n\t"))
			s
		}
	}

	def history = Command { case s @ State(p: HistoryEnabled) =>
		Apply( historyHelp: _* ) {
			case in if in.line startsWith("!") => 
				val logError: (String => Unit) = p match { case l: Logged => (s: String) => l.log.error(s) ; case _ => System.err.println _ }
				HistoryCommands(in.line.substring(HistoryPrefix.length).trim, p.historyPath, 500/*JLine.MaxHistorySize*/, logError) match
				{
					case Some(commands) =>
						commands.foreach(println)  //printing is more appropriate than logging
						(commands ::: s).continue
					case None => s.fail
				}
		}
	}

	def indent(withStar: Boolean) = if(withStar) "\t*" else "\t"
	def listProject(p: Named, current: Boolean, log: Logger) = printProject( indent(current), p, log)
	def printProject(prefix: String, p: Named, log: Logger) = log.info(prefix + p.name)

	def projects = Command { case s @ State(d: Member[_]) =>
		Apply.simple(ProjectsCommand, projectsBrief, projectsDetailed ) { (in,s) =>
			val log = logger(s)
			d.projectClosure.foreach { case n: Named => listProject(n, d eq n, log) }
			s
		}(s)
	}

	def project = Command { case s @ State(d: Member[_] with Named) =>
		Apply.simple(ProjectCommand, projectBrief, projectDetailed ) { (in,s) =>
			val to = in.arguments
			if(to.isEmpty)
			{
				logger(s).info(d.name)
				s
			}
			else
			{
				d.projectClosure.find { case n: Named => n.name == to; case _ => false } match
				{
					case Some(np) => logger(s).info("Set current project to " + to); s.copy(np)()
					case None => logger(s).error("Invalid project name '" + to + "' (type 'projects' to list available projects)."); s.fail
				}
			}
		}(s)
	}
	
	def exit = Command { case s => Apply( Help(exitBrief) ) {
		case in if TerminateActions contains in.line =>
			runExitHooks(s).exit(true)
		}
	}

	def act = Command { case s @ State(p: Tasked) =>
		new Apply {
			def help = p.help
			def complete = in => Completions()
			def run = in => {
				val (checkCycles, maxThreads) = p match {
					case c: TaskSetup => (c.checkCycles, c.maxThreads)
					case _ => (false, Runtime.getRuntime.availableProcessors)
				}
				for( (task, taskToNode) <- p.act(in, s)) yield
					processResult(runTask(task, checkCycles, maxThreads)(taskToNode), s)
			}
		}
	}

	def discover = Command { case s @ State(analysis: inc.Analysis) =>
		Apply.simple(Discover, DiscoverBrief, DiscoverDetailed) { (in, s) =>
			val command = Parse.discover(in.arguments)
			val discovered = Build.discover(analysis, command)
			println(discovered.mkString("\n"))
			s
		}(s)
	}
	def compile = Command.simple(CompileName, CompileBrief, CompileDetailed ) { (in, s) =>
		val command = Parse.compile(in.arguments)(s.baseDir)
		try {
			val analysis = Build.compile(command, s.configuration)
			s.copy(project = analysis)()
		} catch { case e: xsbti.CompileFailed => s.fail /* already logged */ }
	}
	def load = Command.simple(Load, Parse.helpBrief(Load, LoadLabel), Parse.helpDetail(Load, LoadLabel, false) ) { (in, s) =>
		loadCommand(in.arguments, s.configuration, false, "sbt.Project") match // TODO: classOf[Project].getName when ready
		{
			case Right(Seq(newValue)) => runExitHooks(s).copy(project = newValue)()
			case Left(e) => handleException(e, s, false)
		}
	}

	def loadProject = Command.simple(LoadProject, LoadProjectBrief, LoadProjectDetailed) { (in, s) =>
		val base = s.configuration.baseDirectory
		val p = MultiProject.load(s.configuration, ConsoleLogger() /*TODO*/)(base)
		val exts = p match { case pc: ProjectContainer => MultiProject.loadExternals(pc :: Nil, p.info.construct); case _ => Map.empty[File, Project] }
		s.copy(project = p)().put(MultiProject.ExternalProjects, exts.updated(base, p))
	}
	
	def handleException(e: Throwable, s: State, trace: Boolean = true): State = {
		// TODO: log instead of print
		if(trace)
			e.printStackTrace
		System.err.println(e.toString)
		s.fail
	}
	
	def runExitHooks(s: State): State = {
		ExitHooks.runExitHooks(s.exitHooks.toSeq)
		s.copy()(exitHooks = Set.empty)
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
				s.copy()(processors = asCommands ++ s.processors)
			case Left(e) => handleException(e, s, false)
		}
	
	def loadCommand(line: String, configuration: xsbti.AppConfiguration, allowMultiple: Boolean, defaultSuper: String): Either[Throwable, Seq[Any]] =
		try
		{
			val parsed = Parse(line)(configuration.baseDirectory)
			Right( Build( translateEmpty(parsed, defaultSuper), configuration, allowMultiple) )
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
	def processResult[State](result: Result[State], original: State): State =
		result match
		{
			case Value(v) => v
			case Inc(Incomplete(tpe, message, causes, directCause)) => // tpe: IValue = Error, message: Option[String] = None, causes: Seq[Incomplete] = Nil, directCause: Option[Throwable] = None)
				println("Task did not complete successfully (TODO: error logging)")
				directCause.foreach(_.printStackTrace)
				original
		}
		
	def completor(s: State): jline.Completor = new jline.Completor {
		lazy val apply = applicable(s)
		def complete(buffer: String, cursor: Int, candidates: java.util.List[_]): Int =
		{
			val correct = candidates.asInstanceOf[java.util.List[String]]
			val in = Input(buffer, Some(cursor))
			val completions = apply.map(_.complete(in))
			val maxPos = if(completions.isEmpty) -1 else completions.map(_.position).max
			correct ++= ( completions flatMap { c => if(c.position == maxPos) c.candidates else Nil } )
			maxPos
		}
	}
	def addAlias(s: State, name: String, value: String): State =
	{
		val in = Input(name, None)
		if(in.name == name) {
			val removed = removeAlias(s, name)
			if(value.isEmpty) removed else removed.copy()(processors = new Alias(name, value) +: removed.processors)
		} else {
			System.err.println("Invalid alias name '" + name + "'.")
			s.fail
		}
	}
	def removeAlias(s: State, name: String): State =
		s.copy()(processors = s.processors.filter { case a: Alias if a.name == name => false; case _ => true } )

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
		def applies = s => Some(Apply() {
			case in if in.name == name=> (value + " " + in.arguments) :: s
		})
	}
}