/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.std

import java.io.{ PrintWriter, StringWriter }
import java.lang.ref.{ Reference, ReferenceQueue }
import java.util.concurrent.atomic.AtomicInteger
import hedgehog.Result
import hedgehog.runner._
import sbt.internal.util.{ Appender, BufferedAppender, ConsoleAppender, ConsoleOut, Util }
import sbt.io.IO
import sbt.util.{ Level, LoggerContext }
import scala.collection.JavaConverters._
import scala.concurrent.duration._

object StreamsTest extends Properties {
  override def tests: List[Test] = List(
    example("closing task streams unregisters all their loggers", closeLoggers),
    example("closing task streams preserves other streams in the context", preserveLoggers),
    example("closing task streams keeps an appender shared with open streams", sharedWithOpen),
    example("a shared appender keeps working for streams opened later", sharedWithLater),
    example("closing the logger context closes a released appender once", sharedClosedOnce),
    example("a release racing with closing the logger context closes the appender", releaseOnClose),
    example("released appenders with a no-op close are not kept", trivialNotKept),
    example("a closed logger context keeps only appenders with a real close", closedKeepsCustom),
    example("released appenders with a log4j bridge are closed with the context", bridgedClosed),
    example("appenders bridged after their release are closed with the context", bridgedLater),
    example("equal appenders bridged after their release are all closed", equalBridgedLater),
    example("appenders bridged while their release waits are closed", bridgedWhileReleasing),
    example("closing the logger context drops collected weak references", collectedDropped),
  )

  private final class Probe {
    val closes, delivered, dropped = new AtomicInteger
    private val template = ConsoleAppender("probe-template", ConsoleOut.systemOut)
    val appender: Appender =
      new ConsoleAppender("probe", template.properties, template.suppressedMessage) {
        override def close(): Unit = Util.ignoreResult(closes.incrementAndGet())
        override def appendLog(level: Level.Value, message: => String): Unit =
          Util.ignoreResult((if (closes.get > 0) dropped else delivered).incrementAndGet())
      }
    override def toString: String =
      s"closes=${closes.get} delivered=${delivered.get} dropped=${dropped.get}"
  }

  private def using[A <: AutoCloseable, B](resource: A)(f: A => B): B =
    try f(resource)
    finally resource.close()

  private def streams(dir: java.io.File, context: LoggerContext, shared: Seq[Appender]) = {
    val ids = new AtomicInteger
    Streams[String](
      (key: String) => new java.io.File(dir, key),
      (key: String) => key,
      (_: String, writer: PrintWriter) => {
        val log = context.logger(s"task-${ids.incrementAndGet()}", None, None)
        val appender = ConsoleAppender(log.name, ConsoleOut.printWriterOut(writer))
        (appender +: shared).foreach(a => context.addAppender(log.name, a -> Level.Info))
        log
      }
    )
  }

  private def withContext[A](shared: Seq[Appender])(
      f: (CloseableStreams[String], LoggerContext, java.io.File) => A
  ): A =
    IO.withTemporaryDirectory { dir =>
      using(LoggerContext(useLog4J = false)) { context =>
        using(Streams.closeable(streams(dir, context, shared))) { managed =>
          f(managed, context, dir)
        }
      }
    }

  private def withStreams(shared: Appender*)(
      f: (CloseableStreams[String], LoggerContext, java.io.File) => Result
  ): Result = withContext(shared)(f)

  private def field[A](context: LoggerContext, name: String): A =
    context.getClass.getDeclaredFields
      .find(_.getName.split("\\$").last == name)
      .map { field =>
        field.setAccessible(true)
        field.get(context).asInstanceOf[A]
      }
      .getOrElse(sys.error(s"$name not found"))

  private def identitySet(context: LoggerContext, name: String): java.util.Set[_] =
    field[java.util.Set[_]](context, name)

  private def releasedSet(context: LoggerContext): java.util.Set[_] =
    identitySet(context, "released")

  private def released(context: LoggerContext): Set[Any] =
    releasedSet(context).asScala.toSet[Any]

