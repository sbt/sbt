/* sbt -- Simple Build Tool
 * Copyright 2011 Mark Harrah
 */
package sbt

object Util
{
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
	def counted(prefix: String, single: String, plural: String, count: Int): Option[String] =
		count match
		{
			case 0 => None
			case 1 => Some("1 " + prefix + single)
			case x => Some(x.toString + " " + prefix + plural)
		}
}