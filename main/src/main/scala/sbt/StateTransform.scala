/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

final class StateTransform(val state: State) {
  override def equals(o: Any): Boolean = o match {
    case that: StateTransform => this.state == that.state
    case _                    => false
  }
  override def hashCode: Int = state.hashCode
  override def toString: String = s"StateTransform($state)"
}
object StateTransform {
  def apply(state: State): State = state
}
