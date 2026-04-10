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
import verify.BasicTestSuite

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

  test("JUnitXmlTestsListener should capture stdout and stderr when enabled"):
    val tempDir = File.createTempFile("junit-test", "")
    tempDir.delete()
    tempDir.mkdirs()
    try
      val listener = new JUnitXmlTestsListener(tempDir, false, null, true, true)
      listener.doInit()
      listener.startGroup("CaptureTestSuite")

      // Get the content logger and write to it
      val testDef = new TestDefinition(
        "CaptureTestSuite.testCapture",
        null,
        false,
        Array(new TestSelector("testCapture"))
      )
      val contentLoggerOpt = listener.contentLogger(testDef)
      assert(contentLoggerOpt.isDefined, "contentLogger should return Some when capture is enabled")
      val cl = contentLoggerOpt.get
      cl.log.info("hello stdout")
      cl.log.debug("debug stdout")
      cl.log.error("hello stderr")
      cl.log.warn("warn stderr")
      cl.flush()

      val testEvent = new TEvent:
        def fullyQualifiedName = "CaptureTestSuite.testCapture"
        def duration() = 50L
        def status = TStatus.Success
        def fingerprint = null
        def selector = new TestSelector("testCapture")
        def throwable = new OptionalThrowable()

      listener.testEvent(sbt.TestEvent(Seq(testEvent)))
      listener.endGroup("CaptureTestSuite", TestResult.Passed)

      // Read and verify the XML
      val xmlFile = new File(tempDir, "TEST-CaptureTestSuite.xml")
      assert(xmlFile.exists(), "XML file should be created")
      val xml = scala.xml.XML.loadFile(xmlFile)
      val sysOut = (xml \ "system-out").text
      val sysErr = (xml \ "system-err").text
      assert(sysOut.contains("hello stdout"), s"system-out should contain 'hello stdout', got: $sysOut")
      assert(sysOut.contains("debug stdout"), s"system-out should contain 'debug stdout', got: $sysOut")
      assert(sysErr.contains("hello stderr"), s"system-err should contain 'hello stderr', got: $sysErr")
      assert(sysErr.contains("warn stderr"), s"system-err should contain 'warn stderr', got: $sysErr")
    finally
      if tempDir.exists() then
        tempDir.listFiles().foreach(_.delete())
        tempDir.delete()

  test("JUnitXmlTestsListener should have empty system-out/err when capture is disabled"):
    val tempDir = File.createTempFile("junit-test", "")
    tempDir.delete()
    tempDir.mkdirs()
    try
      val listener = new JUnitXmlTestsListener(tempDir, false, null, false, false)
      listener.doInit()
      listener.startGroup("NoCaptureTestSuite")

      val testDef = new TestDefinition(
        "NoCaptureTestSuite.testNoCapture",
        null,
        false,
        Array(new TestSelector("testNoCapture"))
      )
      val contentLoggerOpt = listener.contentLogger(testDef)
      assert(contentLoggerOpt.isEmpty, "contentLogger should return None when capture is disabled")

      val testEvent = new TEvent:
        def fullyQualifiedName = "NoCaptureTestSuite.testNoCapture"
        def duration() = 50L
        def status = TStatus.Success
        def fingerprint = null
        def selector = new TestSelector("testNoCapture")
        def throwable = new OptionalThrowable()

      listener.testEvent(sbt.TestEvent(Seq(testEvent)))
      listener.endGroup("NoCaptureTestSuite", TestResult.Passed)

      val xmlFile = new File(tempDir, "TEST-NoCaptureTestSuite.xml")
      assert(xmlFile.exists(), "XML file should be created")
      val xml = scala.xml.XML.loadFile(xmlFile)
      val sysOut = (xml \ "system-out").text
      val sysErr = (xml \ "system-err").text
      assert(sysOut.isEmpty, s"system-out should be empty, got: $sysOut")
      assert(sysErr.isEmpty, s"system-err should be empty, got: $sysErr")
    finally
      if tempDir.exists() then
        tempDir.listFiles().foreach(_.delete())
        tempDir.delete()

  test("JUnitXmlTestsListener should capture only stdout when only captureStdOut is enabled"):
    val tempDir = File.createTempFile("junit-test", "")
    tempDir.delete()
    tempDir.mkdirs()
    try
      val listener = new JUnitXmlTestsListener(tempDir, false, null, true, false)
      listener.doInit()
      listener.startGroup("StdOutOnlySuite")

      val testDef = new TestDefinition(
        "StdOutOnlySuite.test",
        null,
        false,
        Array(new TestSelector("test"))
      )
      val contentLoggerOpt = listener.contentLogger(testDef)
      assert(contentLoggerOpt.isDefined, "contentLogger should return Some when captureStdOut is enabled")
      val cl = contentLoggerOpt.get
      cl.log.info("info message")
      cl.log.error("error message")
      cl.flush()

      val testEvent = new TEvent:
        def fullyQualifiedName = "StdOutOnlySuite.test"
        def duration() = 50L
        def status = TStatus.Success
        def fingerprint = null
        def selector = new TestSelector("test")
        def throwable = new OptionalThrowable()

      listener.testEvent(sbt.TestEvent(Seq(testEvent)))
      listener.endGroup("StdOutOnlySuite", TestResult.Passed)

      val xmlFile = new File(tempDir, "TEST-StdOutOnlySuite.xml")
      val xml = scala.xml.XML.loadFile(xmlFile)
      val sysOut = (xml \ "system-out").text
      val sysErr = (xml \ "system-err").text
      assert(sysOut.contains("info message"), s"system-out should contain 'info message', got: $sysOut")
      assert(!sysErr.contains("error message"), s"system-err should be empty when captureStdErr is false, got: $sysErr")
    finally
      if tempDir.exists() then
        tempDir.listFiles().foreach(_.delete())
        tempDir.delete()

end JUnitXmlTestsListenerSpec
