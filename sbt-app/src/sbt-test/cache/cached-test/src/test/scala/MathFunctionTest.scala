package example

object MathFunctionTest extends verify.BasicTestSuite:
  test("times2 should double the input"):
    assert(MathFunction.times2(4) == 8)
end MathFunctionTest
