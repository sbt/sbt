package sbt.internal

import sbt.internal.util.appmacro.*
import verify.*
import ContTestMacro.*
import sbt.util.Applicative
import sjsonnew.BasicJsonProtocol

object ContTest extends BasicTestSuite:
  test("pure") {
    given Applicative[List] = sbt.util.ListInstances.listMonad
    import BasicJsonProtocol.given
    val actual = contMapNMacro[List, Int](12)
    assert(actual == List(12))
    println(ContTestMacro.inMemoryCache.toString())
  }

  test("getMap") {
    given Applicative[List] = sbt.util.ListInstances.listMonad
    import BasicJsonProtocol.given
    val actual = contMapNMacro[List, Int](ContTest.wrapInit(List(1)) + 2)
    assert(actual == List(3))
    println(ContTestMacro.inMemoryCache.toString())
    val actual2 = contMapNMacro[List, Int](ContTest.wrapInit(List(1)) + 2)
    assert(actual2 == List(3))
  }

  test("getMapN") {
    given Applicative[List] = sbt.util.ListInstances.listMonad
    import BasicJsonProtocol.given
    val actual = contMapNMacro[List, Int](
      ContTest.wrapInit(List(1))
        + ContTest.wrapInit(List(2)) + 3
    )
    assert(actual == List(6))
    println(ContTestMacro.inMemoryCache.toString())
    val actual2 = contMapNMacro[List, Int](
      ContTest.wrapInit(List(1))
        + ContTest.wrapInit(List(2)) + 4
    )
    assert(actual2 == List(7))
  }

  test("getMapN2") {
    given Applicative[List] = sbt.util.ListInstances.listMonad
    import BasicJsonProtocol.given
    val actual = contMapNMacro[List, Int]({
      val x = ContTest.wrapInit(List(1))
      val y = ContTest.wrapInit(List(2))
      x + y + 3
    })
    assert(actual == List(6))
    println(ContTestMacro.inMemoryCache.toString())
  }

  // This compiles away
  def wrapInit[A](a: List[A]): A = ???
end ContTest
