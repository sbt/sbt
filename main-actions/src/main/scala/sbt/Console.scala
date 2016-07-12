/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010  Mark Harrah
 */
package sbt

import java.io.File
import sbt.internal.inc.AnalyzingCompiler
import sbt.util.Logger

import xsbti.compile.{ Inputs, Compilers }

final class Console(compiler: AnalyzingCompiler) {
  /** Starts an interactive scala interpreter session with the given classpath.*/
  def apply(classpath: Seq[File], log: Logger): Option[String] =
    apply(classpath, Nil, "", "", log)

  def apply(classpath: Seq[File], options: Seq[String], initialCommands: String, cleanupCommands: String, log: Logger): Option[String] =
    apply(classpath, options, initialCommands, cleanupCommands)(None, Nil)(log)

  def apply(classpath: Seq[File], options: Seq[String], loader: ClassLoader, initialCommands: String, cleanupCommands: String)(bindings: (String, Any)*)(implicit log: Logger): Option[String] =
    apply(classpath, options, initialCommands, cleanupCommands)(Some(loader), bindings)

  def apply(classpath: Seq[File], options: Seq[String], initialCommands: String, cleanupCommands: String)(loader: Option[ClassLoader], bindings: Seq[(String, Any)])(implicit log: Logger): Option[String] =
    {
      def console0() = compiler.console(classpath, options, initialCommands, cleanupCommands, log)(loader, bindings)
      // TODO: Fix JLine
      //JLine.withJLine(Run.executeTrapExit(console0, log))
      Run.executeTrapExit(console0, log)
    }
}
object Console {
  def apply(conf: Inputs): Console =
    conf.compilers match {
      case cs: Compilers => new Console(cs.scalac match { case x: AnalyzingCompiler => x })
    }
}
