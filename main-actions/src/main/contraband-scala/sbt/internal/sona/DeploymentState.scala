/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.sona
sealed abstract class DeploymentState extends Serializable
object DeploymentState {
  
  
  case object PENDING extends DeploymentState
  case object VALIDATING extends DeploymentState
  case object VALIDATED extends DeploymentState
  case object PUBLISHING extends DeploymentState
  case object PUBLISHED extends DeploymentState
  case object FAILED extends DeploymentState
}
