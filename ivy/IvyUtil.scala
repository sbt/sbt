package sbt

private[sbt] object IvyUtil
{
	def separate[A,B](l: Seq[Either[A,B]]): (Seq[A], Seq[B]) =
		(l.flatMap(_.left.toOption), l.flatMap(_.right.toOption))
}