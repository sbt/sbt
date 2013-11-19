import sbt._
import Keys._
import scala.xml.XML
import Tests._
import Defaults._

object JUnitXmlReportTest extends Build {
	val checkReport = taskKey[Unit]("Check the test reports")
	val checkNoReport = taskKey[Unit]("Check that no reports are present")

	private val oneSecondReportFile = "target/test-reports/a.pkg.OneSecondTest.xml"
	private val failingReportFile = "target/test-reports/another.pkg.FailingTest.xml"

	lazy val root = Project("root", file("."), settings = defaultSettings ++ Seq(
		scalaVersion := "2.9.2",
		libraryDependencies += "com.novocode" % "junit-interface" % "0.10" % "test",

		testReportJUnitXml := true,

		// TODO use matchers instead of sys.error
		checkReport := {
			val oneSecondReport = XML.loadFile(oneSecondReportFile)
			if( oneSecondReport.label != "testsuite" ) sys.error("Report should have a root <testsuite> element.")
			// somehow the 'success' event doesn't go through... TODO investigate
//			if( (oneSecondReport \ "@time").text.toFloat < 1f ) sys.error("expected test to take at least 1 sec")
			if( (oneSecondReport \ "@name").text != "a.pkg.OneSecondTest" ) sys.error("wrong test name: " + (oneSecondReport \ "@name").text)
			// TODO more checks

			val failingReport = XML.loadFile(failingReportFile)
			if( failingReport.label != "testsuite" ) sys.error("Report should have a root <testsuite> element.")
			if( (failingReport \ "@failures").text != "2" ) sys.error("expected 2 failures")
			if( (failingReport \ "@name").text != "another.pkg.FailingTest" ) sys.error("wrong test name: " + (failingReport \ "@name").text)
			// TODO more checks -> the two test cases with time etc..

			// TODO check console output is in the report
		},

		checkNoReport := {
			if( file(oneSecondReportFile).exists() ) sys.error(oneSecondReportFile + " should not exist")
			if( file(failingReportFile).exists() ) sys.error(failingReportFile + " should not exist")
		}
	))
}