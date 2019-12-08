/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util

final class MessageOnlyException(override val toString: String) extends RuntimeException(toString)

/**
 * A dummy exception for the top-level exception handler to know that an exception
 * has been handled, but is being passed further up to indicate general failure.
 */
final class AlreadyHandledException(val underlying: Throwable) extends RuntimeException

/**
 * A marker trait for a top-level exception handler to know that this exception
 * doesn't make sense to display.
 */
trait UnprintableException extends Throwable

/**
 * A marker trait that refines UnprintableException to indicate to a top-level exception handler
 * that the code throwing this exception has already provided feedback to the user about the error condition.
 */
trait FeedbackProvidedException extends UnprintableException
