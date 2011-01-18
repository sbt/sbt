/* sbt -- Simple Build Tool
 * Copyright 2011 Mark Harrah
 */
package sbt

object Collections
{
	def separate[T,A,B](ps: Seq[T])(f: T => Either[A,B]): (Seq[A], Seq[B]) =
		((Nil: Seq[A], Nil: Seq[B]) /: ps)( (xs, y) => prependEither(xs, f(y)) )

	def prependEither[A,B](acc: (Seq[A], Seq[B]), next: Either[A,B]): (Seq[A], Seq[B]) =
		next match
		{
			case Left(l) => (l +: acc._1, acc._2)
			case Right(r) => (acc._1, r +: acc._2)
		}
}