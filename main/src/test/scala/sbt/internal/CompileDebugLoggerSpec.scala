/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal

import java.util.concurrent.CopyOnWriteArrayList
import scala.jdk.CollectionConverters.*
import sbt.util.{ Level, Logger }

object CompileDebugLoggerSpec extends verify.BasicTestSuite {

  test("CompileDebugLogger prefixes debug messages with project id (#408)") {
    val debugMessages = new CopyOnWriteArrayList[String]()
    val delegate: Logger = new Logger {
      def log(level: Level.Value, message: => String): Unit =
        if (level == Level.Debug) debugMessages.add(message)
      def trace(t: => Throwable): Unit = ()
      def success(message: => String): Unit = ()
    }
    val prefixed = CompileDebugLogger("myProject", delegate)
    prefixed.debug("Initial source changes: ")
    prefixed.debug("removed:Set()")
    prefixed.info("compile success")
    assert(debugMessages.size() >= 2)
    assert(debugMessages.get(0) == "[myProject] Initial source changes: ")
    assert(debugMessages.get(1) == "[myProject] removed:Set()")
  }

  test("CompileDebugLogger does not prefix info/warn/error") {
    val allMessages = new CopyOnWriteArrayList[(Level.Value, String)]()
    val delegate: Logger = new Logger {
      def log(level: Level.Value, message: => String): Unit =
        allMessages.add((level, message))
      def trace(t: => Throwable): Unit = ()
      def success(message: => String): Unit = ()
    }
    val prefixed = CompileDebugLogger("p", delegate)
    prefixed.debug("debug msg")
    prefixed.info("info msg")
    prefixed.warn("warn msg")
    prefixed.error("error msg")
    val list = allMessages.asScala.toSeq
    val debugOnly = list.filter(_._1 == Level.Debug).map(_._2)
    val nonDebug = list.filter(_._1 != Level.Debug).map(_._2)
    assert(debugOnly.forall(_.startsWith("[p] ")))
    assert(nonDebug.forall(!_.startsWith("[p] ")))
  }
}
