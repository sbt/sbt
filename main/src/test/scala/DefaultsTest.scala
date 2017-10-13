/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt

import org.specs2.mutable.Specification

object DefaultsTest extends Specification {
  private def assertFiltered(filter: List[String], expected: Map[String, Boolean]) = {
    val actual = expected.map(t => (t._1, Defaults.selectedFilter(filter).exists(fn => fn(t._1))))

    actual must be equalTo (expected)
  }

  "`selectedFilter`" should {
    "return all tests for an empty list" in {
      assertFiltered(List(), Map("Test1" -> true, "Test2" -> true))
    }

    "work correctly with exact matches" in {
      assertFiltered(List("Test1", "foo"), Map("Test1" -> true, "Test2" -> false, "Foo" -> false))
    }

    "work correctly with glob" in {
      assertFiltered(List("Test*"), Map("Test1" -> true, "Test2" -> true, "Foo" -> false))
    }

    "work correctly with excludes" in {
      assertFiltered(List("Test*", "-Test2"),
                     Map("Test1" -> true, "Test2" -> false, "Foo" -> false))
    }

    "work correctly without includes" in {
      assertFiltered(List("-Test2"), Map("Test1" -> true, "Test2" -> false, "Foo" -> true))
    }

    "work correctly with excluded globs" in {
      assertFiltered(List("Test*", "-F*"), Map("Test1" -> true, "Test2" -> true, "Foo" -> false))
    }

    "cope with multiple filters" in {
      assertFiltered(List("T*1", "T*2", "-F*"),
                     Map("Test1" -> true, "Test2" -> true, "Foo" -> false))
    }

    "cope with multiple exclusion filters, no includes" in {
      assertFiltered(List("-A*", "-F*"),
                     Map("Test1" -> true, "Test2" -> true, "AAA" -> false, "Foo" -> false))
    }

    "cope with multiple exclusion filters with includes" in {
      assertFiltered(List("T*", "-T*1", "-T*2"),
                     Map("Test1" -> false, "Test2" -> false, "Test3" -> true))
    }
  }

}
