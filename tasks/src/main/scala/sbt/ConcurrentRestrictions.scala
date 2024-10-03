/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import java.util.concurrent.atomic.AtomicInteger

import sbt.internal.util.AttributeKey
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.{ Future => JFuture, RejectedExecutionException }
import scala.collection.mutable
import scala.jdk.CollectionConverters.*

/**
 * Describes restrictions on concurrent execution for a set of tasks.
 */
trait ConcurrentRestrictions {

  /** Internal state type used to describe a set of tasks. */
  type G

  /** Representation of zero tasks. */
  def empty: G

  /** Updates the description `g` to include a new task `a`. */
  def add(g: G, a: TaskId[?]): G

  /** Updates the description `g` to remove a previously added task `a`. */
  def remove(g: G, a: TaskId[?]): G

  /**
   * Returns true if the tasks described by `g` are allowed to execute concurrently. The methods in
   * this class must obey the following laws:
   *
   *   1. forall g: G, a: A; valid(g) => valid(remove(g,a)) 2. forall a: A; valid(add(empty, a)) 3.
   *      forall g: G, a: A; valid(g) <=> valid(remove(add(g, a), a)) 4. (implied by 1,2,3)
   *      valid(empty) 5. forall g: G, a: A, b: A; !valid(add(g,a)) => !valid(add(add(g,b), a))
   */
  def valid(g: G): Boolean
}

private[sbt] sealed trait CancelSentiels {
  def cancelSentinels(): Unit
}

import java.util.{ LinkedList, Queue }
import java.util.concurrent.{ Executor, Executors, ExecutorCompletionService }
import annotation.tailrec

object ConcurrentRestrictions {
  private val completionServices = new java.util.WeakHashMap[CompletionService, Boolean]
  def cancelAll() = completionServices.keySet.asScala.toVector.foreach {
    case a: AutoCloseable => a.close()
    case _                =>
  }

  private[sbt] def cancelAllSentinels() = completionServices.keySet.asScala.toVector.foreach {
    case a: CancelSentiels => a.cancelSentinels()
    case _                 =>
  }

  /**
   * A ConcurrentRestrictions instance that places no restrictions on concurrently executing tasks.
   */
  def unrestricted: ConcurrentRestrictions =
    new ConcurrentRestrictions {
      type G = Unit
      def empty = ()
      def add(g: G, a: TaskId[?]) = ()
      def remove(g: G, a: TaskId[?]) = ()
      def valid(g: G) = true
    }

  def limitTotal(i: Int): ConcurrentRestrictions = {
    assert(i >= 1, "Maximum must be at least 1 (was " + i + ")")
    new ConcurrentRestrictions {
      type G = Int
      def empty = 0
      def add(g: Int, a: TaskId[?]) = g + 1
      def remove(g: Int, a: TaskId[?]) = g - 1
      def valid(g: Int) = g <= i
    }
  }

  /** A key object used for associating information with a task. */
  final case class Tag(name: String)

  val tagsKey =
    AttributeKey[TagMap]("tags", "Attributes restricting concurrent execution of tasks.")

  /** A standard tag describing the number of tasks that do not otherwise have any tags. */
  val Untagged = Tag("untagged")

  /** A standard tag describing the total number of tasks. */
  val All = Tag("all")

  type TagMap = Map[Tag, Int]
  val TagMap = Map.empty[Tag, Int]

  /**
   * Implements concurrency restrictions on tasks based on Tags.
   * @param get
   *   extracts tags from a task
   * @param validF
   *   defines whether a set of tasks are allowed to execute concurrently based on their merged tags
   */
  def tagged(validF: TagMap => Boolean): ConcurrentRestrictions =
    new ConcurrentRestrictions {
      type G = TagMap
      def empty = Map.empty
      def add(g: TagMap, a: TaskId[?]) = merge(g, a)(_ + _)
      def remove(g: TagMap, a: TaskId[?]) = merge(g, a)(_ - _)
      def valid(g: TagMap) = validF(g)
    }

  private def merge(m: TagMap, a: TaskId[?])(
      f: (Int, Int) => Int
  ): TagMap = {
    val base = merge(m, a.tags)(f)
    val un = if (a.tags.isEmpty) update(base, Untagged, 1)(f) else base
    update(un, All, 1)(f)
  }

  private def update[A, B](m: Map[A, B], a: A, b: B)(f: (B, B) => B): Map[A, B] = {
    val newb =
      (m get a) match {
        case Some(bv) => f(bv, b)
        case None     => b
      }
    m.updated(a, newb)
  }
  private def merge[A, B](m: Map[A, B], n: Map[A, B])(f: (B, B) => B): Map[A, B] =
    n.foldLeft(m) { case (acc, (a, b)) => update(acc, a, b)(f) }

  private val poolID = new AtomicInteger(1)

