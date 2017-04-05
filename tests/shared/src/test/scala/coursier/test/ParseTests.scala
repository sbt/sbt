package coursier.test

import scalaz.\/-

import coursier.MavenRepository
import coursier.ivy.IvyRepository
import coursier.util.Parse
import utest._

object ParseTests extends TestSuite {
  val tests = TestSuite {
    "bintray-ivy:" - {
      val obtained = Parse.repository("bintray-ivy:scalameta/maven")
      assert(obtained.exists(_.isInstanceOf[IvyRepository]))
    }
    "bintray:" - {
      val obtained = Parse.repository("bintray:scalameta/maven")
      assert(obtained.exists(_.isInstanceOf[MavenRepository]))
    }
  }
}
