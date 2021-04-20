package sbt

import org.scalatest.{ FlatSpec, Matchers }

class ScalatestTest extends FlatSpec with Matchers {
  "scalatest" should "fail" in {
    1 shouldBe 2
  }
}
