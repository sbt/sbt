/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt.internal.util

object Signals {
  val CONT = "CONT"
  val INT = "INT"

  def withHandler[T](handler: () => Unit, signal: String = INT)(action: () => T): T = {
    val result =
      try {
        val signals = new Signals0
        signals.withHandler(signal, handler, action)
      } catch { case _: LinkageError => Right(action()) }

    result match {
      case Left(e)  => throw e
      case Right(v) => v
    }
  }

  /** Helper interface so we can expose internals of signal-isms to others. */
  sealed trait Registration {
    def remove(): Unit
  }

  /**
   * Register a signal handler that can be removed later.
   * NOTE: Does not stack with other signal handlers!!!!
   */
  def register(handler: () => Unit, signal: String = INT): Registration =
    // TODO - Maybe we can just ignore things if not is-supported.
    if (supported(signal)) {
      import sun.misc.{ Signal, SignalHandler }
      val intSignal = new Signal(signal)
      val newHandler = new SignalHandler {
        def handle(sig: Signal): Unit = { handler() }
      }
      val oldHandler = Signal.handle(intSignal, newHandler)
      object unregisterNewHandler extends Registration {
        override def remove(): Unit = {
          Signal.handle(intSignal, oldHandler)
          ()
        }
      }
      unregisterNewHandler
    } else {
      // TODO - Maybe we should just throw an exception if we don't support signals...
      object NullUnregisterNewHandler extends Registration {
        override def remove(): Unit = ()
      }
      NullUnregisterNewHandler
    }

  def supported(signal: String): Boolean =
    try {
      val signals = new Signals0
      signals.supported(signal)
    } catch { case _: LinkageError => false }
}

// Must only be referenced using a
//   try { } catch { case e: LinkageError => ... }
// block to
private final class Signals0 {
  def supported(signal: String): Boolean = {
    import sun.misc.Signal
    try { new Signal(signal); true } catch { case _: IllegalArgumentException => false }
  }

  // returns a LinkageError in `action` as Left(t) in order to avoid it being
  // incorrectly swallowed as missing Signal/SignalHandler
  def withHandler[T](signal: String, handler: () => Unit, action: () => T): Either[Throwable, T] = {
    import sun.misc.{ Signal, SignalHandler }
    val intSignal = new Signal(signal)
    val newHandler = new SignalHandler {
      def handle(sig: Signal): Unit = { handler() }
    }

    val oldHandler = Signal.handle(intSignal, newHandler)

    try Right(action())
    catch { case e: LinkageError => Left(e) } finally { Signal.handle(intSignal, oldHandler); () }
  }
}
