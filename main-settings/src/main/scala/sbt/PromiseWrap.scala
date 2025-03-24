/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import scala.concurrent.{ Promise as XPromise }

final class PromiseWrap[A]:
  private[sbt] val underlying: XPromise[A] = XPromise()
  def complete(result: Result[A]): Unit =
    result match {
      case Result.Inc(cause)   => underlying.failure(cause)
      case Result.Value(value) => underlying.success(value)
    }
  def success(value: A): Unit = underlying.success(value)
  def failure(cause: Throwable): Unit = underlying.failure(cause)
  def isCompleted: Boolean = underlying.isCompleted
end PromiseWrap
