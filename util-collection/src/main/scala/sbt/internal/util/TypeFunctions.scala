/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util

trait TypeFunctions:
  type Id[X] = X
  type NothingK[X] = Nothing

  final val left: [A] => A => Left[A, Nothing] = [A] => (a: A) => Left(a)

  final val right: [A] => A => Right[Nothing, A] = [A] => (a: A) => Right(a)

  final val some: [A] => A => Some[A] = [A] => (a: A) => Some(a)

  final def idFun[A]: A => A = ((a: A) => a)
  final def const[A, B](b: B): A => B = ((_: A) => b)

end TypeFunctions
