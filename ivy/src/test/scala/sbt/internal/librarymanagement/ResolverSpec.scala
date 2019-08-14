package sbttest

import java.net.URL
import sbt.librarymanagement._
import sbt.librarymanagement.syntax._
import verify.BasicTestSuite

class ResolverSpec extends BasicTestSuite {
  test("Resolver.url") {
    Resolver.url("Test Repo", new URL("http://example.com/"))(Resolver.ivyStylePatterns)
    ()
  }

  test("at") {
    "something" at "http://example.com"
    ()
  }
}
