/* sbt -- Simple Build Tool
 * Copyright 2010  Mark Harrah
 */
package sbt

import complete.HistoryCommands
import scala.annotation.tailrec

import java.io.File
import Path._

object CommandSupport
{
	def logger(s: State) = s get Keys.logged getOrElse ConsoleLogger()

	// slightly better fallback in case of older launcher
	def bootDirectory(state: State): File =
		try { state.configuration.provider.scalaProvider.launcher.bootDirectory }
		catch { case e: NoSuchMethodError => new File(".").getAbsoluteFile }

	private def canRead = (_: File).canRead
	def notReadable(files: Seq[File]): Seq[File] = files filterNot canRead
	def readable(files: Seq[File]): Seq[File] = files filter canRead
	def sbtRCs(s: State): Seq[File] =
		(Path.userHome / sbtrc) ::
		(s.baseDir / sbtrc asFile) ::
		Nil

	def readLines(files: Seq[File]): Seq[String] = files flatMap (line => IO.readLines(line)) flatMap processLine
	def processLine(s: String) = { val trimmed = s.trim; if(ignoreLine(trimmed)) None else Some(trimmed) }
	def ignoreLine(s: String) = s.isEmpty || s.startsWith("#")

	/** The prefix used to identify a request to execute the remaining input on source changes.*/
	val ContinuousExecutePrefix = "~"
	val HelpCommand = "help"
	val TasksCommand = "tasks"
	val ProjectCommand = "project"
	val ProjectsCommand = "projects"

	val Exit = "exit"
	val Quit = "quit"

	val EvalCommand = "eval"
	val evalBrief = (EvalCommand + " <expression>", "Evaluates the given Scala expression and prints the result and type.")
	val evalDetailed =
EvalCommand + """ <expression>
	Evaluates the given Scala expression and prints the result and type.
"""

	val LastCommand = "last"
	val LastGrepCommand = "last-grep"

	val lastGrepBrief = (LastGrepCommand + " <pattern> <key>", "Shows lines from the last output for 'key' that match 'pattern'.")
	val lastGrepDetailed = 
LastGrepCommand + """ <pattern> [key]

	<pattern> is a regular expression interpreted by java.util.Pattern.
	Lines that match 'pattern' from the last streams output associated with the key are displayed.
	If no key is specified, the global streams output is used.
	See also """ + LastCommand + "."

	val lastBrief = (LastCommand + " <key>", "Prints the last output associated with 'key'.")
	val lastDetailed = 
LastCommand + """ <key>

	Redisplays the last streams output associated with the key (typically a task key).
	If no key is specified, the global streams output is displayed.
	See also """ + LastGrepCommand + "."

	val InspectCommand = "inspect"
	val inspectBrief = (InspectCommand + " <key>", "Prints the value for 'key', the defining scope, delegates, related definitions, and dependencies.")
	val inspectDetailed =
InspectCommand + """ <key>

	For a plain setting, the value bound to the key argument is displayed using its toString method.
	Otherwise, the type of task ("Task" or "Input task") is displayed.
	
	"Dependencies" shows the settings that this setting depends on.
	"Reverse dependencies" shows the settings that depend on this setting.

	When a key is resolved to a value, it may not actually be defined in the requested scope.
	In this case, there is a defined search sequence.
	"Delegates" shows the scopes that are searched for the key.
	"Provided by" shows the scope that contained the value returned for the key.

	"Related" shows all of the scopes in which the key is defined.
"""
	
	val SetCommand = "set"
	val setBrief = (SetCommand + " <setting-expression>", "Evaluates the given Setting and applies to the current project.")
	val setDetailed =
SetCommand + """ <setting-expression>

	Applies the given setting to the current project:
	  1) Constructs the expression provided as an argument by compiling and loading it.
	  2) Appends the new setting to the current project's settings.
	  3) Re-evaluates the build's settings.

	This command does not rebuild the build definitions, plugins, or configurations.
	It does not automatically persist the setting.
	This is done by running 'settings save' or 'settings save-all'.
"""

	def SessionCommand = "session"
	def sessionBrief = (SessionCommand + " ...", "Manipulates session settings.  For details, run 'help " + SessionCommand + "'..")
	
	/** The command name to terminate the program.*/
	val TerminateAction: String = Exit

	def continuousBriefHelp = (ContinuousExecutePrefix + " <action>", "Executes the specified command whenever source files change.")

	def tasksPreamble = """
This is a list of tasks defined for the current project.
It does not list the scopes the tasks are defined in; use the 'inspect' command for that.
Tasks produce values.  Use the 'show' command to run the task and print the resulting value.
"""

	def tasksBrief = "Displays the tasks defined for the current project."
	def tasksDetailed = TasksCommand + "\n\t" + tasksBrief

	def helpBrief = (HelpCommand + " command*", "Displays this help message or prints detailed help on requested commands.")
	def helpDetailed = "If an argument is provided, this prints detailed help for that command.\nOtherwise, this prints a help summary."

	def projectBrief = (ProjectCommand + " [project]", "Displays the current project or changes to the provided `project`.")
	def projectDetailed =
ProjectCommand +
"""
	Displays the name of the current project.

""" + ProjectCommand + """ name
	Changes to the project with the provided name.
	This command fails if there is no project with the given name.
""" + ProjectCommand + """ /
	Changes to the initial project.
""" + ProjectCommand + """ ..
	Changes to the parent project of the current project.
	If there is no parent project, the current project is unchanged.

	Use n+1 dots to change to the nth parent.
	For example, 'project ....' is equivalent to three consecutive 'project ..' commands.
"""

