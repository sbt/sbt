package sbt

import sbt.Keys._
import xsbti.{Position, Severity}

object TestPlugin extends AutoPlugin {
  override def requires = plugins.JvmPlugin
  override def trigger = allRequirements

  object autoImport {
    val savedReporter = settingKey[xsbti.Reporter]("Saved reporter that collects compilation failures.")
    val problems = taskKey[Array[xsbti.Problem]]("Problems reported during compilation.")
  }
  import autoImport._
  override def projectSettings = Seq(
    savedReporter := new CollectingReporter,
    compilerReporter in (Compile, compile) := savedReporter.value,
    problems := savedReporter.value.problems
  )
}

class CollectingReporter extends xsbti.Reporter {
  val buffer = collection.mutable.ArrayBuffer.empty[xsbti.Problem]

  def reset(): Unit = {
    System.err.println(s"DEBUGME: Clearing errors: $buffer")
    buffer.clear()
  }
  def hasErrors: Boolean = buffer.exists(_.severity == Severity.Error)
  def hasWarnings: Boolean = buffer.exists(_.severity == Severity.Warn)
  def printSummary(): Unit = ()
  def problems: Array[xsbti.Problem] = buffer.toArray

  def log(problem: xsbti.Problem): Unit =
    log(problem.position, problem.message, problem.severity)

  /** Logs a message. */
  def log(pos: xsbti.Position, msg: String, sev: xsbti.Severity): Unit = {
    object MyProblem extends xsbti.Problem {
      def category: String = null
      def severity: Severity = sev
      def message: String = msg
      def position: Position = pos
      override def toString = s"$position:$severity: $message"
    }
    System.err.println(s"DEBUGME: Logging: $MyProblem")
    buffer.append(MyProblem)
  }

  /** Reports a comment. */
  def comment(pos: xsbti.Position, msg: String): Unit = ()

  override def toString = "CollectingReporter"
}