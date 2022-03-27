package foo.main

object Foo {
  val numbers = Seq(1, 2, 3)
}

object Run {
  def main(args: Array[String]) = {
    assert(Foo.numbers.length == 3)
  }
}
