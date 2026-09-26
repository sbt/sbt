/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

import java.io.File

import org.scalatest.FunSuite

class CacheTest extends FunSuite {
  test("record execution") {
    val count = Iterator.from(1).find(i => !new File(s"run-$i").exists).get
    assert(new File(s"run-$count").createNewFile())
  }
}
