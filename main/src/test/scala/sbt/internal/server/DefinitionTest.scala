/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal
package server

import com.github.benmanes.caffeine.cache.Caffeine
import sbt.internal.inc.Analysis

object DefinitionTest extends verify.BasicTestSuite {
  import Definition.textProcessor
  test(
    "text processor should find valid standard scala identifier when caret is set at the start of it"
  ) {
    assert(textProcessor.identifier("val identifier = 0", 4) == Some("identifier"))
  }

  test("it should not find valid standard scala identifier because it is '='") {
    assert(textProcessor.identifier("val identifier = 0", 15) == None)
  }

  test("it should find valid standard scala identifier when caret is set in the middle of it") {
    assert(textProcessor.identifier("val identifier = 0", 11) == Some("identifier"))
  }

  test("it should find valid standard scala identifier with comma") {
    assert(
      textProcessor.identifier("def foo(a: identifier, b: other) = ???", 13) == Some("identifier")
    )
  }

  test("it should find valid standard short scala identifier when caret is set at the start of it") {
    assert(textProcessor.identifier("val a = 0", 4) == Some("a"))
  }

  test("it should find valid standard short scala identifier when caret is set at the end of it") {
    assert(textProcessor.identifier("def foo(f: Int) = Foo(f)", 19) == Some("Foo"))
  }

  test(
    "it should find valid non-standard short scala identifier when caret is set at the start of it"
  ) {
    assert(textProcessor.identifier("val == = 0", 4) == Some("=="))
  }

  test(
    "it should find valid non-standard short scala identifier when caret is set in the middle of it"
  ) {
    assert(textProcessor.identifier("val == = 0", 5) == Some("=="))
  }

  test(
    "it should find valid non-standard short scala identifier when caret is set at the end of it"
  ) {
    assert(textProcessor.identifier("val == = 0", 6) == Some("=="))
  }

  test(
    "it should choose longest valid standard scala identifier from scala keyword when caret is set at the start of it"
  ) {
    assert(
      textProcessor.identifier("val k = 0", 0) == Some("va") || textProcessor
        .identifier("val k = 0", 0) == Some("al")
    )
  }

  test(
    "it should choose longest valid standard scala identifier from scala keyword when caret is set in the middle of it"
  ) {
    assert(
      textProcessor.identifier("val k = 0", 1) == Some("va") || textProcessor
        .identifier("val k = 0", 1) == Some("al")
    )
  }

  test("it should match symbol as class name") {
    assert(textProcessor.potentialClsOrTraitOrObj("A")("com.acme.A") == "com.acme.A")
  }

  test("it should match symbol as object name") {
    assert(textProcessor.potentialClsOrTraitOrObj("A")("com.acme.A$") == "com.acme.A$")
  }

  test("it should match symbol as inner class name") {
    assert(textProcessor.potentialClsOrTraitOrObj("A")("com.acme.A$A") == "com.acme.A$A")
  }

  test("it should match symbol as inner object name") {
    assert(textProcessor.potentialClsOrTraitOrObj("A")("com.acme.A$A$") == "com.acme.A$A$")
  }

  test("it should match symbol as default package class name") {
    assert(textProcessor.potentialClsOrTraitOrObj("A")("A") == "A")
  }

  test("it should match symbol as default package object name") {
    assert(textProcessor.potentialClsOrTraitOrObj("A")("A$") == "A$")
  }

  test("it should match object in line version 1") {
    assert(textProcessor.classTraitObjectInLine("A")("   object A  ").contains(("object A", 3)))
  }

  test("it should match object in line version 2") {
    assert(
      textProcessor.classTraitObjectInLine("A")("   object    A  ").contains(("object    A", 3))
    )
  }

  test("it should match object in line version 3") {
    assert(textProcessor.classTraitObjectInLine("A")("object A {").contains(("object A", 0)))
  }

  test("it should not match object in line") {
    assert(textProcessor.classTraitObjectInLine("B")("object A  ").isEmpty)
  }

