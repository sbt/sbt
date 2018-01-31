package sbt.util

abstract class AbstractLogger extends Logger {
  def getLevel: Level.Value
  def setLevel(newLevel: Level.Value): Unit
  def setTrace(flag: Int): Unit
  def getTrace: Int
  final def traceEnabled: Boolean = getTrace >= 0
  def successEnabled: Boolean
  def setSuccessEnabled(flag: Boolean): Unit

  def atLevel(level: Level.Value): Boolean = level.id >= getLevel.id
  def control(event: ControlEvent.Value, message: => String): Unit

  def logAll(events: Seq[LogEvent]): Unit

  /** Defined in terms of other methods in Logger and should not be called from them. */
  final def log(event: LogEvent): Unit = {
    event match {
      case s: Success       => success(s.msg)
      case l: Log           => log(l.level, l.msg)
      case t: Trace         => trace(t.exception)
      case setL: SetLevel   => setLevel(setL.newLevel)
      case setT: SetTrace   => setTrace(setT.level)
      case setS: SetSuccess => setSuccessEnabled(setS.enabled)
      case c: ControlEvent  => control(c.event, c.msg)
    }
  }
}
