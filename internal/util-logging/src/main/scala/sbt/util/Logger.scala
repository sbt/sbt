/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010  Mark Harrah
 */
package sbt.util

import xsbti.{ Logger => xLogger, F0 }
import xsbti.{ Maybe, Position, Problem, Severity }
import sys.process.ProcessLogger
import sbt.internal.util.{ BufferedLogger, FullLogger }

import java.io.File
import java.util.Optional

/**
 * This is intended to be the simplest logging interface for use by code that wants to log.
 * It does not include configuring the logger.
 */
abstract class Logger extends xLogger {
  final def verbose(message: => String): Unit = debug(message)
  final def debug(message: => String): Unit = log(Level.Debug, message)
  final def info(message: => String): Unit = log(Level.Info, message)
  final def warn(message: => String): Unit = log(Level.Warn, message)
  final def error(message: => String): Unit = log(Level.Error, message)
  // Added by sys.process.ProcessLogger
  final def err(message: => String): Unit = log(Level.Error, message)
  // sys.process.ProcessLogger
  final def out(message: => String): Unit = log(Level.Info, message)

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
  def f0[A](a: => A): F0[A] = InterfaceUtil.f0[A](a)
  def m2o[A](m: Maybe[A]): Option[A] = InterfaceUtil.m2o(m)
  def o2m[A](o: Option[A]): Maybe[A] = InterfaceUtil.o2m(o)
  def jo2o[A](o: Optional[A]): Option[A] = InterfaceUtil.jo2o(o)
  def o2jo[A](o: Option[A]): Optional[A] = InterfaceUtil.o2jo(o)
  def position(line0: Option[Integer], content: String, offset0: Option[Integer], pointer0: Option[Integer],
    pointerSpace0: Option[String], sourcePath0: Option[String], sourceFile0: Option[File]): Position =
    InterfaceUtil.position(line0, content, offset0, pointer0, pointerSpace0, sourcePath0, sourceFile0)
  def problem(cat: String, pos: Position, msg: String, sev: Severity): Problem =
    InterfaceUtil.problem(cat, pos, msg, sev)
}
