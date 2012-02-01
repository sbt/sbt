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

	def helpBrief = (HelpCommand + " [command]*", "Displays this help message or prints detailed help on requested commands.")
	def helpDetailed = """
If an argument is provided, this prints detailed help for that command.
Otherwise, this prints a help summary."""

	def historyHelp = Help.briefDetail(HistoryCommands.descriptions)

	def exitBrief = "Terminates the build."

	def ReadCommand = "<"
	def ReadFiles = " file1 file2 ..."
	def ReadBrief = (ReadCommand + " <file>*", "Reads command lines from the provided files.")
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
	def ApplyBrief = (ApplyCommand + " <module-name>*", ApplyDetailed)
	def ApplyDetailed = "Transforms the current State by calling <module-name>.apply(currentState) for each listed module name."

	def RebootCommand = "reboot"
	def RebootSummary = RebootCommand + " [full]"
	def RebootBrief = (RebootSummary, "Reboots sbt and then executes the remaining commands.")
	def RebootDetailed =
RebootSummary + """

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
	def AppendLastBrief = (AppendCommand + " <command>", AppendLastDetailed)
	def AppendLastDetailed = "Appends 'command' to list of commands to run."

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
	Whenever `name` is entered, the corresponding `value` is run.
	If any argument is provided to `name`, it is appended as argument to `value`.

""" +
AliasCommand + """ name=

	Removes the alias for `name`."""

	def Shell = "shell"
	def ShellBrief = ShellDetailed
	def ShellDetailed = "Provides an interactive prompt from which commands can be run."

	def ClearOnFailure = "--"
	def OnFailure = "-"
	def OnFailureBrief = (OnFailure + " command", "Registers 'command' to run if a command fails.")
	def OnFailureDetailed =
OnFailure + """ command

	Registers 'command' to run when a command fails to complete normally.

	Only one failure command may be registered at a time, so this command
	  replaces the previous command if there is one.

	The failure command resets when it runs once, so it must be added
	  again if desired."""

	def IfLast = "iflast"
	def IfLastBrief = (IfLast + " <command>", IfLastCommon)
	def IfLastCommon = "If there are no more commands after this one, 'command' is run."
	def IfLastDetailed =
IfLast + """ command

	""" + IfLastCommon

	val ContinuousExecutePrefix = "~"
	def continuousBriefHelp = (ContinuousExecutePrefix + " <command>", "Executes the specified command whenever source files change.")
}
