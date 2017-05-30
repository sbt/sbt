import scala.xml.XML
import Tests._
import Defaults._

val checkReport = taskKey[Unit]("Check the test reports")
val checkNoReport = taskKey[Unit]("Check that no reports are present")

val oneSecondReportFile = "target/test-reports/a.pkg.OneSecondTest.xml"
val failingReportFile = "target/test-reports/another.pkg.FailingTest.xml"

val flatSuiteReportFile = "target/test-reports/my.scalatest.MyFlatSuite.xml"
val nestedSuitesReportFile = "target/test-reports/my.scalatest.MyNestedSuites.xml"

lazy val root = (project in file(".")).
  settings(
    scalaVersion := "2.11.8",
    libraryDependencies += "com.novocode" % "junit-interface" % "0.11" % Test,
    libraryDependencies += "org.scalatest" %% "scalatest" % "2.2.6" % Test,
    // TODO use matchers instead of sys.error
    checkReport := {
      val oneSecondReport = XML.loadFile(oneSecondReportFile)
      if( oneSecondReport.label != "testsuite" ) sys.error("Report should have a root <testsuite> element.")
      // somehow the 'success' event doesn't go through... TODO investigate
//      if( (oneSecondReport \ "@time").text.toFloat < 1f ) sys.error("expected test to take at least 1 sec")
      if( (oneSecondReport \ "@name").text != "a.pkg.OneSecondTest" ) sys.error("wrong fixture name: " + (oneSecondReport \ "@name").text)
      // TODO more checks

      val failingReport = XML.loadFile(failingReportFile)
      if( failingReport.label != "testsuite" ) sys.error("Report should have a root <testsuite> element.")
      if( (failingReport \ "@failures").text != "2" ) sys.error("expected 2 failures")
      if( (failingReport \ "@name").text != "another.pkg.FailingTest" ) sys.error("wrong test name: " + (failingReport \ "@name").text)
      // TODO more checks -> the two test cases with time etc..

      val scalaTestFlatReport = XML.loadFile(flatSuiteReportFile)
      if( scalaTestFlatReport.label != "testsuite" ) sys.error("Report should have a root <testsuite> element.")
      if( (scalaTestFlatReport \ "@tests").text != "2" ) sys.error("expected 2 tests")
      if( (scalaTestFlatReport \ "@failures").text != "1" ) sys.error("expected 1 failures")
      if( (scalaTestFlatReport \ "@name").text != "my.scalatest.MyFlatSuite" ) sys.error("wrong fixture name: " + (scalaTestFlatReport \ "@name").text)

      val nestedSuitesReport = XML.loadFile(nestedSuitesReportFile)
      if( nestedSuitesReport.label != "testsuite" ) sys.error("Report should have a root <testsuite> element.")
      if( (nestedSuitesReport \ "@tests").text != "2" ) sys.error("expected 2 tests")
      if( (nestedSuitesReport \ "@failures").text != "1" ) sys.error("expected 1 failures")
      if( (nestedSuitesReport \ "@name").text != "my.scalatest.MyNestedSuites" ) sys.error("wrong fixture name: " + (nestedSuitesReport \ "@name").text)
      val actualTestName = (nestedSuitesReport \ "testcase").map(t => (t \ "@name").text)
      if( actualTestName.toSet != Set("MyInnerSuite.Inner passing test should pass", "MyInnerSuite.Inner failing test should fail")) sys.error(s"wrong test names: ${actualTestName.mkString(", ")}")

      // TODO check console output is in the report
    },

    checkNoReport := {
      for (f <- Seq(
        oneSecondReportFile,
        failingReportFile,
        flatSuiteReportFile,
        nestedSuitesReportFile
      )) {
        if( file(f).exists() ) sys.error(f + " should not exist")
      }
    }
  )
