package coursier.test

import coursier.{ Module, Files }
import utest._

object IvyLocalTests extends TestSuite {

  val tests = TestSuite{
    'coursier{
      // Assume this module (and the sub-projects it depends on) is published locally
      CentralTests.resolutionCheck(
        Module("com.github.alexarchambault", "coursier_2.11"), "0.1.0-SNAPSHOT",
        Some(Files.ivy2Local))
    }
  }

}
