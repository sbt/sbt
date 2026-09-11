package example

object Main:
  def main(args: Array[String]): Unit =
    assert(B.value == 1, s"expected 1, got ${B.value}")
    println("ok")
