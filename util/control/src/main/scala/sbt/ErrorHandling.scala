/* sbt -- Simple Build Tool
 * Copyright 2009  Mark Harrah
 */
package sbt

	import java.io.IOException

object ErrorHandling
{
	def translate[T](msg: => String)(f: => T) =
		try { f }
		catch {
			case e: IOException => throw new TranslatedIOException(msg + e.toString, e)
			case e: Exception => throw new TranslatedException(msg + e.toString, e)
		}

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

	def reducedToString(e: Throwable): String =
		if(e.getClass == classOf[RuntimeException])
		{
			val msg = e.getMessage
			if(msg == null || msg.isEmpty) e.toString else msg
		}
		else
			e.toString
}
sealed class TranslatedException private[sbt](msg: String, cause: Throwable) extends RuntimeException(msg, cause)
{
	override def toString = msg
}
final class TranslatedIOException private[sbt](msg: String, cause: IOException) extends TranslatedException(msg, cause)
