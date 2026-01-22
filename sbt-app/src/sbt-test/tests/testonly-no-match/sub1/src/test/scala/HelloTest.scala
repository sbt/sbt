package example

import org.scalatest.flatspec.AnyFlatSpec

class HelloTest extends AnyFlatSpec {
  "Hello" should "say hello" in {
    assert("hello" == "hello")
  }
}
