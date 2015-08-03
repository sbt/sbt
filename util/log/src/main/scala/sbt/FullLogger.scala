/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package sbt

/** Promotes the simple Logger interface to the full AbstractLogger interface. */
class FullLogger(delegate: Logger) extends BasicLogger {
  override val ansiCodesSupported: Boolean = delegate.ansiCodesSupported
  def trace(t: => Throwable): Unit = {
    if (traceEnabled)
      delegate.trace(t)
  }
  def log(level: Level.Value, message: => String): Unit = {
    if (atLevel(level))
      delegate.log(level, message)
  }
  def success(message: => String): Unit =
    if (successEnabled)
      delegate.success(message)
  def control(event: ControlEvent.Value, message: => String): Unit =
    info(message)
  def logAll(events: Seq[LogEvent]): Unit = events.foreach(log)
}
object FullLogger {
  def apply(delegate: Logger): AbstractLogger =
    delegate match {
      case d: AbstractLogger => d
      case _                 => new FullLogger(delegate)
    }
}
