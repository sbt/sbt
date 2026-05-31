/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import sbt.Keys.*
import sbt.ProjectExtra.*
import sbt.ScopeAxis.{ Select, Zero }
import sbt.SessionVar
import sbt.internal.util.complete.Parser
import sbt.librarymanagement.Configurations.Test as TestConfig

/**
 * Provides commands for running tests with aggregation-aware failure semantics.
 *
 * The `testOnly` command fails if no tests match the patterns across ALL
 * aggregated subprojects, instead of silently passing.
 * See https://github.com/sbt/sbt/issues/3188
 */
object TestCommand:
  val TestOnly = "testOnly"

  private def testOnlyHelp = Help.more(
    TestOnly,
    """testOnly <test-pattern>... [-- <framework-options>]
      |
      |Runs tests matching the given patterns. This command will fail if no
      |tests match the patterns across all aggregated subprojects.
      |""".stripMargin
  )

  /**
   * The testOnly command that fails when no tests match across all subprojects.
   *
   * This command:
   * 1. Parses test patterns and framework options
   * 2. Determines all aggregated subprojects
   * 3. Runs definedTestNames to get all available tests (this also compiles tests)
   * 4. Checks if any tests match the patterns across all subprojects
   * 5. Fails if no tests match, otherwise runs the testSelected task
   */
  def testOnly: Command = Command(TestOnly, testOnlyHelp)(testOnlyParser)(runTestOnly)

  /**
   * Parser for testOnly command arguments.
   * Uses the standard testOnly parser with definedTestNames for consistent completion behavior.
   */
  private def testOnlyParser(state: State): Parser[(Seq[String], Seq[String])] =
    import Defaults.testOnlyParser as defaultParser
    val tests = if Project.isProjectLoaded(state) then
      val extracted = Project.extract(state)
      val currentRef = extracted.currentRef
      val scope = Scope(Select(currentRef), Select(ConfigKey(TestConfig.name)), Zero, Zero)
      val scopedKey = (scope / definedTestNames).scopedKey
      SessionVar.loadAndSet(scopedKey, state, false) match
        case (_, Some(names)) => names.toList
        case _                => Nil
    else Nil
    defaultParser(state, tests)

  /**
   * Get all test names from all aggregated subprojects by running definedTestNames task.
   */
  private def getAllTestNames(state: State): (State, Seq[String]) =
    if !Project.isProjectLoaded(state) then (state, Nil)
    else
      val extracted = Project.extract(state)
      val currentRef = extracted.currentRef
      val structure = extracted.structure

      // Get all aggregated project references (including current)
      val aggregatedProjects = Aggregation.projectAggregates(
        Some(currentRef),
        structure.extra,
        reverse = false
      ) :+ currentRef

      // Run definedTestNames for each project and collect all test names
      var currentState = state
      val allTestNames = aggregatedProjects.flatMap { projRef =>
        val scope = Scope(Select(projRef), Select(ConfigKey(TestConfig.name)), Zero, Zero)
        val scopedKey = scope / definedTestNames
        try
          val (newState, testNames) = extracted.runTask(scopedKey, currentState)
          currentState = newState
          testNames
        catch case _: Exception => Nil
      }.distinct

      (currentState, allTestNames)

  private def runTestOnly(state: State, args: (Seq[String], Seq[String])): State =
    val (patterns, frameworkOptions) = args

    if !Project.isProjectLoaded(state) then
      state.log.error("No project is loaded.")
      state.fail
    else if patterns.isEmpty then
      // No patterns specified, just run the testSelected task
      val taskStr =
        if frameworkOptions.isEmpty then "testSelected"
        else s"testSelected -- ${frameworkOptions.mkString(" ")}"
      taskStr :: state
    else
      // Separate include patterns from exclude patterns (prefixed with -)
      val (excludePatterns, includePatterns) = patterns.partition(_.startsWith("-"))

      // Only check for matches if there are include patterns
      // If only exclude patterns are specified, skip the check (user wants to exclude, not include)
      if includePatterns.nonEmpty then
        // Get all test names by running definedTestNames (this also compiles)
        val (newState, allTestNames) = getAllTestNames(state)
        val filters = IncrementalTest.selectedFilter(includePatterns)
        val matchingTests = allTestNames.filter(name => filters.exists(f => f(name)))

        if matchingTests.isEmpty then
          newState.log.error(s"No tests match the patterns: ${includePatterns.mkString(", ")}")
          newState.log.error(
            "The following patterns were specified but no tests were found in any subproject:"
          )
          includePatterns.foreach(p => newState.log.error(s"  - $p"))
          newState.log.error("")
          newState.log.error("Available tests:")
          allTestNames.sorted.take(20).foreach(t => newState.log.error(s"  - $t"))
          if allTestNames.size > 20 then
            newState.log.error(s"  ... and ${allTestNames.size - 20} more")
          newState.fail
        else
          // Build the testSelected task string
          val testSelectedArgs =
            patterns ++ (if frameworkOptions.nonEmpty then Seq("--") ++ frameworkOptions else Nil)
          val taskStr = s"testSelected ${testSelectedArgs.mkString(" ")}"
          taskStr :: newState
      else
        // Only exclude patterns - just run the task without validation
        val testSelectedArgs =
          patterns ++ (if frameworkOptions.nonEmpty then Seq("--") ++ frameworkOptions else Nil)
        val taskStr = s"testSelected ${testSelectedArgs.mkString(" ")}"
        taskStr :: state

end TestCommand
