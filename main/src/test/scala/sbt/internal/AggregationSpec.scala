/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal

object AggregationSpec extends verify.BasicTestSuite {
  val timing = Aggregation.timing(Aggregation.defaultFormat, 0, _: Long)

  test("timing should format total time properly") {
    assert(timing(101).startsWith("elapsed time: 0 s"))
    assert(timing(1000).startsWith("elapsed time: 1 s"))
    assert(timing(3000).startsWith("elapsed time: 3 s"))
    assert(timing(30399).startsWith("elapsed time: 30 s"))
    assert(timing(60399).startsWith("elapsed time: 60 s"))
    assert(timing(60699).startsWith("elapsed time: 61 s (01:01)"))
    assert(timing(303099).startsWith("elapsed time: 303 s (05:03)"))
    assert(timing(6003099).startsWith("elapsed time: 6003 s (01:40:03)"))
    assert(timing(96003099).startsWith("elapsed time: 96003 s (26:40:03)"))
  }
}
