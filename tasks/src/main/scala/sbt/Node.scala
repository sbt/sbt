/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import sbt.internal.util.AList

/**
 * Represents a task node in a format understood by the task evaluation engine Execute.
 *
 * @tparam Effect
 *   the task type constructor
 * @tparam A
 *   the type computed by this node
 */
private[sbt] trait Node[Effect[_], A]:
  type K[L[x]]
  def in: K[Effect]
  def alist: AList[K]

  /** Computes the result of this task given the results from the inputs. */
  def work(inputs: K[Result]): Either[Effect[A], A]
end Node
