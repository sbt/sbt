package coursier.util

import coursier.core.Attributes
import coursier.{Dependency, Module}
import utest._

object PrintTests extends TestSuite {

  val tests = TestSuite {
    'ignoreAttributes - {
      val dep = Dependency(
        Module("org", "name"),
        "0.1",
        configuration = "foo"
      )
      val deps = Seq(
        dep,
        dep.copy(attributes = Attributes("fooz", ""))
      )

      val res = Print.dependenciesUnknownConfigs(deps, Map())
      val expectedRes = "org:name:0.1:foo"

      assert(res == expectedRes)
    }
  }

}
