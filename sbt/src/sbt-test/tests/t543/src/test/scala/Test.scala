import org.scalatest.FunSuite

class SBT543 extends FunSuite {
  class MyCustomException(message: String) extends RuntimeException(message)
  test("throws a custom excpetion") {
    throw new MyCustomException("this is a custom exception")
  }
}
