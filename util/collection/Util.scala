/* sbt -- Simple Build Tool
 * Copyright 2011 Mark Harrah
 */
package sbt

object Util
{
	def separateE[A,B](ps: Seq[Either[A,B]]): (Seq[A], Seq[B]) =
		separate(ps)(Types.idFun)

	def separate[T,A,B](ps: Seq[T])(f: T => Either[A,B]): (Seq[A], Seq[B]) =
	{
		val (a,b) = ((Nil: Seq[A], Nil: Seq[B]) /: ps)( (xs, y) => prependEither(xs, f(y)) )
		(a.reverse, b.reverse)
	}

	def prependEither[A,B](acc: (Seq[A], Seq[B]), next: Either[A,B]): (Seq[A], Seq[B]) =
		next match
		{
			case Left(l) => (l +: acc._1, acc._2)
			case Right(r) => (acc._1, r +: acc._2)
		}

	def pairID[A,B] = (a: A, b: B) => (a,b)
}
