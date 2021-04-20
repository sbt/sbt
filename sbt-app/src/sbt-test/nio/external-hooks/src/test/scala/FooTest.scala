import org.scalatest.FlatSpec

class FooTest extends FlatSpec {
  Generate.gen
  it should "work" in {
    assert(Foo.x == 2)
  }
}