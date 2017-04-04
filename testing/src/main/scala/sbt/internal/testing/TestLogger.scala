package sbt
package internal.testing

import testing.{ Logger => TLogger }
import sbt.internal.util.{ ManagedLogger, BufferedAppender }
import sbt.util.{ Level, LogExchange }
import sbt.protocol.testing._
import java.util.concurrent.atomic.AtomicInteger
import scala.collection.JavaConverters._

object TestLogger {
  import sbt.protocol.testing.codec.JsonProtocol._

  private def generateName: String =
    "test-" + generateId.incrementAndGet
  private val generateId: AtomicInteger = new AtomicInteger
  private def generateBufferName: String =
    "testbuffer-" + generateBufferId.incrementAndGet
  private val generateBufferId: AtomicInteger = new AtomicInteger

  final class PerTest private[sbt] (val log: ManagedLogger, val flush: () => Unit, val buffered: Boolean)

  def make(global: ManagedLogger, perTest: TestDefinition => PerTest): TestLogger =
    {
      def makePerTest(tdef: TestDefinition): ContentLogger =
        {
          val per = perTest(tdef)
          val l0 = per.log
          val config = LogExchange.loggerConfig(l0.name)
          val as = config.getAppenders.asScala
          val buffs: List[BufferedAppender] = (as map {
            case (k, v) => BufferedAppender(generateBufferName, v)
          }).toList
          val newLog = LogExchange.logger(generateName, l0.channelName, l0.execId)
          LogExchange.bindLoggerAppenders(newLog.name, buffs map { x => (x, Level.Debug) })
          if (per.buffered) {
            buffs foreach { _.record() }
          }
          new ContentLogger(wrap(newLog), () => {
            buffs foreach { _.stopQuietly() }
            per.flush()
          })
        }
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

  def startGroup(name: String): Unit =
    managed.logEvent(Level.Info, StartTestGroupEvent(name))
  def testEvent(event: TestEvent): Unit =
    managed.logEvent(Level.Info, toTestItemEvent(event))
  def endGroup(name: String, t: Throwable): Unit = {
    log.trace(t)
    log.error("Could not run test " + name + ": " + t.toString)
    managed.logEvent(Level.Info, EndTestGroupErrorEvent(
      name,
      t.getMessage + "\n" + t.getStackTrace.toList.mkString("\n")
    ))
  }
  def endGroup(name: String, result: TestResult): Unit =
    managed.logEvent(Level.Info, EndTestGroupEvent(name, result))
  def doInit: Unit = managed.logEvent(Level.Info, TestInitEvent())
  /** called once, at end of test group. */
  def doComplete(finalResult: TestResult): Unit =
    managed.logEvent(Level.Info, TestCompleteEvent(finalResult))
  override def contentLogger(test: TestDefinition): Option[ContentLogger] = Some(logTest(test))
}
