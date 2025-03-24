import org.scalatest.FlatSpec

class MathFunctionTest extends FlatSpec {
  "times2" should "double the input" in {
    assert(MathFunction.times2(4) == 8)
  }
}
