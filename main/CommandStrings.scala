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
	val evalBrief = (EvalCommand + " <expression>", "Evaluates a Scala expression and prints the result and type.")
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

	val lastGrepBrief = (LastGrepCommand, "Shows lines from the last output for 'key' that match 'pattern'.")
	val lastGrepDetailed =
LastGrepCommand + """ <pattern>
	Displays lines from the logging of previous commands that match `pattern`.

""" + LastGrepCommand + """ <pattern> [key]
	Displays lines from logging associated with `key` that match `pattern`.  The key typically refers to a task (for example, test:compile).  The logging that is displayed is restricted to the logging for that particular task.

	<pattern> is a regular expression interpreted by java.util.Pattern.  Matching text is highlighted (when highlighting is supported and enabled).
	See also '""" + LastCommand + "'."

	val lastBrief = (LastCommand, "Displays output from a previous command or the output from a specific task.")
	val lastDetailed =
LastCommand + """
	Prints the logging for the previous command, typically at a more verbose level.

""" + LastCommand + """ <key>
	Prints the logging associated with the provided key.  The key typically refers to a task (for example, test:compile).  The logging that is displayed is restricted to the logging for that particular task.

	See also '""" + LastGrepCommand + "'."

	val InspectCommand = "inspect"
	val inspectBrief = (InspectCommand, "Prints the value for 'key', the defining scope, delegates, related definitions, and dependencies.")
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
	val setBrief = (SetCommand, "Evaluates a Setting and applies it to the current project.")
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
	def sessionBrief = (SessionCommand, "Manipulates session settings.  For details, run 'help " + SessionCommand + "'.")

	/** The command name to terminate the program.*/
	@deprecated("Moved to BasicCommandStrings", "0.12.0")
	val TerminateAction: String = BasicCommandStrings.TerminateAction

	def settingsPreamble = commonPreamble("settings")
	def tasksPreamble = commonPreamble("tasks") + """
Tasks produce values.  Use the 'show' command to run the task and print the resulting value."""

	def commonPreamble(label: String) = """
This is a list of %s defined for the current project.
It does not list the scopes the %<s are defined in; use the 'inspect' command for that.""".format(label)

	def settingsBrief(label: String) = (label, "Lists the " + label + " defined for the current project.")
	def settingsDetailed(label: String) = 
"""
Syntax summary
	%s [-(v|-vv|...|-V)] [<filter>]

%<s
	Displays the main %<s defined directly or indirectly for the current project. 

-v
	Displays additional tasks.  More 'v's increase the number of tasks displayed.

-V
	displays all %<s

<filter>
	Restricts the %<s that are displayed.  The names of %<s are searched for an exact match against the filter, in which case only the description of the exact match is displayed.  Otherwise, the filter is interpreted as a regular expression and all %<s whose name or description match the regular expression are displayed.  Note that this is an additional filter on top of the %<s selected by the -v style switches, so you must specify -V to search all %<s.  Use the %s command to search all commands, tasks, and settings at once.
""".format(label, BasicCommandStrings.HelpCommand)

	def moreAvailableMessage(label: String, search: Boolean) =
		"More %s may be %s by increasing verbosity.  See '%s %s'.\n".format(label, if(search) "searched" else "viewed", BasicCommandStrings.HelpCommand, label)

	def aboutBrief = "Displays basic information about sbt and the build."
	def aboutDetailed = aboutBrief

	def projectBrief = (ProjectCommand, "Displays the current project or changes to the provided `project`.")
	def projectDetailed =
ProjectCommand +
"""

	Displays the name of the current project.

""" + ProjectCommand + """ name

	Changes to the project with the provided name.
	This command fails if there is no project with the given name.

""" + ProjectCommand + """ {uri}

	Changes to the root project in the build defined by `uri`.
	`uri` must have already been declared as part of the build, such as with Project.dependsOn.

""" + ProjectCommand + """ {uri}name

	Changes to the project `name` in the build defined by `uri`.
	`uri` must have already been declared as part of the build, such as with Project.dependsOn.

""" + ProjectCommand + """ /

	Changes to the initial project.

""" + ProjectCommand + """ ..

	Changes to the parent project of the current project.
	If there is no parent project, the current project is unchanged.

	Use n+1 dots to change to the nth parent.
	For example, 'project ....' is equivalent to three consecutive 'project ..' commands."""

	def projectsBrief = "Lists the names of available projects or temporarily adds/removes extra builds to the session."
	def projectsDetailed = 
ProjectsCommand + """
	List the names of available builds and the projects defined in those builds.

""" + ProjectsCommand + """ add <URI>+
	Adds the builds at the provided URIs to this session.
	These builds may be selected using the """ + ProjectCommand + """ command.
	Alternatively, tasks from these builds may be run using the explicit syntax {URI}project/task

""" + ProjectsCommand + """ remove <URI>+
	Removes extra builds from this session.
	Builds explicitly listed in the build definition are not affected by this command.
"""

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
	def LoadProjectDetailed = "(Re)loads the project in the current directory"

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
