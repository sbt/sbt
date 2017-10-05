/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt.util

trait Show[A] {
  def show(a: A): String
}
object Show {
  def apply[A](f: A => String): Show[A] = new Show[A] { def show(a: A): String = f(a) }

  def fromToString[A]: Show[A] = new Show[A] {
    def show(a: A): String = a.toString
  }
}
