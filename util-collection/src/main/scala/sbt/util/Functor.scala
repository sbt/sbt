/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.util

trait Functor[F[_]]:
  def map[A1, A2](fa: F[A1])(f: A1 => A2): F[A2]
end Functor

object Functor:
  given Functor[Option] = OptionInstances.optionMonad
  given Functor[List] = ListInstances.listMonad
end Functor
