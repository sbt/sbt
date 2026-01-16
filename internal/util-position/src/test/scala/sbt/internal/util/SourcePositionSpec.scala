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

object SourcePositionSpec extends Properties:
  override def tests: List[Test] = List(
    example(
      "SourcePosition() should return a SourcePosition", {
        val filename = "SourcePositionSpec.scala"
        val lineNumber = 19
        SourcePosition.fromEnclosing() match {
          case pos @ LinePosition(path, startLine) =>
            Result.assert(path == filename && startLine == lineNumber).log(pos.toString())
            Result
              .assert(pos.sourceCode == Some("SourcePosition.fromEnclosing()"))
              .log(pos.sourceCode.toString())
          case pos @ RangePosition(path, range) =>
            Result.assert(path == filename && inRange(range, lineNumber)).log(pos.toString())
          case NoPosition => Result.assert(false).log("No source position found")
        }
      }
    )
  )

  private def inRange(range: LineRange, lineNo: Int) =
    range.start until range.end contains lineNo
end SourcePositionSpec
