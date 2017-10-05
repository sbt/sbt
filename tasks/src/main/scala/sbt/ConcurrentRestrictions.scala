/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt

import sbt.internal.util.AttributeKey

/**
 * Describes restrictions on concurrent execution for a set of tasks.
 *
 * @tparam A the type of a task
 */
trait ConcurrentRestrictions[A] {

  /** Internal state type used to describe a set of tasks. */
  type G

  /** Representation of zero tasks.*/
  def empty: G

  /** Updates the description `g` to include a new task `a`.*/
  def add(g: G, a: A): G

  /** Updates the description `g` to remove a previously added task `a`.*/
  def remove(g: G, a: A): G

  /**
   * Returns true if the tasks described by `g` are allowed to execute concurrently.
   * The methods in this class must obey the following laws:
   *
   * 1. forall g: G, a: A; valid(g) => valid(remove(g,a))
   * 2. forall a: A; valid(add(empty, a))
   * 3. forall g: G, a: A; valid(g) <=> valid(remove(add(g, a), a))
   * 4. (implied by 1,2,3) valid(empty)
   * 5. forall g: G, a: A, b: A; !valid(add(g,a)) => !valid(add(add(g,b), a))
   */
  def valid(g: G): Boolean
}

import java.util.{ LinkedList, Queue }
import java.util.concurrent.{ Executor, Executors, ExecutorCompletionService }
import annotation.tailrec

object ConcurrentRestrictions {

  /**
   * A ConcurrentRestrictions instance that places no restrictions on concurrently executing tasks.
   * @param zero the constant placeholder used for t
   */
  def unrestricted[A]: ConcurrentRestrictions[A] =
    new ConcurrentRestrictions[A] {
      type G = Unit
      def empty = ()
      def add(g: G, a: A) = ()
      def remove(g: G, a: A) = ()
      def valid(g: G) = true
    }

  def limitTotal[A](i: Int): ConcurrentRestrictions[A] = {
    assert(i >= 1, "Maximum must be at least 1 (was " + i + ")")
    new ConcurrentRestrictions[A] {
      type G = Int
      def empty = 0
      def add(g: Int, a: A) = g + 1
      def remove(g: Int, a: A) = g - 1
      def valid(g: Int) = g <= i
    }
  }

  /** A key object used for associating information with a task.*/
  final case class Tag(name: String)

  val tagsKey =
    AttributeKey[TagMap]("tags", "Attributes restricting concurrent execution of tasks.")

  /** A standard tag describing the number of tasks that do not otherwise have any tags.*/
  val Untagged = Tag("untagged")

  /** A standard tag describing the total number of tasks. */
  val All = Tag("all")

  type TagMap = Map[Tag, Int]

  /**
   * Implements concurrency restrictions on tasks based on Tags.
   * @tparam A type of a task
   * @param get extracts tags from a task
   * @param validF defines whether a set of tasks are allowed to execute concurrently based on their merged tags
   */
  def tagged[A](get: A => TagMap, validF: TagMap => Boolean): ConcurrentRestrictions[A] =
    new ConcurrentRestrictions[A] {
      type G = TagMap
      def empty = Map.empty
      def add(g: TagMap, a: A) = merge(g, a, get)(_ + _)
      def remove(g: TagMap, a: A) = merge(g, a, get)(_ - _)
      def valid(g: TagMap) = validF(g)
    }

  private[this] def merge[A](m: TagMap, a: A, get: A => TagMap)(f: (Int, Int) => Int): TagMap = {
    val aTags = get(a)
    val base = merge(m, aTags)(f)
    val un = if (aTags.isEmpty) update(base, Untagged, 1)(f) else base
    update(un, All, 1)(f)
  }

  private[this] def update[A, B](m: Map[A, B], a: A, b: B)(f: (B, B) => B): Map[A, B] = {
    val newb =
      (m get a) match {
        case Some(bv) => f(bv, b)
        case None     => b
      }
    m.updated(a, newb)
  }
  private[this] def merge[A, B](m: Map[A, B], n: Map[A, B])(f: (B, B) => B): Map[A, B] =
    (m /: n) { case (acc, (a, b)) => update(acc, a, b)(f) }

  /**
   * Constructs a CompletionService suitable for backing task execution based on the provided restrictions on concurrent task execution.
   * @return a pair, with _1 being the CompletionService and _2 a function to shutdown the service.
   * @tparam A the task type
   * @tparam G describes a set of tasks
   * @tparam R the type of data that will be computed by the CompletionService.
   */
  def completionService[A, R](tags: ConcurrentRestrictions[A],
                              warn: String => Unit): (CompletionService[A, R], () => Unit) = {
    val pool = Executors.newCachedThreadPool()
    (completionService[A, R](pool, tags, warn), () => pool.shutdownNow())
  }

  /**
   * Constructs a CompletionService suitable for backing task execution based on the provided restrictions on concurrent task execution
   * and using the provided Executor to manage execution on threads.
   */
  def completionService[A, R](backing: Executor,
                              tags: ConcurrentRestrictions[A],
                              warn: String => Unit): CompletionService[A, R] = {

    /** Represents submitted work for a task.*/
    final class Enqueue(val node: A, val work: () => R)

    new CompletionService[A, R] {

      /** Backing service used to manage execution on threads once all constraints are satisfied. */
      private[this] val jservice = new ExecutorCompletionService[R](backing)

      /** The description of the currently running tasks, used by `tags` to manage restrictions.*/
      private[this] var tagState = tags.empty

      /** The number of running tasks. */
      private[this] var running = 0

      /** Tasks that cannot be run yet because they cannot execute concurrently with the currently running tasks.*/
      private[this] val pending = new LinkedList[Enqueue]

      def submit(node: A, work: () => R): Unit = synchronized {
        val newState = tags.add(tagState, node)
        // if the new task is allowed to run concurrently with the currently running tasks,
        //   submit it to be run by the backing j.u.c.CompletionService
        if (tags valid newState) {
          tagState = newState
          submitValid(node, work)
        } else {
          if (running == 0) errorAddingToIdle()
          pending.add(new Enqueue(node, work))
        }
      }
      private[this] def submitValid(node: A, work: () => R) = {
        running += 1
        val wrappedWork = () =>
          try work()
          finally cleanup(node)
        CompletionService.submit(wrappedWork, jservice)
      }
      private[this] def cleanup(node: A): Unit = synchronized {
        running -= 1
        tagState = tags.remove(tagState, node)
        if (!tags.valid(tagState))
          warn(
            "Invalid restriction: removing a completed node from a valid system must result in a valid system.")
        submitValid(new LinkedList)
      }
      private[this] def errorAddingToIdle() =
        warn("Invalid restriction: adding a node to an idle system must be allowed.")

      /** Submits pending tasks that are now allowed to executed. */
      @tailrec private[this] def submitValid(tried: Queue[Enqueue]): Unit =
        if (pending.isEmpty) {
          if (!tried.isEmpty) {
            if (running == 0) errorAddingToIdle()
            pending.addAll(tried)
          }
        } else {
          val next = pending.remove()
          val newState = tags.add(tagState, next.node)
          if (tags.valid(newState)) {
            tagState = newState
            submitValid(next.node, next.work)
          } else
            tried.add(next)
          submitValid(tried)
        }

      def take(): R = jservice.take().get()
    }
  }
}
