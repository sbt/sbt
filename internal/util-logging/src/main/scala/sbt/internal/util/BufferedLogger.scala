/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010 Mark Harrah
 */
package sbt.internal.util

import sbt.util._
import scala.collection.mutable.ListBuffer
import org.apache.logging.log4j.core.{ LogEvent => XLogEvent, Appender }
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.layout.PatternLayout
import java.util.concurrent.atomic.AtomicInteger

object BufferedAppender {
  def generateName: String =
    "buffered-" + generateId.incrementAndGet
  private val generateId: AtomicInteger = new AtomicInteger
  def apply(delegate: Appender): BufferedAppender =
    apply(generateName, delegate)
  def apply(name: String, delegate: Appender): BufferedAppender =
    {
      val appender = new BufferedAppender(name, delegate)
      appender.start
      appender
    }
}

/**
 * Am appender that can buffer the logging done on it and then can flush the buffer
 * to the delegate appender provided in the constructor.  Use 'record()' to
 * start buffering and then 'play' to flush the buffer to the backing appender.
 *  The logging level set at the time a message is originally logged is used, not
 * the level at the time 'play' is called.
 */
class BufferedAppender private[BufferedAppender] (name: String, delegate: Appender) extends AbstractAppender(name, null, PatternLayout.createDefaultLayout(), true) {
  private[this] val buffer = new ListBuffer[XLogEvent]
  private[this] var recording = false

  def append(event: XLogEvent): Unit =
    {
      if (recording) {
        buffer += event.toImmutable
      } else delegate.append(event)
    }

  /** Enables buffering. */
  def record() = synchronized { recording = true }
  def buffer[T](f: => T): T = {
    record()
    try { f }
    finally { stopQuietly() }
  }
  def bufferQuietly[T](f: => T): T = {
    record()
    try {
      val result = f
      clearBuffer()
      result
    } catch { case e: Throwable => stopQuietly(); throw e }
  }
  def stopQuietly() = synchronized { try { stopBuffer() } catch { case e: Exception => () } }

  /**
   * Flushes the buffer to the delegate logger.  This method calls logAll on the delegate
   * so that the messages are written consecutively. The buffer is cleared in the process.
   */
  def play(): Unit =
    synchronized {
      buffer.toList foreach {
        delegate.append
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
    try { f }
    finally { stopQuietly() }
  }
  def bufferQuietly[T](f: => T): T = {
    record()
    try {
      val result = f
      clear()
      result
    } catch { case e: Throwable => stopQuietly(); throw e }
  }
  def stopQuietly() = synchronized { try { stop() } catch { case e: Exception => () } }

  /**
   * Flushes the buffer to the delegate logger.  This method calls logAll on the delegate
   * so that the messages are written consecutively. The buffer is cleared in the process.
   */
  def play(): Unit = synchronized { delegate.logAll(buffer.toList); buffer.clear() }
  /** Clears buffered events and disables buffering. */
  def clear(): Unit = synchronized { buffer.clear(); recording = false }
  /** Plays buffered events and disables buffering. */
  def stop(): Unit = synchronized { play(); clear() }

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

  def trace(t: => Throwable): Unit =
    doBufferableIf(traceEnabled, new Trace(t), _.trace(t))
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
  private def doBufferable(level: Level.Value, appendIfBuffered: => LogEvent, doUnbuffered: AbstractLogger => Unit): Unit =
    doBufferableIf(atLevel(level), appendIfBuffered, doUnbuffered)
  private def doBufferableIf(condition: => Boolean, appendIfBuffered: => LogEvent, doUnbuffered: AbstractLogger => Unit): Unit = synchronized {
    if (condition) {
      if (recording)
        buffer += appendIfBuffered
      else
        doUnbuffered(delegate)
    }
    ()
  }
}
