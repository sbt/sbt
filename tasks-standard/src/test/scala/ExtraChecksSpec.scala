/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import org.scalacheck._

object ExtraChecksSpec extends Properties("Execute extra checks") {
  // Guards against the forked test configuration in build.sbt silently losing the flag.
  property("sbt.execute.extrachecks is enabled") = Prop(Execute.checkPreAndPostConditions)
}
