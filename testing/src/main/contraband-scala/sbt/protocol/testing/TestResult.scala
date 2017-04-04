/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol.testing
/** Testing result */
sealed abstract class TestResult extends Serializable
object TestResult {
  
  
  case object Passed extends TestResult
  case object Failed extends TestResult
  case object Error extends TestResult
}
