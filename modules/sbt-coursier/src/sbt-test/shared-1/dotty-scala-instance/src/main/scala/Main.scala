
object Main {

  def main(args: Array[String]): Unit = {

    runExample("Trait Params")(TraitParams.test)

  }

  private def runExample(name: String)(f: => Unit) = {
    println(Console.MAGENTA + s"$name example:" + Console.RESET)
    f
    println()
  }

}
