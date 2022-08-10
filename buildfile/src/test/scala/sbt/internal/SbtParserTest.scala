package sbt.internal

import sbt.internal.parser.SbtParser
import sbt.internal.util.LineRange
import xsbti.VirtualFileRef

object SbtParserTest extends verify.BasicTestSuite:
  lazy val testCode: String = """import keys.*
import com.{
  keys
}

val x = 1
lazy val foo = project
  .settings(x := y)
"""

  test("imports with their lines") {
    val ref = VirtualFileRef.of("vfile")
    val p = SbtParser(ref, testCode.linesIterator.toList)
    assert(
      p.imports == List(
        "import keys.*" -> 1,
        """import com.{
  keys
}""" -> 2
      )
    )
  }

  test("imports with their lines2") {
    val ref = VirtualFileRef.of("vfile")
    val p = SbtParser(ref, testCode.linesIterator.toList)
    assert(p.settings.size == 2)
    assert(p.settings(0) == ("""val x = 1""" -> LineRange(6, 6)))
    assert(p.settings(1) == ("""lazy val foo = project
  .settings(x := y)""" -> LineRange(7, 8)))
  }
end SbtParserTest
