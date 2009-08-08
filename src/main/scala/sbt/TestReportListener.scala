/* sbt -- Simple Build Tool
 * Copyright 2008, 2009  Steven Blundy, Mark Harrah
 */

package sbt

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
}

trait TestsListener extends TestReportListener
{
	/** called once, at beginning. */
  def doInit
	/** called once, at end. */
  def doComplete(finalResult: Result.Value)
}

abstract class WriterReportListener(val log: Logger) extends TestsListener
{
	import java.io.{IOException, PrintWriter, Writer}
	import scala.collection.mutable.{Buffer, ListBuffer}

	protected case class Summary(count: Int, failures: Int, errors: Int, skipped: Int, message: Option[String], cause: Option[Throwable]) extends NotNull
	private var out: Option[PrintWriter] = None
	private var groupCount: Int = 0
	private var groupFailures: Int = 0
	private var groupErrors: Int = 0
	private var groupSkipped: Int = 0
	private var groupMessages: Seq[String] = Nil

	protected val passedEventHandler: TestEvent => Summary = (event: TestEvent) => event match
		{
			case SpecificationReportEvent(successes, failures, errors, skipped, desc, systems, subSpecs) => Summary(successes, failures, errors, skipped, None, None)
			case IgnoredEvent(name, Some(message)) => Summary(1, 0, 0, 1, Some(message), None)
			case IgnoredEvent(name, None) => Summary(1, 0, 0, 1, None, None)
			case _ => Summary(1, 0, 0, 0, None, None)
		}
	protected val failedEventHandler: TestEvent => Summary = (event: TestEvent) => event match
		{
			case FailedEvent(name, msg) => Summary(1, 1, 0, 0, Some("! " + name + ": " + msg), None)
			case TypedErrorEvent(name, event, Some(msg), cause) => Summary(1, 1, 0, 0, Some(event + " - " + name + ": " + msg), cause)
			case TypedErrorEvent(name, event, None, cause) => Summary(1, 1, 0, 0, Some(event + " - " + name), cause)
			case ErrorEvent(msg) => Summary(1, 1, 0, 0, Some(msg), None)
			case SpecificationReportEvent(successes, failures, errors, skipped, desc, systems, subSpecs) => Summary(successes + failures + errors + skipped, failures, errors, skipped, Some(desc), None)
			case _ => {log.warn("Unrecognized failure: " + event); Summary(1, 1, 0, 0, None, None)}
		}
	protected val errorEventHandler: TestEvent => Summary = (event: TestEvent) => event match
		{
			case FailedEvent(name, msg) => Summary(1, 0, 1, 0, Some("! " + name + ": " + msg), None)
			case TypedErrorEvent(name, event, Some(msg), cause) => Summary(1, 0, 1, 0, Some(event + " - " + name + ": " + msg), cause)
			case TypedErrorEvent(name, event, None, cause) => Summary(1, 0, 1, 0, Some(event + " - " + name), cause)
			case ErrorEvent(msg) => Summary(1, 0, 1, 0, Some(msg), None)
			case SpecificationReportEvent(successes, failures, errors, skipped, desc, systems, subSpecs) => Summary(successes + failures + errors + skipped, failures, errors, skipped, Some(desc), None)
			case _ => {log.warn("Unrecognized error: " + event); Summary(1, 0, 1, 0, None, None)}
		}
	protected def open: Writer
	protected def close =
	{
		onOut(_.close())
		out = None
	}
	def doInit = Control.trapAndLog(log){ out = Some(new PrintWriter(open)) }
	def doComplete(finalResult: Result.Value) =
	{
		finalResult match
		{
			case Result.Error => println("Error during Tests")
			case Result.Passed => println("All Tests Passed")
			case Result.Failed => println("Tests Failed")
		}
		close
	}
	def doComplete(t: Throwable) =
	{
		println("Exception in Test Framework")
		onOut(t.printStackTrace(_))
		close
	}
	def startGroup(name: String) =
	{
		groupCount = 0
		groupFailures = 0
		groupErrors = 0
		groupSkipped = 0
		groupMessages = Nil
	}
	def testEvent(event: TestEvent) = event.result match
		{
			case Some(result) =>
			{
				val Summary(count, failures, errors, skipped, msg, cause) = result match
				{
					case Result.Passed => passedEventHandler(event)
					case Result.Failed => failedEventHandler(event)
					case Result.Error => errorEventHandler(event)
				}
				groupCount += count
				groupFailures += failures
				groupErrors += errors
				groupSkipped += skipped
				groupMessages ++= msg.toList
			}
			case None => {}
		}
	def endGroup(name: String, t: Throwable) =
	{
		groupMessages = Nil
		println("Exception in " + name)
		onOut(t.printStackTrace(_))
	}
	def endGroup(name: String, result: Result.Value) =
	{
		result match
		{
			case Result.Error => println("Error: " + name + " - Count " + groupCount + ", Failed " + groupFailures + ", Errors " + groupErrors)
			case Result.Passed => println("Passed: " + name + " - Count " + groupCount + ", Failed " + groupFailures + ", Errors " + groupErrors)
			case Result.Failed => println("Failed: " + name + " - Count " + groupCount + ", Failed " + groupFailures + ", Errors " + groupErrors)
		}
		if(!groupMessages.isEmpty)
		{
			groupMessages.foreach(println(_))
			groupMessages = Nil
			println("")
		}
	}
	protected def onOut(f: PrintWriter => Unit) = Control.trapAndLog(log){
			out match
			{
				case Some(pw) => f(pw)
				case None => log.warn("Method called when output was not open")
			}
		}
	protected def println(s: String) = onOut(_.println(s))
}

