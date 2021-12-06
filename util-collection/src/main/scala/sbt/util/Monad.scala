/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.util

import scala.annotation.implicitNotFound

@implicitNotFound("Could not find an instance of Monad for ${F}")
trait Monad[F[_]] extends FlatMap[F] with Applicative[F]:
//
end Monad

object Monad:
  given Monad[Option] = OptionInstances.optionMonad
end Monad
