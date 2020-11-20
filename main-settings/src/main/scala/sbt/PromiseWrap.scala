/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import scala.concurrent.{ Promise => XPromise }

final class PromiseWrap[A] {
  private[sbt] val underlying: XPromise[A] = XPromise()
  def complete(result: Result[A]): Unit =
    result match {
      case Inc(cause)   => underlying.failure(cause)
      case Value(value) => underlying.success(value)
    }
  def success(value: A): Unit = underlying.success(value)
  def failure(cause: Throwable): Unit = underlying.failure(cause)
  def isCompleted: Boolean = underlying.isCompleted
}
