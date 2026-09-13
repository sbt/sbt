import java.io.File

import sbt.io.IO

val implicitSuite = "example.ImplicitInitializationFailure"
val explicitSuite = "example.ExplicitInitializationFailure"

ThisBuild / scalaVersion := "2.12.21"

libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.20" % Test

/** Returns the callback log file for one suite. */
def callbackFile(base: File, suite: String): File = base / s"$suite.log"

/** Renders a throwable callback as the stable form asserted by this fixture. */
def throwableCallbackLine(name: String, throwable: Throwable): String = {
  val causeClass = Option(throwable.getCause).fold("<none>")(_.getClass.getName)
  s"throwable|$name|${throwable.getClass.getName}|$causeClass"
}

/** Verifies that a suite produced exactly one expected terminal callback. */
def checkCallback(base: File, suite: String, expected: String): Unit = {
  val file = callbackFile(base, suite)
  val actual = if (file.exists) IO.readLines(file) else Nil
  if (actual != List(expected)) {
    sys.error(s"Expected only callback '$expected', but observed: ${actual.mkString(", ")}")
  }
}

// Use one listener so the fixture records only the terminal lifecycle callback under test.
Test / testListeners := {
  val base = baseDirectory.value
  Seq(new TestReportListener {
    def startGroup(name: String): Unit = ()
    def testEvent(event: TestEvent): Unit = ()
    def endGroup(name: String, throwable: Throwable): Unit = {
      IO.append(callbackFile(base, name), throwableCallbackLine(name, throwable) + "\n")
    }
    def endGroup(name: String, result: TestResult): Unit = {
      IO.append(callbackFile(base, name), s"result|$name|$result\n")
    }
  })
}

val checkImplicitCallback = taskKey[Unit]("Check the implicit initializer error callback")
val checkExplicitCallback = taskKey[Unit]("Check the explicit initializer error callback")

checkImplicitCallback :=
  checkCallback(
    baseDirectory.value,
    implicitSuite,
    s"throwable|$implicitSuite|java.lang.ExceptionInInitializerError|java.util.NoSuchElementException"
  )
checkExplicitCallback :=
  checkCallback(
    baseDirectory.value,
    explicitSuite,
    s"throwable|$explicitSuite|java.lang.ExceptionInInitializerError|java.lang.IllegalStateException"
  )
