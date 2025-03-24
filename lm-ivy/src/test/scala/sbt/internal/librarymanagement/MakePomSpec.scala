package sbt.internal.librarymanagement

import sbt.internal.util.ConsoleLogger
import sbt.librarymanagement.MavenRepository
import verify.BasicTestSuite

// http://ant.apache.org/ivy/history/2.3.0/ivyfile/dependency.html
// http://maven.apache.org/enforcer/enforcer-rules/versionRanges.html
object MakePomSpec extends BasicTestSuite {
  // This is a specification to check the Ivy revision number conversion to pom.

  test("1.0 should convert to 1.0") {
    convertTo("1.0", "1.0")
  }

  test("[1.0,2.0] should convert to [1.0,2.0]") {
    convertTo("[1.0,2.0]", "[1.0,2.0]")
  }

  test("[1.0,2.0[ should convert to [1.0,2.0)") {
    convertTo("[1.0,2.0[", "[1.0,2.0)")
  }

  test("]1.0,2.0] should convert to (1.0,2.0]") {
    convertTo("]1.0,2.0]", "(1.0,2.0]")
  }

  test("]1.0,2.0[ should convert to (1.0,2.0)") {
    convertTo("]1.0,2.0[", "(1.0,2.0)")
  }

  test("[1.0,) should convert to [1.0,)") {
    convertTo("[1.0,)", "[1.0,)")
  }

  test("]1.0,) should convert to (1.0,)") {
    convertTo("]1.0,)", "(1.0,)")
  }

  test("(,2.0] should convert to (,2.0]") {
    convertTo("(,2.0]", "(,2.0]")
  }

  test("(,2.0[ should convert to (,2.0)") {
    convertTo("(,2.0[", "(,2.0)")
  }

  test("1.+ should convert to [1,2)") {
    convertTo("1.+", "[1,2)")
  }

  test("1.2.3.4.+ should convert to [1.2.3.4,1.2.3.5)") {
    convertTo("1.2.3.4.+", "[1.2.3.4,1.2.3.5)")
  }

  test("12.31.42.+ should convert to [12.31.42,12.31.43)") {
    convertTo("12.31.42.+", "[12.31.42,12.31.43)")
  }

  test(
    "1.1+ should convert to [1.1,1.2),[1.10,1.20),[1.100,1.200),[1.1000,1.2000),[1.10000,1.20000)"
  ) {
    convertTo("1.1+", "[1.1,1.2),[1.10,1.20),[1.100,1.200),[1.1000,1.2000),[1.10000,1.20000)")
  }

  test("1+ should convert to [1,2),[10,20),[100,200),[1000,2000),[10000,20000)") {
    convertTo("1+", "[1,2),[10,20),[100,200),[1000,2000),[10000,20000)")
  }

  test("+ should convert to [0,)") {
    convertTo("+", "[0,)")
  }

  test("foo+ should convert to foo+") {
    beParsedAsError("foo+")
  }

  test("repository id should not contain maven illegal repo id characters") {
    val repository = mp.mavenRepository(
      MavenRepository(
        """repository-id-\with-/illegal:"<-chars>|?*-others~!@#$%^&`';{}[]=+_,.""",
        "uri"
      )
    )
    assert(
      (repository \ "id").text == "repository-id-with-illegal-chars-others~!@#$%^&`';{}[]=+_,."
    )
  }

  val mp = new MakePom(ConsoleLogger())
  def convertTo(s: String, expected: String): Unit = {
    assert(MakePom.makeDependencyVersion(s) == expected)
  }
  def beParsedAsError(s: String): Unit = {
    intercept[Throwable] {
      MakePom.makeDependencyVersion(s)
      ()
    }
  }
}
