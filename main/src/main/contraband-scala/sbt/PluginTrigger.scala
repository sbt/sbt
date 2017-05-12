/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt
/**
 * Type for AutoPlugin's trigger method.
 * Determines whether an AutoPlugin will be activated for a project when the
 * `requires` clause is satisfied.
 */
sealed abstract class PluginTrigger extends Serializable
object PluginTrigger {
  
  
  case object AllRequirements extends PluginTrigger
  case object NoTrigger extends PluginTrigger
}
