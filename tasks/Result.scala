/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package sbt

// used instead of Either[Throwable, T] for type inference
sealed trait Result[+T]
final case class Exc(cause: Throwable) extends Result[Nothing]
final case class Value[+T](value: T) extends Result[T]