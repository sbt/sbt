package sbt

import org.scalacheck._
import org.scalacheck.Arbitrary._
import Prop._
import sbt.librarymanagement._

class CacheIvyTest extends Properties("CacheIvy") {
  import CacheIvy._
  import sbinary.Operations._
  import sbinary._
  import sbinary.DefaultProtocol._

  private def cachePreservesEquality[T: Format](m: T, eq: (T, T) => Prop, str: T => String): Prop = {
    val out = fromByteArray[T](toByteArray(m))
    eq(out, m) :| s"Expected: ${str(m)}" :| s"Got: ${str(out)}"
  }

  implicit val arbExclusionRule: Arbitrary[ExclusionRule] = Arbitrary(
    for {
      o <- Gen.alphaStr
      n <- Gen.alphaStr
      a <- Gen.alphaStr
      cs <- arbitrary[List[String]]
    } yield ExclusionRule(o, n, a, cs)
  )

  implicit val arbCrossVersion: Arbitrary[CrossVersion] = Arbitrary {
    // Actual functions don't matter, just Disabled vs Binary vs Full
    import CrossVersion._
    Gen.oneOf(Disabled, new Binary(identity), new Full(identity))
  }

  implicit val arbArtifact: Arbitrary[Artifact] = Arbitrary {
    for {
      (n, t, e, cls) <- arbitrary[(String, String, String, String)]
    } yield Artifact(n, t, e, cls) // keep it simple
  }

  implicit val arbModuleID: Arbitrary[ModuleID] = Arbitrary {
    for {
      o <- Gen.identifier
      n <- Gen.identifier
      r <- for { n <- Gen.numChar; ns <- Gen.numStr } yield n + ns
      cs <- arbitrary[Option[String]]
      branch <- arbitrary[Option[String]]
      isChanging <- arbitrary[Boolean]
      isTransitive <- arbitrary[Boolean]
      isForce <- arbitrary[Boolean]
      explicitArtifacts <- Gen.listOf(arbitrary[Artifact])
      exclusions <- Gen.listOf(arbitrary[ExclusionRule])
      inclusions <- Gen.listOf(arbitrary[InclusionRule])
      extraAttributes <- Gen.mapOf(arbitrary[(String, String)])
      crossVersion <- arbitrary[CrossVersion]
    } yield ModuleID(
      organization = o, name = n, revision = r, configurations = cs, isChanging = isChanging, isTransitive = isTransitive,
      isForce = isForce, explicitArtifacts = explicitArtifacts, inclusions = inclusions, exclusions = exclusions,
      extraAttributes = extraAttributes, crossVersion = crossVersion, branchName = branch
    )
  }

  property("moduleIDFormat") = forAll { (m: ModuleID) =>
    def str(m: ModuleID) = {
      import m._
      s"ModuleID($organization, ${m.name}, $revision, $configurations, $isChanging, $isTransitive, $isForce, $explicitArtifacts, $exclusions, " +
        s"$inclusions, $extraAttributes, $crossVersion, $branchName)"
    }
    def eq(a: ModuleID, b: ModuleID): Prop = {
      import CrossVersion._
      def rest = a.copy(crossVersion = b.crossVersion) == b
      (a.crossVersion, b.crossVersion) match {
        case (Disabled, Disabled)   => rest
        case (_: Binary, _: Binary) => rest
        case (_: Full, _: Full)     => rest
        case (a, b)                 => Prop(false) :| s"CrossVersions don't match: $a vs $b"
      }

    }
    cachePreservesEquality(m, eq _, str)
  }
}
