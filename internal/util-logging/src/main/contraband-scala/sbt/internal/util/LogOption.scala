/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.util
/** value for logging options like color */
sealed abstract class LogOption extends Serializable
object LogOption {
  
  
  case object Always extends LogOption
  case object Never extends LogOption
  case object Auto extends LogOption
}
