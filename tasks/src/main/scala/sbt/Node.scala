/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

/**
 * Represents a task node in a format understood by the task evaluation engine Execute.
 * @tparam A
 *   the type computed by this node
 */
private[sbt] trait Node[A]:
  type Inputs
  def dependencies: List[TaskId[?]]
  def computeInputs(f: [a] => TaskId[a] => Result[a]): Inputs

  /** Computes the result of this task given the results from the inputs. */
  def work(inputs: Inputs): Either[TaskId[A], A]
end Node
