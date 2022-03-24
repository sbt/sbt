package foo.test

class MyException extends Exception("MyException")

@annotation.experimental
object Exceptional:
  import language.experimental.saferExceptions
  def foo(): Unit throws MyException = // this requires at least 3.1.x to compile
    throw new MyException


class FooTest extends munit.FunSuite:
  test("foo") {
    assertEquals(foo.main.Foo.numbers, Seq(1, 2, 3))

    val canEqualMethods = classOf[CanEqual.type].getMethods.toList
    assert( canEqualMethods.exists(_.getName == "canEqualSeq")) // since 3.0.x
    assert(!canEqualMethods.exists(_.getName == "canEqualSeqs")) // since 3.1.x
  }
