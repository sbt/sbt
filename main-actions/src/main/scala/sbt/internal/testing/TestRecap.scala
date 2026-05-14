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

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.{ Files, StandardOpenOption }

import sbt.Incomplete
import sbt.Tests
import sbt.TestResultLogger
import sbt.TestsFailedException
import sbt.protocol.testing.TestResult
import sbt.util.Logger

/**
 * Stateless formatter that surfaces every failed test task at the end of an
 * aggregated run (see sbt/sbt#2998). The data is read directly off the
 * `Incomplete` tree returned by `Aggregation.runTasks` — each subproject's
 * `testFull` / `testQuick` throws `TestsFailedException` carrying the task
 * name and `Tests.Output`, and we collect those instances from the tree.
 */
private[sbt] object TestRecap:

  /** A single failed test task contributing to the recap. */
  final case class Failure(taskName: String, output: Tests.Output)

  /**
   * Walk the `Incomplete` tree and return one `Failure` per failed
   * `TestsFailedException` that carries its detail payload. Failures whose
   * exceptions don't carry the payload (e.g., user-provided custom
   * exceptions) are skipped — they were not produced by sbt's default test
   * pipeline and there is nothing for the recap to say about them.
   */
  def collect(i: Incomplete): Seq[Failure] =
    Incomplete
      .allExceptions(i)
      .iterator
      .collect:
        case e: TestsFailedException if e.output.isDefined =>
          Failure(e.taskName, e.output.get)
      .toVector

  /** The rendered recap as a single string with `\n`-separated lines. */
  def format(failures: Seq[Failure]): String =
    if failures.isEmpty then ""
    else
      val n = failures.size
      val plural = if n == 1 then "" else "s"
      val sb = StringBuilder()
      sb.append(s"Test failures recap ($n test task$plural failed):\n")
      failures.foreach: f =>
        sb.append(s"  ${f.taskName}: ${TestResultLogger.Defaults.countsString(f.output)}\n")
        val failed = collectByResult(f.output, TestResult.Failed)
        val errored = collectByResult(f.output, TestResult.Error)
        if failed.nonEmpty then
          sb.append("    Failed tests:\n")
          failed.foreach(name => sb.append(s"      $name\n"))
        if errored.nonEmpty then
          sb.append("    Error during tests:\n")
          errored.foreach(name => sb.append(s"      $name\n"))
      sb.result()

  /** Render `failures` and emit one error-level log line per line of output. */
  def formatTo(log: Logger, failures: Seq[Failure]): Unit =
    val text = format(failures)
    if text.nonEmpty then
      // Trim trailing newline so the last log call doesn't emit a blank line.
      text.stripSuffix("\n").split('\n').foreach(log.error(_))

  /**
   * Path within `baseDir` where the recap is persisted as a CI-consumable
   * artifact whenever there are failures. Overwritten on each aggregated run
   * and absent when there are no failures.
   */
  def artifactFile(baseDir: File): File =
    new File(new File(baseDir, "target"), "sbt-test-recap.txt")

  /**
   * Persist `text` to `artifactFile(baseDir)`. Best-effort: any IO problem is
   * silently swallowed because the recap has already been printed to the log
   * and the file is a convenience.
   */
  def writeArtifact(baseDir: File, text: String): Unit =
    if text.nonEmpty then
      try
        val out = artifactFile(baseDir)
        val parent = out.getParentFile
        if parent != null then parent.mkdirs()
        Files.write(
          out.toPath,
          text.getBytes(StandardCharsets.UTF_8),
          StandardOpenOption.CREATE,
          StandardOpenOption.TRUNCATE_EXISTING
        )
        ()
      catch case _: Exception => ()

  /** Remove the artifact file if it exists (used after a passing aggregated run). */
  def deleteArtifact(baseDir: File): Unit =
    try
      val _ = Files.deleteIfExists(artifactFile(baseDir).toPath)
    catch case _: Exception => ()

  private def collectByResult(o: Tests.Output, target: TestResult): Seq[String] =
    o.events.iterator
      .collect {
        case (name, suite) if suite.result == target =>
          scala.reflect.NameTransformer.decode(name)
      }
      .toVector
      .sorted

end TestRecap
