import org.scalatest.funsuite.AnyFunSuite

class FailSpec extends AnyFunSuite:
  test("this test should fail") {
    assert(1 == 2)
  }