  test("it should match class in line version 1") {
    assert(textProcessor.classTraitObjectInLine("A")("   class A  ").contains(("class A", 3)))
  }

  test("it should match class in line version 2") {
    assert(textProcessor.classTraitObjectInLine("A")("   class    A  ").contains(("class    A", 3)))
  }

  test("it should match class in line version 3") {
    assert(textProcessor.classTraitObjectInLine("A")("class A {").contains(("class A", 0)))
  }

  test("it should match class in line version 4") {
    assert(
      textProcessor.classTraitObjectInLine("A")("   class    A[A]  ").contains(("class    A", 3))
    )
  }

  test("it should match class in line version 5") {
    assert(
      textProcessor
        .classTraitObjectInLine("A")("   class    A  [A] ")
        .contains(
          ("class    A", 3)
        )
    )
  }

  test("it should match class in line version 6") {
    assert(textProcessor.classTraitObjectInLine("A")("class A[A[_]] {").contains(("class A", 0)))
  }

  test("it should not match class in line") {
    assert(textProcessor.classTraitObjectInLine("B")("class A  ").isEmpty)
  }

  test("match trait in line version 1") {
    assert(textProcessor.classTraitObjectInLine("A")("   trait A  ").contains(("trait A", 3)))
  }

  test("it should match trait in line version 2") {
    assert(textProcessor.classTraitObjectInLine("A")("   trait    A  ").contains(("trait    A", 3)))
  }

  test("it should match trait in line version 3") {
    assert(textProcessor.classTraitObjectInLine("A")("trait A {").contains(("trait A", 0)))
  }

  test("it should match trait in line version 4") {
    assert(
      textProcessor
        .classTraitObjectInLine("A")("   trait    A[A]  ")
        .contains(
          ("trait    A", 3)
        )
    )
  }

  test("it should match trait in line version 5") {
    assert(
      textProcessor
        .classTraitObjectInLine("A")("   trait    A  [A] ")
        .contains(
          ("trait    A", 3)
        )
    )
  }

  test("it should match trait in line version 6") {
    assert(textProcessor.classTraitObjectInLine("A")("trait A[A[_]] {").contains(("trait A", 0)))
  }

  test("it should not match trait in line") {
    assert(textProcessor.classTraitObjectInLine("B")("trait A  ").isEmpty)
  }

  test("definition should cache data in cache") {
    val cache = Caffeine.newBuilder().build[String, Definition.Analyses]()
    val cacheFile = "Test.scala"
    val useBinary = true
    val useConsistent = true

    Definition.updateCache(cache)(cacheFile, useBinary, useConsistent)

    val actual = Definition.AnalysesAccess.getFrom(cache)

    assert(actual.get.contains((("Test.scala", true, true) -> None)))
  }

  test("it should replace cache data in cache") {
    val cache = Caffeine.newBuilder().build[String, Definition.Analyses]()
    val cacheFile = "Test.scala"
    val useBinary = true
    val falseUseBinary = false
    val useConsistent = true

    Definition.updateCache(cache)(cacheFile, falseUseBinary, useConsistent)
    Definition.updateCache(cache)(cacheFile, useBinary, useConsistent)

    val actual = Definition.AnalysesAccess.getFrom(cache)

    assert(actual.get.contains((("Test.scala", true, true) -> None)))
  }

  test("it should cache more data in cache") {
    val cache = Caffeine.newBuilder().build[String, Definition.Analyses]()
    val cacheFile = "Test.scala"
    val useBinary = true
    val otherCacheFile = "OtherTest.scala"
    val otherUseBinary = false
    val useConsistent = true

    Definition.updateCache(cache)(otherCacheFile, otherUseBinary, useConsistent)
    Definition.updateCache(cache)(cacheFile, useBinary, useConsistent)

    val actual = Definition.AnalysesAccess.getFrom(cache)

    assert(
      actual.get.contains(("Test.scala", true, true) -> Option.empty[Analysis]) &&
        actual.get.contains(("OtherTest.scala", false, true) -> Option.empty[Analysis])
    )
  }
}
