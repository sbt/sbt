/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package sbt

import Node._
import Types._

trait Node[A[_], T]
{
	type Inputs <: MList[A]
	type Results <: Inputs#Map[Result]

	val inputs: Inputs
	def unitDependencies: Iterable[A[_]]

	def work(results: Results, units: Iterable[Incomplete]): Either[A[T], T]
}

object Node
{
	def pure[T](f: => T): PureNode[T]= map[Id, T, MNil](MNil, Nil)((_,_) => f)

	def map[A[_], T, Inputs0 <: MList[A]](inputs0: Inputs0, deps0: Iterable[A[_]])(work0: (Inputs0#Map[Result], Iterable[Incomplete]) => T):
		Node[A,T] { type Inputs = Inputs0; type Results = Inputs0#Map[Result] } =
			new Node[A,T] {
				type Inputs = Inputs0
				type Results = Inputs0#Map[Result]
				val inputs = inputs0
				def unitDependencies = deps0
				def work(results: Results, units: Iterable[Incomplete]) = Right(work0(results, units))
			}

	type PureNode[T] = Node[Id, T] { type Inputs = MNil; type Results = MNil }
}
