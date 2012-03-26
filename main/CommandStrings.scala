/* sbt -- Simple Build Tool
 * Copyright 2010  Mark Harrah
 */
package sbt

object CommandStrings
{
	@deprecated("Use the `log` member of a State instance directly.", "0.12.0")
	def logger(s: State) = s.log

	@deprecated("Use the `globalLogging` member of a State instance directly.", "0.12.0")
	def globalLogging(s: State) = s.globalLogging

	/** The prefix used to identify a request to execute the remaining input on source changes.*/
	val AboutCommand = "about"
	val TasksCommand = "tasks"
	val SettingsCommand = "settings"
	val ProjectCommand = "project"
	val ProjectsCommand = "projects"
	val ShowCommand = "show"
	val BootCommand = "boot"

	@deprecated("Moved to BasicCommandStrings", "0.12.0")
	val ContinuousExecutePrefix = BasicCommandStrings.ContinuousExecutePrefix

	@deprecated("Moved to BasicCommandStrings", "0.12.0")
	val Exit = BasicCommandStrings.Exit

	@deprecated("Moved to BasicCommandStrings", "0.12.0")
	val Quit = BasicCommandStrings.Quit

	val EvalCommand = "eval"
	val evalBrief = (EvalCommand + " <expression>", "Evaluates the given Scala expression and prints the result and type.")
	val evalDetailed =
EvalCommand + """ <expression>

	Evaluates the given Scala expression and prints the result and type."""

	def showHelp = Help(ShowCommand, (ShowCommand + " <key>", actBrief), actDetailed)
	def actBrief = "Displays the result of evaluating the setting or task associated with 'key'."
	def actDetailed =
ShowCommand + """ <setting>

	Displays the value of the specified setting.

""" + ShowCommand + """ <task>

	Evaluates the specified task and display the value returned by the task."""

	val LastCommand = "last"
	val LastGrepCommand = "last-grep"

	val lastGrepBrief = (LastGrepCommand + " <pattern> <key>", "Shows lines from the last output for 'key' that match 'pattern'.")
	val lastGrepDetailed =
LastGrepCommand + """ <pattern> [key]

	<pattern> is a regular expression interpreted by java.util.Pattern.
	Lines that match 'pattern' from the last streams output associated with the key are displayed.
	If no key is specified, the global streams output is used.

	See also '""" + LastCommand + "'."

	val lastBrief = (LastCommand + " <key>", "Prints the last output associated with 'key'.")
	val lastDetailed =
LastCommand + """ <key>

	Redisplays the last streams output associated with the key (typically a task key).
	If no key is specified, the global streams output is displayed.

	See also '""" + LastGrepCommand + "'."

	val InspectCommand = "inspect"
	val inspectBrief = (InspectCommand + " [tree] <key>", "Prints the value for 'key', the defining scope, delegates, related definitions, and dependencies.")
	val inspectDetailed =
InspectCommand + """ [tree] <key>

	For a plain setting, the value bound to the key argument is displayed using its toString method.
	Otherwise, the type of task ("Task" or "Input task") is displayed.

	"Dependencies" shows the settings that this setting depends on.
	If 'tree' is specified, the bound value as well as the settings that this setting depends on
	(and their bound values) are displayed as a tree structure.
	
	"Reverse dependencies" shows the settings that depend on this setting.

	When a key is resolved to a value, it may not actually be defined in the requested scope.
	In this case, there is a defined search sequence.
	"Delegates" shows the scopes that are searched for the key.
	"Provided by" shows the scope that contained the value returned for the key.

	"Related" shows all of the scopes in which the key is defined."""

	val SetCommand = "set"
	val setBrief = (SetCommand + "[every] <setting-expression>", "Evaluates the given Setting and applies it to the current project.")
	val setDetailed =
SetCommand + """ <setting-expression>

	Applies the given setting to the current project:
	  1) Constructs the expression provided as an argument by compiling and loading it.
	  2) Appends the new setting to the current project's settings.
	  3) Re-evaluates the build's settings.

	This command does not rebuild the build definitions, plugins, or configurations.
	It does not automatically persist the setting(s) either.
	To persist the setting(s), run 'session save' or 'session save-all'.

	If 'every' is specified, the setting is evaluated in the current context
	and the resulting value is used in every scope.  This overrides the value
	bound to the key everywhere.
"""

	def SessionCommand = "session"
	def sessionBrief = (SessionCommand + " <session-command>", "Manipulates session settings.  For details, run 'help " + SessionCommand + "'.")

	/** The command name to terminate the program.*/
	@deprecated("Moved to BasicCommandStrings", "0.12.0")
	val TerminateAction: String = BasicCommandStrings.TerminateAction

	def settingsPreamble = commonPreamble("settings")
	def tasksPreamble = commonPreamble("tasks") + """
Tasks produce values.  Use the 'show' command to run the task and print the resulting value."""

	def commonPreamble(label: String) = """
This is a list of %s defined for the current project.
It does not list the scopes the %<s are defined in; use the 'inspect' command for that.""".format(label)

	def settingsBrief(label: String) = (label, "Displays the " + label + " defined for the current project.")
	def settingsDetailed(label: String) = 
"""%s -[v|vv|...|V]

	Displays the %<s defined directly or indirectly for the current project. 
	Additional %<s may be displayed by providing -v, -vv, ... or -V for all %<s.
""".format(label)

	def moreAvailableMessage(label: String) = "More " + label + " may be viewed by increasing verbosity.  See '" + BasicCommandStrings.HelpCommand + " " + label + "'.\n"

	def aboutBrief = "Displays basic information about sbt and the build."
	def aboutDetailed = aboutBrief

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
	For example, 'project ....' is equivalent to three consecutive 'project ..' commands."""

	def projectsBrief = projectsDetailed
	def projectsDetailed = "Displays the names of available projects."

	def sbtrc = ".sbtrc"

	@deprecated("Moved to BasicCommandStrings", "0.12.0")
	def ReadCommand = BasicCommandStrings.ReadCommand

	def DefaultsCommand = "add-default-commands"
	def DefaultsBrief = (DefaultsCommand, DefaultsDetailed)
	def DefaultsDetailed = "Registers default built-in commands"

	@deprecated("Moved to BasicCommandStrings", "0.12.0")
	def RebootCommand = "reboot"

	@deprecated("Moved to BasicCommandStrings", "0.12.0")
	def Multi = ";"

	@deprecated("Moved to BasicCommandStrings", "0.12.0")
	def AppendCommand = "append"

	def Load = "load"
	def LoadLabel = "a project"
	def LoadCommand = "load-commands"
	def LoadCommandLabel = "commands"

	def LoadFailed = "load-failed"

	def LoadProjectImpl = "loadp"
	def LoadProject = "reload"
	def LoadProjectBrief = (LoadProject, LoadProjectDetailed)
	def LoadProjectDetailed = "Loads the project in the current directory"

	@deprecated("Moved to State", "0.12.0")
	val FailureWall = State.FailureWall

	@deprecated("Moved to BasicCommandStrings", "0.12.0")
	def Shell = BasicCommandStrings.Shell

	@deprecated("Moved to BasicCommandStrings", "0.12.0")
	def ClearOnFailure = "--"

	@deprecated("Moved to BasicCommandStrings", "0.12.0")
	def OnFailure = "-"

	@deprecated("Moved to BasicCommandStrings", "0.12.0")
	def IfLast = "iflast"

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

		import java.io.File
		import Path._

	def sbtRCs(s: State): Seq[File] =
		(Path.userHome / sbtrc) ::
		(s.baseDir / sbtrc asFile) ::
		Nil
}
