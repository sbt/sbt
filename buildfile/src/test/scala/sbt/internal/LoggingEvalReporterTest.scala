/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import java.nio.file.{ Path, Paths }
import scala.collection.mutable.ListBuffer
import scala.io.Source
import scala.util.Using

import sbt.util.Level
import sbt.util.Logger

object LoggingEvalReporterTest extends verify.BasicTestSuite:
  private def stripAnsi(s: String): String = s.replaceAll("\\[[0-9;]*m", "")

  private lazy val classpath: Seq[Path] =
    val resource = Option(getClass.getResourceAsStream("/sbt-eval-classpath.txt"))
      .getOrElse(sys.error("Missing generated sbt evaluator classpath"))
    Using.resource(Source.fromInputStream(resource, "UTF-8")):
      _.getLines().map(Paths.get(_)).toVector

  private class RecordingLogger extends Logger:
    val messages = ListBuffer.empty[(Level.Value, String)]
    def log(level: Level.Value, message: => String): Unit = messages += ((level, message))
    def success(message: => String): Unit = ()
    def trace(t: => Throwable): Unit = ()

  test("forwards compile errors to the logger") {
    val log = new RecordingLogger
    val eval = new Eval(Nil, classpath, None, Some(() => EvalReporter.logging(log)))
    intercept[EvalException] {
      eval.evalInfer("\"\".undefined")
    }
    val errors = log.messages.collect { case (Level.Error, m) => stripAnsi(m) }
    assert(errors.exists(_.contains("undefined is not a member of String")))
  }
end LoggingEvalReporterTest
