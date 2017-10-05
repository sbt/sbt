/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt.internal.util

object Types extends Types

trait Types extends TypeFunctions {
  val :^: = KCons
  type :+:[H, T <: HList] = HCons[H, T]
  val :+: = HCons
}
