package coursier
package test

import utest._

object VersionTests extends TestSuite {

  import core.Version

  def compare(first: String, second: String) =
    Version(first).compare(Version(second))

  def increasing(versions: String*): Boolean =
    versions.iterator.sliding(2).withPartial(false).forall{case Seq(a, b) => compare(a, b) < 0 }


  val tests = TestSuite {

    'stackOverflow - {
      val s = "." * 100000
      val v = Version(s)
      assert(v.isEmpty)
    }

    'empty - {
      val v0 = Version("0")
      val v = Version("")

      assert(v0.isEmpty)
      assert(v.isEmpty)
    }

    'max - {
      val v21 = Version("2.1")
      val v22 = Version("2.2")
      val v23 = Version("2.3")
      val v24 = Version("2.4")
      val v241 = Version("2.4.1")

      val l = Seq(v21, v22, v23, v24, v241)
      val max = l.max

      assert(max == v241)
    }

    'buildMetadata - {
      * - {
        assert(compare("1.2", "1.2+foo") < 0)

        // Semver § 10: two versions that differ only in the build metadata, have the same precedence
        assert(compare("1.2+bar", "1.2+foo") == 0)
        assert(compare("1.2+bar.1", "1.2+bar.2") == 0)
      }

      'shouldNotParseMetadata - {
        * - {
          val items = Version("1.2+bar.2").items
          val expectedItems = Seq(
            Version.Number(1), Version.Number(2), Version.BuildMetadata("bar.2")
          )
          assert(items == expectedItems)
        }
        * - {
          val items = Version("1.2+bar-2").items
          val expectedItems = Seq(
            Version.Number(1), Version.Number(2), Version.BuildMetadata("bar-2")
          )
          assert(items == expectedItems)
        }
        * - {
          val items = Version("1.2+bar+foo").items
          val expectedItems = Seq(
            Version.Number(1), Version.Number(2), Version.BuildMetadata("bar+foo")
          )
          assert(items == expectedItems)
        }
      }
    }

    // Adapted from aether-core/aether-util/src/test/java/org/eclipse/aether/util/version/GenericVersionTest.java
    // Only one test doesn't pass (see FIXME below)

    'emptyVersion - {
      assert(compare("0", "" ) == 0)
    }


    'numericOrdering - {
      assert(compare("2", "10" ) < 0)
      assert(compare("1.2", "1.10" ) < 0)
      assert(compare("1.0.2", "1.0.10" ) < 0)
      assert(compare("1.0.0.2", "1.0.0.10" ) < 0)
      assert(compare("1.0.20101206.111434.1", "1.0.20101206.111435.1" ) < 0)
      assert(compare("1.0.20101206.111434.2", "1.0.20101206.111434.10" ) < 0)
    }


    'delimiters - {
      assert(compare("1.0", "1-0" ) == 0)
      assert(compare("1.0", "1_0" ) == 0)
      assert(compare("1.a", "1a" ) == 0)
    }


    'leadingZerosAreSemanticallyIrrelevant - {
      assert(compare("1", "01" ) == 0)
      assert(compare("1.2", "1.002" ) == 0)
      assert(compare("1.2.3", "1.2.0003" ) == 0)
      assert(compare("1.2.3.4", "1.2.3.00004" ) == 0)
    }


    'trailingZerosAreSemanticallyIrrelevant - {
      assert(compare("1", "1.0.0.0.0.0.0.0.0.0.0.0.0.0" ) == 0)
      assert(compare("1", "1-0-0-0-0-0-0-0-0-0-0-0-0-0" ) == 0)
      assert(compare("1", "1.0-0.0-0.0-0.0-0.0-0.0-0.0" ) == 0)
      assert(compare("1", "1.0000000000000" ) == 0)
      assert(compare("1.0", "1.0.0" ) == 0)
    }


    'trailingZerosBeforeQualifierAreSemanticallyIrrelevant - {
      assert(compare("1.0-ga", "1.0.0-ga" ) == 0)
      assert(compare("1.0.ga", "1.0.0.ga" ) == 0)
      assert(compare("1.0ga", "1.0.0ga" ) == 0)

      assert(compare("1.0-alpha", "1.0.0-alpha" ) == 0)
      assert(compare("1.0.alpha", "1.0.0.alpha" ) == 0)
      assert(compare("1.0alpha", "1.0.0alpha" ) == 0)
      assert(compare("1.0-alpha-snapshot", "1.0.0-alpha-snapshot" ) == 0)
      assert(compare("1.0.alpha.snapshot", "1.0.0.alpha.snapshot" ) == 0)

      assert(compare("1.x.0-alpha", "1.x.0.0-alpha" ) == 0)
      assert(compare("1.x.0.alpha", "1.x.0.0.alpha" ) == 0)
      assert(compare("1.x.0-alpha-snapshot", "1.x.0.0-alpha-snapshot" ) == 0)
      assert(compare("1.x.0.alpha.snapshot", "1.x.0.0.alpha.snapshot" ) == 0)
    }


    'trailingDelimitersAreSemanticallyIrrelevant - {
      assert(compare("1", "1............." ) == 0)
      assert(compare("1", "1-------------" ) == 0)
      assert(compare("1.0", "1............." ) == 0)
      assert(compare("1.0", "1-------------" ) == 0)
    }


    'initialDelimiters - {
      assert(compare("0.1", ".1" ) == 0)
      assert(compare("0.0.1", "..1" ) == 0)
      assert(compare("0.1", "-1" ) == 0)
      assert(compare("0.0.1", "--1" ) == 0)
    }


    'consecutiveDelimiters - {
      assert(compare("1.0.1", "1..1" ) == 0)
      assert(compare("1.0.0.1", "1...1" ) == 0)
      assert(compare("1.0.1", "1--1" ) == 0)
      assert(compare("1.0.0.1", "1---1" ) == 0)
    }


    'unlimitedNumberOfVersionComponents - {
      assert(compare("1.0.1.2.3.4.5.6.7.8.9.0.1.2.10", "1.0.1.2.3.4.5.6.7.8.9.0.1.2.3" ) > 0)
    }


    'unlimitedNumberOfDigitsInNumericComponent - {
      assert(compare("1.1234567890123456789012345678901", "1.123456789012345678901234567891" ) > 0)
    }


    'transitionFromDigitToLetterAndViceVersaIsEqualivantToDelimiter - {
      assert(compare("1alpha10", "1.alpha.10" ) == 0)
      assert(compare("1alpha10", "1-alpha-10" ) == 0)

      assert(compare("1.alpha10", "1.alpha2" ) > 0)
      assert(compare("10alpha", "1alpha" ) > 0)
    }


    'wellKnownQualifierOrdering - {
      assert(compare("1-alpha1", "1-a1" ) == 0)
      assert(compare("1-alpha", "1-beta" ) < 0)
      assert(compare("1-beta1", "1-b1" ) == 0)
      assert(compare("1-beta", "1-milestone" ) < 0)
      assert(compare("1-milestone1", "1-m1" ) == 0)
      assert(compare("1-milestone", "1-rc" ) < 0)
      assert(compare("1-rc", "1-cr" ) == 0)
      assert(compare("1-rc", "1-snapshot" ) < 0)
      assert(compare("1-snapshot", "1" ) < 0)
      assert(compare("1", "1-ga" ) == 0)
      assert(compare("1", "1.ga.0.ga" ) == 0)
      assert(compare("1.0", "1-ga" ) == 0)
      assert(compare("1", "1-ga.ga" ) == 0)
      assert(compare("1", "1-ga-ga" ) == 0)
      assert(compare("A", "A.ga.ga" ) == 0)
      assert(compare("A", "A-ga-ga" ) == 0)
      assert(compare("1", "1-final" ) == 0)
      assert(compare("1", "1-sp" ) < 0)

      assert(compare("A.rc.1", "A.ga.1" ) < 0)
      assert(compare("A.sp.1", "A.ga.1" ) > 0)
      assert(compare("A.rc.x", "A.ga.x" ) < 0)
      assert(compare("A.sp.x", "A.ga.x" ) > 0)
    }


    'wellKnownQualifierVersusUnknownQualifierOrdering - {
      assert(compare("1-abc", "1-alpha" ) > 0)
      assert(compare("1-abc", "1-beta" ) > 0)
      assert(compare("1-abc", "1-milestone" ) > 0)
      assert(compare("1-abc", "1-rc" ) > 0)
      assert(compare("1-abc", "1-snapshot" ) > 0)
      assert(compare("1-abc", "1" ) > 0)
      assert(compare("1-abc", "1-sp" ) > 0)
    }


    'wellKnownSingleCharQualifiersOnlyRecognizedIfImmediatelyFollowedByNumber - {
      assert(compare("1.0a", "1.0" ) > 0)
      assert(compare("1.0-a", "1.0" ) > 0)
      assert(compare("1.0.a", "1.0" ) > 0)
      assert(compare("1.0b", "1.0" ) > 0)
      assert(compare("1.0-b", "1.0" ) > 0)
      assert(compare("1.0.b", "1.0" ) > 0)
      assert(compare("1.0m", "1.0" ) > 0)
      assert(compare("1.0-m", "1.0" ) > 0)
      assert(compare("1.0.m", "1.0" ) > 0)

      assert(compare("1.0a1", "1.0" ) < 0)
      assert(compare("1.0-a1", "1.0" ) < 0)
      assert(compare("1.0.a1", "1.0" ) < 0)
      assert(compare("1.0b1", "1.0" ) < 0)
      assert(compare("1.0-b1", "1.0" ) < 0)
      assert(compare("1.0.b1", "1.0" ) < 0)
      assert(compare("1.0m1", "1.0" ) < 0)
      assert(compare("1.0-m1", "1.0" ) < 0)
      assert(compare("1.0.m1", "1.0" ) < 0)

      assert(compare("1.0a.1", "1.0" ) > 0)
      assert(compare("1.0a-1", "1.0" ) > 0)
      assert(compare("1.0b.1", "1.0" ) > 0)
      assert(compare("1.0b-1", "1.0" ) > 0)
      assert(compare("1.0m.1", "1.0" ) > 0)
      assert(compare("1.0m-1", "1.0" ) > 0)
    }


    'unknownQualifierOrdering - {
      assert(compare("1-abc", "1-abcd" ) < 0)
      assert(compare("1-abc", "1-bcd" ) < 0)
      assert(compare("1-abc", "1-aac" ) > 0)
    }


    'caseInsensitiveOrderingOfQualifiers - {
      assert(compare("1.alpha", "1.ALPHA" ) == 0)
      assert(compare("1.alpha", "1.Alpha" ) == 0)

      assert(compare("1.beta", "1.BETA" ) == 0)
      assert(compare("1.beta", "1.Beta" ) == 0)

      assert(compare("1.milestone", "1.MILESTONE" ) == 0)
      assert(compare("1.milestone", "1.Milestone" ) == 0)

      assert(compare("1.rc", "1.RC" ) == 0)
      assert(compare("1.rc", "1.Rc" ) == 0)
      assert(compare("1.cr", "1.CR" ) == 0)
      assert(compare("1.cr", "1.Cr" ) == 0)

      assert(compare("1.snapshot", "1.SNAPSHOT" ) == 0)
      assert(compare("1.snapshot", "1.Snapshot" ) == 0)

      assert(compare("1.ga", "1.GA" ) == 0)
      assert(compare("1.ga", "1.Ga" ) == 0)
      assert(compare("1.final", "1.FINAL" ) == 0)
      assert(compare("1.final", "1.Final" ) == 0)

      assert(compare("1.sp", "1.SP" ) == 0)
      assert(compare("1.sp", "1.Sp" ) == 0)

      assert(compare("1.unknown", "1.UNKNOWN" ) == 0)
      assert(compare("1.unknown", "1.Unknown" ) == 0)
    }


    'qualifierVersusNumberOrdering - {
      assert(compare("1-ga", "1-1" ) < 0)
      assert(compare("1.ga", "1.1" ) < 0)
      assert(compare("1-ga", "1.0" ) == 0)
      assert(compare("1.ga", "1.0" ) == 0)

      assert(compare("1-ga-1", "1-0-1" ) < 0)
      assert(compare("1.ga.1", "1.0.1" ) < 0)

      assert(compare("1.sp", "1.0" ) > 0)
      assert(compare("1.sp", "1.1" ) < 0)

      assert(compare("1-abc", "1-1" ) < 0)
      assert(compare("1.abc", "1.1" ) < 0)

      assert(compare("1-xyz", "1-1" ) < 0)
      assert(compare("1.xyz", "1.1" ) < 0)
    }



    'minimumSegment - {
      assert(compare("1.min", "1.0-alpha-1" ) < 0)
      assert(compare("1.min", "1.0-SNAPSHOT" ) < 0)
      assert(compare("1.min", "1.0" ) < 0)
      assert(compare("1.min", "1.9999999999" ) < 0)

      assert(compare("1.min", "1.MIN" ) == 0)

      assert(compare("1.min", "0.99999" ) > 0)
      assert(compare("1.min", "0.max" ) > 0)
    }


    'maximumSegment - {
      assert(compare("1.max", "1.0-alpha-1" ) > 0)
      assert(compare("1.max", "1.0-SNAPSHOT" ) > 0)
      assert(compare("1.max", "1.0" ) > 0)
      assert(compare("1.max", "1.9999999999" ) > 0)

      assert(compare("1.max", "1.MAX" ) == 0)

      assert(compare("1.max", "2.0-alpha-1" ) < 0)
      assert(compare("1.max", "2.min" ) < 0)
    }


    'versionEvolution - {
      assert(increasing( "0.9.9-SNAPSHOT", "0.9.9", "0.9.10-SNAPSHOT", "0.9.10", "1.0-alpha-2-SNAPSHOT", "1.0-alpha-2",
        "1.0-alpha-10-SNAPSHOT", "1.0-alpha-10", "1.0-beta-1-SNAPSHOT", "1.0-beta-1",
        "1.0-rc-1-SNAPSHOT", "1.0-rc-1", "1.0-SNAPSHOT", "1.0", "1.0-sp-1-SNAPSHOT", "1.0-sp-1"))
      // FIXME Should pass?
      // assert(compare("1.0-sp-1", "1.0.1-alpha-1-SNAPSHOT") < 0)
      assert(increasing("1.0.1-alpha-1-SNAPSHOT",
      "1.0.1-alpha-1", "1.0.1-beta-1-SNAPSHOT", "1.0.1-beta-1",
        "1.0.1-rc-1-SNAPSHOT", "1.0.1-rc-1", "1.0.1-SNAPSHOT", "1.0.1", "1.1-SNAPSHOT", "1.1" ))

      assert(increasing( "1.0-alpha", "1.0", "1.0-1" ))
      assert(increasing( "1.0.alpha", "1.0", "1.0-1" ))
      assert(increasing( "1.0-alpha", "1.0", "1.0.1" ))
      assert(increasing( "1.0.alpha", "1.0", "1.0.1" ))
    }


//    'caseInsensitiveOrderingOfQualifiersIsLocaleIndependent - {
//      import java.util.Locale
//      val orig = Locale.getDefault
//      try {
//        for ( locale <- Seq(Locale.ENGLISH, new Locale( "tr" )) ) {
//          Locale.setDefault( locale )
//          assert(compare("1-abcdefghijklmnopqrstuvwxyz", "1-ABCDEFGHIJKLMNOPQRSTUVWXYZ" ) == 0)
//        }
//      }
//      finally Locale.setDefault( orig )
//    }

    'specialStartChar - {
      val items = Version("[1.2.0]").items
      val expectedItems = Seq(Version.Literal("["), Version.Number(1), Version.Number(2), Version.Literal("]"))
      assert(items == expectedItems)
    }

  }

}
