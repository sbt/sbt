/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
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
