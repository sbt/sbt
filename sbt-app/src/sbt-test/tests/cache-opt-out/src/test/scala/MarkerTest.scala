import java.nio.file.{ Files, Paths }
import verify.*

object MarkerTest extends BasicTestSuite:
  test("writes a marker on every run"):
    Files.writeString(Paths.get("ran.txt"), "ran")
    ()
