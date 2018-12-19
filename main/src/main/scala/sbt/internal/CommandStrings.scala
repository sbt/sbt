/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import sbt.io.Path

object CommandStrings {

  /** The prefix used to identify a request to execute the remaining input on source changes.*/
  val AboutCommand = "about"
  val TasksCommand = "tasks"
  val SettingsCommand = "settings"
  val ProjectCommand = "project"
  val ProjectsCommand = "projects"
  val ShowCommand = "show"
  val PrintCommand = "print"
  val MultiTaskCommand = "all"
  val BootCommand = "boot"

  val EvalCommand = "eval"
  val evalBrief =
    (s"$EvalCommand <expression>", "Evaluates a Scala expression and prints the result and type.")
  val evalDetailed =
    s"""$EvalCommand <expression>

	Evaluates the given Scala expression and prints the result and type."""

  def actHelp = showHelp ++ multiTaskHelp

  def multiTaskHelp = Help(MultiTaskCommand, (multiTaskSyntax, multiTaskBrief), multiTaskDetailed)
  def multiTaskDetailed =
    s"""$multiTaskSyntax

	$multiTaskBrief"""
  def multiTaskSyntax = s"$MultiTaskCommand <task>+"
  def multiTaskBrief = "Executes all of the specified tasks concurrently."

  def showHelp = Help(ShowCommand, (s"$ShowCommand <key>", showBrief), showDetailed)
  def showBrief = "Displays the result of evaluating the setting or task associated with 'key'."
  def showDetailed =
    s"""$ShowCommand <setting>

	Displays the value of the specified setting.

$ShowCommand <task>

	Evaluates the specified task and display the value returned by the task."""

  def printHelp = Help(PrintCommand, (s"$PrintCommand <key>", printBrief), printDetailed)
  def printBrief =
    "Prints the result of evaluating the setting or task associated with 'key' to standard output."
  def printDetailed =
    s"""$PrintCommand <setting>

	Prints the value of the specified setting.

$PrintCommand <task>

	Evaluates the specified task and print the value returned by the task."""

  val PluginsCommand = "plugins"
  val PluginCommand = "plugin"
  def pluginsBrief = "Lists currently available plugins."
  def pluginsDetailed = pluginsBrief // TODO: expand

  val LastCommand = "last"
  val OldLastGrepCommand = "last-grep"
  val LastGrepCommand = "lastGrep"
  val ExportCommand = "export"
  val ExportStream = "export"

  val oldLastGrepBrief =
    (OldLastGrepCommand, "Shows lines from the last output for 'key' that match 'pattern'.")
  val oldLastGrepDetailed =
    s"""$OldLastGrepCommand <pattern>
	Displays lines from the logging of previous commands that match `pattern`.

$OldLastGrepCommand <pattern> [key]
	Displays lines from logging associated with `key` that match `pattern`.  The key typically refers to a task (for example, test:compile).  The logging that is displayed is restricted to the logging for that particular task.

	<pattern> is a regular expression interpreted by java.util.Pattern.  Matching text is highlighted (when highlighting is supported and enabled).
	See also '$LastCommand'."""

  val lastGrepBrief =
    (LastGrepCommand, "Shows lines from the last output for 'key' that match 'pattern'.")
  val lastGrepDetailed =
    s"""$LastGrepCommand <pattern>
	Displays lines from the logging of previous commands that match `pattern`.

$LastGrepCommand <pattern> [key]
	Displays lines from logging associated with `key` that match `pattern`.  The key typically refers to a task (for example, test:compile).  The logging that is displayed is restricted to the logging for that particular task.

	<pattern> is a regular expression interpreted by java.util.Pattern.  Matching text is highlighted (when highlighting is supported and enabled).
	See also '$LastCommand'."""

  val lastBrief =
    (LastCommand, "Displays output from a previous command or the output from a specific task.")
  val lastDetailed =
    s"""$LastCommand
	Prints the logging for the previous command, typically at a more verbose level.

$LastCommand <key>
	Prints the logging associated with the provided key.  The key typically refers to a task (for example, test:compile).  The logging that is displayed is restricted to the logging for that particular task.

	See also '$LastGrepCommand'."""

