/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010  Mark Harrah
 */
package sbt

import xsbti.{ Logger => xLogger, F0 }
import xsbti.{ Maybe, Position, Problem, Severity }

import java.io.File

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

object Logger {
  def transferLevels(oldLog: AbstractLogger, newLog: AbstractLogger): Unit = {
    newLog.setLevel(oldLog.getLevel)
    newLog.setTrace(oldLog.getTrace)
  }

  val Null: AbstractLogger = new AbstractLogger {
    def getLevel: Level.Value = Level.Error
    def setLevel(newLevel: Level.Value): Unit = ()
    def getTrace: Int = 0
    def setTrace(flag: Int): Unit = ()
    def successEnabled: Boolean = false
    def setSuccessEnabled(flag: Boolean): Unit = ()
    def control(event: ControlEvent.Value, message: => String): Unit = ()
    def logAll(events: Seq[LogEvent]): Unit = ()
    def trace(t: => Throwable): Unit = ()
    def success(message: => String): Unit = ()
    def log(level: Level.Value, message: => String): Unit = ()
  }

  implicit def absLog2PLog(log: AbstractLogger): ProcessLogger = new BufferedLogger(log) with ProcessLogger
  implicit def log2PLog(log: Logger): ProcessLogger = absLog2PLog(new FullLogger(log))
  implicit def xlog2Log(lg: xLogger): Logger = lg match {
    case l: Logger => l
    case _         => wrapXLogger(lg)
  }
  private[this] def wrapXLogger(lg: xLogger): Logger = new Logger {
    override def debug(msg: F0[String]): Unit = lg.debug(msg)
    override def warn(msg: F0[String]): Unit = lg.warn(msg)
    override def info(msg: F0[String]): Unit = lg.info(msg)
    override def error(msg: F0[String]): Unit = lg.error(msg)
    override def trace(msg: F0[Throwable]): Unit = lg.trace(msg)
    override def log(level: Level.Value, msg: F0[String]): Unit = lg.log(level, msg)
    def trace(t: => Throwable): Unit = trace(f0(t))
    def success(s: => String): Unit = info(f0(s))
    def log(level: Level.Value, msg: => String): Unit =
      {
        val fmsg = f0(msg)
        level match {
          case Level.Debug => lg.debug(fmsg)
          case Level.Info  => lg.info(fmsg)
          case Level.Warn  => lg.warn(fmsg)
          case Level.Error => lg.error(fmsg)
        }
      }
  }
  def f0[T](t: => T): F0[T] = new F0[T] { def apply = t }

  def m2o[S](m: Maybe[S]): Option[S] = if (m.isDefined) Some(m.get) else None
  def o2m[S](o: Option[S]): Maybe[S] = o match { case Some(v) => Maybe.just(v); case None => Maybe.nothing() }

  def position(line0: Option[Integer], content: String, offset0: Option[Integer], pointer0: Option[Integer], pointerSpace0: Option[String], sourcePath0: Option[String], sourceFile0: Option[File]): Position =
    new Position {
      val line = o2m(line0)
      val lineContent = content
      val offset = o2m(offset0)
      val pointer = o2m(pointer0)
      val pointerSpace = o2m(pointerSpace0)
      val sourcePath = o2m(sourcePath0)
      val sourceFile = o2m(sourceFile0)
    }

  def problem(cat: String, pos: Position, msg: String, sev: Severity): Problem =
    new Problem {
      val category = cat
      val position = pos
      val message = msg
      val severity = sev
      override def toString = s"[$severity] $pos: $message"
    }
}

/**
 * This is intended to be the simplest logging interface for use by code that wants to log.
 * It does not include configuring the logger.
 */
trait Logger extends xLogger {
  final def verbose(message: => String): Unit = debug(message)
  final def debug(message: => String): Unit = log(Level.Debug, message)
  final def info(message: => String): Unit = log(Level.Info, message)
  final def warn(message: => String): Unit = log(Level.Warn, message)
  final def error(message: => String): Unit = log(Level.Error, message)

  def ansiCodesSupported: Boolean = false

  def trace(t: => Throwable): Unit
  def success(message: => String): Unit
  def log(level: Level.Value, message: => String): Unit

  def debug(msg: F0[String]): Unit = log(Level.Debug, msg)
  def warn(msg: F0[String]): Unit = log(Level.Warn, msg)
  def info(msg: F0[String]): Unit = log(Level.Info, msg)
  def error(msg: F0[String]): Unit = log(Level.Error, msg)
  def trace(msg: F0[Throwable]): Unit = trace(msg.apply)
  def log(level: Level.Value, msg: F0[String]): Unit = log(level, msg.apply)
}
