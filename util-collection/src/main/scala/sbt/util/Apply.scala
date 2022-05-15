/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.util

trait Apply[F[_]] extends Functor[F]:
  def ap[A1, A2](ff: F[A1 => A2])(fa: F[A1]): F[A2]

  def product[A1, A2](fa: F[A1], fb: F[A2]): F[(A1, A2)] =
    ap(map(fa)(a => (b: A2) => (a, b)))(fb)
end Apply

object Apply:
  given Apply[Option] = OptionInstances.optionMonad
  given Apply[List] = ListInstances.listMonad
end Apply
