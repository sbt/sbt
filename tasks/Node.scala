package sbt

import Node._
import Types._

trait Node[A[_], T]
{
	type Inputs <: MList[Id]
	type Results <: Inputs#Map[Result]

	def inputs: Inputs#Map[A]
	def unitDependencies: Iterable[A[_]]

	def work(results: Results, units: Iterable[Incomplete]): Either[A[T], T]
}
object Node
{
	type Result[T] = Either[Throwable, T]

	def pure[T](f: => T): PureNode[T]= map[Id, T, MNil](MNil, Nil)((_,_) => f)

	def map[A[_], T, Inputs0 <: MList[Id]](inputs0: Inputs0#Map[A], deps0: Iterable[A[_]])(work0: (Inputs0#Map[Result], Iterable[Incomplete]) => T):
		Node[A,T] { type Inputs = Inputs0; type Results = Inputs0#Map[Result] } =
		new Node[A,T] {
			type Inputs = Inputs0
			type Results = Inputs0#Map[Result]
			def inputs = inputs0
			def unitDependencies = deps0
			def work(results: Results, units: Iterable[Incomplete]) = Right(work0(results, units))
		}

	type PureNode[T] = Node[Id, T] { type Inputs = MNil; type Results = MNil }
	type WorkResult[T] = Either[Id[T], T]
	val pureResults = new ~>[PureNode, WorkResult] { def apply[T](t: PureNode[T] ) = t.work( MNil, Nil ) }
}
object Test
{
	val a = pure(3)
	val b = pure(true)
	val c = pure("asdf")
	val i3 = a :^: b :^: c :^: MNil

	val d = map[PureNode, String, i3.Map[Id]](i3, Nil) { case (a :^: b :^: c :^: MNil, Seq()) => a + " " + b + " " + c }
}