  /**
   * Constructs a CompletionService suitable for backing task execution based on the provided
   * restrictions on concurrent task execution.
   * @return
   *   a pair, with _1 being the CompletionService and _2 a function to shutdown the service.
   * @tparam A
   *   the task type
   * @tparam R
   *   the type of data that will be computed by the CompletionService.
   */
  def completionService(
      tags: ConcurrentRestrictions,
      warn: String => Unit
  ): (CompletionService, () => Unit) = {
    val id = poolID.getAndIncrement
    val i = new AtomicInteger(1)
    val pool = Executors.newCachedThreadPool { r =>
      new Thread(r, s"sbt-completion-service-pool-$id-${i.getAndIncrement()}")
    }
    val service = completionService(pool, tags, warn)
    (service, () => { pool.shutdownNow(); () })
  }

  def completionService(
      tags: ConcurrentRestrictions,
      warn: String => Unit,
      isSentinel: TaskId[?] => Boolean
  ): (CompletionService, () => Unit) = {
    val pool = Executors.newCachedThreadPool()
    val service = completionService(pool, tags, warn, isSentinel)
    (
      service,
      () => {
        pool.shutdownNow()
        ()
      }
    )
  }

  def cancellableCompletionService(
      tags: ConcurrentRestrictions,
      warn: String => Unit,
      isSentinel: TaskId[?] => Boolean
  ): (CompletionService, Boolean => Unit) = {
    val pool = Executors.newCachedThreadPool()
    val service = completionService(pool, tags, warn, isSentinel)
    (
      service,
      force => {
        if (force) service.close()
        pool.shutdownNow()
        ()
      }
    )
  }

  def completionService(
      backing: Executor,
      tags: ConcurrentRestrictions,
      warn: String => Unit
  ): CompletionService with AutoCloseable = {
    completionService(backing, tags, warn, _ => false)
  }

  /**
   * Constructs a CompletionService suitable for backing task execution based on the provided
   * restrictions on concurrent task execution and using the provided Executor to manage execution
   * on threads.
   */
  def completionService(
      backing: Executor,
      tags: ConcurrentRestrictions,
      warn: String => Unit,
      isSentinel: TaskId[?] => Boolean,
  ): CompletionService with CancelSentiels with AutoCloseable = {

    // Represents submitted work for a task.
    final class Enqueue(val node: TaskId[?], val work: () => Completed)

    new CompletionService with CancelSentiels with AutoCloseable {
      completionServices.put(this, true)
      private val closed = new AtomicBoolean(false)
      override def close(): Unit = if (closed.compareAndSet(false, true)) {
        completionServices.remove(this)
        ()
      }

      /** Backing service used to manage execution on threads once all constraints are satisfied. */
      private val jservice = new ExecutorCompletionService[Completed](backing)

      /** The description of the currently running tasks, used by `tags` to manage restrictions. */
      private var tagState = tags.empty

      /** The number of running tasks. */
      private var running = 0

      /**
       * Tasks that cannot be run yet because they cannot execute concurrently with the currently
       * running tasks.
       */
      private val pending = new LinkedList[Enqueue]

      private val sentinels: mutable.ListBuffer[JFuture[_]] = mutable.ListBuffer.empty

      def cancelSentinels(): Unit = {
        sentinels.toList foreach { s =>
          s.cancel(true)
        }
        sentinels.clear()
      }

      def submit(node: TaskId[?], work: () => Completed): Unit = synchronized {
        if (closed.get) throw new RejectedExecutionException
        else if (isSentinel(node)) {
          // skip all checks for sentinels
          sentinels += CompletionService.submitFuture(work, jservice)
        } else {
          val newState = tags.add(tagState, node)
          // if the new task is allowed to run concurrently with the currently running tasks,
          //   submit it to be run by the backing j.u.c.CompletionService
          if (tags valid newState) {
            tagState = newState
            submitValid(node, work)
            ()
          } else {
            if (running == 0) errorAddingToIdle()
            pending.add(new Enqueue(node, work))
            ()
          }
        }
        ()
      }
      private def submitValid(node: TaskId[?], work: () => Completed): Unit = {
        running += 1
        val wrappedWork = () =>
          try work()
          finally cleanup(node)
        CompletionService.submitFuture(wrappedWork, jservice)
        ()
      }
      private def cleanup(node: TaskId[?]): Unit = synchronized {
        running -= 1
        tagState = tags.remove(tagState, node)
        if (!tags.valid(tagState)) {
          warn(
            "Invalid restriction: removing a completed node from a valid system must result in a valid system."
          )
          ()
        }
        submitValid(new LinkedList)
      }
      private def errorAddingToIdle() =
        warn("Invalid restriction: adding a node to an idle system must be allowed.")

      /** Submits pending tasks that are now allowed to executed. */
      @tailrec private def submitValid(tried: Queue[Enqueue]): Unit =
        if (pending.isEmpty) {
          if (!tried.isEmpty) {
            if (running == 0) errorAddingToIdle()
            pending.addAll(tried)
            ()
          }
        } else {
          val next = pending.remove()
          val newState = tags.add(tagState, next.node)
          if (tags.valid(newState)) {
            tagState = newState
            submitValid(next.node, next.work)
            ()
          } else {
            tried.add(next)
            ()
          }
          submitValid(tried)
        }

      def take(): Completed = {
        if (closed.get)
          throw new RejectedExecutionException(
            "Tried to get values for a closed completion service"
          )
        jservice.take().get()
      }
    }
  }
}
