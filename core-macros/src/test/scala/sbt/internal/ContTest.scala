package sbt.internal

import sbt.internal.util.appmacro.*
import verify.*
import ContTestMacro.*
import sbt.util.Applicative

object ContTest extends BasicTestSuite:
  test("pure") {
    given Applicative[List] = sbt.util.ListInstances.listMonad
    val actual = contMapNMacro[List, Int](12)
    assert(actual == List(12))
  }

  test("getMap") {
    given Applicative[List] = sbt.util.ListInstances.listMonad
    val actual = contMapNMacro[List, Int](ContTest.wrapInit(List(1)) + 2)
    assert(actual == List(3))
  }

  test("getMapN") {
    given Applicative[List] = sbt.util.ListInstances.listMonad
    val actual = contMapNMacro[List, Int](
      ContTest.wrapInit(List(1))
        + ContTest.wrapInit(List(2)) + 3
    )
    assert(actual == List(6))
  }

  test("getMapN2") {
    given Applicative[List] = sbt.util.ListInstances.listMonad
    val actual = contMapNMacro[List, Int]({
      val x = ContTest.wrapInit(List(1))
      val y = ContTest.wrapInit(List(2))
      x + y + 3
    })
    assert(actual == List(6))
  }

  // This compiles away
  def wrapInit[A](a: List[A]): A = ???
end ContTest
