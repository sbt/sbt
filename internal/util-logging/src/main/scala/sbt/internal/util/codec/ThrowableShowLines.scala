package sbt
package internal.util.codec

import sbt.util.ShowLines
import sbt.internal.util.{ StackTrace, TraceEvent }

trait ThrowableShowLines {
  implicit val sbtThrowableShowLines: ShowLines[Throwable] =
    ShowLines[Throwable]((t: Throwable) => {
      // 0 means enabled with default behavior. See StackTrace.scala.
      val traceLevel = 0
      List(StackTrace.trimmed(t, traceLevel))
    })
}

object ThrowableShowLines extends ThrowableShowLines

trait TraceEventShowLines {
  implicit val sbtTraceEventShowLines: ShowLines[TraceEvent] =
    ShowLines[TraceEvent]((t: TraceEvent) => {
      ThrowableShowLines.sbtThrowableShowLines.showLines(t.message)
    })
}

object TraceEventShowLines extends TraceEventShowLines
