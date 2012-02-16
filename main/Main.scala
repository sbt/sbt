/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010, 2011  Mark Harrah
 */
package sbt

	import complete.{DefaultParsers, Parser}
	import compiler.EvalImports
	import Types.idFun
	import Aggregation.AnyKeys

	import scala.annotation.tailrec
	import Path._
	import StandardMain._

	import java.io.File
	import java.net.URI

/** This class is the entry point for sbt.*/
final class xMain extends xsbti.AppMain
{
	def run(configuration: xsbti.AppConfiguration): xsbti.MainResult =
	{
		import BuiltinCommands.{initialize, defaults}
		import CommandStrings.{BootCommand, DefaultsCommand, InitCommand}
		MainLoop.runLogged( initialState(configuration,
			Seq(initialize, defaults),
			DefaultsCommand :: InitCommand :: BootCommand :: Nil)
		)
	}
}
final class ScriptMain extends xsbti.AppMain
{
	def run(configuration: xsbti.AppConfiguration): xsbti.MainResult =
		MainLoop.runLogged( initialState(configuration,
			BuiltinCommands.ScriptCommands,
			Script.Name :: Nil)
		)
}
final class ConsoleMain extends xsbti.AppMain
{
	def run(configuration: xsbti.AppConfiguration): xsbti.MainResult =
		MainLoop.runLogged( initialState(configuration,
			BuiltinCommands.ConsoleCommands,
			IvyConsole.Name :: Nil)
		)
}

object StandardMain
{
	def initialState(configuration: xsbti.AppConfiguration, initialDefinitions: Seq[Command], preCommands: Seq[String]): State =
	{
		val commands = preCommands ++ configuration.arguments.map(_.trim)
		State( configuration, initialDefinitions, Set.empty, None, commands, State.newHistory, BuiltinCommands.initialAttributes, initialGlobalLogging, State.Continue )
	}
	def initialGlobalLogging: GlobalLogging =
		GlobalLogging.initial(MainLogging.globalDefault _, File.createTempFile("sbt",".log"))
}

	import DefaultParsers._
	import CommandStrings._
	import BasicCommands._
	import CommandUtil._

object BuiltinCommands
{
	def initialAttributes = AttributeMap.empty

	def ConsoleCommands: Seq[Command] = Seq(ignore, exit, IvyConsole.command, act, nop)
	def ScriptCommands: Seq[Command] = Seq(ignore, exit, Script.command, act, nop)
	def DefaultCommands: Seq[Command] = Seq(ignore, help, about, reboot, read, history, continuous, exit, loadProject, loadProjectImpl, loadFailed, Cross.crossBuild, Cross.switchVersion,
		projects, project, setOnFailure, clearOnFailure, ifLast, multi, shell, set, tasks, inspect, eval, alias, append, last, lastGrep, boot, nop, sessionCommand, call, act)
	def DefaultBootCommands: Seq[String] = LoadProject :: (IfLast + " " + Shell) :: Nil

	def boot = Command.make(BootCommand)(bootParser)

	def about = Command.command(AboutCommand, aboutBrief, aboutDetailed) { s => logger(s).info(aboutString(s)); s }

