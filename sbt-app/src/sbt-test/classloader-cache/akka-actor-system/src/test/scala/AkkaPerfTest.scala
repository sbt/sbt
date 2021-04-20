package sbt.scripted

import utest._

object AkkaPerfTest extends TestSuite {
  val tests: Tests = Tests {
    'run - {
      AkkaTest.main(Array.empty[String])
      1 ==> 1
    }
  }
}
