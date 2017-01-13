package sbt.util

import sbt.internal.util._
import org.apache.logging.log4j.{ LogManager => XLogManager, Level => XLevel }
import org.apache.logging.log4j.core._
import org.apache.logging.log4j.core.appender.AsyncAppender
import org.apache.logging.log4j.core.config.{ AppenderRef, LoggerConfig }
import scala.collection.JavaConverters._

// http://logging.apache.org/log4j/2.x/manual/customconfig.html
// https://logging.apache.org/log4j/2.x/log4j-core/apidocs/index.html

sealed abstract class LogExchange {
  private[sbt] lazy val context: LoggerContext = init()
  private[sbt] lazy val asyncStdout: AsyncAppender = buildAsyncStdout

  def logger(name: String): ManagedLogger = logger(name, None, None)
  def logger(name: String, channelName: Option[String], execId: Option[String]): ManagedLogger = {
    val _ = context
    val ctx = XLogManager.getContext(false) match { case x: LoggerContext => x }
    val config = ctx.getConfiguration
    val loggerConfig = LoggerConfig.createLogger(false, XLevel.DEBUG, name,
      "true", Array[AppenderRef](), null, config, null)
    config.addLogger(name, loggerConfig)
    ctx.updateLoggers
    val logger = ctx.getLogger(name)
    new ManagedLogger(name, channelName, execId, logger)
  }
  def unbindLoggerAppenders(loggerName: String): Unit = {
    val lc = loggerConfig(loggerName)
    lc.getAppenders.asScala foreach {
      case (k, v) => lc.removeAppender(k)
    }
  }
  def bindLoggerAppenders(loggerName: String, appenders: List[(Appender, Level.Value)]): Unit = {
    val lc = loggerConfig(loggerName)
    appenders foreach {
      case (x, lv) => lc.addAppender(x, ConsoleAppender.toXLevel(lv), null)
    }
  }
  def loggerConfig(loggerName: String): LoggerConfig = {
    val ctx = XLogManager.getContext(false) match { case x: LoggerContext => x }
    val config = ctx.getConfiguration
    config.getLoggerConfig(loggerName)
  }
  private[sbt] def buildAsyncStdout: AsyncAppender = {
    val ctx = XLogManager.getContext(false) match { case x: LoggerContext => x }
    val config = ctx.getConfiguration
    // val layout = PatternLayout.newBuilder
    //   .withPattern(PatternLayout.SIMPLE_CONVERSION_PATTERN)
    //   .build
    val appender = ConsoleAppender("Stdout")
    // CustomConsoleAppenderImpl.createAppender("Stdout", layout, null, null)
    appender.start
    config.addAppender(appender)
    val asyncAppender: AsyncAppender = (AsyncAppender.newBuilder(): AsyncAppender.Builder)
      .setName("AsyncStdout")
      .setAppenderRefs(Array(AppenderRef.createAppenderRef("Stdout", XLevel.DEBUG, null)))
      .setBlocking(false)
      .setConfiguration(config)
      .build
    asyncAppender.start
    config.addAppender(asyncAppender)
    asyncAppender
  }
  private[sbt] def init(): LoggerContext = {
    import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilderFactory
    import org.apache.logging.log4j.core.config.Configurator
    val builder = ConfigurationBuilderFactory.newConfigurationBuilder
    builder.setConfigurationName("sbt.util.logging")
    val ctx = Configurator.initialize(builder.build())
    ctx match { case x: LoggerContext => x }
  }
}
object LogExchange extends LogExchange
