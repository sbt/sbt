/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt.internal.util

object Classes {
  trait Applicative[M[_]] {
    def apply[S, T](f: M[S => T], v: M[S]): M[T]
    def pure[S](s: => S): M[S]
    def map[S, T](f: S => T, v: M[S]): M[T]
  }

  trait Monad[M[_]] extends Applicative[M] {
    def flatten[T](m: M[M[T]]): M[T]
  }

  implicit val optionMonad: Monad[Option] = new Monad[Option] {
    def apply[S, T](f: Option[S => T], v: Option[S]) = (f, v) match {
      case (Some(fv), Some(vv)) => Some(fv(vv))
      case _                    => None
    }

    def pure[S](s: => S) = Some(s)
    def map[S, T](f: S => T, v: Option[S]) = v map f
    def flatten[T](m: Option[Option[T]]): Option[T] = m.flatten
  }

  implicit val listMonad: Monad[List] = new Monad[List] {
    def apply[S, T](f: List[S => T], v: List[S]) = for (fv <- f; vv <- v) yield fv(vv)
    def pure[S](s: => S) = s :: Nil
    def map[S, T](f: S => T, v: List[S]) = v map f
    def flatten[T](m: List[List[T]]): List[T] = m.flatten
  }
}
