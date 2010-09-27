package ptest

import sbt._
import Path.fromFile
import org.specs._

class ProcessorTest extends Specification
{
	"basic processor " should {
		def success(f: processor.Success => Unit) = successOrFail(basicProcessorResult)(f)

		"succeed" in {
			success(_ => ())
		}
		"not insert any arguments" in {
			success(_.insertArguments must_== Nil)
		}
		"preserve the fail handler" in {
			success(_.onFailure must_== basicFail)
		}
	}
	"full processor " should {
		def success(f: processor.Success => Unit) = successOrFail(fullProcessorResult)(f)
		"succeed" in {
			success(_ => ())
		}
		"insert correct arguments" in {
			success(_.insertArguments must_== testArgs)
		}
		"preserve the fail handler" in {
			success(_.onFailure must_== testFail)
		}
	}

	def successOrFail(r: processor.ProcessorResult)(f: processor.Success => Unit) =
		r match
		{
			case s: processor.Success => f(s)
			case _ => error("Processor failed: " + r)
		}
		
	def withProject[T](f: Project => T): T =
	{
		val log = new ConsoleLogger
		xsbt.FileUtilities.withTemporaryDirectory { tmp =>
			val app = xsbt.boot.Launcher.defaultAppProvider(tmp)
			val info = new ProjectInfo(tmp, Nil, None)(log, app, None)
			val project = new DefaultProject(info)
			f(project)
		}
	}
	def basicProcessorResult =
	{
		var ranBasicArgs: Option[String] = None
		val basic = new processor.BasicProcessor {
			def apply(p: Project, args: String) = { ranBasicArgs = Some(args) }
		}
		val result = withProject { project => basic("basic", project, basicFail, basicArgs) }
		ranBasicArgs must_== Some(basicArgs)
		result
	}
	def fullProcessorResult =
	{
		val full = new processor.Processor {
			def apply(label: String, p: Project, failAction: Option[String], args: String) = new processor.Success(p, failAction, args.split("""\s+"""): _*)
		}
		withProject { project => full("full", project, testFail, testArgs.mkString(" ")) }
	}

	lazy val testFail = Some("fail")
	lazy val testArgs = List("a", "b")
	lazy val basicArgs = " a b c "
	lazy val basicFail = Some("basic-fail")
}