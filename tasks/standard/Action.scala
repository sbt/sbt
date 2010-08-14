/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package sbt

import Types._
import Task._

// Action, Task, and Info are intentionally invariant in its type parameter.
//  Various natural transformations used, such as PMap, require invariant type constructors for correctness

sealed trait Action[T]
sealed case class Pure[T](f: () => T) extends Action[T]

final case class Mapped[T, In <: HList](in: Tasks[In], f: Results[In] => T) extends Action[T]
final case class MapAll[T, In <: HList](in: Tasks[In], f: In => T) extends Action[T]
final case class MapFailure[T, In <: HList](in: Tasks[In], f: Seq[Incomplete] => T) extends Action[T]

final case class FlatMapAll[T, In <: HList](in: Tasks[In], f: In => Task[T]) extends Action[T]
final case class FlatMapFailure[T, In <: HList](in: Tasks[In], f: Seq[Incomplete] => Task[T]) extends Action[T]
final case class FlatMapped[T, In <: HList](in: Tasks[In], f: Results[In] => Task[T]) extends Action[T]

final case class DependsOn[T](in: Task[T], deps: Seq[Task[_]]) extends Action[T]

final case class Join[T, U](in: Seq[Task[U]], f: Seq[U] => Either[Task[T], T]) extends Action[T]

object Task
{
	type Tasks[HL <: HList] = KList[Task, HL]
	type Results[HL <: HList] = KList[Result, HL]
}

final case class Task[T](info: Info[T], work: Action[T])
{
	def original = info.original getOrElse this
}
/** `original` is used during transformation only.*/
final case class Info[T](name: Option[String] = None, description: Option[String] = None, implied: Boolean = false, original: Option[Task[T]] = None)
{
	assert(name forall (_ != null))
}
