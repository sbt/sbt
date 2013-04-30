/* sbt -- Simple Build Tool
 * Copyright 2010  Mark Harrah
 */
package sbt

import complete.HistoryCommands
import scala.annotation.tailrec

import java.io.File
import Path._

object BasicCommandStrings
{
	val HelpCommand = "help"
	val Exit = "exit"
	val Quit = "quit"

	/** The command name to terminate the program.*/
	val TerminateAction: String = Exit

	def helpBrief = (HelpCommand, "Displays this help message or prints detailed help on requested commands (run 'help <command>').")
	def helpDetailed = HelpCommand + """

	Prints a help summary.

""" + HelpCommand + """ <command>

	Prints detailed help for command <command>.

""" + HelpCommand + """ <regular expression>

	Searches the help according to the provided regular expression.
"""

	def HistoryHelpBrief = (HistoryCommands.Start -> "History command help.  Lists and describes all history commands.")
	def historyHelp = Help(Nil, (HistoryHelpBrief +: HistoryCommands.descriptions).toMap, Set(HistoryCommands.Start))

	def exitBrief = "Terminates the build."

	def ReadCommand = "<"
	def ReadFiles = " file1 file2 ..."
	def ReadDetailed =
ReadCommand + ReadFiles + """

	Reads the lines from the given files and inserts them as commands.
	All empty lines and lines that start with '#' are ignored.
	If a file does not exist or is not readable, this command fails.

	All the lines from all the files are read before any of the commands
	  are executed. Thus, if any file is not readable, none of commands
	  from any of the files (even the existing ones) will be run.

	You probably need to escape this command if entering it at your shell."""

	def ApplyCommand = "apply"
	def ApplyDetailed = 
ApplyCommand + """ [-cp|-classpath <classpath>] <module-name>*
	Transforms the current State by calling <module-name>.apply(currentState) for each listed module name.
	Here, currentState is of type sbt.State.
   If a classpath is provided, modules are loaded from a new class loader for this classpath.
"""

	def RebootCommand = "reboot"
	def RebootDetailed =
RebootCommand + """ [full]

	This command is equivalent to exiting sbt, restarting, and running the
	  remaining commands with the exception that the JVM is not shut down.

	If 'full' is specified, the boot directory (`~/.sbt/boot` by default)
	  is deleted before restarting.  This forces an update of sbt and Scala
	  and is useful when working with development versions of sbt or Scala."""

	def Multi = ";"
	def MultiBrief = (Multi + " <command> (" + Multi + " <command>)*", "Runs the provided semicolon-separated commands.")
	def MultiDetailed =
Multi + " command1 " + Multi + """ command2 ...

	Runs the specified commands."""

	def AppendCommand = "append"
	def AppendLastDetailed =
AppendCommand + """ <command>
	Appends 'command' to list of commands to run.
"""

	val AliasCommand = "alias"
	def AliasDetailed =
AliasCommand + """

	Prints a list of defined aliases.

""" +
AliasCommand + """ name

	Prints the alias defined for `name`.

""" +
AliasCommand + """ name=value

	Sets the alias `name` to `value`, replacing any existing alias with that name.
	Whenever `name` is entered, the corresponding `value` is run.
	If any argument is provided to `name`, it is appended as argument to `value`.

""" +
AliasCommand + """ name=

	Removes the alias for `name`."""

	def Shell = "shell"
	def ShellDetailed = "Provides an interactive prompt from which commands can be run."

	private[sbt] def StashOnFailure = "sbtStashOnFailure"
	private[sbt] def PopOnFailure = "sbtPopOnFailure"

	def ClearOnFailure = "--"
	def OnFailure = "-"
	def OnFailureDetailed =
OnFailure + """ command

	Registers 'command' to run when a command fails to complete normally.

	Only one failure command may be registered at a time, so this command
	  replaces the previous command if there is one.

	The failure command resets when it runs once, so it must be added
	  again if desired."""

	def IfLast = "iflast"
	def IfLastCommon = "If there are no more commands after this one, 'command' is run."
	def IfLastDetailed =
IfLast + """ <command>

	""" + IfLastCommon

	val ContinuousExecutePrefix = "~"
	def continuousDetail = "Executes the specified command whenever source files change."
	def continuousBriefHelp = (ContinuousExecutePrefix + " <command>", continuousDetail)
}
