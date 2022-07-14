/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.util

import org.apache.logging.log4j.core.config.LoggerConfig
import org.apache.logging.log4j.core.layout.PatternLayout
import org.apache.logging.log4j.core.{ LoggerContext => XLoggerContext }
import org.apache.logging.log4j.{ LogManager => XLogManager }
import sbt.internal.util._

import java.util.concurrent.ConcurrentHashMap
import scala.collection.concurrent

// http://logging.apache.org/log4j/2.x/manual/customconfig.html
// https://logging.apache.org/log4j/2.x/log4j-core/apidocs/index.html

sealed abstract class LogExchange {
  private[sbt] lazy val context: XLoggerContext = init()
  private[sbt] val stringCodecs: concurrent.Map[String, ShowLines[_]] = concurrent.TrieMap()
  private[sbt] val builtInStringCodecs: Unit = initStringCodecs()
  private[util] val configs = new ConcurrentHashMap[String, LoggerConfig]
  private[util] def addConfig(name: String, config: LoggerConfig): Unit =
    Util.ignoreResult(configs.putIfAbsent(name, config))
  private[util] def removeConfig(name: String): Option[LoggerConfig] = Option(configs.remove(name))

  def logger(name: String): ManagedLogger = logger(name, None, None)
  def logger(name: String, channelName: Option[String], execId: Option[String]): ManagedLogger =
    LoggerContext.globalContext.logger(name, channelName, execId)
  def unbindLoggerAppenders(loggerName: String): Unit = {
    LoggerContext.globalContext.clearAppenders(loggerName)
  }
  def bindLoggerAppenders(
      loggerName: String,
      appenders: Seq[(Appender, Level.Value)]
  ): Unit = {
    appenders.map(LoggerContext.globalContext.addAppender(loggerName, _))
    ()
  }

  // Construct these StringTypeTags manually, because they're used at the very startup of sbt
  // and we'll try not to initialize the universe by using the StringTypeTag.apply that requires a TypeTag
  // A better long-term solution could be to make StringTypeTag.apply a macro.
  lazy val stringTypeTagThrowable = StringTypeTag[Throwable]("scala.Throwable")
  lazy val stringTypeTagTraceEvent = StringTypeTag[TraceEvent]("sbt.internal.util.TraceEvent")
  lazy val stringTypeTagSuccessEvent = StringTypeTag[SuccessEvent]("sbt.internal.util.SuccessEvent")

  private[sbt] def initStringCodecs(): Unit = {
    import sbt.internal.util.codec.SuccessEventShowLines._
    import sbt.internal.util.codec.ThrowableShowLines._
    import sbt.internal.util.codec.TraceEventShowLines._

    registerStringCodecByStringTypeTag(stringTypeTagThrowable)
    registerStringCodecByStringTypeTag(stringTypeTagTraceEvent)
    registerStringCodecByStringTypeTag(stringTypeTagSuccessEvent)
  }

  // This is a dummy layout to avoid casting error during PatternLayout.createDefaultLayout()
  // that was originally used for ConsoleAppender.
  // The stacktrace shows it's having issue initializing default DefaultConfiguration.
  // Since we currently do not use Layout inside ConsoleAppender, the actual pattern is not relevant.
  private[sbt] lazy val dummyLayout: PatternLayout = {
    val _ = context
    val ctx = XLogManager.getContext(false) match { case x: XLoggerContext => x }
    val config = ctx.getConfiguration
    val lo = PatternLayout.newBuilder
      .withConfiguration(config)
      .withPattern(PatternLayout.SIMPLE_CONVERSION_PATTERN)
      .build
    lo
  }

  def stringCodec[A](tag: String): Option[ShowLines[A]] =
    stringCodecs.get(tag) map { _.asInstanceOf[ShowLines[A]] }
  def hasStringCodec(tag: String): Boolean =
    stringCodecs.contains(tag)
  def getOrElseUpdateStringCodec[A](tag: String, v: ShowLines[A]): ShowLines[A] =
    stringCodecs.getOrElseUpdate(tag, v).asInstanceOf[ShowLines[A]]

  private[sbt] def registerStringCodec[A: ShowLines: StringTypeTag]: Unit = {
    registerStringCodecByStringTypeTag(implicitly[StringTypeTag[A]])
  }

  private[sbt] def registerStringCodecByStringTypeTag[A: ShowLines](tag: StringTypeTag[A]): Unit = {
    val ev = implicitly[ShowLines[A]]
    val _ = getOrElseUpdateStringCodec(tag.key, ev)
  }

  private[sbt] def init(): XLoggerContext = {
    import org.apache.logging.log4j.core.config.Configurator
    import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilderFactory
    val builder = ConfigurationBuilderFactory.newConfigurationBuilder
    builder.setConfigurationName("sbt.util.logging")
    val ctx = Configurator.initialize(builder.build())
    ctx match { case x: XLoggerContext => x }
  }
  private[sbt] def init(name: String): XLoggerContext = new XLoggerContext(name)
}
object LogExchange extends LogExchange
