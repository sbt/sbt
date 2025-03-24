package sbt.librarymanagement

import sjsonnew.support.scalajson.unsafe.{ Converter, CompactPrinter, Parser }

object ModuleIdTest extends verify.BasicTestSuite {
  test("Module Id should return cross-disabled module id as equal to a copy") {
    assert(ModuleID("com.acme", "foo", "1") == ModuleID("com.acme", "foo", "1"))
  }

  test("it should return cross-full module id as equal to a copy") {
    assert(
      ModuleID("com.acme", "foo", "1").cross(CrossVersion.full) ==
        ModuleID("com.acme", "foo", "1").cross(CrossVersion.full)
    )
  }

  test("it should return cross-binary module id as equal to a copy") {
    assert(
      ModuleID("com.acme", "foo", "1").cross(CrossVersion.binary) ==
        ModuleID("com.acme", "foo", "1").cross(CrossVersion.binary)
    )
  }

  test("it should format itself into JSON") {
    import LibraryManagementCodec.*
    val json = Converter.toJson(ModuleID("com.acme", "foo", "1")).get
    assert(CompactPrinter(json) == expectedJson)
  }

  test("it should thaw back from JSON") {
    import LibraryManagementCodec.*
    val json = Parser.parseUnsafe(expectedJson)
    val m = Converter.fromJsonUnsafe[ModuleID](json)
    assert(m == ModuleID("com.acme", "foo", "1"))
  }

  test("cross(...) should compose prefix with the existing value") {
    assert(
      ModuleID("com.acme", "foo", "1")
        .cross(CrossVersion.binaryWith("sjs1_", ""))
        .cross(CrossVersion.for3Use2_13)
        ==
          ModuleID("com.acme", "foo", "1").cross(CrossVersion.for3Use2_13With("sjs1_", ""))
    )
  }

  def expectedJson =
    """{"organization":"com.acme","name":"foo","revision":"1","isChanging":false,"isTransitive":true,"isForce":false,"explicitArtifacts":[],"inclusions":[],"exclusions":[],"extraAttributes":{},"crossVersion":{"type":"Disabled"}}"""
}
