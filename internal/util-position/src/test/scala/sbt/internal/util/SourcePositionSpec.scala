/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util

import org.scalatest.flatspec.AnyFlatSpec

class SourcePositionSpec extends AnyFlatSpec {
  "SourcePosition()" should "return a sane SourcePosition" in {
    val filename = "SourcePositionSpec.scala"
    val lineNumber = 16
    SourcePosition.fromEnclosing() match {
      case LinePosition(path, startLine) => assert(path === filename && startLine === lineNumber)
      case RangePosition(path, range)    => assert(path === filename && inRange(range, lineNumber))
      case NoPosition                    => fail("No source position found")
    }
  }

  private def inRange(range: LineRange, lineNo: Int) =
    range.start until range.end contains lineNo
}
