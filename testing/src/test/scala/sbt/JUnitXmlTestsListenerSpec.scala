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
import sbt.io.IO
import sbt.protocol.testing.TestResult
import verify.BasicTestSuite

object JUnitXmlTestsListenerSpec extends BasicTestSuite:

  test("JUnitXmlTestsListener should log debug message when writing test report"):
    IO.withTemporaryDirectory: tempDir =>
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

  test("JUnitXmlTestsListener should handle null logger gracefully"):
    IO.withTemporaryDirectory: tempDir =>
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

  test("JUnitXmlTestsListener should release the suite from threads that inherited it"):
    IO.withTemporaryDirectory: tempDir =>
      val debugMessages = new AtomicReference[List[String]](Nil)
      val listener = new JUnitXmlTestsListener(tempDir, false, recordingLogger(debugMessages))
      listener.doInit()

      def event(name: String) = new TEvent:
        def fullyQualifiedName = s"InheritSuite.$name"
        def duration() = 1L
        def status = TStatus.Success
        def fingerprint = null
        def selector = new TestSelector(name)
        def throwable = new OptionalThrowable()

      listener.startGroup("InheritSuite")

      // A thread created *while* the suite is set inherits the suite cell, standing in for a
      // pooled worker spawned by an async test framework during the run.
      val suiteWritten = new java.util.concurrent.CountDownLatch(1)
      val childDone = new java.util.concurrent.CountDownLatch(1)
      val endGroupOutcome = new AtomicReference[Option[Throwable]](None)
      val testEventOutcome = new AtomicReference[Option[Throwable]](None)
      val child = new Thread(() =>
        suiteWritten.await()
        // The inherited cell must no longer reach a TestSuite, which the debug line below reports...
        endGroupOutcome.set(
          scala.util.Try(listener.endGroup("InheritSuite", TestResult.Passed)).failed.toOption
        )
        // ...while a late event is dropped rather than raised.
        testEventOutcome.set(
          scala.util.Try(listener.testEvent(sbt.TestEvent(Seq(event("late"))))).failed.toOption
        )
        childDone.countDown()
      )
      child.setDaemon(true)
      child.start()

      listener.testEvent(sbt.TestEvent(Seq(event("testMethod"))))
      listener.endGroup("InheritSuite", TestResult.Passed)
      suiteWritten.countDown()
      assert(childDone.await(30, java.util.concurrent.TimeUnit.SECONDS), "child thread timed out")

      assert(
        debugMessages.get().exists(_.contains("which is not open")),
        s"a thread that inherited the suite could still reach it: ${debugMessages.get()}"
      )
      assert(
        endGroupOutcome.get().isEmpty,
        s"ending a suite this thread never started should be skipped, but threw: ${endGroupOutcome.get()}"
      )
      assert(
        testEventOutcome.get().isEmpty,
        s"a late test event should be dropped, but threw: ${testEventOutcome.get()}"
      )
      // The report the parent wrote still has its test case: nothing invented an empty replacement.
      val written = scala.xml.XML.loadFile(new File(tempDir, "TEST-InheritSuite.xml"))
      assert((written \\ "testcase").size == 1, written.toString)

  test("a suite ended on a thread that never started one is skipped, not an error"):
    // How sbt reports a forked worker that died mid-suite: the process watch thread ends the suites
    // the worker left open, and it never saw them start. Raising there used to cost every listener
    // after this one the crash report.
    IO.withTemporaryDirectory: tempDir =>
      val debugMessages = new AtomicReference[List[String]](Nil)
      val listener = new JUnitXmlTestsListener(tempDir, false, recordingLogger(debugMessages))
      listener.doInit()
      listener.endGroup("Crashed", TestResult.Error)
      listener.endGroup("Crashed", new RuntimeException("the worker died"))
      assert(tempDir.listFiles().isEmpty, tempDir.listFiles().mkString(", "))
      assert(debugMessages.get().count(_.contains("which is not open")) == 2)

  test("suites open at the same time on one thread each end as themselves"):
    // How a forked worker reports a group: it runs the group's classes concurrently, and sbt hands
    // every one of their events to this listener on the single thread reading that connection. Keyed
    // by the ending thread, the whole group collapsed into one report under one class's name.
    IO.withTemporaryDirectory: tempDir =>
      val listener = new JUnitXmlTestsListener(tempDir, false, null)
      listener.doInit()

      val names = Vector("SpecA", "SpecB", "SpecC")
      for (n <- names) listener.startGroup(n)
      for (n <- names) listener.testEvent(sbt.TestEvent(Seq(passed(n, s"only-$n"))))
      for (n <- names) listener.endGroup(n, TestResult.Passed)

      // What a suite's report says it is, and which test cases reached it.
      def summarize(n: String): (String, List[String]) =
        val report = new File(tempDir, s"TEST-$n.xml")
        if !report.exists() then ("<no report>", Nil)
        else
          val xml = scala.xml.XML.loadFile(report)
          ((xml \ "@name").text, (xml \\ "testcase" \ "@name").map(_.text).toList)

      val written = names.map(summarize)
      assert(
        written == names.map(n => (n, List(s"only-$n"))),
        s"each suite must end as itself with only its own events, but got: $written"
      )

  test("a group that finishes does not drop the suites another group still has open"):
    // One listener serves every group of a project's test task, and doInit/doComplete bracket a
    // single group -- ForkTests does it per group, and so does TestFramework.createTestTasks. Groups
    // overlap whenever a build raises Tags.ForkedTestGroup, which is what work stealing asks for, or
    // puts an in-process group beside a forked one, which needs no setting at all. Clearing the open
    // suites on the first doComplete took the other group's report with it, and the only trace was a
    // debug line saying the suite was not open.
    IO.withTemporaryDirectory: tempDir =>
      val listener = new JUnitXmlTestsListener(tempDir, false, null)
      listener.doInit() // the group running FirstGroupSpec
      listener.doInit() // and the group running SecondGroupSpec, which outlives it
      listener.startGroup("FirstGroupSpec")
      listener.startGroup("SecondGroupSpec")
      listener.testEvent(sbt.TestEvent(Seq(passed("FirstGroupSpec", "first"))))
      listener.testEvent(sbt.TestEvent(Seq(passed("SecondGroupSpec", "second"))))
      listener.endGroup("FirstGroupSpec", TestResult.Passed)
      listener.doComplete(TestResult.Passed)
      // The second group's suite was open across that doComplete, and its end still has to write it.
      listener.endGroup("SecondGroupSpec", TestResult.Passed)
      listener.doComplete(TestResult.Passed)

      val report = new File(tempDir, "TEST-SecondGroupSpec.xml")
      assert(report.exists(), s"only wrote: ${tempDir.listFiles().mkString(", ")}")
      val cases = (scala.xml.XML.loadFile(report) \\ "testcase" \ "@name").map(_.text).toList
      assert(cases == List("second"), cases.toString)

  test("the last group out still drops a suite nothing ended"):
    // The other half of that contract: a suite whose group died without ending it has no end left to
    // write it, so once every group is out it must not be held any longer.
    IO.withTemporaryDirectory: tempDir =>
      val debugMessages = new AtomicReference[List[String]](Nil)
      val listener = new JUnitXmlTestsListener(tempDir, false, recordingLogger(debugMessages))
      listener.doInit()
      listener.startGroup("AbandonedSpec")
      listener.doComplete(TestResult.Error)
      listener.endGroup("AbandonedSpec", TestResult.Error)
      assert(tempDir.listFiles().isEmpty, tempDir.listFiles().mkString(", "))
      assert(
        debugMessages.get().exists(_.contains("which is not open")),
        debugMessages.get().toString
      )

  /** A passing event reported by `suite` for the test `testName`. */
  private def passed(suite: String, testName: String): TEvent =
    new TEvent:
      def fullyQualifiedName = suite
      def duration() = 1L
      def status = TStatus.Success
      def fingerprint = null
      def selector = new TestSelector(testName)
      def throwable = new OptionalThrowable()

  private def recordingLogger(into: AtomicReference[List[String]]): AbstractLogger =
    new AbstractLogger:
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
        if level == Level.Debug then
          into.updateAndGet(_ :+ message)
          ()

end JUnitXmlTestsListenerSpec
