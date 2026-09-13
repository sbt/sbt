import org.scalatest.funsuite.AnyFunSuite

class CwdSpec extends AnyFunSuite {
  test("create marker in the forked working directory") {
    val marker = new java.io.File("cwd-marker").getAbsoluteFile
    assert(marker.createNewFile() || marker.exists())
  }
}
