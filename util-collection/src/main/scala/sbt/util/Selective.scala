/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.util

trait Selective[F[_]] extends Applicative[F]:
  def select[A, B](fab: F[Either[A, B]])(fn: F[A => B]): F[B]
end Selective
