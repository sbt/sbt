package sbttest

import java.net.URL
import org.scalatest._
import sbt.librarymanagement._
import sbt.librarymanagement.syntax._

class ResolverSpec extends FunSuite with DiagrammedAssertions {
  test("Resolver.url") {
    Resolver.url("Test Repo", new URL("http://example.com/"))(Resolver.ivyStylePatterns)
  }

  test("at") {
    "something" at "http://example.com"
  }
}
