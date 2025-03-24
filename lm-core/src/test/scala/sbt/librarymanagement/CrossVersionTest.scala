package sbt.librarymanagement

import sbt.internal.librarymanagement.UnitSpec
import CrossVersion.*
import scala.annotation.nowarn

class CrossVersionTest extends UnitSpec {
  "sbtApiVersion" should "for xyz return None" in {
    sbtApiVersion("xyz") shouldBe None
  }
  it should "for 0.12 return None" in {
    sbtApiVersion("0.12") shouldBe None
  }
  it should "for 0.12.0-SNAPSHOT return None" in {
    sbtApiVersion("0.12.0-SNAPSHOT") shouldBe None
  }
  it should "for 0.12.0-RC1 return Some((0, 12))" in {
    sbtApiVersion("0.12.0-RC1") shouldBe Some((0, 12))
  }
  it should "for 0.12.0 return Some((0, 12))" in {
    sbtApiVersion("0.12.0") shouldBe Some((0, 12))
  }
  it should "for 0.12.1-SNAPSHOT return Some((0, 12))" in {
    sbtApiVersion("0.12.1-SNAPSHOT") shouldBe Some((0, 12))
  }
  it should "for 0.12.1-RC1 return Some((0, 12))" in {
    sbtApiVersion("0.12.1-RC1") shouldBe Some((0, 12))
  }
  it should "for 0.12.1 return Some((0, 12))" in {
    sbtApiVersion("0.12.1") shouldBe Some((0, 12))
  }
  it should "for 1.0.0-M6 return None" in {
    sbtApiVersion("1.0.0-M6") shouldBe None
  }
  it should "for 1.0.0-RC1 return Some((1, 0))" in {
    sbtApiVersion("1.0.0-RC1") shouldBe Some((1, 0))
  }
  it should "for 1.0.0 return Some((1, 0))" in {
    sbtApiVersion("1.0.0") shouldBe Some((1, 0))
  }
  it should "for 1.0.2-M1 return Some((1, 0))" in {
    sbtApiVersion("1.0.2-M1") shouldBe Some((1, 0))
  }
  it should "for 1.0.2-RC1 return Some((1, 0))" in {
    sbtApiVersion("1.0.2-RC1") shouldBe Some((1, 0))
  }
  it should "for 1.0.2 return Some((1, 0))" in {
    sbtApiVersion("1.0.2") shouldBe Some((1, 0))
  }
  it should "for 1.3.0 return Some((1, 0))" in {
    sbtApiVersion("1.3.0") shouldBe Some((1, 0))
  }
  it should "for 1.10.0 return Some((1, 0))" in {
    sbtApiVersion("1.10.0") shouldBe Some((1, 0))
  }
  it should "for 2.0.0 return Some((2, 0))" in {
    sbtApiVersion("2.0.0") shouldBe Some((2, 0))
  }

  "isSbtApiCompatible" should "for 0.12.0-M1 return false" in {
    isSbtApiCompatible("0.12.0-M1") shouldBe false
  }
  it should "for 0.12.0-RC1 return true" in {
    isSbtApiCompatible("0.12.0-RC1") shouldBe true
  }
  it should "for 0.12.1-RC1 return true" in {
    isSbtApiCompatible("0.12.1-RC1") shouldBe true
  }
  it should "for 1.0.0-M6 return false" in {
    isSbtApiCompatible("1.0.0-M6") shouldBe false
  }
  it should "for 1.0.0-RC1 return true" in {
    isSbtApiCompatible("1.0.0-RC1") shouldBe true
  }
  it should "for 1.0.0 return true" in {
    isSbtApiCompatible("1.0.0") shouldBe true
  }
  it should "for 1.0.2-M1 return true" in {
    isSbtApiCompatible("1.0.2-M1") shouldBe true
  }
  it should "for 1.0.2-RC1 return true" in {
    isSbtApiCompatible("1.0.2-RC1") shouldBe true
  }
  it should "for 1.0.2 return true" in {
    isSbtApiCompatible("1.0.2") shouldBe true
  }
  it should "for 1.3.0 return true" in {
    isSbtApiCompatible("1.3.0") shouldBe true
  }
  it should "for 1.10.0 return true" in {
    isSbtApiCompatible("1.10.0") shouldBe true
  }
  it should "for 2.0.0 return true" in {
    isSbtApiCompatible("2.0.0") shouldBe true
  }

