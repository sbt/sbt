/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt
package internal

import sbt.librarymanagement.Configurations

import sbt.util.Level

import java.io.File
import Keys._
import EvaluateConfigurations.{ evaluateConfiguration => evaluate }
import Configurations.Compile
import Scope.Global

import sbt.io.{ Hash, IO }

object Script {
  final val Name = "script"
  lazy val command =
    Command.command(Name) { state =>
      val scriptArg = state.remainingCommands.headOption map { _.commandLine } getOrElse sys.error(
        "No script file specified")
      val scriptFile = new File(scriptArg).getAbsoluteFile
      val hash = Hash.halve(Hash.toHex(Hash(scriptFile.getAbsolutePath)))
      val base = new File(CommandUtil.bootDirectory(state), hash)
      IO.createDirectory(base)
      val src = new File(base, "src_managed")
      IO.createDirectory(src)
      // handle any script extension or none
      val scalaFile = {
        val dotIndex = scriptArg.lastIndexOf(".")
        if (dotIndex == -1) scriptArg + ".scala"
        else scriptArg.substring(0, dotIndex) + ".scala"
      }
      val script = new File(src, scalaFile)
      IO.copyFile(scriptFile, script)

      val (eval, structure) = Load.defaultLoad(state, base, state.log)
      val session = Load.initialSession(structure, eval)
      val extracted = Project.extract(session, structure)
      import extracted._

      val embeddedSettings = blocks(script).flatMap { block =>
        evaluate(eval(), script, block.lines, currentUnit.imports, block.offset + 1)(currentLoader)
      }
      val scriptAsSource = sources in Compile := script :: Nil
      val asScript = scalacOptions ++= Seq("-Xscript", script.getName.stripSuffix(".scala"))
      val scriptSettings = Seq(asScript,
                               scriptAsSource,
                               logLevel in Global := Level.Warn,
                               showSuccess in Global := false)
      val append = Load.transformSettings(Load.projectScope(currentRef),
                                          currentRef.build,
                                          rootProject,
                                          scriptSettings ++ embeddedSettings)

      val newStructure = Load.reapply(session.original ++ append, structure)
      val arguments = state.remainingCommands.drop(1).map(e => s""""${e.commandLine}"""")
      val newState = arguments.mkString("run ", " ", "") :: state.copy(remainingCommands = Nil)
      Project.setProject(session, newStructure, newState)
    }

  final case class Block(offset: Int, lines: Seq[String])
  def blocks(file: File): Seq[Block] = {
    val lines = IO.readLines(file).toIndexedSeq
    def blocks(b: Block, acc: List[Block]): List[Block] =
      if (b.lines.isEmpty)
        acc.reverse
      else {
        val (dropped, blockToEnd) = b.lines.span { line =>
          !line.startsWith(BlockStart)
        }
        val (block, remaining) = blockToEnd.span { line =>
          !line.startsWith(BlockEnd)
        }
        val offset = b.offset + dropped.length
        blocks(Block(offset + block.length, remaining), Block(offset, block.drop(1)) :: acc)
      }
    blocks(Block(0, lines), Nil)
  }
  val BlockStart = "/***"
  val BlockEnd = "*/"
  def fail(s: State, msg: String): State = {
    System.err.println(msg)
    s.fail
  }
}
