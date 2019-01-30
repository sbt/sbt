package sbt

import utest._

object SnapshotTest extends TestSuite {
  val tests: Tests = Tests {
    'foo - {
      com.swoval.Foo.x ==> 1
    }
  }
}