  "binarySbtVersion" should "for 0.11.3 return 0.11.3" in {
    binarySbtVersion("0.11.3") shouldBe "0.11.3"
  }
  it should "for 2.0.0 return 2" in {
    binarySbtVersion("2.0.0") shouldBe "2"
  }
  it should "for 2.0.0-M1 return 2.0.0-M1" in {
    binarySbtVersion("2.0.0-M1") shouldBe "2.0.0-M1"
  }
  it should "for 2.0.0-RC1 return 2" in {
    binarySbtVersion("2.0.0-RC1") shouldBe "2"
  }
  it should "for 2.1.0-M1 return 2" in {
    binarySbtVersion("2.1.0-M1") shouldBe "2"
  }
  it should "for 2.1.0 return 2" in {
    binarySbtVersion("2.1.0") shouldBe "2"
  }
  it should "for 0.13.1 return 0.13" in {
    binarySbtVersion("0.13.1") shouldBe "0.13"
  }
  it should "for 1.0.0-M6 return 1.0.0-M6" in {
    binarySbtVersion("1.0.0-M6") shouldBe "1.0.0-M6"
  }
  it should "for 1.0.0-RC1 return 1.0" in {
    binarySbtVersion("1.0.0-RC1") shouldBe "1.0"
  }
  it should "for 1.0.0 return 1.0" in {
    binarySbtVersion("1.0.0") shouldBe "1.0"
  }
  it should "for 1.0.2-M1 return 1.0" in {
    binarySbtVersion("1.0.2-M1") shouldBe "1.0"
  }
  it should "for 1.0.2-RC1 return 1.0" in {
    binarySbtVersion("1.0.2-RC1") shouldBe "1.0"
  }
  it should "for 1.0.2 return 1.0" in {
    binarySbtVersion("1.0.2") shouldBe "1.0"
  }
  it should "for 1.3.0 return 1.0" in {
    binarySbtVersion("1.3.0") shouldBe "1.0"
  }
  it should "for 1.3.0-SNAPSHOT return 1.0" in {
    binarySbtVersion("1.3.0-SNAPSHOT") shouldBe "1.0"
  }
  it should "for 1.3.0-A1-B1.1 return 1.0" in {
    binarySbtVersion("1.3.0-A1-B1.1") shouldBe "1.0"
  }
  it should "for 1.10.0 return 1.0" in {
    binarySbtVersion("1.10.0") shouldBe "1.0"
  }

  "scalaApiVersion" should "for xyz return None" in {
    scalaApiVersion("xyz") shouldBe None
  }
  it should "for 2.10 return None" in {
    scalaApiVersion("2.10") shouldBe None
  }
  it should "for 2.10.0-SNAPSHOT return None" in {
    scalaApiVersion("2.10.0-SNAPSHOT") shouldBe None
  }
  it should "for 2.10.0-RC1 return None" in {
    scalaApiVersion("2.10.0-RC1") shouldBe None
  }
  it should "for 2.10.0 return Some((2, 10))" in {
    scalaApiVersion("2.10.0") shouldBe Some((2, 10))
  }
  it should "for 2.10.0-1 return Some((2, 10))" in {
    scalaApiVersion("2.10.0-1") shouldBe Some((2, 10))
  }
  it should "for 2.10.1-SNAPSHOT return Some((2, 10))" in {
    scalaApiVersion("2.10.1-SNAPSHOT") shouldBe Some((2, 10))
  }
  it should "for 2.10.1-RC1 return Some((2, 10))" in {
    scalaApiVersion("2.10.1-RC1") shouldBe Some((2, 10))
  }
  it should "for 2.10.1 return Some((2, 10))" in {
    scalaApiVersion("2.10.1") shouldBe Some((2, 10))
  }

  "isScalaApiCompatible" should "for 2.10.0-M1 return false" in {
    isScalaApiCompatible("2.10.0-M1") shouldBe false
  }
  it should "for 2.10.0-RC1 return false" in {
    isScalaApiCompatible("2.10.0-RC1") shouldBe false
  }
  it should "for 2.10.1-RC1 return false" in {
    isScalaApiCompatible("2.10.1-RC1") shouldBe true
  }

