import org.scalatest.funsuite.AnyFunSuite

class Test extends AnyFunSuite:
  test("bad") {
    sys.error("boom")
  }
end Test
