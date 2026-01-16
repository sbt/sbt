import org.scalatest.funsuite.AnyFunSuite

class ResourceTest extends AnyFunSuite {
  test("access main resource from forked test") {
    val loader = Thread.currentThread().getContextClassLoader()
    val resource = loader.getResource("foo")
    assert(resource != null, "Resource 'foo' from src/main/resources should be accessible in forked test execution")
  }
}

