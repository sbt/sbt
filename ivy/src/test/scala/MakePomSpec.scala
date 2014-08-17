package sbt

import java.io.File
import org.specs2._

// http://ant.apache.org/ivy/history/2.3.0/ivyfile/dependency.html
// http://maven.apache.org/enforcer/enforcer-rules/versionRanges.html
class MakePomSpec extends Specification {
  def is = s2"""

  This is a specification to check the Ivy revision number conversion to pom.

  1.0 should
    ${convertTo("1.0", "1.0")}

  [1.0,2.0] should
    ${convertTo("[1.0,2.0]", "[1.0,2.0]")} 

  [1.0,2.0[ should
    ${convertTo("[1.0,2.0[", "[1.0,2.0)")}

  ]1.0,2.0] should
    ${convertTo("]1.0,2.0]", "(1.0,2.0]")}

  ]1.0,2.0[ should
    ${convertTo("]1.0,2.0[", "(1.0,2.0)")}

  [1.0,) should
    ${convertTo("[1.0,)", "[1.0,)")}

  ]1.0,) should
    ${convertTo("]1.0,)", "(1.0,)")}

  (,2.0] should
    ${convertTo("(,2.0]", "(,2.0]")}

  (,2.0[ should
    ${convertTo("(,2.0[", "(,2.0)")}

  1.+ should
    ${convertTo("1.+", "[1,2)")}

  1.2.3.4.+ should
    ${convertTo("1.2.3.4.+", "[1.2.3.4,1.2.3.5)")}

  12.31.42.+ should
    ${convertTo("12.31.42.+", "[12.31.42,12.31.43)")}

  1.1+ should
    ${convertTo("1.1+", "[1,2)")}

  1+ should
    ${convertTo("1+", "[0,)")}
                                                                """

  val mp = new MakePom(ConsoleLogger())
  def convertTo(s: String, expected: String) =
    mp.makeDependencyVersion(s) must_== expected
}

