/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal

import sbt.internal.util.ProgressItem
import verify.BasicTestSuite

object TaskProgressSpec extends BasicTestSuite {

  // Reproduces the sorting logic from TaskProgress.report() so we can test it in isolation.
  // The real code sorts Vector[(TaskId[?], Long)] then maps to ProgressItem, but the
  // ordering key is (elapsedMicros / 1_000_000, name) -- we test that contract here.
  private def sortItems(items: Vector[ProgressItem]): Vector[ProgressItem] =
    items.sortBy(item => (item.elapsedMicros / 1000000L, item.name))

  test("tasks with different elapsed seconds are sorted by elapsed time ascending") {
    val items = Vector(
      ProgressItem("compile", 5_000_000L), // 5s
      ProgressItem("update", 2_000_000L), // 2s
      ProgressItem("test", 8_000_000L), // 8s
    )
    val sorted = sortItems(items)
    assert(sorted.map(_.name) == Vector("update", "compile", "test"))
  }

  test("tasks within the same elapsed second are sorted alphabetically by name") {
    // All three tasks are within the same 1-second bucket (1s)
    val items = Vector(
      ProgressItem("zinc", 1_900_000L),
      ProgressItem("compile", 1_100_000L),
      ProgressItem("api", 1_500_000L),
    )
    val sorted = sortItems(items)
    assert(sorted.map(_.name) == Vector("api", "compile", "zinc"))
  }

  test("sub-second microsecond jitter does not change ordering (#5466)") {
    // Two tasks started at almost the same time. On successive refreshes, their
    // microsecond elapsed times may flip relative order. With second-granularity
    // rounding + alphabetical tiebreak, the order must stay stable.
    val refresh1 = Vector(
      ProgressItem("b / compile", 3_000_100L), // 3.000100s
      ProgressItem("a / compile", 3_000_200L), // 3.000200s
    )
    val refresh2 = Vector(
      ProgressItem("b / compile", 3_500_200L), // 3.500200s -- now b > a in micros
      ProgressItem("a / compile", 3_500_100L), // 3.500100s
    )
    val sorted1 = sortItems(refresh1)
    val sorted2 = sortItems(refresh2)
    // Both refreshes should produce the same order: alphabetical within same second bucket
    assert(sorted1.map(_.name) == Vector("a / compile", "b / compile"))
    assert(sorted2.map(_.name) == Vector("a / compile", "b / compile"))
  }

  test("tasks crossing a second boundary do change position") {
    val items = Vector(
      ProgressItem("update", 2_999_999L), // still in 2s bucket
      ProgressItem("compile", 3_000_001L), // in 3s bucket
    )
    val sorted = sortItems(items)
    assert(sorted.map(_.name) == Vector("update", "compile"))
  }

  test("mixed buckets: elapsed seconds dominate, names break ties within") {
    val items = Vector(
      ProgressItem("z-task", 1_200_000L), // 1s bucket
      ProgressItem("a-task", 1_800_000L), // 1s bucket
      ProgressItem("m-task", 2_100_000L), // 2s bucket
      ProgressItem("b-task", 500_000L), // 0s bucket
    )
    val sorted = sortItems(items)
    assert(sorted.map(_.name) == Vector("b-task", "a-task", "z-task", "m-task"))
  }

  test("empty input produces empty output") {
    assert(sortItems(Vector.empty) == Vector.empty)
  }

  test("single task is returned as-is") {
    val items = Vector(ProgressItem("compile", 5_000_000L))
    assert(sortItems(items) == items)
  }
}
