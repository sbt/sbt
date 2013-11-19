/* sbt -- Simple Build Tool
 * Copyright 2008, 2009  Steven Blundy, Mark Harrah
 */

package sbt

	import testing.{Logger => TLogger, Event => TEvent, Status => TStatus}

trait TestReportListener
{
	/** called for each class or equivalent grouping */
  def startSuite(name: String) {}

	/** called for each test method or equivalent */
  def endTest(report: TestReport) {}

	/** called if there was an error during test */
  def endSuite(name: String, t: Throwable, suite: Option[SuiteReport]) {}

	/** called if test completed */
  def endSuite(name: String, report: SuiteReport) {}

	/** Used by the test framework for logging test results*/
  def contentLogger(test: TestDefinition): Option[ContentLogger] = None
}

trait TestsListener extends TestReportListener
{
	/** called once, at beginning. */
  def doInit() {}
	/** called once, at end. */
  def doComplete(finalResult: TestResult.Value) {}
}

/** Provides the overall `result` of a group of tests (a suite) and test counts for each result type. */
final class SuiteResult(
	val result: TestResult.Value,
	val passedCount: Int, val failureCount: Int, val errorCount: Int,
	val skippedCount: Int, val ignoredCount: Int, val canceledCount: Int, val pendingCount: Int)

object SuiteResult
{
	/** Computes the overall result and counts for a suite with individual test results in `events`. */
	def apply(events: Seq[TestReport]): SuiteResult =
	{
		def count(status: TStatus) = events.count(_.detail.status == status)
		val result = TestResult.overall(events.view.map(_.result))
		new SuiteResult(result, count(TStatus.Success), count(TStatus.Failure), count(TStatus.Error),
			count(TStatus.Skipped), count(TStatus.Ignored), count(TStatus.Canceled), count(TStatus.Pending))
	}
	val Error: SuiteResult = new SuiteResult(TestResult.Error, 0, 0, 0, 0, 0, 0, 0)
	val Empty: SuiteResult = new SuiteResult(TestResult.Passed, 0, 0, 0, 0, 0, 0, 0)
}

abstract class SuiteReport
{
	def result: SuiteResult

	/**
	 * @note it might happen that a class is flagged as a test suite but contains no tests, therefore
	 *       producing a suite report with no test report.
	 * @return the test reports of this suite - can be empty
	 */
	def detail: Seq[TestReport] = Nil

	def addTest(test: TestReport) : SuiteReport = {
		SuiteReport(detail :+ test)
	}

	/** Duration of the Suite in milliseconds.
		* @see [[sbt.testing.Event.duration()]]
		*/
	lazy val duration : Long = detail.view.map( _.detail.duration() ).sum
}
object SuiteReport
{
	val Error : SuiteReport = new SuiteReport {
		val result = SuiteResult.Error
	}

	val empty : SuiteReport = apply(Nil)

	def apply(events: Seq[TestReport]): SuiteReport =
		new SuiteReport {
			val result = SuiteResult(events)
			override val detail = events
		}
}

abstract class TestReport {
	def stdout : String
	def detail : TEvent
	def result : TestResult.Value
}
object TestReport {
	
	def apply(out: String, event: TEvent) : TestReport = 
		new TestReport {
			val stdout = out
			val detail = event
			val result = toTestResult(event)
		}
	
	private[sbt] def toTestResult(event: TEvent) : TestResult.Value = {
		event.status() match {
			case TStatus.Error => TestResult.Error
			case TStatus.Failure => TestResult.Failed
			case _ => TestResult.Passed
		}
	}
}

object TestLogger
{
	@deprecated("Doesn't provide for underlying resources to be released.", "0.13.1")
	def apply(logger: sbt.Logger, logTest: TestDefinition => sbt.Logger, buffered: Boolean): TestLogger =
		new TestLogger(new TestLogging(wrap(logger), tdef => contentLogger(logTest(tdef), buffered)) )

	@deprecated("Doesn't provide for underlying resources to be released.", "0.13.1")
	def contentLogger(log: sbt.Logger, buffered: Boolean): ContentLogger =
	{
		val blog = new BufferedLogger(FullLogger(log))
		if(buffered) blog.record()
		new ContentLogger(wrap(blog), () => blog.stopQuietly())
	}

	final class PerTest private[sbt](val log: sbt.Logger, val flush: () => Unit, val buffered: Boolean)

	def make(global: sbt.Logger, perTest: TestDefinition => PerTest): TestLogger =
	{
		def makePerTest(tdef: TestDefinition): ContentLogger =
		{
			val per = perTest(tdef)
			val blog = new BufferedLogger(FullLogger(per.log))
			if(per.buffered) blog.record()
			new ContentLogger(wrap(blog), () => { blog.stopQuietly(); per.flush() })
		}
		val config = new TestLogging(wrap(global), makePerTest)
		new TestLogger(config)
	}


	def wrap(logger: sbt.Logger): TLogger =
		new TLogger
		{
			def error(s: String) = log(Level.Error, s)
			def warn(s: String) = log(Level.Warn, s)
			def info(s: String) = log(Level.Info, s)
			def debug(s: String) = log(Level.Debug, s)
			def trace(t: Throwable) = logger.trace(t)
			private def log(level: Level.Value, s: String) = logger.log(level, s)
			def ansiCodesSupported() = logger.ansiCodesSupported
		}
}
final class TestLogging(val global: TLogger, val logTest: TestDefinition => ContentLogger)
final class ContentLogger(val log: TLogger, val flush: () => Unit)
class TestLogger(val logging: TestLogging) extends TestsListener
{
	import logging.{global => log, logTest}

	override def endSuite(name: String, t: Throwable, suite: Option[SuiteReport])
	{
		log.trace(t)
		log.error("Could not run test " + name + ": " + t.toString)
	}
	override def contentLogger(test: TestDefinition): Option[ContentLogger] = Some(logTest(test))
}
