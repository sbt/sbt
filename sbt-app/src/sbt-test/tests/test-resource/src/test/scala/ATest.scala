package testpkg

import munit.*
import scala.io.Source
import scala.util.Using

class ATest extends FunSuite:
  test("hello.txt should contain hello") {
    Using.resource(Source.fromResource("hello.txt")): r =>
      val actual = r.mkString.trim
      assert(actual == "hello", s"actual: $actual")
  }
end ATest
