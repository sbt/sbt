/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util

object Types extends Types

trait Types extends TypeFunctions {
  val :^: = KCons
  type :+:[H, T <: HList] = HCons[H, T]
  val :+: = HCons
}
