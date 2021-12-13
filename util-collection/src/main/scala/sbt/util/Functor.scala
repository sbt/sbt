/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.util

trait Functor[F[_]]:
  def map[A, B](fa: F[A])(f: A => B): F[B]
end Functor

object Functor:
  given Functor[Option] = OptionInstances.optionMonad
  given Functor[List] = ListInstances.listMonad
end Functor
