/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

trait CompletionService:

  /**
   * Submits a work node A with work that returns R. In Execute this is used for tasks returning
   * sbt.Completed.
   */
  def submit(node: TaskId[?], work: () => Completed): Unit

  /**
   * Retrieves and removes the result from the next completed task, waiting if none are yet present.
   * In Execute this is used for tasks returning sbt.Completed.
   */
  def take(): Completed
end CompletionService

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{
  Callable,
  Executor,
  ExecutorCompletionService,
  Executors,
  Future as JFuture,
  RejectedExecutionException,
  CompletionService as JCompletionService
}

object CompletionService {
  val poolID = new AtomicInteger(1)
  def apply(poolSize: Int): (CompletionService, () => Unit) = {
    val i = new AtomicInteger(1)
    val id = poolID.getAndIncrement()
    val pool = Executors.newFixedThreadPool(
      poolSize,
      (r: Runnable) => new Thread(r, s"sbt-completion-thread-$id-${i.getAndIncrement}")
    )
    (apply(pool), () => { pool.shutdownNow(); () })
  }

  def apply(x: Executor): CompletionService =
    apply(new ExecutorCompletionService[Completed](x))

  def apply(completion: JCompletionService[Completed]): CompletionService =
    new CompletionService {
      def submit(node: TaskId[?], work: () => Completed) = {
        CompletionService.submit(work, completion); ()
      }
      def take() = completion.take().get()
    }

  def submit(work: () => Completed, completion: JCompletionService[Completed]): () => Completed = {
    val future = submitFuture(work, completion)
    () => future.get
  }

  private[sbt] def submitFuture(
      work: () => Completed,
      completion: JCompletionService[Completed]
  ): JFuture[Completed] = {
    val future =
      try
        completion.submit {
          new Callable[Completed] {
            def call =
              try {
                work()
              } catch {
                case _: InterruptedException =>
                  throw Incomplete(None, message = Some("cancelled"))
              }
          }
        }
      catch {
        case _: RejectedExecutionException =>
          throw Incomplete(None, message = Some("cancelled"))
      }
    future
  }
  def manage(
      service: CompletionService
  )(setup: TaskId[?] => Unit, cleanup: TaskId[?] => Unit): CompletionService =
    wrap(service) { (node, work) => () =>
      setup(node)
      try {
        work()
      } finally {
        cleanup(node)
      }
    }
  def wrap(
      service: CompletionService
  )(w: (TaskId[?], () => Completed) => (() => Completed)): CompletionService =
    new CompletionService {
      def submit(node: TaskId[?], work: () => Completed) = service.submit(node, w(node, work))
      def take() = service.take()
    }
}
