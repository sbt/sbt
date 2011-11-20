/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package sbt

	import Types._
	import Task._
	import ConcurrentRestrictions.{Tag, TagMap, tagsKey}

// Action, Task, and Info are intentionally invariant in their type parameter.
//  Various natural transformations used, such as PMap, require invariant type constructors for correctness

/** Defines a task compuation*/
sealed trait Action[T]
/** A direct computation of a value. */
final case class Pure[T](f: () => T) extends Action[T]
/** Applies a function to the result of evaluating a heterogeneous list of other tasks.*/
final case class Mapped[T, In <: HList](in: Tasks[In], f: Results[In] => T) extends Action[T]
/** Computes another task to evaluate based on results from evaluating other tasks.*/
final case class FlatMapped[T, In <: HList](in: Tasks[In], f: Results[In] => Task[T]) extends Action[T]
/** A computation `in` that requires other tasks `deps` to be evaluated first.*/
final case class DependsOn[T](in: Task[T], deps: Seq[Task[_]]) extends Action[T]
/** A computation that operates on the results of a homogeneous list of other tasks. 
* It can either return another task to be evaluated or the final value.*/
final case class Join[T, U](in: Seq[Task[U]], f: Seq[Result[U]] => Either[Task[T], T]) extends Action[T]

object Task
{
	type Tasks[HL <: HList] = KList[Task, HL]
	type Results[HL <: HList] = KList[Result, HL]
}

/** Combines metadata `info` and a computation `work` to define a task. */
final case class Task[T](info: Info[T], work: Action[T])
{
	override def toString = info.name getOrElse ("Task(" + info + ")")
	override def hashCode = info.hashCode

	def tag(tags: Tag*): Task[T] = tagw(tags.map(t => (t, 1)) : _*)
	def tagw(tags: (Tag, Int)*): Task[T] = copy(info = info.set(tagsKey, info.get(tagsKey).getOrElse(Map.empty) ++ tags ))
	def tags: TagMap = info get tagsKey getOrElse Map.empty
}
/** Used to provide information about a task, such as the name, description, and tags for controlling concurrent execution.
* @param attributes Arbitrary user-defined key/value pairs describing this task
* @param post a transformation that takes the result of evaluating this task and produces user-defined key/value pairs. */
final case class Info[T](attributes: AttributeMap = AttributeMap.empty, post: T => AttributeMap = const(AttributeMap.empty))
{
	import Info._
	def name = attributes.get(Name)
	def description = attributes.get(Description)
	def setName(n: String) = set(Name, n)
	def setDescription(d: String) = set(Description, d)
	def set[T](key: AttributeKey[T], value: T) = copy(attributes = this.attributes.put(key, value))
	def get[T](key: AttributeKey[T]) = attributes.get(key)
	def postTransform[A](f: (T, AttributeMap) => AttributeMap) = copy(post = (t: T) => f(t, post(t)) )

	override def toString = if(attributes.isEmpty) "_" else attributes.toString
}
object Info
{
	val Name = AttributeKey[String]("name")
	val Description = AttributeKey[String]("description")
}