	def projectsBrief = projectsDetailed
	def projectsDetailed = "Displays the names of available projects."

	def historyHelp = HistoryCommands.descriptions.map( d => Help(d) )

	def exitBrief = (TerminateAction, "Terminates the build.")

	def sbtrc = ".sbtrc"

	def ReadCommand = "<"
	def ReadFiles = " file1 file2 ..."
	def ReadBrief = (ReadCommand + " file*", "Reads command lines from the provided files.")
	def ReadDetailed = ReadCommand + ReadFiles +
"""
	Reads the lines from the given files and inserts them as commands.
	Any lines that are empty or that start with # are ignored.
	If a file does not exist or is not readable, this command fails.

	All commands are read before any are executed.
	Therefore, if any file is not readable, no commands from any files will be
	run.

	You probably need to escape this command if entering it at your shell.
"""

	def DefaultsCommand = "add-default-commands"
	def DefaultsBrief = (DefaultsCommand, DefaultsDetailed)
	def DefaultsDetailed = "Registers default built-in commands"

	def RebootCommand = "reboot"
	def RebootSummary = RebootCommand + " [full]"
	def RebootBrief = (RebootSummary, "Reboots sbt and then executes the remaining commands.")
	def RebootDetailed =
RebootSummary + """
	This command is equivalent to exiting sbt, restarting, and running the
	 remaining commands with the exception that the jvm is not shut down.
	If 'full' is specified, the `project/boot` directory is deleted before
	 restarting.  This forces an update of sbt and Scala and is useful when
	 working with development versions of sbt or Scala.
"""

	def Multi = ";"
	def MultiBrief = ("( " + Multi + " command )+", "Runs the provided semicolon-separated commands.")
	def MultiDetailed =
Multi + " command1 " + Multi + """ command2 ...
	Runs the specified commands.
"""

	def Append = "append"
	def AppendLastBrief = (Append + " command", AppendLastDetailed)
	def AppendLastDetailed = "Appends `command` to list of commands to run."

	val AliasCommand = "alias"
	def AliasBrief = (AliasCommand, "Adds, removes, or prints command aliases.")
	def AliasDetailed =
AliasCommand + """
	Prints a list of defined aliases.

""" +
AliasCommand + """ name
	Prints the alias defined for `name`.

""" +
AliasCommand + """ name=value
	Sets the alias `name` to `value`, replacing any existing alias with that name.
	Whenever `name` is entered, value is run.
	If any arguments are provided to `name`, those are appended to `value`.

""" +
AliasCommand + """ name=
	Removes the alias for `name`.
"""

	def Discover = "discover"
	def DiscoverBrief = (DiscoverSyntax, "Finds annotated classes and subclasses.")
	def DiscoverSyntax = Discover + " [-module true|false] [-sub <names>] [-annot <names>]"
	def DiscoverDetailed =
DiscoverSyntax + """

	Looks for public, concrete classes that match the requested query using the current sbt.inc.Analysis instance.
	
	-module
		Specifies whether modules (true) or classes (false) are found.
		The default is classes/traits (false).
	
	-sub
		Specifies comma-separated class names.
		Classes that have one or more of these classes as an ancestor are included in the resulting list.
	
	-annot
		Specifies comma-separated annotation names.
		Classes with one or more of these annotations on the class or one of its non-private methods are included in the resulting list.
"""

	def CompileName = "direct-compile"
	def CompileBrief = (CompileSyntax, "Incrementally compiles the provided sources.")
	def CompileSyntax = CompileName + " -src <paths> [-cp <paths>] [-d <path>]"
	def CompileDetailed =
CompileSyntax + """

	Incrementally compiles Scala and Java sources.
	
	<paths> are explicit paths separated by the platform path separator.
	
	The specified output path will contain the following directory structure:
	
		scala_<version>/
			classes/
			cache/

	Compiled classes will be written to the 'classes' directory.
	Cached information about the compilation will be written to 'cache'.
"""

	val FailureWall = "---"
	
	def Load = "load"
	def LoadLabel = "a project"
	def LoadCommand = "load-commands"
	def LoadCommandLabel = "commands"

	def LoadFailed = "load-failed"

	def LoadProjectImpl = "loadp"
	def LoadProject = "reload"
	def LoadProjectBrief = (LoadProject, LoadProjectDetailed)
	def LoadProjectDetailed = "Loads the project in the current directory"

	def Shell = "shell"
	def ShellBrief = ShellDetailed
	def ShellDetailed = "Provides an interactive prompt from which commands can be run."

	def ClearOnFailure = "--"
	def OnFailure = "-"
	def OnFailureBrief = (OnFailure + " command", "Registers 'command' to run if a command fails.")
	def OnFailureDetailed =
OnFailure + """ command
	Registers 'command' to run when a command fails to complete normally.
	Only one failure command may be registered at a time, so this
	  command replaces the previous command if there is one.
	The failure command is reset when it runs, so it must be added again
	  if desired.
"""

	def IfLast = "iflast"
	def IfLastBrief = (IfLast + " command", IfLastCommon)
	def IfLastCommon = "If there are no more commands after this one, 'command' is run."
	def IfLastDetailed =
IfLast + """ command

	""" + IfLastCommon

	def InitCommand = "initialize"
	def InitBrief = (InitCommand, "Initializes command processing.")
	def InitDetailed =
InitCommand + """
	Initializes command processing.

Runs the following commands.

defaults
	Registers default commands.

load-commands -base ~/.sbt/commands
	Builds and loads command definitions from ~/.sbt/commands

< ~/.sbtrc
< .sbtrc
	Runs commands from ~/.sbtrc and ./.sbtrc if they exist
"""
}