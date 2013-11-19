import sbt._
import Keys._
import Tests._
import Defaults._
import java.io.{ CharArrayWriter, PrintWriter }

object Ticket543Test extends Build {
	val marker = new File("marker")
	val check = TaskKey[Unit]("check", "Check correct error has been returned.")

	lazy val root = Project("root", file("."), settings = defaultSettings ++  Seq(
		libraryDependencies += "org.scalatest" %% "scalatest" % "1.8" % "test",
		scalaVersion := "2.9.2",
		fork := true,
		testListeners += new TestReportListener {
		  def testEvent(event: SuiteReport) {
				for (e <- event.detail.filter(_.status == sbt.testing.Status.Failure)) {
					if (e.throwable != null && e.throwable.isDefined) {
						val caw = new CharArrayWriter
						e.throwable.get.printStackTrace(new PrintWriter(caw))
						if (caw.toString.contains("Test.scala:"))
							marker.createNewFile()
					}
				}
		  }
		  def startSuite(name: String) {}
		  def endSuite(name: String, t: Throwable) {}
		  def endSuite(name: String, result: TestResult.Value) {}
		},
		check := {
			val exists = marker.exists
			marker.delete()
			if (!exists) error("Null or invalid error had been returned previously")
		}
	))
}
