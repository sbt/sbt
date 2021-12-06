/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.util

private[sbt] object ListInstances:
  lazy val listMonad: Monad[List] =
    new Monad[List]:
      def pure[A](x: A): List[A] = List(x)
      def ap[A, B](ff: List[A => B])(fa: List[A]): List[B] =
        for
          f <- ff
          a <- fa
        yield f(a)
      def flatMap[A, B](fa: List[A])(f: A => List[B]): List[B] = fa.flatMap(f)

      override def map[A, B](fa: List[A])(f: A => B): List[B] = fa.map(f)
      override def flatten[A](ffa: List[List[A]]): List[A] = ffa.flatten
end ListInstances
