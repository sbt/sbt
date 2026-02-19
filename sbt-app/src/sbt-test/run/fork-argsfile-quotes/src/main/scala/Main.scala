object Main {
  def main(args: Array[String]): Unit = {
    val json = System.getProperty("json")
    assert(json == """{"a":1}""", s"""Expected '{"a":1}' but got '$json'""")
  }
}
