package foo.test

class FooTest extends munit.FunSuite {
  test("foo") {
    assertEquals(foo.main.Foo.numbers, Seq(1, 2, 3))
  }
}
