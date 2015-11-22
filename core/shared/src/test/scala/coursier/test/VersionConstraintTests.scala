package coursier
package test

import coursier.core._
import utest._

object VersionConstraintTests extends TestSuite {

  val tests = TestSuite {
    'parse{
      'empty{
        val c0 = Parse.versionConstraint("")
        assert(c0 == Some(VersionConstraint.None))
      }
      'basicVersion{
        val c0 = Parse.versionConstraint("1.2")
        assert(c0 == Some(VersionConstraint.Preferred(Version("1.2"))))
      }
      'basicVersionInterval{
        val c0 = Parse.versionConstraint("(,1.2]")
        assert(c0 == Some(VersionConstraint.Interval(VersionInterval(None, Some(Version("1.2")), false, true))))
      }
    }

    'repr{
      'empty{
        val s0 = VersionConstraint.None.repr
        assert(s0 == "")
      }
      'preferred{
        val s0 = VersionConstraint.Preferred(Version("2.1")).repr
        assert(s0 == "2.1")
      }
      'interval{
        val s0 = VersionConstraint.Interval(VersionInterval(None, Some(Version("2.1")), false, true)).repr
        assert(s0 == "(,2.1]")
      }
    }

    'interval{
      'empty{
        val s0 = VersionConstraint.None.interval
        assert(s0 == VersionInterval.zero)
      }
      'preferred{
        val s0 = VersionConstraint.Preferred(Version("2.1")).interval
        assert(s0 == VersionInterval(Some(Version("2.1")), None, true, false))
      }
      'interval{
        val s0 = VersionConstraint.Interval(VersionInterval(None, Some(Version("2.1")), false, true)).interval
        assert(s0 == VersionInterval(None, Some(Version("2.1")), false, true))
      }
    }
  }

}
