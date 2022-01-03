package sbt.internal

import verify.BasicTestSuite

object AListTest extends BasicTestSuite:
  val t1 = ((Option(1), Option("foo")))

  test("mapN") {
    import sbt.internal.util.AList
    val tuple = t1
    val f = (arg: (Int, String)) => arg._1.toString + "|" + arg._2
    val actual = AList.tuple[(Int, String)].mapN[Option, String](tuple)(f)
    assert(actual == Option("1|foo"))
  }
end AListTest
