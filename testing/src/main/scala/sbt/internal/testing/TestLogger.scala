/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal.testing

import testing.{ Logger => TLogger }
import sbt.internal.util.{ BufferedAppender, ManagedLogger, Terminal }
import sbt.util.{ Level, ShowLines }
import sbt.protocol.testing._
import java.util.concurrent.atomic.AtomicInteger

object TestLogger {
  import sbt.protocol.testing.codec.JsonProtocol._

  given testStringEventShowLines: ShowLines[TestStringEvent] =
    ShowLines[TestStringEvent]({ case a: TestStringEvent =>
      List(a.value)
    })

  private def generateName: String = "test-" + generateId.incrementAndGet
  private val generateId: AtomicInteger = new AtomicInteger

  private def generateBufferName: String = "testbuffer-" + generateBufferId.incrementAndGet
  private val generateBufferId: AtomicInteger = new AtomicInteger

  final class PerTest private[sbt] (
      val log: ManagedLogger,
      val flush: () => Unit,
      val buffered: Boolean
  )

  @deprecated("Use make variant that accepts a log level.", "1.4.0")
  def make(
      global: ManagedLogger,
      perTest: TestDefinition => PerTest,
  ): TestLogger = make(global, perTest, Level.Debug)

  def make(
      global: ManagedLogger,
      perTest: TestDefinition => PerTest,
      level: Level.Value
  ): TestLogger = {
    val context = global.context
    val as = context.appenders(global.name)
    def makePerTest(tdef: TestDefinition): ContentLogger = {
      val per = perTest(tdef)
      val l0 = per.log
      val buffs = as.map(a => BufferedAppender(generateBufferName, a)).toList
      val newLog = context.logger(generateName, l0.channelName, l0.execId)
      context.clearAppenders(newLog.name)
      buffs.foreach { b =>
        context.addAppender(newLog.name, b -> level)
      }
      if (per.buffered) {
        buffs foreach { _.record() }
      }
      new ContentLogger(
        wrap(newLog),
        () => {
          buffs foreach { _.stopQuietly() }
          per.flush()
          // do not unbind here. since there's a delay in the async appender,
          // it will result in missing log output.
        }
      )
    }

    global.registerStringCodec[TestStringEvent]

    def showNoLines[A] = ShowLines[A](_ => Nil)
    given ShowLines[TestInitEvent] = showNoLines[TestInitEvent]
    given ShowLines[StartTestGroupEvent] = showNoLines[StartTestGroupEvent]
    given ShowLines[TestItemEvent] = showNoLines[TestItemEvent]
    given ShowLines[EndTestGroupEvent] = showNoLines[EndTestGroupEvent]
    given ShowLines[TestCompleteEvent] = showNoLines[TestCompleteEvent]
    global.registerStringCodec[TestInitEvent]
    global.registerStringCodec[StartTestGroupEvent]
    global.registerStringCodec[TestItemEvent]
    global.registerStringCodec[EndTestGroupEvent]
    global.registerStringCodec[TestCompleteEvent]

    val config = new TestLogging(wrap(global), global, makePerTest)
    new TestLogger(config)
  }

  def wrap(logger: ManagedLogger): TLogger =
    new TLogger {
      def error(s: String) = log(Level.Error, TestStringEvent(s))
      def warn(s: String) = log(Level.Warn, TestStringEvent(s))
      def info(s: String) = log(Level.Info, TestStringEvent(s))
      def debug(s: String) = log(Level.Debug, TestStringEvent(s))
      def trace(t: Throwable) = logger.trace(t)
      private def log(level: Level.Value, event: TestStringEvent) = logger.logEvent(level, event)
      def ansiCodesSupported() = Terminal.isAnsiSupported
    }

  private[sbt] def toTestItemEvent(event: TestEvent): TestItemEvent =
    TestItemEvent(
      event.result,
      event.detail.toVector map { d =>
        TestItemDetail(
          d.fullyQualifiedName,
          d.status,
          d.duration match {
            case -1 => (None: Option[Long]) // util.Util is not in classpath
            case x  => (Some(x): Option[Long])
          }
        )
      }
    )
}
final class TestLogging(
    val global: TLogger,
    val managed: ManagedLogger,
    val logTest: TestDefinition => ContentLogger
)

class TestLogger(val logging: TestLogging) extends TestsListener {
  import TestLogger._
  import logging.{ global, logTest, managed }
  import sbt.protocol.testing.codec.JsonProtocol._

  def doInit(): Unit = managed.logEvent(Level.Info, TestInitEvent())

  def startGroup(name: String): Unit = managed.logEvent(Level.Info, StartTestGroupEvent(name))

  def testEvent(event: TestEvent): Unit = managed.logEvent(Level.Info, toTestItemEvent(event))

  def endGroup(name: String, result: TestResult): Unit =
    managed.logEvent(Level.Info, EndTestGroupEvent(name, result))

  def endGroup(name: String, t: Throwable): Unit = {
    global.trace(t)
    global.error(s"Could not run test $name: $t")
    managed.logEvent(
      Level.Info,
      EndTestGroupErrorEvent(name, (t.getMessage + t.getStackTrace.toString).mkString("\n"))
    )
    ()
  }

  def doComplete(finalResult: TestResult): Unit =
    managed.logEvent(Level.Info, TestCompleteEvent(finalResult))

  override def contentLogger(test: TestDefinition): Option[ContentLogger] = Some(logTest(test))
}
