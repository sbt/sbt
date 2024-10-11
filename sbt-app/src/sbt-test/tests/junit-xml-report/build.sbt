import scala.xml.XML
import Tests._
import Defaults._

val checkReport = taskKey[Unit]("Check the test reports")
val checkNoReport = taskKey[Unit]("Check that no reports are present")

val oneSecondReportFile = "target/test-reports/TEST-a.pkg.OneSecondTest.xml"
val failingReportFile = "target/test-reports/TEST-another.pkg.FailingTest.xml"

val flatSuiteReportFile = "target/test-reports/TEST-my.scalatest.MyFlatSuite.xml"
val nestedSuitesReportFile = "target/test-reports/TEST-my.scalatest.MyNestedSuites.xml"

val scalatest = "org.scalatest" %% "scalatest" % "3.0.5"
val junitinterface = "com.novocode" % "junit-interface" % "0.11"

ThisBuild / scalaVersion := "2.12.20"

lazy val root = (project in file(".")).
  settings(
    libraryDependencies += junitinterface % Test,
    libraryDependencies += scalatest % Test,
    // TODO use matchers instead of sys.error
    checkReport := {
      val oneSecondReport = XML.loadFile(oneSecondReportFile)
      if( oneSecondReport.label != "testsuite" ) sys.error("Report should have a root <testsuite> element.")
      // somehow the 'success' event doesn't go through... TODO investigate
//      if( (oneSecondReport \ "@time").text.toFloat < 1f ) sys.error("expected test to take at least 1 sec")
      if( (oneSecondReport \ "@tests").text != "1" ) sys.error("expected 1 tests")
      if( (oneSecondReport \ "@failures").text != "0" ) sys.error("expected 0 failures")
      if( (oneSecondReport \ "@name").text != "a.pkg.OneSecondTest" ) sys.error("wrong fixture name: " + (oneSecondReport \ "@name").text)
      oneSecondReport foreach { testsuite =>
        val className = testsuite \ "testcase" \@ "classname"
        if( className != "a.pkg.OneSecondTest" ) sys.error(s"wrong class name: $className")

        val actualTestName = testsuite \ "testcase" \@ "name"
        if( actualTestName != "oneSecond") sys.error(s"wrong test names: $actualTestName")
      }
      // TODO more checks

      val failingReport = XML.loadFile(failingReportFile)
      if( failingReport.label != "testsuite" ) sys.error("Report should have a root <testsuite> element.")
      if( (failingReport \ "@failures").text != "2" ) sys.error("expected 2 failures")
      if( (failingReport \ "@name").text != "another.pkg.FailingTest" ) sys.error("wrong test name: " + (failingReport \ "@name").text)
      // TODO more checks -> the two test cases with time etc..

      val scalaTestFlatReport = XML.loadFile(flatSuiteReportFile)
      if( scalaTestFlatReport.label != "testsuite" ) sys.error("Report should have a root <testsuite> element.")
      if( (scalaTestFlatReport \ "@tests").text != "3" ) sys.error("expected 3 tests")
      if( (scalaTestFlatReport \ "@failures").text != "1" ) sys.error("expected 1 failures")
      if( (scalaTestFlatReport \ "@name").text != "my.scalatest.MyFlatSuite" ) sys.error("wrong fixture name: " + (scalaTestFlatReport \ "@name").text)
      scalaTestFlatReport foreach { testsuite =>
        val classNames = (testsuite \ "testcase").map(_ \@ "classname")
        if( classNames.length != 3 ) sys.error(s"expected 3 classname's, actual result: " + classNames.length)
        if( classNames.toSet != Set("my.scalatest.MyFlatSuite") ) sys.error(s"wrong class names: ${classNames.mkString(", ")}")

        val expectedTestNames = Set(
          "Passing test should pass",
          "Passing test should also pass with file.extension",
          "Failing test should fail"
        )
        val actualTestName = (testsuite \ "testcase").map(_ \@ "name")
        if( actualTestName.toSet != expectedTestNames) sys.error(s"wrong test names: ${actualTestName.mkString(", ")}")
      }

      val nestedSuitesReport = XML.loadFile(nestedSuitesReportFile)
      if( nestedSuitesReport.label != "testsuite" ) sys.error("Report should have a root <testsuite> element.")
      if( (nestedSuitesReport \ "@tests").text != "2" ) sys.error("expected 2 tests")
      if( (nestedSuitesReport \ "@failures").text != "1" ) sys.error("expected 1 failures")
      if( (nestedSuitesReport \ "@name").text != "my.scalatest.MyNestedSuites" ) sys.error("wrong fixture name: " + (nestedSuitesReport \ "@name").text)
      nestedSuitesReport foreach { testsuite =>
        val classNames = (testsuite \ "testcase").map(_ \@ "classname")
        if( classNames.length != 2 ) sys.error(s"expected 2 classname's, actual result: " + classNames.length)
        if( classNames.toSet != Set("my.scalatest.MyInnerSuite") ) sys.error(s"wrong class names: ${classNames.mkString(", ")}")

        val actualTestName = (testsuite \ "testcase").map(_ \@ "name")
        if( actualTestName.toSet != Set("Inner passing test should pass", "Inner failing test should fail")) sys.error(s"wrong test names: ${actualTestName.mkString(", ")}")
      }

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
