/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt

import sbt.internal.util.AList

/**
 * Represents a task node in a format understood by the task evaluation engine Execute.
 *
 * @tparam A the task type constructor
 * @tparam T the type computed by this node
 */
trait Node[A[_], T] {
  type K[L[x]]
  val in: K[A]
  val alist: AList[K]

  /** Computes the result of this task given the results from the inputs. */
  def work(inputs: K[Result]): Either[A[T], T]
}
