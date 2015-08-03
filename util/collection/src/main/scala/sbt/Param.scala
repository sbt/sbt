/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package sbt

import Types._

// Used to emulate ~> literals
trait Param[A[_], B[_]] {
  type T
  def in: A[T]
  def ret(out: B[T])
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
