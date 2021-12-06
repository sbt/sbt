/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.util

trait Applicative[F[_]] extends Apply[F]:
  def pure[A](x: A): F[A]

  override def map[A, B](fa: F[A])(f: A => B): F[B] =
    ap(pure(f))(fa)
end Applicative

object Applicative:
  given Applicative[Option] = OptionInstances.optionMonad
end Applicative
