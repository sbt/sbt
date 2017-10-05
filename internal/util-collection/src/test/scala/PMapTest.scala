/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt.internal.util

import Types._

// compilation test
object PMapTest {
  val mp = new DelegatingPMap[Some, Id](new collection.mutable.HashMap)
  mp(Some("asdf")) = "a"
  mp(Some(3)) = 9
  val x = Some(3) :^: Some("asdf") :^: KNil
  val y = x.transform[Id](mp)
  assert(y.head == 9)
  assert(y.tail.head == "a")
  assert(y.tail.tail == KNil)
}
