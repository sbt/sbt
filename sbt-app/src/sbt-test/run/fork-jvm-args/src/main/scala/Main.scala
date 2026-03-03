object Main {
  def main(args: Array[String]): Unit = {
    val prop = System.getProperty("test.prop")
    assert(prop == "hello", s"Expected system property test.prop=hello but got '$prop'")
    assert(args.toList == List("arg1", "arg2"), s"Expected args [arg1, arg2] but got ${args.toList}")
    println("OK")
  }
}
