/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal
package scripted

import sbt.io.Path._
import sbt.io.syntax._
import sbt.io.{ CopyOptions, IO }

import java.io.File

class FileCommands(baseDirectory: File) extends BasicStatementHandler {
  lazy val commands: Map[String, List[String] => Unit] = commandMap
  def commandMap: Map[String, List[String] => Unit] =
    Map(
      "touch" nonEmpty touch _,
      "delete" nonEmpty delete _,
      "exists" nonEmpty exists _,
      "mkdir" nonEmpty makeDirectories _,
      "absent" nonEmpty absent _,
      //			"sync" twoArg("Two directory paths", sync _),
      "newer".twoArg("Two paths", newer _),
      "pause" noArg {
        println("Pausing in " + baseDirectory)
        /*readLine("Press enter to continue. ") */
        print("Press enter to continue. ")
        System.console.readLine
        println()
      },
      "sleep".oneArg("Time in milliseconds", time => Thread.sleep(time.toLong)),
      "exec" nonEmpty (execute _),
      "copy" copy (to => rebase(baseDirectory, to)),
      "copy-file".twoArg("Two paths", (from, to) => copyFile(from, to)),
      "copy-file-with-options".fiveArg(
        "Copy files options and file paths",
        (
            overwrite,
            preserveLastModified,
            preserveExecutable,
            from,
            to
        ) => copyFileWithOptions(from, to)(overwrite, preserveLastModified, preserveExecutable)
      ),
      "must-mirror".twoArg("Two paths", diffFiles _),
      "copy-flat" copy flat
    )

  def apply(command: String, arguments: List[String]): Unit =
    commands.get(command).map(_(arguments)) match {
      case Some(_) => ()
      case _       => scriptError("Unknown command " + command); ()
    }

  def scriptError(message: String): Unit = sys.error("Test script error: " + message)
  def spaced[T](l: Seq[T]) = l.mkString(" ")
  def fromStrings(paths: List[String]) = paths.map(fromString)
  def fromString(path: String) = new File(baseDirectory, path)
  def touch(paths: List[String]): Unit = IO.touch(fromStrings(paths))
  def delete(paths: List[String]): Unit = IO.delete(fromStrings(paths))
  /*def sync(from: String, to: String) =
		IO.sync(fromString(from), fromString(to), log)*/
  def copyFile(from: String, to: String, options: CopyOptions = CopyOptions()): Unit =
    IO.copyFile(fromString(from), fromString(to), options)
  def copyFileWithOptions(from: String, to: String)(
      overwrite: String,
      preserveLastModified: String,
      preserveExecutable: String,
  ) =
    copyFile(
      from,
      to,
      CopyOptions(
        overwrite = overwrite.toBoolean,
        preserveLastModified = preserveLastModified.toBoolean,
        preserveExecutable = preserveExecutable.toBoolean
      )
    )
  def makeDirectories(paths: List[String]) =
    IO.createDirectories(fromStrings(paths))
  def diffFiles(file1: String, file2: String): Unit = {
    val lines1 = IO.readLines(fromString(file1))
    val lines2 = IO.readLines(fromString(file2))
    if (lines1 != lines2)
      scriptError(
        "File contents are different:\n" + lines1.mkString("\n") +
          "\nAnd:\n" + lines2.mkString("\n")
      )
  }

  def newer(a: String, b: String): Unit = {
    val pathA = fromString(a)
    val pathB = fromString(b)
    val isNewer = pathA.exists &&
      (!pathB.exists || IO.getModifiedTimeOrZero(pathA) > IO.getModifiedTimeOrZero(pathB))
    if (!isNewer) {
      scriptError(s"$pathA is not newer than $pathB")
    }
  }
  def exists(paths: List[String]): Unit = {
    val notPresent = fromStrings(paths).filter(!_.exists)
    if (notPresent.nonEmpty)
      scriptError("File(s) did not exist: " + notPresent.mkString("[ ", " , ", " ]"))
  }
  def absent(paths: List[String]): Unit = {
    val present = fromStrings(paths).filter(_.exists)
    if (present.nonEmpty)
      scriptError("File(s) existed: " + present.mkString("[ ", " , ", " ]"))
  }
  def execute(command: List[String]): Unit = execute0(command.head, command.tail)
  def execute0(command: String, args: List[String]): Unit = {
    if (command.trim.isEmpty) scriptError("Command was empty.")
    else {
      val exitValue = sys.process.Process(command :: args, baseDirectory).!
      if (exitValue != 0)
        sys.error("Nonzero exit value (" + exitValue + ")")
    }
  }

  // these are for readability of the command list
  implicit def commandBuilder(s: String): CommandBuilder = new CommandBuilder(s)
  final class CommandBuilder(commandName: String) {
    type NamedCommand = (String, List[String] => Unit)
    def nonEmpty(action: List[String] => Unit): NamedCommand =
      commandName -> { paths =>
        if (paths.isEmpty)
          scriptError("No arguments specified for " + commandName + " command.")
        else
          action(paths)
      }
    def fiveArg(
        requiredArgs: String,
        action: (String, String, String, String, String) => Unit
    ): NamedCommand =
      commandName -> {
        case List(a, b, c, d, e) => action(a, b, c, d, e)
        case other               => wrongArguments(requiredArgs, other)
      }
    def twoArg(requiredArgs: String, action: (String, String) => Unit): NamedCommand =
      commandName -> {
        case List(from, to) => action(from, to)
        case other          => wrongArguments(requiredArgs, other)
      }
    def noArg(action: => Unit): NamedCommand =
      commandName -> {
        case Nil   => action
        case other => wrongArguments(other)
      }
    def oneArg(requiredArgs: String, action: String => Unit): NamedCommand =
      commandName -> {
        case List(single) => action(single)
        case other        => wrongArguments(requiredArgs, other)
      }
    def copy(mapper: File => FileMap): NamedCommand =
      commandName -> {
        case Nil         => scriptError("No paths specified for " + commandName + " command.")
        case path :: Nil => scriptError("No destination specified for " + commandName + " command.")
        case paths =>
          val mapped = fromStrings(paths)
          val map = mapper(mapped.last)
          IO.copy(mapped.init pair map)
          ()
      }

    def wrongArguments(args: List[String]): Unit =
      scriptError(
        "Command '" + commandName + "' does not accept arguments (found '" + spaced(args) + "')."
      )

    def wrongArguments(requiredArgs: String, args: List[String]): Unit =
      scriptError(
        "Wrong number of arguments to " + commandName + " command.  " +
          requiredArgs + " required, found: '" + spaced(args) + "'."
      )
  }
}
