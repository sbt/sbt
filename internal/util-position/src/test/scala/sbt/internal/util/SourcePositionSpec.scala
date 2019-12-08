package sbt.internal.util

import org.scalatest._

class SourcePositionSpec extends FlatSpec {
  "SourcePosition()" should "return a sane SourcePosition" in {
    val filename = "SourcePositionSpec.scala"
    val lineNumber = 9
    SourcePosition.fromEnclosing() match {
      case LinePosition(path, startLine) => assert(path === filename && startLine === lineNumber)
      case RangePosition(path, range)    => assert(path === filename && inRange(range, lineNumber))
      case NoPosition                    => fail("No source position found")
    }
  }

  private def inRange(range: LineRange, lineNo: Int) =
    range.start until range.end contains lineNo
}
