import java.io.File

import munit.FunSuite

class FileTest extends FunSuite:
  test("test baseDirectory") {
    val x = new File("foo").getAbsoluteFile().getParentFile()
    assert(x.getName() != "core", s"actual ${x.getName()}")
  }
end FileTest
