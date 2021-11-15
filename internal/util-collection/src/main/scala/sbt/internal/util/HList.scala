/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util

import Types._

/**
 * A minimal heterogeneous list type.  For background, see
 * https://apocalisp.wordpress.com/2010/07/06/type-level-programming-in-scala-part-6a-heterogeneous-list basics/
 */
sealed trait HList {
  type Wrap[M[_]] <: HList
}

sealed trait HNil extends HList {
  type Wrap[M[_]] = HNil
  def :+:[G](g: G): G :+: HNil = HCons(g, this)

  override def toString = "HNil"
}

object HNil extends HNil

final case class HCons[H, T <: HList](head: H, tail: T) extends HList {
  type Wrap[M[_]] = M[H] :+: T#Wrap[M]
  def :+:[G](g: G): G :+: H :+: T = HCons(g, this)

  override def toString = head.toString + " :+: " + tail.toString
}

object HList {
  // contains no type information: not even A
  implicit def fromList[A](list: Traversable[A]): HList =
    list.foldLeft(HNil: HList)((hl, v) => HCons(v, hl))
}