  "binaryScalaVersion" should "for 2.9.2 return 2.9.2" in {
    binaryScalaVersion("2.9.2") shouldBe "2.9.2"
  }
  it should "for 2.10.0-M1 return 2.10.0-M1" in {
    binaryScalaVersion("2.10.0-M1") shouldBe "2.10.0-M1"
  }
  it should "for 2.10.0-RC1 return 2.10.0-RC1" in {
    binaryScalaVersion("2.10.0-RC1") shouldBe "2.10.0-RC1"
  }
  it should "for 2.10.0 return 2.10" in {
    binaryScalaVersion("2.10.0") shouldBe "2.10"
  }
  it should "for 2.10.1-M1 return 2.10" in {
    binaryScalaVersion("2.10.1-M1") shouldBe "2.10"
  }
  it should "for 2.10.1-RC1 return 2.10" in {
    binaryScalaVersion("2.10.1-RC1") shouldBe "2.10"
  }
  it should "for 2.10.1 return 2.10" in {
    binaryScalaVersion("2.10.1") shouldBe "2.10"
  }
  it should "for 2.20170314093845.0-87654321 return 2.20170314093845" in {
    binaryScalaVersion("2.20170314093845.0-87654321") shouldBe "2.20170314093845"
  }
  it should "for 3.0.0-M2 return 3.0.0-M2" in {
    binaryScalaVersion("3.0.0-M2") shouldBe "3.0.0-M2"
  }
  it should "for 3.0.0-M3-bin-SNAPSHOT return 3.0.0-M3" in {
    binaryScalaVersion("3.0.0-M3-bin-SNAPSHOT") shouldBe "3.0.0-M3"
  }
  it should "for 3.0.0-M3-bin-20201215-cbe50b3-NIGHTLY return 3.0.0-M3" in {
    binaryScalaVersion("3.0.0-M3-bin-20201215-cbe50b3-NIGHTLY") shouldBe "3.0.0-M3"
  }
  it should "for 3.0.0-M3.5-bin-20201215-cbe50b3-NIGHTLY return 3.0.0-M3" in {
    binaryScalaVersion("3.0.0-M3.5-bin-20201215-cbe50b3-NIGHTLY") shouldBe "3.0.0-M3.5"
  }
  it should "for 3.0.0-RC1 return 3.0.0-RC1" in {
    binaryScalaVersion("3.0.0-RC1") shouldBe "3.0.0-RC1"
  }

  // Not set in stone but 3 is the favorite candidate so far
  // (see https://github.com/lampepfl/dotty/issues/10244)
  it should "for 3.0.0 return 3" in {
    binaryScalaVersion("3.0.0") shouldBe "3"
  }
  it should "for 3.1.0-M1 return 3" in {
    binaryScalaVersion("3.1.0-M1") shouldBe "3"
  }
  it should "for 3.1.0-RC1-bin-SNAPSHOT return 3" in {
    binaryScalaVersion("3.1.0-RC1-bin-SNAPSHOT") shouldBe "3"
  }
  it should "for 3.1.0-RC1 return 3" in {
    binaryScalaVersion("3.1.0-RC1") shouldBe "3"
  }
  it should "for 3.1.0 return 3" in {
    binaryScalaVersion("3.1.0") shouldBe "3"
  }
  it should "for 3.0.1-RC1 return 3" in {
    binaryScalaVersion("3.0.1-RC1") shouldBe "3"
  }
  it should "for 3.0.1-M1 return 3" in {
    binaryScalaVersion("3.0.1-M1") shouldBe "3"
  }
  it should "for 3.0.1-RC1-bin-SNAPSHOT return 3" in {
    binaryScalaVersion("3.0.1-RC1-bin-SNAPSHOT") shouldBe "3"
  }
  it should "for 3.0.1-bin-nonbootstrapped return 3" in {
    binaryScalaVersion("3.0.1-bin-SNAPSHOT") shouldBe "3"
  }
  it should "for 3.0.1-SNAPSHOT return 3" in {
    binaryScalaVersion("3.0.1-SNAPSHOT") shouldBe "3"
  }

  private def patchVersion(fullVersion: String) =
    CrossVersion(CrossVersion.patch, fullVersion, "dummy") map (fn => fn("artefact"))

  "CrossVersion.patch" should "for 2.11.8 return 2.11.8" in {
    patchVersion("2.11.8") shouldBe Some("artefact_2.11.8")
  }
  it should "for 2.11.8-M1 return 2.11.8-M1" in {
    patchVersion("2.11.8-M1") shouldBe Some("artefact_2.11.8-M1")
  }
  it should "for 2.11.8-RC1 return 2.11.8-RC1" in {
    patchVersion("2.11.8-RC1") shouldBe Some("artefact_2.11.8-RC1")
  }
  it should "for 2.11.8-bin-extra return 2.11.8" in {
    patchVersion("2.11.8-bin-extra") shouldBe Some("artefact_2.11.8")
  }
  it should "for 2.11.8-M1-bin-extra return 2.11.8-M1" in {
    patchVersion("2.11.8-M1-bin-extra") shouldBe Some("artefact_2.11.8-M1")
  }
  it should "for 2.11.8-RC1-bin-extra return 2.11.8-RC1" in {
    patchVersion("2.11.8-RC1-bin-extra") shouldBe Some("artefact_2.11.8-RC1")
  }
  it should "for 2.11.8-X1.5-bin-extra return 2.11.8-X1.5" in {
    patchVersion("2.11.8-X1.5-bin-extra") shouldBe Some("artefact_2.11.8-X1.5")
  }

