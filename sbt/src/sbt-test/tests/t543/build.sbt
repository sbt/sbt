import Tests._
import Defaults._
import java.io.{ CharArrayWriter, PrintWriter }

val marker = new File("marker")
val check = TaskKey[Unit]("check", "Check correct error has been returned.")

lazy val root = (project in file(".")).
  settings(
    libraryDependencies ++= List(
      "org.scala-lang.modules" %% "scala-xml" % "1.0.1",
      "org.scalatest" %% "scalatest" % "2.2.6"
    ),
    scalaVersion := "2.11.8",
    fork := true,
    testListeners += new TestReportListener {
      def testEvent(event: TestEvent): Unit = {
        for (e <- event.detail.filter(_.status == sbt.testing.Status.Failure)) {
          if (e.throwable != null && e.throwable.isDefined) {
            val caw = new CharArrayWriter
            e.throwable.get.printStackTrace(new PrintWriter(caw))
            if (caw.toString.contains("Test.scala:"))
              marker.createNewFile()
          }
        }
      }
      def startGroup(name: String): Unit = ()
      def endGroup(name: String, t: Throwable): Unit = ()
      def endGroup(name: String, result: TestResult): Unit = ()
    },
    check := {
      val exists = marker.exists
      marker.delete()
      if (!exists) sys.error("Null or invalid error had been returned previously")
    }
  )
