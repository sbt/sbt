package sbt.util

import sbt.internal.util._

import org.scalatest._

class LogExchangeSpec extends FlatSpec with Matchers {
  import LogExchange._

  checkTypeTag("stringTypeTagThrowable", stringTypeTagThrowable, StringTypeTag[Throwable])
  checkTypeTag("stringTypeTagTraceEvent", stringTypeTagTraceEvent, StringTypeTag[TraceEvent])
  checkTypeTag("stringTypeTagSuccessEvent", stringTypeTagSuccessEvent, StringTypeTag[SuccessEvent])

  private def checkTypeTag[A, B](name: String, actual: A, expected: B): Unit =
    s"LogExchange.$name" should s"match real StringTypeTag[$expected]" in assert(actual == expected)
}
