package sbt

object Classes
{
	trait Applicative[M[_]]
	{
		def apply2[S,T,U](s: M[S], t: M[T])(f: (S,T) => U): M[U]
		def apply[S,T](f: M[S => T], v: M[S]): M[T]
		def pure[S](s: => S): M[S]
		def map[S, T](f: S => T, v: M[S]): M[T]
	}
	trait Monad[M[_]] extends Applicative[M]
	{
		def flatten[T](m: M[M[T]]): M[T]
	}
	implicit val optionMonad: Monad[Option] = new Monad[Option] {
		def apply[S,T](f: Option[S => T], v: Option[S]) = apply2(f, v)(app2)
		def apply2[S,T,U](s: Option[S], t: Option[T])(f: (S,T) => U): Option[U] = (s, t) match {
			case (Some(sv), Some(tv)) => Some(f(sv,tv))
			case _ => None
		}
		def pure[S](s: => S) = Some(s)
		def map[S, T](f: S => T, v: Option[S]) = v map f
		def flatten[T](m: Option[Option[T]]): Option[T] = m.flatten
	}
	implicit val listMonad: Monad[List] =  new Monad[List] {
		def apply[S,T](f: List[S => T], v: List[S]) = apply2(f, v)(app2)
		def apply2[S,T,U](s: List[S], t: List[T])(f: (S,T) => U) = for(sv <- s; tv <- t) yield f(sv,tv)
		def pure[S](s: => S) = s :: Nil
		def map[S, T](f: S => T, v: List[S]) = v map f
		def flatten[T](m: List[List[T]]): List[T] = m.flatten
	}
	private[this] def app2[S,T] = (f: S => T,v: S) => f(v)
}