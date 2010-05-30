package sbt

trait TypeFunctions
{
	type Id[X] = X
	trait Const[A] { type Apply[B] = A }
	trait Down[M[_]] { type Apply[B] = Id[M[B]] }

	trait ~>[A[_], B[_]]
	{
		def apply[T](a: A[T]): B[T]
	}
	def Id: Id ~> Id =
		new ~>[Id, Id] { def apply[T](a: T): T = a }
}
object TypeFunctions extends TypeFunctions