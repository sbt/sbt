/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util

import hedgehog.*
import hedgehog.runner.*

object UtilSpec extends Properties:
  override def tests: List[Test] = List(
    example(
      "quoteIfNotScalaId should not quote simple identifiers",
      assertQuote("foo", "foo"),
    ),
    example(
      "quoteIfNotScalaId should not quote identifiers with underscores",
      assertQuote("foo_bar", "foo_bar"),
    ),
    example(
      "quoteIfNotScalaId should quote identifiers with hyphens",
      assertQuote("bug-report", "`bug-report`"),
    ),
    example(
      "quoteIfNotScalaId should quote identifiers with dots",
      assertQuote("my.project", "`my.project`"),
    ),
    example(
      "quoteIfNotScalaId should quote identifiers starting with digits",
      assertQuote("123abc", "`123abc`"),
    ),
    example(
      "quoteIfNotScalaId should quote Scala keywords",
      assertQuote("class", "`class`"),
    ),
    example(
      "quoteIfNotScalaId should quote empty strings",
      assertQuote("", "``"),
    ),
  )

  private def assertQuote(input: String, expected: String): Result =
    val actual = Util.quoteIfNotScalaId(input)
    Result.assert(actual == expected).log(s"input=$input expected=$expected actual=$actual")
end UtilSpec
