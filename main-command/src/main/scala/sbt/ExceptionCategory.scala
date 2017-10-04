/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt

import java.lang.reflect.InvocationTargetException
import scala.annotation.tailrec

import sbt.internal.util.{ AlreadyHandledException, MessageOnlyException, UnprintableException }

private[sbt] sealed abstract class ExceptionCategory {
  def isFull: Boolean = false
}
private[sbt] object ExceptionCategory {

  @tailrec def apply(t: Throwable): ExceptionCategory = t match {
    case _: AlreadyHandledException | _: UnprintableException => AlreadyHandled
    case ite: InvocationTargetException =>
      val cause = ite.getCause
      if (cause == null || cause == ite) new Full(ite) else apply(cause)
    case _: MessageOnlyException => new MessageOnly(t.toString)
    case _                       => new Full(t)
  }

  object AlreadyHandled extends ExceptionCategory
  final class MessageOnly(val message: String) extends ExceptionCategory
  final class Full(val exception: Throwable) extends ExceptionCategory {
    override def isFull = true
  }
}
