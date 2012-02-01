/* sbt -- Simple Build Tool
 * Copyright 2011 Mark Harrah
 */
package sbt

final class MessageOnlyException(override val toString: String) extends RuntimeException(toString)

/** A dummy exception for the top-level exception handler to know that an exception
* has been handled, but is being passed further up to indicate general failure. */
final class AlreadyHandledException extends RuntimeException

/** A marker trait for a top-level exception handler to know that this exception
* doesn't make sense to display. */
trait UnprintableException extends Throwable