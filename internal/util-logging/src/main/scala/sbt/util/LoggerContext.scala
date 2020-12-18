/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.util

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import org.apache.logging.log4j.{ Level => XLevel }
import org.apache.logging.log4j.core.{ Appender => XAppender, LoggerContext => XLoggerContext }
import org.apache.logging.log4j.core.config.{ AppenderRef, LoggerConfig }
import sbt.internal.util._
import scala.collection.JavaConverters._
import org.apache.logging.log4j.core.config.AbstractConfiguration
import org.apache.logging.log4j.message.ObjectMessage

/**
 * Provides a context for generating loggers during task evaluation. The logger context
 * can be initialized for a single command evaluation run and all of the resources
 * created (such as cached logger appenders) can be cleaned up after task evaluation.
 * This trait evolved out of LogExchange when it became clear that it was very difficult
 * to manage the loggers and appenders without introducing memory leaks.
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
  private[this] val useLog4J = System.getProperty("sbt.log.uselog4j", "false") == "true"
  private[this] lazy val global = new LoggerContext.LoggerContextImpl(Terminal.get)
  private[this] lazy val globalLog4J =
    new LoggerContext.Log4JLoggerContext(LogExchange.context, Terminal.get)
  private[sbt] lazy val globalContext = if (useLog4J) globalLog4J else global
  private[util] class Log4JLoggerContext(val xlc: XLoggerContext, term: Terminal)
      extends LoggerContext {
    private val config = xlc.getConfiguration match {
      case a: AbstractConfiguration => a
      case _                        => throw new IllegalStateException("")
    }
    val loggers = new java.util.Vector[String]
    private[this] val closed = new AtomicBoolean(false)
    override def logger(
        name: String,
        channelName: Option[String],
        execId: Option[String],
    ): ManagedLogger = {
      if (closed.get) {
        throw new IllegalStateException("Tried to create logger for closed LoggerContext")
      }
      val loggerConfig = LoggerConfig.createLogger(
        false,
        XLevel.DEBUG,
        name,
        // disable the calculation of caller location as it is very expensive
        // https://issues.apache.org/jira/browse/LOG4J2-153
        "false",
        Array[AppenderRef](),
        null,
        config,
        null
      )
      config.addLogger(name, loggerConfig)
      val logger = xlc.getLogger(name)
      LogExchange.addConfig(name, loggerConfig)
      loggers.add(name)
      val xlogger = new MiniLogger {
        def log(level: Level.Value, message: => String): Unit =
          logger.log(
            ConsoleAppender.toXLevel(level),
            new ObjectMessage(StringEvent(level.toString, message, channelName, execId))
          )
        def log[T](level: Level.Value, message: ObjectEvent[T]): Unit =
          logger.log(ConsoleAppender.toXLevel(level), new ObjectMessage(message))
      }
      new ManagedLogger(name, channelName, execId, xlogger, Option(term), this)
    }
    override def clearAppenders(loggerName: String): Unit = {
      val lc = config.getLoggerConfig(loggerName)
      lc.getAppenders.asScala foreach {
        case (name, a) =>
          a.stop()
          lc.removeAppender(name)
      }
    }
    override def addAppender(
        loggerName: String,
        appender: (Appender, Level.Value)
    ): Unit = {
      val lc = config.getLoggerConfig(loggerName)
      appender match {
        case (x: XAppender, lv) => lc.addAppender(x, ConsoleAppender.toXLevel(lv), null)
        case (x, lv)            => lc.addAppender(x.toLog4J, ConsoleAppender.toXLevel(lv), null)
      }
    }
    override def appenders(loggerName: String): Seq[Appender] = {
      val lc = config.getLoggerConfig(loggerName)
      lc.getAppenders.asScala.collect { case (name, ca: ConsoleAppender) => ca }.toVector
    }
    override def remove(name: String): Unit = {
      val lc = config.getLoggerConfig(name)
      config.removeLogger(name)
    }
    def close(): Unit = if (closed.compareAndSet(false, true)) {
      loggers.forEach(l => remove(l))
      loggers.clear()
    }
  }
  private[util] class LoggerContextImpl(term: Terminal) extends LoggerContext {
    private class Log(val term: Terminal) extends MiniLogger {
      private val consoleAppenders: java.util.Vector[(Appender, Level.Value)] =
        new java.util.Vector
      def log(level: Level.Value, message: => String): Unit = {
        val toAppend = consoleAppenders.asScala.filter { case (a, l) => level.compare(l) >= 0 }
        if (toAppend.nonEmpty) {
          val m = message
          toAppend.foreach { case (a, l) => a.appendLog(level, m) }
        }
      }
      def log[T](level: Level.Value, message: ObjectEvent[T]): Unit = {
        consoleAppenders.forEach {
          case (a, l) =>
            if (level.compare(l) >= 0) a.appendObjectEvent(level, message)
        }
      }
      def addAppender(newAppender: (Appender, Level.Value)): Unit =
        Util.ignoreResult(consoleAppenders.add(newAppender))
      def clearAppenders(): Unit = {
        consoleAppenders.forEach { case (a, _) => a.close() }
        consoleAppenders.clear()
      }
      def appenders: Seq[Appender] = consoleAppenders.asScala.map(_._1).toVector
    }
    private[this] val loggers = new ConcurrentHashMap[String, Log]
    private[this] val closed = new AtomicBoolean(false)
    override def logger(
        name: String,
        channelName: Option[String],
        execId: Option[String],
    ): ManagedLogger = {
      if (closed.get) {
        throw new IllegalStateException("Tried to create logger for closed LoggerContext")
      }
      val xlogger = new Log(term)
      loggers.put(name, xlogger)
      new ManagedLogger(name, channelName, execId, xlogger, Option(term), this)
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
    private[this] def clearAppenders(log: Log): Unit = log.term.prompt match {
      case b: Prompt.Blocked => b.addCallback(() => log.clearAppenders())
      case _                 => log.clearAppenders()
    }
    override def remove(name: String): Unit = {
      loggers.remove(name) match {
        case null =>
        case l    => clearAppenders(l)
      }
    }
    def close(): Unit = {
      loggers.forEach((name, l) => clearAppenders(l))
      loggers.clear()
    }
  }
  private[sbt] def apply(useLog4J: Boolean, term: Terminal) =
    if (useLog4J) new Log4JLoggerContext(LogExchange.context, term) else new LoggerContextImpl(term)
}
