object A {
  def main(args: Array[String]): Unit = {
    if (args(0).toBoolean) () else sys.error("Fail")
  }
}
