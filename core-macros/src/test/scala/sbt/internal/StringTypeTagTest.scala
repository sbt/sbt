package sbt.internal

import sbt.internal.util.appmacro.*
import verify.*

object StringTypeTagTest extends BasicTestSuite:
  test("String") {
    assert(StringTypeTag[String].toString == "java.lang.String")
  }

  test("Int") {
    assert(StringTypeTag[Int].toString == "scala.Int")
  }

  test("List[Int]") {
    assert(StringTypeTag[List[Int]].toString == "scala.collection.immutable.List[scala.Int]")
  }
end StringTypeTagTest
