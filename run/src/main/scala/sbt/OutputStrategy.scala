/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt

import sbt.util.Logger
import java.io.OutputStream

/** Configures where the standard output and error streams from a forked process go.*/
sealed abstract class OutputStrategy

object OutputStrategy {

  /**
   * Configures the forked standard output to go to standard output of this process and
   * for the forked standard error to go to the standard error of this process.
   */
  case object StdoutOutput extends OutputStrategy

  /**
   * Logs the forked standard output at the `info` level and the forked standard error at
   * the `error` level. The output is buffered until the process completes, at which point
   * the logger flushes it (to the screen, for example).
   */
  final class BufferedOutput private (val logger: Logger) extends OutputStrategy with Serializable {
    override def equals(o: Any): Boolean = o match {
      case x: BufferedOutput => (this.logger == x.logger)
      case _                 => false
    }
    override def hashCode: Int = {
      37 * (17 + logger.##) + "BufferedOutput".##
    }
    override def toString: String = {
      "BufferedOutput(" + logger + ")"
    }
    protected[this] def copy(logger: Logger = logger): BufferedOutput = {
      new BufferedOutput(logger)
    }
    def withLogger(logger: Logger): BufferedOutput = {
      copy(logger = logger)
    }
  }
  object BufferedOutput {
    def apply(logger: Logger): BufferedOutput = new BufferedOutput(logger)
  }

  /**
   * Logs the forked standard output at the `info` level and the forked standard error at
   * the `error` level.
   */
  final class LoggedOutput private (val logger: Logger) extends OutputStrategy with Serializable {
    override def equals(o: Any): Boolean = o match {
      case x: LoggedOutput => (this.logger == x.logger)
      case _               => false
    }
    override def hashCode: Int = {
      37 * (17 + logger.##) + "LoggedOutput".##
    }
    override def toString: String = {
      "LoggedOutput(" + logger + ")"
    }
    protected[this] def copy(logger: Logger = logger): LoggedOutput = {
      new LoggedOutput(logger)
    }
    def withLogger(logger: Logger): LoggedOutput = {
      copy(logger = logger)
    }
  }
  object LoggedOutput {
    def apply(logger: Logger): LoggedOutput = new LoggedOutput(logger)
  }

  /**
   * Configures the forked standard output to be sent to `output` and the forked standard error
   * to be sent to the standard error of this process.
   */
  final class CustomOutput private (val output: OutputStream)
      extends OutputStrategy
      with Serializable {
    override def equals(o: Any): Boolean = o match {
      case x: CustomOutput => (this.output == x.output)
      case _               => false
    }
    override def hashCode: Int = {
      37 * (17 + output.##) + "CustomOutput".##
    }
    override def toString: String = {
      "CustomOutput(" + output + ")"
    }
    protected[this] def copy(output: OutputStream = output): CustomOutput = {
      new CustomOutput(output)
    }
    def withOutput(output: OutputStream): CustomOutput = {
      copy(output = output)
    }
  }
  object CustomOutput {
    def apply(output: OutputStream): CustomOutput = new CustomOutput(output)
  }
}
