package sbttest

import java.net.URI
import sbt.librarymanagement.*
import sbt.librarymanagement.syntax.*
import verify.BasicTestSuite

class ResolverSpec extends BasicTestSuite {
  test("Resolver.uri") {
    Resolver.uri("Test Repo", new URI("http://example.com/"))(using Resolver.ivyStylePatterns)
    ()
  }

  test("at") {
    "something" at "http://example.com"
    ()
  }
}
