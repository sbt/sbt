/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.util

private[sbt] object OptionInstances:
  lazy val optionMonad: Monad[Option] =
    new Monad[Option]:
      type F[a] = Option[a]

      def pure[A](x: () => A): Option[A] = Some(x())
      def ap[A, B](ff: Option[A => B])(fa: Option[A]): Option[B] =
        if ff.isDefined && fa.isDefined then Some(ff.get(fa.get))
        else None
      def flatMap[A, B](fa: Option[A])(f: A => Option[B]): Option[B] = fa.flatMap(f)

      override def map[A, B](fa: Option[A])(f: A => B): Option[B] = fa.map(f)
      override def flatten[A](ffa: Option[Option[A]]): Option[A] = ffa.flatten
end OptionInstances
