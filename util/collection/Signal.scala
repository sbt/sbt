package sbt

object Signals
{
	val CONT = "CONT"
	val INT = "INT"
	def withHandler[T](handler: () => Unit, signal: String = INT)(action: () => T): T =
	{
		val result = 
			try
			{
				val signals = new Signals0
				signals.withHandler(signal, handler, action)
			}
			catch { case e: LinkageError => Right(action()) }

		result match {
			case Left(e) => throw e
			case Right(v) => v
		}
	}
	def supported(signal: String): Boolean =
		try
		{
			val signals = new Signals0
			signals.supported(signal)
		}
		catch { case e: LinkageError => false }
}

// Must only be referenced using a
//   try { } catch { case e: LinkageError => ... }
// block to 
private final class Signals0
{
	def supported(signal: String): Boolean =
	{
			import sun.misc.Signal
		try { new Signal(signal); true }
		catch { case e: IllegalArgumentException => false }
	}

	// returns a LinkageError in `action` as Left(t) in order to avoid it being
	// incorrectly swallowed as missing Signal/SignalHandler
	def withHandler[T](signal: String, handler: () => Unit, action: () => T): Either[Throwable, T] =
	{
			import sun.misc.{Signal,SignalHandler}
		val intSignal = new Signal(signal)
		val newHandler = new SignalHandler {
			def handle(sig: Signal) { handler() }
		}	

		val oldHandler = Signal.handle(intSignal, newHandler)

		try Right(action())
		catch { case e: LinkageError => Left(e) }
		finally Signal.handle(intSignal, oldHandler)
	}
}