  private def unregistered(context: LoggerContext, name: String, probe: Appender): Boolean = {
    val cleared = context.appenders(name).isEmpty
    context.addAppender(name, probe -> Level.Info)
    cleared && context.appenders(name).isEmpty
  }

  private def closeLoggers: Result = withStreams() { (streams, context, _) =>
    val closed = (1 to 10).flatMap { _ =>
      val stream = streams("compile")
      val log = stream.log
      val other = stream.log("other")
      val appenders = List(log, other).map(log => log.name -> context.appenders(log.name).head)
      log.info("before closing")
      stream.close()
      stream.close()
      appenders.map { case (name, appender) => unregistered(context, name, appender) }
    }
    Result.assert(closed.forall(identity))
  }

  private def preserveLoggers: Result = withStreams() { (streams, context, dir) =>
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
  }

  private def sharedWithOpen: Result = {
    val probe = new Probe
    withStreams(probe.appender) { (streams, _, _) =>
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
    }
  }

  private def sharedWithLater: Result = {
    val probe = new Probe
    withStreams(probe.appender) { (streams, _, _) =>
      val first = streams("first")
      first.log.info("first")
      first.close()
      val later = streams("later")
      later.log.info("later")
      later.close()
      Result
        .assert(probe.closes.get == 0 && probe.delivered.get == 2 && probe.dropped.get == 0)
        .log(probe.toString)
    }
  }

  private def closesWithContext(open: Int): (Int, Int) = {
    val probe = new Probe
    withContext(Seq(probe.appender)) { (managed, context, _) =>
      val first = managed("first")
      first.log.info("first")
      (1 to open).foreach(i => managed(s"open-$i").log.info("open"))
      first.close()
      val beforeClose = probe.closes.get
      context.close()
      beforeClose -> probe.closes.get
    }
  }

  private def sharedClosedOnce: Result = {
    val closes = List(0, 1, 2).map(closesWithContext)
    Result.assert(closes.forall(_ == ((0, 1)))).log(closes.toString)
  }

  private def whileReleaseWaits[A](context: LoggerContext, release: () => Unit)(
      body: => A
  ): (Boolean, A) = {
    val releasing = new Thread(() => release())
    releasing.setDaemon(true)
    val (blocked, result) = releasedSet(context).synchronized {
      releasing.start()
      val deadline = 10.seconds.fromNow
      def blocking = releasing.getState == Thread.State.BLOCKED
      while (!blocking && deadline.hasTimeLeft()) Thread.sleep(10)
      (blocking, body)
    }
    releasing.join(10.seconds.toMillis)
    (blocked && !releasing.isAlive, result)
  }

  private def racingRelease(otherOpen: Boolean): (Boolean, Int, Set[Any]) = {
    val probe = new Probe
    withContext(Seq(probe.appender)) { (managed, context, _) =>
      val stream = managed("racing")
      stream.log.info("racing")
      if (otherOpen) managed("other").log.info("other")
      val (finished, _) = whileReleaseWaits(context, () => stream.close())(context.close())
      (finished, probe.closes.get, released(context))
    }
  }

  private def releaseOnClose: Result = {
    val outcomes = List(false, true).map(racingRelease)
    Result.assert(outcomes.forall(_ == ((true, 1, Set.empty[Any])))).log(outcomes.toString)
  }

  private def bridgedClosed: Result = {
    def out = ConsoleOut.printWriterOut(new PrintWriter(new StringWriter))
    val console = ConsoleAppender("bridged-console", out)
    val buffered = new BufferedAppender("bridged-buffered", ConsoleAppender("delegate", out))
    val bridges = List(console.toLog4J, buffered.toLog4J)
    withContext(Seq(console, buffered)) { (managed, context, _) =>
      val stream = managed("compile")
      stream.log.info("compile")
      stream.close()
      val kept = released(context)
      context.close()
      val stopped = bridges.map(_.isStopped)
      Result
        .assert(kept == Set[Any](console, buffered) && stopped == List(true, true))
        .log(s"kept=$kept stopped=$stopped")
    }
  }

