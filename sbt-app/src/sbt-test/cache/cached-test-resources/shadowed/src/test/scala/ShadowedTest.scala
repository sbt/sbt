import java.nio.file.{ Files, Paths }
import scala.io.Source
import scala.util.Using
import verify.*

object ShadowedTest extends BasicTestSuite:
  def read(name: String): String = Using.resource(Source.fromResource(name))(_.mkString.trim)

  test("read one of the resources with the same path"):
    Files.writeString(Paths.get("ran-shadowed"), "")
    assert(Set("first", "second")(read("shadow.txt")))
end ShadowedTest
