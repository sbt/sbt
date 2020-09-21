package foo

import org.scalatest.FreeSpec

class FailingTest extends FreeSpec {
  "it should fail" in {
    throw new Exception("Test failed")
  }
}