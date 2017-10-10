/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt

import annotation.tailrec

object Tags {
  type Tag = ConcurrentRestrictions.Tag
  type TagMap = ConcurrentRestrictions.TagMap
  def Tag(s: String): Tag = ConcurrentRestrictions.Tag(s)

  val All = ConcurrentRestrictions.All
  val Untagged = ConcurrentRestrictions.Untagged
  val Compile = Tag("compile")
  val Test = Tag("test")
  val Update = Tag("update")
  val Publish = Tag("publish")
  val Clean = Tag("clean")

  val CPU = Tag("cpu")
  val Network = Tag("network")
  val Disk = Tag("disk")

  val ForkedTestGroup = Tag("forked-test-group")

  /**
   * Describes a restriction on concurrently executing tasks.
   * A Rule is constructed using one of the Tags.limit* methods.
   */
  abstract class Rule {
    def apply(m: TagMap): Boolean
    def ||(r: Rule): Rule = new Or(this, r)
    def &&(r: Rule): Rule = new And(this, r)
    def unary_- : Rule = new Not(this)
  }
  private[this] final class Custom(f: TagMap => Boolean) extends Rule {
    def apply(m: TagMap) = f(m)
  }
  private[this] final class Single(tag: Tag, max: Int) extends Rule {
    checkMax(max)
    def apply(m: TagMap) = getInt(m, tag) <= max
    override def toString = "Limit " + tag.name + " to " + max
  }
  private[this] final class Sum(tags: Seq[Tag], max: Int) extends Rule {
    checkMax(max)
    def apply(m: TagMap) = (0 /: tags)((sum, t) => sum + getInt(m, t)) <= max
    override def toString = tags.mkString("Limit sum of ", ", ", " to " + max)
  }
  private[this] final class Or(a: Rule, b: Rule) extends Rule {
    def apply(m: TagMap) = a(m) || b(m)
  }
  private[this] final class And(a: Rule, b: Rule) extends Rule {
    def apply(m: TagMap) = a(m) && b(m)
  }
  private[this] final class Not(a: Rule) extends Rule {
    def apply(m: TagMap) = !a(m)
  }

  private[this] def checkMax(max: Int): Unit = assert(max >= 1, "Limit must be at least 1.")

  /** Converts a sequence of rules into a function that identifies whether a set of tasks are allowed to execute concurrently based on their merged tags. */
  def predicate(rules: Seq[Rule]): TagMap => Boolean = m => {
    @tailrec def loop(rules: List[Rule]): Boolean =
      rules match {
        case x :: xs => x(m) && loop(xs)
        case Nil     => true
      }
    loop(rules.toList)
  }

  def getInt(m: TagMap, tag: Tag): Int = m.getOrElse(tag, 0)

  /**
   * Constructs a custom Rule from the predicate `f`.
   * The input represents the weighted tags of a set of tasks.
   * The function `f` should return true if those tasks are allowed to execute concurrently and false if they are not.
   *
   * If there is only one task represented by the map, it must be allowed to execute.
   */
  def customLimit(f: TagMap => Boolean): Rule = new Custom(f)

  /** Returns a Rule that limits the maximum number of concurrently executing tasks to `max`, regardless of tags. */
  def limitAll(max: Int): Rule = limit(All, max)

  /** Returns a Rule that limits the maximum number of concurrently executing tasks without a tag to `max`.  */
  def limitUntagged(max: Int): Rule = limit(Untagged, max)

  /** Returns a Rule that limits the maximum number of concurrent executings tasks tagged with `tag` to `max`.*/
  def limit(tag: Tag, max: Int): Rule = new Single(tag, max)

  def limitSum(max: Int, tags: Tag*): Rule = new Sum(tags, max)

  /** Ensure that a task with the given tag always executes in isolation.*/
  def exclusive(exclusiveTag: Tag): Rule = customLimit { (tags: Map[Tag, Int]) =>
    // if there are no exclusive tasks in this group, this rule adds no restrictions
    tags.getOrElse(exclusiveTag, 0) == 0 ||
    // If there is only one task, allow it to execute.
    tags.getOrElse(Tags.All, 0) == 1
  }

  /** Ensure that a task with the given tag only executes with tasks also tagged with the given tag.*/
  def exclusiveGroup(exclusiveTag: Tag): Rule = customLimit { (tags: Map[Tag, Int]) =>
    val exclusiveCount = tags.getOrElse(exclusiveTag, 0)
    val allCount = tags.getOrElse(Tags.All, 0)
    // If there are no exclusive tasks in this group, this rule adds no restrictions.
    exclusiveCount == 0 ||
    // If all tasks have this tag, allow them to execute.
    exclusiveCount == allCount ||
    // Always allow a group containing only one task to execute (fallthrough case).
    allCount == 1
  }

  /** A task tagged with one of `exclusiveTags` will not execute with another task with any of the other tags in `exclusiveTags`.*/
  def exclusiveGroups(exclusiveTags: Tag*): Rule = customLimit { (tags: Map[Tag, Int]) =>
    val groups = exclusiveTags.count(tag => tags.getOrElse(tag, 0) > 0)
    groups <= 1
  }
}
