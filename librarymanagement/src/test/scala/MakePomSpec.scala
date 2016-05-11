package sbt.internal.librarymanagement

import java.io.File
import sbt.util.Logger
import sbt.internal.util.ConsoleLogger

// http://ant.apache.org/ivy/history/2.3.0/ivyfile/dependency.html
// http://maven.apache.org/enforcer/enforcer-rules/versionRanges.html
class MakePomSpec extends UnitSpec {
  // This is a specification to check the Ivy revision number conversion to pom.

  "1.0" should "convert to 1.0" in convertTo("1.0", "1.0")

  "[1.0,2.0]" should "convert to [1.0,2.0]" in {
    convertTo("[1.0,2.0]", "[1.0,2.0]")
  }

  "[1.0,2.0[" should "convert to [1.0,2.0)" in {
    convertTo("[1.0,2.0[", "[1.0,2.0)")
  }

  "]1.0,2.0]" should "convert to (1.0,2.0]" in {
    convertTo("]1.0,2.0]", "(1.0,2.0]")
  }

  "]1.0,2.0[" should "convert to (1.0,2.0)" in {
    convertTo("]1.0,2.0[", "(1.0,2.0)")
  }

  "[1.0,)" should "convert to [1.0,)" in {
    convertTo("[1.0,)", "[1.0,)")
  }

  "]1.0,)" should "convert to (1.0,)" in {
    convertTo("]1.0,)", "(1.0,)")
  }

  "(,2.0]" should "convert to (,2.0]" in {
    convertTo("(,2.0]", "(,2.0]")
  }

  "(,2.0[" should "convert to (,2.0)" in {
    convertTo("(,2.0[", "(,2.0)")
  }

  "1.+" should "convert to [1,2)" in {
    convertTo("1.+", "[1,2)")
  }

  "1.2.3.4.+" should "convert to [1.2.3.4,1.2.3.5)" in {
    convertTo("1.2.3.4.+", "[1.2.3.4,1.2.3.5)")
  }

  "12.31.42.+" should "convert to [12.31.42,12.31.43)" in {
    convertTo("12.31.42.+", "[12.31.42,12.31.43)")
  }

  "1.1+" should "convert to [1.1,1.2),[1.10,1.20),[1.100,1.200),[1.1000,1.2000),[1.10000,1.20000)" in {
    convertTo("1.1+", "[1.1,1.2),[1.10,1.20),[1.100,1.200),[1.1000,1.2000),[1.10000,1.20000)")
  }

  "1+" should "convert to [1,2),[10,20),[100,200),[1000,2000),[10000,20000)" in {
    convertTo("1+", "[1,2),[10,20),[100,200),[1000,2000),[10000,20000)")
  }

  "+" should "convert to [0,)" in convertTo("+", "[0,)")

  "foo+" should "convert to foo+" in beParsedAsError("foo+")

  val mp = new MakePom(ConsoleLogger())
  def convertTo(s: String, expected: String): Unit =
    MakePom.makeDependencyVersion(s) shouldBe expected
  def beParsedAsError(s: String): Unit = {
    intercept[Throwable] {
      MakePom.makeDependencyVersion(s)
    }
    ()
  }
}

