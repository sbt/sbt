
import org.scalatest.fixture.FunSuite
import org.scalatest.Tag

class ArgumentTest extends FunSuite{
  type FixtureParam = Map[String,Any]
  override def withFixture(test: OneArgTest) = {
    test(test.configMap)
  }
  test("1", Tag("test1")){ conf => sys.error("error #1") }
  test("2", Tag("test2")){ conf => () }
  test("3", Tag("test3")){ conf => () }
  test("4", Tag("test4")){ conf => sys.error("error #4") }
}