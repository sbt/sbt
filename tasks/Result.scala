/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package sbt

// used instead of Either[Incomplete, T] for type inference

/** Result of completely evaluating a task.*/
sealed trait Result[+T]
/** Indicates the task did not complete normally and so it does not have a value.*/
final case class Inc(cause: Incomplete) extends Result[Nothing]
/** Indicates the task completed normally and produced the given `value`.*/
final case class Value[+T](value: T) extends Result[T]

object Result
{
	type Id[X] = X
	val tryValue = new (Result ~> Id) {
		def apply[T](r: Result[T]): T =
			r match {
				case Value(v) => v
				case Inc(i) => throw i
			}
	}
}