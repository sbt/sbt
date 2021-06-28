package sbt

import utest._

object UtestTest extends TestSuite {
  val tests: Tests = Tests {
    'foo - {
      1 ==> 1
    }
  }
}
