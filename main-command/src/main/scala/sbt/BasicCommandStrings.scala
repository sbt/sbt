/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import sbt.util.Level
import sbt.internal.util.complete.HistoryCommands

object BasicCommandStrings {
  val HelpCommand: String = "help"
  val CompletionsCommand: String = "completions"
  val Exit: String = "exit"
  val Shutdown: String = "shutdown"
  val Quit: String = "quit"
  val TemplateCommand: String = "new"
  val Cancel: String = "cancel"

  /** The command name to terminate the program.*/
  val TerminateAction: String = Exit

  def helpBrief: (String, String) =
    (
      HelpCommand,
      s"Displays this help message or prints detailed help on requested commands (run '$HelpCommand <command>')."
    )
  def helpDetailed: String = s"""$HelpCommand

	Prints a help summary.

$HelpCommand <command>

	Prints detailed help for command <command>.

$HelpCommand <regular expression>

	Searches the help according to the provided regular expression.
"""

  def CompletionsDetailed: String =
    "Displays a list of completions for the given argument string (run 'completions <string>')."
  def CompletionsBrief: (String, String) = (CompletionsCommand, CompletionsDetailed)

  def templateBrief: (String, String) = (TemplateCommand, "Creates a new sbt build.")
  def templateDetailed: String =
    TemplateCommand + """ [--options] <template>
  Create a new sbt build based on the given template.
  sbt provides out-of-the-box support for Giter8 templates. See foundweekends.org/giter8/ for details.
  
  Example:
    sbt new scala/scala-seed.g8
  """

  def HistoryHelpBrief: (String, String) =
    (HistoryCommands.Start, "History command help.  Lists and describes all history commands.")
  def historyHelp =
    Help(Nil, (HistoryHelpBrief +: HistoryCommands.descriptions).toMap, Set(HistoryCommands.Start))

  def exitBrief: String = "Terminates the remote client or the build when called from the console."
  def shutdownBrief: String = "Terminates the build."

  def logLevelHelp: Help = {
    val levels = Level.values.toSeq
    val levelList = levels.mkString(", ")
    val brief =
      ("<log-level>", "Sets the logging level to 'log-level'.  Valid levels: " + levelList)
    val detailed = levels.map(l => (l.toString, logLevelDetail(l))).toMap
    Help(brief, detailed)
  }

  private[this] def logLevelDetail(level: Level.Value): String =
    s"""$level

	Sets the global logging level to $level.
	This will be used as the default level for logging from commands, settings, and tasks.
	Any explicit `logLevel` configuration in a project overrides this setting.

-$level OR --$level

	Sets the global logging level as described above, but does so before any other commands are executed on startup, including project loading.
	This is useful as a startup option:
		* it takes effect before any logging occurs
		* if no other commands are passed, interactive mode is still entered
"""

  def runEarly(command: String): String = s"$EarlyCommand($command)"
  private[sbt] def isEarlyCommand(s: String): Boolean = {
    val levelOptions = Level.values.toSeq flatMap { elem =>
      List("-" + elem, "--" + elem)
    }
    (s.startsWith(EarlyCommand + "(") && s.endsWith(")")) ||
    (levelOptions contains s) ||
    (s.startsWith("-" + AddPluginSbtFileCommand) || s.startsWith("--" + AddPluginSbtFileCommand))
  }

  val EarlyCommand: String = "early"
  val EarlyCommandBrief: (String, String) =
    (s"$EarlyCommand(<command>)", "Schedules a command to run before other commands on startup.")
  val EarlyCommandDetailed: String =
    s"""$EarlyCommand(<command>)

	Schedules an early command, which will be run before other commands on the command line.
	The order is preserved between all early commands, so `sbt "early(a)" "early(b)"` executes `a` and `b` in order.
"""

  def addPluginSbtFileHelp(): Help = {
    val brief =
      (s"--$AddPluginSbtFileCommand=<file>", "Adds the given *.sbt file to the plugin build.")
    Help(brief)
  }

  val AddPluginSbtFileCommand: String = "addPluginSbtFile"

  def ReadCommand: String = "<"
  def ReadFiles: String = " file1 file2 ..."
  def ReadDetailed: String =
    ReadCommand + ReadFiles + """

	Reads the lines from the given files and inserts them as commands.
	All empty lines and lines that start with '#' are ignored.
	If a file does not exist or is not readable, this command fails.

	All the lines from all the files are read before any of the commands
	  are executed. Thus, if any file is not readable, none of commands
	  from any of the files (even the existing ones) will be run.

	You probably need to escape this command if entering it at your shell."""

