/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.util

import scala.annotation.implicitNotFound

@implicitNotFound("Could not find an instance of FlatMap for ${F}")
trait FlatMap[F[_]] extends Apply[F]:
  def flatMap[A, B](fa: F[A])(f: A => F[B]): F[B]

  def flatten[A](ffa: F[F[A]]): F[A] =
    flatMap(ffa)(fa => fa)
end FlatMap

object FlatMap:
  given FlatMap[Option] = OptionInstances.optionMonad
  given FlatMap[List] = ListInstances.listMonad
end FlatMap
