/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt.internal.util

// Used to emulate ~> literals
trait Param[A[_], B[_]] {
  type T
  def in: A[T]
  def ret(out: B[T]): Unit
  def ret: B[T]
}

object Param {
  implicit def pToT[A[_], B[_]](p: Param[A, B] => Unit): A ~> B = new (A ~> B) {
    def apply[s](a: A[s]): B[s] = {
      val v: Param[A, B] { type T = s } = new Param[A, B] {
        type T = s
        def in = a
        private var r: B[T] = _
        def ret(b: B[T]): Unit = { r = b }
        def ret: B[T] = r
      }
      p(v)
      v.ret
    }
  }
}
