/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010 Mark Harrah
 */
package sbt

/**
 * A filter logger is used to delegate messages but not the logging level to another logger.  This means
 * that messages are logged at the higher of the two levels set by this logger and its delegate.
 */
class FilterLogger(delegate: AbstractLogger) extends BasicLogger {
  override lazy val ansiCodesSupported = delegate.ansiCodesSupported
  def trace(t: => Throwable): Unit = {
    if (traceEnabled)
      delegate.trace(t)
  }
  override def setSuccessEnabled(flag: Boolean): Unit = delegate.setSuccessEnabled(flag)
  override def successEnabled = delegate.successEnabled
  override def setTrace(level: Int): Unit = delegate.setTrace(level)
  override def getTrace = delegate.getTrace
  def log(level: Level.Value, message: => String): Unit = {
    if (atLevel(level))
      delegate.log(level, message)
  }
  def success(message: => String): Unit = {
    if (successEnabled)
      delegate.success(message)
  }
  def control(event: ControlEvent.Value, message: => String): Unit = {
    if (atLevel(Level.Info))
      delegate.control(event, message)
  }
  def logAll(events: Seq[LogEvent]): Unit = delegate.logAll(events)
}
