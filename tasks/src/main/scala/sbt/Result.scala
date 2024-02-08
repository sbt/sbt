/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

// used instead of Either[Incomplete, T] for type inference

/** Result of completely evaluating a task. */
enum Result[+A]:
  /** Indicates the task did not complete normally and so it does not have a value. */
  case Inc(cause: Incomplete) extends Result[Nothing]

  /** Indicates the task completed normally and produced the given `value`. */
  case Value[+A](value: A) extends Result[A]

  def toEither: Either[Incomplete, A] = this match
    case Inc(cause)   => Left(cause)
    case Value(value) => Right(value)
end Result

object Result {
  type Id[X] = X
  val tryValue: [A] => Result[A] => A =
    [A] =>
      (r: Result[A]) =>
        r match
          case Result.Value(v) => v
          case Result.Inc(i)   => throw i

  def tryValues[S](r: Seq[Result[Unit]], v: Result[S]): S = {
    r foreach tryValue[Unit]
    tryValue[S](v)
  }
  implicit def fromEither[T](e: Either[Incomplete, T]): Result[T] = e match {
    case Left(i)  => Inc(i)
    case Right(v) => Value(v)
  }
}
