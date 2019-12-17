package example

import org.scalatest._

class HelloSpec extends FlatSpec with Matchers {
  "hello" should "say hello" in {
    assert("hello" == "hello")
  }
}
