import org.scalatest.funsuite.AnyFunSuite

class SBT543 extends AnyFunSuite {
  class MyCustomException(message: String) extends RuntimeException(message)
  test("throws a custom exception") {
    throw new MyCustomException("this is a custom exception")
  }
}
