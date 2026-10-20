/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.std

import java.io.{ PrintWriter, Writer }
import java.util.concurrent.atomic.AtomicInteger
import hedgehog.Result
import hedgehog.runner.*
import sbt.internal.util.{ Appender, ConsoleAppender, ConsoleOut, Util }
import sbt.io.IO
import sbt.util.{ Level, LoggerContext, LoggerContextAccess }
import scala.concurrent.duration.*
import scala.util.Using

object StreamsTest extends Properties:
  override def tests: List[Test] = List(
    example("closing task streams unregisters all their loggers", closeLoggers),
    example("closing task streams preserves other streams in the context", preserveLoggers),
    example("closing task streams keeps an appender shared with open streams", sharedWithOpen),
    example("a shared appender keeps working for streams opened later", sharedWithLater),
    example("closing the logger context closes a released appender once", sharedClosedOnce),
    example("a release racing with closing the logger context closes the appender", releaseOnClose),
    example("released appenders with a no-op close are not kept", trivialNotKept),
    example("a closed logger context keeps only appenders with a real close", closedKeepsCustom),
  )

  private final class Probe:
    val closes, delivered, dropped = new AtomicInteger
    private val template = ConsoleAppender("probe-template", ConsoleOut.systemOut)
    val appender: Appender =
      new ConsoleAppender("probe", template.properties, template.suppressedMessage):
        override def close(): Unit = Util.ignoreResult(closes.incrementAndGet())
        override def appendLog(level: Level.Value, message: => String): Unit =
          Util.ignoreResult((if closes.get > 0 then dropped else delivered).incrementAndGet())
    override def toString: String =
      s"closes=${closes.get} delivered=${delivered.get} dropped=${dropped.get}"
  end Probe

  private def streams(dir: java.io.File, context: LoggerContext, shared: Seq[Appender]) =
    val ids = new AtomicInteger
    Streams[String](
      key => new java.io.File(dir, key),
      identity,
      (_, writer) =>
        val log = context.logger(s"task-${ids.incrementAndGet()}", None, None)
        val appender = ConsoleAppender(log.name, ConsoleOut.printWriterOut(writer))
        (appender +: shared).foreach(a => context.addAppender(log.name, a -> Level.Info))
        log
    )

  private def withStreams(shared: Appender*)(
      f: (CloseableStreams[String], LoggerContext, java.io.File) => Result
  ): Result =
    IO.withTemporaryDirectory: dir =>
      Using.resource(LoggerContext()): context =>
        Using.resource(Streams.closeable(streams(dir, context, shared))): managed =>
          f(managed, context, dir)

  private def released(context: LoggerContext): Set[Any] =
    LoggerContextAccess.released(context).toSet[Any]

  private def unregistered(context: LoggerContext, name: String, probe: Appender): Boolean =
    val cleared = context.appenders(name).isEmpty
    context.addAppender(name, probe -> Level.Info)
    cleared && context.appenders(name).isEmpty

  private def closeLoggers: Result = withStreams(): (streams, context, _) =>
    val closed = (1 to 10).flatMap: _ =>
      val stream = streams("compile")
      val log = stream.log
      val other = stream.log("other")
      val appenders = List(log, other).map(log => log.name -> context.appenders(log.name).head)
      log.info("before closing")
      stream.close()
      stream.close()
      appenders.map(unregistered(context, _, _))
    Result.assert(closed.forall(identity))

  private def preserveLoggers: Result = withStreams(): (streams, context, dir) =>
    val first = streams("first")
    val other = streams("second")
    val firstLog = first.log
    val otherLog = other.log
    val appender = context.appenders(firstLog.name).head
    first.close()
    otherLog.info("still open")
    Result.assert(
      unregistered(context, firstLog.name, appender) &&
        context.appenders(otherLog.name).nonEmpty &&
        IO.read(new java.io.File(dir, "second/out")).contains("still open")
    )

  private def sharedWithOpen: Result =
    val probe = new Probe
    withStreams(probe.appender): (streams, _, _) =>
      val first = streams("first")
      val other = streams("second")
      val firstLog = first.log
      val otherLog = other.log
      first.close()
      firstLog.info("after close")
      otherLog.info("still open")
      Result
        .assert(probe.closes.get == 0 && probe.delivered.get == 1 && probe.dropped.get == 0)
        .log(probe.toString)

  private def sharedWithLater: Result =
    val probe = new Probe
    withStreams(probe.appender): (streams, _, _) =>
      val first = streams("first")
      first.log.info("first")
      first.close()
      val later = streams("later")
      later.log.info("later")
      later.close()
      Result
        .assert(probe.closes.get == 0 && probe.delivered.get == 2 && probe.dropped.get == 0)
        .log(probe.toString)

  private def closesWithContext(open: Int): (Int, Int) =
    val probe = new Probe
    IO.withTemporaryDirectory: dir =>
      Using.resource(LoggerContext()): context =>
        Using.resource(Streams.closeable(streams(dir, context, Seq(probe.appender)))): managed =>
          val first = managed("first")
          first.log.info("first")
          (1 to open).foreach(i => managed(s"open-$i").log.info("open"))
          first.close()
          val beforeClose = probe.closes.get
          context.close()
          beforeClose -> probe.closes.get

  private def sharedClosedOnce: Result =
    val closes = List(0, 1, 2).map(closesWithContext)
    Result.assert(closes.forall(_ == (0, 1))).log(closes.toString)

  private def racingRelease(otherOpen: Boolean): (Boolean, Int, Set[Any]) =
    val probe = new Probe
    IO.withTemporaryDirectory: dir =>
      Using.resource(LoggerContext()): context =>
        Using.resource(Streams.closeable(streams(dir, context, Seq(probe.appender)))): managed =>
          val stream = managed("racing")
          stream.log.info("racing")
          if otherOpen then managed("other").log.info("other")
          val releasing = new Thread(() => stream.close())
          releasing.setDaemon(true)
          val blocked = LoggerContextAccess.holdingReleaseLock(context):
            releasing.start()
            val deadline = 10.seconds.fromNow
            while releasing.getState != Thread.State.BLOCKED && deadline.hasTimeLeft() do
              Thread.sleep(10)
            val blocked = releasing.getState == Thread.State.BLOCKED
            context.close()
            blocked
          releasing.join(10.seconds.toMillis)
          (blocked && !releasing.isAlive, probe.closes.get, released(context))

  private def releaseOnClose: Result =
    val outcomes = List(false, true).map(racingRelease)
    Result.assert(outcomes.forall(_ == (true, 1, Set.empty[Any]))).log(outcomes.toString)

  private def trivialNotKept: Result =
    val probe = new Probe
    val template =
      ConsoleAppender("plain", ConsoleOut.printWriterOut(new PrintWriter(Writer.nullWriter)))
    val plain = new ConsoleAppender("plain", template.properties, template.suppressedMessage):
      override def toString: String = "plain"
    withStreams(probe.appender, plain): (streams, context, _) =>
      val stream = streams("compile")
      stream.log.info("compile")
      stream.log("other").info("other")
      stream.close()
      val kept = released(context)
      Result.assert(kept == Set[Any](probe.appender)).log(kept.toString)

  private def closedKeepsCustom: Result =
    val probe = new Probe
    IO.withTemporaryDirectory: dir =>
      Using.resource(LoggerContext()): context =>
        Using.resource(Streams.closeable(streams(dir, context, Seq(probe.appender)))): managed =>
          val released = managed("released")
          released.log.info("released")
          managed("open").log.info("open")
          released.close()
          context.close()
          val kept = LoggerContextAccess.closedAppenders(context).toSet[Any]
          Result
            .assert(kept == Set[Any](probe.appender) && probe.closes.get == 1)
            .log(s"kept=$kept $probe")
end StreamsTest
