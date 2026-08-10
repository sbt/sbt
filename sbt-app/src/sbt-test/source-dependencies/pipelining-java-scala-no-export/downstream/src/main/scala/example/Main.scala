package example

object Main {
  def run(): String = Greeter.greet("world")

  def main(args: Array[String]): Unit =
    assert(run() == "Updated, world")
}
