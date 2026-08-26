package sbt

import verify.BasicTestSuite

object RunFromSourceMainSpec extends BasicTestSuite {
  test("jarName") {
    assert(RunFromSourceMain.jarName("scala3-compiler_3-3.8.4.jar") == "scala3-compiler_3")
    assert(RunFromSourceMain.jarName("scala3-library_3-3.8.4.jar") == "scala3-library_3")
    assert(RunFromSourceMain.jarName("scala3-library_3-3.9.0-RC6.jar") == "scala3-library_3")
    assert(RunFromSourceMain.jarName("scala3-library_3-3.9.0-RC123.jar") == "scala3-library_3")
  }
}
