/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

object CrossSpec extends verify.BasicTestSuite {
  import Cross._

  test("glob filter should work as expected") {
    assert(globFilter("2.13.*", Seq("2.12.8", "2.13.16", "3.0.1")) == Seq("2.13.16"))
    assert(globFilter("3.*", Seq("2.12.8", "2.13.16", "3.0.1")) == Seq("3.0.1"))
    assert(globFilter("3.*", Seq("3.0.1", "30.1")) == Seq("3.0.1"))
    assert(globFilter("2.*", Seq("2.12.8", "2.13.16", "3.0.1")) == Seq("2.12.8", "2.13.16"))
    assert(globFilter("4.*", Seq("2.12.8", "2.13.16", "3.0.1")) == Nil)
  }
}
