package example

import cats.effect.*
import munit.CatsEffectSuite

class ExampleSuite extends CatsEffectSuite:

  test("tests can return IO[Unit] with assertions expressed via a map") {
    IO(42).map(it => assertEquals(it, 42))
  }

  test("alternatively, assertions can be written via assertIO") {
    assertIO(IO(42), 42)
  }
end ExampleSuite
