/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt
/**
 * Indicate whether the project was created organically, synthesized by a plugin,
 * or is a "generic root" project supplied by sbt when a project doesn't exist for `file(".")`.
 */
sealed abstract class ProjectOrigin extends Serializable
object ProjectOrigin {
  
  
  case object Organic extends ProjectOrigin
  case object ExtraProject extends ProjectOrigin
  case object DerivedProject extends ProjectOrigin
  case object GenericRoot extends ProjectOrigin
}
