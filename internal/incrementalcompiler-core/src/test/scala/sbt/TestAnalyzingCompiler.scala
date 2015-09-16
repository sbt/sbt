package xsbt

import java.io.File

import xsbti._
import xsbti.compile.SingleOutput
import xsbti.api.Source
import sbt.internal.inc.IncrementalCompilerTest
import sbt.internal.inc.IncrementalCompilerTest._
import sbt.internal.inc.{ TestIncremental, TestAnalysis, TestAnalysisCallback, APIs, IncOptions }
import sbt.internal.util.ConsoleLogger
import sbt.io.IO
import sbt.io.Path._

import scala.tools.nsc.reporters.ConsoleReporter

/**
 * Provides common functionality needed for unit tests that require compiling
 * source code using Scala compiler.
 */
class TestAnalyzingCompiler(incOptions: IncOptions) {

  /** Executes the given scenario using this compiler */
  def execute(s: Scenario): Unit = s execute this

  /**
   * Perform one step of incremental compilation, and return the set of files
   * that have been invalidated during this compilation step.
   */
  def incrementalStep(state: ScenarioState): Set[File] = {

    val changedFiles = state.lastChanges map (f => state.directory / f._1)
    val (modifiedFiles, deletedFiles) = changedFiles partition (_.exists)

    changedFiles foreach deleteProducts

    val internalMap = analyses.headOption map (_.products.map(p => (p._2, p._1)).toMap) getOrElse Map.empty
    val analysisCallback = new TestAnalysisCallback(internalMap, incOptions.nameHashing)
    val classesDir = state.directory / "classes"
    classesDir.mkdir()

    val compiler = prepareCompiler(classesDir, analysisCallback, classesDir.getAbsolutePath)

    val run = new compiler.Run
    run compile (modifiedFiles map (_.getAbsolutePath)).toList

    if (compiler.reporter.hasErrors) {
      val exception = new CompilationFailedException(reporter.problems map (_.message))
      reporter.reset()
      throw exception
    }

    reporter.reset()

    analyses = analyses match {
      case Nil     => analysisCallback.get :: Nil
      case x :: xs => (analysisCallback.get.merge(x, deletedFiles)) :: x :: xs
    }

    computeInvalidations(state)

  }

  /**
   * Return the set of files that have been invalidated by the last incremental
   * compilation step.
   */
  def computeInvalidations(state: ScenarioState): Set[File] = {
    def apiFrom(a: Option[TestAnalysis]): File => Source =
      (f: File) => (a map (_.apis) getOrElse APIs.empty) internalAPI f

    val freshlyRecompiled =
      state.lastChanges.map(x => state.directory / x._1).toSet

    val apiChanges = incremental.changedIncremental(freshlyRecompiled, apiFrom(analyses lift 1), apiFrom(analyses.headOption))

    incremental.invalidateIncremental(analyses.head.relations, analyses.head.apis, apiChanges, freshlyRecompiled, false)

  }

  /**
   * Removes all the products and previous analyses
   */
  def clean(state: ScenarioState): Unit = {
    val files = state.files map (f => state.directory / f._1)
    files foreach deleteProducts
    analyses = Nil
  }

  /**
   * Returns the analyses produces by all compilation steps so far.
   */
  def getAnalyses: List[TestAnalysis] = analyses

  private val reporter = new TestReporter
  private val incremental = new TestIncremental(ConsoleLogger(), incOptions)
  private var analyses: List[TestAnalysis] = Nil

  private def deleteProducts(file: File): Unit = {
    analyses.headOption foreach { a =>
      val products = a.products filter (_._1 == file)
      products foreach { case (_, classFile, _) => IO delete classFile }
    }
  }

  private def prepareCompiler(outputDir: File, analysisCallback: AnalysisCallback, classpath: String = "."): CachedCompiler0#Compiler = {
    val args = Array.empty[String]
    object output extends SingleOutput {
      def outputDirectory: File = outputDir
      override def toString = s"SingleOutput($outputDirectory)"
    }
    val weakLog = new WeakLog(ConsoleLogger(), reporter)
    val cachedCompiler = new CachedCompiler0(args, output, weakLog, true)
    val settings = cachedCompiler.settings
    settings.classpath.value = classpath
    settings.usejavacp.value = true
    val scalaReporter = new ConsoleReporter(settings)
    val delegatingReporter = DelegatingReporter(settings, reporter)
    val compiler = cachedCompiler.compiler
    compiler.set(analysisCallback, delegatingReporter)
    compiler
  }
}

class TestReporter extends xsbti.Reporter {

  case class Problem(severity: Severity, message: String, position: Position) extends xsbti.Problem {
    val category = ""
  }

  private var _problems: Array[xsbti.Problem] = Array.empty
  def problems() = _problems

  def comment(pos: Position, msg: String): Unit =
    _problems = _problems :+ Problem(Severity.Info, msg, pos)

  def hasErrors(): Boolean =
    _problems exists (_.severity == Severity.Error)

  def hasWarnings(): Boolean =
    _problems exists (_.severity == Severity.Warn)

  def log(pos: xsbti.Position, msg: String, severity: xsbti.Severity): Unit =
    _problems = _problems :+ Problem(severity, msg, pos)

  def printSummary(): Unit =
    _problems foreach println

  def reset(): Unit = { _problems = Array.empty }
}

case class CompilationFailedException(errors: List[String]) extends Exception {
  def this(errors: Array[String]) = this(errors.toList)
  override def toString: String = errors mkString "\n"
}
