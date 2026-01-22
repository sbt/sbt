package example

import org.scalatest.flatspec.AnyFlatSpec

class WorldTest extends AnyFlatSpec {
  "World" should "say world" in {
    assert("world" == "world")
  }
}
