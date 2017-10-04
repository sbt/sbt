/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt
package internal.testing

import testing.{ Logger => TLogger }
import sbt.internal.util.{ ManagedLogger, BufferedAppender }
import sbt.util.{ Level, LogExchange, ShowLines }
import sbt.protocol.testing._
import java.util.concurrent.atomic.AtomicInteger
import scala.collection.JavaConverters._

object TestLogger {
  import sbt.protocol.testing.codec.JsonProtocol._

  implicit val testStringEventShowLines: ShowLines[TestStringEvent] =
    ShowLines[TestStringEvent]({
      case a: TestStringEvent => List(a.value)
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

  def make(global: ManagedLogger, perTest: TestDefinition => PerTest): TestLogger = {
    def makePerTest(tdef: TestDefinition): ContentLogger = {
      val per = perTest(tdef)
      val l0 = per.log
      val config = LogExchange.loggerConfig(l0.name)
      val as = config.getAppenders.asScala
      val buffs: List[BufferedAppender] = (as map {
        case (_, v) => BufferedAppender(generateBufferName, v)
      }).toList
      val newLog = LogExchange.logger(generateName, l0.channelName, l0.execId)
      LogExchange.unbindLoggerAppenders(newLog.name)
      LogExchange.bindLoggerAppenders(newLog.name, buffs map { x =>
        (x, Level.Debug)
      })
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
    implicit val showNoLinesTestInitEvent = showNoLines[TestInitEvent]
    implicit val showNoLinesStartTestGroupEvent = showNoLines[StartTestGroupEvent]
    implicit val showNoLinesTestItemEvent = showNoLines[TestItemEvent]
    implicit val showNoLinesEndTestGroupEvent = showNoLines[EndTestGroupEvent]
    implicit val showNoLinesTestCompleteEvent = showNoLines[TestCompleteEvent]
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
      def ansiCodesSupported() = logger.ansiCodesSupported
    }

  private[sbt] def toTestItemEvent(event: TestEvent): TestItemEvent =
    TestItemEvent(event.result, event.detail.toVector map { d =>
      TestItemDetail(
        d.fullyQualifiedName,
        d.status,
        d.duration match {
          case -1 => None
          case x  => Some(x)
        }
      )
    })
}
final class TestLogging(
    val global: TLogger,
    val managed: ManagedLogger,
    val logTest: TestDefinition => ContentLogger
)

class TestLogger(val logging: TestLogging) extends TestsListener {
  import TestLogger._
  import logging.{ global => log, logTest, managed }
  import sbt.protocol.testing.codec.JsonProtocol._

  def doInit: Unit = managed.logEvent(Level.Info, TestInitEvent())

  def startGroup(name: String): Unit = managed.logEvent(Level.Info, StartTestGroupEvent(name))

  def testEvent(event: TestEvent): Unit = managed.logEvent(Level.Info, toTestItemEvent(event))

  def endGroup(name: String, result: TestResult): Unit =
    managed.logEvent(Level.Info, EndTestGroupEvent(name, result))

  def endGroup(name: String, t: Throwable): Unit = {
    log.trace(t)
    log.error(s"Could not run test $name: $t")
    managed.logEvent(
      Level.Info,
      EndTestGroupErrorEvent(name, (t.getMessage +: t.getStackTrace).mkString("\n"))
    )
  }

  def doComplete(finalResult: TestResult): Unit =
    managed.logEvent(Level.Info, TestCompleteEvent(finalResult))

  override def contentLogger(test: TestDefinition): Option[ContentLogger] = Some(logTest(test))
}
