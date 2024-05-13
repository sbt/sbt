package sbttest

import java.net.URI
import sbt.librarymanagement._
import sbt.librarymanagement.syntax._
import verify.BasicTestSuite

class ResolverSpec extends BasicTestSuite {
  test("Resolver.url") {
    Resolver.url("Test Repo", new URI("http://example.com/").toURL)(Resolver.ivyStylePatterns)
    ()
  }

  test("at") {
    "something" at "http://example.com"
    ()
  }
}
