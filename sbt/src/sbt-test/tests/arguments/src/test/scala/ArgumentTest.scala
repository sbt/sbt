import org.scalatest._
import org.scalatest.Tag
import org.scalatest.fixture

class ArgumentTest extends fixture.FunSuite {
  type FixtureParam = Map[String,Any]

  override def withFixture(test: OneArgTest) = {
    // Perform setup
    val theFixture = test.configMap
    try withFixture(test.toNoArgTest(theFixture)) // Invoke the test function
    finally {
      // Perform cleanup
    }
  }

  test("1", Tag("test1")){ conf => sys.error("error #1") }
  test("2", Tag("test2")){ conf => () }
  test("3", Tag("test3")){ conf => () }
  test("4", Tag("test4")){ conf => sys.error("error #4") }
}
