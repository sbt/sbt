package sbttest

import java.net.URI
import sbt.librarymanagement.*
import sbt.librarymanagement.syntax.*
import verify.BasicTestSuite

class ResolverSpec extends BasicTestSuite {
  test("Resolver.url") {
    Resolver.url("Test Repo", new URI("http://example.com/").toURL)(using Resolver.ivyStylePatterns)
    ()
  }

  test("at") {
    "something" at "http://example.com"
    ()
  }
}
