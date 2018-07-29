/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt.internal

import org.specs2.mutable.Specification

class AggregationSpec extends Specification {
  val timing = Aggregation.timing(Aggregation.defaultFormat, 0, _: Long)

  "timing" should {
    "format total time properly" in {
      timing(101) must be startWith "Total time: 0 s,"
      timing(1000) must be startWith "Total time: 1 s,"
      timing(3000) must be startWith "Total time: 3 s,"
      timing(30399) must be startWith "Total time: 30 s,"
      timing(60399) must be startWith "Total time: 60 s,"
      timing(60699) must be startWith "Total time: 61 s (01:01),"
      timing(303099) must be startWith "Total time: 303 s (05:03),"
      timing(6003099) must be startWith "Total time: 6003 s (01:40:03),"
      timing(96003099) must be startWith "Total time: 96003 s (26:40:03),"
    }
  }

}
