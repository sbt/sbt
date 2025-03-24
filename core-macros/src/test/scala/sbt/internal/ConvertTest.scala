package sbt.internal

import verify.*
import ConvertTestMacro.*

object ConvertTest extends BasicTestSuite:
  test("convert") {
    // assert(someMacro(ConvertTest.wrapInit(1) == 2))
    assert(someMacro(ConvertTest.wrapInit(1).toString == "Some(2)"))
  }

  def wrapInitTask[A](a: A): Int = 2
  def wrapInit[A](a: A): Int = 2
end ConvertTest
