/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util

import verify.BasicTestSuite

object StackTraceSpec extends BasicTestSuite:
  test("StackTrace.trimmedLines should handle normal exceptions"):
    val exception = new RuntimeException("test exception")
    val lines = StackTrace.trimmedLines(exception, 3)
    assert(lines.nonEmpty)
    assert(lines.head.contains("test exception"))

  test("StackTrace.trimmedLines should handle exceptions with causes"):
    val cause = new RuntimeException("cause exception")
    val exception = new RuntimeException("test exception", cause)
    val lines = StackTrace.trimmedLines(exception, 3)
    assert(lines.exists(_.contains("test exception")))
    assert(lines.exists(_.contains("Caused by:")))
    assert(lines.exists(_.contains("cause exception")))

  test(
    "StackTrace.trimmedLines should handle self-referencing exceptions without StackOverflowError"
  ):
    val exception = new SelfReferencingException("self-referencing exception")
    val lines = StackTrace.trimmedLines(exception, 3)
    assert(lines.nonEmpty)
    assert(lines.head.contains("self-referencing exception"))
    assert(lines.exists(_.contains("[CIRCULAR REFERENCE:")))

  test(
    "StackTrace.trimmedLines should handle circular exception chains without StackOverflowError"
  ):
    val exception1 = new ChainableException("exception 1")
    val exception2 = new ChainableException("exception 2")
    exception1.setCauseException(exception2)
    exception2.setCauseException(exception1)
    val lines = StackTrace.trimmedLines(exception1, 3)
    assert(lines.nonEmpty)
    assert(lines.exists(_.contains("exception 1")))
    assert(lines.exists(_.contains("exception 2")))
    assert(lines.exists(_.contains("[CIRCULAR REFERENCE:")))
end StackTraceSpec

class SelfReferencingException(message: String) extends RuntimeException(message):
  override def getCause: Throwable = this

class ChainableException(message: String) extends RuntimeException(message):
  import scala.compiletime.uninitialized
  private var causeException: Throwable = uninitialized
  def setCauseException(cause: Throwable): Unit = causeException = cause
  override def getCause: Throwable = causeException