  def ApplyCommand: String = "apply"
  def ApplyDetailed: String =
    ApplyCommand + """ [-cp|-classpath <classpath>] <module-name>*
	Transforms the current State by calling <module-name>.apply(currentState) for each listed module name.
	Here, currentState is of type sbt.State.
   If a classpath is provided, modules are loaded from a new class loader for this classpath.
"""

  private[sbt] def RebootNetwork: String = "sbtRebootNetwork"
  private[sbt] def RebootImpl: String = "sbtRebootImpl"
  def RebootCommand: String = "reboot"
  def RebootDetailed: String =
    RebootCommand + """ [dev | full]

	This command is equivalent to exiting sbt, restarting, and running the
	  remaining commands with the exception that the JVM is not shut down.

	If 'dev' is specified, the current sbt artifacts from the boot directory
	  (`~/.sbt/boot` by default) are deleted before restarting.
	This forces an update of sbt and Scala, which is useful when working with development
	  versions of sbt.
	If 'full' is specified, the boot directory is wiped out before restarting.
"""

  def Multi: String = ";"
  def MultiBrief: (String, String) =
    (
      "<command> (" + Multi + " <command>)*",
      "Runs the provided semicolon-separated commands."
    )
  def MultiDetailed: String =
    Multi + " command1 " + Multi + """ command2 ...

	Runs the specified commands."""

  def AppendCommand: String = "append"
  def AppendLastDetailed: String =
    AppendCommand + """ <command>
	Appends 'command' to list of commands to run.
"""

  val AliasCommand: String = "alias"
  def AliasDetailed: String =
    s"""$AliasCommand

	Prints a list of defined aliases.

$AliasCommand name

	Prints the alias defined for `name`.

$AliasCommand name=value

	Sets the alias `name` to `value`, replacing any existing alias with that name.
	Whenever `name` is entered, the corresponding `value` is run.
	If any argument is provided to `name`, it is appended as argument to `value`.

$AliasCommand name=

	Removes the alias for `name`."""

  def Shell = "shell"
  def ShellDetailed: String =
    "Provides an interactive prompt and network server from which commands can be run."

  def StartServer = "startServer"
  def StartServerDetailed: String =
    s"""$StartServer
	Starts the server if it has not been started. This is intended to be used with
	-Dsbt.server.autostart=false."""

  def ServerDetailed: String =
    "--server always runs sbt in not-daemon mode."
  def DashDashServer: String = "--server"

  def OldShell: String = "oldshell"
  def OldShellDetailed = "Provides an interactive prompt from which commands can be run."

  def Client: String = "client"
  def ClientDetailed: String =
    "Provides an interactive prompt from which commands can be run on a server."
  def JavaClient: String = "--java-client"
  def DashClient: String = "-client"
  def DashDashClient: String = "--client"
  def DashDashDetachStdio: String = "--detach-stdio"

  def StashOnFailure: String = "sbtStashOnFailure"
  def PopOnFailure: String = "sbtPopOnFailure"

  def FailureWall: String = "resumeFromFailure"

  def ReportResult = "sbtReportResult"
  def CompleteExec = "sbtCompleteExec"
  def MapExec = "sbtMapExec"
  def PromptChannel = "sbtPromptChannel"

  def ClearOnFailure: String = "sbtClearOnFailure"
  def OnFailure: String = "onFailure"
  def OnFailureDetailed: String =
    OnFailure + """ command

	Registers 'command' to run when a command fails to complete normally.

	Only one failure command may be registered at a time, so this command
	  replaces the previous command if there is one.

	The failure command resets when it runs once, so it must be added
	  again if desired."""

  def IfLast = "iflast"
  def IfLastCommon = "If there are no more commands after this one, 'command' is run."
  def IfLastDetailed =
    s"""$IfLast <command>

	$IfLastCommon"""

  val ContinuousExecutePrefix = "~"
  def continuousDetail: String = "Executes the specified command whenever source files change."
  def continuousBriefHelp: (String, String) =
    (ContinuousExecutePrefix + " <command>", continuousDetail)
  def ClearCaches: String = "clearCaches"
  def ClearCachesDetailed: String = "Clears all of sbt's internal caches."

  private[sbt] val networkExecPrefix = "__"
  private[sbt] val DisconnectNetworkChannel = s"${networkExecPrefix}disconnectNetworkChannel"
}
