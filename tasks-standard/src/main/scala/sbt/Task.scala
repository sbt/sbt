/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import sbt.internal.Action
import sbt.internal.util.Types.const
import sbt.internal.util.{ AttributeKey, AttributeMap }
import ConcurrentRestrictions.{ Tag, TagMap, tagsKey }
import sbt.util.Monad

/**
 * Combines metadata `info` and a computation `work` to define a task.
 */
final case class Task[A](info: Info[A], work: Action[A]) extends TaskId[A]:
  override def toString = info.name getOrElse ("Task(" + info + ")")
  override def hashCode = info.hashCode

  def tag(tags: Tag*): Task[A] = tagw(tags.map(t => (t, 1)): _*)
  def tagw(tags: (Tag, Int)*): Task[A] = {
    val tgs: TagMap = info.get(tagsKey).getOrElse(TagMap.empty)
    val value = tags.foldLeft(tgs)((acc, tag) => acc + tag)
    val nextInfo = info.set(tagsKey, value)
    withInfo(info = nextInfo)
  }

  def tags: TagMap = info.get(tagsKey).getOrElse(TagMap.empty)
  def name: Option[String] = info.name

  def attributes: AttributeMap = info.attributes

  private[sbt] def withInfo(info: Info[A]): Task[A] =
    Task(info = info, work = this.work)
end Task

object Task:
  import sbt.std.TaskExtra.*

  given taskMonad: Monad[Task] with
    type F[a] = Task[a]
    override def pure[A1](a: () => A1): Task[A1] = toTask(a)

    override def ap[A1, A2](ff: Task[A1 => A2])(in: Task[A1]): Task[A2] =
      multT2Task((in, ff)).mapN { case (x, f) =>
        f(x)
      }

    override def map[A1, A2](in: Task[A1])(f: A1 => A2): Task[A2] = in.map(f)
    override def mapN[A1 <: Tuple, A2](t: Tuple.Map[A1, Task])(f: A1 => A2): Task[A2] = t.mapN(f)
    override def flatMap[A1, A2](in: F[A1])(f: A1 => F[A2]): F[A2] = in.flatMap(f)
    override def flatten[A1](in: Task[Task[A1]]): Task[A1] = in.flatMap(identity)
end Task

/**
 * Used to provide information about a task, such as the name, description, and tags for controlling
 * concurrent execution.
 * @param attributes
 *   Arbitrary user-defined key/value pairs describing this task
 * @param post
 *   a transformation that takes the result of evaluating this task and produces user-defined
 *   key/value pairs.
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

object Info:
  val Name = AttributeKey[String]("name")
  val Description = AttributeKey[String]("description")
  val defaultAttributeMap = const(AttributeMap.empty)
end Info
