/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import sbt.internal.util.Types._
import sbt.internal.util.{ ~>, AList, AttributeKey, AttributeMap }
import ConcurrentRestrictions.{ Tag, TagMap, tagsKey }

// Action, Task, and Info are intentionally invariant in their type parameter.
//  Various natural transformations used, such as PMap, require invariant type constructors for correctness

/** Defines a task computation*/
sealed trait Action[T] {
  // TODO: remove after deprecated InputTask constructors are removed
  private[sbt] def mapTask(f: Task ~> Task): Action[T]
}

/**
 * A direct computation of a value.
 * If `inline` is true, `f` will be evaluated on the scheduler thread without the overhead of normal scheduling when possible.
 * This is intended as an optimization for already evaluated values or very short pure computations.
 */
final case class Pure[T](f: () => T, `inline`: Boolean) extends Action[T] {
  private[sbt] def mapTask(f: Task ~> Task) = this
}

/** Applies a function to the result of evaluating a heterogeneous list of other tasks.*/
final case class Mapped[T, K[L[x]]](in: K[Task], f: K[Result] => T, alist: AList[K])
    extends Action[T] {
  private[sbt] def mapTask(g: Task ~> Task) = Mapped[T, K](alist.transform(in, g), f, alist)
}

/** Computes another task to evaluate based on results from evaluating other tasks.*/
final case class FlatMapped[T, K[L[x]]](in: K[Task], f: K[Result] => Task[T], alist: AList[K])
    extends Action[T] {
  private[sbt] def mapTask(g: Task ~> Task) =
    FlatMapped[T, K](alist.transform(in, g), g.fn[T] compose f, alist)
}

/** A computation `in` that requires other tasks `deps` to be evaluated first.*/
final case class DependsOn[T](in: Task[T], deps: Seq[Task[_]]) extends Action[T] {
  private[sbt] def mapTask(g: Task ~> Task) = DependsOn[T](g(in), deps.map(t => g(t)))
}

/**
 * A computation that operates on the results of a homogeneous list of other tasks.
 * It can either return another task to be evaluated or the final value.
 */
final case class Join[T, U](in: Seq[Task[U]], f: Seq[Result[U]] => Either[Task[T], T])
    extends Action[T] {
  private[sbt] def mapTask(g: Task ~> Task) =
    Join[T, U](in.map(g.fn[U]), sr => f(sr).left.map(g.fn[T]))
}

/**
 * A computation that conditionally falls back to a second transformation.
 * This can be used to encode `if` conditions.
 */
final case class Selected[A, B](fab: Task[Either[A, B]], fin: Task[A => B]) extends Action[B] {
  private def ml = AList.single[Either[A, B]]
  type K[L[x]] = L[Either[A, B]]

  private[sbt] def mapTask(g: Task ~> Task) =
    Selected[A, B](g(fab), g(fin))

  /**
   * Encode this computation as a flatMap.
   */
  private[sbt] def asFlatMapped: FlatMapped[B, K] = {
    val f: Either[A, B] => Task[B] = {
      case Right(b) => std.TaskExtra.task(b)
      case Left(a)  => std.TaskExtra.singleInputTask(fin).map(_(a))
    }
    FlatMapped[B, K](fab, {
      f compose std.TaskExtra.successM
    }, ml)
  }
}

/** Combines metadata `info` and a computation `work` to define a task. */
final case class Task[T](info: Info[T], work: Action[T]) {
  override def toString = info.name getOrElse ("Task(" + info + ")")
  override def hashCode = info.hashCode

  def tag(tags: Tag*): Task[T] = tagw(tags.map(t => (t, 1)): _*)
  def tagw(tags: (Tag, Int)*): Task[T] = {
    val tgs: TagMap = info.get(tagsKey).getOrElse(TagMap.empty)
    val value = tags.foldLeft(tgs)((acc, tag) => acc + tag)
    val nextInfo = info.set(tagsKey, value)
    copy(info = nextInfo)
  }

  def tags: TagMap = info get tagsKey getOrElse TagMap.empty
}

/**
 * Used to provide information about a task, such as the name, description, and tags for controlling concurrent execution.
 * @param attributes Arbitrary user-defined key/value pairs describing this task
 * @param post a transformation that takes the result of evaluating this task and produces user-defined key/value pairs.
 */
final case class Info[T](
    attributes: AttributeMap = AttributeMap.empty,
    post: T => AttributeMap = Info.defaultAttributeMap
) {
  import Info._
  def name = attributes.get(Name)
  def description = attributes.get(Description)
  def setName(n: String) = set(Name, n)
  def setDescription(d: String) = set(Description, d)
  def set[A](key: AttributeKey[A], value: A) = copy(attributes = this.attributes.put(key, value))
  def get[A](key: AttributeKey[A]): Option[A] = attributes.get(key)
  def postTransform(f: (T, AttributeMap) => AttributeMap) = copy(post = (t: T) => f(t, post(t)))

  override def toString = if (attributes.isEmpty) "_" else attributes.toString
}
object Info {
  val Name = AttributeKey[String]("name")
  val Description = AttributeKey[String]("description")
  val defaultAttributeMap = const(AttributeMap.empty)
}
