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
  def flatMap[A1, A2](fa: F[A1])(f: A1 => F[A2]): F[A2]

  def flatten[A1](ffa: F[F[A1]]): F[A1] =
    flatMap(ffa)(fa => fa)
end FlatMap

object FlatMap:
  given FlatMap[Option] = OptionInstances.optionMonad
  given FlatMap[List] = ListInstances.listMonad
end FlatMap
