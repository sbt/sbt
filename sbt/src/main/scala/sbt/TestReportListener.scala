/* sbt -- Simple Build Tool
 * Copyright 2008, 2009  Steven Blundy, Mark Harrah
 */

package sbt

	import org.scalatools.testing.{Logger => TLogger, Event => TEvent, Result => TResult}

trait TestReportListener
{
	/** called for each class or equivalent grouping */
  def startGroup(name: String)
	/** called for each test method or equivalent */
  def testEvent(event: TestEvent)
	/** called if there was an error during test */
  def endGroup(name: String, t: Throwable)
	/** called if test completed */
  def endGroup(name: String, result: Result.Value)
  /** Used by the test framework for logging test results*/
  def contentLogger: Option[TLogger] = None
}

trait TestsListener extends TestReportListener
{
	/** called once, at beginning. */
  def doInit
	/** called once, at end. */
  def doComplete(finalResult: Result.Value)
}

abstract class TestEvent extends NotNull
{
	def result: Option[Result.Value]
	def detail: Seq[TEvent] = Nil
}
object TestEvent
{
	def apply(events: Seq[TEvent]): TestEvent =
	{
		val overallResult = (Result.Passed /: events) { (sum, event) =>
			val result = event.result
			if(sum == Result.Error || result == TResult.Error) Result.Error
			else if(sum == Result.Failed || result == TResult.Failure) Result.Failed
			else Result.Passed
		}
		new TestEvent {
			val result = Some(overallResult)
			override val detail = events
		}
	}
}

object TestLogger
{
	def apply(logger: sbt.Logger): TestLogger = new TestLogger(wrap(logger))
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
class TestLogger(val log: TLogger) extends TestsListener
{
	protected var skipped, errors, passed, failures = 0
	
	def startGroup(name: String) {}
	def testEvent(event: TestEvent): Unit = event.detail.foreach(count)
	def endGroup(name: String, t: Throwable)
	{
		log.trace(t)
		log.error("Could not run test " + name + ": " + t.toString)
	}
	def endGroup(name: String, result: Result.Value) {}
	protected def count(event: TEvent): Unit =
	{
		event.result match
		{
			case TResult.Error => errors +=1
			case TResult.Success => passed +=1 
			case TResult.Failure => failures +=1 
			case TResult.Skipped => skipped += 1
		}
	}
	def doInit
	{
		failures = 0
		errors = 0
		passed = 0
		skipped = 0
	}
		/** called once, at end. */
	def doComplete(finalResult: Result.Value): Unit =
	{
		val totalCount = failures + errors + skipped + passed
		val postfix = ": Total " + totalCount + ", Failed " + failures + ", Errors " + errors + ", Passed " + passed + ", Skipped " + skipped
		finalResult match
		{
			case Result.Error => log.error("Error" + postfix)
			case Result.Passed => log.info("Passed: " + postfix)
			case Result.Failed => log.error("Failed: " + postfix)
		}
	}
	override def contentLogger: Option[TLogger] = Some(log)
}
