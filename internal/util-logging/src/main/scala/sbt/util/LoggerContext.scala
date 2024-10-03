/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.util

import sbt.internal.util._

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.{ AtomicReference, AtomicBoolean }
// import scala.jdk.CollectionConverters.*

/**
 * Provides a context for generating loggers during task evaluation. The logger context can be
 * initialized for a single command evaluation run and all of the resources created (such as cached
 * logger appenders) can be cleaned up after task evaluation. This trait evolved out of LogExchange
 * when it became clear that it was very difficult to manage the loggers and appenders without
 * introducing memory leaks.
 */
sealed trait LoggerContext extends AutoCloseable {
  def logger(name: String, channelName: Option[String], execId: Option[String]): ManagedLogger
  def clearAppenders(loggerName: String): Unit
  def addAppender(
      loggerName: String,
      appender: (Appender, Level.Value)
  ): Unit
  def appenders(loggerName: String): Seq[Appender]
  def remove(name: String): Unit
}
object LoggerContext {
  private[sbt] lazy val globalContext: LoggerContext = new LoggerContext.LoggerContextImpl

  private[util] class LoggerContextImpl extends LoggerContext {
    private class Log extends MiniLogger {
      private val consoleAppenders: AtomicReference[Vector[(Appender, Level.Value)]] =
        new AtomicReference(Vector.empty)
      def log(level: Level.Value, message: => String): Unit = {
        val toAppend = consoleAppenders.get.filter { case (a, l) => level.compare(l) >= 0 }
        if (toAppend.nonEmpty) {
          val m = message
          toAppend.foreach { case (a, l) => a.appendLog(level, m) }
        }
      }
      def log[T](level: Level.Value, message: ObjectEvent[T]): Unit = {
        consoleAppenders.get.foreach { case (a, l) =>
          if (level.compare(l) >= 0) a.appendObjectEvent(level, message)
        }
      }
      def addAppender(newAppender: (Appender, Level.Value)): Unit =
        Util.ignoreResult(consoleAppenders.updateAndGet(_ :+ newAppender))
      def clearAppenders(): Unit = {
        consoleAppenders.get.foreach { case (a, _) => a.close() }
        consoleAppenders.set(Vector.empty)
      }
      def appenders: Seq[Appender] = consoleAppenders.get.map(_._1)
    }
    private val loggers = new ConcurrentHashMap[String, Log]
    private val closed = new AtomicBoolean(false)
    override def logger(
        name: String,
        channelName: Option[String],
        execId: Option[String]
    ): ManagedLogger = {
      if (closed.get) {
        throw new IllegalStateException("Tried to create logger for closed LoggerContext")
      }
      val xlogger = new Log
      loggers.put(name, xlogger)
      new ManagedLogger(name, channelName, execId, xlogger, Some(Terminal.get), this)
    }
    override def clearAppenders(loggerName: String): Unit = {
      loggers.get(loggerName) match {
        case null =>
        case l    => l.clearAppenders()
      }
    }
    override def addAppender(
        loggerName: String,
        appender: (Appender, Level.Value)
    ): Unit = {
      if (closed.get) {
        throw new IllegalStateException("Tried to add appender for closed LoggerContext")
      }
      loggers.get(loggerName) match {
        case null =>
        case l    => l.addAppender(appender)
      }
    }
    override def appenders(loggerName: String): Seq[Appender] = {
      loggers.get(loggerName) match {
        case null => Nil
        case l    => l.appenders
      }
    }
    override def remove(name: String): Unit = {
      loggers.remove(name) match {
        case null =>
        case l    => l.clearAppenders()
      }
    }
    def close(): Unit = {
      closed.set(true)
      loggers.forEach((_, l) => l.clearAppenders())
      loggers.clear()
    }
  }
  private[sbt] def apply() = new LoggerContextImpl
}
