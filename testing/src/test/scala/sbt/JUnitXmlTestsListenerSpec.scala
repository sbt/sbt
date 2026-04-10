/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import java.io.File
import java.util.concurrent.atomic.AtomicReference

import testing.{ Event as TEvent, OptionalThrowable, Status as TStatus, TestSelector }
import util.{ AbstractLogger, Level, ControlEvent, LogEvent }
import sbt.protocol.testing.TestResult
import sbt.internal.worker1.ForkTestMain
import verify.BasicTestSuite
import scala.xml.XML

object JUnitXmlTestsListenerSpec extends BasicTestSuite:

  test("JUnitXmlTestsListener should log debug message when writing test report"):
    val tempDir = File.createTempFile("junit-test", "")
    tempDir.delete()
    tempDir.mkdirs()
    try
      val loggedMessages = new AtomicReference[List[String]](Nil)
      val mockLogger = new AbstractLogger:
        def getLevel: Level.Value = Level.Debug
        def setLevel(newLevel: Level.Value): Unit = ()
        def getTrace: Int = 0
        def setTrace(flag: Int): Unit = ()
        def successEnabled: Boolean = false
        def setSuccessEnabled(flag: Boolean): Unit = ()
        def control(event: ControlEvent.Value, message: => String): Unit = ()
        def logAll(events: Seq[LogEvent]): Unit = ()
        def trace(t: => Throwable): Unit = ()
        def success(message: => String): Unit = ()
        def log(level: Level.Value, message: => String): Unit =
          if level == Level.Debug then loggedMessages.updateAndGet(_ :+ message)

      val listener = new JUnitXmlTestsListener(tempDir, false, mockLogger)
      listener.doInit()
      listener.startGroup("TestSuite")

      // Create a test event
      val testEvent = new TEvent:
        def fullyQualifiedName = "TestSuite.testMethod"
        def duration() = 100L
        def status = TStatus.Success
        def fingerprint = null
        def selector = new TestSelector("testMethod")
        def throwable = new OptionalThrowable()

      listener.testEvent(sbt.TestEvent(Seq(testEvent)))

      // End the group to trigger writeSuite()
      listener.endGroup("TestSuite", TestResult.Passed)

      // Verify that the debug message was logged
      val messages = loggedMessages.get()
      assert(
        messages.exists(_.contains("writing JUnit XML test report")),
        s"Expected log message containing 'writing JUnit XML test report', but got: $messages"
      )
      assert(
        messages.exists(_.contains("TEST-TestSuite.xml")),
        s"Expected log message containing 'TEST-TestSuite.xml', but got: $messages"
      )
    finally
      // Cleanup
      if tempDir.exists() then
        tempDir.listFiles().foreach(_.delete())
        tempDir.delete()

  test("JUnitXmlTestsListener should handle null logger gracefully"):
    val tempDir = File.createTempFile("junit-test", "")
    tempDir.delete()
    tempDir.mkdirs()
    try
      val listener = new JUnitXmlTestsListener(tempDir, false, null)
      listener.doInit()
      listener.startGroup("TestSuite")

      val testEvent = new TEvent:
        def fullyQualifiedName = "TestSuite.testMethod"
        def duration() = 100L
        def status = TStatus.Success
        def fingerprint = null
        def selector = new TestSelector("testMethod")
        def throwable = new OptionalThrowable()

      listener.testEvent(sbt.TestEvent(Seq(testEvent)))

      // Should not throw when logger is null
      listener.endGroup("TestSuite", TestResult.Passed)

      // Verify XML file was still created
      val xmlFile = new File(tempDir, "TEST-TestSuite.xml")
      assert(xmlFile.exists(), "XML file should be created even when logger is null")
    finally
      if tempDir.exists() then
        tempDir.listFiles().foreach(_.delete())
        tempDir.delete()

  test("JUnit XML report should use original exception type for forked test failures"):
    val tempDir = File.createTempFile("junit-test", "")
    tempDir.delete()
    tempDir.mkdirs()
    try
      val listener = new JUnitXmlTestsListener(tempDir, false, null)
      listener.doInit()
      listener.startGroup("TestSuite")

      // Simulate a forked test failure: the original NullPointerException gets
      // wrapped in ForkError during serialization across the forked JVM boundary.
      val originalException = new NullPointerException("something was null")
      originalException.setStackTrace(
        Array(new StackTraceElement("com.example.MyTest", "testFoo", "MyTest.java", 42))
      )
      val forkError = new ForkTestMain.ForkError(originalException)

      val testEvent = new TEvent:
        def fullyQualifiedName = "TestSuite.testFoo"
        def duration() = 100L
        def status = TStatus.Failure
        def fingerprint = null
        def selector = new TestSelector("testFoo")
        def throwable = new OptionalThrowable(forkError)

      listener.testEvent(sbt.TestEvent(Seq(testEvent)))
      listener.endGroup("TestSuite", TestResult.Failed)

      val xmlFile = new File(tempDir, "TEST-TestSuite.xml")
      assert(xmlFile.exists(), "XML file should be created")
      val xml = XML.loadFile(xmlFile)
      val failureNodes = xml \\ "failure"
      assert(failureNodes.nonEmpty, "Should have a failure element")
      val failureType = (failureNodes.head \ "@type").text
      val failureMessage = (failureNodes.head \ "@message").text
      assert(
        failureType == "java.lang.NullPointerException",
        s"Expected type 'java.lang.NullPointerException' but got '$failureType'"
      )
      assert(
        failureMessage == "something was null",
        s"Expected message 'something was null' but got '$failureMessage'"
      )
      val traceText = failureNodes.head.text
      assert(
        traceText.contains("java.lang.NullPointerException"),
        s"Stacktrace should contain original exception name, got: $traceText"
      )
      assert(
        !traceText.contains("ForkError"),
        s"Stacktrace should not contain ForkError, got: $traceText"
      )
    finally
      if tempDir.exists() then
        tempDir.listFiles().foreach(_.delete())
        tempDir.delete()

end JUnitXmlTestsListenerSpec
