/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

trait CompletionService[A, R] {
  def submit(submission: Submission[A, R]): Unit
  def take(): R
}

trait Submission[+A, +R] {
  def node: A
  def work(): R
}
abstract class NodeSubmission[+A, +R](val node: A) {
  def work(): R
}

import java.util.concurrent.{
  Callable,
  CompletionService => JCompletionService,
  Executor,
  Executors,
  ExecutorCompletionService,
  RejectedExecutionException,
}

object CompletionService {
  def apply[A, T](poolSize: Int): (CompletionService[A, T], () => Unit) = {
    val pool = Executors.newFixedThreadPool(poolSize)
    (apply[A, T](pool), () => { pool.shutdownNow(); () })
  }
  def apply[A, T](x: Executor): CompletionService[A, T] =
    apply(new ExecutorCompletionService[T](x))
  def apply[A, T](completion: JCompletionService[T]): CompletionService[A, T] =
    new CompletionService[A, T] {
      def submit(submission: Submission[A, T]) = {
        CompletionService.submit(submission, completion)
        ()
      }
      def take() = completion.take().get()
    }
  def submit[T](submission: Submission[_, T], completion: JCompletionService[T]): () => T = {
    val future = try completion.submit { new Callable[T] { def call = submission.work() } } catch {
      case _: RejectedExecutionException => throw Incomplete(None, message = Some("cancelled"))
    }
    () => future.get()
  }
  def manage[A, T](
      service: CompletionService[A, T]
  )(setup: A => Unit, cleanup: A => Unit): CompletionService[A, T] =
    new Wrapping[A, T](service) {
      override protected def wrap(submission: Submission[A, T]): T = {
        setup(submission.node)
        try {
          submission.work()
        } finally {
          cleanup(submission.node)
        }
      }
    }

  abstract class Wrapping[A, T](service: CompletionService[A, T]) extends CompletionService[A, T] {
    protected def wrap(submission: Submission[A, T]): T

    def submit(submission: Submission[A, T]) =
      service.submit(new WrappedSubmission(submission))

    override def take(): T = service.take()

    private class WrappedSubmission(submission: Submission[A, T]) extends Submission[A, T] {
      override def node: A = submission.node
      override def work(): T = wrap(submission)
    }
  }
}
