package example

object ResourceTest extends verify.BasicTestSuite:
  test("resource content"):
    val is = getClass.getResourceAsStream("/hello.txt")
    val content = new String(is.readAllBytes(), "UTF-8").trim
    is.close()
    assert(content == "hello")
end ResourceTest
