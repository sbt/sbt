/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

object DefaultsTest extends verify.BasicTestSuite {

  test("`selectedFilter` should return all tests for an empty list") {
    val expected = Map("Test1" -> true, "Test2" -> true)
    val filter = List.empty[String]
    assert(
      expected.map(t => (t._1, Defaults.selectedFilter(filter).exists(fn => fn(t._1)))) == expected
    )
  }

  test("it should work correctly with exact matches") {
    val expected = Map("Test1" -> true, "Test2" -> false, "Foo" -> false)
    val filter = List("Test1", "foo")
    assert(
      expected.map(t => (t._1, Defaults.selectedFilter(filter).exists(fn => fn(t._1)))) == expected
    )
  }

  test("it should work correctly with glob") {
    val expected = Map("Test1" -> true, "Test2" -> true, "Foo" -> false)
    val filter = List("Test*")
    assert(
      expected.map(t => (t._1, Defaults.selectedFilter(filter).exists(fn => fn(t._1)))) == expected
    )
  }

  test("it should work correctly with excludes") {
    val expected = Map("Test1" -> true, "Test2" -> false, "Foo" -> false)
    val filter = List("Test*", "-Test2")
    assert(
      expected.map(t => (t._1, Defaults.selectedFilter(filter).exists(fn => fn(t._1)))) == expected
    )
  }

  test("it should work correctly without includes") {
    val expected = Map("Test1" -> true, "Test2" -> false, "Foo" -> true)
    val filter = List("-Test2")
    assert(
      expected.map(t => (t._1, Defaults.selectedFilter(filter).exists(fn => fn(t._1)))) == expected
    )
  }

  test("it should work correctly with excluded globs") {
    val expected = Map("Test1" -> true, "Test2" -> true, "Foo" -> false)
    val filter = List("Test*", "-F*")
    assert(
      expected.map(t => (t._1, Defaults.selectedFilter(filter).exists(fn => fn(t._1)))) == expected
    )
  }

  test("it should cope with multiple filters") {
    val expected = Map("Test1" -> true, "Test2" -> true, "Foo" -> false)
    val filter = List("T*1", "T*2", "-F*")
    assert(
      expected.map(t => (t._1, Defaults.selectedFilter(filter).exists(fn => fn(t._1)))) == expected
    )
  }

  test("it should cope with multiple exclusion filters, no includes") {
    val expected = Map("Test1" -> true, "Test2" -> true, "AAA" -> false, "Foo" -> false)
    val filter = List("-A*", "-F*")
    assert(
      expected.map(t => (t._1, Defaults.selectedFilter(filter).exists(fn => fn(t._1)))) == expected
    )
  }

  test("it should cope with multiple exclusion filters with includes") {
    val expected = Map("Test1" -> false, "Test2" -> false, "Test3" -> true)
    val filter = List("T*", "-T*1", "-T*2")
    assert(
      expected.map(t => (t._1, Defaults.selectedFilter(filter).exists(fn => fn(t._1)))) == expected
    )
  }
}
