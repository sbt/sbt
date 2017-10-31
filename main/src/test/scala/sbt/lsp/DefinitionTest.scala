/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt.lsp

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
    "find valid standard short scala identifier when caret is set at the start of it" in {
      textProcessor.identifier("val a = 0", 4) must beSome("a")
    }
    "find valid standard short scala identifier when caret is set at the end of it" in {
      textProcessor.identifier("val a = 0", 5) must beSome("a")
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
    "turn symbol into sequence of potential suffices of jvm class name" in {
      textProcessor.asClassObjectIdentifier("A") must contain(".A", ".A$", "$A", "$A$")
    }
  }
}
