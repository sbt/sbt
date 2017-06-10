package sbt.librarymanagement

import sbt.internal.util.UnitSpec
import CrossVersion._

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
  it should "for 0.12.0-M1 return 0.12.0-M1" in {
    binarySbtVersion("0.12.0-M1") shouldBe "0.12.0-M1"
  }
  it should "for 0.12.0-RC1 return 0.12" in {
    binarySbtVersion("0.12.0-RC1") shouldBe "0.12"
  }
  it should "for 0.12.0 return 0.12" in {
    binarySbtVersion("0.12.0") shouldBe "0.12"
  }
  it should "for 0.12.1-SNAPSHOT return 0.12" in {
    binarySbtVersion("0.12.1-SNAPSHOT") shouldBe "0.12"
  }
  it should "for 0.12.1-RC1 return 0.12" in {
    binarySbtVersion("0.12.1-RC1") shouldBe "0.12"
  }
  it should "for 0.12.1 return 0.12" in {
    binarySbtVersion("0.12.1") shouldBe "0.12"
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
  it should "for 1.10.0 return 1.0" in {
    binarySbtVersion("1.10.0") shouldBe "1.0"
  }
  it should "for 2.0.0 return 2.0" in {
    binarySbtVersion("2.0.0") shouldBe "2.0"
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
  it should "for Dotty 0.1.1 return 0.1" in {
    binaryScalaVersion("0.1.1") shouldBe "0.1"
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

  "Disabled" should "have structural equality" in {
    Disabled() shouldBe Disabled()
  }
  "CrossVersion.full" should "have structural equality" in {
    CrossVersion.full shouldBe CrossVersion.full
  }
  "CrossVersion.binary" should "have structural equality" in {
    CrossVersion.binary shouldBe CrossVersion.binary
  }
}
