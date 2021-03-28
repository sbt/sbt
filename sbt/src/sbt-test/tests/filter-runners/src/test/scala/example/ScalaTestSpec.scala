package example

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ScalaTestSpec extends AnyFlatSpec with Matchers {
  "ScalaTest" should "work" in {
    1 shouldBe 1
  }
}
