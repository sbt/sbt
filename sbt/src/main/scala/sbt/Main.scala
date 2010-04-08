/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010  Steven Blundy, Mark Harrah, David MacIver, Mikko Peltonen
 */
package sbt

import java.io.File
import scala.collection.immutable.TreeSet

/** This class is the entry point for sbt.  If it is given any arguments, it interprets them
* as actions, executes the corresponding actions, and exits.  If there were no arguments provided,
* sbt enters interactive mode.*/
object Main
{
	val NormalExitCode = 0
	val SetupErrorExitCode = 1
	val SetupDeclinedExitCode = 2
	val LoadErrorExitCode = 3
	val UsageErrorExitCode = 4
	val BuildErrorExitCode = 5
	val ProgramErrorExitCode = 6
	val MaxInt = java.lang.Integer.MAX_VALUE
}

import Main._

class xMain extends xsbti.AppMain
{
	final def run(configuration: xsbti.AppConfiguration): xsbti.MainResult =
	{
		def run0(remainingArguments: List[String], buildScalaVersion: Option[String]): xsbti.MainResult =
		{
			// done this way because in Scala 2.7.7, tail recursion in catch blocks is not optimized
			val result = try { Right(run(configuration, remainingArguments, buildScalaVersion)) } catch { case re: ReloadException => Left(re) }
			result match
			{
				case Left(re) => run0(re.remainingArguments, re.buildScalaVersion)
				case Right(r) => r
			}
		}
		run0(configuration.arguments.map(_.trim).toList, None)
	}
	final def run(configuration: xsbti.AppConfiguration, remainingArguments: List[String], buildScalaVersion: Option[String]): xsbti.MainResult =
	{
		val startTime = System.currentTimeMillis
		Project.loadProject(configuration.provider, buildScalaVersion) match
		{
			case err: LoadSetupError =>
				println("\n" + err.message)
				ExitHooks.runExitHooks(Project.bootLogger)
				Exit(SetupErrorExitCode)
			case LoadSetupDeclined =>
				ExitHooks.runExitHooks(Project.bootLogger)
				Exit(SetupDeclinedExitCode)
			case err: LoadError =>
			{
				val log = Project.bootLogger
				println(err.message)
				ExitHooks.runExitHooks(log)
				// Because this is an error that can probably be corrected, prompt user to try again.
				val line =
					try { SimpleReader.readLine("\n Hit enter to retry or 'exit' to quit: ") }
					catch
					{
						case e =>
							log.trace(e)
							log.error(e.toString)
							None
					}
				line match
				{
					case Some(l) => if(!isTerminateAction(l)) run(configuration, remainingArguments, buildScalaVersion) else Exit(NormalExitCode)
					case None => Exit(LoadErrorExitCode)
				}
			}
			case success: LoadSuccess =>
			{
				import success.project
				try
				{
					// in interactive mode, fill all undefined properties
					if(configuration.arguments.length > 0 || fillUndefinedProjectProperties(project.projectClosure.toList.reverse))
						startProject(project, configuration, remainingArguments, startTime)
					else
						Exit(NormalExitCode)
				}
				finally { ExitHooks.runExitHooks(project.log) }
			}
		}
	}
	/** If no arguments are provided, drop to interactive prompt.
	* If the user wants to run commands before dropping to the interactive prompt,
	*   make dropping to the interactive prompt the action to perform on failure */
	private def initialize(args: List[String]): List[String] =
		args.lastOption match
		{
			case None => InteractiveCommand :: Nil
			case Some(InteractiveCommand) => (FailureHandlerPrefix + InteractiveCommand) :: args
			case Some(ExitCommand | QuitCommand) => args
			case _ => args ::: ExitCommand :: Nil
		}
	private def startProject(project: Project, configuration: xsbti.AppConfiguration, remainingArguments: List[String], startTime: Long): xsbti.MainResult =
	{
		project.log.info("Building project " + project.name + " " + project.version.toString + " against Scala " + project.buildScalaVersion)
		project.log.info("   using " + project.getClass.getName + " with sbt " + ComponentManager.version + " and Scala " + project.defScalaVersion.value)
		processArguments(project, initialize(remainingArguments), configuration, startTime) match
		{
			case e: xsbti.Exit =>
				printTime(project, startTime, "session")
				if(e.code == NormalExitCode)
					project.log.success("Build completed successfully.")
				else
					project.log.error("Error during build.")
				e
			case r => r
		}
	}
	/** This is the top-level command processing method. */
	private def processArguments(baseProject: Project, arguments: List[String], configuration: xsbti.AppConfiguration, startTime: Long): xsbti.MainResult =
	{
		type OnFailure = Option[String]
		def ExitOnFailure = None
		lazy val interactiveContinue = Some( InteractiveCommand )
		def remoteContinue(port: Int) = Some( FileCommandsPrefix + "-" + port )
		lazy val PHandler = new processor.Handler(baseProject)
		
		// replace in 2.8
		trait Trampoline
		class Done(val r: xsbti.MainResult) extends Trampoline
		class Continue(project: Project, arguments: List[String], failAction: OnFailure) extends Trampoline {
			def apply() = process(project, arguments, failAction)
		}
		def continue(project: Project, arguments: List[String], failAction: OnFailure) = new Continue(project, arguments, failAction)
		def result(r: xsbti.MainResult) = new Done(r)
		def run(t: Trampoline): xsbti.MainResult = t match { case d: Done => d.r; case c: Continue => run(c()) }

		def process(project: Project, arguments: List[String], failAction: OnFailure): Trampoline =
		{
			project.log.debug("commands " + failAction.map("(on failure: " + _ + "): ").mkString + arguments.mkString(", "))
			def rememberCurrent(newArgs: List[String]) = rememberProject(rememberFail(newArgs))
			def rememberProject(newArgs: List[String]) =
				if(baseProject.name != project.name && !internal(project)) (ProjectAction + " " + project.name) :: newArgs else newArgs
			def rememberFail(newArgs: List[String]) = failAction.map(f => (FailureHandlerPrefix + f)).toList :::  newArgs

			def tryOrFail(action: => Trampoline)  =  try { action } catch { case e: Exception => logCommandError(project.log, e); failed(BuildErrorExitCode) }
			def reload(args: List[String]) =
			{
				val newID = new ApplicationID(configuration.provider.id, baseProject.sbtVersion.value)
				result( new Reboot(project.defScalaVersion.value, rememberCurrent(args), newID, configuration.baseDirectory) )
			}
			def failed(code: Int) =
				failAction match
				{
					case Some(c) => continue(project, c :: Nil, ExitOnFailure)
					case None => result( Exit(code) )
				}
				
			arguments match
			{
				case "" :: tail => continue(project, tail, failAction)
				case x :: tail if x.startsWith(";") => continue(project, x.split("""\s*;\s*""").toList ::: tail, failAction)
				case (ExitCommand | QuitCommand) :: _ => result( Exit(NormalExitCode) )
				case RebootCommand :: tail => reload( tail )
				case InteractiveCommand :: _ => continue(project, prompt(baseProject, project) :: arguments, interactiveContinue)
				case BuilderCommand :: tail =>
					Project.getProjectBuilder(project.info, project.log) match
					{
						case Some(b) => project.log.info("Set current project to builder of " + project.name); continue(b, tail, failAction)
						case None => project.log.error("No project/build directory for " + project.name + ".\n  Not switching to builder project."); failed(BuildErrorExitCode)
					}
				case SpecificBuild(version, action) :: tail =>
					if(Some(version) != baseProject.info.buildScalaVersion)
					{
						if(checkVersion(baseProject, version))
							throw new ReloadException(rememberCurrent(action :: tail), Some(version))
						else
							failed(UsageErrorExitCode)
					}
					else
						continue(project, action :: tail, failAction)

				case CrossBuild(action) :: tail =>
					if(checkAction(project, action))
					{
						CrossBuild(project, action) match
						{
							case Some(actions) => continue(project, actions ::: tail, failAction)
							case None => failed(UsageErrorExitCode)
						}
					}
					else
						failed(UsageErrorExitCode)

				case SetProject(name) :: tail =>
					SetProject(baseProject, name, project) match
					{
						case Some(newProject) => continue(newProject, tail, failAction)
						case None => failed(BuildErrorExitCode)
					}
					
				case action :: tail if action.startsWith(FileCommandsPrefix) =>
					getSource(action.substring(FileCommandsPrefix.length).trim, baseProject.info.projectDirectory) match
					{
						case Left(portAndSuccess) =>
							val port = Math.abs(portAndSuccess)
							val previousSuccess = portAndSuccess >= 0
							readMessage(port, previousSuccess) match
							{
								case Some(message) => continue(project, message :: (FileCommandsPrefix + port) :: Nil, remoteContinue(port))
								case None =>
									project.log.error("Connection closed.")
									failed(BuildErrorExitCode)
							}
							
						case Right(file) =>
							readLines(project, file) match
							{
								case Some(lines) => continue(project, lines ::: tail , failAction)
								case None => failed(UsageErrorExitCode)
							}
					}
					
				case action :: tail if action.startsWith(FailureHandlerPrefix) =>
					val errorAction = action.substring(FailureHandlerPrefix.length).trim
					continue(project, tail, if(errorAction.isEmpty) None else Some(errorAction) )

				case action :: tail if action.startsWith(ProcessorPrefix) =>
					val processorCommand = action.substring(ProcessorPrefix.length).trim
					val runner = processor.CommandRunner(PHandler.manager, PHandler.defParser, ProcessorPrefix, project.log)
					tryOrFail {
						runner(processorCommand)
						continue(project, tail, failAction)
					}

				case PHandler(parsed) :: tail =>
					 tryOrFail {
						parsed.processor(parsed.label, project, failAction, parsed.arguments) match
						{
							case s: processor.Success => continue(s.project, s.insertArguments ::: tail, s.onFailure)
							case e: processor.Exit => result( Exit(e.code) )
							case r: processor.Reload => reload( r.insertArguments ::: tail )
						}
					 }
		
				case action :: tail =>
					val success = processAction(baseProject, project, action, failAction == interactiveContinue)
					if(success) continue(project, tail, failAction)
					else failed(BuildErrorExitCode)
						
				case Nil =>
					project.log.error("Invalid internal sbt state: no arguments")
					result( Exit(ProgramErrorExitCode) )
			}
		}
		run(process(baseProject, arguments, ExitOnFailure))
	}
	private def internal(p: Project) = p.isInstanceOf[InternalProject]
	private def isInteractive(failureActions: Option[List[String]]) = failureActions == Some(InteractiveCommand :: Nil)
	private def getSource(action: String, baseDirectory: File) =
	{
		try { Left(action.toInt) }
		catch { case _: NumberFormatException => Right(new File(baseDirectory, action)) }
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
	object SetProject
	{
		def unapply(s: String) =
			if(s.startsWith(ProjectAction + " "))
				Some(s.substring(ProjectAction.length + 1))
			else
				None
		def apply(baseProject: Project, projectName: String, currentProject: Project) =
		{
			val found = baseProject.projectClosure.find(_.name == projectName)
			found match
			{
				case Some(newProject) => printProject("Set current project to ", newProject)
				case None => currentProject.log.error("Invalid project name '" + projectName + "' (type 'projects' to list available projects).")
			}
			found
		}
	}
	object SpecificBuild
	{
		import java.util.regex.Pattern.{compile,quote}
		val pattern = compile(quote(SpecificBuildPrefix) + """\s*(\S+)\s*(.*)""")
		def unapply(s: String) =
		{
			val m = pattern.matcher(s)
			if(m.matches)
				Some(m.group(1).trim, m.group(2).trim)
			else
				None
		}
	}
	def checkVersion(p: Project, version: String) =
	{
		try { p.getScalaInstance(version); true }
		catch { case e: xsbti.RetrieveException => p.log.error(e.getMessage); false }
	}
	object CrossBuild
	{
		def unapply(s: String) = if(s.startsWith(CrossBuildPrefix) && !s.startsWith(SpecificBuildPrefix)) Some(s.substring(1)) else None
		def apply(project: Project, action: String): Option[List[String]] =
		{
			val againstScalaVersions = project.crossScalaVersions
			if(againstScalaVersions.isEmpty)
			{
				Console.println("Project does not declare any Scala versions to cross-build against, building against current version...")
				Some(action :: Nil)
			}
			else
			{
				if( !againstScalaVersions.forall(v => checkVersion(project, v)) )
					None
				else
				{
					val actions = 
						againstScalaVersions.toList.map(SpecificBuildPrefix + _ + " " + action) ::: // build against all versions
							(SpecificBuildPrefix + project.buildScalaVersion) :: // reset to the version before the cross-build
							Nil
					Some(actions)
				}
			}
		}
	}
	private def readLines(project: Project, file: File): Option[List[String]] =
	{
		try { Some(xsbt.FileUtilities.readLines(file)) }
		catch { case e: Exception =>
			project.log.trace(e)
			project.log.error("Error reading commands from file " + file.getAbsolutePath + ": " + e.toString)
			None
		}
	}
	private def prompt(baseProject: Project, project: Project): String =
	{
		// the times for evaluating the lazy vals here are a few hundred ms out of a 2s startup
		lazy val projectNames = baseProject.projectClosure.map(_.name)
		val prefixes = ContinuousExecutePrefix :: CrossBuildPrefix :: Nil
		lazy val scalaVersions = baseProject.crossScalaVersions ++ Seq(baseProject.defScalaVersion.value)
		lazy val methods = project.methods
		lazy val methodCompletions = new ExtraCompletions { def names = methods.keys.toList; def completions(name: String) = methods(name).completions }
		lazy val completors = new Completors(ProjectAction, projectNames, basicCommands, List(GetAction, SetAction), SpecificBuildPrefix, scalaVersions, prefixes, project.taskNames, project.propertyNames, methodCompletions)
		val reader = new LazyJLineReader(baseProject.historyPath, MainCompletor(completors), baseProject.log)
		reader.readLine("> ").getOrElse(ExitCommand)
	}

	/** The name of the command that loads a console with access to the current project through the variable 'project'.*/
	val ProjectConsoleAction = "console-project"
	/** The name of the command that shows the current project and logging level of that project.*/
	val ShowCurrent = "current"
	/** The name of the command that shows all available actions.*/
	val ShowActions = "actions"
	/** The name of the command that sets the currently active project.*/
	val ProjectAction = "project"
	/** The name of the command that shows all available projects.*/
	val ShowProjectsAction = "projects"
	val ExitCommand = "exit"
	val QuitCommand = "quit"
	val BuilderCommand = "builder"
	val InteractiveCommand = "shell"
	/** The list of lowercase command names that may be used to terminate the program.*/
	val TerminateActions: Iterable[String] = ExitCommand :: QuitCommand :: Nil
	/** The name of the command that sets the value of the property given as its argument.*/
	val SetAction = "set"
	/** The name of the command that gets the value of the property given as its argument.*/
	val GetAction = "get"
	/** The name of the command that displays the help message. */
	val HelpAction = "help"
	/** The command for reloading sbt.*/
	val RebootCommand = "reload"
	/** The name of the command that toggles logging stacktraces. */
	val TraceCommand = "trace"
	/** The name of the command that compiles all sources continuously when they are modified. */
	val ContinuousCompileCommand = "cc"
	/** The prefix used to identify a request to execute the remaining input on source changes.*/
	val ContinuousExecutePrefix = "~"
	/** The prefix used to identify a request to execute the remaining input across multiple Scala versions.*/
	val CrossBuildPrefix = "+"
	/** The prefix used to identify a request to execute the remaining input after the next space against the
	* Scala version between this prefix and the space (i.e. '++version action' means execute 'action' using
	* Scala version 'version'. */
	val SpecificBuildPrefix = "++"
	/** The prefix used to identify a file or local port to read commands from. */
	val FileCommandsPrefix = "<"
	/** The prefix used to identify the action to run after an error*/
	val FailureHandlerPrefix = "!"
	/** The prefix used to identify commands for managing processors.*/
	val ProcessorPrefix = "*"

	/** The number of seconds between polling by the continuous compile command.*/
	val ContinuousCompilePollDelaySeconds = 1

	/** The list of logging levels.*/
	private def logLevels: Iterable[String] = TreeSet.empty[String] ++ Level.levels.map(_.toString)
	/** The list of all interactive commands other than logging level.*/
	private def basicCommands: Iterable[String] = TreeSet(ShowProjectsAction, ShowActions, ShowCurrent, HelpAction,
		RebootCommand, TraceCommand, ContinuousCompileCommand, ProjectConsoleAction, BuilderCommand)  ++ logLevels.toList ++ TerminateActions

	private def processAction(baseProject: Project, currentProject: Project, action: String, isInteractive: Boolean): Boolean =
		action match
		{
			case HelpAction => displayHelp(isInteractive); true
			case ShowProjectsAction => baseProject.projectClosure.foreach(listProject); true
			case ProjectConsoleAction =>
				showResult(Run.projectConsole(currentProject), currentProject.log)
			case _ =>
				if(action.startsWith(SetAction + " "))
					setProperty(currentProject, action.substring(SetAction.length + 1))
				else if(action.startsWith(GetAction + " "))
					getProperty(currentProject, action.substring(GetAction.length + 1))
				else if(action.startsWith(TraceCommand + " "))
					setTrace(currentProject, action.substring(TraceCommand.length + 1))
				else
					handleCommand(currentProject, action)
		}

	private def printCmd(name:String, desc:String) = Console.println("   " + name + " : " + desc)
	val BatchHelpHeader = "You may execute any project action or method or one of the commands described below."
	val InteractiveHelpHeader = "You may execute any project action or one of the commands described below. Only one action " +
			"may be executed at a time in interactive mode and is entered by name, as it would be at the command line." +
			" Also, tab completion is available."
	private def displayHelp(isInteractive: Boolean)
	{
		Console.println(if(isInteractive) InteractiveHelpHeader else BatchHelpHeader)
		Console.println("Available Commands:")

		printCmd("<action name>", "Executes the project specified action.")
		printCmd("<method name> <parameter>*", "Executes the project specified method.")
		printCmd("<processor label> <arguments>", "Runs the specified processor.")
		printCmd(ContinuousExecutePrefix + " <command>", "Executes the project specified action or method whenever source files change.")
		printCmd(FileCommandsPrefix + " file", "Executes the commands in the given file.  Each command should be on its own line.  Empty lines and lines beginning with '#' are ignored")
		printCmd(CrossBuildPrefix + " <command>", "Executes the project specified action or method for all versions of Scala defined in crossScalaVersions.")
		printCmd(SpecificBuildPrefix + "<version> <command>", "Changes the version of Scala building the project and executes the provided command.  <command> is optional.")
		printCmd(ProcessorPrefix, "Prefix for commands for managing processors.  Run '" + ProcessorPrefix + "help' for details.")
		printCmd(ShowActions, "Shows all available actions.")
		printCmd(RebootCommand, "Reloads sbt, picking up modifications to sbt.version or scala.version and recompiling modified project definitions.")
		printCmd(HelpAction, "Displays this help message.")
		printCmd(ShowCurrent, "Shows the current project, Scala version, and logging level.")
		printCmd(Level.levels.mkString(", "), "Set logging for the current project to the specified level.")
		printCmd(TraceCommand + " " + validTraceArguments, "Configures stack trace logging. " + traceExplanation)
		printCmd(ProjectAction + " <project name>", "Sets the currently active project.")
		printCmd(ShowProjectsAction, "Shows all available projects.")
		printCmd(TerminateActions.elements.mkString(", "), "Terminates the build.")
		printCmd(SetAction + " <property> <value>", "Sets the value of the property given as its argument.")
		printCmd(GetAction + " <property>", "Gets the value of the property given as its argument.")
		printCmd(ProjectConsoleAction, "Enters the Scala interpreter with the current project definition bound to the variable 'current' and all members imported.")
		if(!isInteractive)
			printCmd(InteractiveCommand, "Enters the sbt interactive shell")
	}
	private def listProject(p: Project) = printProject("\t", p)
	private def printProject(prefix: String, p: Project): Unit =
		Console.println(prefix + p.name + " " + p.version)

	/** Handles the given command string provided at the command line.  Returns false if there was an error*/
	private def handleCommand(project: Project, command: String): Boolean =
	{
		command match
		{
			case GetAction => getArgumentError(project.log)
			case SetAction => setArgumentError(project.log)
			case ProjectAction => setProjectError(project.log)
			case TraceCommand => setTraceError(project.log); true
			case ShowCurrent =>
				printProject("Current project is ", project)
				Console.println("Current Scala version is " + project.buildScalaVersion)
				Console.println("Current log level is " + project.log.getLevel)
				printTraceEnabled(project)
				true
			case ShowActions => showActions(project); true
			case Level(level) => setLevel(project, level); true
			case ContinuousCompileCommand => compileContinuously(project)
			case action if action.startsWith(ContinuousExecutePrefix) => executeContinuously(project, action.substring(ContinuousExecutePrefix.length).trim)
			case action => handleAction(project, action)
		}
	}
	private def showActions(project: Project): Unit = Console.println(project.taskAndMethodList)

	// returns true if it succeeded
	private def handleAction(project: Project, action: String): Boolean =
	{
		def show(result: Option[String]): Boolean = showResult(result, project.log)
		val startTime = System.currentTimeMillis
		val result = withAction(project, action)( (name, params) => show(project.call(name, params)))( name => show(project.act(name)))
		printTime(project, startTime, "")
		result
	}
	// returns true if it succeeded
	private def showResult(result: Option[String], log: Logger): Boolean =
	{
		result match
		{
			case Some(errorMessage) => log.error(errorMessage); false
			case None => log.success("Successful."); true
		}
	}
	// true if the action exists
	private def checkAction(project: Project, actionString: String): Boolean =
		withAction(project, actionString)(  (n,p) => true)( n => true)
	private def withAction(project: Project, actionString: String)(ifMethod: (String, Array[String]) => Boolean)(ifAction: String => Boolean): Boolean =
	{
		def didNotExist(taskType: String, name: String) =
		{
			project.log.error("No " + taskType + " named '" + name + "' exists.")
			project.log.info("Execute 'help' for a list of commands or 'actions' for a list of available project actions and methods.")
			false
		}
		impl.CommandParser.parse(actionString) match
		{
			case Left(errMsg) => project.log.error(errMsg); false
			case Right((name, parameters)) =>
				if(project.methods.contains(name))
					ifMethod(name, parameters.toArray)
				else if(!parameters.isEmpty)
					didNotExist("method", name)
				else if(project.deepTasks.contains(name))
					ifAction(name)
				else
					didNotExist("action", name)
		}
	}

	/** Toggles whether stack traces are enabled.*/
	private def setTrace(project: Project, value: String): Boolean =
	{
		try
		{
			val newValue = if(value == "on") MaxInt else if(value == "off") -1 else if(value == "nosbt") 0 else value.toInt
			project.projectClosure.foreach(_.log.setTrace(newValue))
			printTraceEnabled(project)
			true
		}
		catch { case _: NumberFormatException => setTraceError(project.log) }
	}
	private def printTraceEnabled(project: Project)
	{
		def traceLevel(level: Int) = if(level == 0) " (no sbt stack elements)" else if(level == MaxInt) "" else " (maximum " + level + " stack elements per exception)"
		Console.println("Stack traces are " + (if(project.log.traceEnabled) "enabled" + traceLevel(project.log.getTrace) else "disabled"))
	}
	/** Sets the logging level on the given project.*/
	private def setLevel(project: Project, level: Level.Value)
	{
		project.projectClosure.foreach(_.log.setLevel(level))
		Console.println("Set log level to " + project.log.getLevel)
	}
	/** Prints the elapsed time to the given project's log using the given
	* initial time and the label 's'.*/
	private def printTime(project: Project, startTime: Long, s: String)
	{
		val endTime = System.currentTimeMillis()
		project.log.info("")
		val ss = if(s.isEmpty) "" else s + " "
		project.log.info("Total " + ss + "time: " + (endTime - startTime + 500) / 1000 + " s, completed " + nowString)
	}
	private def nowString =
	{
		import java.text.DateFormat
		val format = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM)
		format.format(new java.util.Date)
	}
	/** Provides a partial message describing why the given property is undefined. */
	private def undefinedMessage(property: Project#UserProperty[_]): String =
	{
		property.resolve match
		{
			case vu: UndefinedValue => " is not defined."
			case e: ResolutionException => " has invalid value: " + e.toString
			case _ => ""
		}
	}
	/** Prompts the user for the value of undefined properties.  'first' is true if this is the first time
	* that the current property has been prompted.*/
	private def fillUndefinedProperties(project: Project, properties: List[(String, Project#Property[_])], first: Boolean): Boolean =
	{
		properties match
		{
			case (name, variable) :: tail =>
			{
				val shouldAdvanceOrQuit =
					variable match
					{
						case property: Project#UserProperty[_] =>
							if(first)
								project.log.error(" Property '" + name + "' " + undefinedMessage(property))
							for(newValue <- SimpleReader.readLine("  Enter new value for " + name + " : ")) yield
							{
								try
								{
									property.setStringValue(newValue)
									true
								}
								catch
								{
									case e =>
										project.log.error("Invalid value: " + e.getMessage)
										false
								}
							}
						case _ => Some(true)
					}
				shouldAdvanceOrQuit match
				{
					case Some(shouldAdvance) => fillUndefinedProperties(project, if(shouldAdvance) tail else properties, shouldAdvance)
					case None => false
				}
			}
			case Nil => true
		}
	}
	/** Iterates over the undefined properties in the given projects, prompting the user for the value of each undefined
	* property.*/
	private def fillUndefinedProjectProperties(projects: List[Project]): Boolean =
	{
		projects match
		{
			case project :: remaining =>
				val uninitialized = project.uninitializedProperties.toList
				if(uninitialized.isEmpty)
					fillUndefinedProjectProperties(remaining)
				else
				{
					project.log.error("Project in " + project.info.projectDirectory.getAbsolutePath + " has undefined properties.")
					val result = fillUndefinedProperties(project, uninitialized, true) && fillUndefinedProjectProperties(remaining)
					project.saveEnvironment()
					result
				}
			case Nil => true
		}
	}
	/** Prints the value of the property with the given name in the given project. */
	private def getProperty(project: Project, propertyName: String): Boolean =
	{
		if(propertyName.isEmpty)
		{
			project.log.error("No property name specified.")
			false
		}
		else
		{
			project.getPropertyNamed(propertyName) match
			{
				case Some(property) =>
					property.resolve match
					{
						case u: UndefinedValue => project.log.error("Value of property '" + propertyName + "' is undefined."); false
						case ResolutionException(m, e) => project.log.error(m); false
						case DefinedValue(value, isInherited, isDefault) => Console.println(value.toString); true
					}
				case None =>
					val value = System.getProperty(propertyName)
					if(value == null)
						project.log.error("No property named '" + propertyName + "' is defined.")
					else
						Console.println(value)
					value != null
			}
		}
	}
	/** Separates the space separated property name/value pair and stores the value in the user-defined property
	* with the given name in the given project.  If no such property exists, the value is stored in a system
	* property. */
	private def setProperty(project: Project, propertyNameAndValue: String): Boolean =
	{
		val m = """(\S+)(\s+\S.*)?""".r.pattern.matcher(propertyNameAndValue)
		if(m.matches())
		{
			val name = m.group(1)
			val newValue =
			{
				val v = m.group(2)
				if(v == null) "" else v.trim
			}
			def notePending(changed: String): Unit = Console.println(" Build will use " + changed + newValue + " after running 'reload' command or restarting sbt.")
			project.getPropertyNamed(name) match
			{
				case Some(property) =>
				{
					try
					{
						property.setStringValue(newValue)
						property match
						{
							case project.defScalaVersion | project.buildScalaVersions => notePending("Scala ")
							case project.sbtVersion => notePending("sbt ")
							case _ =>  Console.println(" Set property '" + name + "' = '" + newValue + "'")
						}
						true
					}
					catch { case e =>
						project.log.error("Error setting property '" + name + "' in " + project.environmentLabel + ": " + e.toString)
						false
					}
					finally { project.saveEnvironment().foreach(msg => project.log.error("Error saving environment: " + msg)) }
				}
				case None =>
				{
					System.setProperty(name, newValue)
					project.log.info(" Set system property '" + name + "' = '" + newValue + "'")
					true
				}
			}
		}
		else
			setArgumentError(project.log)
	}

	private def compileContinuously(project: Project) = executeContinuously(project, "test-compile")
	private def executeContinuously(project: Project, action: String) =
	{
		def shouldTerminate: Boolean = (System.in.available > 0) && (project.terminateWatch(System.in.read()) || shouldTerminate)
		val actionValid = checkAction(project, action)
		if(actionValid)
		{
			SourceModificationWatch.watchUntil(project, ContinuousCompilePollDelaySeconds)(shouldTerminate)
			{
				handleAction(project, action)
				Console.println("Waiting for source changes... (press enter to interrupt)")
			}
			while (System.in.available() > 0) System.in.read()
		}
		actionValid
	}

	def validTraceArguments = "'on', 'nosbt', 'off', or <integer>"
	def traceExplanation = "'nosbt' prints stack traces up to the first sbt frame.  An integer gives the number of frames to show per exception."
	private def isTerminateAction(s: String) = TerminateActions.elements.contains(s.toLowerCase)
	private def setTraceError(log: Logger) = logError(log)("Invalid arguments for 'trace': expected " + validTraceArguments + ".")
	private def setArgumentError(log: Logger) = logError(log)("Invalid arguments for 'set': expected property name and new value.")
	private def getArgumentError(log: Logger) = logError(log)("Invalid arguments for 'get': expected property name.")
	private def setProjectError(log: Logger) = logError(log)("Invalid arguments for 'project': expected project name.")
	private def logError(log: Logger)(s: String) = { log.error(s); false }

	private def logCommandError(log: Logger, e: Throwable) =
		e match
		{
			case pe: processor.ProcessorException =>
				if(pe.getCause ne null) log.trace(pe.getCause)
				log.error(e.getMessage)
			case e =>
				log.trace(e)
				log.error(e.toString)
		}
}
