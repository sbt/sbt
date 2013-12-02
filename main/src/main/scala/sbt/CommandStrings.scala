/* sbt -- Simple Build Tool
 * Copyright 2010  Mark Harrah
 */
package sbt

object CommandStrings
{
	/** The prefix used to identify a request to execute the remaining input on source changes.*/
	val AboutCommand = "about"
	val TasksCommand = "tasks"
	val SettingsCommand = "settings"
	val ProjectCommand = "project"
	val ProjectsCommand = "projects"
	val ShowCommand = "show"
	val MultiTaskCommand = "all"
	val BootCommand = "boot"

	val EvalCommand = "eval"
	val evalBrief = (EvalCommand + " <expression>", "Evaluates a Scala expression and prints the result and type.")
	val evalDetailed =
EvalCommand + """ <expression>

	Evaluates the given Scala expression and prints the result and type."""

	@deprecated("Misnomer: was only for `show`.  Use showBrief.", "0.13.2")
	def actBrief = showBrief
	@deprecated("Misnomer: was only for `show`.  Use showDetailed.", "0.13.2")
	def actDetailed = showDetailed

	def actHelp = showHelp ++ multiTaskHelp

	def multiTaskHelp = Help(MultiTaskCommand, (multiTaskSyntax, multiTaskBrief), multiTaskDetailed)
	def multiTaskDetailed =
s"""$multiTaskSyntax

	$multiTaskBrief"""
	def multiTaskSyntax = s"""$MultiTaskCommand <task>+"""
	def multiTaskBrief = """Executes all of the specified tasks concurrently."""


	def showHelp = Help(ShowCommand, (ShowCommand + " <key>", actBrief), actDetailed)
	def showBrief = "Displays the result of evaluating the setting or task associated with 'key'."
	def showDetailed =
s"""$ShowCommand <setting>

	Displays the value of the specified setting.

$ShowCommand <task>

	Evaluates the specified task and display the value returned by the task."""

	val LastCommand = "last"
	val LastGrepCommand = "last-grep"
	val ExportCommand = "export"
	val ExportStream = "export"

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

	val exportBrief = (ExportCommand + " <tasks>+", "Executes tasks and displays the equivalent command lines.")
	val exportDetailed =
s"""$ExportCommand [--last] <task>+
	Runs the specified tasks and prints the equivalent command lines or other exportable information for those runs.

	--last
		Uses information from the previous execution

	NOTES: These command lines are necessarily approximate.  Usually tasks do not actually
	execute the command line and the actual command line program may not be installed or
	on the PATH.  Incremental tasks will typically show the command line for an
	incremental run and not for a full run.  Many tasks have no direct command line
	equivalent and will show nothing at all.
"""

	val InspectCommand = "inspect"
	val inspectBrief = (InspectCommand + " [uses|tree|definitions] <key>", "Prints the value for 'key', the defining scope, delegates, related definitions, and dependencies.")
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

""" +
InspectCommand + """ tree <key>

	Displays `key` and its dependencies in a tree structure.
	For settings, the value bound to the setting is displayed and for tasks, the type of the task is shown.

""" +
InspectCommand + """ uses <key>

	Displays the settings and tasks that directly depend on `key`.

""" +
InspectCommand + """ definitions <key>

	Displays the scopes in which `key` is defined.
"""


	val SetCommand = "set"
	val setBrief = (s"$SetCommand [every] <setting>", "Evaluates a Setting and applies it to the current project.")
	val setDetailed =
SetCommand + """ [every] <setting-expression>

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

	def DefaultsCommand = "add-default-commands"
	def DefaultsBrief = (DefaultsCommand, DefaultsDetailed)
	def DefaultsDetailed = "Registers default built-in commands"

	def Load = "load"
	def LoadLabel = "a project"
	def LoadCommand = "load-commands"
	def LoadCommandLabel = "commands"

	def LoadFailed = "load-failed"

	def LoadProjectImpl = "loadp"
	def LoadProject = "reload"
	def LoadProjectBrief = (LoadProject, LoadProjectDetailed)
	def LoadProjectDetailed = "(Re)loads the project in the current directory"

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

	val CrossCommand = "+"
	val SwitchCommand = "++"

	def crossHelp: Help = Help.more(CrossCommand, CrossDetailed)
	def switchHelp: Help = Help.more(SwitchCommand, SwitchDetailed)

	def CrossDetailed =
s"""$CrossCommand <command>
	Runs <command> for each Scala version specified for cross-building.

	For each string in `crossScalaVersions` in the current project, this command sets the
	`scalaVersion` of all projects to that version, reloads the build, and
	executes <command>.  When finished, it reloads the build with the original
	Scala version.

	See also `help $SwitchCommand`
"""

	def SwitchDetailed =
s"""$SwitchCommand <scala-version> [<command>]
	Changes the Scala version and runs a command.

	Sets the `scalaVersion` of all projects to <scala-version> and reloads the build.
	If <command> is provided, it is then executed.

$SwitchCommand [<scala-version>=]<scala-home> [<command>]
	Uses the Scala installation at <scala-home> by configuring the scalaHome setting for
	all projects.

	If <scala-version> is specified, it is used as the value of the scalaVersion setting.
	This is important when using managed dependencies.  This version will determine the
	cross-version used as well as transitive dependencies.

	If <command> is provided, it is then executed.

	See also `help $CrossCommand`
"""
}
