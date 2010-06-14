/* sbt -- Simple Build Tool
 * Copyright 2009, 2010  Mark Harrah
 */
package sbt
package classfile

trait ClassfileLogger
{
	def warn(msg: => String): Unit
	def error(msg: => String): Unit
	def trace(exception: => Throwable): Unit
}
object ClassfileLogger
{
	def convertErrorMessage[T](log: ClassfileLogger)(t: => T): Either[String, T] =
	{
		try { Right(t) }
		catch { case e: Exception => log.trace(e); Left(e.toString) }
	}
	def trapAndLog(log: ClassfileLogger)(execute: => Unit)
	{
		try { execute }
		catch { case e => log.trace(e); log.error(e.toString) }
	}
}