/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import sbt.librarymanagement.{ Configurations, ScalaArtifacts }

import sbt.util.Level

import java.io.File
import Keys.*
import EvaluateConfigurations.evaluateConfiguration as evaluate
import Configurations.Compile
import Scope.Global
import sbt.ProjectExtra.{ extract, setProject }
import sbt.SlashSyntax0.*

import sbt.io.{ Hash, IO }
import scala.annotation.tailrec

object Script {
  final val Name = "script"
  // When shebang is stripped, compiler error line numbers may be off by one for the original file;
  // position mapping could be added in a future improvement (see sbt/sbt#6274).
  /** If the first line is a shebang (#!), drop it so the compiler never sees it. */
  private[internal] def stripShebang(lines: Seq[String]): Seq[String] =
    if (lines.nonEmpty && lines.head.startsWith("#!")) lines.drop(1) else lines

  /** Lines that are not inside any /*** ... */ block (i.e. the executable script body). */
  private[internal] def scriptBodyLines(file: File): Seq[String] = {
    val lines = IO.readLines(file).toIndexedSeq
    // Block(offset, lines): offset = index of /*** line, lines = content between /*** and */ (excl. both).
    // Exclude /*** (off), content (off+1..off+ls.size), and */ (off+1+ls.size).
    val blockSet = blocks(file).flatMap {
      case Block(off, ls) => (off until off + 2 + ls.size)
    }.toSet
    lines.indices.filterNot(blockSet).map(lines)
  }

  /** Write a Scala 3 compilable file that wraps the script body in object Main { def main(...) = { ... } }. */
  private def writeWrappedScript(body: Seq[String], out: File): Unit = {
    val indent = "  "
    val inner = body.map(line => indent + line).mkString("\n")
    val content =
      s"""object Main {
         |  def main(args: Array[String]): Unit = {
         |$inner
         |  }
         |}
         |""".stripMargin
    IO.write(out, content)
  }

  lazy val command =
    Command.command(Name) { state =>
      val scriptArg = state.remainingCommands.headOption map { _.commandLine } getOrElse sys.error(
        "No script file specified"
      )
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
      val linesWithoutShebang = stripShebang(IO.readLines(scriptFile))
      IO.write(script, linesWithoutShebang.mkString("", "\n", "\n"))

      val scriptMain = new File(src, "Main.scala")
      writeWrappedScript(scriptBodyLines(script), scriptMain)

      val (eval, structure) = Load.defaultLoad(state, base, state.log)
      val session = Load.initialSession(structure, eval)
      val extracted = Project.extract(session, structure)
      val vf = structure.converter.toVirtualFile(script.toPath())
      import extracted.{ *, given }

      val embeddedSettings = blocks(script).flatMap { block =>
        evaluate(eval(), vf, block.lines, currentUnit.imports, block.offset + 1)(currentLoader)
      }
      val scriptBaseName = script.getName.stripSuffix(".scala")
      val scriptAsSource = (Compile / sources) := Def.uncached {
        if (ScalaArtifacts.isScala3(scalaVersion.value)) scriptMain :: Nil else script :: Nil
      }
      val asScript = scalacOptions := Def.uncached {
        val extra =
          if (ScalaArtifacts.isScala3(scalaVersion.value)) Nil
          else Seq("-Xscript", scriptBaseName)
        scalacOptions.value ++ extra
      }
      val scriptMainClass = (run / mainClass) := Def.uncached {
        if (ScalaArtifacts.isScala3(scalaVersion.value)) Some("Main") else Some(scriptBaseName)
      }
      val scriptSettings = Seq(
        asScript,
        scriptAsSource,
        scriptMainClass,
        (Global / logLevel) := Level.Warn,
        (Global / showSuccess) := false
      )
      val append = Load.transformSettings(
        Load.projectScope(currentRef),
        currentRef.build,
        rootProject,
        scriptSettings ++ embeddedSettings
      )

      val newStructure = Load.reapply(session.original ++ append, structure)
      val arguments = state.remainingCommands.drop(1).map(e => s""""${e.commandLine}"""")
      val newState = arguments.mkString("run ", " ", "") :: state.copy(remainingCommands = Nil)
      Project.setProject(session, newStructure, newState)
    }

  final case class Block(offset: Int, lines: Seq[String])
  def blocks(file: File): Seq[Block] = {
    val lines = IO.readLines(file).toIndexedSeq
    @tailrec
    def blocks(b: Block, acc: List[Block]): List[Block] =
      if (b.lines.isEmpty) acc.reverse
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
