/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal
package testing

import sbt.Incomplete
import sbt.SuiteResult
import sbt.Tests
import sbt.TestsFailedException
import sbt.protocol.testing.TestResult
import sbt.util.Logger

object TestRecapTest extends verify.BasicTestSuite:

  private def withTempDir(f: java.io.File => Unit): Unit =
    val base = java.nio.file.Files.createTempDirectory("sbt-test-recap-").toFile
    try f(base)
    finally
      val _ = java.nio.file.Files
        .walk(base.toPath)
        .sorted(java.util.Comparator.reverseOrder())
        .forEach: p =>
          try java.nio.file.Files.deleteIfExists(p)
          catch case _: Exception => ()

  private def output(result: TestResult, suites: (String, SuiteResult)*): Tests.Output =
    Tests.Output(result, suites.toMap, Iterable.empty)

  private def suite(result: TestResult): SuiteResult =
    new SuiteResult(result, 0, 1, 0, 0, 0, 0, 0)

  private def failure(
      taskName: String,
      result: TestResult,
      suiteName: String
  ): TestsFailedException =
    new TestsFailedException(taskName, Some(output(result, suiteName -> suite(result))))

  /** Build an Incomplete tree carrying the given TestsFailedExceptions as direct causes. */
  private def incompleteOf(exceptions: TestsFailedException*): Incomplete =
    new Incomplete(
      node = None,
      causes = exceptions.map(e => new Incomplete(node = None, directCause = Some(e)))
    )

  private class Capture extends Logger:
    val lines: scala.collection.mutable.ArrayBuffer[(String, String)] =
      scala.collection.mutable.ArrayBuffer.empty
    override def trace(t: => Throwable): Unit = ()
    override def success(msg: => String): Unit = ()
    override def log(level: sbt.util.Level.Value, msg: => String): Unit =
      lines += level.toString -> msg

  test("collect picks up TestsFailedException payloads from the Incomplete tree") {
    val i = incompleteOf(
      failure("a / Test / test", TestResult.Failed, "AFailing"),
      failure("c / Test / test", TestResult.Error, "CErroring"),
    )
    val collected = TestRecap.collect(i)
    assert(collected.map(_.taskName).sorted == Seq("a / Test / test", "c / Test / test"))
    val resultsBy = collected.map(f => f.taskName -> f.output.overall).toMap
    assert(resultsBy("a / Test / test") == TestResult.Failed)
    assert(resultsBy("c / Test / test") == TestResult.Error)
  }

  test("collect ignores exceptions that aren't TestsFailedException with output") {
    val i = new Incomplete(
      node = None,
      causes = Seq(
        new Incomplete(node = None, directCause = Some(new RuntimeException("nope"))),
        new Incomplete(
          node = None,
          directCause = Some(new TestsFailedException)
        ), // no-arg, no output
        new Incomplete(
          node = None,
          directCause = Some(failure("ok / Test / test", TestResult.Failed, "OkFail"))
        ),
      )
    )
    val collected = TestRecap.collect(i)
    assert(collected.size == 1)
    assert(collected.head.taskName == "ok / Test / test")
  }

  test("format emits a header, per-task counts, and indented suite names") {
    val failures = Seq(
      TestRecap.Failure(
        "a / Test / test",
        output(TestResult.Failed, "AFailing" -> suite(TestResult.Failed))
      ),
      TestRecap.Failure(
        "c / Test / test",
        output(TestResult.Error, "CErroring" -> suite(TestResult.Error))
      ),
    )
    val text = TestRecap.format(failures)
    assert(text.startsWith("Test failures recap (2 test tasks failed):\n"))
    assert(text.contains("a / Test / test:"))
    assert(text.contains("c / Test / test:"))
    assert(text.contains("AFailing"))
    assert(text.contains("CErroring"))
    assert(text.contains("Failed tests:"))
    assert(text.contains("Error during tests:"))
    // ASCII-only output: no em-dashes or other non-ASCII separators.
    assert(text.forall(ch => ch < 128), s"non-ASCII characters in recap: $text")
  }

  test("format is empty when there are no failures") {
    assert(TestRecap.format(Seq.empty) == "")
  }

  test("format singular header when exactly one task failed") {
    val one = Seq(
      TestRecap.Failure(
        "a / Test / test",
        output(TestResult.Failed, "AFailing" -> suite(TestResult.Failed))
      )
    )
    assert(TestRecap.format(one).startsWith("Test failures recap (1 test task failed):\n"))
  }

  test("formatTo emits each rendered line as a single error-level log call") {
    val failures = Seq(
      TestRecap.Failure(
        "a / Test / test",
        output(TestResult.Failed, "AFailing" -> suite(TestResult.Failed))
      )
    )
    val log = new Capture
    TestRecap.formatTo(log, failures)
    assert(log.lines.nonEmpty)
    assert(log.lines.forall(_._1 == "error"), s"all lines should be error level: ${log.lines}")
    // No trailing blank-line entry.
    assert(log.lines.forall(_._2.nonEmpty), s"unexpected empty line: ${log.lines}")
  }

  test("formatTo is a no-op when there are no failures") {
    val log = new Capture
    TestRecap.formatTo(log, Seq.empty)
    assert(log.lines.isEmpty)
  }

  test("writeArtifact persists the recap text under <baseDir>/target/") {
    withTempDir: base =>
      TestRecap.writeArtifact(base, "hello recap")
      val f = TestRecap.artifactFile(base)
      assert(f.exists, s"artifact file not written at ${f.getAbsolutePath}")
      val read = java.nio.file.Files
        .readString(f.toPath, java.nio.charset.StandardCharsets.UTF_8)
      assert(read == "hello recap")
  }

  test("writeArtifact is a no-op for empty text") {
    withTempDir: base =>
      TestRecap.writeArtifact(base, "")
      assert(!TestRecap.artifactFile(base).exists)
  }

  test("deleteArtifact removes a previously written recap") {
    withTempDir: base =>
      TestRecap.writeArtifact(base, "stale")
      assert(TestRecap.artifactFile(base).exists)
      TestRecap.deleteArtifact(base)
      assert(!TestRecap.artifactFile(base).exists)
  }

  test("deleteArtifact is a no-op when no recap exists") {
    withTempDir: base =>
      TestRecap.deleteArtifact(base) // must not throw
      assert(!TestRecap.artifactFile(base).exists)
  }

end TestRecapTest
