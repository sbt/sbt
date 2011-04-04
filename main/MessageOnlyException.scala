/* sbt -- Simple Build Tool
 * Copyright 2011 Mark Harrah
 */
package sbt

final class MessageOnlyException(override val toString: String) extends RuntimeException(toString)
final class NoMessageException extends RuntimeException