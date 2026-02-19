/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal

import java.util.function.Supplier
import sbt.util.{ Level, Logger }

/**
 * Wraps a Logger and prefixes info and debug messages with a tag (e.g. project id).
 * Used so that incremental compiler output (e.g. "compiling X sources", invalidations)
 * can be attributed to a project in multi-project builds (fixes #408).
 */
private[sbt] object CompileDebugLogger {
  def apply(prefix: String, delegate: Logger): Logger =
    new Logger {
      private def prefixed(msg: String): String =
        if (msg == null || msg.isEmpty) s"[$prefix]"
        else s"[$prefix] $msg"

      override def log(level: Level.Value, message: => String): Unit =
        if (level == Level.Debug || level == Level.Info)
          delegate.log(level, prefixed(message))
        else delegate.log(level, message)

      override def log(level: Level.Value, msg: Supplier[String]): Unit =
        if (level == Level.Debug || level == Level.Info)
          delegate.log(level, () => prefixed(msg.get()))
        else delegate.log(level, msg)

      def trace(t: => Throwable): Unit = delegate.trace(t)
      def success(message: => String): Unit = delegate.success(message)
    }
}
