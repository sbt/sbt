/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt

import sbt.internal.util.~>

// used instead of Either[Incomplete, T] for type inference

/** Result of completely evaluating a task.*/
sealed trait Result[+T] {
  def toEither: Either[Incomplete, T]
}

/** Indicates the task did not complete normally and so it does not have a value.*/
final case class Inc(cause: Incomplete) extends Result[Nothing] {
  def toEither: Either[Incomplete, Nothing] = Left(cause)
}

/** Indicates the task completed normally and produced the given `value`.*/
final case class Value[+T](value: T) extends Result[T] {
  def toEither: Either[Incomplete, T] = Right(value)
}

object Result {
  type Id[X] = X
  val tryValue = λ[Result ~> Id] {
    case Value(v) => v
    case Inc(i)   => throw i
  }
  def tryValues[S](r: Seq[Result[Unit]], v: Result[S]): S = {
    r foreach tryValue[Unit]
    tryValue[S](v)
  }
  implicit def fromEither[T](e: Either[Incomplete, T]): Result[T] = e match {
    case Left(i)  => Inc(i)
    case Right(v) => Value(v)
  }
}
