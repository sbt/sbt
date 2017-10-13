/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt

trait CompletionService[A, R] {
  def submit(node: A, work: () => R): Unit
  def take(): R
}

import java.util.concurrent.{
  Callable,
  CompletionService => JCompletionService,
  Executor,
  Executors,
  ExecutorCompletionService
}

object CompletionService {
  def apply[A, T](poolSize: Int): (CompletionService[A, T], () => Unit) = {
    val pool = Executors.newFixedThreadPool(poolSize)
    (apply[A, T](pool), () => pool.shutdownNow())
  }
  def apply[A, T](x: Executor): CompletionService[A, T] =
    apply(new ExecutorCompletionService[T](x))
  def apply[A, T](completion: JCompletionService[T]): CompletionService[A, T] =
    new CompletionService[A, T] {
      def submit(node: A, work: () => T) = CompletionService.submit(work, completion)
      def take() = completion.take().get()
    }
  def submit[T](work: () => T, completion: JCompletionService[T]): () => T = {
    val future = completion.submit { new Callable[T] { def call = work() } }
    () =>
      future.get()
  }
  def manage[A, T](service: CompletionService[A, T])(setup: A => Unit,
                                                     cleanup: A => Unit): CompletionService[A, T] =
    wrap(service) { (node, work) => () =>
      setup(node)
      try { work() } finally { cleanup(node) }
    }
  def wrap[A, T](service: CompletionService[A, T])(
      w: (A, () => T) => (() => T)): CompletionService[A, T] =
    new CompletionService[A, T] {
      def submit(node: A, work: () => T) = service.submit(node, w(node, work))
      def take() = service.take()
    }
}