  private def bridgedLater: Result = {
    def out = ConsoleOut.printWriterOut(new PrintWriter(new StringWriter))
    val console = ConsoleAppender("late-console", out)
    val buffered = new BufferedAppender("late-buffered", ConsoleAppender("delegate", out))
    withContext(Seq(console, buffered)) { (managed, context, _) =>
      val stream = managed("compile")
      stream.log.info("compile")
      stream.close()
      val kept = released(context)
      val bridges = List(console.toLog4J, buffered.toLog4J)
      context.close()
      val stopped = bridges.map(_.isStopped)
      Result
        .assert(kept.isEmpty && stopped == List(true, true))
        .log(s"kept=$kept stopped=$stopped")
    }
  }

  private def equalBridgedLater: Result = {
    val template =
      ConsoleAppender("equal", ConsoleOut.printWriterOut(new PrintWriter(new StringWriter)))
    def console(name: String): Appender =
      new ConsoleAppender(name, template.properties, template.suppressedMessage) {
        override def equals(other: Any): Boolean = other.isInstanceOf[ConsoleAppender]
        override def hashCode: Int = 1
      }
    def buffered(name: String): Appender =
      new BufferedAppender(name, template) {
        override def equals(other: Any): Boolean = other.isInstanceOf[BufferedAppender]
        override def hashCode: Int = 2
      }
    val appenders = List(
      console("console-1"),
      console("console-2"),
      buffered("buffered-1"),
      buffered("buffered-2")
    )
    withContext(appenders) { (managed, context, _) =>
      val stream = managed("compile")
      stream.log.info("compile")
      stream.close()
      val bridges = appenders.map(_.toLog4J)
      context.close()
      val stopped = bridges.map(_.isStopped)
      Result.assert(stopped == List(true, true, true, true)).log(s"stopped=$stopped")
    }
  }

  private def bridgedWhileReleasing: Result = {
    def out = ConsoleOut.printWriterOut(new PrintWriter(new StringWriter))
    val console = ConsoleAppender("handoff-console", out)
    val buffered = new BufferedAppender("handoff-buffered", ConsoleAppender("delegate", out))
    withContext(Seq(console, buffered)) { (managed, context, _) =>
      val stream = managed("compile")
      stream.log.info("compile")
      val (finished, bridges) = whileReleaseWaits(context, () => stream.close()) {
        val bridges = List(console.toLog4J, buffered.toLog4J)
        context.close()
        bridges
      }
      val stopped = bridges.map(_.isStopped)
      Result
        .assert(finished && stopped == List(true, true))
        .log(s"finished=$finished stopped=$stopped")
    }
  }

  private def collectedDropped: Result = withStreams() { (streams, context, _) =>
    val stream = streams("compile")
    stream.log.info("compile")
    stream.close()
    val tracked = identitySet(context, "bridgeable").asScala.toList.collect {
      case r: Reference[_] => r
    }
    tracked.foreach { r =>
      r.clear()
      Util.ignoreResult(r.enqueue())
    }
    context.close()
    val collected = field[ReferenceQueue[_]](context, "collected")
    val left = Iterator.continually(collected.poll()).takeWhile(_ != null).size
    Result.assert(tracked.size == 1 && left == 0).log(s"tracked=${tracked.size} left=$left")
  }

  private def trivialNotKept: Result = {
    val probe = new Probe
    val template =
      ConsoleAppender("plain", ConsoleOut.printWriterOut(new PrintWriter(new StringWriter)))
    val plain = new ConsoleAppender("plain", template.properties, template.suppressedMessage) {
      override def toString: String = "plain"
    }
    withStreams(probe.appender, plain) { (streams, context, _) =>
      val stream = streams("compile")
      stream.log.info("compile")
      stream.log("other").info("other")
      stream.close()
      val kept = released(context)
      Result.assert(kept == Set[Any](probe.appender)).log(kept.toString)
    }
  }

  private def closedKeepsCustom: Result = {
    val probe = new Probe
    withContext(Seq(probe.appender)) { (managed, context, _) =>
      val released = managed("released")
      released.log.info("released")
      managed("open").log.info("open")
      released.close()
      context.close()
      val kept = identitySet(context, "closedAppenders").asScala.toSet[Any]
      Result
        .assert(kept == Set[Any](probe.appender) && probe.closes.get == 1)
        .log(s"kept=$kept $probe")
    }
  }
}