  val exportBrief =
    (s"$ExportCommand <tasks>+", "Executes tasks and displays the equivalent command lines.")
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
  val inspectBrief =
    (
      s"$InspectCommand [tree|uses|definitions|actual] <key>",
      "Prints the value for 'key', the defining scope, delegates, related definitions, and dependencies."
    )
  val inspectDetailed = s"""
    |$InspectCommand [-] <key>
    |
    |	For a plain setting, the value bound to the key argument is displayed using its toString method.
    |	For an alias, the command bound to the alias is displayed.
    |	Otherwise, the type of the key ("Task" or "Input task") is displayed.
    |
    |	"Dependencies" shows the settings that this setting depends on.
    |
    |	"Reverse dependencies" shows the settings that depend on this setting.
    |
    |	When a key is resolved to a value, it may not actually be defined in the requested scope.
    |	In this case, there is a defined search sequence.
    |	"Delegates" shows the scopes that are searched for the key.
    |	"Provided by" shows the scope that contained the value returned for the key.
    |
    |	"Related" shows all of the scopes in which the key is defined.
    |
    |$InspectCommand tree <key>
    |
    |	Displays `key` and its dependencies in a tree structure.
    |	For settings, the value bound to the setting is displayed and for tasks, the type of the task is shown.
    |
    |$InspectCommand uses <key>
    |
    |	Displays the settings and tasks that directly depend on `key`.
    |
    |$InspectCommand definitions <key>
    |
    |	Displays the scopes in which `key` is defined.
    |
    |$InspectCommand actual <key>
    |
    |	Displays the actual dependencies used by `key`.
    |	This is useful because delegation means that a dependency can come from a scope other than the requested one.
    |	Using `inspect actual` will show exactly which scope is providing a value for a setting.
  """.stripMargin.trim

  val SetCommand = "set"
  val setBrief =
    (s"$SetCommand [every] <setting>", "Evaluates a Setting and applies it to the current project.")
  val setDetailed =
    s"""$SetCommand [every] <setting-expression>

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

  def sessionBrief =
    (SessionCommand, s"Manipulates session settings.  For details, run 'help $SessionCommand'.")

  def settingsPreamble = commonPreamble("settings")

  def tasksPreamble =
    commonPreamble("tasks") + """
Tasks produce values.  Use the 'show' command to run the task and print the resulting value."""

  def commonPreamble(label: String) = s"""
This is a list of $label defined for the current project.
It does not list the scopes the $label are defined in; use the 'inspect' command for that."""

  def settingsBrief(label: String) = (label, s"Lists the $label defined for the current project.")

  import BasicCommandStrings.HelpCommand

  def settingsDetailed(label: String) =
    s"""
Syntax summary
	$label [-(v|-vv|...|-V)] [<filter>]

$label
	Displays the main $label defined directly or indirectly for the current project.

-v
	Displays additional $label.  More 'v's increase the number of $label displayed.

-V
	displays all $label

<filter>
	Restricts the $label that are displayed.  The names of $label are searched for
	an exact match against the filter, in which case only the description of the
	exact match is displayed.  Otherwise, the filter is interpreted as a regular
	expression and all $label whose name or description match the regular
	expression are displayed.  Note that this is an additional filter on top of
	the $label selected by the -v style switches, so you must specify -V to search
	all $label.  Use the $HelpCommand command to search all commands, tasks, and
	settings at once.
"""

  def moreAvailableMessage(label: String, search: Boolean) = {
    val verb = if (search) "searched" else "viewed"
    s"More $label may be $verb by increasing verbosity.  See '$HelpCommand $label'\n"
  }

  def aboutBrief = "Displays basic information about sbt and the build."
  def aboutDetailed = aboutBrief

  def projectBrief =
    (ProjectCommand, "Displays the current project or changes to the provided `project`.")
  def projectDetailed =
    s"""$ProjectCommand

	Displays the name of the current project.

$ProjectCommand name

	Changes to the project with the provided name.
	This command fails if there is no project with the given name.

$ProjectCommand {uri}

	Changes to the root project in the build defined by `uri`.
	`uri` must have already been declared as part of the build, such as with Project.dependsOn.

$ProjectCommand {uri}name

	Changes to the project `name` in the build defined by `uri`.
	`uri` must have already been declared as part of the build, such as with Project.dependsOn.

$ProjectCommand /

	Changes to the initial project.

$ProjectCommand ..

	Changes to the parent project of the current project.
	If there is no parent project, the current project is unchanged.

	Use n+1 dots to change to the nth parent.
	For example, 'project ....' is equivalent to three consecutive 'project ..' commands."""

  def projectsBrief =
    "Lists the names of available projects or temporarily adds/removes extra builds to the session."
  def projectsDetailed =
    s"""$ProjectsCommand
	List the names of available builds and the projects defined in those builds.

$ProjectsCommand add <URI>+
	Adds the builds at the provided URIs to this session.
	These builds may be selected using the sProjectCommand command.
	Alternatively, tasks from these builds may be run using the explicit syntax {URI}project/task

$ProjectsCommand remove <URI>+
	Removes extra builds from this session.
	Builds explicitly listed in the build definition are not affected by this command.
"""

  def sbtrc = ".sbtrc"

  def DefaultsCommand = "addDefaultCommands"
  def DefaultsBrief = (DefaultsCommand, DefaultsDetailed)
  def DefaultsDetailed = "Registers default built-in commands"

  def LoadFailed = "loadFailed"
  def OldLoadFailed = "load-failed"

  def LoadProjectImpl = "loadp"
  def LoadProject = "reload"
  def LoadProjectBrief =
    (LoadProject, "(Re)loads the current project or changes to plugins project or returns from it.")
  def LoadProjectDetailed =
    s"""$LoadProject

\t(Re)loads the project in the current directory.

$LoadProject plugins

\t(Re)loads the plugins project (under project directory).

$LoadProject return

\t(Re)loads the root project (and leaves the plugins project)."""

  def InitCommand = "initialize"
  def InitBrief = (InitCommand, "Initializes command processing.")
  def InitDetailed =
    s"""$InitCommand
	Initializes command processing.

Runs the following commands.

defaults
	Registers default commands.

< ~/.sbtrc
< .sbtrc
	Runs commands from ~/.sbtrc and ./.sbtrc if they exist
"""

  import java.io.File
  import sbt.io.syntax._

  def sbtRCs(s: State): Seq[File] =
    (Path.userHome / sbtrc) ::
      (s.baseDir / sbtrc) ::
      Nil

  val CrossCommand = "+"
  val CrossRestoreSessionCommand = "+-"
  val SwitchCommand = "++"

  def crossHelp: Help = Help.more(CrossCommand, CrossDetailed)
  def crossRestoreSessionHelp = Help.more(CrossRestoreSessionCommand, CrossRestoreSessionDetailed)
  def switchHelp: Help = Help.more(SwitchCommand, SwitchDetailed)

  def CrossDetailed =
    s"""$CrossCommand [-v] <command>
	Runs <command> for each Scala version specified for cross-building.

