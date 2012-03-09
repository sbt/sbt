package sbt

object Signals
{
	def withHandler[T](handler: () => Unit, signal: String = "INT")(action: () => T): T =
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
}

// Must only be referenced using a
//   try { } catch { case e: LinkageError => ... }
// block to 
private final class Signals0
{
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