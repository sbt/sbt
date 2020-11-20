package tests

import org.scalatest.FreeSpec

class PassingTest extends FreeSpec {
  "test message" in {
    assert(main.Main.message == "Hello World!")
  }
}