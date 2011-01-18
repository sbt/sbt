/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package sbt

	import Types._
	import Task._

// Action, Task, and Info are intentionally invariant in their type parameter.
//  Various natural transformations used, such as PMap, require invariant type constructors for correctness

sealed trait Action[T]
final case class Pure[T](f: () => T) extends Action[T]
final case class Mapped[T, In <: HList](in: Tasks[In], f: Results[In] => T) extends Action[T]
final case class FlatMapped[T, In <: HList](in: Tasks[In], f: Results[In] => Task[T]) extends Action[T]
final case class DependsOn[T](in: Task[T], deps: Seq[Task[_]]) extends Action[T]
final case class Join[T, U](in: Seq[Task[U]], f: Seq[Result[U]] => Either[Task[T], T]) extends Action[T]

object Task
{
	type Tasks[HL <: HList] = KList[Task, HL]
	type Results[HL <: HList] = KList[Result, HL]
}

final case class Task[T](info: Info[T], work: Action[T])
{
	def original = info.original getOrElse this
	override def toString = info.name orElse original.info.name getOrElse ("Task(" + info + ", " + work + ")")
}
/** `original` is used during transformation only.*/
final case class Info[T](attributes: AttributeMap = AttributeMap.empty, original: Option[Task[T]] = None)
{
	import Info._
	def name = attributes.get(Name)
	def description = attributes.get(Description)
	def setName(n: String) = set(Name, n)
	def setDescription(d: String) = set(Description, d)
	def set[T](key: AttributeKey[T], value: T) = copy(attributes = this.attributes.put(key, value))

	override def toString =
		if(attributes.isEmpty && original.isEmpty)
			"_"
		else
			attributes.toString + (original match { case Some(o) => ", original: " + o; case None => "" })
}
object Info
{
	val Name = AttributeKey[String]("name")
	val Description = AttributeKey[String]("description")
}