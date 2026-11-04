import scala.io.Source
import scala.util.Using
import verify.*

object RemappedTest extends BasicTestSuite:
  def read(name: String): String = Using.resource(Source.fromResource(name))(_.mkString.trim)

  test("read the test resource"):
    assert(read("remapped.txt") == "remapped")
end RemappedTest
