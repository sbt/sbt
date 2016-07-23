package coursier
package test

import coursier.core._
import utest._

object VersionConstraintTests extends TestSuite {

  val tests = TestSuite {
    'parse{
      'empty{
        val c0 = Parse.versionConstraint("")
        assert(c0 == Some(VersionConstraint.all))
      }
      'basicVersion{
        val c0 = Parse.versionConstraint("1.2")
        assert(c0 == Some(VersionConstraint.preferred(Version("1.2"))))
      }
      'basicVersionInterval{
        val c0 = Parse.versionConstraint("(,1.2]")
        assert(c0 == Some(VersionConstraint.interval(VersionInterval(None, Some(Version("1.2")), false, true))))
      }
    }

    'repr{
      'empty{
        val s0 = VersionConstraint.all.repr
        assert(s0 == Some(""))
      }
      'preferred{
        val s0 = VersionConstraint.preferred(Version("2.1")).repr
        assert(s0 == Some("2.1"))
      }
      'interval{
        val s0 = VersionConstraint.interval(VersionInterval(None, Some(Version("2.1")), false, true)).repr
        assert(s0 == Some("(,2.1]"))
      }
    }
  }

}
