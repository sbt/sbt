/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util

import sbt.util._
import scala.collection.mutable.ListBuffer
import org.apache.logging.log4j.core.{ LogEvent => XLogEvent }
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.layout.PatternLayout
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

object BufferedAppender {
  def generateName: String =
    "buffered-" + generateId.incrementAndGet

  private val generateId: AtomicInteger = new AtomicInteger

  def apply(delegate: Appender): BufferedAppender =
    apply(generateName, delegate)

  def apply(name: String, delegate: Appender): BufferedAppender =
    new BufferedAppender(name, delegate)
}

/**
 * An appender that can buffer the logging done on it and then can flush the buffer
 * to the delegate appender provided in the constructor.  Use 'record()' to
 * start buffering and then 'play' to flush the buffer to the backing appender.
 *  The logging level set at the time a message is originally logged is used, not
 * the level at the time 'play' is called.
 */
class BufferedAppender(override val name: String, delegate: Appender) extends Appender {
  override def close(): Unit = log4j.get match {
    case null =>
    case a    => a.stop()
  }
  override private[sbt] def properties: ConsoleAppender.Properties = delegate.properties
  override private[sbt] def suppressedMessage: SuppressedTraceContext => Option[String] =
    delegate.suppressedMessage
  private[this] val log4j = new AtomicReference[AbstractAppender]
  override private[sbt] def toLog4J = log4j.get match {
    case null =>
      val a = new AbstractAppender(
        delegate.name + "-log4j",
        null,
        PatternLayout.createDefaultLayout(),
        true,
        Array.empty
      ) {
        start()
        override def append(event: XLogEvent): Unit = {
          if (recording) {
            Util.ignoreResult(buffer.add(Left(event.toImmutable)))
          } else {
            delegate.toLog4J.append(event)
          }
        }
      }
      log4j.set(a)
      a
    case a => a
  }

  private[this] val buffer =
    new java.util.Vector[Either[XLogEvent, (Level.Value, Option[String], Option[ObjectEvent[_]])]]
  private[this] var recording = false

  override def appendLog(level: Level.Value, message: => String): Unit = {
    if (recording) Util.ignoreResult(buffer.add(Right((level, Some(message), None))))
    else delegate.appendLog(level, message)
  }
  override private[sbt] def appendObjectEvent[T](
      level: Level.Value,
      message: => ObjectEvent[T]
  ): Unit = {
    if (recording) Util.ignoreResult(buffer.add(Right(((level, None, Some(message))))))
    else delegate.appendObjectEvent(level, message)
  }

  /** Enables buffering. */
  def record() = synchronized { recording = true }
  def buffer[T](f: => T): T = {
    record()
    try {
      f
    } finally {
      stopQuietly()
    }
  }
  def bufferQuietly[T](f: => T): T = {
    record()
    try {
      val result = f
      clearBuffer()
      result
    } catch { case e: Throwable => stopQuietly(); throw e }
  }
  def stopQuietly() = synchronized {
    try {
      stopBuffer()
    } catch { case _: Exception => () }
  }

  /**
   * Flushes the buffer to the delegate logger.  This method calls logAll on the delegate
   * so that the messages are written consecutively. The buffer is cleared in the process.
   */
  def play(): Unit =
    synchronized {
      buffer.forEach {
        case Right((l, Some(m), _))  => delegate.appendLog(l, m)
        case Right((l, _, Some(oe))) => delegate.appendObjectEvent(l, oe)
        case Left(x)                 => delegate.toLog4J.append(x)
        case _                       =>
      }
      buffer.clear()
    }

  /** Clears buffered events and disables buffering. */
  def clearBuffer(): Unit = synchronized { buffer.clear(); recording = false }

  /** Plays buffered events and disables buffering. */
  def stopBuffer(): Unit = synchronized { play(); clearBuffer() }

}

/**
 * A logger that can buffer the logging done on it and then can flush the buffer
 * to the delegate logger provided in the constructor.  Use 'startRecording' to
 * start buffering and then 'play' from to flush the buffer to the backing logger.
 *  The logging level set at the time a message is originally logged is used, not
 * the level at the time 'play' is called.
 *
 * This class assumes that it is the only client of the delegate logger.
 */
class BufferedLogger(delegate: AbstractLogger) extends BasicLogger {
  private[this] val buffer = new ListBuffer[LogEvent]
  private[this] var recording = false

  /** Enables buffering. */
  def record() = synchronized { recording = true }
  def buffer[T](f: => T): T = {
    record()
    try {
      f
    } finally {
      stopQuietly()
    }
  }
  def bufferQuietly[T](f: => T): T = {
    record()
    try {
      val result = f
      clear()
      result
    } catch { case e: Throwable => stopQuietly(); throw e }
  }
  def stopQuietly() = synchronized {
    try {
      stop()
    } catch { case _: Exception => () }
  }

  /**
   * Flushes the buffer to the delegate logger.  This method calls logAll on the delegate
   * so that the messages are written consecutively. The buffer is cleared in the process.
   */
  def play(): Unit = synchronized { delegate.logAll(buffer.toList); buffer.clear() }

  /** Clears buffered events and disables buffering. */
  def clear(): Unit = synchronized { buffer.clear(); recording = false }

  /** Plays buffered events and disables buffering. */
  def stop(): Unit = synchronized { play(); clear() }

  @deprecated("No longer used.", "1.0.0")
  override def ansiCodesSupported = delegate.ansiCodesSupported

  override def setLevel(newLevel: Level.Value): Unit = synchronized {
    super.setLevel(newLevel)
    if (recording)
      buffer += new SetLevel(newLevel)
    else
      delegate.setLevel(newLevel)
    ()
  }

  override def setSuccessEnabled(flag: Boolean): Unit = synchronized {
    super.setSuccessEnabled(flag)
    if (recording)
      buffer += new SetSuccess(flag)
    else
      delegate.setSuccessEnabled(flag)
    ()
  }

  override def setTrace(level: Int): Unit = synchronized {
    super.setTrace(level)
    if (recording)
      buffer += new SetTrace(level)
    else
      delegate.setTrace(level)
    ()
  }

  def trace(t: => Throwable): Unit = doBufferableIf(traceEnabled, new Trace(t), _.trace(t))

  def success(message: => String): Unit =
    doBufferable(Level.Info, new Success(message), _.success(message))

  def log(level: Level.Value, message: => String): Unit =
    doBufferable(level, new Log(level, message), _.log(level, message))

  def logAll(events: Seq[LogEvent]): Unit = synchronized {
    if (recording)
      buffer ++= events
    else
      delegate.logAll(events)
    ()
  }

  def control(event: ControlEvent.Value, message: => String): Unit =
    doBufferable(Level.Info, new ControlEvent(event, message), _.control(event, message))

  private def doBufferable(
      level: Level.Value,
      appendIfBuffered: => LogEvent,
      doUnbuffered: AbstractLogger => Unit
  ): Unit =
    doBufferableIf(atLevel(level), appendIfBuffered, doUnbuffered)

  private def doBufferableIf(
      condition: => Boolean,
      appendIfBuffered: => LogEvent,
      doUnbuffered: AbstractLogger => Unit
  ): Unit = synchronized {
    if (condition) {
      if (recording)
        buffer += appendIfBuffered
      else
        doUnbuffered(delegate)
    }
    ()
  }
}
