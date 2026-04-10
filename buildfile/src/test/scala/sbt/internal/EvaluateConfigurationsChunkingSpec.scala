/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal

import sbt.internal.util.LineRange

object EvaluateConfigurationsChunkingSpec extends verify.BasicTestSuite:

  test("partitions by definition count (sbt/sbt#3057)"):
    val defs =
      ((0 until 105)
        .map: i =>
          (s"lazy val x$i = ()", LineRange(i, i)))
        .toList
    val parts = EvaluateConfigurations.partitionDefinitionRanges(defs)
    assert(parts.size == 2)
    assert(parts.head.size == 100)
    assert(parts(1).size == 5)
    assert(parts.map(_.size).sum == 105)

  test("small definition lists stay in one partition"):
    val defs =
      ((0 until 5)
        .map: i =>
          (s"lazy val x$i = ()", LineRange(i, i)))
        .toList
    val parts = EvaluateConfigurations.partitionDefinitionRanges(defs)
    assert(parts.size == 1)
    assert(parts.head.size == 5)

  test("partitions when character budget is exceeded before count limit"):
    val d0 = ("lazy val a = 1", LineRange(0, 0))
    val padding = " " * 13000
    val d1 = (s"lazy val b = 1$padding", LineRange(1, 1))
    val parts = EvaluateConfigurations.partitionDefinitionRanges(List(d0, d1))
    assert(parts.size == 2)
    assert(parts(0).size == 1)
    assert(parts(1).size == 1)

  test("a single oversized definition is not split"):
    val padding = " " * 20000
    val d0 = (s"lazy val huge = 1$padding", LineRange(0, 0))
    val parts = EvaluateConfigurations.partitionDefinitionRanges(List(d0))
    assert(parts.size == 1)
    assert(parts.head.size == 1)

end EvaluateConfigurationsChunkingSpec
