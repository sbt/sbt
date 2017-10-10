/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt.internal.util

// compilation test
object LiteralTest {
  def x[A[_], B[_]](f: A ~> B) = f

  import Param._
  val f = x { (p: Param[Option, List]) =>
    p.ret(p.in.toList)
  }

  val a: List[Int] = f(Some(3))
  val b: List[String] = f(Some("aa"))
}
