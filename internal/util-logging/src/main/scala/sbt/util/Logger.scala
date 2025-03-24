/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.util

import xsbti.{ Logger as xLogger }
import xsbti.{ Position, Problem, Severity }

import sys.process.ProcessLogger
import sbt.internal.util.{ BufferedLogger, FullLogger }
import java.io.File
import java.util.Optional
import java.util.function.Supplier

/**
 * This is intended to be the simplest logging interface for use by code that wants to log. It does
 * not include configuring the logger.
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

  @deprecated("No longer used.", "1.0.0")
  def ansiCodesSupported: Boolean = false

  def trace(t: => Throwable): Unit
  def success(message: => String): Unit
  def log(level: Level.Value, message: => String): Unit

  def verbose(msg: Supplier[String]): Unit = debug(msg)
  def debug(msg: Supplier[String]): Unit = log(Level.Debug, msg)
  def warn(msg: Supplier[String]): Unit = log(Level.Warn, msg)
  def info(msg: Supplier[String]): Unit = log(Level.Info, msg)
  def error(msg: Supplier[String]): Unit = log(Level.Error, msg)
  def trace(msg: Supplier[Throwable]): Unit = trace(msg.get())
  def success(msg: Supplier[String]): Unit = success(msg.get())
  def log(level: Level.Value, msg: Supplier[String]): Unit = log(level, msg.get)
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

  implicit def absLog2PLog(log: AbstractLogger): ProcessLogger =
    new BufferedLogger(log) with ProcessLogger

  implicit def log2PLog(log: Logger): ProcessLogger = absLog2PLog(new FullLogger(log))

  implicit def xlog2Log(lg: xLogger): Logger = lg match {
    case l: Logger => l
    case _         => wrapXLogger(lg)
  }

  private def wrapXLogger(lg: xLogger): Logger = new Logger {
    import InterfaceUtil.toSupplier
    override def debug(msg: Supplier[String]): Unit = lg.debug(msg)
    override def warn(msg: Supplier[String]): Unit = lg.warn(msg)
    override def info(msg: Supplier[String]): Unit = lg.info(msg)
    override def error(msg: Supplier[String]): Unit = lg.error(msg)
    override def trace(msg: Supplier[Throwable]): Unit = lg.trace(msg)
    override def log(level: Level.Value, msg: Supplier[String]): Unit = lg.log(level, msg)
    def trace(t: => Throwable): Unit = trace(toSupplier(t))
    def success(s: => String): Unit = info(toSupplier(s))
    def log(level: Level.Value, msg: => String): Unit = {
      val fmsg = toSupplier(msg)
      level match {
        case Level.Debug => lg.debug(fmsg)
        case Level.Info  => lg.info(fmsg)
        case Level.Warn  => lg.warn(fmsg)
        case Level.Error => lg.error(fmsg)
      }
    }
  }

  def jo2o[A](o: Optional[A]): Option[A] = InterfaceUtil.jo2o(o)
  def o2jo[A](o: Option[A]): Optional[A] = InterfaceUtil.o2jo(o)

  @deprecated("Use InterfaceUtil.position", "1.2.2")
  def position(
      line0: Option[Integer],
      content: String,
      offset0: Option[Integer],
      pointer0: Option[Integer],
      pointerSpace0: Option[String],
      sourcePath0: Option[String],
      sourceFile0: Option[File]
  ): Position =
    InterfaceUtil.position(
      line0,
      content,
      offset0,
      pointer0,
      pointerSpace0,
      sourcePath0,
      sourceFile0
    )

  @deprecated("Use InterfaceUtil.problem", "1.2.2")
  def problem(cat: String, pos: Position, msg: String, sev: Severity): Problem =
    InterfaceUtil.problem(cat, pos, msg, sev)
}
