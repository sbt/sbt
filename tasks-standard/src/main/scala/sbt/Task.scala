/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import sbt.internal.util.Types._
import sbt.internal.util.{ ~>, AList, AttributeKey, AttributeMap }
import ConcurrentRestrictions.{ Tag, TagMap, tagsKey }
import sbt.internal.Action

/** Combines metadata `info` and a computation `work` to define a task. */
final case class Task[A](info: Info[A], work: Action[A]) {
  override def toString = info.name getOrElse ("Task(" + info + ")")
  override def hashCode = info.hashCode

  def tag(tags: Tag*): Task[A] = tagw(tags.map(t => (t, 1)): _*)
  def tagw(tags: (Tag, Int)*): Task[A] = {
    val tgs: TagMap = info.get(tagsKey).getOrElse(TagMap.empty)
    val value = tags.foldLeft(tgs)((acc, tag) => acc + tag)
    val nextInfo = info.set(tagsKey, value)
    copy(info = nextInfo)
  }

  def tags: TagMap = info get tagsKey getOrElse TagMap.empty
}

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
object Info {
  val Name = AttributeKey[String]("name")
  val Description = AttributeKey[String]("description")
  val defaultAttributeMap = const(AttributeMap.empty)
}
