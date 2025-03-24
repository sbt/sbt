/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal.util.codec

import sbt.util.ShowLines
import sbt.internal.util.{ StackTrace, TraceEvent }

trait ThrowableShowLines {
  given sbtThrowableShowLines: ShowLines[Throwable] =
    ShowLines[Throwable]((t: Throwable) => {
      // 0 means enabled with default behavior. See StackTrace.scala.
      val traceLevel = 0
      List(StackTrace.trimmed(t, traceLevel))
    })
}

object ThrowableShowLines extends ThrowableShowLines

trait TraceEventShowLines {
  given sbtTraceEventShowLines: ShowLines[TraceEvent] =
    ShowLines[TraceEvent]((t: TraceEvent) => {
      ThrowableShowLines.sbtThrowableShowLines.showLines(t.message)
    })
}

object TraceEventShowLines extends TraceEventShowLines
