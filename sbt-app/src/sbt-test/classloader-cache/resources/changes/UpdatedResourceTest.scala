package scripted

import org.scalatest.FlatSpec

class ResourceTest extends FlatSpec {
  "test resources" should "load" in {
    Main.main(Array("bar.txt", "updated-test"))
  }
}
