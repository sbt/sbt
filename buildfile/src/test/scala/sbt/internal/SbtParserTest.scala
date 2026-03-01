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

  test("comma separated imports") {
    val ref = VirtualFileRef.of("vfile")
    val code = """import scala.util, util.Random

def f = Random.nextInt()
"""
    val p = SbtParser(ref, code.linesIterator.toList)
    assert(p.imports.size == 2)
    assert(p.imports(0)._1 == "import scala.util")
    assert(p.imports(1)._1 == "import util.Random")
    assert(p.settings.size == 1)
  }

  test("comma separated imports with three entries") {
    val ref = VirtualFileRef.of("vfile")
    val code = """import scala.util, util.Random, util.Properties

val x = 1
"""
    val p = SbtParser(ref, code.linesIterator.toList)
    assert(p.imports.size == 3)
    assert(p.imports(0)._1 == "import scala.util")
    assert(p.imports(1)._1 == "import util.Random")
    assert(p.imports(2)._1 == "import util.Properties")
  }

  test("comma separated imports with wildcard") {
    val ref = VirtualFileRef.of("vfile")
    val code = """import scala.util, util.*

val x = 1
"""
    val p = SbtParser(ref, code.linesIterator.toList)
    assert(p.imports.size == 2)
    assert(p.imports(0)._1 == "import scala.util")
    assert(p.imports(1)._1 == "import util.*")
  }

  test("comma separated imports with rename") {
    val ref = VirtualFileRef.of("vfile")
    val code = """import scala.util, util.{Random => Rng}

val x = 1
"""
    val p = SbtParser(ref, code.linesIterator.toList)
    assert(p.imports.size == 2)
    assert(p.imports(0)._1 == "import scala.util")
    assert(p.imports(1)._1 == "import util.{Random => Rng}")
  }

  test("comma separated imports with multiple selectors") {
    val ref = VirtualFileRef.of("vfile")
    val code = """import scala.util, util.{Random, Properties}

val x = 1
"""
    val p = SbtParser(ref, code.linesIterator.toList)
    assert(p.imports.size == 2)
    assert(p.imports(0)._1 == "import scala.util")
    assert(p.imports(1)._1 == "import util.{Random, Properties}")
  }

  test("isIdentifier") {
    assert(SbtParser.isIdentifier("1a") == false)
  }
end SbtParserTest
