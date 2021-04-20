package sbt

object TestMain {
  def main(args: Array[String]) {
    println(transitive.Transitive.x)
  }
}
