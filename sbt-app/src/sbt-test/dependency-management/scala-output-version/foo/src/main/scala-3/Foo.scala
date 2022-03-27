package foo.main

class MyException extends Exception("MyException")

@annotation.experimental
object Exceptional:
  import language.experimental.saferExceptions
  def foo(): Unit throws MyException = // this requires at least 3.1.x to compile
    throw new MyException

object Foo:
  val numbers = Seq(1, 2, 3)

@main def run() =
  val canEqualMethods = classOf[CanEqual.type].getMethods.toList
  assert( canEqualMethods.exists(_.getName == "canEqualSeq")) // since 3.0.x
  assert(!canEqualMethods.exists(_.getName == "canEqualSeqs")) // since 3.1.x
