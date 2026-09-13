/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import scala.collection.mutable.ListBuffer

import sbt.util.Level
import sbt.util.Logger

object LoggingEvalReporterTest extends verify.BasicTestSuite:
  private def stripAnsi(s: String): String = s.replaceAll("\\[[0-9;]*m", "")

  private class RecordingLogger extends Logger:
    val messages = ListBuffer.empty[(Level.Value, String)]
    def log(level: Level.Value, message: => String): Unit = messages += ((level, message))
    def success(message: => String): Unit = ()
    def trace(t: => Throwable): Unit = ()

  test("forwards compile errors to the logger") {
    val log = new RecordingLogger
    val eval = Eval(() => EvalReporter.logging(log))
    intercept[EvalException] {
      eval.evalInfer("\"\".undefined")
    }
    val errors = log.messages.collect { case (Level.Error, m) => stripAnsi(m) }
    assert(errors.exists(_.contains("undefined is not a member of String")))
  }
