/* sbt -- Simple Build Tool
 * Copyright 2008, 2009  Steven Blundy, Mark Harrah, David MacIver, Mikko Peltonen
 */
package sbt

import scala.collection.immutable.TreeSet

private trait RunCompleteAction extends NotNull
private class Exit(val code: Int) extends RunCompleteAction
private object Reload extends RunCompleteAction

/** This class is the entry point for sbt.  If it is given any arguments, it interprets them
* as actions, executes the corresponding actions, and exits.  If there were no arguments provided,
* sbt enters interactive mode.*/
object Main
{
	/** The entry point for sbt.  If arguments are specified, they are interpreted as actions, executed,
	* and then the program terminates.  If no arguments are specified, the program enters interactive
	* mode. Call run if you need to run sbt in the same JVM.*/
	def main(args: Array[String])
	{
		val exitCode = run(args)
		if(exitCode == RebootExitCode)
		{
			println("Rebooting is not supported when the sbt loader is not used.")
			println("Please manually restart sbt.")
		}
		System.exit(exitCode)
	}
	val RebootExitCode = -1
	val NormalExitCode = 0
	val SetupErrorExitCode = 1
	val SetupDeclinedExitCode = 2
	val LoadErrorExitCode = 3
	val UsageErrorExitCode = 4
	val BuildErrorExitCode = 5
	def run(args: Array[String]): Int =
	{
		val startTime = System.currentTimeMillis
		Project.loadProject match
		{
			case err: LoadSetupError =>
				println("\n" + err.message)
				ExitHooks.runExitHooks(Project.bootLogger)
				SetupErrorExitCode
			case LoadSetupDeclined =>
				ExitHooks.runExitHooks(Project.bootLogger)
				SetupDeclinedExitCode
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
					case Some(l) => if(!isTerminateAction(l)) run(args) else NormalExitCode
					case None => LoadErrorExitCode
				}
			}
			case success: LoadSuccess =>
			{
				import success.project
				val doNext: RunCompleteAction =
					// in interactive mode, fill all undefined properties
					if(args.length > 0 || fillUndefinedProjectProperties(project.topologicalSort.toList.reverse))
						startProject(project, args, startTime)
					else
						new Exit(NormalExitCode)
				ExitHooks.runExitHooks(project.log)
				doNext match
				{
					case Reload => run(args)
					case x: Exit => x.code
				}
			}
		}
	}
	/** Returns true if the project should be reloaded, false if sbt should exit.*/
	private def startProject(project: Project, args: Array[String], startTime: Long): RunCompleteAction =
	{
		project.log.info("Building project " + project.name + " " + project.version.toString + " using " + project.getClass.getName)
		val scalaVersionOpt = ScalaVersion.current orElse project.scalaVersion.get
		for(sbtVersion <- project.sbtVersion.get; scalaVersion <- scalaVersionOpt if !sbtVersion.isEmpty  && !scalaVersion.isEmpty)
			project.log.info("   with sbt " + sbtVersion + " and Scala " + scalaVersion)
		args match
		{
			case Array() =>
				CrossBuild.load() match
				{
					case None =>
						project.log.info("No actions specified, interactive session started. Execute 'help' for more information.")
						val doNext = interactive(project)
						printTime(project, startTime, "session")
						doNext
					case Some(cross) =>
						crossBuildNext(project, cross)
						new Exit(RebootExitCode)
				}
			case CrossBuild(action) =>
				val exitCode =
					CrossBuild.load() match
					{
						case None => if(startCrossBuild(project, action)) RebootExitCode else BuildErrorExitCode
						case Some(cross) => if(crossBuildNext(project, cross)) RebootExitCode else NormalExitCode
					}
				new Exit(exitCode)
			case _ =>
				val exitCode =
					Control.lazyFold(args.toList)(handleBatchCommand(project)) match
					{
						case None => project.log.success("Build completed successfully."); NormalExitCode
						case Some(errorMessage) =>
							project.log.error("Error during build" + (if(errorMessage.isEmpty) "." else ": " + errorMessage) )
							BuildErrorExitCode
					}
				printTime(project, startTime, "build")
				new Exit(exitCode)
		}
	}
	private def crossBuildNext(project: Project, cross: CrossBuild) =
	{
		val setScalaVersion = (newVersion: String) => { System.setProperty(ScalaVersion.LiveKey, newVersion); () }
		val complete =
			if(handleAction(project, cross.command))
				cross.versionComplete(setScalaVersion)
			else
				cross.error(setScalaVersion)
		if(complete)
			printTime(project, cross.startTime, "cross-build")
		!complete
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
	/** The list of lowercase command names that may be used to terminate the program.*/
	val TerminateActions: Iterable[String] = "exit" :: "quit" :: Nil
	/** The name of the command that sets the value of the property given as its argument.*/
	val SetAction = "set"
	/** The name of the command that gets the value of the property given as its argument.*/
	val GetAction = "get"
	/** The name of the command that displays the help message. */
	val HelpAction = "help"
	/** The command for rebooting sbt. Requires sbt to have been launched by the loader.*/
	val RebootCommand = "reboot"
	/** The name of the command that reloads a project.  This is useful for when the project definition has changed. */
	val ReloadAction = "reload"
	/** The name of the command that toggles logging stacktraces. */
	val TraceCommand = "trace"
	/** The name of the command that compiles all sources continuously when they are modified. */
	val ContinuousCompileCommand = "cc"
	/** The prefix used to identify a request to execute the remaining input on source changes.*/
	val ContinuousExecutePrefix = "~"
	/** The prefix used to identify a request to execute the remaining input across multiple Scala versions.*/
	val CrossBuildPrefix = "+"
	/** Error message for when the user tries to prefix an action with CrossBuildPrefix but the loader is not used.*/
	val CrossBuildUnsupported = "Cross-building is not supported when the loader is not used."
	
	/** The number of seconds between polling by the continuous compile command.*/
	val ContinuousCompilePollDelaySeconds = 1

	/** The list of all available commands at the interactive prompt in addition to the tasks defined
	* by a project.*/
	protected def interactiveCommands: Iterable[String] = basicCommands.toList ++ logLevels.toList
	/** The list of logging levels.*/
	private def logLevels: Iterable[String] = TreeSet.empty[String] ++ Level.levels.map(_.toString)
	/** The list of all interactive commands other than logging level.*/
	private def basicCommands: Iterable[String] = TreeSet(ShowProjectsAction, ShowActions, ShowCurrent, HelpAction,
		RebootCommand, ReloadAction, TraceCommand, ContinuousCompileCommand, ProjectConsoleAction)
	
	/** Enters interactive mode for the given root project.  It uses JLine for tab completion and
	* history.  It returns normally when the user terminates or reloads the interactive session.  That is,
	* it does not call System.exit to quit.
	**/
	private def interactive(baseProject: Project): RunCompleteAction =
	{
		val projectNames = baseProject.topologicalSort.map(_.name)
		val prefixes = ContinuousExecutePrefix :: Nil
		val completors = new Completors(ProjectAction, projectNames, interactiveCommands, List(GetAction, SetAction), prefixes)
		val reader = new JLineReader(baseProject.historyPath, completors, baseProject.log)
		def updateTaskCompletions(project: Project)
		{
			val methodCompletions = for( (name, method) <- project.methods) yield (name, method.completions)
			reader.setVariableCompletions(project.taskNames, project.propertyNames, methodCompletions)
		}
		
		/** Prompts the user for the next command using 'currentProject' as context.
		* If the command indicates that the user wishes to terminate or reload the session,
		*   the function returns the appropriate value.
		* Otherwise, the command is handled and this function is called again
		*   (tail recursively) to prompt for the next command. */
		def loop(currentProject: Project): RunCompleteAction =
		{
			updateTaskCompletions(baseProject) // this is done after every command because the completions could change due to the action invoked
			reader.readLine("> ") match
			{
				case Some(line) =>
				{
					val trimmed = line.trim
					if(trimmed.isEmpty)
						loop(currentProject)
					else if(isTerminateAction(trimmed))
						new Exit(NormalExitCode)
					else if(ReloadAction == trimmed)
						Reload
					else if(RebootCommand == trimmed)
						new Exit(RebootExitCode)
					else if(trimmed.startsWith(CrossBuildPrefix))
					{
						if(startCrossBuild(currentProject, trimmed.substring(CrossBuildPrefix.length).trim))
							new Exit(RebootExitCode)
						else
							loop(currentProject)
					}
					else if(trimmed.startsWith(ProjectAction + " "))
					{
						val projectName = trimmed.substring(ProjectAction.length + 1)
						baseProject.topologicalSort.find(_.name == projectName) match
						{
							case Some(newProject) =>
							{
								printProject("Set current project to ", newProject)
								updateTaskCompletions(newProject)
								loop(newProject)
							}
							case None =>
							{
								currentProject.log.error("Invalid project name '" + projectName + "' (type 'projects' to list available projects).")
								loop(currentProject)
							}
						}
					}
					else
					{
						if(trimmed == HelpAction)
							displayInteractiveHelp()
						else if(trimmed == ShowProjectsAction)
							baseProject.topologicalSort.foreach(listProject)
						else if(trimmed.startsWith(SetAction + " "))
							setProperty(currentProject, trimmed.substring(SetAction.length + 1))
						else if(trimmed.startsWith(GetAction + " "))
							getProperty(currentProject, trimmed.substring(GetAction.length + 1))
						else if(trimmed == ProjectConsoleAction)
							showResult(Run.projectConsole(currentProject), currentProject.log)
						else
							handleInteractiveCommand(currentProject, trimmed)
						loop(currentProject)
					}
				}
				case None => new Exit(NormalExitCode)
			}
		}
		
		loop(baseProject)
	}
	private def printCmd(name:String, desc:String) = Console.println("\t" + name + ": " + desc)
	private def displayBatchHelp() = {
		Console.println("You may execute any project action or method or one of the commands described below.")
		Console.println("Available Commands:")
		printCommonCommands()
	}
	private def printCommonCommands()
	{
		printCmd("<action name>", "Executes the project specified action.")
		printCmd("<method name> <parameter>*", "Executes the project specified method.")
		printCmd(ContinuousExecutePrefix + " <command>", "Executes the project specified action or method whenever source files change.")
		printCmd(CrossBuildPrefix + " <command>", "Executes the project specified action or method for all versions of Scala defined in crossScalaVersions.")
		printCmd(ShowActions, "Shows all available actions.")
		printCmd(RebootCommand, "Changes to scala.version or sbt.version are processed and the project definition is reloaded.")
		printCmd(HelpAction, "Displays this help message.")
	}
	private def displayInteractiveHelp() = {
		Console.println("You may execute any project action or one of the commands described below. Only one action " +
			"may be executed at a time in interactive mode and is entered by name, as it would be at the command line." +
			" Also, tab completion is available.")
		Console.println("Available Commands:")

		printCommonCommands()
		printCmd(ReloadAction, "Reloads sbt, recompiling modified project definitions if necessary.")
		printCmd(ShowCurrent, "Shows the current project and logging level of that project.")
		printCmd(Level.levels.mkString(", "), "Set logging for the current project to the specified level.")
		printCmd(TraceCommand, "Toggles whether logging stack traces is enabled.")
		printCmd(ProjectAction + " <project name>", "Sets the currently active project.")
		printCmd(ShowProjectsAction, "Shows all available projects.")
		printCmd(TerminateActions.elements.mkString(", "), "Terminates the program.")
		printCmd(SetAction + " <property> <value>", "Sets the value of the property given as its argument.")
		printCmd(GetAction + " <property>", "Gets the value of the property given as its argument.")
		printCmd(ProjectConsoleAction, "Enters the Scala interpreter with the current project bound to the variable 'current' and all members imported.")
	}
	private def listProject(p: Project) = printProject("\t", p)
	private def printProject(prefix: String, p: Project): Unit =
		Console.println(prefix + p.name + " " + p.version)

	private def startCrossBuild(project: Project, action: String) =
	{
		checkBooted && checkAction(project, action) &&
		{
			val againstScalaVersions = project.crossScalaVersions
			val versionsDefined = !againstScalaVersions.isEmpty
			if(versionsDefined)
				CrossBuild(againstScalaVersions, action, System.currentTimeMillis)
			else
				Console.println("Project does not declare any Scala versions to cross-build against.")
			versionsDefined
		}
	}
	private def checkBooted =
		Project.booted ||
		{
			Console.println(CrossBuildUnsupported)
			false
		}
	
	/** Handles the given command string provided by batch mode execution..*/
	private def handleBatchCommand(project: Project)(command: String): Option[String] =
	{
		command.trim match
		{
			case HelpAction => displayBatchHelp(); None
			case ShowActions => showActions(project); None
			case CrossBuild(crossBuildAction) => Some(CrossBuildUnsupported)
			case action => if(handleAction(project, action)) None else Some("")
		}
	}
	
	/** Handles the given command string provided at the command line.*/
	private def handleInteractiveCommand(project: Project, command: String)
	{
		command match
		{
			case GetAction => getArgumentError(project.log)
			case SetAction => setArgumentError(project.log)
			case ProjectAction => setProjectError(project.log)
			case ShowCurrent =>
				printProject("Current project is ", project)
				Console.println("Current log level is " + project.log.getLevel)
				printTraceEnabled(project)
			case ShowActions => showActions(project)
			case TraceCommand => toggleTrace(project)
			case Level(level) => setLevel(project, level)
			case ContinuousCompileCommand => compileContinuously(project)
			case action if action.startsWith(ContinuousExecutePrefix) =>
				executeContinuously(project, action.substring(ContinuousExecutePrefix.length).trim)
			case action => handleAction(project, action)
		}
	}
	private def showActions(project: Project): Unit =
		Console.println(project.taskAndMethodList)
	
	// returns true if it succeeded (needed by noninteractive handleCommand)
	private def handleAction(project: Project, action: String): Boolean =
	{
		def show(result: Option[String]): Boolean = showResult(result, project.log)
		val startTime = System.currentTimeMillis
		val result = withAction(project, action)( (name, params) => show(project.call(name, params)))( name => show(project.act(name)))
		printTime(project, startTime, "")
		result
	}
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
	private def toggleTrace(project: Project)
	{
		val newValue = !project.log.traceEnabled
		project.topologicalSort.foreach(_.log.enableTrace(newValue))
		printTraceEnabled(project)
	}
	private def printTraceEnabled(project: Project)
	{
		Console.println("Stack traces are " + (if(project.log.traceEnabled) "enabled" else "disabled"))
	}
	/** Sets the logging level on the given project.*/
	private def setLevel(project: Project, level: Level.Value)
	{
		project.topologicalSort.foreach(_.log.setLevel(level))
		Console.println("Set log level to " + project.log.getLevel)
	}
	/** Prints the elapsed time to the given project's log using the given
	* initial time and the label 's'.*/
	private def printTime(project: Project, startTime: Long, s: String)
	{
		val endTime = System.currentTimeMillis()
		project.log.info("")
		val ss = if(s.isEmpty) "" else s + " "
		project.log.info("Total " + ss + "time: " + (endTime - startTime + 500) / 1000 + " s")
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
			{
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
			}
			case Nil => true
		}
	}
	/** Prints the value of the property with the given name in the given project. */
	private def getProperty(project: Project, propertyName: String)
	{
		if(propertyName.isEmpty)
			project.log.error("No property name specified.")
		else
		{
			project.getPropertyNamed(propertyName) match
			{
				case Some(property) =>
				{
					property.resolve match
					{
						case u: UndefinedValue => project.log.error("Value of property '" + propertyName + "' is undefined.")
						case ResolutionException(m, e) => project.log.error(m)
						case DefinedValue(value, isInherited, isDefault) => Console.println(value.toString)
					}
				}
				case None =>
				{
					val value = System.getProperty(propertyName)
					if(value == null)
						project.log.error("No property named '" + propertyName + "' is defined.")
					else
						Console.println(value)
				}
			}
		}
	}
	/** Separates the space separated property name/value pair and stores the value in the user-defined property
	* with the given name in the given project.  If no such property exists, the value is stored in a system
	* property. */
	private def setProperty(project: Project, propertyNameAndValue: String)
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
			project.getPropertyNamed(name) match
			{
				case Some(property) =>
				{
					val succeeded =
						try
						{
							property.setStringValue(newValue)
							val isScalaVersion = property == project.scalaVersion
							val isSbtVersion = property == project.sbtVersion
							if(isScalaVersion || isSbtVersion)
							{
								val changed = if(isSbtVersion) "sbt " else "Scala "
								Console.println(" Build will use " + changed + newValue + " after running 'reboot' command or restarting sbt.")
							}
							else
								Console.println(" Set property '" + name + "' = '" + newValue + "'")
						}
						catch { case e => project.log.error("Error setting property '" + name + "' in " + project.environmentLabel + ": " + e.toString) }
					project.saveEnvironment()
				}
				case None =>
				{
					System.setProperty(name, newValue)
					project.log.info(" Set system property '" + name + "' = '" + newValue + "'")
				}
			}
		}
		else
			setArgumentError(project.log)
	}

	private def compileContinuously(project: Project) = executeContinuously(project, "test-compile")
	private def executeContinuously(project: Project, action: String)
	{
		if(checkAction(project, action))
		{
			SourceModificationWatch.watchUntil(project, ContinuousCompilePollDelaySeconds)(System.in.available() > 0)
			{
				handleAction(project, action)
				Console.println("Waiting for source changes... (press any key to interrupt)")
			}
			while (System.in.available() > 0) System.in.read()
		}
	}

	private def isTerminateAction(s: String) = TerminateActions.elements.contains(s.toLowerCase)
	private def setArgumentError(log: Logger) { log.error("Invalid arguments for 'set': expected property name and new value.") }
	private def getArgumentError(log: Logger) { log.error("Invalid arguments for 'get': expected property name.") }
	private def setProjectError(log: Logger) { log.error("Invalid arguments for 'project': expected project name.") }
}
private class CrossBuild(val remainingScalaVersions: Set[String], val command: String, val startTime: Long)
{
	def error(setScalaVersion: String => Unit) = clearScalaVersion(setScalaVersion)
	private def clearScalaVersion(setScalaVersion: String => Unit) =
	{
		CrossBuild.clear()
		setScalaVersion("")
		true
	}
	def versionComplete(setScalaVersion: String => Unit) =
	{
		val remaining = remainingScalaVersions - ScalaVersion.currentString
		if(remaining.isEmpty)
			clearScalaVersion(setScalaVersion)
		else
		{
			CrossBuild.setProperties(remaining, command, startTime.toString)
			setScalaVersion(remaining.toSeq.first)
			false
		}
	}
}
private object CrossBuild
{
	private val RemainingScalaVersionsKey = "sbt.remaining.scala.versions"
	private val CrossCommandKey = "sbt.cross.build.command"
	private val StartTimeKey = "sbt.cross.start.time"
	private def setProperties(remainingScalaVersions: Set[String], command: String, startTime: String)
	{
		System.setProperty(RemainingScalaVersionsKey, remainingScalaVersions.mkString(" "))
		System.setProperty(CrossCommandKey, command)
		System.setProperty(StartTimeKey, startTime)
	}
	private def getProperty(key: String) =
	{
		val value = System.getProperty(key)
		if(value == null)
			""
		else
			value.trim
	}
	private def clear() { setProperties(Set.empty, "", "") }
	def load() =
	{
		val command = getProperty(CrossCommandKey)
		val remaining = getProperty(RemainingScalaVersionsKey)
		val startTime = getProperty(StartTimeKey)
		if(command.isEmpty || remaining.isEmpty || startTime.isEmpty)
			None
		else
			Some(new CrossBuild(Set(remaining.split(" ") : _*), command, startTime.toLong))
	}
	def apply(remainingScalaVersions: Set[String], command: String, startTime: Long) =
	{
		setProperties(remainingScalaVersions, command, startTime.toString)
		new CrossBuild(remainingScalaVersions, command, startTime)
	}
	import Main.CrossBuildPrefix
	def unapply(s: String): Option[String] =
	{
		val trimmed = s.trim
		if(trimmed.startsWith(CrossBuildPrefix))
			Some(trimmed.substring(CrossBuildPrefix.length).trim)
		else
			None
	}
	def unapply(s: Array[String]): Option[String] =
		s match
		{
			case Array(CrossBuild(crossBuildAction)) => Some(crossBuildAction)
			case _ => None
		}
}