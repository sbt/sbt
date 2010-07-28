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
	def logger(s: State) = s match {
		case State(p: Logged) => p.log
		case _ => new ConsoleLogger //TODO: add a default logger to State
	}
	def notReadable(files: Seq[File]): Seq[File] = files filter { !_.canRead }
	def readable(files: Seq[File]): Seq[File] = files filter { _.canRead }
	def sbtRCs(s: State): Seq[File] =
		(Path.userHome / sbtrc) ::
		(s.baseDir / sbtrc asFile) ::
		Nil

	def readLines(files: Seq[File]): Seq[String] = files flatMap (line => IO.readLines(line)) flatMap processLine
	def processLine(s: String) = { val trimmed = s.trim; if(ignoreLine(trimmed)) None else Some(trimmed) }
	def ignoreLine(s: String) = s.isEmpty || s.startsWith("#")

	val HelpCommand = "help"
	val ProjectCommand = "project"
	val ProjectsCommand = "projects"

	val Exit = "exit"
	val Quit = "quit"

	/** The list of command names that may be used to terminate the program.*/
	val TerminateActions: Seq[String] = Seq(Exit, Quit)

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
"""

	def projectsBrief = (ProjectsCommand, projectsDetailed)
	def projectsDetailed = "Displays the names of available projects."

	def historyHelp = HistoryCommands.descriptions.map( d => Help(d) )

	def exitBrief = (TerminateActions.mkString(", "), "Terminates the build.")

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

	def ReloadCommand = "reload"
	def ReloadBrief = (ReloadCommand, "Reloads the session and continues to execute the remaining commands.")
	def ReloadDetailed =
ReloadCommand + """
	This command is equivalent to exiting, restarting, and running the
	 remaining commands with the exception that the jvm is not shut down.
"""

	def Multi = ";"
	def MultiBrief = ("( " + Multi + " command )+", MultiDetailed)
	def MultiDetailed = "Runs the provided semicolon-separated commands."

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

	def Load = "load"
	def LoadLabel = "a project"
	def LoadCommand = "load-commands"
	def LoadCommandLabel = "commands"

	def Shell = "shell"
	def ShellBrief = (Shell, ShellDetailed)
	def ShellDetailed = "Provides an interactive prompt from which commands can be run."

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
	def IfLastBrief = (IfLast + " command", IfLastDetailed)
	def IfLastDetailed = "If there are no more commands after this one, 'command' is run."

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