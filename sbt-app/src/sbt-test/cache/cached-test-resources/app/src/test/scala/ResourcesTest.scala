import java.nio.file.{ Files, Paths }
import scala.io.Source
import scala.jdk.CollectionConverters.*
import scala.util.Using
import verify.*

object ResourcesTest extends BasicTestSuite:
  def read(name: String): String = Using.resource(Source.fromResource(name))(_.mkString.trim)

  test("read the resources of the project and of the projects it depends on"):
    Files.writeString(Paths.get("ran"), "")
    assert(read("app.txt") == "app")
    assert(read("core.txt") == "core")
    assert(read("util.txt") == "util")
    assert(getClass.getClassLoader.getResources("same.txt").asScala.size == 2)
end ResourcesTest
