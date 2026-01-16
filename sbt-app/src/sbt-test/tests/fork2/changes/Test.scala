import org.scalatest.funsuite.AnyFunSuite

class Test extends AnyFunSuite:
  val v = sys.env.getOrElse("tests.max.value", Int.MaxValue)
  test("A simple equation should hold") {
    assert(Int.MaxValue == v)
  }
end Test
