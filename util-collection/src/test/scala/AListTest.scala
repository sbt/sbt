package sbt.internal

import verify.BasicTestSuite
import sbt.internal.util.AList

object AListTest extends BasicTestSuite:
  val t1 = ((Option(1), Option("foo")))

  test("tuple.mapN") {
    val tuple = t1
    val f = (arg: (Int, String)) => arg._1.toString + "|" + arg._2
    val actual = AList.tuple[(Int, String)].mapN[Option, String](tuple)(f)
    assert(actual == Option("1|foo"))
  }

  test("list.mapN") {
    val list = List(Option(1), Option(2), Option(3))
    val f = (arg: List[Int]) => arg.mkString("|")
    val actual = AList.list[Int].mapN[Option, String](list)(f)
    assert(actual == Some("1|2|3"))
  }
end AListTest
