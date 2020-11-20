/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util

import sun.misc.{ Signal, SignalHandler }

object Signals {
  val CONT = "CONT"
  val INT = "INT"

  def withHandler[T](handler: () => Unit, signal: String = INT)(action: () => T): T = {
    val result =
      try {
        val signals = new Signals0
        signals.withHandler(signal, handler, action)
      } catch { case _: LinkageError => Right(action()): Either[Throwable, T] }

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
      val intSignal = new Signal(signal)
      val newHandler = new SignalHandler {
        def handle(sig: Signal): Unit = { handler() }
      }
      val oldHandler = Signal.handle(intSignal, newHandler)
      new UnregisterNewHandler(intSignal, oldHandler)
    } else {
      // TODO - Maybe we should just throw an exception if we don't support signals...
      NullUnregisterNewHandler
    }

  def supported(signal: String): Boolean =
    try {
      val signals = new Signals0
      signals.supported(signal)
    } catch { case _: LinkageError => false }
}

private class UnregisterNewHandler(intSignal: Signal, oldHandler: SignalHandler)
    extends Signals.Registration {
  override def remove(): Unit = {
    Signal.handle(intSignal, oldHandler)
    ()
  }
}
private object NullUnregisterNewHandler extends Signals.Registration {
  override def remove(): Unit = ()
}

// Must only be referenced using a
//   try { } catch { case _: LinkageError => ... }
// block to
private final class Signals0 {
  def supported(signal: String): Boolean = {
    import sun.misc.Signal
    try {
      new Signal(signal); true
    } catch { case _: IllegalArgumentException => false }
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
    catch { case e: LinkageError => Left(e) } finally {
      Signal.handle(intSignal, oldHandler); ()
    }
  }
}
