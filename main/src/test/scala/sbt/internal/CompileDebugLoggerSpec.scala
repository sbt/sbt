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

  test("CompileDebugLogger prefixes debug and info messages with project id (#408)") {
    val messages = new CopyOnWriteArrayList[(Level.Value, String)]()
    val delegate: Logger = new Logger {
      def log(level: Level.Value, message: => String): Unit =
        if (level == Level.Debug || level == Level.Info) messages.add((level, message))
      def trace(t: => Throwable): Unit = ()
      def success(message: => String): Unit = ()
    }
    val prefixed = CompileDebugLogger("myProject", delegate)
    prefixed.debug("Initial source changes: ")
    prefixed.debug("removed:Set()")
    prefixed.info("compiling 1 Scala source to ...")
    assert(messages.size() >= 3)
    assert(messages.get(0) == (Level.Debug, "[myProject] Initial source changes: "))
    assert(messages.get(1) == (Level.Debug, "[myProject] removed:Set()"))
    assert(messages.get(2) == (Level.Info, "[myProject] compiling 1 Scala source to ..."))
  }

  test("CompileDebugLogger does not prefix warn/error") {
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
    val prefixedLevels = list.filter { case (_, msg) => msg.startsWith("[p] ") }.map(_._1)
    val notPrefixed = list.filter { case (_, msg) => !msg.startsWith("[p] ") }
    assert(prefixedLevels.forall(l => l == Level.Debug || l == Level.Info))
    assert(notPrefixed.map(_._1).forall(l => l == Level.Warn || l == Level.Error))
  }
}
