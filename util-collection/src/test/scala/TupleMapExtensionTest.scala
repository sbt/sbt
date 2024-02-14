package sbt.internal.util

import verify.BasicTestSuite

object TupleMapExtensionTest extends BasicTestSuite:
  val tuple: Tuple.Map[(Int, String), Option] = ((Option(1), Option("foo")))

  test("tuple.mapN") {
    val f = (arg: (Int, String)) => arg._1.toString + "|" + arg._2
    val actual = TupleMapExtension.mapN[(Int, String), Option](tuple)(f)
    assert(actual == Option("1|foo"))
  }
