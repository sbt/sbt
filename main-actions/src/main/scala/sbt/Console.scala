/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import java.io.File
import sbt.internal.inc.{ AnalyzingCompiler, PlainVirtualFile }
import sbt.internal.util.Terminal
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
    def console0() =
      compiler.console(classpath map { x =>
        PlainVirtualFile(x.toPath)
      }, options, initialCommands, cleanupCommands, log)(
        loader,
        bindings
      )
    Terminal.withRawSystemIn(Run.executeTrapExit(console0, log))
  }
}

object Console {
  def apply(conf: Inputs): Console =
    conf.compilers match {
      case cs: Compilers => new Console(cs.scalac match { case x: AnalyzingCompiler => x })
    }
}
