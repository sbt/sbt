/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

trait CompletionService[A, R] {

  /**
   * Submits a work node A with work that returns R.
   * In Execute this is used for tasks returning sbt.Completed.
   */
  def submit(node: A, work: () => R): Unit

  /**
   * Retrieves and removes the result from the next completed task,
   * waiting if none are yet present.
   * In Execute this is used for tasks returning sbt.Completed.
   */
  def take(): R
}

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{
  Callable,
  Executor,
  ExecutorCompletionService,
  Executors,
  RejectedExecutionException,
  CompletionService => JCompletionService
}

object CompletionService {
  val poolID = new AtomicInteger(1)
  def apply[A, T](poolSize: Int): (CompletionService[A, T], () => Unit) = {
    val i = new AtomicInteger(1)
    val id = poolID.getAndIncrement()
    val pool = Executors.newFixedThreadPool(
      poolSize,
      (r: Runnable) => new Thread(r, s"sbt-completion-thread-$id-${i.getAndIncrement}")
    )
    (apply[A, T](pool), () => { pool.shutdownNow(); () })
  }
  def apply[A, T](x: Executor): CompletionService[A, T] =
    apply(new ExecutorCompletionService[T](x))
  def apply[A, T](completion: JCompletionService[T]): CompletionService[A, T] =
    new CompletionService[A, T] {
      def submit(node: A, work: () => T) = { CompletionService.submit(work, completion); () }
      def take() = completion.take().get()
    }
  def submit[T](work: () => T, completion: JCompletionService[T]): () => T = {
    val future = try completion.submit {
      new Callable[T] {
        def call =
          try {
            work()
          } catch {
            case _: InterruptedException =>
              throw Incomplete(None, message = Some("cancelled"))
          }
      }
    } catch {
      case _: RejectedExecutionException =>
        throw Incomplete(None, message = Some("cancelled"))
    }
    () => future.get()
  }
  def manage[A, T](
      service: CompletionService[A, T]
  )(setup: A => Unit, cleanup: A => Unit): CompletionService[A, T] =
    wrap(service) { (node, work) => () =>
      setup(node)
      try {
        work()
      } finally {
        cleanup(node)
      }
    }
  def wrap[A, T](
      service: CompletionService[A, T]
  )(w: (A, () => T) => (() => T)): CompletionService[A, T] =
    new CompletionService[A, T] {
      def submit(node: A, work: () => T) = service.submit(node, w(node, work))
      def take() = service.take()
    }
}
