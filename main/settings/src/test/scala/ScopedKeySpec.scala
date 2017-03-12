package sbt

import org.specs2._
import Scope.{ parseScopedKey, GlobalScope, ThisScope }
import java.net.URI

/**
  * http://www.scala-sbt.org/0.13/tutorial/Scopes.html
  */
class ScopedKeySpec extends Specification {
  def is =
    s2"""

  This is a specification to check the scoped key parsing.

  fullClasspath should
    ${beParsedAs("fullClasspath", ThisScope, "fullClasspath")}

  test:fullClasspath should
    ${beParsedAs("test:fullClasspath", ThisScope in ConfigKey("test"), "fullClasspath")}

  *:fullClasspath
    ${beParsedAs("*:fullClasspath", GlobalScope, "fullClasspath")}

  aea33a/test:fullClasspath   
    ${beParsedAs(
      "aea33a/test:fullClasspath",
      ThisScope in (LocalProject("aea33a"), ConfigKey("test")),
      "fullClasspath"
    )}

  doc::fullClasspath
    ${beParsedAs("doc::fullClasspath", ThisScope in AttributeKey("doc"), "fullClasspath")}

  {file:/hello/}aea33a/test:fullClasspath
    ${beParsedAs(
      "{file:/hello/}aea33a/test:fullClasspath",
      ThisScope in (ProjectRef(new URI("file:/hello/"), "aea33a"), ConfigKey("test")),
      "fullClasspath"
    )}

  {file:/hello/}/test:fullClasspath
    ${beParsedAs(
      "{file:/hello/}/test:fullClasspath",
      ThisScope in (BuildRef(new URI("file:/hello/")), ConfigKey("test")),
      "fullClasspath"
    )}

  {.}/test:fullClasspath
    ${beParsedAs("{.}/test:fullClasspath", ThisScope in (ThisBuild, ConfigKey("test")), "fullClasspath")}

  {file:/hello/}/compile:doc::fullClasspath
    ${beParsedAs(
      "{file:/hello/}/compile:doc::fullClasspath",
      ThisScope in (BuildRef(new URI("file:/hello/")), ConfigKey("compile"), AttributeKey("doc")),
      "fullClasspath"
    )}
                                                                """

  def beParsedAs(cmd: String, scope0: Scope, key0: String) = {
    val (scope, key) = parseScopedKey(cmd)
    (scope must_== scope0) and (key must_== key0)
  }
}
