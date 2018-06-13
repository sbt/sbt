/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt
package internal
package server

class DefinitionTest extends org.specs2.mutable.Specification {
  import Definition.textProcessor

  "text processor" should {
    "find valid standard scala identifier when caret is set at the start of it" in {
      textProcessor.identifier("val identifier = 0", 4) must beSome("identifier")
    }
    "not find valid standard scala identifier because it is '='" in {
      textProcessor.identifier("val identifier = 0", 15) must beNone
    }
    "find valid standard scala identifier when caret is set in the middle of it" in {
      textProcessor.identifier("val identifier = 0", 11) must beSome("identifier")
    }
    "find valid standard scala identifier with comma" in {
      textProcessor.identifier("def foo(a: identifier, b: other) = ???", 13) must beSome(
        "identifier"
      )
    }
    "find valid standard short scala identifier when caret is set at the start of it" in {
      textProcessor.identifier("val a = 0", 4) must beSome("a")
    }
    "find valid standard short scala identifier when caret is set at the end of it" in {
      textProcessor.identifier("def foo(f: Int) = Foo(f)", 19) must beSome("Foo")
    }
    "find valid non-standard short scala identifier when caret is set at the start of it" in {
      textProcessor.identifier("val == = 0", 4) must beSome("==")
    }
    "find valid non-standard short scala identifier when caret is set in the middle of it" in {
      textProcessor.identifier("val == = 0", 5) must beSome("==")
    }
    "find valid non-standard short scala identifier when caret is set at the end of it" in {
      textProcessor.identifier("val == = 0", 6) must beSome("==")
    }
    "choose longest valid standard scala identifier from scala keyword when caret is set at the start of it" in {
      textProcessor.identifier("val k = 0", 0) must beSome("va") or beSome("al")
    }
    "choose longest valid standard scala identifier from scala keyword when caret is set in the middle of it" in {
      textProcessor.identifier("val k = 0", 1) must beSome("va") or beSome("al")
    }
    "match symbol as class name" in {
      textProcessor.potentialClsOrTraitOrObj("A")("com.acme.A") must be_==("com.acme.A")
    }
    "match symbol as object name" in {
      textProcessor.potentialClsOrTraitOrObj("A")("com.acme.A$") must be_==("com.acme.A$")
    }
    "match symbol as inner class name" in {
      textProcessor.potentialClsOrTraitOrObj("A")("com.acme.A$A") must be_==("com.acme.A$A")
    }
    "match symbol as inner object name" in {
      textProcessor.potentialClsOrTraitOrObj("A")("com.acme.A$A$") must be_==("com.acme.A$A$")
    }
    "match symbol as default package class name" in {
      textProcessor.potentialClsOrTraitOrObj("A")("A") must be_==("A")
    }
    "match symbol as default package object name" in {
      textProcessor.potentialClsOrTraitOrObj("A")("A$") must be_==("A$")
    }
    "match object in line version 1" in {
      textProcessor.classTraitObjectInLine("A")("   object A  ") must contain(("object A", 3))
    }
    "match object in line version 2" in {
      textProcessor.classTraitObjectInLine("A")("   object    A  ") must contain(("object    A", 3))
    }
    "match object in line version 3" in {
      textProcessor.classTraitObjectInLine("A")("object A {") must contain(("object A", 0))
    }
    "not match object in line" in {
      textProcessor.classTraitObjectInLine("B")("object A  ") must be empty
    }
    "match class in line version 1" in {
      textProcessor.classTraitObjectInLine("A")("   class A  ") must contain(("class A", 3))
    }
    "match class in line version 2" in {
      textProcessor.classTraitObjectInLine("A")("   class    A  ") must contain(("class    A", 3))
    }
    "match class in line version 3" in {
      textProcessor.classTraitObjectInLine("A")("class A {") must contain(("class A", 0))
    }
    "match class in line version 4" in {
      textProcessor.classTraitObjectInLine("A")("   class    A[A]  ") must contain(
        ("class    A", 3)
      )
    }
    "match class in line version 5" in {
      textProcessor.classTraitObjectInLine("A")("   class    A  [A] ") must contain(
        ("class    A", 3)
      )
    }
    "match class in line version 6" in {
      textProcessor.classTraitObjectInLine("A")("class A[A[_]] {") must contain(("class A", 0))
    }
    "not match class in line" in {
      textProcessor.classTraitObjectInLine("B")("class A  ") must be empty
    }
    "match trait in line version 1" in {
      textProcessor.classTraitObjectInLine("A")("   trait A  ") must contain(("trait A", 3))
    }
    "match trait in line version 2" in {
      textProcessor.classTraitObjectInLine("A")("   trait    A  ") must contain(("trait    A", 3))
    }
    "match trait in line version 3" in {
      textProcessor.classTraitObjectInLine("A")("trait A {") must contain(("trait A", 0))
    }
    "match trait in line version 4" in {
      textProcessor.classTraitObjectInLine("A")("   trait    A[A]  ") must contain(
        ("trait    A", 3)
      )
    }
    "match trait in line version 5" in {
      textProcessor.classTraitObjectInLine("A")("   trait    A  [A] ") must contain(
        ("trait    A", 3)
      )
    }
    "match trait in line version 6" in {
      textProcessor.classTraitObjectInLine("A")("trait A[A[_]] {") must contain(("trait A", 0))
    }
    "not match trait in line" in {
      textProcessor.classTraitObjectInLine("B")("trait A  ") must be empty
    }
  }

  "definition" should {

    import scalacache.caffeine._
    import scalacache.modes.sync._

    "cache data in cache" in {
      val cache = CaffeineCache[Any]
      val cacheFile = "Test.scala"
      val useBinary = true

      Definition.updateCache(cache)(cacheFile, useBinary)

      val actual = Definition.AnalysesAccess.getFrom(cache)

      actual.get should contain("Test.scala" -> true -> None)
    }

    "replace cache data in cache" in {
      val cache = CaffeineCache[Any]
      val cacheFile = "Test.scala"
      val useBinary = true
      val falseUseBinary = false

      Definition.updateCache(cache)(cacheFile, falseUseBinary)
      Definition.updateCache(cache)(cacheFile, useBinary)

      val actual = Definition.AnalysesAccess.getFrom(cache)

      actual.get should contain("Test.scala" -> true -> None)
    }

    "cache more data in cache" in {
      val cache = CaffeineCache[Any]
      val cacheFile = "Test.scala"
      val useBinary = true
      val otherCacheFile = "OtherTest.scala"
      val otherUseBinary = false

      Definition.updateCache(cache)(otherCacheFile, otherUseBinary)
      Definition.updateCache(cache)(cacheFile, useBinary)

      val actual = Definition.AnalysesAccess.getFrom(cache)

      actual.get should contain("Test.scala" -> true -> None, "OtherTest.scala" -> false -> None)
    }
  }
}
