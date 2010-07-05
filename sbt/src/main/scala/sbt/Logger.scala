/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010 Mark Harrah
 */
package sbt

import scala.collection.mutable.{Buffer, HashMap, ListBuffer}

/** A logger that can buffer the logging done on it by currently executing Thread and
* then can flush the buffer to the delegate logger provided in the constructor.  Use
* 'startRecording' to start buffering and then 'play' from to flush the buffer for the
* current Thread to the backing logger.  The logging level set at the
* time a message is originally logged is used, not the level at the time 'play' is
* called.
*
* This class assumes that it is the only client of the delegate logger.
*
* This logger is thread-safe.
* */
final class BufferedLogger(delegate: Logger) extends AbstractLogger
{
	override lazy val ansiCodesSupported = delegate.ansiCodesSupported
	private[this] val buffers = wrap.Wrappers.weakMap[Thread, Buffer[LogEvent]]
	private[this] var recordingAll = false

	private[this] def getOrCreateBuffer = buffers.getOrElseUpdate(key, createBuffer)
	private[this] def buffer = if(recordingAll) Some(getOrCreateBuffer) else buffers.get(key)
	private[this] def createBuffer = new ListBuffer[LogEvent]
	private[this] def key = Thread.currentThread

	@deprecated def startRecording() = recordAll()
	/** Enables buffering for logging coming from the current Thread. */
	def record(): Unit = synchronized { buffers(key) = createBuffer }
	/** Enables buffering for logging coming from all Threads. */
	def recordAll(): Unit = synchronized{ recordingAll = true }
	def buffer[T](f: => T): T =
	{
		record()
		try { f }
		finally { Control.trap(stop()) }
	}
	def bufferAll[T](f: => T): T =
	{
		recordAll()
		try { f }
		finally { Control.trap(stopAll()) }
	}

	/** Flushes the buffer to the delegate logger for the current thread.  This method calls logAll on the delegate
	* so that the messages are written consecutively. The buffer is cleared in the process. */
	def play(): Unit =
 		synchronized
		{
			for(buffer <- buffers.get(key))
				delegate.logAll(wrap.Wrappers.readOnly(buffer))
		}
	def playAll(): Unit =
		synchronized
		{
			for(buffer <- buffers.values)
				delegate.logAll(wrap.Wrappers.readOnly(buffer))
		}
	/** Clears buffered events for the current thread and disables buffering. */
	def clear(): Unit = synchronized { buffers -= key }
	/** Clears buffered events for all threads and disables all buffering. */
	def clearAll(): Unit = synchronized { buffers.clear(); recordingAll = false }
	/** Plays buffered events for the current thread and disables buffering. */
	def stop(): Unit =
		synchronized
		{
			play()
			clear()
		}
	def stopAll(): Unit =
		synchronized
		{
			playAll()
			clearAll()
		}

	def setLevel(newLevel: Level.Value): Unit =
		synchronized
		{
			buffer.foreach{_  += new SetLevel(newLevel) }
			delegate.setLevel(newLevel)
		}
	def getLevel = synchronized { delegate.getLevel }
	def getTrace = synchronized { delegate.getTrace }
	def setTrace(level: Int): Unit =
		synchronized
		{
			buffer.foreach{_  += new SetTrace(level) }
			delegate.setTrace(level)
		}

	def trace(t: => Throwable): Unit =
		doBufferableIf(traceEnabled, new Trace(t), _.trace(t))
	def success(message: => String): Unit =
		doBufferable(Level.Info, new Success(message), _.success(message))
	def log(level: Level.Value, message: => String): Unit =
		doBufferable(level, new Log(level, message), _.log(level, message))
	def logAll(events: Seq[LogEvent]): Unit =
		synchronized
		{
			buffer match
			{
				case Some(b) => b ++= events
				case None => delegate.logAll(events)
			}
		}
	def control(event: ControlEvent.Value, message: => String): Unit =
		doBufferable(Level.Info, new ControlEvent(event, message), _.control(event, message))
	private def doBufferable(level: Level.Value, appendIfBuffered: => LogEvent, doUnbuffered: AbstractLogger => Unit): Unit =
		doBufferableIf(atLevel(level), appendIfBuffered, doUnbuffered)
	private def doBufferableIf(condition: => Boolean, appendIfBuffered: => LogEvent, doUnbuffered: AbstractLogger => Unit): Unit =
		synchronized
		{
			if(condition)
			{
				buffer match
				{
					case Some(b) => b += appendIfBuffered
					case None => doUnbuffered(delegate)
				}
			}
		}
}