class FileReportListener(val file: Path, log: Logger) extends WriterReportListener(log)
{
	def open = new java.io.FileWriter(file.asFile)
}

abstract class TestEvent extends NotNull
{
	def result: Option[Result.Value]
}

sealed abstract class ScalaCheckEvent extends TestEvent
final case class PassedEvent(name: String, msg: String) extends ScalaCheckEvent { def result = Some(Result.Passed) }
final case class FailedEvent(name: String, msg: String) extends ScalaCheckEvent { def result = Some(Result.Failed) }

sealed abstract class ScalaTestEvent(val result: Option[Result.Value]) extends TestEvent
final case class TypedEvent(name: String, `type`: String, msg: Option[String])(result: Option[Result.Value]) extends ScalaTestEvent(result)
final case class TypedErrorEvent(name: String, `type`: String, msg: Option[String], cause: Option[Throwable])(result: Option[Result.Value]) extends ScalaTestEvent(result)
final case class MessageEvent(msg: String) extends ScalaTestEvent(None)
final case class ErrorEvent(msg: String) extends ScalaTestEvent(None)
final case class IgnoredEvent(name: String, msg: Option[String]) extends ScalaTestEvent(Some(Result.Passed))

sealed abstract class SpecsEvent extends TestEvent
final case class SpecificationReportEvent(successes: Int, failures: Int, errors: Int, skipped: Int, pretty: String, systems: Seq[SystemReportEvent], subSpecs: Seq[SpecificationReportEvent]) extends SpecsEvent
{
	def result = if(errors > 0) Some(Result.Error) else if(failures > 0) Some(Result.Failed) else Some(Result.Passed)
}
final case class SystemReportEvent(description: String, verb: String, skippedSus:Option[Throwable], literateDescription: Option[Seq[String]], examples: Seq[ExampleReportEvent]) extends SpecsEvent { def result = None }
final case class ExampleReportEvent(description: String, errors: Seq[Throwable], failures: Seq[RuntimeException], skipped: Seq[RuntimeException], subExamples: Seq[ExampleReportEvent]) extends SpecsEvent { def result = None }

trait EventOutput[E <: TestEvent]
{
	def output(e: E): Unit
}

sealed abstract class LazyEventOutput[E <: TestEvent](val log: Logger) extends EventOutput[E]

class ScalaCheckOutput(log: Logger) extends LazyEventOutput[ScalaCheckEvent](log)
{
	def output(event: ScalaCheckEvent) = event match
		{
			case PassedEvent(name, msg) => log.info("+ " + name + ": " + msg)
			case FailedEvent(name, msg) => log.error("! " + name + ": " + msg)
		}
}

class ScalaTestOutput(log: Logger) extends LazyEventOutput[ScalaTestEvent](log)
{
	def output(event: ScalaTestEvent) = event match
		{
			case TypedEvent(name, event, Some(msg)) => log.info(event + " - " + name + ": " + msg)
			case TypedEvent(name, event, None) => log.info(event + " - " + name)
			case TypedErrorEvent(name, event, Some(msg), cause) => logError(event + " - " + name + ": " + msg, cause)
			case TypedErrorEvent(name, event, None, cause) => logError(event + " - " + name, cause)
			case MessageEvent(msg) => log.info(msg)
			case ErrorEvent(msg) => logError(msg, None)
			case IgnoredEvent(name, Some(msg)) => log.info("Test ignored - " + name + ": " + msg)
			case IgnoredEvent(name, None) => log.info("Test ignored - " + name)
		}
	private def logError(message: String, cause: Option[Throwable])
	{
		cause.foreach(x => log.trace(x))
		log.error(message)
	}
}

