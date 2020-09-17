package foo

import org.scalatest.FreeSpec

class FooTest  extends FreeSpec {
  "test message" in {
    assert(FooMain.message == "Hello World!")
  }
}