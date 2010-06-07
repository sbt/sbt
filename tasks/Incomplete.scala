/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package sbt

import Incomplete.{Error, Value => IValue}
final case class Incomplete(tpe: IValue = Error, message: Option[String] = None, causes: Seq[Incomplete] = Nil, directCause: Option[Throwable] = None)
	extends Exception(message.orNull, directCause.orNull)

object Incomplete extends Enumeration {
	val Skipped, Error = Value
}