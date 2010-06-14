/* sbt -- Simple Build Tool
 * Copyright 2009  Mark Harrah
 */
package sbt

object ErrorHandling
{
	def translate[T](msg: => String)(f: => T) =
		try { f }
		catch { case e: Exception => throw new TranslatedException(msg + e.toString, e) }

	def wideConvert[T](f: => T): Either[Throwable, T] =
		try { Right(f) }
		catch
		{
			case ex @ (_: Exception | _: StackOverflowError) => Left(ex)
			case err @ (_: ThreadDeath | _: VirtualMachineError) => throw err
			case x => Left(x)
		}

	def convert[T](f: => T): Either[Exception, T] =
		try { Right(f) }
		catch { case e: Exception => Left(e) }
}
final class TranslatedException private[sbt](msg: String, cause: Throwable) extends RuntimeException(msg, cause)
{
	override def toString = msg
}