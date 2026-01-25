package example

@main
def hello(args: String*): Unit =
  println("hello")
  assert(args(0).toInt == A.x)
