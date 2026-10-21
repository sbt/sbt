/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.util

import java.lang.ref.WeakReference
import sbt.internal.util.Appender

object LoggerContextAccess {
  private def impl(context: LoggerContext): LoggerContext.LoggerContextImpl = context match {
    case c: LoggerContext.LoggerContextImpl => c
    case other                              => sys.error(s"unexpected logger context: $other")
  }

  def released(context: LoggerContext): Set[Appender] = impl(context).releasedAppenders

  def closedAppenders(context: LoggerContext): Set[Appender] = impl(context).closedAppenderSet

  def bridgeable(context: LoggerContext): Vector[WeakReference[Appender]] =
    impl(context).bridgeableReferences

  def drainCollected(context: LoggerContext): Int = impl(context).drainCollected()

  def holdingReleaseLock[A](context: LoggerContext)(body: => A): A =
    impl(context).holdingReleaseLock(body)
}