class SpecsOutput(val log: Logger) extends EventOutput[SpecsEvent]
{
		private val Indent = "  "

	def output(event: SpecsEvent) = event match
		{
			case sre: SpecificationReportEvent => reportSpecification(sre, "")
			case sre: SystemReportEvent => reportSystem(sre, "")
			case ere: ExampleReportEvent => reportExample(ere, "")
		}

	/* The following is closely based on org.specs.runner.OutputReporter,
	* part of specs, which is Copyright 2007-2008 Eric Torreborre.
	* */

	private def reportSpecification(specification: SpecificationReportEvent, padding: String)
	{
		val newIndent = padding + Indent
		reportSpecifications(specification.subSpecs, newIndent)
		reportSystems(specification.systems, newIndent)
	}
	private def reportSpecifications(specifications: Iterable[SpecificationReportEvent], padding: String)
	{
		for(specification <- specifications)
			reportSpecification(specification, padding)
	}
	private def reportSystems(systems: Iterable[SystemReportEvent], padding: String)
	{
		for(system <- systems)
			reportSystem(system, padding)
	}
	private def reportSystem(sus: SystemReportEvent, padding: String)
	{
		log.info(padding + sus.description + " " + sus.verb + sus.skippedSus.map(" (skipped: " + _.getMessage + ")").getOrElse(""))
		for(description <- sus.literateDescription)
			log.info(padding + description.mkString)
		reportExamples(sus.examples, padding)
		log.info(" ")
	}
	private def reportExamples(examples: Iterable[ExampleReportEvent], padding: String)
	{
		for(example <- examples)
		{
			reportExample(example, padding)
			reportExamples(example.subExamples, padding + Indent)
		}
	}
	private def status(example: ExampleReportEvent) =
	{
		if (example.errors.size + example.failures.size > 0)
			"x "
		else if (example.skipped.size > 0)
			"o "
		else
			"+ "
	}
	private def reportExample(example: ExampleReportEvent, padding: String)
	{
		log.info(padding + status(example) + example.description)
		for(skip <- example.skipped)
		{
			log.trace(skip)
			log.warn(padding + skip.toString)
		}
		for(e <- example.failures ++ example.errors)
		{
			log.trace(e)
			log.error(padding + e.toString)
		}
	}
}

class LogTestReportListener(val log: Logger) extends TestsListener
{
	lazy val scalaCheckOutput: EventOutput[ScalaCheckEvent] = createScalaCheckOutput
	lazy val scalaTestOutput: EventOutput[ScalaTestEvent] = createScalaTestOutput
	lazy val specsOutput: EventOutput[SpecsEvent] = createSpecsOutput

	protected def createScalaCheckOutput = new ScalaCheckOutput(log)
	protected def createScalaTestOutput = new ScalaTestOutput(log)
	protected def createSpecsOutput = new SpecsOutput(log)
	
	def startGroup(name: String) {}
	def testEvent(event: TestEvent)
	{
		log.debug("in testEvent:" + event)
		count(event)
		event match
		{
			case sce: ScalaCheckEvent => scalaCheckOutput.output(sce)
			case ste: ScalaTestEvent => scalaTestOutput.output(ste)
			case se: SpecsEvent => specsOutput.output(se)
			case e => handleOtherTestEvent(e)
		}
	}
	protected def handleOtherTestEvent(event: TestEvent) {}
	def endGroup(name: String, t: Throwable)
	{
		log.error("Could not run test " + name + ": " + t.toString)
		log.trace(t)
	}
	def endGroup(name: String, result: Result.Value)
	{
		log.debug("in endGroup:" + result)
	}
	protected def count(event: TestEvent): Unit =
	{
		for(result <- event.result)
		{
			totalTests += 1
			result match
			{
				case Result.Error => errors += 1
				case Result.Failed => failed += 1
				case Result.Passed => passed += 1
			}
		}
	}
	protected var totalTests, errors, passed, failed = 0
	def doInit
	{
		totalTests = 0
		errors = 0
		passed = 0
		failed = 0
	}
		/** called once, at end. */
	def doComplete(finalResult: Result.Value): Unit =
		log.info(<x>Run: {totalTests.toString}, Passed: {passed.toString}, Errors: {errors.toString}, Failed: {failed.toString}</x>.text)
}