	// This parser schedules the default boot commands unless overridden by an alias
	def bootParser(s: State) =
	{
		val orElse = () => DefaultBootCommands ::: s
		delegateToAlias(BootCommand, success(orElse) )(s)
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

	def defaults = Command.command(DefaultsCommand) { s =>
		s ++ DefaultCommands
	}

	def initialize = Command.command(InitCommand) { s =>
		/*"load-commands -base ~/.sbt/commands" :: */readLines( readable( sbtRCs(s) ) ) ::: s
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
	def set = Command(SetCommand, setBrief, setDetailed)(setParser) { case (s, (all, arg)) =>
		val extracted = Project extract s
		import extracted._
		val settings = EvaluateConfigurations.evaluateSetting(session.currentEval(), "<set>", imports(extracted), arg, LineRange(0,0))(currentLoader)
		val newSession = if(all) Project.setAll(extracted, settings) else setThis(s, extracted, settings, arg)
		reapply(newSession, structure, s)
	}
	def setThis(s: State, extracted: Extracted, settings: Seq[Project.Setting[_]], arg: String) =
	{
		import extracted._
		val append = Load.transformSettings(Load.projectScope(currentRef), currentRef.build, rootProject, settings)
		session.appendSettings( append map (a => (a, arg)))
	}
	def inspect = Command(InspectCommand, inspectBrief, inspectDetailed)(inspectParser) { case (s, (option, sk)) => 
		logger(s).info(inspectOutput(s, option, sk))
		s
	}
	def inspectOutput(s: State, option: InspectOption, sk: Project.ScopedKey[_]): String =
	{
		val extracted = Project.extract(s)
			import extracted._
		option match
		{
			case InspectOption.Details(actual) =>
				Project.details(structure, actual, sk.scope, sk.key)
			case InspectOption.DependencyTree => 
				val basedir = new File(Project.session(s).current.build)
				Project.settingGraph(structure, basedir, sk).dependsAscii
			case InspectOption.Uses =>
				Project.showUses(Project.usedBy(structure, true, sk.key))
			case InspectOption.Definitions =>
				Project.showDefinitions(sk.key, Project.definitions(structure, true, sk.key))
		}
	}
	def lastGrep = Command(LastGrepCommand, lastGrepBrief, lastGrepDetailed)(lastGrepParser) {
		case (s, (pattern,Some(sks))) =>
			val (str, ref, display) = extractLast(s)
			Output.lastGrep(sks, str.streams(s), pattern, printLast(s))(display)
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

	def setParser = (s: State) => token(Space ~> flag("every" ~ Space)) ~ token(any.+.string)

		import InspectOption._
	def inspectParser = (s: State) => spacedInspectOptionParser(s) flatMap {
		case opt @ (Uses | Definitions) => allKeyParser(s).map(key => (opt, Project.ScopedKey(Global, key)))
		case opt @ (DependencyTree | Details(_)) => spacedKeyParser(s).map(key => (opt, key))
	}
	val spacedInspectOptionParser: (State => Parser[InspectOption]) = (s: State) => {
		val actual = "actual" ^^^ Details(true)
		val tree = "tree" ^^^ DependencyTree
		val uses = "uses" ^^^ Uses
		val definitions = "definitions" ^^^ Definitions
		token(Space ~> (tree | actual | uses | definitions)) ?? Details(false)
	}
	def allKeyParser(s: State): Parser[AttributeKey[_]] =
	{
		val keyMap = Project.structure(s).index.keyMap
		token(Space ~> (ID !!! "Expected key" examples keyMap.keySet)) flatMap { key => Act.getKey(keyMap, key, idFun) }
	}

	val spacedKeyParser = (s: State) => Act.requireSession(s, token(Space) ~> Act.scopedKeyParser(s))
	val spacedAggregatedParser = (s: State) => Act.requireSession(s, token(Space) ~> Act.aggregatedKeyParser(s))
	val aggregatedKeyValueParser: State => Parser[Option[AnyKeys]] =
		(s: State) => spacedAggregatedParser(s).map(x => Act.keyValues(s)(x) ).?

	def lastGrepParser(s: State) = Act.requireSession(s, (token(Space) ~> token(NotSpace, "<pattern>")) ~ aggregatedKeyValueParser(s))
	def last = Command(LastCommand, lastBrief, lastDetailed)(aggregatedKeyValueParser) {
		case (s,Some(sks)) =>
			val (str, ref, display) = extractLast(s)
			Output.last(sks, str.streams(s), printLast(s))(display)
			keepLastLog(s)
		case (s, None) =>
			for(logFile <- lastLogFile(s)) yield
				Output.last( logFile, printLast(s) )
			keepLastLog(s)
	}

	/** Determines the log file that last* commands should operate on.  See also isLastOnly. */
	def lastLogFile(s: State) =
	{
		val backing = s.globalLogging.backing
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
	def actHelp = (s: State) => CommandStrings.showHelp ++ keysHelp(s)
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

	def project = Command.make(ProjectCommand, projectBrief, projectDetailed)(ProjectNavigation.command)

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
		val session = Load.initialSession(structure, eval, s0)
		SessionSettings.checkSession(session, s)
		Project.setProject(session, structure, s)
	}
}
