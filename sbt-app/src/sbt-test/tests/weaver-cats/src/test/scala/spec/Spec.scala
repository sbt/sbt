package spec

import weaver._

object Spec extends FunSuite {
  test("test") {
    // expected to fail
    assert(1 == 2)
  }
}
