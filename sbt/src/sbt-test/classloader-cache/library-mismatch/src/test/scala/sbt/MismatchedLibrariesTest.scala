package sbt

import utest._

object MismatchedLibrariesTest extends TestSuite {
  val tests: Tests = Tests {
    'check - {
      assert(foo.Foo.y == 3)
    }
  }
}
