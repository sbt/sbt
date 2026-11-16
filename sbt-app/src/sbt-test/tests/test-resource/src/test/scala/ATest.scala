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

  test("hello.txt via getResource.openStream should contain hello"):
    val url = getClass.getClassLoader.getResource("hello.txt")
    Using.resource(url.openStream()): in =>
      val actual = String(in.readAllBytes()).trim
      assert(actual == "hello", s"actual: $actual")

  test("resource URL should keep the .jar name"):
    val url = getClass.getClassLoader.getResource("hello.txt").toString
    assert(url.endsWith(".jar!/hello.txt"), url)
end ATest
