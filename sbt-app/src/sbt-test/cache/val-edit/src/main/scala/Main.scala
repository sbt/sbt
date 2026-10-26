package example

object Old:
  @deprecated("use something else", "1.0")
  def greeting: String = "hello"

@main def main(): Unit =
  println(Old.greeting)
