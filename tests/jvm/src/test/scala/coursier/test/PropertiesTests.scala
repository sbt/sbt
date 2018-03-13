package coursier.test

import coursier.util.Properties
import utest._

object PropertiesTests extends TestSuite {

  val tests = Tests {

    'version - {
      assert(Properties.version.nonEmpty)
    }

    'commitHash - {
      assert(Properties.commitHash.nonEmpty)
    }
  }

}
