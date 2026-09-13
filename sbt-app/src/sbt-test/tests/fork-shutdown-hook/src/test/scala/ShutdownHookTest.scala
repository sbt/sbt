package repro

import java.nio.file.{ Files, Paths }

class ShutdownHookTest extends munit.FunSuite {
  test("shutdown hook can load classes after the test run") {
    val out = Paths.get("hook-result.txt")
    Files.writeString(out, "PENDING: shutdown hook did not run" + System.lineSeparator)
    val _ = sys.addShutdownHook {
      val result =
        try {
          Class.forName("repro.LazilyLoaded$")
          "OK: classloader still works at JVM shutdown"
        } catch {
          case t: Throwable => s"FAIL: $t"
        }
      Files.writeString(out, result + System.lineSeparator)
    }
  }
}
