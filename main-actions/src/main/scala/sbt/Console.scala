/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import java.io.File
import java.nio.channels.ClosedChannelException
import sbt.internal.inc.{ AnalyzingCompiler, MappedFileConverter, PlainVirtualFile }
import sbt.internal.util.{ DeprecatedJLine, Terminal }
import sbt.util.Logger
import xsbti.compile.{ Compilers, Inputs }

import scala.util.Try

final class Console(compiler: AnalyzingCompiler) {

  /** Starts an interactive scala interpreter session with the given classpath.*/
  def apply(classpath: Seq[File], log: Logger): Try[Unit] =
    apply(classpath, Nil, "", "", log)

  def apply(
      classpath: Seq[File],
      options: Seq[String],
      initialCommands: String,
      cleanupCommands: String,
      log: Logger
  ): Try[Unit] =
    apply(classpath, options, initialCommands, cleanupCommands)(None, Nil)(log)

  def apply(
      classpath: Seq[File],
      options: Seq[String],
      loader: ClassLoader,
      initialCommands: String,
      cleanupCommands: String
  )(bindings: (String, Any)*)(implicit log: Logger): Try[Unit] =
    apply(classpath, options, initialCommands, cleanupCommands)(Some(loader), bindings)

  def apply(
      classpath: Seq[File],
      options: Seq[String],
      initialCommands: String,
      cleanupCommands: String
  )(loader: Option[ClassLoader], bindings: Seq[(String, Any)])(implicit log: Logger): Try[Unit] = {
    apply(classpath, options, initialCommands, cleanupCommands, Terminal.get)(loader, bindings)
  }
  def apply(
      classpath: Seq[File],
      options: Seq[String],
      initialCommands: String,
      cleanupCommands: String,
      terminal: Terminal
  )(loader: Option[ClassLoader], bindings: Seq[(String, Any)])(implicit log: Logger): Try[Unit] = {
    def console0(): Unit =
      try {
        compiler.console(classpath map { x =>
          PlainVirtualFile(x.toPath)
        }, MappedFileConverter.empty, options, initialCommands, cleanupCommands, log)(
          loader,
          bindings
        )
      } catch { case _: InterruptedException | _: ClosedChannelException => }
    val previous = sys.props.get("scala.color").getOrElse("auto")
    val jline3term = sbt.internal.util.JLine3(terminal)
    try {
      sys.props("scala.color") = if (terminal.isColorEnabled) "true" else "false"
      terminal.withRawOutput {
        jline.TerminalFactory.set(terminal.toJLine)
        DeprecatedJLine.setTerminalOverride(jline3term)
        terminal.withRawInput(Run.executeSuccess(console0))
      }
    } finally {
      sys.props("scala.color") = previous
      jline3term.close()
    }
  }
}

object Console {
  def apply(conf: Inputs): Console =
    conf.compilers match {
      case cs: Compilers => new Console(cs.scalac match { case x: AnalyzingCompiler => x })
    }
}