	For each string in `crossScalaVersions` in each project project, this command sets
	the `scalaVersion` of all projects that list that Scala version with that Scala
  version reloads the build, and then executes <command> for those projects.  When
  finished, it resets the build to its original state.

  If -v is supplied, verbose logging of the Scala version switching is done.

	See also `help $SwitchCommand`
"""

  def CrossRestoreSessionDetailed =
    s"""$CrossRestoreSessionCommand

  Restores a session that was captured by the cross command, +.
"""

  def SwitchDetailed =
    s"""$SwitchCommand <scala-version>[!] [-v] [<command>]
	Changes the Scala version and runs a command.

	Sets the `scalaVersion` of all projects that define a Scala cross version that is binary
  compatible with <scala-version> and reloads the build.  If ! is supplied, then the
  version is forced on all projects regardless of whether they are binary compatible or
  not.

  If -v is supplied, verbose logging of the Scala version switching is done.

	If <command> is provided, it is then executed.

$SwitchCommand [<scala-version>=]<scala-home>[!] [-v] [<command>]
	Uses the Scala installation at <scala-home> by configuring the scalaHome setting for
	all projects.

	If <scala-version> is specified, it is used as the value of the scalaVersion setting.
	This is important when using managed dependencies.  This version will determine the
	cross-version used as well as transitive dependencies.

  Only projects that are listed to be binary compatible with the selected Scala version
  have their Scala version switched.  If ! is supplied, then all projects projects have
  their Scala version switched.

  If -v is supplied, verbose logging of the Scala version switching is done.

	If <command> is provided, it is then executed.

	See also `help $CrossCommand`
"""

  val PluginCrossCommand = "^"
  val PluginSwitchCommand = "^^"

  def pluginCrossHelp: Help = Help.more(PluginCrossCommand, PluginCrossDetailed)
  def pluginSwitchHelp: Help = Help.more(PluginSwitchCommand, PluginSwitchDetailed)

  def PluginCrossDetailed =
    s"""$PluginCrossCommand <command>
  Runs <command> for each sbt version specified for cross-building.

  For each string in `crossSbtVersions` in the current project, this command sets the
  `sbtVersion in pluginCrossBuild` of all projects to that version, reloads the build,
  and executes <command>.  When finished, it reloads the build with the original
  Scala version.

  See also `help $PluginSwitchCommand`
"""

  def PluginSwitchDetailed =
    s"""$PluginSwitchCommand <sbt-version> [<command>]
  Changes the sbt version and runs a command.

  Sets the `sbtVersion in pluginCrossBuild` of all projects to <sbt-version> and
  reloads the build. If <command> is provided, it is then executed.

  See also `help $CrossCommand`
"""

  val JavaCrossCommand = "java+"
  val JavaSwitchCommand = "java++"

  def javaCrossHelp: Help = Help.more(JavaCrossCommand, JavaCrossDetailed)
  def javaSwitchHelp: Help = Help.more(JavaSwitchCommand, JavaSwitchDetailed)

  def JavaCrossDetailed =
    s"""$JavaCrossCommand <command>
  Runs <command> for each JDK version specified for cross-JDK testing.
  For each string in `crossJavaVersions` in the current project, this command sets the
  `javaHome` of all projects to the corresponding Java home, reloads the build,
  and executes <command>.  When finished, it reloads the build with the original
  `javaHome`.
  Note that `Test / fork := true` is needed for `javaHome` to be effective.
  See also `help $JavaSwitchCommand`
"""

  def JavaSwitchDetailed =
    s"""$JavaSwitchCommand <java-version>
  Changes the JDK version and runs a command.
  Sets the `javaHome` of all projects to <java-version> and
  reloads the build. If <command> is provided, it is then executed.
  See also `help $JavaSwitchCommand`
"""
}
