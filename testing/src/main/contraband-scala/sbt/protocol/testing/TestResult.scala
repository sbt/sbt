/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
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
