import sbt._
import Keys._
import scala.xml.XML
import Tests._
import Defaults._
import org.backuity.matchete.{Matcher, AssertionMatchers, XmlMatchers, FileMatchers}

object JUnitXmlReportTest extends Build with AssertionMatchers with XmlMatchers with FileMatchers {
	val checkReport = taskKey[Unit]("Check the test reports")
	val checkReportNoStdOut = taskKey[Unit]("Check the test reports (not checking stdout)")
	val checkNoReport = taskKey[Unit]("Check that no reports are present")

	private val oneSecondReportFile = "target/test-reports/a.pkg.OneSecondTest.xml"
	private val failingReportFile = "target/test-reports/another.pkg.FailingTest.xml"
	private val failUponConstructionReportFile = "target/test-reports/another.pkg.FailUponConstructionTest.xml"
	private val consoleReportFile = "target/test-reports/console.test.pkg.ConsoleTests.xml"

	def greaterThan(float: Float) : Matcher[String] = be("greater than " + float) {
		case attr => attr.toFloat must be_>=(float)
	}

	lazy val root = Project("root", file("."), settings = defaultSettings ++ Seq(
		scalaVersion := "2.9.2",
		libraryDependencies ++= Seq(
			"com.novocode" % "junit-interface" % "0.10" % "test",
			"org.scalatest" % "scalatest_2.9.2" % "2.0.M3" % "test" intransitive()),

		testReportJUnitXml := true,

		checkReport := {
			doCheckReport(checkStdOut = true)
		},

		checkReportNoStdOut := {
			doCheckReport(checkStdOut = false)
		},

		checkNoReport := {
			file(oneSecondReportFile) must not(exist)
			file(failingReportFile) must not(exist)
			file(failUponConstructionReportFile) must not(exist)
			file(consoleReportFile) must not(exist)
		}
	))

	private def doCheckReport(checkStdOut: Boolean) {
		val oneSecondReport = XML.loadFile(oneSecondReportFile)
		oneSecondReport must haveLabel("testsuite")
		oneSecondReport must haveAttribute("name", equalTo("a.pkg.OneSecondTest"))

		// junit-interface does not report time yet...
		//			oneSecondReport must haveAttribute("time", greaterThan(1f))

		oneSecondReport \ "testcase" must containExactly(
			a("one-second testcase") { case tc =>
				tc must haveAttribute("name", equalTo("oneSecond"))
				tc must haveAttribute("classname", equalTo("a.pkg.OneSecondTest"))
				//					tc must haveAttribute("time", greaterThan(1f))
			})

		val failingReport = XML.loadFile(failingReportFile)
		failingReport must haveLabel("testsuite")
		failingReport must haveAttribute("failures", equalTo("2"))
		//			failingReport must haveAttribute("time", greaterThan(1.5f)) // time is sumed-up in the testsuite element
		failingReport must haveAttribute("name", equalTo("another.pkg.FailingTest"))
		// TODO more checks -> the two test cases with time etc..

		val failUponConstructionReport = XML.loadFile(failUponConstructionReportFile)
		failUponConstructionReport must haveLabel("testsuite")
		failUponConstructionReport must haveAttribute("errors", equalTo("1"))
		(failUponConstructionReport \ "system-err").text must (contain("failed upon construction") and contain("RuntimeException"))
		failUponConstructionReport \ "testcase" must haveSize(0)

		val consoleReport = XML.loadFile(consoleReportFile)
		consoleReport must haveLabel("testsuite")
		consoleReport must haveAttribute("tests", equalTo("2"))
		consoleReport must haveAttribute("name", equalTo("console.test.pkg.ConsoleTests"))
		consoleReport must haveAttribute("failures", equalTo("0"))
		consoleReport \ "testcase" must containExactly(
			a("sayHello test-case") { case tc =>
				tc must haveAttribute("name", equalTo("sayHello"))
				tc must haveAttribute("classname", equalTo("console.test.pkg.ConsoleTests"))
				if( checkStdOut ) {
					tc \ "system-out" must haveTrimmedText("Hello\nWorld!")
				}},

			a("multiThreadedHello test-case") { case tc =>
				tc must haveAttribute("name", equalTo("multiThreadedHello"))
				tc must haveAttribute("classname", equalTo("console.test.pkg.ConsoleTests"))
				if( checkStdOut ) {
					(tc \ "system-out").text.trim.split("\n").toList must containElements( (for( i <- 1 to 15) yield s"Hello from thread $i") : _*)
				}})

	}
}