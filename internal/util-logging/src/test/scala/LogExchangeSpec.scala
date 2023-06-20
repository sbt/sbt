/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.util

import sbt.internal.util._

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class LogExchangeSpec extends AnyFlatSpec with Matchers {
  import LogExchange._

  checkTypeTag("stringTypeTagThrowable", stringTypeTagThrowable, StringTypeTag.fast[Throwable])
  checkTypeTag(
    "stringTypeTagTraceEvent",
    stringTypeTagTraceEvent,
    StringTypeTag.fast[TraceEvent]
  )
  checkTypeTag(
    "stringTypeTagSuccessEvent",
    stringTypeTagSuccessEvent,
    StringTypeTag.fast[SuccessEvent]
  )

  private def checkTypeTag[A](name: String, inc: StringTypeTag[A], exp: StringTypeTag[A]): Unit =
    s"LogExchange.$name" should s"match real StringTypeTag[$exp]" in {
      val StringTypeTag(incomingString) = inc
      val StringTypeTag(expectedString) = exp
      if ((incomingString startsWith "scala.") || (expectedString startsWith "scala.")) {
        // > historically [Scala] has been inconsistent whether `scala.` is included, or not
        // > would it be hard to make the test accept either result?
        // https://github.com/scala/community-builds/pull/758#issuecomment-409760633
        assert((incomingString stripPrefix "scala.") == (expectedString stripPrefix "scala."))
      } else {
        assert(incomingString == expectedString)
      }
    }
}
