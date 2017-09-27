package example

import utest._

import java.nio.file.{ Files, Path, Paths }

object HelloTests extends TestSuite {
  val tests = Tests {
    'test1 - {
      val p = Paths.get("target", "foo")
      Files.createFile(p)
    }
  }
}