  "isScalaBinaryCompatibleWith" should "for (2.10.4, 2.10.5) return true" in {
    isScalaBinaryCompatibleWith("2.10.4", "2.10.5") shouldBe true
  }
  it should "for (2.10.6, 2.10.5) return true" in {
    isScalaBinaryCompatibleWith("2.10.6", "2.10.5") shouldBe true
  }
  it should "for (2.11.0, 2.10.5) return false" in {
    isScalaBinaryCompatibleWith("2.11.0", "2.10.5") shouldBe false
  }
  it should "for (3.0.0, 2.10.5) return false" in {
    isScalaBinaryCompatibleWith("3.0.0", "2.10.5") shouldBe false
  }
  it should "for (3.0.0, 3.1.0) return false" in {
    isScalaBinaryCompatibleWith("3.0.0", "3.1.0") shouldBe false
  }
  it should "for (3.1.0, 3.0.0) return true" in {
    isScalaBinaryCompatibleWith("3.1.0", "3.0.0") shouldBe true
  }
  it should "for (3.1.0, 3.1.1) return true" in {
    isScalaBinaryCompatibleWith("3.1.0", "3.1.1") shouldBe true
  }
  it should "for (3.1.1, 3.1.0) return true" in {
    isScalaBinaryCompatibleWith("3.1.1", "3.1.0") shouldBe true
  }
  it should "for (2.10.0-M1, 2.10.5) return false" in {
    isScalaBinaryCompatibleWith("2.10.0-M1", "2.10.5") shouldBe false
  }
  it should "for (2.10.5, 2.10.0-M1) return false" in {
    isScalaBinaryCompatibleWith("2.10.5", "2.10.0-M1") shouldBe false
  }
  it should "for (2.10.0-M1, 2.10.0-M2) return false" in {
    isScalaBinaryCompatibleWith("2.10.0-M1", "2.10.0-M2") shouldBe false
  }
  it should "for (2.10.0-M1, 2.11.0-M1) return false" in {
    isScalaBinaryCompatibleWith("2.10.0-M1", "2.11.0-M1") shouldBe false
  }
  it should "for (3.1.0-M1, 3.0.0) return true" in {
    isScalaBinaryCompatibleWith("3.1.0-M1", "3.0.0") shouldBe true
  }
  it should "for (3.1.0-M1, 3.1.0) return false" in {
    isScalaBinaryCompatibleWith("3.1.0-M1", "3.1.0") shouldBe false
  }
  it should "for (3.1.0-M1, 3.1.0-M2) return false" in {
    isScalaBinaryCompatibleWith("3.1.0-M1", "3.1.0-M2") shouldBe false
  }
  it should "for (3.1.0-M2, 3.1.0-M1) return false" in {
    isScalaBinaryCompatibleWith("3.1.0-M2", "3.1.0-M1") shouldBe false
  }

  private def constantVersion(value: String) =
    CrossVersion(CrossVersion.constant(value), "dummy1", "dummy2") map (fn => fn("artefact"))

  "CrossVersion.constant" should "add a constant to the version" in {
    constantVersion("duck") shouldBe Some("artefact_duck")
  }

  "Disabled" should "have structural equality" in {
    Disabled() shouldBe Disabled()
  }: @nowarn
  "CrossVersion.full" should "have structural equality" in {
    CrossVersion.full shouldBe CrossVersion.full
  }
  "CrossVersion.binary" should "have structural equality" in {
    CrossVersion.binary shouldBe CrossVersion.binary
  }
  "CrossVersion.constant" should "have structural equality" in {
    CrossVersion.constant("duck") shouldBe CrossVersion.constant("duck")
  }

  "CrossVersion.for3Use2_13" should "have structural equality" in {
    CrossVersion.for3Use2_13 shouldBe CrossVersion.for3Use2_13
    CrossVersion.for3Use2_13With("_sjs1", "") shouldBe CrossVersion.for3Use2_13With("_sjs1", "")
  }
  it should "use the cross version 2.13 instead of 3" in {
    CrossVersion(CrossVersion.for3Use2_13, "3.0.0", "3").map(_("artefact")) shouldBe Some(
      "artefact_2.13"
    )
  }
  it should "use the cross version 2.13 instead of 3.0.0-M3" in {
    CrossVersion(CrossVersion.for3Use2_13, "3.0.0-M3", "3.0.0-M3").map(_("artefact")) shouldBe Some(
      "artefact_2.13"
    )
  }

  "CrossVersion.for2_13Use3" should "have structural equality" in {
    CrossVersion.for2_13Use3 shouldBe CrossVersion.for2_13Use3
    CrossVersion.for2_13Use3With("_sjs1", "") shouldBe CrossVersion.for2_13Use3With("_sjs1", "")
  }
  it should "use the cross version 3 instead of 2.13" in {
    CrossVersion(CrossVersion.for2_13Use3, "2.13.4", "2.13").map(_("artefact")) shouldBe Some(
      "artefact_3"
    )
  }
}
