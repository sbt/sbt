/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

/**
 * Provides a mechanism for a task to transform the state based on the result of the task. For
 * example:
 * {{{
 *   val foo = AttributeKey[String]("foo")
 *   val setFoo = taskKey[StateTransform]("")
 *   setFoo := {
 *     state.value.get(foo) match {
 *       case None => StateTransform(_.put(foo, "foo"))
 *       case _ => StateTransform(identity)
 *     }
 *   }
 *   val getFoo = taskKey[Option[String]]
 *   getFoo := state.value.get(foo)
 * }}}
 * Prior to a call to `setFoo`, `getFoo` will return `None`. After a call to `setFoo`, `getFoo` will
 * return `Some("foo")`.
 */
final class StateTransform private (val transform: State => State, stateProxy: () => State) {
  @deprecated("Exists only for binary compatibility with 1.3.x.", "1.4.0")
  private[sbt] def state: State = stateProxy()
  @deprecated("1.4.0", "Use the constructor that takes a transform function.")
  private[sbt] def this(state: State) = this((_: State) => state, () => state)
}

object StateTransform {
  @deprecated("Exists only for binary compatibility with 1.3.x", "1.4.0")
  def apply(state: State): State = state

  /**
   * Create an instance of [[StateTransform]].
   * @param transform the transformation to apply after task evaluation has completed
   * @return the [[StateTransform]].
   */
  def apply(transform: State => State) =
    new StateTransform(
      transform,
      () => throw new IllegalStateException("No state was added to the StateTransform.")
    )
}
