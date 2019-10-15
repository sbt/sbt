package sbt.librarymanagement

import sbt.internal.librarymanagement.UnitSpec
import sjsonnew.support.scalajson.unsafe.{ Converter, CompactPrinter, Parser }

class ModuleIdTest extends UnitSpec {
  val expectedJson =
    """{"organization":"com.acme","name":"foo","revision":"1","isChanging":false,"isTransitive":true,"isForce":false,"explicitArtifacts":[],"inclusions":[],"exclusions":[],"extraAttributes":{},"crossVersion":{"type":"Disabled"}}"""
  "Module Id" should "return cross-disabled module id as equal to a copy" in {
    ModuleID("com.acme", "foo", "1") shouldBe ModuleID("com.acme", "foo", "1")
  }
  it should "return cross-full module id as equal to a copy" in {
    (ModuleID("com.acme", "foo", "1") cross CrossVersion.full) shouldBe
      (ModuleID("com.acme", "foo", "1") cross CrossVersion.full)
  }
  it should "return cross-binary module id as equal to a copy" in {
    (ModuleID("com.acme", "foo", "1") cross CrossVersion.binary) shouldBe
      (ModuleID("com.acme", "foo", "1") cross CrossVersion.binary)
  }
  it should "format itself into JSON" in {
    import LibraryManagementCodec._
    val json = Converter.toJson(ModuleID("com.acme", "foo", "1")).get
    assert(CompactPrinter(json) == expectedJson)
  }
  it should "thaw back from JSON" in {
    import LibraryManagementCodec._
    val json = Parser.parseUnsafe(expectedJson)
    val m = Converter.fromJsonUnsafe[ModuleID](json)
    assert(m == ModuleID("com.acme", "foo", "1"))
  }
}
