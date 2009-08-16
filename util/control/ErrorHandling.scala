package xsbt

object ErrorHandling
{
	def translate[T](msg: => String)(f: => T) =
		try { f }
		catch { case e => throw new TranslatedException(msg + e.toString, e) }
	def wideConvert[T](f: => T): Either[Throwable, T] =
		try { Right(f) }
		catch { case e => Left(e) } // TODO: restrict type of e
	def convert[T](f: => T): Either[Exception, T] =
		try { Right(f) }
		catch { case e: Exception => Left(e) }
}
final class TranslatedException private[xsbt](msg: String, cause: Throwable) extends RuntimeException(msg, cause)
{
	override def toString = msg
}