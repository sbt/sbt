/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.util

import sbt.internal.util.*

import java.util.{ Collections, IdentityHashMap }
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.{ AtomicReference, AtomicBoolean }
import scala.jdk.CollectionConverters.*

/**
 * Provides a context for generating loggers during task evaluation. The logger context can be
 * initialized for a single command evaluation run and all of the resources created (such as cached
 * logger appenders) can be cleaned up after task evaluation. This trait evolved out of LogExchange
 * when it became clear that it was very difficult to manage the loggers and appenders without
 * introducing memory leaks.
 */
sealed trait LoggerContext extends AutoCloseable:
  def logger(name: String, channelName: Option[String], execId: Option[String]): ManagedLogger
  def clearAppenders(loggerName: String): Unit

  /**
   * Removes a single appender from the logger, leaving the other appenders in place, and closes it.
   * Does nothing if the appender is not bound to the logger.
   */
  def removeAppender(loggerName: String, appender: Appender): Unit
  def addAppender(
      loggerName: String,
      appender: (Appender, Level.Value)
  ): Unit
  def appenders(loggerName: String): Seq[Appender]
  def remove(name: String): Unit

  /**
   * Removes the logger and detaches its appenders without closing them, because other loggers may
   * share them. Appenders whose close does something are closed once when this context closes.
   */
  private[sbt] def release(name: String): Unit = remove(name)
end LoggerContext
object LoggerContext:
  private[sbt] lazy val globalContext: LoggerContext = new LoggerContext.LoggerContextImpl

  private[util] class LoggerContextImpl extends LoggerContext:
    private class Log extends MiniLogger:
      private val consoleAppenders: AtomicReference[Vector[(Appender, Level.Value)]] =
        new AtomicReference(Vector.empty)
      def log(level: Level.Value, message: => String): Unit =
        val toAppend = consoleAppenders.get.filter { (a, l) => level.compare(l) >= 0 }
        if toAppend.nonEmpty then
          val m = message
          toAppend.foreach { (a, l) => a.appendLog(level, m) }
      def log[T](level: Level.Value, message: ObjectEvent[T]): Unit =
        consoleAppenders.get.foreach { (a, l) =>
          if level.compare(l) >= 0 then a.appendObjectEvent(level, message)
        }
      def addAppender(newAppender: (Appender, Level.Value)): Unit =
        Util.ignoreResult(consoleAppenders.updateAndGet(_ :+ newAppender))
      def clearAppenders(): Unit =
        consoleAppenders.get.foreach { case (a, _) => a.close() }
        consoleAppenders.set(Vector.empty)
      def removeAppender(toRemove: Appender): Unit =
        val previous = consoleAppenders.getAndUpdate(_.filterNot { (a, _) => a eq toRemove })
        previous.foreach { (a, _) => if a eq toRemove then a.close() }
      def appenders: Seq[Appender] = consoleAppenders.get.map(_._1)
      def detach(): Seq[Appender] = consoleAppenders.getAndSet(Vector.empty).map(_._1)
    end Log
    private val loggers = new ConcurrentHashMap[String, Log]
    private val closed = new AtomicBoolean(false)
    private val released = identitySet()
    private val closedAppenders = identitySet()
    private def identitySet() =
      Collections.newSetFromMap(new IdentityHashMap[Appender, java.lang.Boolean])
    override def logger(
        name: String,
        channelName: Option[String],
        execId: Option[String]
    ): ManagedLogger =
      if closed.get then
        throw new IllegalStateException("Tried to create logger for closed LoggerContext")
      val xlogger = new Log
      loggers.put(name, xlogger)
      new ManagedLogger(name, channelName, execId, xlogger, Some(Terminal.get), this)
    override def clearAppenders(loggerName: String): Unit =
      loggers.get(loggerName) match
        case null =>
        case l    => l.clearAppenders()
    override def removeAppender(loggerName: String, appender: Appender): Unit =
      loggers.get(loggerName) match
        case null =>
        case l    => l.removeAppender(appender)
    override def addAppender(
        loggerName: String,
        appender: (Appender, Level.Value)
    ): Unit =
      if closed.get then
        throw new IllegalStateException("Tried to add appender for closed LoggerContext")
      loggers.get(loggerName) match
        case null =>
        case l    => l.addAppender(appender)
    override def appenders(loggerName: String): Seq[Appender] =
      loggers.get(loggerName) match
        case null => Nil
        case l    => l.appenders
    override def remove(name: String): Unit =
      loggers.remove(name) match
        case null =>
        case l    => l.clearAppenders()
    override private[sbt] def release(name: String): Unit =
      loggers.remove(name) match
        case null =>
        case l    =>
          val closeLater = l.detach().filterNot(LoggerContext.closesTrivially)
          if closeLater.nonEmpty then
            val closeNow = released.synchronized:
              if closed.get then closeLater.filter(a => closedAppenders.add(a))
              else
                closeLater.foreach(released.add)
                Nil
            closeNow.foreach(_.close())
    def close(): Unit =
      val toClose = released.synchronized:
        closed.set(true)
        loggers.forEach((_, l) => l.detach().foreach(released.add))
        loggers.clear()
        val all = released.asScala.toVector
        released.clear()
        all.filterNot(LoggerContext.closesTrivially).foreach(closedAppenders.add)
        all
      toClose.foreach(_.close())
  end LoggerContextImpl
  private val trivialClose = new ClassValue[java.lang.Boolean]:
    override def computeValue(c: Class[?]): java.lang.Boolean =
      val owner = c.getMethod("close").getDeclaringClass
      owner == classOf[ConsoleAppender] || owner == classOf[BufferedAppender]
  private def closesTrivially(a: Appender): Boolean = trivialClose.get(a.getClass)
  private[sbt] def apply() = new LoggerContextImpl
end LoggerContext
