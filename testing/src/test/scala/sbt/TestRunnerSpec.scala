/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import scala.collection.mutable.ArrayBuffer

import sbt.protocol.testing.TestResult
import sbt.util.LoggerContext
import testing.{
  AnnotatedFingerprint,
  EventHandler,
  Logger => TestLogger,
  Runner,
  SuiteSelector,
  Task => TestTask,
  TaskDef
}
import verify.BasicTestSuite

object TestRunnerSpec extends BasicTestSuite {
  // The direct counterpart to sbt-app/src/sbt-test/tests/test-report-listener-linkage-error.
  // This test isolates TestRunner's catch path; the scripted fixture additionally verifies the
  // public testListeners setting with both implicitly created and explicitly thrown errors.
  test("TestRunner should end the group and rethrow linkage errors") {
    val suiteName = "example.InitializationFailure"
    val linkageError = new ExceptionInInitializerError("suite initialization failed")
    val listener = new RecordingListener
    val testTaskDef = taskDef(suiteName)

    val thrown = withTestRunner(listener) { runner =>
      try {
        runner.run(testTaskDef, new ThrowingTask(testTaskDef, linkageError))
        None
      } catch {
        case error: ExceptionInInitializerError => Some(error)
      }
    }

    assert(thrown.contains(linkageError), s"expected $linkageError to be rethrown, got $thrown")
    assert(
      listener.startedGroups.toSeq == Seq(suiteName),
      s"unexpected started groups: ${listener.startedGroups}"
    )
    assert(
      listener.errorEnds.toSeq == Seq(suiteName -> linkageError),
      s"unexpected error ends: ${listener.errorEnds}"
    )
    assert(
      listener.resultEnds.isEmpty,
      s"expected no result-based group end, got ${listener.resultEnds}"
    )
  }

  private final class RecordingListener extends TestReportListener {
    val startedGroups: ArrayBuffer[String] = ArrayBuffer.empty
    val errorEnds: ArrayBuffer[(String, Throwable)] = ArrayBuffer.empty
    val resultEnds: ArrayBuffer[(String, TestResult)] = ArrayBuffer.empty

    override def startGroup(name: String): Unit = startedGroups += name
    override def testEvent(event: TestEvent): Unit = ()
    override def endGroup(name: String, error: Throwable): Unit = errorEnds += name -> error
    override def endGroup(name: String, result: TestResult): Unit = resultEnds += name -> result
  }

  private final class ThrowingTask(testTaskDef: TaskDef, error: LinkageError) extends TestTask {
    override def tags(): Array[String] = Array.empty
    override def taskDef(): TaskDef = testTaskDef
    override def execute(handler: EventHandler, loggers: Array[TestLogger]): Array[TestTask] = {
      throw error
    }
  }

  private object NoOpRunner extends Runner {
    override def tasks(taskDefs: Array[TaskDef]): Array[TestTask] = Array.empty
    override def done(): String = ""
    override def remoteArgs(): Array[String] = Array.empty
    override def args(): Array[String] = Array.empty
  }

  private def taskDef(name: String): TaskDef = {
    new TaskDef(
      name,
      new AnnotatedFingerprint {
        override def isModule(): Boolean = false
        override def annotationName(): String = "example.Test"
      },
      false,
      Array(new SuiteSelector)
    )
  }

  private def withTestRunner[T](listener: TestReportListener)(f: TestRunner => T): T = {
    val loggerContext = LoggerContext(useLog4J = false)
    try {
      f(
        new TestRunner(
          NoOpRunner,
          Vector(listener),
          loggerContext.logger("TestRunnerSpec", None, None)
        )
      )
    } finally {
      loggerContext.close()
    }
  }
}
