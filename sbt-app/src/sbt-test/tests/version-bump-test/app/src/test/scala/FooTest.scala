package example

import java.nio.file.{ Files, Paths }

class FooTest extends munit.FunSuite:
  test("greeting"):
    Files.write(Paths.get("test-ran.marker"), "ran".getBytes)
    assertEquals(Foo.greeting, "hello")
end FooTest
