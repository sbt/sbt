import sbt._
import Keys._
import Tests._
import Defaults._

object Ticket543Test extends Build {
	val marker = new File("marker")
	val check = TaskKey[Unit]("check", "Check non-null error has been returned.")

	lazy val root = Project("root", file("."), settings = defaultSettings ++  Seq(
		libraryDependencies += "org.scalatest" %% "scalatest" % "1.8" % "test",
		fork := true,
		testListeners += new TestReportListener {
		  def testEvent(event: TestEvent) {
				for (e <- event.detail.filter(_.result() == org.scalatools.testing.Result.Failure)) {
					if (e.error() ne null) {
						marker.createNewFile()
					}
				}
		  }
		  def startGroup(name: String) {}
		  def endGroup(name: String, t: Throwable) {}
		  def endGroup(name: String, result: TestResult.Value) {}
		},
		check := {
			val exists = marker.exists
			marker.delete()
			if (!exists) error("Null error had been returned previously")
		}
	))
}
