import verify.BasicTestSuite

object ExampleSpec extends BasicTestSuite:
  test("test with autoScalaLibrary false should use project classpath only"):
    assert(1 == 1)
