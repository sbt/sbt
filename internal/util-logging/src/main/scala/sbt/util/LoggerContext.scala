/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.util

import java.lang.ref.{ ReferenceQueue, WeakReference }
import java.util.{ Collections, HashSet, IdentityHashMap }
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.apache.logging.log4j.{ Level => XLevel }
import org.apache.logging.log4j.core.{ Appender => XAppender, LoggerContext => XLoggerContext }
import org.apache.logging.log4j.core.config.{ AppenderRef, LoggerConfig }
import sbt.internal.util._
import scala.annotation.nowarn
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

  /**
   * Removes the logger and detaches its appenders without closing them, because other loggers may
   * share them. Appenders other than sbt's console and buffered appenders, and those that have a
   * log4j bridge by then, are closed once when this context closes.
   */
  private[sbt] def release(name: String): Unit = remove(name)
}
object LoggerContext {
  private[this] val useLog4J = System.getProperty("sbt.log.uselog4j", "false") == "true"
  private[this] lazy val global = new LoggerContext.LoggerContextImpl
  private[this] lazy val globalLog4J = new LoggerContext.Log4JLoggerContext(LogExchange.context)
  private[sbt] lazy val globalContext = if (useLog4J) globalLog4J else global
  private[util] class Log4JLoggerContext(val xlc: XLoggerContext) extends LoggerContext {
    private val config = xlc.getConfiguration match {
      case a: AbstractConfiguration => a
      case _                        => throw new IllegalStateException("")
    }
    val loggers = new java.util.HashSet[String]
    private[this] val closed = new AtomicBoolean(false)
    override def logger(
        name: String,
        channelName: Option[String],
        execId: Option[String]
    ): ManagedLogger = {
      if (closed.get) {
        throw new IllegalStateException("Tried to create logger for closed LoggerContext")
      }
      @nowarn
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
      new ManagedLogger(name, channelName, execId, xlogger, Some(Terminal.get), this)
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
        consoleAppenders.get.foreach {
          case (a, l) =>
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
      def detach(): Seq[Appender] = consoleAppenders.getAndSet(Vector.empty).map(_._1)
    }
    private[this] val loggers = new ConcurrentHashMap[String, Log]
    private[this] val closed = new AtomicBoolean(false)
    private[this] val released = identitySet()
    private[this] val closedAppenders = identitySet()
    private[this] val bridgeable = new HashSet[WeakIdentity]
    private[this] val collected = new ReferenceQueue[Appender]
    private[this] def expunge(): Unit = {
      var stale = collected.poll()
      while (stale != null) {
        bridgeable.remove(stale)
        stale = collected.poll()
      }
    }
    private[this] def identitySet() =
      Collections.newSetFromMap(new IdentityHashMap[Appender, java.lang.Boolean])
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
    override private[sbt] def release(name: String): Unit =
      loggers.remove(name) match {
        case null =>
        case l =>
          val detached = l.detach()
          val closeNow = released.synchronized {
            val (closeLater, unbridged) = detached.partition(!LoggerContext.closesTrivially(_))
            if (closed.get) closeLater.filter(a => closedAppenders.add(a))
            else {
              closeLater.foreach(released.add)
              expunge()
              unbridged.foreach(a => bridgeable.add(new WeakIdentity(a, collected)))
              Nil
            }
          }
          closeNow.foreach(_.close())
      }
    def close(): Unit = {
      val toClose = released.synchronized {
        closed.set(true)
        loggers.forEach((_, l) => l.detach().foreach(released.add))
        loggers.clear()
        bridgeable.asScala.toVector
          .flatMap(r => Option(r.get))
          .filterNot(LoggerContext.closesTrivially)
          .foreach(released.add)
        bridgeable.clear()
        expunge()
        val all = released.asScala.toVector
        released.clear()
        all.filterNot(LoggerContext.closesTrivially).foreach(closedAppenders.add)
        all
      }
      toClose.foreach(_.close())
    }
  }
  private final class WeakIdentity(appender: Appender, queue: ReferenceQueue[Appender])
      extends WeakReference[Appender](appender, queue) {
    private[this] val hash = System.identityHashCode(appender)
    override def hashCode: Int = hash
    override def equals(other: Any): Boolean = other match {
      case that: WeakIdentity =>
        (this eq that) || (get match {
          case null => false
          case a    => a eq that.get
        })
      case _ => false
    }
  }
  private[this] val trivialClose = new ClassValue[java.lang.Boolean] {
    override def computeValue(c: Class[_]): java.lang.Boolean = {
      val owner = c.getMethod("close").getDeclaringClass
      owner == classOf[ConsoleAppender] || owner == classOf[BufferedAppender]
    }
  }
  private def closesTrivially(a: Appender): Boolean =
    trivialClose.get(a.getClass) && (a match {
      case c: ConsoleAppender  => !c.hasLog4J
      case b: BufferedAppender => !b.hasLog4J
      case _                   => true
    })
  private[sbt] def apply(useLog4J: Boolean) =
    if (useLog4J) new Log4JLoggerContext(LogExchange.context) else new LoggerContextImpl
}
